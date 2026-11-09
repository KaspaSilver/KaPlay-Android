package com.kachat.app.util

import com.kachat.app.services.UtxoEntry

/**
 * The Send screens' fee model, as on iOS (SendKaspaComponents / SpendingAddressWithdrawView) and
 * the chat Send KAS sheet: a base fee priced at the quoted network rate on the REAL input count,
 * plus Fast / Priority / a custom fee as a flat extra on top, paid exactly
 * ([com.kachat.app.services.KaspaWalletEngine.sendKaspa]'s `extraFeeSompi`).
 *
 * The screens used to turn the chosen total fee into a rate (total / one-input mass) and pass
 * that rate to the engine, which charges `rate * realMass`. Every extra input then multiplied the
 * fee: a 0.1 KAS custom fee on a 20-input send paid about 1.14 KAS. A flat extra cannot scale
 * with the input count, so the fee shown is the fee paid.
 *
 * Pure, so the screens, the view models and the unit tests all price a send the same way.
 */
object SendFeeModel {
    /** Standard P2PK scriptPublicKey length, for a recipient or change output whose exact script
     *  is not known yet. */
    const val STANDARD_SCRIPT_LEN = 34

    /** A fresh quantum-safe (KIP-22) slot's scriptPublicKey: `OP_BLAKE2B <32 bytes> OP_EQUAL`, a
     *  byte longer than [STANDARD_SCRIPT_LEN]. Every slot's is this long, and length is all a fee
     *  sees (iOS da59726 `plannedChangeScript`). */
    const val QUANTUM_CHANGE_SCRIPT_LEN = 35

    /** Passes of [previewAutomaticSelection]'s fixed point. Each pass that does not settle adds at
     *  least one input, so this only bounds pathological coin sets. */
    private const val MAX_PREVIEW_PASSES = 16

    /** The extra over [baseFeeSompi]: the custom extra when one is set, else what the speed adds
     *  (Fast 2x, Priority 5x the base). */
    fun extraFee(baseFeeSompi: Long, feeMultiplier: Long, customExtraFeeSompi: Long?): Long =
        customExtraFeeSompi?.coerceAtLeast(0L) ?: (baseFeeSompi * (feeMultiplier - 1)).coerceAtLeast(0L)

    /** The coins one transaction can spend: the [maxInputs] largest when there are more, else all
     *  of them (order kept). Mirrors the engine's refusal of anything over
     *  [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] inputs. */
    fun largestSpendable(
        utxos: List<UtxoEntry>,
        maxInputs: Int = KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION,
    ): List<UtxoEntry> =
        if (utxos.size > maxInputs) utxos.sortedByDescending { it.utxoEntry.amount }.take(maxInputs) else utxos

    /** Base fee for [inputCount] inputs paying a recipient plus change. */
    fun baseFee(
        inputCount: Int,
        feeRateSompiPerGram: Long,
        payloadSize: Int = 0,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** Per-input signature-script size: a quantum-safe (KIP-22) source's inputs are bigger
         *  ([KaspaMass.KIP22_SIGNATURE_SCRIPT_LEN]). */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
    ): Long =
        KaspaMass.calculateFee(
            KaspaMass.calculateMass(
                numInputs = inputCount.coerceAtLeast(1),
                outputScriptLens = listOf(recipientScriptLen, changeScriptLen),
                payloadSize = payloadSize,
                sigScriptLens = KaspaMass.sigScriptLensFor(inputCount.coerceAtLeast(1), signatureScriptSize),
            ),
            feeRateSompiPerGram,
        )

    /**
     * Max for a send that spends [totalSompi] over [inputCount] inputs: what is left after the
     * base fee and the extra. The extra is derived from this same base (or is the custom extra),
     * never from a fee estimated for some other amount - so Max always leaves room for exactly the
     * extra a Fast / Priority send of that amount adds.
     */
    fun maxAfterFees(
        totalSompi: Long,
        inputCount: Int,
        feeRateSompiPerGram: Long,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        payloadSize: Int = 0,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** The change output's real script length (iOS da59726): a Max priced a byte short would
         *  leave the send's own fee uncovered. */
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
    ): Long {
        val base = baseFee(inputCount, feeRateSompiPerGram, payloadSize, changeScriptLen = changeScriptLen, signatureScriptSize = signatureScriptSize)
        val extra = extraFee(base, feeMultiplier, customExtraFeeSompi)
        return (totalSompi - base - extra).coerceAtLeast(0L)
    }

    /** What an automatic-selection send of a given amount spends, and what it pays. Passing
     *  [utxos] as coin control and [extraFeeSompi] as the extra reproduces exactly this fee. */
    /** [foldedChangeSompi]: change too small to keep, which the send gives to the fee - the wallet
     *  loses it like any other fee, so the Review counts it (iOS 8d5f925 `SendFeeEstimate`). */
    data class Preview(
        val utxos: List<UtxoEntry>,
        val baseFeeSompi: Long,
        val extraFeeSompi: Long,
        val foldedChangeSompi: Long = 0L,
        /** Set when the engine would refuse this send (see [sendBlockedMessage]): shown instead of
         *  a fee, and the swipe waits for another amount (iOS estimate errors). */
        val blockedMessage: String? = null,
    ) {
        val totalFeeSompi: Long get() = baseFeeSompi + extraFeeSompi + foldedChangeSompi
    }

    /**
     * Why the engine would refuse a send of [amountSompi] from coins of [inputAmounts] leaving
     * [changeSompi], or null when it goes out - KaspaWalletEngine's own rules: change that can't
     * stand as an output is folded into the fee only up to [KaspaUtxoSelector.MAX_FOLDED_CHANGE_SOMPI],
     * and the transaction must fit storage mass as built (iOS's selector refuses the same).
     */
    fun sendBlockedMessage(inputAmounts: List<Long>, amountSompi: Long, changeSompi: Long): String? {
        if (changeSompi > 0 && !KaspaUtxoSelector.changeIsKeptOrFoldable(inputAmounts, listOf(amountSompi), changeSompi)) {
            return KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE
        }
        val kept = changeSompi > 0 && KaspaMass.fitsStorageMass(inputAmounts, listOf(amountSompi, changeSompi))
        val outputs = if (kept) listOf(amountSompi, changeSompi) else listOf(amountSompi)
        if (!KaspaMass.fitsStorageMass(inputAmounts, outputs)) return TOO_SMALL_TO_SEND_MESSAGE
        return null
    }

    /** Within this of the Review's fee a send rebuilt at the swipe still goes out (iOS 4922804
     *  `reviewFeeMarginSompi`). */
    const val REVIEW_FEE_MARGIN_SOMPI = 1_000L

    /** What a transaction spending [inputAmounts] and paying [outputAmounts] gives the network -
     *  folded change included - or null when the amounts are not real values or don't add up. */
    fun paidFee(inputAmounts: List<Long>, outputAmounts: List<Long>): Long? {
        val inTotal = UtxoMath.checkedTotal(inputAmounts) ?: return null
        val outTotal = UtxoMath.checkedTotal(outputAmounts) ?: return null
        return (inTotal - outTotal).takeIf { inTotal >= outTotal }
    }

    /** A send that would pay [paidFeeSompi] stops when that is more than [REVIEW_FEE_MARGIN_SOMPI]
     *  over the [reviewFeeSompi] its Review showed (iOS 4922804 `checkFeeAgainstReview`). */
    fun exceedsReviewFee(paidFeeSompi: Long, reviewFeeSompi: Long): Boolean =
        paidFeeSompi > reviewFeeSompi + REVIEW_FEE_MARGIN_SOMPI

    /** The engine's words for a send consensus would reject on storage mass. */
    const val TOO_SMALL_TO_SEND_MESSAGE =
        "This amount is too small to send from the coins available (Kaspa storage-mass limit). Try a larger amount, or consolidate this address first."

    /**
     * What automatic selection (largest-first, [KaspaUtxoSelector.selectUtxosAndCalculateFee] - the
     * selector the real send runs) picks for [amountSompi], with the base fee priced on those
     * inputs and the extra on top. Mirrors ColdStorageSendEngine.previewAutomaticSelection, which
     * the Send screen's display used to lack: it assumed one input.
     *
     * The extra changes how many inputs are needed (they must cover amount + base + extra), and a
     * tier's extra depends on the base, so this settles the two together. Whatever pass it ends
     * on, [Preview.baseFeeSompi] is priced on the inputs selected WITH [Preview.extraFeeSompi], so
     * a send passing that extra pays [Preview.totalFeeSompi]. Null when the coins cannot cover it,
     * or when their amounts are not real values ([UtxoMath]).
     */
    fun previewAutomaticSelection(
        utxos: List<UtxoEntry>,
        amountSompi: Long,
        feeRateSompiPerGram: Long,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        recipientScriptLen: Int = STANDARD_SCRIPT_LEN,
        changeScriptLen: Int = STANDARD_SCRIPT_LEN,
        /** See [baseFee]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
    ): Preview? {
        if (amountSompi <= 0 || utxos.isEmpty()) return null
        var extra = customExtraFeeSompi?.coerceAtLeast(0L) ?: 0L
        var preview: Preview? = null
        repeat(MAX_PREVIEW_PASSES) {
            val selection = try {
                KaspaUtxoSelector.selectUtxosAndCalculateFee(
                    utxos = utxos,
                    amountSompi = amountSompi,
                    feeRateSompiPerGram = feeRateSompiPerGram,
                    payloadBytes = null,
                    recipientScriptLen = recipientScriptLen,
                    changeScriptLen = changeScriptLen,
                    extraFeeSompi = extra,
                    signatureScriptSize = signatureScriptSize,
                )
            } catch (e: IllegalStateException) {
                return null // invalid node data (UtxoMath): no preview rather than a wrong one
            }
            if (selection.totalSelected < selection.requiredAmount) return null
            val base = selection.estimatedFee - extra
            // The engine's rule: change stands as its own output when storage mass allows it, and
            // otherwise goes to the fee.
            val change = selection.changeAmount
            val inputAmounts = selection.selectedUtxos.map { it.utxoEntry.amount }
            val folded = if (change > 0 && !KaspaMass.fitsStorageMass(inputAmounts, listOf(amountSompi, change))) change else 0L
            val blocked = if (selection.storageMassBlocked) KaspaUtxoSelector.SMALL_SEND_MASS_MESSAGE
                else sendBlockedMessage(inputAmounts, amountSompi, change)
            val settled = Preview(selection.selectedUtxos, base, extra, folded, blocked)
            if (blocked != null) return settled
            preview = settled
            val next = extraFee(base, feeMultiplier, customExtraFeeSompi)
            if (next == extra) return settled
            extra = next
        }
        return preview
    }
}

/**
 * A send built at the swipe would pay more than its Review showed: its coins (or the network's
 * rate) changed in between (`SendFeeModel.exceedsReviewFee`). Nothing was sent; the Review works
 * its fee out again (iOS 4922804 `SendFeeChangedError`).
 */
class SendFeeChangedException(val feeSompi: Long) : IllegalStateException(
    KaspaUnit.label("The network fee is now ${KaspaUnit.plain(feeSompi)} KAS, more than the Review showed. Nothing was sent: check the new fee and swipe again.")
)
