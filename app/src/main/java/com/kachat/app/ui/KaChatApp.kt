package com.kachat.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.navigation.navArgument
import com.kachat.app.ui.screens.*
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.AccountBalanceWallet

/**
 * Top-level navigation destinations.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    /**
     * True for tabs drawn with the bundled Kaspa mark instead of a Material icon. [icon] stays a
     * real ImageVector so anything not taught about the drawable still renders something sensible
     * rather than nothing.
     */
    val usesKaspaLogo: Boolean get() = this is KaspaHub

    object Settings    : Screen("settings",     "Settings",     Icons.Default.Settings)
    // KaChat Chess's dock (ChessMainShell): Play, Leaderboard, Wallet, Settings - fixed, nothing
    // to rearrange (iOS ChessMainTabView).
    object Play        : Screen("play",         "Play",         com.kachat.app.ui.theme.IosSymbols.ControllerTab)
    object Leaderboard : Screen("leaderboard",  "Leaderboard",  Icons.Default.EmojiEvents)
    // KaPay's wallet symbol, as KaPay's own dock has it (iOS 38e0455 `wallet.bifold`) - filled,
    // as the iPhone tab bar draws it (wallet.bifold.fill).
    object Wallet      : Screen("wallet",       "Wallet",       Icons.Filled.AccountBalanceWallet)
    object Chats       : Screen("chats",        "Chats",        Icons.Default.Forum)
    object Portfolio   : Screen("portfolio",    "Portfolio",    Icons.Default.PieChart)
    object Profile     : Screen("profile",      "Profile",      Icons.Default.AccountCircle)
    object Swap        : Screen("swap",         "Swap",         Icons.Default.SwapHoriz)
    // Labeled "Storage" (not "Cold Storage") and always in the default tab set, matching iOS's
    // AppTab.coldStorage — hideable like Portfolio/Swap via Settings > Customization > Menu, but
    // no longer a separate opt-in reached through Portfolio's old "Cold Storage Devices" row.
    object ColdStorage : Screen("cold_storage", "Storage",      Icons.Default.Security)
    object KaPosts     : Screen("kaposts",      "KaPosts",      Icons.Default.NoteAlt)
    // Named "Public Chats" everywhere a person reads it, matching iOS's AppTab.publicChats title.
    // The route stays "broadcasts": it is persisted in saved dock arrangements, and the thread id,
    // the share links and the on-chain subtype are all still broadcast-named on the wire.
    object Broadcasts  : Screen("broadcasts",   "Public Chats", Icons.Default.Sensors)
    // A placeable tab like any other, matching iOS's AppTab.apps - it can sit in the dock or in
    // the Kaspa Hub, and it can be reordered in either. It used to be a hardcoded tile appended
    // to the Hub grid with no Screen behind it, which is exactly why Customize Dock could not
    // see it. Labeled "Websites" in the dock (the bar truncates hard); "Kaspa Websites" in full.
    object KaspaWebsites : Screen("kaspa_websites", "Websites", Icons.Default.Public)
    // Chess tournaments (5.1) - placeable like the others, in the Hub by default (iOS AppTab.chess).
    object Chess       : Screen("chess",        "Chess",        Icons.Default.GridOn)
    // .kachat names (5.2) - Kaspa Hub > .kachat: the marketplace for KaChat's own names (claim,
    // buy and sell, trustless). UI only until the name service is built (iOS AppTab.kachatNames).
    // Drawn as the ".kachat" wordmark wherever tabs are drawn (see tabIconPainter).
    object KachatNames : Screen("kachat_names", ".kachat",      Icons.Default.AlternateEmail)
    // KaChat Stats (5.2) - KaChat's transactions on Kaspa by kind, from the indexers' /stats
    // (iOS AppTab.kachatStats). Read-only, so Simple Mode shows it.
    object KachatStats : Screen("kachat_stats", "Stats",        Icons.Default.BarChart)
    // Address Book (iOS AppTab.addressBook, 00767a4) - Kaspa Hub > Address Book: saved Kaspa
    // addresses with names, per wallet, kept in KaChat (it replaced syncing with the phone's
    // Contacts). Your own saved addresses, so Simple Mode shows it.
    object AddressBook : Screen("address_book", "Address Book", Icons.Outlined.Book)
    // KaChat Bots (5.2.1, iOS AppTab.kachatBots, 82ab31d) - Kaspa Hub > KaChat Bots: Block Reward
    // Tracker, Wallet Tracker, Price Bot and Kaspa Answers; tapping one opens a chat with it.
    // Ordinary chats with the bots, so Simple Mode shows it.
    object KachatBots  : Screen("kachat_bots",  "Bots",         Icons.Outlined.Memory)
    // Holds whatever of the above is turned on but not in the dock - see [kaspaHubSections].
    // Route stays "kaspa_hub" once shipped: it is persisted in saved dock arrangements.
    object KaspaHub    : Screen("kaspa_hub",    "Kaspa Hub",    Icons.Default.BubbleChart)
    // The old "+ More" pseudo-tab (opened Customize Dock from the dock itself) is gone —
    // Customize Dock is reached via Settings > Customization instead. The dock still caps at
    // MAX_DOCK_ITEMS, with over-cap KaPosts/Broadcasts riding the Chats-slot cycle.
}

/**
 * Tabs that always hold a dock slot and cannot be moved into Kaspa Hub (iOS
 * `AppTab.pinnedToDock`).
 *
 * Chats, because it is what KaChat is. Kaspa Hub, because it is what HOLDS whatever is not in the
 * dock - move it inside itself and everything it holds becomes unreachable. Profile, because it is
 * the way to Settings, the account list and the wallet's own addresses, so it must never be a
 * level deeper than the dock. Listed in the order a fresh dock places them, so a missing one can
 * be reinserted sensibly; where they sit among the five is the person's (Customize Dock), that
 * they are there is not.
 */
val PINNED_DOCK_ROUTES = listOf(Screen.KaspaHub.route, Screen.Chats.route, Screen.Profile.route)

/** Tabs the user can place. Chats is listed but pinned (see [PINNED_DOCK_ROUTES]). */
val ASSIGNABLE_TAB_ROUTES = listOf(
    Screen.Chats.route, Screen.Portfolio.route, Screen.ColdStorage.route,
    Screen.Swap.route, Screen.KaPosts.route,
    Screen.KaspaWebsites.route, Screen.Chess.route,
    Screen.KachatNames.route, Screen.KachatStats.route, Screen.AddressBook.route,
    Screen.KachatBots.route
)

/** Route strings for tabs that can never be hidden — see [resolveTabOrder]. */
val ALWAYS_VISIBLE_TAB_ROUTES = setOf(Screen.Chats.route, Screen.Profile.route)

// All top-level tabs, in the app's default order (matches iOS's AppTab.defaultOrder). Settings
// isn't a tab at all (matches iOS) — it's reached one tap in from Profile's gear icon instead,
// see ProfileScreen. A default install shows exactly the five the dock can fit - Kaspa Hub,
// Portfolio, Chats, KaPosts, Profile - with Cold Storage, Swap and the websites list still
// ENABLED but living inside Kaspa Hub rather than competing for a dock slot.
val bottomNavItems = listOf(
    Screen.KaspaHub,
    Screen.Portfolio,
    Screen.Chats,
    Screen.KaPosts,
    Screen.Profile,
    Screen.ColdStorage,
    Screen.AddressBook,
    Screen.KachatNames,
    Screen.KachatStats,
    Screen.KachatBots,
    Screen.Swap,
    Screen.Broadcasts,
    Screen.KaspaWebsites,
    Screen.Chess
)

/** The dock renders at most this many items (matches iOS's AppTab.maxDockItems). */
const val MAX_DOCK_ITEMS = 5

/**
 * Routes hard-hidden everywhere while Child Mode is on (Settings > Security > Child Mode) —
 * matches iOS's AppTab.isEnabled gate. [resolveTabOrder] below is the single choke point every
 * dock consumer flows through (the bar itself, the Chats-slot cycle, Customize Menu's preview),
 * so while it's on these tabs can't render in the dock NOR ride the Chats-slot cycle, regardless
 * of dock settings. Deliberately derived at render time only: the per-account stored dock prefs
 * (tab_order/hidden_tabs) are never rewritten with the masked state, so turning Child Mode off
 * restores exactly the arrangement the user had before.
 */
val CHILD_MODE_HIDDEN_ROUTES = setOf(
    Screen.Swap.route, Screen.KaPosts.route, Screen.Broadcasts.route,
    // A browser onto the open web - the one tab most obviously not for a child's phone.
    Screen.KaspaWebsites.route,
    // Public tournaments with strangers, paid per move (iOS hides Chess in Child Mode too).
    Screen.Chess.route,
    // A marketplace - buying and selling with real KAS - so Simple Mode hides it, like Swap.
    Screen.KachatNames.route
)

/**
 * Maps persisted route strings (from AppSettingsRepository.tabOrder) back to [Screen] objects,
 * in that order. Any route no longer recognized (e.g. a tab removed in a future update) is
 * dropped, and any [Screen] missing from the persisted list (e.g. a tab added since the user
 * last reordered) is appended at the end — so neither a stale persisted value nor an app update
 * can leave a tab permanently missing or crash on an unknown route. [hiddenTabs] then filters out
 * anything the user unchecked in Settings > Customization > Menu (never applied to
 * [ALWAYS_VISIBLE_TAB_ROUTES]). [childMode] hard-hides [CHILD_MODE_HIDDEN_ROUTES] on top of
 * whatever the user's own dock settings say.
 */
@Deprecated("Superseded by resolveDock. Kept only so the one-time placement migration can read what a user could SEE under the old model.")
fun resolveTabOrder(routes: List<String>, hiddenTabs: Set<String>, childMode: Boolean = false): List<Screen> {
    val byRoute = bottomNavItems.associateBy { it.route }
    val resolved = routes.mapNotNull { byRoute[it] }
    val missing = bottomNavItems.filter { it !in resolved }
    // Public Chats moved INTO the Chats screen as its third tab, so the rooms are no longer a
    // destination of their own. The Screen stays so a dock arrangement saved by an older build
    // still decodes; it simply never renders (iOS keeps the AppTab case for the same reason).
    val ordered = (resolved + missing).filterNot { it.route == Screen.Broadcasts.route }
    var visible = ordered.filter { it.route in ALWAYS_VISIBLE_TAB_ROUTES || it.route !in hiddenTabs }
    if (childMode) {
        visible = visible.filter { it.route !in CHILD_MODE_HIDDEN_ROUTES }
    }
    // Dock cap (matches iOS AppTab.visible). Anything enabled that doesn't fit simply isn't in
    // the dock - it is not lost, it is reached through Kaspa Hub. There is no longer a Chats-tab
    // cycle: re-tapping or holding Chats to reach KaPosts and Broadcasts was removed once the Hub
    // gave them a place of their own.
    if (visible.size > MAX_DOCK_ITEMS) {
        visible = visible.take(MAX_DOCK_ITEMS)
    }
    return visible
}

/**
 * Root composable: the chess dock and its NavHost ([ChessMainShell]).
 * Wallet onboarding is shown instead when no wallet exists.
 */
@Composable
fun KaChatApp(walletViewModel: WalletViewModel = hiltViewModel()) {
    val isLoggedIn by walletViewModel.isLoggedIn.collectAsState()
    val mnemonic by walletViewModel.mnemonic.collectAsState()
    val startupResolved by walletViewModel.startupResolved.collectAsState()

    if (!startupResolved) {
        // Cold start, routing decision still loading (the async biometrics-for-login read that
        // gates auto-login into the last-used account). Composing the Onboarding/accounts screen
        // as a transient default flashed the account list for a few frames before jumping into
        // the account. iOS's LoadingView instead - after a moment, since the wait is usually a
        // few milliseconds of DataStore read and a spinner that blinks past is noise.
        var showLoading by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300)
            showLoading = true
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (showLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CircularProgressIndicator(color = LocalAppColors.current.textSecondary)
                    Text(
                        "Loading...",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    } else if (!isLoggedIn || mnemonic != null) {
        OnboardingScreen(walletViewModel)
    } else {
        ChessMainShell(walletViewModel = walletViewModel)
    }
}

