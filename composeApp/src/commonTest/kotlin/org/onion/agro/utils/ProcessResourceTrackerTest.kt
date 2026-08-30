package org.onion.agro.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcessResourceTrackerTest {
    @Test
    fun recordsResidentMemoryPeakAndDeltaAcrossSamples() {
        val tracker = ProcessResourceTracker(
            initialSnapshot = snapshot(
                residentMemoryBytes = 1_000L,
                processCpuTimeMillis = 100L,
            ),
            initialTimestampMillis = 1_000L,
        )

        tracker.record(
            snapshot = snapshot(
                residentMemoryBytes = 1_600L,
                processCpuTimeMillis = 140L,
            ),
            timestampMillis = 1_200L,
        )
        val usage = tracker.record(
            snapshot = snapshot(
                residentMemoryBytes = 1_300L,
                processCpuTimeMillis = 180L,
            ),
            timestampMillis = 1_400L,
        )

        assertEquals(1_300L, usage.currentResidentMemoryBytes)
        assertEquals(1_600L, usage.peakResidentMemoryBytes)
        assertEquals(600L, usage.peakResidentMemoryDeltaBytes)
    }

    @Test
    fun normalizesCpuTimeAcrossLogicalProcessors() {
        val tracker = ProcessResourceTracker(
            initialSnapshot = snapshot(
                processCpuTimeMillis = 1_000L,
                logicalProcessorCount = 4,
            ),
            initialTimestampMillis = 5_000L,
        )

        val usage = tracker.record(
            snapshot = snapshot(
                processCpuTimeMillis = 1_400L,
                logicalProcessorCount = 4,
            ),
            timestampMillis = 5_200L,
        )

        assertEquals(50.0, usage.currentCpuLoadPercent)
        assertEquals(50.0, usage.peakCpuLoadPercent)
    }

    @Test
    fun keepsUnsupportedMetricsUnavailable() {
        val tracker = ProcessResourceTracker(
            initialSnapshot = snapshot(),
            initialTimestampMillis = 0L,
        )

        val usage = tracker.record(
            snapshot = snapshot(),
            timestampMillis = 200L,
        )

        assertNull(usage.currentResidentMemoryBytes)
        assertNull(usage.peakResidentMemoryBytes)
        assertNull(usage.peakResidentMemoryDeltaBytes)
        assertNull(usage.currentCpuLoadPercent)
        assertNull(usage.peakCpuLoadPercent)
    }

    private fun snapshot(
        residentMemoryBytes: Long? = null,
        processCpuTimeMillis: Long? = null,
        logicalProcessorCount: Int = 1,
    ) = ProcessResourceSnapshot(
        residentMemoryBytes = residentMemoryBytes,
        totalPhysicalMemoryBytes = 8_000L,
        processCpuTimeMillis = processCpuTimeMillis,
        logicalProcessorCount = logicalProcessorCount,
    )
}
