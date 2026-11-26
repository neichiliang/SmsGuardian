package com.smsguardian.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一的短信处理器：广播路径与轮询路径共用，保证提醒逻辑一致。
 * 负责：去重 → 关键字匹配 → 本地响铃振动 → 微信推送 → 诊断记录。
 */
object SmsProcessor {

    private const val TAG = "SmsProcessor"
    private const val DEDUP_WINDOW_MS = 8_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 处理一条短信。
     * @param source 来源标记（broadcast / polling），仅用于日志
     */
    fun process(context: Context, address: String, body: String, dateMillis: Long, source: String) {
        if (body.isBlank()) return
        val prefs = Prefs(context)

        // 去重：广播与轮询可能都拿到同一条短信，8 秒内相同内容只处理一次
        val fingerprint = "$address|$body|$dateMillis"
        val now = System.currentTimeMillis()
        if (fingerprint == prefs.lastFingerprint && now - prefs.lastFingerprintTime < DEDUP_WINDOW_MS) {
            Log.d(TAG, "重复短信，跳过（来源=$source）")
            return
        }
        prefs.lastFingerprint = fingerprint
        prefs.lastFingerprintTime = now

        // 诊断记录
        prefs.detectedCount = prefs.detectedCount + 1
        prefs.lastDetectTime = now
        prefs.lastDetectPreview = "$address：${body.take(40)}"

        val keyword = prefs.getKeywords()
            .firstOrNull { it.isNotBlank() && body.contains(it, ignoreCase = true) }

        if (keyword == null) {
            prefs.lastDetectResult = "检测到新短信，未命中关键字"
            Log.d(TAG, "新短信未命中关键字（来源=$source）")
            return
        }

        prefs.lastDetectResult = "命中「$keyword」→ 已触发提醒（来源=$source）"
        Log.d(TAG, "命中关键字「$keyword」，触发提醒（来源=$source）")

        // 本地响铃振动
        if (prefs.enableSound) {
            try {
                Notifier(context.applicationContext).notifyMatchedSms(address, body)
            } catch (e: Exception) {
                Log.w(TAG, "本地通知失败：${e.message}")
            }
        }

        // 微信推送
        if (prefs.enableWechat && prefs.sendKey.isNotBlank()) {
            val key = prefs.sendKey
            val title = "关键字短信：$address"
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(if (dateMillis > 0) Date(dateMillis) else Date())
            val desp = "### 关键字短信提醒\n\n**命中关键字**：$keyword\n\n" +
                    "**发件人**：$address\n\n**时间**：$timeText\n\n**内容**：\n\n$body"
            scope.launch {
                val result = ServerChanClient.send(key, title, desp)
                if (result.isFailure) {
                    Log.w(TAG, "Server酱推送失败：${result.exceptionOrNull()?.message}")
                }
            }
        }
    }
}
