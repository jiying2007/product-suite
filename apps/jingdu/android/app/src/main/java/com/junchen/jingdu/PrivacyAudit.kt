package com.junchen.jingdu

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import org.json.JSONArray
import org.json.JSONObject

data class PrivacyAuditResult(
    val networkPermissionAbsent: Boolean,
    val automaticBackupDisabled: Boolean,
    val bookTextUploadCapability: Boolean,
    val analyticsSdkPresent: Boolean,
    val adsSdkPresent: Boolean,
    val folderRoots: Int,
    val libraryBooks: Int,
    val feedbackKeep: Int,
    val feedbackDelete: Int,
    val feedbackProtect: Int,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val manufacturer: String = Build.MANUFACTURER.orEmpty(),
    val model: String = Build.MODEL.orEmpty(),
    val localeTag: String = "",
    val usablePrivateStorageBytes: Long = 0L,
    val memoryClassMb: Int = 0,
    val recentErrors: List<ProductErrorEvent> = emptyList(),
)

/** Runtime-verifiable privacy facts. The generated report contains counts/configuration only. */
internal object PrivacyAudit {
    private val knownAnalyticsClasses = listOf(
        "com.google.firebase.analytics.FirebaseAnalytics",
        "com.microsoft.appcenter.AppCenter",
        "com.umeng.analytics.MobclickAgent",
        "com.sensorsdata.analytics.android.sdk.SensorsDataAPI",
        "com.growingio.android.sdk.collection.GrowingIO",
        "com.flurry.android.FlurryAgent",
    )
    private val knownAdsClasses = listOf(
        "com.google.android.gms.ads.AdView",
        "com.facebook.ads.AdView",
        "com.bytedance.sdk.openadsdk.TTAdSdk",
        "com.qq.e.comm.managers.GDTAdSdk",
    )

    fun inspect(
        context: Context,
        libraryBooks: Int,
        folderRoots: Int,
        feedback: SmartCleanFeedbackStore.FeedbackSummary,
    ): PrivacyAuditResult {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toSet().orEmpty()
        val networkPermissionAbsent = Manifest.permission.INTERNET !in permissions
        val automaticBackupDisabled = context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0
        val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 0
        val storageManager = context.getSystemService(StorageManager::class.java)
        val allocatableBytes = runCatching {
            storageManager?.getAllocatableBytes(StorageManager.UUID_DEFAULT) ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)
        return PrivacyAuditResult(
            networkPermissionAbsent = networkPermissionAbsent,
            automaticBackupDisabled = automaticBackupDisabled,
            // Conservative runtime interpretation: without INTERNET permission this APK has no
            // direct network transport for uploading private book text.
            bookTextUploadCapability = !networkPermissionAbsent,
            analyticsSdkPresent = knownAnalyticsClasses.any { classPresent(context, it) },
            adsSdkPresent = knownAdsClasses.any { classPresent(context, it) },
            folderRoots = folderRoots,
            libraryBooks = libraryBooks,
            feedbackKeep = feedback.keep,
            feedbackDelete = feedback.delete,
            feedbackProtect = feedback.protect,
            localeTag = context.resources.configuration.locales[0]?.toLanguageTag().orEmpty(),
            usablePrivateStorageBytes = allocatableBytes,
            memoryClassMb = memoryClass,
            recentErrors = ProductErrorLog(context).recent(),
        )
    }

    fun toJson(context: Context, result: PrivacyAuditResult): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val errors = JSONArray()
        result.recentErrors.forEach { event ->
            errors.put(
                JSONObject()
                    .put("code", event.code.name)
                    .put("operation", event.operation)
                    .put("timestampMs", event.timestampMs),
            )
        }
        return JSONObject()
            .put("schema", 3)
            .put("type", "jingdu-local-privacy-audit")
            .put("package", context.packageName)
            .put("versionName", packageInfo.versionName ?: "")
            .put("versionCode", versionCode)
            .put("networkPermissionAbsent", result.networkPermissionAbsent)
            .put("automaticBackupDisabled", result.automaticBackupDisabled)
            .put("bookTextUploadCapability", result.bookTextUploadCapability)
            .put("analyticsSdkPresent", result.analyticsSdkPresent)
            .put("adsSdkPresent", result.adsSdkPresent)
            .put("libraryBooks", result.libraryBooks)
            .put("folderRoots", result.folderRoots)
            .put("smartCleanFeedback", JSONObject()
                .put("keep", result.feedbackKeep)
                .put("delete", result.feedbackDelete)
                .put("protect", result.feedbackProtect))
            .put("device", JSONObject()
                .put("sdkInt", result.sdkInt)
                .put("manufacturer", result.manufacturer)
                .put("model", result.model)
                .put("locale", result.localeTag)
                .put("memoryClassMb", result.memoryClassMb)
                .put("usablePrivateStorageBytes", result.usablePrivateStorageBytes))
            .put("recentErrors", errors)
            .put("diagnosticPolicy", JSONObject()
                .put("boundedErrorEvents", ProductErrorLog.MAX_EVENTS)
                .put("containsPaths", false)
                .put("containsUris", false)
                .put("containsSearchQueries", false)
                .put("containsPurchaseTokens", false))
            .put("containsBookText", false)
            .toString(2)
    }

    private fun classPresent(context: Context, className: String): Boolean = runCatching {
        Class.forName(className, false, context.classLoader)
        true
    }.getOrDefault(false)
}
