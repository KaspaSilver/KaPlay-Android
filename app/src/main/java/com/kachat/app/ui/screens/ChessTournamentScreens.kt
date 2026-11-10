package com.kachat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.kachat.app.R
import com.kachat.app.models.ContactEntity
import com.kachat.app.models.displayName
import com.kachat.app.services.ChessTournamentService
import com.kachat.app.ui.tabIconModifier
import com.kachat.app.ui.tabIconPainter
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.ChessColor
import com.kachat.app.util.ChessEngine
import com.kachat.app.util.ChessMove
import com.kachat.app.util.ChessPiece
import com.kachat.app.util.ChessPieceType
import com.kachat.app.util.ChessSquare
import com.kachat.app.util.ChessTournament
import com.kachat.app.util.ChessTournamentCodec
import com.kachat.app.util.ChessTournamentGame
import com.kachat.app.util.ChessTournamentOutcome
import com.kachat.app.util.KaspaUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Play (5.1): the lobby, one tournament, one game, and the leaderboard. Mirrors
// iOS's Views/Chess. Every screen holds ChessTournamentService.acquire() while it is up, so the
// arena is scanned exactly as long as someone is looking at chess.

@HiltViewModel
class ChessTournamentViewModel @Inject constructor(
    val service: ChessTournamentService,
    private val chatRepository: com.kachat.app.repository.ChatRepository,
    private val walletManager: com.kachat.app.services.WalletManager,
) : ViewModel() {
    val contacts: StateFlow<Map<String, ContactEntity>> = chatRepository.getContacts()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** A player tapped on a leaderboard: User Info opens on them, so they have to be a contact
     *  first - the same as tapping an avatar in a public room (BroadcastViewModel.openSenderProfile). */
    fun openPlayerProfile(address: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            if (chatRepository.getContact(address) == null) {
                runCatching {
                    chatRepository.addContact(
                        ContactEntity(
                            id = address,
                            walletAddress = walletManager.getAddress(),
                            alias = null,
                            knsName = null,
                            publicKeyHex = null,
                        )
                    )
                }
            }
            onReady(address)
        }
    }

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * The app's naming rule, in the arena too: the contact's name, then their KNS domain, then the
 * shortened address - and the same for the player themselves, never "You" (iOS 30d0cca).
 */
internal fun chessName(
    address: String,
    contacts: Map<String, ContactEntity>,
    knsNames: Map<String, String> = emptyMap(),
): String {
    contacts[address]?.let { return it.displayName }
    // the app's one rule: on testnet the .kachat name, KNS elsewhere (iOS e52357d)
    return com.kachat.app.models.addressDisplayName(address, knsName = knsNames[address])
}

@Composable
internal fun ChessAvatar(address: String, contacts: Map<String, ContactEntity>, size: Int = 36) {
    val contact = contacts[address]
    ContactAvatar(
        imageUrl = contact?.knsAvatarUrl,
        // The one name rule (your name for them, else their .kachat name on testnet, else the
        // short address) - stripping only "kaspa:" left every testnet initial as "ka".
        fallbackText = contact?.displayName ?: com.kachat.app.models.addressDisplayName(address),
        size = size.dp,
        address = address,
    )
}

/** Holds the arena scan for as long as the calling screen is composed. */
@Composable
internal fun HoldArena(service: ChessTournamentService) {
    DisposableEffect(Unit) {
        service.acquire()
        onDispose { service.release() }
    }
}

/**
 * iOS's `.toast(message: service.lastError, style: .error)` on every chess screen: the red toast,
 * up for as long as the service holds the error - the next action that goes through clears it.
 */
@Composable
private fun ChessErrorToast(service: ChessTournamentService) {
    val error by service.lastError.collectAsState()
    IosToastWhile(error, IosToastStyle.Error)
}

/** An insetGrouped list's section header as iOS 26 draws it: title case, 17pt semibold in the
 *  secondary colour, lined up with the footer text (measured on the iPhone). */
@Composable
internal fun ChessSectionHeader(text: String) {
    Text(
        text,
        color = LocalAppColors.current.textSecondary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** The line a chess screen shows while the chess server isn't answering: what's on screen may be
 *  behind, and says so (iOS da59726 ChessServerDownNote). */
@Composable
internal fun ChessServerDownNote(
    modifier: Modifier = Modifier,
    text: String = "Chess server unreachable, showing what we have. Your games keep going from the network. Public rooms can be joined again when it's back.",
) {
    val colors = LocalAppColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(text, color = colors.textSecondary, fontSize = 12.sp)
    }
}

/** [ChessServerDownNote] as its own card at the head of a list, as iOS's section. */
@Composable
internal fun ChessServerDownCard(text: String? = null) {
    ChessCard {
        if (text != null) ChessServerDownNote(Modifier.padding(16.dp), text) else ChessServerDownNote(Modifier.padding(16.dp))
    }
}

@Composable
internal fun ChessCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            // iOS 26's insetGrouped rows: 26 round (measured on the iPhone).
            .clip(RoundedCornerShape(26.dp))
            .background(LocalAppColors.current.surface),
    ) { content() }
}

/** How long the waiting room waits for a join to show up before giving up on it. */
private const val SEAT_WAIT_MS = 30_000L

private fun clockText(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

// MARK: - Lobby

/** Which game: also the name of the play tab. */
enum class ChessLobbyMode(val label: String) { DUEL("1v1"), TOURNAMENT("Tournaments") }

/**
 * Play > 1v1 or Tournaments: one kind of game per screen, chosen on [ChessHomeScreen]. Tabs: the
 * play tab (a public room that pairs the next joiners or fills to eight, plus private games by
 * code), Active and Finished. Mirrors iOS's ChessTournamentsView.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChessTournamentsScreen(mode: ChessLobbyMode, navController: NavController, onBack: (() -> Unit)? = null) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    HoldArena(service)
    ChessErrorToast(service)
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val now by service.now.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val me = service.myAddress
    val mine = service.myActiveTournament(all)
    val serverDown by service.arenaServerDown.collectAsState()
    val historyReady by service.historyReady.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var creatorCode by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var showJoinPrivate by remember { mutableStateOf(false) }
    var privateCode by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    /** The waiting room on screen (full-screen, nothing else reachable). */
    var waitingRoomId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    /** iOS's waitingNotice: why the waiting room closed on its own (the seat ran out). */
    var waitingNotice by remember { mutableStateOf<String?>(null) }

    fun open(id: String) = navController.navigate("chess_tournament/$id")

    // Seated in a room that is still filling - a fresh join, a relaunch, or coming back here:
    // the waiting room is the only place to be. And once it starts, the game is: a room that
    // filled while this screen was up (or while the app was away) must not leave the player on
    // the lobby being told they are already playing somewhere.
    var openedGameForRoom by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(mine?.id, mine?.status, mine?.games?.size, now) {
        val room = mine ?: return@LaunchedEffect
        val address = me ?: return@LaunchedEffect
        when (room.status) {
            // Still filling and holding a seat: the waiting room is the only place to be.
            ChessTournament.Status.OPEN -> {
                if (room.isSeated(address, now)) waitingRoomId = room.id
            }
            ChessTournament.Status.LIVE -> {
                // Just filled: the waiting room's "Match found" countdown. A join that took the
                // last seat lands here too - the room is live before this screen ever sees it
                // open, and without this the player sat on the lobby being told they are already
                // playing somewhere (iOS 36bb3de).
                if (now < (room.startedAt ?: 0L) + com.kachat.app.util.ChessTournamentCodec.MATCH_FOUND_DELAY_MS) {
                    if (waitingRoomId != room.id) waitingRoomId = room.id
                } else if (address in room.players) {
                    // Past the countdown: straight onto the board, the bracket a tap away from it.
                    val game = room.currentGame(address) ?: return@LaunchedEffect
                    if (game.isOver || openedGameForRoom == room.id) return@LaunchedEffect
                    openedGameForRoom = room.id
                    waitingRoomId = null
                    navController.navigate("chess_tournament_game/${room.id}/${game.id}")
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = colors.background,
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        // iOS's pushed pages carry the back button and the balance only: the title is inline and
        // the balance takes the bar's centre, and the connection dot is on the dock's root screens.
        topBar = { ChessHomeBar(onBack = onBack, showConnectionDot = false, title = mode.label) },
    ) { padding ->
        // The same tab bar and swipe the Chats screen uses, so a tab is a tab wherever it
        // appears in the app (iOS 353f040).
        // Both screens: play, Active (watch-only), Finished. The leaderboards have a tab of their
        // own in the dock (ChessLeaderboardTabScreen).
        val tabs = listOf(mode.label, "Active", "Finished")
        val pagerState = androidx.compose.foundation.pager.rememberPagerState { tabs.size }
        val tabScope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().padding(padding)) {
            // iOS's tab buttons: subheadline bold 12 under the bar, 8 above a 2.5 underline in
            // the accent, then a divider across the screen.
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    val selected = pagerState.currentPage == index
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { tabScope.launch { pagerState.animateScrollToPage(index) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(12.dp))
                        ChessTabLabel(label = label, selected = selected)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(2.5.dp).background(if (selected) KaspaTeal else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                1 -> { ChessActiveGames(mode, navController); return@HorizontalPager }
                2 -> { ChessFinishedGames(mode, navController); return@HorizontalPager }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockInset.current + 32.dp)) {
                val duel = mode == ChessLobbyMode.DUEL
                val publicId = if (duel) service.currentDuelRoomId(all) else service.currentPublicRoomId(all)
                val number = (if (duel) ChessTournamentCodec.duelNumber(publicId) else ChessTournamentCodec.publicNumber(publicId)) ?: 1
                if (serverDown) {
                    Spacer(Modifier.height(12.dp))
                    ChessServerDownCard()
                }
                ChessSectionHeader("Public")
                ChessCard {
                    PublicRoomCard(
                        room = all[publicId],
                        now = now,
                        title = if (duel) "Public 1v1 #$number" else "Public tournament #$number",
                        capacity = if (duel) 2 else ChessTournamentCodec.PLAYER_COUNT,
                        me = me,
                        myActive = mine,
                        isJoining = isJoining,
                        serverDown = serverDown,
                        historyReady = historyReady,
                        joinLabel = service.joinLabel(publicId),
                        onOpen = ::open,
                        onJoin = {
                            if (!isJoining) {
                                isJoining = true
                                // Straight into the waiting room: the seat only exists once the
                                // transaction lands, and sitting on the lobby until then looks
                                // like the button did nothing. Closed again if nothing was sent.
                                waitingRoomId = publicId
                                vm.launch {
                                    // The service re-syncs with the indexer before choosing, so
                                    // the room it joined is the one to wait in - not this screen's
                                    // guess, which may be a room the others have moved past.
                                    val joined = if (duel) service.joinPublicDuelQueue() else service.joinPublicQueue()
                                    isJoining = false
                                    if (waitingRoomId == publicId) waitingRoomId = joined
                                }
                            }
                        },
                    )
                }
                Text(
                    if (duel) "Join and you are paired with the next person who joins. When a room fills, the game starts and the next room opens. Five minutes a side; every move is a Kaspa transaction (about 0.0017 ${KaspaUnit.symbol} each). Games here count on the leaderboard."
                    else "There is always a public room waiting for players. When it fills, it starts and the next one opens. Eight players, single elimination, five minutes a side. Every move is a Kaspa transaction (about 0.0017 ${KaspaUnit.symbol} each).",
                    color = colors.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )

                ChessSectionHeader("Private")
                ChessCard {
                    val privates = if (duel) service.myPrivateDuels(all) else service.myPrivateTournaments(all)
                    privates.forEach { t ->
                        val action = when {
                            t.status != ChessTournament.Status.OPEN -> if (service.hasUnclaimedWin(t)) "Claim win" else "In play"
                            duel -> "Waiting"
                            else -> "${t.seatsLeft} seat${if (t.seatsLeft == 1) "" else "s"} left"
                        }
                        TournamentRow(t, action) { open(t.id) }
                        ChessRowDivider()
                    }
                    ChessActionRow(Icons.Default.Key, "Join with a code") { privateCode = ""; showJoinPrivate = true }
                    ChessRowDivider()
                    ChessActionRow(Icons.Default.AddCircleOutline, if (duel) "Create a private 1v1" else "Create a private tournament") {
                        creatorCode = ""; showCreate = true
                    }
                }
                Text(
                    if (duel) "Play a friend: create a 1v1, share its code. Private 1v1s count on the leaderboard too."
                    else "A private tournament is for friends: the creator shares its eight-character code. Creating one needs the creator code.",
                    color = colors.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                )

                // Games in play and finished ones have their own tabs now.
            }
            }
        }
    }

    // iOS's `.toast(message: waitingNotice, style: .error)`: up while this screen is.
    IosToastWhile(waitingNotice, IosToastStyle.Error)

    waitingRoomId?.let { id ->
        ChessWaitingRoom(
            tournamentId = id,
            onStarted = { started ->
                waitingRoomId = null
                // Straight to the board the player is in; the bracket only when there is no game
                // of theirs to open (a room they are watching rather than playing in).
                val game = me?.let { all[started]?.currentGame(it) }?.takeIf { !it.isOver }
                if (game != null) {
                    openedGameForRoom = started
                    navController.navigate("chess_tournament_game/$started/${game.id}")
                } else {
                    open(started)
                }
            },
            onFinished = { seatExpired ->
                waitingRoomId = null
                if (seatExpired) {
                    waitingNotice = "No one joined in time. You're out of the queue - join again whenever you like."
                }
            },
        )
    }

    if (showCreate) {
        val duel = mode == ChessLobbyMode.DUEL
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { if (!isCreating) showCreate = false },
            title = { Text(if (duel) "Create a private 1v1" else "Create a private tournament") },
            text = {
                Column {
                    Text(
                        if (duel) "You get a code to share with the person you want to play. The game starts when they join. Creating it is one transaction."
                        else "You take the first seat and get a code to share. It starts when eight players have joined. Creating it is one transaction."
                    )
                    if (!duel) {
                        Spacer(Modifier.height(12.dp))
                        com.kachat.app.ui.theme.IosTextField(value = creatorCode, onValueChange = { creatorCode = it }, singleLine = true, placeholder = { Text("Creator code") })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isCreating) return@TextButton
                    isCreating = true
                    vm.launch {
                        val id = if (duel) service.createPrivateDuel() else service.createPrivateTournament(creatorCode)
                        isCreating = false
                        showCreate = false
                        // The creator holds the first seat: straight into the waiting room.
                        if (id != null) waitingRoomId = id
                    }
                }) { Text(if (isCreating) "Creating…" else "Create", color = KaspaTeal) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }, enabled = !isCreating) { Text("Cancel") } },
        )
    }

    if (showJoinPrivate) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { if (!isJoining) showJoinPrivate = false },
            title = { Text("Join with a code") },
            text = {
                Column {
                    Text(
                        "The eight-character code the creator shared. Joining is one transaction (fee: " +
                            "${service.feeText(ChessTournamentCodec.join("abcdefgh")) ?: "--"})."
                    )
                    Spacer(Modifier.height(12.dp))
                    com.kachat.app.ui.theme.IosTextField(
                        value = privateCode, onValueChange = { privateCode = it }, singleLine = true, placeholder = { Text("Code") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, autoCorrect = false,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isJoining) return@TextButton
                    isJoining = true
                    vm.launch {
                        val code = privateCode
                        val joined = service.joinPrivate(code)
                        isJoining = false
                        showJoinPrivate = false
                        if (joined) waitingRoomId = code.trim().lowercase()
                    }
                }) { Text(if (isJoining) "Joining…" else "Join", color = KaspaTeal) }
            },
            dismissButton = { TextButton(onClick = { showJoinPrivate = false }, enabled = !isJoining) { Text("Cancel") } },
        )
    }
}

/**
 * Every public 1v1 being played right now, for anyone to watch - board and clocks live, and
 * nothing sent: the game screen gives its composer to the two players only (iOS 3e366a4).
 */
@Composable
private fun ChessActiveGames(mode: ChessLobbyMode, navController: NavController) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val now by service.now.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val duel = mode == ChessLobbyMode.DUEL
    val live = service.liveTournaments(all).filter { it.isDuel == duel && it.isPublic }
    val serverDown by service.arenaServerDown.collectAsState()

    @Composable
    fun liveGameRow(game: ChessTournamentGame, tournament: ChessTournament) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { navController.navigate("chess_tournament_game/${tournament.id}/${game.id}") }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(chessName(game.white, contacts, knsNames), color = colors.textPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("  vs  ", color = colors.textSecondary, fontSize = 14.sp)
                    Text(chessName(game.black, contacts, knsNames), color = colors.textPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                val where = if (tournament.isDuel) tournament.name else when (game.round) {
                    3 -> "Final"; 2 -> "Semifinal"; else -> "Round 1"
                }
                Text(
                    "$where · move ${game.moves.size / 2 + 1} · ${if (game.sideToMove == ChessColor.WHITE) "white" else "black"} to move",
                    color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(clockText(game.remainingMs(game.sideToMove, now)), color = colors.textSecondary,
                fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text("Watch", color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockInset.current + 32.dp)) {
        if (serverDown) {
            Spacer(Modifier.height(12.dp))
            ChessServerDownCard()
        }
        if (live.isEmpty()) {
            ChessSectionHeader("Live now")
            ChessCard {
                Text(
                    if (duel) "No 1v1 games are being played right now. When one starts, it shows up here to watch."
                    else "No tournament is being played right now. When one starts, its games show up here to watch.",
                    color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                )
            }
        } else if (duel) {
            // One row per game.
            ChessSectionHeader("Live now")
            ChessCard {
                live.mapNotNull { room -> room.games.values.firstOrNull()?.let { it to room } }
                    .let { games -> ChessRows(games, start = 54.dp) { (game, room) -> liveGameRow(game, room) } }
            }
        } else {
            // A section per tournament, a row per game in play, and the bracket a tap away.
            live.forEach { tournament ->
                ChessSectionHeader(tournament.name)
                ChessCard {
                    val playing = tournament.games.values.filter { !it.isDecided(now) }.sortedWith(compareBy({ it.round }, { it.id }))
                    ChessRows(playing, start = 54.dp) { liveGameRow(it, tournament) }
                    if (playing.isNotEmpty()) ChessRowDivider(start = 54.dp)
                    TournamentRow(tournament, "Bracket") { navController.navigate("chess_tournament/${tournament.id}") }
                }
            }
        }
        Text(
            "Watching is free - nothing is sent. Only the two players can move.",
            color = colors.textSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
        )
    }
}

/**
 * Every public game of this kind that has ended, newest first - the result on the row, the board
 * a tap away (iOS 8afa81a).
 */
@Composable
private fun ChessFinishedGames(mode: ChessLobbyMode, navController: NavController) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val duel = mode == ChessLobbyMode.DUEL
    val now by service.now.collectAsState()
    val done = service.finishedTournaments(all).filter { it.isDuel == duel && it.isPublic }.take(100)
    val serverDown by service.arenaServerDown.collectAsState()

    @Composable
    fun finishedGameRow(game: ChessTournamentGame, tournament: ChessTournament) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { navController.navigate("chess_tournament_game/${tournament.id}/${game.id}") }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(chessName(game.white, contacts, knsNames), color = colors.textPrimary, fontSize = 14.sp,
                        fontWeight = if (game.decidedWinner(now) == game.white) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    Text("  vs  ", color = colors.textSecondary, fontSize = 14.sp)
                    Text(chessName(game.black, contacts, knsNames), color = colors.textPrimary, fontSize = 14.sp,
                        fontWeight = if (game.decidedWinner(now) == game.black) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
                val where = if (tournament.isDuel) tournament.name else when (game.round) {
                    3 -> "Final"; 2 -> "Semifinal"; else -> "Round 1"
                }
                Text(
                    "$where · ${gameStatus(game, now, contacts, knsNames)}",
                    color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockInset.current + 32.dp)) {
        if (serverDown) {
            Spacer(Modifier.height(12.dp))
            ChessServerDownCard()
        }
        if (done.isEmpty()) {
            ChessSectionHeader(if (duel) "Finished 1v1 games" else "Finished tournaments")
            ChessCard {
                Text(
                    if (duel) "No finished 1v1 games yet." else "No finished tournaments yet.",
                    color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                )
            }
        } else if (duel) {
            // A finished 1v1 is one game: tap it and the board is there as it ended.
            ChessSectionHeader("Finished 1v1 games")
            ChessCard {
                ChessRows(done.mapNotNull { room -> room.games.values.firstOrNull()?.let { it to room } }, start = 54.dp) { (game, room) ->
                    finishedGameRow(game, room)
                }
            }
        } else {
            // Finished tournaments as a list; each opens its bracket - the champion at the end of
            // it, every game a tap away to see the board as it was left (iOS 4317bcc).
            ChessSectionHeader("Finished tournaments")
            ChessCard {
                ChessRows(done, start = 54.dp) { tournament ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { navController.navigate("chess_tournament/${tournament.id}") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tournament.name, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                tournament.champion?.let { "Won by ${chessName(it, contacts, knsNames)}" } ?: "Finished",
                                color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** The one public room taking players: its seats, and Join - or where you already are. */
@Composable
private fun PublicRoomCard(
    room: ChessTournament?,
    now: Long,
    title: String,
    capacity: Int,
    me: String?,
    myActive: ChessTournament?,
    isJoining: Boolean,
    /** The chess server isn't answering (iOS da59726). */
    serverDown: Boolean,
    /** The arena's first page is in, or there's no waiting for it: rooms can be picked (iOS da59726). */
    historyReady: Boolean,
    /** "Join (Fee: 0.00170000 KAS)" - what this join costs (iOS 3076f66). */
    joinLabel: String,
    onOpen: (String) -> Unit,
    onJoin: () -> Unit,
) {
    val colors = LocalAppColors.current
    // Only live seats count: one older than five minutes has expired (iOS 7206e25).
    val seated = room?.seatedPlayers(now).orEmpty()
    val count = seated.size
    val inThisRoom = me != null && me in seated
    // Without the chess server this phone can't know the room's number or its seats (iOS da59726).
    val unconfirmed = serverDown && !inThisRoom
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (capacity == 2) Icons.Default.People else Icons.Default.Groups, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                if (unconfirmed) {
                    Text(if (capacity == 2) "Public 1v1" else "Public tournament", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Seats can't be checked right now", color = colors.textSecondary, fontSize = 12.sp)
                } else {
                    Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("$count of $capacity player${if (capacity == 1) "" else "s"} waiting", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
        if (!unconfirmed) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(capacity) { seat ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (seat < count) KaspaTeal else colors.textSecondary.copy(alpha = 0.25f)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            inThisRoom && room != null -> {
                ChessPill("You're in. Waiting for ${maxOf(0, capacity - count)} more…", filled = false) { onOpen(room.id) }
                if (me != null) {
                    room.seatExpiry(me)?.let { expiry ->
                        val left = maxOf(0L, (expiry - now) / 1000)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your seat is held for ${left / 60}:%02d".format(left % 60) +
                                ". If the room hasn't filled by then, you're out of the queue.",
                            color = colors.textSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }
            myActive != null ->
                ChessPill(
                    if (myActive.status == ChessTournament.Status.OPEN) "You're waiting in ${myActive.name}" else "You're playing in ${myActive.name}",
                    filled = false,
                ) { onOpen(myActive.id) }
            unconfirmed -> ChessPill("Waiting for the chess server", filled = false) {}
            else -> ChessPill(
                if (isJoining) "Joining…" else if (historyReady) joinLabel else "Loading rooms…",
                filled = historyReady,
            ) { if (historyReady) onJoin() }
        }
    }
}

@Composable
private fun ChessPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (filled) KaspaTeal else KaspaTeal.copy(alpha = 0.15f))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) Color.Black else KaspaTeal, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun ChessActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    // iOS's Label in a list row: the 17pt body text in the accent colour, after its icon.
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = KaspaTeal, fontSize = 17.sp)
    }
}

/** [items] as a card's rows, a [ChessRowDivider] between each two. */
@Composable
private fun <T> ChessRows(items: List<T>, start: androidx.compose.ui.unit.Dp, row: @Composable (T) -> Unit) {
    items.forEachIndexed { index, item ->
        if (index > 0) ChessRowDivider(start = start)
        row(item)
    }
}

/** The separator between two rows of a card, as iOS's list draws it: a hairline that starts where
 *  the rows' text does (after the icon) and stops short of the card's trailing edge. */
@Composable
internal fun ChessRowDivider(start: androidx.compose.ui.unit.Dp = 50.dp) {
    HorizontalDivider(
        color = LocalAppColors.current.divider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = start, end = 16.dp),
    )
}

@Composable
private fun TournamentRow(tournament: ChessTournament, action: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                tournament.status == ChessTournament.Status.FINISHED -> Icons.Default.EmojiEvents
                !tournament.isPublic -> Icons.Default.Lock
                tournament.isDuel -> Icons.Default.People
                else -> Icons.Default.Groups
            },
            contentDescription = null,
            tint = KaspaTeal,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tournament.name, color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${tournament.players.size} of ${tournament.capacity} players" + if (tournament.isPublic) "" else " · code ${tournament.id}",
                color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(action, color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/**
 * One chess tab's label: bold, on one line, teal for the tab you are on and half-strength for
 * the ones you are not (iOS's tabButton). A label too long for its quarter of the screen shrinks
 * to fit rather than being cut off, down to 80% of its size - iOS's `minimumScaleFactor(0.8)`,
 * which is what keeps "Leaderboard" whole.
 */
@Composable
private fun ChessTabLabel(label: String, selected: Boolean) {
    // subheadline, shrinking to 0.8 of it before it would cut a word (minimumScaleFactor).
    val maxSize = 15.sp
    val minSize = maxSize * 0.8f
    var fontSize by remember(label) { mutableStateOf(maxSize) }
    Text(
        label,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        color = if (selected) KaspaTeal else KaspaTeal.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 2.dp),
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minSize.value) {
                val next = fontSize * 0.94f
                fontSize = if (next.value < minSize.value) minSize else next
            }
        },
    )
}

// MARK: - Leaderboard


/**
 * The tournament as a bracket - quarterfinals, semifinals, final, champion - with the pairs
 * joined by lines and every game a card you can tap to watch. A live game carries a LIVE pill
 * with the running clock; a finished one marks its winner and dims the loser; a pair not decided
 * yet names who it is waiting on. Your own games get the accent border. Mirrors iOS's
 * ChessBracketView (7049e70).
 */
@Composable
private fun ChessBracket(
    tournament: ChessTournament,
    me: String?,
    now: Long,
    contacts: Map<String, ContactEntity>,
    knsNames: Map<String, String>,
    onOpenGame: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val cardWidth = 164.dp
    val cardHeight = 66.dp
    val rowGap = 14.dp
    val columnGap = 40.dp
    val density = LocalDensity.current

    val columnX = List(4) { (cardWidth + columnGap) * it.toFloat() }
    val round1Y = List(4) { (cardHeight + rowGap) * it.toFloat() }
    val semiY = listOf((round1Y[0] + round1Y[1]) / 2f, (round1Y[2] + round1Y[3]) / 2f)
    val finalY = (semiY[0] + semiY[1]) / 2f
    val contentWidth = columnX[3] + cardWidth
    val contentHeight = round1Y[3] + cardHeight

    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row {
            listOf("QUARTERFINALS", "SEMIFINALS", "FINAL", "CHAMPION").forEachIndexed { index, title ->
                Text(
                    title,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.width(cardWidth),
                )
                if (index < 3) Spacer(Modifier.width(columnGap))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(contentWidth).height(contentHeight)) {
            // The lines joining each pair to the game it feeds.
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val w = with(density) { cardWidth.toPx() }
                val h = with(density) { (cardHeight / 2f).toPx() }
                val gap = with(density) { (columnGap / 2f).toPx() }
                fun join(fromX: Dp, fromYs: List<Dp>, toX: Dp, toY: Dp) {
                    val fromXPx = with(density) { fromX.toPx() }
                    val toXPx = with(density) { toX.toPx() }
                    val toYPx = with(density) { toY.toPx() } + h
                    for (y in fromYs) {
                        val yPx = with(density) { y.toPx() } + h
                        drawLine(colors.textSecondary.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(fromXPx + w, yPx), androidx.compose.ui.geometry.Offset(fromXPx + w + gap, yPx), strokeWidth = 3f)
                        drawLine(colors.textSecondary.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(fromXPx + w + gap, yPx), androidx.compose.ui.geometry.Offset(fromXPx + w + gap, toYPx), strokeWidth = 3f)
                    }
                    drawLine(colors.textSecondary.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(fromXPx + w + gap, toYPx), androidx.compose.ui.geometry.Offset(toXPx, toYPx), strokeWidth = 3f)
                }
                join(columnX[0], listOf(round1Y[0], round1Y[1]), columnX[1], semiY[0])
                join(columnX[0], listOf(round1Y[2], round1Y[3]), columnX[1], semiY[1])
                join(columnX[1], listOf(semiY[0], semiY[1]), columnX[2], finalY)
                join(columnX[2], listOf(finalY), columnX[3], finalY)
            }
            // Round 1 pairs by seed: 1v8, 2v7, 3v6, 4v5.
            repeat(4) { index ->
                ChessBracketCard(
                    game = tournament.games["1-$index"],
                    placeholder = "Seed ${index + 1}" to "Seed ${8 - index}",
                    me = me, now = now, contacts = contacts, knsNames = knsNames,
                    width = cardWidth, height = cardHeight, onOpenGame = onOpenGame,
                    modifier = Modifier.offset(x = columnX[0], y = round1Y[index]),
                )
            }
            repeat(2) { index ->
                ChessBracketCard(
                    game = tournament.games["2-$index"],
                    placeholder = "Winner of QF${index * 2 + 1}" to "Winner of QF${index * 2 + 2}",
                    me = me, now = now, contacts = contacts, knsNames = knsNames,
                    width = cardWidth, height = cardHeight, onOpenGame = onOpenGame,
                    modifier = Modifier.offset(x = columnX[1], y = semiY[index]),
                )
            }
            ChessBracketCard(
                game = tournament.games["3-0"],
                placeholder = "Winner of SF1" to "Winner of SF2",
                me = me, now = now, contacts = contacts, knsNames = knsNames,
                width = cardWidth, height = cardHeight, onOpenGame = onOpenGame,
                modifier = Modifier.offset(x = columnX[2], y = finalY),
            )
            // The champion, or the trophy waiting for one.
            val champion = tournament.champion
            Row(
                Modifier
                    .offset(x = columnX[3], y = finalY)
                    .width(cardWidth).height(cardHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFCC00).copy(alpha = if (champion == null) 0.06f else 0.14f))
                    .border(1.dp, Color(0xFFFFCC00).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (champion != null) {
                    ChessAvatar(champion, contacts, size = 28)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Champion", color = Color(0xFFFFCC00), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            chessName(champion, contacts, knsNames),
                            color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFCC00).copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("To be decided", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** One pair in the bracket: two players, or the seeds/winners the pair is still waiting on. */
@Composable
private fun ChessBracketCard(
    game: ChessTournamentGame?,
    placeholder: Pair<String, String>,
    me: String?,
    now: Long,
    contacts: Map<String, ContactEntity>,
    knsNames: Map<String, String>,
    width: Dp,
    height: Dp,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val mine = game != null && me != null && (game.white == me || game.black == me)
    Box(modifier.width(width).height(height)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(
                    if (mine) 1.6.dp else 1.dp,
                    if (mine) KaspaTeal else colors.textSecondary.copy(alpha = 0.25f),
                    RoundedCornerShape(12.dp),
                )
                .then(if (game == null) Modifier else Modifier.clickable { onOpenGame(game.id) }),
        ) {
            if (game == null) {
                ChessBracketPlaceholderRow(placeholder.first, height / 2f)
                HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.15f), modifier = Modifier.padding(horizontal = 8.dp))
                ChessBracketPlaceholderRow(placeholder.second, height / 2f)
            } else {
                ChessBracketPlayerRow(game.white, game, contacts, knsNames, height / 2f)
                HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.15f), modifier = Modifier.padding(horizontal = 8.dp))
                ChessBracketPlayerRow(game.black, game, contacts, knsNames, height / 2f)
            }
        }
        // LIVE, with the clock of whoever is to move.
        if (game != null && !game.isOver) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-8).dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.danger.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(4.dp))
                Text(
                    "LIVE ${clockText(game.remainingMs(game.sideToMove, now))}",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ChessBracketPlayerRow(
    address: String,
    game: ChessTournamentGame,
    contacts: Map<String, ContactEntity>,
    knsNames: Map<String, String>,
    height: Dp,
) {
    val colors = LocalAppColors.current
    val isWinner = game.winner == address
    val isLoser = game.isOver && game.winner != null && !isWinner
    val toMove = !game.isOver && game.address(game.sideToMove) == address
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp).alpha(if (isLoser) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChessAvatar(address, contacts, size = 20)
        Spacer(Modifier.width(6.dp))
        Text(
            chessName(address, contacts, knsNames),
            color = if (isLoser) colors.textSecondary else colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isWinner) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Won", tint = colors.success, modifier = Modifier.size(13.dp))
        } else if (toMove) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(KaspaTeal))
        }
    }
}

@Composable
private fun ChessBracketPlaceholderRow(text: String, height: Dp) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(1.dp, colors.textSecondary.copy(alpha = 0.4f), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One kind's leaderboard, under the Leaderboard tab's 1v1 | Tournaments bar: 1v1 is wins and losses in 1v1 games;
 * Tournaments is whole tournaments only - won (champion) and lost (knocked out); a game won
 * inside a tournament is not a score there (iOS e86868e, 7049e70). For this UTC month or all
 * time: the indexer's boards (ONLINE_CHESS.md §6) merged with what the phone has read; both count
 * from [com.kachat.app.util.ChessTournamentEngine.LEADERBOARD_FRESH_FROM_MS] (iOS 77d6038).
 */
@Composable
internal fun ChessLeaderboardRows(mode: ChessLobbyMode, navController: NavController? = null) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    val colors = LocalAppColors.current
    var period by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(com.kachat.app.util.ChessLeaderboardPeriod.MONTH)
    }
    val board by service.board(period).collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val me = service.myAddress
    val duel = mode == ChessLobbyMode.DUEL
    val serverDown by service.arenaServerDown.collectAsState()
    val rows = if (duel) {
        com.kachat.app.util.ChessTournamentEngine.duelLeaderboard(board)
    } else {
        com.kachat.app.util.ChessTournamentEngine.tournamentLeaderboard(board)
    }
    // On appear and on every period switch, like iOS's onAppear / onChange(of: period).
    LaunchedEffect(period) { service.refreshIndexerLeaderboards() }
    val emptyText = when (period) {
        com.kachat.app.util.ChessLeaderboardPeriod.MONTH -> stringResource(
            if (duel) R.string.chess_no_finished_duels_this_month else R.string.chess_no_finished_tournaments_this_month
        )
        com.kachat.app.util.ChessLeaderboardPeriod.ALL_TIME -> stringResource(
            if (duel) R.string.chess_no_finished_duels else R.string.chess_no_finished_tournaments
        )
    }
    val periodNote = when (period) {
        com.kachat.app.util.ChessLeaderboardPeriod.MONTH -> stringResource(R.string.chess_leaderboard_resets_monthly)
        com.kachat.app.util.ChessLeaderboardPeriod.ALL_TIME -> {
            // iOS `.dateTime.year().month(.wide).day()`: the cutoff's day, in the user's language.
            val locale = java.util.Locale.getDefault()
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMMd")
            val day = java.text.SimpleDateFormat(pattern, locale)
                .format(java.util.Date(com.kachat.app.util.ChessTournamentEngine.LEADERBOARD_FRESH_FROM_MS))
            stringResource(R.string.chess_leaderboard_counted_from, day)
        }
    }
    val periodLabel = stringResource(R.string.chess_leaderboard_period)
    // The list's first section starts well clear of the tabs (48 between them on the iPhone).
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 44.dp, bottom = 8.dp + LocalDockInset.current)) {
        if (serverDown) {
            item {
                ChessServerDownCard("Chess server unreachable. Showing what this phone has, which can be less than the full board.")
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            // iOS: a segmented Picker on its own row at the top, its note as the section footer.
            KaPostsSegmentedPicker(
                titles = listOf(
                    stringResource(R.string.chess_leaderboard_this_month),
                    stringResource(R.string.chess_leaderboard_all_time),
                ),
                selected = if (period == com.kachat.app.util.ChessLeaderboardPeriod.MONTH) 0 else 1,
                onSelect = {
                    period = if (it == 0) com.kachat.app.util.ChessLeaderboardPeriod.MONTH
                    else com.kachat.app.util.ChessLeaderboardPeriod.ALL_TIME
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = periodLabel },
            )
            Text(
                periodNote,
                color = colors.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 32.dp).padding(top = 6.dp, bottom = 10.dp),
            )
        }
        item {
            // What this board counts, in its own words (iOS 1d32335) - the rows' section header.
            ChessSectionHeader(if (duel) "Games won and lost - 1v1s and tournament games alike" else "Tournaments won and lost")
        }
        if (rows.isEmpty()) {
            item {
                ChessCard {
                    Text(
                        emptyText,
                        color = colors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        // iOS's insetGrouped section: one card, 26 round, its rows split by hairlines, your own
        // row tinted with the accent.
        itemsIndexed(rows, key = { _, row -> row.address }) { index, row ->
            val corner = 26.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) corner else 0.dp, topEnd = if (index == 0) corner else 0.dp,
                bottomStart = if (index == rows.lastIndex) corner else 0.dp, bottomEnd = if (index == rows.lastIndex) corner else 0.dp,
            )
            val isMe = row.address == me
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (isMe) KaspaTeal.copy(alpha = 0.12f).compositeOver(colors.surface) else colors.surface),
            ) {
                if (index > 0) ChessRowDivider(start = 34.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        // Tap anyone for their card (ChessPlayerScreen).
                        .then(
                            if (navController == null) Modifier else Modifier.clickable {
                                navController.navigate("chess_player/${row.address}")
                            }
                        )
                        // A 36 avatar in a row 66 tall (measured on the iPhone).
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // subheadline (15pt) throughout, the figures in tabular digits.
                    val digits = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                    Text(
                        "${index + 1}", color = colors.textSecondary, style = digits,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    ChessAvatar(row.address, contacts)
                    Spacer(Modifier.width(12.dp))
                    // The numbers keep their full width; a long name gives way, not the score -
                    // cut in the middle, as iOS's .truncationMode(.middle).
                    Text(
                        middleEllipsis(chessName(row.address, contacts, knsNames), 20),
                        color = colors.textPrimary, fontSize = 15.sp,
                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (duel) {
                        Text("${row.wins} W", color = colors.success, style = digits, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                        Text("${row.losses} L", color = colors.danger, style = digits, maxLines = 1)
                    } else {
                        // Whole tournaments only: won (champion) and lost (knocked out). A game won
                        // inside one is not a score (iOS 7049e70).
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${row.tournamentsWon}", color = Color(0xFFFFCC00), style = digits, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                        Text("${row.tournamentsLost} L", color = colors.danger, style = digits, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** [text] cut in the middle when longer than [max] characters ("castlek…achat"), the way iOS's
 *  .truncationMode(.middle) keeps both ends of a name in view. */
internal fun middleEllipsis(text: String, max: Int): String {
    if (text.length <= max) return text
    val head = (max - 1) / 2
    return text.take(head) + "…" + text.takeLast(max - 1 - head)
}

/**
 * Play > Chess: choose your game. 1v1 or a tournament, each its own screen, or the bots
 * ([ChessBotsScreen]). A room the player is already waiting or playing in is offered first.
 * Mirrors iOS's ChessHomeView.
 */
@Composable
fun ChessHomeScreen(navController: NavController) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    HoldArena(service)
    ChessErrorToast(service)
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    // Read on every tick: a seat that runs out changes only the time (iOS re-renders on `now`).
    val now by service.now.collectAsState()
    val mine = remember(all, now) { service.myActiveTournament(all) }

    fun open(mode: ChessLobbyMode) = navController.navigate("chess_mode/${mode.name}")

    Scaffold(
        // iOS: Color(.systemGroupedBackground) behind it all.
        containerColor = colors.background,
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = { ChessHomeBar(onBack = { navController.popBackStack() }, showConnectionDot = false, title = "Chess") },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = LocalDockInset.current + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // iOS: the rook and knight 64 tall, "Choose your game" in title2 bold and the
            // subheadline note, 6 apart; 24 above, 8 below.
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    com.kachat.app.ui.Screen.Chess.tabIconPainter(),
                    contentDescription = null, tint = KaspaTeal,
                    modifier = com.kachat.app.ui.Screen.Chess.tabIconModifier(64.dp),
                )
                Text("Choose your game", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
                Text(
                    "Online, five minutes a side and every move is a Kaspa transaction. Bot games stamp their result on Kaspa when they end.",
                    color = colors.textSecondary, fontSize = 15.sp, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            if (mine != null) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(KaspaTeal.copy(alpha = 0.12f))
                        .clickable { open(if (mine.isDuel) ChessLobbyMode.DUEL else ChessLobbyMode.TOURNAMENT) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // iOS: hourglass / play.fill in title3, in a 32-wide slot, 12 from the text.
                    Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (mine.status == ChessTournament.Status.OPEN) Icons.Default.HourglassEmpty else Icons.Default.PlayArrow,
                            contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (mine.status == ChessTournament.Status.OPEN) "You're waiting in ${mine.name}" else "You're playing in ${mine.name}",
                            color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp,
                        )
                        Text("Tap to go back to it", color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            ChessGameCard(
                icon = Icons.Default.People,
                title = "1v1",
                detail = "Play the next person who joins, or a friend by code. One game, winner takes the leaderboard point.",
            ) { open(ChessLobbyMode.DUEL) }
            ChessGameCard(
                icon = Icons.Default.EmojiEvents,
                title = "Tournament",
                detail = "Eight players, single elimination: quarterfinals, semifinals, final. Public rooms fill as players arrive; private ones by code.",
            ) { open(ChessLobbyMode.TOURNAMENT) }
            ChessGameCard(
                icon = Icons.Default.Memory,
                title = "Bots",
                detail = "Five bots, level 1 to 5, from Kaspa Silver to Yonatan Sompolinsky. Beat one to unlock the next. The result goes on Kaspa.",
            ) { navController.navigate("chess_bots") }
        }
    }
}

/**
 * The bar of every chess page: the connection dot (or a pushed page's back button) and the balance
 * in the centre. A menu's [title] is big and fixed under it, above the content that scrolls, with a
 * divider (iOS `fixedScreenTitle`, the same as KaPosts'). A game board has no room for that, so its
 * [centerTitle] goes over the balance in the bar (iOS `ScreenTitleToolbarLabel`).
 */
@Composable
internal fun ChessHomeBar(
    onBack: (() -> Unit)?,
    /** iOS puts the connection dot on the dock's root screens only; pushed pages have none. */
    showConnectionDot: Boolean = true,
    title: String? = null,
    centerTitle: String? = null,
    /** A tab's first screen: [AccountBackupBanner] above its title, as in KaChat (iOS 8d5f925). */
    showsBackupBanner: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null && !LocalInHubSection.current) {
                ToolbarGlassGroup {
                    ToolbarIconButton(Icons.Filled.ArrowBackIosNew, "Back", onBack, iconSize = 20.dp, tint = LocalAppColors.current.textPrimary)
                }
            }
            if (showConnectionDot) ConnectionDotButton(onClick = { ConnectionStatusOverlayState.open() })
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (centerTitle != null) {
                    Text(centerTitle, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BalanceTopBarLabel()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
    if (showsBackupBanner) LocalBackupBanner.current()
    if (title != null) ChessFixedTitle(title)
    }
}

/** The backup strip a tab's first screen shows above its title (ChessMainShell provides it). */
val LocalBackupBanner = androidx.compose.runtime.staticCompositionLocalOf<@Composable () -> Unit> { {} }

/** iOS `.largeTitle.weight(.bold)`, one line, shrinking to 0.6 of it (minimumScaleFactor), then a
 *  divider. */
@Composable
private fun ChessFixedTitle(title: String) {
    val colors = LocalAppColors.current
    val maxSize = 34.sp
    val minSize = maxSize * 0.6f
    var fontSize by remember(title) { mutableStateOf(maxSize) }
    Text(
        title,
        color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = 41.sp,
        maxLines = 1, softWrap = false,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minSize.value) {
                val next = fontSize * 0.94f
                fontSize = if (next.value < minSize.value) minSize else next
            }
        },
    )
    HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
}

/** iOS's gameCard: a 60 accent tile (radius 16) with the 28pt symbol in white, the title in
 *  title3 bold over the subheadline detail, a subheadline chevron; padding 16, radius 20. */
@Composable
private fun ChessGameCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    ChessGameCard(title, detail, onClick) {
        // A Material glyph is inset in its box; 38 draws it the size of a 28pt symbol.
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
    }
}

/** The same card with anything in the tile: Play's Chess card has the rook and knight. */
@Composable
internal fun ChessGameCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    tile: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .clickable {
                haptic(com.kachat.app.util.IosHaptic.SELECTION)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(KaspaTeal),
            contentAlignment = Alignment.Center,
        ) { tile() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp)
            Text(detail, color = colors.textSecondary, fontSize = 15.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(16.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
    }
}

/**
 * The waiting room: covers the app from the moment a player joins a public 1v1 or tournament
 * room - or creates or joins a private one - until it fills, their seat runs out, or they
 * leave. Nothing else is reachable meanwhile: searching for players is the one thing happening.
 * Their avatar, a question mark for every empty seat, the time the seat is held for, the
 * private code to share, and Leave behind a warning. Mirrors iOS's ChessWaitingRoomView.
 */
@Composable
private fun ChessWaitingRoom(
    tournamentId: String,
    /** The room filled and the game exists: the caller opens it and closes this. */
    onStarted: (String) -> Unit,
    /** Left, or the seat ran out: the caller closes this (with a note when it ran out). */
    onFinished: (seatExpired: Boolean) -> Unit,
) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    HoldArena(service)
    ChessErrorToast(service)
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val now by service.now.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val tournament = all[tournamentId]
    val me = service.myAddress
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val chessHaptic = com.kachat.app.util.rememberHaptics()
    val serverDown by service.arenaServerDown.collectAsState()
    var showLeaveWarning by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var handedOff by remember { mutableStateOf(false) }
    // Whether this player has actually been seen in the room. A join takes a few seconds to land,
    // and a room someone else is already waiting in EXISTS before then - so "not seated" on its
    // own meant "your seat expired" the instant the room opened, which closed the room with that
    // notice and left the player outside the game that followed.
    var everSeated by remember { mutableStateOf(false) }
    val iAmSeated = me != null && tournament?.isSeated(me, now) == true
    LaunchedEffect(iAmSeated) { if (iAmSeated) everSeated = true }
    // Leave went out: the room closes when the chain shows the seat given back - or opens the
    // board, if the room filled first (iOS 8d5f925).
    var leftByChoice by remember { mutableStateOf(false) }
    // A join that never shows up (ignored, or never mined) does not hold this screen forever.
    val appearedAt = remember { service.now.value }

    // The room has filled and the ten seconds of "Match found" are still running. Chain time,
    // so it ends at the same instant on every phone and both players reach the board together.
    // Only for a room this player is in: one that filled without them is no match (iOS 4922804).
    val matchFound = tournament != null && tournament.status == ChessTournament.Status.LIVE &&
        me != null && me in tournament.players &&
        now < (tournament.startedAt ?: 0L) + com.kachat.app.util.ChessTournamentCodec.MATCH_FOUND_DELAY_MS
    val matchFoundSecondsLeft = if (!matchFound) 0 else {
        val left = (tournament?.startedAt ?: 0L) + com.kachat.app.util.ChessTournamentCodec.MATCH_FOUND_DELAY_MS - now
        maxOf(0L, (left + 999) / 1000).toInt()
    }

    // Filled, or the seat ran out: hand the screen over. Runs on every tick.
    LaunchedEffect(tournament?.status, now, handedOff, everSeated, matchFound) {
        if (handedOff || tournament == null || me == null) return@LaunchedEffect
        val started = tournament.status == ChessTournament.Status.LIVE ||
            tournament.status == ChessTournament.Status.FINISHED
        when {
            // The room started and this player is in it: their game is waiting - after the
            // match-found countdown, which every phone is watching end together.
            started && me in tournament.players -> {
                if (matchFound) return@LaunchedEffect
                handedOff = true
                onStarted(tournament.id)
            }
            // Filled without us: our join lost the race for the last seat. Not a match - the room
            // closes (with no notice about a seat that was never taken), and the service joins
            // the next room by itself (iOS 4922804).
            started -> {
                handedOff = true
                onFinished(false)
            }
            tournament.status == ChessTournament.Status.CANCELLED -> {
                handedOff = true
                onFinished(false)
            }
            iAmSeated -> Unit
            // Seated once and no longer: the five minutes ran out, or the seat was given back.
            // Never seated after half a minute: the join never landed.
            everSeated || now - appearedAt > SEAT_WAIT_MS -> {
                handedOff = true
                onFinished(everSeated && !leftByChoice)
            }
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false,
        ),
    ) {
        // One Box so the leave sheet below draws OVER the room rather than under it.
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(colors.background).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            val duel = tournament?.isDuel == true
            Text(
                when {
                    matchFound && duel -> "Match found!"
                    matchFound -> "Tournament full!"
                    duel -> "Looking for an opponent"
                    else -> "Waiting for players"
                },
                color = colors.textPrimary,
                fontWeight = if (matchFound) FontWeight.Black else FontWeight.Bold,
                fontSize = 22.sp,
            )
            // You first, then whoever else is here, then a question mark for each empty seat.
            val capacity = tournament?.capacity ?: 2
            val seatedNow = tournament?.seatedPlayers(now).orEmpty()
            val shown = (listOfNotNull(me).filter { it in seatedNow } + seatedNow.filter { it != me })
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(if (capacity == 2) 2 else 4),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(horizontal = 32.dp).heightIn(max = 260.dp),
            ) {
                items(shown.size) { index ->
                    val address = shown[index]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChessAvatar(address, contacts, size = 64)
                        Spacer(Modifier.height(8.dp))
                        Text(chessName(address, contacts, knsNames), color = colors.textPrimary, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items(maxOf(0, capacity - shown.size)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { Text("?", color = colors.textSecondary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = IosRoundedFontFamily) }
                        Spacer(Modifier.height(8.dp))
                        Text("Waiting", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            if (matchFound) {
                // The same ten seconds of chain time on every phone, then every board opens.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$matchFoundSecondsLeft",
                        color = colors.textPrimary,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IosRoundedFontFamily,
                    )
                    Text(
                        if (duel) "Taking both of you to the board" else "Taking everyone to their boards",
                        color = colors.textSecondary, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
                // Nothing else belongs on the screen now: no seat clock, no code to share, no
                // Leave - the game exists on chain.
                return@Column
            }
            // The seat exists only once the join lands, which is a few seconds - until then the
            // clock has nothing to count, and a hard 0:00 would read as "already out".
            val expiry = me?.let { tournament?.seatExpiry(it) }
            val left = expiry?.let { maxOf(0L, (it - now) / 1000) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    left?.let { "%d:%02d".format(it / 60, it % 60) } ?: "--:--",
                    color = if (left != null && left < 30) colors.danger else colors.textPrimary,
                    fontSize = 44.sp, fontWeight = FontWeight.SemiBold, fontFamily = IosRoundedFontFamily,
                )
                Text(
                    when {
                        tournament != null && tournament.isFull && !iAmSeated ->
                            "That room filled first - finding you the next one."
                        left == null -> "Taking your seat - this is one transaction, so it takes a few seconds."
                        else -> "Your seat is held this long. If no one joins in time, you leave the queue."
                    },
                    color = colors.textSecondary, fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            if (serverDown) {
                ChessServerDownNote(
                    Modifier.padding(horizontal = 32.dp),
                    "Chess server unreachable. Watching the network for players, so someone who joined while the app was away may not show.",
                )
            }
            if (tournament != null && !tournament.isPublic) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Share this code", color = colors.textSecondary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tournament.id, color = colors.textPrimary, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        IconButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(tournament.id))
                            chessHaptic(com.kachat.app.util.IosHaptic.SUCCESS) // iOS: a haptic, no toast
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KaspaTeal, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = {
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Play me at chess in KaPlay: open Play > ${if (tournament.isDuel) "1v1" else "Tournaments"} > Join with a code, and enter ${tournament.id}",
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(share, null))
                        }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = KaspaTeal, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            Text(
                if (duel) "You're paired with the next person who joins. The game starts by itself."
                else "The tournament starts by itself when all eight seats are taken.",
                color = colors.textSecondary, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Box(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(colors.danger.copy(alpha = 0.12f))
                    .clickable(enabled = !isLeaving && !leftByChoice) { showLeaveWarning = true }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isLeaving || leftByChoice) "Leaving…" else "Leave", color = colors.danger, fontWeight = FontWeight.SemiBold)
            }
        }

        // Drawn in the dialog's OWN window, deliberately: a ModalBottomSheet (ActionSheetContainer)
        // opened from inside a Dialog goes to a window of its own behind this one and is never
        // seen - so Leave looked like it did nothing at all. Same shape as the Resign sheet.
        if (showLeaveWarning) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { showLeaveWarning = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(com.kachat.app.ui.theme.IosSheetShape)
                        .background(colors.background)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        ) {}
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = colors.danger, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Leave the queue?", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Leaving means you will no longer be searching for another player. Leaving is one transaction; you can join again any time.",
                        color = colors.textSecondary, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.danger)
                            .clickable {
                                val room = tournament
                                showLeaveWarning = false
                                if (room != null && !isLeaving && !leftByChoice) {
                                    isLeaving = true
                                    vm.launch {
                                        // Not closed here: a leave that failed (the error shows)
                                        // keeps the player waiting, and one beaten by the last
                                        // join is a game - the check above closes the room, or
                                        // opens the board, by what the chain says.
                                        if (service.leave(room)) leftByChoice = true
                                        isLeaving = false
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (isLeaving) "Leaving…" else "Leave", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant)
                            .clickable { showLeaveWarning = false }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Keep waiting", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        }
    }
}

// MARK: - One tournament

/**
 * The bracket and the players. A player is taken straight to their game the
 * moment it exists (round 1 at the eighth join; later rounds when the pair's other game ends),
 * and can tap any game to watch it meanwhile.
 */
@Composable
fun ChessTournamentScreen(tournamentId: String, navController: NavController) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    HoldArena(service)
    ChessErrorToast(service)
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val now by service.now.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val tournament = all[tournamentId]
    val me = service.myAddress
    // Saveable: backing out of the game recomposes this screen, and a plain remember would
    // forget the game was opened and push it straight back.
    var autoOpenedGameId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var isJoining by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val chessHaptic = com.kachat.app.util.rememberHaptics()
    val context = LocalContext.current

    fun openGame(gameId: String) = navController.navigate("chess_tournament_game/$tournamentId/$gameId")

    // The player's next game, waiting on its cool-down: it opens at its start block time plus
    // MATCH_FOUND_DELAY_MS - ten seconds on the bracket, the same instant for both players - and
    // immediately for a player who arrives after that (iOS 8e113c4).
    val myNextGame = me?.let { tournament?.currentGame(it) }?.takeIf { !it.isOver }
    val nextGameCountdownMs = myNextGame?.let {
        (it.startedAt + com.kachat.app.util.ChessTournamentCodec.MATCH_FOUND_DELAY_MS - now).takeIf { left -> left > 0 }
    }

    // The player's game came into being: open it once its cool-down has run out.
    LaunchedEffect(tournament?.games?.size, nextGameCountdownMs == null) {
        val game = myNextGame ?: return@LaunchedEffect
        if (autoOpenedGameId == game.id || nextGameCountdownMs != null) return@LaunchedEffect
        autoOpenedGameId = game.id
        openGame(game.id)
    }

    Scaffold(
        containerColor = colors.background,
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = { ChessHomeBar(onBack = { navController.popBackStack() }, showConnectionDot = false, title = tournament?.name ?: "Tournament") },
    ) { padding ->
        if (tournament == null) {
            // Centred in the space above the dock.
            Column(Modifier.fillMaxSize().padding(padding).padding(bottom = LocalDockInset.current), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                Spacer(Modifier.height(12.dp))
                Text("Loading the tournament from the arena…", color = colors.textSecondary, fontSize = 14.sp)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = LocalDockInset.current + 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ChessCard {
                Column(Modifier.padding(16.dp)) {
                    when (tournament.status) {
                        ChessTournament.Status.OPEN -> {
                            Text(
                                if (tournament.isDuel) "Waiting for your opponent. The game starts by itself when they join."
                                else "Waiting for ${tournament.seatsLeft} more player${if (tournament.seatsLeft == 1) "" else "s"}. It starts by itself when the eighth joins.",
                                color = colors.textPrimary, fontSize = 14.sp,
                            )
                            if (me != null && !tournament.isSeated(me, now)) {
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (isJoining) return@Button
                                        isJoining = true
                                        vm.launch { service.join(tournament); isJoining = false }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (isJoining) "Joining…" else service.joinLabel(tournament.id), fontWeight = FontWeight.SemiBold) }
                            }
                            if (tournament.creator == me && !tournament.isPublic) {
                                TextButton(onClick = { showCancelConfirm = true }) { Text("Cancel tournament", color = colors.danger) }
                            }
                            if (!tournament.isPublic) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Code: ${tournament.id}", color = colors.textPrimary, fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(tournament.id))
                                        chessHaptic(com.kachat.app.util.IosHaptic.SUCCESS) // iOS: a haptic, no toast
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy", color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(onClick = {
                                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                "Join my chess tournament in KaPlay: open Play > ${if (tournament.isDuel) "1v1" else "Tournaments"} > Join with a code, and enter ${tournament.id}",
                                            )
                                        }
                                        context.startActivity(android.content.Intent.createChooser(share, null))
                                    }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = KaspaTeal) }
                                }
                            }
                        }
                        ChessTournament.Status.LIVE -> {
                            val game = me?.let { tournament.currentGame(it) }
                            when {
                                game == null -> Text("In play. Tap any game to watch it live.", color = colors.textPrimary, fontSize = 14.sp)
                                nextGameCountdownMs != null && myNextGame != null -> {
                                    // Cool-down on the bracket before the next round: who it is,
                                    // and when (iOS 8e113c4).
                                    val opponentColor = myNextGame.color(me)?.opposite ?: ChessColor.BLACK
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Next: ${roundName(myNextGame.round)} vs ${chessName(myNextGame.address(opponentColor), contacts, knsNames)}",
                                                color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text("Your game starts in", color = colors.textSecondary, fontSize = 12.sp)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "${(nextGameCountdownMs + 999) / 1000}",
                                            color = KaspaTeal, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                            fontFamily = IosRoundedFontFamily,
                                        )
                                    }
                                }
                                game.isOver && game.winner == me -> Text(
                                    "You won ${if (tournament.isDuel) "the game" else roundName(game.round)}. Waiting for your next opponent - watch the other game meanwhile.",
                                    color = colors.textPrimary, fontSize = 14.sp,
                                )
                                game.isOver -> Text("You are out of this tournament. Watch the rest of the bracket.", color = colors.textPrimary, fontSize = 14.sp)
                                else -> Row(Modifier.clickable { openGame(game.id) }, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = KaspaTeal)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Go to your game", color = KaspaTeal, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        ChessTournament.Status.FINISHED -> tournament.champion?.let { champion ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFCC00))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (tournament.isDuel) "${chessName(champion, contacts, knsNames)} won" else "${chessName(champion, contacts, knsNames)} won the tournament",
                                    color = Color(0xFFFFCC00), fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        ChessTournament.Status.CANCELLED -> Text("Cancelled by the creator.", color = colors.textSecondary, fontSize = 14.sp)
                    }
                }
            }

            if (tournament.status == ChessTournament.Status.OPEN) {
                val seated = tournament.seatedPlayers(now)
                ChessSectionHeader("Players (${seated.size} of ${tournament.capacity})")
                ChessCard {
                    seated.forEachIndexed { index, address ->
                        if (index > 0) ChessRowDivider(start = 64.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChessAvatar(address, contacts)
                            Spacer(Modifier.width(12.dp))
                            Text(chessName(address, contacts, knsNames), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("Seed ${index + 1}", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    repeat(maxOf(0, tournament.capacity - seated.size)) { index ->
                        if (index > 0 || seated.isNotEmpty()) ChessRowDivider(start = 64.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).border(1.dp, colors.textSecondary.copy(alpha = 0.4f), CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text("Open seat", color = colors.textSecondary)
                        }
                    }
                }
            } else if (!tournament.isDuel) {
                // The bracket, not a list of rounds: where the tournament stands, all at once
                // (iOS 7049e70).
                ChessSectionHeader("Bracket · tap a game to watch")
                ChessBracket(
                    tournament = tournament,
                    me = me,
                    now = now,
                    contacts = contacts,
                    knsNames = knsNames,
                    onOpenGame = ::openGame,
                )
            } else {
                for (round in 1..tournament.rounds) {
                    val games = tournament.gamesInRound(round)
                    if (games.isEmpty()) continue
                    ChessSectionHeader(if (tournament.isDuel) "Game" else when (round) { 3 -> "Final"; 2 -> "Semifinals"; else -> "Round 1" })
                    ChessCard {
                        ChessRows(games, start = 16.dp) { game ->
                            Row(
                                Modifier.fillMaxWidth().clickable { openGame(game.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(chessName(game.white, contacts, knsNames), color = colors.textPrimary, fontSize = 14.sp,
                                            fontWeight = if (game.decidedWinner(now) == game.white) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                                        Text("  vs  ", color = colors.textSecondary, fontSize = 14.sp)
                                        Text(chessName(game.black, contacts, knsNames), color = colors.textPrimary, fontSize = 14.sp,
                                            fontWeight = if (game.decidedWinner(now) == game.black) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                                    }
                                    Text(gameStatus(game, now, contacts, knsNames), color = colors.textSecondary, fontSize = 12.sp)
                                }
                                if (!game.isDecided(now)) {
                                    Text(clockText(game.remainingMs(game.sideToMove, now)), color = colors.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

        }
    }

    if (showCancelConfirm && tournament != null) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel this tournament?") },
            text = { Text("Everyone who joined is released. This is one transaction.") },
            confirmButton = {
                TextButton(onClick = { showCancelConfirm = false; vm.launch { service.cancel(tournament) } }) {
                    Text("Cancel tournament", color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("Keep it") } },
        )
    }
}

private fun roundName(round: Int) = when (round) { 3 -> "the final"; 2 -> "the semifinal"; else -> "round 1" }

private fun gameStatus(game: ChessTournamentGame, now: Long, contacts: Map<String, ContactEntity>, knsNames: Map<String, String>): String {
    val winner = game.winner
    val outcome = game.outcome
    if (winner == null || outcome == null) {
        // Out of clock, claim not posted: the same result, said the same way (iOS a94f0e8).
        game.decidedWinner(now)?.let { return "${chessName(it, contacts, knsNames)} won on time" }
        return "Move ${game.moves.size / 2 + 1} · ${if (game.sideToMove == ChessColor.WHITE) "white" else "black"} to move"
    }
    val who = chessName(winner, contacts, knsNames)
    return when (outcome) {
        ChessTournamentOutcome.Checkmate -> "$who won by checkmate"
        ChessTournamentOutcome.Resignation -> "$who won by resignation"
        ChessTournamentOutcome.Timeout -> "$who won on time"
        is ChessTournamentOutcome.DrawTiebreak -> "$who won on clock after a draw (${outcome.reason})"
    }
}

/**
 * The moment a game ends, over the board: a burst for the winner, a quiet card for the loser, a
 * plain one for anyone watching. It stays about two seconds and the result screen follows.
 * Mirrors iOS's ChessGameEndOverlay.
 */
@Composable
private fun ChessGameEndOverlay(winnerName: String, outcome: ChessTournamentOutcome, iWon: Boolean?) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0.4f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f),
        label = "endScale",
    )
    // Sparks flying out from the middle - a real burst, not a static badge.
    val burst by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1_100, delayMillis = 150),
        label = "endBurst",
    )
    val headline = when (iWon) {
        true -> "You won!"
        false -> "You lost"
        null -> "$winnerName won"
    }
    val detail = when (outcome) {
        ChessTournamentOutcome.Checkmate -> "Checkmate"
        ChessTournamentOutcome.Resignation -> "By resignation"
        ChessTournamentOutcome.Timeout -> "On time"
        is ChessTournamentOutcome.DrawTiebreak -> "Draw by ${outcome.reason} - won on clock"
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        if (iWon == true) {
            val sparks = listOf("✨", "🎉", "⭐", "🎊", "💫")
            repeat(18) { i ->
                val angle = i / 18.0 * 2 * Math.PI
                Text(
                    sparks[i % sparks.size],
                    fontSize = if (i % 3 == 0) 26.sp else 18.sp,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = (kotlin.math.cos(angle) * 150f * burst).toFloat()
                            translationY = (kotlin.math.sin(angle) * 150f * burst).toFloat()
                            alpha = 1f - burst
                        },
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (appeared) 1f else 0f },
        ) {
            Icon(
                when (iWon) {
                    true -> Icons.Default.EmojiEvents
                    false -> Icons.Default.Flag
                    null -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = if (iWon == true) Color(0xFFFFCC00) else Color.White,
                modifier = Modifier.size(54.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(headline, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IosRoundedFontFamily)
            Text(detail, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * After a game: the player's record on the board this game counts on (1v1 or Tournaments), with
 * the change this game made counted up from [before], and the top of that board around them.
 * Mirrors iOS's ChessGameResultView.
 */
@Composable
private fun ChessGameResultScreen(
    tournamentId: String,
    gameId: String,
    /** The player's record before this game landed - what the screen counts up from. */
    before: com.kachat.app.util.ChessLeaderboardRow?,
    onDone: () -> Unit,
) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    HoldArena(service)
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val leaderboard by service.leaderboard.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val me = service.myAddress
    val tournament = all[tournamentId]
    val game = tournament?.games?.get(gameId)
    val isDuel = tournament?.isDuel ?: true
    val board = if (isDuel) {
        com.kachat.app.util.ChessTournamentEngine.duelLeaderboard(leaderboard)
    } else {
        com.kachat.app.util.ChessTournamentEngine.tournamentLeaderboard(leaderboard)
    }
    val mine = board.firstOrNull { it.address == me }
    val rank = board.indexOfFirst { it.address == me }.takeIf { it >= 0 }?.plus(1)
    val iWon = game?.winner != null && game.winner == me
    // 1v1: games won and lost. Tournaments: whole tournaments won (champion) and lost (knocked
    // out) - a game won inside a tournament is not a score, and this screen is never shown for
    // one (the player goes to the bracket instead).
    fun wins(row: com.kachat.app.util.ChessLeaderboardRow?) = if (isDuel) row?.wins ?: 0 else row?.tournamentsWon ?: 0
    fun losses(row: com.kachat.app.util.ChessLeaderboardRow?) = if (isDuel) row?.losses ?: 0 else row?.tournamentsLost ?: 0
    // The figures start where they stood and settle on the new ones a beat later.
    var revealed by remember { mutableStateOf(false) }
    /** A tapped leaderboard row, for that player's card (ChessPlayerScreen), over this screen. */
    var profileAddress by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); revealed = true }

    Dialog(
        onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                // iOS's full-screen cover: an inline title, centred, and Done as the confirmation
                // action - no balance, no connection dot.
                Box(
                    Modifier.fillMaxWidth().background(colors.background).statusBarsPadding()
                        .height(54.dp).padding(horizontal = 16.dp),
                ) {
                    Text(
                        if (isDuel) "1v1" else "Tournament",
                        color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, modifier = Modifier.align(Alignment.Center),
                    )
                    SheetBarButton("Done", onClick = onDone, emphasized = true, modifier = Modifier.align(Alignment.CenterEnd))
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    if (iWon) Icons.Default.EmojiEvents else Icons.Default.Flag,
                    contentDescription = null,
                    tint = if (iWon) Color(0xFFFFCC00) else colors.textSecondary,
                    modifier = Modifier.size(44.dp),
                )
                Text(if (iWon) "Victory" else "Defeat", color = colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Black)
                if (game != null) {
                    Text(gameStatus(game, service.now.value, contacts, knsNames), color = colors.textSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(20.dp))
                // The record: before -> after, on the board this game counts on.
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(colors.surface).padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (isDuel) "YOUR 1V1 RECORD" else "YOUR TOURNAMENT RECORD",
                        color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        val shownWins = if (revealed) wins(mine) else wins(before)
                        val shownLosses = if (revealed) losses(mine) else losses(before)
                        ChessResultStat("Wins", shownWins, if (iWon) 1 else 0, colors.success, revealed)
                        val total = shownWins + shownLosses
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (total == 0) "-" else "%.0f%%".format(shownWins * 100.0 / total),
                                color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                                fontFamily = IosRoundedFontFamily,
                            )
                            Text("Win rate", color = colors.textSecondary, fontSize = 12.sp)
                        }
                        ChessResultStat("Losses", shownLosses, if (iWon) 0 else 1, colors.danger, revealed)
                    }
                    if (rank != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "#$rank on the ${if (isDuel) "1v1" else "tournament"} board",
                            color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        )
                    }
                }
                if (tournament != null && !tournament.isDuel) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when {
                            iWon && tournament.status == ChessTournament.Status.FINISHED -> "You won the tournament."
                            iWon -> "You go through to the next round. Your next game opens by itself when your opponent is decided."
                            else -> "You are out of this tournament. You can watch the rest of the bracket."
                        },
                        color = colors.textSecondary, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    if (isDuel) "1V1 LEADERBOARD" else "TOURNAMENT LEADERBOARD",
                    color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start).padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                board.take(5).forEachIndexed { index, row ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (row.address == me) KaspaTeal.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { profileAddress = row.address }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}", color = colors.textSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
                        ChessAvatar(row.address, contacts, size = 32)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            chessName(row.address, contacts, knsNames), color = colors.textPrimary,
                            fontWeight = if (row.address == me) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (isDuel) {
                            Text("${row.wins} W  ${row.losses} L", color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${row.tournamentsWon}", color = Color(0xFFFFCC00), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(8.dp))
                            Text("${row.tournamentsLost} L", color = colors.danger,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        profileAddress?.let { address ->
            ChessPlayerScreen(address = address, onDone = { profileAddress = null })
        }
        }
    }
}

@Composable
private fun ChessResultStat(label: String, value: Int, delta: Int, color: Color, revealed: Boolean) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = color, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = IosRoundedFontFamily)
        Text(label, color = colors.textSecondary, fontSize = 12.sp)
        Text(
            if (delta > 0) "+$delta" else " ",
            color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.graphicsLayer { alpha = if (revealed && delta > 0) 1f else 0f },
        )
    }
}

// MARK: - One game

/**
 * The board with both clocks and the controls. Players tap to move; everyone else watches the
 * same board live.
 */
@Composable
fun ChessTournamentGameScreen(tournamentId: String, gameId: String, navController: NavController) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    HoldArena(service)
    ChessErrorToast(service)
    val colors = LocalAppColors.current
    val all by service.tournaments.collectAsState()
    val now by service.now.collectAsState()
    val pending by service.pendingMoveGames.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val tournament = all[tournamentId]
    val game = tournament?.games?.get(gameId)
    val me = service.myAddress
    val myColor = me?.let { game?.color(it) }
    val isPending = "$tournamentId|$gameId" in pending
    val serverDown by service.arenaServerDown.collectAsState()
    // Not once the clock has decided the game: a move after the flag is void on chain (iOS 4922804).
    val isMyTurn = game != null && myColor != null && !game.isDecided(now) && game.sideToMove == myColor && !isPending
    // The board is drawn from the viewer's side: black players see black at the bottom.
    val flipped = myColor == ChessColor.BLACK
    var selectedSquare by remember { mutableStateOf<ChessSquare?>(null) }
    var pendingPromotion by remember { mutableStateOf<ChessMove?>(null) }
    var showResignConfirm by remember { mutableStateOf(false) }
    // End-of-game flow: the burst over the board, then the result screen (players only).
    var showEndOverlay by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var endHandledForGame by remember { mutableStateOf<String?>(null) }
    // The player's record as it stood while the game was on - the result screen counts up from it.
    var recordBeforeEnd by remember { mutableStateOf<com.kachat.app.util.ChessLeaderboardRow?>(null) }
    val leaderboard by service.leaderboard.collectAsState()

    // Kept fresh while the game is on (the arena may still be loading when the screen opens);
    // once the game is over it is left alone.
    LaunchedEffect(leaderboard, game?.isOver) {
        val address = me ?: return@LaunchedEffect
        if (game?.isOver != false) return@LaunchedEffect
        recordBeforeEnd = leaderboard.firstOrNull { it.address == address }
            ?: com.kachat.app.util.ChessLeaderboardRow(address)
    }

    // The game just ended: the burst over the board for a couple of seconds, then - for the two
    // players, not for someone watching - the result screen (iOS 759a2d3).
    LaunchedEffect(game?.isOver, game?.id) {
        val finished = game ?: return@LaunchedEffect
        if (!finished.isOver || finished.winner == null || endHandledForGame == finished.id) return@LaunchedEffect
        endHandledForGame = finished.id
        // Opened on a game that was already over (watching a finished bracket): nothing to show.
        if ((finished.endedAt ?: 0L) <= service.now.value - 60_000) return@LaunchedEffect
        showEndOverlay = true
        kotlinx.coroutines.delay(2_400)
        showEndOverlay = false
        if (myColor == null) return@LaunchedEffect
        val room = tournament
        if (room != null && !room.isDuel && finished.winner == me &&
            room.status != ChessTournament.Status.FINISHED
        ) {
            // Advanced: nothing to score yet - only the whole tournament counts - so to the
            // bracket, where the other games can be watched and the next one opens by itself
            // when the opponent is decided (iOS 7049e70).
            navController.navigate("chess_tournament/${room.id}") {
                popUpTo("chess_tournament/${room.id}") { inclusive = true }
            }
        } else {
            showResult = true
        }
    }

    // Watching another game while waiting for the next round: the moment this player's own next
    // game exists, the bracket takes over and opens it.
    LaunchedEffect(tournament?.games?.size, myColor) {
        val room = tournament ?: return@LaunchedEffect
        val address = me ?: return@LaunchedEffect
        if (room.isDuel || myColor != null) return@LaunchedEffect
        val mine = room.currentGame(address) ?: return@LaunchedEffect
        if (mine.isOver || mine.id == gameId) return@LaunchedEffect
        navController.navigate("chess_tournament/${room.id}") {
            popUpTo("chess_tournament/${room.id}") { inclusive = true }
        }
    }

    val title = game?.let { if (tournament?.isDuel == true) "1v1" else when (it.round) { 3 -> "Final"; 2 -> "Semifinal"; else -> "Round 1" } } ?: "Game"
    // A player in a game that is still on: no wandering off - back asks first, because leaving
    // resigns. A spectator, or a game that is over, is free to leave (iOS 4152bf0) - and so is a
    // game decided on the clock with the claim still to come: its winner must not have only
    // Resign, which would hand the opponent the win (iOS 8d5f925).
    val lockedIn = myColor != null && game != null && !game.isDecided(now)
    // Won on time, the claim not on chain yet (the app posts it by itself; this is the button for
    // when it has not taken).
    val canClaimWin = game != null && me != null && !game.isOver && game.decidedWinner(now) == me
    // A claim going out: the button waits for it, so taps never send it twice (iOS 4922804).
    val claimsInFlight by service.claimsInFlight.collectAsState()
    val isClaiming = tournament != null && game != null && service.isClaiming(tournament, game, claimsInFlight)
    var showLeaveWarning by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = lockedIn) { showLeaveWarning = true }
    // ...and the dock goes while they are (ChessMainShell); a spectator keeps it.
    ChessGameDock.Hold(lockedIn)
    Scaffold(
        containerColor = colors.background,
        // Runs on under the dock (LocalDockInset), as iOS's content runs on under its tab bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            // iOS: the inline title ([title], for TalkBack) gives way to the balance; back and
            // Resign while the player is locked in.
            ChessHomeBar(
                onBack = { if (lockedIn) showLeaveWarning = true else navController.popBackStack() },
                showConnectionDot = false,
                centerTitle = title,
                modifier = Modifier.semantics { paneTitle = title },
            ) {
                if (lockedIn) SheetBarButton("Resign", onClick = { showResignConfirm = true }, color = colors.danger)
                if (canClaimWin) {
                    SheetBarButton(if (isClaiming) "Claiming…" else "Claim win", enabled = !isClaiming, onClick = {
                        if (tournament != null && game != null) service.claimWin(tournament, game)
                    })
                }
            }
        },
    ) { padding ->
        if (tournament == null || game == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal) }
            return@Scaffold
        }
        val legalDestinations: List<ChessSquare> = selectedSquare?.takeIf { isMyTurn }?.let { from ->
            ChessEngine.legalMoves(from, game.board).map { it.to }
        } ?: emptyList()

        fun tap(sq: ChessSquare) {
            if (!isMyTurn || myColor == null) return
            val from = selectedSquare
            if (from != null) {
                if (sq in legalDestinations) {
                    val move = ChessMove(from, sq, null)
                    val piece = game.board.piece(from)
                    selectedSquare = null
                    if (piece?.type == ChessPieceType.PAWN && sq.rank == (if (myColor == ChessColor.WHITE) 7 else 0)) {
                        pendingPromotion = move
                    } else {
                        vm.launch { service.play(move, tournament, game) }
                    }
                    return
                }
                selectedSquare = if (game.board.piece(sq)?.color == myColor) sq else null
                return
            }
            if (game.board.piece(sq)?.color == myColor) selectedSquare = sq
        }

        // Header with the opponent and the status, the other side's clock chip, the board as large
        // as the width allows, our clock chip.
        Column(Modifier.fillMaxSize().padding(padding).padding(top = 8.dp)) {
            if (serverDown) {
                ChessServerDownNote(
                    Modifier.padding(horizontal = 16.dp),
                    "Chess server unreachable. Moves are coming from the network, and the board may be a moment behind.",
                )
                Spacer(Modifier.height(8.dp))
            }
            val statusText = run {
                val winner = game.winner
                val outcome = game.outcome
                if (winner != null && outcome != null) {
                    val who = chessName(winner, contacts, knsNames)
                    when (outcome) {
                        ChessTournamentOutcome.Checkmate -> "Checkmate. $who won."
                        ChessTournamentOutcome.Resignation -> "$who won by resignation."
                        ChessTournamentOutcome.Timeout -> "$who won on time."
                        is ChessTournamentOutcome.DrawTiebreak -> "Draw by ${outcome.reason}. $who won on clock."
                    }
                } else if (game.decidedWinner(now) != null) {
                    // Out of clock, claim not posted yet: the result is already fixed.
                    "Time ran out. ${chessName(game.decidedWinner(now)!!, contacts, knsNames)} won on time."
                } else if (isPending) {
                    "Sending your move…"
                } else {
                    // The clock runs from the moment the board opens - the match-found countdown
                    // covers the start (ChessTournamentCodec.MATCH_FOUND_DELAY_MS), so there is
                    // nothing to count down here (iOS 11ac2b7).
                    if (myColor != null) {
                        if (game.sideToMove == myColor && ChessEngine.isKingInCheck(game.sideToMove, game.board)) "Check. Your move."
                        else if (game.sideToMove == myColor) "Your move" else "Their move"
                    } else {
                        if (game.sideToMove == ChessColor.WHITE) "White to move" else "Black to move"
                    }
                }
            }
            // Header: a player sees the opponent (their own name is on their clock chip); a
            // spectator sees both.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (myColor != null) chessName(game.address(myColor.opposite), contacts, knsNames)
                    else "${chessName(game.white, contacts, knsNames)} vs ${chessName(game.black, contacts, knsNames)}",
                    color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        statusText,
                        color = if (game.isDecided(now)) colors.textSecondary else colors.textPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ChessClockChip(game, if (flipped) ChessColor.WHITE else ChessColor.BLACK, now, myColor, contacts, knsNames)
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                // Anything drawn over the board sits in its middle, as on the 1:1 board - the
                // default would pin "Waiting on opponent..." to the top-left corner.
                contentAlignment = Alignment.Center,
            ) {
                val size = maxWidth / 8
                val ranks = if (flipped) (0..7).toList() else (7 downTo 0).toList()
                val files = if (flipped) (7 downTo 0).toList() else (0..7).toList()
                val last = game.moves.lastOrNull()
                Column(Modifier.matchParentSize()) {
                    for (rank in ranks) {
                        Row {
                            for (file in files) {
                                val sq = ChessSquare(file, rank)
                                val isLight = (file + rank) % 2 != 0
                                Box(
                                    modifier = Modifier
                                        .size(size)
                                        .background(if (isLight) ChessLightSquareColor else ChessDarkSquareColor)
                                        .clickable { tap(sq) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (last != null && (last.from == sq || last.to == sq)) {
                                        Box(Modifier.fillMaxSize().background(Color.Yellow.copy(alpha = 0.35f)))
                                    }
                                    if (selectedSquare == sq) Box(Modifier.fillMaxSize().background(KaspaTeal.copy(alpha = 0.35f)))
                                    game.board.piece(sq)?.let { ChessPieceGlyph(it, fontSize = (size.value * 0.7f).sp) }
                                    if (sq in legalDestinations) {
                                        Box(Modifier.size(size * 0.3f).clip(CircleShape).background(KaspaTeal.copy(alpha = 0.55f)))
                                    }
                                }
                            }
                        }
                    }
                }
                // The same "Waiting on opponent..." as the 1:1 board, while it is their move.
                if (myColor != null && !game.isDecided(now) && game.sideToMove != myColor) {
                    WaitingOnOpponentOverlay()
                }
                if (showEndOverlay) {
                    val winner = game.winner
                    val outcome = game.outcome
                    if (winner != null && outcome != null) {
                        ChessGameEndOverlay(
                            winnerName = chessName(winner, contacts, knsNames),
                            outcome = outcome,
                            iWon = if (myColor == null) null else winner == me,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ChessClockChip(game, if (flipped) ChessColor.BLACK else ChessColor.WHITE, now, myColor, contacts, knsNames)
            Spacer(Modifier.height(LocalDockInset.current))
        }

        pendingPromotion?.let { move ->
            Dialog(onDismissRequest = { pendingPromotion = null }) {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Promote to", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT).forEach { type ->
                            Box(
                                Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).clickable {
                                    pendingPromotion = null
                                    vm.launch { service.play(ChessMove(move.from, move.to, type), tournament, game) }
                                },
                                contentAlignment = Alignment.Center,
                            ) { ChessPieceGlyph(ChessPiece(type, myColor ?: ChessColor.WHITE), fontSize = 40.sp) }
                        }
                    }
                }
            }
        }
    }

    // One sheet behind Resign - and behind the back button while the game is on, where leaving
    // means resigning.
    if ((showResignConfirm || showLeaveWarning) && tournament != null && game != null) {
        val leaving = showLeaveWarning
        val opponent = chessName(game.address(if (myColor == ChessColor.WHITE) ChessColor.BLACK else ChessColor.WHITE), contacts, knsNames)
        val consequence = if (tournament.isDuel) {
            "$opponent wins, and it counts as a loss on the leaderboard. Resigning is one transaction."
        } else {
            "$opponent goes through and you are out of the tournament. It counts as a loss on the leaderboard. Resigning is one transaction."
        }
        val dismiss = { showResignConfirm = false; showLeaveWarning = false }
        ActionSheetContainer(
            title = if (leaving) "Leave the game?" else "Resign this game?",
            subtitle = null,
            onDismiss = dismiss,
        ) {
            Icon(
                if (leaving) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.Flag,
                contentDescription = null, tint = colors.danger,
                modifier = Modifier.size(34.dp).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (leaving) "If you leave, you resign the game. $consequence" else consequence,
                color = colors.textSecondary, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.danger)
                    .clickable {
                        dismiss()
                        vm.launch {
                            // A resignation that did not go out leaves the game on: the player
                            // stays on the board, and the error shows (iOS 4922804).
                            if (service.resign(tournament, game) && leaving) navController.popBackStack()
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (leaving) "Resign and leave" else "Resign", color = Color.White, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant)
                    .clickable { dismiss() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Keep playing", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showResult && tournament != null && game != null) {
        ChessGameResultScreen(
            tournamentId = tournamentId,
            gameId = gameId,
            before = recordBeforeEnd,
            // Done is not "back to the board": it goes back out to the 1v1 / Tournaments screen
            // the player came from (iOS af865b4).
            onDone = {
                showResult = false
                // The whole way out in one animation: a bracket and the board can both sit above
                // the 1v1 / Tournaments screen, and Done means that screen, not one level back.
                // A game opened from a link or a notification has no such screen behind it, so
                // the Play tab is the fallback, then plain back (iOS 1a26ba0's popToRoot).
                if (!navController.popBackStack("chess_mode/{mode}", inclusive = false) &&
                    !navController.popBackStack(com.kachat.app.ui.Screen.Play.route, inclusive = false)
                ) {
                    navController.popBackStack()
                }
            },
        )
    }
}

@Composable
private fun ChessClockChip(
    game: ChessTournamentGame,
    color: ChessColor,
    now: Long,
    myColor: ChessColor?,
    contacts: Map<String, ContactEntity>,
    knsNames: Map<String, String>,
) {
    // The 1:1 board's clock chip: label, timer, time - lit while that side is to move, red under
    // twenty seconds; the side's avatar and name sit at the leading edge (iOS b27b4c8).
    val colors = LocalAppColors.current
    val address = game.address(color)
    val remaining = game.remainingMs(color, now)
    val isActive = !game.isOver && game.sideToMove == color
    val isLow = remaining < 20_000
    val label = when {
        myColor == null -> if (color == ChessColor.WHITE) "White" else "Black"
        color == myColor -> "You"
        else -> "Them"
    }
    val tint = when {
        isLow -> colors.danger.copy(alpha = 0.14f)
        isActive -> KaspaTeal.copy(alpha = 0.12f)
        else -> colors.surface
    }
    val stroke = when {
        isLow -> colors.danger.copy(alpha = 0.55f)
        isActive -> KaspaTeal.copy(alpha = 0.6f)
        else -> colors.divider
    }
    val content = when {
        isLow -> colors.danger
        isActive -> colors.textPrimary
        else -> colors.textSecondary
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChessAvatar(address, contacts, size = 26)
        Spacer(Modifier.width(8.dp))
        Text(
            chessName(address, contacts, knsNames), color = colors.textSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(tint)
                .border(if (isActive) 1.2.dp else 0.8.dp, stroke, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.Timer, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            // `remaining` moves once a second now. In the last ten seconds this chip shows
            // tenths, so it counts them down itself from the last published value rather than
            // making every chess screen recompose five times a second for a digit only it draws
            // (iOS cbf7f26 does this with a TimelineView). Re-anchors on each new publish.
            val drawsTenths = isActive && remaining < 10_000
            var ticked by remember(remaining) { mutableStateOf(remaining) }
            LaunchedEffect(remaining, drawsTenths) {
                if (!drawsTenths) return@LaunchedEffect
                val anchoredAt = System.currentTimeMillis()
                while (true) {
                    kotlinx.coroutines.delay(100)
                    ticked = (remaining - (System.currentTimeMillis() - anchoredAt)).coerceAtLeast(0L)
                }
            }
            val shown = if (drawsTenths) ticked else remaining
            val tenths = (shown / 100).toInt()
            val seconds = tenths / 10
            Text(
                if (seconds < 10) "0:%02d.%d".format(seconds, tenths % 10) else "%d:%02d".format(seconds / 60, seconds % 60),
                color = content, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }
}
