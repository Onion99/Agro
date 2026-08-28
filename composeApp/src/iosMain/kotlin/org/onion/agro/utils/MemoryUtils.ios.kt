package org.onion.agro.utils

actual fun getAppMemoryUsageMb(): Pair<Long, Long> {
    return Pair(256L, 4096L)
}
