// app/src/main/java/com/quickshare/ui/common/Extensions.kt
package com.quickshare.ui.common

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

fun <T> Flow<T>.asLiveData(): LiveData<T> = liveData {
    collect { emit(it) }
}.apply { flowOn(Dispatchers.Main) }

fun Uri.getFileName(contentResolver: ContentResolver): String {
    var name = ""
    contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name.ifEmpty { this.lastPathSegment ?: "unknown" }
}

fun Uri.getFileSize(contentResolver: ContentResolver): Long {
    var size = 0L
    contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            size = cursor.getLong(sizeIndex)
        }
    }
    return size
}

fun String.getMimeType(): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(this)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}
