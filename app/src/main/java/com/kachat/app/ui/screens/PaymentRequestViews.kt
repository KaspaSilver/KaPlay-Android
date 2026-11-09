package com.kachat.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kachat.app.R
import com.kachat.app.models.PaymentRequest
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.formatFiatAmount
import com.kachat.app.viewmodels.KaspaPriceViewModel
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Making one (iOS 1751d20 Views/Shared/PaymentRequestViews.swift, KaPay Android's)

/** Where a request is paid (iOS `CreatePaymentRequestView.Destination`). */
private enum class PaymentRequestDestination {
    /** A brand-new address, never revealed, used or handed out. */
    FRESH,
    /** One of your addresses, picked from the list: the chatting address or a spending one. */
    CHOSEN,
}

/** Request Payment's steps, in order (iOS `CreatePaymentRequestView.Step`). */
private enum class PaymentRequestStep { DESTINATION, AMOUNT, REVIEW }

/** iOS `.spring(response: 0.4, dampingFraction: 0.9)`: stiffness (2*pi/0.4)^2. */
private val PaymentRequestStepSpring = spring(dampingRatio = 0.9f, stiffness = 247f, visibilityThreshold = IntOffset.VisibilityThreshold)

/** Shares [link] through the system share sheet (iOS `PaymentRequestShareSheet`). */
private fun sharePaymentRequestLink(context: android.content.Context, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/**
 * [CreatePaymentRequestContent] in iOS's sheet, over whatever presents it: the Wallet tab's
 * Request (iOS 1751d20 `walletAction = .request`). Swiping down closes it, as iOS's sheet.
 */
@Composable
fun CreatePaymentRequestSheet(walletViewModel: WalletViewModel, onDismissed: () -> Unit) {
    IosFullSheet(onDismissed = onDismissed, swipeToDismiss = true) { close ->
        CreatePaymentRequestContent(walletViewModel = walletViewModel, onClose = close)
    }
}

/**
 * Request Payment, from the Wallet tab's Request (iOS 1751d20 `CreatePaymentRequestView`, KaPay's):
 * an amount, an optional memo and where it's paid - a brand-new address, or one you pick - handed
 * out as a link (Copy or Share). Opening it in KaPay or KaChat gives Send Kaspa with all of it
 * filled in. Signed by the chatting address, so whoever pays sees who asked.
 *
 * Like a send, in steps: "Where do you want to be paid?" (Fresh Address, or Choose an Address with
 * your addresses listed right on the screen, [OwnAddressList]), "How much?" on the app's keypad
 * ([KaspaKeypadAmountEntry], no Max), then the review with the memo, Copy Payment Request (the
 * bottom button) and Share.
 *
 * Fresh Address (the default) shows the next brand-new slot (derived, not reserved - checked on
 * chain, so the preview never shows a slot already used) and reserves it only when the request is
 * copied or shared: opening the screen and cancelling uses none up. Once per screen: Copy and
 * Share both reuse it, so no two requests share an address.
 *
 * [insets] are the system insets the steps keep clear of: none inside a sheet, the system bars
 * on the "payment_request_create" route.
 */
@Composable
fun CreatePaymentRequestContent(
    walletViewModel: WalletViewModel,
    onClose: () -> Unit,
    insets: WindowInsets = WindowInsets(0, 0, 0, 0),
    priceViewModel: KaspaPriceViewModel = hiltViewModel(),
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val haptic = com.kachat.app.util.rememberHaptics()
    val fiatPriceInCurrency by priceViewModel.currentPriceUsd.collectAsState()
    val fiatCurrencyCode by priceViewModel.currency.collectAsState()
    val chattingAddress by walletViewModel.address.collectAsState()

    var step by remember { mutableStateOf(PaymentRequestStep.DESTINATION) }
    var destination by remember { mutableStateOf(PaymentRequestDestination.FRESH) }
    /** The brand-new address reserved for this request - only once it's copied or shared. */
    var freshAddress by remember { mutableStateOf<String?>(null) }
    /** The next brand-new address, shown before it's reserved. */
    var nextFreshAddress by remember { mutableStateOf<String?>(null) }
    var chosenAddress by remember { mutableStateOf<String?>(null) }
    var amountInput by remember { mutableStateOf("") }
    val fiatAmountState = com.kachat.app.util.rememberKaspaFiatAmountState(onKasTextChange = { amountInput = it })
    var memo by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // derived, not reserved: opening the screen and cancelling uses no address up. Checked on
    // chain, so the preview never shows a slot already used.
    LaunchedEffect(Unit) { nextFreshAddress = walletViewModel.nextFreshSpendingAddress() }

    val amountSompi = KaspaUnit.sompiFromUserText(amountInput)?.takeIf { it > 0 }
    /** Ready to hand out: an amount, and an address (or a fresh one to be made). */
    val canMake = amountSompi != null && (destination == PaymentRequestDestination.FRESH || chosenAddress != null)
    /** The address the request pays: the fresh one (reserved, else the one it will be), or the pick. */
    val payeeAddress = when (destination) {
        PaymentRequestDestination.FRESH -> freshAddress ?: nextFreshAddress
        PaymentRequestDestination.CHOSEN -> chosenAddress
    }
    LaunchedEffect(amountInput, memo, destination, chosenAddress) { copied = false }

    /** To another step; the slide's direction follows the steps' order (iOS `go(to:)`). */
    fun go(next: PaymentRequestStep) {
        focusManager.clearFocus()
        step = next
    }

    /**
     * The request to hand out, unsigned (signed by the chatting address as it goes out - the key
     * is only derived then). A fresh address is reserved here, the first time it's needed. (KaPay
     * subscribes to it here; KaPlay watches the chatting address only, so its payment shows on the
     * next refresh of Manage Addresses.)
     */
    suspend fun makeRequest(): PaymentRequest? {
        val sompi = amountSompi ?: return null
        val address = when (destination) {
            PaymentRequestDestination.FRESH -> freshAddress ?: run {
                val reserved = walletViewModel.reserveFreshSpendingAddress()
                if (reserved == null) {
                    errorMessage = context.getString(R.string.pr_couldnt_make_address)
                    return null
                }
                freshAddress = reserved
                reserved
            }
            PaymentRequestDestination.CHOSEN -> chosenAddress ?: return null
        }
        return PaymentRequest.create(address, sompi, memo, address == chattingAddress)
    }

    /** The request's link, signed by the chatting address: whoever pays sees it's from you, even
     *  when it's paid to a spending address, which has no identity. An account whose key can't be
     *  had still asks; the payer is told it's unsigned. */
    suspend fun signedLink(unsigned: PaymentRequest): String = withContext(Dispatchers.Default) {
        val creator = chattingAddress
        val key = runCatching { hexToBytes(walletViewModel.getPrivateKeyHex()) }.getOrNull()
        val signed = if (creator != null && key != null) {
            try {
                unsigned.signed(creator, key)
            } finally {
                key.fill(0)
            }
        } else null
        (signed ?: unsigned).shareUrlString
    }

    fun copy() {
        isWorking = true
        errorMessage = null
        scope.launch {
            try {
                val unsigned = makeRequest() ?: return@launch
                clipboard.setText(AnnotatedString(signedLink(unsigned)))
                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                copied = true
            } finally {
                isWorking = false
            }
        }
    }

    // Share waits for the fresh address before the share sheet opens.
    fun share() {
        isWorking = true
        errorMessage = null
        scope.launch {
            try {
                val unsigned = makeRequest() ?: return@launch
                sharePaymentRequestLink(context, signedLink(unsigned))
            } finally {
                isWorking = false
            }
        }
    }

    val title = stringResource(R.string.pr_request_payment)

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            // iOS: the next step moves in from the trailing edge, Back brings one in from the leading
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(PaymentRequestStepSpring) { if (forward) it else -it } togetherWith
                slideOutHorizontally(PaymentRequestStepSpring) { if (forward) -it else it })
        },
        label = "requestPaymentStep",
        modifier = Modifier.fillMaxSize(),
    ) { shown ->
        when (shown) {
            // MARK: Where
            PaymentRequestStep.DESTINATION -> SendStepScreen(
                title = title,
                onCancel = onClose,
                canContinue = payeeAddress != null,
                onContinue = { go(PaymentRequestStep.AMOUNT) },
                insets = insets,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SendStepQuestion("Where do you want to be paid?")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectablePill(
                            title = stringResource(R.string.pr_fresh_address),
                            selected = destination == PaymentRequestDestination.FRESH,
                            modifier = Modifier.weight(1f),
                        ) {
                            destination = PaymentRequestDestination.FRESH
                        }
                        SelectablePill(
                            title = stringResource(R.string.pr_choose_an_address),
                            selected = destination == PaymentRequestDestination.CHOSEN,
                            modifier = Modifier.weight(1f),
                        ) {
                            destination = PaymentRequestDestination.CHOSEN
                        }
                    }
                    when (destination) {
                        PaymentRequestDestination.FRESH -> {
                            val fresh = freshAddress ?: nextFreshAddress
                            if (fresh != null) {
                                AddressResolutionCard(address = fresh)
                                Text(
                                    "A brand-new address of yours that has never been used or handed out.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                )
                            } else {
                                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                    com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
                                }
                            }
                        }
                        PaymentRequestDestination.CHOSEN -> {
                            // the list right here, no sheet: tap the address that gets paid
                            Text(stringResource(R.string.pr_pick_address), color = colors.textSecondary, fontSize = 13.sp)
                            OwnAddressList(
                                selected = chosenAddress,
                                walletViewModel = walletViewModel,
                                onPick = { picked -> chosenAddress = picked.address(chattingAddress) },
                            )
                        }
                    }
                }
            }

            // MARK: How much
            PaymentRequestStep.AMOUNT -> SendStepScreen(
                title = title,
                onCancel = onClose,
                canContinue = amountSompi != null,
                onContinue = { go(PaymentRequestStep.REVIEW) },
                onBack = { go(PaymentRequestStep.DESTINATION) },
                continueTitle = "Review",
                insets = insets,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SendStepQuestion("How much?")
                    payeeAddress?.let { address ->
                        SendInfoPill {
                            Text("Paid to")
                            Text("${address.take(14)}...${address.takeLast(6)}", fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                KaspaKeypadAmountEntry(
                    fiatAmountState = fiatAmountState,
                    priceInCurrency = fiatPriceInCurrency,
                    currencyCode = fiatCurrencyCode,
                    onMax = {},
                    showsMax = false,
                    modifier = Modifier.weight(1f),
                )
            }

            // MARK: Review
            PaymentRequestStep.REVIEW -> SendStepScreen(
                title = "Review",
                onCancel = onClose,
                canContinue = canMake && !isWorking,
                onContinue = { copy() },
                onBack = { go(PaymentRequestStep.AMOUNT) },
                continueTitle = stringResource(if (copied) R.string.ab_copied else R.string.pr_copy),
                insets = insets,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("You're requesting", color = colors.textSecondary, fontSize = 15.sp)
                        amountSompi?.let { sompi ->
                            PaymentRequestFitText(
                                "${KaspaUnit.plain(sompi)} ${KaspaUnit.symbol}",
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IosRoundedFontFamily,
                                    fontFeatureSettings = "tnum",
                                ),
                                minScale = 0.5f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            fiatPriceInCurrency?.takeIf { it > 0.0 }?.let { price ->
                                Text(
                                    formatFiatAmount(sompi / 100_000_000.0 * price, fiatCurrencyCode),
                                    color = colors.textSecondary,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Paid to".uppercase(),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AddressResolutionCard(address = payeeAddress)
                    }

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val placeholder = stringResource(R.string.pr_memo_placeholder)
                        BasicTextField(
                            value = memo,
                            onValueChange = { value ->
                                memo = if (PaymentRequest.characterCount(value) > PaymentRequest.MEMO_LIMIT) {
                                    PaymentRequest.takeCharacters(value, PaymentRequest.MEMO_LIMIT)
                                } else value
                            },
                            maxLines = 4,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                            cursorBrush = SolidColor(KaspaTeal),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .sendKaspaGlass(14.dp)
                                .padding(14.dp)
                                .semantics { contentDescription = placeholder },
                            decorationBox = { inner ->
                                Box {
                                    if (memo.isEmpty()) Text(placeholder, color = colors.textTertiary, fontSize = 17.sp)
                                    inner()
                                }
                            },
                        )
                        Text(
                            "${PaymentRequest.characterCount(memo)}/${PaymentRequest.MEMO_LIMIT}",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        stringResource(R.string.pr_memo_shows_note),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    errorMessage?.let {
                        Text(it, color = colors.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }

                    PaymentRequestSharePill(enabled = canMake && !isWorking) { share() }

                    Text(
                        "Anyone who opens it in KaPay or KaChat gets Send Kaspa with this filled in, and sees it was made by your chatting address (signed with its key).",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A private key's hex, as bytes (the caller zeroes them). */
private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0)
    return ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}

/** The review's Share: `Label("Share", systemImage: "square.and.arrow.up")` in the accent colour
 *  on an accent capsule at 15%. */
@Composable
private fun PaymentRequestSharePill(enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(KaspaTeal.copy(alpha = 0.15f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Outlined.IosShare, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.share), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One line that shrinks to fit its width rather than being cut, down to [minScale] of [style]'s
 *  size (iOS `.lineLimit(1).minimumScaleFactor`) - the review's amount. */
@Composable
private fun PaymentRequestFitText(
    text: String,
    style: TextStyle,
    minScale: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidth = constraints.maxWidth
        val scale = remember(text, style, maxWidth) {
            if (maxWidth == Constraints.Infinity) {
                1f
            } else {
                val width = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
                if (width <= maxWidth) 1f else (maxWidth.toFloat() / width).coerceIn(minScale, 1f)
            }
        }
        Text(
            text,
            style = style.copy(fontSize = style.fontSize * scale),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Paying one

/**
 * What a payment request asks for, above the amount on Send Kaspa (iOS 1751d20
 * `PaymentRequestSummary`): the amount, the memo, and who made it - "Requested by" with that
 * address's card once the signature checked out, else "Not signed".
 */
@Composable
fun PaymentRequestSummary(request: PaymentRequest, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sendKaspaGlass(14.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.pr_requested).uppercase(),
            color = KaspaTeal,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${request.amountText} ${KaspaUnit.symbol}",
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (request.memo.isNotEmpty()) {
            Text(request.memo, color = colors.textPrimary, fontSize = 15.sp)
        }

        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))

        val creator = request.creator
        if (creator != null) {
            // Who made it. The signature in the link was checked when it was opened, but it covers
            // the address only: the name on the card comes from the indexer or the KNS API, so no
            // green check sits over it (iOS da59726).
            Text(stringResource(R.string.pr_requested_by), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            AddressResolutionCard(address = creator)
            if (!creator.equals(request.address, ignoreCase = true)) {
                Text(stringResource(R.string.pr_signed_by_their_chatting), color = colors.textSecondary, fontSize = 12.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Text(stringResource(R.string.pr_not_signed), color = colors.warning, fontSize = 12.sp)
            }
        }
    }
}
