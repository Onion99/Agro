package org.onion.agro.native.llm

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LmInferenceGateTest {
    @Test
    fun rejectsSecondInferenceUntilActiveLeaseIsReleased() {
        val gate = LmInferenceGate()
        val chatLease = assertNotNull(gate.tryAcquire())

        assertNull(gate.tryAcquire())

        chatLease.release()
        assertNotNull(gate.tryAcquire())
    }
}
