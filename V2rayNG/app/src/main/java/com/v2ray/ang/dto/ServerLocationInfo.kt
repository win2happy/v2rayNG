 package com.v2ray.ang.dto
 
 data class ServerLocationInfo(
     val serverAddress: String,
     val country: String? = null,
     val countryCode: String? = null,
     val region: String? = null,
     val city: String? = null,
     val lastUpdated: Long = System.currentTimeMillis()
 ) {
     fun getLocationString(): String {
         return when {
             !city.isNullOrEmpty() && !country.isNullOrEmpty() -> "$city, ${countryCode ?: country}"
             !country.isNullOrEmpty() -> countryCode ?: country
             else -> "Unknown"
         }
     }
 }