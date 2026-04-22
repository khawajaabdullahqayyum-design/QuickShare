// app/src/main/java/com/quickshare/network/transfer/ChunkedTransfer.kt
package com.quickshare.network.transfer

import android.util.Log
import com.quickshare.network.security.AesEncryption
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.Socket
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChunkedTransfer @Inject constructor(
    private val encryption: AesEncryption
) {

    companion object {
        private const val TAG = "ChunkedTransfer"
        private const val CHUNK_SIZE = 64 * 1024 // 64KB
        private const val MAX_CONCURRENT_CHUNKS = 4
    }

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private var transferJob: Job? = null
    private val transferScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun sendFile(
        fileInfo: TransferProtocol.FileInfo,
        inputStream: InputStream,
        outputStream: OutputStream,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult {
        return withContext(Dispatchers.IO) {
            try {
                _transferState.value = TransferState.Transferring(fileInfo.fileId, 0)

                val fileBytes = inputStream.readBytes()
                val totalChunks = ((fileBytes.size + CHUNK_SIZE - 1) / CHUNK_SIZE)
                var sentBytes = 0L
                val startTime = System.currentTimeMillis()

                // Send file info first
                val fileInfoPacket = TransferProtocol.createFileInfoPacket(fileInfo)
                outputStream.write(fileInfoPacket)
                outputStream.flush()

                // Calculate total checksum
                val md5 = MessageDigest.getInstance("MD5")
                val totalChecksum = md5.digest(fileBytes).joinToString("") { "%02x".format(it) }

                // Send chunks
                for (chunkIndex in 0 until totalChunks) {
                    val start = chunkIndex * CHUNK_SIZE
                    val end = minOf(start + CHUNK_SIZE, fileBytes.size)
                    val chunkData = fileBytes.copyOfRange(start, end)

                    // Encrypt chunk
                    val encryptedChunk = encryption.encrypt(chunkData)

                    val chunk = TransferProtocol.ChunkData(
                        fileId = fileInfo.fileId,
                        chunkIndex = chunkIndex,
                        offset = start.toLong(),
                        length = chunkData.size,
                        data = encryptedChunk,
                        checksum = md5.digest(chunkData).joinToString("") { "%02x".format(it) }
                    )

                    val chunkPacket = TransferProtocol.createChunkPacket(chunk)
                    outputStream.write(chunkPacket)
                    outputStream.flush()

                    sentBytes += chunkData.size
                    val progress = ((sentBytes.toFloat() / fileBytes.size) * 100).toInt()
                    val elapsed = System.currentTimeMillis() - startTime
                    val speed = if (elapsed > 0) (sentBytes * 1000 / elapsed) else 0L

                    _transferState.value = TransferState.Transferring(fileInfo.fileId, progress)
                    onProgress(TransferProgress(
                        fileId = fileInfo.fileId,
                        progress = progress,
                        transferredBytes = sentBytes,
                        totalBytes = fileBytes.size.toLong(),
                        speed = speed
                    ))
                }

                // Send completion packet
                val completePacket = TransferProtocol.createPacket(
                    TransferProtocol.TYPE_TRANSFER_COMPLETE,
                    byteArrayOf()
                )
                outputStream.write(completePacket)
                outputStream.flush()

                _transferState.value = TransferState.Completed(fileInfo.fileId, totalChecksum)
                TransferResult.Success(fileInfo.fileId, totalChecksum)

            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
                _transferState.value = TransferState.Failed(fileInfo.fileId, e.message ?: "Unknown error")
                TransferResult.Failed(e.message ?: "Transfer failed")
            }
        }
    }

    suspend fun receiveFile(
        inputStream: InputStream,
        outputStream: OutputStream,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult {
        return withContext(Dispatchers.IO) {
            try {
                // Read file info first
                val headerBuffer = ByteArray(TransferProtocol.HEADER_SIZE)
                inputStream.read(headerBuffer)
                val header = TransferProtocol.parseHeader(headerBuffer)

                if (header.type != TransferProtocol.TYPE_FILE_INFO) {
                    return@withContext TransferResult.Failed("Expected file info packet")
                }

                val fileInfo = TransferProtocol.parseFileInfo(inputStream)
                _transferState.value = TransferState.Transferring(fileInfo.fileId, 0)

                val receivedBytes = mutableListOf<Byte>()
                val startTime = System.currentTimeMillis()
                var totalReceived = 0L

                while (true) {
                    val chunkHeaderBuffer = ByteArray(TransferProtocol.HEADER_SIZE)
                    val bytesRead = inputStream.read(chunkHeaderBuffer)
                    if (bytesRead <= 0) break

                    val chunkHeader = TransferProtocol.parseHeader(chunkHeaderBuffer)

                    when (chunkHeader.type) {
                        TransferProtocol.TYPE_CHUNK_DATA -> {
                            val chunkData = parseChunkData(inputStream)
                            chunkData?.let {
                                // Decrypt chunk
                                val decryptedData = encryption.decrypt(it.data)
                                receivedBytes.addAll(decryptedData.toList())
                                totalReceived += it.length

                                // Send ACK
                                val ack = TransferProtocol.ChunkAck(
                                    fileId = it.fileId,
                                    chunkIndex = it.chunkIndex,
                                    success = true
                                )
                                // Note: In a real implementation, you'd send ACK back

                                val progress = if (fileInfo.fileSize > 0) {
                                    ((totalReceived.toFloat() / fileInfo.fileSize) * 100).toInt()
                                } else 0

                                val elapsed = System.currentTimeMillis() - startTime
                                val speed = if (elapsed > 0) (totalReceived * 1000 / elapsed) else 0L

                                _transferState.value = TransferState.Transferring(fileInfo.fileId, progress)
                                onProgress(TransferProgress(
                                    fileId = fileInfo.fileId,
                                    progress = progress,
                                    transferredBytes = totalReceived,
                                    totalBytes = fileInfo.fileSize,
                                    speed = speed
                                ))
                            }
                        }
                        TransferProtocol.TYPE_TRANSFER_COMPLETE -> {
                            break
                        }
                    }
                }

                // Write received data
                outputStream.write(receivedBytes.toByteArray())
                outputStream.flush()

                // Verify checksum
                val md5 = MessageDigest.getInstance("MD5")
                val receivedChecksum = md5.digest(receivedBytes.toByteArray())
                    .joinToString("") { "%02x".format(it) }

                if (receivedChecksum == fileInfo.checksum) {
                    _transferState.value = TransferState.Completed(fileInfo.fileId, receivedChecksum)
                    TransferResult.Success(fileInfo.fileId, receivedChecksum)
                } else {
                    _transferState.value = TransferState.Failed(fileInfo.fileId, "Checksum mismatch")
                    TransferResult.Failed("Checksum verification failed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Receive failed", e)
                _transferState.value = TransferState.Failed("", e.message ?: "Unknown error")
                TransferResult.Failed(e.message ?: "Transfer failed")
            }
        }
    }

    private fun parseChunkData(inputStream: InputStream): TransferProtocol.ChunkData? {
        try {
            // Read fileId (16 bytes for UUID format)
            val fileIdBytes = ByteArray(36) // UUID string length
            inputStream.read(fileIdBytes)
            val fileId = String(fileIdBytes, Charsets.UTF_8)

            val chunkIndex = readInt(inputStream)
            val offset = readLong(inputStream)
            val length = readInt(inputStream)

            val data = ByteArray(length)
            inputStream.read(data)

            return TransferProtocol.ChunkData(
                fileId = fileId,
                chunkIndex = chunkIndex,
                offset = offset,
                length = length,
                data = data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chunk", e)
            return null
        }
    }

    private fun readInt(inputStream: InputStream): Int {
        val bytes = ByteArray(4)
        inputStream.read(bytes)
        return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun readLong(inputStream: InputStream): Long {
        val bytes = ByteArray(8)
        inputStream.read(bytes)
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return result
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        _transferState.value = TransferState.Idle
    }
}

sealed class TransferState {
    object Idle : TransferState()
    data class Transferring(val fileId: String, val progress: Int) : TransferState()
    data class Completed(val fileId: String, val checksum: String) : TransferState()
    data class Failed(val fileId: String, val error: String) : TransferState()
}

data class TransferProgress(
    val fileId: String,
    val progress: Int,
    val transferredBytes: Long,
    val totalBytes: Long,
    val speed: Long
)

sealed class TransferResult {
    data class Success(val fileId: String, val checksum: String) : TransferResult()
    data class Failed(val reason: String) : TransferResult()
}