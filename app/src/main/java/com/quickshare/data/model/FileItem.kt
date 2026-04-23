// app/src/main/java/com/quickshare/data/model/FileItem.kt
package com.quickshare.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val type: FileType,
    var isSelected: Boolean = false
) : Parcelable {
    
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    
    val extension: String
        get() = name.substringAfterLast(".", "")
}

enum class FileType {
    IMAGE,
    VIDEO,
    DOCUMENT,
    APK,
    OTHER
}

enum class TransferDirection {
    SENDING,
    RECEIVING
}

enum class TransferStatus {
    PENDING,
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Parcelize
data class TransferItem(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: TransferDirection,
    var progress: Int = 0,
    var transferredBytes: Long = 0,
    var status: TransferStatus = TransferStatus.PENDING,
    var speed: Long = 0 // bytes per second
) : Parcelable {
    
    val id: String = fileId
    
    val formattedSpeed: String
        get() = when {
            speed < 1024 -> "$speed B/s"
            speed < 1024 * 1024 -> "${speed / 1024} KB/s"
            else -> "${speed / (1024 * 1024)} MB/s"
        }
}
