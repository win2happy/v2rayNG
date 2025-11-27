package com.v2ray.ang.handler

import android.content.Context
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.BackupData
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.ServerAffiliationInfo
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import java.io.File

object BackupManager {

    /**
     * Export all data to JSON
     */
    fun exportAllData(): BackupData {
        return BackupData(
            subscriptions = MmkvManager.decodeSubscriptions(),
            servers = exportServers(),
            serverRaws = exportServerRaws(),
            serverAffiliations = exportServerAffiliations(),
            settings = exportSettings(),
            selectedServer = MmkvManager.getSelectServer(),
            serverList = MmkvManager.decodeServerList()
        )
    }

    /**
     * Export subscriptions only
     */
    fun exportSubscriptions(): BackupData {
        return BackupData(
            subscriptions = MmkvManager.decodeSubscriptions()
        )
    }

    /**
     * Export servers only
     */
    fun exportServers(): List<Pair<String, ProfileItem>> {
        val servers = mutableListOf<Pair<String, ProfileItem>>()
        MmkvManager.decodeServerList().forEach { guid ->
            val config = MmkvManager.decodeServerConfig(guid)
            if (config != null) {
                servers.add(Pair(guid, config))
            }
        }
        return servers
    }

    /**
     * Export server raws
     */
    private fun exportServerRaws(): Map<String, String> {
        val serverRaws = mutableMapOf<String, String>()
        MmkvManager.decodeServerList().forEach { guid ->
            val raw = MmkvManager.decodeServerRaw(guid)
            if (raw != null) {
                serverRaws[guid] = raw
            }
        }
        return serverRaws
    }

    /**
     * Export server affiliations
     */
    private fun exportServerAffiliations(): Map<String, ServerAffiliationInfo> {
        val affiliations = mutableMapOf<String, ServerAffiliationInfo>()
        MmkvManager.decodeServerList().forEach { guid ->
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            if (aff != null) {
                affiliations[guid] = aff
            }
        }
        return affiliations
    }

    /**
     * Export settings only
     */
    fun exportSettings(): Map<String, Any> {
        val settings = mutableMapOf<String, Any>()
        
        // String type settings
        val stringKeys = listOf(
            AppConfig.PREF_MODE,
            AppConfig.PREF_VPN_DNS,
            AppConfig.PREF_REMOTE_DNS,
            AppConfig.PREF_DOMESTIC_DNS,
            AppConfig.PREF_SOCKS_PORT,
            AppConfig.PREF_LOGLEVEL,
            AppConfig.PREF_MUX_CONCURRENCY,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.PREF_LOCAL_DNS_PORT,
            AppConfig.PREF_VPN_MTU
        )
        
        stringKeys.forEach { key ->
            val value = MmkvManager.decodeSettingsString(key)
            if (value != null) {
                settings[key] = value
            }
        }
        
        // Boolean type settings
        val boolKeys = listOf(
            AppConfig.PREF_SPEED_ENABLED,
            AppConfig.PREF_SNIFFING_ENABLED,
            AppConfig.PREF_LOCAL_DNS_ENABLED,
            AppConfig.PREF_FAKE_DNS_ENABLED,
            AppConfig.PREF_ALLOW_INSECURE,
            AppConfig.PREF_MUX_ENABLED,
            AppConfig.PREF_PER_APP_PROXY,
            AppConfig.PREF_PREFER_IPV6,
            AppConfig.PREF_CONFIRM_REMOVE,
            AppConfig.PREF_START_SCAN_IMMEDIATE,
            AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
            AppConfig.PREF_PROXY_SHARING
        )
        
        boolKeys.forEach { key ->
            // Always export boolean settings, even if false
            settings[key] = MmkvManager.decodeSettingsBool(key, false)
        }
        
        // Export string set settings
        val bypassApps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_BYPASS_APPS)
        if (bypassApps != null && bypassApps.isNotEmpty()) {
            settings[AppConfig.PREF_BYPASS_APPS] = bypassApps.toList()
        }

        // Export routing rulesets
        val rulesets = MmkvManager.decodeRoutingRulesets()
        if (rulesets != null) {
            settings[AppConfig.PREF_ROUTING_RULESET] = JsonUtil.toJson(rulesets) ?: ""
        }

        return settings
    }

    /**
     * Save backup data to file
     */
    fun saveBackupToFile(context: Context, backupData: BackupData, filename: String): File? {
        try {
            val json = JsonUtil.toJsonPretty(backupData)
            if (json == null) {
                Log.e(AppConfig.TAG, "Failed to serialize backup data")
                return null
            }

            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val file = File(backupDir, filename)
            file.writeText(json)
            return file
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to save backup file", e)
            return null
        }
    }

    /**
     * Import all data from backup
     */
    fun importAllData(backupData: BackupData, replace: Boolean = false): Pair<Int, Int> {
        var subsCount = 0
        var serversCount = 0

        try {
            if (replace) {
                // Clear existing data
                MmkvManager.removeAllServer()
            }

            // Import subscriptions
            backupData.subscriptions?.forEach { (guid, subItem) ->
                MmkvManager.encodeSubscription(if (replace) "" else guid, subItem)
                subsCount++
            }

            // Import servers
            backupData.servers?.forEach { (guid, config) ->
                val newGuid = if (replace) "" else guid
                val key = MmkvManager.encodeServerConfig(newGuid, config)
                
                // Import server raw
                backupData.serverRaws?.get(guid)?.let { raw ->
                    MmkvManager.encodeServerRaw(key, raw)
                }
                
                // Import server affiliation
                backupData.serverAffiliations?.get(guid)?.let { aff ->
                    aff.testDelayMillis?.let { delay ->
                        MmkvManager.encodeServerTestDelayMillis(key, delay)
                    }
                    aff.locationInfo?.let { location ->
                        MmkvManager.encodeServerLocationInfo(key, location)
                    }
                    aff.purityInfo?.let { purity ->
                        MmkvManager.encodeServerPurityInfo(key, purity)
                    }
                }
                
                serversCount++
            }

            // Import server list order
            if (replace && backupData.serverList != null) {
                MmkvManager.encodeServerList(backupData.serverList.toMutableList())
            }

            // Import selected server
            if (replace && backupData.selectedServer != null) {
                MmkvManager.setSelectServer(backupData.selectedServer)
            }

            // Import settings
            importSettings(backupData.settings)

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import data", e)
        }

        return Pair(subsCount, serversCount)
    }

    /**
     * Import subscriptions only
     */
    fun importSubscriptions(backupData: BackupData): Int {
        var count = 0
        try {
            backupData.subscriptions?.forEach { (_, subItem) ->
                MmkvManager.encodeSubscription("", subItem)
                count++
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import subscriptions", e)
        }
        return count
    }

    /**
     * Import servers only
     */
    fun importServers(backupData: BackupData): Int {
        var count = 0
        try {
            backupData.servers?.forEach { (guid, config) ->
                val key = MmkvManager.encodeServerConfig("", config)
                
                // Import server raw
                backupData.serverRaws?.get(guid)?.let { raw ->
                    MmkvManager.encodeServerRaw(key, raw)
                }
                
                count++
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import servers", e)
        }
        return count
    }

    /**
     * Import settings only
     */
    fun importSettings(settings: Map<String, Any>?): Int {
        var count = 0
        try {
            settings?.forEach { (key, value) ->
                when (value) {
                    is String -> {
                        MmkvManager.encodeSettings(key, value)
                        count++
                    }
                    is Boolean -> {
                        MmkvManager.encodeSettings(key, value)
                        count++
                    }
                    is Int -> {
                        MmkvManager.encodeSettings(key, value)
                        count++
                    }
                    is Double -> {
                        MmkvManager.encodeSettings(key, value.toInt())
                        count++
                    }
                    is List<*> -> {
                        // Handle string set (bypass apps)
                        @Suppress("UNCHECKED_CAST")
                        val stringSet = (value as? List<String>)?.toMutableSet()
                        if (stringSet != null) {
                            MmkvManager.encodeSettings(key, stringSet)
                            count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import settings", e)
        }
        return count
    }

    /**
     * Load backup data from file
     */
    fun loadBackupFromFile(file: File): BackupData? {
        try {
            val json = file.readText()
            return JsonUtil.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to load backup file", e)
            return null
        }
    }
}
