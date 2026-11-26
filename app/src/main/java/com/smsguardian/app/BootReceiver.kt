package com.smsguardian.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用更新后自动恢复监控服务（部分厂商需在系统设置里额外允许自启动）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).serviceEnabled) return
        SmsMonitorService.start(context)
    }
}
