package com.kachat.app.services

import android.util.Log
import com.kachat.app.util.KaspaUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import protowire.Rpc
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Received" rows for the Wallet tab's bell ([GlobalNotificationCenterStore]): Kaspa that reached
 * the chatting address from someone else (iOS 1751d20 `WalletReceiptNotifier`). Fed every live
 * UTXO batch ([handle]; iOS calls it from `ChatService.handleUtxoChangeNotification`). A
 * transaction counts as ours, and never shows, when the same batch spent one of our coins, or when
 * any of its inputs (the Kaspa REST API's resolved previous outpoints) is one of our addresses - so
 * a move's change, a send's change, a compound, a .kachat name transaction and anything another app
 * on the same seed sends stay out. A transaction that can't be read isn't shown: a missed row is
 * better than a wrong one. One row per transaction, the outputs to us summed.
 *
 * The live batches: iOS KaPlay's chat service keeps a `NotifyUtxosChanged` subscription on the
 * chatting address; KaPlay Android had none, so this holds it ([start] / [stop] with the app in
 * front, from [com.kachat.app.KaChatApplication]) - the chatting address only, as iOS, so spending
 * addresses get no Received rows. A failed subscribe (no node yet, a node without a UTXO index)
 * retries with capped backoff (1 s doubling to 30 s), a dropped stream re-subscribes on the next
 * connection, and a switch of account re-subscribes with the new address.
 */
@Singleton
class WalletReceiptNotifier @Inject constructor(
    private val nodePoolManager: NodePoolManager,
    private val walletManager: WalletManager,
    private val networkService: NetworkService,
    private val bell: GlobalNotificationCenterStore,
) {
    /** One output a live batch added or removed. */
    data class LiveUtxo(
        val address: String,
        val transactionId: String,
        val index: Int,
        val amount: Long,
        val isCoinbase: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler(TAG))
    private var runJob: Job? = null

    /** Transactions already looked at, so a batch delivered twice is looked at once. */
    private val handled: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private val _utxoActivity = MutableSharedFlow<Set<String>>(extraBufferCapacity = 16)

    /**
     * Any UTXO change on one of our addresses, its own change included: the involved addresses,
     * lowercased (iOS `.ownAddressUtxoActivity` with `utxoActivityAddressesKey`). Open balances and
     * lists refresh on it (KaPay's event).
     */
    val utxoActivity: SharedFlow<Set<String>> = _utxoActivity.asSharedFlow()

    // The subscription

    /** App in front: subscribe (again) for whichever account is active, following switches. */
    @Synchronized
    fun start() {
        if (runJob?.isActive == true) return
        runJob = scope.launch {
            walletManager.activeAddressFlow.collectLatest { address ->
                if (address != null) runSubscription(address)
            }
        }
    }

    /** App away: the subscription is dropped. */
    @Synchronized
    fun stop() {
        runJob?.cancel()
        runJob = null
    }

    /** Subscribes [walletAddress] (the chatting address) and keeps it subscribed until cancelled. */
    private suspend fun runSubscription(walletAddress: String) {
        var attempts = 0
        var subscribedOn: com.kachat.app.services.grpc.KaspadConnection? = null
        try {
            while (true) {
                val connection = nodePoolManager.getBroadcastConnection()
                if (!connection.isConnected) connection.connect()
                val generation = connection.connectionGeneration.value
                val notifications = try {
                    connection.subscribeToUtxosChanged(listOf(walletAddress))
                } catch (e: Exception) {
                    // A subscribe that timed out is a failed attempt, not this job being stopped
                    // (iOS da59726 `retryResubscribe`).
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    // Capped exponential backoff: a dead pool must not burn an attempt every second.
                    val delaySeconds = minOf(30.0, Math.pow(2.0, attempts.toDouble()))
                    attempts++
                    Log.w(TAG, "UTXO subscription failed (retry in ${delaySeconds.toInt()}s): ${e.message}")
                    delay((delaySeconds * 1000).toLong())
                    continue
                }
                // Back after failed attempts: what confirmed during the gap never reached us, so
                // open balances look again (iOS da59726 `retryResubscribe`).
                if (attempts > 0) _utxoActivity.tryEmit(setOf(walletAddress.lowercase()))
                attempts = 0
                subscribedOn = connection
                val collector = scope.launch {
                    notifications.collect { message -> onBatch(message, walletAddress) }
                }
                try {
                    // Until the stream dies (a new generation).
                    while (connection.connectionGeneration.value == generation && connection.isConnected) {
                        delay(RESUBSCRIBE_CHECK_MS)
                    }
                    Log.i(TAG, "UTXO stream dropped - re-subscribing")
                } finally {
                    collector.cancel()
                }
            }
        } finally {
            // Stop the node sending this account's changes once nobody is listening. Part of the
            // teardown, not a coroutine of its own: `collectLatest` waits for this before the next
            // address subscribes, so on a switch A -> B -> A the old NOTIFY_STOP for A can no longer
            // land after A's new subscribe and silently end it. Bounded, so a hung node can't hold
            // the next subscription back.
            val connection = subscribedOn
            if (connection != null && connection.isConnected) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(UNSUBSCRIBE_TIMEOUT_MS) {
                        runCatching { connection.unsubscribeFromUtxosChanged(listOf(walletAddress)) }
                    }
                }
            }
        }
    }

    private suspend fun onBatch(message: Rpc.UtxosChangedNotificationMessage, walletAddress: String) {
        if (walletManager.activeAddressFlow.value != walletAddress) return
        // More than the Kaspa supply in one batch is a broken or hostile node's: dropped (iOS
        // 283cd28, IOS-020).
        val added = runCatching { liveEntries(message.addedList) }.getOrNull() ?: return
        val removed = runCatching { liveEntries(message.removedList) }.getOrNull() ?: return
        try {
            handle(added, removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Live UTXO batch failed", e)
        }
    }

    private fun liveEntries(entries: List<Rpc.RpcUtxosByAddressesEntry>): List<LiveUtxo> =
        com.kachat.app.util.UtxoMath.checkedFromNetwork(entries) { it.utxoEntry.amount }.mapNotNull { entry ->
            val txId = entry.outpoint.transactionId
            if (entry.address.isEmpty() || txId.isEmpty()) return@mapNotNull null
            LiveUtxo(
                address = entry.address,
                transactionId = txId,
                index = entry.outpoint.index,
                amount = entry.utxoEntry.amount,
                isCoinbase = entry.utxoEntry.isCoinbase,
            )
        }

    // The receipt check (iOS `handle(_:)`)

    fun handle(added: List<LiveUtxo>, removed: List<LiveUtxo>) {
        val wallet = walletManager.activeAddressFlow.value ?: return
        val own = walletManager.ownAddressSetLowercased()
        // Any UTXO change on one of our addresses: open balances and lists refresh (KaPay's event).
        val involved = (added + removed).map { it.address.lowercase() }.filter { it in own }.toSet()
        if (involved.isNotEmpty()) _utxoActivity.tryEmit(involved)
        // The same batch spent one of our coins: whatever it added is our own change.
        if (removed.any { it.address.lowercase() in own }) return
        val amounts = LinkedHashMap<String, Long>()
        // one entry per outpoint: the same output delivered twice must not count twice (IOS-071)
        for (entry in added.distinctBy { "${it.transactionId}:${it.index}" }) {
            if (entry.isCoinbase || !entry.address.equals(wallet, ignoreCase = true)) continue
            amounts[entry.transactionId] = (amounts[entry.transactionId] ?: 0L) + entry.amount
        }
        for ((txId, amount) in amounts) {
            if (!handled.add(txId)) continue
            // iOS also skips a transaction this app sent (`isKnownOutgoingAttemptTxId`); KaPlay
            // Android keeps no such list, and its inputs (ours) leave it out below anyway.
            scope.launch { confirmAndRecord(txId, amount, wallet) }
        }
    }

    private suspend fun confirmAndRecord(txId: String, amount: Long, wallet: String) {
        // The API can be a moment behind the node: asked again a few times.
        var transaction: TransactionResponse? = null
        for (attempt in 0 until 4) {
            if (attempt > 0) delay(4_000)
            val api = networkService.kaspaRestApi.value ?: continue
            val tx = try {
                api.getTransactionWithInputAddresses(txId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (tx != null && tx.inputs.isNotEmpty() && tx.inputs.all { !it.previousOutpointAddress.isNullOrEmpty() }) {
                transaction = tx
                break
            }
        }
        if (transaction == null || walletManager.activeAddressFlow.value != wallet) return
        val own = walletManager.ownAddressSetLowercased()
        if (transaction.inputs.any { own.contains(it.previousOutpointAddress.orEmpty().lowercase()) }) return
        bell.record(
            id = "wallet-$txId",
            source = SOURCE,
            title = "Received ${formatKas(amount)} ${KaspaUnit.symbol}",
            body = "Chatting address ${shortAddress(wallet)}",
            timestampMs = System.currentTimeMillis(),
            targetId = wallet,
        )
    }

    companion object {
        private const val TAG = "WalletReceiptNotifier"
        const val SOURCE = "wallet"
        /** How often the subscription checks its stream. */
        private const val RESUBSCRIBE_CHECK_MS = 5_000L
        /** The longest the teardown waits for the node to take the unsubscribe. */
        private const val UNSUBSCRIBE_TIMEOUT_MS = 5_000L

        fun formatKas(sompi: Long): String =
            String.format(java.util.Locale.US, "%.8f", sompi.toDouble() / 100_000_000.0).trimEnd('0').trimEnd('.')

        private fun shortAddress(address: String): String =
            if (address.length > 16) "${address.take(12)}...${address.takeLast(4)}" else address
    }
}
