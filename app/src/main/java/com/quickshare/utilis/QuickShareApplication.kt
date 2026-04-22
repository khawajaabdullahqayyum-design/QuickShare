// app/src/main/java/com/quickshare/QuickShareApplication.kt
package com.quickshare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.quickshare.utils.Constants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class QuickShareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transferChannel = NotificationChannel(
                Constants.CHANNEL_TRANSFER,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of ongoing file transfers"
                setShowBadge(false)
            }

            val connectionChannel = NotificationChannel(
                Constants.CHANNEL_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows connection status with other devices"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(transferChannel)
            notificationManager.createNotificationChannel(connectionChannel)
        }
    }
}