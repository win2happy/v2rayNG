package com.v2ray.ang.dto

/**
 * Backup data structure for import/export functionality
 */
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val subscriptions: List<Pair<String, SubscriptionItem>>? = null,
    val servers: List<Pair<String, ProfileItem>>? = null,
    val serverRaws: Map<String, String>? = null,
    val serverAffiliations: Map<String, ServerAffiliationInfo>? = null,
    val settings: Map<String, Any>? = null,
    val selectedServer: String? = null,
    val serverList: List<String>? = null
)
