package org.onion.agro.native.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LiteRtLmModelMetadataTest {

    @Test
    fun readsMaxNumTokensFromLiteRtLmMetadataSection() {
        val model = liteRtLmModel(
            llmMetadata = byteArrayOf(
                0x28,
                0x80.toByte(),
                0x40
            )
        )

        assertEquals(8192, parse(model))
    }

    @Test
    fun returnsNullWhenMaxNumTokensIsNotPresent() {
        val model = liteRtLmModel(
            llmMetadata = byteArrayOf(
                0x30,
                0x01
            )
        )

        assertNull(parse(model))
    }

    @Test
    fun rejectsNonLiteRtLmFiles() {
        val model = liteRtLmModel(byteArrayOf(0x28, 0x01))
        model[0] = 0

        assertFailsWith<IllegalArgumentException> {
            parse(model)
        }
    }

    private fun parse(model: ByteArray): Int? =
        LiteRtLmModelMetadata.parseLmMaxNumTokens { offset, byteCount ->
            val start = offset.toInt()
            model.copyOfRange(start, start + byteCount)
        }

    private fun liteRtLmModel(llmMetadata: ByteArray): ByteArray {
        val headerSize = 112
        val headerEndOffset = 32 + headerSize
        val metadataBeginOffset = 160
        val metadataEndOffset = metadataBeginOffset + llmMetadata.size
        val model = ByteArray(metadataEndOffset)

        "LITERTLM".encodeToByteArray().copyInto(model)
        model.writeIntLe(8, 1)
        model.writeLongLe(24, headerEndOffset.toLong())

        val headerOffset = 32
        model.writeIntLe(headerOffset, 16)

        model.writeShortLe(headerOffset + 8, 8)
        model.writeShortLe(headerOffset + 10, 8)
        model.writeShortLe(headerOffset + 12, 0)
        model.writeShortLe(headerOffset + 14, 4)
        model.writeIntLe(headerOffset + 16, 8)
        model.writeIntLe(headerOffset + 20, 20)

        model.writeShortLe(headerOffset + 32, 6)
        model.writeShortLe(headerOffset + 34, 8)
        model.writeShortLe(headerOffset + 36, 4)
        model.writeIntLe(headerOffset + 40, 8)
        model.writeIntLe(headerOffset + 44, 8)

        model.writeIntLe(headerOffset + 52, 1)
        model.writeIntLe(headerOffset + 56, 24)

        model.writeShortLe(headerOffset + 64, 12)
        model.writeShortLe(headerOffset + 66, 25)
        model.writeShortLe(headerOffset + 68, 0)
        model.writeShortLe(headerOffset + 70, 8)
        model.writeShortLe(headerOffset + 72, 16)
        model.writeShortLe(headerOffset + 74, 24)
        model.writeIntLe(headerOffset + 80, 16)
        model.writeLongLe(headerOffset + 88, metadataBeginOffset.toLong())
        model.writeLongLe(headerOffset + 96, metadataEndOffset.toLong())
        model[headerOffset + 104] = 5

        llmMetadata.copyInto(model, destinationOffset = metadataBeginOffset)
        return model
    }

    private fun ByteArray.writeShortLe(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.writeIntLe(offset: Int, value: Int) {
        repeat(Int.SIZE_BYTES) { index ->
            this[offset + index] = (value ushr (index * 8)).toByte()
        }
    }

    private fun ByteArray.writeLongLe(offset: Int, value: Long) {
        repeat(Long.SIZE_BYTES) { index ->
            this[offset + index] = (value ushr (index * 8)).toByte()
        }
    }
}
