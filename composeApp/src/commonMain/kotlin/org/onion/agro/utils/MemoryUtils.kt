package org.onion.agro.utils

import kotlin.math.max

/**
 * A point-in-time view of resources owned by the current application process.
 *
 * Values are nullable because the host OS may not expose every counter. In particular,
 * accelerator compute utilization is not inferred from process CPU time.
 */
data class ProcessResourceSnapshot(
    val residentMemoryBytes: Long? = null,
    val totalPhysicalMemoryBytes: Long? = null,
    val processCpuTimeMillis: Long? = null,
    val logicalProcessorCount: Int = 1,
)

/** Aggregated process resources for one benchmark sampling window. */
data class ProcessResourceUsage(
    val currentResidentMemoryBytes: Long? = null,
    val peakResidentMemoryBytes: Long? = null,
    val peakResidentMemoryDeltaBytes: Long? = null,
    val totalPhysicalMemoryBytes: Long? = null,
    val currentCpuLoadPercent: Double? = null,
    val peakCpuLoadPercent: Double? = null,
)

/**
 * Aggregates periodic OS samples into benchmark peak and baseline-delta metrics.
 *
 * CPU load is normalized by the logical processor count, so 100% represents all logical
 * processors being busy for the app process during the sample interval.
 */
class ProcessResourceTracker(
    initialSnapshot: ProcessResourceSnapshot,
    initialTimestampMillis: Long,
) {
    private var baselineResidentMemoryBytes = initialSnapshot.residentMemoryBytes.validCounter()
    private var peakResidentMemoryBytes = baselineResidentMemoryBytes
    private var previousSnapshot = initialSnapshot
    private var previousTimestampMillis = initialTimestampMillis
    private var latestUsage = ProcessResourceUsage(
        currentResidentMemoryBytes = baselineResidentMemoryBytes,
        peakResidentMemoryBytes = baselineResidentMemoryBytes,
        peakResidentMemoryDeltaBytes = baselineResidentMemoryBytes?.let { 0L },
        totalPhysicalMemoryBytes = initialSnapshot.totalPhysicalMemoryBytes.validCounter(),
    )

    val currentUsage: ProcessResourceUsage
        get() = latestUsage

    fun record(
        snapshot: ProcessResourceSnapshot,
        timestampMillis: Long,
    ): ProcessResourceUsage {
        val currentResidentMemory = snapshot.residentMemoryBytes.validCounter()
        if (baselineResidentMemoryBytes == null && currentResidentMemory != null) {
            baselineResidentMemoryBytes = currentResidentMemory
        }
        if (currentResidentMemory != null) {
            peakResidentMemoryBytes = max(peakResidentMemoryBytes ?: currentResidentMemory, currentResidentMemory)
        }

        val currentCpuLoad = calculateCpuLoadPercent(
            previous = previousSnapshot,
            current = snapshot,
            elapsedMillis = timestampMillis - previousTimestampMillis,
        )
        val previousPeakCpuLoad = latestUsage.peakCpuLoadPercent
        val peakCpuLoad = when {
            currentCpuLoad == null -> previousPeakCpuLoad
            previousPeakCpuLoad == null -> currentCpuLoad
            else -> max(previousPeakCpuLoad, currentCpuLoad)
        }
        val peakMemoryDelta = peakResidentMemoryBytes?.let { peak ->
            baselineResidentMemoryBytes?.let { baseline -> (peak - baseline).coerceAtLeast(0L) }
        }

        latestUsage = ProcessResourceUsage(
            currentResidentMemoryBytes = currentResidentMemory,
            peakResidentMemoryBytes = peakResidentMemoryBytes,
            peakResidentMemoryDeltaBytes = peakMemoryDelta,
            totalPhysicalMemoryBytes = snapshot.totalPhysicalMemoryBytes.validCounter()
                ?: latestUsage.totalPhysicalMemoryBytes,
            currentCpuLoadPercent = currentCpuLoad,
            peakCpuLoadPercent = peakCpuLoad,
        )
        previousSnapshot = snapshot
        previousTimestampMillis = timestampMillis
        return latestUsage
    }

    private fun calculateCpuLoadPercent(
        previous: ProcessResourceSnapshot,
        current: ProcessResourceSnapshot,
        elapsedMillis: Long,
    ): Double? {
        if (elapsedMillis <= 0L) return null
        val previousCpuTime = previous.processCpuTimeMillis.validCounter() ?: return null
        val currentCpuTime = current.processCpuTimeMillis.validCounter() ?: return null
        val cpuTimeDelta = currentCpuTime - previousCpuTime
        if (cpuTimeDelta < 0L) return null

        val processorCount = current.logicalProcessorCount.coerceAtLeast(1)
        return (cpuTimeDelta.toDouble() / elapsedMillis.toDouble() / processorCount * 100.0)
            .coerceIn(0.0, 100.0)
    }
}

expect fun getProcessResourceSnapshot(): ProcessResourceSnapshot

private fun Long?.validCounter(): Long? = this?.takeIf { it >= 0L }
