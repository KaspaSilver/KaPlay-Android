package com.kachat.app.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A submit the network accepted is never reported as failed (iOS ad29da9, audit IOS-014). */
class SubmitConfirmationTest {

    private val txId = "ab".repeat(32)

    @Test
    fun `a successful submit returns the node's id without any lookup`() = runBlocking {
        var lookups = 0
        val id = SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { lookups++; true }) { "node-id" }
        assertEquals("node-id", id)
        assertEquals(0, lookups)
    }

    @Test
    fun `an already-in-mempool rejection of a transaction the network has is a sent transaction`() = runBlocking {
        var recovered: Exception? = null
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { it == txId },
            onRecovered = { recovered = it },
        ) { throw IllegalStateException("transaction $txId is already in the mempool") }
        assertEquals(txId, id)
        assertTrue(recovered is IllegalStateException)
    }

    @Test
    fun `a timeout followed by a successful re-check is a sent transaction`() = runBlocking {
        val id = SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { true }) {
            withTimeout(1) { kotlinx.coroutines.delay(1_000); "never" }
        }
        assertEquals(txId, id)
    }

    @Test
    fun `a rejection of a transaction the network does not have is still a failure`() = runBlocking {
        val error = IllegalStateException("fee too low")
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }) { throw error }
            fail("expected the submit's own error")
        } catch (e: IllegalStateException) {
            assertSame(error, e)
        }
    }

    @Test
    fun `without a local id there is no lookup and the error stands`() = runBlocking {
        var lookups = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown("", isKnown = { lookups++; true }) { throw IllegalStateException("x") }
            fail("expected the error")
        } catch (e: IllegalStateException) {
            assertEquals(0, lookups)
        }
    }

    @Test
    fun `a cancelled send stays cancelled`() = runBlocking {
        var lookups = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { lookups++; true }) { throw CancellationException("gone") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals(0, lookups)
        }
    }

    @Test
    fun `the lookup asks the mempool then the REST API, twice`() = runBlocking {
        val asked = mutableListOf<String>()
        val known = SubmitConfirmation.isKnown(
            txId,
            inMempool = { asked += "mempool"; false },
            acceptedViaRest = { asked += "rest"; false },
            delayMs = 0,
        )
        assertFalse(known)
        assertEquals(listOf("mempool", "rest", "mempool", "rest"), asked)
    }

    @Test
    fun `a transaction that shows up on the second look is known`() = runBlocking {
        var looks = 0
        val known = SubmitConfirmation.isKnown(
            txId,
            inMempool = { looks++; looks > 1 },
            acceptedViaRest = { false },
            delayMs = 0,
        )
        assertTrue(known)
    }

    @Test
    fun `accepted via REST alone is known`() = runBlocking {
        assertTrue(SubmitConfirmation.isKnown(txId, inMempool = { false }, acceptedViaRest = { true }, delayMs = 0))
    }
    @Test
    fun `an already-known refusal is no verdict, a refusal of the transaction itself is (iOS 4922804)`() {
        assertFalse(SubmitConfirmation.isNodeVerdict(IllegalStateException("Rejected transaction $txId: transaction $txId is already in the mempool")))
        assertFalse(SubmitConfirmation.isNodeVerdict(IllegalStateException("transaction $txId is an orphan where orphan is disallowed")))
        assertFalse(SubmitConfirmation.isNodeVerdict(IllegalStateException("output already spent by transaction in the memory pool")))
        assertFalse(SubmitConfirmation.isNodeVerdict(IllegalStateException("missing outpoint")))
        assertTrue(SubmitConfirmation.isNodeVerdict(IllegalStateException("Rejected transaction $txId: transaction has insufficient fee")))
        assertTrue(SubmitConfirmation.isNodeVerdict(IllegalStateException("failed to verify the signature script")))
        assertFalse(SubmitConfirmation.isNodeVerdict(java.net.SocketTimeoutException("timeout")))
    }

    @Test
    fun `after a node's answer the stricter lookup decides, after a lost answer the usual one`() = runBlocking {
        val asked = mutableListOf<String>()
        try {
            SubmitConfirmation.submitOrConfirmKnown(
                txId,
                isKnown = { asked += "any"; true },
                isKnownAfterRejection = { asked += "strict"; false },
            ) { throw IllegalStateException("transaction $txId is already in the mempool") }
            fail("expected the submit's own error")
        } catch (e: IllegalStateException) {
            assertEquals(listOf("strict"), asked)
        }
        asked.clear()
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { asked += "any"; true },
            isKnownAfterRejection = { asked += "strict"; false },
        ) { throw java.net.SocketTimeoutException("no answer") }
        assertEquals(txId, id)
        assertEquals(listOf("any"), asked)
    }

    @Test
    fun `a lost answer during the orphan retries rides along on the error thrown`() = runBlocking {
        val orphan = IllegalStateException("transaction $txId is an orphan where orphan is disallowed")
        var calls = 0
        try {
            SubmitConfirmation.submitWaitingForParent(false, delayMs = 0) {
                calls++
                when (calls) {
                    1 -> throw orphan
                    2 -> throw java.net.SocketTimeoutException("no answer")
                    else -> throw IllegalStateException("transaction $txId is an orphan where orphan is disallowed")
                }
            }
            fail("expected the first rejection")
        } catch (e: IllegalStateException) {
            assertSame(orphan, e)
            assertTrue(SubmitConfirmation.isAnswerLost(e))
        }
    }
}
