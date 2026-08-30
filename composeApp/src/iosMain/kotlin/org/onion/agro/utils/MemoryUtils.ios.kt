package org.onion.agro.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSProcessInfo
import platform.darwin.KERN_SUCCESS
import platform.darwin.MACH_TASK_BASIC_INFO
import platform.darwin.MACH_TASK_BASIC_INFO_COUNT
import platform.darwin.mach_task_basic_info
import platform.darwin.mach_task_self_
import platform.darwin.task_info
import platform.posix.RUSAGE_SELF
import platform.posix.getrusage
import platform.posix.rusage

@OptIn(ExperimentalForeignApi::class)
actual fun getProcessResourceSnapshot(): ProcessResourceSnapshot = memScoped {
    val taskInfo = alloc<mach_task_basic_info>()
    val taskInfoResult = task_info(
        target_task = mach_task_self_,
        flavor = MACH_TASK_BASIC_INFO.toUInt(),
        task_info_out = taskInfo.ptr.reinterpret(),
        task_info_outCnt = cValuesOf(MACH_TASK_BASIC_INFO_COUNT.toUInt()),
    )

    val resourceUsage = alloc<rusage>()
    val resourceUsageResult = getrusage(RUSAGE_SELF, resourceUsage.ptr)
    val processCpuTime = if (resourceUsageResult == 0) {
        timevalToMillis(resourceUsage.ru_utime.tv_sec, resourceUsage.ru_utime.tv_usec) +
            timevalToMillis(resourceUsage.ru_stime.tv_sec, resourceUsage.ru_stime.tv_usec)
    } else {
        null
    }

    ProcessResourceSnapshot(
        residentMemoryBytes = if (taskInfoResult == KERN_SUCCESS) {
            taskInfo.resident_size.toLong()
        } else {
            null
        },
        totalPhysicalMemoryBytes = NSProcessInfo.processInfo.physicalMemory.toLong(),
        processCpuTimeMillis = processCpuTime,
        logicalProcessorCount = NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceAtLeast(1),
    )
}

private fun timevalToMillis(seconds: Long, microseconds: Int): Long =
    seconds * MILLIS_PER_SECOND + microseconds / MICROS_PER_MILLI

private const val MILLIS_PER_SECOND = 1_000L
private const val MICROS_PER_MILLI = 1_000
