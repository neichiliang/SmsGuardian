package com.smsguardian.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
                val sorted = parts.sortedBy { it.messageNumber }
                val body = sorted.joinToString("") { it.messageBody ?: "" }
                val address = sorted.first().originatingAddress ?: ""
                val timestamp = sorted.first().timestampMillis
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
        val pdus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("pdus", Array<ByteArray>::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("pdus") as? Array<ByteArray>
        } ?: return emptyList()

        val format = intent.getStringExtra("format")
        return pdus.mapNotNull { pdu ->
            try {
                if (format != null) SmsMessage.createFromPdu(pdu, format)
                else @Suppress("DEPRECATION") SmsMessage.createFromPdu(pdu)
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
