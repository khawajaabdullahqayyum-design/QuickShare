// app/src/main/java/com/quickshare/data/repository/TransferRepository.kt
package com.quickshare.data.repository

import com.quickshare.data.model.TransferItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor() {

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    fun addTransfer(item: TransferItem) {
        _transfers.value = _transfers.value + item
    }

    fun updateProgress(fileId: String, progress: Int, transferredBytes: Long, speed: Long) {
        _transfers.value = _transfers.value.map { item ->
            if (item.fileId == fileId) {
                item.copy(progress = progress, transferredBytes = transferredBytes, speed = speed)
            } else item
        }
    }

    fun completeTransfer(fileId: String, success: Boolean) {
        _transfers.value = _transfers.value.map { item ->
            if (item.fileId == fileId) {
                item.copy(status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED)
            } else item
        }
    }

    fun clearCompleted() {
        _transfers.value = _transfers.value.filter { it.status == TransferStatus.TRANSFERRING }
    }
}