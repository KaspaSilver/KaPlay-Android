package com.kachat.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.ui.theme.iosGlass
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

// ---------------------------------------------------------------------------------------------
// The funding card and its sheet (iOS 1751d20 Views/Shared/FundingSheetView.swift, KaPay
// Android's ZeroBalanceFundingUi.kt): the "not enough funds" popup of a .kachat profile save.
// ---------------------------------------------------------------------------------------------

/**
 * "Fund your chatting address" (iOS `ZeroBalanceFundingCardView`): the headline in subheadline
 * semibold, a 140 QR of [address] on a white plate (padded 8, corners 12 - the white is what keeps
 * it scannable in dark mode), then the address in caption2 monospaced beside a copy button, 14
 * between each, padded 16 on the glass card (corners 20). [title] is the headline; null = "Fund
 * your chatting address". [onCopied] is called after the address is copied so the host can show its
 * own toast; when null the copy icon flips to a checkmark for 1.5 s instead.
 */
@Composable
fun ZeroBalanceFundingCard(
    address: String?,
    modifier: Modifier = Modifier,
    title: String? = null,
    onCopied: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    var showCopiedCheckmark by remember { mutableStateOf(false) }
    LaunchedEffect(showCopiedCheckmark) {
        if (showCopiedCheckmark) {
            delay(1_500)
            showCopiedCheckmark = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosGlass(cornerRadius = 20.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            title ?: "Fund your chatting address",
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        address?.let { shown ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Image(
                    painter = rememberQrBitmapPainter(shown),
                    contentDescription = stringResource(R.string.qr_code),
                    modifier = Modifier.size(140.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = shown,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // doc.on.doc at 14 semibold in the accent colour, padded 6 on a 10-corner glass.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .iosGlass(cornerRadius = 10.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(shown))
                            haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                            if (onCopied != null) {
                                onCopied(shown)
                            } else {
                                showCopiedCheckmark = true
                            }
                        }
                        .padding(6.dp),
                ) {
                    Icon(
                        if (showCopiedCheckmark) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy address",
                        tint = KaspaTeal,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * The funding card in a sheet (iOS `ZeroBalanceFundingSheetView`): the "not enough funds" popup of
 * a .kachat profile save, with that message as [title]. Half height first, pulled up to full
 * (iOS's medium and large detents), for the chatting address. It closes by itself the moment the
 * chatting balance reaches [dismissAtSompi] (any KAS by default) - a change of the balance, not the
 * one it opens with; nothing else pushes a balance update while it is up, so the balance is
 * fetched now and every 10 s. [onDismiss] runs either way (the caller works the fee out again).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroBalanceFundingSheet(
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit,
    title: String? = null,
    dismissAtSompi: Long = 1L,
) {
    val address by walletViewModel.address.collectAsState()
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        while (true) {
            walletViewModel.refreshBalanceAndAwait()
            delay(10_000)
        }
    }
    LaunchedEffect(Unit) {
        walletViewModel.balanceSompi.drop(1).collect { balance ->
            if (balance >= dismissAtSompi) latestDismiss()
        }
    }
    com.kachat.app.ui.theme.IosSheetColors(grouped = false) {
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = { latestDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = LocalAppColors.current.background,
            // iOS `.presentationDragIndicator(.visible)`
            dragHandle = { IosSheetGrabber() },
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 16.dp)
            ) {
                ZeroBalanceFundingCard(address = address, title = title)
            }
        }
    }
}
