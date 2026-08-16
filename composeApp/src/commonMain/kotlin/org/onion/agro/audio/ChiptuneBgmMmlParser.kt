package org.onion.agro.audio

import com.onion.model.ChiptuneBgmMmlSpec
import com.onion.model.ChiptuneMmlTrack
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object ChiptuneBgmMmlParser {
    private const val CONTENT_TYPE = "chiptune_bgm_mml"
    private const val SCHEMA_VERSION = 1
    private const val TICKS_PER_QUARTER = 96
    private const val MAX_MML_LENGTH = 32_768
    private const val MAX_EXPANDED_MML_LENGTH = 131_072
    private const val MAX_REPEAT_BLOCKS = 64

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    internal fun parse(response: String): ParsedChiptuneBgm {
        val spec = try {
            json.decodeFromString<ChiptuneBgmMmlSpec>(response)
        } catch (error: Exception) {
            throw BgmParseException("invalid_bgm_json", error)
        }
        validateSpec(spec)

        val requiredTicks = spec.loopBars * 4 * TICKS_PER_QUARTER
        val parsedTracks = spec.tracks.map { track ->
            parseTrack(
                track = track,
                bpm = spec.bpm,
                requiredTicks = requiredTicks
            )
        }
        return ParsedChiptuneBgm(
            spec = spec,
            score = BgmScore(
                seed = spec.seed ?: response.hashCode(),
                bpm = spec.bpm,
                sampleRate = spec.sampleRate,
                masterVolume = spec.masterVolume,
                totalTicks = requiredTicks,
                tracks = parsedTracks
            )
        )
    }

    fun declaredType(response: String): String? {
        return runCatching {
            val type = json.parseToJsonElement(response).jsonObject["type"] as? JsonPrimitive
            type?.takeIf(JsonPrimitive::isString)?.content
        }.getOrNull()
    }

    private fun validateSpec(spec: ChiptuneBgmMmlSpec) {
        requireBgm(spec.type == CONTENT_TYPE, "unexpected_content_type")
        requireBgm(spec.schemaVersion == SCHEMA_VERSION, "unsupported_schema_version")
        requireBgm(spec.title.trim().length in 1..48, "invalid_title")
        requireBgm(spec.bpm in 60..200, "invalid_bpm")
        requireBgm(spec.timeSignature == "4/4", "unsupported_time_signature")
        requireBgm(spec.loopBars in 2..16, "invalid_loop_bars")
        requireBgm(spec.sampleRate in SUPPORTED_SAMPLE_RATES, "invalid_sample_rate")
        requireBgm(spec.bitDepth == 8, "unsupported_bit_depth")
        requireBgm(
            spec.masterVolume.isFinite() && spec.masterVolume in 0f..1f,
            "invalid_master_volume"
        )
        requireBgm(spec.tracks.size in 1..4, "invalid_track_count")

        val channels = spec.tracks.map { it.channel.lowercase() }
        requireBgm(channels.distinct().size == channels.size, "duplicate_track_channel")
        requireBgm(channels.all(SUPPORTED_CHANNELS::contains), "unsupported_track_channel")
    }

    private fun parseTrack(
        track: ChiptuneMmlTrack,
        bpm: Int,
        requiredTicks: Int
    ): BgmTrackScore {
        requireBgm(track.mml.length <= MAX_MML_LENGTH, "mml_too_large")
        val channel = BgmChannel.fromValue(track.channel)
            ?: throw BgmParseException("unsupported_track_channel:${track.channel}")
        val dutyCycle = validateDutyCycle(channel, track.dutyCycle)
        val expandedMml = expandRepeats(track.mml)
        val cursor = MmlCursor(expandedMml)
        var octave = if (channel == BgmChannel.TRIANGLE) 3 else 4
        var defaultLength = if (channel == BgmChannel.NOISE) 16 else 4
        var volume = if (channel == BgmChannel.PULSE_1) 12 else 9
        var currentTick = 0
        val events = mutableListOf<BgmEvent>()

        while (cursor.hasNext()) {
            cursor.skipSeparators()
            if (!cursor.hasNext()) break

            var duration = 0
            when (val token = cursor.readUppercase()) {
                '>' -> {
                    requireBgm(channel != BgmChannel.NOISE, "invalid_noise_token:>")
                    octave = (octave + 1).coerceIn(1, 7)
                }
                '<' -> {
                    requireBgm(channel != BgmChannel.NOISE, "invalid_noise_token:<")
                    octave = (octave - 1).coerceIn(1, 7)
                }
                'O' -> {
                    requireBgm(channel != BgmChannel.NOISE, "invalid_noise_token:O")
                    octave = cursor.readRequiredNumber("missing_octave").coerceIn(1, 7)
                }
                'L' -> {
                    val len = cursor.readRequiredNumber("missing_default_length")
                    requireBgm(len in SUPPORTED_LENGTHS, "unsupported_note_length")
                    defaultLength = len
                }
                'V' -> {
                    requireBgm(channel != BgmChannel.NOISE, "invalid_noise_token:V")
                    volume = cursor.readRequiredNumber("missing_volume").coerceIn(0, 15)
                }
                'T' -> {
                    val tempo = cursor.readNumberOrNull()
                    if (tempo != null) {
                        requireBgm(tempo == bpm, "track_tempo_mismatch")
                    } else if (channel == BgmChannel.NOISE) {
                        duration = cursor.readDurationTicks(defaultLength)
                        events += BgmEvent.drum(currentTick, duration, BgmDrum.TOM, volume)
                    } else {
                        throw BgmParseException("missing_tempo")
                    }
                }
                'R', 'P' -> {
                    duration = cursor.readDurationTicks(defaultLength)
                    events += BgmEvent.rest(currentTick, duration)
                }
                'K', 'S', 'H' -> {
                    requireBgm(channel == BgmChannel.NOISE, "invalid_melodic_token:$token")
                    duration = cursor.readDurationTicks(defaultLength)
                    val drum = when (token) {
                        'K' -> BgmDrum.KICK
                        'S' -> BgmDrum.SNARE
                        else -> BgmDrum.HIHAT
                    }
                    events += BgmEvent.drum(currentTick, duration, drum, volume)
                }
                in 'A'..'G' -> {
                    requireBgm(channel != BgmChannel.NOISE, "invalid_noise_token:$token")
                    val accidental = cursor.readAccidental()
                    duration = cursor.readDurationTicks(defaultLength)
                    val midiNote = midiNote(token, accidental, octave)
                    events += BgmEvent.note(currentTick, duration, midiNote, volume)
                }
                else -> throw BgmParseException("invalid_mml_token:$token")
            }

            if (duration > 0) {
                if (currentTick + duration >= requiredTicks) {
                    val excess = (currentTick + duration) - requiredTicks
                    val clampedDuration = duration - excess
                    if (clampedDuration > 0 && events.isNotEmpty()) {
                        val last = events.removeLast()
                        events += BgmEvent(
                            startTick = last.startTick,
                            durationTicks = clampedDuration,
                            midiNote = last.midiNote,
                            drum = last.drum,
                            volume = last.volume
                        )
                    }
                    currentTick = requiredTicks
                    break
                }
                currentTick += duration
            }
        }

        requireBgm(events.isNotEmpty(), "empty_track:${track.channel}")
        if (currentTick < requiredTicks) {
            events += BgmEvent.rest(currentTick, requiredTicks - currentTick)
        }
        return BgmTrackScore(
            channel = channel,
            dutyCycle = dutyCycle,
            events = events
        )
    }

    private fun validateDutyCycle(channel: BgmChannel, value: Float?): Float {
        return when (channel) {
            BgmChannel.PULSE_1 -> {
                val dutyCycle = value ?: 0.5f
                requireBgm(dutyCycle == 0.5f, "invalid_pulse1_duty_cycle")
                dutyCycle
            }
            BgmChannel.PULSE_2 -> {
                val dutyCycle = value ?: 0.25f
                requireBgm(dutyCycle == 0.25f || dutyCycle == 0.125f, "invalid_pulse2_duty_cycle")
                dutyCycle
            }
            BgmChannel.TRIANGLE,
            BgmChannel.NOISE -> 0.5f
        }
    }

    private fun expandRepeats(source: String): String {
        var expanded = source
        var expandedBlocks = 0
        while (true) {
            val match = REPEAT_BLOCK_REGEX.find(expanded) ?: break
            expandedBlocks += 1
            requireBgm(expandedBlocks <= MAX_REPEAT_BLOCKS, "too_many_repeat_blocks")
            val count = match.groupValues[2].toIntOrNull()
                ?: throw BgmParseException("invalid_repeat_count")
            requireBgm(count in 2..8, "repeat_count_out_of_range")
            val repeated = List(count) { match.groupValues[1] }.joinToString(separator = " ")
            expanded = expanded.replaceRange(match.range, repeated)
            requireBgm(expanded.length <= MAX_EXPANDED_MML_LENGTH, "expanded_mml_too_large")
        }
        requireBgm('[' !in expanded && ']' !in expanded, "malformed_repeat_block")
        return expanded
    }

    private fun midiNote(note: Char, accidental: Int, octave: Int): Int {
        val semitone = when (note) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B' -> 11
            else -> error("Unsupported note")
        }
        return ((octave + 1) * 12 + semitone + accidental).also {
            requireBgm(it in 24..119, "note_out_of_range")
        }
    }

    private fun requireBgm(condition: Boolean, reason: String) {
        if (!condition) throw BgmParseException(reason)
    }

    private class MmlCursor(private val source: String) {
        private var index = 0

        fun hasNext(): Boolean = index < source.length

        fun skipSeparators() {
            while (hasNext() && (source[index].isWhitespace() || source[index] == '|')) {
                index += 1
            }
        }

        fun readUppercase(): Char {
            val result = source[index].uppercaseChar()
            index += 1
            return result
        }

        fun readRequiredNumber(reason: String): Int {
            return readNumberOrNull() ?: throw BgmParseException(reason)
        }

        fun readNumberOrNull(): Int? {
            val start = index
            while (hasNext() && source[index].isDigit()) index += 1
            return if (start == index) null else source.substring(start, index).toIntOrNull()
        }

        fun readAccidental(): Int {
            if (!hasNext()) return 0
            return when (source[index]) {
                '#', '+' -> {
                    index += 1
                    1
                }
                '-' -> {
                    index += 1
                    -1
                }
                else -> 0
            }
        }

        fun readDurationTicks(defaultLength: Int): Int {
            val length = readNumberOrNull() ?: defaultLength
            requireBgm(length in SUPPORTED_LENGTHS, "unsupported_note_length")
            var ticks = WHOLE_NOTE_TICKS / length
            if (hasNext() && source[index] == '.') {
                index += 1
                ticks = ticks * 3 / 2
                requireBgm(!hasNext() || source[index] != '.', "multiple_dots_not_supported")
            }
            return ticks
        }
    }

    private const val WHOLE_NOTE_TICKS = TICKS_PER_QUARTER * 4
    private val SUPPORTED_LENGTHS = setOf(1, 2, 4, 8, 16, 32)
    private val SUPPORTED_SAMPLE_RATES = setOf(11_025, 22_050, 44_100)
    private val SUPPORTED_CHANNELS = setOf("pulse1", "pulse2", "triangle", "noise")
    private val REPEAT_BLOCK_REGEX = Regex("\\[([^\\[\\]]*)]\\s*[xX](\\d+)")
}

class BgmParseException(
    val reason: String,
    cause: Throwable? = null
) : IllegalArgumentException(reason, cause)

internal data class ParsedChiptuneBgm(
    val spec: ChiptuneBgmMmlSpec,
    internal val score: BgmScore
)

internal data class BgmScore(
    val seed: Int,
    val bpm: Int,
    val sampleRate: Int,
    val masterVolume: Float,
    val totalTicks: Int,
    val tracks: List<BgmTrackScore>
)

internal data class BgmTrackScore(
    val channel: BgmChannel,
    val dutyCycle: Float,
    val events: List<BgmEvent>
)

internal data class BgmEvent(
    val startTick: Int,
    val durationTicks: Int,
    val midiNote: Int?,
    val drum: BgmDrum?,
    val volume: Int
) {
    companion object {
        fun note(startTick: Int, durationTicks: Int, midiNote: Int, volume: Int): BgmEvent {
            return BgmEvent(startTick, durationTicks, midiNote, null, volume)
        }

        fun drum(startTick: Int, durationTicks: Int, drum: BgmDrum, volume: Int): BgmEvent {
            return BgmEvent(startTick, durationTicks, null, drum, volume)
        }

        fun rest(startTick: Int, durationTicks: Int): BgmEvent {
            return BgmEvent(startTick, durationTicks, null, null, 0)
        }
    }
}

internal enum class BgmChannel(val value: String) {
    PULSE_1("pulse1"),
    PULSE_2("pulse2"),
    TRIANGLE("triangle"),
    NOISE("noise");

    companion object {
        fun fromValue(value: String): BgmChannel? {
            return entries.firstOrNull { it.value == value.lowercase() }
        }
    }
}

internal enum class BgmDrum {
    KICK,
    SNARE,
    HIHAT,
    TOM
}
