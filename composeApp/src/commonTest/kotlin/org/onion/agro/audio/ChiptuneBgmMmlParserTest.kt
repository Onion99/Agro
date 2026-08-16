package org.onion.agro.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChiptuneBgmMmlParserTest {
    @Test
    fun parsesJsonAndMmlTracks() {
        val parsed = ChiptuneBgmMmlParser.parse(validScoreJson())

        assertEquals("Test Loop", parsed.spec.title)
        assertEquals(120, parsed.score.bpm)
        assertEquals(2, parsed.score.tracks.size)
        assertEquals(768, parsed.score.totalTicks)
        assertEquals(768, parsed.score.tracks.first().events.sumOf { it.durationTicks })
    }

    @Test
    fun expandsNoiseRepeatAndDistinguishesTomFromTempo() {
        val parsed = ChiptuneBgmMmlParser.parse(validScoreJson())
        val noiseEvents = parsed.score.tracks.last().events

        assertTrue(noiseEvents.any { it.drum == BgmDrum.TOM })
        assertEquals(768, noiseEvents.sumOf { it.durationTicks })
    }

    @Test
    fun rejectsTrackTempoMismatch() {
        val error = assertFailsWith<BgmParseException> {
            ChiptuneBgmMmlParser.parse(validScoreJson().replace("T120 O5", "T121 O5"))
        }

        assertEquals("track_tempo_mismatch", error.reason)
    }

    @Test
    fun rejectsUnexpectedContentType() {
        val error = assertFailsWith<BgmParseException> {
            ChiptuneBgmMmlParser.parse(
                validScoreJson().replace("chiptune_bgm_mml", "unknown_audio")
            )
        }

        assertEquals("unexpected_content_type", error.reason)
    }

    @Test
    fun rendersDeterministicUnsignedEightBitWav() {
        val parsed = ChiptuneBgmMmlParser.parse(validScoreJson())
        val first = EightBitBgmRenderer.render(parsed)
        val second = EightBitBgmRenderer.render(parsed)

        assertEquals(4_000L, first.durationMs)
        assertEquals(22_050, first.sampleRate)
        assertEquals(8, first.bitDepth)
        assertEquals(44 + 88_200, first.wavBytes.size)
        assertEquals("RIFF", first.wavBytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", first.wavBytes.copyOfRange(8, 12).decodeToString())
        assertContentEquals(first.wavBytes, second.wavBytes)
    }

    @Test
    fun clampsOversizedTrackDurationToLoopLength() {
        // 10 quarter notes (960 ticks) in a 2-bar loop (768 ticks required)
        val json = validScoreJson().replace(
            "T120 O5 L4 C D E F | G A B >C",
            "T120 O5 L4 C D E F G A B >C D E"
        )
        val parsed = ChiptuneBgmMmlParser.parse(json)
        val track = parsed.score.tracks.first()

        assertEquals(768, track.events.sumOf { it.durationTicks })
    }

    @Test
    fun healsMarkdownWrappedAndTempoMismatchedBgmResponse() {
        val payload = """
            ```json
            {
              "type": "chiptune_bgm_mml",
              "schemaVersion": 1,
              "title": "Retro Test",
              "bpm": 140,
              "timeSignature": "4/4",
              "loopBars": 2,
              "sampleRate": 22050,
              "bitDepth": 8,
              "masterVolume": 0.8,
              "tracks": [
                {
                  "channel": "pulse1",
                  "dutyCycle": 0.5,
                  "mml": "T120 O5 L4 C D E F | G A B >C",
                },
                {
                  "channel": "noise",
                  "mml": "T160 L8 [K R H R S R H T] * 2",
                }
              ],
            }
            ```
        """.trimIndent()

        val parsedContent = org.onion.agro.message.ChiptuneBgmMessageParser.parseCompletedResponse(payload)
        val audio = kotlin.test.assertIs<com.onion.model.ChatMessageContent.Audio>(
            parsedContent,
            "Expected audio message content but got $parsedContent"
        )
        assertEquals("Retro Test", audio.title)
        assertEquals(140, audio.bpm)
        assertEquals(2, audio.loopBars)
        assertTrue(audio.durationMs > 0)
    }

    @Test
    fun createsEncodedFileUriForLocalPlayback() {
        assertEquals(
            "file:///var/mobile/Library/Caches/generated-bgm/loop%20%231.wav",
            BgmAudioFileStore.playbackUri(
                "/var/mobile/Library/Caches/generated-bgm/loop #1.wav"
            )
        )
        assertEquals(
            "file:///C:/Users/Test%20User/bgm.wav",
            BgmAudioFileStore.playbackUri("C:\\Users\\Test User\\bgm.wav")
        )
        assertEquals(
            "file:///tmp/%E4%BD%9C%E5%93%81.wav",
            BgmAudioFileStore.playbackUri("/tmp/作品.wav")
        )
    }

    private fun validScoreJson(): String {
        return """
            {
              "type": "chiptune_bgm_mml",
              "schemaVersion": 1,
              "title": "Test Loop",
              "seed": 42,
              "bpm": 120,
              "timeSignature": "4/4",
              "loopBars": 2,
              "sampleRate": 22050,
              "bitDepth": 8,
              "masterVolume": 0.8,
              "tracks": [
                {
                  "channel": "pulse1",
                  "dutyCycle": 0.5,
                  "mml": "T120 O5 L4 C D E F | G A B >C"
                },
                {
                  "channel": "noise",
                  "mml": "T120 L8 [K R H R S R H T]x2"
                }
              ]
            }
        """.trimIndent()
    }
}

