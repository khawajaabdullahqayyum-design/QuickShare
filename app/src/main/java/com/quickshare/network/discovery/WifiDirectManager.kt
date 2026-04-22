// app/src/main/java/com/quickshare/network/discovery/WifiDirectManager.kt
package com.quickshare.network.discovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.*
import android.os.Build
import android.os.Looper
import com.quickshare.data.model.ConnectionStatus
import com.quickshare.data.model.Device
import com.quickshare.data.model.toDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectManager @Inject constructor(
    private val context: Context
) {
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: Channel? = null
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.AVAILABLE)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val peerListListener = PeerListListener { peerList ->
        val devices = peerList.deviceList.map { it.toDevice() }
        _devices.value = devices
    }

    private val connectionInfoListener = ConnectionInfoListener { wifiP2pInfo ->
        // Handle connection info - group owner IP can be obtained here
        wifiP2pInfo?.let {
            if (it.groupFormed && it.isGroupOwner) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(EXTRA_WIFI_STATE, -1)
                    _isDiscovering.value = state == WIFI_P2P_STATE_ENABLED
                }
                WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager?.requestPeers(channel, peerListListener)
                }
                WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    wifiP2pManager?.requestConnectionInfo(channel, connectionInfoListener)
                }
                WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // This device's details changed
                }
            }
        }
    }

    fun initialize(): Boolean {
        if (wifiP2pManager == null) return false

        channel = wifiP2pManager?.initialize(
            context,
            Looper.getMainLooper()
        ) {
            // Channel disconnected
        }

        val intentFilter = IntentFilter().apply {
            addAction(WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, intentFilter)
        }

        return true
    }

    fun startDiscovery(): Flow<DiscoveryResult> = callbackFlow {
        _isDiscovering.value = true

        val actionListener = object : ActionListener {
            override fun onSuccess() {
                trySend(DiscoveryResult.DiscoveryStarted)
            }

            override fun onFailure(reason: Int) {
                trySend(DiscoveryResult.DiscoveryFailed(getFailureReason(reason)))
                _isDiscovering.value = false
            }
        }

        wifiP2pManager?.discoverPeers(channel, actionListener)

        awaitClose {
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        wifiP2pManager?.stopPeerDiscovery(channel, null)
        _isDiscovering.value = false
    }

    fun connectToDevice(device: Device): Flow<ConnectionResult> = callbackFlow {
        _connectionStatus.value = ConnectionStatus.CONNECTING

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }

        val actionListener = object : ActionListener {
            override fun onSuccess() {
                trySend(ConnectionResult.Connecting)
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = ConnectionStatus.FAILED
                trySend(ConnectionResult.Failed(getFailureReason(reason)))
                close()
            }
        }

        wifiP2pManager?.connect(channel, config, actionListener)

        awaitClose {
            disconnect()
        }
    }

    fun disconnect() {
        wifiP2pManager?.removeGroup(channel, null)
        _connectionStatus.value = ConnectionStatus.AVAILABLE
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        disconnect()
    }

    private fun getFailureReason(reason: Int): String = when (reason) {
        P2P_UNSUPPORTED -> "Wi-Fi Direct not supported"
        ERROR -> "Internal error"
        BUSY -> "Framework busy, try again"
        else -> "Unknown error"
    }
}

sealed class DiscoveryResult {
    object DiscoveryStarted : DiscoveryResult()
    data class DiscoveryFailed(val reason: String) : DiscoveryResult()
    object DiscoveryStopped : DiscoveryResult()
}

sealed class ConnectionResult {
    object Connecting : ConnectionResult()
    object Connected : ConnectionResult()
    data class Failed(val reason: String) : ConnectionResult()
}