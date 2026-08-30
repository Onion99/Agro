package org.onion.agro.utils

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.lang.management.ManagementFactory

actual fun getProcessResourceSnapshot(): ProcessResourceSnapshot = ProcessResourceSnapshot(
    residentMemoryBytes = DesktopProcessResources.residentMemoryBytes(),
    totalPhysicalMemoryBytes = DesktopProcessResources.totalPhysicalMemoryBytes(),
    processCpuTimeMillis = ProcessHandle.current()
        .info()
        .totalCpuDuration()
        .orElse(null)
        ?.toMillis(),
    logicalProcessorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
)

private object DesktopProcessResources {
    private val osName = System.getProperty("os.name").lowercase()

    fun residentMemoryBytes(): Long? = when {
        osName.contains("linux") -> readLinuxMemoryBytes("/proc/self/status", "VmRSS")
        osName.contains("windows") -> WindowsProcessMemory.residentMemoryBytes()
        osName.contains("mac") -> MacProcessMemory.residentMemoryBytes()
        else -> null
    }

    fun totalPhysicalMemoryBytes(): Long? = runCatching {
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
            ?.totalMemorySize
            ?.takeIf { it > 0L }
    }.getOrNull()

    private fun readLinuxMemoryBytes(path: String, key: String): Long? = runCatching {
        File(path).useLines { lines ->
            lines.firstOrNull { it.startsWith(key) }
                ?.substringAfter(':')
                ?.trim()
                ?.substringBefore(' ')
                ?.toLongOrNull()
                ?.times(BYTES_PER_KIBIBYTE)
        }
    }.getOrNull()
}

private object WindowsProcessMemory {
    private val kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
    private val psapi by lazy { Native.load("psapi", Psapi::class.java) }

    fun residentMemoryBytes(): Long? = runCatching {
        val countersSize = DWORD_FIELD_BYTES * 2 + Native.SIZE_T_SIZE * PROCESS_MEMORY_SIZE_T_FIELD_COUNT
        val counters = Memory(countersSize.toLong()).apply {
            clear()
            setInt(0L, countersSize)
        }
        val process = kernel32.GetCurrentProcess()
        if (psapi.GetProcessMemoryInfo(process, counters, countersSize)) {
            val workingSetOffset = DWORD_FIELD_BYTES * 2L + Native.SIZE_T_SIZE
            val workingSet = if (Native.SIZE_T_SIZE == Long.SIZE_BYTES) {
                counters.getLong(workingSetOffset)
            } else {
                counters.getInt(workingSetOffset).toLong() and UINT_MASK
            }
            workingSet.takeIf { it >= 0L }
        } else {
            null
        }
    }.getOrNull()

    private interface Kernel32 : Library {
        fun GetCurrentProcess(): Pointer
    }

    private interface Psapi : Library {
        fun GetProcessMemoryInfo(
            process: Pointer,
            counters: Pointer,
            size: Int,
        ): Boolean
    }

    private const val DWORD_FIELD_BYTES = 4
    private const val PROCESS_MEMORY_SIZE_T_FIELD_COUNT = 8
    private const val UINT_MASK = 0xFFFF_FFFFL
}

private object MacProcessMemory {
    private const val MACH_TASK_BASIC_INFO = 20
    private const val MACH_TASK_BASIC_INFO_COUNT = 12
    private val systemLibrary by lazy { NativeLibrary.getInstance("System") }
    private val machApi by lazy { Native.load("System", MachApi::class.java) }

    fun residentMemoryBytes(): Long? = runCatching {
        val task = systemLibrary.getGlobalVariableAddress("mach_task_self_").getInt(0)
        val info = MachTaskBasicInfo()
        val count = IntByReference(MACH_TASK_BASIC_INFO_COUNT)
        val result = machApi.task_info(task, MACH_TASK_BASIC_INFO, info.pointer, count)
        if (result == 0) {
            info.read()
            info.residentSize.takeIf { it >= 0L }
        } else {
            null
        }
    }.getOrNull()

    private interface MachApi : Library {
        @Suppress("FunctionName")
        fun task_info(
            targetTask: Int,
            flavor: Int,
            taskInfo: Pointer,
            taskInfoCount: IntByReference,
        ): Int
    }

    @Structure.FieldOrder(
        "virtualSize",
        "residentSize",
        "residentSizeMax",
        "userTime",
        "systemTime",
        "policy",
        "suspendCount",
    )
    private class MachTaskBasicInfo : Structure() {
        @JvmField var virtualSize: Long = 0L
        @JvmField var residentSize: Long = 0L
        @JvmField var residentSizeMax: Long = 0L
        @JvmField var userTime = TimeValue()
        @JvmField var systemTime = TimeValue()
        @JvmField var policy: Int = 0
        @JvmField var suspendCount: Int = 0
    }

    @Structure.FieldOrder("seconds", "microseconds")
    private class TimeValue : Structure() {
        @JvmField var seconds: Int = 0
        @JvmField var microseconds: Int = 0
    }
}

private const val BYTES_PER_KIBIBYTE = 1024L
