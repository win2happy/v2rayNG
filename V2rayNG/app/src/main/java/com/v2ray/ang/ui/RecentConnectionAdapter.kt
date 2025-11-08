package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.dto.RecentConnection

class RecentConnectionAdapter(
    private val onItemClick: (RecentConnection) -> Unit
) : RecyclerView.Adapter<RecentConnectionAdapter.ViewHolder>() {

    private val items = mutableListOf<RecentConnection>()

    fun updateData(newItems: List<RecentConnection>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_connection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivStatus: ImageView = itemView.findViewById(R.id.iv_node_status)
        private val tvName: TextView = itemView.findViewById(R.id.tv_node_name)
        private val tvAddress: TextView = itemView.findViewById(R.id.tv_node_address)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_node_time)

        fun bind(item: RecentConnection) {
            tvName.text = item.name
            tvAddress.text = "${item.server}:${item.port}"
            tvTime.text = formatTimestamp(item.timestamp)

            val statusColor = if (item.isActive) {
                ContextCompat.getColor(itemView.context, R.color.color_fab_active)
            } else {
                ContextCompat.getColor(itemView.context, R.color.color_fab_inactive)
            }
            ivStatus.setColorFilter(statusColor)

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val minutes = diff / (1000 * 60)
            val hours = diff / (1000 * 60 * 60)
            val days = diff / (1000 * 60 * 60 * 24)

            return when {
                minutes < 1 -> itemView.context.getString(R.string.home_time_just_now)
                minutes < 60 -> itemView.context.getString(R.string.home_time_minutes_ago, minutes)
                hours < 24 -> itemView.context.getString(R.string.home_time_hours_ago, hours)
                else -> itemView.context.getString(R.string.home_time_days_ago, days)
            }
        }
    }
}
