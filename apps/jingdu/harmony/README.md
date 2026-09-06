# HarmonyOS

Native HarmonyOS Stage/ArkUI shell for Jingdu TXT. All document semantics come from the shared C++ ABI v2 through Node-API.

HarmonyOS owns DocumentViewPicker, charset normalization, ArkUI/lifecycle, Preferences and Core Speech Kit. Long import/search/chapter/repair/export work runs through TaskPool `@Concurrent` tasks; UIAbility-only APIs remain on the UI thread.

Requires the official DevEco/HarmonyOS SDK 6.x environment. Real HAP verification is defined by `.github/workflows/harmony-device.yml` and `scripts/check-harmony.sh`.
