package com.smsguardian.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

/**
 * 短信广播接收器——息屏后台提醒的主路径。
 *
 * 系统收到短信时会发出 SMS_RECEIVED 广播，静态注册的接收器能被系统唤醒，
 * 即使应用进程被冻结（需在系统设置中允许本应用「自启动」）。
 * 这里直接从广播的 PDU 中解析发件人和内容，立即触发提醒，不依赖服务或轮询。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs(context).serviceEnabled) return

        val messages = extractMessages(intent)
        if (messages.isEmpty()) {
            Log.w(TAG, "收到短信广播但解析到 0 条消息")
            return
        }

        // 多条短信（长短信分片）按发件人+时间合并
        messages.groupBy { "${it.originatingAddress}|${it.timestampMillis}" }
            .forEach { (_, parts) ->
                // PDU 数组本身即长短信分片的正确顺序，直接拼接
                val body = parts.joinToString("") { it.messageBody ?: "" }
                val address = parts.first().originatingAddress ?: ""
                val timestamp = parts.first().timestampMillis
                SmsProcessor.process(
                    context = context.applicationContext,
                    address = address,
                    body = body,
                    dateMillis = timestamp,
                    source = "broadcast"
                )
            }
    }

    private fun extractMessages(intent: Intent): List<SmsMessage> {
        // 系统传入的 pdus 是 Object[]，元素为 byte[]；用通用写法兼容所有 Android 版本
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return emptyList()
        val format = intent.getStringExtra("format")
        return pdus.mapNotNull { pdu ->
            val bytes = pdu as? ByteArray ?: return@mapNotNull null
            try {
                if (format != null) SmsMessage.createFromPdu(bytes, format)
                else @Suppress("DEPRECATION") SmsMessage.createFromPdu(bytes)
            } catch (e: Exception) {
                Log.w(TAG, "解析短信 PDU 失败：${e.message}")
                null
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
