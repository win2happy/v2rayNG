package com.v2ray.ang.ui

import android.app.Activity
import android.os.Bundle
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager

/**
 * Quick Switch Activity
 * Transparent activity for quick server switching from shortcuts
 */
class QuickSwitchActivity : Activity() {
    
    companion object {
        const val EXTRA_SERVER_GUID = "server_guid"
        const val EXTRA_ACTION = "action"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_SWITCH_NEXT = "switch_next"
        const val ACTION_SWITCH_PREV = "switch_prev"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent()
        finish()
    }
    
    private fun handleIntent() {
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_TOGGLE -> {
                handleToggle()
            }
            ACTION_SWITCH_NEXT -> {
                handleSwitchNext()
            }
            ACTION_SWITCH_PREV -> {
                handleSwitchPrev()
            }
            else -> {
                // Switch to specific server
                val guid = intent.getStringExtra(EXTRA_SERVER_GUID)
                if (!guid.isNullOrEmpty()) {
                    handleSwitchToServer(guid)
                }
            }
        }
    }
    
    private fun handleToggle() {
        if (V2RayServiceManager.isRunning()) {
            V2RayServiceManager.stopVService(this)
        } else {
            V2RayServiceManager.startVServiceFromToggle(this)
        }
    }
    
    private fun handleSwitchNext() {
        val serverList = MmkvManager.decodeServerList()
        if (serverList.isEmpty()) {
            return
        }
        
        val currentGuid = MmkvManager.getSelectServer()
        val currentIndex = serverList.indexOf(currentGuid)
        val nextIndex = (currentIndex + 1) % serverList.size
        val nextGuid = serverList[nextIndex]
        
        switchToServer(nextGuid)
    }
    
    private fun handleSwitchPrev() {
        val serverList = MmkvManager.decodeServerList()
        if (serverList.isEmpty()) {
            return
        }
        
        val currentGuid = MmkvManager.getSelectServer()
        val currentIndex = serverList.indexOf(currentGuid)
        val prevIndex = if (currentIndex - 1 < 0) serverList.size - 1 else currentIndex - 1
        val prevGuid = serverList[prevIndex]
        
        switchToServer(prevGuid)
    }
    
    private fun handleSwitchToServer(guid: String) {
        switchToServer(guid)
    }
    
    private fun switchToServer(guid: String) {
        MmkvManager.setSelectServer(guid)
        
        if (V2RayServiceManager.isRunning()) {
            V2RayServiceManager.startVService(this, guid)
        }
        
        // Get server name and show toast
        val config = MmkvManager.decodeServerConfig(guid)
        val serverName = config?.remarks ?: "Unknown"
        android.widget.Toast.makeText(
            this,
            "Switched to: $serverName",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
