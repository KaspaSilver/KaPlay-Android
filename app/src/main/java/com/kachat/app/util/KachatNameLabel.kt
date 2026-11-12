package com.kachat.app.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * How a .kachat name is shown: "alice.kachat" by default, or "alice" when Settings >
 * Customization > Show .kachat Endings is off (iOS 3262381 `KachatNameLabel`, as in KaChat).
 * Display only: typing, copying and anything sent keep working with either form (the codec
 * strips the ending).
 *
 * Backed by Compose state, so every name on screen redraws the moment the toggle changes, and
 * readable from plain code (the bell, the registry) without a coroutine.
 */
object KachatNameLabel {
    private const val PREFS = "kachat_display"
    const val SHOW_ENDINGS_KEY = "kachat_show_name_endings"

    private val showsEndingState = mutableStateOf(true)
    private var prefs: android.content.SharedPreferences? = null

    /** Reads the saved choice. Called once at startup, from
     *  [com.kachat.app.KaChatApplication.attachBaseContext] with the base context (its
     *  applicationContext is still null there, so it isn't used). */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        showsEndingState.value = p.getBoolean(SHOW_ENDINGS_KEY, true)
    }

    val showsEnding: Boolean get() = showsEndingState.value

    fun setShowsEnding(value: Boolean) {
        showsEndingState.value = value
        prefs?.edit()?.putBoolean(SHOW_ENDINGS_KEY, value)?.apply()
    }

    fun display(name: String): String = if (showsEnding) "$name.kachat" else name
}
