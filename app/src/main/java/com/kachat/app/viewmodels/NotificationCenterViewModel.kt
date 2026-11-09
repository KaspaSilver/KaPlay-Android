package com.kachat.app.viewmodels

import androidx.lifecycle.ViewModel
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.services.GlobalNotificationCenterStore
import com.kachat.app.services.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Thin Compose bridge to the Wallet tab's bell (iOS 1751d20 `GlobalNotificationCenter`). */
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    val store: GlobalNotificationCenterStore,
    private val walletManager: WalletManager,
    private val addressReader: ColdStorageAddressDiscovery,
) : ViewModel() {
    init {
        store.reloadIfNeeded()
    }

    /** Which of your addresses a tapped "Received" row named (iOS 1751d20 `OwnAddressHistorySheet.Resolved`). */
    sealed interface OwnAddress {
        data object Chatting : OwnAddress
        /** A spending address of the active account, by its index (opens `manage_address/{index}`). */
        data class Spending(val index: Int) : OwnAddress
        data object Unknown : OwnAddress
        /** Its coins couldn't be read: a page showing 0 would say it's empty, and its Send would
         *  be off (iOS `.unreadable`). */
        data object Unreadable : OwnAddress
    }

    /**
     * The chatting address, a spending address of this wallet, or neither (iOS 1751d20
     * `OwnAddressHistorySheet.resolve()`): the chatting address at once; otherwise the address's
     * coins are read first - unreadable if they can't be - then it is matched against the active
     * account's spending addresses on both chains (cache-first, as WalletManager derives them).
     */
    suspend fun resolveOwnAddress(address: String): OwnAddress = withContext(Dispatchers.IO) {
        val target = address.trim().lowercase()
        if (walletManager.activeAddressFlow.value?.lowercase() == target) return@withContext OwnAddress.Chatting
        try {
            addressReader.getUtxosOrThrow(address)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext OwnAddress.Unreadable
        }
        val account = walletManager.getActiveAccount() ?: return@withContext OwnAddress.Unknown
        val base = WalletManager.QUANTUM_INDEX_BASE
        val indices = (0..maxOf(0, WalletManager.legacyMaxSpendingIndex(account))) +
            (base until base + WalletManager.maxQuantumIndex(account) + 1)
        val match = runCatching { walletManager.deriveSpendingAddresses(indices) }.getOrDefault(emptyMap())
            .entries.firstOrNull { it.value.lowercase() == target }
        if (match != null) OwnAddress.Spending(match.key) else OwnAddress.Unknown
    }
}
