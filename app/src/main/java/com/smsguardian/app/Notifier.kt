package com.smsguardian.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
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

    // 保存正在播放的铃声，便于下次提醒前停掉上一次，避免叠加
    private var alarmRingtone: Ringtone? = null

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // 删除历史渠道：系统缓存了渠道的声音/振动设置，改代码必须换 ID 才生效，
        // 否则覆盖安装后仍是旧配置（这正是上次改了却只有振动没铃声的原因）。
        @Suppress("DEPRECATION")
        manager.deleteNotificationChannel("sms_alert")
        @Suppress("DEPRECATION")
        manager.deleteNotificationChannel("sms_alert_alarm")

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "关键字短信提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "命中关键字的短信会响铃并振动（静音模式下也会提醒）"
            // 声音与振动都不走通知渠道（vivo 等 ROM 会拦截），改由代码直接播放/振动
            setSound(null, null)
            enableVibration(false)
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
        manager.notify(NOTIFY_ID_SMS, builder.build())

        // 直接播放铃声 + 振动，完全绕过通知渠道（vivo 等 ROM 会拦截渠道音效）
        playAlarmSoundDirectly()
        vibrateDirectly()
    }

    /**
     * 直接播放系统闹钟铃声，音频流设为 USAGE_ALARM。
     * 这是闹钟 App 的标准做法：普通静音模式不静音闹钟流，所以静音下也能响。
     */
    private fun playAlarmSoundDirectly() {
        try {
            // 停掉上一次，避免多条短信时铃声叠加
            alarmRingtone?.let { if (it.isPlaying) it.stop() }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: run {
                Log.w(TAG, "无法获取系统铃声")
                return
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.setAudioAttributes(attrs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = false
            }
            alarmRingtone = ringtone
            ringtone.play()
            Log.d(TAG, "直接播放闹钟铃声")
        } catch (e: Exception) {
            Log.w(TAG, "直接播放铃声失败：${e.message}")
        }
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
        // 换 ID 让系统重建渠道（渠道设置被系统缓存，改代码不换 ID 不生效）
        const val CHANNEL_ALERT = "sms_alert_direct"
        const val CHANNEL_SERVICE = "sms_service"
        const val NOTIFY_ID_SERVICE = 2001
        const val NOTIFY_ID_SMS = 2002
        const val EXTRA_OPEN_SMS_TAB = "open_sms_tab"
    }
}
