package com.smsguardian.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvDiagnostics: TextView
    private lateinit var etKeyword: EditText
    private lateinit var keywordContainer: LinearLayout
    private lateinit var etSendKey: EditText
    private lateinit var cbWechat: CheckBox
    private lateinit var cbSound: CheckBox

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = Prefs(requireContext())

        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        tvDiagnostics = view.findViewById(R.id.tvDiagnostics)
        etKeyword = view.findViewById(R.id.etKeyword)
        keywordContainer = view.findViewById(R.id.keywordContainer)
        etSendKey = view.findViewById(R.id.etSendKey)
        cbWechat = view.findViewById(R.id.cbWechat)
        cbSound = view.findViewById(R.id.cbSound)

        etSendKey.setText(prefs.sendKey)
        cbWechat.isChecked = prefs.enableWechat
        cbSound.isChecked = prefs.enableSound

        cbWechat.setOnCheckedChangeListener { _, checked -> prefs.enableWechat = checked }
        cbSound.setOnCheckedChangeListener { _, checked -> prefs.enableSound = checked }

        view.findViewById<Button>(R.id.btnAddKeyword).setOnClickListener {
            val kw = etKeyword.text.toString().trim()
            if (kw.isEmpty()) {
                toast("请输入关键字")
                return@setOnClickListener
            }
            prefs.addKeyword(kw)
            etKeyword.text.clear()
            renderKeywords()
            toast("已添加：$kw")
        }

        view.findViewById<Button>(R.id.btnSaveKey).setOnClickListener {
            val key = etSendKey.text.toString().trim()
            prefs.sendKey = key
            toast(if (key.isEmpty()) "已清空 SendKey" else "SendKey 已保存")
        }

        view.findViewById<Button>(R.id.btnTestWechat).setOnClickListener {
            val key = etSendKey.text.toString().trim().ifEmpty { prefs.sendKey }
            if (key.isBlank()) {
                toast("请先填写并保存 SendKey")
                return@setOnClickListener
            }
            toast("正在发送测试消息…")
            scope.launch {
                val result = ServerChanClient.send(key, "短信守卫测试", "收到这条消息说明微信通知配置成功。")
                toast(
                    if (result.isSuccess) "已发送，请查看微信"
                    else "推送失败：${result.exceptionOrNull()?.message}"
                )
            }
        }

        view.findViewById<Button>(R.id.btnStartStop).setOnClickListener {
            if (isServiceRunning()) {
                prefs.serviceEnabled = false
                SmsMonitorService.stop(requireContext())
                toast("已停止监控")
            } else {
                if (ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.READ_SMS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    toast("请先授予短信权限")
                    return@setOnClickListener
                }
                prefs.serviceEnabled = true
                SmsMonitorService.start(requireContext())
                toast("已启动监控")
            }
            // 服务停止是异步的，延迟刷新状态，避免按钮点了还显示"运行中"
            view.postDelayed({ refreshStatus() }, 600)
        }

        view.findViewById<Button>(R.id.btnCheckNow).setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                toast("请先授予短信权限")
                return@setOnClickListener
            }
            SmsMonitorService.checkNow(requireContext())
            toast("已触发检测，1 秒后刷新诊断")
            view.postDelayed({ refreshStatus() }, 1000)
        }

        view.findViewById<Button>(R.id.btnPermission).setOnClickListener { openAppDetails() }

        view.findViewById<Button>(R.id.btnBattery).setOnClickListener { requestIgnoreBatteryOptimizations() }

        view.findViewById<Button>(R.id.btnAutoStart).setOnClickListener {
            toast("在应用详情页中找到「自启动 / 允许后台运行」并开启")
            openAppDetails()
        }

        renderKeywords()
        refreshStatus()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (::tvServiceStatus.isInitialized) refreshStatus()
    }

    /** 同时刷新服务状态与诊断信息 */
    private fun refreshStatus() {
        updateServiceStatus()
        val count = prefs.detectedCount
        val time = prefs.lastDetectTime
        val timeText = if (time <= 0) "从未" else
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
        tvDiagnostics.text = buildString {
            append("已检测到新短信：$count 条\n")
            append("最近检测时间：$timeText\n")
            append("检测结果：${prefs.lastDetectResult}\n")
            if (prefs.lastDetectPreview.isNotBlank()) {
                append("最后一条：${prefs.lastDetectPreview}")
            }
        }
    }

    private fun renderKeywords() {
        keywordContainer.removeAllViews()
        val keywords = prefs.getKeywords().sorted()
        if (keywords.isEmpty()) {
            keywordContainer.addView(TextView(requireContext()).apply {
                text = "（尚未添加关键字）"
                textSize = 13f
                setTextColor(Color.parseColor("#999999"))
            })
            return
        }
        keywords.forEach { kw ->
            keywordContainer.addView(TextView(requireContext()).apply {
                text = "$kw   [点击删除]"
                textSize = 14f
                setTextColor(Color.parseColor("#3700B3"))
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    prefs.removeKeyword(kw)
                    renderKeywords()
                }
            })
        }
    }

    private fun isServiceRunning(): Boolean {
        val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == SmsMonitorService::class.java.name }
    }

    private fun updateServiceStatus() {
        val hasPerm = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        tvServiceStatus.text = when {
            !hasPerm -> "状态：未授予短信读取权限，监控无法工作"
            isServiceRunning() -> "状态：监控运行中"
            else -> "状态：监控未运行（回到应用或点击启动）"
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            toast("已忽略电池优化")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("当前系统不支持此设置，请手动关闭电池优化")
        }
    }

    private fun openAppDetails() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("无法打开应用详情")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
