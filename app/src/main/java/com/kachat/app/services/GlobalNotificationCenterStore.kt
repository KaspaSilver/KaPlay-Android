package com.kachat.app.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bell on the Wallet tab, KaPay's (iOS 1751d20 `GlobalNotificationCenter`). It holds two things:
 * - Kaspa arriving on your chatting address from someone else ([WalletReceiptNotifier]: KaPay's
 *   AddressActivityNotifier also watches spending and cold-storage addresses, which KaPlay's UTXO
 *   subscription doesn't);
 * - your .kachat names: offers, sales, renewal and expiry, what happened to your offers
 *   ([com.kachat.app.services.kachatnames.KachatNamesNotifier]).
 * Entries are account-scoped, persisted, deduped by id, and capped; opening the list marks
 * everything seen. The feed reloads whenever the active account changes (iOS reloads it on every
 * sign-in, switch, sign-out and removal).
 */
@Singleton
class GlobalNotificationCenterStore @Inject constructor(
    @ApplicationContext context: Context,
    private val walletManager: WalletManager,
) {
    data class Entry(
        val id: String,
        /** "wallet" or "kachat" ([IN_BELL]). */
        val source: String,
        val title: String,
        val body: String,
        val timestampMs: Long,
        /** The address that got the Kaspa, or the .kachat name - what tapping the row opens. */
        val targetId: String?,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<Entry>>() {}.type
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + com.kachat.app.util.StartupSafety.logOnlyHandler("GlobalNotifCenter"))

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _lastSeenAt = MutableStateFlow(0L)
    val lastSeenAt: StateFlow<Long> = _lastSeenAt.asStateFlow()

    private val maxEntries = 100
    private var loadedWallet: String? = null

    init {
        // Account switch, sign-out, removal: the active wallet's feed (iOS `reload()` beside every
        // `setActiveWalletAddress`).
        scope.launch {
            walletManager.activeAddressFlow.collect { reload(it) }
        }
    }

    /** Loads the active wallet's feed if it isn't the one in memory; call before reading. */
    @Synchronized
    fun reloadIfNeeded() {
        val wallet = walletManager.activeAddressFlow.value
        if (loadedWallet == wallet) return
        reload(wallet)
    }

    /** Loads [wallet]'s feed (none when signed out). Rows a source this build no longer lists
     *  are dropped, so one of those never shows (iOS `StoredEntry`). */
    @Synchronized
    private fun reload(wallet: String?) {
        loadedWallet = wallet
        if (wallet == null) {
            _entries.value = emptyList()
            _lastSeenAt.value = 0L
            return
        }
        val loaded: List<Entry> = try {
            gson.fromJson<List<Entry>>(prefs.getString(entriesKey(wallet), null) ?: "[]", listType) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val kept = loaded.filter { it.source in IN_BELL && it.id.isNotEmpty() }
        _entries.value = kept
        _lastSeenAt.value = prefs.getLong(seenKey(wallet), 0L)
        if (kept.size != loaded.size) persist()
    }

    private fun persist() {
        val wallet = loadedWallet ?: return
        prefs.edit().putString(entriesKey(wallet), gson.toJson(_entries.value.take(maxEntries))).apply()
    }

    // Feed mutations

    @Synchronized
    fun record(id: String, source: String, title: String, body: String, timestampMs: Long, targetId: String?) {
        if (source !in IN_BELL) return
        reloadIfNeeded()
        if (loadedWallet == null) return
        if (id.isEmpty() || _entries.value.any { it.id == id }) return
        _entries.value = (listOf(Entry(id, source, title, body, timestampMs, targetId)) + _entries.value)
            .take(maxEntries)
        persist()
    }

    @Synchronized
    fun markAllSeen() {
        val wallet = loadedWallet ?: return
        _lastSeenAt.value = System.currentTimeMillis()
        prefs.edit().putLong(seenKey(wallet), _lastSeenAt.value).apply()
    }

    @Synchronized
    fun clearAll() {
        _entries.value = emptyList()
        persist()
    }

    /** Public rooms aren't in KaPlay's bell (iOS 1751d20 lists Received and .kachat rows only).
     *  Kept as a no-op for [BroadcastScanningService]'s call site. */
    @Suppress("UNUSED_PARAMETER")
    fun recordBroadcastIfLive(channel: String, senderAddress: String, senderName: String, content: String, txId: String, blockTimeMs: Long) {}

    companion object {
        private const val PREFS = "global_notification_center"

        /** What the bell lists (iOS `Entry.Source`: wallet, kachat). */
        val IN_BELL = setOf("wallet", "kachat")

        private fun entriesKey(wallet: String) = "entries_$wallet"
        private fun seenKey(wallet: String) = "seen_$wallet"

        /** The source tag a row shows (iOS `Entry.Source.label`). */
        fun sourceLabel(source: String): String = when (source) {
            "wallet" -> "Wallet"
            "kachat" -> ".kachat"
            else -> source
        }

        /**
         * Delete Account: one wallet's feed, from this device (iOS `removeAllData(forWalletAddress:)`,
         * called by [WalletManager.removeWalletTabData]). Static, as WalletManager can't take the
         * store (the store takes WalletManager); the feed in memory follows the active account,
         * which the removal clears.
         */
        fun removeAllData(context: Context, walletAddress: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(entriesKey(walletAddress))
                .remove(seenKey(walletAddress))
                .apply()
        }
    }
}
