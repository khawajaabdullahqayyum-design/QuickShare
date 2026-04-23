// app/src/main/java/com/quickshare/network/connection/ConnectionManager.kt
package com.quickshare.network.connection

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    val tcpServer: TcpServer,
    val tcpClient: TcpClient
) {
    fun startServer(port: Int) = tcpServer.startServer(port)
    fun stopServer() = tcpServer.stopServer()
    suspend fun connect(host: String, port: Int) = tcpClient.connect(host, port)
    fun disconnect() = tcpClient.disconnect()
}