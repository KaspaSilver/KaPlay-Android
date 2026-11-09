package com.kachat.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kachat.app.services.CrashRecorder
import com.kachat.app.ui.KaChatApp
import com.kachat.app.ui.theme.KaChatTheme
import com.kachat.app.viewmodels.WalletViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Single Activity — all navigation is handled in Compose via NavHost. KaChat's own also routed
 * notifications, links, the share sheet and calls; none of that is here (iOS KaChatChessApp).
 *
 * Extends [AppCompatActivity] (rather than plain `FragmentActivity`, which it's a superset of)
 * because the per-app language API (`AppCompatDelegate.setApplicationLocales`, used by the
 * Language setting in Settings) relies on `AppCompatActivity`'s `attachBaseContext` override to
 * apply the locale override on API levels below 33.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not on a recreated Activity, nor on one reopened from Recents (which replays the link
        // that first opened the task): its challenge has been taken already.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) takeChallenge(intent)
        // Phones lock to portrait; tablets (sw>=600dp, see res/values-sw600dp/bools.xml) rotate
        // freely - mirroring iOS (iPhone portrait-only, iPad any orientation).
        requestedOrientation = if (resources.getBoolean(R.bool.lock_portrait)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        // The balance and the seed screen used to sit in the task switcher's thumbnail of the app
        // in plain view. This is Android's own switch for exactly that, and unlike FLAG_SECURE it
        // does not also block screenshots - and it cannot fight the FLAG_SECURE the seed and
        // private-key screens set and clear for themselves. Below API 33, PrivacyCover (iOS's
        // cover: the icon over the app while it is not in front) is what the thumbnail shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()
        setContent {
            val walletViewModel: WalletViewModel = hiltViewModel()
            // Settings > Customization > Appearance: System follows the device, as on iOS.
            val appearance by walletViewModel.appearance.collectAsState()
            KaChatTheme(darkTheme = appearance.isDark(androidx.compose.foundation.isSystemInDarkTheme())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KaChatApp(walletViewModel)
                    PrivacyCover()
                    CrashNotice()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        takeChallenge(intent)
    }

    /** A challenge link from a KaPost: the signed-in app opens it, now or once it is up. */
    private fun takeChallenge(intent: android.content.Intent?) {
        val uri = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.data ?: return
        com.kachat.app.services.ChessBotLinks.challenge(uri)?.let {
            com.kachat.app.services.ChessBotLinkRouter.challenge.value = it
        }
    }

    /**
     * What the task switcher and a system prompt see instead of the app (iOS PrivacyCoverView):
     * the app's icon over a frosted layer, from the moment the app stops being the thing in
     * front until it is again. Below API 33 this is also what the switcher's thumbnail shows.
     */
    @androidx.compose.runtime.Composable
    private fun PrivacyCover() {
        var covered by remember { mutableStateOf(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> covered = false
                    Lifecycle.Event.ON_PAUSE -> covered = true
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        // A window of its own, as KaPost's: an overlay on the root sat underneath sheets and
        // dialogs, which is where the 12 or 24 words, the passphrase and the private key are
        // shown. A dialog opened last stacks over them all; solid, so nothing reads through it.
        if (covered) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    // Swallows touches so nothing underneath is tapped while covered.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // The app's own icon, shaped like it is on the home screen.
                Image(
                    painter = painterResource(R.drawable.kachat_chess_app_mark),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            }
        }
    }

    /**
     * Shown once on the launch after the app crashed: the previous process died before anyone
     * could see why, and its record is the one thing that turns "it will not open" into a fix.
     * Share sends the newest report to a share sheet (mail, Telegram, anything).
     */
    @androidx.compose.runtime.Composable
    private fun CrashNotice() {
        val crashFile = remember { CrashRecorder.unseenCrash(this) }
        var show by remember { mutableStateOf(crashFile != null) }
        if (!show || crashFile == null) return
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { show = false; CrashRecorder.markSeen(this) },
            title = { Text("KaPlay closed unexpectedly") },
            text = { Text("The app did not close normally the last time it ran. A report of what happened was saved. Sharing it with the developer is the quickest way to get it fixed - it contains no keys or addresses.") },
            confirmButton = {
                TextButton(onClick = {
                    show = false
                    CrashRecorder.markSeen(this)
                    shareCrashReport(crashFile)
                }) { Text("Share report") }
            },
            dismissButton = {
                TextButton(onClick = { show = false; CrashRecorder.markSeen(this) }) { Text("Not now") }
            }
        )
    }

    private fun shareCrashReport(file: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "KaPlay crash report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share crash report"))
        }.onFailure { android.util.Log.w("MainActivity", "Could not share crash report", it) }
    }
}
