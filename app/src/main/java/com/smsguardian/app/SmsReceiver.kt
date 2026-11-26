package com.smsguardian.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * 短信广播兜底：若系统/厂商允许本应用收到 SMS_RECEIVED，则确保监控服务处于运行状态。
 * 实际的新短信识别与去重统一由 SmsMonitorService 的 ContentObserver 完成，避免重复提醒。
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs(context).serviceEnabled) return
        SmsMonitorService.start(context)
    }
}
