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

    companion object {
        private const val PREFS_NAME = "sms_guardian_prefs"
        private const val KEY_SEND_KEY = "send_key"
        private const val KEY_ENABLE_WECHAT = "enable_wechat"
        private const val KEY_ENABLE_SOUND = "enable_sound"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_LAST_SMS_ID = "last_sms_id"
        private const val KEY_LAST_SMS_DATE = "last_sms_date"
    }
}
