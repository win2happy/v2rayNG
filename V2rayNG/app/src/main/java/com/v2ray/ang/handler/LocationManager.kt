 package com.v2ray.ang.handler
 
 import android.util.Log
 import com.v2ray.ang.AppConfig
 import com.v2ray.ang.dto.IPAPIInfo
 import com.v2ray.ang.dto.ServerLocationInfo
 import com.v2ray.ang.util.HttpUtil
 import com.v2ray.ang.util.JsonUtil
 import kotlinx.coroutines.*
 import java.net.InetAddress
 
 object LocationManager {
     private const val TAG = "LocationManager"
     
     // Various IP location services as fallbacks
     private val locationServices = listOf(
         "https://ipapi.co/json",
         "http://ip-api.com/json",
         "https://ipinfo.io/json",
         "https://api.ipgeolocation.io/ipgeo"
     )
     
     private val locationCache = mutableMapOf<String, ServerLocationInfo>()
     private const val CACHE_VALIDITY_HOURS = 24 * 7 // Cache for 7 days
 
     /**
      * Gets the location information for a server address
      * @param serverAddress The server IP or hostname
      * @return ServerLocationInfo object with location data or null if failed
      */
     suspend fun getServerLocation(serverAddress: String): ServerLocationInfo? {
         if (serverAddress.isBlank()) return null
         
         // Check cache first
         val cached = getCachedLocation(serverAddress)
         if (cached != null && !isLocationInfoExpired(cached)) {
             return cached
         }
         
         return try {
             // Resolve hostname to IP if needed
             val ipAddress = resolveToIpAddress(serverAddress)
             if (ipAddress == null) {
                 Log.w(TAG, "Could not resolve hostname: $serverAddress")
                 return null
             }
             
             // Try to get location info from various services
             val locationInfo = fetchLocationFromServices(ipAddress)
             
             if (locationInfo != null) {
                 // Cache the result
                 locationCache[serverAddress] = locationInfo
                 // Store in persistent storage
                 return locationInfo
             }
             
             null
         } catch (e: Exception) {
             Log.e(TAG, "Error getting location for $serverAddress: ${e.message}")
             null
         }
     }
     
     private fun getCachedLocation(serverAddress: String): ServerLocationInfo? {
         return locationCache[serverAddress]
     }
     
     private fun isLocationInfoExpired(locationInfo: ServerLocationInfo): Boolean {
         val now = System.currentTimeMillis()
         val hours = (now - locationInfo.lastUpdated) / (1000 * 60 * 60)
         return hours > CACHE_VALIDITY_HOURS
     }
     
     private suspend fun resolveToIpAddress(address: String): String? {
         return try {
             withContext(Dispatchers.IO) {
                 // Check if it's already an IP address
                 if (address.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                     address
                 } else {
                     // Resolve hostname to IP
                     val inetAddress = InetAddress.getByName(address)
                     inetAddress.hostAddress
                 }
             }
         } catch (e: Exception) {
             Log.e(TAG, "Error resolving address $address: ${e.message}")
             null
         }
     }
     
     private suspend fun fetchLocationFromServices(ipAddress: String): ServerLocationInfo? {
         for (service in locationServices) {
             try {
                 val locationInfo = fetchLocationFromService(service, ipAddress)
                 if (locationInfo != null) {
                     return locationInfo
                 }
             } catch (e: Exception) {
                 Log.w(TAG, "Failed to get location from $service: ${e.message}")
                 continue
             }
         }
         return null
     }
     
     private suspend fun fetchLocationFromService(serviceUrl: String, ipAddress: String): ServerLocationInfo? {
         return try {
             withContext(Dispatchers.IO) {
                 val url = when {
                     serviceUrl.contains("ipapi.co") -> "$serviceUrl/$ipAddress"
                     serviceUrl.contains("ip-api.com") -> "$serviceUrl/$ipAddress"
                     serviceUrl.contains("ipinfo.io") -> "$serviceUrl/$ipAddress"
                     else -> "$serviceUrl?ip=$ipAddress"
                 }
                 
                 val content = HttpUtil.getUrlContent(url, 5000) ?: return@withContext null
                 val ipInfo = JsonUtil.fromJson(content, IPAPIInfo::class.java) ?: return@withContext null
                 
                 ServerLocationInfo(
                     serverAddress = ipAddress,
                     country = ipInfo.country ?: ipInfo.country_name,
                     countryCode = ipInfo.country_code ?: ipInfo.countryCode,
                     region = ipInfo.region ?: ipInfo.regionName,
                     city = ipInfo.city,
                     lastUpdated = System.currentTimeMillis()
                 )
             }
         } catch (e: Exception) {
             Log.e(TAG, "Error fetching from $serviceUrl: ${e.message}")
             null
         }
     }
     
     /**
      * Clears the location cache
      */
     fun clearLocationCache() {
         locationCache.clear()
     }
     
     /**
      * Preloads location info for multiple servers
      * @param serverAddresses List of server addresses to preload
      */
     suspend fun preloadLocations(serverAddresses: List<String>) {
         serverAddresses.forEach { address ->
             try {
                 getServerLocation(address)
             } catch (e: Exception) {
                 Log.w(TAG, "Failed to preload location for $address: ${e.message}")
             }
         }
     }
 }