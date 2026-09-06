# 净读 TXT

**离线、隐私优先的本地 TXT 小说阅读器。** 不追求最多格式，而是把乱码 TXT 打开正确、把广告/水印/网站尾巴找出来并安全净化，让长篇小说读得舒服、稳定、快速。

> Open messy TXT correctly, clean distracting text locally, and keep reading comfortably for hours.

## Product

- **中文 TXT 深度支持**：AUTO + UTF-8 / UTF-16 / GB18030 / GBK / Big5，可从私有 source 副本重新解码。
- **简繁内容同等对待**：Smart Clean 同时识别简体/繁体常见推广水印；搜索会尝试安全的一对一简繁字形变体；未手选 TTS voice 时按正文判断 `zh-CN / zh-TW / zh-HK / English`。
- **三语 UI**：Android 原生支持简体中文、繁體中文和 English；UI 语言与书籍正文语言完全解耦，其他系统语言回退英文。
- **智能净读**：共享 C++ Core 本地扫描高频重复、网址/域名和常见推广水印；Free 可完整查看候选，Pro 可一键应用。
- **安全规则**：Free 精确规则；Pro whole-line `*` 通配、全局规则库、推荐简繁中文网文规则、规则导入/导出。
- **大文件可信**：与文件规模相关的工作不跑 UI thread；不可变 revision + `.jdx` 稀疏索引缓存；10/100/300 MiB 为正式资格尺寸。
- **长期阅读**：书架/进度、搜索、Smart TOC、revision-safe 书签/高亮/笔记、Reader 排版与选择、TTS、自动翻页/滚动、睡眠定时、阅读统计、批量 TXT 导入。
- **本地资产**：Pro 可选择系统离线 TTS voice，并导出/恢复 Reader 设置、全局规则、标注、收藏/标签、exact-revision 进度、阅读 session/pace 与 Smart Clean 指纹反馈；备份声明 `containsBookText=false`，不含 source/normalized/Clean 正文。
- **隐私**：无账号、无广告/analytics SDK、Android manifest 不直接申请 `INTERNET`，TXT 不上传，源文件永不修改。

## Free / Pro

Free 是完整阅读器，不锁搜索/目录/书签/基础排版/TTS。`jingdu_pro_lifetime` 是 Google Play 一次性买断，主要销售智能净读自动化、通配/全局规则、离线 voice 选择和可迁移的本地用户资产；当前没有订阅。

详见 `docs/PRODUCT.md` 与 `docs/GROWTH_MONETIZATION.md`。

## Localization

- Android UI：`zh-Hans` / `zh-Hant` / `en-US`，英文是未匹配系统语言的最终 fallback。
- Google Play：`zh-CN` / `zh-TW` / `zh-HK` / `en-US` 默认 listing、Custom Listing 规格与截图制作 brief。
- UI locale 不驱动正文算法；编码、Smart Clean、搜索和 TTS 根据文本本身工作。
- 共享 C++ Core 只输出语言无关 Smart Clean reason code，不承载 UI 翻译。
- CI 强制三套资源 key/格式占位符一致，并禁止主要 Android 展示/控制层重新写死中文文案。

完整契约见 `docs/LOCALIZATION.md`。

## Architecture

```text
Android Compose / Kotlin platform shell -- JNI -----+
                                                     +-- C ABI v2 -- C++17 Jingdu Core
HarmonyOS ArkUI / ArkTS platform shell ----- NAPI --+
```

- `platform/text/native/` — 唯一跨平台文本/算法实现，包括编码、索引、搜索、目录、Repair、Smart Clean。
- `apps/jingdu/android/` — Reader Compose 产品壳、平台生命周期/TTS/Google Play Billing & Review、JNI。
- `apps/jingdu/harmony/` — HarmonyOS Stage/ArkUI + Node-API shell（当前 source-complete/pre-release）。
- `fastlane/metadata/android/` — 四地区默认 Play 商店元数据。
- `store/play/` — keyword-targeted Custom Listing 规格和多语言截图制作 brief。
- `docs/` — 产品、商业化、UX、Localization、架构、ABI、性能、测试、发布事实源与 production readiness 证据合同。
- `scripts/` — Native/Android i18n/terminal/Play metadata/Reader/source provenance CI 门禁。

## Product invariants

- `book id == SHA256(source bytes)`；
- normalized/clean 是不可变 content-addressed revision；
- `.jdx` 只是可丢弃性能 cache，不是身份；
- post-normalization read/search/chapter/repair/speech/Smart Clean 语义来自同一个 C++ Core；
- UI 语言与正文语言解耦，Core 不输出本地化展示文案；
- 原文 progress/bookmark/annotation 不接收 Clean offset；
- portable progress 只在 exact `normalizedSha256` 匹配时恢复；
- Smart Clean 只给建议，用户显式 Apply 才改变派生 Clean 输出；
- 商业化不允许把基本阅读能力移到 Pro；
- 不允许兼容 Core、旧 ABI bridge、prototype production root、提交 release binary 或 production signing material；`config/signing/android-debug.keystore` 是当前 GitHub release 阶段明确允许的公开 debug 签名例外。

## Gates

```bash
./scripts/check-native.sh
cd apps/jingdu/android && ./gradlew --no-daemon --warning-mode all androidCheck
cd ../..
./scripts/verify-android-i18n.py
./scripts/verify-play-store.sh
./scripts/verify-terminal.sh
```

Hosted CI 运行 `native-core / android / android-performance / play-store-contract / harmony-contract / terminal-contract`。Harmony 真 HAP 仍依赖官方 HarmonyOS/DevEco toolchain 与 `self-hosted,harmonyos` runner，不阻断 Android-only 2.x source merge/release。

当前 Android GitHub release 阶段以 hosted source gates + immutable source tag + Android 通用 debug 签名 APK 为发布标准。`main` branch protection / repository ruleset **不是当前阶段发布门禁**。发布 APK 由 immutable tag 构建，使用仓库稳定 `androiddebugkey`，并附带 APK SHA-256 与 signing-certificate SHA-256。

这不等于 Google Play production。生产 AAB/Play 签名、真实 Android 设备矩阵、License Tester、listing 和 staged rollout 的未来外部证据合同见 `docs/PRODUCTION_READINESS.md`。

## Google Play discovery / commerce

- 简中：`净读 - TXT 小说阅读器`；繁中：`淨讀 - TXT 小說閱讀器`；英文：`Jingdu - Offline TXT Reader`。
- Custom Listing 分为 TXT Reader、乱码编码、Smart Clean、本地小说 4 个搜索意图，并提供简中/繁中/英文素材规范。
- Lifetime Pro 商品：`jingdu_pro_lifetime`，Play Console 商品名/说明同样按 `zh-CN / zh-TW / zh-HK / en-US` 本地化。
- Play Console 实际商品激活、价格实验、listing 上传和 staged rollout 按 `docs/PLAY_CONSOLE_SETUP.md` / `docs/PRODUCTION_READINESS.md` / `docs/RELEASE.md` 执行；当前源码工具不能代替真实 Console 发布操作。

## Source provenance / current Android release

Android 2.3.x source releases 由 `publish-source-release` 在六项 hosted gate 全绿后的 `main` tail 创建。新的 source tag 使用 annotated tag object，把 exact gated `main` SHA 与永久 `releases/source/vX.Y.Z.md` 的 SHA-256 绑定；publisher 从不移动已有 tag。

随后 `publish-android-debug-apk` 从该 immutable source tag 构建并发布 `Jingdu-vX.Y.Z-debug-signed.apk`。它使用仓库稳定 Android debug key (`androiddebugkey`)；`SHA256SUMS.txt` 与 `SIGNING-CERT-SHA256.txt` 同步发布。这个 debug-signed APK 是**当前阶段正式可安装的 Android GitHub release artifact**。

当前 GitHub release 不要求 `main` protection；同时它也不声称 Google Play production signing/rollout 或 HarmonyOS 真机资格。

## Documentation

推荐顺序：
1. `PRODUCT.md` / `PRODUCT_REQUIREMENTS.md`
2. `GROWTH_MONETIZATION.md` / `UX.md` / `LOCALIZATION.md`
3. `ARCHITECTURE.md` / `CORE_CONTRACT.md` / `DATA_MODEL.md`
4. `ENCODING.md` / `PERFORMANCE.md` / `PERFORMANCE_SLO.md` / `TESTING.md` / `DEVICE_MATRIX.md`
5. `PLAY_CONSOLE_SETUP.md` / `PRODUCTION_READINESS.md` / `QUALITY_GATES.md` / `RELEASE.md`

`main` 保持可发布源码；APK/AAB/HAP、mapping/symbol package 不提交仓库。当前 GitHub release 所需的公开 Android debug keystore 是唯一明确的签名材料例外；production/upload key 不进入源码仓库。
