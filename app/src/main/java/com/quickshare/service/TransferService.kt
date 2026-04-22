// app/src/main/java/com/quickshare/service/TransferService.kt
package com.quickshare.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.quickshare.R
import com.quickshare.data.model.TransferItem
import com.quickshare.data.model.TransferStatus
import com.quickshare.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class TransferService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeTransfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val activeTransfers: StateFlow<List<TransferItem>> = _activeTransfers.asStateFlow()

    private val _serviceState = MutableStateFlow(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_SERVER -> startServer()
            Constants.ACTION_STOP_SERVER -> stopServer()
        }

        startForeground(NOTIFICATION_ID, createForegroundNotification())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }

    fun addTransfer(transferItem: TransferItem) {
        val current = _activeTransfers.value.toMutableList()
        current.add(transferItem)
        _activeTransfers.value = current
        updateNotification()
    }

    fun updateTransferProgress(fileId: String, progress: Int, transferredBytes: Long, speed: Long) {
        val current = _activeTransfers.value.toMutableList()
        val index = current.indexOfFirst { it.fileId == fileId }
        if (index != -1) {
            current[index] = current[index].copy(
                progress = progress,
                transferredBytes = transferredBytes,
                speed = speed,
                status = TransferStatus.TRANSFERRING
            )
            _activeTransfers.value = current
            updateNotification()
        }
    }

    fun completeTransfer(fileId: String, success: Boolean) {
        val current = _activeTransfers.value.toMutableList()
        val index = current.indexOfFirst { it.fileId == fileId }
        if (index != -1) {
            current[index] = current[index].copy(
                status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED,
                progress = if (success) 100 else current[index].progress
            )
            _activeTransfers.value = current
            updateNotification()

            // Show completion notification
            showCompletionNotification(current[index])
        }

        // Clean up completed transfers
        _activeTransfers.value = current.filter {
            it.status != TransferStatus.COMPLETED && it.status != TransferStatus.FAILED
        }
    }

    private fun createForegroundNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, Constants.CHANNEL_TRANSFER)
            .setContentTitle("QuickShare")
            .setContentText("Ready to transfer files")
            .setSmallIcon(R.drawable.ic_transfer)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val transfers = _activeTransfers.value
        if (transfers.isEmpty()) {
            notificationManager?.notify(NOTIFICATION_ID, createForegroundNotification())
            return
        }

        val totalProgress = if (transfers.isNotEmpty()) {
            transfers.sumOf { it.progress } / transfers.size
        } else 0

        val contentText = when (transfers.size) {
            1 -> "Transferring: ${transfers[0].fileName} (${transfers[0].progress}%)"
            else -> "Transferring ${transfers.size} files ($totalProgress%)"
        }

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_TRANSFER)
            .setContentTitle("QuickShare Transfer")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_transfer)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, totalProgress, false)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(transferItem: TransferItem) {
        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_TRANSFER)
            .setContentTitle("Transfer Complete")
            .setContentText("${transferItem.fileName} ${if (transferItem.status == TransferStatus.COMPLETED) "completed" else "failed"}")
            .setSmallIcon(if (transferItem.status == TransferStatus.COMPLETED) R.drawable.ic_success else R.drawable.ic_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(transferItem.fileId.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.CHANNEL_TRANSFER,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startServer() {
        _serviceState.value = ServiceState.SERVER_RUNNING
        // Server logic handled by TcpServer
    }

    private fun stopServer() {
        _serviceState.value = ServiceState.IDLE
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}

enum class ServiceState {
    IDLE,
    SERVER_RUNNING,
    TRANSFERRING
}