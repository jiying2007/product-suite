#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
APP_GRADLE="$ANDROID_DIR/app/build.gradle"
TAG="${1:-}"

usage() {
  echo "usage: bash ./scripts/stage-android-production-evidence.sh vX.Y.Z" >&2
  exit 2
}

[[ -n "$TAG" ]] || usage
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || usage
VERSION="${TAG#v}"

cd "$ROOT"

TAG_TYPE="$(git cat-file -t "$TAG" 2>/dev/null || true)"
[[ "$TAG_TYPE" == "tag" ]] || {
  echo "source ref must be an annotated immutable release tag: $TAG" >&2
  exit 1
}

HEAD_SHA="$(git rev-parse HEAD)"
TAG_SHA="$(git rev-parse "$TAG^{commit}")"
[[ "$HEAD_SHA" == "$TAG_SHA" ]] || {
  echo "checkout must be exact $TAG ($TAG_SHA); current HEAD is $HEAD_SHA" >&2
  exit 1
}

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "tracked worktree must be clean before production evidence staging" >&2
  git status --short >&2
  exit 1
fi

SOURCE_MANIFEST="$ROOT/releases/source/$TAG.md"
[[ -s "$SOURCE_MANIFEST" ]] || {
  echo "source manifest missing: $SOURCE_MANIFEST" >&2
  exit 1
}
grep -Fxq "version: $TAG" "$SOURCE_MANIFEST" || {
  echo "source manifest version mismatch: $SOURCE_MANIFEST" >&2
  exit 1
}
grep -Fxq "kind: source-release" "$SOURCE_MANIFEST" || {
  echo "source manifest kind mismatch: $SOURCE_MANIFEST" >&2
  exit 1
}

VERSION_CODE="$(sed -n 's/.*versionCodeValue = versionCodeProperty.map.*getOrElse(\([0-9][0-9]*\)).*/\1/p' "$APP_GRADLE")"
DEFAULT_VERSION="$(sed -n 's/.*versionNameValue = versionNameProperty.getOrElse("\([^"]*\)").*/\1/p' "$APP_GRADLE")"
[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || {
  echo "could not resolve positive default versionCode from $APP_GRADLE" >&2
  exit 1
}
[[ "$DEFAULT_VERSION" == "$VERSION" ]] || {
  echo "tag/app version mismatch: tag=$VERSION app=$DEFAULT_VERSION" >&2
  exit 1
}

KEYSTORE_PROPERTIES="$ANDROID_DIR/keystore.properties"
[[ -s "$KEYSTORE_PROPERTIES" ]] || {
  echo "production/upload signing config missing: $KEYSTORE_PROPERTIES" >&2
  exit 1
}

export ORG_GRADLE_PROJECT_jingduApplicationId="com.junchen.jingdu"
export ORG_GRADLE_PROJECT_jingduVersionCode="$VERSION_CODE"
export ORG_GRADLE_PROJECT_jingduVersionName="$VERSION"

cd "$ANDROID_DIR"
./gradlew --no-daemon --no-configuration-cache --warning-mode all androidStoreCheck
cd "$ROOT"

# Reuse the canonical package/native-symbol verifier with the same explicit Gradle properties.
bash ./scripts/verify-android-16k-page-size.sh

APK="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
AAB="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.aab"
MAPPING="$ANDROID_DIR/app/build/outputs/mapping/release/mapping.txt"
SYMBOLS="$ANDROID_DIR/app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"
DEPENDENCIES="$ANDROID_DIR/app/build/reports/jingdu-release-dependencies.txt"
for artifact in "$APK" "$AAB" "$MAPPING" "$SYMBOLS" "$DEPENDENCIES"; do
  [[ -s "$artifact" ]] || {
    echo "required production evidence artifact missing: $artifact" >&2
    exit 1
  }
done

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner -perm -111 | sort -V | tail -n1)"
[[ -x "$APKSIGNER" ]] || {
  echo "apksigner not found under $SDK_ROOT/build-tools" >&2
  exit 1
}

APKSIGNER_REPORT="$(mktemp)"
trap 'rm -f "$APKSIGNER_REPORT"' EXIT
"$APKSIGNER" verify --verbose --print-certs "$APK" | tee "$APKSIGNER_REPORT"
UPLOAD_CERT_SHA="$(sed -n 's/.*certificate SHA-256 digest: //p' "$APKSIGNER_REPORT" | head -n1 | tr '[:upper:]' '[:lower:]' | tr -d ':')"
[[ "$UPLOAD_CERT_SHA" =~ ^[0-9a-f]{64}$ ]] || {
  echo "could not resolve release APK signing certificate SHA-256" >&2
  exit 1
}

command -v jarsigner >/dev/null || {
  echo "jarsigner missing from the configured JDK" >&2
  exit 1
}
command -v keytool >/dev/null || {
  echo "keytool missing from the configured JDK" >&2
  exit 1
}
JARSIGNER_REPORT="$(mktemp)"
trap 'rm -f "$APKSIGNER_REPORT" "$JARSIGNER_REPORT"' EXIT
jarsigner -J-Duser.language=en -J-Duser.country=US -verify "$AAB" | tee "$JARSIGNER_REPORT"
grep -Fq "jar verified." "$JARSIGNER_REPORT" || {
  echo "release AAB JAR signature verification failed" >&2
  exit 1
}
AAB_CERT_SHA="$(keytool -J-Duser.language=en -J-Duser.country=US -printcert -jarfile "$AAB" \
  | sed -n 's/^[[:space:]]*SHA256: //p' | head -n1 | tr '[:upper:]' '[:lower:]' | tr -d ':')"
[[ "$AAB_CERT_SHA" =~ ^[0-9a-f]{64}$ ]] || {
  echo "could not resolve release AAB signing certificate SHA-256" >&2
  exit 1
}
[[ "$AAB_CERT_SHA" == "$UPLOAD_CERT_SHA" ]] || {
  echo "release APK/AAB signing certificate mismatch" >&2
  exit 1
}

OUT="$ANDROID_DIR/build/production-evidence/$VERSION"
rm -rf "$OUT"
mkdir -p "$OUT"

APK_NAME="jingdu-$VERSION-release.apk"
AAB_NAME="jingdu-$VERSION-release.aab"
MAPPING_NAME="mapping-$VERSION.txt"
SYMBOLS_NAME="native-debug-symbols-$VERSION.zip"
DEPENDENCIES_NAME="release-dependencies-$VERSION.txt"

cp "$APK" "$OUT/$APK_NAME"
cp "$AAB" "$OUT/$AAB_NAME"
cp "$MAPPING" "$OUT/$MAPPING_NAME"
cp "$SYMBOLS" "$OUT/$SYMBOLS_NAME"
cp "$DEPENDENCIES" "$OUT/$DEPENDENCIES_NAME"
printf '%s\n' "$UPLOAD_CERT_SHA" > "$OUT/UPLOAD-CERT-SHA256.txt"

SOURCE_MANIFEST_SHA="$(sha256sum "$SOURCE_MANIFEST" | awk '{print $1}')"
cat > "$OUT/PROVENANCE.txt" <<EOF
source_tag=$TAG
source_sha=$TAG_SHA
source_manifest=releases/source/$TAG.md
source_manifest_sha256=$SOURCE_MANIFEST_SHA
application_id=com.junchen.jingdu
version_code=$VERSION_CODE
version_name=$VERSION
upload_certificate_sha256=$UPLOAD_CERT_SHA
aab_signing_certificate_sha256=$AAB_CERT_SHA
play_app_signing_certificate_sha256=external-play-console-evidence
google_play_production=false
EOF

(
  cd "$OUT"
  sha256sum \
    "$APK_NAME" \
    "$AAB_NAME" \
    "$MAPPING_NAME" \
    "$SYMBOLS_NAME" \
    "$DEPENDENCIES_NAME" \
    "UPLOAD-CERT-SHA256.txt" \
    "PROVENANCE.txt" > SHA256SUMS.txt
)

echo "Production evidence staged from immutable $TAG ($TAG_SHA)"
echo "Output: $OUT"
echo "This bundle proves local upload-signed artifact provenance only."
echo "Play App Signing, Play bundle/pre-launch acceptance and rollout remain external evidence."
