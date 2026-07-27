package com.onion.model

import kotlinx.serialization.Serializable

@Serializable
data class ChiptuneBgmMmlSpec(
    val type: String,
    val schemaVersion: Int = 1,
    val title: String,
    val seed: Int? = null,
    val bpm: Int,
    val timeSignature: String = "4/4",
    val loopBars: Int,
    val sampleRate: Int = 22_050,
    val bitDepth: Int = 8,
    val masterVolume: Float = 0.8f,
    val tracks: List<ChiptuneMmlTrack>
)

@Serializable
data class ChiptuneMmlTrack(
    val channel: String,
    val dutyCycle: Float? = null,
    val mml: String
)
