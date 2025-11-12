package com.v2ray.ang.dto

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var locationInfo: ServerLocationInfo? = null,
    var purityInfo: ServerPurityInfo? = null
) {
    fun getTestDelayString(): String {
        if (testDelayMillis == 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }
    
    fun getLocationDisplayString(): String {
        return locationInfo?.getLocationString() ?: ""
    }
    
    fun getPurityDisplayString(): String {
        return purityInfo?.getPurityDisplayString() ?: ""
    }
}
