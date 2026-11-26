package com.smsguardian.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知监听服务——息屏后台实时提醒的主路径。
 *
 * 用户授予「通知使用权」后，系统会在通知到达时主动绑定并回调本服务，
 * 即使 App 进程被冻结（vivo/OPPO 等国产 ROM），只要系统短信 App 收到短信会弹通知，
 * 这里就能实时捕获。相比静态短信广播（被 ROM 限制）和前台轮询（会被冻结），
 * 通知监听是国产 ROM 上最可靠的唤醒通道。
 *
 * 需在系统设置中授予「通知使用权」才会生效，设置页有跳转按钮。
 */
class SmsNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "通知监听服务已连接")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "通知监听服务断开，尝试重新绑定")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, SmsNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            handleNotification(sbn)
        } catch (e: Exception) {
            Log.w(TAG, "处理通知异常：${e.message}")
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        // 用户关闭监控时，通知监听也不触发
        if (!Prefs(this).serviceEnabled) return
        // 跳过本 App 自己发出的通知，避免自触发循环
        if (sbn.packageName == packageName) return
        // 只处理默认短信 App 的通知，避免把微信/QQ 等通知误当作短信
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        if (smsPackage != null && sbn.packageName != smsPackage) return

        val extras = sbn.notification.extras ?: return
        val (address, body) = extractSmsText(extras) ?: return
        if (body.isBlank()) return

        val timestamp = sbn.postTime
        if (timestamp > 0) {
            SmsProcessor.process(
                context = applicationContext,
                address = address,
                body = body,
                dateMillis = timestamp,
                source = "notification"
            )
            Log.d(TAG, "通知捕获短信：${address.take(20)} / ${body.take(20)}")
        }
    }

    /**
     * 从通知 extras 中提取发件人和正文。
     * 兼容两种通知样式：
     * 1) MessagingStyle（现代短信 App）：正文在 EXTRA_MESSAGES 最后一条消息里
     * 2) 普通样式：正文在 EXTRA_BIG_TEXT / EXTRA_TEXT，发件人在 EXTRA_TITLE
     */
    private fun extractSmsText(extras: Bundle): Pair<String, String>? {
        // MessagingStyle：AOSP 写入消息 Bundle 的键为 "text" / "sender"（非公开常量，用字面量）
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!messages.isNullOrEmpty()) {
            val last = messages.lastOrNull() as? Bundle
            // 部分 ROM 会把键加 android. 前缀，优先无前缀再兜底
            val text = (last?.getCharSequence("text") ?: last?.getCharSequence("android.text"))
                ?.toString()
            if (!text.isNullOrBlank()) {
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val sender = (last?.getCharSequence("sender") ?: last?.getCharSequence("android.sender"))
                    ?.toString()
                val address = if (!sender.isNullOrBlank() && sender != title) sender else title
                return address to text
            }
        }
        // 普通样式
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return null
        val address = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        return address to body
    }

    companion object {
        private const val TAG = "SmsNotificationListener"

        /** 是否已授予通知使用权 */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val component = ComponentName(context, SmsNotificationListener::class.java)
            return flat.split(":").any { entry ->
                entry == component.flattenToString() ||
                    entry == component.flattenToShortString() ||
                    (entry.contains(context.packageName) && entry.contains("SmsNotificationListener"))
            }
        }

        /** 打开系统「通知使用权」授权页 */
        fun openSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "打开通知使用权设置失败：${e.message}")
            }
        }

        /** 请求系统重新绑定服务（授权后调用，让服务立即生效） */
        fun requestRebindIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isEnabled(context)) {
                try {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, SmsNotificationListener::class.java)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "重新绑定失败：${e.message}")
                }
            }
        }
    }
}
