// app/src/main/java/com/quickshare/data/model/Device.kt
package com.quickshare.data.model

import android.net.wifi.p2p.WifiP2pDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val ipAddress: String? = null,
    val port: Int = 8888,
    var status: ConnectionStatus = ConnectionStatus.AVAILABLE,
    val deviceType: DeviceType = DeviceType.ANDROID
) : Parcelable {
    val displayName: String
        get() = name.ifEmpty { "Unknown Device" }
}

enum class ConnectionStatus {
    AVAILABLE,
    CONNECTING,
    CONNECTED,
    FAILED
}

enum class DeviceType {
    ANDROID,
    IOS,
    WINDOWS,
    UNKNOWN
}

// Helper extension to convert from Wi-Fi Direct device
fun WifiP2pDevice.toDevice(): Device {
    return Device(
        id = deviceAddress,
        name = deviceName,
        address = deviceAddress,
        status = when (status) {
            WifiP2pDevice.CONNECTED -> ConnectionStatus.CONNECTED
            WifiP2pDevice.INVITED -> ConnectionStatus.CONNECTING
            WifiP2pDevice.FAILED -> ConnectionStatus.FAILED
            else -> ConnectionStatus.AVAILABLE
        }
    )
}