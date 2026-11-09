package com.kachat.app.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kachat.app.util.ChessBot
import com.kachat.app.util.KaspaAddress
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Someone's win against a bot, from the Challenge button on their KaPost: beat [bot] in fewer than
 * [moves] (iOS ChessBotChallenge, ddec987).
 */
data class ChessBotChallenge(
    val bot: ChessBot,
    val moves: Int?,
    /** The address that won. */
    val by: String?,
) {
    /** For a navigation route's `challenge` argument; [fromRouteArg] reads it back. */
    fun routeArg(): String = Uri.encode("${bot.level},${moves ?: ""},${by ?: ""}")

    companion object {
        fun fromRouteArg(arg: String?): ChessBotChallenge? {
            val parts = arg?.split(",")?.takeIf { it.size == 3 } ?: return null
            val bot = parts[0].toIntOrNull()?.let { ChessBot.forLevel(it) } ?: return null
            return ChessBotChallenge(bot, parts[1].toIntOrNull(), parts[2].ifEmpty { null })
        }
    }
}

/** A challenge link waiting for the signed-in app, which may not be up yet (a cold start, or a
 *  link opened before signing in). ChessMainShell takes it. */
object ChessBotLinkRouter {
    val challenge = MutableStateFlow<ChessBotChallenge?>(null)
}

/**
 * The two links between KaPlay and KaPost (ONLINE_CHESS.md §7): the post KaPlay asks KaPost to
 * compose, and the challenge link in that post, which opens KaPlay.
 */
object ChessBotLinks {
    const val SCHEME = "kachatchess"
    /** Where sharing goes when KaPost isn't installed. */
    private const val KAPOST_STORE_URL = "https://play.google.com/store/search?q=KaPost&c=apps"

    /** `kachatchess://bot/<level>?name=<bot>&moves=<n>&by=<address>&game=<game id>` */
    fun challengeUrl(bot: ChessBot, moves: Int, by: String, gameId: String): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority("bot")
            .appendPath(bot.level.toString())
            .appendQueryParameter("name", bot.name)
            .appendQueryParameter("moves", moves.toString())
            .appendQueryParameter("by", by)
            .appendQueryParameter("game", gameId)
            .build()
            .toString()

    /** Links come from other people's posts, so only the level has to be right; a bad `moves` or
     *  `by` just leaves that part out of the challenge. */
    fun challenge(uri: Uri): ChessBotChallenge? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) || !uri.host.equals("bot", ignoreCase = true)) return null
        val bot = uri.lastPathSegment?.toIntOrNull()?.let { ChessBot.forLevel(it) } ?: return null
        val moves = uri.getQueryParameter("moves")?.toIntOrNull()?.takeIf { it in 1..999 }
        val by = uri.getQueryParameter("by")?.takeIf { KaspaAddress.isValid(it) }
        return ChessBotChallenge(bot, moves, by)
    }

    /** Opens KaPost's composer with the post written (`kaposts://compose?text=...`); the player
     *  still taps Post there. Without KaPost installed, the Play Store opens. */
    fun shareToKaPost(context: Context, bot: ChessBot, moves: Int, by: String, gameId: String) {
        val challenge = challengeUrl(bot, moves, by, gameId)
        val text = "I beat ${bot.name} (Level ${bot.level}) in $moves moves on KaPlay. Think you can do it in fewer?\n\n$challenge"
        val compose = Intent(Intent.ACTION_VIEW, Uri.parse("kaposts://compose?text=${percentEncoded(text)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(compose)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(KAPOST_STORE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Letters, digits and -._~ only: the whole post is one value, so the challenge link's own & and
     *  = must not end it, and its own escapes get escaped again to come back intact. */
    private fun percentEncoded(text: String): String = buildString {
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val char = (byte.toInt() and 0xFF).toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-._~") {
                append(char)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }
}
