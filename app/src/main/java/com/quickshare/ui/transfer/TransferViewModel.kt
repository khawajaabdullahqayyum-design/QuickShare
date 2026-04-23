// app/src/main/java/com/quickshare/ui/transfer/TransferViewModel.kt
package com.quickshare.ui.transfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.quickshare.data.model.*
import com.quickshare.network.connection.ConnectionResult
import com.quickshare.network.connection.TcpClient
import com.quickshare.network.connection.TcpServer
import com.quickshare.network.discovery.WifiDirectManager
import com.quickshare.network.transfer.ChunkedTransfer
import com.quickshare.network.transfer.TransferProtocol
import com.quickshare.network.transfer.TransferState
import com.quickshare.ui.common.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val app: Application,
    private val wifiDirectManager: WifiDirectManager,
    private val tcpServer: TcpServer,
    private val tcpClient: TcpClient,
    private val chunkedTransfer: ChunkedTransfer
) : AndroidViewModel(app) {

    val devices: LiveData<List<Device>> = wifiDirectManager.devices.asLiveData()
    val isDiscovering: LiveData<Boolean> = wifiDirectManager.isDiscovering.asLiveData()
    val connectionStatus: LiveData<ConnectionStatus> = wifiDirectManager.connectionStatus.asLiveData()

    private val _selectedFiles = MutableLiveData<List<FileItem>>(emptyList())
    val selectedFiles: LiveData<List<FileItem>> = _selectedFiles

    private val _transferItems = MutableLiveData<List<TransferItem>>(emptyList())
    val transferItems: LiveData<List<TransferItem>> = _transferItems

    private val _transferState = MutableLiveData<TransferState>()
    val transferState: LiveData<TransferState> = _transferState

    fun startDiscovery() {
        viewModelScope.launch {
            wifiDirectManager.startDiscovery().collect { result ->
                // handle discovery result if needed
            }
        }
    }

    fun stopDiscovery() {
        wifiDirectManager.stopDiscovery()
    }

    fun refreshDiscovery() {
        stopDiscovery()
        startDiscovery()
    }

    fun connectToDevice(device: Device) {
        viewModelScope.launch {
            wifiDirectManager.connectToDevice(device).collect { result ->
                when (result) {
                    is ConnectionResult.Connected -> {
                        // After Wi-Fi Direct connected, connect TCP
                        val groupOwnerAddress = getGroupOwnerIp()
                        groupOwnerAddress?.let { ip ->
                            val connection = tcpClient.connect(ip)
                            if (connection is ConnectionResult.Success) {
                                tcpClient.sendHandshake(getDeviceName())
                                startFileTransfer()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startServer() {
        tcpServer.startServer()
    }

    fun setSelectedFiles(files: List<FileItem>) {
        _selectedFiles.value = files
    }

    private suspend fun startFileTransfer() {
        val files = _selectedFiles.value ?: return
        val transferItems = files.map { file ->
            TransferItem(
                fileId = file.uri.toString(),
                fileName = file.name,
                fileSize = file.size,
                mimeType = file.mimeType,
                direction = TransferDirection.SENDING,
                status = TransferStatus.PENDING
            )
        }
        _transferItems.value = transferItems

        transferItems.forEach { item ->
            sendFile(item)
        }
    }

    private suspend fun sendFile(transferItem: TransferItem) {
        val fileItem = _selectedFiles.value?.find { it.uri.toString() == transferItem.fileId } ?: return

        withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(fileItem.uri)?.use { inputStream ->
                val outputStream = tcpClient.getOutputStream() ?: return@withContext

                val fileInfo = TransferProtocol.FileInfo(
                    fileId = transferItem.fileId,
                    fileName = fileItem.name,
                    fileSize = fileItem.size,
                    mimeType = fileItem.mimeType
                )

                chunkedTransfer.sendFile(
                    fileInfo = fileInfo,
                    inputStream = inputStream,
                    outputStream = outputStream
                ) { progress ->
                    _transferState.postValue(TransferState.Transferring(transferItem.fileId, progress.progress))
                }
            }
        }
    }

    private fun getGroupOwnerIp(): String? {
        // This would be obtained from WifiP2pInfo after connection
        return "192.168.49.1" // Placeholder
    }

    private fun getDeviceName(): String {
        return android.os.Build.MODEL
    }

    fun cleanup() {
        wifiDirectManager.cleanup()
        tcpServer.stopServer()
        tcpClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
