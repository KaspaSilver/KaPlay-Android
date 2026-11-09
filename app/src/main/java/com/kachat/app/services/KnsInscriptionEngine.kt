package com.kachat.app.services

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kachat.app.models.PendingKnsCommit
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaMass
import com.kachat.app.util.KaspaTransactionId
import com.kachat.app.util.KaspaTransactionSigner
import com.kachat.app.util.KaspaUtxoSelector
import com.kachat.app.util.KnsInscriptionScript
import com.kachat.app.util.Schnorr
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Builds and submits KNS commit/reveal inscription transactions — the same commit->reveal
 * pattern used for both domain registration and profile field updates. Mirrors
 * [KaspaWalletEngine]'s structure; verified byte-for-byte against the iOS reference
 * (`KaChat/Services/KaChatTransactionBuilder.swift:480-1188`).
 */
@Singleton
class KnsInscriptionEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val networkService: NetworkService,
    private val nodePoolManager: NodePoolManager,
    private val settings: AppSettingsRepository
) {
    data class CommitResult(
        val commitTxId: String,
        val redeemScript: ByteArray,
        val commitScriptPubKeyHex: String,
        val commitAmountSompi: Long,
        val revealAmountSompi: Long
    )

    /**
     * A transfer whose commit went out, or may have, and whose reveal isn't accepted yet: all a
     * retry needs to finish it instead of committing another 2 KAS. Saved until it's done (iOS
     * da59726 `KNSDomainTransferService.PendingTransfer`). The commit's output is always index 0.
     */
    data class PendingTransfer(
        val assetId: String,
        val domain: String,
        val recipient: String,
        val account: String,
        val sourceAddress: String,
        val sourceIndex: Int?,
        val changeAddress: String,
        val commitTxId: String,
        val redeemScriptHex: String,
        val commitAddress: String,
        val commitScriptPubKeyHex: String,
        val commitAmountSompi: Long,
        val revealAmountSompi: Long,
        val priorityFeeSompi: Long
    ) {
        val commit: CommitResult
            get() = CommitResult(commitTxId, redeemScriptHex.hexBytes(), commitScriptPubKeyHex, commitAmountSompi, revealAmountSompi)

        /** Hand-written JSON, so no field name depends on R8 (see proguard-rules.pro on Gson stores). */
        fun json(): JsonObject = JsonObject().apply {
            addProperty("assetId", assetId)
            addProperty("domain", domain)
            addProperty("recipient", recipient)
            addProperty("account", account)
            addProperty("sourceAddress", sourceAddress)
            sourceIndex?.let { addProperty("sourceIndex", it) }
            addProperty("changeAddress", changeAddress)
            addProperty("commitTxId", commitTxId)
            addProperty("redeemScriptHex", redeemScriptHex)
            addProperty("commitAddress", commitAddress)
            addProperty("commitScriptPubKeyHex", commitScriptPubKeyHex)
            addProperty("commitAmountSompi", commitAmountSompi)
            addProperty("revealAmountSompi", revealAmountSompi)
            addProperty("priorityFeeSompi", priorityFeeSompi)
        }

        companion object {
            /** Null for a record this version can't read. */
            fun from(o: JsonObject): PendingTransfer? = runCatching {
                fun text(f: String) = o.get(f).asString
                fun amount(f: String) = o.get(f).asLong
                PendingTransfer(
                    assetId = text("assetId"), domain = text("domain"), recipient = text("recipient"), account = text("account"),
                    sourceAddress = text("sourceAddress"), sourceIndex = o.get("sourceIndex")?.asInt, changeAddress = text("changeAddress"),
                    commitTxId = text("commitTxId"), redeemScriptHex = text("redeemScriptHex"), commitAddress = text("commitAddress"),
                    commitScriptPubKeyHex = text("commitScriptPubKeyHex"), commitAmountSompi = amount("commitAmountSompi"),
                    revealAmountSompi = amount("revealAmountSompi"), priorityFeeSompi = amount("priorityFeeSompi")
                )
            }.getOrNull()
        }
    }

    private val pendingPrefs by lazy { appContext.getSharedPreferences(PENDING_TRANSFERS_FILE, Context.MODE_PRIVATE) }

    /** The signed commits of this run, so one whose outcome is unknown can go out again: the
     *  same transaction, which can't spend twice (iOS da59726). */
    private val signedCommits = ConcurrentHashMap<String, RawTransaction>()

    private fun pendingTransfers(): List<PendingTransfer> = runCatching {
        val raw = pendingPrefs.getString(PENDING_TRANSFERS_KEY, null) ?: return emptyList()
        JsonParser.parseString(raw).asJsonArray.mapNotNull { PendingTransfer.from(it.asJsonObject) }
    }.getOrDefault(emptyList())

    private fun storePendingTransfers(all: List<PendingTransfer>) {
        val array = JsonArray()
        all.forEach { array.add(it.json()) }
        pendingPrefs.edit().putString(PENDING_TRANSFERS_KEY, array.toString()).apply()
    }

    /** The unfinished transfer of [assetId] by [account], if any. */
    fun pendingTransfer(assetId: String, account: String): PendingTransfer? =
        pendingTransfers().firstOrNull { it.assetId == assetId && it.account == account }

    /** The saved form of [commit], a transfer of [assetId] from [sourceAddress] to [recipient]. */
    fun pendingRecord(
        commit: CommitResult,
        assetId: String,
        domain: String,
        recipient: String,
        account: String,
        sourceAddress: String,
        sourceIndex: Int?,
        changeAddress: String,
        priorityFeeSompi: Long
    ): PendingTransfer = PendingTransfer(
        assetId = assetId, domain = domain, recipient = recipient, account = account,
        sourceAddress = sourceAddress, sourceIndex = sourceIndex, changeAddress = changeAddress,
        commitTxId = commit.commitTxId, redeemScriptHex = commit.redeemScript.toHex(),
        commitAddress = KnsInscriptionScript.commitAddress(commit.redeemScript, sourceAddress.substringBefore(":")),
        commitScriptPubKeyHex = commit.commitScriptPubKeyHex, commitAmountSompi = commit.commitAmountSompi,
        revealAmountSompi = commit.revealAmountSompi, priorityFeeSompi = priorityFeeSompi
    )

    /** Saves the record of a commit about to go out, or one just accepted under another id. */
    fun savePendingTransfer(pending: PendingTransfer, signedCommit: RawTransaction? = null) {
        storePendingTransfers(pendingTransfers().filterNot { it.assetId == pending.assetId && it.account == pending.account } + pending)
        signedCommit?.let { signedCommits[pending.commitTxId] = it }
    }

    fun clearPendingTransfer(pending: PendingTransfer) {
        storePendingTransfers(pendingTransfers().filterNot { it.assetId == pending.assetId && it.account == pending.account })
        signedCommits.remove(pending.commitTxId)
    }

    /**
     * Finishes [pending] without a new commit: the (commit, reveal) ids, or null when there is
     * nothing to finish (the commit never landed, or its coin is spent) and the transfer starts
     * over. Throws when it can't tell - a failed read never leads to a new commit on a guess
     * (iOS da59726 `resumeTransfer`).
     */
    suspend fun resumeTransfer(
        pending: PendingTransfer,
        recipient: String,
        ownerKey: () -> ByteArray,
        onSubmittingCommit: () -> Unit,
        onSubmittingReveal: () -> Unit
    ): Pair<String, String>? {
        val api = networkService.kaspaRestApi.value ?: throw IllegalStateException("Network service unavailable")
        val listed = nodePoolManager.getUtxosByAddress(pending.commitAddress) ?: api.getUtxos(pending.commitAddress)
        var commitIsLive = listed.any {
            it.outpoint.transactionId.equals(pending.commitTxId, ignoreCase = true) && it.outpoint.index == 0
        }
        val commitTx = signedCommits[pending.commitTxId]
        if (!commitIsLive && commitTx != null) {
            onSubmittingCommit()
            commitIsLive = try {
                nodePoolManager.submitConfirmingKnown(commitTx, api) {
                    nodePoolManager.submitWaitingForParent(commitTx, allowOrphan = false)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (unknown: SendOutcomeUnknownException) {
                throw unknown
            } catch (e: Exception) {
                false
            }
        }
        if (!commitIsLive) {
            Log.i("KnsInscriptionEngine", "RESUME nothing to finish commit=${pending.commitTxId}")
            clearPendingTransfer(pending)
            return null
        }
        // the commit names its recipient: finishing it can only pay that one
        if (!pending.recipient.equals(recipient, ignoreCase = true)) {
            throw IllegalStateException("An earlier transfer of this domain to ${pending.recipient} didn't finish. Send it to that address to finish it, without paying for a new one.")
        }
        val revealTx = buildRevealTx(pending.commit, pending.sourceAddress, pending.changeAddress, ownerKey(), pending.priorityFeeSompi)
        onSubmittingReveal()
        val revealTxId = submitRevealTx(revealTx)
        clearPendingTransfer(pending)
        return pending.commitTxId to revealTxId
    }

    // Serializes commit/reveal builds the same way KaspaWalletEngine.sendKaspa does, for the
    // same reason: overlapping UTXO fetch+select would otherwise race and double-spend.
    private val mutex = Mutex()

    suspend fun buildAndSubmitCommit(
        payloadJson: ByteArray,
        commitAmountSompi: Long,
        revealAmountSompi: Long,
        revealTargetAddress: String,
        operationType: String,
        fundingAddress: String,
        fundingPrivateKey: ByteArray,
        ownerPrivateKey: ByteArray,
        /** Where the commit's funding change goes; defaults back to [fundingAddress]. Used when
         *  the primary spending address funds a transfer and its change must land on a fresh
         *  address (see WalletService.transferDomain). */
        changeAddress: String = fundingAddress,
        /** A transfer's own record (iOS da59726): called with the signed commit, under its own
         *  id, before it goes out. When set, the commit isn't saved as the [PendingKnsCommit]. */
        beforeSubmit: (suspend (CommitResult, RawTransaction) -> Unit)? = null
    ): CommitResult = mutex.withLock {
        val api = networkService.kaspaRestApi.value ?: throw IllegalStateException("Network service unavailable")

        // The redeem script's embedded pubkey is [ownerPrivateKey]'s (identity), not the funding
        // key's — that's what the KNS indexer treats as the domain/profile's owner, and what the
        // later reveal signature must satisfy. [fundingAddress]/[fundingPrivateKey] only pay for
        // the commit output; they never touch ownership.
        val hrp = fundingAddress.substringBefore(":")
        val xOnlyPubKey = Schnorr.publicKeyXOnly(ownerPrivateKey)
        val redeemScript = KnsInscriptionScript.buildRedeemScript(xOnlyPubKey, "kns", payloadJson)
        val commitAddress = KnsInscriptionScript.commitAddress(redeemScript, hrp)
        val commitScriptPubKeyHex = KaspaAddress.getScriptPublicKey(commitAddress)
        val changeScriptHex = KaspaAddress.getScriptPublicKey(changeAddress)

        // Node first, REST second - see NodePoolManager.getUtxosByAddress. Not a mining reward
        // that may not be mature, nor a coin another send still in flight spent: name and profile
        // writes skip coins another send holds (iOS 1751d20 `unreservedUtxos`).
        val utxos = (nodePoolManager.getUtxosByAddress(fundingAddress) ?: api.getUtxos(fundingAddress))
            .filter { !it.utxoEntry.isCoinbase && it.utxoEntry.blockDaaScore > 0 }
            .filter { !nodePoolManager.isSpentOutpointReserved(KaspaWalletEngine.outpointKey(it.outpoint)) }
        if (utxos.isEmpty()) throw IllegalStateException("No spendable UTXOs available for KNS inscription")

        val feeRateSompiPerGram = fetchFeeRate(api)

        val selection = KaspaUtxoSelector.selectUtxosAndCalculateFee(
            utxos = utxos,
            amountSompi = commitAmountSompi,
            feeRateSompiPerGram = feeRateSompiPerGram,
            payloadBytes = null,
            recipientScriptLen = commitScriptPubKeyHex.length / 2,
            changeScriptLen = changeScriptHex.length / 2
        )
        if (selection.totalSelected < selection.requiredAmount) {
            throw IllegalStateException("Insufficient funds: needed ${selection.requiredAmount}, have ${selection.totalSelected}")
        }

        val outputs = mutableListOf<RawOutputWithVersion>(
            RawOutputWithVersion(amount = commitAmountSompi, scriptPublicKey = ScriptPublicKeyWithVersion(commitScriptPubKeyHex, 0))
        )
        // Change stays when this transaction's KIP-9 storage mass allows it (see
        // KaspaMass.storageMass), the same rule every other builder uses; otherwise it is fee.
        val commitInputs = selection.selectedUtxos.map { it.utxoEntry.amount }
        // Only dust is ever folded into the fee (iOS dd4d977) - see KaspaWalletEngine.
        if (selection.storageMassBlocked || (selection.changeAmount > 0 &&
                !KaspaUtxoSelector.changeIsKeptOrFoldable(commitInputs, listOf(commitAmountSompi), selection.changeAmount))
        ) {
            throw IllegalStateException(KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE)
        }
        if (selection.changeAmount > 0 && KaspaMass.fitsStorageMass(commitInputs, listOf(commitAmountSompi, selection.changeAmount))) {
            outputs.add(RawOutputWithVersion(amount = selection.changeAmount, scriptPublicKey = ScriptPublicKeyWithVersion(changeScriptHex, 0)))
        }

        val rawTx = RawTransaction(
            inputs = selection.selectedUtxos.map { RawInput(previousOutpoint = it.outpoint, signatureScript = "") },
            outputs = outputs
        )
        val signedTx = KaspaTransactionSigner.signTransaction(rawTx, selection.selectedUtxos, fundingPrivateKey)
        beforeSubmit?.invoke(
            CommitResult(KaspaTransactionId.compute(signedTx), redeemScript, commitScriptPubKeyHex, commitAmountSompi, revealAmountSompi),
            signedTx
        )
        // A submit error is checked against the network before it counts (audit IOS-014).
        val commitTxId = nodePoolManager.submitConfirmingKnown(signedTx, api) {
            nodePoolManager.submitWaitingForParent(signedTx, allowOrphan = false)
        }

        val result = CommitResult(commitTxId, redeemScript, commitScriptPubKeyHex, commitAmountSompi, revealAmountSompi)

        // Persist BEFORE attempting reveal — if reveal fails or the app dies here, the commit
        // amount is still recoverable on next launch instead of silently stuck. changeAddress is
        // needed so a retry (possibly after the active spending address has since rotated) still
        // returns the reveal's leftover change to the same address the commit was funded from.
        if (beforeSubmit == null) settings.setPendingKnsCommit(
            PendingKnsCommit(
                commitTxId = commitTxId,
                redeemScriptHex = redeemScript.toHex(),
                commitScriptPubKeyHex = commitScriptPubKeyHex,
                commitAmountSompi = commitAmountSompi,
                revealAmountSompi = revealAmountSompi,
                revealTargetAddress = revealTargetAddress,
                operationType = operationType,
                changeAddress = changeAddress
            )
        )

        result
    }

    suspend fun buildAndSubmitReveal(
        commit: CommitResult,
        revealTargetAddress: String,
        changeAddress: String,
        ownerPrivateKey: ByteArray,
        priorityFeeSompi: Long = REVEAL_PRIORITY_FEE_SOMPI
    ): String = mutex.withLock {
        val signedTx = buildRevealTx(commit, revealTargetAddress, changeAddress, ownerPrivateKey, priorityFeeSompi)
        val revealTxId = submitRevealTx(signedTx)
        settings.clearPendingKnsCommit()
        revealTxId
    }

    /**
     * The signed reveal of [commit], not sent. A transfer builds it before its commit goes out,
     * from the commit's own id: a reveal that can't be paid for stops there, with nothing spent
     * (iOS da59726).
     */
    suspend fun buildRevealTx(
        commit: CommitResult,
        revealTargetAddress: String,
        changeAddress: String,
        ownerPrivateKey: ByteArray,
        priorityFeeSompi: Long = REVEAL_PRIORITY_FEE_SOMPI
    ): RawTransaction {
        val api = networkService.kaspaRestApi.value ?: throw IllegalStateException("Network service unavailable")

        val targetScriptHex = KaspaAddress.getScriptPublicKey(revealTargetAddress)
        val changeScriptHex = KaspaAddress.getScriptPublicKey(changeAddress)
        val feeRateSompiPerGram = fetchFeeRate(api)

        val recipientOutput = if (commit.revealAmountSompi > 0) {
            RawOutputWithVersion(amount = commit.revealAmountSompi, scriptPublicKey = ScriptPublicKeyWithVersion(targetScriptHex, 0))
        } else null
        val baseOutputs = listOfNotNull(recipientOutput)

        // Signature script is push(sig+hashtype) + push(redeemScript) — much bigger than the
        // standard 66-byte Schnorr push, so mass/fee must account for the real size.
        val sigScriptSize = 66L + KnsInscriptionScript.canonicalPushSize(commit.redeemScript.size)

        require(commit.commitAmountSompi >= commit.revealAmountSompi) {
            "KNS reveal amount cannot exceed the commit amount"
        }

        val massWithChange = KaspaMass.calculateMass(
            numInputs = 1,
            outputScriptLens = (baseOutputs.map { it.scriptPublicKey.scriptPublicKey.length / 2 }) + (changeScriptHex.length / 2),
            payloadSize = 0,
            sigScriptLens = listOf(sigScriptSize)
        )
        val feeWithChange = KaspaMass.calculateFee(massWithChange, feeRateSompiPerGram) + priorityFeeSompi

        val outputs = baseOutputs.toMutableList()
        val availableForChangeAndFee = commit.commitAmountSompi - commit.revealAmountSompi

        require(availableForChangeAndFee >= feeWithChange) { "KNS reveal amount cannot be covered by commit output" }
        val changeWithFee = availableForChangeAndFee - feeWithChange

        // The reveal spends exactly one input, the commit output, so the shape is fully known
        // here: that input against the reveal output (if any) plus this change. Decided by KIP-9
        // storage mass (see KaspaMass.storageMass), not a flat floor.
        val revealInputs = listOf(commit.commitAmountSompi)
        val baseAmounts = baseOutputs.map { it.amount }
        if (changeWithFee > 0 && KaspaMass.fitsStorageMass(revealInputs, baseAmounts + changeWithFee)) {
            outputs.add(RawOutputWithVersion(amount = changeWithFee, scriptPublicKey = ScriptPublicKeyWithVersion(changeScriptHex, 0)))
        } else {
            // Change would not stand on its own — recompute the fee for a transaction with no
            // change output (lower mass, so a lower fee), and see if THAT leaves enough instead.
            val massNoChange = KaspaMass.calculateMass(
                numInputs = 1,
                outputScriptLens = baseOutputs.map { it.scriptPublicKey.scriptPublicKey.length / 2 },
                payloadSize = 0,
                sigScriptLens = listOf(sigScriptSize)
            )
            val feeNoChange = KaspaMass.calculateFee(massNoChange, feeRateSompiPerGram) + priorityFeeSompi
            require(availableForChangeAndFee >= feeNoChange) { "Insufficient commit amount for KNS reveal fee" }
            val changeNoChange = availableForChangeAndFee - feeNoChange
            if (changeNoChange > 0 && KaspaMass.fitsStorageMass(revealInputs, baseAmounts + changeNoChange)) {
                outputs.add(RawOutputWithVersion(amount = changeNoChange, scriptPublicKey = ScriptPublicKeyWithVersion(changeScriptHex, 0)))
            }
        }
        require(outputs.isNotEmpty()) { "KNS reveal transaction has no spendable outputs after fees" }

        val commitUtxo = UtxoEntry(
            address = changeAddress,
            outpoint = Outpoint(transactionId = commit.commitTxId, index = 0),
            utxoEntry = UtxoData(
                amount = commit.commitAmountSompi,
                scriptPublicKey = ScriptPublicKey(commit.commitScriptPubKeyHex),
                blockDaaScore = 0,
                isCoinbase = false
            )
        )

        val rawTx = RawTransaction(
            inputs = listOf(RawInput(previousOutpoint = commitUtxo.outpoint, signatureScript = "")),
            outputs = outputs
        )
        return KaspaTransactionSigner.signRevealInput(rawTx, commitUtxo, commit.redeemScript, ownerPrivateKey)
    }

    /** Sends a signed reveal: an orphan rejection first waits for the commit to reach that node. */
    suspend fun submitRevealTx(signedTx: RawTransaction): String {
        val connection = nodePoolManager.getBroadcastConnection()
        return nodePoolManager.submitConfirmingKnown(signedTx, networkService.kaspaRestApi.value) {
            try {
                // an orphan rejection first waits for the commit to reach that node (iOS 4eb492f)
                nodePoolManager.submitWaitingForParent(signedTx, allowOrphan = false)
            } catch (e: Exception) {
                if (e.message?.contains("orphan", ignoreCase = true) == true) {
                    Log.w("KnsInscriptionEngine", "Reveal rejected as orphan, retrying with allowOrphan=true", e)
                    connection.submitTransaction(signedTx, allowOrphan = true)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun fetchFeeRate(api: KaspaRestApi): Long {
        return try {
            val estimate = api.getFeeEstimate()
            val quoted = estimate.normalBuckets.firstOrNull()?.feerate ?: KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM.toDouble()
            ceil(quoted).toLong().coerceAtLeast(KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM)
        } catch (e: Exception) {
            Log.w("KnsInscriptionEngine", "Failed to fetch fee estimate, using network minimum", e)
            KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val REVEAL_PRIORITY_FEE_SOMPI = 2_000_000L
        /** The most a transfer's fee can be: the reveal pays it out of the 2 KAS commit (iOS da59726). */
        const val MAX_PRIORITY_FEE_SOMPI = 100_000_000L
        private const val PENDING_TRANSFERS_FILE = "kaplay_kns_pending_transfers"
        private const val PENDING_TRANSFERS_KEY = "kaplay_kns_pending_transfers_v1"
    }
}

private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
