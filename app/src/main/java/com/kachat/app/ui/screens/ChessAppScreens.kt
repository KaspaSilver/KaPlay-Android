package com.kachat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kachat.app.R
import com.kachat.app.ui.tabIconModifier
import com.kachat.app.ui.tabIconPainter
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.AppVersion
import com.kachat.app.util.ChessLeaderboardPeriod
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.viewmodels.WalletViewModel

// KaChat Chess's own tab screens (iOS App/ChessMainTabView.swift, Views/Games/GamesView.swift,
// Views/Settings/SettingsView.swift's SettingsView and Views/Chess/ChessPlayerView.swift): the Play,
// Leaderboard and Wallet tabs, the short Settings list, and a player's card.

/** The Play tab: a card per game. Chess is the only one so far; a new game is one more card here. */
@Composable
fun GamesScreen(navController: NavController) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { ChessHomeBar(onBack = null, title = "Play", showsBackupBanner = true) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = LocalDockInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChessGameCard(
                title = "Chess",
                detail = "1v1, tournaments and five bots. Online moves and bot results go on Kaspa.",
                onClick = { navController.navigate("chess_home") },
            ) {
                Icon(
                    com.kachat.app.ui.Screen.Chess.tabIconPainter(),
                    contentDescription = null, tint = Color.White,
                    modifier = com.kachat.app.ui.Screen.Chess.tabIconModifier(30.dp),
                )
            }
        }
    }
}

/**
 * The Leaderboard tab: three squares, each opening its own page. 1v1 and Tournaments are a board for
 * this month or all time ([ChessLeaderboardPageScreen]); Bots is a board per bot
 * ([ChessBotLeaderboardsScreen]).
 */
@Composable
fun ChessLeaderboardTabScreen(navController: NavController) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { ChessHomeBar(onBack = null, title = "Leaderboard", showsBackupBanner = true) },
    ) { padding ->
        Row(
            Modifier.padding(padding).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChessLeaderboardSquare("1v1", Icons.Default.People, Modifier.weight(1f)) {
                navController.navigate("chess_leaderboard/${ChessLobbyMode.DUEL.name}")
            }
            ChessLeaderboardSquare("Tournaments", Icons.Default.EmojiEvents, Modifier.weight(1f)) {
                navController.navigate("chess_leaderboard/${ChessLobbyMode.TOURNAMENT.name}")
            }
            ChessLeaderboardSquare("Bots", Icons.Default.Memory, Modifier.weight(1f)) {
                navController.navigate("chess_bot_boards")
            }
        }
    }
}

/** Leaderboard > 1v1 or Tournaments: that board, on a page of its own. */
@Composable
fun ChessLeaderboardPageScreen(mode: ChessLobbyMode, navController: NavController) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    // Reads the arena while the board is up, the same as the Play screens do.
    HoldArena(vm.service)
    Scaffold(
        containerColor = LocalAppColors.current.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { ChessHomeBar(onBack = { navController.popBackStack() }, showConnectionDot = false, title = mode.label) },
    ) { padding ->
        ChessRefreshBox(onRefresh = { vm.service.refreshIndexerLeaderboards(force = true) }, modifier = Modifier.padding(padding)) {
            ChessLeaderboardRows(mode, navController)
        }
    }
}

/**
 * The Wallet tab: KaPay's full wallet (iOS 1751d20 ChessWalletView) - see [ChessWalletTabScreen].
 * It is no longer the chatting address alone: spending addresses are used too.
 */
@Composable
fun ChessWalletScreen(walletViewModel: WalletViewModel, navController: NavController) {
    ChessWalletTabScreen(walletViewModel = walletViewModel, navController = navController)
}

/**
 * The Settings tab: KaChat Chess's own short list. The pages behind it are KaChat's app-wide ones
 * (Customization, Security, Connection); the account actions that live on KaChat's Profile screen
 * (seed phrase, Log Out) sit here, since there is no Profile.
 */
@Composable
fun ChessSettingsScreen(navController: NavController, walletViewModel: WalletViewModel) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val biometricSeedPhraseEnabled by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val address by walletViewModel.address.collectAsState()
    val deleteError by walletViewModel.deleteAccountError.collectAsState()
    val backupPending by walletViewModel.accountBackupStatus.pendingAddresses.collectAsState()
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    // Saveable: the seed phrase and the backup steps open over the warning and close back to it.
    var showDeleteConfirmation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(deleteError) {
        deleteError?.let {
            IosToasts.error("Couldn't delete the account: $it")
            walletViewModel.clearDeleteAccountError()
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { ChessHomeBar(onBack = null, title = "Settings", showsBackupBanner = true) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                // insetGrouped's top margin, under the fixed title.
                .padding(top = 16.dp, bottom = LocalDockInset.current + 24.dp),
        ) {
            // Alphabetical, as in KaChat. Delete Account and Log Out stay apart below (iOS 8d5f925).
            SettingsFormSection(title = null, first = true) {
                SettingsNavigationItem(stringResource(R.string.about), Icons.Outlined.Info, onClick = { navController.navigate("chess_settings/about") })
                SettingsDivider(inset = IosRowTextInset)
                SettingsNavigationItem("Account Name", Icons.Outlined.Edit, onClick = { navController.navigate("chess_settings/account_name") })
                SettingsDivider(inset = IosRowTextInset)
                SettingsNavigationItem("Back Up Account", Icons.Outlined.Key, onClick = { navController.navigate("chess_settings/backup") })
                SettingsDivider(inset = IosRowTextInset)
                SettingsNavigationItem(stringResource(R.string.connection), Icons.Outlined.CellTower, onClick = { navController.navigate("chess_settings/connection") })
                SettingsDivider(inset = IosRowTextInset)
                SettingsNavigationItem(stringResource(R.string.customization), Icons.Outlined.Brush, onClick = { navController.navigate("chess_settings/customization") })
                SettingsDivider(inset = IosRowTextInset)
                SettingsNavigationItem(stringResource(R.string.security), com.kachat.app.ui.theme.IosSymbols.LockShield, onClick = { navController.navigate("chess_settings/security") })
            }
            // Its own section at the very bottom, so it is easy to find and hard to hit by accident.
            // Log Out sits here too: KaPlay has no Profile tab, where KaChat keeps it.
            SettingsFormSection(title = null) {
                SettingsActionItem(stringResource(R.string.delete_account), Icons.Outlined.Delete, colors.danger) {
                    showDeleteConfirmation = true
                }
                SettingsDivider(inset = IosRowTextInset)
                SettingsActionItem(stringResource(R.string.log_out), Icons.AutoMirrored.Filled.Logout, colors.danger) {
                    showLogoutConfirmation = true
                }
            }
        }
    }

    if (showLogoutConfirmation) {
        ConfirmActionSheet(
            title = stringResource(R.string.log_out),
            confirmTitle = stringResource(R.string.log_out),
            confirmSubtitle = "Signs out of this account. Its data stays on this device.",
            confirmIcon = Icons.AutoMirrored.Filled.Logout,
            onConfirm = { walletViewModel.logout() },
            onDismiss = { showLogoutConfirmation = false },
        )
    }
    if (showDeleteConfirmation) {
        DeleteAccountSheet(
            confirmSubtitle = "Removes this account and its data from this device.",
            notBackedUp = address != null && address in backupPending,
            onViewSeedPhrase = { unlockAccountBackup(context, biometricSeedPhraseEnabled) { navController.navigate("seed_phrase") } },
            onBackUp = { unlockAccountBackup(context, biometricSeedPhraseEnabled) { navController.navigate("account_backup") } },
            // Face ID first, as in KaChat (iOS 8d5f925).
            onConfirm = {
                context.authenticateWithDeviceCredential(
                    title = "Confirm deleting this account",
                    onSuccess = { address?.let { walletViewModel.deleteWallet(it) } },
                )
            },
            onDismiss = { showDeleteConfirmation = false },
        )
    }
}

/** Settings' pages: About, Account Name, Back Up Account, Connection, Customization (Appearance,
 *  Language, Currency), Security. */
@Composable
fun ChessSettingsPage(page: String, navController: NavController, walletViewModel: WalletViewModel) {
    val onBack: () -> Unit = { navController.popBackStack() }
    when (page) {
        "customization" -> SettingsFormPage(title = stringResource(R.string.customization), onBack = onBack) {
            SettingsFormSection(title = stringResource(R.string.customization)) {
                AppWideCustomizationPickers(walletViewModel)
            }
        }
        "security" -> SettingsFormPage(title = stringResource(R.string.security), onBack = onBack) {
            SettingsFormSection(title = stringResource(R.string.security)) {
                SecuritySettingsItems(walletViewModel = walletViewModel)
            }
        }
        "about" -> ChessAboutPage(walletViewModel, navController, onBack)
        "account_name" -> ChessAccountNamePage(walletViewModel, onBack)
        "backup" -> ChessBackUpAccountPage(walletViewModel, navController, onBack)
        "connection" -> SettingsFormPage(title = stringResource(R.string.connection), onBack = onBack) {
            val explorer by walletViewModel.kaspaExplorer.collectAsState()
            ConnectionHubSections(
                explorerName = explorer.displayName,
                onOpenConnectionSettings = { navController.navigate("connection_settings") },
                onOpenExplorer = { navController.navigate("kaspa_explorer_settings") },
            )
        }
    }
}

/**
 * Settings > Back Up Account: Complete Your Backup while the account owes one, then the 12 or 24
 * words and, for an account with a passphrase, the NFC card (iOS 8d5f925 `backUpAccountPage`).
 */
@Composable
private fun ChessBackUpAccountPage(walletViewModel: WalletViewModel, navController: NavController, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val biometric by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val address by walletViewModel.address.collectAsState()
    val pending by walletViewModel.accountBackupStatus.pendingAddresses.collectAsState()
    // Without a passphrase, nothing would protect the seed if someone got past the card's PIN.
    val nfcCardBackupOffered = remember(address) {
        com.kachat.app.services.NfcCard.isAvailable(context) && !walletViewModel.getActivePassphrase().isNullOrEmpty()
    }
    SettingsFormPage(title = "Back Up Account", onBack = onBack) {
        if (address != null && address in pending) {
            SettingsFormSection(title = null, first = true) {
                SettingsActionItem("Complete Your Backup", Icons.Default.GppMaybe, colors.warning, labelColor = colors.textPrimary) {
                    unlockAccountBackup(context, biometric) { navController.navigate("account_backup") }
                }
            }
        }
        SettingsFormSection(title = null, first = address == null || address !in pending) {
            SettingsActionItem(stringResource(R.string.view_seed_phrase), Icons.Outlined.Key, KaspaTeal, labelColor = colors.textPrimary) {
                unlockAccountBackup(context, biometric) { navController.navigate("seed_phrase") }
            }
            if (nfcCardBackupOffered) {
                SettingsDivider(inset = IosRowTextInset)
                SettingsActionItem("Back Up to NFC Card", Icons.Default.Nfc, KaspaTeal, labelColor = colors.textPrimary) {
                    unlockAccountBackup(context, biometric) { navController.navigate("nfc_card_backup") }
                }
            }
        }
    }
}

/** Your own label for this account, on this device. Saved when you leave the screen or tap Done
 *  (iOS 8d5f925 `AccountNamePage`). */
@Composable
private fun ChessAccountNamePage(walletViewModel: WalletViewModel, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val address by walletViewModel.address.collectAsState()
    val current by walletViewModel.accountName.collectAsState()
    var name by remember { mutableStateOf(current.orEmpty()) }
    fun save() {
        val trimmed = name.trim()
        val account = address ?: return
        if (trimmed.isEmpty() || trimmed == current) return
        walletViewModel.renameAccount(account, trimmed)
    }
    DisposableEffect(Unit) { onDispose { save() } }
    SettingsFormPage(title = "Account Name", onBack = onBack) {
        SettingsFormSection(title = null, first = true) {
            com.kachat.app.ui.theme.IosTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Account name") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** Settings > About: this account's created date, then the app's version, links and licenses -
 *  KaChat's page, with its website, support email and privacy policy (iOS 8d5f925). */
@Composable
private fun ChessAboutPage(walletViewModel: WalletViewModel, navController: NavController, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val address by walletViewModel.address.collectAsState()
    val createdAt = remember(address) { address?.let { walletViewModel.accountAddedAt(it) } }
    SettingsFormPage(title = stringResource(R.string.about), onBack = onBack) {
        SettingsFormSection(title = null, first = true) {
            if (createdAt != null) {
                val created = remember(createdAt) {
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(createdAt))
                }
                SettingsInfoItem("Created", created)
                SettingsDivider()
            }
            SettingsInfoItem(stringResource(R.string.version), AppVersion.display)
            SettingsDivider()
            SettingsInfoItem("Website", "home.kachat.app", onClick = { runCatching { uriHandler.openUri("https://home.kachat.app") } })
            SettingsDivider()
            SettingsInfoItem("Support Email", "kaspasilver@gmail.com", onClick = { runCatching { uriHandler.openUri("mailto:kaspasilver@gmail.com") } })
            SettingsDivider()
            SettingsNavigationItem("Privacy Policy", null, onClick = { runCatching { uriHandler.openUri("https://home.kachat.app/privacy/") } })
            SettingsDivider()
            SettingsNavigationItem(stringResource(R.string.open_source_licenses), null, onClick = { navController.navigate("open_source_licenses") })
        }
    }
}

/**
 * A player's card, opened by tapping a player on a leaderboard: who they are (avatar, name,
 * address) and their record here, this month and all time. KaChat opened its chat contact screen
 * for this; there are no chats here.
 */
@Composable
fun ChessPlayerScreen(address: String, onDone: () -> Unit) {
    val vm: ChessTournamentViewModel = hiltViewModel()
    val service = vm.service
    val colors = LocalAppColors.current
    val contacts by vm.contacts.collectAsState()
    val knsNames by service.knsNames.collectAsState()
    val month by service.board(ChessLeaderboardPeriod.MONTH).collectAsState()
    val allTime by service.board(ChessLeaderboardPeriod.ALL_TIME).collectAsState()
    val explorer by hiltViewModel<WalletViewModel>().kaspaExplorer.collectAsState()
    val uriHandler = LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val haptic = com.kachat.app.util.rememberHaptics()
    val name = chessName(address, contacts, knsNames)

    fun row(board: List<com.kachat.app.util.ChessLeaderboardRow>) =
        board.firstOrNull { it.address.equals(address, ignoreCase = true) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            SheetNavBar(
                title = "Player",
                modifier = Modifier.statusBarsPadding(),
                trailing = { SheetBarButton(stringResource(R.string.done), onClick = onDone) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChessAvatar(address, contacts, size = 88)
                Text(
                    name,
                    color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsFormSection(title = "Address") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(address))
                            haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                            copied = true
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        address,
                        color = colors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 2, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(20.dp),
                    )
                }
                SettingsDivider()
                SettingsActionItem("View in Explorer", Icons.Default.Explore, KaspaTeal) {
                    uriHandler.openUri(explorer.addressUrl(address))
                }
            }
            for ((title, board) in listOf("This month" to month, "All time" to allTime)) {
                val record = row(board)
                SettingsFormSection(title = title) {
                    ChessRecordLine("1v1 games", record?.wins ?: 0, record?.losses ?: 0)
                    SettingsDivider()
                    ChessRecordLine("Tournaments", record?.tournamentsWon ?: 0, record?.tournamentsLost ?: 0)
                }
            }
        }
    }
}

@Composable
private fun ChessRecordLine(title: String, wins: Int, losses: Int) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$wins W", color = colors.success, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Text("$losses L", color = colors.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}
