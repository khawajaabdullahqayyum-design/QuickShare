// app/src/main/java/com/quickshare/data/model/TransferStatus.kt
package com.quickshare.data.model

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