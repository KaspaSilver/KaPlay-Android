package com.kachat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel

// ---------------------------------------------------------------------------------------------
// The Wallet tab's Receive (iOS 1751d20 `ChessWalletView.ReceiveKaspaQRView`, KaPay's): a fresh,
// never-used address of this account on the white QR page. Route "receive_fresh".
// ---------------------------------------------------------------------------------------------

/**
 * The Receive QR's address, decided ahead of the tap (iOS 1751d20 `ReceiveAddressWarmCache`).
 *
 * A plain box rather than state on a screen: the Wallet tab warms it as it appears ([warm]) and the
 * Receive page reads it, so the page usually draws its code at once instead of a spinner behind a
 * network round trip. Process-lifetime only - a head start, never a source of truth: the page
 * re-confirms it on every open. Kept per account, so a switch never shows the other account's.
 */
object ReceiveAddressWarmCache {
    @Volatile private var entry: Pair<String, String>? = null

    /** The warmed address for the account whose chatting address is [account], if any. */
    fun address(account: String?): String? = entry?.takeIf { account != null && it.first == account }?.second

    fun store(account: String?, address: String?) {
        entry = if (account != null && address != null) account to address else null
    }

    /**
     * Picks the Receive QR's address now, not when Receive is tapped (iOS `warmReceiveAddress`):
     * only picked, not revealed - nothing appears in Manage Addresses until the code is shown.
     * Call it from the Wallet tab's appear.
     */
    fun warm(walletViewModel: WalletViewModel) {
        val account = walletViewModel.address.value ?: return
        walletViewModel.resolveFreshReceiveAddress(revealing = false) { address ->
            if (address != null) store(account, address)
        }
    }
}

/** The Receive page's caption (iOS 1751d20 `ReceiveKaspaQRView`'s subtitle). */
private const val RECEIVE_FRESH_CAPTION =
    "A fresh address, never used before. Kaspa sent here lands in this account and shows in your spending total. Use it for everything except .kachat names."

/**
 * Receive Kaspa (iOS 1751d20 `ReceiveKaspaQRView` in the Wallet tab's Receive sheet): resolves a
 * fresh receive address before drawing the QR, so the code on screen is never one that has already
 * appeared on chain. Until then the page says "Preparing a fresh address" under a spinner, titled
 * "Receive Kaspa" with Done; if nothing can be derived, that the spending address is still
 * unlocking. Once the code shows, the bar is the QR page's ("Receive" and the address's balance)
 * with Done trailing, as iOS's sheet keeps its Done.
 *
 * Shown means revealed: the address goes on the Manage Addresses list and is watched for payments.
 * A warmed answer ([ReceiveAddressWarmCache]) is only swapped if it went stale meanwhile - a QR is
 * scanned the instant it appears, so it must not change under a pointed camera for any lesser
 * reason.
 */
@Composable
fun ReceiveFreshScreen(walletViewModel: WalletViewModel, onDone: () -> Unit) {
    val colors = LocalAppColors.current
    val account = walletViewModel.address.value
    var address by remember { mutableStateOf(ReceiveAddressWarmCache.address(account)) }
    var resolving by remember { mutableStateOf(address == null) }
    LaunchedEffect(Unit) {
        walletViewModel.resolveFreshReceiveAddress(revealing = true) { resolved ->
            if (resolved != address) address = resolved
            ReceiveAddressWarmCache.store(account, resolved)
            resolving = false
        }
    }
    val shown = address
    // The page's colour runs up under the status bar and down under the navigation bar.
    Box(
        Modifier
            .fillMaxSize()
            .background(if (shown != null) Color.White else colors.background)
            .systemBarsPadding()
    ) {
        if (shown != null) {
            // A failed lookup leaves the balance unknown ("—") rather than 0 KAS.
            val balanceSompi by produceState<Long?>(initialValue = null, shown) {
                value = runCatching {
                    val coins = walletViewModel.fetchUtxosForCoinControl(shown)
                    com.kachat.app.util.UtxoMath.checkedTotal(coins.map { it.utxoEntry.amount })
                }.getOrNull()
            }
            AddressQrPage(
                address = shown,
                balanceSompi = balanceSompi,
                caption = RECEIVE_FRESH_CAPTION,
                dismiss = QrPageDismiss.DONE,
                onDismiss = onDone,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                // The sheet's bar: "Receive Kaspa" in headline, Done trailing on its glass.
                Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp)) {
                    Text(
                        "Receive Kaspa",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    SheetBarButton(stringResource(R.string.done), onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (resolving) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
                            Text(stringResource(R.string.receive_preparing_fresh_address), color = colors.textSecondary, fontSize = 15.sp)
                        }
                    } else {
                        Text(
                            stringResource(R.string.spending_address_unlocking),
                            color = colors.textSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
    // Android's Back is the sheet's Done.
    androidx.activity.compose.BackHandler(onBack = onDone)
}
