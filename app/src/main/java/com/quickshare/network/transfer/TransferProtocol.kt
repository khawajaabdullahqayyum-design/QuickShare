// app/src/main/java/com/quickshare/network/transfer/TransferProtocol.kt
package com.quickshare.network.transfer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TransferProtocol {

    // Packet Types
    const val TYPE_HANDSHAKE: Byte = 0x01
    const val TYPE_FILE_INFO: Byte = 0x02
    const val TYPE_CHUNK_DATA: Byte = 0x03
    const val TYPE_CHUNK_ACK: Byte = 0x04
    const val TYPE_TRANSFER_COMPLETE: Byte = 0x05
    const val TYPE_ERROR: Byte = 0x06
    const val TYPE_PAUSE: Byte = 0x07
    const val TYPE_RESUME: Byte = 0x08

    const val HEADER_SIZE = 12 // type(1) + flags(1) + length(4) + checksum(4) + reserved(2)

    @Parcelize
    data class Handshake(
        val deviceName: String,
        val version: Int,
        val supportedFormats: List<String>
    ) : Parcelable

    @Parcelize
    data class FileInfo(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val checksum: String = "",
        val chunkSize: Int = 65536
    ) : Parcelable

    @Parcelize
    data class ChunkData(
        val fileId: String,
        val chunkIndex: Int,
        val offset: Long,
        val length: Int,
        val data: ByteArray,
        val checksum: String = ""
    ) : Parcelable {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ChunkData
            if (fileId != other.fileId) return false
            if (chunkIndex != other.chunkIndex) return false
            if (offset != other.offset) return false
            if (length != other.length) return false
            if (!data.contentEquals(other.data)) return false
            if (checksum != other.checksum) return false
            return true
        }

        override fun hashCode(): Int {
            var result = fileId.hashCode()
            result = 31 * result + chunkIndex
            result = 31 * result + offset.hashCode()
            result = 31 * result + length
            result = 31 * result + data.contentHashCode()
            result = 31 * result + checksum.hashCode()
            return result
        }
    }

    @Parcelize
    data class ChunkAck(
        val fileId: String,
        val chunkIndex: Int,
        val success: Boolean
    ) : Parcelable

    fun createHandshakePacket(handshake: Handshake): ByteArray {
        val deviceNameBytes = handshake.deviceName.toByteArray(Charsets.UTF_8)
        val formatsString = handshake.supportedFormats.joinToString(",")
        val formatsBytes = formatsString.toByteArray(Charsets.UTF_8)

        val payload = ByteBuffer.allocate(8 + deviceNameBytes.size + formatsBytes.size)
        payload.putInt(handshake.version)
        payload.putInt(deviceNameBytes.size)
        payload.put(deviceNameBytes)
        payload.put(formatsBytes)

        return createPacket(TYPE_HANDSHAKE, payload.array())
    }

    fun createFileInfoPacket(fileInfo: FileInfo): ByteArray {
        val nameBytes = fileInfo.fileName.toByteArray(Charsets.UTF_8)
        val mimeBytes = fileInfo.mimeType.toByteArray(Charsets.UTF_8)
        val checksumBytes = fileInfo.checksum.toByteArray(Charsets.UTF_8)

        val payload = ByteBuffer.allocate(24 + nameBytes.size + mimeBytes.size + checksumBytes.size)
        payload.putLong(fileInfo.fileSize)
        payload.putInt(fileInfo.chunkSize)
        payload.putInt(nameBytes.size)
        payload.put(nameBytes)
        payload.putInt(mimeBytes.size)
        payload.put(mimeBytes)
        payload.putInt(checksumBytes.size)
        payload.put(checksumBytes)

        return createPacket(TYPE_FILE_INFO, payload.array())
    }

    fun createChunkPacket(chunk: ChunkData): ByteArray {
        val payload = ByteBuffer.allocate(24 + chunk.data.size)
        val fileIdBytes = chunk.fileId.toByteArray(Charsets.UTF_8)
        payload.put(fileIdBytes)
        payload.putInt(chunk.chunkIndex)
        payload.putLong(chunk.offset)
        payload.putInt(chunk.length)
        payload.put(chunk.data)

        return createPacket(TYPE_CHUNK_DATA, payload.array())
    }

    fun createAckPacket(ack: ChunkAck): ByteArray {
        val fileIdBytes = ack.fileId.toByteArray(Charsets.UTF_8)
        val payload = ByteBuffer.allocate(8 + fileIdBytes.size)
        payload.put(fileIdBytes)
        payload.putInt(ack.chunkIndex)
        payload.put(if (ack.success) 1 else 0)

        return createPacket(TYPE_CHUNK_ACK, payload.array())
    }

    private fun createPacket(type: Byte, payload: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        header.put(type)
        header.put(0) // flags
        header.putInt(payload.size)
        header.putInt(calculateChecksum(payload))
        header.putShort(0) // reserved

        return header.array() + payload
    }

    fun parseHeader(data: ByteArray): PacketHeader {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        return PacketHeader(
            type = buffer.get(),
            flags = buffer.get(),
            length = buffer.int,
            checksum = buffer.int
        )
    }

    fun parseHandshake(inputStream: InputStream): Handshake {
        val version = readInt(inputStream)
        val nameLength = readInt(inputStream)
        val nameBytes = ByteArray(nameLength)
        inputStream.read(nameBytes)
        val deviceName = String(nameBytes, Charsets.UTF_8)

        val remainingBytes = inputStream.available()
        val formatsBytes = ByteArray(remainingBytes)
        inputStream.read(formatsBytes)
        val formats = String(formatsBytes, Charsets.UTF_8).split(",")

        return Handshake(deviceName, version, formats)
    }

    fun parseFileInfo(inputStream: InputStream): FileInfo {
        val fileSize = readLong(inputStream)
        val chunkSize = readInt(inputStream)
        val nameLength = readInt(inputStream)
        val nameBytes = ByteArray(nameLength)
        inputStream.read(nameBytes)
        val fileName = String(nameBytes, Charsets.UTF_8)

        val mimeLength = readInt(inputStream)
        val mimeBytes = ByteArray(mimeLength)
        inputStream.read(mimeBytes)
        val mimeType = String(mimeBytes, Charsets.UTF_8)

        val checksumLength = readInt(inputStream)
        val checksumBytes = ByteArray(checksumLength)
        inputStream.read(checksumBytes)
        val checksum = String(checksumBytes, Charsets.UTF_8)

        return FileInfo(
            fileId = generateFileId(fileName, fileSize),
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            checksum = checksum,
            chunkSize = chunkSize
        )
    }

    private fun readInt(inputStream: InputStream): Int {
        val bytes = ByteArray(4)
        inputStream.read(bytes)
        return ByteBuffer.wrap(bytes).int
    }

    private fun readLong(inputStream: InputStream): Long {
        val bytes = ByteArray(8)
        inputStream.read(bytes)
        return ByteBuffer.wrap(bytes).long
    }

    private fun calculateChecksum(data: ByteArray): Int {
        var checksum = 0
        for (byte in data) {
            checksum = (checksum + byte.toInt()) and 0xFFFFFFFF.toInt()
        }
        return checksum
    }

    private fun generateFileId(fileName: String, fileSize: Long): String {
        return "${fileName.hashCode()}_${fileSize}_${System.currentTimeMillis()}"
    }
}

data class PacketHeader(
    val type: Byte,
    val flags: Byte,
    val length: Int,
    val checksum: Int
)