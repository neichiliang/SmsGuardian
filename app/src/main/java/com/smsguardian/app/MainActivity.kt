package com.smsguardian.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) SmsListFragment() else SettingsFragment()
        }

        bottomNav.setOnItemSelectedListener { item ->
            viewPager.currentItem = if (item.itemId == R.id.nav_settings) 1 else 0
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = if (position == 0) R.id.nav_sms else R.id.nav_settings
            }
        })

        requestPermissionsIfNeeded()

        if (intent.getBooleanExtra(Notifier.EXTRA_OPEN_SMS_TAB, false)) {
            viewPager.currentItem = 0
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs(this).serviceEnabled &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            SmsMonitorService.start(this)
        }
    }

    companion object {
        private const val REQ_PERMISSIONS = 1001
    }
}
