package com.kachat.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kachat.app.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long the pool must stay empty before the dot is allowed to turn red. */
private const val DISCONNECT_GRACE_MS = 8_000L

enum class ConnectionStatus {
    CONNECTED, DEGRADED, DISCONNECTED
}

/**
 * Pure threshold logic, extracted so it's directly unit-testable without needing
 * a real NodePoolManager/AppSettingsRepository — see ConnectionViewModel.status.
 * Same latency threshold as [deriveDotColorHex] (which derives from this) so the
 * "Status"/"Pool Health" text on screen can never say something that contradicts
 * the dot's color: red whenever there's no active node at all (regardless of
 * latency), otherwise green under 300ms and orange at 300ms or above.
 */
internal fun deriveConnectionStatus(
    activeNodes: List<NodeInfo>,
    /** Red is reserved for SUSTAINED disconnection: an empty pool during startup probing,
     *  failover, or a WiFi-cellular handoff reads DEGRADED (orange) until the emptiness has
     *  persisted past the grace window. Defaults true so direct calls keep the strict
     *  "empty means disconnected" semantics the unit tests pin. */
    disconnectedGraceElapsed: Boolean = true,
): ConnectionStatus {
    val bestLatencyMs = activeNodes.firstOrNull()?.latency?.removeSuffix("ms")?.toIntOrNull()
    return when {
        activeNodes.isEmpty() || bestLatencyMs == null ->
            if (disconnectedGraceElapsed) ConnectionStatus.DISCONNECTED else ConnectionStatus.DEGRADED
        bestLatencyMs < 300 -> ConnectionStatus.CONNECTED
        else -> ConnectionStatus.DEGRADED
    }
}

internal fun dotColorHexFor(status: ConnectionStatus, dpiSuspected: Boolean = false): Long = when (status) {
    // Connected with DPI suspected (indexer requests over HTTP/1.1): a darker green, as iOS.
    ConnectionStatus.CONNECTED -> if (dpiSuspected) 0xFF008000 else 0xFF4CD964
    ConnectionStatus.DEGRADED -> 0xFFF39C12
    ConnectionStatus.DISCONNECTED -> 0xFFFF3B30
}

data class NodeInfo(
    val ip: String,
    val type: String, // Seed, Manual
    val latency: String,
    val daaScore: String,
    val status: String, // Active, Quarantined, Suspect, Candidate
    val color: Long, // Hex color for status
    /** From the bundled geo database (iOS NodeProfiler): null when the address is not in it. */
    val countryCode: String? = null,
    /** From this device's place, estimated from the nearest nodes (iOS NodeProfiler). */
    val distanceKm: Double? = null,
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val nodePoolManager: com.kachat.app.services.NodePoolManager,
    private val kachatNamesRegistry: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesRegistry>,
    dpiGuard: com.kachat.app.services.IndexerDpiGuard,
) : ViewModel() {

    /** The Message Indexer is being reached over HTTP/1.1 because DPI is suspected (iOS). */
    val dpiSuspected: StateFlow<Boolean> = dpiGuard.dpiSuspected

    /** Where .kachat names are read from right now, for Connection Settings > Domains: the
     *  Message Indexer's URL, the chain, or null until the registry has picked (iOS 1d23002). */
    val kachatNamesSource: StateFlow<com.kachat.app.services.kachatnames.KachatNamesRegistry.Source?>
        get() = kachatNamesRegistry.get().source

    /** The source is picked on first use; pick it now if nothing has asked yet. */
    fun prepareKachatNamesSource() {
        if (!com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) return
        val registry = kachatNamesRegistry.get()
        if (registry.source.value != null) return
        viewModelScope.launch { runCatching { registry.prepare() } }
    }

    val activeNodes: StateFlow<List<NodeInfo>> = nodePoolManager.activeNodes
    val allNodes: StateFlow<List<NodeInfo>> = nodePoolManager.allNodes

    /** True when probing has concluded no Kaspa gRPC node is reachable at all on this network
     *  (port blocked, poisoned DNS with dead seeds, or no uplink) — drives the honest
     *  explanation on the connection status screen. See NodePoolManager.nodeConnectionsBlocked. */
    val nodeConnectionsBlocked: StateFlow<Boolean> = nodePoolManager.nodeConnectionsBlocked

    /** Wall-clock ms when the active-node list last became empty; null while any node is
     *  active. Tracked by an always-live collector, NOT inside the WhileSubscribed pipelines,
     *  so leaving and re-entering a screen can never reset the disconnection stopwatch. */
    private val emptySinceMs = MutableStateFlow<Long?>(
        if (nodePoolManager.activeNodes.value.isEmpty()) System.currentTimeMillis() else null
    )

    init {
        viewModelScope.launch {
            nodePoolManager.activeNodes.collect { nodes ->
                emptySinceMs.value =
                    if (nodes.isEmpty()) (emptySinceMs.value ?: System.currentTimeMillis()) else null
            }
        }
    }

    /** Emits the graced status: orange immediately when the pool empties (startup probing,
     *  failover, network handoff), red only once the emptiness has persisted for the grace
     *  window - the dot must never show red unless the user is truly disconnected. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun statusFlow(): Flow<ConnectionStatus> =
        combine(activeNodes, emptySinceMs) { nodes, since -> nodes to since }
            .transformLatest { (nodes, since) ->
                val elapsedMs = since?.let { System.currentTimeMillis() - it }
                val graceElapsed = elapsedMs != null && elapsedMs >= DISCONNECT_GRACE_MS
                emit(deriveConnectionStatus(nodes, graceElapsed))
                if (since != null && !graceElapsed) {
                    delay(DISCONNECT_GRACE_MS - (elapsedMs ?: 0L))
                    emit(deriveConnectionStatus(nodes, disconnectedGraceElapsed = true))
                }
            }

    private fun initialGracedStatus(): ConnectionStatus {
        val since = emptySinceMs.value
        val graceElapsed = since != null && System.currentTimeMillis() - since >= DISCONNECT_GRACE_MS
        return deriveConnectionStatus(nodePoolManager.activeNodes.value, graceElapsed)
    }

    /** Real status derived from the live node pool — no more hardcoded CONNECTED. */
    val status: StateFlow<ConnectionStatus> = statusFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialGracedStatus())

    /** Connection dot color: green under 300ms, orange at 300ms+, red only after sustained disconnection. */
    val dotColorHex: StateFlow<Long> = statusFlow()
        .combine(dpiSuspected) { status, dpi -> dotColorHexFor(status, dpi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), dotColorHexFor(initialGracedStatus()))

    /** Real "Xs/Xm/Xh ago" string, ticking every second, sourced from the pool's most recent successful probe. */
    val lastSyncAt: StateFlow<String> = flow {
        while (true) {
            val ts = nodePoolManager.lastSuccessAt()
            emit(
                if (ts == null) {
                    "Never"
                } else {
                    val secondsAgo = (System.currentTimeMillis() - ts) / 1000
                    when {
                        secondsAgo < 60 -> "${secondsAgo}s ago"
                        secondsAgo < 3600 -> "${secondsAgo / 60}m ago"
                        else -> "${secondsAgo / 3600}h ago"
                    }
                }
            )
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Never")

    val network: StateFlow<String> = settings.network.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "Mainnet")
    // Connection Settings shows the Testnet switch's network's values: after a switch they are
    // the chosen network's, which the app connects with from the next launch (iOS e6f664f). The
    // running app keeps using its own network's until then (AppSettingsRepository).
    val indexerUrl: StateFlow<String> = settings.selectedIndexerUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val knsApiUrl: StateFlow<String> = settings.selectedKnsApiUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val kaspaRestApiUrl: StateFlow<String> = settings.selectedKaspaRestUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    /** The indexer the running app is connected to, for the connection status screen. */
    val runningIndexerUrl: StateFlow<String> = settings.indexerUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    /** The KaPosts indexer the running app uses - whether KaPosts can open now. */
    val runningKapostIndexerUrl: StateFlow<String?> = settings.kapostIndexerUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val broadcastIndexerUrl: StateFlow<String> = settings.selectedBroadcastIndexerUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val trustedNodeAddress: StateFlow<String> = settings.selectedTrustedNodeAddress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    /**
     * True when node selection is AUTOMATIC (no pinned node, the pool discovers and picks),
     * false when pinned to a specific node (the shipped default or the user's own) - see
     * [AppSettingsRepository.trustedNodeAddress]: blank means discovery, non-blank means pinned.
     * Null until the stored value has actually been read, so the connection status screen can
     * keep pool-only sections hidden for that first frame instead of flashing them at a pinned
     * user (or vice versa) while DataStore loads.
     */
    // The running pool's selection (the status screen describes the live pool).
    val nodeSelectionIsAutomatic: StateFlow<Boolean?> = settings.trustedNodeAddress
        .map { it.trim().isBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val savedNodeAddresses: StateFlow<List<com.kachat.app.models.SavedNodeAddress>> =
        settings.selectedSavedNodeAddresses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /** Opt-in per-request HTTP logging (Diagnostics section), default OFF. */
    val verboseApiLogging: StateFlow<Boolean> =
        settings.verboseApiLogging.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _discoverNewPeers = MutableStateFlow(true)
    val discoverNewPeers: StateFlow<Boolean> = _discoverNewPeers

    /** Settings > Connection > Testnet: only records the choice; the switch, with each network's
     *  own connection settings, happens at the next launch (iOS `SettingsViewModel.switchNetwork(to:)`,
     *  e6f664f). Choosing the running network again cancels it. */
    fun switchNetwork(to: com.kachat.app.util.KaspaNetwork.Type) { viewModelScope.launch { settings.switchNetwork(to) } }
    /** The connection endpoints Connection Settings can edit (iOS ConnectionSettingsView). */
    enum class Endpoint { MESSAGE_INDEXER, PUBLIC_CHATS_INDEXER, KASPA_REST }

    /**
     * iOS ConnectionSettingsView's save rule, for one field: https only (an http:// value is
     * refused - returns false so the dialog can say so), a bare host gets https://, and blank is
     * the network's default (the settings resolve an empty value to it). A new Message Indexer
     * asks again where .kachat names come from, now rather than at the next stale refresh.
     */
    fun setEndpointUrl(endpoint: Endpoint, value: String, current: String): Boolean {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.startsWith("http://", ignoreCase = true)) return false
        val normalized = if (trimmed.isEmpty() || trimmed.startsWith("https://", ignoreCase = true)) trimmed else "https://$trimmed"
        viewModelScope.launch {
            when (endpoint) {
                Endpoint.MESSAGE_INDEXER -> settings.setIndexerUrl(normalized)
                Endpoint.PUBLIC_CHATS_INDEXER -> settings.setBroadcastIndexerUrl(normalized)
                Endpoint.KASPA_REST -> settings.setKaspaRestUrl(normalized)
            }
            val effective = normalized.ifBlank {
                if (onTestnetSettings) "" else AppSettingsRepository.DEFAULT_INDEXER_URL
            }
            if (endpoint == Endpoint.MESSAGE_INDEXER && effective != current &&
                com.kachat.app.services.kachatnames.KachatNamesService.isLaunched
            ) {
                runCatching { kachatNamesRegistry.get().prepare(forceSourceCheck = true) }
            }
        }
        return true
    }

    private val onTestnetSettings: Boolean
        get() = com.kachat.app.util.KaspaNetwork.selected.value == com.kachat.app.util.KaspaNetwork.Type.TESTNET

    fun setTrustedNodeAddress(value: String) { viewModelScope.launch { settings.setTrustedNodeAddress(value) } }
    fun setDiscoverNewPeers(value: Boolean) { _discoverNewPeers.value = value }
    fun setVerboseApiLogging(value: Boolean) { viewModelScope.launch { settings.setVerboseApiLogging(value) } }
    fun addSavedNodeAddress(label: String, address: String) {
        viewModelScope.launch {
            settings.addSavedNodeAddress(com.kachat.app.models.SavedNodeAddress(label = label, address = address))
        }
    }
    fun removeSavedNodeAddress(id: String) { viewModelScope.launch { settings.removeSavedNodeAddress(id) } }

    // iOS ConnectionStatusDetailView: Refresh Pool and Reconnect each spin in their row while
    // they run, and every pool action is disabled until both are done.
    private val _isRefreshingPool = MutableStateFlow(false)
    val isRefreshingPool: StateFlow<Boolean> = _isRefreshingPool.asStateFlow()
    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private fun poolBusy() = _isRefreshingPool.value || _isReconnecting.value

    fun refreshPool() {
        if (poolBusy()) return
        _isRefreshingPool.value = true
        viewModelScope.launch {
            try { nodePoolManager.refreshNow().join() } finally { _isRefreshingPool.value = false }
        }
    }

    /** "Clear" in the confirmation: the pool is emptied, then the app reconnects (iOS clears, then
     *  runs its Reconnect, whose spinner shows meanwhile). */
    fun clearPool() {
        if (poolBusy()) return
        _isReconnecting.value = true
        viewModelScope.launch {
            try { nodePoolManager.clearPool().join() } finally { _isReconnecting.value = false }
        }
    }

    fun reconnect() {
        if (poolBusy()) return
        _isReconnecting.value = true
        viewModelScope.launch {
            try { nodePoolManager.reconnect().join() } finally { _isReconnecting.value = false }
        }
    }
    fun addManualEndpoint(address: String) { nodePoolManager.addManualEndpoint(address) }
}
