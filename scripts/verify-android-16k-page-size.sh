#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
MAIN_MANIFEST="$ANDROID_DIR/app/src/main/AndroidManifest.xml"
RELEASE_MANIFEST="$ANDROID_DIR/app/src/release/AndroidManifest.xml"
APP_GRADLE="$ANDROID_DIR/app/build.gradle"
PRIVACY_POLICY="$ROOT/docs/PRIVACY_POLICY.md"
DATA_SAFETY="$ROOT/store/play/DATA_SAFETY.md"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
NDK_VERSION="29.0.14206865"
NDK_DIR="$SDK_ROOT/ndk/$NDK_VERSION"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -d "$NDK_DIR" ]]; then
  [[ -x "$SDKMANAGER" ]] || { echo "sdkmanager missing: $SDKMANAGER" >&2; exit 1; }
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" "ndk;$NDK_VERSION" >/dev/null
fi

cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all :app:assembleRelease :app:bundleRelease

APK="$(find app/build/outputs/apk/release -maxdepth 1 -type f \( -name 'app-release.apk' -o -name 'app-release-unsigned.apk' \) -print -quit)"
AAB="app/build/outputs/bundle/release/app-release.aab"
SYMBOL_ZIP="app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"
[[ -n "$APK" && -s "$APK" ]] || { echo "release APK missing" >&2; exit 1; }
[[ -s "$AAB" ]] || { echo "release AAB missing" >&2; exit 1; }
[[ -s "$SYMBOL_ZIP" ]] || { echo "FULL native debug symbols missing: $SYMBOL_ZIP" >&2; exit 1; }
[[ -d "$NDK_DIR" ]] || { echo "pinned NDK missing: $NDK_DIR" >&2; exit 1; }

# Inspect the merged release manifest, not only source manifests, so transitive library manifests
# cannot silently change package behavior. Reader main stays network-free; the release overlay
# explicitly declares the approved Google Play Billing / In-App Review capability.
MERGED_MANIFEST="$(find app/build/intermediates -type f -name AndroidManifest.xml -path '*release*' -print | grep -E '/merged_manifest/|/merged_manifests/' | head -n1 || true)"
[[ -n "$MERGED_MANIFEST" && -s "$MERGED_MANIFEST" ]] || { echo "merged release manifest missing" >&2; exit 1; }
if grep -Fq 'android.permission.INTERNET' "$MAIN_MANIFEST"; then
  echo "Reader core/main manifest must remain network-free" >&2
  exit 1
fi
grep -Fq 'android.permission.INTERNET' "$RELEASE_MANIFEST" || { echo "Jingdu release overlay must explicitly declare approved Play network capability" >&2; exit 1; }
grep -Fq 'android.permission.INTERNET' "$MERGED_MANIFEST" || { echo "merged release manifest lost required INTERNET permission for Play services" >&2; exit 1; }
grep -Fq 'android:usesCleartextTraffic="false"' "$MERGED_MANIFEST" || { echo "merged release manifest must keep cleartext traffic disabled" >&2; exit 1; }
grep -Fq 'com.android.billingclient:billing:9.1.0' "$APP_GRADLE" || { echo "approved Billing dependency drift" >&2; exit 1; }
grep -Fq 'com.google.android.play:review:2.0.2' "$APP_GRADLE" || { echo "approved In-App Review dependency drift" >&2; exit 1; }
grep -Fq 'Google Play Billing' "$PRIVACY_POLICY" || { echo "privacy policy missing Google Play Billing network boundary" >&2; exit 1; }
grep -Fq 'Google Play In-App Review' "$PRIVACY_POLICY" || { echo "privacy policy missing Google Play Review network boundary" >&2; exit 1; }
grep -Fq 'package requests `INTERNET`' "$DATA_SAFETY" || { echo "Data Safety worksheet missing final package INTERNET disclosure" >&2; exit 1; }
if grep -Fq '<profileable' "$MERGED_MANIFEST"; then
  echo "merged release manifest must not be profileable" >&2
  exit 1
fi
if grep -Eq 'android:debuggable="true"|android:allowBackup="true"' "$MERGED_MANIFEST"; then
  echo "merged release manifest contains debug/backup behavior forbidden for production" >&2
  exit 1
fi

READELF="$(find "$NDK_DIR/toolchains/llvm/prebuilt" -path '*/bin/llvm-readelf' -print -quit)"
ZIPALIGN="$(find "$SDK_ROOT/build-tools" -type f -name zipalign -perm -111 | sort -V | tail -n1)"
[[ -n "$READELF" && -x "$READELF" ]] || { echo "llvm-readelf missing from pinned NDK" >&2; exit 1; }
[[ -x "$ZIPALIGN" ]] || { echo "zipalign missing" >&2; exit 1; }

"$ZIPALIGN" -c -P 16 -v 4 "$APK" >/tmp/jingdu-zipalign-16k.txt
cat /tmp/jingdu-zipalign-16k.txt

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" 'lib/*/*.so' -d "$TMP"
for abi in arm64-v8a x86_64; do
  [[ -d "$TMP/lib/$abi" ]] || { echo "release APK missing required 64-bit ABI: $abi" >&2; exit 1; }
done
mapfile -t LIBS < <(find "$TMP/lib/arm64-v8a" "$TMP/lib/x86_64" -type f -name '*.so' | sort)
((${#LIBS[@]} > 0)) || { echo "release APK contains no 64-bit native libraries" >&2; exit 1; }

for lib in "${LIBS[@]}"; do
  mapfile -t aligns < <("$READELF" -lW "$lib" | awk '$1 == "LOAD" {print $NF}')
  ((${#aligns[@]} > 0)) || { echo "no ELF LOAD segments: $lib" >&2; exit 1; }
  for align in "${aligns[@]}"; do
    value=$((align))
    if (( value < 16384 )); then
      echo "16 KiB 64-bit ELF alignment failure: $lib LOAD align=$align" >&2
      exit 1
    fi
  done
  echo "16 KiB 64-bit ELF alignment OK: ${lib#"$TMP/"} (${aligns[*]})"
done

grep -Fq 'ndkVersion = "29.0.14206865"' app/build.gradle
grep -Fq 'debugSymbolLevel = "FULL"' app/build.gradle
grep -Fq 'androidx.sqlite:sqlite-bundled:2.7.1' app/build.gradle
unzip -Z1 "$SYMBOL_ZIP" > "$TMP/native-symbols.list"
grep -qE '\.(so|dbg|sym)$|libjingdu_(native|core)' "$TMP/native-symbols.list"

echo "Android release package/network/16 KiB gate PASS"
echo "Pinned NDK: $NDK_VERSION"
echo "Validated ELF ABIs: arm64-v8a x86_64"
echo "Release AAB: $AAB"
echo "Native symbols: $SYMBOL_ZIP"
