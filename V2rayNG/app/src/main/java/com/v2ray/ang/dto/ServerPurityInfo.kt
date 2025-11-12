package com.v2ray.ang.dto

/**
 * Data class for server purity/cleanliness information
 * Purity score ranges from 0-100, where 100 is the best (cleanest)
 */
data class ServerPurityInfo(
    val serverAddress: String,
    val purityScore: Int = 0,
    val lastTested: Long = System.currentTimeMillis()
) {
    /**
     * Gets a human-readable purity level string
     */
    fun getPurityLevelString(): String {
        return when {
            purityScore >= 90 -> "Excellent"
            purityScore >= 75 -> "Good"
            purityScore >= 60 -> "Fair"
            purityScore >= 40 -> "Poor"
            else -> "Bad"
        }
    }
    
    /**
     * Gets the purity display string with score
     */
    fun getPurityDisplayString(): String {
        if (purityScore == 0) {
            return ""
        }
        return "$purityScore%"
    }
    
    /**
     * Gets an emoji indicator for the purity level
     */
    fun getPurityEmoji(): String {
        return when {
            purityScore >= 90 -> "🟢"
            purityScore >= 75 -> "🟡"
            purityScore >= 60 -> "🟠"
            else -> "🔴"
        }
    }
}
