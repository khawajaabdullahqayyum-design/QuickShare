// app/src/main/java/com/quickshare/utils/FileUtils.kt
package com.quickshare.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun getAppExternalFilesDir(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    fun createTempFile(context: Context, fileName: String): File {
        val dir = File(getAppExternalFilesDir(context), "temp")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getMimeType(uri: Uri, contentResolver: ContentResolver): String {
        return contentResolver.getType(uri) ?: "application/octet-stream"
    }

    fun isImageFile(mimeType: String): Boolean = mimeType.startsWith("image/")
    fun isVideoFile(mimeType: String): Boolean = mimeType.startsWith("video/")
    fun isApkFile(mimeType: String): Boolean = mimeType == "application/vnd.android.package-archive"
    fun isDocumentFile(mimeType: String): Boolean = 
        mimeType == "application/pdf" || 
        mimeType == "application/msword" ||
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        mimeType == "text/plain"
}