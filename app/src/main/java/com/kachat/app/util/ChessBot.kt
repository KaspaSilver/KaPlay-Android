package com.kachat.app.util

import androidx.compose.ui.graphics.Color

/**
 * A bot to play in Play > Bots (iOS ChessBotModels c3c04a1): five characters, one per level.
 * Level 1 is open from the start; each one after opens when the one before it is beaten.
 */
data class ChessBot(
    val level: Int,
    val name: String,
    val title: String,
    val bio: String,
    val initials: String,
    /** The avatar's gradient, top to bottom. */
    val colors: List<Color>,
    val settings: ChessBotEngine.Settings,
) {
    /**
     * The search for the move at [ply] (0 = the game's first). The master varies its opening a
     * little so no two games start the same; past the opening it plays its best.
     */
    fun searchSettings(ply: Int): ChessBotEngine.Settings =
        if (settings.noise != 0 || ply >= 8) settings else settings.copy(noise = 12)

    companion object {
        val all: List<ChessBot> = listOf(
            ChessBot(
                level = 1, name = "Kaspa Silver", title = "Beginner",
                bio = "Here for the fun of it. Moves fast and sometimes leaves a piece hanging.",
                initials = "KS",
                colors = listOf(Color(0.78f, 0.80f, 0.84f), Color(0.50f, 0.53f, 0.58f)),
                settings = ChessBotEngine.Settings(depth = 1, quiescence = false, noise = 250, randomChance = 0.35, timeLimitSeconds = 1.0),
            ),
            ChessBot(
                level = 2, name = "Izio Dev", title = "Novice",
                bio = "Knows how every piece moves and loves grabbing free material. Watch your loose pawns.",
                initials = "ID",
                colors = listOf(Color(0.36f, 0.80f, 0.86f), Color(0.12f, 0.52f, 0.66f)),
                settings = ChessBotEngine.Settings(depth = 1, quiescence = true, noise = 100, randomChance = 0.12, timeLimitSeconds = 1.0),
            ),
            ChessBot(
                level = 3, name = "Coderofstuff", title = "Intermediate",
                bio = "Looks a couple of moves ahead before committing. Steady and hard to trick.",
                initials = "CS",
                colors = listOf(Color(0.44f, 0.78f, 0.73f), Color(0.16f, 0.55f, 0.47f)),
                settings = ChessBotEngine.Settings(depth = 2, quiescence = true, noise = 40, randomChance = 0.04, timeLimitSeconds = 2.0),
            ),
            ChessBot(
                level = 4, name = "Michael Sutton", title = "Advanced",
                bio = "Calculates carefully and punishes mistakes. Come with a plan.",
                initials = "MS",
                colors = listOf(Color(0.55f, 0.55f, 0.95f), Color(0.32f, 0.25f, 0.72f)),
                settings = ChessBotEngine.Settings(depth = 3, quiescence = true, noise = 12, randomChance = 0.0, timeLimitSeconds = 3.0),
            ),
            ChessBot(
                level = 5, name = "Yonatan Sompolinsky", title = "Master",
                bio = "Thinks deep and sees far. Beat this one and you have climbed the whole ladder.",
                initials = "YS",
                colors = listOf(Color(0.98f, 0.80f, 0.36f), Color(0.85f, 0.52f, 0.10f)),
                settings = ChessBotEngine.Settings(depth = 6, quiescence = true, noise = 0, randomChance = 0.0, timeLimitSeconds = 4.0),
            ),
        )

        fun forLevel(level: Int): ChessBot? = all.firstOrNull { it.level == level }
    }
}

/** The player's colour choice before a bot game. */
enum class ChessBotSidePreference(val label: String) {
    WHITE("White"), RANDOM("Random"), BLACK("Black");

    fun resolve(): ChessColor = when (this) {
        WHITE -> ChessColor.WHITE
        BLACK -> ChessColor.BLACK
        RANDOM -> if (kotlin.random.Random.nextBoolean()) ChessColor.WHITE else ChessColor.BLACK
    }
}

// MARK: - On chain (ONLINE_CHESS.md §7)

/**
 * A finished bot game's stamp: plaintext broadcast JSON in `#chess-bots`, signed by the player's
 * address, sent once when the game ends (iOS ddec987).
 */
data class ChessBotMessage(
    /** Game id: a lowercase UUID chosen by the player's phone. */
    val g: String,
    /** The bot's level, 1...5. */
    val bot: Int,
    /** The player's colour, "w" or "b". */
    val c: String,
    /** How it ended for the player: won, lost, drawn. */
    val r: String,
    /** Why: checkmate, resignation, stalemate, insufficient material, the fifty-move rule,
     *  threefold repetition. */
    val why: String,
    /** Every move of the game, both sides, space separated: "e2e4 e7e5", a promotion "e7e8q". */
    val m: String,
) {
    val type: String get() = "chess_bot"
    val v: Int get() = 2
    val a: String get() = "result"
}

object ChessBotCodec {
    const val CHANNEL = "chess-bots"

    fun result(id: String, bot: ChessBot, playerColor: ChessColor, result: ChessBotResult, moves: List<ChessMove>) =
        ChessBotMessage(
            g = id, bot = bot.level, c = if (playerColor == ChessColor.WHITE) "w" else "b",
            r = result.code, why = result.reason, m = moves.joinToString(" ", transform = ::moveText),
        )

    private fun moveText(move: ChessMove): String =
        move.from.algebraic + move.to.algebraic + (move.promotion?.promotionLetter ?: "")

    /** JSON with sorted keys and nothing escaped beyond quotes - every value is an id, a word, a
     *  letter, a number or moves - the same bytes iOS's sortedKeys encoder writes. */
    fun encode(message: ChessBotMessage): String {
        val fields = sortedMapOf<String, Any>(
            "type" to message.type, "v" to message.v, "g" to message.g, "a" to message.a,
            "bot" to message.bot, "c" to message.c, "r" to message.r, "why" to message.why, "m" to message.m,
        )
        return fields.entries.joinToString(",", "{", "}") { (key, value) ->
            "\"$key\":${if (value is String) "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else value}"
        }
    }

    /**
     * A stamp's fee, for the "about 0.0017 KAS" line: the same local estimate the arena's Join
     * button shows (one input), on a game of 40 moves a side. A longer game's stamp costs a little
     * more.
     */
    fun stampFeeText(): String? = runCatching {
        val e4 = ChessMove(ChessSquare(4, 1), ChessSquare(4, 3), null)
        val sample = result(
            java.util.UUID.randomUUID().toString().lowercase(), ChessBot.all[0], ChessColor.WHITE,
            ChessBotResult.Won("checkmate"), List(80) { e4 },
        )
        val payload = MessageProtocol.buildBcastPayload(CHANNEL, encode(sample))
        val mass = KaspaMass.calculateMass(numInputs = 1, outputScriptLens = listOf(34), payloadSize = payload.size)
        val sompi = KaspaMass.calculateFee(mass, KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM.toLong())
        "%.4f %s".format(sompi / 100_000_000.0, KaspaUnit.symbol)
    }.getOrNull()
}

/** One bot game's end, with the reason ("checkmate", "resignation", ...). */
sealed class ChessBotResult(val reason: String, /** `r` in the stamp. */ val code: String) {
    class Won(reason: String) : ChessBotResult(reason, "won")
    class Lost(reason: String) : ChessBotResult(reason, "lost")
    class Drawn(reason: String) : ChessBotResult(reason, "drawn")
}
