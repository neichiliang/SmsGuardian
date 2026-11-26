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
 * 常驻前台服务：双保险检测新短信。
 * 1) ContentObserver 监听短信数据库变化（及时，但部分国产 ROM 可能不回调）
 * 2) 定时轮询兜底（每 4 秒查一次收件箱，不依赖 ROM 是否放行监听）
 * 用 _id 判断新旧并批量处理，避免延迟到达/时间错乱/多条短信导致的漏报。
 */
class SmsMonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var notifier: Notifier
    private var observer: SmsObserver? = null
    private var observerThread: HandlerThread? = null
    private var workHandler: Handler? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                checkNewSms()
            } catch (e: Exception) {
                Log.w(TAG, "轮询检测异常：${e.message}")
            }
            workHandler?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

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
        if (intent?.action == ACTION_CHECK) {
            checkNewSms()
            return START_STICKY
        }
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun startMonitoring() {
        if (observerThread != null || !hasReadSmsPermission()) return
        observerThread = HandlerThread("sms-observer").apply { start() }
        workHandler = Handler(observerThread!!.looper)

        // 首次运行：以当前收件箱最大 ID 为基线，避免把历史短信当新短信
        if (prefs.lastSmsId < 0) {
            prefs.lastSmsId = SmsHelper.queryMaxId(contentResolver)
        }

        val obs = SmsObserver(workHandler!!)
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        observer = obs

        // 启动定时轮询兜底
        workHandler?.postDelayed(pollRunnable, 1500)
        Log.d(TAG, "Monitoring started (observer + polling every ${POLL_INTERVAL_MS}ms)")
    }

    private fun stopMonitoring() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        workHandler?.removeCallbacks(pollRunnable)
        workHandler = null
        observerThread?.quitSafely()
        observerThread = null
    }

    private inner class SmsObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // 短信入库可能有多次回调，延迟一点再查；轮询也会兜底
            workHandler?.postDelayed({ checkNewSms() }, 500)
        }
    }

    /** 检测比基线 ID 更新的所有短信（核心逻辑，ContentObserver 与轮询共用） */
    private fun checkNewSms() {
        if (!hasReadSmsPermission()) return
        val baseline = prefs.lastSmsId
        val newSmsList = SmsHelper.queryNewerThanId(contentResolver, baseline)
        if (newSmsList.isEmpty()) return

        var maxId = baseline
        for (sms in newSmsList) {
            if (sms.id <= maxId) continue
            maxId = sms.id
            prefs.lastSmsId = sms.id
            prefs.lastSmsDate = sms.date
            prefs.detectedCount = prefs.detectedCount + 1
            prefs.lastDetectTime = System.currentTimeMillis()
            prefs.lastDetectPreview = "${sms.address}：${sms.body.take(40)}"

            val matchedKeyword = prefs.getKeywords()
                .firstOrNull { it.isNotBlank() && sms.body.contains(it, ignoreCase = true) }

            if (matchedKeyword != null) {
                prefs.lastDetectResult = "命中关键字「$matchedKeyword」→ 已触发提醒"
                handleMatchedSms(sms.address, sms.body, matchedKeyword)
            } else {
                prefs.lastDetectResult = "检测到新短信，但未命中关键字"
            }
        }
        Log.d(TAG, "检测到 ${newSmsList.size} 条新短信，基线更新到 $maxId")
    }

    /** 命中关键字 → 本地响铃振动 + 可选的微信推送 */
    private fun handleMatchedSms(address: String, body: String, keyword: String) {
        if (prefs.enableSound) {
            notifier.notifyMatchedSms(address, body)
        }

        if (prefs.enableWechat && prefs.sendKey.isNotBlank()) {
            val key = prefs.sendKey
            val title = "关键字短信：$address"
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val desp = "### 关键字短信提醒\n\n**命中关键字**：$keyword\n\n**发件人**：$address\n\n**时间**：$time\n\n**内容**：\n\n$body"
            scope.launch {
                val result = ServerChanClient.send(key, title, desp)
                if (result.isFailure) {
                    Log.w(TAG, "Server酱推送失败：${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmsMonitorService"
        private const val POLL_INTERVAL_MS = 4000L
        const val ACTION_STOP = "com.smsguardian.app.STOP"
        const val ACTION_CHECK = "com.smsguardian.app.CHECK"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SmsMonitorService::class.java))
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsMonitorService::class.java).apply { action = ACTION_STOP }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "stopService 失败：${e.message}")
            }
        }

        /** 手动触发一次检测（用于诊断） */
        fun checkNow(context: Context) {
            val intent = Intent(context, SmsMonitorService::class.java).apply { action = ACTION_CHECK }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
