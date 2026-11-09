package com.kachat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kachat.app.R
import com.kachat.app.services.KnsInscriptionEngine
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.viewmodels.WalletViewModel

// Wallet > Your Domains (iOS 1751d20: KaPay's `KNSDomainsListView`, `KNSDomainDetailView`,
// `KNSDomainSendView` and `KNSProfileEditorSheet`, which KaPlay's WalletViews.swift copied as they
// are) - KaPay Android's KnsDomainsScreen and its pages, ported. Left out as on iOS: Inscribe
// (the .kachat marketplace) and Add to Portfolio.

/** KaPay's `AboveDock`: a full-screen page over Your Domains, kept clear of the dock it runs
 *  under (the pages inside pad for nothing more). */
@Composable
private fun DomainsAboveDock(content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = LocalDockInset.current)) {
        CompositionLocalProvider(LocalDockInset provides 0.dp) { content() }
    }
}

/**
 * Your Domains (iOS 1751d20 `KNSDomainsListView`, KaPay's, pushed from the Wallet tab): one square
 * card per name ending - .kachat, .kas, .k, .kaspa - with how many names the wallet holds there.
 * A .kas domain's detail has Set as Primary, Customize Profile ([KnsDomainProfileEditorScreen])
 * and Send ([KnsDomainSendScreen]); a .kachat name opens its live detail
 * ([KachatLiveNameDetailScreen]). No Inscribe under .kachat: KaPlay has no .kachat marketplace.
 *
 * A bell row's or the expired-names banner's .kachat name ([WalletBellRoute.pendingKachatName],
 * [KachatDeepLink.pendingName]) is taken here and opened ([KachatNameRouteScreen], iOS
 * `KachatDeepLink.pendingName` -> `KachatNameRouteView`); arriving for it, Back goes straight back
 * to the Wallet tab, as iOS's name page is pushed there.
 */
@Composable
fun KnsDomainsScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    // A KNS profile save running in the background shows here too: iOS's pill overlays the pages
    // pushed over the Wallet tab (iOS 1751d20).
    Box(Modifier.fillMaxSize()) {
        KnsDomainsScreenContent(viewModel, onBack)
        KnsProfileSavePill(viewModel, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalDockInset.current))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnsDomainsScreenContent(viewModel: WalletViewModel, onBack: () -> Unit) {
    // The name a bell row or the expired banner opened (iOS 1751d20 `nameRoute`), taken on the
    // first frame so the cards never flash under it.
    var routeName by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf((WalletBellRoute.pendingKachatName.value ?: KachatDeepLink.pendingName.value)
            ?.let { com.kachat.app.services.kachatnames.KachatNames.Codec.normalize(it) })
    }
    // Opened for that name alone: its Back leaves Your Domains too.
    val routeOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(routeName != null) }
    val bellName by WalletBellRoute.pendingKachatName.collectAsState()
    val linkName by KachatDeepLink.pendingName.collectAsState()
    LaunchedEffect(bellName, linkName) {
        val name = bellName ?: linkName ?: return@LaunchedEffect
        WalletBellRoute.pendingKachatName.value = null
        KachatDeepLink.pendingName.value = null
        routeName = com.kachat.app.services.kachatnames.KachatNames.Codec.normalize(name)
    }
    routeName?.let { name ->
        val close: () -> Unit = {
            if (routeOnly) {
                onBack()
            } else {
                routeName = null
            }
        }
        androidx.activity.compose.BackHandler(onBack = close)
        CompositionLocalProvider(LocalKachatWalletViewModel provides viewModel) {
            DomainsAboveDock { KachatNameRouteScreen(name, onBack = close) }
        }
        return
    }

    val ownedDomainAssets by viewModel.ownedDomainAssets.collectAsState()
    // Testnet: a .kachat name opened from the .kachat tab - its live detail (iOS 5df42b4).
    var selectedKachat by remember { mutableStateOf<com.kachat.app.services.kachatnames.NameInfo?>(null) }
    // The name service whose card was tapped (iOS debd6fd), null on the cards themselves.
    var openedService by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }
    val walletAddress by viewModel.address.collectAsState()
    // The cards' counts (iOS 2ee667c), kept while a card's screen is open.
    val ownedServiceNames by viewModel.nameServices.owned.collectAsState()
    val kachatCount = rememberKachatHeldCount(walletAddress ?: "")
    val primaryDomainName by viewModel.primaryDomainName.collectAsState()
    val setPrimaryState by viewModel.setPrimaryState.collectAsState()
    var selectedDomain by remember { mutableStateOf<com.kachat.app.services.KnsAsset?>(null) }
    var showSendScreen by remember { mutableStateOf(false) }
    var editingDomain by remember { mutableStateOf<com.kachat.app.services.KnsAsset?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshOwnedDomainsAndAwait()
    }
    LaunchedEffect(Unit) {
        viewModel.refreshServiceNamesAndAwait()
    }

    selectedKachat?.let { info ->
        androidx.activity.compose.BackHandler { selectedKachat = null }
        CompositionLocalProvider(LocalKachatWalletViewModel provides viewModel) {
            DomainsAboveDock { KachatLiveNameDetailScreen(info, onBack = { selectedKachat = null }) }
        }
        return
    }

    // A .kas domain's own profile, edited on its own (iOS 09e0403): .kas profiles are no longer
    // shown in the app, but a domain's profile is still its owner's to change.
    editingDomain?.let { domain ->
        DomainsAboveDock { KnsDomainProfileEditorScreen(domain = domain, viewModel = viewModel, onBack = { editingDomain = null }) }
        return
    }

    // Full-screen swap: domain detail (card, primary status, Send entry point) - reached by
    // tapping a card in the list below. No transfer-history section: KNS only exposes a
    // "currently owned assets" endpoint, and this app's own KNS-transfer tracking is a one-shot
    // chat notification, not a persisted per-domain log, so there's no reliable data source yet.
    selectedDomain?.let { domain ->
        // The system back walks back one page, as the pages' own Back buttons do.
        BackHandler { if (showSendScreen) showSendScreen = false else selectedDomain = null }
        if (!showSendScreen) {
            // Trimmed, ignoring case (iOS 1751d20 `isPrimaryDomain`).
            val isPrimary = isPrimaryDomain(domain.asset, primaryDomainName)
            val settingThisOne = setPrimaryState.inFlight && setPrimaryState.assetId == domain.assetId
            DomainsAboveDock {
                KnsDomainDetailScreen(
                    domain = domain,
                    isPrimary = isPrimary,
                    settingInFlight = settingThisOne,
                    // Disabled while ANY set-primary is in flight; the last failure under the row.
                    setPrimaryBusy = setPrimaryState.inFlight,
                    setPrimaryError = setPrimaryState.errorMessage,
                    onSetPrimary = { domain.assetId?.let { viewModel.setPrimaryDomain(it) } },
                    onSend = {
                        viewModel.resetTransferDomainState()
                        showSendScreen = true
                    },
                    onCustomizeProfile = { editingDomain = domain },
                    onBack = { selectedDomain = null }
                )
            }
            return
        } else {
            DomainsAboveDock {
                KnsDomainSendScreen(
                    domain = domain,
                    viewModel = viewModel,
                    onDone = {
                        showSendScreen = false
                        selectedDomain = null
                    },
                    onBack = { showSendScreen = false }
                )
            }
            return
        }
    }

    // The service a card opened: its own screen, titled by its ending, holding the list each
    // service's tab used to show (iOS debd6fd - these used to be tabs on this screen).
    openedService?.let { tld ->
        androidx.activity.compose.BackHandler { openedService = null }
        Scaffold(
            containerColor = LocalAppColors.current.background,
            // Runs on under the dock, as iOS's content runs on under its tab bar; each list clears
            // it (its pinned button above the dock, or its scroll's end).
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(tld.suffix, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        SheetBarBackButton(onClick = { openedService = null }, modifier = Modifier.padding(start = 12.dp))
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tld) {
                    com.kachat.app.services.NameServiceTLD.KAS -> KnsDomainListTab(
                        domains = ownedDomainAssets.filter { it.asset != null },
                        primaryDomainName = primaryDomainName,
                        onRefresh = { viewModel.refreshOwnedDomainsAndAwait() },
                        onSelect = { selectedDomain = it },
                    )
                    com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA ->
                        ServiceNameListTab(tld = tld, viewModel = viewModel)
                    // Live on both networks (testnet-10, and mainnet since iOS ef6b21e). The
                    // "coming" note stays, unreachable.
                    com.kachat.app.services.NameServiceTLD.KACHAT ->
                        if (com.kachat.app.services.kachatnames.KachatNamesService.isEnabled) {
                            // no Inscribe: KaPlay has no .kachat marketplace (iOS 1751d20)
                            KachatLiveDomainsTab(
                                walletAddress = walletAddress ?: "",
                                onOpen = { selectedKachat = it },
                            )
                        } else {
                            KachatNamesComingSoon()
                        }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under the dock, as iOS's scroll runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Your Domains", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
    ) { padding ->
        // One square card per name ending, KaChat's own first: .kachat, KNS (.kas), dotk (.k),
        // Kaspa Names (.kaspa) - the same tile look as Kaspa Hub, two across (iOS debd6fd).
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalDockInset.current),
        ) {
            items(com.kachat.app.services.NameServiceTLD.entries.size) { i ->
                val tld = com.kachat.app.services.NameServiceTLD.entries[i]
                // How many names this wallet has with each service, 0 included; null while the
                // answer is not in yet (first load, or the service unreachable) - iOS 2ee667c.
                val count = when (tld) {
                    com.kachat.app.services.NameServiceTLD.KAS -> ownedDomainAssets.count { it.asset != null }
                    com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA -> ownedServiceNames[tld]?.size
                    com.kachat.app.services.NameServiceTLD.KACHAT -> kachatCount
                }
                DomainServiceCard(tld = tld, count = count, onClick = { openedService = tld })
            }
        }
    }
}

/**
 * A Kaspa Hub style square (iOS debd6fd `serviceCard`): the name ending big in the brand style,
 * who runs it under it. Same fill, 18 radius and 0.8 white hairline at 15% as the hub tiles.
 */
@Composable
private fun DomainServiceCard(
    tld: com.kachat.app.services.NameServiceTLD,
    /** How many names you hold there; null draws a dash until the answer is in (iOS 2ee667c). */
    count: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(LocalAppColors.current.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // iOS .title2 rounded, heavy, in the accent colour; one line, shrinking to 0.6.
            KachatFitText(
                tld.suffix,
                color = KaspaTeal,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IosRoundedFontFamily,
                minScale = 0.6f,
            )
            // iOS .caption semibold, secondary.
            Text(
                tld.serviceName,
                color = LocalAppColors.current.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // How many you have there, 0 included; a dash until the answer is in.
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .defaultMinSize(minWidth = 28.dp, minHeight = 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(KaspaTeal.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count?.toString() ?: "\u2013",
                    color = KaspaTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A pull-to-refresh list with the service's "Get a ... domain" button pinned under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainTabList(
    tld: com.kachat.app.services.NameServiceTLD,
    onRefresh: suspend () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            onRefresh()
            pullRefreshState.endRefresh()
        }
    }
    // With no "Get a domain" button pinned under it, the list itself clears the dock.
    val hasGetButton = tld.websiteUrl != null && tld.websiteName != null
    val dockInset = LocalDockInset.current
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                content()
                item { Spacer(Modifier.height(if (hasGetButton) 16.dp else 16.dp + dockInset)) }
            }
            PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        }
        GetDomainButton(tld)
    }
}

/**
 * "Get a .kas domain at knsdomains.org" - the pinned button under each outside service's tab,
 * opening that service's own site (iOS 718724b). KaChat creates only its own `.kachat` names; the
 * other services' names are registered with them. The same outlined capsule the in-app Inscribe
 * button was (not the teal fill: the cards above are teal-filled).
 */
@Composable
private fun GetDomainButton(tld: com.kachat.app.services.NameServiceTLD) {
    val url = tld.websiteUrl ?: return
    val site = tld.websiteName ?: return
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Above the dock the page runs under.
            .padding(bottom = LocalDockInset.current)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(LocalAppColors.current.surface)
            .border(1.5.dp, KaspaTeal, RoundedCornerShape(28.dp))
            .clickable { runCatching { uriHandler.openUri(url) } }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.get_domain_at, tld.suffix, site),
            color = KaspaTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun KnsDomainListTab(
    domains: List<com.kachat.app.services.KnsAsset>,
    primaryDomainName: String?,
    onRefresh: suspend () -> Unit,
    onSelect: (com.kachat.app.services.KnsAsset) -> Unit,
) {
    DomainTabList(tld = com.kachat.app.services.NameServiceTLD.KAS, onRefresh = onRefresh) {
        if (domains.isEmpty()) {
            item {
                Text(text = stringResource(R.string.no_domains_yet), color = LocalAppColors.current.textSecondary, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(domains, key = { it.assetId ?: it.asset ?: it.hashCode().toString() }) { domainAsset ->
                KnsDomainCard(
                    domain = domainAsset,
                    isPrimary = isPrimaryDomain(domainAsset.asset, primaryDomainName),
                    modifier = Modifier.clickable { onSelect(domainAsset) }
                )
            }
        }
    }
}

@Composable
private fun ServiceNameListTab(tld: com.kachat.app.services.NameServiceTLD, viewModel: WalletViewModel) {
    val owned by viewModel.nameServices.owned.collectAsState()
    val loading by viewModel.nameServices.loading.collectAsState()
    val failed by viewModel.nameServices.failed.collectAsState()
    val names = owned[tld].orEmpty()
    DomainTabList(tld = tld, onRefresh = { viewModel.refreshServiceNamesAndAwait() }) {
        if (names.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    when {
                        tld in loading && owned[tld] == null -> com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                        tld in failed -> Text(
                            stringResource(R.string.could_not_reach_service, tld.serviceName),
                            color = LocalAppColors.current.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        else -> Text(
                            stringResource(R.string.no_names_yet, tld.suffix),
                            color = LocalAppColors.current.textSecondary,
                        )
                    }
                }
            }
        } else {
            items(names, key = { it.display }) { name ->
                DomainNameCard(
                    title = name.display,
                    badge = if (name.isProvisional) stringResource(R.string.settling) else null,
                )
            }
        }
    }
}

@Composable
private fun KachatNamesComingSoon() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp)
            .padding(bottom = LocalDockInset.current),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.kachat_names_are_coming), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            stringResource(R.string.kachat_names_coming_body),
            color = LocalAppColors.current.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
        )
    }
}

/** Teal card matching the app's KNS domain branding - used both as the row style in [KnsDomainsScreen] and as the header of [KnsDomainDetailScreen]. */
@Composable
fun KnsDomainCard(domain: com.kachat.app.services.KnsAsset, isPrimary: Boolean = false, modifier: Modifier = Modifier) {
    DomainNameCard(title = domain.asset ?: "", badge = if (isPrimary) stringResource(R.string.primary) else null, modifier = modifier)
}

/** The teal name card, for any name service: the name, and an optional corner badge ("Primary"
 *  on KNS, "Settling" on a `.kaspa` name still inside its settling window) - iOS DomainNameCardView. */
@Composable
fun DomainNameCard(
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    /** A small line in the bottom-right corner (Your Domains > .kachat: when it expires, iOS 72b7dc7). */
    footnote: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(KaspaTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp)
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
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (footnote != null) {
            Text(
                footnote,
                color = Color.Black.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            )
        }
    }
}

/** iOS 1751d20 `isPrimaryDomain`: the domain and the primary compared trimmed, ignoring case. */
private fun isPrimaryDomain(domainName: String?, primaryDomainName: String?): Boolean {
    val domain = domainName?.trim()?.lowercase().orEmpty()
    val primary = primaryDomainName?.trim()?.lowercase().orEmpty()
    return domain == primary && domain.isNotEmpty()
}

/** iOS 1751d20 `isDomainTransferAllowed`: an asset id, and not listed for sale. */
private fun isDomainTransferAllowed(domain: com.kachat.app.services.KnsAsset): Boolean =
    !domain.assetId.isNullOrBlank() && !isDomainListed(domain)

/** iOS 1751d20 `KNSDomainDetailView.isListed`: the domain is up for sale on KNS's marketplace. */
private fun isDomainListed(domain: com.kachat.app.services.KnsAsset): Boolean =
    domain.status?.trim()?.lowercase() == "listed"

/**
 * Detail screen for a single owned domain (iOS 1751d20 `KNSDomainDetailView`) - the card itself,
 * primary/status info, and a dedicated Send entry point. Reached by tapping a card in
 * [KnsDomainsScreen]. No transfer history section: KNS only exposes a "currently owned assets"
 * endpoint, and this app's own KNS-transfer tracking isn't a persisted per-domain log, so there's
 * no reliable data source yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnsDomainDetailScreen(
    domain: com.kachat.app.services.KnsAsset,
    isPrimary: Boolean,
    /** This domain's set-primary is the one in flight: its row spins. */
    settingInFlight: Boolean,
    /** Any set-primary is in flight: the row is disabled (iOS `settingPrimaryDomainId != nil`). */
    setPrimaryBusy: Boolean = settingInFlight,
    /** Why the last "Set as Primary" failed, shown in red under the row (iOS 1751d20
     *  `setPrimaryError`); null when it succeeded or has not been tried - the moved star says so. */
    setPrimaryError: String? = null,
    onSetPrimary: () -> Unit,
    onSend: () -> Unit,
    /** Opens the full profile editor for this domain (avatar, banner, bio, links). KaChat does not
     *  SHOW .kas profiles anymore - .kachat profiles take priority - but a .kas domain's own
     *  profile can still be customized here (iOS 09e0403). Null where the host offers no editor. */
    onCustomizeProfile: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasAssetId = !domain.assetId.isNullOrBlank()
    val transferAllowed = isDomainTransferAllowed(domain)
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(domain.asset ?: "", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                // iOS: disabled at half opacity unless it has an asset id and isn't listed.
                Button(
                    onClick = onSend,
                    enabled = transferAllowed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaTeal,
                        disabledContainerColor = KaspaTeal.copy(alpha = 0.5f),
                        disabledContentColor = Color.Black.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    val tint = if (transferAllowed) Color.Black else Color.Black.copy(alpha = 0.5f)
                    // iOS Label("Send", systemImage: "arrow.up.circle.fill").
                    Icon(Icons.Filled.ArrowCircleUp, null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.send), color = tint, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            KnsDomainCard(domain = domain, isPrimary = isPrimary)
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.asset_id), color = colors.textPrimary)
                    Spacer(Modifier.width(12.dp))
                    // iOS: caption, monospaced, one line, truncated in the middle.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        MiddleEllipsisText(
                            domain.assetId ?: "",
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                if (onCustomizeProfile != null && hasAssetId) {
                    HorizontalDivider(color = colors.background)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onCustomizeProfile() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.customize_profile), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = KaspaTeal)
                    }
                }
                if (isPrimary) {
                    HorizontalDivider(color = colors.background)
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.primary_domain), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Star, contentDescription = null, tint = KaspaTeal)
                    }
                } else if (hasAssetId) {
                    // iOS `isSetPrimaryAllowed`: not the primary, and an asset id to set.
                    HorizontalDivider(color = colors.background)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !setPrimaryBusy) {
                                // pays a fee from the chatting address, like any send (iOS da59726)
                                context.authenticateWithDeviceCredential(
                                    title = "Confirm setting your primary domain",
                                    onSuccess = onSetPrimary,
                                )
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.set_as_primary), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (settingInFlight) {
                            com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.StarBorder, contentDescription = null, tint = KaspaTeal)
                        }
                    }
                    if (setPrimaryError != null && !setPrimaryBusy) {
                        // In place rather than as a popup (iOS 1751d20).
                        Text(
                            setPrimaryError,
                            color = colors.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                }
                if (isDomainListed(domain)) {
                    HorizontalDivider(color = colors.background)
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Status", color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("Listed", color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Sends (transfers) a single KNS domain inscription to a recipient address or domain (iOS 1751d20
 * `KNSDomainSendView`) - same UX conventions as the app's KAS send flows (name-aware recipient,
 * editable network fee) but with no amount field or coin control, since a domain transfer moves
 * the whole inscription rather than a chosen KAS amount. Cancel top left, Send top right (behind
 * the device lock), progress in its own sheet, a failure under the fee with the form kept, and the
 * "Sent" sheet when it's done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnsDomainSendScreen(
    domain: com.kachat.app.services.KnsAsset,
    viewModel: WalletViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** Non-null: sign/fund the transfer from this spending-chain address index instead of the
     *  identity address (the spending-address detail screen's KNS Domains tab passes it). */
    fromSpendingAddressIndex: Int? = null,
    /** The source address matching [fromSpendingAddressIndex] — recipient validation checks the
     *  recipient differs from the SOURCE, not necessarily from the identity address. */
    sourceAddress: String? = null
) {
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val recipientPreview by viewModel.transferRecipientPreview.collectAsState()
    val transferState by viewModel.transferDomainState.collectAsState()
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()

    var recipientInput by remember { mutableStateOf("") }
    var feeTier by remember { mutableStateOf(ColdFeeTier.NORMAL) }
    var customFeeSompi by remember { mutableStateOf<Long?>(null) }
    var isEditingFee by remember { mutableStateOf(false) }
    var customFeeText by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val domainName = domain.asset ?: ""
    val assetId = domain.assetId ?: ""
    val baseFeeSompi = KnsInscriptionEngine.REVEAL_PRIORITY_FEE_SOMPI
    val priorityFeeSompi = customFeeSompi ?: (baseFeeSompi * feeTier.multiplier)
    val inFlight = transferState.status !in listOf(
        WalletViewModel.KnsInscribeUiStatus.IDLE,
        WalletViewModel.KnsInscribeUiStatus.SUCCESS,
        WalletViewModel.KnsInscribeUiStatus.FAILED
    )
    // Leaving mid-transfer would hide a running on-chain operation.
    BackHandler(enabled = inFlight) {}

    // Progress in its own sheet rather than a spinner alone: a transfer is a commit, a wait, then
    // a reveal, and a spinner through all of it is indistinguishable from being stuck. Not
    // dismissible - leaving mid-transfer would hide a running on-chain operation.
    if (inFlight) {
        DomainTransferProgressSheet(domainName = domainName, status = transferState.status)
    }
    val canSend = !inFlight && recipientPreview?.resolvedAddress != null

    fun setRecipient(text: String) {
        recipientInput = text
        viewModel.checkTransferRecipient(text, sourceAddress)
    }

    fun trimmedKas(sompi: Long): String {
        var text = "%.8f".format(sompi / 100_000_000.0)
        while (text.endsWith("0")) text = text.dropLast(1)
        if (text.endsWith(".")) text = text.dropLast(1)
        return text
    }

    // Done: the "Sent" sheet, then the toast and back to Your Domains (iOS 1751d20
    // `onTransferComplete`: "%@ transferred to %@." once verified, else "Transfer submitted for %@.").
    val result = transferState.result
    if (transferState.status == WalletViewModel.KnsInscribeUiStatus.SUCCESS && result != null && !finished) {
        SentConfirmationSheet(
            transaction = SentTransaction(txId = result.revealTxId),
            explorerName = kaspaExplorer.displayName,
            explorerUrl = kaspaExplorer.txUrl(result.revealTxId),
            onDone = {
                if (!finished) {
                    finished = true
                    IosToasts.show(
                        if (result.verified) "${result.domain} transferred to ${result.toAddress}."
                        else "Transfer submitted for ${result.domain}."
                    )
                    onDone()
                }
            },
        )
    }

    if (showScanner) {
        QrScannerSheet(
            onScanned = { code ->
                // A scanned kaspa: URI loses its ?amount= query (iOS `handleScannedQRCode`).
                var address = code.trim()
                val lower = address.lowercase()
                if (lower.startsWith("kaspa:") || lower.startsWith("kaspatest:")) address = address.substringBefore('?')
                setRecipient(address)
                showScanner = false
            },
            onDismiss = { showScanner = false },
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.send_domain), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarButton(stringResource(R.string.cancel), onClick = onBack, enabled = !inFlight, modifier = Modifier.padding(start = 12.dp))
                },
                actions = {
                    if (inFlight) {
                        Box(Modifier.padding(end = 24.dp).size(44.dp), contentAlignment = Alignment.Center) {
                            com.kachat.app.ui.theme.IosActivityIndicator()
                        }
                    } else {
                        SheetBarButton(
                            stringResource(R.string.send),
                            onClick = {
                                // Giving a domain away can't be undone, so the phone's lock comes
                                // first. Declining leaves the form as it was (iOS 1751d20).
                                context.authenticateWithDeviceCredential(
                                    title = "Confirm sending this domain",
                                    onSuccess = { viewModel.transferDomain(domainName, assetId, priorityFeeSompi, fromSpendingAddressIndex) },
                                )
                            },
                            enabled = canSend,
                            emphasized = true,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SettingsSection(title = "Domain") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(domainName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    // iOS: caption2, monospaced, one line, truncated in the middle.
                    MiddleEllipsisText(
                        assetId,
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }

            Column {
                SettingsSection(title = "Recipient Address") {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.kachat.app.ui.theme.IosTextField(
                            value = recipientInput,
                            onValueChange = { setRecipient(it) },
                            placeholder = { Text(stringResource(R.string.kaspa_qr_or_domain)) },
                            singleLine = true,
                            enabled = !inFlight,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = KaspaTeal,
                                unfocusedBorderColor = colors.textSecondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val preview = recipientPreview
                        if (recipientInput.isNotEmpty() && preview != null) {
                            // the shared card: the domain typed, else the address's own .kachat name
                            AddressResolutionCard(address = preview.resolvedAddress, domain = preview.resolvedName)
                            when {
                                preview.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.looking_up_domain), color = colors.textSecondary, fontSize = 12.sp)
                                }
                                preview.resolvedAddress != null && preview.resolvedName != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RecipientStatusLine(ok = true, text = stringResource(R.string.resolved_name, preview.resolvedName))
                                    MiddleEllipsisText(
                                        preview.resolvedAddress,
                                        color = colors.textSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                }
                                // iOS `KaspaAddress.validityText`: "Valid address" with a check.
                                preview.resolvedAddress != null -> RecipientStatusLine(ok = true, text = addressValidityText(preview.resolvedAddress, isValid = true))
                                preview.errorMessage != null -> RecipientStatusLine(ok = false, text = preview.errorMessage)
                            }
                            if (!preview.checking) {
                                OtherDomainsDropdown(preview.nameResolutions, preview.selectedTld) {
                                    viewModel.selectTransferResolution(it, sourceAddress)
                                }
                            }
                        }
                        // Paste and Scan QR, under the field (iOS 1751d20).
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = !inFlight) {
                                    clipboard.getText()?.text?.let { setRecipient(it.trim()) }
                                }.padding(vertical = 6.dp),
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.paste), color = KaspaTeal)
                            }
                            Spacer(Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = !inFlight) { showScanner = true }.padding(vertical = 6.dp),
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.scan_qr), color = KaspaTeal)
                            }
                        }
                    }
                }
                SectionFooter(stringResource(R.string.enter_kaspa_address_or_domain_short_kachat_first))
            }

            Column {
                SettingsSection(title = "Fee") {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.background)
                        ) {
                            ColdFeeTier.entries.forEach { tier ->
                                val selected = feeTier == tier && customFeeSompi == null
                                Text(
                                    tier.label,
                                    color = if (selected) Color.Black else colors.textSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) KaspaTeal else Color.Transparent)
                                        .clickable(enabled = !inFlight) {
                                            feeTier = tier
                                            customFeeSompi = null
                                            isEditingFee = false
                                        }
                                        .padding(vertical = 10.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.network_fee), color = colors.textPrimary, modifier = Modifier.weight(1f))
                            if (isEditingFee) {
                                com.kachat.app.ui.theme.IosTextField(
                                    value = customFeeText,
                                    onValueChange = { customFeeText = it },
                                    singleLine = true,
                                    modifier = Modifier.width(110.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    )
                                )
                                IconButton(onClick = {
                                    KaspaUnit.sompiFromUserText(customFeeText)?.let { sompi ->
                                        // the reveal pays this out of the commit: never more than it can cover (iOS da59726)
                                        customFeeSompi = minOf(sompi, KnsInscriptionEngine.MAX_PRIORITY_FEE_SOMPI)
                                    }
                                    isEditingFee = false
                                }) {
                                    Icon(Icons.Default.CheckCircle, null, tint = KaspaTeal)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(enabled = !inFlight) {
                                        customFeeText = trimmedKas(priorityFeeSompi)
                                        isEditingFee = true
                                    }
                                ) {
                                    Text("${trimmedKas(priorityFeeSompi)} ${KaspaUnit.symbol}", color = KaspaTeal, textDecoration = TextDecoration.Underline)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Edit, null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                // iOS KNSDomainSendView's Fee section footer.
                SectionFooter("If the network is busy, Fast or Priority pays a higher fee to help your transfer confirm sooner. Tap the fee amount to set a custom fee.")
            }

            // A failure keeps the form, the reason under the fee (iOS 1751d20 `errorMessage`).
            if (transferState.status == WalletViewModel.KnsInscribeUiStatus.FAILED) {
                Text(
                    transferState.errorMessage ?: stringResource(R.string.something_went_wrong),
                    color = colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.height(24.dp + LocalDockInset.current))
        }
    }
}

/** A recipient check's line: a green check or a red cross, and what it says (iOS 1751d20). */
@Composable
private fun RecipientStatusLine(ok: Boolean, text: String) {
    val colors = LocalAppColors.current
    val tint = if (ok) colors.success else colors.danger
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, fontSize = 12.sp)
    }
}

/** A Form section's footer: small secondary text under the card. */
@Composable
private fun SectionFooter(text: String) {
    Text(
        text,
        color = LocalAppColors.current.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
    )
}

/**
 * What a domain transfer is doing right now (iOS 1751d20 `KNSDomainTransferService.TransferStage`:
 * "Preparing transfer" 0.1, "Submitting commit transaction" 0.3, "Waiting for the commit to
 * settle" 0.55, "Submitting reveal transaction" 0.8, "Confirming the new owner" 0.95). The
 * fractions are approximate on purpose: the waits are network-dependent, and a bar that moves in
 * known steps beats a spinner that says nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainTransferProgressSheet(
    domainName: String,
    status: WalletViewModel.KnsInscribeUiStatus,
) {
    val (label, fraction) = when (status) {
        WalletViewModel.KnsInscribeUiStatus.SUBMITTING_COMMIT -> "Submitting commit transaction" to 0.3f
        WalletViewModel.KnsInscribeUiStatus.WAITING_FOR_COMMIT -> "Waiting for the commit to settle" to 0.55f
        WalletViewModel.KnsInscribeUiStatus.SUBMITTING_REVEAL -> "Submitting reveal transaction" to 0.8f
        WalletViewModel.KnsInscribeUiStatus.VERIFYING -> "Confirming the new owner" to 0.95f
        else -> "Preparing transfer" to 0.1f
    }

    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            // Held open for the duration: the work keeps running either way, and a dismissed sheet
            // would leave an on-chain transfer with nothing reporting on it.
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Sending $domainName",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = KaspaTeal,
                        trackColor = colors.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(label, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text(
                        "A domain transfer is two transactions, so this takes a moment. Keep the app open until it finishes.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A .kas domain's own profile, from Your Domains > the domain > Customize Profile (iOS 09e0403):
 * the full editor, loaded for that domain on its own and saved to it. The app no longer SHOWS
 * .kas profiles, but a domain's profile is still its owner's to change.
 */
@Composable
fun KnsDomainProfileEditorScreen(
    domain: com.kachat.app.services.KnsAsset,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
) {
    val loading by viewModel.editingDomainLoading.collectAsState()
    val current by viewModel.editingDomain.collectAsState()
    LaunchedEffect(domain.assetId) { viewModel.beginEditingDomain(domain) }
    DisposableEffect(Unit) { onDispose { viewModel.endEditingDomain() } }
    BackHandler(onBack = onBack)
    // The fields seed once per domain, so they wait for its profile rather than seeding empty.
    if (loading || current?.assetId != domain.assetId) {
        Box(Modifier.fillMaxSize().background(LocalAppColors.current.background), contentAlignment = Alignment.Center) {
            com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
        }
        return
    }
    EditKnsProfileScreen(viewModel = viewModel, onBack = onBack, editingDomain = domain)
}

/**
 * The .kas profile editor (iOS 1751d20 `KNSProfileEditorSheet`): Avatar, Banner, Profile and the
 * Domain it belongs to. Save asks to confirm the changes; Confirm closes the editor, asks for the
 * device lock, and saves in the background - the Wallet tab's pill ([KnsProfileSavePill]) shows
 * how it goes ([WalletViewModel.saveKnsProfile]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditKnsProfileScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onNavigateToDomains: () -> Unit = {},
    onNavigateToSetupGuide: () -> Unit = {},
    /** Edit THIS .kas domain's profile (from Your Domains) rather than the primary's - see
     *  [KnsDomainProfileEditorScreen]. */
    editingDomain: com.kachat.app.services.KnsAsset? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primaryProfile by viewModel.knsProfile.collectAsState()
    val domainProfile by viewModel.editingDomainProfile.collectAsState()
    val knsProfile = if (editingDomain != null) domainProfile else primaryProfile
    val primaryDomainName by viewModel.activeProfileDomainName.collectAsState()
    val activeProfileDomainName = editingDomain?.asset ?: primaryDomainName
    val primaryAssetId by viewModel.profileDomainAssetId.collectAsState()
    val activeAssetId = if (editingDomain != null) editingDomain.assetId else primaryAssetId
    val pendingAvatarUri by viewModel.pendingAvatarUri.collectAsState()
    val pendingBannerUri by viewModel.pendingBannerUri.collectAsState()
    val avatarCleared by viewModel.avatarCleared.collectAsState()
    val bannerCleared by viewModel.bannerCleared.collectAsState()
    // iOS `displayName`: the domain's name, else "KNS Profile".
    val displayName = activeProfileDomainName?.trim()?.takeIf { it.isNotEmpty() } ?: "KNS Profile"

    var bio by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var telegram by remember { mutableStateOf("") }
    var discord by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var redirect by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    // The domain the fields were last read from. A KNS profile belongs to a DOMAIN, so promoting a
    // different one to primary swaps which avatar, banner and details are in effect - and these
    // fields used to seed exactly once, so the screen kept showing the old domain's values until
    // it was closed and reopened. Keyed on the domain rather than on the profile object so an
    // ordinary refresh of the SAME profile never overwrites what the user is part-way through
    // typing.
    var seededDomain by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(knsProfile, activeProfileDomainName) {
        if (!seeded || seededDomain != activeProfileDomainName) {
            bio = knsProfile?.bio ?: ""
            x = knsProfile?.x ?: ""
            website = knsProfile?.website ?: ""
            telegram = knsProfile?.telegram ?: ""
            discord = knsProfile?.discord ?: ""
            email = knsProfile?.contactEmail ?: ""
            github = knsProfile?.github ?: ""
            redirect = knsProfile?.redirectUrl ?: ""
            // Staged image picks belonged to the previous domain's profile.
            if (seeded) viewModel.clearPendingProfileImages()
            seeded = true
            seededDomain = activeProfileDomainName
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetEditProfileState() }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) viewModel.setPendingAvatar(uri) }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) viewModel.setPendingBanner(uri) }

    var showSaveDialog by remember { mutableStateOf(false) }

    // What "Save" is actually about to submit — each entry becomes its own on-chain commit/reveal
    // transaction, so this doubles as both the confirm dialog's change list and its cost count.
    val pendingChanges = remember(bio, x, website, telegram, discord, email, github, redirect, pendingAvatarUri, pendingBannerUri, avatarCleared, bannerCleared, knsProfile) {
        buildList {
            if (pendingAvatarUri != null) add("Avatar")
            else if (avatarCleared && !knsProfile?.avatarUrl.isNullOrEmpty()) add("Avatar (removed)")
            if (pendingBannerUri != null) add("Banner")
            else if (bannerCleared && !knsProfile?.bannerUrl.isNullOrEmpty()) add("Banner (removed)")
            if (bio.trim() != (knsProfile?.bio ?: "")) add("Bio")
            if (x.trim() != (knsProfile?.x ?: "")) add("X")
            if (website.trim() != (knsProfile?.website ?: "")) add("Website")
            if (telegram.trim() != (knsProfile?.telegram ?: "")) add("Telegram")
            if (discord.trim() != (knsProfile?.discord ?: "")) add("Discord")
            if (email.trim() != (knsProfile?.contactEmail ?: "")) add("Email")
            if (github.trim() != (knsProfile?.github ?: "")) add("GitHub")
            if (redirect.trim() != (knsProfile?.redirectUrl ?: "")) add("Redirect")
        }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.edit_kns_profile), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarButton(stringResource(R.string.cancel), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                actions = {
                    SheetBarButton(
                        stringResource(R.string.save),
                        onClick = { if (pendingChanges.isNotEmpty()) showSaveDialog = true },
                        enabled = pendingChanges.isNotEmpty() && !activeAssetId.isNullOrEmpty(),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // No setup guide here since 5.2 (iOS b000310): the guide is .kachat's, in Edit .kachat
            // Profile. A .kas profile is edited field by field below.

            SettingsSection(title = stringResource(R.string.avatar)) {
                // iOS: the 64 avatar on the left, Choose Avatar and Remove Avatar stacked beside it.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        imageUrl = if (avatarCleared) null else (pendingAvatarUri?.toString() ?: knsProfile?.avatarUrl),
                        fallbackText = displayName,
                        size = 64.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // iOS Label("Choose Avatar", systemImage: "photo").
                        EditorLabelButton(Icons.Filled.Photo, stringResource(R.string.choose_avatar), KaspaTeal) {
                            avatarPicker.launch("image/*")
                        }
                        if (pendingAvatarUri != null || (!avatarCleared && !knsProfile?.avatarUrl.isNullOrEmpty())) {
                            // iOS Label("Remove Avatar", systemImage: "trash").
                            EditorLabelButton(Icons.Default.Delete, "Remove Avatar", LocalAppColors.current.danger) {
                                if (pendingAvatarUri != null) viewModel.setPendingAvatar(null) else viewModel.clearExistingAvatar()
                            }
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.banner)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val previewUrl = if (bannerCleared) null else (pendingBannerUri?.toString() ?: knsProfile?.bannerUrl)
                    if (!previewUrl.isNullOrEmpty()) {
                        SubcomposeAsyncImage(
                            model = previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LocalAppColors.current.surface)
                        )
                    }
                    // iOS Label("Choose Banner", systemImage: "photo.on.rectangle").
                    EditorLabelButton(Icons.Filled.PhotoLibrary, stringResource(R.string.choose_banner), KaspaTeal) {
                        bannerPicker.launch("image/*")
                    }
                    if (pendingBannerUri != null || (!bannerCleared && !knsProfile?.bannerUrl.isNullOrEmpty())) {
                        // iOS Label("Remove Banner", systemImage: "trash").
                        EditorLabelButton(Icons.Default.Delete, "Remove Banner", LocalAppColors.current.danger) {
                            if (pendingBannerUri != null) viewModel.setPendingBanner(null) else viewModel.clearExistingBanner()
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.profile)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditProfileTextField(stringResource(R.string.bio), bio, { bio = it }, enabled = true, singleLine = false)
                    // iOS 1751d20's placeholders: "X handle", "Discord user id", "Redirect URL".
                    EditProfileTextField("X handle", x, { x = it }, enabled = true)
                    EditProfileTextField(stringResource(R.string.website), website, { website = it }, enabled = true)
                    EditProfileTextField(stringResource(R.string.telegram), telegram, { telegram = it }, enabled = true)
                    EditProfileTextField("Discord user id", discord, { discord = it }, enabled = true)
                    EditProfileTextField(stringResource(R.string.email), email, { email = it }, enabled = true)
                    EditProfileTextField(stringResource(R.string.github), github, { github = it }, enabled = true)
                    EditProfileTextField("Redirect URL", redirect, { redirect = it }, enabled = true)
                }
            }

            // iOS 1751d20: the Domain section last - its name, and its asset id.
            SettingsSection(title = "Domain") {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.name), color = LocalAppColors.current.textPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            displayName,
                            color = LocalAppColors.current.textSecondary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!activeAssetId.isNullOrEmpty()) {
                        HorizontalDivider(color = LocalAppColors.current.background)
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.asset_id), color = LocalAppColors.current.textPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                activeAssetId,
                                color = LocalAppColors.current.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }

    if (showSaveDialog) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text("Confirm Changes", color = LocalAppColors.current.textPrimary) },
            text = {
                Column {
                    Text(
                        "${pendingChanges.size} change${if (pendingChanges.size == 1) "" else "s"}. Each is submitted as its own on-chain transaction from your chatting address:",
                        color = LocalAppColors.current.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    pendingChanges.forEach { Text("• $it", color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        KaspaUnit.label(stringResource(R.string.each_transaction_temporarily_uses_2_kas)),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Read now: closing the editor drops its staged picks (iOS 1751d20
                        // `KNSProfileEditorSubmission`).
                        val submission = viewModel.knsProfileSubmission(
                            mapOf(
                                "bio" to bio,
                                "x" to x,
                                "website" to website,
                                "telegram" to telegram,
                                "discord" to discord,
                                "contactEmail" to email,
                                "github" to github,
                                "redirectUrl" to redirect
                            )
                        )
                        showSaveDialog = false
                        onBack()
                        // It spends Kaspa on fees, and the phone's lock comes first (iOS 1751d20
                        // "Confirm saving this profile"); declined, nothing is saved.
                        if (submission != null) {
                            context.authenticateWithDeviceCredential(
                                title = "Confirm saving this profile",
                                onSuccess = { viewModel.saveKnsProfile(submission) },
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/** A Form row button with its icon, as iOS's `Label(_, systemImage:)` in a Form. */
@Composable
private fun EditorLabelButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color)
    }
}

@Composable
private fun EditProfileTextField(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean, singleLine: Boolean = true) {
    com.kachat.app.ui.theme.IosTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LocalAppColors.current.textPrimary,
            unfocusedTextColor = LocalAppColors.current.textPrimary,
            disabledTextColor = LocalAppColors.current.textSecondary,
            focusedBorderColor = KaspaTeal,
            unfocusedBorderColor = LocalAppColors.current.textSecondary,
            focusedLabelColor = KaspaTeal,
            unfocusedLabelColor = LocalAppColors.current.textSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
