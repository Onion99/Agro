package org.onion.agro.utils

actual fun getAppMemoryUsageMb(): Pair<Long, Long> {
    val runtime = Runtime.getRuntime()
    val used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val max = runtime.maxMemory() / (1024 * 1024)
    return Pair(used, max)
}
