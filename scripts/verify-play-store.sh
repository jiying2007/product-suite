#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

root = Path('.')
locales = ['zh-CN', 'zh-TW', 'zh-HK', 'en-US']
limits = {'title.txt': 30, 'short_description.txt': 80, 'full_description.txt': 4000}
for locale in locales:
    base = root / 'fastlane' / 'metadata' / 'android' / locale
    for filename, limit in limits.items():
        path = base / filename
        if not path.is_file():
            raise SystemExit(f'missing Play metadata: {path}')
        value = path.read_text(encoding='utf-8').strip()
        if not value:
            raise SystemExit(f'empty Play metadata: {path}')
        if len(value) > limit:
            raise SystemExit(f'{path}: {len(value)} characters > {limit}')

expected_titles = {
    'zh-CN': '净读 - TXT 小说阅读器',
    'zh-TW': '淨讀 - TXT 小說閱讀器',
    'zh-HK': '淨讀 - TXT 小說閱讀器',
}
for locale, expected in expected_titles.items():
    title = (root / f'fastlane/metadata/android/{locale}/title.txt').read_text(encoding='utf-8').strip()
    if title != expected:
        raise SystemExit(f'unexpected {locale} default title: {title!r}')

zh_title = expected_titles['zh-CN']
for banned in ('免费', '#1', '最强', '第一', '折扣', '限时'):
    if banned in zh_title:
        raise SystemExit(f'promotional/ranking term in Play title: {banned}')

short = (root / 'fastlane/metadata/android/zh-CN/short_description.txt').read_text(encoding='utf-8')
for required in ('TXT', '乱码', '净读', '大文件'):
    if required not in short:
        raise SystemExit(f'zh-CN short description missing product intent: {required}')

custom = (root / 'store/play/CUSTOM_LISTINGS.zh-CN.md').read_text(encoding='utf-8')
for key in ('txt-reader', 'txt-encoding', 'smart-clean', 'local-novel'):
    if f'`{key}`' not in custom:
        raise SystemExit(f'missing custom listing spec: {key}')

for path in (
    root / 'store/play/SCREENSHOT_BRIEF.zh-CN.md',
    root / 'docs/GROWTH_MONETIZATION.md',
    root / 'docs/PLAY_CONSOLE_SETUP.md',
    root / 'docs/PRIVACY_POLICY.md',
    root / 'docs/PRIVACY_POLICY.zh-CN.md',
    root / 'docs/PRIVACY_POLICY.zh-Hant.md',
    root / 'store/play/DATA_SAFETY.md',
    root / 'store/play/PRIVACY_POLICY_URL.txt',
):
    if not path.is_file() or not path.read_text(encoding='utf-8').strip():
        raise SystemExit(f'missing growth/store/privacy SSOT: {path}')

privacy = (root / 'docs/PRIVACY_POLICY.md').read_text(encoding='utf-8')
for required in (
    'Jingdu Privacy Policy',
    'jiying2007',
    'Text-to-Speech',
    'PROCESS_TEXT',
    'Google Play Billing',
    'Google Play In-App Review',
    'requests `INTERNET`',
    'Retention and deletion',
    'com.junchen.jingdu',
):
    if required not in privacy:
        raise SystemExit(f'privacy policy missing required disclosure: {required}')

privacy_url = (root / 'store/play/PRIVACY_POLICY_URL.txt').read_text(encoding='utf-8').strip()
if not privacy_url.startswith('https://') or 'docs/PRIVACY_POLICY.md' not in privacy_url:
    raise SystemExit(f'invalid Play privacy policy URL: {privacy_url!r}')
if '/blob/main/' in privacy_url:
    raise SystemExit('Play privacy policy URL must not depend on mutable main')

privacy_resource = (root / 'apps/jingdu/android/app/src/main/res/values/strings_privacy.xml').read_text(encoding='utf-8')
if privacy_url not in privacy_resource:
    raise SystemExit('in-app privacy policy URL must match Play privacy policy URL')

data_safety = (root / 'store/play/DATA_SAFETY.md').read_text(encoding='utf-8')
for required in ('Data safety', 'TTS engine', 'PROCESS_TEXT', 'Google Play Billing', 'Google Play In-App Review', 'package requests `INTERNET`', 'production AAB'):
    if required not in data_safety:
        raise SystemExit(f'Data safety SSOT missing required boundary: {required}')

play_setup = (root / 'docs/PLAY_CONSOLE_SETUP.md').read_text(encoding='utf-8')
for required in ('App content and policy declarations', 'Ads: declare **No**', 'Target audience and content', 'Content rating', 'Foreground services', '`mediaPlayback`'):
    if required not in play_setup:
        raise SystemExit(f'Play Console setup missing required production declaration: {required}')
PY

MAIN_MANIFEST='apps/jingdu/android/app/src/main/AndroidManifest.xml'
BENCHMARK_MANIFEST='apps/jingdu/android/app/src/benchmark/AndroidManifest.xml'
if grep -Fq '<profileable' "$MAIN_MANIFEST"; then
  echo 'production Jingdu manifest must not be profileable' >&2
  exit 1
fi
grep -Fq '<profileable android:shell="true"' "$BENCHMARK_MANIFEST"
grep -Fq 'android.permission.INTERNET' "$MAIN_MANIFEST"
grep -Fq 'android:usesCleartextTraffic="false"' "$MAIN_MANIFEST"

python3 ./scripts/verify-android-i18n.py
python3 ./scripts/verify-release-version.py

grep -q 'com.android.billingclient:billing:9.1.0' apps/jingdu/android/app/build.gradle
grep -q 'com.google.android.play:review:2.0.2' apps/jingdu/android/app/build.gradle
grep -q 'androidx.room3:room3-runtime:3.0.3' apps/jingdu/android/app/build.gradle
grep -q 'androidx.sqlite:sqlite-bundled:2.7.1' apps/jingdu/android/app/build.gradle

grep -q 'jingdu_pro_lifetime' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'enableOneTimeProducts' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'queryPurchasesAsync' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'Purchase.PurchaseState.PURCHASED' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt
grep -q 'acknowledgePurchase' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingManager.kt

grep -q 'R.string.scan_noise_free' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q 'R.string.unlock_pro_apply' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSheets.kt
grep -q 'R.string.offline_voice' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
grep -q 'R.string.local_asset_backup' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
grep -q 'R.string.privacy_policy' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
grep -q 'Intent.ACTION_VIEW' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
grep -q 'OpenMultipleDocuments' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
grep -q 'ReviewManagerFactory' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReviewPrompter.kt

echo 'Play store/growth/monetization/privacy contract OK'
