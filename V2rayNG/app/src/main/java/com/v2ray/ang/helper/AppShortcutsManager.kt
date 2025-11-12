package com.v2ray.ang.helper

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.QuickSwitchActivity

/**
 * App Shortcuts Manager
 * Creates and manages dynamic shortcuts for quick server switching
 */
object AppShortcutsManager {
    
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updateShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = mutableListOf<ShortcutInfo>()
        
        // Toggle Connection Shortcut
        shortcuts.add(createToggleShortcut(context))
        
        // Switch to Next Server Shortcut
        shortcuts.add(createSwitchNextShortcut(context))
        
        // Recent Servers Shortcuts (up to 3)
        shortcuts.addAll(createRecentServersShortcuts(context))
        
        shortcutManager.dynamicShortcuts = shortcuts
    }
    
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createToggleShortcut(context: Context): ShortcutInfo {
        val intent = Intent(context, QuickSwitchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(QuickSwitchActivity.EXTRA_ACTION, QuickSwitchActivity.ACTION_TOGGLE)
        }
        
        return ShortcutInfo.Builder(context, "shortcut_toggle")
            .setShortLabel(context.getString(R.string.shortcut_toggle_short))
            .setLongLabel(context.getString(R.string.shortcut_toggle_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_play_24dp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }
    
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createSwitchNextShortcut(context: Context): ShortcutInfo {
        val intent = Intent(context, QuickSwitchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(QuickSwitchActivity.EXTRA_ACTION, QuickSwitchActivity.ACTION_SWITCH_NEXT)
        }
        
        return ShortcutInfo.Builder(context, "shortcut_switch_next")
            .setShortLabel(context.getString(R.string.shortcut_switch_next_short))
            .setLongLabel(context.getString(R.string.shortcut_switch_next_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_swap_24dp))
            .setIntent(intent)
            .setRank(1)
            .build()
    }
    
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createRecentServersShortcuts(context: Context): List<ShortcutInfo> {
        val shortcuts = mutableListOf<ShortcutInfo>()
        val serverList = MmkvManager.decodeServerList()
        val currentGuid = MmkvManager.getSelectServer()
        
        // Get up to 3 recent servers (excluding current)
        val recentServers = serverList
            .filter { it != currentGuid }
            .take(3)
        
        recentServers.forEachIndexed { index, guid ->
            val config = MmkvManager.decodeServerConfig(guid) ?: return@forEachIndexed
            
            val intent = Intent(context, QuickSwitchActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(QuickSwitchActivity.EXTRA_SERVER_GUID, guid)
            }
            
            val shortcut = ShortcutInfo.Builder(context, "shortcut_server_$guid")
                .setShortLabel(config.remarks.take(10))
                .setLongLabel(config.remarks)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_server_24dp))
                .setIntent(intent)
                .setRank(2 + index)
                .build()
            
            shortcuts.add(shortcut)
        }
        
        return shortcuts
    }
    
    /**
     * Pin shortcut to launcher (Android 8.0+)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun pinServerShortcut(context: Context, guid: String) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        
        if (!shortcutManager.isRequestPinShortcutSupported) {
            return
        }
        
        val config = MmkvManager.decodeServerConfig(guid) ?: return
        
        val intent = Intent(context, QuickSwitchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(QuickSwitchActivity.EXTRA_SERVER_GUID, guid)
        }
        
        val pinShortcutInfo = ShortcutInfo.Builder(context, "pin_$guid")
            .setShortLabel(config.remarks.take(10))
            .setLongLabel(config.remarks)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_server_24dp))
            .setIntent(intent)
            .build()
        
        shortcutManager.requestPinShortcut(pinShortcutInfo, null)
    }
}
