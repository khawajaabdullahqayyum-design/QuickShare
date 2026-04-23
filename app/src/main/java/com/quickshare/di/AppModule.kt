// app/src/main/java/com/quickshare/di/AppModule.kt
package com.quickshare.di

import android.content.Context
import com.quickshare.network.connection.TcpClient
import com.quickshare.network.connection.TcpServer
import com.quickshare.network.discovery.WifiDirectManager
import com.quickshare.network.security.AesEncryption
import com.quickshare.network.transfer.ChunkedTransfer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWifiDirectManager(@ApplicationContext context: Context): WifiDirectManager {
        return WifiDirectManager(context)
    }

    @Provides
    @Singleton
    fun provideTcpServer(): TcpServer = TcpServer()

    @Provides
    @Singleton
    fun provideTcpClient(): TcpClient = TcpClient()

    @Provides
    @Singleton
    fun provideAesEncryption(): AesEncryption = AesEncryption()

    @Provides
    @Singleton
    fun provideChunkedTransfer(aesEncryption: AesEncryption): ChunkedTransfer {
        return ChunkedTransfer(aesEncryption)
    }
}