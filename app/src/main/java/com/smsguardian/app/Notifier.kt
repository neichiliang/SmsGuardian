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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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

        // 删除旧渠道（旧渠道用通知音频流，静音下不响；且系统缓存了旧设置，改代码不生效）
        @Suppress("DEPRECATION")
        manager.deleteNotificationChannel("sms_alert")

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "关键字短信提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "命中关键字的短信会响铃并振动（静音模式下也会提醒）"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            // 关键：用 USAGE_ALARM（闹钟音频流）+ 闹钟铃声，普通静音模式不会静音闹钟流
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
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

        // 双重保险：直接调用振动器，不依赖通知渠道（静音/勿扰下通知振动可能被系统拦截）
        vibrateDirectly()
    }

    /** 直接振动，绕过通知渠道；静音模式下同样生效 */
    @Suppress("DEPRECATION")
    private fun vibrateDirectly() {
        try {
            val pattern = longArrayOf(0, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val v = vm?.defaultVibrator
                if (v?.hasVibrator() == true) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (v?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        v.vibrate(pattern, -1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "直接振动失败：${e.message}")
        }
    }

    companion object {
        private const val TAG = "Notifier"
        // 换 ID 让系统重建渠道并应用闹钟音频流（旧渠道设置被系统缓存，改代码不生效）
        const val CHANNEL_ALERT = "sms_alert_alarm"
        const val CHANNEL_SERVICE = "sms_service"
        const val NOTIFY_ID_SERVICE = 2001
        const val NOTIFY_ID_SMS = 2002
        const val EXTRA_OPEN_SMS_TAB = "open_sms_tab"
    }
}
