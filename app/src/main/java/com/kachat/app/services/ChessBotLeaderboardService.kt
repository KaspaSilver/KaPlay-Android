package com.kachat.app.services

import com.kachat.app.repository.BroadcastRepository
import com.kachat.app.util.ChessBot
import com.kachat.app.util.ChessBotCodec
import com.kachat.app.util.ChessLeaderboardPeriod
import com.kachat.app.util.ChessTournamentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A player's best game against one bot: the fewest moves they beat it in. */
data class ChessBotBoardRow(
    val address: String,
    val moves: Int,
    /** Block time of the win (ms). */
    val at: Long,
)

/**
 * The bots' boards, from the indexer's `/chess/bots/leaderboard` (ONLINE_CHESS.md §7) with the
 * player's own stamped wins merged in, so a win shows the moment it is stamped. Any failure leaves
 * what is there in place (iOS ChessBotLeaderboardService, ddec987).
 */
@Singleton
class ChessBotLeaderboardService @Inject constructor(
    private val broadcastRepository: BroadcastRepository,
    private val tournamentService: ChessTournamentService,
    private val progress: ChessBotProgress,
) {
    data class Board(val games: Int = 0, val rows: List<ChessBotBoardRow> = emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Period -> level -> board. */
    private val _boards = MutableStateFlow<Map<ChessLeaderboardPeriod, Map<Int, Board>>>(emptyMap())
    val boards: StateFlow<Map<ChessLeaderboardPeriod, Map<Int, Board>>> = _boards.asStateFlow()

    /** The last refresh got no board at all: what's shown is what the phone already had (iOS da59726). */
    private val _serverUnreachable = MutableStateFlow(false)
    val serverUnreachable: StateFlow<Boolean> = _serverUnreachable.asStateFlow()

    private var lastFetch: Long? = null
    private var job: Job? = null

    init {
        // The chess server came back: the boards too, without waiting for a pull (iOS da59726).
        scope.launch {
            broadcastRepository.arenaIndexerDown.drop(1).collect { down ->
                if (!down) {
                    _serverUnreachable.value = false
                    refresh(force = true)
                }
            }
        }
    }

    /** At most every 20 s unless forced. */
    fun refresh(force: Boolean = false) {
        val last = lastFetch
        if (!force && last != null && System.currentTimeMillis() - last < REFRESH_INTERVAL_MS) return
        lastFetch = System.currentTimeMillis()
        job?.cancel()
        job = scope.launch {
            // No indexer configured: nothing was asked, so nothing is "unreachable".
            if (!broadcastRepository.indexerConfigured(ChessBotCodec.CHANNEL)) return@launch
            var answered = false
            try {
                for (period in ChessLeaderboardPeriod.entries) {
                    val response = broadcastRepository.fetchChessBotLeaderboard(ChessBotCodec.CHANNEL, period.raw)
                        ?: continue
                    if (response.period != period.raw) continue
                    val levels = accepted(response) ?: continue
                    if (!isActive) return@launch
                    answered = true
                    _boards.value = _boards.value + (period to levels)
                    tournamentService.resolveNames(levels.values.flatMap { board -> board.rows.map { it.address } }.toSet())
                }
            } finally {
                if (isActive) _serverUnreachable.value = !answered
            }
        }
    }

    /** The whole board, or null when anything in it is missing. */
    private fun accepted(response: ChessBotLeaderboardResponse): Map<Int, Board>? {
        val levels = mutableMapOf<Int, Board>()
        for (bot in response.bots ?: return null) {
            val level = bot?.level ?: return null
            val players = bot.players ?: return null
            levels[level] = Board(
                games = bot.games ?: return null,
                rows = players.map { player ->
                    ChessBotBoardRow(
                        address = player?.address ?: return null,
                        moves = player.moves ?: return null,
                        at = player.at ?: return null,
                    )
                },
            )
        }
        return levels
    }

    /** Fewest moves first, the earlier win on a tie, then by address (iOS da59726). */
    fun rows(
        bot: ChessBot,
        period: ChessLeaderboardPeriod,
        boards: Map<ChessLeaderboardPeriod, Map<Int, Board>> = _boards.value,
        wins: Map<String, Map<Int, List<ChessBotProgress.StampedWin>>> = progress.stampedWins.value,
    ): List<ChessBotBoardRow> {
        val rows = boards[period]?.get(bot.level)?.rows.orEmpty().toMutableList()
        val since = ChessTournamentEngine.periodSinceMs(period, System.currentTimeMillis())
        val address = progress.address
        val mine = progress.bestWin(bot, since, wins)
        if (address.isNotEmpty() && mine != null) {
            val own = ChessBotBoardRow(address, mine.moves, mine.at)
            val index = rows.indexOfFirst { it.address.equals(address, ignoreCase = true) }
            if (index < 0) rows.add(own) else if (mine.moves < rows[index].moves) rows[index] = own
        }
        // One row per player, their best: the list is keyed by address, and a repeated key crashes it.
        return rows.sortedWith(compareBy({ it.moves }, { it.at }, { it.address })).distinctBy { it.address.lowercase() }
    }

    /** Has anyone played this bot, as far as this phone can tell? */
    fun hasBeenPlayed(
        bot: ChessBot,
        boards: Map<ChessLeaderboardPeriod, Map<Int, Board>> = _boards.value,
        records: Map<String, Map<Int, ChessBotProgress.Record>> = progress.records.value,
    ): Boolean {
        val record = progress.record(bot, records)
        if (record.wins + record.losses + record.draws > 0) return true
        return (boards[ChessLeaderboardPeriod.ALL_TIME]?.get(bot.level)?.games ?: 0) > 0
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 20_000L
    }
}
