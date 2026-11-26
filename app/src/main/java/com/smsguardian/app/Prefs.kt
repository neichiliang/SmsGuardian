package com.smsguardian.app

import android.content.Context
import android.content.SharedPreferences

/** 应用配置存储：关键字、Server酱 SendKey、开关、短信去重基线等。 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Server酱 SendKey（sctapi.ftqq.com 获取） */
    var sendKey: String
        get() = sp.getString(KEY_SEND_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_SEND_KEY, value.trim()).apply()

    /** 是否通过 Server酱推送到微信 */
    var enableWechat: Boolean
        get() = sp.getBoolean(KEY_ENABLE_WECHAT, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLE_WECHAT, value).apply()

    /** 命中关键字时是否本地响铃振动通知 */
    var enableSound: Boolean
        get() = sp.getBoolean(KEY_ENABLE_SOUND, true)
        set(value) = sp.edit().putBoolean(KEY_ENABLE_SOUND, value).apply()

    /** 是否允许开机自动启动监控服务 */
    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    /** 关键字列表 */
    fun getKeywords(): Set<String> =
        sp.getStringSet(KEY_KEYWORDS, emptySet())?.toSet() ?: emptySet()

    fun setKeywords(keywords: Collection<String>) {
        val cleaned = keywords.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        sp.edit().putStringSet(KEY_KEYWORDS, cleaned).apply()
    }

    fun addKeyword(keyword: String) {
        val value = keyword.trim()
        if (value.isEmpty()) return
        setKeywords(getKeywords() + value)
    }

    fun removeKeyword(keyword: String) {
        setKeywords(getKeywords() - keyword)
    }

    /** 已处理的最新短信，用于去重，避免重复提醒同一条 */
    var lastSmsId: Long
        get() = sp.getLong(KEY_LAST_SMS_ID, -1L)
        set(value) = sp.edit().putLong(KEY_LAST_SMS_ID, value).apply()

    var lastSmsDate: Long
        get() = sp.getLong(KEY_LAST_SMS_DATE, -1L)
        set(value) = sp.edit().putLong(KEY_LAST_SMS_DATE, value).apply()

    // ===== 诊断信息：用于在设置页直观看到检测器是否在工作 =====

    /** 已检测到的新短信累计数 */
    var detectedCount: Int
        get() = sp.getInt(KEY_DETECTED_COUNT, 0)
        set(value) = sp.edit().putInt(KEY_DETECTED_COUNT, value).apply()

    /** 最近一次检测到新短信的时间戳 */
    var lastDetectTime: Long
        get() = sp.getLong(KEY_LAST_DETECT_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_DETECT_TIME, value).apply()

    /** 最近一次检测结果描述（命中/未命中关键字） */
    var lastDetectResult: String
        get() = sp.getString(KEY_LAST_DETECT_RESULT, "尚未检测到新短信") ?: "尚未检测到新短信"
        set(value) = sp.edit().putString(KEY_LAST_DETECT_RESULT, value).apply()

    /** 最近检测到的短信摘要 */
    var lastDetectPreview: String
        get() = sp.getString(KEY_LAST_DETECT_PREVIEW, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_DETECT_PREVIEW, value).apply()

    /** 去重指纹：防止广播与服务轮询重复提醒同一条短信 */
    var lastFingerprint: String
        get() = sp.getString(KEY_LAST_FINGERPRINT, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_FINGERPRINT, value).apply()

    var lastFingerprintTime: Long
        get() = sp.getLong(KEY_LAST_FINGERPRINT_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_FINGERPRINT_TIME, value).apply()

    companion object {
        private const val PREFS_NAME = "sms_guardian_prefs"
        private const val KEY_SEND_KEY = "send_key"
        private const val KEY_ENABLE_WECHAT = "enable_wechat"
        private const val KEY_ENABLE_SOUND = "enable_sound"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_LAST_SMS_ID = "last_sms_id"
        private const val KEY_LAST_SMS_DATE = "last_sms_date"
        private const val KEY_DETECTED_COUNT = "detected_count"
        private const val KEY_LAST_DETECT_TIME = "last_detect_time"
        private const val KEY_LAST_DETECT_RESULT = "last_detect_result"
        private const val KEY_LAST_DETECT_PREVIEW = "last_detect_preview"
        private const val KEY_LAST_FINGERPRINT = "last_fingerprint"
        private const val KEY_LAST_FINGERPRINT_TIME = "last_fingerprint_time"
    }
}
