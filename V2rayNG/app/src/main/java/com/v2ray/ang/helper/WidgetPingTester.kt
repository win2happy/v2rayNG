package com.v2ray.ang.helper

import android.content.Context
import android.os.SystemClock
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Widget Ping Tester
 * Provides lightweight ping functionality for widget updates
 */
object WidgetPingTester {

    /**
     * Test current connection latency (lightweight version)
     * @param context Application context
     * @return Latency in milliseconds, or -1 if failed
     */
    suspend fun testCurrentLatency(context: Context): Long {
        return withContext(Dispatchers.IO) {
            try {
                // Method 1: Use existing SpeedtestManager
                val httpPort = SettingsManager.getHttpPort()
                if (httpPort > 0) {
                    val result = SpeedtestManager.testConnection(context, httpPort)
                    if (result.first > 0) {
                        return@withContext result.first
                    }
                }
                
                // Method 2: Fallback to simple HTTP test
                testSimpleHttpLatency()
            } catch (e: Exception) {
                -1L
            }
        }
    }

    /**
     * Test simple HTTP latency (for when VPN is connected)
     * @return Latency in milliseconds, or -1 if failed
     */
    private fun testSimpleHttpLatency(): Long {
        return try {
            val testUrl = SettingsManager.getDelayTestUrl()
            val url = URL(testUrl)
            val start = SystemClock.elapsedRealtime()
            
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            val elapsed = SystemClock.elapsedRealtime() - start
            
            connection.disconnect()
            
            if (responseCode in 200..299 || responseCode == 204) {
                elapsed
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Test TCP connection latency (alternative method)
     * @param host Server host
     * @param port Server port
     * @return Latency in milliseconds, or -1 if failed
     */
    suspend fun testTcpLatency(host: String, port: Int): Long {
        return withContext(Dispatchers.IO) {
            try {
                val start = SystemClock.elapsedRealtime()
                
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5000)
                }
                
                SystemClock.elapsedRealtime() - start
            } catch (e: Exception) {
                -1L
            }
        }
    }

    /**
     * Format latency for display
     * @param latency Latency in milliseconds
     * @return Formatted string (e.g., "55 ms" or "-- ms")
     */
    fun formatLatency(latency: Long): String {
        return if (latency > 0) {
            "$latency ms"
        } else {
            "-- ms"
        }
    }

    /**
     * Get latency color indicator
     * @param latency Latency in milliseconds
     * @return Color code: 1=excellent, 2=good, 3=fair, 4=poor, -1=unknown
     */
    fun getLatencyLevel(latency: Long): Int {
        return when {
            latency < 0 -> -1
            latency < 100 -> 1  // Excellent (green)
            latency < 200 -> 2  // Good (yellow-green)
            latency < 500 -> 3  // Fair (orange)
            else -> 4           // Poor (red)
        }
    }
}
