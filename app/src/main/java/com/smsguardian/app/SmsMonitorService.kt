package com.smsguardian.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 常驻前台服务：通过 ContentObserver 监听短信数据库变化。
 * 相比 SMS_RECEIVED 广播，此方式无需把本应用设为默认短信 App（Android 4.4+ 限制），
 * 也不会影响你正在使用的短信应用。
 */
class SmsMonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var notifier: Notifier
    private var observer: SmsObserver? = null
    private var observerThread: HandlerThread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        notifier = Notifier(this)
        startForeground(Notifier.NOTIFY_ID_SERVICE, notifier.serviceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        registerObserver()
        // 被系统杀掉后尝试重建
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun registerObserver() {
        if (observer != null || !hasReadSmsPermission()) return
        observerThread = HandlerThread("sms-observer").apply { start() }
        val obs = SmsObserver(Handler(observerThread!!.looper))
        obs.initBaseline()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        observer = obs
        // 主动补检一次，覆盖服务重启/开机期间到达的短信
        obs.scheduleInitialCheck()
        Log.d(TAG, "SMS observer registered")
    }

    private fun unregisterObserver() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        observerThread?.quitSafely()
        observerThread = null
    }

    private inner class SmsObserver(handler: Handler) : ContentObserver(handler) {

        private val workHandler = handler
        private var lastId = -1L
        private var lastDate = -1L

        /** 启动时以当前最新短信为基线，避免把历史短信误判为新短信 */
        fun initBaseline() {
            lastId = prefs.lastSmsId
            lastDate = prefs.lastSmsDate
            if (lastId <= 0) {
                SmsHelper.queryLatestInbox(contentResolver)?.let { sms ->
                    lastId = sms.id
                    lastDate = sms.date
                    prefs.lastSmsId = sms.id
                    prefs.lastSmsDate = sms.date
                }
            }
        }

        override fun onChange(selfChange: Boolean) {
            // 短信入库过程中可能连续多次回调，延迟读取以拿到完整内容
            workHandler.postDelayed({ checkNewSms() }, 700)
        }

        fun scheduleInitialCheck() {
            workHandler.postDelayed({ checkNewSms() }, 1200)
        }

        private fun checkNewSms() {
            if (!hasReadSmsPermission()) return
            val sms = SmsHelper.queryLatestInbox(contentResolver) ?: return
            if (sms.id == lastId && sms.date == lastDate) return
            if (sms.date < lastDate) return

            lastId = sms.id
            lastDate = sms.date
            prefs.lastSmsId = sms.id
            prefs.lastSmsDate = sms.date

            handleMatchedSms(sms.address, sms.body)
        }
    }

    /** 关键字匹配 → 本地响铃振动 + 可选的微信推送 */
    private fun handleMatchedSms(address: String, body: String) {
        val matched = prefs.getKeywords().any { it.isNotBlank() && body.contains(it, ignoreCase = true) }
        if (!matched) return

        if (prefs.enableSound) {
            notifier.notifyMatchedSms(address, body)
        }

        if (prefs.enableWechat && prefs.sendKey.isNotBlank()) {
            val key = prefs.sendKey
            val title = "关键字短信：$address"
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val desp = "### 关键字短信提醒\n\n**发件人**：$address\n\n**时间**：$time\n\n**内容**：\n\n$body"
            scope.launch {
                val result = ServerChanClient.send(key, title, desp)
                if (result.isFailure) {
                    Log.w(TAG, "Server酱推送失败：${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterObserver()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmsMonitorService"
        const val ACTION_STOP = "com.smsguardian.app.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SmsMonitorService::class.java))
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsMonitorService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
