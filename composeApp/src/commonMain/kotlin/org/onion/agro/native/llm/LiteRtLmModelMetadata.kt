package org.onion.agro.native.llm

import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Reads the LiteRT-LM model context limit without loading the model payload.
 *
 * The value is stored in the `LlmMetadata` protobuf section of a `.litertlm`
 * container. Only the container header and that metadata section are read.
 */
internal object LiteRtLmModelMetadata {
    private const val PREAMBLE_SIZE = 32
    private const val MAX_HEADER_SIZE = 16 * 1024
    private const val MAX_METADATA_SIZE = 16 * 1024 * 1024
    private const val LLM_METADATA_SECTION_TYPE = 5
    private const val MAX_NUM_TOKENS_FIELD_NUMBER = 5
    private val magic = "LITERTLM".encodeToByteArray()

    fun getLmMaxNumTokens(modelPath: String): Int? {
        if (modelPath.isBlank()) return null

        return FileSystem.SYSTEM.openReadOnly(modelPath.toPath()).use { file ->
            parseLmMaxNumTokens { offset, byteCount ->
                file.readExactly(offset, byteCount)
            }
        }
    }

    internal fun parseLmMaxNumTokens(
        readAt: (offset: Long, byteCount: Int) -> ByteArray
    ): Int? {
        val preamble = readAt(0, PREAMBLE_SIZE)
        require(preamble.copyOfRange(0, magic.size).contentEquals(magic)) {
            "The selected model is not a LiteRT-LM container"
        }

        val headerEndOffset = preamble.readLongLe(24)
        require(headerEndOffset in PREAMBLE_SIZE.toLong()..MAX_HEADER_SIZE.toLong()) {
            "Invalid LiteRT-LM header end offset: $headerEndOffset"
        }

        val header = FlatBufferBytes(
            readAt(
                PREAMBLE_SIZE.toLong(),
                (headerEndOffset - PREAMBLE_SIZE).toInt()
            )
        )
        val metadataRange = header.findLlmMetadataRange() ?: return null
        val metadataSize = metadataRange.lastExclusive - metadataRange.first
        require(metadataSize in 1..MAX_METADATA_SIZE.toLong()) {
            "Invalid LiteRT-LM metadata size: $metadataSize"
        }

        val metadata = readAt(metadataRange.first, metadataSize.toInt())
        return metadata.readPositiveInt32ProtoField(MAX_NUM_TOKENS_FIELD_NUMBER)
    }

    private fun FileHandle.readExactly(offset: Long, byteCount: Int): ByteArray {
        require(offset >= 0)
        require(byteCount >= 0)

        val result = ByteArray(byteCount)
        var resultOffset = 0
        while (resultOffset < byteCount) {
            val readCount = read(
                fileOffset = offset + resultOffset,
                array = result,
                arrayOffset = resultOffset,
                byteCount = byteCount - resultOffset
            )
            check(readCount > 0) {
                "Unexpected end of LiteRT-LM file at offset ${offset + resultOffset}"
            }
            resultOffset += readCount
        }
        return result
    }

    private fun ByteArray.readPositiveInt32ProtoField(fieldNumber: Int): Int? {
        var offset = 0
        while (offset < size) {
            val tag = readVarint(offset)
            offset = tag.nextOffset
            val currentFieldNumber = (tag.value ushr 3).toInt()
            val wireType = (tag.value and 0x07).toInt()
            require(currentFieldNumber > 0) { "Invalid protobuf field number" }

            if (currentFieldNumber == fieldNumber && wireType == 0) {
                val fieldValue = readVarint(offset).value
                return fieldValue
                    .takeIf { it in 1..Int.MAX_VALUE.toLong() }
                    ?.toInt()
            }

            offset = skipProtoField(offset, wireType)
        }
        return null
    }

    private fun ByteArray.skipProtoField(offset: Int, wireType: Int): Int = when (wireType) {
        0 -> readVarint(offset).nextOffset
        1 -> checkedEndOffset(offset, 8)
        2 -> {
            val length = readVarint(offset)
            require(length.value <= Int.MAX_VALUE.toLong()) {
                "Protobuf field is too large"
            }
            checkedEndOffset(length.nextOffset, length.value.toInt())
        }
        5 -> checkedEndOffset(offset, 4)
        else -> error("Unsupported protobuf wire type: $wireType")
    }

    private fun ByteArray.readVarint(startOffset: Int): Varint {
        var offset = startOffset
        var value = 0L
        var shift = 0
        while (shift < 64) {
            require(offset < size) { "Unexpected end of protobuf varint" }
            val byte = this[offset].toInt() and 0xff
            offset++
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) {
                return Varint(value, offset)
            }
            shift += 7
        }
        error("Invalid protobuf varint")
    }

    private fun ByteArray.checkedEndOffset(offset: Int, byteCount: Int): Int {
        require(offset >= 0 && byteCount >= 0 && offset <= size - byteCount) {
            "Protobuf field exceeds metadata bounds"
        }
        return offset + byteCount
    }

    private fun ByteArray.readLongLe(offset: Int): Long {
        require(offset >= 0 && offset <= size - Long.SIZE_BYTES)
        var value = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            value = value or ((this[offset + index].toLong() and 0xff) shl (index * 8))
        }
        return value
    }

    private data class Varint(
        val value: Long,
        val nextOffset: Int
    )

    private data class OffsetRange(
        val first: Long,
        val lastExclusive: Long
    )

    private class FlatBufferBytes(
        private val bytes: ByteArray
    ) {
        fun findLlmMetadataRange(): OffsetRange? {
            val rootTable = indirect(0)
            val sectionMetadataField = fieldAddress(rootTable, 1) ?: return null
            val sectionMetadataTable = indirect(sectionMetadataField)
            val objectsField = fieldAddress(sectionMetadataTable, 0) ?: return null
            val objectsVector = indirect(objectsField)
            val objectCount = readUnsignedInt(objectsVector)
            require(objectCount <= Int.MAX_VALUE.toLong()) {
                "LiteRT-LM section count is too large"
            }

            repeat(objectCount.toInt()) { index ->
                val elementAddress = checkedAddress(
                    objectsVector.toLong() + Int.SIZE_BYTES + index.toLong() * Int.SIZE_BYTES,
                    Int.SIZE_BYTES
                )
                val sectionTable = indirect(elementAddress)
                val dataType = fieldAddress(sectionTable, 3)?.let(::readUnsignedByte) ?: 0
                if (dataType == LLM_METADATA_SECTION_TYPE) {
                    val beginField = fieldAddress(sectionTable, 1) ?: return@repeat
                    val endField = fieldAddress(sectionTable, 2) ?: return@repeat
                    val beginOffset = readLong(beginField)
                    val endOffset = readLong(endField)
                    require(beginOffset >= 0 && endOffset > beginOffset) {
                        "Invalid LiteRT-LM metadata offsets: $beginOffset..$endOffset"
                    }
                    return OffsetRange(beginOffset, endOffset)
                }
            }
            return null
        }

        private fun fieldAddress(tableAddress: Int, fieldIndex: Int): Int? {
            val vtableAddress = checkedAddress(
                tableAddress.toLong() - readInt(tableAddress).toLong(),
                Short.SIZE_BYTES * 2
            )
            val vtableSize = readUnsignedShort(vtableAddress)
            val entryAddress = vtableAddress + Short.SIZE_BYTES * (fieldIndex + 2)
            if (entryAddress > vtableAddress + vtableSize - Short.SIZE_BYTES) return null

            val fieldOffset = readUnsignedShort(entryAddress)
            if (fieldOffset == 0) return null
            return checkedAddress(tableAddress.toLong() + fieldOffset, 1)
        }

        private fun indirect(offsetAddress: Int): Int {
            val relativeOffset = readUnsignedInt(offsetAddress)
            return checkedAddress(offsetAddress.toLong() + relativeOffset, Int.SIZE_BYTES)
        }

        private fun readUnsignedByte(offset: Int): Int =
            bytes[checkedAddress(offset.toLong(), 1)].toInt() and 0xff

        private fun readUnsignedShort(offset: Int): Int {
            val address = checkedAddress(offset.toLong(), Short.SIZE_BYTES)
            return (bytes[address].toInt() and 0xff) or
                ((bytes[address + 1].toInt() and 0xff) shl 8)
        }

        private fun readInt(offset: Int): Int {
            val value = readUnsignedInt(offset)
            require(value <= Int.MAX_VALUE.toLong()) {
                "FlatBuffer signed offset exceeds Int range"
            }
            return value.toInt()
        }

        private fun readUnsignedInt(offset: Int): Long {
            val address = checkedAddress(offset.toLong(), Int.SIZE_BYTES)
            var value = 0L
            repeat(Int.SIZE_BYTES) { index ->
                value = value or
                    ((bytes[address + index].toLong() and 0xff) shl (index * 8))
            }
            return value
        }

        private fun readLong(offset: Int): Long {
            val address = checkedAddress(offset.toLong(), Long.SIZE_BYTES)
            var value = 0L
            repeat(Long.SIZE_BYTES) { index ->
                value = value or
                    ((bytes[address + index].toLong() and 0xff) shl (index * 8))
            }
            return value
        }

        private fun checkedAddress(offset: Long, byteCount: Int): Int {
            require(offset >= 0 && offset <= bytes.size.toLong() - byteCount) {
                "FlatBuffer offset exceeds header bounds"
            }
            return offset.toInt()
        }
    }
}
