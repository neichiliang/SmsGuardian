package com.smsguardian.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsAdapter(private var items: List<SmsItem>) : RecyclerView.Adapter<SmsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val address: TextView = view.findViewById(R.id.tvAddress)
        val body: TextView = view.findViewById(R.id.tvBody)
        val time: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.address.text = item.address.ifBlank { "(未知号码)" }
        holder.body.text = item.body.ifBlank { "(无内容)" }
        holder.time.text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.date))
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<SmsItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
