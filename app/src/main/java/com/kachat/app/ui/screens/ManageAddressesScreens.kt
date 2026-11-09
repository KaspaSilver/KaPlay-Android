package com.kachat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Shield as OutlinedShield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.kachat.app.R
import com.kachat.app.services.KnsService
import com.kachat.app.services.NameServiceTLD
import com.kachat.app.services.NameServicesClient
import com.kachat.app.services.WalletManager
import com.kachat.app.services.WalletService
import com.kachat.app.services.kachatnames.KachatNamesRegistry
import com.kachat.app.services.kachatnames.KachatNamesService
import com.kachat.app.services.kachatnames.NameInfo
import com.kachat.app.services.kachatnames.Status
import com.kachat.app.ui.Screen
import com.kachat.app.ui.tabIconModifier
import com.kachat.app.ui.tabIconPainter
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.ui.theme.iosShadow
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.rememberHaptics
import com.kachat.app.util.showAddressCopiedToast
import com.kachat.app.viewmodels.WalletViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ---------------------------------------------------------------------------------------------
// Manage Addresses (iOS 1751d20 `Views/Wallet/ManageAddressesView.swift`, KaPay's): the Wallet
// tab's square opens it. The chatting address first, then the spending addresses numbered from #1
// ([WalletManager.addressNumber]), Address Actions (Generate, Discover Addresses, Address
// Visibility), each address's page (History, UTXOs, Domains, Receive / Send, Compound UTXOs and its
// private key), and the Chatting Address's own page ([ChattingAddressManageScreen], iOS
// `ChattingAddressManageView`). Ported from KaPay Android's screens of the same names, less what
// KaPlay leaves out: the "Notify on receive" switch (KaPlay watches only the chatting address), Swap's
// address picker, Add to Portfolio (a transaction opens the explorer) and Consolidate to Primary,
// which iOS keeps but nothing opens.
// ---------------------------------------------------------------------------------------------

/**
 * Every address of this account (iOS 1751d20 `ManageAddressesView`): the total, the chatting
 * address first, then the spending addresses, each with its balance and whether it was used. The
 * floating Address Actions button opens Generate, Discover Addresses and Address Visibility; a
 * row's ellipsis opens Rename, Copy, Show QR Code and Hide. Which address a send comes from is
 * picked on the send screen itself (From), so there is no "primary" here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAddressesScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    /** The chatting address's own page ("chatting_address_manage"). */
    onOpenChattingAddress: () -> Unit,
    /** A spending address's page, by its index ("manage_address/{index}"). */
    onOpenAddress: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val chattingAddress by viewModel.address.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    val loading by viewModel.manageAddressesLoading.collectAsState()
    val isDiscovering by viewModel.discoveringAddresses.collectAsState()
    val discoveryProgress by viewModel.spendingDiscoveryProgress.collectAsState()
    val domainOwningAddresses by viewModel.domainOwningAddresses.collectAsState()
    val quantumSpentAddresses by viewModel.quantumSpentAddresses.collectAsState()
    val spendingTotalSompi by viewModel.spendingTotalSompi.collectAsState()
    val balancesUnread by viewModel.manageAddressesBalancesUnread.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val dockInset = LocalDockInset.current

    var isGenerating by remember { mutableStateOf(false) }
    // The "Address Actions" half sheet, and the per-row one behind each ellipsis.
    var showAddressActions by remember { mutableStateOf(false) }
    var discoverySummary by remember { mutableStateOf<String?>(null) }
    var showVisibility by remember { mutableStateOf(false) }
    var rowActionsTarget by remember { mutableStateOf<WalletService.SpendingAddressEntry?>(null) }
    var qrTarget by remember { mutableStateOf<WalletService.SpendingAddressEntry?>(null) }
    var renameTarget by remember { mutableStateOf<WalletService.SpendingAddressEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    val pullRefreshState = rememberPullToRefreshState()

    // The spending addresses under the Chatting Address: unused ones first, newest first - so a
    // freshly generated address lands right under the Chatting Address - then the used ones in
    // number order. One still being checked counts as unused until its check says otherwise.
    val visibleEntries = remember(addresses) {
        fun used(a: WalletService.SpendingAddressEntry) = a.everUsed || a.balanceSompi > 0
        addresses.filterNot { it.hidden }.sortedWith { a, b ->
            val aUsed = used(a)
            val bUsed = used(b)
            when {
                aUsed != bUsed -> if (aUsed) 1 else -1
                aUsed -> a.index.compareTo(b.index)
                else -> b.index.compareTo(a.index)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadManageAddresses() }
    // Coins moved on a spending address (iOS reloads on `.ownAddressUtxoActivity`): the spending
    // total moved, so the rows' balances are read again.
    var spendingSeen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(spendingTotalSompi) {
        val previous = spendingSeen
        spendingSeen = spendingTotalSompi
        if (previous != null && spendingTotalSompi != null && previous != spendingTotalSompi) viewModel.loadManageAddresses()
    }
    // Pull to refresh owns its spinner: it waits for its own reload, never the loading flag
    // background reloads also drive.
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.loadManageAddressesAndAwait()
            pullRefreshState.endRefresh()
        }
    }

    fun generateNew() {
        if (isGenerating) return
        isGenerating = true
        viewModel.generateNewSpendingAddress { index ->
            isGenerating = false
            // The chain's own number, never the raw index (1,000,000+ for a quantum slot). Generate
            // never refuses (iOS 1751d20): null only when there is no account at all.
            if (index != null) IosToasts.show("${WalletManager.spendingAddressTitle(index)} is ready.")
        }
    }

    fun discoverAddresses() {
        if (isDiscovering) return
        discoverySummary = null
        viewModel.discoverSpendingAddresses { discovered ->
            // The Wallet tab's spending total follows what the scan found (iOS
            // `HotWalletBalance.refresh(force:)`).
            viewModel.refreshSpendingAddress()
            // The count is addresses that hold a balance or a domain - say so, rather than "used".
            val summary = if (discovered == 0) "No addresses with a balance or domain found."
            else "Found $discovered address${if (discovered == 1) "" else "es"} with a balance or domain."
            discoverySummary = summary
            // Closed the sheet and carried on? Then the summary has nowhere to render.
            if (!showAddressActions) IosToasts.show(summary)
        }
    }

    Scaffold(
        containerColor = colors.background,
        // Runs on under the dock, as iOS's list runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.manage_addresses), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = { SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                // An idle pull indicator rests above its box: clipped, it can't sit over the bar.
                .clipToBounds()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // iOS `accountSummary`: everything this account holds - the chatting address and
                // every spending address, hidden ones too.
                item(key = "summary") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(stringResource(R.string.total_balance), color = colors.textSecondary, fontSize = 12.sp)
                        Text(
                            // iOS da59726: a failed read is unknown, never a figure.
                            if (balancesUnread) "--" else formatKasExact(chattingBalanceSompi + addresses.sumOf { it.balanceSompi }),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        )
                    }
                }
                // (KaPay's "Notify on receive" switch is left out: KaPlay doesn't watch spending
                // addresses for receives.)
                val chatting = chattingAddress
                if (!chatting.isNullOrEmpty()) {
                    item(key = "chatting") {
                        ManageChattingAddressRow(
                            address = chatting,
                            balanceSompi = chattingBalanceSompi,
                            onClick = onOpenChattingAddress,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                when {
                    loading && addresses.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                            com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                        }
                    }
                    // iOS `emptyState`.
                    visibleEntries.isEmpty() -> item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.CreditCard, null, tint = colors.textSecondary, modifier = Modifier.size(40.dp))
                            Text("No Spending Addresses", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Generate one below to send payments from a separate address than your main one.",
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> items(visibleEntries, key = { it.index }) { entry ->
                        ManageAddressRow(
                            entry = entry,
                            showsDomainTag = entry.address in domainOwningAddresses,
                            quantumSpent = entry.address.lowercase() in quantumSpentAddresses,
                            balancesUnread = balancesUnread,
                            onClick = { onOpenAddress(entry.index) },
                            onActions = { rowActionsTarget = entry },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                // Room to scroll the last row out from under the floating button and the dock.
                item(key = "foot") { Spacer(Modifier.height(72.dp + dockInset)) }
            }

            if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
                PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
            }

            // The floating glass button behind "Address Actions", like the Wallet tab's one.
            val busy = isGenerating || isDiscovering
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 16.dp + dockInset)
                    .size(56.dp)
                    .iosShadow(28.dp, Color.Black.copy(alpha = 0.12f), 10.dp, 5.dp)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xFF202020) else colors.surface)
                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(enabled = !busy) {
                        haptic(IosHaptic.IMPACT_LIGHT)
                        showAddressActions = true
                    }
                    .semantics { contentDescription = "Address Actions" },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    if (showAddressActions) {
        ManageAddressesActionsSheet(
            isDiscovering = isDiscovering,
            progress = discoveryProgress,
            summary = discoverySummary,
            // Every way the sheet goes clears its scan summary (iOS 1751d20 .onDisappear).
            onGenerate = {
                generateNew()
                showAddressActions = false
                discoverySummary = null
            },
            onDiscover = { discoverAddresses() },
            onVisibility = {
                showAddressActions = false
                discoverySummary = null
                showVisibility = true
            },
            // The scan belongs to the screen, not the sheet: closing it keeps scanning.
            onDismiss = {
                showAddressActions = false
                discoverySummary = null
            },
        )
    }

    rowActionsTarget?.let { entry ->
        ManageAddressRowActionsSheet(
            title = entry.displayLabel,
            subtitle = shortSpendingAddress(entry.address),
            onDismiss = { rowActionsTarget = null },
        ) {
            ActionSheetRow(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.rename_address),
                subtitle = "Gives this address a label of your own.",
            ) {
                rowActionsTarget = null
                renameText = entry.label.orEmpty()
                renameTarget = entry
            }
            ActionSheetRow(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.copy_address),
                subtitle = "Puts the full address on the clipboard.",
            ) {
                clipboardManager.setText(AnnotatedString(entry.address))
                rowActionsTarget = null
                showAddressCopiedToast(context, entry.address)
            }
            ActionSheetRow(
                icon = Icons.Default.QrCode,
                title = stringResource(R.string.show_qr_code),
                subtitle = "Full screen, for scanning with another device.",
            ) {
                rowActionsTarget = null
                qrTarget = entry
            }
            // Hide straight from the row - same effect as unchecking it in Address Visibility.
            // Any address, funded too: it comes back from there.
            ActionSheetRow(
                icon = Icons.Default.VisibilityOff,
                title = "Hide Address",
                subtitle = "Removes it from this list. Re-enable it in Address Visibility.",
                tint = colors.warning,
            ) {
                rowActionsTarget = null
                viewModel.setManageAddressHidden(entry.index, true) { ok ->
                    if (ok) {
                        haptic(IosHaptic.SUCCESS)
                        IosToasts.show("Address hidden. Re-enable it in Address Visibility.")
                    } else {
                        IosToasts.show("This address can't be hidden.")
                    }
                }
            }
        }
    }

    qrTarget?.let { entry ->
        QrPageSheet(onDismiss = { qrTarget = null }, white = true) {
            SpendingAddressQrPage(address = entry.address, title = entry.displayTitle, onClose = { qrTarget = null })
        }
    }

    renameTarget?.let { entry ->
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_address), color = colors.textPrimary) },
            text = {
                Column {
                    Text("Give this address a label to help you recognize it.", color = colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    com.kachat.app.ui.theme.IosTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        // iOS 1751d20 TextField("Label"): that word only.
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setManageAddressLabel(entry.index, renameText)
                    renameTarget = null
                    viewModel.loadManageAddresses()
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel), color = colors.textSecondary)
                }
            },
        )
    }

    // Address Visibility, a sheet as on iOS. On closing, the rows read the stored hidden set and
    // reload (iOS `applyVisibilityChangesInstantly` then `loadEntries`): the view model's rows
    // already follow each toggle, so only the reload is left.
    if (showVisibility) {
        IosFullSheet(
            onDismissed = {
                showVisibility = false
                viewModel.loadManageAddresses()
            },
            swipeToDismiss = true,
        ) { close ->
            AddressVisibilitySheet(
                viewModel = viewModel,
                chattingAddress = chattingAddress,
                chattingBalanceSompi = chattingBalanceSompi,
                onDone = close,
            )
        }
    }
}

/** "%.8f KAS", as iOS's `formatKasExact`. */
private fun formatKasExact(sompi: Long): String =
    "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, sompi / 100_000_000.0)

/** iOS `SpendingAddressEntry.shortAddress`: "kaspa:qzy7abcd...d2fu" (14 + 6). */
private fun shortSpendingAddress(address: String): String =
    if (address.length <= 20) address else "${address.take(14)}...${address.takeLast(6)}"

/**
 * The half sheet behind Manage Addresses' "Address Actions" (iOS 1751d20 `addressActionsSheet`).
 * Discovery counts up here rather than closing on a spinner: a scan is a balance lookup per address
 * until the gap limit, easily half a minute. Closing the sheet keeps it scanning; the result then
 * arrives as a toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageAddressesActionsSheet(
    isDiscovering: Boolean,
    progress: com.kachat.app.services.SpendingAddressDiscovery.DiscoveryProgress?,
    summary: String?,
    onGenerate: () -> Unit,
    onDiscover: () -> Unit,
    onVisibility: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
        ) {
            // The sheet runs down behind the navigation bar; its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.address_actions), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (isDiscovering) {
                        Spacer(Modifier.height(8.dp))
                        com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary, modifier = Modifier.size(24.dp))
                        Text(
                            "Checking address #${progress?.checkingIndex ?: 0}",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            if ((progress?.foundCount ?: 0) == 0) "No addresses with a balance or domain yet"
                            else "${progress?.foundCount} found so far",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            "Checks the first thousand addresses whatever the gaps, then keeps going while it keeps finding.",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        // The scan keeps running with the sheet closed and reports in a toast.
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .manageAddressGlass(14)
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Close and Keep Scanning", color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                    } else {
                        ActionSheetRow(
                            icon = Icons.Default.AddCircleOutline,
                            title = stringResource(R.string.generate_new_spending_address),
                            subtitle = "Reveals the next unused address in this wallet.",
                            onClick = onGenerate,
                        )
                        ActionSheetRow(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.discover_addresses),
                            subtitle = stringResource(R.string.finds_addresses_balance_or_domain),
                            onClick = onDiscover,
                        )
                        ActionSheetRow(
                            icon = Icons.Default.Checklist,
                            title = "Address Visibility",
                            subtitle = "Check off every address you want on the list, in one sitting.",
                            onClick = onVisibility,
                        )
                        if (summary != null) {
                            Text(summary, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The half sheet behind one address row's ellipsis (iOS `addressRowActionsSheet`): its name and
 *  short address on top, then the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageAddressRowActionsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(subtitle, color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
                    }
                    content()
                }
            }
        }
    }
}

/**
 * A spending address's card (iOS `addressRow`): its label (and a quantum row's shield), the short
 * address, the balance, Used / Checking / Unused and "Contains domain", and the ellipsis that opens
 * its actions. Tapping the card opens the address's page.
 */
@Composable
private fun ManageAddressRow(
    entry: WalletService.SpendingAddressEntry,
    showsDomainTag: Boolean,
    quantumSpent: Boolean,
    /** The last read of the balances failed: a row with no known figure shows a dash, not 0. */
    balancesUnread: Boolean,
    onClick: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .manageAddressGlass(18)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.displayLabel, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (entry.isQuantum) QuantumShield(spent = quantumSpent, funded = entry.balanceSompi > 0L)
            }
            Text(
                shortSpendingAddress(entry.address),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                if (balancesUnread && entry.balanceSompi == 0L) "--" else formatKasExact(entry.balanceSompi),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Never a guess: "Used" once a balance or history says so, "Unused" only when this
                // load's live check confirmed it, else "Checking" - claiming "Unused" could invite
                // address reuse.
                val (text, color) = when {
                    entry.everUsed || entry.balanceSompi > 0 -> "Used" to colors.warning
                    entry.liveChecked -> "Unused" to colors.success
                    else -> "Checking" to colors.textSecondary
                }
                Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (showsDomainTag) ManageAddressDomainTag()
            }
        }
        // iOS: a bold "ellipsis" turned upright, secondary, in a 24 frame.
        Box(
            modifier = Modifier.size(24.dp).clickable(onClick = onActions),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MoreVert, "Address actions", tint = colors.textSecondary, modifier = Modifier.requiredSize(28.dp))
        }
    }
}

/** iOS `ContainsDomainTag`: the accent capsule on an address that holds a domain. */
@Composable
private fun ManageAddressDomainTag() {
    Text(
        text = "Contains domain",
        color = KaspaTeal,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(KaspaTeal.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The quantum (KIP-22) state of a row (iOS `quantumShield`): a shield while the key has never been
 * on chain; a slashed shield once the address spent - orange while funds sit on it again, grey
 * when it is empty.
 */
@Composable
private fun QuantumShield(spent: Boolean, funded: Boolean) {
    val colors = LocalAppColors.current
    if (spent) {
        Icon(
            Icons.Filled.RemoveModerator,
            contentDescription = stringResource(R.string.key_revealed_by_a_spend),
            tint = if (funded) colors.warning else colors.textSecondary,
            modifier = Modifier.size(11.dp),
        )
    } else {
        Icon(
            Icons.Filled.Shield,
            contentDescription = stringResource(R.string.quantum_safe),
            tint = colors.success,
            modifier = Modifier.size(11.dp),
        )
    }
}

/**
 * The chatting address's card, first in the list (iOS `chattingAddressRow`): "Chatting Address"
 * with its icon, the short address, its balance and "Used", and a chevron - it opens its own page.
 */
@Composable
private fun ManageChattingAddressRow(address: String, balanceSompi: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .manageAddressGlass(18)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.chatting_address), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.Forum, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
            }
            Text(
                "${address.take(14)}...${address.takeLast(6)}",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(formatKasExact(balanceSompi), color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Used", color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp).offset(x = 6.dp))
    }
}

/**
 * iOS ManageAddressesView `glassBackground(cornerRadius:)` behind the address cards:
 * `.regularMaterial` (#202020 on the black list), a 0.8 white hairline at 18% and a soft shadow.
 */
@Composable
private fun Modifier.manageAddressGlass(cornerRadius: Int): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(cornerRadius.dp)
    val fill = if (colors.isDark) Color(0xFF202020) else colors.surface
    return this
        .iosShadow(cornerRadius.dp, Color.Black.copy(alpha = 0.12f), 10.dp, 5.dp)
        .clip(shape)
        .background(fill)
        .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
}

/**
 * A spending address's QR code, full screen and white (iOS `SpendingAddressQRView`): the code in a
 * 3pt accent frame, the whole address under it and "Tap anywhere to copy"; the address's number in
 * the bar with Close leading. A tap anywhere copies the address.
 */
@Composable
private fun SpendingAddressQrPage(address: String, title: String, onClose: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clipboardManager.setText(AnnotatedString(address))
                haptic(IosHaptic.SUCCESS)
                showAddressCopiedToast(context, address, haptic = false)
            },
    ) {
        Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp)) {
            // Light glass with black text over the white page, in dark mode too.
            CompositionLocalProvider(LocalAppColors provides com.kachat.app.ui.theme.LightAppColors) {
                SheetBarButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.align(Alignment.CenterStart))
            }
            Text(title, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    // iOS strokes 3pt on the edge and clips it to the shape: 1.5 shows.
                    .border(1.5.dp, KaspaTeal, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Image(
                    painter = rememberQrBitmapPainter(address),
                    contentDescription = stringResource(R.string.qr_code),
                    modifier = Modifier.size(240.dp),
                )
            }
            Text(
                address,
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(stringResource(R.string.tap_anywhere_to_copy), color = Color.Black.copy(alpha = 0.4f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

// MARK: - Address Visibility

/**
 * Address Visibility (iOS 1751d20 `SpendingAddressVisibilityView`), the sheet from Address
 * Actions: a square per chain - Quantum Addresses and Non-Quantum Addresses - with how many of its
 * addresses Manage Addresses shows and what they hold, then that chain's checklist. Hide All
 * (confirmed) covers both chains; Done closes. The Chatting Address is the legacy list's
 * always-shown #0.
 */
@Composable
private fun AddressVisibilitySheet(
    viewModel: WalletViewModel,
    chattingAddress: String?,
    chattingBalanceSompi: Long,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val loading by viewModel.manageAddressesLoading.collectAsState()
    var showHideAllConfirm by remember { mutableStateOf(false) }
    // The chain whose square was tapped (true = quantum), null on the squares.
    var openedChain by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { viewModel.loadManageAddresses() }

    val chain = openedChain
    if (chain != null) {
        BackHandler { openedChain = null }
        AddressVisibilityChainList(
            viewModel = viewModel,
            quantum = chain,
            chattingAddress = chattingAddress,
            chattingBalanceSompi = chattingBalanceSompi,
            onBack = { openedChain = null },
        )
    } else {
        BackHandler(onBack = onDone)
        Column(Modifier.fillMaxSize()) {
            SheetNavBar(
                title = "Address Visibility",
                leading = { SheetBarButton(stringResource(R.string.hide_all), onClick = { showHideAllConfirm = true }) },
                trailing = { SheetBarButton(stringResource(R.string.done), onClick = onDone, emphasized = true) },
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                items(2) { i ->
                    val quantum = i == 0
                    val rows = addresses.filter { it.isQuantum == quantum }
                    // The Chatting Address is the legacy list's always-shown #0.
                    val chattingRows = if (quantum) 0 else 1
                    // Hidden addresses count too: their Kaspa is still in the account's total.
                    val balance = rows.sumOf { it.balanceSompi } + if (quantum) 0L else chattingBalanceSompi
                    AddressVisibilityChainCard(
                        quantum = quantum,
                        shown = rows.count { !it.hidden } + chattingRows,
                        total = rows.size + chattingRows,
                        balanceSompi = balance,
                        loading = loading && addresses.isEmpty(),
                        onClick = { openedChain = quantum },
                    )
                }
            }
        }
    }

    if (showHideAllConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showHideAllConfirm = false },
            title = { Text(stringResource(R.string.hide_all_addresses_title), color = colors.textPrimary) },
            text = { Text(stringResource(R.string.hide_all_addresses_message), color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showHideAllConfirm = false
                    // Every revealed address hidden, both chains: the list starts from nothing.
                    viewModel.hideAllSpendingAddresses()
                }) {
                    Text(stringResource(R.string.hide_all), color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideAllConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textSecondary)
                }
            },
        )
    }
}

/** A square tile (iOS `chainCard`): the chain's glyph and name, how many of its addresses Manage
 *  Addresses shows, and what they hold. */
@Composable
private fun AddressVisibilityChainCard(
    quantum: Boolean,
    shown: Int,
    total: Int,
    balanceSompi: Long,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(if (colors.isDark) Color(0xFF202020) else colors.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (quantum) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(26.dp))
            } else {
                // "circle.grid.2x2.fill": four dots in a square.
                Canvas(Modifier.size(26.dp)) {
                    val r = size.minDimension * 0.2f
                    for (cx in listOf(0.27f, 0.73f)) for (cy in listOf(0.27f, 0.73f)) {
                        drawCircle(KaspaTeal, radius = r, center = Offset(size.width * cx, size.height * cy))
                    }
                }
            }
            Text(
                stringResource(if (quantum) R.string.quantum_addresses else R.string.non_quantum_addresses),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (loading) {
                com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text(
                    stringResource(R.string.addresses_shown_count, shown, total),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                // iOS `KaspaUnit.amount`: exact, trailing zeros dropped.
                Text(
                    "${KaspaUnit.plain(balanceSompi)} ${KaspaUnit.symbol}",
                    color = if (balanceSompi > 0) KaspaTeal else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One chain's checklist (iOS 1751d20 `AddressVisibilityChainList`): every address on that chain,
 * 50 to a page from the chain's own start, checked = shown on Manage Addresses. The right arrow
 * never runs out: pages past the revealed set derive addresses on the fly, and checking one reveals
 * just it. Numbered as people see them ([WalletManager.addressNumber]); the legacy chain's first
 * page also holds #0, the Chatting Address, checked and fixed.
 */
@Composable
private fun AddressVisibilityChainList(
    viewModel: WalletViewModel,
    quantum: Boolean,
    chattingAddress: String?,
    chattingBalanceSompi: Long,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    var page by remember { mutableIntStateOf(0) }
    val pageSize = 50
    val byIndex = remember(addresses) { addresses.associateBy { it.index } }
    val chainBase = if (quantum) WalletManager.QUANTUM_INDEX_BASE else 0
    val start = chainBase + page * pageSize
    val end = start + pageSize - 1
    // Addresses for the indices the loaded rows don't cover, derived off the main thread, once per
    // page (one seed derivation for the whole range).
    var derivedAddresses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var derivedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(page, byIndex.keys) {
        val needed = (start..end).filter { it !in byIndex }
        if (needed.isEmpty()) {
            derivedAddresses = emptyMap()
            derivedPage = page
            return@LaunchedEffect
        }
        derivedPage = null
        derivedAddresses = withContext(Dispatchers.Default) { viewModel.spendingAddressesAt(needed) }
        derivedPage = page
    }
    val pageEntries = remember(byIndex, page, derivedAddresses, derivedPage) {
        (start..end).mapNotNull { index ->
            byIndex[index] ?: derivedAddresses[index]?.takeIf { it.isNotEmpty() }?.let { address ->
                WalletService.SpendingAddressEntry(
                    index = index,
                    address = address,
                    balanceSompi = 0L,
                    everUsed = false,
                    isCurrent = false,
                    hidden = true,
                    label = null,
                )
            }
        }
    }
    // Whether each zero-balance address was ever used, looked up once per address for the life of
    // this sheet. A failed lookup stores nothing: the row keeps "…", never a possibly-wrong
    // "Unused". Paced, so a page is a trickle of lookups rather than fifty at once.
    val usedByAddress = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(pageEntries) {
        for (entry in pageEntries) {
            if (entry.balanceSompi > 0 || entry.address.isEmpty() || entry.address in usedByAddress) continue
            viewModel.spendingAddressUsedState(entry.address)?.let { usedByAddress[entry.address] = it }
            delay(60)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }

    fun toggle(entry: WalletService.SpendingAddressEntry) {
        // Per chain: a quantum slot must not hide that a legacy row lies past the legacy bound.
        val revealedMax = addresses.filter { it.isQuantum == entry.isQuantum }.maxOfOrNull { it.index }
            ?: (if (entry.isQuantum) WalletManager.QUANTUM_INDEX_BASE - 1 else -1)
        if (entry.index > revealedMax) {
            // A derived future index: reveal exactly this one (those in between stay hidden).
            viewModel.revealSpendingAddress(entry.index)
        } else {
            viewModel.setManageAddressHidden(entry.index, !entry.hidden)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SheetNavBar(
            title = stringResource(if (quantum) R.string.quantum_addresses else R.string.non_quantum_addresses),
            leading = { SheetBarBackButton(onClick = onBack) },
        )
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (derivedPage == page && pageEntries.isEmpty()) {
                item(key = "failed") {
                    Text(
                        "These addresses couldn't be derived right now. Go back a page or reopen this screen to retry.",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            val chatting = chattingAddress
            if (!quantum && page == 0 && !chatting.isNullOrEmpty()) {
                item(key = "chatting") {
                    // #0: always shown on Manage Addresses, so it's checked and tapping does nothing.
                    VisibilityRow(
                        number = "#0",
                        quantum = false,
                        label = stringResource(R.string.chatting_address),
                        address = chatting,
                        visible = true,
                        fixed = true,
                        trailing = {
                            if (chattingBalanceSompi > 0) VisibilityAmount(chattingBalanceSompi)
                        },
                        onClick = null,
                    )
                }
            }
            items(pageEntries, key = { it.index }) { entry ->
                VisibilityRow(
                    number = "#${WalletManager.addressNumber(entry.index)}",
                    quantum = entry.isQuantum,
                    label = entry.label?.trim()?.takeIf { it.isNotEmpty() },
                    address = entry.address,
                    // Every row toggles both ways - funded ones too (Hide All starts the list over).
                    visible = !entry.hidden,
                    fixed = false,
                    trailing = {
                        val used = usedByAddress[entry.address]
                        when {
                            entry.balanceSompi > 0 -> VisibilityAmount(entry.balanceSompi)
                            used != null -> Text(
                                if (used) "Used" else "Unused",
                                color = if (used) colors.warning else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            else -> Text("…", color = colors.textSecondary, fontSize = 11.sp)
                        }
                    },
                    onClick = { toggle(entry) },
                )
            }
        }
        // The pager: "#first - #last" with arrows; the right arrow never runs out. The legacy
        // chain's first page also holds #0, the Chatting Address.
        val lower = if (!quantum && page == 0) 0 else WalletManager.addressNumber(start)
        val upper = WalletManager.addressNumber(end)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (page > 0) page -= 1 }, enabled = page > 0) {
                Icon(
                    Icons.Default.ChevronLeft,
                    "Previous page",
                    tint = if (page > 0) KaspaTeal else colors.textSecondary.copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "#$lower - #$upper",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { page += 1 }) {
                Icon(Icons.Default.ChevronRight, "Next page", tint = KaspaTeal)
            }
        }
    }
}

/** A checklist row (iOS `row` / `chattingRow`): the check, the number with a quantum row's outline
 *  shield and its label, the short address, and what it holds or whether it was used. */
@Composable
private fun VisibilityRow(
    number: String,
    quantum: Boolean,
    label: String?,
    address: String,
    visible: Boolean,
    fixed: Boolean,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)?,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (visible) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (visible) "Visible" else "Hidden",
                tint = if (visible) KaspaTeal else colors.textSecondary,
                modifier = Modifier.size(22.dp).alpha(if (fixed) 0.45f else 1f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        number,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                    if (quantum) {
                        // iOS: "shield" (outline) at .caption2, secondary.
                        Icon(
                            Icons.Outlined.OutlinedShield,
                            contentDescription = stringResource(R.string.quantum_safe),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                    if (label != null) {
                        Text(label, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    if (address.length > 22) "${address.take(16)}…${address.takeLast(6)}" else address,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            trailing()
        }
        Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(0.5.dp).background(colors.textSecondary.copy(alpha = 0.2f)))
    }
}

@Composable
private fun VisibilityAmount(sompi: Long) {
    Text(
        "%.4f ${KaspaUnit.symbol}".format(java.util.Locale.US, sompi / 100_000_000.0),
        color = KaspaTeal,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

// MARK: - A spending address's page

/**
 * One spending address's page (iOS 1751d20 `SpendingAddressTransactionHistoryView`): its balance,
 * then History, UTXOs and Domains; the options button in the bar (iOS da59726); Receive
 * and Send at the foot. Send is the Wallet's Send Kaspa with this address as the fixed From
 * (`lockedSource`); Compound UTXOs (under UTXOs) merges this address's coins. A transaction opens
 * the explorer outright (KaPay asks first because of Add to Portfolio, which KaPlay doesn't have).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAddressHistoryScreen(
    index: Int,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    /** A .kachat name tapped under Domains > .kachat; null opens its detail
     *  ([KachatLiveNameDetailScreen]) in this page's place. */
    onOpenKachatName: ((NameInfo) -> Unit)? = null,
    /** The row to show while the shared list hasn't got this index (opened from the bell). */
    fallbackEntry: WalletService.SpendingAddressEntry? = null,
    /** Opened as the root of a sheet: Done closes it, top left, in place of the back arrow. */
    closesWithDone: Boolean = false,
) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val entry = remember(addresses, index, fallbackEntry) {
        addresses.firstOrNull { it.index == index } ?: fallbackEntry?.takeIf { it.index == index }
    }
    val address = entry?.address.orEmpty()
    // This address's own lists: empty with spinners until its own load lands, never another
    // page's (iOS 1751d20 SpendingAddressTransactionHistoryView's @State).
    val allAddressLists by viewModel.addressPageLists.collectAsState()
    val pageLists = allAddressLists[address] ?: WalletViewModel.AddressPageLists()
    val txHistory = pageLists.txHistory
    val isLoadingTxHistory = pageLists.txHistoryLoading
    val txHistoryFailed = pageLists.txHistoryFailed
    val utxos = pageLists.utxos
    val isLoadingUtxos = pageLists.utxosLoading
    val utxosLoadError = pageLists.utxosError
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val biometricSpendingKeyEnabled by viewModel.biometricSpendingKeyEnabled.collectAsState()
    val chattingAddress by viewModel.address.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var showCompound by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    // The "Address Actions" sheet, and the public-key screen it can open.
    var showAddressActions by remember { mutableStateOf(false) }
    var showPublicKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var renamingUtxoKey by remember { mutableStateOf<String?>(null) }
    var renameUtxoText by remember { mutableStateOf("") }
    var openedDomainService by remember { mutableStateOf<NameServiceTLD?>(null) }
    var openedKachat by remember { mutableStateOf<NameInfo?>(null) }

    // A page opened before the list loaded (or after an account switch) finds its row once it has.
    LaunchedEffect(Unit) { if (addresses.isEmpty()) viewModel.loadManageAddresses() }
    LaunchedEffect(address) {
        if (address.isEmpty()) return@LaunchedEffect
        utxoLabels = viewModel.getSpendingUtxoLabels(address)
        // Starts empty with both spinners, then loads, as iOS's page does.
        viewModel.openAddressPageLists(address)
    }

    fun reload() {
        if (address.isEmpty()) return
        viewModel.loadSpendingAddressTxHistory(address)
        viewModel.loadSpendingAddressUtxos(address)
        viewModel.loadManageAddresses()
        // the Wallet tab's spending total too (iOS da59726 `HotWalletBalance.refresh(force:)`)
        viewModel.refreshSpendingAddress()
    }

    // A .kachat name opened from Domains > .kachat: its live detail, which knows which of your
    // addresses holds it (iOS `KachatListingDetailView`).
    openedKachat?.let { info ->
        BackHandler { openedKachat = null }
        // Its Fund opens the funding sheet with this wallet (iOS ZeroBalanceFundingSheetView).
        androidx.compose.runtime.CompositionLocalProvider(LocalKachatWalletViewModel provides viewModel) {
            KachatLiveNameDetailScreen(initial = info, onBack = { openedKachat = null })
        }
        return
    }
    // A name service opened from the Domains tab: its names, in this page's place.
    openedDomainService?.let { tld ->
        AddressDomainServiceScreen(
            address = address,
            tld = tld,
            onBack = { openedDomainService = null },
            onOpenKachat = onOpenKachatName ?: { openedKachat = it },
        )
        return
    }

    // Send and Compound are sheets only Cancel closes, as iOS presents them.
    val sendFrom = chattingAddress
    if (showSend && entry != null && !sendFrom.isNullOrEmpty()) {
        IosFullSheet(onDismissed = { showSend = false; reload() }, swipeToDismiss = false) { close ->
            SpendingAddressSendFlow(
                fromAddress = sendFrom,
                balanceSompi = chattingBalanceSompi,
                title = "Send Kaspa",
                viewModel = viewModel,
                onDone = close,
                presentedAsSheet = true,
                // Send from this address's own page: it is the From, with no other to pick.
                lockedSource = SendSource.Spending(entry.index, entry.address, entry.displayLabel),
            )
        }
    }
    if (showCompound && entry != null) {
        IosFullSheet(onDismissed = { showCompound = false; reload() }, swipeToDismiss = false) { close ->
            SpendingAddressSendFlow(
                fromAddress = entry.address,
                balanceSompi = entry.balanceSompi,
                title = stringResource(R.string.compound_utxos),
                viewModel = viewModel,
                onDone = close,
                spendingIndex = entry.index,
                isCompoundMode = true,
                presentedAsSheet = true,
            )
        }
    }

    Scaffold(
        containerColor = colors.background,
        // Runs on under Receive / Send and the dock, as iOS's list runs on under its bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        entry?.displayLabel ?: WalletManager.spendingAddressTitle(index),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (closesWithDone) {
                        SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    } else {
                        SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                // The one options button of every address page (iOS da59726).
                actions = { AddressOptionsButton(onClick = { showAddressActions = true }, enabled = address.isNotEmpty()) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            AddressReceiveSendBar(
                receiveEnabled = address.isNotEmpty(),
                sendEnabled = (entry?.balanceSompi ?: 0L) > 0L,
                onReceive = { showQr = true },
                onSend = { showSend = true },
            )
        },
    ) { padding ->
        // What Receive / Send and the dock cover: the lists scroll clear of it.
        val barInset = padding.calculateBottomPadding()
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(entry?.balanceSompi ?: 0L)
            UnderlineTabBar(
                titles = listOf(
                    stringResource(R.string.address_tab_history),
                    stringResource(R.string.utxos),
                    stringResource(R.string.address_tab_domains),
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(top = 4.dp),
            )
            when (selectedTab) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { if (address.isNotEmpty()) viewModel.loadSpendingAddressTxHistory(address) },
                    onTransactionClick = { uriHandler.openUri(kaspaExplorer.txUrl(it.txId)) },
                    bottomPadding = barInset,
                )
                1 -> AddressUtxoList(
                    utxos = utxos,
                    isLoading = isLoadingUtxos,
                    loadError = utxosLoadError,
                    labels = utxoLabels,
                    onRetry = { if (address.isNotEmpty()) viewModel.loadSpendingAddressUtxos(address) },
                    onCompound = { showCompound = true },
                    onRename = { key ->
                        renamingUtxoKey = key
                        renameUtxoText = utxoLabels[key].orEmpty()
                    },
                    bottomPadding = barInset,
                    compoundFooter = "Combines this address's UTXOs to reduce the inputs a future send needs. A single transaction can only merge so many at once, so if this address has a very large number, tap Compound again after it confirms to keep reducing.",
                )
                else -> AddressDomainsTab(address = address, onOpenService = { openedDomainService = it }, bottomPadding = barInset)
            }
        }
    }

    if (showQr && address.isNotEmpty()) {
        QrPageSheet(onDismiss = { showQr = false }, white = true) {
            SpendingAddressQrPage(
                address = address,
                title = entry?.displayTitle ?: WalletManager.spendingAddressTitle(index),
                onClose = { showQr = false },
            )
        }
    }
    if (showAddressActions) {
        IdentityAddressActionsSheet(
            // A quantum address keeps its key hidden until it spends: that's the point of it.
            showsPublicKey = !WalletManager.isQuantumIndex(index),
            onPrivateKey = {
                showAddressActions = false
                // Settings' "Biometrics for Address Private Keys".
                if (biometricSpendingKeyEnabled) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to view this address's private key",
                        onSuccess = { showPrivateKey = true },
                    )
                } else {
                    showPrivateKey = true
                }
            },
            onPublicKey = {
                showAddressActions = false
                showPublicKey = true
            },
            onExplorer = {
                showAddressActions = false
                if (address.isNotEmpty()) uriHandler.openUri(kaspaExplorer.addressUrl(address))
            },
            onDismiss = { showAddressActions = false },
        )
    }
    if (showPublicKey) {
        IdentityAddressPublicKeyOverlay(
            address = address,
            onDismiss = { showPublicKey = false },
            note = "This is the public half of this address. It cannot spend anything.",
        )
    }
    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(
            privateKeyHex = viewModel.getSpendingPrivateKeyHex(index),
            onDismiss = { showPrivateKey = false },
            title = entry?.displayLabel ?: WalletManager.spendingAddressTitle(index),
        )
    }
    renamingUtxoKey?.let { key ->
        RenameUtxoDialog(
            text = renameUtxoText,
            onTextChange = { renameUtxoText = it },
            onSave = {
                viewModel.setSpendingUtxoLabel(address, key, renameUtxoText)
                utxoLabels = viewModel.getSpendingUtxoLabels(address)
                renamingUtxoKey = null
            },
            onDismiss = { renamingUtxoKey = null },
        )
    }
}

/** iOS's "Rename UTXO" alert: a Name field, Save and Cancel. */
@Composable
private fun RenameUtxoDialog(text: String, onTextChange: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_utxo), color = colors.textPrimary) },
        text = {
            com.kachat.app.ui.theme.IosTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.textSecondary) }
        },
    )
}

// MARK: - The Chatting Address's page

/**
 * The chatting address's own page, from Manage Addresses' Chatting Address row (iOS 1751d20
 * `ChattingAddressManageView`): its balance, Address Actions (View Private Key, View Public Key,
 * View in Explorer), then History, UTXOs (Compound UTXOs above them while a send could merge more
 * than one coin) and Domains, with Receive and Send at the foot. Send leaves from this address
 * (`lockedSource: .chatting`). The Wallet tab's old body ([IdentityAddressDetailScreen]) is this
 * page's ancestor; its pieces are reused here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChattingAddressManageScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    /** A .kachat name tapped under Domains > .kachat; null opens its detail
     *  ([KachatLiveNameDetailScreen]) in this page's place. */
    onOpenKachatName: ((NameInfo) -> Unit)? = null,
    /** Opened as the root of a sheet (the bell's History): Done closes it, top left. */
    closesWithDone: Boolean = false,
) {
    val colors = LocalAppColors.current
    val address by viewModel.address.collectAsState()
    // The live balance only says when the coins moved (the reload below); what the page shows
    // and sends with is its own coins' sum.
    val liveBalanceSompi by viewModel.balanceSompi.collectAsState()
    // This address's own lists: empty with spinners until its own load lands, never another
    // page's (iOS 1751d20 ChattingAddressManageView's @State).
    val allAddressLists by viewModel.addressPageLists.collectAsState()
    val pageLists = allAddressLists[address.orEmpty()] ?: WalletViewModel.AddressPageLists()
    val txHistory = pageLists.txHistory
    val isLoadingTxHistory = pageLists.txHistoryLoading
    val txHistoryFailed = pageLists.txHistoryFailed
    val utxos = pageLists.utxos
    val isLoadingUtxos = pageLists.utxosLoading
    val utxosLoadError = pageLists.utxosError
    // iOS 1751d20 `balanceSompi`: the sum of the loaded UTXOs, for the header, Send and the
    // send's available amount.
    val balanceSompi = remember(utxos) { utxos.sumOf { it.amountSompi } }
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val biometricSpendingKeyEnabled by viewModel.biometricSpendingKeyEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var showCompound by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showAddressActions by remember { mutableStateOf(false) }
    var showPublicKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var renamingUtxoKey by remember { mutableStateOf<String?>(null) }
    var renameUtxoText by remember { mutableStateOf("") }
    var openedDomainService by remember { mutableStateOf<NameServiceTLD?>(null) }
    var openedKachat by remember { mutableStateOf<NameInfo?>(null) }

    LaunchedEffect(address) {
        val addr = address
        if (addr.isNullOrEmpty()) return@LaunchedEffect
        utxoLabels = viewModel.getSpendingUtxoLabels(addr)
        // Starts empty with both spinners, then loads, as iOS's page does.
        viewModel.openAddressPageLists(addr)
    }
    // The coins moved (a payment in, a move or send out): the UTXOs and History follow the live
    // balance; the indexer can be a moment behind the node, so History is asked again shortly.
    var balanceSeen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(liveBalanceSompi) {
        val addr = address
        val previous = balanceSeen
        balanceSeen = liveBalanceSompi
        if (previous == null || previous == liveBalanceSompi || addr.isNullOrEmpty()) return@LaunchedEffect
        viewModel.loadSpendingAddressUtxos(addr)
        viewModel.loadSpendingAddressTxHistory(addr)
        delay(4_000)
        viewModel.loadSpendingAddressTxHistory(addr)
    }
    // Only coins a send could spend (not unripe mining rewards, unconfirmed or just spent): Compound
    // is offered while there is more than one (iOS `withdrawalSpendable(utxos).count > 1`).
    var spendableCoinCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(utxos, address) {
        val addr = address?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        spendableCoinCount = try {
            viewModel.fetchUtxosForCoinControl(addr).size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
    }

    fun reload() {
        val addr = address
        if (addr.isNullOrEmpty()) return
        viewModel.loadSpendingAddressTxHistory(addr)
        viewModel.loadSpendingAddressUtxos(addr)
    }

    val pageAddress = address.orEmpty()
    openedKachat?.let { info ->
        BackHandler { openedKachat = null }
        // Its Fund opens the funding sheet with this wallet (iOS ZeroBalanceFundingSheetView).
        androidx.compose.runtime.CompositionLocalProvider(LocalKachatWalletViewModel provides viewModel) {
            KachatLiveNameDetailScreen(initial = info, onBack = { openedKachat = null })
        }
        return
    }
    openedDomainService?.let { tld ->
        AddressDomainServiceScreen(
            address = pageAddress,
            tld = tld,
            onBack = { openedDomainService = null },
            onOpenKachat = onOpenKachatName ?: { openedKachat = it },
        )
        return
    }

    if (showSend && pageAddress.isNotEmpty()) {
        IosFullSheet(onDismissed = { showSend = false; reload() }, swipeToDismiss = false) { close ->
            SpendingAddressSendFlow(
                fromAddress = pageAddress,
                balanceSompi = balanceSompi,
                title = "Send Kaspa",
                viewModel = viewModel,
                onDone = close,
                presentedAsSheet = true,
                lockedSource = SendSource.Chatting,
            )
        }
    }
    if (showCompound && pageAddress.isNotEmpty()) {
        IosFullSheet(onDismissed = { showCompound = false; reload() }, swipeToDismiss = false) { close ->
            SpendingAddressSendFlow(
                fromAddress = pageAddress,
                balanceSompi = balanceSompi,
                title = "Send Kaspa",
                viewModel = viewModel,
                isCompoundMode = true,
                onDone = close,
                presentedAsSheet = true,
            )
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chatting_address), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (closesWithDone) {
                        SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    } else {
                        SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = { AddressOptionsButton(onClick = { showAddressActions = true }, enabled = pageAddress.isNotEmpty()) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            AddressReceiveSendBar(
                receiveEnabled = pageAddress.isNotEmpty(),
                sendEnabled = balanceSompi > 0L,
                onReceive = { showQr = true },
                onSend = { showSend = true },
            )
        },
    ) { padding ->
        val barInset = padding.calculateBottomPadding()
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(balanceSompi)
            Spacer(Modifier.height(8.dp))
            // Right above what it switches.
            UnderlineTabBar(
                titles = listOf(
                    stringResource(R.string.address_tab_history),
                    stringResource(R.string.utxos),
                    stringResource(R.string.address_tab_domains),
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            when (selectedTab) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { if (pageAddress.isNotEmpty()) viewModel.loadSpendingAddressTxHistory(pageAddress) },
                    // Opens the explorer outright: KaPay asks first because of Add to Portfolio.
                    onTransactionClick = { uriHandler.openUri(kaspaExplorer.txUrl(it.txId)) },
                    bottomPadding = barInset,
                )
                1 -> AddressUtxoList(
                    utxos = utxos,
                    isLoading = isLoadingUtxos,
                    loadError = utxosLoadError,
                    labels = utxoLabels,
                    onRetry = { if (pageAddress.isNotEmpty()) viewModel.loadSpendingAddressUtxos(pageAddress) },
                    onCompound = { showCompound = true },
                    onRename = { key ->
                        renamingUtxoKey = key
                        renameUtxoText = utxoLabels[key].orEmpty()
                    },
                    bottomPadding = barInset,
                    showsCompound = spendableCoinCount > 1,
                )
                else -> AddressDomainsTab(address = pageAddress, onOpenService = { openedDomainService = it }, bottomPadding = barInset)
            }
        }
    }

    if (showQr && pageAddress.isNotEmpty()) {
        // In a sheet with Close leading, as iOS presents `ChattingAddressQRView` here.
        QrPageSheet(onDismiss = { showQr = false }, white = true) {
            ChattingAddressQrPage(
                address = pageAddress,
                balanceSompi = balanceSompi,
                dismiss = QrPageDismiss.CLOSE,
                onDismiss = { showQr = false },
            )
        }
    }
    if (showAddressActions) {
        IdentityAddressActionsSheet(
            onPrivateKey = {
                showAddressActions = false
                // Settings' "Biometrics for Address Private Keys", which this key is one of
                // (KaPlay's choice; KaPay asks with the seed phrase's switch).
                if (biometricSpendingKeyEnabled) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to view this address's private key",
                        onSuccess = { showPrivateKey = true },
                    )
                } else {
                    showPrivateKey = true
                }
            },
            onPublicKey = {
                showAddressActions = false
                showPublicKey = true
            },
            onExplorer = {
                showAddressActions = false
                if (pageAddress.isNotEmpty()) uriHandler.openUri(kaspaExplorer.addressUrl(pageAddress))
            },
            onDismiss = { showAddressActions = false },
        )
    }
    if (showPublicKey) {
        IdentityAddressPublicKeyOverlay(address = pageAddress, onDismiss = { showPublicKey = false })
    }
    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(privateKeyHex = viewModel.getPrivateKeyHex(), onDismiss = { showPrivateKey = false }, title = "Chatting Address")
    }
    renamingUtxoKey?.let { key ->
        RenameUtxoDialog(
            text = renameUtxoText,
            onTextChange = { renameUtxoText = it },
            onSave = {
                if (pageAddress.isNotEmpty()) {
                    viewModel.setSpendingUtxoLabel(pageAddress, key, renameUtxoText)
                    utxoLabels = viewModel.getSpendingUtxoLabels(pageAddress)
                }
                renamingUtxoKey = null
            },
            onDismiss = { renamingUtxoKey = null },
        )
    }
}

// MARK: - An address's Domains tab

/**
 * One address's names on every service, for its Domains tab (iOS 1751d20 `AddressDomainsTab`,
 * KaPay Android's). Held here rather than in the tab so a service's own screen, opened over the
 * tab, reads the same answers: both are composed under the address page's back-stack entry.
 */
@HiltViewModel
class AddressDomainsViewModel @Inject constructor(
    private val nameServices: NameServicesClient,
    private val knsService: KnsService,
    /** Only built where the .kachat registry is live ([KachatNamesService.isLaunched]). */
    val kachatRegistry: dagger.Lazy<KachatNamesRegistry>,
    val kachatService: dagger.Lazy<KachatNamesService>,
) : ViewModel() {
    data class State(
        val address: String = "",
        /** A service's names; a service missing here is still on the way, or failed. */
        val names: Map<NameServiceTLD, List<String>> = emptyMap(),
        val failed: Set<NameServiceTLD> = emptySet(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Every service at once, for this address only (Your Domains' own lists are left alone). */
    suspend fun load(address: String) {
        val normalized = address.trim().lowercase()
        if (_state.value.address != normalized) _state.value = State(address = normalized)
        if (normalized.isEmpty()) return
        coroutineScope {
            launch {
                val kachat = try {
                    nameServices.kachatNames(listOf(normalized))[normalized].orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                set(normalized, NameServiceTLD.KACHAT, kachat.map { it.display })
            }
            launch {
                val byService = nameServices.ownedNamesByService(normalized)
                for (tld in listOf(NameServiceTLD.K, NameServiceTLD.KASPA)) {
                    val list = byService[tld]
                    if (list != null) set(normalized, tld, list.map { it.display }) else fail(normalized, tld)
                }
            }
            launch {
                val kns = knsService.getOwnedDomainsOrNull(normalized)
                if (kns != null) set(normalized, NameServiceTLD.KAS, kns.mapNotNull { it.asset }) else fail(normalized, NameServiceTLD.KAS)
            }
        }
    }

    private fun set(address: String, tld: NameServiceTLD, list: List<String>) {
        val current = _state.value
        if (current.address != address) return
        _state.value = current.copy(names = current.names + (tld to list), failed = current.failed - tld)
    }

    private fun fail(address: String, tld: NameServiceTLD) {
        val current = _state.value
        if (current.address != address) return
        _state.value = current.copy(failed = current.failed + tld)
    }

    companion object {
        /** The order the cards are listed in. */
        val order = listOf(NameServiceTLD.KACHAT, NameServiceTLD.K, NameServiceTLD.KASPA, NameServiceTLD.KAS)
    }
}

/**
 * The Domains tab of an address's page (the chatting address, a spending address): one card per
 * name service - .kachat, .k, .kaspa, .kas - with how many names this address holds there, or a
 * warning when that service couldn't be reached. A card opens [AddressDomainServiceScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressDomainsTab(
    address: String,
    onOpenService: (NameServiceTLD) -> Unit,
    /** Extra room at the scroll's end for what floats over the page's foot (its bar, the dock). */
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    vm: AddressDomainsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(address) { vm.load(address) }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing) {
            vm.load(address)
            pullState.endRefresh()
        }
    }
    val current = state.takeIf { it.address == address.trim().lowercase() } ?: AddressDomainsViewModel.State()
    Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pullState.nestedScrollConnection)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AddressDomainsViewModel.order.forEach { tld ->
                AddressDomainServiceCard(
                    tld = tld,
                    count = current.names[tld]?.size,
                    failed = tld in current.failed,
                    onClick = { onOpenService(tld) },
                )
            }
        }
        if (pullState.verticalOffset > 0f || pullState.isRefreshing) {
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun AddressDomainServiceCard(tld: NameServiceTLD, count: Int?, failed: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same globe on every card: the suffix beside it says which service it is.
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(KaspaTeal.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(tld.suffix, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(tld.serviceName, color = colors.textSecondary, fontSize = 15.sp)
        }
        Spacer(Modifier.width(14.dp))
        when {
            count != null -> {
                val label = if (count == 1) stringResource(R.string.address_domains_one)
                else stringResource(R.string.address_domains_count, count)
                Text(
                    "$count",
                    color = if (count > 0) Color.Black else colors.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .semantics { contentDescription = label }
                        .clip(RoundedCornerShape(50))
                        .background(if (count > 0) KaspaTeal else colors.textSecondary.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            failed -> Icon(
                Icons.Filled.WarningAmber,
                contentDescription = stringResource(R.string.address_domains_couldnt_load),
                tint = colors.warning,
                modifier = Modifier.size(20.dp),
            )
            else -> com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A service's names on one address, opened from its Domains card, in the page's place: .kachat
 * lists the address's .kachat names ([onOpenKachat] opens one); the others list the names.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressDomainServiceScreen(
    address: String,
    tld: NameServiceTLD,
    onBack: () -> Unit,
    onOpenKachat: ((NameInfo) -> Unit)?,
    vm: AddressDomainsViewModel = hiltViewModel(),
) {
    val colors = LocalAppColors.current
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tld.suffix, color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (tld == NameServiceTLD.KACHAT) {
                AddressKachatNamesList(address = address, onOpen = onOpenKachat, vm = vm)
            } else {
                AddressDomainServiceNames(address = address, tld = tld, vm = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressDomainServiceNames(address: String, tld: NameServiceTLD, vm: AddressDomainsViewModel) {
    val colors = LocalAppColors.current
    val state by vm.state.collectAsState()
    // Opened before the tab's answer landed (or for another address): ask again.
    LaunchedEffect(address) {
        if (vm.state.value.address != address.trim().lowercase()) vm.load(address)
    }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing) {
            vm.load(address)
            pullState.endRefresh()
        }
    }
    val list = state.names[tld].orEmpty()
    val failed = tld in state.failed
    Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pullState.nestedScrollConnection)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (list.isEmpty()) {
                Text(
                    if (failed) stringResource(R.string.could_not_reach_service, tld.serviceName)
                    else stringResource(R.string.address_domains_none, tld.suffix),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                list.forEach { name -> AddressDomainNameCard(title = name) }
            }
        }
        if (pullState.verticalOffset > 0f || pullState.isRefreshing) {
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * One address's .kachat names (iOS `KachatAddressDomainsList` / `KachatAddressLiveNamesList`), with
 * the same cards as Your Domains > .kachat. Where the registry isn't live nothing is read: the list
 * is its empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressKachatNamesList(address: String, onOpen: ((NameInfo) -> Unit)?, vm: AddressDomainsViewModel) {
    val colors = LocalAppColors.current
    val launched = KachatNamesService.isLaunched
    // Fixed for the process's life, so the calls below are never conditional in practice.
    val registry = remember(launched) { if (launched) vm.kachatRegistry.get() else null }
    val service = remember(launched) { if (launched) vm.kachatService.get() else null }
    val revision = registry?.revision?.collectAsState()?.value
    val upgrading = service?.registryUpgrading?.collectAsState()?.value == true
    var names by remember { mutableStateOf<List<NameInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(registry == null) }
    LaunchedEffect(address, revision) {
        val key = KachatNamesRegistry.keyOf(address)
        if (registry == null || key == null) {
            loaded = true
            return@LaunchedEffect
        }
        if (registry.refreshedAt.value == null) registry.refresh()
        // An expired name past grace isn't theirs any more: it's available to anyone.
        // A failed read keeps what's on screen: it isn't "holds no names" (iOS da59726).
        names = try {
            registry.heldNames(key)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            names
        }
        loaded = true
    }
    val pullState = rememberPullToRefreshState(enabled = { registry != null })
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing) {
            registry?.refresh()
            pullState.endRefresh()
        }
    }
    val graceMs = registry?.graceMs ?: 0L
    Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pullState.nestedScrollConnection)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !loaded -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                }
                upgrading || names.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = Screen.KachatNames.tabIconPainter(),
                        contentDescription = null,
                        tint = KaspaTeal,
                        modifier = Screen.KachatNames.tabIconModifier(40.dp),
                    )
                    Text(
                        stringResource(R.string.kachat_no_names_on_address),
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.kachat_names_this_address_owns),
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> names.forEach { n ->
                    // iOS `KachatLiveDomainsTab.badge`.
                    val badge = when (n.status(graceMs)) {
                        Status.ACTIVE -> if (n.isListed) stringResource(R.string.kn_ev_listed) else null
                        Status.GRACE -> stringResource(R.string.kn_status_expired)
                        Status.LAPSED -> stringResource(R.string.kn_available)
                    }
                    AddressDomainNameCard(
                        title = n.display,
                        badge = badge,
                        modifier = if (onOpen != null) Modifier.clickable { onOpen(n) } else Modifier,
                    )
                }
            }
        }
        if (registry != null && (pullState.verticalOffset > 0f || pullState.isRefreshing)) {
            PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/** The teal name card (iOS `DomainNameCardView`): the name, and an optional corner badge. */
@Composable
private fun AddressDomainNameCard(title: String, badge: String? = null, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(modifier)
            .background(KaspaTeal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
