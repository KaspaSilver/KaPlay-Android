package com.kachat.app.viewmodels

import com.kachat.app.util.UserFacingError
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kachat.app.models.PendingKnsCommit
import com.kachat.app.models.WalletSourceFamily
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.services.KaspaWalletEngine
import com.kachat.app.services.KnsInscriptionEngine
import com.kachat.app.services.KnsProfileFields
import com.kachat.app.services.KnsService
import com.kachat.app.services.SpendingAddressDiscovery
import com.kachat.app.services.UtxoEntry
import com.kachat.app.services.WalletManager
import com.kachat.app.services.WalletService
import com.kachat.app.util.KaspaMass
import com.kachat.app.util.KaspaUtxoSelector
import com.kachat.app.util.SendFeeModel
import com.kachat.app.util.UtxoMath
import com.kachat.app.util.ImagePrep
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** How many identity-chain indices one "Change Chatting Address" scan pass covers (matches iOS). */
const val CHATTING_ADDRESS_SCAN_BATCH = 50

@HiltViewModel
class WalletViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val walletManager: WalletManager,
    private val walletService: WalletService,
    private val walletEngine: KaspaWalletEngine,
    private val knsService: KnsService,
    private val settings: AppSettingsRepository,
    private val spendingAddressDiscovery: SpendingAddressDiscovery,
    /** Reused purely for its address-string-keyed REST fetchers (`getTransactionHistory`/
     *  `getUtxos`) - it has no Cold-Storage-account/kpub state, so it's just as valid a data
     *  source here as it is for Cold Storage's own tx-history screen. */
    private val coldStorageAddressDiscovery: ColdStorageAddressDiscovery,
    private val onboardingGate: com.kachat.app.services.OnboardingGate,
    private val chatRepository: com.kachat.app.repository.ChatRepository,
    /** The .k and .kaspa name services (Your Domains tabs, name resolution) - iOS NameServicesClient. */
    val nameServices: com.kachat.app.services.NameServicesClient,
    /** .kachat owners count toward the "Contains domain" tag (iOS 881ada6). Lazy, and only asked
     *  where the registry is launched, so mainnet never builds it (iOS 7227d69). */
    private val kachatRegistry: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesRegistry>,
    /** A deleted account's .kachat local reminders go with it (iOS 6128f1e, IOS-088). */
    private val kachatNameReminders: com.kachat.app.services.kachatnames.KachatNameReminders,
    /** Who still owes the backup steps (iOS ddec987 AccountBackupStatus). */
    val accountBackupStatus: com.kachat.app.services.AccountBackupStatus,
    /** A deleted account's bot games go with it (iOS da59726). */
    private val chessBotProgress: com.kachat.app.services.ChessBotProgress,
    /** The last account's rooms go: what was paged in of them goes too (iOS da59726). */
    private val broadcastRepository: com.kachat.app.repository.BroadcastRepository,
) : ViewModel() {

    /** Refreshes what the active account owns on .k and .kaspa. */
    fun refreshServiceNames() {
        val address = walletManager.activeAddressFlow.value ?: return
        viewModelScope.launch { nameServices.refresh(address) }
    }

    suspend fun refreshServiceNamesAndAwait() {
        val address = walletManager.activeAddressFlow.value ?: return
        nameServices.refresh(address)
    }


    /**
     * The send screens' sends ([onSendClicked], [withdrawFromSpendingAddress]), each kept to the
     * presentation that started it - see [com.kachat.app.util.PresentationSends]. A send screen
     * closed mid-send (iOS lets Cancel close it at any time) leaves its send running here, and
     * its outcome never reaches the next send screen.
     */
    val sends = com.kachat.app.util.PresentationSends<String>()

    // Fires when the user taps a bottom tab that's already selected — lets that tab's screen
    // dismiss its own transient UI (e.g. a full-screen QR overlay) instead of the tap being a
    // dead no-op, since re-navigating to an already-selected destination doesn't recompose it.
    // The counter makes every tap distinct even when re-tapping the same route repeatedly.
    private val _tabReselectSignal = MutableStateFlow(0 to "")
    val tabReselectSignal: StateFlow<Pair<Int, String>> = _tabReselectSignal.asStateFlow()

    fun notifyTabReselected(route: String) {
        _tabReselectSignal.value = (_tabReselectSignal.value.first + 1) to route
    }

    // A tab route's own screen can toggle an internal full-screen state (e.g. Cold Storage's QR
    // scanner) without navigating to a new route — the floating bottom nav bar's visibility is
    // otherwise purely route-based, so it would stay overlaid on top of a full-screen camera view.
    // Screens raising this must always clear it again on dismiss (including back-press).
    private val _hideBottomBar = MutableStateFlow(false)
    val hideBottomBar: StateFlow<Boolean> = _hideBottomBar.asStateFlow()

    fun setHideBottomBar(hide: Boolean) {
        _hideBottomBar.value = hide
    }

    private val _hasWallet = MutableStateFlow(walletManager.hasWallet())
    val hasWallet: StateFlow<Boolean> = _hasWallet

    private val _mnemonic = MutableStateFlow<List<String>?>(null)
    val mnemonic: StateFlow<List<String>?> = _mnemonic


    // Armed whenever an account is added — both the create-a-new-wallet flow
    // (`commitCreatedWallet`) and the import flow (`ImportWalletScreen`'s `onImported`)
    // — so the main app shows the Welcome Guide automatically. The guide always appears on
    // create/import regardless of the "show setup guides" setting (that toggle only gates the
    // replayable guide entries in Settings). Also re-armed by the guide's own language step to
    // restart it in the newly-picked language. Transient/in-memory only — the first composable to
    // notice it is expected to call `consumePendingWelcomeGuide()` right after presenting the
    // guide, so this is a one-shot signal, not a persisted flag.
    private val _pendingWelcomeGuide = MutableStateFlow(false)
    val pendingWelcomeGuide: StateFlow<Boolean> = _pendingWelcomeGuide

    fun markPendingWelcomeGuide() {
        _pendingWelcomeGuide.value = true
        // Nothing syncs while the wizard is on screen: an import's from-genesis sync of every
        // contact is what made typing through setup crawl. Released by the guide's Finish, or by
        // KaChatApp if no guide ends up being shown.
        onboardingGate.hold()
    }

    /**
     * KaChat Chess has no welcome guide to wait for: the shell takes the signal the moment it is
     * up and lifts the hold, or nothing would sync (iOS ChessMainTabView's onAppear).
     */
    fun consumePendingWelcomeGuide() {
        _pendingWelcomeGuide.value = false
        if (onboardingGate.isHeld) onboardingGate.release()
    }

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address

    private val _accountName = MutableStateFlow<String?>(null)
    val accountName: StateFlow<String?> = _accountName

    private val _accounts = MutableStateFlow(walletManager.getAllAccounts())
    val accounts: StateFlow<List<WalletManager.Account>> = _accounts

    val balance: StateFlow<String> = walletService.balance.map { 
        val kAs = it.toDouble() / 100_000_000.0
        "%.2f %s".format(kAs, KaspaUnit.symbol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "0.00 ${KaspaUnit.symbol}")

    val fullBalance: StateFlow<String> = walletService.balance.map {
        val kAs = it.toDouble() / 100_000_000.0
        "%.8f %s".format(java.util.Locale.US, kAs, KaspaUnit.symbol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "0.00000000 ${KaspaUnit.symbol}")

    val balanceSompi: StateFlow<Long> = walletService.balance
    /** The chatting balance has been read since login: until then it is unknown, not zero. */
    val balanceKnown: StateFlow<Boolean> = walletService.balanceKnown

    // --- Spending address (separate from the identity address above) --------------------
    private val _spendingAddress = MutableStateFlow<String?>(null)
    val spendingAddress: StateFlow<String?> = _spendingAddress

    /** Everything this account holds (iOS 6333dd3 `accountTotalSompi`, Profile's Manage Addresses
     *  card): the chatting address and every revealed spending address, hidden ones too. Null
     *  until the spending total is known. */
    /** Every revealed spending address together; null until it's known (the Wallet tab's Spending). */
    val spendingTotalSompi: StateFlow<Long?> get() = walletService.spendingTotalBalance

    val accountTotalSompi: StateFlow<Long?> =
        combine(walletService.balance, walletService.spendingTotalBalance) { chatting, spending ->
            spending?.let { chatting + it }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Re-derives the current spending address and refreshes its balance — safe to call anytime the Profile screen appears, since the underlying index only ever changes via a successful send. */
    /** For which account and when (unix ms) Profile's appear last ran its balance requests - see
     *  [claimProfileAppearRefresh]. */
    private var profileAppearRefreshed: Pair<String?, Long>? = null

    /** True (and stamped) when Profile's appear may run its balance requests again: at most every
     *  30 s for the same account, as tab switches otherwise started them each time (iOS 9de981a
     *  `appearRefreshedAt`, which lives in a view rebuilt for another wallet). */
    fun claimProfileAppearRefresh(): Boolean {
        val now = System.currentTimeMillis()
        val account = walletManager.activeAddressFlow.value
        val last = profileAppearRefreshed
        if (last != null && last.first == account && now - last.second < 30_000L) return false
        profileAppearRefreshed = account to now
        return true
    }

    fun refreshSpendingAddress() {
        _spendingAddress.value = try { walletManager.currentSpendingAddress() } catch (e: Exception) { null }
        viewModelScope.launch { walletService.refreshSpendingBalance() }
    }

    /** Suspend variant of [refreshSpendingAddress]'s balance refresh - awaits the actual fetch
     *  instead of firing it into [viewModelScope], for callers (the Profile screen's pull-to-
     *  refresh) that need to know when it's actually done before dismissing a refresh spinner. */
    suspend fun refreshSpendingBalanceAndAwait() {
        walletService.refreshSpendingBalance()
    }

    // -------------------------------------------------------------------------
    // Manage Addresses screen — every spending-chain address derived so far, so the user can
    // find/copy an old one that might still hold a stray balance.
    // -------------------------------------------------------------------------

    // Raw rows as loaded/edited. NEVER exposed directly: their isCurrent (and a hidden flag that
    // wrongly caught the primary) can be stale — built from a pre-rotation read, painted from a
    // persisted snapshot, or overwritten by an in-flight load that started before a send rotated
    // the primary. The public [manageAddresses] below re-derives those flags on every emission.
    private val _manageAddressesRaw = MutableStateFlow<List<WalletService.SpendingAddressEntry>>(emptyList())

    /**
     * THE isCurrent SINGLE-SOURCE RULE: the flag is DERIVED, never stored. Rendered rows stamp
     * isCurrent purely from [WalletManager.primarySpendingIndexFlow] (the authoritative live
     * index) at combine time — so no persisted or captured isCurrent is ever trusted. Any row
     * may be hidden, the current one too (iOS 245a5d2).
     */
    val manageAddresses: StateFlow<List<WalletService.SpendingAddressEntry>> =
        combine(_manageAddressesRaw, walletManager.primarySpendingIndexFlow) { rows, primary ->
            rows.map { entry ->
                val isCurrent = primary != null && entry.index == primary
                if (entry.isCurrent == isCurrent) entry else entry.copy(isCurrent = isCurrent)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _manageAddressesLoading = MutableStateFlow(false)
    val manageAddressesLoading: StateFlow<Boolean> = _manageAddressesLoading.asStateFlow()

    // False once a load could not read the balances: the rows are then the last known figures,
    // not today's (iOS da59726 `readSpendingAddressList`'s `balancesRead`).
    private val _manageAddressesBalancesRead = MutableStateFlow(true)
    val manageAddressesBalancesRead: StateFlow<Boolean> = _manageAddressesBalancesRead.asStateFlow()

    /** The last read of the balances failed (iOS da59726 `balancesUnread`): what's on the rows is
     *  the last known figure, and with none known a row and the total show a dash rather than 0. */
    private val _manageAddressesBalancesUnread = MutableStateFlow(false)
    val manageAddressesBalancesUnread: StateFlow<Boolean> = _manageAddressesBalancesUnread.asStateFlow()

    /** Quantum-safe (KIP-22) rows whose key is on chain (they spent once), lowercased: the shield
     *  is off for good. Seeded from the local marks, confirmed per row by the on-chain probe
     *  (iOS ManageAddressesView.quantumSpentAddresses). */
    private val _quantumSpentAddresses = MutableStateFlow<Set<String>>(emptySet())
    val quantumSpentAddresses: StateFlow<Set<String>> = _quantumSpentAddresses.asStateFlow()

    /** The running per-row shield probe; a newer load replaces it. */
    private var quantumShieldJob: kotlinx.coroutines.Job? = null

    /**
     * Quantum shields for [rows] (iOS loadEntries): local spend marks at once, then the chain's
     * word per row, one at a time, for spends made on another device or before a restore. A
     * spent mark is permanent - a revealed key cannot be unrevealed.
     */
    private fun refreshQuantumShields(rows: List<WalletService.SpendingAddressEntry>) {
        val quantum = rows.filter { it.isQuantum }
        _quantumSpentAddresses.value = quantum.map { it.address.lowercase() }
            .filter { walletManager.isQuantumAddressSpentLocally(it) }.toSet()
        val candidates = quantum.filter { it.address.lowercase() !in _quantumSpentAddresses.value }
        quantumShieldJob?.cancel()
        if (candidates.isEmpty()) return
        quantumShieldJob = viewModelScope.launch {
            for (entry in candidates) {
                if (walletService.quantumAddressSpentState(entry.address) == true) {
                    _quantumSpentAddresses.value = _quantumSpentAddresses.value + entry.address.lowercase()
                }
            }
        }
    }

    // Monotonic ticket for loadManageAddresses commits: only the NEWEST in-flight live load may
    // write its result, so a slow load that started before a rotation/newer refresh can never
    // overwrite fresher rows with pre-rotation balances.
    private var manageAddressesLoadGeneration = 0

    init {
        // Current-index freshness: a send from the current spending address (Send Kaspa with that
        // address as its From, withdrawFromSpendingAddress) moves the index onto its fresh change
        // address. This collector refreshes the displayed spending address, moves the old row's
        // balance to the new one optimistically, and kicks a reconciling reload.
        viewModelScope.launch {
            var lastPrimary: Int? = null
            walletManager.primarySpendingIndexFlow.collect { primary ->
                _spendingAddress.value = try { walletManager.currentSpendingAddress() } catch (e: Exception) { null }
                val previous = lastPrimary
                lastPrimary = primary
                if (primary == null || previous == null || previous == primary) return@collect
                val current = _manageAddressesRaw.value
                if (current.isEmpty()) return@collect
                // The send's change moved the old row's balance to the new index.
                val sweptFromOldPrimary = current.firstOrNull { it.index == previous }?.balanceSompi ?: 0L
                _manageAddressesRaw.value = current.map { entry ->
                    when (entry.index) {
                        primary -> entry.copy(hidden = false, balanceSompi = entry.balanceSompi + sweptFromOldPrimary)
                        previous -> entry.copy(balanceSompi = 0L)
                        else -> entry
                    }
                }
                // Background reconcile with real on-chain state (also adds the new primary row
                // when it was a freshly derived index the list has never shown).
                loadManageAddresses()
            }
        }
    }

    fun loadManageAddresses() {
        viewModelScope.launch { loadManageAddressesInternal() }
    }

    /**
     * Awaited variant for the Manage Addresses pull-to-refresh: the screen keeps its refresh
     * spinner up exactly as long as THIS call runs and dismisses it when the call returns —
     * never by watching [manageAddressesLoading], which background reloads (the rotation
     * collector, Generate, Activate, Withdraw) also drive and which never even flips on a warm
     * refresh (it only signals the empty-list initial load). The load itself still runs in
     * [viewModelScope], so backing out of the screen mid-refresh cancels only the wait, not the
     * load. Returning is always safe even when a newer load superseded this one and the
     * generation fence dropped its commit: the fresher rows are already on their way.
     */
    suspend fun loadManageAddressesAndAwait() {
        viewModelScope.launch { loadManageAddressesInternal() }.join()
    }

    private suspend fun loadManageAddressesInternal() {
        val generation = ++manageAddressesLoadGeneration
        try {
            // Instant paint from the persisted snapshot of the last full load (balances may be
            // a refresh stale — the live load below replaces them). Only seeds when the screen
            // has nothing yet, so a live list never regresses to older data.
            if (_manageAddressesRaw.value.isEmpty()) {
                val cached = try { walletService.cachedSpendingAddressList() } catch (e: Exception) { emptyList() }
                if (cached.isNotEmpty()) _manageAddressesRaw.value = cached
            }
            _manageAddressesLoading.value = _manageAddressesRaw.value.isEmpty()
            // The rows paint as soon as the balances are in and each badge follows its own probe
            // (iOS 1751d20 loadEntries), not once the whole sweep is done.
            var painted = false
            val live = try {
                walletService.getSpendingAddressList { rows ->
                    // Stale-load fence, as below.
                    if (generation != manageAddressesLoadGeneration || rows.isEmpty()) return@getSpendingAddressList
                    if (painted) {
                        // A probe landed: only that answer is folded in, so a hide, rename or
                        // Generate made meanwhile stays as it is.
                        val byAddress = rows.associateBy { it.address }
                        _manageAddressesRaw.value = _manageAddressesRaw.value.map { current ->
                            byAddress[current.address]?.let { current.copy(everUsed = it.everUsed, liveChecked = it.liveChecked) } ?: current
                        }
                    } else {
                        _manageAddressesRaw.value = rows
                        _manageAddressesLoading.value = false
                        painted = true
                        refreshQuantumShields(rows)
                        // "Contains domain" tags: batched cached KNS lookups once the rows are visible.
                        refreshDomainOwningAddresses(rows.map { it.address })
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { emptyList() }
            // An empty live list means the balances could not be read (see getSpendingAddressList).
            if (generation == manageAddressesLoadGeneration) _manageAddressesBalancesRead.value = painted || live.isNotEmpty()
            // Stale-load fence: a newer load (or the post-rotation reconcile) superseded this one
            // while its network round trip ran — its rows are pre-rotation, drop them. Rows
            // already painted above have had every probe folded in: nothing left to commit.
            if (!painted && generation == manageAddressesLoadGeneration &&
                (live.isNotEmpty() || _manageAddressesRaw.value.isEmpty())
            ) {
                _manageAddressesRaw.value = live
            }
            // The rows are read before the balances, and index 0 always exists: an empty answer is
            // balances that couldn't be read, which is unknown, never zero (iOS da59726).
            if (generation == manageAddressesLoadGeneration) {
                _manageAddressesBalancesUnread.value = !painted && live.isEmpty()
                if (!painted && live.isEmpty() && _manageAddressesRaw.value.isEmpty()) {
                    _manageAddressesRaw.value = withContext(Dispatchers.Default) { walletService.spendingAddressListWithoutBalances() }
                }
            }
            if (!painted) {
                refreshQuantumShields(_manageAddressesRaw.value)
                // "Contains domain" tags: batched cached KNS lookups after the rows are visible.
                refreshDomainOwningAddresses(_manageAddressesRaw.value.map { it.address })
            }
        } finally {
            // try/finally so no exit — success, a throw, or cancellation — can strand the
            // initial-load spinner.
            _manageAddressesLoading.value = false
        }
    }

    /**
     * Hiding is purely a display preference — the address and its label are untouched; it's just
     * filtered out of the main Manage Addresses list. Any address can be hidden or shown, funded
     * ones and the current one too (iOS 245a5d2): Address Visibility brings any back, and
     * Profile's Manage Addresses total still counts it. Commits and flips the row instantly - no
     * network. [onResult] reports whether it was persisted.
     */
    fun setManageAddressHidden(index: Int, hidden: Boolean, onResult: (Boolean) -> Unit = {}) {
        val account = walletManager.getActiveAccount()
        if (account == null) {
            onResult(false)
            return
        }
        walletManager.setSpendingAddressHidden(account.address, index, hidden)
        _manageAddressesRaw.value = _manageAddressesRaw.value.map {
            if (it.index == index) it.copy(hidden = hidden) else it
        }
        onResult(true)
    }

    /** Address Visibility > Hide All (iOS 245a5d2): every revealed spending address hidden, so
     *  Manage Addresses starts over with only the chatting address. */
    fun hideAllSpendingAddresses() {
        walletService.hideAllSpendingAddresses()
        _manageAddressesRaw.value = _manageAddressesRaw.value.map { it.copy(hidden = true) }
    }

    /** A From address's balance (iOS `SendFromSection`: the sum of the address's UTXOs as the node
     *  sees it), the REST gateway's coins when no node answers; 0 when neither can. */
    suspend fun addressBalanceSompi(address: String): Long =
        walletEngine.nodeBalance(address)
            ?: runCatching { com.kachat.app.util.UtxoMath.checkedTotal(walletEngine.fetchUtxosOrThrow(address, spendableOnly = false).map { it.utxoEntry.amount }) }.getOrNull()
            ?: 0L

    /** Sets or clears (blank/null) a nickname for one spending-chain address, shown in place of "Address #N". */
    fun setManageAddressLabel(index: Int, label: String?) {
        walletService.setSpendingAddressLabel(index, label)
        _manageAddressesRaw.value = _manageAddressesRaw.value.map {
            if (it.index == index) it.copy(label = label?.trim()?.takeIf { l -> l.isNotBlank() }) else it
        }
    }

    /**
     * The address to show on the Receive QR: one that has never been used, on the quantum-safe
     * chain (KIP-22).
     *
     * Reusing an address across payments links them to each other and to you, so the QR must
     * never hand out one that has already appeared on chain. Two rules, in order:
     *
     * 1. The address it handed out last time, while that is still unused - and quantum. It does
     *    NOT mint a new address per tap - an unused address is still unused the second time you
     *    open it, and minting one per tap would balloon the address list and lengthen every
     *    future gap-limit scan for no privacy gained. A legacy pointer, even unused, carries its
     *    key in the address itself (P2PK), so the QR moves to the quantum chain the first time it
     *    is opened after the upgrade.
     * 2. Otherwise the lowest quantum slot never used: the lowest hidden, live-confirmed unused
     *    one (recycling it), else a brand-new slot past the quantum chain's end - remembered as
     *    the receive pointer for next time.
     *
     * Picking has no side effects (iOS 24c2094): nothing is un-hidden or revealed, and the chain
     * is not extended. With [revealing] - the Receive QR is actually being shown - the address goes
     * on the Manage Addresses list, the chain extended to it if needed so its payment is watched
     * ([revealSpendingAddress]). Without it (Profile or the New sheet warming the QR ahead of the
     * tap) nothing about the list changes: an address appearing after Hide All with nobody having
     * done anything was exactly that.
     *
     * The primary is never changed. Receiving and spending are separate jobs: which address a
     * payment comes out of is not the QR's business.
     *
     * [onResult] receives the address to draw, or null when nothing can be derived. A live check
     * that FAILS keeps the current answer rather than rotating on a guess: rotating whenever the
     * network hiccups is how an address list fills with empty slots.
     *
     * Matches iOS's WalletManager.freshReceiveAddress(revealing:).
     */
    fun resolveFreshReceiveAddress(revealing: Boolean = false, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val walletAddress = try { walletManager.getAddress() } catch (e: Exception) { null }
            val current = try { walletManager.currentSpendingAddress() } catch (e: Exception) { null }
            val account = walletManager.getActiveAccount()
            if (walletAddress == null || current == null || account == null) {
                onResult(null)
                return@launch
            }
            // The QR is for the account active now: an answer that comes back after a switch
            // belongs to the other account and is dropped, nothing written (iOS fb2323e, IOS-109
            // / AND-056 follow-up).
            fun switched() = walletManager.getActiveAccount()?.address != walletAddress
            val entries = try { walletService.getSpendingAddressList() } catch (e: Exception) { emptyList() }
            // Asked of the network each time, not this session's memory of "unused": the app gets
            // no event when a spending address is paid. An address that has spent is out whatever
            // it says (iOS da59726). Unknown (a failed lookup) counts as "not used" here, so a
            // network wobble never rotates the pointer.
            suspend fun confirmedUsed(address: String): Boolean =
                walletManager.isQuantumAddressSpentLocally(address) ||
                    walletService.spendingAddressUsedState(address, ignoringSessionCache = true) == true

            val storedIndex = walletManager.getReceiveAddressIndex(walletAddress)
            val stored = storedIndex?.let { try { walletManager.deriveSpendingAddress(it) } catch (e: Exception) { null } }
            val chosen: Pair<Int, String> = if (storedIndex != null && stored != null &&
                WalletManager.isQuantumIndex(storedIndex) && !confirmedUsed(stored)
            ) {
                // Rule 1: the pointer from last time, while it is still untouched - and quantum.
                storedIndex to stored
            } else {
                // Rule 2: the lowest quantum slot never used (iOS lowestUnusedQuantumAddress,
                // side-effect free): a hidden, live-confirmed unused quantum row, else a brand-new
                // slot past the chain's end - answered without extending the chain, and checked on
                // chain first (iOS da2c8e2 `verifiedFreshQuantumIndex`, IOS-101 / AND-049). Never
                // a slot a send still in flight picked for its change (Android's own guarantee).
                val inFlight = walletManager.inFlightQuantumChange()
                var recycled: Int? = null
                for (entry in entries.sortedBy { it.index }) {
                    if (!entry.isQuantum || !entry.hidden || !entry.liveChecked || entry.isCurrent ||
                        entry.index == account.spendingAddressIndex ||
                        entry.balanceSompi != 0L || entry.everUsed || entry.index in inFlight
                    ) continue
                    // Spent, or paid since this session looked: not unused (iOS da59726).
                    if (walletManager.isQuantumAddressSpentLocally(entry.address)) continue
                    if (walletService.spendingAddressUsedState(entry.address, ignoringSessionCache = true) == false) {
                        recycled = entry.index
                        break
                    }
                }
                val index = recycled
                    ?: walletManager.verifyFreshQuantumIndex(owner = walletAddress) { walletService.spendingAddressUsedState(it) }
                if (switched()) {
                    onResult(null)
                    return@launch
                }
                val address = try { walletManager.deriveSpendingAddress(index) } catch (e: Exception) { null }
                if (address == null) {
                    onResult(current)
                    return@launch
                }
                // Only an address really shown is remembered. A warm-up's answer sits past the
                // chain's end, where the next send's change or Request's Fresh Address would land
                // on it too (iOS da59726).
                if (revealing) walletManager.setReceiveAddressIndex(walletAddress, index)
                index to address
            }
            if (switched()) {
                onResult(null)
                return@launch
            }
            // Shown now: it goes on the Manage Addresses list (and is watched for payments) - of
            // the account the QR was resolved for (iOS ca09dd0, IOS-109 / AND-056).
            if (revealing) revealSpendingAddress(chosen.first, owner = walletAddress)
            onResult(chosen.second)
        }
    }

    /**
     * iOS parity (lowestUnusedSpendingAddress): Generate recycles the LOWEST truly-unused
     * index — skipping the primary and anything holding a balance or with on-chain history —
     * unhiding it so it shows on the main list. Falls back to deriving maxIndex+1 when every existing index is
     * spoken for. [onResult] receives the index that is now ready, or null when there is no
     * account (or, for the legacy chain, the live check failed: never recycle blind). The quantum
     * Generate never refuses (iOS 1751d20 generateNew): with no live list to recycle from it
     * extends the quantum chain, which is fresh by construction.
     *
     * [quantum]: Manage Addresses' Generate makes quantum-safe (KIP-22) slots from now on (iOS
     * ManageAddressesView.generateNew): recycling considers only hidden unused QUANTUM rows, and
     * the fresh path extends the quantum chain - the legacy chain is never grown again from
     * there. Swap's picker keeps the legacy chain, as iOS SwapView's generateNextSpendingAddress.
     */
    fun generateNewSpendingAddress(quantum: Boolean = true, onResult: (Int?) -> Unit = {}) {
        viewModelScope.launch {
            val walletAddress = try { walletManager.getAddress() } catch (e: Exception) { null }
            if (walletAddress == null) {
                onResult(null)
                return@launch
            }
            // LIVE list only, never the cached rows in _manageAddressesRaw: the instant-paint
            // snapshot carries stale balance/used/isCurrent flags (the primary rotates after
            // every send), which is exactly how a used, funded, or even the primary index could
            // be re-offered as "fresh".
            val primaryIndex = walletManager.getActiveAccount()?.spendingAddressIndex
            val entries = try { walletService.getSpendingAddressList() } catch (e: Exception) { emptyList() }
            // A real wallet always lists at least index 0, so an empty live list means the check
            // FAILED (offline, unreachable or throttled API), never a wallet with no addresses.
            // Quantum Generate then skips recycling and extends the quantum chain, as iOS 1751d20
            // does when no recyclable row is known; the legacy chain still refuses.
            if (entries.isEmpty() && !quantum) {
                onResult(null)
                return@launch
            }
            // Generate is a SEQUENCE: each press must land a NEW row on the list, forever. So a
            // recycle pick is the lowest truly-unused index that is still HIDDEN — un-hiding it
            // adds a row the user can see. A visible unused row is already on screen; re-picking
            // it made press two a silent no-op (the stall this replaces). Only rows whose balance
            // and used-ness were both live-confirmed this load are recyclable; unconfirmed rows
            // are skipped rather than trusted. A slot a send still in flight picked for its change
            // is taken, never recycled (Android's own guarantee; iOS tracks no in-flight picks).
            val inFlight = walletManager.inFlightQuantumChange()
            val pick = entries.sortedBy { it.index }.firstOrNull { entry ->
                entry.isQuantum == quantum &&
                    entry.hidden && entry.liveChecked && entry.index != primaryIndex && !entry.isCurrent &&
                    entry.balanceSompi == 0L && !entry.everUsed && entry.index !in inFlight
            }
            val readyIndex = if (pick != null) {
                walletService.setSpendingAddressHidden(pick.index, false)
                pick.index
            } else if (quantum) {
                // A brand-new quantum slot, un-hidden: always extends the chain (iOS
                // generateNextQuantumAddress) - never revealed, funded, or offered, and checked
                // on chain first (iOS 2343e84, IOS-101): a slot already used is skipped. Probed
                // and revealed on the account this press started for (IOS-109 / AND-056).
                try {
                    walletService.generateNextQuantumAddress(owner = walletAddress)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No account to extend (removed mid-press): nothing was revealed.
                    onResult(null)
                    return@launch
                }
            } else {
                // Every listed index is spoken for (or unconfirmed): extend the chain. A brand-new
                // index past the all-time max has never been revealed, funded, or offered, so it
                // is always safe to hand out — and this branch only runs off a LOADED live list.
                val newIndex = walletService.generateNextSpendingAddress()
                // iOS parity (lowestUnusedSpendingAddress's tail): explicitly clear any hidden
                // flag on the new slot so it can never arrive pre-hidden. Instant, no network.
                walletManager.setSpendingAddressHidden(walletAddress, newIndex, false)
                newIndex
            }
            // iOS parity (ManageAddressesView.generateNew awaits its reload before toasting): the
            // ready row must be ON SCREEN when the toast names it. We just fetched the live list,
            // so commit it now with the ready row unhidden — recycled picks flip visible in place,
            // a newly derived index is appended as a fresh row (its address derives locally) — and
            // let the background reload only reconcile flags afterwards.
            // With no live list (the check failed), the rows already on screen take the new one.
            val fresh = entries.ifEmpty { _manageAddressesRaw.value }
                .map { if (it.index == readyIndex) it.copy(hidden = false) else it }
            _manageAddressesRaw.value = if (fresh.any { it.index == readyIndex }) fresh else {
                fresh + com.kachat.app.services.WalletService.SpendingAddressEntry(
                    index = readyIndex,
                    address = spendingAddressAt(readyIndex) ?: "",
                    balanceSompi = 0L,
                    everUsed = false,
                    isCurrent = false,
                    hidden = false,
                    label = null,
                    // Past the all-time max index, so provably never revealed or funded
                    // — unused by construction, no network needed. Marking it
                    // live-confirmed lets the row show "Unused" (not the neutral unverified
                    // state) and hide instantly; the reload below still reconciles.
                    liveChecked = true
                )
            }
            loadManageAddresses()
            onResult(readyIndex)
        }
    }

    /**
     * Which revealed spending-chain slot [address] is, as its row with a live balance - for the
     * History sheet a tapped "Received" notification opens (iOS 13046ad,
     * `OwnAddressHistorySheet.resolve`). Null when it isn't one of this wallet's spending
     * addresses. The list is reloaded first so the header shows the Kaspa that just arrived,
     * not a snapshot's balance; if that load fails the row still opens, from the snapshot or
     * bare.
     */
    suspend fun resolveOwnSpendingAddress(address: String): WalletService.SpendingAddressEntry? {
        // Both chains (iOS OwnAddressHistorySheet.resolve): the legacy range and the quantum one.
        val account = walletManager.getActiveAccount() ?: return null
        val base = WalletManager.QUANTUM_INDEX_BASE
        val indices = (0..maxOf(0, WalletManager.legacyMaxSpendingIndex(account))) +
            (base until base + WalletManager.maxQuantumIndex(account) + 1)
        val all = withContext(Dispatchers.IO) { walletManager.deriveSpendingAddresses(indices) }
        val (index, ownAddress) = all.entries.firstOrNull { it.value.equals(address, ignoreCase = true) }
            ?.let { it.key to it.value } ?: return null
        loadManageAddressesAndAwait()
        val isCurrent = walletManager.primarySpendingIndexFlow.value == index
        return _manageAddressesRaw.value.firstOrNull { it.index == index }?.copy(isCurrent = isCurrent)
            ?: WalletService.SpendingAddressEntry(
                index = index,
                address = ownAddress,
                balanceSompi = 0L,
                everUsed = true,
                isCurrent = isCurrent,
                liveChecked = false,
            )
    }

    /**
     * Request Payment's Fresh Address (iOS `reserveFreshSpendingAddresses(count: 1)`, 86dfd2d /
     * 6dee531): a quantum-safe (KIP-22) address never revealed, used or handed out, reserved now -
     * the quantum bound moves past it in the same step ([WalletManager.allocateFreshQuantumIndices],
     * shared with payment change), so no later request or change output can ever get the same
     * one. Null when it can't be derived. The address-activity poll watches every revealed
     * address, so its payment shows the moment it lands. The slot is checked on chain first: one
     * that already has history is skipped (iOS da2c8e2 `verifiedFreshQuantumIndex`, IOS-101 /
     * AND-049). Probe, address and the raised bound all belong to the account active when this
     * starts; nothing is reserved if it was removed meanwhile (iOS fb2323e, IOS-109 / AND-056
     * follow-up).
     */
    suspend fun reserveFreshSpendingAddress(): String? = withContext(Dispatchers.IO) {
        try {
            val account = walletManager.spendingSweepAccount() ?: return@withContext null
            walletManager.verifyFreshQuantumIndex(account) { walletService.spendingAddressUsedState(it) }
            walletManager.allocateFreshQuantumIndices(1, account).firstOrNull()?.second
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The address [reserveFreshSpendingAddress] would hand out next, derived but NOT reserved
     *  (iOS 3f5f79c) - see [WalletManager.nextFreshSpendingAddress]. Checked on chain first (iOS
     *  2343e84 `verifiedFreshQuantumIndex`, IOS-101), so the preview never shows a slot already
     *  used: a used one is skipped (the chain's end raised past it), as the reservation does.
     *  Slot and address from the same account, the one active when this starts (iOS fb2323e
     *  `verifiedFreshQuantumSlot()?.address`, IOS-109 / AND-056 follow-up). */
    suspend fun nextFreshSpendingAddress(): String? = withContext(Dispatchers.IO) {
        val account = walletManager.spendingSweepAccount() ?: return@withContext null
        try {
            walletManager.verifyFreshQuantumIndex(account) { walletService.spendingAddressUsedState(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // the check is best effort, as verifyFreshQuantumIndex itself treats an unanswered one
        }
        walletManager.nextFreshSpendingAddress(account)
    }

    /** See [WalletService.cachedSpendingAddressUsedState]. */
    fun cachedSpendingAddressUsedState(address: String): Boolean? = walletService.cachedSpendingAddressUsedState(address)

    /** See [WalletService.spendingAddressUsedState]. */
    suspend fun spendingAddressUsedState(address: String): Boolean? = walletService.spendingAddressUsedState(address)

    /** Address at [index] on the spending chain, derived on demand — Address Visibility pager
     *  rows beyond the revealed bound. */
    fun spendingAddressAt(index: Int): String? =
        try { walletManager.deriveSpendingAddress(index) } catch (e: Exception) { null }

    /** Addresses for a whole range, paying the BIP-39 seed derivation once rather than once per
     *  index - see [WalletManager.deriveSpendingAddresses]. Call it off the main thread. */
    fun spendingAddressesAt(indices: Iterable<Int>): Map<Int, String> =
        try { walletManager.deriveSpendingAddresses(indices) } catch (e: Exception) { emptyMap() }

    /**
     * iOS parity (WalletManager.revealSpendingAddress): raises the revealed bound to [index],
     * keeping the intermediate indices hidden so revealing a far-out address doesn't flood the
     * main Manage Addresses list - on its own chain (the quantum chain has its own bound).
     *
     * [owner]: the account a caller started on before an await (iOS ca09dd0 `owner`, IOS-109 /
     * AND-056) - its list gets the address even if another account is active by now, and the
     * active account's Manage Addresses is then left alone; nothing is written once [owner] has
     * been removed. Null = the active account.
     */
    fun revealSpendingAddress(index: Int, owner: String? = null) {
        val active = walletManager.getActiveAccount()?.address
        val target = owner ?: active ?: return
        if (!walletManager.revealSpendingAddress(target, index)) return
        if (target == active) loadManageAddresses()
    }

    /** Used-check for Address Visibility rows derived beyond the loaded list. */
    suspend fun hasSpendingAddressBeenUsed(address: String): Boolean =
        walletService.hasSpendingAddressBeenUsed(address)

    /**
     * Sends KAS out of one specific spending-chain address (not necessarily the currently
     * active one) to [toAddress] — unlike [WalletService.sendKaspa]/`onSendClicked` (identity)
     * or a chat payment from a spending address (KaspaWalletEngine.sendSpendingPayment), this targets a single address by
     * [index]. Its progress and outcome are [sends]' under [sendId], the send screen's own.
     *
     * For a NON-primary legacy index the change stays on the same address: that is a scoped,
     * explicit single-address operation, and the row's own balance is what the user is managing.
     * The primary is different. A send from it puts its change on a fresh index and rotates the
     * primary to that index - exactly what a chat payment does - so the address you spend from
     * and hand out next has never been used before. This path used to leave the primary sitting
     * on the address it had just spent from. The old primary keeps whatever it still holds,
     * listed in Manage Addresses like any other revealed slot. A quantum-safe (KIP-22) source
     * always rotates the same way: the spend reveals its key, and change back to it would park
     * funds on a revealed address. The fresh change slot is quantum either way.
     */
    fun withdrawFromSpendingAddress(
        sendId: String,
        index: Int,
        toAddress: String,
        amountSompi: Long,
        feeRateOverride: Long? = null,
        manualUtxos: List<UtxoEntry>? = null,
        /** Fast / Priority / custom fee as a flat extra over the base fee, paid exactly - see
         *  [com.kachat.app.util.SendFeeModel]. */
        extraFeeSompi: Long = 0L,
        /** The fee the Review showed: a send that would now pay more stops (iOS 4922804). */
        expectedFeeSompi: Long? = null,
    ) {
        sends.start(sendId)
        viewModelScope.launch {
            // The account the source is derived from: the fresh change slot is probed and picked
            // there too (iOS fb2323e, IOS-109 / AND-056 follow-up).
            val owner = walletManager.getActiveAccount()?.address
            val fromAddress = walletManager.deriveSpendingAddress(index)
            // With its source, before anything waits: a switch during a wait must not sign with
            // the other account's key at this index (iOS da59726).
            val signingKey = walletManager.getSpendingPrivateKeyBytes(index)
            // A "Not confirmed" send to this recipient is settled first (iOS da59726).
            walletEngine.unresolvedSendRefusal(toAddress)?.let { refusal ->
                sends.finish(sendId, Result.failure(refusal))
                return@launch
            }
            // Fresh change index for the primary only: past the all-time max, so it has never
            // been revealed, funded or offered (the rule KaspaWalletEngine.sendSpendingPayment
            // uses). Decided up front and applied only after the node accepts the transaction,
            // so a failed send moves nothing. If the fresh address cannot be derived, change
            // stays put and the primary stays put with it - rotating onto an address the change
            // did not reach would strand the funds behind a pointer. A self-send (the Compound
            // action) moves nothing out of the address, so it neither splits its consolidation
            // onto a fresh address nor rotates.
            val isSelfSend = toAddress.equals(fromAddress, ignoreCase = true)
            val activeIndex = walletManager.getActiveAccount()?.spendingAddressIndex
            val isQuantumSource = WalletManager.isQuantumIndex(index)
            // A quantum-safe source spends with its redeem script pushed after the signature.
            val redeemScript = if (isQuantumSource) {
                try { walletManager.quantumRedeemScript(index) } catch (e: Exception) { null }
            } else null
            if (isQuantumSource && redeemScript == null) {
                sends.finish(sendId, Result.failure(IllegalStateException("Could not derive the quantum address's key")))
                return@launch
            }
            // A self-send (Compound) still consolidates into the address itself, shield off.
            // Picked, not revealed: held as in flight so nothing else gets the same slot, and
            // given back if the send fails ([WalletManager.pickFreshQuantumChange]). Checked on
            // chain first: a slot that already has history is skipped (iOS da2c8e2
            // `verifiedFreshQuantumIndex`, IOS-101 / AND-049). Slot and address from the source's
            // account, whichever is active once the probe returns (iOS fb2323e
            // `verifiedFreshQuantumSlot`, IOS-109 / AND-056 follow-up).
            val fresh = if (owner != null && !isSelfSend && (isQuantumSource || index == activeIndex)) {
                walletManager.verifiedFreshQuantumChange(owner = owner) { walletService.spendingAddressUsedState(it) }
            } else null
            // Never change back onto the key this spend reveals: a quantum source whose fresh
            // slot can't be derived stops here (iOS da2c8e2, IOS-104 / AND-052). A legacy source
            // keeps its fallback - change and primary stay put.
            if (fresh == null && !isSelfSend && isQuantumSource) {
                sends.finish(sendId, Result.failure(IllegalStateException(appContext.getString(com.kachat.app.R.string.quantum_next_address_failed))))
                return@launch
            }
            val result = try {
                walletEngine.sendKaspa(
                    toAddress = toAddress,
                    amountSompi = amountSompi,
                    fromAddress = fromAddress,
                    signingPrivateKey = signingKey,
                    changeAddress = fresh?.address ?: fromAddress,
                    feeRateOverride = feeRateOverride,
                    manualUtxos = manualUtxos,
                    extraFeeSompi = extraFeeSompi,
                    expectedFeeSompi = expectedFeeSompi,
                    senderRedeemScript = redeemScript,
                    holdUnknownOutcome = true,
                    markAddressesUsed = true,
                )
            } catch (e: Throwable) {
                // outcome unknown: revealing a slot is always safe, handing it out again is not
                fresh?.let { walletManager.finishQuantumChangePick(it, reveal = true) }
                throw e
            }
            // Every write below goes to the account this send started on ([owner], the pick's
            // own), even if another is active by now; nothing once it was removed (iOS ca09dd0,
            // IOS-109 / AND-056).
            // An unknown outcome may have landed: its change may be on the fresh address, and its
            // key on chain (iOS da59726).
            val mayHaveLanded = result.isSuccess || result.exceptionOrNull() is com.kachat.app.services.SendOutcomeUnknownException
            if (mayHaveLanded && isQuantumSource) {
                // the spend put this address's key on chain for good (self-sends included)
                walletManager.markQuantumAddressSpent(fromAddress, owner = owner)
            }
            if (fresh != null) {
                // listed either way, its slot never handed out again
                walletManager.finishQuantumChangePick(fresh, reveal = mayHaveLanded)
                if (result.isSuccess && index == activeIndex) {
                    // The primary follows its change onto the fresh address, only when it was the
                    // primary that sent (iOS da59726 `wasPrimary`). Manage Addresses reloads below
                    // and moves its star.
                    walletManager.setSpendingAddressIndex(fresh.walletAddress, fresh.index)
                }
            }
            sends.finish(sendId, result)
            // Another account's primary moves without touching what is on screen now (iOS
            // ca09dd0: no notification for another account).
            if (result.isSuccess && walletManager.getActiveAccount()?.address == owner) {
                refreshSpendingAddress()
                loadManageAddresses()
            }
        }
    }

    /** Coin control's data source for a spending address - see `SpendingAddressSendFlow`. Never
     *  [coldStorageAddressDiscovery]/[spendingAddressUtxos] - those return a display-only DTO
     *  with no scriptPublicKey, which signing needs. */
    /** Throws when the coins could not be fetched, so coin control can say so. */
    suspend fun fetchUtxosForCoinControl(address: String): List<UtxoEntry> = walletEngine.fetchUtxosOrThrow(address)

    /** Coin Control's coins: every one in the node's order, with why a send leaves it out (null
     *  when it can spend it) ([KaspaWalletEngine.fetchCoinsForCoinControl], iOS 1751d20). */
    suspend fun fetchCoinsForCoinControl(address: String): List<Pair<UtxoEntry, String?>> =
        walletEngine.fetchCoinsForCoinControl(address)

    /** The live quoted fee rate a send with no override pays - fetched once when the Send screen
     *  opens, to price its base fee. */
    suspend fun fetchQuotedFeeRateSompiPerGram(): Long = walletEngine.fetchQuotedFeeRateSompiPerGram()

    /**
     * What an automatic-selection send of [amountSompi] from [address] spends and pays - see
     * [SendFeeModel.previewAutomaticSelection]. Same coins the send itself fetches
     * ([KaspaWalletEngine.fetchUtxos]: node first, reconciled, immature coinbase dropped). Null
     * when it cannot be worked out (no REST client yet, nothing there, or not enough to cover it).
     */
    suspend fun previewAutomaticSelection(
        address: String,
        amountSompi: Long,
        feeRateSompiPerGram: Long,
        feeMultiplier: Long,
        customExtraFeeSompi: Long?,
        recipientScriptLen: Int = SendFeeModel.STANDARD_SCRIPT_LEN,
        /** Per-input signature-script size of [address] ([WalletManager.signatureScriptSize]). */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** Where the change really goes ([changeScriptLen]); null for back to [address]. */
        changeScriptLen: Int? = null,
    ): SendFeeModel.Preview? {
        if (!walletEngine.isRestApiReady) return null
        val utxos = walletEngine.fetchUtxos(address)
        val changeLen = changeScriptLen ?: ownScriptLen(address)
        return SendFeeModel.previewAutomaticSelection(
            utxos = utxos,
            amountSompi = amountSompi,
            feeRateSompiPerGram = feeRateSompiPerGram,
            feeMultiplier = feeMultiplier,
            customExtraFeeSompi = customExtraFeeSompi,
            recipientScriptLen = recipientScriptLen,
            changeScriptLen = changeLen,
            signatureScriptSize = signatureScriptSize,
        )
    }

    private fun ownScriptLen(address: String): Int =
        runCatching { KaspaAddress.getScriptPublicKey(address).length / 2 }.getOrDefault(SendFeeModel.STANDARD_SCRIPT_LEN)

    /**
     * The script length of the change a send from [fromAddress] to [toAddress] will really make
     * (iOS da59726 `plannedChangeScript`), so the Review and Max price what the send pays: a
     * quantum source, or the primary, puts its change on a fresh quantum-safe slot
     * ([withdrawFromSpendingAddress]), and that script is a byte longer. Everything else (the
     * chatting address, another legacy address, a self-send) keeps change on the source.
     */
    fun changeScriptLen(sourceIndex: Int?, fromAddress: String, toAddress: String): Int {
        val movesToFreshSlot = sourceIndex != null && !toAddress.trim().equals(fromAddress, ignoreCase = true) &&
            (WalletManager.isQuantumIndex(sourceIndex) || sourceIndex == walletManager.getActiveAccount()?.spendingAddressIndex)
        return if (movesToFreshSlot) SendFeeModel.QUANTUM_CHANGE_SCRIPT_LEN else ownScriptLen(fromAddress)
    }

    /**
     * Maximum sendable amount from a specific spending address - thin wrapper over
     * [estimateMaxSendableAmount], resolving the index to an address first.
     */
    suspend fun estimateMaxSpendingAddressAmount(
        index: Int,
        feeRateOverride: Long? = null,
        manualUtxos: List<UtxoEntry>? = null,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
    ): Long? =
        estimateMaxSendableAmount(
            walletManager.deriveSpendingAddress(index), feeRateOverride, manualUtxos, feeMultiplier, customExtraFeeSompi,
            signatureScriptSize = WalletManager.signatureScriptSize(index),
        )

    /**
     * Maximum sendable amount from any address this wallet holds the key for (spending-chain or
     * the identity address) - mirrors [ColdStorageSendEngine.estimateMaxAmount]. With
     * [manualUtxos] set (coin control active), "max" means max spendable from just that selected
     * subset, resolved fresh by outpoint in case it's gone stale - not the whole address's
     * balance. Shared by [SpendingAddressSendFlow]'s Max button regardless of which address it's
     * sending from.
     *
     * Returns null for "cannot work it out yet" - the REST client is created a moment after
     * launch, and before that a UTXO fetch answers with an empty list, which is indistinguishable
     * from an address that holds nothing. Returning 0 for that made Max fill in "0" during the
     * first seconds of a session and read as a button that does not work. 0 now means only what
     * it says: there is a balance, and the fee eats all of it.
     *
     * Leaves room for the Fast / Priority / custom extra ([feeMultiplier], [customExtraFeeSompi])
     * as a flat amount, the extra derived from this same base - see [SendFeeModel.maxAfterFees].
     */
    suspend fun estimateMaxSendableAmount(
        address: String,
        feeRateOverride: Long? = null,
        manualUtxos: List<UtxoEntry>? = null,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        /** Per-input signature-script size of [address]: a quantum-safe source's inputs are
         *  bigger ([WalletManager.signatureScriptSize]). */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
    ): Long? = estimateMaxSend(address, feeRateOverride, manualUtxos, feeMultiplier, customExtraFeeSompi, signatureScriptSize)?.amountSompi

    /** A Max worked out by [estimateMaxSend]: the amount, whether one transaction's input cap held
     *  it under the coins' total (iOS 1751d20 `cappedAtInputLimit`, which the amount step and the
     *  Review explain), and whether there was any coin to spend at all (none: iOS's "No spendable
     *  UTXOs" rather than "Balance too low to cover fee"). */
    data class MaxSendEstimate(val amountSompi: Long, val cappedAtInputLimit: Boolean, val hasCoins: Boolean)

    /** [estimateMaxSendableAmount] with whether the input cap held it back (iOS 1751d20
     *  `estimateMaxWithdrawalAmount`'s `cappedAtInputLimit`). Null: the wallet can't answer yet. */
    suspend fun estimateMaxSend(
        address: String,
        feeRateOverride: Long? = null,
        manualUtxos: List<UtxoEntry>? = null,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** Where the change really goes ([changeScriptLen]); null for back to [address]. */
        changeScriptLen: Int? = null,
    ): MaxSendEstimate? {
        if (!walletEngine.isRestApiReady) return null
        val fetched = walletEngine.fetchUtxos(address)
        if (fetched.isEmpty()) return MaxSendEstimate(0L, cappedAtInputLimit = false, hasCoins = false)

        val utxos = if (!manualUtxos.isNullOrEmpty()) {
            val freshByOutpoint = fetched.associateBy { it.outpoint }
            // Picked coins that are all gone say so, rather than "not enough" (iOS 4922804
            // resolveReviewSelection).
            manualUtxos.mapNotNull { freshByOutpoint[it.outpoint] }
                .ifEmpty { throw IllegalStateException("Selected UTXOs are no longer available - please reselect") }
        } else {
            fetched
        }
        if (utxos.isEmpty()) return MaxSendEstimate(0L, cappedAtInputLimit = false, hasCoins = false)

        // Largest-first, capped at what one transaction can actually hold. Kaspa caps a
        // transaction's mass, and pricing EVERY UTXO at the address as an input - which this used
        // to do - breaks down exactly where an address has collected a lot of small ones. The
        // chatting address is the one that does: every message send leaves its change there, so a
        // chatty account ends up with dozens of tiny UTXOs, the modelled fee grows with each one,
        // and past a point `totalBalance <= fee` returned 0. Max then filled in "0" and looked
        // like a dead button. A spending address never showed it, because its change is routed to
        // a fresh address every spend, so it holds one or two.
        //
        // The cap also makes the answer honest: a "max" priced over more inputs than a
        // transaction can carry is not sendable. `maxConsolidatableChunk` already reasons this
        // way; this is the same rule for an ordinary send, and the send's own greedy selection
        // (largest-first, stops once covered) picks the same inputs back.
        val capped = SendFeeModel.largestSpendable(utxos)

        // Node-supplied amounts that are not real values give no Max, not a wrong one (IOS-020).
        val totalBalance = UtxoMath.checkedTotal(capped.map { it.utxoEntry.amount }) ?: return null
        val feeRateSompiPerGram = feeRateOverride?.coerceAtLeast(KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM)
            ?: walletEngine.fetchQuotedFeeRateSompiPerGram()

        val amount = SendFeeModel.maxAfterFees(totalBalance, capped.size, feeRateSompiPerGram, feeMultiplier, customExtraFeeSompi, signatureScriptSize = signatureScriptSize, changeScriptLen = changeScriptLen ?: ownScriptLen(address))
        // Capped only for automatic selection: hand-picked coins are a set of their own (iOS
        // `oneTransactionChunk` runs only without a selection).
        return MaxSendEstimate(amount, cappedAtInputLimit = manualUtxos.isNullOrEmpty() && capped.size < utxos.size, hasCoins = true)
    }

    /**
     * The largest set of UTXOs that fit in a single mass-safe transaction (up to
     * [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION], largest-first), plus the max self-send amount
     * for exactly that set. Kaspa caps a transaction's mass (~89 inputs), so the compound UI's "Max"
     * reflects *one transaction's worth* of consolidatable value instead of the whole (over-mass)
     * balance — the user consolidates that chunk, then repeats to reduce further. Returns the chunk
     * so the send pins exactly those inputs; null if nothing is spendable. Mirrors iOS's
     * ChatService.maxConsolidatableChunk. [address]'s UTXOs come back already maturity-filtered
     * (matured coinbase + non-coinbase) via [KaspaWalletEngine.fetchUtxos]. The Fast / Priority /
     * custom extra comes off flat, as in [estimateMaxSendableAmount].
     */
    suspend fun maxConsolidatableChunk(
        address: String,
        feeRateOverride: Long? = null,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        /** See [estimateMaxSendableAmount]. */
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
    ): Pair<Long, List<UtxoEntry>>? =
        consolidationChunk(address, feeRateOverride, feeMultiplier, customExtraFeeSompi, signatureScriptSize)
            ?.let { it.amountSompi to it.utxos }

    /** One compound's worth ([maxConsolidatableChunk]): the amount, the coins it pins, and whether
     *  the address holds more coins than one transaction can combine (iOS 1751d20
     *  `cappedAtInputLimit`: "Compound again for the rest"). */
    data class ConsolidationChunk(val amountSompi: Long, val utxos: List<UtxoEntry>, val cappedAtInputLimit: Boolean)

    /** [maxConsolidatableChunk] with whether the input cap left coins out. */
    suspend fun consolidationChunk(
        address: String,
        feeRateOverride: Long? = null,
        feeMultiplier: Long = 1L,
        customExtraFeeSompi: Long? = null,
        signatureScriptSize: Int = KaspaMass.SCHNORR_SIGNATURE_SCRIPT_LEN,
        /** The coins Coin Control picked, null for all of them (iOS da59726 `selection`). */
        selection: List<UtxoEntry>? = null,
    ): ConsolidationChunk? {
        val fetched = walletEngine.fetchUtxos(address)
        if (fetched.isEmpty()) return null
        val candidates = if (!selection.isNullOrEmpty()) {
            val freshByOutpoint = fetched.associateBy { it.outpoint }
            // Picked coins that are all gone say so (iOS resolveReviewSelection).
            selection.mapNotNull { freshByOutpoint[it.outpoint] }
                .ifEmpty { throw IllegalStateException("Selected UTXOs are no longer available - please reselect") }
        } else {
            fetched
        }
        val chunk = candidates.sortedByDescending { it.utxoEntry.amount }
            .take(KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION)
        if (chunk.isEmpty()) return null

        val totalBalance = UtxoMath.checkedTotal(chunk.map { it.utxoEntry.amount }) ?: return null // IOS-020
        val feeRateSompiPerGram = feeRateOverride?.coerceAtLeast(KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM)
            ?: walletEngine.fetchQuotedFeeRateSompiPerGram()
        val maxAmount = SendFeeModel.maxAfterFees(totalBalance, chunk.size, feeRateSompiPerGram, feeMultiplier, customExtraFeeSompi, signatureScriptSize = signatureScriptSize, changeScriptLen = ownScriptLen(address))
        return ConsolidationChunk(
            maxAmount,
            chunk,
            // a full chunk with coins left over; a smaller set picked by hand was not cut short
            cappedAtInputLimit = chunk.size == KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION && fetched.size > chunk.size,
        )
    }

    /**
     * One address page's History and UTXOs. iOS 1751d20 keeps these as each page's own @State
     * (SpendingAddressTransactionHistoryView, ChattingAddressManageView): a page starts empty with
     * its spinners and shows only what its own load brings back. They used to be one shared list
     * here, so a page showed the previous address's history and coins until its load landed.
     * Now each address has its own slot ([addressPageLists]); one with no slot yet reads as
     * empty and loading.
     */
    data class AddressPageLists(
        val txHistory: List<ColdStorageAddressDiscovery.AddressTransaction> = emptyList(),
        /** The last history fetch gave up. Kept separate from an empty list because the two mean
         *  opposite things and used to render identically. */
        val txHistoryFailed: Boolean = false,
        /** A history fetch is running, or none has landed yet. */
        val txHistoryLoading: Boolean = true,
        val utxos: List<ColdStorageAddressDiscovery.AddressUtxo> = emptyList(),
        /** A UTXO fetch is running, or none has landed yet. */
        val utxosLoading: Boolean = true,
        /** Why the last UTXO fetch could not ask the node - iOS's utxoLoadError, shown as the
         *  failure row in place of "No UTXOs.". Null after a fetch that answered. */
        val utxosError: String? = null,
    )

    private val _addressPageLists = MutableStateFlow<Map<String, AddressPageLists>>(emptyMap())
    /** Every address page's lists, by address. Read a page's with `[address] ?: AddressPageLists()`. */
    val addressPageLists: StateFlow<Map<String, AddressPageLists>> = _addressPageLists.asStateFlow()
    // Main-thread only (viewModelScope): the newest fetch per address, so an older one that
    // lands late is dropped rather than written over the newer answer.
    private val addressTxHistoryJobs = mutableMapOf<String, Job>()
    private val addressUtxoJobs = mutableMapOf<String, Job>()
    private val addressTxHistoryGen = mutableMapOf<String, Int>()
    private val addressUtxoGen = mutableMapOf<String, Int>()

    private fun updateAddressPageLists(address: String, change: (AddressPageLists) -> AddressPageLists) {
        _addressPageLists.update { all -> all + (address to change(all[address] ?: AddressPageLists())) }
    }

    /** A page opening for [address]: its lists start empty with their spinners, as an iOS page's
     *  @State does, then both load. */
    fun openAddressPageLists(address: String) {
        if (address.isEmpty()) return
        _addressPageLists.update { it + (address to AddressPageLists()) }
        loadSpendingAddressTxHistory(address)
        loadSpendingAddressUtxos(address)
    }

    /** Transaction history for a single address's page ([addressPageLists]).
     *  Reuses [ColdStorageAddressDiscovery]'s address-string-keyed REST fetch (no Cold Storage
     *  account state involved) rather than duplicating the same logic. */
    fun loadSpendingAddressTxHistory(address: String) {
        if (address.isEmpty()) return
        val gen = (addressTxHistoryGen[address] ?: 0) + 1
        addressTxHistoryGen[address] = gen
        addressTxHistoryJobs.remove(address)?.cancel()
        updateAddressPageLists(address) { it.copy(txHistoryLoading = true) }
        addressTxHistoryJobs[address] = viewModelScope.launch {
            val result = coldStorageAddressDiscovery.getTransactionHistoryResult(address, limit = ColdStorageAddressDiscovery.HISTORY_PAGE_SIZE)
            if (addressTxHistoryGen[address] != gen) return@launch
            updateAddressPageLists(address) {
                it.copy(txHistory = result.transactions, txHistoryFailed = !result.complete, txHistoryLoading = false)
            }
            addressTxHistoryJobs.remove(address)
        }
    }

    /** Live UTXOs for a single address's page ([addressPageLists]). */
    fun loadSpendingAddressUtxos(address: String) {
        if (address.isEmpty()) return
        val gen = (addressUtxoGen[address] ?: 0) + 1
        addressUtxoGen[address] = gen
        addressUtxoJobs.remove(address)?.cancel()
        updateAddressPageLists(address) { it.copy(utxosLoading = true, utxosError = null) }
        addressUtxoJobs[address] = viewModelScope.launch {
            val outcome = try {
                Result.success(coldStorageAddressDiscovery.getUtxosOrThrow(address))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (addressUtxoGen[address] != gen) return@launch
            updateAddressPageLists(address) { lists ->
                outcome.fold(
                    onSuccess = { lists.copy(utxos = it, utxosError = null, utxosLoading = false) },
                    // The list keeps what it had; an outage is not an empty address.
                    onFailure = { lists.copy(utxosError = UserFacingError.message(it, "Couldn't load the coins."), utxosLoading = false) },
                )
            }
            addressUtxoJobs.remove(address)
        }
    }

    private val _discoveringAddresses = MutableStateFlow(false)
    val discoveringAddresses: StateFlow<Boolean> = _discoveringAddresses.asStateFlow()

    /**
     * Re-runs the same gap-limit on-chain scan used on wallet import, to pick up any spending
     * address with real history beyond what's currently shown (e.g. KAS sent to one directly,
     * before the Manage Addresses screen ever generated it locally). [onResult] receives how
     * many used addresses the scan found in total (0 if none).
     */
    /** Live scan position, so Manage Addresses' actions sheet can count up during a discovery
     *  rather than sit on a spinner - same readout Cold Storage's sheet gives. */
    private val _spendingDiscoveryProgress = MutableStateFlow<SpendingAddressDiscovery.DiscoveryProgress?>(null)
    val spendingDiscoveryProgress: StateFlow<SpendingAddressDiscovery.DiscoveryProgress?> = _spendingDiscoveryProgress.asStateFlow()

    /** [onResult] receives how many addresses HOLD something (a balance or a KNS domain), not a
     *  recovered index - see [SpendingAddressDiscovery.discoverFunded].
     *
     *  The sweep belongs to the account it started for - [owner], or the active account when
     *  the sweep starts (iOS e571f45, IOS-107, owner's choice): that account's address and chain
     *  material are taken at the start ([WalletManager.spendingSweepAccount]), and every
     *  derivation and write (legacy bound, hidden set, quantum bound, address cache) goes to that
     *  account even if another one is active by the time an await returns. Its results are
     *  dropped only if the account was removed meanwhile. */
    fun discoverSpendingAddresses(owner: String? = null, onResult: (Int) -> Unit) {
        if (_discoveringAddresses.value) return
        viewModelScope.launch {
            _discoveringAddresses.value = true
            _spendingDiscoveryProgress.value = SpendingAddressDiscovery.DiscoveryProgress(0, 0)
            try {
                val sweepAccount = walletManager.spendingSweepAccount(owner)
                if (sweepAccount == null) {
                    onResult(0)
                    return@launch
                }
                val walletAddress = sweepAccount.owner
                val matched = spendingAddressDiscovery.discoverFunded(sweepAccount) { progress ->
                    _spendingDiscoveryProgress.value = progress
                }
                // IOS-107: the writes below go to the swept account whichever one is active now;
                // they are dropped only if it was removed. Nothing between here and the quantum
                // sweep suspends.
                val sweptRecord = walletManager.sweepAccountRecord(sweepAccount)
                if (sweptRecord == null) {
                    android.util.Log.i("WalletViewModel", "Discovery dropped: its account was removed during the sweep")
                    onResult(0)
                    return@launch
                }
                // The stored bound has to cover the highest MATCH so those rows can be derived
                // and shown. It only ever grows: an address that held funds last month and is
                // empty now should not vanish from the list.
                matched.maxOrNull()?.let { highest ->
                    val previousMax = WalletManager.legacyMaxSpendingIndex(sweptRecord)
                    // Hide the empty indices the new bound sweeps in, exactly as
                    // revealSpendingAddress does. The scan now reaches a thousand addresses deep,
                    // so a match at index 291 would otherwise raise the bound and flood Manage
                    // Addresses with 290 empty rows - and make its next load fetch a balance for
                    // every one of them.
                    for (i in (previousMax + 1) until highest) {
                        if (i !in matched) walletManager.setSpendingAddressHidden(walletAddress, i, true)
                    }
                    walletManager.ensureMaxSpendingAddressIndexAtLeast(walletAddress, highest)
                }
                // Anything the scan matched gets un-hidden. A previously-hidden address that now
                // holds a balance or a domain would otherwise be found and then dropped straight
                // back out of the list, which reads as the scan not finding it at all - and
                // hiding is a tidying choice about empty addresses, not a decision that should
                // outrank a balance.
                matched.forEach { walletManager.setSpendingAddressHidden(walletAddress, it, false) }
                // The quantum-safe (KIP-22) chain's half (iOS discoverQuantumAddresses): funded
                // quantum slots extend its own bound and are un-hidden the same way.
                // Tied to the same account: it writes there even after a switch, and nothing
                // (counting 0) once that account has been removed.
                val quantumMatched = spendingAddressDiscovery.discoverQuantumAddresses(sweepAccount)
                // Load first, THEN reconcile: the list fetch already reads every address's
                // balance in one go, so the reservation check can use those instead of making
                // its own per-address REST calls - which is both faster and the reason this
                // used to come back empty-handed, since those extra calls are exactly what the
                // public API throttles. loadManageAddressesAndAwait rather than the fire-and-
                // forget variant, or the rows would not be there yet to read.
                loadManageAddressesAndAwait()
                onResult(matched.size + quantumMatched)
            } finally {
                _discoveringAddresses.value = false
                _spendingDiscoveryProgress.value = null
            }
        }
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    /**
     * False until the cold-start routing decision is known: with a wallet present, the auto-login
     * gate below has to read the (async, DataStore-backed) biometrics-for-login setting before
     * `isLoggedIn` reflects where startup should land. KaChatApp holds off composing either the
     * accounts/Welcome screen or the main shell until this flips true — otherwise every cold
     * start briefly flashed the saved-accounts list before jumping into the last-used account.
     * With no wallet at all, onboarding is the right destination immediately.
     */
    private val _startupResolved = MutableStateFlow(false)
    val startupResolved: StateFlow<Boolean> = _startupResolved

    /** Sentinel for "no activeAddressFlow emission observed yet" — distinct from null, which is a
     *  real state (logged out / no wallet). See the account-change collector in [init]. */
    private object NoAddressYet

    /**
     * Clears every per-account StateFlow this activity-scoped ViewModel holds, called the moment
     * the active account changes. Without this, a freshly created account inherits the previous
     * account's KNS identity on screen (domain name, banner, avatar, bio) until a network fetch
     * happens to overwrite it — and [_knsProfile] was never overwritten at all for a domainless
     * account, so the old profile card stuck permanently.
     */
    private fun resetPerAccountUiState() {
        _ownedDomainAssets.value = emptyList()
        _primaryDomainName.value = null
        _knsProfile.value = null
        _addressKnsDomains.value = emptyList()
        _domainOwningAddresses.value = emptySet()
        _quantumSpentAddresses.value = emptySet()
        walletService.resetBalancesForAccountSwitch()
    }

    init {
        // One-time 4.0 dock seeding (existing users: KaPosts/Broadcasts/+More enabled, dock
        // preserved via the cap/cycle; fresh installs: minimal Chats/Profile/+More dock).
        // Sentinel-guarded no-op on every later launch.
        viewModelScope.launch { settings.applyKaPostsTabDefaultsIfNeeded() }
        // Placement migration, derived from what this user could already SEE rather than reset to
        // the default, so an arrangement they built survives. Runs after the 4.0 seeding so it
        // reads the settled hidden set.
        viewModelScope.launch {
            settings.applyPlacementDefaultsIfNeeded(
                resolveVisible = { order, hidden ->
                    @Suppress("DEPRECATION")
                    com.kachat.app.ui.resolveTabOrder(order, hidden).map { it.route }
                },
                pinned = com.kachat.app.ui.PINNED_DOCK_ROUTES,
                assignable = com.kachat.app.ui.ASSIGNABLE_TAB_ROUTES,
                maxDock = com.kachat.app.ui.MAX_DOCK_ITEMS,
            )
        }
        // (KaPosts social-ping polling is started/stopped by KaChatApplication's process
        // lifecycle observer now — foreground-only, since push covers KaPosts when closed.)
        // Mirror the active account's address into DataStore so the per-account dock keys
        // (AppSettingsRepository.tabOrder/hiddenTabs) always resolve against the right account,
        // including immediately after an account switch.
        viewModelScope.launch {
            // Sentinel distinct from any real address (including null = logged out), so the very
            // first emission after process start never counts as an account CHANGE — resetting
            // there would race the init-block refreshes below for no benefit.
            var lastSeenAddress: Any? = NoAddressYet
            walletManager.activeAddressFlow.collect { address ->
                if (lastSeenAddress != NoAddressYet && lastSeenAddress != address) {
                    // Account switched (create/import/switch/logout): this ViewModel outlives the
                    // account (it's activity-scoped), so every per-account StateFlow must reset
                    // NOW rather than showing the previous account's data until some screen-entry
                    // fetch happens to overwrite it. Same disease ColdStorageViewModel had with
                    // its shared _addresses (fixed by resetAddressesIfAccountChanged).
                    resetPerAccountUiState()
                    if (address != null) {
                        // Repopulate for the new account right away (an account with no domains
                        // simply stays blank — exactly what a brand-new account should show).
                        refreshOwnedDomains()
                    }
                }
                lastSeenAddress = address
                if (address != null) {
                    settings.setActiveAddress(address)
                }
            }
        }
        if (walletManager.hasWallet()) {
            // Signed out last time (Log Out, Delete): the accounts are listed, none is active and
            // none opens (iOS da59726 `logoutFlagKey`).
            val signedOut = walletManager.isSessionLoggedOut()
            _accounts.value = walletManager.getAllAccounts()
            if (!signedOut) {
                _address.value = walletManager.getAddress()
                _accountName.value = walletManager.getAccountName()
                refreshBalance()
                refreshSpendingAddress()
            }

            // A cold start should land back in whichever account was already active, not the
            // Welcome screen's saved-accounts list, since nothing about closing and reopening the
            // app implies the user wants to switch or re-confirm anything — unless they've
            // explicitly turned on biometrics for account login, in which case that tap-to-unlock
            // gate (see OnboardingScreen's SavedAccountCard) is the whole point and must still fire.
            viewModelScope.launch {
                if (!signedOut && !settings.biometricAccountLoginEnabled.first()) {
                    _isLoggedIn.value = true
                }
                // Only now is the start destination known — see startupResolved's doc comment.
                _startupResolved.value = true
            }
        } else {
            // No wallet: onboarding is the correct first screen, nothing async to wait for.
            _startupResolved.value = true
        }
    }

    fun refreshBalance() {
        viewModelScope.launch {
            walletService.refreshBalance()
        }
    }

    /** Suspend variant of [refreshBalance] - awaits the actual balance fetch instead of firing it
     *  into [viewModelScope] and returning immediately, for callers (the KNS create-profile
     *  wizard's funding gate, now checking the identity/chatting address since all KNS activity
     *  is funded and settled there) that need to know the balance is current before deciding
     *  whether to proceed. */
    suspend fun refreshBalanceAndAwait() {
        walletService.refreshBalance()
    }

    /** Why the last saved-account sign-in failed, for the Welcome screen's "Sign In Failed" alert
     *  (iOS 4922804 signInFailureMessage). Held here, not on the screen, as iOS does. */
    private val _signInFailureMessage = MutableStateFlow<String?>(null)
    val signInFailureMessage: StateFlow<String?> = _signInFailureMessage.asStateFlow()

    fun clearSignInFailure() {
        _signInFailureMessage.value = null
    }

    fun login(address: String? = null) {
        if (address != null) {
            // A saved account only, and a failure says why rather than crashing: an address no
            // longer in the list left nothing active, and getAddress() threw (iOS 4922804).
            if (walletManager.getAllAccounts().none { com.kachat.app.util.KaspaNetwork.isSameAccount(it.address, address) }) {
                _signInFailureMessage.value = "Unable to sign in to this account."
                return
            }
            val wasSignedOut = walletManager.isSessionLoggedOut()
            try {
                // Ends a signed-out session before anything reads the active account.
                walletManager.setActiveAccount(address)
                _address.value = walletManager.getAddress()
                _accountName.value = walletManager.getAccountName()
            } catch (e: Exception) {
                if (wasSignedOut) walletManager.markSessionLoggedOut()
                android.util.Log.e("WalletViewModel", "Sign-in failed", e)
                _signInFailureMessage.value = e.message ?: "Unable to sign in to this account."
                return
            }
            _signInFailureMessage.value = null
            refreshBalance()
            refreshSpendingAddress()
        }
        if (walletManager.hasWallet()) {
            _isLoggedIn.value = true
        }
    }

    /** Stays signed out across launches, with nothing watching this account (iOS da59726
     *  `logoutFlagKey`, `currentWallet = nil`). */
    fun logout() {
        walletManager.markSessionLoggedOut()
        _isLoggedIn.value = false
        _address.value = null
        _accountName.value = null
    }

    /** The accounts are here but their keys can't be opened: onboarding opens on the recovery
     *  screen (iOS WalletRecoveryView). */
    val keysUnavailable: StateFlow<Boolean> = walletManager.keysUnavailable

    /** The account last open on this phone, from the plain settings copy - the encrypted list
     *  that names it is the thing that can't be read. */
    val accountWithoutKeysAddress: StateFlow<String?> =
        settings.activeAddress.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /**
     * The recovery screen's "Remove This Account From the Phone": the unreadable store goes, and
     * so does what this phone kept for the account last open on it (iOS deleteWallet of
     * walletNeedingKeyRecovery). Nothing on the chain changes.
     *
     * Only while the keys still can't be opened: once a recovery phrase has been saved here this
     * would erase a working account's data, so it stops with nothing removed and returns why
     * (iOS 4922804). Null when the account was removed.
     */
    fun removeAccountWithoutKeys(): String? {
        // Asked again now: what couldn't be opened at launch may open today, and Remove would
        // erase it (iOS da59726).
        val reopened = walletManager.reopenStore()
        // Couldn't be read this moment, which is not "gone": every account's seed is in there
        // (iOS da59726 `.unreadable`).
        if (reopened == WalletManager.StoreReopen.UNREADABLE_FOR_NOW) {
            return "This account's saved keys can't be read right now, so nothing was removed. Unlock this phone and try again."
        }
        if (reopened == WalletManager.StoreReopen.READABLE && keysAreBack()) return "This account's keys are on this phone again, so nothing was removed."
        val address = accountWithoutKeysAddress.value
        // Should even a new store be refused, the Welcome screen still opens; saving an account
        // there tries again.
        runCatching { walletManager.replaceUnreadableStore() }
            .onFailure { android.util.Log.e("WalletViewModel", "Wallet store could not be replaced", it) }
        if (address != null) {
            // Its backup status goes with it (iOS deleteWallet forgets the account's flags).
            accountBackupStatus.forget(address)
            // and what the app keeps for it (iOS da59726 removeWalletTabData; its reminders below)
            walletManager.removeWalletTabData(address)
            chessBotProgress.removeAll(address)
            wipeLocalData(address, lastAccount = !walletManager.hasWallet())
        }
        _accounts.value = walletManager.getAllAccounts()
        _hasWallet.value = walletManager.hasWallet()
        _isLoggedIn.value = false
        _address.value = null
        _accountName.value = null
        return null
    }

    /**
     * True when the accounts can be read after all: the store that couldn't be opened at launch
     * opens now, or a recovery phrase has been saved here since. The Welcome screen then lists
     * them (iOS da59726 WalletRecoveryView, which reloads when the phone's saved keys can be read).
     * [signIn]: and the account opens as at any launch, unless biometric login guards it or the
     * person had signed out (iOS `loadWallet()`, not forced).
     */
    fun keysAreBack(signIn: Boolean = false): Boolean {
        if (!walletManager.reopenStoreIfReadable() || !walletManager.hasWallet()) return false
        _accounts.value = walletManager.getAllAccounts()
        _hasWallet.value = true
        if (signIn && !walletManager.isSessionLoggedOut()) {
            viewModelScope.launch {
                if (!settings.biometricAccountLoginEnabled.first()) {
                    walletManager.getActiveAccount()?.let { login(it.address) }
                }
            }
        }
        return true
    }

    /** What the database keeps for an account that has left this phone, on both networks: the
     *  same key is the same account as kaspa: and kaspatest:, and each kept its own (iOS 741c005).
     *  [lastAccount]: none is left, so the public room messages they shared go too. */
    private fun wipeLocalData(address: String, lastAccount: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            for (variant in com.kachat.app.util.KaspaNetwork.accountAddressVariants(address)) {
                runCatching { chatRepository.wipeAllLocalDataForAddress(variant) }
                    .onFailure { android.util.Log.w("WalletViewModel", "Local data wipe failed for a deleted account", it) }
                // its .kachat reminders would keep firing for names this phone no longer holds
                runCatching { kachatNameReminders.removeAll(variant) }
                    .onFailure { android.util.Log.w("WalletViewModel", ".kachat reminder removal failed for a deleted account", it) }
            }
            if (lastAccount) {
                runCatching { chatRepository.wipePublicRoomMessages() }
                    .onFailure { android.util.Log.w("WalletViewModel", "Public room wipe failed", it) }
                broadcastRepository.resetAfterRoomWipe()
            }
        }
    }

    /**
     * Renames any saved account by address — edited from the Welcome screen's saved-accounts
     * list, not just the currently active one, since you can rename an account you're not
     * logged into.
     */
    fun renameAccount(address: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        walletManager.renameAccount(address, trimmed)
        _accounts.value = walletManager.getAllAccounts()
        if (_address.value == address) {
            _accountName.value = trimmed
        }
    }

    // Name + words captured during the seed step, carried to the passphrase step where the wallet
    // is actually committed (create) or imported. In-memory only, like the onboarding flow itself.
    private var pendingAccountName: String = ""
    private var pendingMnemonicWords: List<String> = emptyList()

    /** Create New Account's tap, while the account is being made. */
    private val _isCreatingAccount = MutableStateFlow(false)
    val isCreatingAccount: StateFlow<Boolean> = _isCreatingAccount.asStateFlow()

    /** Why Create New Account's one tap failed, for its "Couldn't Create Account" alert. */
    private val _createAccountError = MutableStateFlow<String?>(null)
    val createAccountError: StateFlow<String?> = _createAccountError.asStateFlow()

    fun clearCreateAccountError() {
        _createAccountError.value = null
    }

    /** A new account in one tap: 24 words, no passphrase, the next free default name. The seed
     *  phrase comes later, through the Wallet tab's backup card (iOS ddec987). */
    fun createAccountInOneTap() {
        if (_isCreatingAccount.value) return
        _isCreatingAccount.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val words = walletManager.generateMnemonic(24)
                    commitOwingBackup(words, "") {
                        walletManager.commitCreatedWallet(walletManager.nextDefaultAccountName(), words, "")
                    }
                }
                finishCreatingAccount()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("WalletViewModel", "createAccountInOneTap failed", e)
                _createAccountError.value = e.message ?: "Something went wrong"
            } finally {
                _isCreatingAccount.value = false
            }
        }
    }

    /** "Advanced: Create with a Passphrase": a new 24-word seed for the passphrase screens, which
     *  commit it. In memory only, and never shown (audit AND-031, iOS ddec987). */
    fun beginCreateWithPassphrase() {
        pendingSourceFamily = WalletSourceFamily.KASPA_STANDARD
        pendingAccountName = ""
        pendingMnemonicWords = walletManager.generateMnemonic(24)
    }

    /** The passphrase step came back after the process was recreated: its words are gone. */
    fun ensureCreateWithPassphrase() {
        if (pendingMnemonicWords.isEmpty()) beginCreateWithPassphrase()
    }

    /** Create with a Passphrase was left: forget the uncommitted words. */
    fun abandonCreatedWallet() {
        pendingAccountName = ""
        pendingMnemonicWords = emptyList()
    }

    /** Commits the pending new wallet with the chosen passphrase ("" = none), then logs in. The
     *  save happens before login() so the main shell sees a fully-persisted active account. */
    fun commitCreatedWallet(passphrase: String) {
        // The words are dropped once committed or abandoned (AND-031): a second tap has nothing
        // to commit.
        val words = pendingMnemonicWords
        // One commit at a time: the save takes a moment off the main thread, and a second tap
        // meanwhile would save the same account twice.
        if (words.isEmpty() || _isCreatingAccount.value) return
        _isCreatingAccount.value = true
        viewModelScope.launch {
            try {
                // Two passphrase derivations and a keystore write: off the main thread, as the
                // one-tap create.
                withContext(Dispatchers.Default) {
                    commitOwingBackup(words, passphrase) {
                        walletManager.commitCreatedWallet(walletManager.nextDefaultAccountName(), words, passphrase)
                    }
                }
                _createWalletError.value = null
                abandonCreatedWallet()
                finishCreatingAccount()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: WalletManager.AccountsNotKeptException) {
                // The accounts already on this device couldn't be kept: nothing was changed.
                android.util.Log.e("WalletViewModel", "commitCreatedWallet stopped", e)
                _createWalletError.value = e.message
            } catch (e: Exception) {
                // Shown, as iOS alerts, rather than crashing the app (a keystore failure, say).
                android.util.Log.e("WalletViewModel", "commitCreatedWallet failed", e)
                _createWalletError.value = e.message ?: "Something went wrong"
            } finally {
                _isCreatingAccount.value = false
            }
        }
    }

    /** Nobody has seen these words yet: the backup strip stays until they're backed up. Marked
     *  before the keys are saved, so an app killed mid-create can't leave an account that is never
     *  owed its backup; nothing created, nothing owed (iOS 8d5f925). */
    private inline fun commitOwingBackup(words: List<String>, passphrase: String, commit: () -> Unit) {
        val address = walletManager.previewIdentityAddress(words, passphrase)
        address?.let { accountBackupStatus.markPending(it) }
        try {
            commit()
        } catch (e: Exception) {
            address?.let { accountBackupStatus.markDone(it) }
            throw e
        }
    }

    private fun finishCreatingAccount() {
        // nobody has seen these words yet: the backup strip stays until they're backed up
        accountBackupStatus.markPending(walletManager.getAddress())
        _hasWallet.value = true
        _address.value = walletManager.getAddress()
        _accountName.value = walletManager.getAccountName()
        _accounts.value = walletManager.getAllAccounts()
        markPendingWelcomeGuide()
        login()
        refreshBalance()
    }

    /** The backup steps were finished or Backup Hygiene passed: the six months start over. */
    fun markAccountBackupConfirmed() {
        _address.value?.let { accountBackupStatus.markConfirmed(it) }
    }

    /**
     * Always returns to the Welcome/saved-accounts screen after deleting an account, even if
     * other saved accounts remain — silently falling through to whichever account happens to be
     * "next" would leave the user unsure which account they're now using, right after a
     * destructive action. An explicit re-login/account tap is clearer.
     */
    fun deleteWallet(address: String) {
        // The record first (iOS da59726 stops polling before anything goes): the active address
        // moves off the account, so everything watching it stops writing for it. Signed out before
        // that, so nothing moves onto the next saved account (iOS da59726 clears the active slot).
        walletManager.markSessionLoggedOut()
        walletManager.deleteAccount(address)
        accountBackupStatus.forget(address)
        chessBotProgress.removeAll(address)
        // Everything this account held locally goes with it. wipeAllLocalDataForAddress has been
        // here the whole time and nothing called it on deletion, so a deleted account's messages,
        // contacts, reactions, edits, groups and sync cursors all stayed on the device - and came
        // back the moment the account was re-imported (iOS 78152d3).
        wipeLocalData(address, lastAccount = !walletManager.hasWallet())
        _accounts.value = walletManager.getAllAccounts()
        _hasWallet.value = walletManager.hasWallet()
        _isLoggedIn.value = false
        _address.value = null
        _accountName.value = null
    }

    /** Set when a deletion went through but its Nextcloud archive could not be removed. The
     *  account is already gone at that point, so this is the only place it can be said. */
    private val _deleteAccountError = MutableStateFlow<String?>(null)
    val deleteAccountError: StateFlow<String?> = _deleteAccountError.asStateFlow()

    fun clearDeleteAccountError() { _deleteAccountError.value = null }

    enum class ImportWalletStatus { IDLE, IMPORTING, SUCCESS, FAILED }

    data class ImportWalletUiState(val status: ImportWalletStatus = ImportWalletStatus.IDLE, val errorMessage: String? = null)

    private val _importWalletState = MutableStateFlow(ImportWalletUiState())
    val importWalletState: StateFlow<ImportWalletUiState> = _importWalletState.asStateFlow()

    /**
     * Validates the seed phrase up front (so a typo surfaces on the import screen, not after the
     * passphrase step) and stashes it for [commitImport]. Returns false and surfaces the error if
     * the mnemonic is invalid. The passphrase itself can't be validated — a wrong one just derives
     * a different, empty account — so it's only collected on the next screen.
     */
    /**
     * The wallet type the pending import lands on: what import's auto-detect found or the user
     * picked on the passphrase step ([setPendingImportChoice]), or an NFC card's backup. Persisted
     * on the account by [commitImport]. Reset to the default whenever a fresh import run starts.
     */
    private var pendingSourceFamily: WalletSourceFamily = WalletSourceFamily.KASPA_STANDARD

    /** True while the CURRENT onboarding run is an import (not a create) — gates the wizard's
     *  "Change Chatting Address" option, which must never appear on Help replays or after a
     *  freshly created wallet (whose index 0 is the only address that can exist). Mirrors iOS's
     *  `WalletManager.justImportedWallet`. */
    private val _justImportedWallet = MutableStateFlow(false)
    val justImportedWallet: StateFlow<Boolean> = _justImportedWallet.asStateFlow()

    fun clearJustImportedWallet() {
        _justImportedWallet.value = false
    }

    fun prepareImport(name: String, words: List<String>): Boolean {
        if (!walletManager.isValidMnemonic(words)) {
            _importWalletState.value = ImportWalletUiState(
                status = ImportWalletStatus.FAILED,
                errorMessage = "These words are invalid. Double-check the words - the last word encodes a checksum, so one wrong word fails validation."
            )
            return false
        }
        pendingAccountName = name
        pendingMnemonicWords = words
        pendingChattingAddressIndex = 0
        _importWalletState.value = ImportWalletUiState() // clear any prior FAILED before advancing
        return true
    }

    /** The standard wallet's address #0 the pending seed would produce with [passphrase] - drives
     *  the live preview on the passphrase entry screen until import's search places the words.
     *  See [WalletManager.previewIdentityAddress]. */
    fun previewChattingAddress(passphrase: String): String? =
        if (pendingMnemonicWords.isEmpty()) null
        else walletManager.previewIdentityAddress(pendingMnemonicWords, passphrase, WalletSourceFamily.KASPA_STANDARD)

    /** What [address] holds for the passphrase entry screen's Balance, or null when neither a node
     *  nor the REST gateway answered (iOS ddec987). */
    suspend fun previewAddressBalanceSompi(address: String): Long? =
        walletEngine.nodeBalance(address)
            ?: runCatching { com.kachat.app.util.UtxoMath.checkedTotal(walletEngine.fetchUtxosOrThrow(address, spendableOnly = false).map { it.utxoEntry.amount }) }.getOrNull()

    /** The address the passphrase step found or the user picked there. */
    private var pendingChattingAddressIndex = 0

    /** The wallet type and address the passphrase step found or the user picked there (iOS
     *  `ImportWalletView.importChoice`). */
    fun setPendingImportChoice(choice: com.kachat.app.models.ImportAccountChoice) {
        pendingSourceFamily = choice.family
        pendingChattingAddressIndex = choice.index
    }

    /** Address #0 of every wallet type the pending words could be, for an import before there's
     *  an account: the rows "See Every Wallet Type" offers. Needs no network (iOS
     *  `importAddressesAtZero`). */
    fun importAddressesAtZero(passphrase: String): List<com.kachat.app.models.ImportAccountCandidate> {
        val words = pendingMnemonicWords
        if (words.isEmpty()) return emptyList()
        return walletManager.deriveImportCandidates(words, passphrase) { 0 until 1 }
    }

    /**
     * Import's auto-detect: every wallet type's addresses ([WalletSourceFamily.importSearchDepth]),
     * keeping the ones with funds, plus any address #0 with transaction history (a chat identity
     * or an emptied wallet holds no coins). The wallet type holding the most comes first and each
     * type's addresses are in index order, so the first entry is the likeliest account. Empty when
     * nothing has any activity; null when the words are unusable or the balances couldn't be
     * checked (iOS `findImportAccounts`).
     */
    suspend fun findImportAccounts(passphrase: String): List<com.kachat.app.models.ImportAccountCandidate>? {
        val words = pendingMnemonicWords
        if (words.isEmpty()) return null
        val candidates = withContext(Dispatchers.Default) {
            walletManager.deriveImportCandidates(words, passphrase) { it.importSearchDepth }
        }
        if (candidates.isEmpty()) return null
        val addresses = candidates.map { it.address }
        val (balances, withHistory) = kotlinx.coroutines.coroutineScope {
            val used = async { walletService.addressesWithHistory(candidates.filter { it.index == 0 }.map { it.address }) }
            val held = walletEngine.nodeBalances(addresses)
                ?: walletService.fetchBalancesBatched(addresses, requireAll = true).takeIf { it.isNotEmpty() }
            if (held == null) used.cancel()
            held to (if (held == null) emptySet() else used.await())
        }
        if (balances == null) return null

        val totals = mutableMapOf<WalletSourceFamily, Long>()
        val active = candidates.mapNotNull { candidate ->
            val found = candidate.copy(
                balanceSompi = balances[candidate.address] ?: 0L,
                hasHistory = candidate.address in withHistory,
            )
            if (found.balanceSompi <= 0L && !found.hasHistory) return@mapNotNull null
            totals[found.family] = (totals[found.family] ?: 0L) + found.balanceSompi
            found
        }
        return active.sortedWith { lhs, rhs ->
            when {
                lhs.family == rhs.family -> lhs.index.compareTo(rhs.index)
                (totals[lhs.family] ?: 0L) != (totals[rhs.family] ?: 0L) ->
                    (totals[rhs.family] ?: 0L).compareTo(totals[lhs.family] ?: 0L)
                // Ties go to the earlier type, so the standard wallet wins when nothing tells them apart.
                else -> lhs.family.ordinal.compareTo(rhs.family.ordinal)
            }
        }
    }

    /** The active account's backup for an NFC card (iOS 0877f88 `NFCCardBackupView.save`). The
     *  passphrase is never in it, and without one there is nothing to protect it. */
    fun nfcCardBackupPayload(): com.kachat.app.services.NfcCardBackupPayload {
        val words = walletManager.getActiveMnemonic()?.split(" ")?.filter { it.isNotBlank() }.orEmpty()
        if (words.isEmpty()) throw com.kachat.app.services.NfcCardException.SeedUnavailable()
        val passphrase = walletManager.getActivePassphrase().orEmpty()
        if (passphrase.isEmpty()) throw com.kachat.app.services.NfcCardException.NoPassphrase()
        val family = walletManager.activeSourceFamily()
        val index = walletManager.activeChattingAddressIndex()
        // The same derivation a restore checks against, so the two can't disagree.
        val address = walletManager.previewIdentityAddress(words, passphrase, family, index)
            ?: throw com.kachat.app.services.NfcCardException.SeedUnavailable()
        return com.kachat.app.services.NfcCardBackupPayload(
            words = words,
            chattingAddress = address,
            family = com.kachat.app.services.NfcCardBackupFormat.familyRaw(family),
            chattingAddressIndex = index,
            alias = walletManager.getAccountName().orEmpty(),
        )
    }

    /** The address a card's backup opens with [passphrase]: the live preview, and the check that
     *  catches a mistyped one. Null when the backup can't be used here. */
    fun previewCardAddress(payload: com.kachat.app.services.NfcCardBackupPayload, passphrase: String): String? {
        val family = com.kachat.app.services.NfcCardBackupFormat.family(payload.family) ?: return null
        return walletManager.previewIdentityAddress(payload.words.orEmpty(), passphrase, family, payload.chattingAddressIndex ?: return null)
    }

    /** Imports a card's backup with its passphrase, the same as a typed import (iOS 0877f88). */
    fun restoreFromNfcCard(payload: com.kachat.app.services.NfcCardBackupPayload, passphrase: String) {
        // Checked before anything is kept: a card this app can't read leaves no words behind.
        val family = com.kachat.app.services.NfcCardBackupFormat.family(payload.family) ?: return
        val index = payload.chattingAddressIndex ?: return
        pendingMnemonicWords = payload.words.orEmpty()
        // An account already saved here keeps the name it has, not the one on the card (iOS 4922804).
        val savedName = payload.chattingAddress?.let { onCard ->
            walletManager.getAllAccounts()
                .firstOrNull { com.kachat.app.util.KaspaNetwork.isSameAccount(it.address, onCard) }
                ?.name?.trim()?.takeIf { it.isNotEmpty() }
        }
        pendingAccountName = savedName ?: payload.alias.orEmpty()
        pendingSourceFamily = family
        pendingChattingAddressIndex = index
        commitImport(passphrase)
    }

    /** Imports the prepared wallet with the chosen passphrase ("" = none), then logs in. */
    fun commitImport(passphrase: String) {
        if (_importWalletState.value.status == ImportWalletStatus.IMPORTING) return
        viewModelScope.launch {
            _importWalletState.value = ImportWalletUiState(status = ImportWalletStatus.IMPORTING)
            try {
                // Key derivation and a keystore write: off the main thread, as creating is.
                val words = pendingMnemonicWords
                val name = pendingAccountName
                // What the search found or the user picked, with or without a passphrase (iOS:
                // import's auto-detect now runs on No as well).
                val family = pendingSourceFamily
                val index = pendingChattingAddressIndex
                withContext(Dispatchers.Default) {
                    walletManager.importWallet(words, name, passphrase, family = family, chattingAddressIndex = index)
                }
                _justImportedWallet.value = true
                pendingMnemonicWords = emptyList()
                pendingChattingAddressIndex = 0
                // whoever imported it has the seed: the six months start now
                accountBackupStatus.markConfirmed(walletManager.getAddress())
                _hasWallet.value = true
                _address.value = walletManager.getAddress()
                _accountName.value = walletManager.getAccountName()
                _accounts.value = walletManager.getAllAccounts()
                refreshBalance()
                _importWalletState.value = ImportWalletUiState(status = ImportWalletStatus.SUCCESS)
                markPendingWelcomeGuide()
                login()

                // The account's profile (avatar, banner, bio, Linktree), saved from any device,
                // comes with it: the indexer's record is adopted at once (iOS 5d4ce87).
                walletManager.getActiveAccount()?.address?.let { chatting ->
                    if (com.kachat.app.services.kachatnames.KachatNamesService.profilesEnabled) {
                        launch {
                            try {
                                kachatRegistry.get().syncOwnProfile(chatting)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("WalletViewModel", "own profile sync failed", e)
                            }
                        }
                    }
                }

                // Recovers this mnemonic's real spending-address index if it was already used
                // with this feature before (a different install, or after a wipe) — runs after
                // reporting import success so it doesn't add scan latency to that UX; a fresh
                // mnemonic just confirms index 0, which is already the default.
                val importedAddress = walletManager.getAddress()
                launch {
                    try {
                        // The restored account's own (IOS-107, as the sweep below, iOS e571f45):
                        // the scan derives that account's chain and the index and visibility go
                        // to it even if another account is active by then; dropped only if it
                        // was removed meanwhile.
                        val restored = walletManager.spendingSweepAccount(importedAddress)
                        if (restored != null) {
                            val recoveredIndex = spendingAddressDiscovery.discoverIndex(restored)
                            if (walletManager.sweepAccountStillSaved(restored)) {
                                walletManager.setSpendingAddressIndex(restored.owner, recoveredIndex)
                                // Initial visibility: only the primary and funded addresses paint
                                // on Manage Addresses after a rebuild; every other recovered index
                                // starts hidden (see seedImportedSpendingVisibility's doc for the
                                // full rule).
                                walletService.seedImportedSpendingVisibility(restored)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WalletViewModel", "Spending address discovery failed", e)
                    }
                    // A restored seed finds its spending addresses by itself - legacy and quantum,
                    // the same sweep as Manage Addresses' Discover Addresses - so balances and
                    // change rotation are right without anyone knowing to tap it (owner's call,
                    // iOS da2c8e2). Only this path: a typed-in recovery phrase; creating or
                    // switching accounts doesn't scan. Tied to the restored account: it writes
                    // there even if another account is active by then (iOS e571f45, IOS-107).
                    // Then the Wallet tab's spending total reads what it found (iOS 1751d20
                    // `HotWalletBalance.refresh(force:)` after the sweep).
                    discoverSpendingAddresses(owner = importedAddress) { refreshSpendingAddress() }
                }
            } catch (e: Exception) {
                android.util.Log.e("WalletViewModel", "importWallet failed", e)
                _importWalletState.value = ImportWalletUiState(
                    status = ImportWalletStatus.FAILED,
                    // Only words that really failed are called invalid (iOS da59726
                    // `invalidSeedPhrase`); anything else says what happened - the saved accounts
                    // couldn't be kept (IOS-016), a keystore failure.
                    errorMessage = if (e is org.bitcoinj.crypto.MnemonicException) {
                        "These words aren't valid. Please enter your 12 or 24 words."
                    } else {
                        e.message ?: "Something went wrong"
                    }
                )
            }
        }
    }

    /** Why committing a new wallet stopped (the saved accounts couldn't be kept - IOS-016), or
     *  null. Shown on the create flow's passphrase step. */
    private val _createWalletError = MutableStateFlow<String?>(null)
    val createWalletError: StateFlow<String?> = _createWalletError.asStateFlow()

    fun clearCreateWalletError() {
        _createWalletError.value = null
    }

    fun resetImportWalletState() {
        _importWalletState.value = ImportWalletUiState()
        pendingSourceFamily = WalletSourceFamily.KASPA_STANDARD
        pendingChattingAddressIndex = 0
    }

    // --- Chatting-address picker (import onboarding runs only) --------------------------------

    /** Progressive scan state for the wizard's "Change Chatting Address" picker: candidates found
     *  so far, how many indices have been covered, and whether a pass is running/failed. */
    data class ChattingAddressScanState(
        val candidates: List<WalletService.ChattingAddressCandidate> = emptyList(),
        val scannedCount: Int = 0,
        val isScanning: Boolean = false,
        val failed: Boolean = false
    )

    private val _chattingAddressScan = MutableStateFlow(ChattingAddressScanState())
    val chattingAddressScan: StateFlow<ChattingAddressScanState> = _chattingAddressScan.asStateFlow()

    /** The identity-chain index the active account currently chats from (0 = the default identity). */
    fun currentChattingAddressIndex(): Int = walletManager.activeChattingAddressIndex()

    /** Scans the next [batchSize] identity-chain indices; "Scan Further" simply calls this again. */
    fun scanNextChattingAddressBatch(batchSize: Int = CHATTING_ADDRESS_SCAN_BATCH) {
        if (_chattingAddressScan.value.isScanning) return
        val from = _chattingAddressScan.value.scannedCount
        _chattingAddressScan.value = _chattingAddressScan.value.copy(isScanning = true, failed = false)
        viewModelScope.launch {
            val batch = try {
                walletService.scanChattingAddressCandidates(from until (from + batchSize))
            } catch (e: Exception) {
                android.util.Log.w("WalletViewModel", "Chatting-address scan failed", e)
                null
            }
            _chattingAddressScan.value = if (batch == null) {
                _chattingAddressScan.value.copy(isScanning = false, failed = true)
            } else {
                _chattingAddressScan.value.copy(
                    candidates = _chattingAddressScan.value.candidates + batch,
                    scannedCount = from + batchSize,
                    isScanning = false,
                    failed = false
                )
            }
        }
    }

    fun resetChattingAddressScan() {
        _chattingAddressScan.value = ChattingAddressScanState()
    }

    /**
     * Makes the scanned address at [index] this account's chatting identity. Routes through the
     * normal account-switch machinery (see [WalletManager.switchChattingAddress]) and then
     * refreshes every piece of derived state a real account switch would: address, name, saved
     * accounts, balance, spending address, and push registration for the new identity.
     */
    fun switchChattingAddress(index: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val previousAddress = _address.value
                val newAddress = walletService.switchChattingAddress(index)
                // the same account, now at another address: its backup status goes with it
                if (previousAddress != null && previousAddress != newAddress) {
                    accountBackupStatus.moveStatus(previousAddress, newAddress)
                }
                _address.value = newAddress
                _accountName.value = walletManager.getAccountName()
                _accounts.value = walletManager.getAllAccounts()
                refreshBalance()
                refreshSpendingAddress()
                resetChattingAddressScan()
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("WalletViewModel", "Chatting-address switch failed", e)
                onResult(false)
            }
        }
    }

    /** BIP39 English wordlist (2048 words) for the in-app import keyboard's autocomplete. */
    val bip39Words: List<String> by lazy { walletManager.bip39WordList() }

    /** True if [word] is an exact BIP39 English word. */
    fun isBip39Word(word: String): Boolean = walletManager.isValidMnemonicWord(word)

    /** Drops every in-memory copy of a generated, uncommitted seed (audit AND-031). */
    fun clearMnemonic() {
        _mnemonic.value = null
        pendingMnemonicWords = emptyList()
    }

    /**
     * Sends Kaspa to a given address (from the identity address). [manualUtxos] threads through
     * coin control the same way [withdrawFromSpendingAddress] already does for spending-chain
     * addresses - see [SpendingAddressSendFlow]'s shared coin-control UI.
     */
    fun onSendClicked(
        sendId: String,
        address: String,
        amountSompi: Long,
        feeRateOverride: Long? = null,
        manualUtxos: List<UtxoEntry>? = null,
        /** Flat extra over the base fee, as in [withdrawFromSpendingAddress]. */
        extraFeeSompi: Long = 0L,
        /** As in [withdrawFromSpendingAddress]. */
        expectedFeeSompi: Long? = null,
    ) {
        sends.start(sendId)
        viewModelScope.launch {
            // The account that asked, before anything waits: a switch during the wait below must
            // not move this send onto another account's coins (iOS da59726).
            val (from, key) = try {
                walletManager.getAddress() to walletManager.getPrivateKeyBytes()
            } catch (e: Exception) {
                sends.finish(sendId, Result.failure(e))
                return@launch
            }
            // A "Not confirmed" send to this recipient is settled first (iOS da59726).
            walletEngine.unresolvedSendRefusal(address)?.let { refusal ->
                sends.finish(sendId, Result.failure(refusal))
                return@launch
            }
            // the chatting address's own rule: confirmed coins, no mining rewards (iOS 1751d20)
            val result = walletEngine.sendKaspa(address, amountSompi, fromAddress = from, signingPrivateKey = key, feeRateOverride = feeRateOverride, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi, expectedFeeSompi = expectedFeeSompi, withdrawal = true, holdUnknownOutcome = true)
            // Paying B from the chatting address watches B from now on and files the send in the
            // chat with B, as iOS's payment sync does for any outgoing transfer (iOS 1eb4708,
            // NO_HANDSHAKE_MESSAGING.md section 4). Before the send is reported done, so the
            // chat is there when the sheet closes.
            result.getOrNull()?.let { txId ->
                runCatching { chatRepository.recordPlainSendFromChattingAddress(address, txId, amountSompi) }
                    .onFailure { android.util.Log.w("WalletViewModel", "Could not record the send in a chat", it) }
            }
            sends.finish(sendId, result)

            if (result.isSuccess) {
                refreshBalance()
            }
        }
    }


    fun getActiveMnemonic(): String? = walletManager.getActiveMnemonic()
    fun getActivePassphrase(): String? = walletManager.getActivePassphrase()
    fun getPrivateKeyHex(): String = walletManager.getPrivateKeyHex()

    /** Hex-encoded private key for one spending-chain address — powers the per-address "Export" screen. */
    fun getSpendingPrivateKeyHex(index: Int): String = walletManager.getSpendingPrivateKeyHex(index)

    fun getSpendingUtxoLabels(address: String): Map<String, String> = walletManager.getSpendingUtxoLabels(address)

    fun setSpendingUtxoLabel(address: String, outpointKey: String, label: String?) {
        walletManager.setSpendingUtxoLabel(address, outpointKey, label)
    }

    /** Forward KNS domain resolution for any recipient-address field (Withdraw dialogs, the
     *  spending-address send flow) - lets typing "name.kas" resolve to a Kaspa address the same
     *  way Create Chat's own address field already does. */
    /** What a typed name points to on every service, .kachat first - the primary answer and the
     *  "Other domains" (iOS 6ac48a7). */
    suspend fun resolveEverywhere(input: String): List<com.kachat.app.services.NameResolution> = try {
        nameServices.resolveEverywhere(input)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e // a lookup that was cancelled didn't find nothing
    } catch (_: Exception) {
        emptyList()
    }

    /** Epoch millis this account was first added to this device, or null for one added before
     *  that started being recorded. See [WalletManager.accountAddedAt]. */
    fun accountAddedAt(address: String): Long? = walletManager.accountAddedAt(address)

    // -------------------------------------------------------------------------
    // KNS domain inscription — real on-chain commit/reveal, see WalletService.inscribeDomain
    // -------------------------------------------------------------------------

    private val _ownedDomainAssets = MutableStateFlow<List<com.kachat.app.services.KnsAsset>>(emptyList())
    val ownedDomainAssets: StateFlow<List<com.kachat.app.services.KnsAsset>> = _ownedDomainAssets.asStateFlow()
    val ownedDomains: StateFlow<List<String>> = _ownedDomainAssets
        .map { assets -> assets.mapNotNull { it.asset } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _primaryDomainName = MutableStateFlow<String?>(null)
    /** The wallet's explicitly-set primary domain name, or null if none has ever been set. */
    val primaryDomainName: StateFlow<String?> = _primaryDomainName.asStateFlow()

    /** The domain KNS Profile fields attach to — the explicit primary domain if still owned, else the first owned domain. */
    val activeProfileDomainName: StateFlow<String?> = combine(_ownedDomainAssets, _primaryDomainName) { assets, primary ->
        KnsService.pickActiveDomain(assets.mapNotNull { it.asset }, null, primary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** [activeProfileDomainName]'s assetId — the target of every profile read/write call. */
    val profileDomainAssetId: StateFlow<String?> = combine(_ownedDomainAssets, activeProfileDomainName) { assets, activeName ->
        assets.firstOrNull { it.asset == activeName }?.assetId ?: assets.firstOrNull()?.assetId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Full refresh: domains + primary + the active domain's profile (the "active" domain can
     * change as a result of setting a new primary, transferring one away, or inscribing a new
     * one, so all three of those call this - not [refreshOwnedDomainsAndAwait]). */
    fun refreshOwnedDomains() {
        viewModelScope.launch {
            refreshOwnedDomainsAndAwait()
            refreshKnsProfile()
        }
    }

    /** Domains + primary only, no profile fetch - used by the Domains screen's pull-to-refresh,
     * which never displays `knsProfile`, so fetching it on every pull was a wasted network call. */
    suspend fun refreshOwnedDomainsAndAwait() {
        // The WalletManager flow, not this ViewModel's _address mirror: it's the authority and is
        // updated first on every account create/import/switch, so a refresh fired during the
        // switch window can never fetch (and then publish) the OLD account's domains.
        val currentAddress = walletManager.activeAddressFlow.value ?: return
        val domains = knsService.getOwnedDomains(currentAddress)
        val primary = knsService.getExplicitPrimaryDomain(currentAddress)
        // Account switched while the fetch was in flight — these results belong to the previous
        // account and must not be published under the new one.
        if (walletManager.activeAddressFlow.value != currentAddress) return
        _ownedDomainAssets.value = domains
        _primaryDomainName.value = primary
    }

    data class SetPrimaryDomainUiState(val assetId: String? = null, val inFlight: Boolean = false, val errorMessage: String? = null)

    private val _setPrimaryState = MutableStateFlow(SetPrimaryDomainUiState())
    val setPrimaryState: StateFlow<SetPrimaryDomainUiState> = _setPrimaryState.asStateFlow()

    /** Marks a domain as primary — off-chain and free, blocked while another set-primary call is already in flight. */
    fun setPrimaryDomain(assetId: String) {
        if (_setPrimaryState.value.inFlight) return
        viewModelScope.launch {
            _setPrimaryState.value = SetPrimaryDomainUiState(assetId = assetId, inFlight = true)
            try {
                walletService.setPrimaryDomain(assetId)
                // The star moves to this domain the moment the write lands (iOS 1751d20
                // `knsPrimaryDomain = domain.fullName`), before the settle refresh.
                val name = _ownedDomainAssets.value.firstOrNull { it.assetId == assetId }?.asset
                if (name != null) _primaryDomainName.value = name
                refreshUntilPrimarySettles(assetId)
                _setPrimaryState.value = SetPrimaryDomainUiState()
                // No message on the page: the moved star is the confirmation; the toast names it
                // (iOS 1751d20 "Primary domain set to %@.").
                if (name != null) com.kachat.app.ui.screens.IosToasts.show("Primary domain set to $name.")
            } catch (e: Exception) {
                _setPrimaryState.value = SetPrimaryDomainUiState(assetId = assetId, inFlight = false, errorMessage = UserFacingError.message(e, "Failed to set primary domain"))
            }
        }
    }

    /**
     * Refreshes until KNS actually reports the new primary, or the attempts run out.
     *
     * A KNS profile belongs to a DOMAIN, so the avatar, banner and details all change with the
     * primary - but the write has only just been submitted, and one immediate refresh often races
     * the indexer and reads back the OLD primary, leaving the editor showing the previous domain's
     * profile. Bounded, and it keeps whatever the last refresh produced either way, so a slow
     * indexer costs a stale screen rather than a hang. Matches iOS.
     */
    private suspend fun refreshUntilPrimarySettles(assetId: String) {
        val expected = _ownedDomainAssets.value.firstOrNull { it.assetId == assetId }?.asset
        repeat(3) { attempt ->
            if (attempt > 0) kotlinx.coroutines.delay(2_000)
            refreshOwnedDomainsAndAwait()
            refreshKnsProfile()
            if (expected == null || _primaryDomainName.value.equals(expected, ignoreCase = true)) return
        }
    }

    fun clearSetPrimaryError() {
        _setPrimaryState.value = SetPrimaryDomainUiState()
    }

    // -------------------------------------------------------------------------
    // Transfer domain — irreversible, so the recipient is resolved and validated live as the
    // user types (matching a ".kas" name to an address, checking it's a real/different/
    // same-network address) BEFORE the Transfer screen ever lets them confirm — iOS shows no
    // resolved-address preview at all before submitting, this is deliberately stricter.
    // -------------------------------------------------------------------------

    data class TransferRecipientPreview(
        val input: String,
        val checking: Boolean = false,
        val resolvedAddress: String? = null,
        val errorMessage: String? = null,
        /** The name it resolved as, and every service's answer for "Other domains" (iOS 6ac48a7). */
        val resolvedName: String? = null,
        val nameResolutions: List<com.kachat.app.services.NameResolution> = emptyList(),
        val selectedTld: com.kachat.app.services.NameServiceTLD? = null,
    )

    private val _transferRecipientPreview = MutableStateFlow<TransferRecipientPreview?>(null)
    val transferRecipientPreview: StateFlow<TransferRecipientPreview?> = _transferRecipientPreview.asStateFlow()

    private var transferPreviewJob: Job? = null

    /** Debounced: resolves a ".kas" name to an address (or validates a raw address directly), then
     *  checks it's a real, different, same-network address. [sourceAddress] overrides which own
     *  address the recipient must differ from — the spending-address domain-transfer flow passes
     *  that address (recipient must differ from the SOURCE, matching iOS); null = the identity. */
    fun checkTransferRecipient(rawInput: String, sourceAddress: String? = null) {
        transferPreviewJob?.cancel()
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            _transferRecipientPreview.value = null
            return
        }
        // At once, not after the debounce: the old recipient must not stand for a new input (iOS
        // da59726 `handleInputChange`).
        _transferRecipientPreview.value = TransferRecipientPreview(input = trimmed, checking = true)
        transferPreviewJob = viewModelScope.launch {
            delay(350)
            // Every name service, in priority: the ending typed, else .kachat only (iOS 5a5122d) - the same lookup as every other address field; the others are
            // offered under "Other domains" (iOS 6ac48a7).
            val isName = !KaspaAddress.isValid(trimmed) && com.kachat.app.services.NameServicesClient.looksLikeName(trimmed)
            val results = if (isName) resolveEverywhere(trimmed) else emptyList()
            val primary = if (isName) com.kachat.app.services.NameServicesClient.primary(results, trimmed)?.takeIf { it.address != null } else null
            if (isName && primary == null) {
                val message = com.kachat.app.services.NameServicesClient.notFoundMessage(appContext, trimmed, results)
                _transferRecipientPreview.value = TransferRecipientPreview(input = trimmed, checking = false, errorMessage = message, nameResolutions = results)
                return@launch
            }
            _transferRecipientPreview.value = validatedTransferRecipient(trimmed, primary?.address ?: trimmed, primary, results, sourceAddress)
        }
    }

    /** Picks another service's answer for the typed name (under "Other domains"): it goes through
     *  the same checks as the priority one. */
    fun selectTransferResolution(resolution: com.kachat.app.services.NameResolution, sourceAddress: String? = null) {
        val current = _transferRecipientPreview.value ?: return
        val address = resolution.address ?: return
        transferPreviewJob?.cancel()
        _transferRecipientPreview.value = validatedTransferRecipient(current.input, address, resolution, current.nameResolutions, sourceAddress)
    }

    /** A real, different, same-network address - else the reason, with the name lookup kept. */
    private fun validatedTransferRecipient(
        input: String,
        resolved: String,
        resolution: com.kachat.app.services.NameResolution?,
        results: List<com.kachat.app.services.NameResolution>,
        sourceAddress: String?,
    ): TransferRecipientPreview {
        val myAddress = sourceAddress ?: address.value
        return try {
            // iOS 1751d20 `KaspaAddress.validityText`: "Invalid address format".
            if (!KaspaAddress.isValid(resolved)) throw IllegalStateException(appContext.getString(com.kachat.app.R.string.invalid_address_format))
            if (resolved == myAddress) throw IllegalStateException("Recipient must be different from your own wallet")
            // The other network's address is the same key on another chain (iOS ce20e87).
            KaspaAddress.otherNetworkMessageRes(resolved)?.let { throw IllegalStateException(appContext.getString(it)) }
            if (myAddress != null && resolved.substringBefore(":") != myAddress.substringBefore(":")) {
                throw IllegalStateException("Recipient address is on the wrong network")
            }
            TransferRecipientPreview(
                input = input, checking = false, resolvedAddress = resolved,
                resolvedName = resolution?.display, nameResolutions = results, selectedTld = resolution?.tld,
            )
        } catch (e: Exception) {
            TransferRecipientPreview(
                input = input, checking = false, errorMessage = UserFacingError.message(e, "Invalid recipient"),
                nameResolutions = results, selectedTld = resolution?.tld,
            )
        }
    }

    fun clearTransferRecipientPreview() {
        transferPreviewJob?.cancel()
        _transferRecipientPreview.value = null
    }

    data class TransferDomainUiState(
        val status: KnsInscribeUiStatus = KnsInscribeUiStatus.IDLE,
        val errorMessage: String? = null,
        val result: WalletService.TransferDomainResult? = null
    )

    private val _transferDomainState = MutableStateFlow(TransferDomainUiState())
    val transferDomainState: StateFlow<TransferDomainUiState> = _transferDomainState.asStateFlow()

    /** Submits the real, irreversible on-chain transfer — only proceeds using the already-resolved+validated recipient address, never the raw typed input.
     *  [fromSpendingAddressIndex] non-null signs/funds from that spending-chain address instead of the identity (see WalletService.transferDomain). */
    fun transferDomain(fullDomain: String, assetId: String, priorityFeeSompi: Long = KnsInscriptionEngine.REVEAL_PRIORITY_FEE_SOMPI, fromSpendingAddressIndex: Int? = null) {
        val resolvedAddress = _transferRecipientPreview.value?.resolvedAddress ?: return
        val current = _transferDomainState.value.status
        if (current != KnsInscribeUiStatus.IDLE && current != KnsInscribeUiStatus.SUCCESS && current != KnsInscribeUiStatus.FAILED) return

        viewModelScope.launch {
            // "Preparing transfer" first (iOS 1751d20 `TransferStage.preparing`).
            _transferDomainState.value = TransferDomainUiState(status = KnsInscribeUiStatus.CHECKING_AVAILABILITY)
            try {
                val result = walletService.transferDomain(fullDomain, assetId, resolvedAddress, priorityFeeSompi, fromSpendingAddressIndex) { step ->
                    _transferDomainState.value = _transferDomainState.value.copy(status = step.toUiStatus())
                }
                _transferDomainState.value = TransferDomainUiState(status = KnsInscribeUiStatus.SUCCESS, result = result)
                refreshOwnedDomains()
            } catch (e: Exception) {
                _transferDomainState.value = TransferDomainUiState(status = KnsInscribeUiStatus.FAILED, errorMessage = UserFacingError.message(e, "Transfer failed"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-address KNS domains ("KNS Domains (n)" tab on a spending address's detail screen) and
    // the "Contains domain" tags on the Manage Addresses list.
    // -------------------------------------------------------------------------

    private val _addressKnsDomains = MutableStateFlow<List<com.kachat.app.services.KnsAsset>>(emptyList())
    val addressKnsDomains: StateFlow<List<com.kachat.app.services.KnsAsset>> = _addressKnsDomains.asStateFlow()
    private val _addressKnsDomainsLoading = MutableStateFlow(false)
    val addressKnsDomainsLoading: StateFlow<Boolean> = _addressKnsDomainsLoading.asStateFlow()

    fun loadAddressKnsDomains(address: String) {
        viewModelScope.launch {
            _addressKnsDomainsLoading.value = true
            _addressKnsDomains.value = try { knsService.getOwnedDomains(address) } catch (e: Exception) { emptyList() }
            _addressKnsDomainsLoading.value = false
        }
    }

    /** Addresses in the Manage Addresses list that own at least one KNS domain (or, where it is
     *  live, a .kachat name - iOS 881ada6) — batched cached
     *  lookups fired AFTER the rows are already visible, so tags fill in without blocking. */
    /** The registry's revision where it's live, so Profile's Your Domains count reloads when a
     *  registration, sale or transfer lands; a flat 0 where it isn't launched (iOS 10e4a1a). */
    val kachatRegistryRevision: StateFlow<Int> =
        if (com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) kachatRegistry.get().revision
        else MutableStateFlow(0)

    /**
     * The account's .kachat names for Profile's Your Domains count - the same set its .kachat tab
     * lists (active and in grace, registry heldNames; a lapsed one isn't yours any more). [update]
     * gets the count, then again whenever one of them lapses while Profile is open. 0 where the
     * registry isn't launched; nothing when the lookup failed, so the caller keeps what it had
     * (iOS 10e4a1a, aa36d2a loadKachatOwnedCount).
     */
    suspend fun watchKachatOwnedCount(address: String, update: (Int) -> Unit) {
        if (!com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) { update(0); return }
        val key = com.kachat.app.services.kachatnames.KachatNamesRegistry.keyOf(address) ?: run { update(0); return }
        val registry = kachatRegistry.get()
        val names = try {
            registry.refreshIfStale(maxAgeMs = 300_000)
            registry.heldNames(key)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        update(names.size)
        // and one that lapses while Profile is open comes off the count right then
        registry.dropLapsed(names) { update(it.size) }
    }

    private val _domainOwningAddresses = MutableStateFlow<Set<String>>(emptySet())
    val domainOwningAddresses: StateFlow<Set<String>> = _domainOwningAddresses.asStateFlow()

    private fun refreshDomainOwningAddresses(addresses: List<String>) {
        if (addresses.isEmpty()) return
        viewModelScope.launch {
            val kns = try { knsService.domainOwningAddresses(addresses) } catch (e: Exception) { emptySet() }
            _domainOwningAddresses.value = kns
            // .kachat names count too, where the registry is live (testnet) - iOS 881ada6.
            if (com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
                val kachat = try {
                    kachatRegistry.get().ownersOfNames(addresses)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptySet()
                }
                if (kachat.isNotEmpty()) _domainOwningAddresses.value = kns + kachat
            }
        }
    }

    fun resetTransferDomainState() {
        _transferDomainState.value = TransferDomainUiState()
        clearTransferRecipientPreview()
    }

    private val _knsProfile = MutableStateFlow<KnsProfileFields?>(null)
    val knsProfile: StateFlow<KnsProfileFields?> = _knsProfile.asStateFlow()

    fun refreshKnsProfile() {
        viewModelScope.launch {
            val fetchedForAddress = walletManager.activeAddressFlow.value
            val assets = _ownedDomainAssets.value
            val activeName = KnsService.pickActiveDomain(assets.mapNotNull { it.asset }, null, _primaryDomainName.value)
            val assetId = assets.firstOrNull { it.asset == activeName }?.assetId ?: assets.firstOrNull()?.assetId
            if (assetId == null) {
                // No domain, no profile. Clearing (instead of the old early-return that left the
                // previous value standing) is what blanks the profile card when the active account
                // has no KNS identity — the early return let a prior account's banner/avatar/bio
                // survive an account switch forever.
                _knsProfile.value = null
                return@launch
            }
            val profile = knsService.getProfile(assetId)
            // Don't publish a fetch that raced an account switch (same guard as refreshOwnedDomainsAndAwait).
            if (walletManager.activeAddressFlow.value != fetchedForAddress) return@launch
            _knsProfile.value = profile
        }
    }

    // -------------------------------------------------------------------------
    // Edit KNS Profile screen — avatar/banner staging + a single save-all that uploads
    // changed images and submits only changed text fields, each its own real transaction.
    // -------------------------------------------------------------------------

    enum class EditProfileStep { IDLE, UPLOADING_AVATAR, UPLOADING_BANNER, SUBMITTING_FIELD, SUCCESS, PARTIAL_FAILURE, FAILED }

    data class EditProfileFieldResult(val fieldKey: String, val success: Boolean, val errorMessage: String? = null)

    data class EditProfileUiState(
        val step: EditProfileStep = EditProfileStep.IDLE,
        val currentFieldLabel: String? = null,
        val fieldResults: List<EditProfileFieldResult> = emptyList(),
        val errorMessage: String? = null
    )

    private val _editProfileState = MutableStateFlow(EditProfileUiState())
    val editProfileState: StateFlow<EditProfileUiState> = _editProfileState.asStateFlow()

    private val _pendingAvatarUri = MutableStateFlow<Uri?>(null)
    val pendingAvatarUri: StateFlow<Uri?> = _pendingAvatarUri.asStateFlow()

    private val _pendingBannerUri = MutableStateFlow<Uri?>(null)
    val pendingBannerUri: StateFlow<Uri?> = _pendingBannerUri.asStateFlow()

    /** True once the user taps "Remove" on an existing (already on-chain) avatar/banner - distinct
     * from simply having no pending pick, since it means Save should explicitly clear the field
     * rather than leave it untouched. Reset by picking a new image or leaving the screen. */
    private val _avatarCleared = MutableStateFlow(false)
    val avatarCleared: StateFlow<Boolean> = _avatarCleared.asStateFlow()

    private val _bannerCleared = MutableStateFlow(false)
    val bannerCleared: StateFlow<Boolean> = _bannerCleared.asStateFlow()

    fun setPendingAvatar(uri: Uri?) {
        _pendingAvatarUri.value = uri
        if (uri != null) _avatarCleared.value = false
    }

    fun setPendingBanner(uri: Uri?) {
        _pendingBannerUri.value = uri
        if (uri != null) _bannerCleared.value = false
    }

    /**
     * Drops any staged avatar/banner pick and the "remove existing" flags.
     *
     * Called when the profile being edited changes domain: those images were chosen for the
     * PREVIOUS domain's profile, and carrying them across silently would save the wrong ones.
     */
    fun clearPendingProfileImages() {
        _pendingAvatarUri.value = null
        _pendingBannerUri.value = null
        _avatarCleared.value = false
        _bannerCleared.value = false
    }

    fun clearExistingAvatar() {
        _pendingAvatarUri.value = null
        _avatarCleared.value = true
    }

    fun clearExistingBanner() {
        _pendingBannerUri.value = null
        _bannerCleared.value = true
    }

    fun resetEditProfileState() {
        _editProfileState.value = EditProfileUiState()
        _pendingAvatarUri.value = null
        _pendingBannerUri.value = null
        _avatarCleared.value = false
        _bannerCleared.value = false
    }

    /**
     * Uploads any newly-picked avatar/banner, then submits only the text fields that actually
     * changed from [knsProfile]'s current values — each image/field is still its own real
     * commit/reveal transaction (~2/1 KAS), matching iOS's `saveKNSProfile` order exactly:
     * avatar first, then banner, then changed text fields. Reports a partial-failure state
     * rather than silently swallowing individual failures if some succeed and others don't.
     */
    // A .kas domain's own profile, edited from Your Domains (iOS 09e0403). .kas profiles are no
    // longer shown in the app (KnsService.LOADS_DOMAIN_PROFILES), so the domain's profile is
    // fetched on its own here - not cached, not shown anywhere else.
    private val _editingDomain = MutableStateFlow<com.kachat.app.services.KnsAsset?>(null)
    val editingDomain: StateFlow<com.kachat.app.services.KnsAsset?> = _editingDomain.asStateFlow()
    private val _editingDomainProfile = MutableStateFlow<com.kachat.app.services.KnsProfileFields?>(null)
    val editingDomainProfile: StateFlow<com.kachat.app.services.KnsProfileFields?> = _editingDomainProfile.asStateFlow()
    private val _editingDomainLoading = MutableStateFlow(false)
    val editingDomainLoading: StateFlow<Boolean> = _editingDomainLoading.asStateFlow()

    fun beginEditingDomain(domain: com.kachat.app.services.KnsAsset) {
        _editingDomain.value = domain
        _editingDomainProfile.value = null
        clearPendingProfileImages()
        val assetId = domain.assetId ?: return
        _editingDomainLoading.value = true
        viewModelScope.launch {
            val profile = knsService.getProfileForEditing(assetId)
            if (_editingDomain.value?.assetId == assetId) _editingDomainProfile.value = profile
            _editingDomainLoading.value = false
        }
    }

    fun endEditingDomain() {
        _editingDomain.value = null
        _editingDomainProfile.value = null
        _editingDomainLoading.value = false
    }

    /**
     * What Confirm hands the save, read the moment it is tapped (iOS 1751d20
     * `KNSProfileEditorSubmission`): the editor closes before the device lock and the save, and
     * closing it drops the staged picks and the domain being edited.
     */
    data class KnsProfileSubmission(
        val assetId: String,
        val currentProfile: KnsProfileFields?,
        val avatarUri: Uri?,
        val bannerUri: Uri?,
        val avatarCleared: Boolean,
        val bannerCleared: Boolean,
        val textFields: Map<String, String>,
    )

    /** The editor's fields and staged images as a [KnsProfileSubmission]; null without a domain. */
    fun knsProfileSubmission(textFields: Map<String, String>): KnsProfileSubmission? {
        val editing = _editingDomain.value
        val assetId = editing?.assetId ?: profileDomainAssetId.value ?: return null
        return KnsProfileSubmission(
            assetId = assetId,
            currentProfile = if (editing != null) _editingDomainProfile.value else _knsProfile.value,
            avatarUri = _pendingAvatarUri.value,
            bannerUri = _pendingBannerUri.value,
            avatarCleared = _avatarCleared.value,
            bannerCleared = _bannerCleared.value,
            textFields = textFields,
        )
    }

    /**
     * The Wallet tab's save pill (iOS 1751d20 `isSavingKNSProfile`, `knsSaveProgressText` and
     * `failedKNSUpdates`): the step while a save runs ("Preparing profile update...", then
     * "Updating X (n/m)..."), then the fields that failed (key to value) for Retry.
     */
    data class KnsProfileSaveStatus(
        val progressText: String? = null,
        val failed: Map<String, String> = emptyMap(),
        val assetId: String? = null,
    ) {
        val isSaving: Boolean get() = progressText != null
    }

    private val _knsSaveStatus = MutableStateFlow(KnsProfileSaveStatus())
    val knsSaveStatus: StateFlow<KnsProfileSaveStatus> = _knsSaveStatus.asStateFlow()

    /** iOS 1751d20 `KNSProfileFieldKey.displayName`. */
    private fun knsFieldDisplayName(fieldKey: String): String = when (fieldKey) {
        "redirectUrl" -> "Redirect"
        "avatarUrl" -> "Avatar"
        "bannerUrl" -> "Banner"
        "bio" -> "Bio"
        "x" -> "X"
        "website" -> "Website"
        "telegram" -> "Telegram"
        "discord" -> "Discord"
        "contactEmail" -> "Email"
        "github" -> "GitHub"
        else -> fieldKey
    }

    /**
     * Saves a profile in the background, the editor already closed (iOS 1751d20
     * `saveKNSProfile`): the picked images uploaded first, then every changed field written in
     * iOS's order, each its own commit/reveal; the pill on the Wallet tab says where it is, and a
     * toast how it ended. Fields that fail stay in [knsSaveStatus] for Retry.
     */
    fun saveKnsProfile(submission: KnsProfileSubmission) {
        if (_knsSaveStatus.value.isSaving) return
        val assetId = submission.assetId
        _knsSaveStatus.value = KnsProfileSaveStatus(progressText = "Preparing profile update...", assetId = assetId)
        viewModelScope.launch {
            try {
                val values = submission.textFields.mapValues { it.value.trim() }.toMutableMap()
                val original = submission.currentProfile
                val avatarUri: Uri? = submission.avatarUri
                val bannerUri: Uri? = submission.bannerUri
                // Upload picked avatar/banner first; the resulting URLs are written on-chain.
                values["avatarUrl"] = if (avatarUri != null) {
                    _knsSaveStatus.value = _knsSaveStatus.value.copy(progressText = "Uploading avatar...")
                    val bytes = withContext(Dispatchers.Default) { ImagePrep.prepareForUpload(appContext, avatarUri) }
                    walletService.uploadKnsProfileImageUrl(assetId, "avatar", bytes)
                } else if (submission.avatarCleared) "" else (original?.avatarUrl ?: "").trim()
                values["bannerUrl"] = if (bannerUri != null) {
                    _knsSaveStatus.value = _knsSaveStatus.value.copy(progressText = "Uploading banner...")
                    val bytes = withContext(Dispatchers.Default) { ImagePrep.prepareForUpload(appContext, bannerUri) }
                    walletService.uploadKnsProfileImageUrl(assetId, "banner", bytes)
                } else if (submission.bannerCleared) "" else (original?.bannerUrl ?: "").trim()

                val changes = listOf(
                    "avatarUrl", "bannerUrl", "bio", "x", "website",
                    "telegram", "discord", "contactEmail", "github", "redirectUrl",
                ).mapNotNull { key ->
                    val target = values[key] ?: return@mapNotNull null
                    val current = (WalletService.fieldValue(original, key) ?: "").trim()
                    if (target != current) key to target else null
                }
                if (changes.isEmpty()) {
                    _knsSaveStatus.value = KnsProfileSaveStatus()
                    com.kachat.app.ui.screens.IosToasts.show("No KNS profile changes detected.")
                    return@launch
                }

                var successCount = 0
                val failedFields = mutableListOf<String>()
                val failedChanges = LinkedHashMap<String, String>()
                val failedMessages = mutableListOf<String>()
                changes.forEachIndexed { index, (key, value) ->
                    _knsSaveStatus.value = _knsSaveStatus.value.copy(
                        progressText = "Updating ${knsFieldDisplayName(key)} (${index + 1}/${changes.size})..."
                    )
                    try {
                        walletService.updateKnsProfileField(assetId, key, value)
                        successCount++
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedFields.add(knsFieldDisplayName(key))
                        failedChanges[key] = value
                        failedMessages.add(UserFacingError.message(e, ""))
                    }
                }

                refreshKnsProfile()
                _knsSaveStatus.value = KnsProfileSaveStatus(failed = failedChanges, assetId = assetId)
                val firstReason = failedMessages.firstOrNull { it.isNotEmpty() } ?: ""
                when {
                    successCount == changes.size ->
                        com.kachat.app.ui.screens.IosToasts.show("KNS profile updated.")
                    successCount > 0 -> {
                        val failedList = failedFields.joinToString(", ")
                        com.kachat.app.ui.screens.IosToasts.error(
                            if (firstReason.isEmpty()) "Updated $successCount/${changes.size}. Failed: $failedList."
                            else "Updated $successCount/${changes.size}. Failed: $failedList. $firstReason"
                        )
                    }
                    else -> com.kachat.app.ui.screens.IosToasts.error(
                        if (firstReason.isEmpty()) "KNS profile update failed." else "KNS profile update failed: $firstReason"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _knsSaveStatus.value = KnsProfileSaveStatus()
                com.kachat.app.ui.screens.IosToasts.error("KNS profile update failed: ${UserFacingError.message(e, "")}")
            }
        }
    }

    /** The pill's Retry (iOS 1751d20 `retryAllFailedKNSUpdates`): each failed field again, in
     *  turn, "Retrying X..." in the pill; one that lands leaves the list. */
    fun retryFailedKnsUpdates() {
        val status = _knsSaveStatus.value
        val assetId = status.assetId ?: return
        if (status.isSaving || status.failed.isEmpty()) return
        val pending = status.failed
        viewModelScope.launch {
            for ((key, value) in pending) {
                if (_knsSaveStatus.value.isSaving) continue
                val name = knsFieldDisplayName(key)
                _knsSaveStatus.value = _knsSaveStatus.value.copy(progressText = "Retrying $name...")
                try {
                    walletService.updateKnsProfileField(assetId, key, value)
                    refreshKnsProfile()
                    _knsSaveStatus.value = _knsSaveStatus.value.let { it.copy(progressText = null, failed = it.failed - key) }
                    com.kachat.app.ui.screens.IosToasts.show("$name updated.")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _knsSaveStatus.value = _knsSaveStatus.value.copy(progressText = null)
                    com.kachat.app.ui.screens.IosToasts.error("Retry failed: ${UserFacingError.message(e, "")}")
                }
            }
        }
    }

    /** The pill's close button: the failed list dropped. */
    fun dismissFailedKnsUpdates() {
        _knsSaveStatus.value = _knsSaveStatus.value.copy(failed = emptyMap())
    }

    enum class KnsInscribeUiStatus { IDLE, CHECKING_AVAILABILITY, FETCHING_FEE, SUBMITTING_COMMIT, WAITING_FOR_COMMIT, SUBMITTING_REVEAL, VERIFYING, SUCCESS, FAILED }

    data class KnsInscribeUiState(
        val status: KnsInscribeUiStatus = KnsInscribeUiStatus.IDLE,
        val errorMessage: String? = null,
        val result: WalletService.DomainInscribeResult? = null
    )

    data class DomainAvailabilityPreview(
        val label: String,
        val checking: Boolean = false,
        val available: Boolean? = null,
        val isReserved: Boolean = false,
        val revealKas: Double? = null,
        val commitKas: Double? = null,
        val errorMessage: String? = null
    )

    private val _knsInscribeState = MutableStateFlow(KnsInscribeUiState())
    val knsInscribeState: StateFlow<KnsInscribeUiState> = _knsInscribeState.asStateFlow()

    private val _domainPreview = MutableStateFlow<DomainAvailabilityPreview?>(null)
    val domainPreview: StateFlow<DomainAvailabilityPreview?> = _domainPreview.asStateFlow()

    val pendingKnsCommit: StateFlow<PendingKnsCommit?> = settings.pendingKnsCommit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Route strings, in the user's chosen bottom-tab order — see AppSettingsRepository.tabOrder. */
    val tabOrder: StateFlow<List<String>> = settings.tabOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AppSettingsRepository.DEFAULT_TAB_ORDER)

    fun updateKaspaExplorer(explorer: com.kachat.app.models.KaspaExplorer) {
        viewModelScope.launch { settings.setKaspaExplorer(explorer) }
    }

    val kaspaExplorer: StateFlow<com.kachat.app.models.KaspaExplorer> = settings.kaspaExplorer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), com.kachat.app.models.KaspaExplorer.default)

    fun setTabOrder(routes: List<String>) {
        viewModelScope.launch { settings.setTabOrder(routes) }
    }

    /** Bottom-tab routes the user has hidden from the nav bar via Settings > Customization > Menu. */
    /** Routes in the dock, in order — the user's placement (see AppSettingsRepository.dockTabs). */
    val dockTabs: StateFlow<List<String>> = settings.dockTabs
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettingsRepository.DEFAULT_DOCK_TABS)

    /** Routes in Kaspa Hub, in the order its grid shows them. */
    val hubTabs: StateFlow<List<String>> = settings.hubTabs
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettingsRepository.DEFAULT_HUB_TABS)

    /** Both lists are written together so a tab can never be in both or neither. */
    fun setPlacement(dock: List<String>, hub: List<String>) {
        viewModelScope.launch { settings.setPlacement(dock, hub) }
    }

    val hiddenTabs: StateFlow<Set<String>> = settings.hiddenTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    fun setTabHidden(route: String, hidden: Boolean) {
        viewModelScope.launch { settings.setTabHidden(route, hidden) }
    }

    /** Settings > Customization > Appearance (iOS AppAppearance) - System by default. */
    val appearance: StateFlow<com.kachat.app.models.AppAppearance> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), com.kachat.app.models.AppAppearance.SYSTEM)

    fun setAppearance(appearance: com.kachat.app.models.AppAppearance) {
        viewModelScope.launch { settings.setAppearance(appearance) }
    }

    /** Settings > Customization > Currency — lowercase ISO 4217 code (e.g. "usd", "eur"). Global,
     *  not per-account (see [AppSettingsRepository.currency]). */
    val currency: StateFlow<String> = settings.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "usd")

    fun setCurrency(code: String) {
        viewModelScope.launch { settings.setCurrency(code) }
    }

    /** Settings > Security — whether viewing the seed phrase requires device authentication first. */
    val biometricSeedPhraseEnabled: StateFlow<Boolean> = settings.biometricSeedPhraseEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setBiometricSeedPhraseEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricSeedPhraseEnabled(enabled) }
    }

    /** Settings > Security — whether unlocking a saved account after logout requires device authentication first. */
    val biometricAccountLoginEnabled: StateFlow<Boolean> = settings.biometricAccountLoginEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setBiometricAccountLoginEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricAccountLoginEnabled(enabled) }
    }

    /** Settings > Security — whether the "Export" button on a spending address's own screen requires device authentication first. */
    val biometricSpendingKeyEnabled: StateFlow<Boolean> = settings.biometricSpendingKeyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setBiometricSpendingKeyEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricSpendingKeyEnabled(enabled) }
    }

    /** Settings > Security > Child Mode — the dock/deep-link/notification gates all derive from
     *  this at render time (see resolveTabOrder); the masked state is never written into the
     *  stored per-account dock prefs. */
    val childModeEnabled: StateFlow<Boolean> = settings.childModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    /** Race-free read for one-shot navigation decisions (deep links / notification taps) — the
     *  StateFlow above starts as `false` before DataStore loads, which a cold-start notification
     *  tap could otherwise slip through. */
    suspend fun isChildModeEnabled(): Boolean = settings.childModeEnabled.first()

    /** True while the Welcome Guide's "Who will use KaChat?" step is owed an answer (marker
     *  "pending") — MainShell re-presents the guide at that step on every launch until it is.
     *  null while DataStore loads (don't re-present on an unknown value). */
    val userTypePending: StateFlow<Boolean?> = settings.userTypeChoiceState
        .map { (it == com.kachat.app.repository.AppSettingsRepository.USER_TYPE_PENDING) as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Called right before the first-run guide auto-presents — never downgrades "chosen". */
    fun markUserTypePending() {
        viewModelScope.launch { settings.markUserTypePending() }
    }

    /** True while an auto-presented onboarding run (create or import) hasn't reached the wizard's
     *  Finish — MainShell re-presents the FULL guide at next launch until it does. null while
     *  DataStore loads (don't re-present on an unknown value). */
    val onboardingWizardPending: StateFlow<Boolean?> = settings.onboardingWizardPending
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Stamped when the guide auto-presents for an onboarding run. */
    fun markOnboardingWizardPending() {
        viewModelScope.launch { settings.markOnboardingWizardPending() }
    }

    /** Only the wizard's Finish button clears the marker. */
    fun clearOnboardingWizardPending() {
        viewModelScope.launch { settings.clearOnboardingWizardPending() }
    }

    /**
     * Settings > Customization — whether the "Setup Guide" re-entry points (the Profile screen's
     * "Welcome Guide" row, the "Edit KNS Profile" screen's "Setup Guide" section) are shown.
     * Scoped to the currently active account's address (see [AppSettingsRepository.showSetupGuides]),
     * so it re-derives whenever [address] changes rather than sticking to whichever account was
     * active when this StateFlow was first collected.
     */
    val showSetupGuides: StateFlow<Boolean> = address
        .flatMapLatest { addr -> if (addr != null) settings.showSetupGuides(addr) else flowOf(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setShowSetupGuides(enabled: Boolean) {
        val addr = _address.value ?: return
        viewModelScope.launch { settings.setShowSetupGuides(addr, enabled) }
    }

    private var previewJob: Job? = null

    /** Debounced live availability + fee-tier lookup as the user types a label — cancels any in-flight check for the previous label. */
    fun checkDomainLabel(rawLabel: String) {
        previewJob?.cancel()
        val label = KnsService.normalizeDomainLabel(rawLabel)
        if (label == null) {
            _domainPreview.value = null
            return
        }
        previewJob = viewModelScope.launch {
            delay(350)
            _domainPreview.value = DomainAvailabilityPreview(label = label, checking = true)
            try {
                val address = walletManager.getAddress()
                val availability = knsService.checkDomainAvailability(address, "$label.kas")
                if (!availability.available) {
                    _domainPreview.value = DomainAvailabilityPreview(label = label, checking = false, available = false)
                    return@launch
                }
                val feeTiers = knsService.fetchInscribeFeeTiers()
                val tier = KnsService.feeTierForLabel(label)
                val tierFee = KnsService.feeForTier(tier, feeTiers)
                val revealKas = KnsService.revealAmountKas(tierFee, availability.isReservedDomain)
                val commitKas = KnsService.commitAmountKas(revealKas)
                _domainPreview.value = DomainAvailabilityPreview(
                    label = label,
                    checking = false,
                    available = true,
                    isReserved = availability.isReservedDomain,
                    revealKas = revealKas,
                    commitKas = commitKas
                )
            } catch (e: Exception) {
                _domainPreview.value = DomainAvailabilityPreview(label = label, checking = false, errorMessage = UserFacingError.message(e, "Check failed"))
            }
        }
    }

    fun clearDomainPreview() {
        previewJob?.cancel()
        _domainPreview.value = null
    }

    /** Starts a real domain registration — blocked while one is already in flight, matching iOS's `guard !isSubmitting`. */
    fun inscribeDomain(label: String) {
        if (_knsInscribeState.value.status.let { it != KnsInscribeUiStatus.IDLE && it != KnsInscribeUiStatus.SUCCESS && it != KnsInscribeUiStatus.FAILED }) return
        viewModelScope.launch {
            _knsInscribeState.value = KnsInscribeUiState(status = KnsInscribeUiStatus.CHECKING_AVAILABILITY)
            try {
                val result = walletService.inscribeDomain(label) { step ->
                    _knsInscribeState.value = _knsInscribeState.value.copy(status = step.toUiStatus())
                }
                _knsInscribeState.value = KnsInscribeUiState(status = KnsInscribeUiStatus.SUCCESS, result = result)
                refreshOwnedDomains()
            } catch (e: Exception) {
                _knsInscribeState.value = KnsInscribeUiState(status = KnsInscribeUiStatus.FAILED, errorMessage = UserFacingError.message(e, "Inscription failed"))
            }
        }
    }

    fun resetKnsInscribeState() {
        _knsInscribeState.value = KnsInscribeUiState()
    }

    /** Retries just the reveal half of a commit that broadcast but never finished — see WalletService.retryPendingKnsReveal. */
    fun retryPendingKnsReveal() {
        val pending = pendingKnsCommit.value ?: return
        viewModelScope.launch {
            _knsInscribeState.value = KnsInscribeUiState(status = KnsInscribeUiStatus.SUBMITTING_REVEAL)
            try {
                walletService.retryPendingKnsReveal(pending)
                _knsInscribeState.value = KnsInscribeUiState(status = KnsInscribeUiStatus.SUCCESS)
            } catch (e: Exception) {
                _knsInscribeState.value = KnsInscribeUiState(status = KnsInscribeUiStatus.FAILED, errorMessage = UserFacingError.message(e, "Retry failed"))
            }
        }
    }

    private fun WalletService.KnsInscribeStep.toUiStatus(): KnsInscribeUiStatus = when (this) {
        WalletService.KnsInscribeStep.CHECKING_AVAILABILITY -> KnsInscribeUiStatus.CHECKING_AVAILABILITY
        WalletService.KnsInscribeStep.FETCHING_FEE -> KnsInscribeUiStatus.FETCHING_FEE
        WalletService.KnsInscribeStep.SUBMITTING_COMMIT -> KnsInscribeUiStatus.SUBMITTING_COMMIT
        WalletService.KnsInscribeStep.WAITING_FOR_COMMIT -> KnsInscribeUiStatus.WAITING_FOR_COMMIT
        WalletService.KnsInscribeStep.SUBMITTING_REVEAL -> KnsInscribeUiStatus.SUBMITTING_REVEAL
        WalletService.KnsInscribeStep.VERIFYING -> KnsInscribeUiStatus.VERIFYING
    }
}
