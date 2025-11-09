package com.v2ray.ang.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityHomeBinding
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.RecentConnection
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class HomeActivity : BaseActivity() {

    private val binding by lazy {
        ActivityHomeBinding.inflate(layoutInflater)
    }

    private lateinit var recentConnectionAdapter: RecentConnectionAdapter
    private var isRunning = false
    private var currentServerGuid: String? = null
    private var speedMonitorJob: Job? = null
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTimestamp = 0L

    private val requestVpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning = true
                    updateConnectionStatus()
                    startSpeedMonitor()
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning = false
                    updateConnectionStatus()
                    stopSpeedMonitor()
                }
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    toast(R.string.toast_services_success)
                    addToRecentConnections()
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    toast(R.string.toast_services_failure)
                }
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    // Connection stopped
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.home_title)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupViews()
        loadRecentConnections()
        checkConnectionStatus()
        loadLocationInfo()
    }

    override fun onResume() {
        super.onResume()
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(this, mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
        
        // Refresh connection status when returning to page
        checkConnectionStatus()
        updateConnectionStatus()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mMsgReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeedMonitor()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupViews() {
        // 设置连接按钮
        binding.btnConnectionToggle.setOnClickListener {
            if (isRunning) {
                stopV2Ray()
            } else {
                val selectedGuid = MmkvManager.getSelectServer()
                if (selectedGuid.isNullOrEmpty()) {
                    toast(R.string.home_no_server_selected)
                    return@setOnClickListener
                }
                
                if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN) {
                    val intent = VpnService.prepare(this)
                    if (intent == null) {
                        startV2Ray()
                    } else {
                        requestVpnPermission.launch(intent)
                    }
                } else {
                    startV2Ray()
                }
            }
        }

        // 设置最近连接列表
        recentConnectionAdapter = RecentConnectionAdapter { connection ->
            connectToServer(connection.guid)
        }
        binding.recyclerRecentConnections.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = recentConnectionAdapter
        }
    }

    private fun checkConnectionStatus() {
        currentServerGuid = MmkvManager.getSelectServer()
        
        // Check if service is running
        lifecycleScope.launch {
            delay(100)
            MessageUtil.sendMsg2Service(this@HomeActivity, AppConfig.MSG_STATE_START, "")
        }
    }

    private fun updateConnectionStatus() {
        if (isRunning) {
            binding.ivConnectionStatus.setColorFilter(
                ContextCompat.getColor(this, R.color.color_fab_active)
            )
            binding.tvConnectionStatus.text = getString(R.string.connection_connected)
            binding.btnConnectionToggle.text = getString(R.string.home_disconnect)
            
            // 显示当前节点信息
            currentServerGuid?.let { guid ->
                val config = MmkvManager.decodeServerConfig(guid)
                config?.let {
                    binding.tvCurrentNode.visibility = View.VISIBLE
                    binding.tvCurrentNode.text = it.remarks ?: getString(R.string.home_unnamed_node)
                    
                    // 加载节点位置信息
                    loadNodeLocation(it.server ?: "")
                }
            }
            
            // 显示速度卡片
            binding.cardSpeed.visibility = View.VISIBLE
        } else {
            binding.ivConnectionStatus.setColorFilter(
                ContextCompat.getColor(this, R.color.color_fab_inactive)
            )
            binding.tvConnectionStatus.text = getString(R.string.connection_not_connected)
            binding.btnConnectionToggle.text = getString(R.string.home_connect)
            binding.tvCurrentNode.visibility = View.GONE
            binding.tvNodeLocation.visibility = View.GONE
            binding.cardSpeed.visibility = View.GONE
        }
        
        loadRecentConnections()
    }

    private fun startV2Ray() {
        V2RayServiceManager.startVService(this)
    }

    private fun stopV2Ray() {
        V2RayServiceManager.stopVService(this)
    }

    private fun connectToServer(guid: String) {
        if (isRunning) {
            V2RayServiceManager.stopVService(this)
            lifecycleScope.launch {
                delay(500)
                MmkvManager.setSelectServer(guid)
                currentServerGuid = guid
                startV2Ray()
            }
        } else {
            MmkvManager.setSelectServer(guid)
            currentServerGuid = guid
            startV2Ray()
        }
    }

    private fun loadRecentConnections() {
        val recentJson = MmkvManager.decodeSettingsString(AppConfig.PREF_RECENT_CONNECTIONS) ?: "[]"
        val recentList = try {
            Gson().fromJson(recentJson, Array<RecentConnection>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }

        // Mark the active connection
        val updatedList = recentList.map { it.copy(isActive = it.guid == currentServerGuid && isRunning) }
        
        if (updatedList.isEmpty()) {
            binding.tvNoRecentConnections.visibility = View.VISIBLE
            binding.recyclerRecentConnections.visibility = View.GONE
        } else {
            binding.tvNoRecentConnections.visibility = View.GONE
            binding.recyclerRecentConnections.visibility = View.VISIBLE
            recentConnectionAdapter.updateData(updatedList.take(5))
        }
    }

    private fun addToRecentConnections() {
        currentServerGuid?.let { guid ->
            val config = MmkvManager.decodeServerConfig(guid) ?: return
            
            val recentJson = MmkvManager.decodeSettingsString(AppConfig.PREF_RECENT_CONNECTIONS) ?: "[]"
            val recentList = try {
                Gson().fromJson(recentJson, Array<RecentConnection>::class.java).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Remove if already exists
            recentList.removeAll { it.guid == guid }

            // Add to front
            val newConnection = RecentConnection(
                guid = guid,
                name = config.remarks ?: getString(R.string.home_unnamed_node),
                server = config.server ?: "",
                port = config.serverPort ?: "",
                configType = config.configType.name,
                timestamp = System.currentTimeMillis(),
                isActive = true
            )
            recentList.add(0, newConnection)

            // Keep only last 5
            val trimmedList = recentList.take(5)

            // Save back
            val json = Gson().toJson(trimmedList)
            MmkvManager.encodeSettings(AppConfig.PREF_RECENT_CONNECTIONS, json)

            loadRecentConnections()
        }
    }

    private fun loadLocationInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "http://ip-api.com/json/?fields=status,message,country,countryCode,city,isp,query"
                val response = URL(url).readText()
                val locationInfo = Gson().fromJson(response, IPAPIInfo::class.java)

                withContext(Dispatchers.Main) {
                    if (locationInfo.status == "success") {
                        binding.tvLocationIp.text = "${getString(R.string.home_ip)}: ${locationInfo.query}"
                        binding.tvLocationCountry.text = "${getString(R.string.home_location)}: ${locationInfo.city}, ${locationInfo.country}"
                        binding.tvLocationIsp.text = "${getString(R.string.home_isp)}: ${locationInfo.isp}"
                    } else {
                        binding.tvLocationIp.text = getString(R.string.home_location_failed)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvLocationIp.text = getString(R.string.home_location_failed)
                }
            }
        }
    }

    private fun loadNodeLocation(serverIp: String) {
        if (serverIp.isEmpty()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "http://ip-api.com/json/$serverIp?fields=status,country,city"
                val response = URL(url).readText()
                val locationInfo = Gson().fromJson(response, IPAPIInfo::class.java)

                withContext(Dispatchers.Main) {
                    if (locationInfo.status == "success") {
                        binding.tvNodeLocation.visibility = View.VISIBLE
                        binding.tvNodeLocation.text = "${getString(R.string.home_node_location)}: ${locationInfo.city}, ${locationInfo.country}"
                    }
                }
            } catch (e: Exception) {
                // Ignore errors for node location
            }
        }
    }

    private fun startSpeedMonitor() {
        stopSpeedMonitor()
        
        speedMonitorJob = lifecycleScope.launch(Dispatchers.IO) {
            lastTimestamp = System.currentTimeMillis()
            
            // Get current config to determine outbound tags
            val currentConfig = currentServerGuid?.let { MmkvManager.decodeServerConfig(it) }
            val outboundTags = currentConfig?.getAllOutboundTags()?.toMutableList()
            outboundTags?.remove(AppConfig.TAG_DIRECT)
            
            while (isRunning) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = (currentTime - lastTimestamp) / 1000.0
                    
                    if (timeDiff > 0) {
                        // Get real traffic stats from service
                        var totalUpload = 0L
                        var totalDownload = 0L
                        
                        // Query proxy traffic
                        outboundTags?.forEach { tag ->
                            totalUpload += V2RayServiceManager.queryStats(tag, AppConfig.UPLINK)
                            totalDownload += V2RayServiceManager.queryStats(tag, AppConfig.DOWNLINK)
                        }
                        
                        // Calculate speed (bytes per second)
                        val uploadSpeed = if (lastTxBytes > 0) {
                            ((totalUpload - lastTxBytes) / timeDiff).toLong()
                        } else {
                            0L
                        }
                        
                        val downloadSpeed = if (lastRxBytes > 0) {
                            ((totalDownload - lastRxBytes) / timeDiff).toLong()
                        } else {
                            0L
                        }
                        
                        // Update display
                        withContext(Dispatchers.Main) {
                            updateSpeedDisplay(uploadSpeed, downloadSpeed)
                            binding.speedChart.addData(uploadSpeed, downloadSpeed)
                        }
                        
                        // Save current values for next calculation
                        lastTxBytes = totalUpload
                        lastRxBytes = totalDownload
                        lastTimestamp = currentTime
                    }
                    
                    delay(1000) // Update every second
                } catch (e: Exception) {
                    // Continue on error
                }
            }
        }
    }

    private fun stopSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = null
        binding.speedChart.clear()
    }

    private fun updateSpeedDisplay(uploadSpeed: Long, downloadSpeed: Long) {
        binding.tvUploadSpeed.text = formatSpeed(uploadSpeed)
        binding.tvDownloadSpeed.text = formatSpeed(downloadSpeed)
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
            bytesPerSecond < 1024 * 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            else -> String.format("%.2f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
