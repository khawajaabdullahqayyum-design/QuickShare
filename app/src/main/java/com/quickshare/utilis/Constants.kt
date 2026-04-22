// app/src/main/java/com/quickshare/utils/Constants.kt
package com.quickshare.utils

object Constants {
    // Service Discovery
    const val SERVICE_TYPE = "_quickshare._tcp."
    const val SERVICE_NAME_PREFIX = "QuickShare-"
    const val DISCOVERY_PORT = 8888
    const val TRANSFER_PORT = 8889

    // Transfer Settings
    const val CHUNK_SIZE = 64 * 1024 // 64 KB chunks
    const val BUFFER_SIZE = 256 * 1024 // 256 KB buffer
    const val SOCKET_TIMEOUT = 30000 // 30 seconds
    const val MAX_RETRY_COUNT = 3

    // Notification Channels
    const val CHANNEL_TRANSFER = "transfer_channel"
    const val CHANNEL_CONNECTION = "connection_channel"

    // Shared Preferences
    const val PREFS_NAME = "quickshare_prefs"
    const val KEY_DEVICE_NAME = "device_name"

    // Intent Actions
    const val ACTION_START_SERVER = "com.quickshare.START_SERVER"
    const val ACTION_STOP_SERVER = "com.quickshare.STOP_SERVER"
    const val ACTION_TRANSFER_UPDATE = "com.quickshare.TRANSFER_UPDATE"
}