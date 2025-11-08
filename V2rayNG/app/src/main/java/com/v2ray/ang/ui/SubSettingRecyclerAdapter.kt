package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.databinding.ItemRecyclerSubSettingBinding
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubSettingRecyclerAdapter(val activity: SubSettingActivity) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>(), ItemTouchHelperAdapter {

    private var mActivity: SubSettingActivity = activity

    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_sub_method)
    }

    override fun getItemCount() = mActivity.subscriptions.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subId = mActivity.subscriptions[position].first
        val subItem = mActivity.subscriptions[position].second
        holder.itemSubSettingBinding.tvName.text = subItem.remarks
        holder.itemSubSettingBinding.tvUrl.text = subItem.url
        holder.itemSubSettingBinding.chkEnable.isChecked = subItem.enabled
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        // Display node count and last update time
        val nodeCount = getNodeCount(subId)
        val lastUpdateInfo = getLastUpdateInfo(subItem.lastUpdated)
        holder.itemSubSettingBinding.tvInfo.text = mActivity.getString(
            R.string.subscription_info_format,
            nodeCount,
            lastUpdateInfo
        )

        holder.itemSubSettingBinding.layoutUpdate.setOnClickListener {
            updateSubscription(subId, subItem, position)
        }

        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
            mActivity.startActivity(
                Intent(mActivity, SubEditActivity::class.java)
                    .putExtra("subId", subId)
            )
        }

        holder.itemSubSettingBinding.layoutRemove.setOnClickListener {
            removeSubscription(subId, position)
        }

        holder.itemSubSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            subItem.enabled = isChecked
            MmkvManager.encodeSubscription(subId, subItem)

        }

        if (TextUtils.isEmpty(subItem.url)) {
            holder.itemSubSettingBinding.layoutUrl.visibility = View.GONE
            holder.itemSubSettingBinding.layoutUpdate.visibility = View.INVISIBLE
            holder.itemSubSettingBinding.layoutShare.visibility = View.INVISIBLE
            holder.itemSubSettingBinding.chkEnable.visibility = View.INVISIBLE
        } else {
            holder.itemSubSettingBinding.layoutUrl.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutUpdate.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.visibility = View.VISIBLE
            holder.itemSubSettingBinding.chkEnable.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.setOnClickListener {
                AlertDialog.Builder(mActivity)
                    .setItems(share_method.asList().toTypedArray()) { _, i ->
                        try {
                            when (i) {
                                0 -> {
                                    val ivBinding =
                                        ItemQrcodeBinding.inflate(LayoutInflater.from(mActivity))
                                    ivBinding.ivQcode.setImageBitmap(
                                        QRCodeDecoder.createQRCode(
                                            subItem.url

                                        )
                                    )
                                    AlertDialog.Builder(mActivity).setView(ivBinding.root).show()
                                }

                                1 -> {
                                    Utils.setClipboard(mActivity, subItem.url)
                                }

                                else -> mActivity.toast("else")
                            }
                        } catch (e: Exception) {
                            Log.e(AppConfig.TAG, "Share subscription failed", e)
                        }
                    }.show()
            }
        }
    }

    private fun removeSubscription(subId: String, position: Int) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE) == true) {
            AlertDialog.Builder(mActivity).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    removeSubscriptionSub(subId, position)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do noting
                }
                .show()
        } else {
            removeSubscriptionSub(subId, position)
        }
    }
    
    private fun updateSubscription(subId: String, subItem: SubscriptionItem, position: Int) {
        if (TextUtils.isEmpty(subItem.url)) {
            return
        }

        mActivity.toast(R.string.title_sub_update)
        
        mActivity.lifecycleScope.launch(Dispatchers.IO) {
            // Re-read the subscription item to ensure we have the latest data
            val currentSubItem = MmkvManager.decodeSubscription(subId) ?: subItem
            val count = AngConfigManager.updateConfigViaSub(Pair(subId, currentSubItem))
            kotlinx.coroutines.delay(500L)
            launch(Dispatchers.Main) {
                if (count > 0) {
                    mActivity.toast(mActivity.getString(R.string.toast_success) + " ($count)")
                    // Refresh the data to update node count and last update time
                    mActivity.refreshData()
                } else {
                    mActivity.toast(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeSubscriptionSub(subId: String, position: Int) {
        mActivity.lifecycleScope.launch(Dispatchers.IO) {
            MmkvManager.removeSubscription(subId)
            launch(Dispatchers.Main) {
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, mActivity.subscriptions.size)
                mActivity.refreshData()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerSubSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) :
        BaseViewHolder(itemSubSettingBinding.root), ItemTouchHelperViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        SettingsManager.swapSubscriptions(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        mActivity.refreshData()
    }

    override fun onItemDismiss(position: Int) {
    }
    
    /**
     * Get the count of nodes for a subscription
     */
    private fun getNodeCount(subId: String): Int {
        val serverList = MmkvManager.decodeServerList()
        var count = 0
        for (guid in serverList) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: continue
            // Compare subscriptionId, handling empty strings
            if (profile.subscriptionId == subId) {
                count++
            }
        }
        Log.d(AppConfig.TAG, "getNodeCount for subId=$subId: count=$count")
        return count
    }
    
    /**
     * Format last update time
     */
    private fun getLastUpdateInfo(lastUpdated: Long): String {
        if (lastUpdated <= 0) {
            return mActivity.getString(R.string.subscription_never_updated)
        }
        
        val now = System.currentTimeMillis()
        val diff = now - lastUpdated
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> mActivity.getString(R.string.subscription_updated_days_ago, days)
            hours > 0 -> mActivity.getString(R.string.subscription_updated_hours_ago, hours)
            minutes > 0 -> mActivity.getString(R.string.subscription_updated_minutes_ago, minutes)
            else -> mActivity.getString(R.string.subscription_updated_just_now)
        }
    }
}
