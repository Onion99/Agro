package org.onion.agro.utils

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformProcessResourceSnapshotTest {
    @Test
    fun reportsRealProcessAndHostCounters() {
        val snapshot = getProcessResourceSnapshot()

        assertTrue(snapshot.logicalProcessorCount >= 1)
        assertTrue(assertNotNull(snapshot.residentMemoryBytes) > 0L)
        assertTrue(assertNotNull(snapshot.totalPhysicalMemoryBytes) > 0L)
        assertTrue(assertNotNull(snapshot.processCpuTimeMillis) >= 0L)
    }
}
