// app/src/main/java/com/quickshare/network/connection/TcpClient.kt
package com.quickshare.network.connection

import android.util.Log
import com.quickshare.network.transfer.TransferProtocol
import com.quickshare.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpClient @Inject constructor() {

    companion object {
        private const val TAG = "TcpClient"
    }

    private var socket: Socket? = null

    suspend fun connect(host: String, port: Int = Constants.TRANSFER_PORT): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                socket = Socket(host, port).apply {
                    soTimeout = Constants.SOCKET_TIMEOUT
                    tcpNoDelay = true // Disable Nagle's algorithm for faster transfer
                    sendBufferSize = Constants.BUFFER_SIZE
                    receiveBufferSize = Constants.BUFFER_SIZE
                }
                Log.d(TAG, "Connected to $host:$port")
                ConnectionResult.Success(socket!!)
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                ConnectionResult.Failed(e.message ?: "Connection failed")
            }
        }
    }

    suspend fun sendHandshake(deviceName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket?.let {
                    val handshake = TransferProtocol.Handshake(
                        deviceName = deviceName,
                        version = 1,
                        supportedFormats = listOf("image", "video", "document", "apk")
                    )
                    val data = TransferProtocol.createHandshakePacket(handshake)
                    val output = BufferedOutputStream(it.getOutputStream())
                    output.write(data)
                    output.flush()
                    true
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send handshake", e)
                false
            }
        }
    }

    suspend fun sendFileInfo(fileInfo: TransferProtocol.FileInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket?.let {
                    val data = TransferProtocol.createFileInfoPacket(fileInfo)
                    val output = BufferedOutputStream(it.getOutputStream())
                    output.write(data)
                    output.flush()
                    true
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file info", e)
                false
            }
        }
    }

    fun getOutputStream() = socket?.getOutputStream()
    fun getInputStream() = socket?.getInputStream()

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        socket = null
    }

    fun isConnected(): Boolean = socket?.isConnected == true && !socket!!.isClosed
}

sealed class ConnectionResult {
    data class Success(val socket: Socket) : ConnectionResult()
    data class Failed(val reason: String) : ConnectionResult()
}