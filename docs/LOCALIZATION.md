# Localization contract

Jingdu is Chinese-content-first, not Chinese-UI-only.

## Supported application languages

Android ships three first-class UI language families:

- `en-US` — unqualified fallback for devices whose language is not otherwise supported.
- `zh-Hans` — Simplified Chinese.
- `zh-Hant` — Traditional Chinese. Regional overrides may be added only when Taiwan or Hong Kong wording materially differs.

`apps/jingdu/android/app/src/main/res/resources.properties` declares `en-US` as the unqualified locale and AGP generates the platform `LocaleConfig`. The app follows the Android per-app/system language selection; Jingdu does not maintain a second custom language preference.

All three `strings.xml` files must contain the same resource-key set. User-facing Compose copy belongs in resources, not Kotlin literals.

## Content language is independent from UI language

A user may read Traditional Chinese while using an English UI, or Simplified Chinese while using a Traditional Chinese UI. Therefore document behavior must never branch on the app locale.

- Encoding detection continues to support UTF-8/UTF-16, GB18030/GBK/GB2312 and Big5.
- Smart Clean scans Simplified and Traditional promotional/watermark markers in the shared Core.
- Smart Clean Core output uses stable language-neutral reason codes (`url`, `promo`, `repeated`, `promo_repeated`); each platform shell localizes them.
- Recommended global Clean rules include Simplified and Traditional variants.
- Search performs exact search first and also tries safe one-to-one Simplified/Traditional character variants, merging results by document offset. This is a convenience fallback, not a lossy whole-document conversion.
- TTS chooses a document-language locale from visible text when the user has not selected an explicit offline voice. Explicit voice selection always wins.

## Google Play locales

The default listing must be maintained in all four supported store locales:

- `zh-CN`
- `zh-TW`
- `zh-HK`
- `en-US`

The English listing communicates the same product position: a privacy-first offline TXT reader with deep Chinese-text support. English UI/store support does **not** expand the 2.x scope into a generic multi-format ebook reader.

The one-time product `jingdu_pro_lifetime` must have localized product name/description in the same four Play Console locales. Screenshot captions and feature-graphic copy should be produced from the same locale strings rather than embedding Simplified Chinese in every market asset.

## Quality gates

`scripts/verify-android-i18n.py` is the source localization gate. It verifies:

1. all three Android resource sets exist and have identical keys;
2. English remains the unqualified fallback;
3. Android manifest label is resource-backed;
4. AGP automatic locale config generation remains enabled;
5. active Compose presentation files do not regress to hard-coded CJK UI copy.

`scripts/verify-play-store.sh` validates all four Play listing locales and invokes the Android localization gate. `scripts/verify-terminal.sh` treats these files and checks as part of the terminal product contract.

## HarmonyOS

HarmonyOS remains source-complete/pre-release in v2.2. Its product shell must preserve the same language/content separation when localization is activated: platform-native resources, no UI strings in the shared C++ Core, and the same stable Smart Clean reason codes. Real HAP/device qualification remains a separate release gate.
