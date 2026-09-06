package com.junchen.jingdu

internal data class BuiltinCleanRule(
    val id: String,
    val locale: String,
    val confidence: Int,
    val rule: RepairRule,
)

/**
 * Versioned offline signature pack. Entries stay deterministic and reviewable; updates ship with
 * the application instead of downloading remote rules or sending book text anywhere.
 */
internal object BuiltinCleanRules {
    const val PACK_VERSION = 4

    val rules = listOf(
        BuiltinCleanRule("zh-cn-latest-url", "zh-Hans", 96, RepairRule("*最新网址*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-backup-url", "zh-Hans", 94, RepairRule("*备用网址*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-bookmark-site", "zh-Hans", 95, RepairRule("*请收藏本站*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-remember-site", "zh-Hans", 94, RepairRule("*请记住本站*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-remember-url", "zh-Hans", 94, RepairRule("*请记住网址*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-mobile-site", "zh-Hans", 93, RepairRule("*手机用户请访问*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-follow-account", "zh-Hans", 92, RepairRule("*关注公众号*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-download-app", "zh-Hans", 90, RepairRule("*下载APP*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-app-download", "zh-Hans", 90, RepairRule("*APP下载*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-domain-reminder", "zh-Hans", 92, RepairRule("*牢记本站域名*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-cn-release-page", "zh-Hans", 93, RepairRule("*最新网址发布页*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-latest-url", "zh-Hant", 96, RepairRule("*最新網址*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-backup-url", "zh-Hant", 94, RepairRule("*備用網址*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-bookmark-site", "zh-Hant", 95, RepairRule("*請收藏本站*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-remember-site", "zh-Hant", 94, RepairRule("*請記住本站*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-remember-url", "zh-Hant", 94, RepairRule("*請記住網址*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-mobile-site", "zh-Hant", 93, RepairRule("*手機用戶請訪問*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-follow-account", "zh-Hant", 92, RepairRule("*關注公眾號*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-download-app", "zh-Hant", 90, RepairRule("*下載APP*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-app-download", "zh-Hant", 90, RepairRule("*APP下載*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-domain-reminder", "zh-Hant", 92, RepairRule("*請牢記網域*", "", RepairRuleMode.LINE_GLOB)),
        BuiltinCleanRule("zh-tw-release-page", "zh-Hant", 93, RepairRule("*最新網址發布頁*", "", RepairRuleMode.LINE_GLOB)),
    )
}
