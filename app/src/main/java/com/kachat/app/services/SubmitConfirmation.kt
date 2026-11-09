package com.kachat.app.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * A failed submit is not always a failed send (iOS ad29da9, audit IOS-014).
 *
 * A node can accept a transaction while its answer times out; the retry that follows (a fresh
 * connection, an allowOrphan resubmit, the REST gateway) then gets "already in the mempool",
 * "already accepted", an orphan or a double-spend verdict for the very same transaction.
 * Reporting that as a failure invites a resend that pays twice. So before a submit error is
 * passed on, the transaction's own id - computed locally, before anything was sent - is looked
 * up: found in a mempool or accepted, the send succeeded.
 *
 * Pure apart from the lookups it is handed, so the decision is unit-tested.
 */
/**
 * A send a node may have received without ever answering, which the network doesn't have yet. It
 * may still land, so nothing should offer to send it again: the send screen shows "Not confirmed"
 * with [MESSAGE] and only Done (iOS 8d5f925 `SendOutcomeUnknownError`).
 */
class SendOutcomeUnknownException(val txId: String) : IllegalStateException(MESSAGE) {
    companion object {
        const val MESSAGE = "This may have gone through, but the network didn't confirm it. Check your balance before sending again."
    }
}

object SubmitConfirmation {

    /** Between the two lookups: a just-accepted transaction may not have propagated yet. */
    const val RECHECK_DELAY_MS = 1_500L

    /** How long a send rejected as an orphan gives its parent to reach the node (iOS 4eb492f). */
    const val PARENT_WAIT_MS = 1_500L

    /** Whether a submit error is the node's "orphan where orphan is disallowed" (iOS
     *  `NodePoolService.isOrphanRejection`). */
    fun isOrphanRejection(e: Throwable): Boolean = (e.message ?: e.toString()).lowercase().contains("orphan")

    /**
     * Runs [submit] with [allowOrphan] (iOS 4eb492f, NodePoolService.submitTransaction). A
     * rejection as an orphan "where orphan is disallowed" means the transaction's inputs came from
     * a node that already has their parent transaction (the UTXO query), but the node this submit
     * reached hasn't caught up yet - a reaction or message sent right after the previous one. Then
     * the parent gets [delayMs] to propagate and the submit runs once more; if that is refused too,
     * the node is asked to hold it as an orphan until the parent arrives ([onHeldAsOrphan] is told
     * the id). When neither goes through, the first rejection is what is thrown, as before. Any
     * other error, or a submit that already allowed orphans, is thrown at once. A cancelled send
     * stays cancelled (a timeout is not a cancellation of the send).
     */
    suspend fun submitWaitingForParent(
        allowOrphan: Boolean,
        delayMs: Long = PARENT_WAIT_MS,
        onHeldAsOrphan: (String) -> Unit = {},
        submit: suspend (allowOrphan: Boolean) -> String,
    ): String {
        try {
            return submit(allowOrphan)
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            if (allowOrphan || !isOrphanRejection(e)) throw e
            delay(delayMs)
            // A retry whose answer was lost rides along on the error thrown, so the send still
            // takes the lost-answer path: a node may hold it (iOS 4922804, `answerLost` set by the
            // orphan retries too).
            val lost = mutableListOf<Exception>()
            attempt(lost) { submit(false) }?.let { return it }
            attempt(lost) { submit(true) }?.let { id ->
                onHeldAsOrphan(id)
                return id
            }
            lost.filter { it !== e && isAnswerLost(it) }.forEach(e::addSuppressed)
            throw e
        }
    }

    /** [block]'s id, or null when it failed (iOS `try?`), its error added to [errors]; a
     *  cancelled send stays cancelled. */
    private suspend fun attempt(errors: MutableList<Exception>, block: suspend () -> String): String? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        currentCoroutineContext().ensureActive()
        errors += e
        null
    }

    /**
     * Runs [submit]. On any error, [txId] is looked up with [isKnown]; if the network has it,
     * [txId] is returned as the sent transaction's id and [onRecovered] is told why, otherwise
     * the submit's own error is thrown. A cancelled send stays cancelled (a timeout is not a
     * cancellation of the send).
     *
     * [isKnownAfterRejection] is the lookup used when the error is an answer, not a lost one: a
     * node turned the transaction down, so a REST server typed into Settings isn't enough to call
     * it sent - only a mempool, or the built-in REST server (iOS 4922804 `afterRejection`).
     */
    suspend fun submitOrConfirmKnown(
        txId: String,
        isKnown: suspend (String) -> Boolean,
        isKnownOnce: suspend (String) -> Boolean = isKnown,
        isKnownAfterRejection: suspend (String) -> Boolean = isKnown,
        onRecovered: (Exception) -> Unit = {},
        submit: suspend () -> String,
    ): String {
        try {
            return submit()
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            currentCoroutineContext().ensureActive()
            val lookup = if (isAnswerLost(e)) isKnown else isKnownAfterRejection
            if (txId.isNotEmpty() && lookup(txId)) {
                onRecovered(e)
                return txId
            }
            // A node may have the transaction and its answer never came, so a resend could pay
            // twice: ask for longer, then call the outcome unknown rather than a failure (iOS
            // 8d5f925 `confirmAfterLostAnswer`).
            if (txId.isNotEmpty() && isAnswerLost(e)) {
                repeat(LOST_ANSWER_CHECKS) {
                    delay(LOST_ANSWER_CHECK_DELAY_MS)
                    if (isKnownOnce(txId)) {
                        onRecovered(e)
                        return txId
                    }
                }
                throw SendOutcomeUnknownException(txId)
            }
            throw e
        }
    }

    private const val LOST_ANSWER_CHECKS = 6
    private const val LOST_ANSWER_CHECK_DELAY_MS = 3_000L

    /** The request went out and no answer came back: a timeout. A node's verdict on the
     *  transaction (a rejection) is an answer. A request whose answer was lost earlier in the
     *  same send rides along as the error's cause or a suppressed error, so a later "couldn't
     *  connect" doesn't hide it (iOS 8d5f925 `SubmitAnswerLostFlag`). */
    fun isAnswerLost(e: Throwable): Boolean =
        generateSequence(e) { it.cause?.takeIf { cause -> cause !== it } }.take(8)
            .any { isTimeout(it) || it.suppressed.any(::isTimeout) }

    private fun isTimeout(e: Throwable): Boolean {
        if (e is TimeoutCancellationException || e is java.net.SocketTimeoutException) return true
        val status = (e as? io.grpc.StatusException)?.status ?: (e as? io.grpc.StatusRuntimeException)?.status
        return status?.code == io.grpc.Status.Code.DEADLINE_EXCEEDED
    }

    /** A node's or the gateway's answer that the transaction can't be valid - a rejection -
     *  rather than no answer at all. After one of these, an earlier lost answer no longer makes the
     *  outcome unknown: something did look at the transaction and said no. Only a real verdict
     *  does this (iOS 4922804): an "already known" style refusal ([rejectionMayMeanAlreadyKnown])
     *  is what a node says when another node - the one whose answer was lost - already took and
     *  relayed this very transaction, so it leaves the lost-answer path in place. */
    fun isNodeVerdict(e: Throwable): Boolean {
        if (isTimeout(e)) return false
        if (e is retrofit2.HttpException) return e.code() in 400..499 && e.code() != 429
        if (e is io.grpc.StatusException || e is io.grpc.StatusRuntimeException || e is java.io.IOException) return false
        val message = e.message ?: ""
        if (rejectionMayMeanAlreadyKnown(message)) return false
        val text = message.lowercase()
        return listOf("reject", "mass", "fee", "invalid", "signature", "script", "standard", "dust").any { it in text }
    }

    /** A rejection a node also gives for a transaction the network already has (sent through
     *  another node): in a mempool, accepted, its inputs spent by it, or its parents not yet seen.
     *  A bad signature, a low fee or too much mass is a verdict on the transaction itself (iOS
     *  4922804 `rejectionMayMeanAlreadyKnown`). */
    fun rejectionMayMeanAlreadyKnown(message: String): Boolean {
        val lower = message.lowercase()
        return listOf("already", "orphan", "double spend", "double-spend", "spent", "missing outpoint", "mempool", "memory pool")
            .any { it in lower }
    }

    /**
     * Whether the network has [txId]: in some node's mempool ([inMempool]), or accepted
     * ([acceptedViaRest]). Asked twice, [delayMs] apart.
     */
    suspend fun isKnown(
        txId: String,
        inMempool: suspend (String) -> Boolean,
        acceptedViaRest: suspend (String) -> Boolean,
        delayMs: Long = RECHECK_DELAY_MS,
    ): Boolean {
        for (attempt in 0 until 2) {
            if (attempt > 0) delay(delayMs)
            if (inMempool(txId)) return true
            if (acceptedViaRest(txId)) return true
        }
        return false
    }
}
