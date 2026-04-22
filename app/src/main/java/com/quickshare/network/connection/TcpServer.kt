// app/src/main/java/com/quickshare/network/connection/TcpServer.kt
package com.quickshare.network.connection

import android.util.Log
import com.quickshare.network.transfer.TransferProtocol
import com.quickshare.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpServer @Inject constructor() {

    companion object {
        private const val TAG = "TcpServer"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var clientSockets = mutableListOf<Socket>()

    private val _serverEvents = MutableSharedFlow<ServerEvent>()
    val serverEvents: SharedFlow<ServerEvent> = _serverEvents.asSharedFlow()

    private val _clientConnected = MutableSharedFlow<Socket>()
    val clientConnected: SharedFlow<Socket> = _clientConnected.asSharedFlow()

    fun startServer(port: Int = Constants.TRANSFER_PORT) {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                _serverEvents.emit(ServerEvent.Started(port))
                Log.d(TAG, "Server started on port $port")

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let {
                            clientSockets.add(it)
                            _clientConnected.emit(it)
                            _serverEvents.emit(ServerEvent.ClientConnected(it.inetAddress.hostAddress))
                            Log.d(TAG, "Client connected: ${it.inetAddress.hostAddress}")

                            // Handle client in separate coroutine
                            launch {
                                handleClient(it)
                            }
                        }
                    } catch (e: IOException) {
                        if (isActive) {
                            _serverEvents.emit(ServerEvent.Error("Accept error: ${e.message}"))
                        }
                    }
                }
            } catch (e: IOException) {
                _serverEvents.emit(ServerEvent.Error("Server error: ${e.message}"))
                Log.e(TAG, "Server error", e)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use {
                val inputStream = it.getInputStream()
                val outputStream = it.getOutputStream()

                // Read protocol header
                val headerBuffer = ByteArray(TransferProtocol.HEADER_SIZE)
                var bytesRead = 0
                while (bytesRead < TransferProtocol.HEADER_SIZE) {
                    val read = inputStream.read(headerBuffer, bytesRead, TransferProtocol.HEADER_SIZE - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }

                if (bytesRead == TransferProtocol.HEADER_SIZE) {
                    val header = TransferProtocol.parseHeader(headerBuffer)
                    when (header.type) {
                        TransferProtocol.TYPE_FILE_INFO -> {
                            val fileInfo = TransferProtocol.parseFileInfo(inputStream)
                            _serverEvents.emit(ServerEvent.FileInfoReceived(fileInfo))
                        }
                        TransferProtocol.TYPE_HANDSHAKE -> {
                            val handshake = TransferProtocol.parseHandshake(inputStream)
                            _serverEvents.emit(ServerEvent.HandshakeReceived(handshake))
                        }
                        else -> {
                            // Handle other types
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _serverEvents.emit(ServerEvent.Error("Client handling error: ${e.message}"))
            Log.e(TAG, "Client handling error", e)
        } finally {
            clientSockets.remove(socket)
            _serverEvents.emit(ServerEvent.ClientDisconnected(socket.inetAddress.hostAddress))
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        serverSocket?.close()
        clientSockets.forEach { it.close() }
        clientSockets.clear()
        CoroutineScope(Dispatchers.Main).launch {
            _serverEvents.emit(ServerEvent.Stopped)
        }
    }

    fun isRunning(): Boolean = serverJob?.isActive == true
}

sealed class ServerEvent {
    data class Started(val port: Int) : ServerEvent()
    data class ClientConnected(val address: String) : ServerEvent()
    data class ClientDisconnected(val address: String) : ServerEvent()
    data class HandshakeReceived(val handshake: TransferProtocol.Handshake) : ServerEvent()
    data class FileInfoReceived(val fileInfo: TransferProtocol.FileInfo) : ServerEvent()
    data class Error(val message: String) : ServerEvent()
    object Stopped : ServerEvent()
}