package com.kachat.app.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kachat.app.repository.BroadcastRepository
import com.kachat.app.util.ChessBoard
import com.kachat.app.util.ChessBot
import com.kachat.app.util.ChessBotCodec
import com.kachat.app.util.ChessBotEngine
import com.kachat.app.util.ChessBotMessage
import com.kachat.app.util.ChessBotResult
import com.kachat.app.util.ChessColor
import com.kachat.app.util.ChessEngine
import com.kachat.app.util.ChessMove
import com.kachat.app.util.ChessPieceType
import com.kachat.app.util.ChessTournamentEngine
import com.kachat.app.util.UserFacingError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The player's record against each bot, kept on this phone per address (iOS ChessBotProgress
 * c3c04a1). A win against a bot opens the next level.
 */
@Singleton
class ChessBotProgress @Inject constructor(
    @ApplicationContext context: Context,
    private val walletManager: WalletManager,
) {
    data class Record(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0)

    /** A win that went on chain: how many moves the player took, and when (ms). */
    data class StampedWin(val moves: Int, val at: Long)

    private val prefs = context.getSharedPreferences("chess_bots", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Address -> level -> record. */
    private val _records = MutableStateFlow(load())
    val records: StateFlow<Map<String, Map<Int, Record>>> = _records.asStateFlow()

    /** Address -> level -> wins, so the player's own best shows before the indexer has it. */
    private val _stampedWins = MutableStateFlow(loadStampedWins())
    val stampedWins: StateFlow<Map<String, Map<Int, List<StampedWin>>>> = _stampedWins.asStateFlow()

    private fun loadStampedWins(): Map<String, Map<Int, List<StampedWin>>> = runCatching {
        val json = prefs.getString(STAMPED_WINS_KEY, null) ?: return@runCatching emptyMap()
        val type = object : TypeToken<Map<String, Map<Int, List<StampedWin>>>>() {}.type
        gson.fromJson<Map<String, Map<Int, List<StampedWin>>>>(json, type) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun load(): Map<String, Map<Int, Record>> = runCatching {
        val json = prefs.getString(KEY, null) ?: return@runCatching emptyMap()
        val type = object : TypeToken<Map<String, Map<Int, Record>>>() {}.type
        gson.fromJson<Map<String, Map<Int, Record>>>(json, type) ?: emptyMap()
    }.getOrDefault(emptyMap())

    val address: String get() = runCatching { walletManager.getAddress() }.getOrDefault("")

    fun record(bot: ChessBot, records: Map<String, Map<Int, Record>> = _records.value): Record =
        records[address]?.get(bot.level) ?: Record()

    fun isUnlocked(bot: ChessBot, records: Map<String, Map<Int, Record>> = _records.value): Boolean {
        val previous = ChessBot.forLevel(bot.level - 1) ?: return true
        return record(previous, records).wins > 0
    }

    fun add(result: ChessBotResult, bot: ChessBot) {
        val current = record(bot)
        val updated = when (result) {
            is ChessBotResult.Won -> current.copy(wins = current.wins + 1)
            is ChessBotResult.Lost -> current.copy(losses = current.losses + 1)
            is ChessBotResult.Drawn -> current.copy(draws = current.draws + 1)
        }
        val all = _records.value.toMutableMap()
        all[address] = (all[address] ?: emptyMap()) + (bot.level to updated)
        _records.value = all
        prefs.edit().putString(KEY, gson.toJson(all)).apply()
    }

    fun addStampedWin(moves: Int, bot: ChessBot) {
        val win = StampedWin(moves, System.currentTimeMillis())
        val all = _stampedWins.value.toMutableMap()
        val levels = all[address] ?: emptyMap()
        all[address] = levels + (bot.level to (levels[bot.level] ?: emptyList()) + win)
        _stampedWins.value = all
        prefs.edit().putString(STAMPED_WINS_KEY, gson.toJson(all)).apply()
    }

    /** An account was deleted: its records go from memory as well as the saved copies (on both
     *  networks' addresses, any case), or the next bot game finished would write them back
     *  (iOS da59726). */
    fun removeAll(forAddress: String) {
        val variants = com.kachat.app.util.KaspaNetwork.accountAddressVariants(forAddress).map { it.lowercase() }.toSet()
        val records = _records.value.filterKeys { it.lowercase() !in variants }
        val wins = _stampedWins.value.filterKeys { it.lowercase() !in variants }
        _records.value = records
        _stampedWins.value = wins
        prefs.edit()
            .putString(KEY, gson.toJson(records))
            .putString(STAMPED_WINS_KEY, gson.toJson(wins))
            .apply()
    }

    /** The player's fewest moves against [bot] since [since] (ms), the earlier win on a tie. */
    fun bestWin(
        bot: ChessBot,
        since: Long,
        wins: Map<String, Map<Int, List<StampedWin>>> = _stampedWins.value,
    ): StampedWin? = wins[address]?.get(bot.level).orEmpty()
        .filter { it.at >= since }
        .minWithOrNull(compareBy({ it.moves }, { it.at }))

    private companion object {
        const val KEY = "chessBotRecords"
        const val STAMPED_WINS_KEY = "chessBotStampedWins"
    }
}

/**
 * One game against a bot (Play > Bots, iOS ChessBotGame ddec987). The whole game lives on this
 * phone and costs nothing while it is played; when it ends, its result and moves go on chain in
 * one transaction, the stamp (ONLINE_CHESS.md §7). A stamp that fails holds until Retry, and only
 * a stamped win counts toward the player's best on the bot's leaderboard.
 */
class ChessBotGame(
    val bot: ChessBot,
    val playerColor: ChessColor,
    private val broadcastRepository: BroadcastRepository,
    private val progress: ChessBotProgress,
    private val scope: CoroutineScope,
) {
    data class PlayedMove(
        val move: ChessMove,
        val color: ChessColor,
        val piece: ChessPieceType,
        val captured: ChessPieceType?,
    )

    /** Where the result's transaction stands. */
    sealed class Stamp {
        /** The game is still on. */
        data object None : Stamp()
        data object Sending : Stamp()
        data object Stamped : Stamp()
        data class Failed(val error: String) : Stamp()
        /** A node may have taken it and never answered: it may be on chain. No Retry - a second
         *  stamp of the same game (iOS 4922804). */
        data class Unconfirmed(val note: String) : Stamp()
    }

    val id: String = java.util.UUID.randomUUID().toString().lowercase()

    private val _board = MutableStateFlow(ChessEngine.initialBoard())
    val board: StateFlow<ChessBoard> = _board.asStateFlow()
    private val _moves = MutableStateFlow<List<PlayedMove>>(emptyList())
    val moves: StateFlow<List<PlayedMove>> = _moves.asStateFlow()
    private val _result = MutableStateFlow<ChessBotResult?>(null)
    val result: StateFlow<ChessBotResult?> = _result.asStateFlow()
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()
    private val _stamp = MutableStateFlow<Stamp>(Stamp.None)
    val stamp: StateFlow<Stamp> = _stamp.asStateFlow()

    private var started = false
    private val positionCounts = mutableMapOf<String, Int>()
    private var halfmoveClock = 0

    val botColor: ChessColor get() = playerColor.opposite
    val isOver: Boolean get() = _result.value != null

    /** The player may move: their turn, the bot not thinking. */
    val canPlayerMove: Boolean
        get() = !isOver && _board.value.sideToMove == playerColor && !_isThinking.value

    /** The moves the player made, the number a win is ranked by. */
    val playerMoveCount: Int
        get() = _moves.value.count { it.color == playerColor }

    /** The board opened: if the bot has white it moves now. */
    fun start() {
        if (started) return
        started = true
        positionCounts[ChessTournamentEngine.positionKey(_board.value)] = 1
        botMoveIfDue()
    }

    fun play(move: ChessMove) {
        if (!canPlayerMove || !ChessEngine.isLegal(move, _board.value)) return
        apply(ChessEngine.normalizingPromotion(move, _board.value))
    }

    fun resign() {
        if (!started || isOver) return
        finish(ChessBotResult.Lost("resignation"))
    }

    fun retry() {
        if (_stamp.value !is Stamp.Failed) return
        sendStamp()
    }

    // MARK: - The board

    private fun apply(move: ChessMove) {
        val board = _board.value
        val piece = board.piece(move.from) ?: return
        val isEnPassant = piece.type == ChessPieceType.PAWN && move.to == board.enPassantTarget && board.piece(move.to) == null
        val captured = board.piece(move.to)?.type ?: if (isEnPassant) ChessPieceType.PAWN else null
        val mover = board.sideToMove
        val next = ChessEngine.apply(move, board)
        _board.value = next
        _moves.value = _moves.value + PlayedMove(move, mover, piece.type, captured)

        halfmoveClock = if (piece.type == ChessPieceType.PAWN || captured != null) 0 else halfmoveClock + 1
        val key = ChessTournamentEngine.positionKey(next)
        positionCounts[key] = (positionCounts[key] ?: 0) + 1

        when {
            ChessEngine.isCheckmate(next) ->
                finish(if (mover == playerColor) ChessBotResult.Won("checkmate") else ChessBotResult.Lost("checkmate"))
            ChessEngine.isStalemate(next) -> finish(ChessBotResult.Drawn("stalemate"))
            ChessEngine.isInsufficientMaterial(next) -> finish(ChessBotResult.Drawn("insufficient material"))
            halfmoveClock >= 100 -> finish(ChessBotResult.Drawn("the fifty-move rule"))
            (positionCounts[key] ?: 0) >= 3 -> finish(ChessBotResult.Drawn("threefold repetition"))
        }
        botMoveIfDue()
    }

    private fun finish(result: ChessBotResult) {
        if (_result.value != null) return
        _result.value = result
        progress.add(result, bot)
        sendStamp()
    }

    /**
     * The bot's turn: think off the main thread (never quicker than a beat, so a reply does not
     * land in the same instant as the player's move), then play.
     */
    private fun botMoveIfDue() {
        if (isOver || _board.value.sideToMove != botColor || _isThinking.value) return
        _isThinking.value = true
        val position = _board.value
        val settings = bot.searchSettings(_moves.value.size)
        val avoiding = repeatingMoves()
        val began = System.currentTimeMillis()
        scope.launch {
            val move = withContext(Dispatchers.Default) {
                ChessBotEngine.bestMove(position, settings, avoiding)
            }
            val elapsed = System.currentTimeMillis() - began
            if (elapsed < 800) delay(800 - elapsed)
            _isThinking.value = false
            if (isOver || move == null || _board.value != position) return@launch
            apply(move)
        }
    }

    /** The bot's moves that bring back a position already seen this game. */
    private fun repeatingMoves(): List<ChessMove> {
        val board = _board.value
        return ChessEngine.legalMoves(board).filter { move ->
            positionCounts[ChessTournamentEngine.positionKey(ChessEngine.apply(move, board))] != null
        }
    }

    // MARK: - On chain

    /** Goes on even if the player leaves the screen: [scope] outlives it. Never twice for one
     *  game: not while one is going out, once one went out, or once one may have
     *  ([Stamp.Unconfirmed], iOS 4922804). */
    private fun sendStamp() {
        val result = _result.value ?: return
        when (_stamp.value) {
            is Stamp.None, is Stamp.Failed -> Unit
            is Stamp.Sending, is Stamp.Stamped, is Stamp.Unconfirmed -> return
        }
        _stamp.value = Stamp.Sending
        val message = ChessBotCodec.result(id, bot, playerColor, result, _moves.value.map { it.move })
        scope.launch {
            val txId = when (val sent = send(message)) {
                is SendResult.Sent -> sent.txId
                is SendResult.Failed -> {
                    _stamp.value = Stamp.Failed(sent.error)
                    return@launch
                }
                is SendResult.Unknown -> {
                    _stamp.value = Stamp.Unconfirmed(SendOutcomeUnknownException.MESSAGE)
                    return@launch
                }
            }
            // A node taking it is not the chain showing it: "stamped", and a win counting on the
            // board, wait for that. No Retry if it doesn't show: it may yet land (iOS da59726).
            if (!landed(txId)) {
                _stamp.value = Stamp.Unconfirmed("Sent, but Kaspa hasn't confirmed it yet.")
                return@launch
            }
            _stamp.value = Stamp.Stamped
            if (result is ChessBotResult.Won) progress.addStampedWin(playerMoveCount, bot)
        }
    }

    /** Whether the REST API shows the stamp accepted, within a little over a minute. */
    private suspend fun landed(txId: String): Boolean {
        repeat(CONFIRM_CHECKS) {
            delay(CONFIRM_CHECK_GAP_MS)
            if (broadcastRepository.isTransactionAccepted(txId)) return true
        }
        return false
    }

    private sealed class SendResult {
        data class Sent(val txId: String) : SendResult()
        data class Failed(val error: String) : SendResult()
        /** [SendOutcomeUnknownException]: it may have gone through. */
        data object Unknown : SendResult()
    }

    /**
     * One broadcast transaction, with the retries a send needs while the change of the last
     * transaction is not spendable yet (the arena's sends do the same). Only that kind of error is
     * retried: no KAS on the chatting address, or a send whose outcome is unknown (a second stamp
     * of the same game), stops at once. [SendResult.Sent] - "stamped" - means the transaction
     * really went out (iOS 8d5f925, 4922804).
     */
    private suspend fun send(message: ChessBotMessage): SendResult {
        val content = ChessBotCodec.encode(message)
        var attempt = 0
        while (true) {
            try {
                return SendResult.Sent(broadcastRepository.sendBroadcast(ChessBotCodec.CHANNEL, content))
            } catch (e: Exception) {
                if (e is SendOutcomeUnknownException) {
                    Log.w("ChessBots", "Bot game stamp outcome unknown")
                    return SendResult.Unknown
                }
                attempt += 1
                if (attempt > 5 || !ChessTournamentService.isRetryableSendError(e)) {
                    Log.w("ChessBots", "Bot game stamp failed", e)
                    return SendResult.Failed(ChessTournamentService.friendlySendErrorMessage(e))
                }
                delay(1_200)
            }
        }
    }

    private companion object {
        const val CONFIRM_CHECKS = 20
        const val CONFIRM_CHECK_GAP_MS = 4_000L
    }
}
