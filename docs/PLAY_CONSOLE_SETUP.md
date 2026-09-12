# Google Play Console Setup — Android 2.3.x

This repository contains the code and store-copy SSOT. It does not have a connected Google Play Console automation provider, so the Console actions below must be performed in Play Console and then verified against this contract and `PRODUCTION_READINESS.md`.

## 1. Lifetime Pro one-time product

Create one one-time in-app product for application `com.junchen.jingdu`:

- Product ID: `jingdu_pro_lifetime`
- Type: non-consumable / one-time product
- Product value: local Smart Clean automation and reusable local Reader assets
- Do not describe core reading as paid-only.

Localize the product name/description in all supported Play locales:

| Locale | Product name | Positioning |
| --- | --- | --- |
| `zh-CN` | `净读 Pro 永久版` | 一次买断的智能净读自动化、全局规则、离线 voice 与本地资产备份 |
| `zh-TW` | `淨讀 Pro 永久版` | 一次買斷的智慧淨讀自動化、全域規則、離線 voice 與本機資產備份 |
| `zh-HK` | `淨讀 Pro 永久版` | 一次買斷的智慧淨讀自動化、全域規則、離線 voice 與本機資產備份 |
| `en-US` | `Jingdu Pro Lifetime` | One-time unlock for Smart Clean automation, global rules, offline voice selection and local Reader asset backup |

Activate an eligible one-time purchase offer and configure localized prices. The app never hard-codes a price; it renders Play’s localized `formattedPrice`.

Suggested first price experiment anchors:
- US$4.99
- US$6.99
- US$8.99

Choose the production starting price based on the target countries and then use Play one-time-product price experiments where available.

## 2. Billing tests

Before production rollout:

- add license testers;
- verify normal purchase;
- verify purchase cancellation;
- verify pending purchase does not unlock Pro;
- verify completed purchase unlocks Pro and is acknowledged;
- verify reinstall/clear-data restore through the same Play account;
- verify offline use after a previously Play-verified entitlement;
- verify authoritative no-ownership refresh removes stale entitlement;
- verify a device without Play Billing keeps all Free reading capabilities functional;
- verify product-not-configured state shows a localized unavailable/retry message rather than blocking Clean or reading.

## 3. Default store listings

Repository source:

- `fastlane/metadata/android/zh-CN/`
- `fastlane/metadata/android/zh-TW/`
- `fastlane/metadata/android/zh-HK/`
- `fastlane/metadata/android/en-US/`

Expected titles:

- `zh-CN`: `净读 - TXT 小说阅读器`
- `zh-TW`: `淨讀 - TXT 小說閱讀器`
- `zh-HK`: `淨讀 - TXT 小說閱讀器`
- `en-US`: `Jingdu - Offline TXT Reader`

English localization communicates the same Chinese-TXT-depth product and must not imply EPUB/PDF/cloud catalog support.

Do not add ranking, award, temporary price, discount or “best/#1” claims to titles or graphic assets.

## 4. Search-keyword Custom Store Listings

Locale specifications:

- `store/play/CUSTOM_LISTINGS.zh-CN.md`
- `store/play/CUSTOM_LISTINGS.zh-TW.md`
- `store/play/CUSTOM_LISTINGS.zh-HK.md`
- `store/play/CUSTOM_LISTINGS.en-US.md`

Create keyword-targeted listings only with Search keyword bundles that Play Console makes available for the app. Target four intent groups when suitable traffic exists:

1. `txt-reader`
2. `txt-encoding`
3. `smart-clean`
4. `local-novel`

Each listing should use its matching hero screenshot first. The listing may customize app name, descriptions and graphic assets, while shared privacy/contact/category settings remain consistent.

## 5. Screenshots and graphics

Use the matching locale brief:

- `store/play/SCREENSHOT_BRIEF.zh-CN.md`
- `store/play/SCREENSHOT_BRIEF.zh-TW.md`
- `store/play/SCREENSHOT_BRIEF.zh-HK.md`
- `store/play/SCREENSHOT_BRIEF.en-US.md`

Capture actual release UI using synthetic/public-domain demo TXT content. Do not expose private user books or filenames. Do not put unverified performance numbers into screenshots. Do not reuse Simplified-Chinese captioned artwork for Traditional or English listings.

## 6. App-language verification

Android ships `zh-Hans`, `zh-Hant` and `en-US` UI resources and uses generated platform LocaleConfig. Before staged rollout:

- verify system/per-app language selection on `zh-CN`, `zh-TW`, `zh-HK`, `en-US`;
- verify an unsupported system language falls back to English;
- verify changing app language leaves book identity, progress, bookmarks, rules and selected offline TTS voice unchanged;
- verify Library/Reader/Clean/Settings with 200% font scaling in all three UI language families.

See `LOCALIZATION.md` and `DEVICE_MATRIX.md`.

## 7. Portable local-user backup verification

Before advertising local backup as a Pro value:

- verify Reader settings/global rules/annotations round-trip;
- verify favorites/tags round-trip by source identity;
- verify progress restores only for the exact `normalizedSha256` and remains staged until that revision is present;
- verify reading sessions/pace round-trip without book text;
- verify Smart Clean KEEP/DELETE/PROTECT memory round-trips as fingerprints/decisions only;
- verify backup root declares `containsBookText=false`;
- verify SAF folder grants and unavailable imported fonts require user re-selection rather than pretending those capabilities are portable.

## 8. Store listing experiments

Recommended order:

1. icon;
2. first/hero screenshot;
3. short description;
4. screenshot order.

Change one major variable per test. Compare install conversion and downstream user quality rather than chasing clicks alone.

## 9. App content and policy declarations

Complete every applicable Play Console **App content** declaration against the exact production AAB before review:

- Privacy policy: use the HTTPS URL in `store/play/PRIVACY_POLICY_URL.txt`; confirm it is publicly accessible, non-geofenced, non-PDF, and matches the in-app Privacy policy action.
- Data safety: submit answers from `store/play/DATA_SAFETY.md` and re-check the exact production AAB dependency/permission graph before submission.
- Ads: declare **No** while the product contains no advertising SDK or ad placement.
- App access: Jingdu has no product account/sign-in gate; declare that reviewer access credentials are not required unless that architecture changes.
- Target audience and content: select the actual intended age groups. Do not include children merely to broaden reach; if children are intentionally included, complete the Families-policy review before release.
- Content rating: complete and retain the rating questionnaire/result for the exact listing; do not submit an unrated app.
- Foreground services: declare the `mediaPlayback` foreground-service type used by `TtsPlaybackService`. State that it continues user-initiated read-aloud while the app is backgrounded, explain the impact of deferral/interruption, and provide the Play-required reviewer video showing how a user starts and stops read-aloud.
- Any newly surfaced declaration in **Needs attention** is release-blocking until completed or made inapplicable by changing the production AAB.

The foreground-service declaration must remain consistent with the production manifest permissions `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `android:foregroundServiceType="mediaPlayback"`. Do not add broader FGS types as a workaround.

## 10. Release safety

Before uploading Android 2.3.x:

- use the retained Android upload key from the existing signing identity;
- run `androidStoreCheck` with explicit version properties;
- archive signed APK/AAB, mapping, SHA256 manifest and signing certificate fingerprint;
- complete physical-device matrix and release SLO evidence from `PRODUCTION_READINESS.md`;
- confirm Billing product and all four localized product descriptions are active before advertising Pro as purchasable;
- confirm all four default listings and intended Custom Listings are uploaded from repository SSOT;
- confirm Data safety / privacy declarations remain consistent with no text upload, no advertising SDK and no analytics SDK;
- complete the App content declarations in section 9, including the `mediaPlayback` foreground-service declaration;
- capture actual GitHub `main`/`v*` protection evidence;
- use internal/closed testing and staged rollout rather than immediately exposing 100% of production users after the Reader + commerce hardening changes.

## 11. Post-release checks

Verify in production Play:

- `zh-CN / zh-TW / zh-HK / en-US` default titles/descriptions render correctly;
- search-keyword listings route to the intended localized page;
- product title/description and price are localized correctly;
- purchase/restore works on a real production-installed build;
- unsupported system language falls back to English in-app;
- no unexpected INTERNET/runtime analytics dependency was introduced;
- review prompt appears only after meaningful milestones and never as a first-launch gate;
- staged rollout expansion is tied to the exact source tag/AAB checksum and Android vitals evidence.
