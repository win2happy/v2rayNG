package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InfoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_CONNECTION = "com.v2ray.ang.action.TOGGLE_CONNECTION"
        const val ACTION_SWITCH_SERVER = "com.v2ray.ang.action.SWITCH_SERVER"
        const val ACTION_OPEN_APP = "com.v2ray.ang.action.OPEN_APP"
        const val ACTION_UPDATE_TRAFFIC = "com.v2ray.ang.action.UPDATE_TRAFFIC"
        const val ACTION_UPDATE_PING = "com.v2ray.ang.action.UPDATE_PING"

        fun updateWidget(context: Context) {
            val intent = Intent(context, InfoWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, InfoWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        
        // Start periodic ping test if VPN is running
        if (V2RayServiceManager.isRunning()) {
            schedulePingTest(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget added, start ping scheduler
        if (V2RayServiceManager.isRunning()) {
            schedulePingTest(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed, no need to cancel (handled by system)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        try {
            when (intent.action) {
                ACTION_TOGGLE_CONNECTION -> {
                    handleToggleConnection(context)
                }
                ACTION_SWITCH_SERVER -> {
                    handleSwitchServer(context)
                }
                ACTION_OPEN_APP -> {
                    handleOpenApp(context)
                }
                ACTION_UPDATE_TRAFFIC -> {
                    handleUpdateTraffic(context, intent)
                }
                ACTION_UPDATE_PING -> {
                    handleUpdatePing(context, intent)
                }
                AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                    // Update widget when service state changes
                    updateWidget(context)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error in onReceive: ${e.message}", e)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            // Use simple layout for better compatibility
            val views = RemoteViews(context.packageName, R.layout.widget_info_simple)
            
            // Get current service state
            val isRunning = V2RayServiceManager.isRunning()
            val serverName = getServerName()
        
            // Update status indicator - use setInt for background color
            if (isRunning) {
                views.setTextViewText(R.id.widget_status_text, context.getString(R.string.widget_status_connected))
                views.setInt(R.id.widget_status_indicator, "setBackgroundColor", 0xFF4CAF50.toInt())
                views.setTextViewText(R.id.widget_btn_toggle, "■") // Stop symbol
                views.setInt(R.id.widget_traffic_layout, "setVisibility", View.VISIBLE)
                
                // Initialize traffic display
                views.setTextViewText(R.id.widget_upload_speed, "0 B/s")
                views.setTextViewText(R.id.widget_download_speed, "0 B/s")
                views.setTextViewText(R.id.widget_upload_total, "0 B")
                views.setTextViewText(R.id.widget_download_total, "0 B")
                views.setTextViewText(R.id.widget_ping, "-- ms")
            } else {
                views.setTextViewText(R.id.widget_status_text, context.getString(R.string.widget_status_disconnected))
                views.setInt(R.id.widget_status_indicator, "setBackgroundColor", 0xFF9E9E9E.toInt())
                views.setTextViewText(R.id.widget_btn_toggle, "▶") // Play symbol
                views.setInt(R.id.widget_traffic_layout, "setVisibility", View.GONE)
            }
            
            // Update server name
            if (serverName.isNotEmpty()) {
                views.setTextViewText(R.id.widget_server_name, serverName)
            } else {
                views.setTextViewText(R.id.widget_server_name, context.getString(R.string.widget_no_server_selected))
            }
            
            // Set up click listeners
            setupClickListeners(context, views)
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // Update ping asynchronously if connected
            if (isRunning) {
                updatePingAsync(context, appWidgetId)
            }
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error updating widget: ${e.message}", e)
            e.printStackTrace()
            // Create a minimal fallback view to prevent widget from showing error
            try {
                val fallbackViews = RemoteViews(context.packageName, R.layout.widget_info_simple)
                fallbackViews.setTextViewText(R.id.widget_server_name, "Loading...")
                fallbackViews.setTextViewText(R.id.widget_status_text, "Please wait")
                fallbackViews.setInt(R.id.widget_status_indicator, "setBackgroundColor", 0xFFFF9800.toInt())
                fallbackViews.setInt(R.id.widget_traffic_layout, "setVisibility", View.GONE)
                setupClickListeners(context, fallbackViews)
                appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)
            } catch (e2: Exception) {
                android.util.Log.e("InfoWidgetProvider", "Error creating fallback view: ${e2.message}", e2)
                e2.printStackTrace()
            }
        }
    }

    private fun setupClickListeners(context: Context, views: RemoteViews) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Toggle connection button
            val toggleIntent = Intent(context, InfoWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_CONNECTION
            }
            val togglePendingIntent = PendingIntent.getBroadcast(context, 0, toggleIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_toggle, togglePendingIntent)

            // Switch server button
            val switchIntent = Intent(context, InfoWidgetProvider::class.java).apply {
                action = ACTION_SWITCH_SERVER
            }
            val switchPendingIntent = PendingIntent.getBroadcast(context, 1, switchIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_switch, switchPendingIntent)

            // Open app button
            val openIntent = Intent(context, InfoWidgetProvider::class.java).apply {
                action = ACTION_OPEN_APP
            }
            val openPendingIntent = PendingIntent.getBroadcast(context, 2, openIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_open_app, openPendingIntent)
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error setting up click listeners: ${e.message}", e)
        }
    }

    private fun handleToggleConnection(context: Context) {
        try {
            if (V2RayServiceManager.isRunning()) {
                V2RayServiceManager.stopVService(context)
            } else {
                V2RayServiceManager.startVServiceFromToggle(context)
            }
            updateWidget(context)
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error toggling connection: ${e.message}", e)
        }
    }

    private fun handleSwitchServer(context: Context) {
        try {
            val serverList = MmkvManager.decodeServerList()
            if (serverList.isEmpty()) {
                android.util.Log.w("InfoWidgetProvider", "Server list is empty, cannot switch")
                return
            }

            val currentGuid = MmkvManager.getSelectServer()
            val currentIndex = serverList.indexOf(currentGuid)
            val nextIndex = (currentIndex + 1) % serverList.size
            val nextGuid = serverList[nextIndex]

            MmkvManager.setSelectServer(nextGuid)
            
            if (V2RayServiceManager.isRunning()) {
                V2RayServiceManager.startVService(context, nextGuid)
            }
            
            updateWidget(context)
            
            // Show toast
            val serverName = getServerName()
            if (serverName.isNotEmpty()) {
                context.toast("${context.getString(R.string.widget_switched_to)}: $serverName")
            }
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error switching server: ${e.message}", e)
        }
    }

    private fun handleOpenApp(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error opening app: ${e.message}", e)
        }
    }

    private fun handleUpdateTraffic(context: Context, intent: Intent) {
        try {
            val uploadSpeed = intent.getLongExtra("uploadSpeed", 0L)
            val downloadSpeed = intent.getLongExtra("downloadSpeed", 0L)
            val uploadTotal = intent.getLongExtra("uploadTotal", 0L)
            val downloadTotal = intent.getLongExtra("downloadTotal", 0L)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, InfoWidgetProvider::class.java)
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_info_simple)
                
                views.setTextViewText(R.id.widget_upload_speed, Utils.humanReadableByteCount(uploadSpeed, true) + "/s")
                views.setTextViewText(R.id.widget_download_speed, Utils.humanReadableByteCount(downloadSpeed, true) + "/s")
                views.setTextViewText(R.id.widget_upload_total, Utils.humanReadableByteCount(uploadTotal, false))
                views.setTextViewText(R.id.widget_download_total, Utils.humanReadableByteCount(downloadTotal, false))
                
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error updating traffic: ${e.message}", e)
        }
    }

    private fun getServerName(): String {
        return try {
            val guid = MmkvManager.getSelectServer()
            if (guid.isNullOrEmpty()) {
                android.util.Log.d("InfoWidgetProvider", "No server GUID selected")
                return ""
            }
            val config = MmkvManager.decodeServerConfig(guid)
            if (config == null) {
                android.util.Log.w("InfoWidgetProvider", "Failed to decode server config for GUID: $guid")
                return ""
            }
            config.remarks ?: ""
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error getting server name: ${e.message}", e)
            ""
        }
    }

    /**
     * Schedule periodic ping test (every 10 seconds)
     */
    private fun schedulePingTest(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            while (V2RayServiceManager.isRunning()) {
                val latency = com.v2ray.ang.helper.WidgetPingTester.testCurrentLatency(context)
                
                // Send broadcast to update all widgets
                val intent = Intent(context, InfoWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_PING
                    putExtra("latency", latency)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) // Update all
                }
                context.sendBroadcast(intent)
                
                // Wait 10 seconds before next test
                delay(10000)
            }
        }
    }

    /**
     * Update ping asynchronously (for single widget)
     */
    private fun updatePingAsync(context: Context, appWidgetId: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            val latency = com.v2ray.ang.helper.WidgetPingTester.testCurrentLatency(context)
            
            // Send broadcast to update ping
            val intent = Intent(context, InfoWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_PING
                putExtra("latency", latency)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.sendBroadcast(intent)
        }
    }

    /**
     * Handle ping update
     */
    private fun handleUpdatePing(context: Context, intent: Intent) {
        try {
            val latency = intent.getLongExtra("latency", -1L)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            
            if (appWidgetId == -1) {
                // Update all widgets
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, InfoWidgetProvider::class.java)
                )
                
                for (widgetId in appWidgetIds) {
                    updatePingDisplay(context, appWidgetManager, widgetId, latency)
                }
            } else {
                // Update specific widget
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updatePingDisplay(context, appWidgetManager, appWidgetId, latency)
            }
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error updating ping: ${e.message}", e)
        }
    }

    /**
     * Update ping display
     */
    private fun updatePingDisplay(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        latency: Long
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_info_simple)
            
            // Format and set ping text
            val pingText = com.v2ray.ang.helper.WidgetPingTester.formatLatency(latency)
            views.setTextViewText(R.id.widget_ping, pingText)
            
            // Set color based on latency level
            val color = when (com.v2ray.ang.helper.WidgetPingTester.getLatencyLevel(latency)) {
                1 -> 0xFF4CAF50.toInt()  // Excellent - Green
                2 -> 0xFF8BC34A.toInt()  // Good - Light Green
                3 -> 0xFFFF9800.toInt()  // Fair - Orange
                4 -> 0xFFF44336.toInt()  // Poor - Red
                else -> 0xFFFFFFFF.toInt() // Unknown - White
            }
            views.setTextColor(R.id.widget_ping, color)
            
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            android.util.Log.e("InfoWidgetProvider", "Error updating ping display: ${e.message}", e)
        }
    }
}

private fun Context.toast(message: String) {
    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}
