package org.onion.agro.utils

import android.os.Debug
import android.os.Process
import java.io.File

actual fun getProcessResourceSnapshot(): ProcessResourceSnapshot = ProcessResourceSnapshot(
    residentMemoryBytes = readProcMemoryBytes("/proc/self/status", "VmRSS")
        ?: Debug.getPss().takeIf { it >= 0L }?.times(BYTES_PER_KIBIBYTE),
    totalPhysicalMemoryBytes = readProcMemoryBytes("/proc/meminfo", "MemTotal"),
    processCpuTimeMillis = Process.getElapsedCpuTime(),
    logicalProcessorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
)

private fun readProcMemoryBytes(path: String, key: String): Long? = runCatching {
    File(path).useLines { lines ->
        lines.firstOrNull { it.startsWith(key) }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()
            ?.times(BYTES_PER_KIBIBYTE)
    }
}.getOrNull()

private const val BYTES_PER_KIBIBYTE = 1024L
