package com.kachat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kachat.app.R
import com.kachat.app.services.GlobalNotificationCenterStore
import com.kachat.app.services.WalletReceiptNotifier
import com.kachat.app.services.kachatnames.KachatNames
import com.kachat.app.services.kachatnames.KachatNamesNotifier
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.NotificationCenterViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Where a tapped bell row lands once the bell's sheet has closed (iOS 1751d20 `OwnAddressRoute` /
 * `KachatDeepLink.pendingName`): the .kachat name a row named, kept until Your Domains takes it
 * and opens that name's detail. Your Domains clears it.
 */
object WalletBellRoute {
    val pendingKachatName = MutableStateFlow<String?>(null)
}

/** The routes a bell row opens (see COMMON's route names; owned by the addresses and domains areas). */
private const val BELL_CHATTING_ADDRESS_ROUTE = "chatting_address_manage"
/** A spending address's own page (ChessMainShell's MANAGE_ADDRESS_ROUTE, "manage_address/{index}"). */
private fun bellSpendingAddressRoute(index: Int) = "manage_address/$index"
private const val BELL_YOUR_DOMAINS_ROUTE = "your_domains"

/**
 * The Wallet tab's notification bell (iOS 1751d20 `ChessWalletView`'s trailing toolbar item): the
 * bell outline in the accent colour with a plain red DOT (no count) while something is unread,
 * drawn inside the button's bounds. Tapping it opens [GlobalNotificationSheet]; a tapped row opens
 * its subject after the sheet has gone - a "Received" row the address that got the Kaspa, a
 * .kachat row the name.
 */
@Composable
fun WalletNotificationBell(navController: NavController) {
    val viewModel: NotificationCenterViewModel = hiltViewModel()
    val entries by viewModel.store.entries.collectAsState()
    val lastSeen by viewModel.store.lastSeenAt.collectAsState()
    val unread = entries.count { it.timestampMs > lastSeen }
    var showSheet by remember { mutableStateOf(false) }
    /** A "Received" row's address that isn't in this wallet any more (iOS `.unknown`). */
    var unknownAddress by remember { mutableStateOf<String?>(null) }
    /** A "Received" row's address whose coins couldn't be read (iOS 1751d20 `.unreadable`). */
    var unreadableAddress by remember { mutableStateOf<String?>(null) }
    /** Try Again is reading it again: the spinner in its place (iOS `resolved = nil`). */
    var retryingUnreadable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The address that got the Kaspa, on the screen you'd reach it through (iOS 1751d20
    // `OwnAddressHistorySheet`): the chatting address's page, or that spending address's own page.
    suspend fun openOwnAddress(target: String) {
        when (val resolved = viewModel.resolveOwnAddress(target)) {
            NotificationCenterViewModel.OwnAddress.Chatting -> {
                unreadableAddress = null
                navigateSafely(navController, BELL_CHATTING_ADDRESS_ROUTE)
            }
            is NotificationCenterViewModel.OwnAddress.Spending -> {
                unreadableAddress = null
                navigateSafely(navController, bellSpendingAddressRoute(resolved.index))
            }
            NotificationCenterViewModel.OwnAddress.Unknown -> {
                unreadableAddress = null
                unknownAddress = target
            }
            NotificationCenterViewModel.OwnAddress.Unreadable -> { unreadableAddress = target }
        }
    }

    IconButton(
        onClick = {
            viewModel.store.reloadIfNeeded()
            showSheet = true
        },
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = "Notifications" },
    ) {
        // The dot is drawn inside the padded bounds (iOS: `.padding(4).overlay(alignment: .topTrailing)`).
        Box {
            Icon(
                // SF Symbols "bell" is an outline.
                Icons.Outlined.Notifications,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.padding(4.dp).size(22.dp),
            )
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0245E)),
                )
            }
        }
    }

    if (showSheet) {
        GlobalNotificationSheet(
            store = viewModel.store,
            onDismiss = { showSheet = false },
            onOpen = { entry ->
                val target = entry.targetId.orEmpty()
                when (entry.source) {
                    // A receipt opens the address that got the Kaspa: the chatting address's page,
                    // or the spending address's own page (iOS SpendingAddressTransactionHistoryView).
                    WalletReceiptNotifier.SOURCE -> scope.launch { openOwnAddress(target) }
                    // The name, its detail over the Wallet tab.
                    KachatNamesNotifier.SOURCE -> {
                        WalletBellRoute.pendingKachatName.value = KachatNames.Codec.normalize(target)
                        navigateSafely(navController, BELL_YOUR_DOMAINS_ROUTE)
                    }
                    else -> {}
                }
            },
        )
    }

    unknownAddress?.let {
        OwnAddressUnknownSheet(onDismiss = { unknownAddress = null })
    }
    unreadableAddress?.let { target ->
        OwnAddressUnreadableSheet(
            retrying = retryingUnreadable,
            onRetry = {
                if (!retryingUnreadable) {
                    retryingUnreadable = true
                    scope.launch {
                        try { openOwnAddress(target) } finally { retryingUnreadable = false }
                    }
                }
            },
            onDismiss = { unreadableAddress = null },
        )
    }
}

/** A route another area adds; a build without it yet must not crash on a tap. */
private fun navigateSafely(navController: NavController, route: String) {
    runCatching { navController.navigate(route) { launchSingleTop = true } }
        .onFailure { android.util.Log.w("WalletNotificationBell", "No route $route", it) }
}

/**
 * The bell's sheet (iOS 1751d20 `GlobalNotificationListView`): a full-height sheet with Done on the
 * left, "Notifications" in the middle and Clear All on the right, then the newest-first feed - each
 * row its source's icon, the title, up to two lines of body and the source with how long ago - or,
 * with nothing in it, the bell and "No notifications yet". Opening it marks everything seen (clears
 * the bell's dot), as iOS's onAppear does. A tapped row closes the sheet first (only one sheet
 * presents at a time), then [onOpen] runs - a row without a target opens nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalNotificationSheet(
    store: GlobalNotificationCenterStore,
    onDismiss: () -> Unit,
    onOpen: (GlobalNotificationCenterStore.Entry) -> Unit,
) {
    val entries by store.entries.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { store.markAllSeen() }

    /** Closes the sheet (animated, like iOS's dismiss), then runs [then]. */
    fun dismiss(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    fun open(entry: GlobalNotificationCenterStore.Entry) {
        if (entry.targetId.isNullOrEmpty()) return
        dismiss { onOpen(entry) }
    }

    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colors.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home
            // indicator; its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(Modifier.fillMaxSize()) {
                    // The inline navigation bar: Done (cancellation, left), the title, Clear All
                    // (right); Clear All greys out with nothing to clear.
                    SheetNavBar(
                        title = stringResource(R.string.notifications),
                        leading = { SheetBarButton(stringResource(R.string.done), onClick = { dismiss() }) },
                        trailing = {
                            SheetBarButton(
                                stringResource(R.string.notification_center_clear_all),
                                onClick = { store.clearAll() },
                                enabled = entries.isNotEmpty(),
                            )
                        },
                    )
                    if (entries.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                // iOS's "bell" at 40pt draws about 36 wide and 38 tall; Material's
                                // outlined bell fills 20/24 of its box, so 46.
                                modifier = Modifier.size(46.dp),
                            )
                            Text(
                                stringResource(R.string.notification_center_empty_title),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            )
                            Text(
                                // iOS 1751d20's own words (English there too).
                                "Kaspa arriving on your chatting address and news about your .kachat names show up here.",
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 40.dp),
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(entries, key = { it.id }) { entry ->
                                GlobalNotificationRow(entry, onClick = { open(entry) })
                                // Plain-list separator: from where the text starts to the trailing edge.
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 50.dp),
                                    color = colors.divider,
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One row: the source's icon, then the title, the body (2 lines) and "source  time ago". */
@Composable
private fun GlobalNotificationRow(entry: GlobalNotificationCenterStore.Entry, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(24.dp).padding(top = 1.dp), contentAlignment = Alignment.Center) {
            Icon(sourceIcon(entry.source), contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (entry.body.isNotEmpty()) {
                Text(entry.body, color = colors.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(GlobalNotificationCenterStore.sourceLabel(entry.source), color = colors.textSecondary, fontSize = 11.sp)
                Text(
                    bellRelativeTime(entry.timestampMs),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * A row's time, relative however old it is (iOS 1751d20 `.relative(presentation: .named)`): "now",
 * "5 seconds ago", "3 minutes ago", "2 hours ago", "yesterday", "4 days ago", "last week",
 * "3 weeks ago", "last month", "5 months ago", "last year", "2 years ago". The largest whole unit
 * that has passed, as Apple's formatter; `DateUtils` switched to a date after a week.
 */
internal fun bellRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val then = java.time.Instant.ofEpochMilli(minOf(timestampMs, nowMs)).atZone(zone)
    val now = java.time.Instant.ofEpochMilli(nowMs).atZone(zone)
    fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
    val years = java.time.temporal.ChronoUnit.YEARS.between(then, now)
    if (years > 0) return if (years == 1L) "last year" else plural(years, "year")
    val months = java.time.temporal.ChronoUnit.MONTHS.between(then, now)
    if (months > 0) return if (months == 1L) "last month" else plural(months, "month")
    val weeks = java.time.temporal.ChronoUnit.WEEKS.between(then, now)
    if (weeks > 0) return if (weeks == 1L) "last week" else plural(weeks, "week")
    val days = java.time.temporal.ChronoUnit.DAYS.between(then, now)
    if (days > 0) return if (days == 1L) "yesterday" else plural(days, "day")
    val hours = java.time.temporal.ChronoUnit.HOURS.between(then, now)
    if (hours > 0) return plural(hours, "hour")
    val minutes = java.time.temporal.ChronoUnit.MINUTES.between(then, now)
    if (minutes > 0) return plural(minutes, "minute")
    val seconds = java.time.temporal.ChronoUnit.SECONDS.between(then, now)
    if (seconds > 0) return plural(seconds, "second")
    return "now"
}

/** A row's icon (iOS `Entry.Source.icon`): arrow.down.circle for a receipt, "at" for .kachat. */
private fun sourceIcon(source: String): ImageVector = when (source) {
    KachatNamesNotifier.SOURCE -> Icons.Default.AlternateEmail
    else -> Icons.Outlined.ArrowCircleDown
}

/**
 * A "Received" row whose address's coins couldn't be read (iOS 1751d20 `OwnAddressHistorySheet`'s
 * `.unreadable`): Done, then "Couldn't load the coins at this address" over Try Again, 14 apart -
 * a page showing 0 would say it's empty. While Try Again reads it again, the spinner in its place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnAddressUnreadableSheet(retrying: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colors.background,
            dragHandle = null,
        ) {
            Column(Modifier.navigationBarsPadding().fillMaxSize()) {
                SheetNavBar(
                    title = "",
                    leading = {
                        SheetBarButton(stringResource(R.string.done), onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        })
                    },
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (retrying) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                "Couldn't load the coins at this address",
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.try_again),
                                color = KaspaTeal,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(onClick = onRetry),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A "Received" row whose address isn't one of this wallet's any more (iOS `OwnAddressHistorySheet`'s
 * `.unknown`): Done, and the sentence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnAddressUnknownSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colors.background,
            dragHandle = null,
        ) {
            Column(Modifier.navigationBarsPadding().fillMaxSize()) {
                SheetNavBar(
                    title = "",
                    leading = {
                        SheetBarButton(stringResource(R.string.done), onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        })
                    },
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.own_address_not_in_wallet),
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
