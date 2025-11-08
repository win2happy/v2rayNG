package com.v2ray.ang.dto

data class RecentConnection(
    val guid: String,
    val name: String,
    val server: String,
    val port: String,
    val configType: String,
    val timestamp: Long,
    val isActive: Boolean = false
)
