package org.onion.agro.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object EightBitBgmRenderer {
    internal fun render(parsed: ParsedChiptuneBgm): RenderedBgm {
        val score = parsed.score
        val durationSeconds = score.totalTicks.toDouble() / TICKS_PER_QUARTER * 60.0 / score.bpm
        val sampleCount = (durationSeconds * score.sampleRate)
            .roundToInt()
            .coerceAtLeast(1)
        val mix = FloatArray(sampleCount)

        score.tracks.forEachIndexed { trackIndex, track ->
            track.events.forEachIndexed { eventIndex, event ->
                if (event.midiNote == null && event.drum == null) return@forEachIndexed
                val startSample = ticksToSamples(event.startTick, sampleCount, score.totalTicks)
                val endSample = ticksToSamples(
                    event.startTick + event.durationTicks,
                    sampleCount,
                    score.totalTicks
                ).coerceAtMost(sampleCount)
                val eventSamples = endSample - startSample
                if (eventSamples <= 0) return@forEachIndexed

                for (sampleIndex in startSample until endSample) {
                    val localSample = sampleIndex - startSample
                    val progress = localSample.toFloat() / eventSamples.toFloat()
                    val envelope = eventEnvelope(localSample, eventSamples)
                    val rawSample = when {
                        event.midiNote != null -> melodicSample(
                            channel = track.channel,
                            dutyCycle = track.dutyCycle,
                            midiNote = event.midiNote,
                            localSample = localSample,
                            sampleRate = score.sampleRate
                        )
                        event.drum != null -> drumSample(
                            drum = event.drum,
                            seed = score.seed xor (trackIndex shl 20) xor (eventIndex shl 12),
                            sampleIndex = sampleIndex,
                            localSample = localSample,
                            sampleRate = score.sampleRate,
                            progress = progress
                        )
                        else -> 0f
                    }
                    val channelVolume = event.volume.coerceIn(0, 15) / 15f
                    mix[sampleIndex] += rawSample * envelope * channelVolume
                }
            }
        }

        val trackScale = 1f / score.tracks.size.coerceAtLeast(1)
        val pcm = ByteArray(sampleCount) { index ->
            val normalized = (mix[index] * trackScale * score.masterVolume).coerceIn(-1f, 1f)
            ((normalized + 1f) * 127.5f).roundToInt().coerceIn(0, 255).toByte()
        }
        return RenderedBgm(
            wavBytes = WavWriter.writeUnsigned8BitMono(pcm, score.sampleRate),
            durationMs = (durationSeconds * 1_000.0).roundToInt().toLong(),
            sampleRate = score.sampleRate,
            bitDepth = 8
        )
    }

    private fun melodicSample(
        channel: BgmChannel,
        dutyCycle: Float,
        midiNote: Int,
        localSample: Int,
        sampleRate: Int
    ): Float {
        val frequency = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
        val phase = (localSample * frequency / sampleRate) % 1.0
        return when (channel) {
            BgmChannel.PULSE_1,
            BgmChannel.PULSE_2 -> if (phase < dutyCycle) 1f else -1f
            BgmChannel.TRIANGLE -> (4.0 * abs(phase - floor(phase + 0.5)) - 1.0).toFloat()
            BgmChannel.NOISE -> 0f
        }
    }

    private fun drumSample(
        drum: BgmDrum,
        seed: Int,
        sampleIndex: Int,
        localSample: Int,
        sampleRate: Int,
        progress: Float
    ): Float {
        val seconds = localSample.toDouble() / sampleRate
        val noise = deterministicNoise(seed xor sampleIndex)
        return when (drum) {
            BgmDrum.KICK -> {
                val frequency = 110.0 - 70.0 * progress
                (sin(2.0 * PI * frequency * seconds) * (1f - progress)).toFloat()
            }
            BgmDrum.SNARE -> noise * (1f - progress).pow(2)
            BgmDrum.HIHAT -> noise * (1f - progress).pow(5)
            BgmDrum.TOM -> {
                val frequency = 170.0 - 55.0 * progress
                (sin(2.0 * PI * frequency * seconds) * (1f - progress).pow(2)).toFloat()
            }
        }
    }

    private fun deterministicNoise(seed: Int): Float {
        var value = seed
        value = value xor (value shl 13)
        value = value xor (value ushr 17)
        value = value xor (value shl 5)
        return ((value ushr 8) and 0xFFFF) / 32_767.5f - 1f
    }

    private fun eventEnvelope(localSample: Int, eventSamples: Int): Float {
        val edgeSamples = min(64, (eventSamples / 4).coerceAtLeast(1))
        val attack = (localSample + 1).toFloat() / edgeSamples
        val release = (eventSamples - localSample).toFloat() / edgeSamples
        return min(1f, min(attack, release)).coerceAtLeast(0f)
    }

    private fun ticksToSamples(ticks: Int, sampleCount: Int, totalTicks: Int): Int {
        return (ticks.toLong() * sampleCount / totalTicks).toInt()
    }

    private const val TICKS_PER_QUARTER = 96.0
}

data class RenderedBgm(
    val wavBytes: ByteArray,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int
)

private object WavWriter {
    private const val HEADER_SIZE = 44

    fun writeUnsigned8BitMono(pcm: ByteArray, sampleRate: Int): ByteArray {
        val output = ByteArray(HEADER_SIZE + pcm.size)
        output.writeAscii(0, "RIFF")
        output.writeIntLittleEndian(4, 36 + pcm.size)
        output.writeAscii(8, "WAVE")
        output.writeAscii(12, "fmt ")
        output.writeIntLittleEndian(16, 16)
        output.writeShortLittleEndian(20, 1)
        output.writeShortLittleEndian(22, 1)
        output.writeIntLittleEndian(24, sampleRate)
        output.writeIntLittleEndian(28, sampleRate)
        output.writeShortLittleEndian(32, 1)
        output.writeShortLittleEndian(34, 8)
        output.writeAscii(36, "data")
        output.writeIntLittleEndian(40, pcm.size)
        pcm.copyInto(output, destinationOffset = HEADER_SIZE)
        return output
    }

    private fun ByteArray.writeAscii(offset: Int, value: String) {
        value.encodeToByteArray().copyInto(this, destinationOffset = offset)
    }

    private fun ByteArray.writeShortLittleEndian(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.writeIntLittleEndian(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
        this[offset + 2] = (value ushr 16).toByte()
        this[offset + 3] = (value ushr 24).toByte()
    }
}
