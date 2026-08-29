package org.onion.agro.native.llm

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkCancellationBarrierTest {

    private class MockBenchmarkSession {
        val callOrder = mutableListOf<String>()
        var isCancelled = false
        var isClosed = false

        fun cancelProcess() {
            callOrder += "cancelProcess"
            isCancelled = true
        }

        fun close() {
            callOrder += "close"
            isClosed = true
        }
    }

    @Test
    fun barrierGuaranteesCancelBeforeJoinAndCloseAfterJoin() = runTest {
        val session = MockBenchmarkSession()
        val timeline = mutableListOf<String>()
        val mutex = Mutex()

        var job: Job? = null
        job = launch(Dispatchers.Default) {
            try {
                flow {
                    emit("token1")
                    delay(500)
                    emit("token2")
                }.collect {
                    timeline += "received:$it"
                }
            } catch (e: CancellationException) {
                timeline += "jobCancelled"
                throw e
            }
        }

        // Give the coroutine a moment to start collecting
        while (!timeline.contains("received:token1")) {
            delay(10)
        }

        // Execute barrier logic: native cancel → cancelAndJoin → close
        mutex.withLock {
            timeline += "barrier:cancelProcess"
            session.cancelProcess()

            timeline += "barrier:cancelAndJoin"
            job.cancelAndJoin()

            timeline += "barrier:close"
            session.close()
        }

        assertEquals("cancelProcess", session.callOrder[0])
        assertEquals("close", session.callOrder[1])

        val indexOfJobCancelled = timeline.indexOf("jobCancelled")
        val indexOfClose = timeline.indexOf("barrier:close")
        assertTrue(indexOfJobCancelled < indexOfClose, "Job must complete cancellation before close() is invoked")
    }

    @Test
    fun multipleStopRequestsAreSerializedAndIdempotent() = runTest {
        val session = MockBenchmarkSession()
        val mutex = Mutex()
        var job: Job? = launch(Dispatchers.Default) {
            delay(2000)
        }

        suspend fun stopAndWait() = mutex.withLock {
            val activeJob = job
            if (activeJob == null && session.isClosed) return@withLock
            session.cancelProcess()
            activeJob?.cancelAndJoin()
            session.close()
            job = null
        }

        // Run 5 concurrent stop requests
        val stopJobs = (1..5).map {
            async(Dispatchers.Default) {
                stopAndWait()
            }
        }
        stopJobs.awaitAll()

        assertTrue(session.isCancelled)
        assertTrue(session.isClosed)
        // Exactly one cancel and one close due to activeJob nulling & isClosed check
        assertEquals(listOf("cancelProcess", "close"), session.callOrder)
    }
}
