package com.kachat.app.services

import com.kachat.app.util.UserFacingError
import android.util.Log
import com.kachat.app.repository.BroadcastRepository
import com.kachat.app.services.database.KaChatDatabase
import com.kachat.app.util.ChessEngine
import com.kachat.app.util.ChessArenaEvent
import com.kachat.app.util.ChessLeaderboardPeriod
import com.kachat.app.util.ChessLeaderboardRow
import com.kachat.app.util.ChessMove
import com.kachat.app.util.ChessTournament
import com.kachat.app.util.ChessTournamentCodec
import com.kachat.app.util.ChessTournamentEngine
import com.kachat.app.util.ChessTournamentGame
import com.kachat.app.util.ChessTournamentMessage
import com.kachat.app.util.KaspaUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chess tournaments (5.1): the `#chess-arena` room, reduced by [ChessTournamentEngine] into the
 * bracket every phone agrees on, and the actions a player can take - each one a broadcast
 * transaction. Mirrors iOS's ChessTournamentService.
 *
 * The arena is machinery, not a chat: it is scanned only while a chess screen holds [acquire],
 * never notifies, and never appears in Public Chats (see [SERVICE_CHANNELS]).
 */
@Singleton
class ChessTournamentService @Inject constructor(
    private val database: KaChatDatabase,
    private val broadcastRepository: BroadcastRepository,
    private val scanningService: BroadcastScanningService,
    private val walletManager: WalletManager,
    private val knsService: KnsService,
    /** Whether the app is on screen: the arena's poll only runs while it is (iOS da59726). */
    private val notificationHelper: NotificationHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tournaments = MutableStateFlow<Map<String, ChessTournament>>(emptyMap())
    val tournaments: StateFlow<Map<String, ChessTournament>> = _tournaments.asStateFlow()

    /** All time (since [ChessTournamentEngine.LEADERBOARD_FRESH_FROM_MS]) and this UTC month: the
     *  indexer's boards (all history) merged with what this phone has read (the newest rows). */
    private val _leaderboard = MutableStateFlow<List<ChessLeaderboardRow>>(emptyList())
    val leaderboard: StateFlow<List<ChessLeaderboardRow>> = _leaderboard.asStateFlow()
    private val _monthlyLeaderboard = MutableStateFlow<List<ChessLeaderboardRow>>(emptyList())
    val monthlyLeaderboard: StateFlow<List<ChessLeaderboardRow>> = _monthlyLeaderboard.asStateFlow()
    fun board(period: ChessLeaderboardPeriod): StateFlow<List<ChessLeaderboardRow>> =
        if (period == ChessLeaderboardPeriod.MONTH) monthlyLeaderboard else leaderboard

    /** Chain time as this phone estimates it: wall clock, for running clocks between blocks. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()


    /** KNS domains for arena players, so a name can fall back to one - see [resolveNames]. */
    private val _knsNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val knsNames: StateFlow<Map<String, String>> = _knsNames.asStateFlow()

    /** Moves sent and not yet seen back from the chain ("<tournament>|<game>"), so the player
     *  cannot double-send. */
    private val _pendingMoveGames = MutableStateFlow<Set<String>>(emptySet())
    val pendingMoveGames: StateFlow<Set<String>> = _pendingMoveGames.asStateFlow()

    private val lock = Any()
    private var refCount = 0
    private var liveViewing: AutoCloseable? = null
    private var clockJob: Job? = null
    private var backfillJob: Job? = null
    private var historyReadyJob: Job? = null
    private var fastPollJob: Job? = null
    /** When a claim was last posted for a game (wall ms). One is enough when it takes; one the
     *  chain voided (a last-second move landed first, a clock ahead of the chain) or that failed
     *  to send is posted again after [CLAIM_RETRY_MS], while the game is still undecided on chain. */
    private val claimedGames = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** The automatic claims of one flag. A claim that keeps not taking is going against a board
     *  that's wrong, so each retry first pages the arena again, and after a few the app stops
     *  paying for them: the board's Claim win is still there (iOS da59726). */
    private data class AutoClaim(val flagAt: Long, val tries: Int = 0, val repaged: Boolean = false)
    private val autoClaims = java.util.concurrent.ConcurrentHashMap<String, AutoClaim>()
    /** One of this player's games has a later move than the board reached: at most every
     *  [GAP_REPAGE_GAP_MS] the arena is paged back to the game's start. */
    @Volatile private var lastGapRepageAt = 0L
    /** When each pending move was sent (wall ms): one the reducer still has not applied after
     *  [PENDING_MOVE_TIMEOUT_MS] stops showing as sending. (One the network dropped is applied from
     *  its local row until [dropUnconfirmedRowsIfDue] takes it out, iOS 4922804.) */
    private val pendingMoveSentAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Games whose claim is being sent right now: the board's Claim win does nothing while one is
     *  (iOS 4922804). */
    private val _claimsInFlight = MutableStateFlow<Set<String>>(emptySet())
    val claimsInFlight: StateFlow<Set<String>> = _claimsInFlight.asStateFlow()
    /** True while the chess server (the arena's indexer) isn't answering: what's on screen is what
     *  this phone has, plus what the block stream brings (iOS da59726). */
    val arenaServerDown: StateFlow<Boolean> get() = broadcastRepository.arenaIndexerDown
    /** While the indexer is down, when each of this player's games last had all its players read
     *  from the REST API (wall ms). */
    private val chainReadAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var nextChainRead = 0L
    /** The account the per-account state above belongs to. */
    @Volatile private var stateAddress: String? = null

    private fun accountChanged(address: String?) {
        if (address == stateAddress) return
        stateAddress = address
        queuedPublicRoomId = null
        _pendingMoveGames.update { emptySet() }
        pendingMoveSentAt.clear()
        claimedGames.clear()
        autoClaims.clear()
        autoRejoinsLeft = 0
        chainReadAt.clear()
        nextChainRead = 0L
        lastGapRepageAt = 0L
        _lastError.value = null
    }

    val myAddress: String? get() = runCatching { walletManager.getAddress() }.getOrNull()

    // MARK: - Watching the arena

    /** A chess screen came on: keep the arena scanned while any is up. */
    fun acquire() = synchronized(lock) {
        refCount += 1
        if (refCount != 1) return@synchronized
        scope.launch {
            runCatching { broadcastRepository.joinChannel(ChessTournamentCodec.ARENA_CHANNEL) }
                .onFailure { Log.w(TAG, "Could not join the arena", it) }
        }
        refreshIndexerLeaderboards()
        // The clock stood still while no chess screen was up: the first checks on this screen must
        // not read a time minutes old.
        _now.value = System.currentTimeMillis()
        liveViewing = scanningService.startLiveViewing(ChessTournamentCodec.ARENA_CHANNEL)
        // The live block scan only ever sees what is mined while it runs, so on its own a phone
        // opening Chess cannot see a seat taken a minute earlier - the two would never find each
        // other in the same room. The broadcast indexer serves the arena's history like a
        // curated room's, so it is read on open and kept fresh while a chess screen is up.
        backfillJob?.cancel()
        _historyReady.value = false
        historyReadyJob?.cancel()
        // Rooms can be picked once the indexer has answered a page (even with nothing), is down or
        // isn't set, or the wait ran out: the whole backfill can take far longer (iOS da59726).
        historyReadyJob = scope.launch {
            val deadline = System.currentTimeMillis() + HISTORY_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                if (broadcastRepository.arenaIndexerAnswered || broadcastRepository.arenaIndexerDown.value ||
                    !runCatching { broadcastRepository.arenaIndexerAddressSet() }.getOrDefault(true)
                ) break
                delay(HISTORY_CHECK_INTERVAL_MS)
            }
            _historyReady.value = true
        }
        backfillJob = scope.launch {
            // Opening a chess screen asks at once whether the arena's indexer is back (iOS da59726).
            broadcastRepository.allowArenaProbeNow()
            runCatching { broadcastRepository.resyncArenaIndexerIfUnset() }
                .onFailure { Log.w(TAG, "Arena indexer resync failed", it) }
            while (isActive) {
                // A chess screen stays up while the app is in the background, with nobody to show
                // a row to: no network until it's back, when the gap is paged in again (iOS da59726).
                if (notificationHelper.isAppInForeground) {
                    runCatching { broadcastRepository.backfillFromIndexer(ChessTournamentCodec.ARENA_CHANNEL) }
                        .onFailure { Log.w(TAG, "Arena backfill failed", it) }
                }
                delay(ARENA_BACKFILL_INTERVAL_MS)
            }
        }
        // While this player is waiting in a room or playing, the arena is pulled every two
        // seconds on top of the 8s backfill and the block scan - the other side's join or move
        // should be on this phone in a couple of seconds, not ten, out of a five-minute clock
        // (iOS 593620e).
        fastPollJob?.cancel()
        fastPollJob = scope.launch {
            while (isActive) {
                delay(FAST_POLL_INTERVAL_MS)
                runCatching { broadcastRepository.resyncArenaIndexerIfUnset() }
                    .onFailure { Log.w(TAG, "Arena indexer resync failed", it) }
                // With the indexer down the room's own poll checks when it's back; this phone's
                // games are read by their players' addresses (iOS da59726).
                if (broadcastRepository.arenaIndexerDown.value) {
                    runCatching { readMyGamesFromChainIfDue() }
                        .onFailure { Log.w(TAG, "Arena chain read failed", it) }
                    continue
                }
                // Only while it matters: a seat held, a game on, or a flag waiting to be claimed.
                // Idle in the lobby, the ordinary backfill is plenty.
                if (!isBusyOnline(_tournaments.value)) continue
                runCatching { broadcastRepository.fetchNewestFromIndexer(ChessTournamentCodec.ARENA_CHANNEL) }
                    .onFailure { Log.w(TAG, "Arena fast poll failed", it) }
            }
        }
        clockJob?.cancel()
        clockJob = scope.launch {
            while (isActive) {
                // Once a second. Five chess composables collect `now`, so a 5 Hz publish
                // re-rendered every chess screen five times a second for a digit only the board's
                // clock chip draws - and that chip runs its own 100 ms timeline in the last ten
                // seconds instead (iOS cbf7f26). A timeout claim does not need 200 ms precision:
                // the clock it reads is chain time, and the claim is idempotent.
                delay(1_000)
                _now.value = System.currentTimeMillis()
                expirePendingMoves()
                dropUnconfirmedRowsIfDue()
                claimTimeoutsIfDue()
                // The 1st of the month (UTC) came while a chess screen was up: a new month board.
                val monthStart = boardsMonthStart
                if (monthStart != 0L && ChessTournamentEngine.utcMonthStartMs(_now.value) != monthStart) {
                    recomputeLocalBoards()
                    refreshIndexerLeaderboards(force = true)
                }
            }
        }
    }

    fun release() = synchronized(lock) {
        refCount = maxOf(0, refCount - 1)
        if (refCount != 0) return@synchronized
        liveViewing?.close()
        liveViewing = null
        backfillJob?.cancel()
        backfillJob = null
        historyReadyJob?.cancel()
        historyReadyJob = null
        fastPollJob?.cancel()
        fastPollJob = null
        clockJob?.cancel()
        clockJob = null
    }

    /**
     * The indexer is down: this player's live games, and the ones just flagged, from the two
     * players' addresses. It fills in what the block stream missed (a reconnect, the app in the
     * background) and is what lets a timeout claim go out ([flagIsConfirmed]), which rests on the
     * opponent's address alone: their moves are what could be missing (iOS da59726).
     */
    private suspend fun readMyGamesFromChainIfDue() {
        val me = myAddress ?: return
        val now = System.currentTimeMillis()
        if (now < nextChainRead) return
        nextChainRead = now + CHAIN_READ_INTERVAL_MS
        for (tournament in _tournaments.value.values) {
            if (tournament.status != ChessTournament.Status.LIVE) continue
            for (game in tournament.games.values) {
                val mine = game.color(me) ?: continue
                val flagAt = game.flagAt ?: continue
                if (now - flagAt >= AUTO_CLAIM_WINDOW_MS) continue
                val askedAt = System.currentTimeMillis()
                val answered = broadcastRepository.readArenaFromChain(
                    tournamentId = tournament.id, players = listOf(game.address(mine.opposite)), since = game.startedAt,
                )
                broadcastRepository.readArenaFromChain(
                    tournamentId = tournament.id, players = listOf(me), since = game.startedAt,
                )
                if (answered) chainReadAt["${tournament.id}|${game.id}"] = askedAt
            }
        }
    }

    /** Whether the chain has been read since this flag: the moves on this phone can be behind
     *  (just relaunched, or the indexer down), and a clock judged from those says nothing
     *  (iOS da59726). */
    private fun flagIsConfirmed(tournament: ChessTournament, game: ChessTournamentGame, flagAt: Long): Boolean {
        if (!broadcastRepository.arenaHasIndexer) return true
        if (broadcastRepository.arenaIndexerAnsweredAt >= flagAt + 1_500) return true
        return (chainReadAt["${tournament.id}|${game.id}"] ?: 0L) >= flagAt + CHAIN_FLAG_MARGIN_MS
    }

    /** One reduce at a time, off the main thread: the DAO flow's, and the one a join runs on the
     *  freshest rows before it picks a room. Rows that change while one runs get one more replay
     *  after it, of the rows as they stand then (the collector is conflated, iOS 4922804). */
    private val reduceMutex = Mutex()

    private fun reduce(rows: List<com.kachat.app.models.BroadcastMessageEntity>) {
        val events = rows.mapNotNull { row ->
            if (row.deliveryStatus != "sent" || row.id.startsWith("pending_")) return@mapNotNull null
            val message = ChessTournamentCodec.decode(row.content) ?: return@mapNotNull null
            ChessArenaEvent(row.id, row.senderAddress, row.blockTimestamp, message)
        }
        val mineAddress = myAddress
        val reduced = ChessTournamentEngine.reduce(orderingLocalStamps(events, broadcastRepository.locallyStampedTxIds.toSet()))
        val wallNow = System.currentTimeMillis()
        val gapSince = mineAddress?.let { earliestGap(events, reduced, it, wallNow) }
        _tournaments.value = reduced
        if (gapSince != null && wallNow - lastGapRepageAt >= GAP_REPAGE_GAP_MS) {
            lastGapRepageAt = wallNow
            broadcastRepository.repageArena(gapSince)
        }
        resolveNames(reduced.values.flatMap { it.players }.toSet() + listOfNotNull(mineAddress))
        recomputeLocalBoards()
        // Asked to join a public room that filled first: queue into the next one. Bounded per tap
        // ([autoRejoinsLeft]), so a join can never turn into spending nobody asked for (iOS da59726).
        val queued = queuedPublicRoomId
        val me = myAddress
        if (queued != null && me != null) {
            val room = reduced[queued]
            if (room != null && room.isFull && me !in room.players) {
                queuedPublicRoomId = null
                if (autoRejoinsLeft > 0) {
                    autoRejoinsLeft -= 1
                    val wasDuel = ChessTournamentCodec.duelNumber(queued) != null
                    scope.launch { joinPublicRoom(duel = wasDuel, automaticAfter = queued) }
                } else {
                    _lastError.value = ROOM_FILLED_MESSAGE
                }
            } else if (room != null && me in room.players) {
                queuedPublicRoomId = null
            }
        }
        // A move of ours that the chain now shows is no longer pending.
        val mine = myAddress
        _pendingMoveGames.update { pending -> pending.filter { key ->
            val (tid, gid) = key.split('|', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return@filter true
            val game = reduced[tid]?.games?.get(gid) ?: return@filter true
            game.playerToMove == mine && !game.isOver
        }.toSet() }
        pendingMoveSentAt.keys.retainAll(_pendingMoveGames.value)
    }

    /** A game of [me]'s still undecided has a move from one of its players beyond the next ply: a
     *  row before it is missing on this phone. Returns the earliest such game's start (iOS da59726). */
    private fun earliestGap(events: List<ChessArenaEvent>, tournaments: Map<String, ChessTournament>, me: String, now: Long): Long? {
        var since: Long? = null
        for (event in events) {
            if (event.message.a != "move") continue
            val gameId = event.message.g ?: continue
            val ply = event.message.n ?: continue
            val game = tournaments[event.message.t]?.games?.get(gameId) ?: continue
            if (game.color(me) == null || game.color(event.sender) == null) continue
            if (ply <= game.moves.size + 1 || game.isDecided(now)) continue
            since = minOf(since ?: game.startedAt, game.startedAt)
        }
        return since
    }

    /**
     * Our own rows carry this phone's clock until the chain's block time reaches us
     * ([BroadcastRepository.locallyStampedTxIds]). A clock ahead of the chain put our move after
     * the reply to it, a clock behind before the move it answers - either way a move out of turn,
     * ignored, and a board stuck until the chain's time came. Until then such a move sits between
     * the moves of its game around it by ply, whatever the local stamp says. This phone's view
     * only: every phone still reduces the chain's times once they are known (iOS 8d5f925).
     */
    private fun orderingLocalStamps(events: List<ChessArenaEvent>, localStamps: Set<String>): List<ChessArenaEvent> {
        if (localStamps.isEmpty()) return events
        return events.map { event ->
            val message = event.message
            val gameId = message.g
            val ply = message.n
            if (event.txId !in localStamps || message.a != "move" || gameId == null || ply == null) return@map event
            var lower: Long? = null
            var upper: Long? = null
            for (other in events) {
                if (other.txId == event.txId || other.message.t != message.t || other.message.g != gameId || other.message.a != "move") continue
                val otherPly = other.message.n ?: continue
                if (otherPly < ply) lower = maxOf(lower ?: other.blockTime, other.blockTime)
                if (otherPly > ply) upper = minOf(upper ?: other.blockTime, other.blockTime)
            }
            var time = event.blockTime
            upper?.let { time = minOf(time, it - 1) }
            lower?.let { time = maxOf(time, it + 1) }
            if (time == event.blockTime) event else event.copy(blockTime = time)
        }
    }

    /** Set while an unconfirmed-row check runs, so the next tick does not start another. */
    private val checkingUnconfirmed = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Own arena transactions the network never took ([UNCONFIRMED_ROW_MS]) stop counting on this
     * phone: the reducer drops them and the board is the chain's again - a dropped move is the
     * player's to play again, and a timeout claim the board was waiting on goes out after all.
     * Only once the arena's history is in, so a row the chain showed while the app was closed is
     * found there first (iOS 4922804).
     */
    private fun dropUnconfirmedRowsIfDue() {
        if (!_historyReady.value || !checkingUnconfirmed.compareAndSet(false, true)) return
        // The rooms as they stood before the drop: the DAO flow may reduce the dropped rows away
        // before the check returns.
        val before = _tournaments.value
        scope.launch {
            try {
                val dropped = runCatching {
                    broadcastRepository.checkUnconfirmedServiceRows(
                        olderThanMs = UNCONFIRMED_ROW_MS, giveUpAfterMs = UNCONFIRMED_GIVE_UP_MS,
                    )
                }.onFailure { Log.w(TAG, "Unconfirmed arena row check failed", it) }.getOrDefault(emptySet())
                if (dropped.isEmpty()) return@launch
                val wasMove = before.values.any { tournament ->
                    tournament.games.values.any { game -> game.moves.any { it.txId in dropped } }
                }
                _lastError.value = if (wasMove) {
                    "Your move didn't reach the network. Play it again."
                } else {
                    "That didn't reach the network. Try again."
                }
            } finally {
                checkingUnconfirmed.set(false)
            }
        }
    }

    /** A move the reducer never applied (it came too late for the clock, say): the board stops
     *  saying "Sending your move…" (iOS 4922804). */
    private fun expirePendingMoves() {
        val now = _now.value
        for ((key, sentAt) in pendingMoveSentAt.entries.toList()) {
            if (now - sentAt <= PENDING_MOVE_TIMEOUT_MS) continue
            pendingMoveSentAt.remove(key)
            _pendingMoveGames.update { it - key }
            _lastError.value = "Your move didn't count. Check the board."
        }
    }

    // MARK: - Lists

    /** The public tournament room taking players right now - see [currentRoomNumber]. Its id
     *  exists before anyone has joined it (the first join creates it), so the lobby can always
     *  show "Public tournament #N" with its seats. */
    fun currentPublicRoomId(all: Map<String, ChessTournament>): String =
        ChessTournamentCodec.publicId(currentRoomNumber(all, { ChessTournamentCodec.publicNumber(it) }, { ChessTournamentCodec.publicId(it) }))

    /** The public 1v1 room taking players right now. */
    fun currentDuelRoomId(all: Map<String, ChessTournament>): String =
        ChessTournamentCodec.duelId(currentRoomNumber(all, { ChessTournamentCodec.duelNumber(it) }, { ChessTournamentCodec.duelId(it) }))

    /**
     * The room to queue into: the lowest-numbered public room of that kind still waiting for
     * players; when none is, one past the highest room this phone knows. Every phone with the
     * same recent history lands on the same number - and it does not need the history back to
     * room 1, which is why the engine no longer requires the previous room to be full
     * (iOS d2ab780).
     *
     * Anyone can post a join naming any room, so this is the choice of a room to pick, never the
     * reducer's view of what rooms exist: only a room's canonical id counts (`duel-01` is not room
     * 1), nothing past [MAX_ROOM_NUMBER], and of the runs of numbers near each other only the
     * longest (the real queue), so a stray `duel-900000` can't move it (iOS da59726).
     */
    private fun currentRoomNumber(all: Map<String, ChessTournament>, numberOf: (String) -> Int?, idOf: (Int) -> String): Int {
        val rooms = ArrayList<Pair<Int, Boolean>>()
        for (room in all.values) {
            val number = numberOf(room.id) ?: continue
            if (number > MAX_ROOM_NUMBER || idOf(number) != room.id) continue
            rooms += number to (room.status == ChessTournament.Status.OPEN && !room.isFull)
        }
        rooms.sortBy { it.first }
        var best: List<Pair<Int, Boolean>> = emptyList()
        var start = 0
        for (index in rooms.indices) {
            if (index + 1 != rooms.size && rooms[index + 1].first - rooms[index].first <= MAX_ROOM_GAP) continue
            val run = rooms.subList(start, index + 1)
            if (run.size > best.size) best = run
            start = index + 1
        }
        return best.firstOrNull { it.second }?.first ?: ((best.lastOrNull()?.first ?: 0) + 1)
    }

    /**
     * True once the arena's history has come back from the indexer (or a few seconds have
     * passed). A join before that would pick a room from an empty view - room 1 - which
     * everyone else finished long ago.
     */
    private val _historyReady = MutableStateFlow(false)
    val historyReady: StateFlow<Boolean> = _historyReady.asStateFlow()

    /** Private 1v1s this player is in, still open or in play. */
    fun myPrivateDuels(all: Map<String, ChessTournament>) = myPrivate(all, duel = true)

    /** Private tournaments this player is in, still open or in play. */
    fun myPrivateTournaments(all: Map<String, ChessTournament>) = myPrivate(all, duel = false)

    private fun myPrivate(all: Map<String, ChessTournament>, duel: Boolean): List<ChessTournament> {
        val me = myAddress ?: return emptyList()
        val now = _now.value
        return all.values
            .filter {
                !it.isPublic && it.isDuel == duel && me in it.players &&
                    (it.status == ChessTournament.Status.OPEN || it.isInPlay(now) || hasUnclaimedWin(it))
            }
            .sortedByDescending { it.createdAt }
    }

    /** A game here this player has won on the clock, with no claim on chain. Finished lists public
     *  games only, so this keeps a private one reachable, for its Claim win (iOS da59726). */
    fun hasUnclaimedWin(tournament: ChessTournament): Boolean {
        val me = myAddress ?: return false
        if (tournament.status != ChessTournament.Status.LIVE) return false
        val now = _now.value
        return tournament.games.values.any { !it.isOver && it.decidedWinner(now) == me }
    }

    fun openTournaments(all: Map<String, ChessTournament>) =
        all.values.filter { it.status == ChessTournament.Status.OPEN }.sortedByDescending { it.createdAt }

    /** Rooms with a game still being played. A live room whose clocks have all run out with no
     *  claim posted is not one of them (see [ChessTournamentGame.flaggedSide]). */
    fun liveTournaments(all: Map<String, ChessTournament>): List<ChessTournament> {
        val now = _now.value
        return all.values.filter { it.isInPlay(now) }.sortedByDescending { it.startedAt ?: 0 }
    }

    /** Finished on the chain, or finished on the clock with the claim still to come. */
    fun finishedTournaments(all: Map<String, ChessTournament>): List<ChessTournament> {
        val now = _now.value
        return all.values
            .filter {
                it.status == ChessTournament.Status.FINISHED ||
                    (it.status == ChessTournament.Status.LIVE && !it.isInPlay(now))
            }
            .sortedByDescending { it.startedAt ?: 0 }
    }

    /** The tournament this player is in that is not over, if any. A waiting seat that has
     *  expired does not count: the player is free to join elsewhere. */
    fun myActiveTournament(all: Map<String, ChessTournament>): ChessTournament? {
        val me = myAddress ?: return null
        val now = _now.value
        return all.values
            .filter {
                // Out of a live room once you have lost a game in it, on the board or on the
                // clock with the claim still to come - otherwise a winner who had left kept the
                // loser "busy" forever and unable to join another room (iOS a94f0e8).
                it.isStillPlaying(me, now) ||
                    (it.status == ChessTournament.Status.OPEN && it.isSeated(me, now))
            }
            .maxByOrNull { it.createdAt }
    }

    /**
     * Too busy online for a challenge link to open a bot screen: the rooms are still loading (a
     * game in play may not be known yet), the player is seated or playing, or a game of theirs was
     * decided on the clock lately with the claim not on chain yet - its winner is still on the
     * board to claim it (iOS 4922804).
     */
    fun isBusyOnline(all: Map<String, ChessTournament>): Boolean {
        if (!_historyReady.value) return true
        if (myActiveTournament(all) != null) return true
        val me = myAddress ?: return false
        val now = _now.value
        return all.values.any { tournament ->
            tournament.status == ChessTournament.Status.LIVE && tournament.games.values.any { game ->
                val flagAt = game.flagAt
                game.color(me) != null && flagAt != null && now - flagAt < AUTO_CLAIM_WINDOW_MS
            }
        }
    }

    // MARK: - Actions (each one a broadcast transaction)

    /** The room this player asked to join and is waiting to appear in. */
    @Volatile
    private var queuedPublicRoomId: String? = null

    /** How many more rooms the app may try by itself before the player taps Join again. */
    @Volatile
    private var autoRejoinsLeft = 0

    /** Whether the rooms on this phone are the indexer's as of just now, with any history still
     *  owed paged in. One failed request isn't an outage: it's tried again, and an answer from the
     *  last few seconds stands (iOS da59726). */
    private suspend fun arenaViewIsConfirmed(): Boolean {
        val channel = ChessTournamentCodec.ARENA_CHANNEL
        if (!broadcastRepository.arenaIndexerConfigured()) return true
        var confirmed = broadcastRepository.refreshFromIndexerNow(channel)
        if (!confirmed && !broadcastRepository.arenaIndexerDown.value) {
            delay(800)
            confirmed = broadcastRepository.refreshFromIndexerNow(channel)
        }
        if (!confirmed && System.currentTimeMillis() - broadcastRepository.arenaIndexerAnsweredAt >= RECENT_ANSWER_MS) return false
        // Past one poll interval: paging is done by the room's 8 s poll.
        val deadline = System.currentTimeMillis() + ARENA_BACKFILL_WAIT_MS
        while (!broadcastRepository.arenaBackfillDone && System.currentTimeMillis() < deadline) delay(250)
        return broadcastRepository.arenaBackfillDone
    }

    /** Joins the public room taking players now. If that room fills before this join lands
     *  (someone else got the last seat), [reduce] notices and joins the next room. */
    suspend fun joinPublicQueue(): String? = withContext(NonCancellable) { joinPublicRoom(duel = false) }

    /** Joins the public 1v1 room taking players now; same race handling as the tournaments. */
    suspend fun joinPublicDuelQueue(): String? = withContext(NonCancellable) { joinPublicRoom(duel = true) }

    /**
     * The seat that ran out is still in `players` until the next join drops it (the engine judges
     * that at the join's block time) - so "already in" means seated NOW, never the stale list, or
     * a returning player's tap would do nothing at all. Every refusal says why (iOS b552d7d).
     *
     * Returns the room joined (or the one the player is already in), so the screen can open its
     * waiting room - and null when nothing was sent. [automaticAfter]: this is the app moving on
     * from a room that filled first, not a tap (iOS da59726).
     */
    private suspend fun joinPublicRoom(duel: Boolean, automaticAfter: String? = null): String? {
        val me = myAddress ?: return null
        if (!_historyReady.value) {
            _lastError.value = "Still loading the rooms - try again in a moment."
            return null
        }
        if (automaticAfter == null) autoRejoinsLeft = MAX_AUTO_REJOINS
        // The freshest shared view first: whatever the indexer holds this second is what every
        // other phone is choosing from, and a phone choosing off its own stale view offers a room
        // the others have moved past (iOS 29bf054). The DAO flow delivers the merge a beat later,
        // so the rows are reduced here and now rather than read off the old state.
        // Without that view the room number and its seats are only what this phone last saw:
        // nothing is sent. (No indexer configured at all: the block stream is all there is.)
        val confirmed = runCatching { arenaViewIsConfirmed() }
            .onFailure { Log.w(TAG, "Arena refresh before joining failed", it) }
            .getOrDefault(false)
        if (!confirmed) {
            _lastError.value = ROOMS_UNCONFIRMED_MESSAGE
            return null
        }
        runCatching {
            withContext(Dispatchers.Default) {
                reduceMutex.withLock {
                    reduce(database.broadcastDao().getMessagesForChannel(ChessTournamentCodec.ARENA_CHANNEL).first())
                }
            }
        }.onFailure { Log.w(TAG, "Arena reduce before joining failed", it) }
        // The account may have changed while the rooms loaded.
        if (myAddress != me) return null
        val id = if (duel) currentDuelRoomId(_tournaments.value) else currentPublicRoomId(_tournaments.value)
        // Never a join the reducer would ignore: it can't take a seat, and it costs a fee.
        val targetRoom = _tournaments.value[id]
        if (id == automaticAfter || (targetRoom != null && targetRoom.status != ChessTournament.Status.OPEN)) {
            _lastError.value = ROOM_FILLED_MESSAGE
            return null
        }
        val busy = myActiveTournament(_tournaments.value)
        if (busy != null) {
            _lastError.value = if (busy.status == ChessTournament.Status.OPEN) {
                "You're already waiting in ${busy.name}."
            } else {
                "You're still playing in ${busy.name}."
            }
            return null
        }
        // Already seated here: no transaction, but the room is still where this player belongs.
        if (_tournaments.value[id]?.isSeated(me, _now.value) == true) return id
        queuedPublicRoomId = id
        if (send(ChessTournamentCodec.join(id))) return id
        // A join that did not go out queues nothing: the next room must not be joined for it -
        // unless the queue has already moved on to another room while it was being sent
        // (iOS 4922804).
        if (queuedPublicRoomId == id) queuedPublicRoomId = null
        return null
    }

    /** A private 1v1 for a friend: no creator code, an eight-character code to share. */
    suspend fun createPrivateDuel(): String? {
        val id = ChessTournamentCodec.newPrivateId()
        if (!send(ChessTournamentCodec.createDuel(id, ChessTournamentCodec.PRIVATE_DUEL_NAME))) return null
        return id
    }

    /** A private tournament for friends. Needs the creator code; returns null (with a message)
     *  when it is wrong, without sending anything. */
    suspend fun createPrivateTournament(code: String): String? {
        if (!ChessTournamentCodec.isValidCreateKey(ChessTournamentCodec.createKey(code, "check"), "check")) {
            _lastError.value = "That creator code is not right."
            return null
        }
        val id = ChessTournamentCodec.newPrivateId()
        if (!send(ChessTournamentCodec.create(id, ChessTournamentCodec.PRIVATE_TOURNAMENT_NAME, code))) return null
        return id
    }

    /** Joins a friend's private tournament or 1v1 by its code (the id). */
    suspend fun joinPrivate(rawCode: String): Boolean {
        val id = rawCode.trim().lowercase()
        val tournament = _tournaments.value[id]
        if (tournament == null || tournament.isPublic) {
            _lastError.value = if (broadcastRepository.arenaIndexerDown.value) {
                "The chess server is unreachable, so that code can't be looked up. Try again when it's back."
            } else {
                "No open tournament with that code. Codes are eight characters; the tournament must exist and still have seats."
            }
            return false
        }
        if (tournament.status != ChessTournament.Status.OPEN) {
            _lastError.value = "That tournament has already started."
            return false
        }
        return join(tournament)
    }

    /** True when the player is seated or the join went out; false (with [lastError]) when
     *  nothing was sent. */
    suspend fun join(tournament: ChessTournament): Boolean {
        val me = myAddress ?: return false
        if (tournament.status != ChessTournament.Status.OPEN) return false
        if (tournament.isSeated(me, _now.value)) return true
        val busy = myActiveTournament(_tournaments.value)
        if (busy != null && busy.id != tournament.id) {
            _lastError.value = if (busy.status == ChessTournament.Status.OPEN) {
                "You're already waiting in ${busy.name}."
            } else {
                "You're still playing in ${busy.name}."
            }
            return false
        }
        if (tournament.isPublic && broadcastRepository.arenaIndexerDown.value) {
            _lastError.value = ROOMS_UNCONFIRMED_MESSAGE
            return false
        }
        return send(ChessTournamentCodec.join(tournament.id))
    }

    /** Gives the seat back while the room is still waiting (one transaction). True when the
     *  leave went out (or there was no seat to give back). */
    suspend fun leave(tournament: ChessTournament): Boolean {
        val me = myAddress ?: return true
        if (tournament.status != ChessTournament.Status.OPEN || !tournament.isSeated(me, _now.value)) return true
        queuedPublicRoomId = null
        return send(ChessTournamentCodec.leave(tournament.id))
    }

    suspend fun cancel(tournament: ChessTournament) {
        if (tournament.creator != myAddress || tournament.status != ChessTournament.Status.OPEN) return
        send(ChessTournamentCodec.cancel(tournament.id))
    }

    /** Plays a move if it is ours to make and legal; the board shows it as pending until the
     *  chain returns it. Finishes even when the board is left mid-send, so its pending state is
     *  always settled (an iOS Task outlives its view the same way). */
    suspend fun play(move: ChessMove, tournament: ChessTournament, game: ChessTournamentGame) =
        withContext(NonCancellable) { playNow(move, tournament, game) }

    private suspend fun playNow(move: ChessMove, tournament: ChessTournament, game: ChessTournamentGame) {
        val me = myAddress ?: return
        val key = "${tournament.id}|${game.id}"
        // Not once the clock has decided it: a move after the flag is void on chain (iOS 4922804).
        if (game.playerToMove != me || game.isDecided(_now.value) || key in _pendingMoveGames.value ||
            !ChessEngine.isLegal(move, game.board)) return
        val normalized = ChessEngine.normalizingPromotion(move, game.board)
        _pendingMoveGames.update { it + key }
        val sent = send(
            ChessTournamentCodec.move(
                id = tournament.id, game = game.id, ply = game.moves.size + 1,
                from = normalized.from.algebraic, to = normalized.to.algebraic,
                promotion = normalized.promotion?.promotionLetter,
            )
        )
        if (myAddress != me) return
        if (sent) {
            // Timed from the moment it went out, if the chain has not shown it already.
            if (key in _pendingMoveGames.value) pendingMoveSentAt[key] = System.currentTimeMillis()
        } else {
            _pendingMoveGames.update { it - key }
            pendingMoveSentAt.remove(key)
        }
    }

    /** True when the resignation went out; false (with [lastError]) when it did not, and the
     *  game goes on - the board must keep the player (iOS 4922804). */
    suspend fun resign(tournament: ChessTournament, game: ChessTournamentGame): Boolean {
        val me = myAddress ?: return false
        if (game.color(me) == null || game.isOver) return false
        return send(ChessTournamentCodec.resign(tournament.id, game.id))
    }

    // MARK: - Names

    /** Addresses whose KNS domain was asked for this session. */
    private val resolvedNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Names in the arena follow the app's rule - the contact's name, then their KNS domain, then
     * the shortened address - and the domain part needs a lookup. Asked once per address per
     * session; [knsNames] then answers, and the screens re-render when it lands (iOS 30d0cca).
     */
    fun resolveNames(addresses: Set<String>) {
        val fresh = addresses.filter { it.isNotEmpty() && resolvedNames.add(it) }
        if (fresh.isEmpty()) return
        scope.launch {
            for (address in fresh) {
                val domain = runCatching { knsService.identityName(address) }.getOrNull()
                if (!domain.isNullOrBlank()) _knsNames.update { it + (address to domain) }
            }
        }
    }

    // MARK: - Leaderboards

    /** One period's board as the indexer served it: the window it counted, and its rows. */
    data class IndexerBoard(val since: Long, val rows: List<ChessLeaderboardRow>)

    private val boardsLock = Any()
    private var localBoards: Map<ChessLeaderboardPeriod, List<ChessLeaderboardRow>> = emptyMap()
    private val indexerBoards = HashMap<ChessLeaderboardPeriod, IndexerBoard>()
    @Volatile
    private var boardsMonthStart = 0L
    private var lastIndexerLeaderboardFetch: Long? = null
    private var indexerLeaderboardJob: Job? = null

    private fun recomputeLocalBoards() {
        val nowMs = System.currentTimeMillis()
        val all = _tournaments.value.values
        val boards = ChessLeaderboardPeriod.entries.associateWith { period ->
            ChessTournamentEngine.leaderboard(all, since = ChessTournamentEngine.periodSinceMs(period, nowMs))
        }
        synchronized(boardsLock) {
            boardsMonthStart = ChessTournamentEngine.utcMonthStartMs(nowMs)
            localBoards = boards
            publishBoards()
        }
    }

    /**
     * Per player, each figure is the larger of the two sources: both count the same window, the
     * indexer has the older history and this phone the newest blocks, so neither can be above
     * the true count. An indexer board for another window (an old indexer without `period`, or a
     * month that has since rolled over) is not used. Call with [boardsLock] held.
     */
    private fun publishBoards() {
        val nowMs = System.currentTimeMillis()
        fun merged(period: ChessLeaderboardPeriod) = mergedBoard(
            localBoards[period].orEmpty(),
            usableIndexerRows(period, indexerBoards[period], nowMs),
        )
        val all = merged(ChessLeaderboardPeriod.ALL_TIME)
        val month = merged(ChessLeaderboardPeriod.MONTH)
        if (all != _leaderboard.value) _leaderboard.value = all
        if (month != _monthlyLeaderboard.value) _monthlyLeaderboard.value = month
    }

    /**
     * GET /chess/leaderboard?period=month|all on the arena's indexer (ONLINE_CHESS.md §6). At
     * most every 20 s unless forced; any failure leaves the phone's own boards in place.
     */
    fun refreshIndexerLeaderboards(force: Boolean = false) {
        synchronized(boardsLock) {
            val last = lastIndexerLeaderboardFetch
            if (!force && last != null && System.currentTimeMillis() - last < INDEXER_LEADERBOARD_INTERVAL_MS) return
            lastIndexerLeaderboardFetch = System.currentTimeMillis()
            indexerLeaderboardJob?.cancel()
            indexerLeaderboardJob = scope.launch {
                for (period in ChessLeaderboardPeriod.entries) {
                    val response = broadcastRepository.fetchChessLeaderboard(ChessTournamentCodec.ARENA_CHANNEL, period.raw)
                        ?: continue
                    val board = acceptedIndexerBoard(period, response) ?: continue
                    if (!isActive) return@launch
                    synchronized(boardsLock) {
                        indexerBoards[period] = board
                        publishBoards()
                    }
                    resolveNames(board.rows.take(100).map { it.address }.toSet())
                }
            }
        }
    }

    // MARK: - Fees

    /**
     * What sending [message] costs right now, as "0.0017 KAS", for a button label. An arena
     * message is a fixed-size payload with one input, like every arena send, so this is the same
     * local calculation the composers use - no network round trip (iOS 3076f66).
     */
    fun feeText(message: ChessTournamentMessage): String? = runCatching {
        val payload = com.kachat.app.util.MessageProtocol.buildBcastPayload(
            ChessTournamentCodec.ARENA_CHANNEL, ChessTournamentCodec.encode(message),
        )
        val mass = com.kachat.app.util.KaspaMass.calculateMass(
            numInputs = 1, outputScriptLens = listOf(34), payloadSize = payload.size,
        )
        val sompi = com.kachat.app.util.KaspaMass.calculateFee(
            mass, com.kachat.app.util.KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM.toLong(),
        )
        // Four decimals: "0.0017 KAS" reads at a glance; the exact sompi is in the transaction.
        "%.4f %s".format(sompi / 100_000_000.0, KaspaUnit.symbol)
    }.getOrNull()

    /** The join button's label: "Join (Fee: 0.0017 KAS)". */
    fun joinLabel(roomId: String): String {
        val fee = feeText(ChessTournamentCodec.join(roomId)) ?: return "Join"
        return "Join (Fee: $fee)"
    }

    fun clearError() {
        _lastError.value = null
    }

    /** The opponent flagged in one of our games, lately: post the claim. Runs on every tick; a
     *  claim that has not taken after [CLAIM_RETRY_MS] (voided, or not sent) goes again. */
    private fun claimTimeoutsIfDue() {
        val me = myAddress ?: return
        val now = _now.value
        val localStamps = broadcastRepository.locallyStampedTxIds
        for (tournament in _tournaments.value.values) {
            if (tournament.status != ChessTournament.Status.LIVE) continue
            for (game in tournament.games.values) {
                if (game.isOver) continue
                val mine = game.color(me) ?: continue
                if (game.sideToMove == mine) continue
                val flagAt = game.flagAt ?: continue
                // A second's margin past zero, so the claim's block time is safely after; and only
                // a recent flag - an old abandoned game is left to the board's Claim win.
                if (now - 1_500 < flagAt || now - flagAt >= AUTO_CLAIM_WINDOW_MS) continue
                // Not off a board that may be behind the chain (iOS da59726).
                if (!flagIsConfirmed(tournament, game, flagAt)) continue
                // Our own last move still carries this phone's clock: the opponent's clock started
                // at an instant the chain has not confirmed yet - wait for it.
                val last = game.moves.lastOrNull()
                if (last != null && last.txId in localStamps) continue
                postClaim(tournament, game)
            }
        }
    }

    /** The board's Claim win, for a game the opponent has lost on time with no claim on chain yet -
     *  the way off the board for its winner that is not Resign (iOS 8d5f925). Repeated taps are one
     *  claim: nothing while one is being sent or within a few seconds of the last (iOS 4922804). */
    fun claimWin(tournament: ChessTournament, game: ChessTournamentGame) {
        val me = myAddress ?: return
        if (game.decidedWinner(_now.value) != me || game.isOver) return
        val at = claimedGames["${tournament.id}|${game.id}"]
        if (at != null && _now.value - at < CLAIM_TAP_GAP_MS) return
        if (broadcastRepository.arenaIndexerDown.value && !flagIsConfirmed(tournament, game, game.flagAt ?: _now.value)) {
            _lastError.value = "The chess server is unreachable and the network hasn't confirmed this yet. Try again in a moment."
            return
        }
        postClaim(tournament, game, force = true)
    }

    private fun postClaim(tournament: ChessTournament, game: ChessTournamentGame, force: Boolean = false) {
        val key = "${tournament.id}|${game.id}"
        if (key in _claimsInFlight.value) return
        val now = _now.value
        var autoClaim: AutoClaim? = null
        if (!force) {
            val at = claimedGames[key]
            if (at != null && now - at < CLAIM_RETRY_MS) return
            val flagAt = game.flagAt ?: 0L
            val record = autoClaims[key]?.takeIf { it.flagAt == flagAt } ?: AutoClaim(flagAt)
            if (record.tries >= MAX_AUTO_CLAIMS) return
            // A claim that didn't take: read the arena again before paying for another. The next
            // tick waits on [flagIsConfirmed] until that's done (iOS da59726).
            if (record.tries > 0 && !record.repaged) {
                autoClaims[key] = record.copy(repaged = true)
                broadcastRepository.repageArena(game.startedAt)
                return
            }
            autoClaim = record
        }
        // A tap comes in on the main thread and the clock's tick on another, where iOS has them
        // take turns on the main actor: whoever adds the key is the one that pays.
        if (key in _claimsInFlight.getAndUpdate { it + key }) return
        autoClaim?.let { autoClaims[key] = it.copy(tries = it.tries + 1, repaged = false) }
        claimedGames[key] = now
        scope.launch {
            try {
                send(ChessTournamentCodec.claim(tournament.id, game.id))
            } finally {
                _claimsInFlight.update { it - key }
            }
        }
    }

    fun isClaiming(tournament: ChessTournament, game: ChessTournamentGame, inFlight: Set<String> = _claimsInFlight.value): Boolean =
        "${tournament.id}|${game.id}" in inFlight

    /**
     * One broadcast transaction, with the retries a fast sequence of sends needs: the change of
     * the previous transaction is not spendable until it is mined, so a move sent within a second
     * of the last one can hit "no spendable UTXO" - the same retry iOS uses. Only that kind of
     * error is retried ([isRetryableSendError]): no KAS on the chatting address fails at once with
     * its message (iOS 8d5f925).
     */
    private suspend fun send(message: ChessTournamentMessage): Boolean = withContext(NonCancellable) { sendNow(message) }

    private suspend fun sendNow(message: ChessTournamentMessage): Boolean {
        val content = ChessTournamentCodec.encode(message)
        val sender = myAddress
        var attempt = 0
        while (true) {
            try {
                broadcastRepository.sendBroadcast(ChessTournamentCodec.ARENA_CHANNEL, content)
                _lastError.value = null
                return true
            } catch (e: SendOutcomeUnknownException) {
                // A node may hold it: the move's ply stays taken, the join or claim counts as out,
                // and the unconfirmed-row check settles it - the chain shows it, or it is dropped
                // ([dropUnconfirmedRowsIfDue]). Never a blind resend (iOS 4922804).
                Log.w(TAG, "Send outcome unknown", e)
                _lastError.value = null
                return true
            } catch (e: Exception) {
                attempt += 1
                // A switched account: nothing more goes out for the old one.
                if (myAddress != sender) return false
                if (attempt > 5 || !isRetryableSendError(e)) {
                    _lastError.value = friendlySendErrorMessage(e)
                    Log.w(TAG, "Send failed", e)
                    return false
                }
                delay(1_200)
            }
        }
    }

    // Last in the class: Kotlin runs initializers in order, and these collectors reduce on
    // another thread at once, so every field they touch is set before they start.
    init {
        scope.launch {
            database.broadcastDao().getMessagesForChannel(ChessTournamentCodec.ARENA_CHANNEL)
                .distinctUntilChanged()
                .conflate()
                .collect { rows -> reduceMutex.withLock { reduce(rows) } }
        }
        // A queued join, a pending move or a claim belongs to the account that made it: after a
        // log out or a switch they must never be sent from the next one (iOS 8d5f925).
        scope.launch {
            walletManager.activeAddressFlow.collect { address -> accountChanged(address) }
        }
        // The chess server came back: the boards too, without waiting for a pull (iOS da59726).
        scope.launch {
            broadcastRepository.arenaIndexerDown.drop(1).collect { down ->
                if (!down) refreshIndexerLeaderboards(force = true)
            }
        }
    }

    companion object {
        private const val TAG = "ChessTournament"
        private const val CLAIM_RETRY_MS = 30_000L
        private const val MAX_AUTO_CLAIMS = 4
        private const val GAP_REPAGE_GAP_MS = 30_000L
        private const val CHAIN_READ_INTERVAL_MS = 10_000L
        /** The REST API shows a move a few seconds after it lands: a read this long past the flag
         *  has seen any move made before it (iOS da59726). */
        private const val CHAIN_FLAG_MARGIN_MS = 5_000L
        private const val MAX_ROOM_NUMBER = 1_000_000_000
        private const val MAX_ROOM_GAP = 1_000
        private const val MAX_AUTO_REJOINS = 2
        private const val RECENT_ANSWER_MS = 15_000L
        private const val ARENA_BACKFILL_WAIT_MS = 12_000L
        private const val ROOMS_UNCONFIRMED_MESSAGE = "The chess server is unreachable, so open rooms can't be confirmed. Try again when it's back."
        private const val ROOM_FILLED_MESSAGE = "That room filled first. Tap Join to try the next one."
        /** The app claims by itself only for a flag this recent: an old game both players walked
         *  away from is not paid for on every launch. The board's Claim win covers the rest. */
        private const val AUTO_CLAIM_WINDOW_MS = 30L * 60_000
        private const val PENDING_MOVE_TIMEOUT_MS = 25_000L
        /** Taps on Claim win closer together than this are one claim (iOS 4922804). */
        private const val CLAIM_TAP_GAP_MS = 5_000L
        /** An own arena transaction the chain has not shown this long after it was sent is looked
         *  up, and dropped when the network has never seen it
         *  ([BroadcastRepository.checkUnconfirmedServiceRows]). Blocks come ten a second and the
         *  indexer is polled every 2-8 s, so a transaction that landed is normally back in well
         *  under 10 s; 60 s also covers a slow indexer or an orphan waiting on its parent, and
         *  still leaves most of a five-minute clock to play the move again. One the network cannot
         *  be asked about is given up after five minutes (iOS 4922804). */
        private const val UNCONFIRMED_ROW_MS = 60_000L
        private const val UNCONFIRMED_GIVE_UP_MS = 5L * 60_000

        /** What a chess send says when the chatting address has no coins at all - not the "wait"
         *  of a change output still being mined, which retries. */
        const val NOT_ENOUGH_KAS_MESSAGE = "Not enough KAS on your Chatting Address. Add some and try again."
        private const val STILL_CONFIRMING_MESSAGE = "The network is still confirming your previous send. Please try again in a few seconds."

        /** Worth trying again in a moment: the coins are there but not spendable yet (the change
         *  of the last send), or the node rejected the transaction as an orphan or already spent -
         *  none of which reached the chain. No funds is not retried blindly (iOS 8d5f925). */
        fun isRetryableSendError(error: Throwable): Boolean {
            val text = error.message.orEmpty().lowercase()
            if (text.contains("insufficient funds") || text.contains("no utxos found")) return false
            return text.contains("orphan") || text.contains("already spent") || text.contains("double spend") ||
                text.contains("no confirmed") || text.contains("not spendable") || text.contains("still confirming") ||
                text.contains("no spendable") || text.contains("no utxos available")
        }

        fun friendlySendErrorMessage(error: Throwable): String {
            val text = error.message.orEmpty().lowercase()
            return when {
                text.contains("insufficient funds") || text.contains("no utxos found") -> NOT_ENOUGH_KAS_MESSAGE
                text.contains("orphan") || text.contains("already spent") || text.contains("double spend") -> STILL_CONFIRMING_MESSAGE
                else -> UserFacingError.message(error, "Could not send")
            }
        }

        /** How often the arena's history is re-read while a chess screen is up - the same
         *  cadence a public room's own indexer poll uses. */
        private const val ARENA_BACKFILL_INTERVAL_MS = 8_000L

        /** How long a join waits for that history before going ahead anyway. */
        private const val HISTORY_WAIT_MS = 8_000L
        private const val HISTORY_CHECK_INTERVAL_MS = 250L

        /** The tighter pull while a seat is held or a game is on - see [acquire]. */
        private const val FAST_POLL_INTERVAL_MS = 2_000L

        /** The indexer's leaderboards are re-read at most this often unless forced. */
        private const val INDEXER_LEADERBOARD_INTERVAL_MS = 20_000L

        /**
         * The indexer's answer for [period], or null when it is not one: another `period` (an old
         * indexer without it), no `since`, or a row missing a figure - iOS's strict Decodable
         * refuses the whole answer then too.
         */
        internal fun acceptedIndexerBoard(
            period: ChessLeaderboardPeriod,
            response: ChessIndexerLeaderboardResponse,
        ): IndexerBoard? {
            if (response.period != period.raw) return null
            val since = response.since ?: return null
            val players = response.players ?: return null
            val rows = players.map { p ->
                ChessLeaderboardRow(
                    address = p?.address ?: return null,
                    wins = p.wins ?: return null,
                    losses = p.losses ?: return null,
                    tournamentsPlayed = p.tournamentsPlayed ?: return null,
                    tournamentsWon = p.tournamentsWon ?: return null,
                    tournamentsLost = p.tournamentsLost ?: return null,
                    lastPlayedAt = p.lastPlayedAt ?: return null,
                    duelWins = p.duelWins ?: return null,
                    duelLosses = p.duelLosses ?: return null,
                    tournamentGameWins = p.tournamentGameWins ?: return null,
                    tournamentGameLosses = p.tournamentGameLosses ?: return null,
                )
            }
            return IndexerBoard(since, rows)
        }

        /** The indexer's rows for [period], only if they count the window this phone counts now. */
        internal fun usableIndexerRows(
            period: ChessLeaderboardPeriod,
            board: IndexerBoard?,
            nowMs: Long,
        ): List<ChessLeaderboardRow>? =
            board?.takeIf { it.since == ChessTournamentEngine.periodSinceMs(period, nowMs) }?.rows

        /** Per player, the larger of each figure; then most wins, fewest losses, latest played.
         *  Without an indexer board the phone's own board is shown as it is. */
        internal fun mergedBoard(
            local: List<ChessLeaderboardRow>,
            remote: List<ChessLeaderboardRow>?,
        ): List<ChessLeaderboardRow> {
            if (remote == null) return local
            val rows = LinkedHashMap<String, ChessLeaderboardRow>()
            for (r in remote) rows.putIfAbsent(r.address, r)
            for (r in local) {
                val m = rows[r.address]
                rows[r.address] = if (m == null) r else m.copy(
                    wins = maxOf(m.wins, r.wins),
                    losses = maxOf(m.losses, r.losses),
                    duelWins = maxOf(m.duelWins, r.duelWins),
                    duelLosses = maxOf(m.duelLosses, r.duelLosses),
                    tournamentGameWins = maxOf(m.tournamentGameWins, r.tournamentGameWins),
                    tournamentGameLosses = maxOf(m.tournamentGameLosses, r.tournamentGameLosses),
                    tournamentsPlayed = maxOf(m.tournamentsPlayed, r.tournamentsPlayed),
                    tournamentsWon = maxOf(m.tournamentsWon, r.tournamentsWon),
                    tournamentsLost = maxOf(m.tournamentsLost, r.tournamentsLost),
                    lastPlayedAt = maxOf(m.lastPlayedAt, r.lastPlayedAt),
                )
            }
            return rows.values.sortedWith(
                compareByDescending<ChessLeaderboardRow> { it.wins }
                    .thenBy { it.losses }
                    .thenByDescending { it.lastPlayedAt }
                    .thenBy { it.address }
            )
        }

        /** Rooms the app uses as machinery, never shown as chats: the chess arena. Hidden from
         *  Public Chats, no unread, no banners (iOS BroadcastService.serviceChannels). */
        val SERVICE_CHANNELS: Set<String> = setOf(ChessTournamentCodec.ARENA_CHANNEL)
    }
}
