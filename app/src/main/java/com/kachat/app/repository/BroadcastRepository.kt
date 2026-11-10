package com.kachat.app.repository

import com.kachat.app.models.BroadcastChannelEntity
import com.kachat.app.models.BroadcastMessageEntity
import com.kachat.app.models.BroadcastRetention
import com.kachat.app.models.HiddenBroadcastSenderEntity
import com.kachat.app.models.FeaturedBroadcastChannels
import com.kachat.app.models.ReactionEntity
import com.kachat.app.services.NetworkService
import com.kachat.app.services.WalletManager
import com.kachat.app.services.WalletService
import com.kachat.app.services.database.KaChatDatabase
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.kachat.app.util.MessageProtocol
import com.kachat.app.util.MessageReaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the chess arena's indexer is answering (iOS da59726 IndexerHealth). It only counts as down
 * after several failures in a row, so one timeout never moves the arena off it. While it's down,
 * one request a minute is let through to find out when it's back; any answer brings it back at once.
 */
internal class ArenaIndexerHealth(private val clock: () -> Long = System::currentTimeMillis) {
    private var downSince: Long? = null
    private var failuresInARow = 0
    private var lastFailureAt = Long.MIN_VALUE / 2
    private var retryAt = Long.MIN_VALUE / 2

    @get:Synchronized
    val isDown: Boolean get() = downSince != null

    /** The next read goes through, down or not (a chess screen just opened). */
    @Synchronized
    fun allowProbeNow() {
        retryAt = Long.MIN_VALUE / 2
    }

    @Synchronized
    fun reset() {
        downSince = null
        failuresInARow = 0
        retryAt = Long.MIN_VALUE / 2
    }

    @Synchronized
    fun shouldSkipIndexer(): Boolean {
        if (downSince == null) return false
        val now = clock()
        // Moved here rather than a "probing" flag, which could stick if the caller never asks.
        if (now < retryAt) return true
        retryAt = now + RETRY_INTERVAL_MS
        return false
    }

    @Synchronized
    fun recordFailure() {
        val now = clock()
        if (now - lastFailureAt > FAILURE_WINDOW_MS) failuresInARow = 0
        lastFailureAt = now
        failuresInARow += 1
        if (downSince == null && failuresInARow >= FAILURES_TO_GO_DOWN) {
            downSince = now
            retryAt = now + RETRY_INTERVAL_MS
        }
    }

    @Synchronized
    fun recordAnswer() {
        failuresInARow = 0
        downSince = null
    }

    private companion object {
        const val FAILURES_TO_GO_DOWN = 3
        /** Failures further apart than this aren't a run. */
        const val FAILURE_WINDOW_MS = 120_000L
        const val RETRY_INTERVAL_MS = 60_000L
    }
}

/**
 * Broadcast rooms — public, unencrypted, one-to-many channels identified by a plaintext channel
 * name (see MessageProtocol's bcast payload functions). Kept separate from [ChatRepository]:
 * channels/messages here have no contact concept, no per-account message scoping (the message
 * cache is a raw capture of public chain data, shared across whichever account is active), and no
 * encryption, so folding this into ChatRepository would mostly add conditionals rather than reuse.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BroadcastRepository @Inject constructor(
    private val database: KaChatDatabase,
    private val walletManager: WalletManager,
    private val walletService: WalletService,
    private val networkService: NetworkService,
    private val settings: com.kachat.app.repository.AppSettingsRepository,
    // Public-chat authorship (audit XP-012): the indexer's senderAddress is only its guess, so
    // every row it serves is verified (input 0's address == output 0's) before it is stored.
    private val senderVerifier: com.kachat.app.services.BroadcastSenderVerifier,
    // The unconfirmed arena-row check: where its stamps are kept, and the mempools it asks.
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val nodePoolManager: com.kachat.app.services.NodePoolManager,
) {
    /** The active account's address - the scanner refreshes the indexer's tracked rooms on a switch. */
    val activeAddressFlow: kotlinx.coroutines.flow.StateFlow<String?> get() = walletManager.activeAddressFlow

    /** Channels joined by whichever account is currently active — re-emits automatically on account switch. */
    fun getJoinedChannels(): Flow<List<BroadcastChannelEntity>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else database.broadcastDao().getJoinedChannels(address)
        }
    }

    /** "Joining" and "creating" a channel are the same action — there's no ownership/membership protocol. */
    suspend fun joinChannel(rawName: String) {
        val name = MessageProtocol.normalizeChannelName(rawName)
        require(MessageProtocol.isValidChannelName(name)) { "Invalid channel name" }
        database.broadcastDao().joinChannel(
            BroadcastChannelEntity(channelName = name, walletAddress = walletManager.getAddress())
        )
    }

    /** Removes the channel from the joined list AND permanently deletes every cached message for it — the UI must confirm this with the user first, since it's destructive and can't be undone (rejoining later starts with no history). Featured rooms can't be left (matches iOS). */
    suspend fun leaveChannel(channelName: String) {
        // Curated rooms (Popular and the language rooms alike) are permanent fixtures of the
        // list screen, so no UI offers Leave; guard against stray paths. A room the indexer
        // tracks that you added yourself stays one you can leave (iOS 21c480d).
        if (channelName in FeaturedBroadcastChannels.CURATED_NAMES) return
        database.broadcastDao().leaveChannel(channelName, walletManager.getAddress())
        database.broadcastDao().deleteMessagesForChannel(channelName)
    }

    /**
     * The curated #kaspa/#kachat-bugs rooms are always present for every account (4.0, matches
     * iOS): auto-joined with the FIXED 3-day retention their indexer backfill serves. Idempotent
     * - joinChannel inserts with IGNORE, so an already-joined row (and its bell/listen toggles)
     * is left completely untouched.
     */
    suspend fun ensureFeaturedChannelsJoined() {
        val address = try { walletManager.getAddress() } catch (_: Exception) { return }
        for (name in FeaturedBroadcastChannels.NAMES) {
            database.broadcastDao().joinChannel(
                BroadcastChannelEntity(
                    channelName = name,
                    walletAddress = address,
                    retentionMillis = BroadcastRetention.MAX_MILLIS,
                )
            )
        }
    }

    /**
     * Per-channel opt-in to background scanning — the user chooses exactly which channels stay
     * live while the app is backgrounded, rather than one all-or-nothing setting. Turning this
     * off also turns notifications off for the channel (a channel that isn't being scanned has
     * no way to know about new messages to notify about).
     */
    suspend fun setAlwaysListen(channelName: String, alwaysListen: Boolean) {
        if (alwaysListen) {
            database.broadcastDao().setAlwaysListen(channelName, walletManager.getAddress(), true)
        } else {
            database.broadcastDao().disableAlwaysListenAndNotify(channelName, walletManager.getAddress())
        }
    }

    /** Per-channel opt-in to a notification for new messages — enabling this also turns always-listen on for the channel, since notifications depend on it actually being scanned. */
    suspend fun setNotifyEnabled(channelName: String, notifyEnabled: Boolean) {
        val address = walletManager.getAddress()
        if (notifyEnabled) {
            database.broadcastDao().enableNotifyAndAlwaysListen(channelName, address)
        } else {
            database.broadcastDao().disableNotify(channelName, address)
        }
    }

    /** Per-channel override of local message retention, set via the settings icon next to a channel — clamped to [1 second, BroadcastRetention.MAX_MILLIS] so the UI's 3-day cap can't be bypassed by a bad input. */
    suspend fun setRetentionMillis(channelName: String, retentionMillis: Long) {
        // Rooms the indexer tracks have a FIXED 30-day retention (the indexer serves 30 days of
        // history and the prune keeps all of it) - no per-room override.
        if (FeaturedBroadcastChannels.isIndexerTracked(channelName)) return
        val clamped = retentionMillis.coerceIn(1_000L, BroadcastRetention.MAX_MILLIS)
        database.broadcastDao().setRetentionMillis(channelName, walletManager.getAddress(), clamped)
    }

    /** The active account's always-listen channel names — non-empty drives whether background scanning runs at all, and membership decides which channels' messages actually get cached while it's running. */
    fun getAlwaysListenChannelNames(): Flow<Set<String>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptySet()) else database.broadcastDao().getAlwaysListenChannelNames(address).map { it.toSet() }
        }
    }

    /** The active account's notify-enabled channel names — drives which channels' new messages fire a system notification. */
    fun getNotifyEnabledChannelNames(): Flow<Set<String>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptySet()) else database.broadcastDao().getNotifyEnabledChannelNames(address).map { it.toSet() }
        }
    }

    /**
     * What the Public Chats list shows for one room: its newest message and how many messages
     * from other people are newer than the read marker. [lastMessage] is null for a room with
     * nothing in it yet. Counting stops at the bounded window it looks at, which is far more
     * than a badge ever needs to say.
     */
    data class RoomSummary(
        val lastMessage: BroadcastMessageEntity?,
        val unreadCount: Int,
    )

    /**
     * Newest message and unread count for [channelName], respecting the senders hidden in this
     * room and never counting reactions - they are not messages anyone reads. [lastReadMs] null
     * means the room has no marker yet, so nothing in it counts as unread.
     */
    fun roomSummary(channelName: String, myAddress: String?, lastReadMs: Long?): Flow<RoomSummary> {
        return combine(
            database.broadcastDao().getLatestMessagesForChannel(channelName, SUMMARY_WINDOW),
            getHiddenSenders(),
        ) { newestFirst, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            val readable = newestFirst.filterNot {
                it.senderAddress in hiddenHere || MessageReaction.parseOrNull(it.content) != null ||
                    com.kachat.app.util.MessageEdit.parseOrNull(it.content) != null
            }
            val unread = if (lastReadMs == null) 0 else readable.count {
                it.blockTimestamp > lastReadMs && it.senderAddress != myAddress
            }
            RoomSummary(lastMessage = readable.firstOrNull(), unreadCount = unread)
        }.flowOn(Dispatchers.Default)
    }

    /** A room's newest [ROOM_WINDOW_SIZE] rows (widened by "Load earlier messages"), and whether
     *  the read filled the window — which is the only way to know there is more behind it. */
    data class RoomWindow(val messages: List<BroadcastMessageEntity>, val hasMore: Boolean)

    /** Never includes messages from a sender hidden IN THIS ROOM (or via a legacy every-room hide) — including ones already cached from before the hide (see BroadcastScanningService for the future-side enforcement). Reaction messages (see [getReactions]) never render as a message row, so they're filtered out here too. */
    fun getMessages(channelName: String): Flow<List<BroadcastMessageEntity>> =
        getMessageWindow(channelName, limit = null).map { it.messages }

    /**
     * The room read as a window. A curated room keeps thirty days of history, and the screen used
     * to load every row of it on open and again on every change (iOS 02a4f58). [limit] null reads
     * the whole room, which is what the summary paths and the chess arena's reducer need.
     *
     * `hasMore` is measured on the RAW rows, before reactions and edits are filtered out: those
     * are rows in the same table, so a window of N raw rows renders fewer than N bubbles, and
     * counting the rendered ones would call a full window exhausted.
     */
    fun getMessageWindow(channelName: String, limit: Int?): Flow<RoomWindow> {
        val rows = if (limit == null) {
            database.broadcastDao().getMessagesForChannel(channelName)
        } else {
            // DESC + LIMIT takes the NEWEST n off the (channelName, blockTimestamp) index; the
            // screen wants them oldest-first, as the unwindowed query already returns them.
            database.broadcastDao().getLatestMessagesForChannel(channelName, limit).map { it.asReversed() }
        }
        return combine(rows, getHiddenSenders()) { messages, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            // And never two rows with one id: the room's LazyColumn is keyed by it, and a
            // duplicate key crashes Compose (iOS d0b819f - there it left a blank gap). The table's
            // primary key is the id, so this is a guard, not a cleanup.
            val seen = HashSet<String>(messages.size)
            RoomWindow(
                messages = messages.filter {
                    seen.add(it.id) &&
                        it.senderAddress !in hiddenHere && MessageReaction.parseOrNull(it.content) == null &&
                        com.kachat.app.util.MessageEdit.parseOrNull(it.content) == null
                },
                hasMore = limit != null && messages.size >= limit,
            )
        // The reaction/edit sniff runs over every row on every change - through a room's first
        // backfill that is once per page. Collected from the screen it ran on the main thread
        // (iOS 9c9e3e0, 5463ccc).
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Reactions in [channelName], derived from the cached broadcast message rows themselves —
     * a reaction is just a broadcast whose content is the [MessageReaction] JSON (same wire
     * format as 1:1/group chats), so the rows the block scanner / indexer backfill already
     * persist ARE the reaction storage: they survive restarts, load with the channel's history,
     * and dedupe by txId, which keeps re-processing the indexer's repeated 200-row pages
     * harmless (unlike replaying add/remove events into a separate table would be).
     *
     * Aggregation matches group chat's semantics exactly: reactor = sender address, one reaction
     * per (target, reactor) with the newest blockTime winning, and a "remove" deleting the chip —
     * except a FAILED remove, which is restored marked failed (so it isn't silently lost and the
     * Retry under the message can re-attempt it), mirroring GroupRepository.sendGroupReaction's
     * failure handling. Emitted as in-memory [ReactionEntity] values (never persisted to the
     * `reactions` table — walletAddress is left blank) purely so the existing ReactionPill UI is
     * reused as-is; `reactionTxId` carries the reaction message row's own id for [retryReactionMessage].
     */
    fun getReactions(channelName: String): Flow<List<ReactionEntity>> {
        return combine(database.broadcastDao().getMessagesForChannel(channelName), getHiddenSenders()) { messages, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            val newestPerReactor = LinkedHashMap<Pair<String, String>, Pair<BroadcastMessageEntity, com.kachat.app.util.MessageReactionContent>>()
            for (row in messages) {
                if (row.senderAddress in hiddenHere) continue
                val parsed = MessageReaction.parseOrNull(row.content) ?: continue
                val key = parsed.targetTxId to row.senderAddress
                val existing = newestPerReactor[key]
                // >= so a tie is broken by row order (DAO orders by blockTimestamp ASC).
                if (existing == null || row.blockTimestamp >= existing.first.blockTimestamp) {
                    newestPerReactor[key] = row to parsed
                }
            }
            newestPerReactor.values.mapNotNull { (row, parsed) ->
                if (parsed.action != "add" && row.deliveryStatus != "failed") return@mapNotNull null
                ReactionEntity(
                    targetTxId = parsed.targetTxId,
                    walletAddress = "", // in-memory value object only, never inserted into the reactions table
                    reactorAddress = row.senderAddress,
                    emoji = parsed.emoji,
                    reactionTxId = row.id,
                    blockTimestamp = row.blockTimestamp,
                    deliveryStatus = row.deliveryStatus,
                    failedAction = if (row.deliveryStatus == "failed") parsed.action else null
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * The newest edit per message in [channelName], derived from the cached broadcast rows the
     * same way [getReactions] derives reactions: an edit is just a broadcast whose content is the
     * [com.kachat.app.util.MessageEdit] JSON, so the stored rows ARE the storage — they survive
     * restarts, load with the room's history and dedupe by txId.
     *
     * An edit counts only if its sender sent the message it names and that message is text; the
     * newest by block time wins. Keyed by the target's txId, ready for the screen to apply as it
     * draws each bubble.
     */
    fun getEdits(channelName: String): Flow<Map<String, BroadcastEdit>> {
        return combine(database.broadcastDao().getMessagesForChannel(channelName), getHiddenSenders()) { messages, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            val senders = HashMap<String, BroadcastMessageEntity>(messages.size)
            for (row in messages) senders[row.id] = row
            val newestPerTarget = LinkedHashMap<String, BroadcastEdit>()
            for (row in messages) {
                if (row.senderAddress in hiddenHere) continue
                val parsed = com.kachat.app.util.MessageEdit.parseOrNull(row.content) ?: continue
                val target = senders[parsed.targetTxId] ?: continue
                if (target.senderAddress != row.senderAddress) continue
                if (!com.kachat.app.util.MessageEdit.isEditable(target.content)) continue
                val existing = newestPerTarget[parsed.targetTxId]
                // >= so a tie is broken by row order (DAO orders by blockTimestamp ASC).
                if (existing == null || row.blockTimestamp >= existing.blockTimestamp) {
                    newestPerTarget[parsed.targetTxId] = BroadcastEdit(
                        targetTxId = parsed.targetTxId,
                        text = parsed.text,
                        editMessageId = row.id,
                        blockTimestamp = row.blockTimestamp,
                        deliveryStatus = row.deliveryStatus
                    )
                }
            }
            newestPerTarget
        }.flowOn(Dispatchers.Default)
    }

    /** One message's newest edit in a room — see [getEdits]. */
    data class BroadcastEdit(
        val targetTxId: String,
        val text: String,
        val editMessageId: String,
        val blockTimestamp: Long,
        val deliveryStatus: String
    )

    /** Re-attempts a reaction message whose send previously failed — [reactionMessageId] is the reaction's own broadcast message row id (see [getReactions]'s `reactionTxId`). */
    suspend fun retryReactionMessage(reactionMessageId: String?) {
        val row = reactionMessageId?.let { database.broadcastDao().getMessage(it) } ?: return
        retryBroadcast(row)
    }

    /** The active account's hidden-sender rows (per-room since 4.0; channelName "" = every room) — re-emits on account switch, same as everything else here. */
    fun getHiddenSenders(): Flow<List<HiddenBroadcastSenderEntity>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else database.broadcastDao().getHiddenSenders(address)
        }
    }

    companion object {
        private const val LOCAL_STAMPS_PREFS = "broadcast_local_stamps"
        private const val LOCAL_STAMPS_KEY = "publicChatLocalStampSentAt"
        /** Per account, like iOS's, whose store they live beside (iOS da59726). */
        private fun localStampsWalletKey(wallet: String) = "$LOCAL_STAMPS_KEY.$wallet"
        /** How far back the Public Chats list looks in each room. A badge past this reads the
         *  same to anyone, and it keeps a busy curated room cheap to summarise. */
        const val SUMMARY_WINDOW = 200

        /** How much of a room the thread screen reads at once, and how much each "Load earlier
         *  messages" adds. iOS's `roomWindowSize`. */
        const val ROOM_WINDOW_SIZE = 400

        /** How often the app-wide indexer is asked which rooms it tracks (iOS: hourly). */
        private const val TRACKED_ROOMS_CHECK_INTERVAL_MS = 60L * 60 * 1000

        /** Deep backfill re-runs while some rows' senders are still unverified (XP-012). */
        private const val DEEP_UNVERIFIED_RETRIES = 3

        /** Launches [reverifyCachedSenders] may wait for the REST API before giving up (XP-012). */
        const val REVERIFY_MAX_ATTEMPTS = 3

        /** Cached rows checked per pass step - one REST search request. */
        private const val REVERIFY_CHUNK = 100

        /** The polls stopped this long: what they missed may be past the newest page (iOS da59726). */
        private const val POLL_LAPSE_MS = 20_000L
        private const val REPAGE_MARGIN_MS = 60_000L
        /** A row a node says it doesn't hold still waits this long from sending, for a REST API
         *  running behind. Short of the give-up: a move really lost must leave clock to replay it. */
        private const val REST_LAG_GRACE_MS = 120_000L
        /** A row the store couldn't take yet leaves the answer not fresh only for this long from
         *  its block: a row that never verifies can't hold the arena's freshness back for good. */
        private const val UNKNOWN_ROW_FRESH_MS = 120_000L
        private const val CHAIN_PAGE_SIZE = 100
        private const val CHAIN_MAX_PAGES = 20
        private const val ARENA = com.kachat.app.util.ChessTournamentCodec.ARENA_CHANNEL

        /** Which senders are hidden in [channelName]: room-scoped rows plus legacy every-room ("") rows. */
        fun hiddenAddressesIn(channelName: String, rows: List<HiddenBroadcastSenderEntity>): Set<String> =
            rows.filter { it.channelName.isEmpty() || it.channelName == channelName }
                .map { it.senderAddress }
                .toSet()
    }

    /** Hides a sender in ONE room - their messages and notifications from that room disappear; other rooms are unaffected. */
    suspend fun hideSender(senderAddress: String, channelName: String) {
        database.broadcastDao().hideSender(
            HiddenBroadcastSenderEntity(senderAddress, walletManager.getAddress(), channelName)
        )
    }

    suspend fun unhideSender(senderAddress: String, channelName: String) {
        database.broadcastDao().unhideSender(senderAddress, walletManager.getAddress(), channelName)
    }

    // MARK: - Indexer backfill (4.0): the featured rooms are backed by the KaChat broadcast
    // indexer, so history sent while the app was closed appears on room open. Merge is
    // dedupe-by-txId via the DAO's REPLACE insert; hidden-sender filtering happens at read.

    /** Channels whose full 30-day history was already paged in this process — the deep backfill
     *  runs once per room per launch; the 8s poll then only needs the newest page. */
    private val deepBackfilledChannels: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Where an interrupted deep backfill picks up: the `before` cursor of the next page to ask
     *  for, and how many pages of the safety valve are left. Session-only, like the completed
     *  set above. Without this a single thrown page - one timeout on page 30 of a busy room -
     *  restarted the whole pager from page 1 on the next 8s tick, and kept doing so until every
     *  page happened to succeed in one go (iOS deepBackfillResume). */
    private val deepBackfillResume = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()

    /** How many times a room's deep backfill was re-run because some rows' senders could not be
     *  verified yet (audit XP-012). Session-only. */
    private val deepUnverifiedRetries = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** When the newest answered poll of a room was asked for (wall ms). Anything older than this
     *  that a newest-rows poll can't reach (the room closed, polls failing) is paged in again
     *  ([repageIfPollingLapsed], iOS da59726). */
    private val indexerCoveredUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** How far back a repeat of the backfill goes; none means the whole 30-day window. */
    private val backfillFloor = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // MARK: - The arena's indexer (iOS da59726)

    /** Whether the arena's indexer is answering. Every arena read reports here. */
    private val arenaHealth = ArenaIndexerHealth()

    /** Guards the arena's backfill bookkeeping below, which iOS keeps on the main actor. */
    private val arenaLock = Any()

    private val _arenaIndexerDown = MutableStateFlow(false)

    /** True while the chess arena's indexer has stopped answering. The arena then lives on the
     *  block stream, with the REST API filling in the player's own games
     *  (ChessTournamentService.readMyGamesFromChainIfDue). */
    val arenaIndexerDown: StateFlow<Boolean> = _arenaIndexerDown.asStateFlow()

    /** When the newest indexer answer for the arena was asked for (wall ms): rows up to then are
     *  in the store, so it only counts once any paging-in that was due has finished. A timeout
     *  claim waits for one newer than the flag. */
    @Volatile
    var arenaIndexerAnsweredAt: Long = 0L
        private set

    /** Whether the arena has an indexer configured at all, as of the last read that asked. */
    @Volatile
    var arenaHasIndexer: Boolean = true
        private set

    /** The indexer has answered an arena page: what the lobby's "Loading rooms…" waits for, the
     *  first page and not the whole backfill (iOS da59726 `indexerFetchedChannels`). */
    @Volatile
    var arenaIndexerAnswered: Boolean = false
        private set

    /** The arena's history is paged in, as far as this session owes it. */
    val arenaBackfillDone: Boolean get() = ARENA in deepBackfilledChannels

    /** This room's own indexer where it has one, the app-wide client otherwise. A broadcast is
     *  on-chain, so any indexer watching the same network serves the same room. */
    private suspend fun indexerApi(channelName: String): com.kachat.app.services.BroadcastIndexerApi? {
        val override = settings.broadcastIndexerOverrides.first()[channelName.trim().lowercase()]
        val api = override?.let { networkService.broadcastIndexerApiFor(it) } ?: networkService.broadcastIndexerApi.value
        if (channelName == ARENA) arenaHasIndexer = api != null
        return api
    }

    /** Whether a room has an indexer to ask (iOS `indexerBaseURL(forChannel:)` not empty). */
    suspend fun indexerConfigured(channelName: String): Boolean = indexerApi(channelName) != null

    suspend fun arenaIndexerConfigured(): Boolean = indexerConfigured(ARENA)

    /** Whether the arena has an indexer address set, read from the settings: the app-wide client
     *  only comes up a moment after launch, which mustn't read as "there is none". */
    suspend fun arenaIndexerAddressSet(): Boolean =
        !settings.broadcastIndexerOverrides.first()[ARENA.trim().lowercase()].isNullOrBlank() ||
            settings.broadcastIndexerUrl.first().isNotBlank()

    private fun noteArenaAnswered(channelName: String, askedAt: Long) {
        if (channelName != ARENA) return
        synchronized(arenaLock) {
            if (channelName !in deepBackfilledChannels) return
            arenaIndexerAnsweredAt = maxOf(arenaIndexerAnsweredAt, askedAt)
        }
    }

    /** The latest word on the arena's indexer, after every read: state, not transitions, so a
     *  missed or reordered change can't leave it stuck either way. Reads come in from several
     *  coroutines; on iOS this runs on the main actor. */
    @Synchronized
    private fun arenaHealthObserved() {
        val down = arenaHealth.isDown
        if (down == _arenaIndexerDown.value) return
        android.util.Log.w(
            "BroadcastRepository",
            if (down) "Arena indexer isn't answering: reading from the block stream and the Kaspa REST API" else "Arena indexer is answering again",
        )
        // Back: the arena is paged in again, since the steady poll's newest rows can't reach back
        // over an outage, and the indexer's block times replace what the stream gave meanwhile.
        // (Down: on a metered network the block stream comes back on, BroadcastScanningService.)
        if (!down) {
            synchronized(arenaLock) {
                deepBackfilledChannels.remove(ARENA)
                deepBackfillResume.remove(ARENA)
                backfillFloor.remove(ARENA)
                arenaIndexerAnsweredAt = 0L
            }
        }
        _arenaIndexerDown.value = down
    }

    /** The indexer's address was cleared while it was down: nothing would ever report it back. */
    suspend fun resyncArenaIndexerIfUnset() {
        if (arenaIndexerConfigured() || !_arenaIndexerDown.value) return
        arenaHealth.reset()
        arenaHealthObserved()
    }

    /** Opening a chess screen asks at once whether the arena's indexer is back. */
    fun allowArenaProbeNow() = arenaHealth.allowProbeNow()

    private fun skipsIndexer(channelName: String): Boolean =
        channelName == ARENA && arenaHealth.shouldSkipIndexer()

    /** No connection on the phone says nothing about the indexer (iOS's `.notConnectedToInternet`). */
    private fun phoneIsOffline(): Boolean {
        val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        return manager.activeNetwork == null
    }

    /** One page of a room's history. Every arena read reports how it went: any status that isn't
     *  a success, or a body that doesn't decode, gave no rows, and to the arena that's the same as
     *  no answer - the REST API must take over. */
    private suspend fun indexerPage(
        api: com.kachat.app.services.BroadcastIndexerApi,
        channelName: String,
        limit: Int,
        before: Long?,
    ): com.kachat.app.services.BroadcastHistoryResponse {
        if (channelName != ARENA) return api.getBroadcasts(channel = channelName, limit = limit, before = before)
        try {
            val page = api.getBroadcasts(channel = channelName, limit = limit, before = before)
            // Gson fills a missing field with null where iOS's Decodable throws: a 200 without the
            // expected shape is a body that doesn't decode (iOS da59726 `IndexedPublicChat`).
            val rows = page.messages ?: throw com.google.gson.JsonParseException("Arena page has no messages")
            if (rows.any { it.txId == null || it.senderAddress == null || it.content == null || it.blockTime == null }) {
                throw com.google.gson.JsonParseException("Arena row is missing a field")
            }
            arenaHealth.recordAnswer()
            arenaHealthObserved()
            arenaIndexerAnswered = true
            return page
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val saysDown = when (e) {
                is java.io.IOException -> !phoneIsOffline()
                // Retrofit's own NullPointerException is a 200 with an empty or "null" body.
                is retrofit2.HttpException, is com.google.gson.JsonParseException, is NullPointerException -> true
                else -> false
            }
            if (saysDown) arenaHealth.recordFailure()
            arenaHealthObserved()
            throw e
        }
    }

    /** The polls stopped for a while: the rows since then may be past what the newest page holds. */
    private fun repageIfPollingLapsed(channelName: String, now: Long) {
        val covered = indexerCoveredUntil[channelName] ?: return
        if (channelName !in deepBackfilledChannels || now - covered <= POLL_LAPSE_MS) return
        requestRepage(channelName, covered - REPAGE_MARGIN_MS)
    }

    /** The next poll pages the room back to [since]. A backfill already running to an older point,
     *  or the whole window, is left to go there. */
    private fun requestRepage(channelName: String, since: Long) {
        synchronized(arenaLock) {
            if (deepBackfilledChannels.remove(channelName)) {
                backfillFloor[channelName] = since
                deepBackfillResume.remove(channelName)
            } else {
                backfillFloor[channelName]?.let { floor -> backfillFloor[channelName] = minOf(floor, since) }
            }
            if (channelName == ARENA) arenaIndexerAnsweredAt = 0L
        }
    }

    /** Pages the arena back to [since] on the next poll: one of this player's games shows a move
     *  missing, or a claim didn't take. Until that's done no answer counts as fresh. */
    fun repageArena(since: Long) = requestRepage(ARENA, since - REPAGE_MARGIN_MS)

    /**
     * The last account left and every room's rows went with it: nothing here is paged in any more.
     * Without this the next account's polls only ask for the newest 30 rows, and a paid join can
     * go to a room long since moved past (iOS da59726 `setCurrentWallet`).
     */
    fun resetAfterRoomWipe() {
        synchronized(arenaLock) {
            deepBackfilledChannels.clear()
            deepBackfillResume.clear()
            deepUnverifiedRetries.clear()
            indexerCoveredUntil.clear()
            backfillFloor.clear()
            arenaIndexerAnsweredAt = 0L
            arenaIndexerAnswered = false
            localStampSentAt.clear()
            localStampNextCheck.clear()
            // Stamps are kept per account, and no account is left to keep any.
            localStampPrefs.edit().clear().apply()
        }
    }

    /** Fetches history for [channelName] and merges it into the local cache. The FIRST call per
     *  channel per launch pages backwards (`before` = oldest blockTime seen) through the
     *  indexer's whole 30-day window — a single newest page (200 rows) meant busy rooms never
     *  loaded anywhere near what the indexer holds. Later calls fetch just the newest page.
     *  Returns the number of rows fetched, or -1 when the indexer is unreachable (callers treat
     *  that as "no backfill", nothing user-facing breaks). */
    /** This wallet's own address, or null before there is one. */
    fun myAddress(): String? = runCatching { walletManager.getAddress() }.getOrNull()

    /**
     * One small newest-page fetch for a room nobody is looking at, merged like any other indexer
     * page. Returns the rows that were NEW to the store, so the caller can banner what just
     * arrived (iOS 3c14b46). Reactions never render as rows, so they are left out.
     */
    suspend fun fetchNewestFromIndexer(channelName: String, limit: Int = 40): List<BroadcastMessageEntity> =
        fetchNewestAndMerge(channelName, limit).orEmpty()

    /**
     * Asks the indexer for a room's newest rows right now and merges them: what the chess arena
     * does just before a player picks a room. False when there is no indexer for the room or the
     * request failed (iOS `refreshFromIndexerNow`).
     */
    suspend fun refreshFromIndexerNow(channelName: String): Boolean = fetchNewestAndMerge(channelName, 40) != null

    /** Null when there was no answer; otherwise the rows that were new to the store. */
    private suspend fun fetchNewestAndMerge(channelName: String, limit: Int): List<BroadcastMessageEntity>? {
        val api = indexerApi(channelName) ?: return null
        if (skipsIndexer(channelName)) return null
        val askedAt = System.currentTimeMillis()
        repageIfPollingLapsed(channelName, askedAt)
        val hidden = hiddenAddressesIn(channelName, getHiddenSenders().first())
        val page = try {
            indexerPage(api, channelName, limit, before = null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Sweep fetch failed for $channelName", e)
            return null
        }
        // An indexer only stores the rooms it tracks: rows back mean it keeps this room's history.
        if (!page.messages.isNullOrEmpty()) com.kachat.app.services.IndexerTrackedRooms.markServed(channelName)
        // Neither a reaction nor an edit is a message anyone reads; the room's own backfill is
        // what persists them.
        val wanted = page.messages.orEmpty().filter { message ->
            val content = message.content ?: return@filter false
            MessageReaction.parseOrNull(content) == null && com.kachat.app.util.MessageEdit.parseOrNull(content) == null
        }
        // Hidden is judged on the VERIFIED sender, never on the indexer's guess.
        val admitted = admitIndexerRows(channelName, wanted, skipSender = { it in hidden })
        // The store is missing a row that just happened: as far as a claim or a join goes, the
        // indexer hasn't answered yet (on iOS an answered page is always stored whole).
        if (channelName == ARENA && admitted.recentUnknown) return null
        noteArenaAnswered(channelName, askedAt)
        return admitted.inserted
    }

    /** What [admitIndexerRows] stored, how many rows it could not verify yet, and whether one of
     *  those is from the last [UNKNOWN_ROW_FRESH_MS]. */
    private data class Admitted(
        val inserted: List<BroadcastMessageEntity>,
        val unknown: Int,
        val recentUnknown: Boolean = false,
    )

    /**
     * Merges indexer rows into the store with their sender VERIFIED (audit XP-012): the
     * indexer's `senderAddress` is ignored; a new row is stored only when input 0 spends from the
     * address output 0 pays, under that address. Forged rows are dropped; rows the REST API
     * can't answer for yet are skipped and asked again on a later poll.
     *
     * A row already in the store was stored by a verified path (this one, the live scan, or this
     * phone's own send; older cached rows are re-checked once by [reverifyCachedSenders]), so it
     * keeps its sender and only takes the indexer's chain time: a row this phone sent carries its
     * own clock until the chain's time reaches it, the block scan may never have seen the block,
     * and the chess arena orders seats and runs its clocks by the indexer's time (iOS 29bf054).
     */
    private suspend fun admitIndexerRows(
        channelName: String,
        rows: List<com.kachat.app.services.BroadcastHistoryRow>,
        skipSender: (String) -> Boolean = { false },
    ): Admitted {
        val toVerify = ArrayList<com.kachat.app.services.BroadcastHistoryRow>()
        for (row in rows) {
            val txId = row.txId?.takeIf { it.isNotBlank() } ?: continue
            if (row.content == null) continue
            val existing = database.broadcastDao().getMessage(txId)
            if (existing != null) {
                val chainTime = row.blockTime
                if (chainTime != null) confirmLocalStamp(txId)
                if (chainTime != null && (chainTime != existing.blockTimestamp || existing.deliveryStatus != "sent")) {
                    database.broadcastDao().insertMessage(existing.copy(blockTimestamp = chainTime, deliveryStatus = "sent"))
                }
                continue
            }
            toVerify += row
        }
        if (toVerify.isEmpty()) return Admitted(emptyList(), 0)
        val verdicts = senderVerifier.verifyRows(toVerify.mapNotNull { it.txId }, channelName)
        val inserted = mutableListOf<BroadcastMessageEntity>()
        var unknown = 0
        var recentUnknown = false
        val now = System.currentTimeMillis()
        for (row in toVerify) {
            val txId = row.txId ?: continue
            val judged = verdicts[txId]
            when (judged.verdict) {
                com.kachat.app.services.BroadcastSenderVerifier.Verdict.VERIFIED -> {
                    val sender = judged.senderAddress ?: continue
                    if (skipSender(sender)) continue
                    val entity = BroadcastMessageEntity(
                        id = txId,
                        channelName = channelName,
                        senderAddress = sender,
                        content = row.content ?: continue,
                        blockTimestamp = row.blockTime ?: System.currentTimeMillis(),
                        deliveryStatus = "sent"
                    )
                    // Only a missing id (iOS): the block scan may have stored it meanwhile.
                    if (database.broadcastDao().insertMessageIfAbsent(entity) != -1L) inserted += entity
                }
                com.kachat.app.services.BroadcastSenderVerifier.Verdict.FORGED -> Unit // logged by the verifier
                com.kachat.app.services.BroadcastSenderVerifier.Verdict.UNKNOWN -> {
                    unknown += 1
                    val blockTime = row.blockTime
                    if (blockTime != null && kotlin.math.abs(now - blockTime) <= UNKNOWN_ROW_FRESH_MS) recentUnknown = true
                }
            }
        }
        return Admitted(inserted, unknown, recentUnknown)
    }

    /**
     * One pass over the cached public-chat rows stored before sender verification existed
     * (audit XP-012): those took output 0's address (live scan) or the indexer's guess as the
     * sender. Each is checked against the chain once: verified rows keep (or get corrected to)
     * the real sender, forged rows are deleted. Rows the REST API answers for but doesn't know are
     * deleted too (retry, then drop). If the REST API can't be reached the pass is tried again on
     * a later launch, up to [REVERIFY_MAX_ATTEMPTS] times, after which unverifiable rows go.
     * Returns true once the pass is complete.
     */
    suspend fun reverifyCachedSenders(attempt: Int): Boolean {
        val rows = database.broadcastDao().getAllSentMessages()
        if (rows.isEmpty()) return true
        // The last allowed attempt settles every row, reachable REST API or not.
        val giveUp = attempt >= REVERIFY_MAX_ATTEMPTS - 1
        var complete = true
        for (chunk in rows.chunked(REVERIFY_CHUNK)) {
            val verdicts = senderVerifier.verifyRows(chunk.map { it.id }, channel = "cached")
            if (verdicts.lookupFailed && !giveUp) {
                complete = false
                continue
            }
            for (row in chunk) {
                val judged = verdicts[row.id]
                when (judged.verdict) {
                    com.kachat.app.services.BroadcastSenderVerifier.Verdict.VERIFIED -> {
                        val sender = judged.senderAddress ?: continue
                        if (sender != row.senderAddress) {
                            database.broadcastDao().insertMessage(row.copy(senderAddress = sender))
                        }
                    }
                    com.kachat.app.services.BroadcastSenderVerifier.Verdict.FORGED -> database.broadcastDao().deleteMessage(row.id)
                    com.kachat.app.services.BroadcastSenderVerifier.Verdict.UNKNOWN -> {
                        if (!verdicts.wasAnswered(row.id) && !giveUp) complete = false
                        else database.broadcastDao().deleteMessage(row.id)
                    }
                }
            }
        }
        return complete
    }

    /** When the app-wide indexer was last asked which rooms it tracks: (base URL, millis). */
    @Volatile
    private var trackedRoomsCheckedAt: Pair<String, Long>? = null

    /**
     * Asks the app-wide public chat indexer which rooms it tracks (`/broadcast-channels`), at
     * most once an hour per indexer - on app open and wallet switch. An indexer without the
     * endpoint answers nothing and the set keeps what it learned from served rows
     * (iOS `PublicChatService.refreshIndexerTrackedRooms`, 21c480d).
     */
    suspend fun refreshIndexerTrackedRooms() {
        val base = settings.broadcastIndexerUrl.first().trim()
        if (base.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            val last = trackedRoomsCheckedAt
            if (last != null && last.first == base && now - last.second < TRACKED_ROOMS_CHECK_INTERVAL_MS) return
            trackedRoomsCheckedAt = base to now
        }
        val api = networkService.broadcastIndexerApiFor(base) ?: return
        val channels: List<String>? = try {
            api.getTrackedChannels().channels?.filterNotNull()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Tracked rooms not listed by $base: ${e.message}")
            null
        }
        if (channels == null) return
        com.kachat.app.services.IndexerTrackedRooms.setReported(channels)
    }

    /**
     * GET /chess/leaderboard on [channelName]'s indexer - its own where it has one, the app-wide
     * one otherwise (iOS ChessTournamentService.refreshIndexerLeaderboards). Null on any failure:
     * the phone's own boards stay in place.
     */
    suspend fun fetchChessLeaderboard(
        channelName: String,
        period: String,
    ): com.kachat.app.services.ChessIndexerLeaderboardResponse? {
        val override = settings.broadcastIndexerOverrides.first()[channelName.trim().lowercase()]
        val api = (override?.let { networkService.broadcastIndexerApiFor(it) }
            ?: networkService.broadcastIndexerApi.value) ?: return null
        return try {
            api.getChessLeaderboard(period = period, limit = 1000)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Chess leaderboard ($period) not served: ${e.message}")
            null
        }
    }

    /** GET /chess/bots/leaderboard on [channelName]'s indexer (iOS ChessBotLeaderboardService).
     *  Null on any failure: what is on screen stays. */
    suspend fun fetchChessBotLeaderboard(
        channelName: String,
        period: String,
    ): com.kachat.app.services.ChessBotLeaderboardResponse? {
        val override = settings.broadcastIndexerOverrides.first()[channelName.trim().lowercase()]
        val api = (override?.let { networkService.broadcastIndexerApiFor(it) }
            ?: networkService.broadcastIndexerApi.value) ?: return null
        return try {
            api.getChessBotLeaderboard(period = period, limit = 100)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Chess bot leaderboard ($period) not served: ${e.message}")
            null
        }
    }

    suspend fun backfillFromIndexer(channelName: String): Int {
        val api = indexerApi(channelName) ?: return -1
        // Down: one request a minute finds out when it's back, the rest are not made (iOS da59726).
        if (skipsIndexer(channelName)) return -1
        val askedAt = System.currentTimeMillis()
        repageIfPollingLapsed(channelName, askedAt)
        var fetched = 0
        var unverified = 0
        var recentUnknown = false
        val deep = channelName !in deepBackfilledChannels
        // Picking up an interrupted pager: everything newer than the recorded cursor is already
        // in the store from the attempt that recorded it.
        val resume = if (deep) deepBackfillResume[channelName] else null
        var before: Long? = resume?.first
        var pagesLeft = when {
            !deep -> 1
            resume != null -> resume.second
            else -> 50 // 50 × 200 = 10k rows, far beyond any real room
        }
        return try {
            // The 30-day window, or the floor of a repeat after a gap in the polling (iOS da59726).
            val cutoff = maxOf(System.currentTimeMillis() - BroadcastRetention.INDEXER_MILLIS, backfillFloor[channelName] ?: 0L)
            // The one-time deep backfill pages at 200; the recurring 8s poll only needs the
            // newest sliver (dedupe-by-txId makes overlap harmless), so its page is small —
            // 200 rows every 8s was the single biggest steady-state indexer cost per open room.
            val pageLimit = if (deep) 200 else 30
            while (pagesLeft > 0) {
                pagesLeft -= 1
                val response = indexerPage(api, channelName, pageLimit, before)
                val messages = response.messages.orEmpty()
                // An indexer only stores the rooms it tracks: rows back mean it keeps this
                // room's history (iOS 21c480d).
                if (messages.isNotEmpty()) com.kachat.app.services.IndexerTrackedRooms.markServed(channelName)
                // Stored only with a verified sender (audit XP-012).
                val admitted = admitIndexerRows(channelName, messages)
                unverified += admitted.unknown
                recentUnknown = recentUnknown || admitted.recentUnknown
                fetched += messages.size
                // The newest page is in: the polling's cover reaches to when it was asked for. (A
                // resumed backfill starts from an older page, which says nothing about the newest.)
                if (before == null) indexerCoveredUntil[channelName] = askedAt
                if (!deep || response.hasMore != true || messages.isEmpty()) break
                val oldest = messages.mapNotNull { it.blockTime }.minOrNull() ?: break
                if (oldest < cutoff) break // older pages would be pruned anyway
                before = oldest
                // Recorded after every landed page: a thrown page resumes from here, not page 1.
                deepBackfillResume[channelName] = oldest to pagesLeft
            }
            // Marked done only once the pager actually finishes; a failed one keeps its resume
            // cursor (see the catch) and the next 8s poll continues from there.
            if (deep) {
                deepBackfillResume.remove(channelName)
                // Rows whose sender could not be verified yet (the REST API had no answer) were
                // left out: page the history again on a later poll - rows already stored cost no
                // lookup - a few times before settling for what verified.
                val retries = deepUnverifiedRetries[channelName] ?: 0
                if (unverified > 0 && retries < DEEP_UNVERIFIED_RETRIES) {
                    deepUnverifiedRetries[channelName] = retries + 1
                } else {
                    synchronized(arenaLock) {
                        deepBackfilledChannels.add(channelName)
                        backfillFloor.remove(channelName)
                    }
                    deepUnverifiedRetries.remove(channelName)
                }
            }
            // A row that just happened isn't in the store yet: not fresh (see fetchNewestAndMerge).
            if (!recentUnknown) noteArenaAnswered(channelName, askedAt)
            fetched
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Indexer backfill paused for $channelName at before=$before", e)
            -1
        }
    }

    /**
     * Inserts an optimistic "pending" placeholder immediately (before the network call) so the
     * message shows up in the room right away and stays visible even if the send fails — matches
     * ChatViewModel.sendMessage's exact pattern for 1:1 chats. On success the placeholder is
     * swapped for the real message (real txId, "sent"); on failure it flips to "failed" in place
     * with a Retry option, rather than disappearing silently.
     */
    /**
     * Rows this phone sent whose block time is still this phone's own clock: the chain's time has
     * not reached us yet. The chess arena orders these by the moves around them, never by the
     * local stamp (iOS 8d5f925 `locallyStampedTxIds`, see ChessTournamentService).
     */
    val locallyStampedTxIds: Set<String> get() = localStampSentAt.keys.toSet()

    private val localStampPrefs = context.getSharedPreferences(LOCAL_STAMPS_PREFS, android.content.Context.MODE_PRIVATE)

    /** The same rows, by when this phone started waiting for them (wall ms). Kept across launches,
     *  so a row the network dropped before a relaunch is still found out
     *  ([checkUnconfirmedServiceRows]); a relaunch restarts the wait, as the chain's answer may
     *  have come while the app was closed (iOS 4922804). */
    private val localStampSentAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val localStampNextCheck = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val checkingLocalStamps = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The account [localStampSentAt] belongs to: stamps are per account (iOS da59726). */
    @Volatile
    private var localStampWallet: String? = null

    private fun setLocalStampWallet(wallet: String?) {
        if (wallet == localStampWallet) return
        localStampWallet = wallet
        localStampSentAt.clear()
        localStampNextCheck.clear()
        if (wallet == null) return
        val now = System.currentTimeMillis()
        localStampPrefs.getStringSet(localStampsWalletKey(wallet), emptySet()).orEmpty().forEach { localStampSentAt[it] = now }
    }

    private fun saveLocalStamps() {
        val wallet = localStampWallet ?: return
        localStampPrefs.edit().putStringSet(localStampsWalletKey(wallet), localStampSentAt.keys.toSet()).apply()
    }

    /** The row still carries this phone's clock: the chain's time has not reached it. */
    fun isLocallyStamped(txId: String): Boolean = localStampSentAt.containsKey(txId)

    private fun trackLocalStamp(txId: String) {
        localStampSentAt[txId] = System.currentTimeMillis()
        saveLocalStamps()
    }

    /** The chain (a scanned block or the indexer) showed the row: it carries the chain's time now. */
    fun confirmLocalStamp(txId: String) {
        if (localStampSentAt.remove(txId) == null) return
        localStampNextCheck.remove(txId)
        saveLocalStamps()
    }

    private sealed class TransactionLookup {
        data class Accepted(val blockTime: Long?) : TransactionLookup()
        data object NotAccepted : TransactionLookup()
        data object Unanswered : TransactionLookup()
    }

    /** The REST API's view of a transaction: accepted (with its block's time), unknown or not
     *  accepted, or no answer (iOS 4922804 `lookUpTransaction`). */
    private suspend fun lookUpTransaction(txId: String): TransactionLookup {
        val rest = networkService.kaspaRestApi.value ?: return TransactionLookup.Unanswered
        return try {
            val answer = kotlinx.coroutines.withTimeoutOrNull(8_000L) { rest.getTransactionAcceptance(txId) }
                ?: return TransactionLookup.Unanswered
            if (answer.isAccepted == true) TransactionLookup.Accepted(answer.blockTime) else TransactionLookup.NotAccepted
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) TransactionLookup.NotAccepted else TransactionLookup.Unanswered
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TransactionLookup.Unanswered
        }
    }

    /** Whether the REST API shows this transaction accepted (a bot stamp, iOS da59726). */
    suspend fun isTransactionAccepted(txId: String): Boolean = lookUpTransaction(txId) is TransactionLookup.Accepted

    /**
     * Own arena rows the chain has not shown [olderThanMs] after they were sent. Each is looked
     * up: accepted (REST API), it takes the block's time there; in a mempool or spent to a coin the
     * address holds, it waits or counts as landed; found nowhere by a node that answered, once the
     * REST API has had [REST_LAG_GRACE_MS] to catch up, it is marked failed, so the arena's reducer
     * stops applying it and the board is the chain's again (a move is the player's to play again).
     * A row the network cannot be asked about is given up after [giveUpAfterMs]. A failed row the
     * chain shows later takes its block time and counts again (BroadcastScanningService). Returns
     * the ids marked failed (iOS 4922804, da59726 `checkUnconfirmedServiceRows`).
     */
    suspend fun checkUnconfirmedServiceRows(olderThanMs: Long, giveUpAfterMs: Long): Set<String> {
        val now = System.currentTimeMillis()
        val due = localStampSentAt.entries
            .filter { now - it.value > olderThanMs && (localStampNextCheck[it.key] ?: 0L) <= now }
            .map { it.key to it.value }
        if (due.isEmpty() || !checkingLocalStamps.compareAndSet(false, true)) return emptySet()
        try {
            val failed = mutableSetOf<String>()
            for ((txId, sentAt) in due) {
                val lookup = lookUpTransaction(txId)
                // The chain showed it while the lookup ran.
                if (localStampSentAt[txId] == null) continue
                val later = System.currentTimeMillis() + 15_000L
                when (lookup) {
                    is TransactionLookup.Accepted -> {
                        confirmLocalStamp(txId)
                        val blockTime = lookup.blockTime
                        val existing = database.broadcastDao().getMessage(txId)
                        if (blockTime != null && existing != null &&
                            (existing.blockTimestamp != blockTime || existing.deliveryStatus != "sent")) {
                            database.broadcastDao().insertMessage(existing.copy(blockTimestamp = blockTime, deliveryStatus = "sent"))
                        }
                        continue
                    }
                    TransactionLookup.Unanswered -> if (now - sentAt <= giveUpAfterMs) {
                        localStampNextCheck[txId] = later
                        continue
                    }
                    TransactionLookup.NotAccepted -> {
                        if (nodePoolManager.isInMempool(txId)) {
                            localStampNextCheck[txId] = later
                            continue
                        }
                        // No mempool entry also comes back when no node could be asked, and a 404
                        // from a REST API running behind: neither says the transaction is gone.
                        val utxos = myAddress()?.let { nodePoolManager.getUtxosByAddress(it) }
                        if (utxos != null) {
                            if (utxos.any { it.outpoint.transactionId == txId }) {
                                confirmLocalStamp(txId)
                                continue
                            }
                            if (now - sentAt <= REST_LAG_GRACE_MS) {
                                localStampNextCheck[txId] = later
                                continue
                            }
                        } else if (now - sentAt <= giveUpAfterMs) {
                            localStampNextCheck[txId] = later
                            continue
                        }
                    }
                }
                // The chain showed it, or the account changed, while the lookups ran.
                if (localStampSentAt[txId] == null) continue
                android.util.Log.w("BroadcastRepository", "Own arena tx ${txId.take(12)} never reached the chain: dropped")
                localStampSentAt.remove(txId)
                localStampNextCheck.remove(txId)
                saveLocalStamps()
                // The DAO flow re-emits, and the arena's reducer only applies rows that are "sent".
                database.broadcastDao().updateMessageStatus(txId, "failed")
                failed += txId
            }
            return failed
        } finally {
            checkingLocalStamps.set(false)
        }
    }

    // MARK: - The arena from the REST API (iOS da59726)

    private data class ChainArenaRow(val txId: String, val sender: String, val content: String, val blockTime: Long)

    /**
     * One room's messages from its known players, read by address from the Kaspa REST API, for
     * while the indexer is down. A channel has no address of its own, so this can only fill in a
     * room whose players this phone already knows; it can't find new rooms or new joiners, and the
     * arena's history can't be rebuilt this way. Only rows the store doesn't hold go in, so a time
     * the indexer gave is left alone. True when every player's address answered.
     */
    suspend fun readArenaFromChain(tournamentId: String, players: List<String>, since: Long): Boolean {
        val found = mutableListOf<ChainArenaRow>()
        var complete = true
        for (address in players) {
            val rows = chainArenaRows(address, tournamentId, since)
            if (rows == null) complete = false else found += rows
        }
        mergeChainArenaRows(found)
        return complete
    }

    private suspend fun mergeChainArenaRows(found: List<ChainArenaRow>) {
        if (found.isEmpty()) return
        val hidden = hiddenAddressesIn(ARENA, getHiddenSenders().first())
        val dao = database.broadcastDao()
        for (row in found) {
            if (row.sender in hidden) continue
            val inserted = dao.insertMessageIfAbsent(
                BroadcastMessageEntity(
                    id = row.txId,
                    channelName = ARENA,
                    senderAddress = row.sender,
                    content = row.content,
                    blockTimestamp = row.blockTime,
                    deliveryStatus = "sent",
                )
            ) != -1L
            if (inserted) continue
            // Our own rows still on this phone's clock, or given up on, take the chain's time.
            val existing = dao.getMessage(row.txId) ?: continue
            if (!isLocallyStamped(row.txId) && existing.deliveryStatus != "failed") continue
            confirmLocalStamp(row.txId)
            if (existing.blockTimestamp != row.blockTime || existing.deliveryStatus != "sent") {
                dao.insertMessage(existing.copy(blockTimestamp = row.blockTime, deliveryStatus = "sent"))
            }
        }
    }

    /**
     * [address]'s accepted posts to the room since [since], newest pages first until they're older
     * than that. The author rule is the block scan's (XP-012): input 0 spends from the address
     * output 0 pays. Null when the REST API didn't answer.
     */
    private suspend fun chainArenaRows(address: String, tournamentId: String, since: Long): List<ChainArenaRow>? {
        val rest = networkService.kaspaRestApi.value ?: return null
        val me = address.lowercase()
        val found = mutableListOf<ChainArenaRow>()
        for (page in 0 until CHAIN_MAX_PAGES) {
            val rows = try {
                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    rest.getChainArenaTransactions(address = me, limit = CHAIN_PAGE_SIZE, offset = page * CHAIN_PAGE_SIZE)
                } ?: return null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
            var reachedSince = false
            for (row in rows) {
                val blockTime = row.blockTime ?: continue
                if (blockTime < since) {
                    reachedSince = true
                    continue
                }
                val parsed = chainArenaRow(row, address, me, blockTime) ?: continue
                if (com.kachat.app.util.ChessTournamentCodec.decode(parsed.content)?.t == tournamentId) found += parsed
            }
            if (rows.size < CHAIN_PAGE_SIZE || reachedSince) break
        }
        return found
    }

    private fun chainArenaRow(
        row: com.kachat.app.services.ChainArenaTransaction,
        address: String,
        me: String,
        blockTime: Long,
    ): ChainArenaRow? {
        if (row.isAccepted != true) return null
        val txId = row.transactionId ?: return null
        if (row.inputs.orEmpty().minByOrNull { it.index ?: 0 }?.previousOutpointAddress?.lowercase() != me) return null
        if (row.outputs.orEmpty().minByOrNull { it.index ?: 0 }?.scriptPublicKeyAddress?.lowercase() != me) return null
        val hex = row.payload ?: return null
        val payload = runCatching { hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull() ?: return null
        val parsed = MessageProtocol.parseBcastPayload(payload) ?: return null
        if (MessageProtocol.normalizeChannelName(parsed.channel) != ARENA) return null
        return ChainArenaRow(txId, address, parsed.content, blockTime)
    }

    suspend fun sendBroadcast(channelName: String, content: String, feeRateOverride: Long? = null): String {
        val myAddress = walletManager.getAddress()
        val pendingId = "pending_${java.util.UUID.randomUUID()}"
        database.broadcastDao().insertMessage(
            BroadcastMessageEntity(
                id = pendingId,
                channelName = channelName,
                senderAddress = myAddress,
                content = content,
                blockTimestamp = System.currentTimeMillis(),
                deliveryStatus = "pending"
            )
        )
        try {
            val txId = walletService.sendBroadcast(channelName, content, feeRateOverride = feeRateOverride).txId
            // Signed here, so its sender is known when it comes back from the chain (XP-012).
            senderVerifier.rememberOwnBroadcast(txId, myAddress)
            if (channelName == com.kachat.app.util.ChessTournamentCodec.ARENA_CHANNEL) trackLocalStamp(txId)
            database.broadcastDao().deleteMessage(pendingId)
            database.broadcastDao().insertMessage(
                BroadcastMessageEntity(
                    id = txId,
                    channelName = channelName,
                    senderAddress = myAddress,
                    content = content,
                    blockTimestamp = System.currentTimeMillis(),
                    deliveryStatus = "sent"
                )
            )
            return txId
        } catch (unknown: com.kachat.app.services.SendOutcomeUnknownException) {
            // A node may hold it: its coins stay reserved (KaspaWalletEngine / NodePoolManager).
            // An arena row is kept as sent - a move's ply stays taken - and
            // [checkUnconfirmedServiceRows] settles it: the chain shows it, or it is dropped.
            // Elsewhere the send fails with "may have gone through", never a blind resend
            // (iOS 4922804).
            if (channelName != com.kachat.app.util.ChessTournamentCodec.ARENA_CHANNEL) {
                database.broadcastDao().updateMessageStatus(pendingId, "failed")
                throw unknown
            }
            senderVerifier.rememberOwnBroadcast(unknown.txId, myAddress)
            trackLocalStamp(unknown.txId)
            database.broadcastDao().deleteMessage(pendingId)
            database.broadcastDao().insertMessage(
                BroadcastMessageEntity(
                    id = unknown.txId,
                    channelName = channelName,
                    senderAddress = myAddress,
                    content = content,
                    blockTimestamp = System.currentTimeMillis(),
                    deliveryStatus = "sent"
                )
            )
            return unknown.txId
        } catch (e: Exception) {
            database.broadcastDao().updateMessageStatus(pendingId, "failed")
            throw e
        }
    }

    /** Re-attempts a failed broadcast, reusing its same "pending_<uuid>" id/content — the same placeholder resurrected, not a new message, matching ChatViewModel.retrySendMessage. */
    suspend fun retryBroadcast(message: BroadcastMessageEntity) {
        database.broadcastDao().updateMessageStatus(message.id, "pending")
        try {
            val txId = walletService.sendBroadcast(message.channelName, message.content).txId
            senderVerifier.rememberOwnBroadcast(txId, message.senderAddress)
            database.broadcastDao().deleteMessage(message.id)
            database.broadcastDao().insertMessage(
                BroadcastMessageEntity(
                    id = txId,
                    channelName = message.channelName,
                    senderAddress = message.senderAddress,
                    content = message.content,
                    blockTimestamp = System.currentTimeMillis(),
                    deliveryStatus = "sent"
                )
            )
        } catch (e: Exception) {
            database.broadcastDao().updateMessageStatus(message.id, "failed")
        }
    }

    // Last in the class: Kotlin runs initializers in order, and the collector reads the stamp maps
    // on another thread at once.
    init {
        // Before stamps were kept per account (iOS da59726).
        localStampPrefs.edit().remove(LOCAL_STAMPS_KEY).apply()
        setLocalStampWallet(walletManager.activeAddressFlow.value)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            walletManager.activeAddressFlow.collect { address -> setLocalStampWallet(address) }
        }
    }
}
