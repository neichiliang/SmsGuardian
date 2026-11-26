package com.smsguardian.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

/** 通知封装：常驻服务通知 + 关键字短信响铃振动提醒。 */
class Notifier(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "关键字短信提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "命中关键字的短信会响铃并振动提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
        }
        manager.createNotificationChannel(alert)

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "短信监控服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "保持后台监听运行，可点击返回应用" }
        manager.createNotificationChannel(service)
    }

    /** 前台服务常驻通知 */
    fun serviceNotification(): Notification {
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_SERVICE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.notify_service_title))
            .setContentText(context.getString(R.string.notify_service_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    /** 命中关键字的本地提醒（响铃 + 振动 + 通知栏） */
    fun notifyMatchedSms(address: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_SMS_TAB, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) CHANNEL_ALERT else CHANNEL_SERVICE
        )
        builder.setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("关键字短信：$address")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        manager.notify(NOTIFY_ID_SMS, builder.build())
    }

    companion object {
        const val CHANNEL_ALERT = "sms_alert"
        const val CHANNEL_SERVICE = "sms_service"
        const val NOTIFY_ID_SERVICE = 2001
        const val NOTIFY_ID_SMS = 2002
        const val EXTRA_OPEN_SMS_TAB = "open_sms_tab"
    }
}
