package com.smsguardian.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class SmsListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: SmsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_sms_list, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        adapter = SmsAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        swipeRefresh.setOnRefreshListener { loadSms() }
        loadSms()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (::recyclerView.isInitialized) loadSms()
    }

    private fun loadSms() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "请先授予短信读取权限", Toast.LENGTH_SHORT).show()
            swipeRefresh.isRefreshing = false
            return
        }
        try {
            adapter.submitList(SmsHelper.queryInbox(requireContext().contentResolver))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "读取短信失败：${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            swipeRefresh.isRefreshing = false
        }
    }
}
