package com.kachat.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.ui.theme.iosGlass
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.rememberHaptics
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// The Wallet tab, KaPay's (iOS 1751d20 `ChessWalletView`, `Views/Wallet/WalletViews.swift`; every
// app has KaPay's full wallet since 2026-11-07), in KaPlay's look: the fixed title with the backup
// strip, the chatting balance in the bar, the connection dot and the bell. The screens it opens
// are routes of their own (Manage Addresses, Your Domains, Help); Send, Receive and Request are
// sheets over the tab, as iOS's `walletAction` - Send the one send screen,
// [SpendingAddressSendFlow], with its From step.

/** The Wallet tab's way to the live UTXO events (iOS `.ownAddressUtxoActivity`), which no view
 *  model carries. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WalletTabEntryPoint {
    fun walletReceiptNotifier(): com.kachat.app.services.WalletReceiptNotifier
}

/**
 * The Wallet tab (iOS 1751d20 `ChessWalletView`): the total balance split into Chatting and
 * Spending, Send, Receive and Request, Backup Hygiene when due, Claim Testnet Kaspa on testnet, the
 * Manage Addresses and Chatting Address squares, then Your Domains and Help, with the bell top
 * right. Every send has the From step: only moves, joins, claims and bot stamps always sign from the
 * chatting address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessWalletTabScreen(
    walletViewModel: WalletViewModel,
    navController: NavController,
    priceViewModel: com.kachat.app.viewmodels.KaspaPriceViewModel = hiltViewModel(),
) {
    val colors = LocalAppColors.current
    val address by walletViewModel.address.collectAsState()
    val balanceSompi by walletViewModel.balanceSompi.collectAsState()
    val balanceKnown by walletViewModel.balanceKnown.collectAsState()
    // Every spending address together (iOS `HotWalletBalance`): null until it's known.
    val spendingTotalSompi by walletViewModel.spendingTotalSompi.collectAsState()
    val backupConfirmedAt by walletViewModel.accountBackupStatus.confirmedAt.collectAsState()
    var showSend by remember { mutableStateOf(false) }
    // Receive and Request are sheets over the tab, as Send (iOS 1751d20 `walletAction`).
    var showReceive by remember { mutableStateOf(false) }
    var showRequest by remember { mutableStateOf(false) }

    // The network part at most every 30 s: switching tabs back and forth started the requests
    // each time. Arriving funds still refresh at once (below), and pull to refresh is always there.
    LaunchedEffect(Unit) {
        if (walletViewModel.claimProfileAppearRefresh()) {
            walletViewModel.refreshBalance()
            walletViewModel.refreshSpendingAddress()
            // Receive's address decided now, not when it's tapped: the freshness check is a
            // network round trip (iOS `warmReceiveAddress`).
            ReceiveAddressWarmCache.warm(walletViewModel)
        }
    }
    // The spending addresses' total, for the balance card; kept trying while unknown - the node
    // may not be connected yet just after launch, so it doesn't stay a dash (iOS
    // `HotWalletBalance.refreshUntilKnown`).
    LaunchedEffect(address) {
        if (address.isNullOrEmpty()) return@LaunchedEffect
        walletViewModel.refreshSpendingBalanceAndAwait()
        while (walletViewModel.spendingTotalSompi.value == null) {
            kotlinx.coroutines.delay(5_000)
            walletViewModel.refreshSpendingBalanceAndAwait()
        }
    }
    // Funds landing, or a send's own change (iOS `.ownAddressUtxoActivity`): one refresh shortly
    // after the burst stops - a send fires several changes - as iOS's `scheduleBalanceRefresh`.
    val appContext = LocalContext.current.applicationContext
    val receiptNotifier = remember(appContext) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(appContext, WalletTabEntryPoint::class.java).walletReceiptNotifier()
    }
    LaunchedEffect(receiptNotifier) {
        receiptNotifier.utxoActivity.collectLatest {
            kotlinx.coroutines.delay(500)
            walletViewModel.refreshBalanceAndAwait()
            walletViewModel.refreshSpendingBalanceAndAwait()
        }
    }

    // Send: From is picked on the send's own step - the chatting address or any other (iOS
    // 1751d20: no lockedSource). Only Cancel closes it, as iOS presents WithdrawKaspaView.
    val sendFrom = address
    if (showSend && !sendFrom.isNullOrEmpty()) {
        IosFullSheet(onDismissed = { showSend = false }, swipeToDismiss = false) { close ->
            SpendingAddressSendFlow(
                fromAddress = sendFrom,
                balanceSompi = balanceSompi,
                title = "Send Kaspa",
                viewModel = walletViewModel,
                onDone = {
                    // A send from a spending address raises no live event here (only the chatting
                    // address is watched), so Total and Spending refresh on Done (iOS da59726).
                    walletViewModel.refreshBalance()
                    walletViewModel.refreshSpendingAddress()
                    close()
                },
                presentedAsSheet = true,
            )
        }
    }
    // Receive: a sheet with Done (iOS 1751d20 `.receive`: ReceiveKaspaQRView in a NavigationStack).
    if (showReceive) {
        IosFullSheet(onDismissed = { showReceive = false }, swipeToDismiss = true) { close ->
            // its Back is its Done
            ReceiveFreshScreen(walletViewModel = walletViewModel, onDone = close)
        }
    }
    // Request: a sheet, swiped down to close (iOS 1751d20 `.request`: CreatePaymentRequestView).
    if (showRequest) {
        CreatePaymentRequestSheet(walletViewModel = walletViewModel, onDismissed = { showRequest = false })
    }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            walletViewModel.refreshBalanceAndAwait()
            walletViewModel.refreshSpendingBalanceAndAwait()
            pullState.endRefresh()
        }
    }

    Scaffold(
        containerColor = colors.background,
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // KaPlay's title, as on every tab: big and fixed above what scrolls, the backup strip
            // over it while one is owed; the connection dot left, the balance centred, the bell
            // right (iOS 1751d20).
            ChessHomeBar(
                onBack = null,
                title = "Wallet",
                showsBackupBanner = true,
                trailing = {
                    // The notification bell (agent bell): Kaspa received and .kachat news.
                    WalletNotificationBell(navController)
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .clipToBounds()
                .nestedScroll(pullState.nestedScrollConnection),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalDockInset.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val wallet = address
                if (wallet.isNullOrEmpty()) {
                    Text("No active account", color = colors.textSecondary, fontSize = 17.sp)
                } else {
                    // A .kachat name in its grace period: opens it under Your Domains (iOS 1751d20).
                    KachatExpiredNamesBanner(wallet, onOpen = { openKachatNameInYourDomains(navController, it) })
                    WalletBalanceCard(
                        // "--" until it is read, as iOS's nil balance: a 0 would read as an empty account.
                        chattingSompi = balanceSompi.takeIf { balanceKnown },
                        spendingSompi = spendingTotalSompi,
                        priceViewModel = priceViewModel,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WalletTabActionButton("Send", Icons.Filled.ArrowUpward, Modifier.weight(1f)) { showSend = true }
                        WalletTabActionButton("Receive", Icons.Filled.ArrowDownward, Modifier.weight(1f)) {
                            showReceive = true
                        }
                        WalletTabActionButton("Request", Icons.Outlined.Description, Modifier.weight(1f)) {
                            showRequest = true
                        }
                    }
                    if (remember(wallet, backupConfirmedAt) { walletViewModel.accountBackupStatus.isHygieneDue(wallet) }) {
                        BackupHygieneCard(onClick = { navController.navigate("backup_hygiene") })
                    }
                    // Testnet only: free TN10 coins for the chatting address, from the official faucet.
                    if (com.kachat.app.util.KaspaNetwork.isTestnet) {
                        TestnetFaucetClaimButton(
                            address = wallet,
                            // null until the first read, as iOS's nil balance: a 0 then would make
                            // the first real balance read as a claim
                            balanceSompi = { walletViewModel.balanceSompi.value.takeIf { walletViewModel.balanceKnown.value } },
                            refreshBalance = { walletViewModel.refreshBalanceAndAwait() },
                        )
                    }
                    WalletAddressSquares(
                        onManageAddresses = { navController.navigate("manage_addresses") },
                        onChattingAddress = { navController.navigate(CHATTING_ADDRESS_QR_ROUTE) },
                    )
                    WalletYourDomainsRow(
                        walletViewModel = walletViewModel,
                        address = wallet,
                        onClick = { navController.navigate("your_domains") },
                    )
                    WalletLinkRow(label = "Help", icon = Icons.AutoMirrored.Outlined.HelpOutline) {
                        navController.navigate("wallet_help")
                    }
                }
            }
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
            // A KNS profile save running in the background, or its failures (iOS 1751d20).
            KnsProfileSavePill(
                walletViewModel,
                Modifier.align(Alignment.BottomCenter).padding(bottom = LocalDockInset.current),
            )
        }
    }
}

/**
 * The KNS profile save pill at the bottom of the Wallet tab (iOS 1751d20 `ChessWalletView`'s
 * `.overlay(alignment: .bottom)`): while a save runs, a spinner and its step ("Preparing profile
 * update...", "Updating X (n/m)..."), state-driven with no timer; after one with failures, a red
 * "%d profile update(s) failed" with Retry and a close button. Your Domains shows it too, as
 * iOS's overlay covers the pages pushed over the tab.
 */
@Composable
fun KnsProfileSavePill(walletViewModel: WalletViewModel, modifier: Modifier = Modifier) {
    val status by walletViewModel.knsSaveStatus.collectAsState()
    val colors = LocalAppColors.current
    val saving = status.progressText
    val failedCount = status.failed.size
    androidx.compose.animation.AnimatedVisibility(
        visible = saving != null || failedCount > 0,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .iosGlass(cornerRadius = 50.dp, shadowAlpha = 0.15f, shadowY = 4.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (saving != null) {
                com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(17.dp))
                Text(
                    saving,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(17.dp))
                Text(
                    "$failedCount profile update(s) failed",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Retry",
                    color = KaspaTeal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable { walletViewModel.retryFailedKnsUpdates() },
                )
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = "Dismiss",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp).clickable { walletViewModel.dismissFailedKnsUpdates() },
                )
            }
        }
    }
}

/** The route of the Chatting Address square's page: the chatting address's QR, pushed inside the
 *  Wallet tab (iOS 1751d20 `ChattingAddressQRView`). */
const val CHATTING_ADDRESS_QR_ROUTE = "chatting_address_qr"

/**
 * The Chatting Address square's page (iOS 1751d20 `addressSquares`: a NavigationLink to
 * `ChattingAddressQRView(address:balanceSompi:)`): the chatting address's QR, pushed, with a back
 * chevron.
 */
@Composable
fun ChattingAddressQrRouteScreen(walletViewModel: WalletViewModel, onBack: () -> Unit) {
    val address by walletViewModel.address.collectAsState()
    val balanceSompi by walletViewModel.balanceSompi.collectAsState()
    val balanceKnown by walletViewModel.balanceKnown.collectAsState()
    androidx.activity.compose.BackHandler(onBack = onBack)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(bottom = LocalDockInset.current),
    ) {
        val qrAddress = address
        if (!qrAddress.isNullOrEmpty()) {
            ChattingAddressQrPage(
                address = qrAddress,
                balanceSompi = balanceSompi.takeIf { balanceKnown },
                dismiss = QrPageDismiss.BACK,
                onDismiss = onBack,
            )
        }
    }
}

/**
 * The top of the Wallet tab (iOS 1751d20 `WalletBalanceCard`): the total balance (tap to copy),
 * split into the chatting and spending addresses. Its own composable so that price updates
 * redraw only this card.
 */
@Composable
private fun WalletBalanceCard(
    chattingSompi: Long?,
    /** Every spending address together. Null until it's known, which shows as a dash. */
    spendingSompi: Long?,
    priceViewModel: com.kachat.app.viewmodels.KaspaPriceViewModel,
) {
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberHaptics()
    val price by priceViewModel.currentPriceUsd.collectAsState()
    val currency by priceViewModel.currency.collectAsState()
    // Both parts or a dash: a total missing one of them would read as the whole balance.
    val total = if (chattingSompi != null && spendingSompi != null) chattingSompi + spendingSompi else null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(KaspaTeal.copy(alpha = 0.32f), KaspaTeal.copy(alpha = 0.08f))))
            .border(0.8.dp, KaspaTeal.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total Balance", color = colors.textSecondary, fontSize = 15.sp)
            // shrinks to 0.6 of it before it would clip (iOS 1751d20 `.minimumScaleFactor(0.6)`)
            BalanceShrinkText(
                total?.let { "${KaspaUnit.plain(it)} ${KaspaUnit.symbol}" } ?: "--",
                fontSize = 34.sp,
                minScale = 0.6f,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = total != null) {
                    total ?: return@clickable
                    clipboard.setText(AnnotatedString(KaspaUnit.plain(total)))
                    haptic(IosHaptic.SUCCESS)
                    IosToasts.show("Balance copied to clipboard.")
                },
            )
            // What the total is worth in your currency. Testnet coins aren't worth anything.
            val p = price
            if (total != null && p != null && p > 0 && !com.kachat.app.util.KaspaNetwork.isTestnet) {
                Text(com.kachat.app.util.formatFiatAmount(total / 100_000_000.0 * p, currency), color = colors.textSecondary, fontSize = 15.sp)
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((label, sompi) in listOf("Chatting" to chattingSompi, "Spending" to spendingSompi)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, color = colors.textSecondary, fontSize = 12.sp)
                    // shrinks to 0.7 of it before it would clip (iOS 1751d20 `balancePart`)
                    BalanceShrinkText(
                        sompi?.let { "${KaspaUnit.plain(it)} ${KaspaUnit.symbol}" } ?: "--",
                        fontSize = 13.sp,
                        minScale = 0.7f,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** One line of the balance card that shrinks rather than clip, down to [minScale] of [fontSize]
 *  (iOS `.lineLimit(1).minimumScaleFactor`), the way ChessFixedTitle shrinks the tab title. */
@Composable
private fun BalanceShrinkText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    minScale: Float,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    Text(
        text,
        color = LocalAppColors.current.textPrimary,
        fontSize = size,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(fontFeatureSettings = "tnum"),
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size.value > fontSize.value * minScale) {
                size = (size.value * 0.94f).coerceAtLeast(fontSize.value * minScale).sp
            }
        },
    )
}

/** One of the Wallet tab's Send / Receive / Request: a 60 accent circle over its name (iOS
 *  `walletActionButton`). */
@Composable
private fun WalletTabActionButton(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = rememberHaptics()
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic(IosHaptic.IMPACT_LIGHT)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(KaspaTeal), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Text(title, color = LocalAppColors.current.textPrimary, fontSize = 15.sp)
    }
}

/**
 * The Manage Addresses and Chatting Address squares (iOS 1751d20 `addressSquares`). Which
 * address a send comes from is picked on the send screen (From), so this tab doesn't split Main
 * from Spending or have a "primary". Square on a phone; as wide as half a tablet a square would
 * fill most of the screen with nothing, so there it keeps the width and a set height.
 */
@Composable
private fun WalletAddressSquares(onManageAddresses: () -> Unit, onChattingAddress: () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        WalletAddressSquare(Icons.AutoMirrored.Filled.ListAlt, "Manage Addresses", wide, Modifier.weight(1f), onManageAddresses)
        WalletAddressSquare(Icons.Filled.QrCode, "Chatting Address", wide, Modifier.weight(1f), onChattingAddress)
    }
}

@Composable
private fun WalletAddressSquare(icon: ImageVector, title: String, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .then(if (wide) Modifier.height(170.dp) else Modifier.aspectRatio(1f))
            .clip(shape)
            .background(colors.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Your Domains (iOS 1751d20 `yourDomainsSection`): shown for every account, with the count of every
 * name it owns - .kachat, KNS, .k and .kaspa. The list itself is its own page ("your_domains").
 */
@Composable
private fun WalletYourDomainsRow(walletViewModel: WalletViewModel, address: String, onClick: () -> Unit) {
    val ownedKnsDomains by walletViewModel.ownedDomains.collectAsState()
    val ownedServiceNames by walletViewModel.nameServices.owned.collectAsState()
    val kachatRevision by walletViewModel.kachatRegistryRevision.collectAsState()
    var kachatOwnedCount by remember(address) { mutableIntStateOf(0) }
    val context = LocalContext.current
    // KNS domains (cache-first) and the .k and .kaspa lookups, so the count includes them before
    // the list opens.
    LaunchedEffect(address) {
        walletViewModel.refreshOwnedDomains()
        walletViewModel.nameServices.refresh(address)
    }
    // Reloads whenever the registry moves (a registration, sale or transfer lands).
    LaunchedEffect(address, kachatRevision) {
        // the saved list's count at once; the read below corrects it when it answers
        if (kachatOwnedCount == 0 && com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
            com.kachat.app.services.kachatnames.KachatOwnedNamesCache.load(context, address)?.let {
                kachatOwnedCount = it.names.size
            }
        }
        walletViewModel.watchKachatOwnedCount(address) { kachatOwnedCount = it }
    }
    // .k and .kaspa only when they're this wallet's: the client holds whichever address it last
    // looked up (iOS 1751d20 `nameServices.ownerAddress == walletAddress.lowercased()`). [owned]
    // changes with its owner, so this is read again whenever it could differ.
    val serviceNamesCount = if (walletViewModel.nameServices.ownerAddress == address.trim().lowercase()) {
        ownedServiceNames.values.sumOf { it.size }
    } else {
        0
    }
    val count = kachatOwnedCount + ownedKnsDomains.size + serviceNamesCount
    WalletLinkRow(label = "Your Domains", icon = Icons.Filled.AlternateEmail, value = "$count", onClick = onClick)
}

/** One of the Wallet tab's link cards (iOS: a Label, the count for Your Domains and a chevron, on
 *  `glassBackground(cornerRadius: 18)`, padded 16). */
@Composable
private fun WalletLinkRow(label: String, icon: ImageVector, value: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlass(18.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.textPrimary, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = colors.textSecondary, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        IosDisclosureChevron()
    }
}

/**
 * "Claim Testnet Kaspa", on the Wallet tab on testnet (iOS 1751d20 `TestnetFaucetClaimButton`;
 * KaPay Android's). The official TN10 faucet sits behind a Cloudflare check, so it can't be
 * claimed in the background: the button copies the chatting address in its `kaspatest:` form and
 * opens the faucet in the in-app browser, with a strip saying it's copied. The faucet allows one
 * claim a day, so once the chatting balance goes up after a visit the button locks for 24 hours
 * (kept per address, with a countdown). No rise leaves it free to try again.
 */
@Composable
private fun TestnetFaucetClaimButton(
    address: String,
    /** The chatting balance, null while it hasn't been read (iOS 1751d20 `balanceSompi` nil). */
    balanceSompi: () -> Long?,
    refreshBalance: suspend () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("kachat_tn10_faucet", android.content.Context.MODE_PRIVATE) }
    val key = "claimed_${address.lowercase()}"
    var claimedAt by remember(key) { mutableStateOf(prefs.getLong(key, 0L).takeIf { it > 0L }) }
    var showFaucet by remember { mutableStateOf(false) }
    var balanceBefore by remember { mutableStateOf<Long?>(null) }
    var checkingClaim by remember { mutableStateOf(false) }
    // TimelineView(.periodic(by: 60)): the clock read again every minute for the countdown.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val until = claimedAt?.let { it + FAUCET_LOCK_MS }?.takeIf { it > now }
    val locked = until != null

    /** After the faucet closes: the payment usually lands within seconds, so watch the chatting
     *  balance for up to a minute. A rise means the claim went through - lock for 24 hours. */
    suspend fun checkForClaim() {
        val before = balanceBefore ?: return
        checkingClaim = true
        try {
            repeat(12) { attempt ->
                if (attempt > 0) kotlinx.coroutines.delay(5_000L)
                runCatching { refreshBalance() }
                // an unread balance is no answer, never a rise (iOS 1751d20 `if let now`)
                val current = balanceSompi()
                if (current != null && current > before) {
                    val at = System.currentTimeMillis()
                    claimedAt = at
                    now = at
                    prefs.edit().putLong(key, at).apply()
                    haptic(IosHaptic.SUCCESS)
                    return
                }
            }
        } finally {
            checkingClaim = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KaspaTeal.copy(alpha = if (locked) 0.06f else 0.15f))
            .clickable(enabled = !locked && !checkingClaim) {
                // The faucet's address field takes the TN10 form; the wallet already shows it
                // that way on testnet - this makes sure of it.
                clipboard.setText(AnnotatedString(com.kachat.app.util.KaspaNetwork.reencode(address, "kaspatest")))
                haptic(IosHaptic.SUCCESS)
                balanceBefore = balanceSompi()
                showFaucet = true
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                checkingClaim -> Icons.Filled.HourglassTop
                locked -> Icons.Filled.CheckCircle
                else -> Icons.Filled.WaterDrop
            },
            contentDescription = null,
            tint = if (locked) colors.textSecondary else KaspaTeal,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Claim Testnet Kaspa",
                color = if (locked) colors.textSecondary else colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    checkingClaim -> "Waiting for the faucet's payment..."
                    until != null -> "Claimed. You can claim again in ${faucetRemaining(until - now)}."
                    else -> "Copies your chatting address and opens the TN10 faucet: paste it, pass the check and claim the most it offers."
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (!locked && !checkingClaim) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }

    if (showFaucet) {
        val close = {
            showFaucet = false
            scope.launch { checkForClaim() }
            Unit
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = close,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            InAppBrowserScreen(
                url = FAUCET_URL,
                title = "faucet-tn10.kaspanet.io",
                notice = "Your TN10 chatting address is copied. Paste it into the faucet's address field.",
                onClose = close,
            )
        }
    }
}

private const val FAUCET_URL = "https://faucet-tn10.kaspanet.io"
private const val FAUCET_LOCK_MS = 24L * 60 * 60 * 1000

/** "5h 12m" / "37m" - iOS's abbreviated DateComponentsFormatter, at least a minute. */
private fun faucetRemaining(ms: Long): String {
    val minutes = maxOf(1L, ms / 60_000L)
    return if (ms >= 3_600_000L) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/**
 * Full-screen in-app browser (iOS 1751d20 `InAppBrowserScreen`, KaPay Android's): the X top left
 * is the only way out. A plain WebView with JavaScript on; navigation stays inside. [notice] is an
 * optional strip under the bar (Claim Testnet Kaspa: "your address is copied").
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppBrowserScreen(url: String, title: String, notice: String? = null, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    var isLoading by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
            }
            Text(
                title,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // the page loading, in the slot that balances the X (iOS 1751d20 `isLoading`)
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }
        if (notice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KaspaTeal.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(notice, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // the bar's spinner follows the page's own loads (iOS 1751d20 `InAppWebView`'s
                    // didStart / didFinish / didFail)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }
                    }
                    loadUrl(url)
                }
            },
            // Closing the browser frees the page (its renderer, timers and media).
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
        )
    }
}
