package com.kachat.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kachat.app.services.ChessBotChallenge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import com.kachat.app.services.ChessBotLinkRouter
import com.kachat.app.ui.screens.*
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/** KaChat Chess's dock, in order (iOS ChessMainTabView): fixed, nothing to rearrange. */
val CHESS_DOCK_TABS = listOf(Screen.Play, Screen.Leaderboard, Screen.Wallet, Screen.Settings)

/**
 * Screens pushed inside a tab that keep the dock, as iOS's tab bar stays under a NavigationStack's
 * pushed pages: choosing and watching games, and the Settings pages. A game being played takes the
 * whole screen (see [ChessGameDock]), and so do the sheets iOS presents over the tab bar (the seed
 * phrase, a player's card, the licences).
 */
private val CHESS_DOCKED_ROUTES = CHESS_DOCK_TABS.map { it.route }.toSet() + setOf(
    "chess_home",
    "chess_mode/{mode}",
    "chess_tournament/{tournamentId}",
    CHESS_BOTS_ROUTE,
    CHESS_BOT_ROUTE,
    "chess_leaderboard/{mode}",
    "chess_bot_boards",
    "chess_bot_board/{level}",
    "chess_settings/{page}",
    "connection_settings",
    "kaspa_explorer_settings",
    // Wallet > Help, pushed inside the Wallet tab (iOS 1751d20 ProfileHelpView).
    "wallet_help",
    // Wallet > the Chatting Address square: its QR, pushed (iOS 1751d20 ChattingAddressQRView).
    CHATTING_ADDRESS_QR_ROUTE,
    // Wallet > Manage Addresses and the address pages it pushes (iOS 1751d20 ManageAddressesView).
    "manage_addresses",
    MANAGE_ADDRESS_ROUTE,
    "chatting_address_manage",
    // Wallet > Your Domains and the pages it opens (iOS 1751d20 KNSDomainsListView).
    "your_domains",
)

/** One spending address's page, by its index (iOS 1751d20 SpendingAddressTransactionHistoryView). */
private const val MANAGE_ADDRESS_ROUTE = "manage_address/{index}"

/** Play > Chess and the rooms it opens: a challenge that has to wait waits here. */
private val CHESS_ARENA_ROUTES = setOf("chess_home", "chess_mode/{mode}", "chess_tournament/{tournamentId}")

/** Both can carry a challenge from a KaPost ([ChessBotChallenge.routeArg]). */
private const val CHESS_BOTS_ROUTE = "chess_bots?challenge={challenge}"
private const val CHESS_BOT_ROUTE = "chess_bot/{level}?challenge={challenge}"

/** The game boards: docked unless the player is locked in ([ChessGameDock]). */
private val CHESS_GAME_ROUTES = setOf(
    "chess_tournament_game/{tournamentId}/{gameId}",
    "chess_bot_game/{level}/{side}",
)

/**
 * The signed-in app: the dock and everything it opens. KaChat's own shell (dock arrangement, Kaspa
 * Hub, chats, notifications, deep links, the share sheet) has no place here - only the wallet and
 * Chess Online, as iOS's ChessMainTabView.
 */
@Composable
fun ChessMainShell(walletViewModel: WalletViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val backStack by navController.currentBackStack.collectAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    // A game keeps the dock away only while its player is in it, as iOS hides the tab bar then:
    // someone watching, or a game that is over, keeps it.
    val showDock = currentRoute == null || currentRoute in CHESS_DOCKED_ROUTES ||
        (currentRoute in CHESS_GAME_ROUTES && !ChessGameDock.lockedIn)

    // What KaChat's dock did on appearing (iOS ChessMainTabView's onAppear): a hold left from
    // onboarding is lifted, or nothing would start. There is no welcome guide to lift it.
    LaunchedEffect(Unit) { walletViewModel.consumePendingWelcomeGuide() }

    // The tab a screen sits in: its own route, else the nearest tab under it on the stack.
    val selectedIndex = backStack.asReversed().firstNotNullOfOrNull { entry ->
        CHESS_DOCK_TABS.indexOfFirst { it.route == entry.destination.route }.takeIf { it >= 0 }
    } ?: 0

    fun goToTab(screen: Screen) {
        val onOwnRoute = currentDestination?.hierarchy?.any { it.route == screen.route } == true
        if (onOwnRoute) {
            walletViewModel.notifyTabReselected(screen.route)
            return
        }
        // popBackStack to a tab already on the stack actually pops it; navigate() + popUpTo
        // alone is a silent no-op for the start destination.
        val poppedToExisting = navController.popBackStack(route = screen.route, inclusive = false, saveState = true)
        if (!poppedToExisting) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Frosted glass under the floating dock: the NavHost below is the backdrop.
    val dockHaze = remember { HazeState() }
    Scaffold(
        containerColor = LocalAppColors.current.background,
        bottomBar = {
            if (showDock) {
                ChessDock(selectedIndex = selectedIndex, haze = dockHaze, onSelect = ::goToTab)
            }
        },
    ) { innerPadding ->
        ConnectionStatusOverlayHost()
        val dockInset = innerPadding.calculateBottomPadding()
        val botsViewModel: ChessBotsViewModel = hiltViewModel()
        val arena: ChessTournamentViewModel = hiltViewModel()
        val arenaTournaments by arena.service.tournaments.collectAsState()
        // The dock has no badge since iOS 8d5f925: a tab's first screen shows the backup strip.
        val backupBanner: @Composable () -> Unit = { AccountBackupBanner(walletViewModel) { navController.navigate("account_backup") } }
        CompositionLocalProvider(LocalBackupBanner provides backupBanner) {
        NavHost(
            navController = navController,
            startDestination = Screen.Play.route,
            modifier = Modifier.haze(state = dockHaze),
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) },
        ) {
            chessGraph(navController, walletViewModel, dockInset)
        }
        }
        // A challenge from a KaPost opens on Play > Chess, on that bot's screen, or the ladder if
        // the bot is still locked. A game the player is in comes first: it opens when that is over
        // (iOS ChessMainTabView / ChessHomeView takeChallenge).
        val challenge by ChessBotLinkRouter.challenge.collectAsState()
        // A challenge never pops a board or a waiting room: while the player is busy online - the
        // rooms still loading, seated or playing, or a game of theirs decided on the clock lately
        // with the claim still to come - it waits in the router until that is over
        // (iOS 4922804 ChessTournamentService.isBusyOnline / takeChallenge).
        // Off the clock as well as the rooms: a seat that runs out changes no room, only the time
        // (iOS ChessHomeView re-renders on every tick and sees isBusyOnline go).
        val busyOnline by remember(arena.service) {
            combine(arena.service.tournaments, arena.service.now, arena.service.historyReady) { all, _, _ ->
                arena.service.isBusyOnline(all)
            }.distinctUntilChanged()
        }.collectAsState(initial = arena.service.isBusyOnline(arenaTournaments))
        // It is taken only from a docked screen, never from under a board, a result or a full-screen
        // page (the seed phrase, a backup, an NFC write): iOS takes it "never from under a pushed
        // screen". While a seat or a game holds it, it waits on Play > Chess, which holds the arena
        // so an expired seat clears (iOS ChessMainTabView opens Chess on every challenge).
        // One that had to wait opens only on Play or Chess, as iOS takes it from ChessHomeView (or
        // GamesView re-opening Chess), never as a game's Done lands on a lobby or a bracket.
        var parkedChallenge by remember { mutableStateOf<ChessBotChallenge?>(null) }
        var waitedChallenge by remember { mutableStateOf<ChessBotChallenge?>(null) }
        // The tab being left keeps its pushed pages, as iOS only switches the selection.
        fun popToPlay() {
            val tabRoutes = CHESS_DOCK_TABS.map { it.route }.toSet()
            val onPlayTab = backStack.lastOrNull { it.destination.route in tabRoutes }?.destination?.route == Screen.Play.route
            navController.popBackStack(Screen.Play.route, inclusive = false, saveState = !onPlayTab)
        }
        LaunchedEffect(challenge, ChessGameDock.lockedIn, busyOnline, currentRoute) {
            val taken = challenge ?: return@LaunchedEffect
            if (busyOnline || ChessGameDock.lockedIn) waitedChallenge = taken
            if (ChessGameDock.lockedIn) return@LaunchedEffect
            if (currentRoute != null && (currentRoute in CHESS_GAME_ROUTES || currentRoute !in CHESS_DOCKED_ROUTES)) return@LaunchedEffect
            if (busyOnline) {
                // Once per challenge, and never out of the waiting room the seat is in.
                if (parkedChallenge !== taken && currentRoute !in CHESS_ARENA_ROUTES) {
                    parkedChallenge = taken
                    popToPlay()
                    navController.navigate("chess_home")
                }
                return@LaunchedEffect
            }
            if (waitedChallenge === taken && currentRoute != Screen.Play.route && currentRoute != "chess_home") return@LaunchedEffect
            ChessBotLinkRouter.challenge.value = null
            popToPlay()
            navController.navigate("chess_home")
            navController.navigate(
                if (botsViewModel.progress.isUnlocked(taken.bot)) "chess_bot/${taken.bot.level}?challenge=${taken.routeArg()}"
                else "chess_bots?challenge=${taken.routeArg()}"
            )
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.chessGraph(
    navController: NavHostController,
    walletViewModel: WalletViewModel,
    dockInset: androidx.compose.ui.unit.Dp,
) {
    // A tab swaps instantly: a fade behind the dock's own instant tint reads as sluggish.
    fun tab(screen: Screen, content: @Composable () -> Unit) = composable(
        screen.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        CompositionLocalProvider(LocalDockInset provides dockInset) { content() }
    }

    tab(Screen.Play) { GamesScreen(navController) }
    tab(Screen.Leaderboard) { ChessLeaderboardTabScreen(navController) }
    tab(Screen.Wallet) { ChessWalletScreen(walletViewModel, navController) }
    tab(Screen.Settings) { ChessSettingsScreen(navController, walletViewModel) }

    // Play > Chess, then 1v1 or Tournaments, a tournament's bracket, and a game.
    composable("chess_home") {
        CompositionLocalProvider(LocalDockInset provides dockInset) { ChessHomeScreen(navController) }
    }
    composable("chess_mode/{mode}") { entry ->
        val mode = runCatching {
            ChessLobbyMode.valueOf(entry.arguments?.getString("mode").orEmpty())
        }.getOrDefault(ChessLobbyMode.DUEL)
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChessTournamentsScreen(mode, navController, onBack = { navController.popBackStack() })
        }
    }
    composable("chess_tournament/{tournamentId}") { entry ->
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChessTournamentScreen(entry.arguments?.getString("tournamentId").orEmpty(), navController)
        }
    }
    composable("chess_tournament_game/{tournamentId}/{gameId}") { entry ->
        CompositionLocalProvider(LocalDockInset provides if (ChessGameDock.lockedIn) 0.dp else dockInset) {
            ChessTournamentGameScreen(
                entry.arguments?.getString("tournamentId").orEmpty(),
                entry.arguments?.getString("gameId").orEmpty(),
                navController,
            )
        }
    }
    // Play > Bots: the ladder, one bot, a game against it (iOS ChessBotsView, c3c04a1).
    val challengeArgument = listOf(
        navArgument("challenge") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )
    composable(CHESS_BOTS_ROUTE, arguments = challengeArgument) { entry ->
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChessBotsScreen(navController, ChessBotChallenge.fromRouteArg(entry.arguments?.getString("challenge")))
        }
    }
    composable(CHESS_BOT_ROUTE, arguments = challengeArgument) { entry ->
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChessBotDetailScreen(
                entry.arguments?.getString("level")?.toIntOrNull() ?: 1,
                navController,
                ChessBotChallenge.fromRouteArg(entry.arguments?.getString("challenge")),
            )
        }
    }
    // Leaderboard > 1v1 or Tournaments: that board (iOS 45f28d9 ChessLeaderboardPage).
    composable("chess_leaderboard/{mode}") { entry ->
        val mode = runCatching {
            ChessLobbyMode.valueOf(entry.arguments?.getString("mode").orEmpty())
        }.getOrDefault(ChessLobbyMode.DUEL)
        CompositionLocalProvider(LocalDockInset provides dockInset) { ChessLeaderboardPageScreen(mode, navController) }
    }
    // Leaderboard > Bots: a board per played bot (iOS ChessBotLeaderboardsView, ddec987).
    composable("chess_bot_boards") {
        CompositionLocalProvider(LocalDockInset provides dockInset) { ChessBotLeaderboardsScreen(navController) }
    }
    composable("chess_bot_board/{level}") { entry ->
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChessBotLeaderboardScreen(entry.arguments?.getString("level")?.toIntOrNull() ?: 1, navController)
        }
    }
    composable("chess_bot_game/{level}/{side}") { entry ->
        CompositionLocalProvider(LocalDockInset provides if (ChessGameDock.lockedIn) 0.dp else dockInset) {
            ChessBotGameScreen(
                entry.arguments?.getString("level")?.toIntOrNull() ?: 1,
                entry.arguments?.getString("side").orEmpty(),
                navController,
            )
        }
    }
    // A tapped player, for their card (iOS ChessPlayerView, a sheet with Done).
    composable("chess_player/{address}") { entry ->
        ChessPlayerScreen(
            address = entry.arguments?.getString("address").orEmpty(),
            onDone = { navController.popBackStack() },
        )
    }

    // Settings' pages. They keep the dock, so each stops above it.
    composable("chess_settings/{page}") { entry ->
        Box(Modifier.padding(bottom = dockInset)) {
            ChessSettingsPage(
                page = entry.arguments?.getString("page").orEmpty(),
                navController = navController,
                walletViewModel = walletViewModel,
            )
        }
    }
    composable("connection_settings") {
        Box(Modifier.padding(bottom = dockInset)) {
            ConnectionSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
    composable("kaspa_explorer_settings") {
        Box(Modifier.padding(bottom = dockInset)) {
            KaspaExplorerSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
    composable("seed_phrase") {
        SeedPhraseScreen(viewModel = walletViewModel, onBack = { navController.popBackStack() })
    }
    // The backup steps and the six-monthly check, full screen as iOS's sheets (ddec987).
    composable("account_backup") {
        AccountBackupScreen(viewModel = walletViewModel, onClose = { navController.popBackStack() })
    }
    composable("backup_hygiene") {
        BackupHygieneScreen(viewModel = walletViewModel, onClose = { navController.popBackStack() })
    }
    // Settings > Back Up to NFC Card, beta only (iOS 0877f88 NFCCardBackupView).
    composable("nfc_card_backup") {
        NfcCardBackupScreen(viewModel = walletViewModel, onClose = { navController.popBackStack() })
    }
    composable("open_source_licenses") {
        OpenSourceLicensesScreen(onBack = { navController.popBackStack() })
    }

    // --- Wallet tab: Request, Receive and Help (iOS 1751d20; agent "requests") ---
    // Request and Receive are iOS sheets over the tab, so they take the whole screen here.
    composable("payment_request_create") {
        CreatePaymentRequestContent(
            walletViewModel = walletViewModel,
            onClose = { navController.popBackStack() },
            insets = WindowInsets.systemBars,
        )
    }
    composable("receive_fresh") {
        ReceiveFreshScreen(walletViewModel = walletViewModel, onDone = { navController.popBackStack() })
    }
    // Help is pushed inside the Wallet tab (docked); the .kachat names guide covers everything
    // (iOS's fullScreenCover).
    composable("wallet_help") {
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            WalletHelpScreen(
                onBack = { navController.popBackStack() },
                onKachatGuide = { navController.navigate("wallet_help_guide") },
            )
        }
    }
    composable("wallet_help_guide") {
        KachatSetupGuideScreen(onClose = { navController.popBackStack() })
    }

    // --- Wallet tab: the Chatting Address square (iOS 1751d20; agent "wallet-tab") ---
    // Pushed inside the Wallet tab, so the dock stays (ChessWalletTab.kt).
    composable(CHATTING_ADDRESS_QR_ROUTE) {
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChattingAddressQrRouteScreen(walletViewModel = walletViewModel, onBack = { navController.popBackStack() })
        }
    }

    // --- Wallet tab: Your Domains (iOS 1751d20 KNSDomainsListView; agent "domains") ---
    // Pushed inside the Wallet tab, so the dock stays (YourDomainsScreens.kt). A .kas domain's
    // detail, Customize Profile and Send, and a .kachat name's detail, open inside it; so does a
    // bell row's .kachat name (WalletBellRoute.pendingKachatName).
    composable("your_domains") {
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            KnsDomainsScreen(viewModel = walletViewModel, onBack = { navController.popBackStack() })
        }
    }

    // --- Wallet tab: Manage Addresses (iOS 1751d20 ManageAddressesView; agent "addresses") ---
    // Pushed inside the Wallet tab, so the dock stays (ManageAddressesScreens.kt).
    composable("manage_addresses") {
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ManageAddressesScreen(
                viewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onOpenChattingAddress = { navController.navigate("chatting_address_manage") },
                onOpenAddress = { index -> navController.navigate("manage_address/$index") },
            )
        }
    }
    composable(MANAGE_ADDRESS_ROUTE) { entry ->
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            SpendingAddressHistoryScreen(
                index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0,
                viewModel = walletViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable("chatting_address_manage") {
        CompositionLocalProvider(LocalDockInset provides dockInset) {
            ChattingAddressManageScreen(viewModel = walletViewModel, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * The floating glass dock: four tabs on a frosted bar, a filled capsule under the selected one,
 * and a lens that follows a finger sliding along it - KaChat's dock, with the four fixed tabs.
 */
@Composable
private fun ChessDock(selectedIndex: Int, haze: HazeState, onSelect: (Screen) -> Unit) {
    val tabs = CHESS_DOCK_TABS
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // navigationBarsPadding() first, so the margin sits above the system nav bar.
            .navigationBarsPadding()
            // iOS 26's tab bar: 21pt in from each side (measured on the iPhone).
            .padding(bottom = 12.dp, start = 21.dp, end = 21.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Null while nobody is sliding a finger along the bar.
        var dragIndex by remember { mutableStateOf<Int?>(null) }
        val latestOnSelect by rememberUpdatedState(onSelect)
        // iOS's bar is 60pt tall (measured on the iPhone 17).
        BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
            val itemWidth = (maxWidth - 4.dp) / tabs.size
            val pillIndex = dragIndex ?: selectedIndex
            val pillOffset by animateDpAsState(
                targetValue = 2.dp + itemWidth * pillIndex,
                animationSpec = if (dragIndex != null) tween(90) else spring(dampingRatio = 0.75f, stiffness = 420f),
                label = "dockPill",
            )
            Box(
                Modifier
                    .height(60.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .hazeChild(
                        state = haze,
                        shape = RoundedCornerShape(30.dp),
                        style = HazeStyle(tint = toolbarGlassFill().copy(alpha = 0.92f), blurRadius = 24.dp),
                    )
                    .border(0.5.dp, toolbarGlassEdge(), RoundedCornerShape(30.dp)),
            )
            if (dragIndex == null) {
                // At rest iOS 26 marks the tab with a filled capsule, 4pt inside the bar's height.
                Box(
                    Modifier
                        .offset(x = pillOffset)
                        .padding(vertical = 4.dp)
                        .width(itemWidth)
                        .height(52.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (LocalAppColors.current.isDark) Color(0xFF3A3A3A) else Color.Black.copy(alpha = 0.07f)),
                )
            } else {
                // While a finger slides: a nearly clear lens with a bright rim, a little proud of the bar.
                val lensShape = RoundedCornerShape(percent = 50)
                Box(
                    Modifier
                        .offset(x = pillOffset - 3.dp, y = (-4).dp)
                        .width(itemWidth + 6.dp)
                        .height(68.dp)
                        .clip(lensShape)
                        .hazeChild(state = haze, shape = lensShape, style = HazeStyle(tint = Color.White.copy(alpha = 0.06f), blurRadius = 30.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.55f), KaspaTeal.copy(alpha = 0.55f), Color.White.copy(alpha = 0.30f)),
                            ),
                            shape = lensShape,
                        ),
                )
            }
            val density = LocalDensity.current
            Row(
                modifier = Modifier
                    .height(60.dp)
                    .fillMaxWidth()
                    // A slide starting on an outer tab began inside the system Back strip.
                    .systemGestureExclusion()
                    .pointerInput(itemWidth) {
                        val slotPx = with(density) { itemWidth.toPx() }
                        val startPadPx = with(density) { 2.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var sliding = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (sliding) dragIndex?.let { tabs.getOrNull(it)?.let(latestOnSelect) }
                                        break
                                    }
                                    if (!sliding && kotlin.math.abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) sliding = true
                                    if (sliding) {
                                        change.consume()
                                        dragIndex = ((change.position.x - startPadPx) / slotPx).toInt().coerceIn(0, tabs.lastIndex)
                                    }
                                }
                            } finally {
                                // A slide the system takes away cancels rather than releasing.
                                dragIndex = null
                            }
                        }
                    }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                tabs.forEachIndexed { index, screen ->
                    val selected = index == selectedIndex
                    val tint = if (selected) KaspaTeal else LocalAppColors.current.textPrimary
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable { onSelect(screen) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                Icon(
                                    painter = screen.tabIconPainter(),
                                    contentDescription = screen.label,
                                    tint = tint,
                                    // iOS's tab glyphs are about 24pt (the gear, measured on the
                                    // iPhone); Material's fill about 20 of a 24 box, hence 28. The
                                    // controller is its own 32 x 20 picture, in the same 28 box.
                                    modifier = if (screen == Screen.Play) Modifier.size(width = 32.dp, height = 28.dp) else screen.tabIconModifier(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = screen.label,
                                color = tint,
                                fontSize = if (screen.label.length > 9) 9.sp else 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                // iOS 26's tab labels are medium weight; only the colour marks the selected tab.
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
