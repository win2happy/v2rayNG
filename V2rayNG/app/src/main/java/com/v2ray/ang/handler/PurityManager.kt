package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ServerPurityInfo
import com.v2ray.ang.util.HttpUtil
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Manager for testing server purity/cleanliness
 * Tests include: DNS leak, IP consistency, port accessibility, etc.
 */
object PurityManager {
    private const val TAG = "PurityManager"
    
    // Test endpoints for checking DNS leaks and IP consistency
    private val testEndpoints = listOf(
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://ifconfig.me/all.json",
        "https://api.ipify.org?format=json"
    )
    
    private val purityCache = mutableMapOf<String, ServerPurityInfo>()
    private const val CACHE_VALIDITY_HOURS = 24 // Cache for 24 hours
    
    /**
     * Tests the purity of a server
     * @param serverAddress The server IP or hostname
     * @param serverPort The server port
     * @return ServerPurityInfo object with purity data
     */
    suspend fun testServerPurity(serverAddress: String, serverPort: Int): ServerPurityInfo {
        if (serverAddress.isBlank()) {
            return ServerPurityInfo(
                serverAddress = serverAddress,
                purityScore = 0,
                lastTested = System.currentTimeMillis()
            )
        }
        
        // Check cache first
        val cacheKey = "$serverAddress:$serverPort"
        val cached = getCachedPurity(cacheKey)
        if (cached != null && !isPurityInfoExpired(cached)) {
            return cached
        }
        
        return try {
            val score = calculatePurityScore(serverAddress, serverPort)
            val purityInfo = ServerPurityInfo(
                serverAddress = serverAddress,
                purityScore = score,
                lastTested = System.currentTimeMillis()
            )
            
            // Cache the result
            purityCache[cacheKey] = purityInfo
            purityInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error testing purity for $serverAddress: ${e.message}")
            ServerPurityInfo(
                serverAddress = serverAddress,
                purityScore = 0,
                lastTested = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Calculates purity score based on multiple tests
     * Score range: 0-100
     */
    private suspend fun calculatePurityScore(serverAddress: String, serverPort: Int): Int {
        var score = 100
        
        try {
            // Test 1: Port accessibility (30 points)
            val portAccessible = testPortAccessibility(serverAddress, serverPort)
            if (!portAccessible) {
                score -= 30
            }
            
            // Test 2: DNS consistency (30 points)
            val dnsConsistent = testDnsConsistency(serverAddress)
            if (!dnsConsistent) {
                score -= 30
            }
            
            // Test 3: Response time stability (20 points)
            val responseStable = testResponseStability(serverAddress, serverPort)
            if (!responseStable) {
                score -= 20
            }
            
            // Test 4: Connection success rate (20 points)
            val connectionSuccess = testConnectionSuccessRate(serverAddress, serverPort)
            if (connectionSuccess < 0.8) { // Less than 80% success
                score -= 20
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating purity score: ${e.message}")
            score = 50 // Default to medium score on error
        }
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Tests if the port is accessible
     */
    private suspend fun testPortAccessibility(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5000)
                    true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Port accessibility test failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Tests DNS consistency by resolving the hostname multiple times
     */
    private suspend fun testDnsConsistency(host: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // If it's already an IP, skip this test
                if (host.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                    return@withContext true
                }
                
                val resolvedIPs = mutableSetOf<String>()
                repeat(3) {
                    try {
                        val address = java.net.InetAddress.getByName(host)
                        resolvedIPs.add(address.hostAddress ?: "")
                    } catch (e: Exception) {
                        Log.d(TAG, "DNS resolution attempt failed: ${e.message}")
                    }
                    delay(100)
                }
                
                // Consistent if all resolutions return the same IP
                resolvedIPs.size <= 1
            } catch (e: Exception) {
                Log.d(TAG, "DNS consistency test failed: ${e.message}")
                true // Assume consistent if test fails
            }
        }
    }
    
    /**
     * Tests response time stability
     */
    private suspend fun testResponseStability(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val responseTimes = mutableListOf<Long>()
                
                repeat(3) {
                    val startTime = System.currentTimeMillis()
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), 3000)
                        }
                        val elapsed = System.currentTimeMillis() - startTime
                        responseTimes.add(elapsed)
                    } catch (e: Exception) {
                        // Connection failed, record as high latency
                        responseTimes.add(10000)
                    }
                    delay(100)
                }
                
                if (responseTimes.isEmpty()) return@withContext false
                
                // Calculate variance
                val avg = responseTimes.average()
                val variance = responseTimes.map { (it - avg) * (it - avg) }.average()
                val stdDev = kotlin.math.sqrt(variance)
                
                // Stable if standard deviation is less than 50% of average
                stdDev < avg * 0.5
            } catch (e: Exception) {
                Log.d(TAG, "Response stability test failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Tests connection success rate
     */
    private suspend fun testConnectionSuccessRate(host: String, port: Int): Double {
        return withContext(Dispatchers.IO) {
            try {
                var successCount = 0
                val totalAttempts = 5
                
                repeat(totalAttempts) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), 2000)
                            successCount++
                        }
                    } catch (e: Exception) {
                        // Connection failed
                    }
                    delay(50)
                }
                
                successCount.toDouble() / totalAttempts
            } catch (e: Exception) {
                Log.d(TAG, "Connection success rate test failed: ${e.message}")
                0.0
            }
        }
    }
    
    private fun getCachedPurity(cacheKey: String): ServerPurityInfo? {
        return purityCache[cacheKey]
    }
    
    private fun isPurityInfoExpired(purityInfo: ServerPurityInfo): Boolean {
        val now = System.currentTimeMillis()
        val hours = (now - purityInfo.lastTested) / (1000 * 60 * 60)
        return hours > CACHE_VALIDITY_HOURS
    }
    
    /**
     * Clears the purity cache
     */
    fun clearPurityCache() {
        purityCache.clear()
    }
}
