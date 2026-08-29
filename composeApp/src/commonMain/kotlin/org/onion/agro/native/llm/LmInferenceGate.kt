package org.onion.agro.native.llm

import kotlinx.coroutines.sync.Mutex

/**
 * Grants at most one inference lease for a shared native LM engine.
 *
 * A lease may be acquired and released by different coroutines because the
 * underlying native inference outlives the coroutine that starts it.
 */
internal class LmInferenceGate {
    private val mutex = Mutex()

    fun tryAcquire(): Lease? {
        val owner = Any()
        return if (mutex.tryLock(owner)) Lease(mutex, owner) else null
    }

    internal class Lease(
        private val mutex: Mutex,
        private val owner: Any,
    ) {
        fun release() {
            mutex.unlock(owner)
        }
    }
}
