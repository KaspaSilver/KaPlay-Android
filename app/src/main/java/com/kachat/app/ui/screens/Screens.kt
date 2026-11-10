package com.kachat.app.ui.screens

import androidx.compose.material.icons.filled.IosShare
import com.kachat.app.util.UserFacingError
import com.kachat.app.util.redactedForLog
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.iosGlass
import com.kachat.app.ui.theme.iosShadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.PhoneEnabled
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.FlowRow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kachat.app.R
import com.kachat.app.models.displayName
import com.kachat.app.models.MessageEntity
import com.kachat.app.models.ReactionEntity
import com.kachat.app.repository.ChatRepository
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.services.KnsInscriptionEngine
import com.kachat.app.services.KnsService
import com.kachat.app.services.UtxoEntry
import com.kachat.app.ui.theme.KaspaSubtext
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaMass
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.ImageMessage
import com.kachat.app.util.ImagePrep
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.MessageReply
import com.kachat.app.util.MessageProtocol
import com.kachat.app.util.copyPrivateKeyWithAutoWipe
import com.kachat.app.util.VoiceMessage
import com.kachat.app.util.VoiceMessageContent
import com.kachat.app.viewmodels.ConnectionStatus as ConnStatus
import com.kachat.app.viewmodels.ConnectionViewModel
import com.kachat.app.viewmodels.NodeInfo
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.kachat.app.util.showAddressCopiedToast
import androidx.compose.material.icons.outlined.InsertDriveFile

/** Classic wood-tone board colors (matches chess.com/lichess's default theme) - shared by the
 *  in-chat thumbnail and the full-screen board in ChessGameScreen.kt. */
val ChessLightSquareColor = Color(0xFFEFD9B4)
val ChessDarkSquareColor = Color(0xFFB58863)

/** Renders a single chess piece glyph with an explicit white/black fill plus a crisp
 *  contrasting outline (four offset copies of the glyph drawn behind the fill, a standard
 *  lightweight text-stroke trick), rather than relying on the bare Unicode glyph's
 *  outline-vs-filled shape alone to distinguish sides - at typical board sizes that distinction
 *  was too subtle to read at a glance, especially on a same-toned square. */
@Composable
fun ChessPieceGlyph(piece: com.kachat.app.util.ChessPiece, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val fillColor = if (piece.color == com.kachat.app.util.ChessColor.WHITE) Color(0xFFFCFCFC) else Color(0xFF121212)
    val outlineColor = if (piece.color == com.kachat.app.util.ChessColor.WHITE) Color(0xFF121212) else Color(0xFFFCFCFC)
    val d = 0.9.dp
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(piece.glyph, fontSize = fontSize, color = outlineColor, modifier = Modifier.offset(x = d))
        Text(piece.glyph, fontSize = fontSize, color = outlineColor, modifier = Modifier.offset(x = -d))
        Text(piece.glyph, fontSize = fontSize, color = outlineColor, modifier = Modifier.offset(y = d))
        Text(piece.glyph, fontSize = fontSize, color = outlineColor, modifier = Modifier.offset(y = -d))
        Text(piece.glyph, fontSize = fontSize, color = fillColor)
    }
}

/**
 * Send Kaspa (iOS `WithdrawKaspaView`): the Wallet tab's Send, an address's own Send, a payment
 * request ([paymentRequest]) - and, in [isCompoundMode], Compound UTXOs for one address. It is
 * KaPay's step-by-step send (iOS 38e0455; the pieces in SendKaspaComponents.kt): To, From
 * ([SendFromStep]: Chatting Address or Choose an Address; skipped when [lockedSource] fixes it, as
 * an address's own Send does - the Wallet tab's Send has it again since iOS 1751d20), Amount on the app's own keypad, a
 * Review that ends in Swipe up to Send ([SwipeUpToSendScreen], which shows the outcome), and the
 * fee's Edit. Compound UTXOs opens on the review. Fees, Max, coin control and the send follow the
 * chosen address: your address signs with the identity key ([WalletViewModel.onSendClicked]), a
 * spending address with its own key ([WalletViewModel.withdrawFromSpendingAddress]). Never the
 * external-QR-signer round trip [ColdSendFlow] uses for watch-only keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAddressSendFlow(
    fromAddress: String,
    balanceSompi: Long,
    title: String,
    viewModel: WalletViewModel,
    onDone: () -> Unit,
    // Compound UTXOs only: the spending-chain index of [fromAddress], or null for the chatting
    // address. Every other send picks its address under From.
    spendingIndex: Int? = null,
    // Pre-fills the recipient with fromAddress itself (a self-send) and auto-fills Max, for the
    // "Compound UTXOs" entry point - merges every UTXO at this address into one. Locks the
    // recipient field instead of just pre-filling it, matching ColdSendFlow's identical
    // isCompoundMode behavior, since editing it away from fromAddress would defeat the point of
    // a compound send.
    isCompoundMode: Boolean = false,
    /**
     * Shown inside a sheet ([IosFullSheet] - every send entry, as iOS presents them): the bar's
     * leading control is "Cancel", as iOS's SpendingAddressWithdrawView and WithdrawKaspaView have
     * it, and the bars take no system insets of their own - the sheet already sits clear of them.
     */
    presentedAsSheet: Boolean = false,
    /** Pre-fills the recipient (Address Book > Send KAS, iOS `WithdrawKaspaView(prefillAddress:)`);
     *  it stays editable. */
    prefillAddress: String? = null,
    /** Opened from one address's history (its Send button, iOS 7a8102b `lockedSource`): the Kaspa
     *  leaves from that address and the From section offers no other. */
    lockedSource: SendSource? = null,
    /** Someone's payment request (iOS 1751d20 `WithdrawKaspaView(paymentRequest:)`): its address
     *  and exact amount are fixed - the send opens on From (or the amount, when From is fixed),
     *  with no recipient step and no keypad. A request pasted or scanned into the recipient field
     *  does the same ([com.kachat.app.models.pastedPaymentTarget]; not in Compound). */
    paymentRequest: com.kachat.app.models.PaymentRequest? = null,
    /** The KAS price for the KAS / your-currency switch (iOS KaspaPriceStore). */
    priceViewModel: com.kachat.app.viewmodels.KaspaPriceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val fiatPriceInCurrency by priceViewModel.currentPriceUsd.collectAsState()
    val fiatCurrencyCode by priceViewModel.currency.collectAsState()
    var recipientInput by remember(fromAddress) { mutableStateOf(prefillAddress.orEmpty()) }
    var amountInput by remember(fromAddress) { mutableStateOf("") }
    val fiatAmountState = com.kachat.app.util.rememberKaspaFiatAmountState(resetKey = fromAddress, onKasTextChange = { amountInput = it })
    var showScanner by remember { mutableStateOf(false) }
    var manualUtxos by remember { mutableStateOf<List<UtxoEntry>?>(null) }
    var showCoinControl by remember { mutableStateOf(false) }
    var isEstimatingMax by remember { mutableStateOf(false) }
    var feeTier by remember { mutableStateOf(ColdFeeTier.NORMAL) }
    var customExtraFeeSompi by remember { mutableStateOf<Long?>(null) }
    // Custom fee, edited in place on the fee row (SendFeeControls), as on iOS.
    var isEditingFee by remember { mutableStateOf(false) }
    var customFeeText by remember { mutableStateOf("") }
    /// The completed send: the review's swipe-up screen shows it.
    var sentTransaction by remember { mutableStateOf<SentTransaction?>(null) }
    /** Why the last send failed, under the swipe-up screen's "Failed". */
    var sendError by remember { mutableStateOf<String?>(null) }
    /** The step on screen; null until the first move (then [initialStep]). */
    var step by remember { mutableStateOf<SendFlowStep?>(null) }
    /** The fee being typed on the fee step, in KAS. */
    var feeDraft by remember { mutableStateOf("") }
    /** The From step's Choose an Address is on (its list shows), before or after a pick. */
    var choosingFrom by remember { mutableStateOf(false) }
    // This presentation's own sends (iOS's per-sheet isSending/sentTransaction): one closed
    // mid-send leaves its send running in the view model, and its outcome is never shown here
    // or on the next send screen.
    val sendId = remember { java.util.UUID.randomUUID().toString() }
    DisposableEffect(sendId) {
        viewModel.sends.open(sendId)
        onDispose { viewModel.sends.close(sendId) }
    }
    val isSending = sendId in viewModel.sends.inFlight.collectAsState().value
    val sendResult = viewModel.sends.results.collectAsState().value[sendId]
    val coroutineScope = rememberCoroutineScope()

    // Where the Kaspa leaves from (the From step): your address by default. Compound UTXOs has no
    // From - it merges [fromAddress] ([spendingIndex]) itself.
    var source by remember { mutableStateOf<SendSource>(lockedSource ?: SendSource.Chatting) }
    /** The From address's balance: the From step's card and the amount's Available and its check. */
    var sourceBalanceSompi by remember { mutableStateOf<Long?>(null) }
    val chattingAddress by viewModel.address.collectAsState()
    val sourceIndex = if (isCompoundMode) spendingIndex else source.spendingIndex
    val sourceAddress = if (isCompoundMode) fromAddress else (source.address(chattingAddress) ?: fromAddress)
    // A quantum-safe (KIP-22) From prices every input with its redeem script too (iOS
    // signatureScriptSize: kip22SignatureScriptSize).
    val sourceSignatureScriptSize = com.kachat.app.services.WalletManager.signatureScriptSize(sourceIndex)

    var isResolvingKns by remember { mutableStateOf(false) }
    var knsResolvedAddress by remember { mutableStateOf<String?>(null) }
    var knsResolvedDomain by remember { mutableStateOf<String?>(null) }
    var knsError by remember { mutableStateOf<String?>(null) }
    // Every service's answer for a typed name and the one in use: the rest go under "Other
    // domains" (iOS 6ac48a7).
    var nameResolutions by remember { mutableStateOf<List<com.kachat.app.services.NameResolution>>(emptyList()) }
    var selectedTld by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }
    // Use one service's answer (the priority one, or a pick from "Other domains").
    val selectResolution: (com.kachat.app.services.NameResolution) -> Unit = { resolution ->
        resolution.address?.let {
            knsResolvedAddress = it
            knsResolvedDomain = resolution.display
            selectedTld = resolution.tld
            knsError = null
        }
    }

    // The fee, as iOS prices it (SendKaspaComponents / SpendingAddressWithdrawView) and as the chat
    // Send KAS sheet already pays it: a base fee at the live quoted rate on the real inputs, plus
    // Fast / Priority / a custom fee as a flat extra on top (SendFeeModel). The send goes out with
    // no rate override (the engine prices the base at the quote) and the extra paid exactly - so a
    // multi-input send no longer scales the chosen fee by its input count.
    //
    // The quote is fetched once when the screen opens; until then the base is priced at the
    // network minimum, the floor of any quote.
    var liveFeeRateSompiPerGram by remember { mutableStateOf<Long?>(null) }
    val baseFeeRateSompiPerGram = liveFeeRateSompiPerGram ?: KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM
    // What automatic selection spends and pays for the amount typed (coin control off) - see the
    // preview LaunchedEffect below. Null while there is no amount, while it is being worked out,
    // or when the coins cannot cover it.
    var feePreview by remember { mutableStateOf<com.kachat.app.util.SendFeeModel.Preview?>(null) }
    var isPreviewingFee by remember { mutableStateOf(false) }
    // The automatic preview's verdict when it can't price this amount: shown under the fee, and
    // the swipe waits (iOS feeError).
    var previewFeeError by remember { mutableStateOf<String?>(null) }
    // Bumped when a send stopped because its fee had changed, or by Try Again under a failed fee
    // estimate: the Review works the fee out again, at a fresh quote (iOS 4922804 `feeRefresh`).
    var feeRefresh by remember { mutableIntStateOf(0) }
    // Coin control's picks as they are now: a coin spent since it was picked (a move, a claim)
    // drops out, and the Review prices what is left - or says none is, rather than pricing coins
    // the send would refuse (iOS 4922804 `resolveReviewSelection`). Null until looked up.
    var liveManualUtxos by remember { mutableStateOf<List<UtxoEntry>?>(null) }
    var isResolvingCoins by remember { mutableStateOf(false) }
    // The amount Max last wrote, and the fee choice, From and coins it left room for: changing
    // any of them while the field still holds that amount works Max out again (see setMaxAmount).
    var maxFill by remember { mutableStateOf<SendMaxFill?>(null) }
    /** Why Max couldn't fill the amount: red under the amount, and on the Review; a key press or
     *  the next Max clears it (iOS 1751d20 `errorMessage` from `setMaxAmount`). */
    var maxError by remember { mutableStateOf<String?>(null) }
    /** The payment request this send pays, if any ([paymentRequest], or one pasted in; iOS
     *  1751d20 `activeRequest`). */
    var activeRequest by remember { mutableStateOf<com.kachat.app.models.PaymentRequest?>(null) }
    /** Fills in a payment request: the address (locked) and the exact amount - what the request
     *  says is what is sent, in whichever unit the field shows (iOS `apply(_:)`). */
    fun applyRequest(request: com.kachat.app.models.PaymentRequest, address: String = request.address) {
        activeRequest = request
        recipientInput = address
        maxFill = null
        fiatAmountState.setExactKas(request.amountText, fiatPriceInCurrency)
    }
    // The one exact parser for typed KAS (iOS 16b64bc): comma or dot, at most 8 decimals.
    val previewAmountSompi = KaspaUnit.sompiFromUserText(amountInput)
    // Base fee on the real inputs: coin control's exact set, else the automatic-selection preview,
    // else (no amount yet) one input.
    // The recipient's real script length when it is known (a P2SH or ECDSA address is a byte
    // longer than the standard one), so the base priced here is the base the send is priced at.
    val feeRecipient = (knsResolvedAddress ?: recipientInput).trim()
    val recipientScriptLen = remember(feeRecipient) {
        if (KaspaAddress.isValid(feeRecipient)) {
            runCatching { KaspaAddress.getScriptPublicKey(feeRecipient).length / 2 }
                .getOrDefault(com.kachat.app.util.SendFeeModel.STANDARD_SCRIPT_LEN)
        } else {
            com.kachat.app.util.SendFeeModel.STANDARD_SCRIPT_LEN
        }
    }
    // Where the change really goes, priced with its real script (iOS da59726 `plannedChangeScript`).
    val changeScriptLen = viewModel.changeScriptLen(sourceIndex, sourceAddress, feeRecipient)
    // The picks still there (all of them until looked up); none left is an error of its own below.
    val manualCoinsGone = !manualUtxos.isNullOrEmpty() && liveManualUtxos?.isEmpty() == true
    val pricedManualUtxos = liveManualUtxos?.takeIf { it.isNotEmpty() } ?: manualUtxos
    val manualInputCount = pricedManualUtxos?.size?.takeIf { it > 0 }
    val defaultFeeSompi = when {
        manualInputCount != null -> com.kachat.app.util.SendFeeModel.baseFee(manualInputCount, baseFeeRateSompiPerGram, recipientScriptLen = recipientScriptLen, changeScriptLen = changeScriptLen, signatureScriptSize = sourceSignatureScriptSize)
        feePreview != null -> feePreview!!.baseFeeSompi
        else -> com.kachat.app.util.SendFeeModel.baseFee(1, baseFeeRateSompiPerGram, changeScriptLen = changeScriptLen, signatureScriptSize = sourceSignatureScriptSize)
    }
    // The preview settled its extra together with the inputs it needs; passing exactly that extra
    // makes the send pick those same inputs and pay the total shown.
    val extraFeeSompi = if (manualInputCount == null && feePreview != null) {
        feePreview!!.extraFeeSompi
    } else {
        com.kachat.app.util.SendFeeModel.extraFee(defaultFeeSompi, feeTier.multiplier, customExtraFeeSompi)
    }
    // Change too small to keep goes to the fee, so the Review's fee and Total count it (iOS 8d5f925),
    // with hand-picked coins too: their change is what is left of them, folded by the engine's rule.
    val foldedChangeSompi = if (manualInputCount == null) {
        feePreview?.foldedChangeSompi ?: 0L
    } else {
        val amount = previewAmountSompi ?: 0L
        val inputAmounts = pricedManualUtxos.orEmpty().map { it.utxoEntry.amount }
        val change = inputAmounts.sum() - amount - defaultFeeSompi - extraFeeSompi
        if (amount > 0 && change > 0 && !KaspaMass.fitsStorageMass(inputAmounts, listOf(amount, change))) change else 0L
    }
    val effectiveFeeSompi = defaultFeeSompi + extraFeeSompi + foldedChangeSompi
    // Hand-picked coins are judged here: too few for the amount and its fee, or a shape the
    // engine refuses (iOS estimates with the coins and shows its error).
    val manualFeeError = if (manualInputCount != null && !isCompoundMode && (previewAmountSompi ?: 0L) > 0L) {
        val amount = previewAmountSompi ?: 0L
        val inputAmounts = pricedManualUtxos.orEmpty().map { it.utxoEntry.amount }
        val change = inputAmounts.sum() - amount - defaultFeeSompi - extraFeeSompi
        // the send's own refusal when every picked coin is gone (iOS 4922804)
        if (manualCoinsGone) SELECTED_COINS_GONE_MESSAGE
        // over what one transaction carries, ahead of the coins' total (iOS da59726)
        else if (inputAmounts.size > com.kachat.app.util.KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION) "Too many inputs selected (${inputAmounts.size}). Kaspa transactions are limited to about ${com.kachat.app.util.KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION} inputs - select fewer, or compound this address first."
        else if (change < 0) INSUFFICIENT_FOR_FEE_MESSAGE
        else com.kachat.app.util.SendFeeModel.sendBlockedMessage(inputAmounts, amount, change)
    } else null
    val feeError = manualFeeError ?: previewFeeError.takeIf { manualInputCount == null }

    // Debounced KNS domain resolution - lets typing "name.kas" here resolve the same way Create
    // Chat's own address field already does. Skipped entirely in compound mode, where the
    // recipient is always the locked self-address, never user-typed.
    val resolveContext = LocalContext.current
    LaunchedEffect(recipientInput) {
        knsResolvedAddress = null
        knsResolvedDomain = null
        knsError = null
        nameResolutions = emptyList()
        selectedTld = null
        if (isCompoundMode) {
            isResolvingKns = false
            return@LaunchedEffect
        }
        val trimmed = recipientInput.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("kaspa:", ignoreCase = true) ||
            trimmed.startsWith("kaspatest:", ignoreCase = true) || !com.kachat.app.services.NameServicesClient.looksLikeName(trimmed)
        ) {
            isResolvingKns = false
            return@LaunchedEffect
        }
        isResolvingKns = true
        kotlinx.coroutines.delay(500)
        // Every name service, in priority: the ending typed, else .kachat only (the others wait
        // under Other domains, iOS 5a5122d). The resolved line names which one answered; the
        // others are offered under "Other domains" (iOS 6ac48a7).
        val results = viewModel.resolveEverywhere(trimmed)
        nameResolutions = results
        isResolvingKns = false
        val primary = com.kachat.app.services.NameServicesClient.primary(results, trimmed)
        if (primary?.address != null) {
            selectResolution(primary)
        } else {
            knsError = com.kachat.app.services.NameServicesClient.notFoundMessage(resolveContext, trimmed, results)
        }
    }

    // Automatic selection's preview (coin control off): which coins this amount needs and what
    // they cost, so the fee shown is priced on the real inputs rather than an assumed one -
    // mirrors ColdSendFlow's previewSelection. Debounced; Send waits for it, so it never goes out
    // with an extra worked out for a different amount.
    LaunchedEffect(previewAmountSompi, baseFeeRateSompiPerGram, feeTier, customExtraFeeSompi, manualUtxos, sourceAddress, recipientScriptLen, changeScriptLen, sourceSignatureScriptSize, feeRefresh) {
        feePreview = null
        previewFeeError = null
        if (manualUtxos.isNullOrEmpty() && previewAmountSompi != null && previewAmountSompi > 0) {
            isPreviewingFee = true
            kotlinx.coroutines.delay(400)
            var failure: Exception? = null
            feePreview = try {
                viewModel.previewAutomaticSelection(
                    address = sourceAddress,
                    amountSompi = previewAmountSompi,
                    feeRateSompiPerGram = baseFeeRateSompiPerGram,
                    feeMultiplier = feeTier.multiplier,
                    customExtraFeeSompi = customExtraFeeSompi,
                    recipientScriptLen = recipientScriptLen,
                    signatureScriptSize = sourceSignatureScriptSize,
                    changeScriptLen = changeScriptLen,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SendFlow", "Fee preview failed for ${sourceAddress.redactedForLog()}", e)
                failure = e
                null
            }
            // Compound mode sweeps everything to itself and is priced on its own.
            if (!isCompoundMode) {
                previewFeeError = feePreview?.blockedMessage ?: if (feePreview == null) {
                    failure?.let { com.kachat.app.util.UserFacingError.message(it, INSUFFICIENT_FOR_FEE_MESSAGE) }
                        ?: INSUFFICIENT_FOR_FEE_MESSAGE
                } else null
            }
        }
        isPreviewingFee = false
    }

    // The picked coins looked up again (see liveManualUtxos): when coin control changes, and on a
    // fee refresh. A failed lookup prices the picks as they are; the send resolves them anyway.
    LaunchedEffect(manualUtxos, sourceAddress, feeRefresh) {
        liveManualUtxos = null
        val picked = manualUtxos?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        isResolvingCoins = true
        try {
            val fresh = viewModel.fetchUtxosForCoinControl(sourceAddress).map { it.outpoint }.toSet()
            liveManualUtxos = picked.filter { it.outpoint in fresh }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SendFlow", "Couldn't look up the picked coins for ${sourceAddress.redactedForLog()}", e)
        } finally {
            isResolvingCoins = false
        }
    }

    // A fee refresh asks for the network's rate again too: a send stopped because the rate rose
    // would otherwise be priced at the old one and stop again (iOS 4922804).
    LaunchedEffect(feeRefresh) {
        if (feeRefresh > 0) liveFeeRateSompiPerGram = viewModel.fetchQuotedFeeRateSompiPerGram()
    }

    LaunchedEffect(Unit) {
        if (isCompoundMode) {
            recipientInput = fromAddress
            isEstimatingMax = true
        } else if (paymentRequest != null && activeRequest == null) {
            applyRequest(paymentRequest)
        }
        // The live quote, once (see baseFeeRateSompiPerGram). Falls back to the network minimum
        // on any failure.
        val quotedRate = viewModel.fetchQuotedFeeRateSompiPerGram()
        liveFeeRateSompiPerGram = quotedRate
        if (isCompoundMode) {
            try {
                // Consolidation is bounded by Kaspa's per-transaction mass cap, so pin the send to
                // one transaction's worth of UTXOs (largest-first, ≤MAX_INPUTS_PER_TRANSACTION) and
                // estimate Max over exactly those. This is why Max works even when the address has
                // more UTXOs than a single tx can hold - it consolidates one batch; repeat to reduce.
                val chunk = viewModel.consolidationChunk(fromAddress, quotedRate, feeTier.multiplier, customExtraFeeSompi, signatureScriptSize = sourceSignatureScriptSize)
                when {
                    // iOS 1751d20's errors from `estimateMaxWithdrawalAmount`, shown on the Review
                    chunk == null -> { maxError = MAX_NO_SPENDABLE_MESSAGE }
                    chunk.amountSompi <= 0L -> { maxError = MAX_FEE_TOO_HIGH_MESSAGE }
                    else -> {
                        manualUtxos = chunk.utxos
                        fiatAmountState.setMaxKas(chunk.amountSompi / 100_000_000.0, fiatPriceInCurrency)
                        maxFill = SendMaxFill(com.kachat.app.util.formatKasAmount(chunk.amountSompi / 100_000_000.0), feeTier, customExtraFeeSompi, source, chunk.utxos, chunk.cappedAtInputLimit)
                        maxError = null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the field untouched on failure - same as the Max button itself.
                maxError = UserFacingError.message(e, MAX_FAILED_FALLBACK)
            } finally {
                isEstimatingMax = false
            }
        }
    }

    // How the send ended, for the review's swipe-up screen: the transaction it shows, or the error
    // under "Failed" (the confirmation is the swipe-up's own full screen, iOS 38e0455).
    LaunchedEffect(sendResult) {
        val result = sendResult ?: return@LaunchedEffect
        if (result.isSuccess) {
            result.getOrNull()?.let { txId ->
                sentTransaction = SentTransaction(
                    txId = txId,
                    amountSompi = KaspaUnit.sompiFromUserText(amountInput)?.takeIf { it > 0 },
                    // A compound is a self-send: naming the address it just came from reads as a
                    // mistake, so it just says how much moved. Else the domain when one was
                    // resolved, otherwise the address itself.
                    recipient = if (isCompoundMode) null else (knsResolvedDomain ?: recipientInput.trim()),
                )
            }
        } else {
            // a sentence, not the exception's log text; else the swipe-up's own fallback (iOS
            // 1751d20 `UserFacingError.message(for:)`, `SwipeUpLabels.failedFallback`)
            sendError = UserFacingError.message(result.exceptionOrNull(), context.getString(R.string.swipe_send_failed_fallback))
            // nothing went out; Try Again goes back to a Review with the fee of today's coins
            // (iOS 4922804)
            if (result.exceptionOrNull() is com.kachat.app.util.SendFeeChangedException) feeRefresh += 1
        }
        viewModel.sends.consume(sendId)
    }

    // Coin control is a sheet over the send, as iOS's SpendingAddressWithdrawView and
    // WithdrawKaspaView present CoinControlView (the recipient scanner: below, by the steps).
    if (showCoinControl) {
        CoinControlSheet(
            fromAddress = sourceAddress,
            fetchUtxos = { addr -> viewModel.fetchUtxosForCoinControl(addr) },
            initialSelection = manualUtxos,
            onDone = { selection -> manualUtxos = selection },
            onDismiss = { showCoinControl = false },
            fetchCoins = { addr -> viewModel.fetchCoinsForCoinControl(addr) },
        )
    }

    val amountSompi = KaspaUnit.sompiFromUserText(amountInput)
    // Only an address of the network the app runs on: the other network's is the same key on
    // another chain, refused with its own reason under the field (iOS ce20e87).
    val isValidAddress = remember(recipientInput) { KaspaAddress.isValidOnActiveNetwork(recipientInput.trim()) }
    val effectiveAddress = knsResolvedAddress ?: recipientInput
    val hasValidRecipient = if (knsResolvedAddress != null) true else (isValidAddress && !isResolvingKns)

    // Max: fee-aware, from the funds this send actually spends. Compound pins one mass-safe
    // transaction's worth of UTXOs (largest-first, <=cap) as the input set - see the compound
    // LaunchedEffect above.
    val setMaxAmount: () -> Unit = {
        coroutineScope.launch {
            isEstimatingMax = true
            // A failure is said in red under the amount (and on the Review), not in a toast
            // (iOS 1751d20 `setMaxAmount`: `errorMessage`).
            maxError = null
            try {
                // Room for the base fee and the Fast / Priority / custom extra, the extra derived
                // from Max's own base - never from a fee estimated for whatever amount was typed
                // before, which is how Max used to leave too little room for the extra.
                val maxTier = feeTier
                val maxCustomExtra = customExtraFeeSompi
                if (isCompoundMode) {
                    // the coins Coin Control picked, when it did; else the largest that fit (iOS da59726)
                    val chunk = viewModel.consolidationChunk(fromAddress, liveFeeRateSompiPerGram, maxTier.multiplier, maxCustomExtra, signatureScriptSize = sourceSignatureScriptSize, selection = manualUtxos)
                    when {
                        chunk == null -> { maxError = MAX_NO_SPENDABLE_MESSAGE }
                        chunk.amountSompi <= 0L -> { maxError = MAX_FEE_TOO_HIGH_MESSAGE }
                        else -> {
                            manualUtxos = chunk.utxos
                            fiatAmountState.setMaxKas(chunk.amountSompi / 100_000_000.0, fiatPriceInCurrency)
                            maxFill = SendMaxFill(com.kachat.app.util.formatKasAmount(chunk.amountSompi / 100_000_000.0), maxTier, maxCustomExtra, source, chunk.utxos, chunk.cappedAtInputLimit)
                        }
                    }
                } else {
                    // null = the wallet cannot answer yet (the REST client is created a moment
                    // after launch); 0 = it answered, and there's no coin or the fee eats them.
                    // Neither may write "0" into the field, which read as a dead button.
                    val estimate = viewModel.estimateMaxSend(sourceAddress, liveFeeRateSompiPerGram, manualUtxos, maxTier.multiplier, maxCustomExtra, signatureScriptSize = sourceSignatureScriptSize, changeScriptLen = changeScriptLen)
                    when {
                        estimate == null -> { maxError = MAX_NOT_READY_MESSAGE }
                        !estimate.hasCoins -> { maxError = MAX_NO_SPENDABLE_MESSAGE }
                        estimate.amountSompi <= 0L -> { maxError = MAX_FEE_TOO_HIGH_MESSAGE }
                        else -> {
                            val maxSompi = estimate.amountSompi
                            fiatAmountState.setMaxKas(maxSompi / 100_000_000.0, fiatPriceInCurrency)
                            maxFill = SendMaxFill(com.kachat.app.util.formatKasAmount(maxSompi / 100_000_000.0), maxTier, maxCustomExtra, source, manualUtxos, estimate.cappedAtInputLimit)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Said out loud, not swallowed. A silent catch here is indistinguishable from a
                // button that does nothing, which is exactly how this was reported - and it left
                // no trace in the log to diagnose it from either.
                Log.w("SendFlow", "Max estimate failed for ${sourceAddress.redactedForLog()}", e)
                maxError = UserFacingError.message(e, MAX_FAILED_FALLBACK)
            } finally {
                isEstimatingMax = false
            }
        }
    }
    // The fee, the From or the coins changed after Max filled the field (a speed picked, a custom
    // fee set, another address, coin control): Max is worked out again, or the send would need
    // more than the coins hold (iOS `usedMax`). Only while the field still holds what Max wrote -
    // an amount typed since is left alone.
    LaunchedEffect(feeTier, customExtraFeeSompi, source, manualUtxos) {
        val filled = maxFill ?: return@LaunchedEffect
        if (filled.amount == amountInput &&
            (filled.tier != feeTier || filled.customExtra != customExtraFeeSompi || filled.source != source || filled.utxos != manualUtxos)
        ) {
            setMaxAmount()
        }
    }
    // Not while the fee preview is being worked out: the send passes the preview's extra.
    val canSend = !isSending && hasValidRecipient && (amountSompi ?: 0) > 0 && !isPreviewingFee &&
        !isEstimatingMax && !isResolvingCoins && feeError == null
    val send: () -> Unit = {
        sendError = null
        maxError = null
        amountSompi?.takeIf { canSend }?.let {
            val recipient = effectiveAddress.trim()
            // what the Review shows: a send rebuilt from other coins that would pay more stops
            // (iOS 4922804 `expectedFeeSompi`)
            val expectedFee = effectiveFeeSompi
            // No rate override: the engine prices the base at the live quote, and the speed or
            // custom fee goes on top as the flat extra shown (SendFeeModel).
            if (sourceIndex != null) {
                viewModel.withdrawFromSpendingAddress(sendId, sourceIndex, recipient, it, feeRateOverride = null, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi, expectedFeeSompi = expectedFee)
            } else {
                viewModel.onSendClicked(sendId, recipient, it, feeRateOverride = null, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi, expectedFeeSompi = expectedFee)
            }
        }
    }

    // The steps of a send (iOS 38e0455 `WithdrawKaspaView`): who it goes to, where from, how much
    // (on the app's own keypad, so no keyboard stays up), and a review where only the fee and coin
    // control can change before swiping up to send. Compound UTXOs opens on the review (its
    // recipient and amount are set), a payment request or a pre-filled recipient on From (or the
    // amount, when From is fixed).
    /** The step after To: From, unless the send's From is fixed (an address's own Send). */
    val afterRecipient = if (lockedSource == null) SendFlowStep.FROM else SendFlowStep.AMOUNT
    val initialStep = when {
        isCompoundMode -> SendFlowStep.REVIEW
        paymentRequest != null || prefillAddress != null -> afterRecipient
        else -> SendFlowStep.RECIPIENT
    }
    val currentStep = step ?: initialStep
    val insets = if (presentedAsSheet) WindowInsets(0, 0, 0, 0) else WindowInsets.systemBars
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    /** Where Back goes from a step; null for the first one. A payment request handed in has no
     *  recipient step (its address is fixed, iOS 1751d20). */
    fun backStep(from: SendFlowStep): SendFlowStep? = when (from) {
        SendFlowStep.RECIPIENT -> null
        SendFlowStep.FROM -> if (paymentRequest != null) null else SendFlowStep.RECIPIENT
        SendFlowStep.AMOUNT -> when {
            lockedSource == null -> SendFlowStep.FROM
            isCompoundMode || paymentRequest != null -> null
            else -> SendFlowStep.RECIPIENT
        }
        SendFlowStep.REVIEW -> if (isCompoundMode) null else SendFlowStep.AMOUNT
        SendFlowStep.FEE -> SendFlowStep.REVIEW
    }
    fun go(to: SendFlowStep) {
        focusManager.clearFocus()
        if (to == SendFlowStep.RECIPIENT && activeRequest != null && paymentRequest == null) {
            // back from a pasted request: start the recipient over (iOS 1751d20 `go(to:)`)
            activeRequest = null
            recipientInput = ""
            fiatAmountState.onDisplayTextChange("", fiatPriceInCurrency)
            amountInput = ""
            maxFill = null
        }
        step = to
    }
    // A payment request pasted or scanned in fills in everything it carries - when the input is
    // just that link: one inside other text isn't necessarily the one meant, and it would set the
    // amount unseen - and moves on from To (iOS 1751d20 `handleInputChange`, as KaPay and KaPost).
    val onRecipientInput: (String) -> Unit = { input ->
        val pasted = if (isCompoundMode) null else com.kachat.app.models.pastedPaymentTarget(input.trim())
        if (pasted != null) {
            applyRequest(pasted.request, pasted.address)
            if (currentStep == SendFlowStep.RECIPIENT) go(afterRecipient)
        } else {
            recipientInput = input
        }
    }
    // The recipient scanner is a sheet over the send, as iOS's WithdrawKaspaView presents
    // QRScannerView.
    if (showScanner) {
        QrScannerSheet(
            onScanned = { scanned -> onRecipientInput(KaspaAddress.fromScanned(scanned)) },
            onDismiss = { showScanner = false }
        )
    }
    val screenTitle = when {
        isCompoundMode -> stringResource(R.string.compound_utxos)
        activeRequest != null -> "Payment Request"
        else -> title
    }
    val availableSompi = if (isCompoundMode) balanceSompi else sourceBalanceSompi
    /** The amount is more than the From address holds. */
    val exceedsBalance = amountSompi != null && availableSompi != null && amountSompi > availableSompi
    /** Why Max isn't the whole balance, while Max still holds the amount: one transaction spends
     *  at most [com.kachat.app.util.KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] coins, and this
     *  address holds more (iOS 1751d20 `usedMax && maxCapped`, `maxCappedNote`). */
    val maxCappedNote = maxFill?.takeIf { it.capped && it.amount == amountInput }?.let {
        val limit = com.kachat.app.util.KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION
        if (isCompoundMode) stringResource(R.string.send_compound_capped, limit, limit)
        else stringResource(R.string.send_max_capped, limit)
    }
    val canContinueFromAmount = hasValidRecipient && (amountSompi ?: 0) > 0 && !exceedsBalance && !isEstimatingMax
    /** The recipient's name: the domain it resolved from. */
    val recipientName = knsResolvedDomain
    val sourceTitle = if (isCompoundMode) stringResource(R.string.send_this_address) else sendSourceTitle(source)
    // the From address's balance: the amount's Available and its check. What the send can spend
    // (its own coins, KaspaWalletEngine.fetchUtxos): coins a move or send just spent, or not yet
    // usable, aren't Available - an amount within the whole balance but over these could never go
    // (iOS 4922804). Left unknown when the coins can't be read.
    LaunchedEffect(source, chattingAddress) {
        if (isCompoundMode) return@LaunchedEffect
        sourceBalanceSompi = null
        val address = source.address(chattingAddress) ?: return@LaunchedEffect
        val spendable = try {
            viewModel.fetchUtxosForCoinControl(address)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
        sourceBalanceSompi = com.kachat.app.util.UtxoMath.checkedTotal(spendable.map { it.utxoEntry.amount })
    }

    SendStepTransition(currentStep, Modifier.fillMaxSize()) { shown ->
        when (shown) {
            SendFlowStep.RECIPIENT -> SendStepScreen(
                title = screenTitle,
                onCancel = onDone,
                canContinue = hasValidRecipient,
                onContinue = { go(afterRecipient) },
                insets = insets,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SendStepQuestion(stringResource(R.string.send_who_to))
                    SendRecipientCard(
                        input = recipientInput,
                        onInputChange = onRecipientInput,
                        isResolving = isResolvingKns,
                        resolvedAddress = knsResolvedAddress,
                        resolvedName = knsResolvedDomain,
                        nameResolutions = nameResolutions,
                        selectedTld = selectedTld,
                        onSelectResolution = selectResolution,
                        lookupError = knsError,
                        isValidAddress = isValidAddress,
                        onScan = { showScanner = true },
                        focusOnAppear = true,
                    )
                }
            }

            SendFlowStep.FROM -> SendFromStep(
                title = screenTitle,
                source = source,
                onSourceChange = { picked ->
                    if (picked != source) {
                        source = picked
                        // coins picked on one address can't pay from another
                        manualUtxos = null
                    }
                },
                choosingFrom = choosingFrom,
                onChoosingFromChange = { choosingFrom = it },
                availableSompi = sourceBalanceSompi,
                walletViewModel = viewModel,
                onBack = backStep(SendFlowStep.FROM)?.let { back -> { go(back) } },
                onCancel = onDone,
                onContinue = { go(SendFlowStep.AMOUNT) },
                insets = insets,
            )

            SendFlowStep.AMOUNT -> SendStepScreen(
                title = screenTitle,
                onBack = backStep(SendFlowStep.AMOUNT)?.let { back -> { go(back) } },
                onCancel = onDone,
                continueTitle = stringResource(R.string.send_review),
                canContinue = canContinueFromAmount,
                onContinue = { go(SendFlowStep.REVIEW) },
                insets = insets,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SendStepChip(stringResource(R.string.to), recipientName ?: effectiveAddress.trim(), isAddress = recipientName == null)
                    SendStepChip(stringResource(R.string.from), sourceTitle)
                }
                val available = availableSompi
                val request = activeRequest
                if (request != null) {
                    // A request's amount is fixed: no keypad, the request itself instead (iOS 1751d20).
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        SendReviewAmount(
                            amountSompi = amountSompi,
                            priceInCurrency = fiatPriceInCurrency,
                            currencyCode = fiatCurrencyCode,
                            label = stringResource(R.string.send_youre_sending),
                        )
                        PaymentRequestSummary(request = request)
                        if (exceedsBalance) {
                            Text(
                                stringResource(R.string.send_exceeds_source),
                                color = LocalAppColors.current.danger,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                KaspaKeypadAmountEntry(
                    fiatAmountState = fiatAmountState,
                    priceInCurrency = fiatPriceInCurrency,
                    currencyCode = fiatCurrencyCode,
                    isEstimatingMax = isEstimatingMax,
                    maxEnabled = hasValidRecipient,
                    onMax = setMaxAmount,
                    // what's wrong with the amount, else why Max is short of the balance, else
                    // what's available (iOS 1751d20 `amountFootnote`)
                    footnote = when {
                        maxError != null -> maxError
                        exceedsBalance -> stringResource(R.string.send_exceeds_source)
                        maxCappedNote != null -> maxCappedNote
                        available != null -> KaspaUnit.label(stringResource(R.string.send_available_kas, KaspaUnit.plain(available)))
                        else -> null
                    },
                    footnoteIsError = exceedsBalance || maxError != null,
                    // a typed key: the amount is no longer Max's (iOS 1751d20 `onKeyTyped`)
                    onKeyTyped = {
                        maxFill = null
                        maxError = null
                    },
                    modifier = Modifier.weight(1f),
                )
                }
            }

            SendFlowStep.REVIEW -> SwipeUpToSendScreen(
                screenTitle = if (isCompoundMode) stringResource(R.string.compound_utxos) else stringResource(R.string.send_review),
                onCancel = onDone,
                onBack = backStep(SendFlowStep.REVIEW)?.let { back -> { go(back) } },
                barTitle = stringResource(if (isCompoundMode) R.string.swipe_up_to_consolidate else R.string.swipe_up_to_send),
                isBusy = isSending,
                isEnabled = canSend,
                amountSompi = amountSompi,
                recipient = if (isCompoundMode) null else (knsResolvedDomain ?: effectiveAddress.trim()).takeIf { it.isNotEmpty() },
                sent = sentTransaction,
                failureMessage = sendError,
                explorerUrl = { kaspaExplorer.txUrl(it) },
                onDone = {
                    sentTransaction = null
                    onDone()
                },
                onSwipe = send,
                insets = insets,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SendReviewAmount(
                        amountSompi = amountSompi,
                        priceInCurrency = fiatPriceInCurrency,
                        currencyCode = fiatCurrencyCode,
                        label = stringResource(if (isCompoundMode) R.string.send_consolidating else R.string.send_youre_sending),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val fromAddressShown = if (isCompoundMode) fromAddress else source.address(chattingAddress)
                    SendReviewCard {
                        SendReviewRow(stringResource(R.string.to)) {
                            if (isCompoundMode) {
                                SendReviewValue(stringResource(R.string.send_this_address_consolidating))
                            } else {
                                SendReviewValue(recipientName, effectiveAddress.trim())
                            }
                        }
                        SendReviewDivider()
                        SendReviewRow(stringResource(R.string.from)) {
                            SendReviewValue(
                                sourceTitle,
                                fromAddressShown?.let { "${it.take(14)}...${it.takeLast(6)}" },
                                middleEllipsis = false,
                            )
                        }
                        SendReviewDivider()
                        SendReviewRow(stringResource(R.string.total)) {
                            val amount = amountSompi
                            when {
                                isPreviewingFee || isEstimatingMax || isResolvingCoins -> com.kachat.app.ui.theme.IosActivityIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = LocalAppColors.current.textSecondary,
                                    strokeWidth = 2.dp,
                                )
                                // no fee worked out, no Total (iOS 1751d20: `totalFeeSompi` nil)
                                amount != null && feeError == null -> SendReviewValue("${KaspaUnit.plain(amount + effectiveFeeSompi)} ${KaspaUnit.symbol}")
                                else -> Text("—", color = LocalAppColors.current.textSecondary, fontSize = 15.sp)
                            }
                        }
                    }

                    // the request this send pays (iOS 1751d20)
                    activeRequest?.let { PaymentRequestSummary(request = it) }

                    // The only things that can still change: the fee and the coins it spends.
                    SendFeeControls(
                        feeTier = feeTier,
                        onFeeTierChange = {
                            feeTier = it
                            customExtraFeeSompi = null
                            isEditingFee = false
                        },
                        isEditingFee = false,
                        customFeeText = customFeeText,
                        onCustomFeeTextChange = { customFeeText = it },
                        isEstimatingFee = isPreviewingFee || isResolvingCoins,
                        // a fee that couldn't be worked out shows "—" and Edit greys out (iOS
                        // 1751d20 `feeText: totalFeeSompi.map`, nil with a feeError)
                        feeText = if (feeError == null) "${ChatRepository.formatKas(effectiveFeeSompi)} ${KaspaUnit.symbol}" else null,
                        onStartEditing = {},
                        onCommit = {},
                        coinControlSummary = coinControlSummary(manualUtxos),
                        onCoinControl = { if (!isSending) showCoinControl = true },
                        onEditFee = {
                            if (feeError == null) {
                                feeDraft = KaspaUnit.plain(effectiveFeeSompi)
                                go(SendFlowStep.FEE)
                            }
                        },
                    )

                    val reviewError = sendError ?: maxError ?: feeError
                    if (reviewError != null) {
                        Text(reviewError, color = LocalAppColors.current.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                    } else if (maxCappedNote != null) {
                        // why Max is short of the balance (iOS 1751d20 `usedMax && maxCapped`)
                        Text(maxCappedNote, color = LocalAppColors.current.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    // Without a fee the swipe stays off: a dropped connection mustn't strand it
                    // (iOS 4922804 Try Again).
                    if (sendError == null && maxError == null && feeError != null && !isPreviewingFee && !isResolvingCoins) {
                        Text(
                            stringResource(R.string.try_again),
                            color = KaspaTeal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { feeRefresh += 1 },
                        )
                    }
                }
            }

            SendFlowStep.FEE -> SendFeeEditorScreen(
                draft = feeDraft,
                onDraftChange = { feeDraft = it },
                minimumSompi = defaultFeeSompi,
                onCancel = { go(SendFlowStep.REVIEW) },
                onConfirm = { typed ->
                    // never under the network's minimum (iOS commitCustomFee)
                    customExtraFeeSompi = (typed - defaultFeeSompi).coerceAtLeast(0L)
                    go(SendFlowStep.REVIEW)
                },
                insets = insets,
            )
        }
    }
}

/** The steps of [SpendingAddressSendFlow], in order (iOS `WithdrawKaspaView.Step`). */
private enum class SendFlowStep { RECIPIENT, FROM, AMOUNT, REVIEW, FEE }

/** What Max last wrote into the amount, and the fee, From and coins it left room for. */
private data class SendMaxFill(
    val amount: String,
    val tier: ColdFeeTier,
    val customExtra: Long?,
    val source: SendSource,
    val utxos: List<UtxoEntry>?,
    /** One transaction's input cap held Max under the whole balance (iOS 1751d20 `maxCapped`). */
    val capped: Boolean = false,
)

/**
 * On-chain transaction history + UTXOs for the wallet's identity/chatting address, with direct
 * Send/Receive/Export - reached from the Profile screen's "Chatting Address" section's "Manage
 * Address" row. Field-for-field the same screen as [SpendingAddressTxHistoryScreen] (balance
 * header, Transaction History/UTXOs tabs, Compound UTXOs, Export, Explorer, Receive/Send), just
 * for the single fixed identity address instead of one spending-chain index - reuses the same
 * address-keyed StateFlows/functions on [WalletViewModel] (addressPageLists,
 * getSpendingUtxoLabels, etc. - all keyed by address string, nothing spending-chain-specific
 * about them) rather than duplicating a second copy of this state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityAddressDetailScreen(
    /** Null on the Wallet tab, which is not pushed into: the connection dot takes its place. */
    onBack: (() -> Unit)?,
    viewModel: WalletViewModel,
    /** Opened as the root of the "Received" History sheet (iOS 13046ad `OwnAddressHistorySheet`):
     *  Done closes it, top left, in place of the back arrow. */
    closesWithDone: Boolean = false,
    title: String = "Chatting Address",
    /** The Wallet tab's: its backup cards lead to the backup steps. */
    navController: NavController? = null,
) {
    val address by viewModel.address.collectAsState()
    val balanceSompi by viewModel.balanceSompi.collectAsState()
    val balanceKnown by viewModel.balanceKnown.collectAsState()

    // This address's own lists, never another page's (see WalletViewModel.AddressPageLists).
    val allAddressLists by viewModel.addressPageLists.collectAsState()
    val pageLists = allAddressLists[address.orEmpty()] ?: WalletViewModel.AddressPageLists()
    val txHistory = pageLists.txHistory
    val isLoadingTxHistory = pageLists.txHistoryLoading
    val txHistoryFailed = pageLists.txHistoryFailed
    val utxos = pageLists.utxos
    val isLoadingUtxos = pageLists.utxosLoading
    val utxosLoadError = pageLists.utxosError
    val utxoListAddress: String? = (address as String?)?.takeIf { it.isNotEmpty() }
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    // The identity key is at least as sensitive as the seed phrase itself (it IS the wallet's
    // main spending key) - gated behind the same biometric flag Settings > View Seed Phrase
    // uses, not the lower-stakes biometricSpendingKeyEnabled a derived spending address uses.
    val biometricSeedPhraseEnabled by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showCompoundFlow by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    // The "Address Actions" sheet, and the public-key screen it can open.
    var showAddressActions by remember { mutableStateOf(false) }
    var showPublicKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var labelingUtxoKey by remember { mutableStateOf<String?>(null) }
    var labelInput by remember { mutableStateOf("") }

    LaunchedEffect(address) {
        val addr = address
        if (!addr.isNullOrEmpty()) {
            viewModel.loadSpendingAddressTxHistory(addr)
            viewModel.loadSpendingAddressUtxos(addr)
            utxoLabels = viewModel.getSpendingUtxoLabels(addr)
        }
    }
    // The coins moved (a payment in, a move or send out): the UTXOs and History follow the live
    // balance. The history comes from the indexer, which can be a moment behind the node, so it
    // is asked again a few seconds later (iOS 7a65fab).
    var balanceSeen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(balanceSompi) {
        val addr = address
        val previous = balanceSeen
        balanceSeen = balanceSompi
        if (previous == null || previous == balanceSompi || addr.isNullOrEmpty()) return@LaunchedEffect
        viewModel.loadSpendingAddressUtxos(addr)
        viewModel.loadSpendingAddressTxHistory(addr)
        kotlinx.coroutines.delay(4_000)
        viewModel.loadSpendingAddressTxHistory(addr)
    }

    val flowAddress = address
    // Send and Compound are sheets over this screen that only Cancel closes, as iOS presents
    // WithdrawKaspaView (.interactiveDismissDisabled()).
    if (showWithdraw && flowAddress != null) {
        IosFullSheet(
            onDismissed = {
                showWithdraw = false
                viewModel.loadSpendingAddressTxHistory(flowAddress)
                viewModel.loadSpendingAddressUtxos(flowAddress)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = flowAddress,
                balanceSompi = balanceSompi,
                title = "Send Kaspa",
                viewModel = viewModel,
                onDone = close,
                presentedAsSheet = true,
                lockedSource = SendSource.Chatting,
            )
        }
    }

    if (showCompoundFlow && flowAddress != null) {
        IosFullSheet(
            onDismissed = {
                showCompoundFlow = false
                viewModel.loadSpendingAddressTxHistory(flowAddress)
                viewModel.loadSpendingAddressUtxos(flowAddress)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = flowAddress,
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
        containerColor = LocalAppColors.current.background,
        // Runs on under Receive / Send and the dock, as iOS's list runs on under its
        // safeAreaInset bar and tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // The Wallet tab (iOS ChessWalletView): the connection dot, the balance, the backup strip
            // and the fixed title.
            if (onBack == null) ChessHomeBar(onBack = null, title = title, showsBackupBanner = true) else CenterAlignedTopAppBar(
                title = { Text(title, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // At the root of the "Received" History sheet: Done, as iOS's
                    // OwnAddressHistorySheet puts it (13046ad); a back arrow everywhere else.
                    if (closesWithDone) {
                        SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    } else {
                        SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                // The one options button of every address page (iOS da59726).
                actions = { AddressOptionsButton(onClick = { showAddressActions = true }, enabled = !address.isNullOrEmpty()) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            // The Wallet tab has Send and Receive in its own row (KaPost's Wallet screen).
            if (navController == null) {
                AddressReceiveSendBar(
                    receiveEnabled = !address.isNullOrEmpty(),
                    sendEnabled = balanceSompi > 0L,
                    onReceive = { showQr = true },
                    onSend = { showWithdraw = true },
                )
            }
        }
    ) { padding ->
        if (navController != null) {
            val spendingTotalSompi by viewModel.spendingTotalSompi.collectAsState()
            val backupConfirmedAt by viewModel.accountBackupStatus.confirmedAt.collectAsState()
            val biometricSpendingKeyEnabled by viewModel.biometricSpendingKeyEnabled.collectAsState()
            var showMore by remember { mutableStateOf(false) }
            // The coins a send from here may spend (KaspaWalletEngine.fetchUtxos): not the ones a
            // move or send just spent that the node still lists, nor unripe mining rewards.
            // Compound combines only these, so it counts only these (iOS 4922804
            // `spendableCoinCount`). Read again whenever the coin list is.
            var spendableCoinCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(utxos, address) {
                val addr = address?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
                spendableCoinCount = try {
                    viewModel.fetchUtxosForCoinControl(addr).size
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@LaunchedEffect
                }
            }
            ChessWalletTabBody(
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                // "--" until it is read, as iOS's nil balance: a 0 would read as an empty account.
                chattingSompi = balanceSompi.takeIf { !address.isNullOrEmpty() && balanceKnown },
                spendingSompi = spendingTotalSompi,
                hygieneDue = remember(address, backupConfirmedAt) { viewModel.accountBackupStatus.isHygieneDue(address) },
                txHistory = txHistory,
                isLoadingHistory = isLoadingTxHistory,
                historyFailed = txHistoryFailed,
                onSend = { showWithdraw = true },
                onReceive = { showQr = true },
                onMore = { showMore = true },
                onHygiene = { navController.navigate("backup_hygiene") },
                onRefresh = {
                    address?.let {
                        viewModel.loadSpendingAddressTxHistory(it)
                        viewModel.loadSpendingAddressUtxos(it)
                    }
                    viewModel.refreshBalance()
                    viewModel.refreshSpendingAddress()
                },
                onTransactionClick = { uriHandler.openUri(kaspaExplorer.txUrl(it.txId)) },
            )
            if (showMore) {
                ChessWalletMoreSheet(
                    coinCount = spendableCoinCount,
                    onCompound = { showMore = false; showCompoundFlow = true },
                    onPrivateKey = {
                        showMore = false
                        // Settings' "Biometrics for Address Private Keys", which this key is one of
                        if (biometricSpendingKeyEnabled) {
                            context.authenticateWithDeviceCredential(title = "Unlock to view this address's private key", onSuccess = { showPrivateKey = true })
                        } else {
                            showPrivateKey = true
                        }
                    },
                    onPublicKey = { showMore = false; showPublicKey = true },
                    onExplorer = { showMore = false; address?.let { uriHandler.openUri(kaspaExplorer.addressUrl(it)) } },
                    onDismiss = { showMore = false },
                )
            }
            return@Scaffold
        }
        // What Receive / Send and the dock cover: the lists scroll clear of it.
        val barInset = padding.calculateBottomPadding()
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(balanceSompi)
            Spacer(Modifier.height(8.dp))
            // Right above what it switches, the way Chats has it: History, UTXOs, Domains
            // (iOS b96d727/718b88c).
            UnderlineTabBar(
                titles = listOf(stringResource(R.string.address_tab_history), stringResource(R.string.utxos)),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            when (selectedTab) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { address?.let { viewModel.loadSpendingAddressTxHistory(it) } },
                    // A transaction opens the explorer (iOS WalletViews).
                    onTransactionClick = { uriHandler.openUri(kaspaExplorer.txUrl(it.txId)) },
                    bottomPadding = barInset,
                )
                else -> AddressUtxoList(
                    utxos = utxos,
                    isLoading = isLoadingUtxos,
                    loadError = utxosLoadError,
                    labels = utxoLabels,
                    onRetry = { utxoListAddress?.let { viewModel.loadSpendingAddressUtxos(it) } },
                    onCompound = { showCompoundFlow = true },
                    onRename = { key ->
                        labelingUtxoKey = key
                        labelInput = utxoLabels[key] ?: ""
                    },
                    bottomPadding = barInset,
                )
            }
        }
    }

    val qrAddress = address
    if (showQr && !qrAddress.isNullOrEmpty()) {
        // iOS ChattingAddressManageView shows ChattingAddressQRView with its default caption -
        // the chatting caption Profile uses (since 492aa53: "for chatting and domains only").
        // In a sheet with Close leading, as iOS's ChattingAddressManageView presents it.
        QrPageSheet(onDismiss = { showQr = false }, white = true) {
            ChattingAddressQrPage(
                address = qrAddress,
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
                if (biometricSeedPhraseEnabled) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to View Private Key",
                        onSuccess = { showPrivateKey = true }
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
                address?.let { uriHandler.openUri(kaspaExplorer.addressUrl(it)) }
            },
            onDismiss = { showAddressActions = false },
        )
    }

    if (showPublicKey) {
        IdentityAddressPublicKeyOverlay(address = address.orEmpty(), onDismiss = { showPublicKey = false })
    }

    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(privateKeyHex = viewModel.getPrivateKeyHex(), onDismiss = { showPrivateKey = false })
    }

    labelingUtxoKey?.let { key ->
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { labelingUtxoKey = null },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.rename_utxo), color = LocalAppColors.current.textPrimary) },
            text = {
                com.kachat.app.ui.theme.IosTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val addr = address ?: return@TextButton
                    viewModel.setSpendingUtxoLabel(addr, key, labelInput)
                    utxoLabels = viewModel.getSpendingUtxoLabels(addr)
                    labelingUtxoKey = null
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labelingUtxoKey = null }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/**
 * The options button at the top right of every address page, the chatting address's and each
 * spending address's (iOS da59726 `AddressOptionsButton`). Opens [IdentityAddressActionsSheet].
 */
@Composable
internal fun AddressOptionsButton(onClick: () -> Unit, enabled: Boolean = true) {
    val haptic = com.kachat.app.util.rememberHaptics()
    SheetBarIconButton(
        Icons.Default.MoreHoriz,
        stringResource(R.string.address_actions),
        onClick = {
            haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
            onClick()
        },
        modifier = Modifier.padding(end = 12.dp),
        enabled = enabled,
    )
}

/**
 * The half sheet behind [AddressOptionsButton], the same on every address page (iOS da59726
 * `AddressActionsSheet`). A quantum address keeps its key hidden until it spends, so
 * [showsPublicKey] is false for one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityAddressActionsSheet(
    onPrivateKey: () -> Unit,
    onPublicKey: () -> Unit,
    onExplorer: () -> Unit,
    onDismiss: () -> Unit,
    showsPublicKey: Boolean = true,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            // Expanded, not half-height: a partially-expanded sheet cuts the last row off.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.address_actions),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    ActionSheetRow(
                        icon = Icons.Default.Key,
                        title = "View Private Key",
                        subtitle = "The key that spends this address. Never share it.",
                        onClick = onPrivateKey,
                    )
                    if (showsPublicKey) {
                        ActionSheetRow(
                            icon = Icons.Default.Numbers,
                            title = "View Public Key",
                            subtitle = "The public half of this address, for anyone who asks for it.",
                            onClick = onPublicKey,
                        )
                    }
                    ActionSheetRow(
                        icon = Icons.Default.Public,
                        title = stringResource(R.string.view_in_explorer),
                        subtitle = "Opens this address on your chosen block explorer.",
                        onClick = onExplorer,
                    )
                }
            }
        }
    }
}

/**
 * An address's public key, from the Address Actions sheet.
 *
 * Nothing is derived or unlocked to show this: a Kaspa P2PK address IS its public key in bech32,
 * so this decodes the address the screen already has. No FLAG_SECURE, no reveal gate and no
 * clipboard expiry either - unlike the private key alongside it, this is a value you hand out on
 * purpose. Mirrors iOS's `AddressPublicKeyView`.
 */
@Composable
internal fun IdentityAddressPublicKeyOverlay(
    address: String,
    onDismiss: () -> Unit,
    note: String = "This is the public half of your chatting address. It identifies you and cannot spend anything.",
) {
    val keyCopyHaptic = com.kachat.app.util.rememberHaptics()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // null only for an address shape that carries no key (script-hash), which the wallet's own
    // chatting address never is.
    val publicKeyHex = remember(address) {
        runCatching {
            com.kachat.app.util.KaspaAddress.decode(address).second.joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // iOS 1751d20 ChattingAddressPublicKeyView: an inline "Public Key" centred, "Close" leading.
            Box(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                SheetBarButton(stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                Text(
                    "Public Key",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaspaTeal.copy(alpha = 0.1f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Safe to share", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    note,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            if (publicKeyHex != null) {
                Text(
                    publicKeyHex,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(publicKeyHex))
                    keyCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                    IosToasts.show("Public key copied")
                }) {
                    Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Public Key", color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "This address does not carry a public key.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Reveals a single spending address's own derived private key - not the wallet's seed phrase -
 * so a specific address's spending capability can be exported/backed up without exposing the
 * rest of the wallet. Mirrors SeedPhraseScreen's reveal flow at the same sensitivity level
 * (FLAG_SECURE screenshot/recording block, tap-to-reveal with a 7s auto-hide timer) rather than
 * inventing a lighter-weight pattern for equally sensitive key material. The caller already
 * gates presentation behind biometrics, same as View Seed Phrase's own entry point.
 */
@Composable
internal fun SpendingAddressPrivateKeyOverlay(
    privateKeyHex: String,
    onDismiss: () -> Unit,
    /** The inline title: the address's label, or "Chatting Address" (iOS 1751d20). */
    title: String = "Chatting Address",
) {
    val keyCopyHaptic = com.kachat.app.util.rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(revealed) {
        if (revealed) {
            delay(7000)
            revealed = false
        }
    }

    BackHandler(onBack = onDismiss)

    val colors = LocalAppColors.current
    // iOS 1751d20 SpendingAddressPrivateKeyView / ChattingAddressPrivateKeyView: the title inline
    // and centred with Close leading, then from the top, 24 apart: the orange Security Warning
    // box, then the grey reveal box - or the key in a grey box with Copy Private Key Hex under it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SheetNavBar(
                title = title,
                leading = { SheetBarButton(stringResource(R.string.close), onClick = onDismiss) },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.warning.copy(alpha = 0.1f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Label("Security Warning", systemImage: "exclamationmark.triangle.fill"), headline.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = colors.warning, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.security_warning),
                            color = colors.warning,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        )
                    }
                    Text(
                        "Anyone with this address's private key can spend its funds. Never share it with anyone.",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                    )
                }

                if (!revealed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .clickable { revealed = true }
                            .padding(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            "Tap to reveal private key",
                            color = colors.textSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    // Its Copy button sits right under it (iOS .padding(.top) on a 24 stack).
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            privateKeyHex,
                            color = colors.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .padding(16.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        TextButton(onClick = {
                            copyPrivateKeyWithAutoWipe(context, privateKeyHex)
                            keyCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                            IosToasts.show("Private key copied. Clipboard will clear in 30s.")
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.copy_private_key_hex), color = KaspaTeal, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingAddressTxHistoryRow(
    tx: ColdStorageAddressDiscovery.AddressTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // iOS `direction(for:)` is nil - "Transaction", grey, no amount - when nothing left for
    // someone else (sent) or arrived here (received): a self-send or consolidation.
    val directionKnown = tx.amountSompi > 0
    val directionColor = when {
        !directionKnown -> colors.textSecondary
        tx.sent -> colors.danger
        else -> colors.success
    }
    // iOS `.formatted(date: .abbreviated, time: .shortened)`: "Sep 24, 2026 at 2:25 PM".
    val dateStr = tx.blockTimeMillis?.let {
        java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.US).format(java.util.Date(it))
    }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // "arrow.up.circle.fill" / "arrow.down.circle.fill" at title2: a 22 disc, the arrow cut
        // out of it in the card's colour.
        FilledCircleArrow(up = directionKnown && tx.sent, color = directionColor, cutout = Color(0xFF1C1C1E), size = 22.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    !directionKnown -> "Transaction"
                    tx.sent -> "Sent"
                    else -> "Received"
                },
                color = directionColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            MiddleEllipsisText(
                tx.txId,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            dateStr?.let {
                Text(it, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (directionKnown) {
                Text(
                    "${if (tx.sent) "-" else "+"}%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, tx.amountSompi / 100_000_000.0),
                    color = directionColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            // What this transaction paid the network (iOS 9839906).
            transactionFeeText(tx.feeSompi)?.let {
                Text(it, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
            // iOS "arrow.up.right.square" at caption, accent.
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = KaspaTeal, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun SpendingAddressUtxoRow(
    utxo: ColdStorageAddressDiscovery.AddressUtxo,
    label: String? = null,
    onRenameClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // iOS `utxoRow`: the coin glyph at title2, the label / amount / outpoint, Coinbase, the pencil.
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (utxo.isCoinbase) {
            // "cube.fill"
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ViewInAr, null, tint = KaspaTeal, modifier = Modifier.requiredSize(28.dp))
            }
        } else {
            // "circle.grid.2x2.fill": four dots in a square.
            androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                val r = size.minDimension * 0.2f
                for (cx in listOf(0.27f, 0.73f)) for (cy in listOf(0.27f, 0.73f)) {
                    drawCircle(KaspaTeal, radius = r, center = Offset(size.width * cx, size.height * cy))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    color = KaspaTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, utxo.amountSompi / 100_000_000.0),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            MiddleEllipsisText(
                "${utxo.transactionId}:${utxo.index}",
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (utxo.isCoinbase) {
            Text(
                stringResource(R.string.coinbase),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.size(32.dp).clickable(onClick = onRenameClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Edit, stringResource(R.string.rename), tint = KaspaTeal, modifier = Modifier.size(21.dp))
        }
    }
}

/** iOS 26 `.insetGrouped` corner radius and the gap above a list's first section. */
private val InsetGroupedRadius = 26.dp
private val InsetGroupedTopGap = 35.dp

/**
 * One row of an iOS 26 `.insetGrouped` section: the section is a single rounded card 16 in from
 * each edge, so only the first row rounds its top corners and the last its bottom ones, and every
 * row after the first draws the inset hairline separator above itself (from [separatorStart] to
 * 16 short of the trailing edge).
 */
@Composable
private fun Modifier.insetGroupedCell(index: Int, count: Int, separatorStart: Dp = 16.dp): Modifier {
    val colors = LocalAppColors.current
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) InsetGroupedRadius else 0.dp,
        topEnd = if (first) InsetGroupedRadius else 0.dp,
        bottomStart = if (last) InsetGroupedRadius else 0.dp,
        bottomEnd = if (last) InsetGroupedRadius else 0.dp,
    )
    val divider = colors.divider
    return this
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(shape)
        .background(colors.surface)
        .then(
            if (first) Modifier
            else Modifier.drawBehind {
                val stroke = 0.5.dp.toPx().coerceAtLeast(1f)
                drawLine(
                    color = divider,
                    start = Offset(separatorStart.toPx(), stroke / 2f),
                    end = Offset(size.width - 16.dp.toPx(), stroke / 2f),
                    strokeWidth = stroke,
                )
            }
        )
}

/** A one-row `.insetGrouped` list - the loading, empty and failed states iOS puts in the list. */
@Composable
private fun InsetGroupedSingleRowList(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .insetGroupedCell(0, 1)
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                content()
            }
        }
    }
}

/** "Balance" over the amount at the top of an address's page (iOS: caption, then title3 semibold). */
@Composable
internal fun AddressPageBalanceHeader(balanceSompi: Long) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.balance),
            color = LocalAppColors.current.textSecondary,
            fontSize = 12.sp
        )
        Text(
            "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, balanceSompi / 100_000_000.0),
            color = LocalAppColors.current.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
    }
}

/**
 * An address page's History tab - iOS `transactionsList`: one `.insetGrouped` card of
 * transactions, or the spinner, "Could not load transactions." with Try Again, or "No
 * transactions yet." as the list's only row.
 */
@Composable
internal fun AddressHistoryList(
    txHistory: List<ColdStorageAddressDiscovery.AddressTransaction>,
    isLoading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onTransactionClick: (ColdStorageAddressDiscovery.AddressTransaction) -> Unit,
    /** Extra room at the list's end for what floats over the page's foot (Receive / Send, the dock). */
    bottomPadding: Dp = 0.dp,
) {
    val colors = LocalAppColors.current
    val txTapHaptic = com.kachat.app.util.rememberHaptics()
    // One row per transaction: a REST reply that repeats one would crash the keyed list.
    val rows = remember(txHistory) { txHistory.distinctBy { it.txId } }
    // The whole list pulls to refresh, whatever it shows (iOS 1751d20 `.refreshable` on the List).
    AddressHistoryRefreshable(isLoading = isLoading, onRefresh = onRetry) {
    when {
        isLoading && rows.isEmpty() -> InsetGroupedSingleRowList {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
            }
        }
        // A failed fetch is not an empty history. Saying "No transactions yet." here was a
        // confident answer about someone's money the app had not got.
        rows.isEmpty() && failed -> InsetGroupedSingleRowList {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Could not load transactions.", color = colors.textSecondary, fontSize = 17.sp)
                Text(
                    stringResource(R.string.try_again),
                    color = KaspaTeal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }
        rows.isEmpty() -> InsetGroupedSingleRowList {
            Text(stringResource(R.string.no_transactions_yet), color = colors.textSecondary, fontSize = 17.sp)
        }
        else -> {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp + bottomPadding),
        ) {
            if (failed) {
                // Some pages landed, some did not: say so rather than letting a truncated list
                // read as the whole history (iOS WalletViews).
                item(key = "incomplete") {
                    Text(
                        "Some transactions could not be loaded. Pull to refresh to try again.",
                        color = colors.warning, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { _, tx -> tx.txId }) { i, tx ->
                SpendingAddressTxHistoryRow(
                    tx = tx,
                    onClick = {
                        // iOS 1751d20: Haptics.impact(.light), then the explorer.
                        txTapHaptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                        onTransactionClick(tx)
                    },
                    // The separator starts under the text, past the 26 icon and its 12 gap.
                    modifier = Modifier.insetGroupedCell(i, rows.size, separatorStart = 54.dp),
                )
            }
        }
        }
    }
    }
}

/** Pull to refresh over the history, as iOS's `.refreshable` list: the spinner stays until the
 *  reload it started has finished. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressHistoryRefreshable(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val loading by rememberUpdatedState(isLoading)
    if (state.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh()
            // Give the reload a moment to start, then hold the spinner until it is done.
            kotlinx.coroutines.delay(400)
            while (loading) kotlinx.coroutines.delay(100)
            state.endRefresh()
        }
    }
    // Clipped: at rest the spinner waits just above the list, and unclipped it drew as a grey
    // circle over the History / UTXOs tabs.
    Box(modifier.fillMaxSize().clipToBounds().nestedScroll(state.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(state = state, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/**
 * An address page's UTXOs tab - iOS `utxosList`: Compound UTXOs as its own section with its
 * footer when there is more than one coin, then one `.insetGrouped` card of coins, or the
 * spinner, the load-failure row or "No UTXOs." as that section's only row.
 */
@Composable
internal fun AddressUtxoList(
    utxos: List<ColdStorageAddressDiscovery.AddressUtxo>,
    isLoading: Boolean,
    loadError: String?,
    labels: Map<String, String>,
    onRetry: () -> Unit,
    onCompound: () -> Unit,
    onRename: (String) -> Unit,
    /** Extra room at the list's end for what floats over the page's foot (Receive / Send, the dock). */
    bottomPadding: Dp = 0.dp,
    /** Whether Compound UTXOs is offered: more than one coin, or - on the chatting address's page
     *  - more than one coin a send could spend (iOS 1751d20 `withdrawalSpendable`). */
    showsCompound: Boolean = utxos.size > 1,
    /** The Compound section's footer; null is the chatting address's (iOS 1751d20: a spending
     *  address's page has its own). */
    compoundFooter: String? = null,
) {
    val colors = LocalAppColors.current
    // One row per outpoint: a REST reply that repeats one would crash the keyed list.
    val rows = remember(utxos) { utxos.distinctBy { "${it.transactionId}:${it.index}" } }
    // The whole list pulls to refresh (iOS 1751d20 `.refreshable { await loadUtxos() }`).
    AddressHistoryRefreshable(isLoading = isLoading, onRefresh = onRetry) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp + bottomPadding),
    ) {
        if (showsCompound) {
            item(key = "compound") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .insetGroupedCell(0, 1)
                            .clickable(onClick = onCompound)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.CallMerge, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.compound_utxos),
                            color = KaspaTeal,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // The section footer.
                    Text(
                        compoundFooter ?: stringResource(R.string.compound_utxos_description),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp, bottom = 24.dp)
                    )
                }
            }
        }
        when {
            isLoading && rows.isEmpty() -> item(key = "loading") {
                Box(
                    modifier = Modifier.insetGroupedCell(0, 1).padding(horizontal = 16.dp, vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                }
            }
            // The load-failure row when the node could not be asked, otherwise "No UTXOs.".
            rows.isEmpty() -> item(key = "empty") {
                if (loadError != null) {
                    UtxoLoadFailureRow(detail = loadError, retry = onRetry, modifier = Modifier.insetGroupedCell(0, 1))
                } else {
                    Text(
                        stringResource(R.string.no_utxos),
                        color = colors.textSecondary,
                        fontSize = 17.sp,
                        modifier = Modifier.insetGroupedCell(0, 1).padding(horizontal = 16.dp, vertical = 15.dp),
                    )
                }
            }
            else -> itemsIndexed(rows, key = { _, u -> "${u.transactionId}:${u.index}" }) { i, utxo ->
                val key = "${utxo.transactionId}:${utxo.index}"
                SpendingAddressUtxoRow(
                    utxo = utxo,
                    label = labels[key],
                    onRenameClick = { onRename(key) },
                    modifier = Modifier.insetGroupedCell(i, rows.size, separatorStart = 54.dp),
                )
            }
        }
    }
    }
}

/**
 * Receive and Send at the foot of an address's page (iOS `safeAreaInset(edge: .bottom)`): two
 * accent capsules 12 apart, 16 in and 16 off the bottom. Send is "arrow.up.circle.fill" and, with
 * nothing to send, disabled at half opacity as iOS draws it.
 */
@Composable
internal fun AddressReceiveSendBar(
    receiveEnabled: Boolean,
    sendEnabled: Boolean,
    onReceive: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            // Above the dock the page runs under (0 where there is none).
            .padding(bottom = LocalDockInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AddressBarCapsule(Icons.Default.QrCode, stringResource(R.string.receive), FontWeight.SemiBold, receiveEnabled, onReceive, Modifier.weight(1f))
        AddressBarCapsule(null, stringResource(R.string.send), FontWeight.Bold, sendEnabled, onSend, Modifier.weight(1f))
    }
}

@Composable
private fun AddressBarCapsule(
    /** Null: iOS's "arrow.up.circle.fill" - a black disc with the arrow cut out in the accent. */
    icon: ImageVector?,
    title: String,
    weight: FontWeight,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp).alpha(if (enabled) 1f else 0.5f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = KaspaTeal,
            contentColor = Color.Black,
            disabledContainerColor = KaspaTeal,
            disabledContentColor = Color.Black,
        )
    ) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(18.dp))
        else FilledCircleArrow(up = true, color = Color.Black, cutout = KaspaTeal, size = 17.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = weight)
    }
}

/** SF Symbols' "arrow.up.circle.fill" / "arrow.down.circle.fill": a [color] disc with the arrow
 *  cut out of it, drawn in [cutout] (what is behind the symbol). */
@Composable
private fun FilledCircleArrow(up: Boolean, color: Color, cutout: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            null,
            tint = cutout,
            modifier = Modifier.size(size * 0.68f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeedPhraseScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val secretCopyHaptic = com.kachat.app.util.rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    val mnemonic = remember { viewModel.getActiveMnemonic() ?: "" }
    // A passphrase (the "25th word") changes every key: the words alone restore a different,
    // empty wallet, so the backup screen must say so and show it (iOS 3076ab7, IOS-017).
    val passphrase = remember { viewModel.getActivePassphrase() }
    val privateKey = remember { viewModel.getPrivateKeyHex() }
    val words = remember { mnemonic.split(" ") }
    val context = LocalContext.current

    // Blocks screenshots and screen recording of the seed phrase / private key for as long as
    // this screen is on-screen (window-level flag, the standard Android mechanism - unlike iOS,
    // which has no API to block a screenshot outright, only to detect active screen recording
    // after the fact via UIScreen.isCaptured). Cleared on leaving so it doesn't leak onto other
    // screens.
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Auto-hides the words again after a short window instead of leaving them on screen
    // indefinitely once revealed — someone glancing at the phone later shouldn't still see them.
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(20_000)
            revealed = false
        }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.seed_phrase), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2C1E1E))
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = LocalAppColors.current.warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.security_warning),
                        color = LocalAppColors.current.warning,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.anyone_with_your_seed_phrase_can),
                        color = Color(0xFF948B8B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Best practice is to write your 12 or 24 words down to back them up, and never store it online.",
                        color = Color(0xFF948B8B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (passphrase != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = LocalAppColors.current.warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.seed_phrase_also_uses_passphrase),
                        color = LocalAppColors.current.warning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!revealed) {
                Column(
                    modifier = Modifier
                        .clickable { revealed = true }
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = LocalAppColors.current.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tap_to_reveal_seed_phrase),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    words.forEachIndexed { index, word ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(LocalAppColors.current.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = word,
                                color = LocalAppColors.current.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                // Under the words, with the same FLAG_SECURE window and 7-second auto-hide.
                if (passphrase != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalAppColors.current.surface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.seed_phrase_passphrase_label),
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passphrase,
                            color = LocalAppColors.current.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (revealed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Both copies go through the sensitive path: marked secret for the system
                    // clipboard and wiped 30 seconds later (copyPrivateKeyWithAutoWipe). Copying
                    // the phrase is how it reaches a password manager, which is a safer home for
                    // it than a photograph of a notebook (iOS f9beba1).
                    TextButton(onClick = {
                        copyPrivateKeyWithAutoWipe(context, mnemonic, label = "recovery phrase")
                        secretCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show("Seed phrase copied. Clipboard will clear in 30s.")
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy 12 or 24 Words", color = KaspaTeal)
                        }
                    }
                    TextButton(onClick = {
                        copyPrivateKeyWithAutoWipe(context, privateKey)
                        // iOS SettingsView: the haptic and the 30-second notice, as the phrase has.
                        secretCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show("Private key hex copied. Clipboard will clear in 30s.")
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tag, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_private_key_hex), color = KaspaTeal)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String?, headerAction: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // iOS 26's inset-grouped Form section: the header in title case, 17pt semibold in the
    // secondary label colour (no longer iOS's old small uppercase), over a card with 26pt corners.
    Column {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, bottom = 8.dp)
                )
                headerAction?.invoke()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(LocalAppColors.current.surface)
        ) {
            content()
        }
    }
}

/** iOS Form spacing, measured on the iPhone: 28 from a card to the next section's header, 15
 *  from a footer to it. */
val IosFormSectionGap = 28.dp
val IosFormFooterGap = 15.dp

/**
 * One section of an iOS Form page: [SettingsSection] (header, card), the [footer] under the card
 * rather than in it, and the gap to the next section ([IosFormSectionGap], or [IosFormFooterGap]
 * after a footer). Lay these out in a plain Column (no spacedBy) - see [SettingsFormPage].
 */
@Composable
fun SettingsFormSection(
    title: String?,
    footer: String? = null,
    /** A footer that reports an outcome (iOS colours an error red, a success green). */
    footerColor: Color = Color.Unspecified,
    /** The page's first section: without a header, iOS still starts its card where a first
     *  header's card would sit. */
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // A section without a header keeps part of a header's room above its card on the
        // iPhone: 32 from the footer above it, against 23 here (measured) - and the whole of it
        // when it opens the page.
        if (title == null) Spacer(Modifier.height(if (first) 24.dp else 9.dp))
        SettingsSection(title = title, content = content)
        if (footer != null) {
            SettingsFormFooter(footer, color = footerColor)
            Spacer(Modifier.height(IosFormFooterGap))
        } else {
            Spacer(Modifier.height(IosFormSectionGap))
        }
    }
}

/** An iOS section footer under its card: 13pt secondary, 16 in, 7 below the card. */
@Composable
fun SettingsFormFooter(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = if (color != Color.Unspecified) color else LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp)
    )
}

/**
 * A pushed settings page's bar, iOS's inline navigation bar: the back chevron on a glass disc at
 * the left, [title] centred in headline (17 semibold), and the page's own [trailing] items.
 * No connection dot and no balance - those are the Settings hub's alone.
 */
@Composable
fun SettingsSubpageBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    SheetNavBar(
        title = title,
        // Inside the Settings sheet the status bar is already consumed; on a full-screen page
        // (the accounts screen's App Settings) this keeps the bar below it.
        modifier = Modifier.background(LocalAppColors.current.background).statusBarsPadding(),
        leading = { SheetBarBackButton(onClick = onBack) },
        trailing = trailing,
    )
}

/**
 * A pushed settings page laid out as iOS's inset-grouped Form: [SettingsSubpageBar], then a
 * scrolling column of [SettingsFormSection]s 16 in from the edges, the first header 4 under the bar.
 */
@Composable
fun SettingsFormPage(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = { SettingsSubpageBar(title = title, onBack = onBack, trailing = trailing) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * iOS's inline Picker row (`.pickerStyle(.menu)` in a Form): [label] at the left, the selected
 * option in the accent colour with the menu's up/down chevrons at the right. A tap opens a small
 * menu of [options], a checkmark on the selected one.
 */
@Composable
fun <T> SettingsPickerItem(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = LocalAppColors.current.textPrimary,
            fontSize = 17.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    optionLabel(selected),
                    color = KaspaTeal,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.UnfoldMore, null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        leadingIcon = {
                            if (option == selected) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                            } else {
                                Spacer(Modifier.size(20.dp))
                            }
                        },
                        onClick = {
                            expanded = false
                            if (option != selected) onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/**
 * iOS's `AppWideCustomizationPickers`: Appearance, Language and Currency as menu Pickers, shared by
 * Settings > Customization and the accounts screen's App Settings (one source of truth).
 */
@Composable
fun AppWideCustomizationPickers(walletViewModel: WalletViewModel) {
    val appearance by walletViewModel.appearance.collectAsState()
    val currencyCode by walletViewModel.currency.collectAsState()
    var language by remember { mutableStateOf(currentAppLanguage()) }

    SettingsPickerItem(
        label = stringResource(R.string.appearance),
        options = com.kachat.app.models.AppAppearance.entries,
        selected = appearance,
        optionLabel = {
            when (it) {
                com.kachat.app.models.AppAppearance.SYSTEM -> stringResource(R.string.appearance_system)
                com.kachat.app.models.AppAppearance.LIGHT -> stringResource(R.string.appearance_light)
                com.kachat.app.models.AppAppearance.DARK -> stringResource(R.string.appearance_dark)
            }
        },
        onSelect = { walletViewModel.setAppearance(it) },
    )
    SettingsDivider()
    SettingsPickerItem(
        label = stringResource(R.string.language),
        options = AppLanguage.entries,
        selected = language,
        optionLabel = { if (it == AppLanguage.SYSTEM) stringResource(R.string.appearance_system) else it.displayName },
        onSelect = {
            language = it
            applyAppLanguage(it)
        },
    )
    SettingsDivider()
    SettingsPickerItem(
        label = stringResource(R.string.currency),
        options = AppCurrency.entries,
        selected = AppCurrency.fromCode(currencyCode),
        optionLabel = { it.displayName },
        onSelect = { walletViewModel.setCurrency(it.code) },
    )
}

@Composable
fun SettingsSwitchItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = LocalAppColors.current.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        com.kachat.app.ui.theme.IosSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsNavigationItem(
    label: String,
    icon: ImageVector?,
    value: String = "",
    showIcon: Boolean = true,
    /** iOS tints a row's symbol with the accent; a Label given `.primary` draws it white. */
    iconTint: Color = KaspaTeal,
    /** iOS writes a link row's trailing value in `.caption` (12): pass 12.sp for that. */
    valueFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    valueColor: Color = LocalAppColors.current.textSecondary,
    onClick: () -> Unit = {},
) {
    // iOS 26 list row: 52pt tall, a 24pt symbol in its own column, the label lined up with the
    // separator that starts under it (see SettingsDivider(inset)).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon && icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(text = value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontSize = valueFontSize, modifier = Modifier.padding(horizontal = 8.dp))
        }
        IosDisclosureChevron()
    }
}

/** iOS's disclosure chevron at the end of a navigation row. */
@Composable
fun IosDisclosureChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalAppColors.current.textSecondary,
        modifier = Modifier.size(24.dp)
    )
}

/** Where a row's text starts when it has a leading symbol: 16 + 24 + 16. Separators inset to it. */
val IosRowTextInset = 56.dp

@Composable
fun SettingsActionItem(
    label: String,
    icon: ImageVector,
    color: Color,
    /** Defaults to [color]; set it when the label should read differently from the icon. */
    labelColor: Color = color,
    /** A row that opens a page (iOS's Danger Zone) carries the disclosure chevron. */
    showChevron: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = labelColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (showChevron) IosDisclosureChevron()
    }
}

@Composable
fun SettingsInfoItem(label: String, value: String, valueColor: Color = LocalAppColors.current.textSecondary, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, color = valueColor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SettingsDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    // iOS separators start where the row's text starts and stop short of the right edge.
    HorizontalDivider(modifier = Modifier.padding(start = inset, end = 16.dp), color = LocalAppColors.current.divider, thickness = 0.5.dp)
}

/**
 * Settings > Connection (iOS `ConnectionHubPage`, 0c1af03; 010167f): the Testnet switch first -
 * with the restart note while the running network differs, and no footer - then Connection
 * Settings, then Kaspa Explorer in its own section. Shared by the in-account
 * Settings page and the accounts screen's App Settings.
 */
@Composable
fun ConnectionHubSections(
    explorerName: String,
    onOpenConnectionSettings: () -> Unit,
    onOpenExplorer: () -> Unit,
    connectionViewModel: com.kachat.app.viewmodels.ConnectionViewModel = hiltViewModel(),
) {
    val selected by com.kachat.app.util.KaspaNetwork.selected.collectAsState()
    val isTestnet = selected == com.kachat.app.util.KaspaNetwork.Type.TESTNET
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val colors = LocalAppColors.current
    SettingsFormSection(title = stringResource(R.string.connection)) {
        // Mainnet <-> testnet, first in the section (no footer). The choice applies at the next
        // launch, all at once (iOS e6f664f): until then the running app stays on its network.
        // Each network keeps its own connection settings, so Connection Settings below shows
        // the chosen network's values - the ones the app uses after the restart.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.testnet),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            com.kachat.app.ui.theme.IosSwitch(
                checked = isTestnet,
                onCheckedChange = { on ->
                    connectionViewModel.switchNetwork(if (on) com.kachat.app.util.KaspaNetwork.Type.TESTNET else com.kachat.app.util.KaspaNetwork.Type.MAINNET)
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                onColor = colors.warning,
            )
        }
        if (selected != com.kachat.app.util.KaspaNetwork.launch) {
            SettingsDivider(inset = IosRowTextInset)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.warning, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(if (isTestnet) R.string.testnet_restart_to_testnet else R.string.testnet_restart_to_mainnet),
                    color = colors.textPrimary,
                    fontSize = 15.sp
                )
            }
        }
        SettingsDivider(inset = IosRowTextInset)
        SettingsNavigationItem(stringResource(R.string.connection_settings), Icons.Default.Language, onClick = onOpenConnectionSettings)
    }
    // Its own section, no header; the explorer's name in iOS's caption.
    SettingsFormSection(title = null) {
        SettingsNavigationItem(
            stringResource(R.string.kaspa_explorer),
            Icons.Default.Explore,
            explorerName,
            valueFontSize = 12.sp,
            onClick = onOpenExplorer,
        )
    }
}

@Composable
fun SettingsFooter(text: String) {
    // iOS section footer: 13pt secondary label.
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/**
 * The Security section's rows. The biometric toggles and Child Mode are app-wide, so the same
 * items render both in the in-account Settings > Security page and in the accounts screen's App
 * Settings sheet (see AppSettingsScreen.kt), one source of truth. Caller supplies the enclosing [SettingsSection] and the navigation to the Child Mode screen
 * (each host has its own NavHost).
 */
@Composable
fun SecuritySettingsItems(
    walletViewModel: WalletViewModel,
    /** Null leaves Child Mode out: KaChat Chess's Security is the three biometric locks alone. */
    onNavigateToChildMode: (() -> Unit)? = null
) {
    val biometricSeedPhraseEnabled by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val biometricAccountLoginEnabled by walletViewModel.biometricAccountLoginEnabled.collectAsState()
    val biometricSpendingKeyEnabled by walletViewModel.biometricSpendingKeyEnabled.collectAsState()
    val securityContext = LocalContext.current
    // A biometric lock that can be switched off without passing the lock is not a lock - anyone
    // holding an unlocked phone could have turned all three off and then read the seed phrase.
    // So the switch itself is gated, in BOTH directions: turning one on is a claim about who is
    // holding the device too. The setting is only written after auth succeeds, so a cancelled
    // prompt leaves the switch exactly where it was rather than flipping and flipping back.
    fun gated(reason: String, apply: (Boolean) -> Unit): (Boolean) -> Unit = { enabled ->
        securityContext.authenticateWithDeviceCredential(
            title = reason,
            onSuccess = { apply(enabled) },
        )
    }
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_seed_phrase),
        biometricSeedPhraseEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_seed_phrase_lock)) {
            walletViewModel.setBiometricSeedPhraseEnabled(it)
        },
    )
    SettingsDivider()
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_account_login),
        biometricAccountLoginEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_account_login_lock)) {
            walletViewModel.setBiometricAccountLoginEnabled(it)
        },
    )
    SettingsDivider()
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_address_private_keys),
        biometricSpendingKeyEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_private_key_lock)) {
            walletViewModel.setBiometricSpendingKeyEnabled(it)
        },
    )
    if (onNavigateToChildMode == null) return
    SettingsDivider()
    // Child Mode (matches iOS Settings > Security > Child Mode). NEVER biometric-gated - the
    // whole point is that the device owner (the child) can pass fingerprint/face unlock but
    // must not know the parent's password. Fully functional with no account active: the
    // password record and enabled flag are device-global (ChildModeService/DataStore).
    val childModeEnabled by walletViewModel.childModeEnabled.collectAsState()
    // iOS: the lock.shield symbol, On / Off in caption.
    SettingsNavigationItem(
        stringResource(R.string.child_mode),
        com.kachat.app.ui.theme.IosSymbols.LockShield,
        if (childModeEnabled) stringResource(R.string.on) else stringResource(R.string.off),
        valueFontSize = 12.sp,
        onClick = onNavigateToChildMode
    )
}

/**
 * Kaspa-logo total-balance row — Android port of iOS's BalanceToolbarLabel
 * (SettingsView.swift): 15dp Kaspa logo + full 8-decimal balance in bold with
 * tabular figures and secondary color, so the header balance reads identically
 * on every main page (Chats/Settings via TopStatusBar; Broadcasts, Cold Storage,
 * Swap, Portfolio and KaPosts via BalanceTopBarLabel below).
 */
@Composable
fun BalanceLabelRow(
    balance: String,
    modifier: Modifier = Modifier,
    /** Chats, Profile and Settings: a tap copies the exact figure (iOS's balance toolbar label,
     *  "Balance copied to clipboard."). */
    copyOnTap: Boolean = false,
) {
    val balanceClipboard = LocalClipboardManager.current
    val balanceHaptic = com.kachat.app.util.rememberHaptics()
    Row(
        modifier = modifier.then(
            if (copyOnTap) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                balanceClipboard.setText(AnnotatedString(balance.substringBefore(' ')))
                balanceHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                IosToasts.show("Balance copied to clipboard.")
            } else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_kaspa_logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = balance,
            color = LocalAppColors.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

/**
 * Self-contained variant for screens that don't already collect the wallet balance:
 * observes WalletViewModel.fullBalance and refreshes it once on appearance (matches
 * the iOS label's `.task { refreshBalance() }`).
 */
@Composable
fun BalanceTopBarLabel(
    modifier: Modifier = Modifier,
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val balance by walletViewModel.fullBalance.collectAsState()
    LaunchedEffect(Unit) { walletViewModel.refreshBalance() }
    BalanceLabelRow(balance, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(onBack: () -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    val network by viewModel.network.collectAsState()
    // The indexer this launch is connected to (a Testnet switch waits for the next launch).
    val indexerUrl by viewModel.runningIndexerUrl.collectAsState()

    val activeNodes by viewModel.activeNodes.collectAsState()
    val allNodes by viewModel.allNodes.collectAsState()
    // Pool sections only make sense when the POOL is choosing the node. Pinned to a specific
    // node (the shipped default or the user's own), there is no pool at work - discovery is
    // off and the app only ever talks to that one address - so pool status, pool actions, and
    // the node lists would just show a pool of one. Null (not yet loaded) hides them too, so
    // a pinned user never sees them flash on screen entry.
    val nodeSelectionIsAutomatic by viewModel.nodeSelectionIsAutomatic.collectAsState()
    val showPoolSections = nodeSelectionIsAutomatic == true
    val status by viewModel.status.collectAsState()
    val dotColorHex by viewModel.dotColorHex.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()
    val nodeConnectionsBlocked by viewModel.nodeConnectionsBlocked.collectAsState()
    val scrollState = rememberScrollState()
    val isRefreshingPool by viewModel.isRefreshingPool.collectAsState()
    val isReconnecting by viewModel.isReconnecting.collectAsState()
    val poolActionsEnabled = !isRefreshingPool && !isReconnecting
    var showClearPoolConfirm by remember { mutableStateOf(false) }
    val connectionClipboard = LocalClipboardManager.current
    val connectionHaptic = com.kachat.app.util.rememberHaptics()
    val connectionContext = LocalContext.current
    // iOS: tapping an active node copies its host:port.
    val copyNodeEndpoint: (NodeInfo) -> Unit = { node ->
        connectionClipboard.setText(AnnotatedString(node.ip))
        connectionHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
        IosToasts.show(connectionContext.getString(R.string.node_endpoint_copied))
    }

    // status is derived from the exact same latency threshold as dotColorHex, so
    // the text here can never contradict the dot's color: green only says
    // Connected/Healthy, orange only says Degraded, red only says Disconnected/Unhealthy.
    val statusColor = Color(dotColorHex)
    val statusText = when (status) {
        ConnStatus.CONNECTED -> "Connected"
        ConnStatus.DEGRADED -> "Degraded"
        ConnStatus.DISCONNECTED -> "Disconnected"
    }
    val poolHealthText = when (status) {
        ConnStatus.CONNECTED -> "Healthy"
        ConnStatus.DEGRADED -> "Degraded"
        ConnStatus.DISCONNECTED -> "Unhealthy"
    }
    // "Verified" = currently reachable at all (Active or Suspect), a broader real count
    // than "Active" (Active additionally requires being in-sync and not recently failing).
    // Excludes nodes that haven't been probed even once yet (latency == "—") — those also
    // report as "Suspect" (see NodeRegistry.statusOf's lastProbe == null branch), but counting
    // them as "Verified" is misleading: right after a fresh launch/DNS-seed resolution this made
    // the whole pool look "Verified" before a single probe had actually completed.
    val verifiedCount = remember(allNodes) { allNodes.count { (it.status == "Active" || it.status == "Suspect") && it.latency != "—" } }

    if (showClearPoolConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showClearPoolConfirm = false },
            title = { Text(stringResource(R.string.clear_connection_pool_confirm_title), color = LocalAppColors.current.textPrimary) },
            text = { Text(stringResource(R.string.clear_connection_pool_confirm_message), color = LocalAppColors.current.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    showClearPoolConfirm = false
                    viewModel.clearPool()
                }) { Text(stringResource(R.string.clear), color = LocalAppColors.current.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPoolConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = KaspaTeal, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // iOS presents Connection Status as a sheet holding a Form (ConnectionStatusDetailView): the
    // grouped sheet palette, rounded top 10 below the status bar, the screen behind dimmed, an
    // inline bar with Done on glass.
    val primaryHost = activeNodes.firstOrNull()?.ip?.let { ip ->
        com.kachat.app.services.grpc.parseNodeAddress(ip)?.host ?: ip.substringAfter("://")
    }
    // iOS writes "138 ms" and colours it by speed: green under 100, the primary colour under
    // 200, orange under 500, red above.
    val latencyMs = activeNodes.firstOrNull()?.latency?.removeSuffix("ms")?.trim()?.toIntOrNull()
    val latencyColor = when {
        latencyMs == null -> LocalAppColors.current.textSecondary
        latencyMs < 100 -> LocalAppColors.current.success
        latencyMs < 200 -> LocalAppColors.current.textPrimary
        latencyMs < 500 -> LocalAppColors.current.warning
        else -> LocalAppColors.current.danger
    }
    // iOS shows the indexer's host alone (URLComponents.host): no scheme, port or path.
    val indexerHost = indexerUrl.substringAfter("://").substringBefore("/").substringBefore(":")

    IosSheetPage(grouped = true) {
        SheetNavBar(
            title = stringResource(R.string.connection_status),
            trailing = { SheetBarButton(stringResource(R.string.done), onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            SettingsSection(title = stringResource(R.string.connection_status)) {
                ConnectionStatusRow(stringResource(R.string.status), statusText, dot = statusColor)
                SettingsDivider()
                ConnectionStatusRow(
                    stringResource(R.string.protocol),
                    if (activeNodes.firstOrNull()?.ip?.let { com.kachat.app.services.grpc.parseNodeAddress(it)?.secure } == true) {
                        "gRPC (secure)"
                    } else {
                        "gRPC (plaintext)"
                    }
                )
                // Indexer requests going over HTTP/1.1 because HTTP/2 looked blocked (iOS's DPI row).
                val dpiSuspected by viewModel.dpiSuspected.collectAsState()
                if (dpiSuspected) {
                    SettingsDivider()
                    ConnectionStatusRow("DPI", "Suspected", valueColor = LocalAppColors.current.warning)
                    Text(
                        "Connectivity might be limited. Using HTTP/1.1 and decreased pagination for indexer requests.",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
                // Honest all-blocked explanation: only shown once the pool has concluded that
                // NO node answers at all on this network (see NodePoolManager's
                // nodeConnectionsBlocked doc) - a plain red "Disconnected" with no reason
                // otherwise reads as an app bug on firewalled/captive networks. Laid out as
                // iOS's "Node Network: Blocked" row with its caption under it.
                if (nodeConnectionsBlocked && status == ConnStatus.DISCONNECTED) {
                    SettingsDivider()
                    ConnectionStatusRow("Node Network", "Blocked", valueColor = LocalAppColors.current.warning)
                    Text(
                        stringResource(R.string.node_connections_blocked),
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
                // iOS leaves the node and latency rows out until there is one.
                if (primaryHost != null) {
                    SettingsDivider()
                    ConnectionStatusRow(stringResource(R.string.connected_node), primaryHost, monospaced = true)
                }
                if (latencyMs != null) {
                    SettingsDivider()
                    ConnectionStatusRow(stringResource(R.string.latency), "$latencyMs ms", valueColor = latencyColor)
                }
                // The connected node's place, from the bundled geo database (iOS).
                activeNodes.firstOrNull()?.distanceKm?.let { km ->
                    SettingsDivider()
                    ConnectionStatusRow("Distance", formatNodeDistance(km, spaced = true))
                }
                activeNodes.firstOrNull()?.countryCode?.let { country ->
                    SettingsDivider()
                    ConnectionStatusRow("Country", country)
                }
                SettingsDivider()
                ConnectionStatusRow(stringResource(R.string.indexer), indexerHost, monospaced = true)
                SettingsDivider()
                ConnectionStatusRow(stringResource(R.string.last_sync), lastSyncAt)
            }

            if (showPoolSections) {
                SettingsSection(title = stringResource(R.string.pool_status)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        PoolStatItem(stringResource(R.string.active), activeNodes.size.toString(), LocalAppColors.current.success)
                        PoolStatItem(stringResource(R.string.verified), verifiedCount.toString(), Color(0xFF2196F3))
                        PoolStatItem(stringResource(R.string.total), allNodes.size.toString(), LocalAppColors.current.textSecondary)
                    }
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.pool_health), color = LocalAppColors.current.textPrimary)
                        Text(poolHealthText, color = statusColor)
                    }
                }
            }

            // iOS's Actions section: plain text buttons, a spinner in the row while it runs, no
            // toast; Clear asks first. The primary node's host is the section's footer.
            Column {
                SettingsSection(title = stringResource(R.string.actions)) {
                    // Refresh/Clear operate on pool discovery, which is off while pinned to a
                    // specific node - only Reconnect still means something there.
                    if (showPoolSections) {
                        ConnectionActionRow(
                            stringResource(R.string.refresh_pool),
                            color = KaspaTeal,
                            busy = isRefreshingPool,
                            enabled = poolActionsEnabled,
                        ) { viewModel.refreshPool() }
                        SettingsDivider()
                        ConnectionActionRow(
                            stringResource(R.string.clear_connection_pool),
                            color = LocalAppColors.current.danger,
                            busy = false,
                            enabled = poolActionsEnabled,
                        ) { showClearPoolConfirm = true }
                        SettingsDivider()
                    }
                    ConnectionActionRow(
                        stringResource(R.string.reconnect),
                        color = KaspaTeal,
                        busy = isReconnecting,
                        enabled = poolActionsEnabled,
                    ) { viewModel.reconnect() }
                }
                primaryHost?.let { primary ->
                    Text(
                        text = "Primary: $primary",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    )
                }
            }

            KaspaNodeQuickAccessSection(viewModel)

            if (showPoolSections) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(LocalAppColors.current.success, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.active_nodes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textSecondary)
                    }
                    Text(text = activeNodes.size.toString(), color = LocalAppColors.current.textSecondary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalAppColors.current.surface)
                ) {
                    if (activeNodes.isEmpty()) NodeListEmptyRow("No active nodes")
                    activeNodes.forEachIndexed { index, node ->
                        Box(Modifier.clickable { copyNodeEndpoint(node) }) { ActiveNodeRow(node) }
                        if (index < activeNodes.size - 1) SettingsDivider()
                    }
                }
                Text(
                    text = stringResource(R.string.tap_a_node_to_copy_host_port),
                    fontSize = 13.sp,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Every known node, active ones included, sorted by state then latency (the
                    // pool publishes them in that order) - iOS's All Nodes section.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "All Nodes", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textSecondary)
                    }
                    Text(text = allNodes.size.toString(), color = LocalAppColors.current.textSecondary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalAppColors.current.surface)
                ) {
                    if (allNodes.isEmpty()) NodeListEmptyRow("No nodes discovered")
                    allNodes.forEachIndexed { index, node ->
                        AllNodeRow(node)
                        if (index < allNodes.size - 1) SettingsDivider()
                    }
                }

                Text(
                    text = stringResource(R.string.all_discovered_nodes_sorted_by_state),
                    fontSize = 13.sp,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }


            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/** A label/value row of iOS ConnectionStatusDetailView's first section: the label 17pt, the value
 *  trailing in the secondary colour (or [valueColor]) - a host name in monospaced caption, as iOS
 *  writes the node and indexer - and, for Status, a 10pt dot in [dot]'s colour before it. */
/** A node's distance as iOS writes it: "42 km" under 100, else "1.2k km" (the node rows drop the
 *  space below 100: "42km"). */
internal fun formatNodeDistance(km: Double, spaced: Boolean): String =
    if (km < 100) "${km.toInt()}${if (spaced) " " else ""}km" else "%.1fk km".format(java.util.Locale.US, km / 1000)

/** An empty node list's one line, as iOS writes it: secondary and italic. */
@Composable
private fun NodeListEmptyRow(text: String) {
    Text(
        text,
        color = LocalAppColors.current.textSecondary,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 17.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun ConnectionStatusRow(
    label: String,
    value: String,
    valueColor: Color = LocalAppColors.current.textSecondary,
    monospaced: Boolean = false,
    dot: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LocalAppColors.current.textPrimary, fontSize = 17.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        if (dot != null) {
            Box(modifier = Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            value,
            color = valueColor,
            fontSize = if (monospaced) 12.sp else 17.sp,
            fontFamily = if (monospaced) FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of iOS ConnectionStatusDetailView's Actions section: the label as a plain text button,
 *  and while [busy], the activity indicator at the trailing edge. Greyed out while disabled. */
@Composable
private fun ConnectionActionRow(label: String, color: Color, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (enabled) color else LocalAppColors.current.textSecondary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (busy) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
    }
}

/** Settings > Connection > Kaspa Explorer, iOS's KaspaExplorerSettingsView: the explorers under an
 *  "Explorer" header, a checkmark on the chosen one, and iOS's footer under the card. */
@Composable
fun KaspaExplorerSettingsScreen(onBack: () -> Unit, walletViewModel: WalletViewModel = hiltViewModel()) {
    val kaspaExplorer by walletViewModel.kaspaExplorer.collectAsState()

    SettingsFormPage(title = stringResource(R.string.kaspa_explorer), onBack = onBack) {
        SettingsFormSection(
            title = stringResource(R.string.explorer),
            footer = stringResource(R.string.explorer_footer),
        ) {
            com.kachat.app.models.KaspaExplorer.entries.forEachIndexed { index, explorer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { walletViewModel.updateKaspaExplorer(explorer) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(explorer.displayName, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (kaspaExplorer == explorer) {
                        Icon(Icons.Default.Check, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                    }
                }
                if (index < com.kachat.app.models.KaspaExplorer.entries.lastIndex) {
                    SettingsDivider()
                }
            }
        }
    }
}

/**
 * A single dropdown for quickly picking which node to connect to: the default node (recommended),
 * automatic discovery, or any address already saved in the IP Address Book. Used by both
 * [ConnectionStatusScreen] (tap the status dot) and [ConnectionSettingsScreen] (above the
 * [AddressBookSection], which is where the address book itself is managed - adding, removing, and
 * labeling entries) - there's no free-text entry anymore, only this dropdown.
 */
@Composable
private fun KaspaNodeQuickAccessSection(viewModel: ConnectionViewModel) {
    val trustedNodeAddress by viewModel.trustedNodeAddress.collectAsState()
    val savedNodeAddresses by viewModel.savedNodeAddresses.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val normalizedTrusted = trustedNodeAddress.trim()
    val defaultAddress = com.kachat.app.repository.AppSettingsRepository.DEFAULT_TRUSTED_NODE_ADDRESS
    val matchingSaved = savedNodeAddresses.firstOrNull { it.address.trim() == normalizedTrusted }

    val selectedLabel = when {
        normalizedTrusted.isEmpty() -> stringResource(R.string.automatic_scan)
        normalizedTrusted == defaultAddress.trim() -> stringResource(R.string.default_recommended)
        matchingSaved != null -> matchingSaved.label.ifBlank { matchingSaved.address }
        else -> normalizedTrusted
    }

    // iOS: a Picker row in a Form - the "Kaspa Node" label, the current choice trailing in the
    // accent colour with the menu's up/down chevrons - and the explanation as the section's
    // footer, under the card rather than in it.
    Column {
    SettingsSection(title = stringResource(R.string.kaspa_node)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.kaspa_node),
                color = LocalAppColors.current.textPrimary,
                fontSize = 17.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Spacer(Modifier.weight(1f))
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedLabel,
                        color = KaspaTeal,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.UnfoldMore, null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    // The shipped default node is a mainnet node - not offered on testnet (iOS bdae4b7).
                    val selectedNetwork by com.kachat.app.util.KaspaNetwork.selected.collectAsState()
                    if (selectedNetwork == com.kachat.app.util.KaspaNetwork.Type.MAINNET || normalizedTrusted == defaultAddress.trim()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.default_recommended)) },
                        onClick = {
                            expanded = false
                            viewModel.setTrustedNodeAddress(defaultAddress)
                        }
                    )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.automatic_scan)) },
                        onClick = {
                            expanded = false
                            viewModel.setTrustedNodeAddress("")
                        }
                    )
                    savedNodeAddresses.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label.ifBlank { entry.address }) },
                            onClick = {
                                expanded = false
                                viewModel.setTrustedNodeAddress(entry.address)
                            }
                        )
                    }
                }
            }
        }
        if (normalizedTrusted.isNotEmpty()) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.connected_only_to_this_node), color = KaspaTeal, fontSize = 13.sp)
            }
        }
    }
    Text(
        stringResource(R.string.kaspa_node_quick_access_footer),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
    )
    }
}

/**
 * "IP Address Book" - the full management UI (add/remove/label saved addresses), used only by
 * [ConnectionSettingsScreen], right below its own [KaspaNodeQuickAccessSection]. The trusted-node
 * override itself is now dropdown-only (default/automatic/a saved address) - no free-text entry -
 * so this section is just the address book, feeding entries into that dropdown.
 */
@Composable
private fun AddressBookSection(viewModel: ConnectionViewModel) {
    val savedNodeAddresses by viewModel.savedNodeAddresses.collectAsState()
    val pinnedNodeAddress by viewModel.trustedNodeAddress.collectAsState()
    val nodeBookHaptic = com.kachat.app.util.rememberHaptics()

    SettingsSection(title = stringResource(R.string.ip_address_book)) {
        var newLabel by remember { mutableStateOf("") }
        var newAddress by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf<String?>(null) }
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current

        TextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            placeholder = { Text(stringResource(R.string.label_optional), color = LocalAppColors.current.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(50.dp).clip(RoundedCornerShape(12.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LocalAppColors.current.surfaceVariant,
                unfocusedContainerColor = LocalAppColors.current.surfaceVariant,
                focusedTextColor = LocalAppColors.current.textPrimary,
                unfocusedTextColor = LocalAppColors.current.textPrimary,
                cursorColor = KaspaTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = newAddress,
                onValueChange = { newAddress = it; addError = null },
                placeholder = { Text(stringResource(R.string.host_port_or_grpcs_host), color = LocalAppColors.current.textTertiary) },
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LocalAppColors.current.surfaceVariant,
                    unfocusedContainerColor = LocalAppColors.current.surfaceVariant,
                    focusedTextColor = LocalAppColors.current.textPrimary,
                    unfocusedTextColor = LocalAppColors.current.textPrimary,
                    cursorColor = KaspaTeal,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = {
                    val trimmed = newAddress.trim()
                    if (trimmed.isEmpty()) return@IconButton
                    if (com.kachat.app.services.grpc.parseNodeAddress(trimmed) == null) {
                        addError = "Enter as host:port or grpcs://host"
                    } else {
                        addError = null
                        viewModel.addSavedNodeAddress(newLabel.trim(), trimmed)
                        newLabel = ""
                        newAddress = ""
                    }
                },
                modifier = Modifier.size(40.dp).background(KaspaTeal, CircleShape)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        }
        addError?.let {
            Text(it, color = LocalAppColors.current.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (savedNodeAddresses.isEmpty()) {
            Text(
                stringResource(R.string.no_saved_addresses),
                color = LocalAppColors.current.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            savedNodeAddresses.forEachIndexed { index, entry ->
                if (index > 0) SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(entry.address))
                            nodeBookHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                            IosToasts.show("Node address copied.")
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (entry.label.isNotBlank()) {
                            Text(entry.label, color = LocalAppColors.current.textPrimary)
                            Text(
                                entry.address,
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(entry.address, color = LocalAppColors.current.textPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                    // Immediate delete (matching this screen's convention: row taps copy with a
                    // toast, no confirm dialogs anywhere), so a toast confirms the removal.
                    // Deleting the entry the Kaspa Node dropdown currently pins does NOT touch
                    // node selection: only the book entry goes, the pinned address stays set
                    // and the dropdown falls back to showing the raw address (see
                    // KaspaNodeQuickAccessSection's selectedLabel).
                    IconButton(onClick = {
                        viewModel.removeSavedNodeAddress(entry.id)
                        nodeBookHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        // iOS deleteSavedNodeAddress: which of the two cases it was.
                        IosToasts.show(
                            context.getString(
                                if (pinnedNodeAddress.trim().isNotEmpty() && pinnedNodeAddress.trim() == entry.address.trim()) R.string.node_removed_still_connected
                                else R.string.node_address_removed
                            )
                        )
                    }) {
                        Icon(Icons.Default.Delete, "Delete saved address", tint = LocalAppColors.current.textSecondary)
                    }
                }
            }
        }
        SettingsFooter(stringResource(R.string.save_your_own_node_addresses_here))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(onBack: () -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    val network by viewModel.network.collectAsState()
    val indexerUrl by viewModel.indexerUrl.collectAsState()
    val knsApiUrl by viewModel.knsApiUrl.collectAsState()
    val kaspaRestApiUrl by viewModel.kaspaRestApiUrl.collectAsState()
    // The URL being edited, if any: every endpoint is editable, as on iOS's ConnectionSettingsView.
    var editingEndpoint by remember { mutableStateOf<ConnectionViewModel.Endpoint?>(null) }
    val broadcastIndexerUrl by viewModel.broadcastIndexerUrl.collectAsState()
    val scrollState = rememberScrollState()
    val onMainnetSettings = network.equals("mainnet", ignoreCase = true)

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            SettingsSubpageBar(
                title = stringResource(R.string.connection_settings),
                onBack = onBack,
                trailing = { SheetBarButton(stringResource(R.string.save), onClick = onBack) },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // One tab per kind of connection, so the page stays short as the app talks to more
            // services: indexers, the Kaspa node, name services, the block explorer (iOS ee01f81,
            // da59726). Every field keeps its value while you switch between them.
            val connectionTabs = listOf(
                stringResource(R.string.connection_tab_indexer),
                stringResource(R.string.connection_tab_node),
                stringResource(R.string.connection_tab_domains),
                stringResource(R.string.connection_tab_explorer),
            )
            var selectedConnectionTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
            UnderlineTabBar(
                titles = connectionTabs,
                selectedIndex = selectedConnectionTab,
                onSelect = { selectedConnectionTab = it },
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
        ) {
            // Each section spaces itself, its footer under the card (SettingsFormSection).
            Spacer(Modifier.height(16.dp))
            when (selectedConnectionTab) {
            0 -> {
            // In English until translated, as on iOS (da59726).
            SettingsFormSection(title = stringResource(R.string.message_indexer), footer = "Serves .kachat names when it has them (see the Domains tab).") {
                ConnectionUrlField(
                    label = "Indexer URL",
                    value = indexerUrl,
                    onClick = { editingEndpoint = ConnectionViewModel.Endpoint.MESSAGE_INDEXER }
                )
            }

            SettingsFormSection(title = "Chess Indexer", footer = "Serves the chess arena's rooms, moves and leaderboards. A server that doesn't have them means online games can't find each other.") {
                ConnectionUrlField(
                    label = "Chess Indexer URL",
                    value = broadcastIndexerUrl,
                    onClick = { editingEndpoint = ConnectionViewModel.Endpoint.PUBLIC_CHATS_INDEXER }
                )
            }

            // Verbose API Logging lives in Settings > Diagnostics, as on iOS.
            }
            1 -> {
            KaspaNodeQuickAccessSection(viewModel)
            Spacer(Modifier.height(24.dp))
            AddressBookSection(viewModel)
            }
            2 -> {
            // .kachat first: it is what every address field resolves before anything else (iOS 6ac48a7).
            SettingsFormSection(title = stringResource(R.string.kn_kachat_names), footer = stringResource(R.string.kachat_names_resolved_first_footer)) {
                val isMainnet = network.equals("mainnet", ignoreCase = true)
                // Read through the chat indexer above when it serves this network's names, else
                // straight from the chain (KachatNamesRegistry, iOS 5df42b4, ef6b21e).
                ConnectionUrlField(
                    label = "KaChat Names (.kachat)",
                    value = stringResource(if (isMainnet) R.string.kn_live_on_mainnet else R.string.kn_live_testnet_registry)
                )
                // Where the names come from right now (iOS 1d23002): the Message Indexer's URL,
                // or the chain when it doesn't serve them or is behind.
                val namesSource by viewModel.kachatNamesSource.collectAsState()
                LaunchedEffect(Unit) { viewModel.prepareKachatNamesSource() }
                SettingsDivider()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Source", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                    when (val source = namesSource) {
                        is com.kachat.app.services.kachatnames.KachatNamesRegistry.Source.Indexer -> Text(
                            source.base, color = LocalAppColors.current.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 15.sp,
                        )
                        com.kachat.app.services.kachatnames.KachatNamesRegistry.Source.Chain -> {
                            Text("Read straight from the chain", color = LocalAppColors.current.textSecondary, fontSize = 15.sp)
                            Text(
                                "The Message Indexer doesn't serve .kachat names on this network, or is behind.",
                                color = LocalAppColors.current.textSecondary, fontSize = 12.sp,
                            )
                        }
                        null -> Text("Checking...", color = LocalAppColors.current.textSecondary, fontSize = 15.sp)
                    }
                }
                SettingsDivider()
                SettingsActionItem("Change on the Indexer tab", Icons.Default.ArrowCircleRight, KaspaTeal) {
                    selectedConnectionTab = 0
                }
            }
            // The other name services this app reads (see NameServicesClient). Shown, not
            // editable; the .kas URL follows the selected network.
            SettingsFormSection(title = stringResource(R.string.other_domains_title), footer = stringResource(R.string.other_domains_settings_footer)) {
                val isMainnet = network.equals("mainnet", ignoreCase = true)
                ConnectionUrlField(label = ".kas", value = knsApiUrl)
                listOf(com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA).forEach { tld ->
                    ConnectionUrlField(
                        label = tld.suffix,
                        value = tld.apiBaseUrl(isMainnet) ?: stringResource(R.string.not_available_on_this_network),
                    )
                }
            }
            }
            else -> {
            SettingsFormSection(title = stringResource(R.string.kaspa_explorer_api), footer = stringResource(R.string.rest_api_for_transaction_history_and)) {
                ConnectionUrlField(
                    label = "Kaspa REST API URL",
                    value = kaspaRestApiUrl,
                    onClick = { editingEndpoint = ConnectionViewModel.Endpoint.KASPA_REST }
                )
            }
            }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
        }
    }

    editingEndpoint?.let { endpoint ->
        val S = com.kachat.app.repository.AppSettingsRepository
        val (title, current, default) = when (endpoint) {
            ConnectionViewModel.Endpoint.MESSAGE_INDEXER ->
                Triple("Indexer URL", indexerUrl, if (onMainnetSettings) S.DEFAULT_INDEXER_URL else "")
            // The network's default: none on testnet, which has no KaChat indexer.
            ConnectionViewModel.Endpoint.PUBLIC_CHATS_INDEXER ->
                Triple("Chess Indexer URL", broadcastIndexerUrl, if (onMainnetSettings) S.DEFAULT_BROADCAST_INDEXER_URL else "")
            ConnectionViewModel.Endpoint.KASPA_REST ->
                Triple("Kaspa REST API URL", kaspaRestApiUrl, if (onMainnetSettings) S.DEFAULT_KASPA_REST_URL else S.DEFAULT_KASPA_TESTNET_REST_URL)
        }
        var draft by remember(endpoint) { mutableStateOf(current) }
        var rejected by remember(endpoint) { mutableStateOf(false) }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { editingEndpoint = null },
            containerColor = LocalAppColors.current.surface,
            title = { Text(title, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    com.kachat.app.ui.theme.IosTextField(
                        value = draft,
                        onValueChange = { draft = it; rejected = false },
                        singleLine = true,
                        isError = rejected,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                            autoCorrect = false,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // iOS's words for a plain-http URL.
                        if (rejected) "Use https. Unencrypted connections are not supported."
                        // Testnet has no default indexer (iOS c2d4bc1).
                        else if (default.isEmpty()) "No testnet indexer yet"
                        else "Leave blank to use $default",
                        color = if (rejected) Color(0xFFE57373) else LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.setEndpointUrl(endpoint, draft, current)) editingEndpoint = null else rejected = true
                }) { Text("Save", color = KaspaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { editingEndpoint = null }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/** A connection endpoint's row; with [onClick] it opens that endpoint's edit dialog. */
@Composable
fun ConnectionUrlField(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(16.dp)
    ) {
        Text(label, color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        // iOS shows an address in the body font, monospaced.
        Text(
            text = value,
            color = LocalAppColors.current.textPrimary,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun PoolStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActiveNodeRow(node: NodeInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // weight(1f) here (not on the status badge) so a long address — real discovered
            // peers can be IPv6 literals like [2601:680:cc80:5630:e1e5:e6fa:c86b:b946]:16111,
            // much longer than the old hardcoded IPv4 seeds — elides instead of squeezing the
            // badge down to near-zero width, which forced its own text to wrap letter by letter.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Shield, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(node.ip, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.background(Color(0xFF1E3A1E), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(node.status, color = Color(node.color), fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row {
                Text(node.type, color = Color(0xFF2196F3), fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(node.latency, color = LocalAppColors.current.warning, fontSize = 10.sp)
                node.distanceKm?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(formatNodeDistance(it, spaced = false), color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
                }
                node.countryCode?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
                }
            }
            Text("DAA: ${node.daaScore}", color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun AllNodeRow(node: NodeInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // weight(1f) here (not on the latency/status side) so a long address — real discovered
        // peers can be IPv6 literals like [2601:680:cc80:5630:e1e5:e6fa:c86b:b946]:16111, much
        // longer than the old hardcoded IPv4 seeds — elides instead of squeezing the status
        // badge down to near-zero width, which forced its own text to wrap letter by letter.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(8.dp).background(Color(node.color), CircleShape))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Shield, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(node.ip, color = LocalAppColors.current.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            node.countryCode?.let {
                Text(it, color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
            }
            node.distanceKm?.let {
                Text(formatNodeDistance(it, spaced = false), color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(node.latency, color = LocalAppColors.current.warning, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.background(Color(0x33FF3B30), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(node.status, color = Color(node.color), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun rememberQrBitmapPainter(
    content: String,
    size: Int = 512,
    padding: Int = 0
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.roundToPx() }
    
    val bitmap = remember(content) {
        if (content.isEmpty()) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to padding)
            )
            val w = matrix.width
            val h = matrix.height
            // One setPixels() beats width*height individual bounds-checked setPixel() calls
            // (~262k for a 512px QR) by 1-2 orders of magnitude - this runs on the main thread
            // inside remember{}, and the animated (KSPT) variant re-encodes every 2.5s.
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.asImageBitmap()
        }
    }
    return BitmapPainter(bitmap)
}

/**
 * Byte-mode QR encoding for raw binary payloads (e.g. a KSPT transaction frame) — ISO-8859-1 maps
 * every byte 1:1 to a char and back, so wrapping [bytes] this way and forcing ZXing's
 * `CHARACTER_SET` hint round-trips the exact bytes through its String-based encoder in byte mode,
 * matching KasSigner's own raw-byte QR encoding (`qrcode::QrCode::new(&[u8])`) on the other end.
 */
@Composable
fun rememberQrBitmapPainter(
    bytes: ByteArray,
    size: Int = 512,
    padding: Int = 0
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.roundToPx() }

    val bitmap = remember(bytes) {
        if (bytes.isEmpty()) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val content = String(bytes, Charsets.ISO_8859_1)
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to padding, EncodeHintType.CHARACTER_SET to "ISO-8859-1")
            )
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        }
    }
    return BitmapPainter(bitmap)
}

/** How an [AddressQrPage] is closed - iOS puts a different control in its bar for each way it
 *  presents the page. */
enum class QrPageDismiss {
    /** Pushed (Profile): a back chevron, leading. */
    BACK,
    /** In a sheet with Close leading (Chatting Address details). */
    CLOSE,
    /** In a sheet with Done trailing (the Chats New sheet). */
    DONE,
}

/**
 * iOS's `ChattingAddressQRView`: the full white page with an address's QR code - the code in a
 * 3pt accent frame, the whole address under it, what the address is for, and "Tap anywhere to
 * copy" - placed a third of the way down. Its bar shows the address's balance in the title's
 * place (iOS's principal toolbar item, which takes precedence over the navigation title), or "—"
 * while it is unknown, with [dismiss]'s control. A tap anywhere copies the address.
 */
@Composable
fun AddressQrPage(
    address: String,
    balanceSompi: Long?,
    caption: String,
    dismiss: QrPageDismiss,
    onDismiss: () -> Unit,
    /** Under the bar: Receive's backup reminder. */
    banner: @Composable () -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clipboardManager.setText(AnnotatedString(address))
                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                com.kachat.app.util.showAddressCopiedToast(context, address, haptic = false)
            },
    ) {
        AddressQrPageBar(dismiss = dismiss, onDismiss = onDismiss) {
            // Black in dark mode too: this screen is always white (iOS ddec987).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Receive", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    balanceSompi?.let { "%.8f %s".format(java.util.Locale.US, it / 100_000_000.0, KaspaUnit.symbol) } ?: "—",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }
        banner()
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
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
                color = Color.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Text(
                caption,
                color = Color.Black.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Text(
                stringResource(R.string.tap_anywhere_to_copy),
                color = Color.Black.copy(alpha = 0.4f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The page's navigation bar: [dismiss]'s control and [center] in the title's place. */
@Composable
private fun AddressQrPageBar(dismiss: QrPageDismiss, onDismiss: () -> Unit, center: @Composable () -> Unit) {
    // A sheet's bar is 56 tall on iOS, a pushed page's 44.
    // iOS 26: each control on its own glass, 16 in from the edge.
    Box(Modifier.fillMaxWidth().height(if (dismiss == QrPageDismiss.BACK) 52.dp else 60.dp).padding(horizontal = 16.dp)) {
        // iOS's glass takes on what is under it: over the white page the control is light glass
        // with a black chevron, in dark mode too. (The balance keeps the theme's colour, below.)
        CompositionLocalProvider(LocalAppColors provides com.kachat.app.ui.theme.LightAppColors) {
            when (dismiss) {
                QrPageDismiss.BACK -> SheetBarBackButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                QrPageDismiss.CLOSE -> SheetBarButton(stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                QrPageDismiss.DONE -> SheetBarButton(stringResource(R.string.done), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        Box(Modifier.align(Alignment.Center)) { center() }
    }
}

/**
 * The chatting address's QR page ([AddressQrPage] with the chatting caption, iOS
 * `ChattingAddressQRView`'s default).
 */
@Composable
fun ChattingAddressQrPage(
    address: String,
    balanceSompi: Long?,
    dismiss: QrPageDismiss,
    onDismiss: () -> Unit,
    banner: @Composable () -> Unit = {},
) {
    AddressQrPage(
        address = address,
        balanceSompi = balanceSompi,
        caption = stringResource(R.string.qr_chatting_address_subtitle),
        dismiss = dismiss,
        onDismiss = onDismiss,
        banner = banner,
    )
}

/**
 * The Wallet tab, laid out like KaPost's Wallet screen (iOS 8d5f925 `ChattingAddressManageView`):
 * the total (tap to copy), split into Chatting and Spending, then Send, Receive and More, the Backup
 * Hygiene card when due, then the chatting address's history. KaPlay uses only the chatting
 * address, the one it plays from; spending addresses only count in the total.
 *
 * Superseded since iOS 1751d20: the Wallet tab is [ChessWalletTabScreen] (ChessWalletTab.kt), KaPay's
 * full wallet. Only [IdentityAddressDetailScreen] with a navController still draws this; kept for
 * the Chatting Address page's port (Manage Addresses > Chatting Address) to take what it needs.
 */
@Composable
private fun ChessWalletTabBody(
    modifier: Modifier,
    chattingSompi: Long?,
    spendingSompi: Long?,
    hygieneDue: Boolean,
    txHistory: List<ColdStorageAddressDiscovery.AddressTransaction>,
    isLoadingHistory: Boolean,
    historyFailed: Boolean,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onMore: () -> Unit,
    onHygiene: () -> Unit,
    onRefresh: () -> Unit,
    onTransactionClick: (ColdStorageAddressDiscovery.AddressTransaction) -> Unit,
    priceViewModel: com.kachat.app.viewmodels.KaspaPriceViewModel = hiltViewModel(),
) {
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val price by priceViewModel.currentPriceUsd.collectAsState()
    val currency by priceViewModel.currency.collectAsState()
    // Both parts or a dash: a total missing one of them would read as the whole balance.
    val total = if (chattingSompi != null && spendingSompi != null) chattingSompi + spendingSompi else null
    AddressHistoryRefreshable(isLoading = isLoadingHistory, onRefresh = onRefresh, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "balance") {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(KaspaTeal.copy(alpha = 0.32f), KaspaTeal.copy(alpha = 0.08f))))
                        .border(0.8.dp, KaspaTeal.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total Balance", color = colors.textSecondary, fontSize = 15.sp)
                        Text(
                            total?.let { "${KaspaUnit.plain(it)} ${KaspaUnit.symbol}" } ?: "--",
                            color = colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.clickable(enabled = total != null) {
                                total ?: return@clickable
                                clipboard.setText(AnnotatedString(KaspaUnit.plain(total)))
                                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                                IosToasts.show("Balance copied to clipboard.")
                            },
                        )
                        // What the balance is worth in your currency. Testnet coins aren't worth anything.
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
                                Text(
                                    sompi?.let { "${KaspaUnit.plain(it)} ${KaspaUnit.symbol}" } ?: "--",
                                    color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "actions") {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WalletActionButton("Send", Icons.Default.ArrowUpward, enabled = (chattingSompi ?: 0L) > 0L, modifier = Modifier.weight(1f), onClick = onSend)
                    WalletActionButton("Receive", Icons.Default.ArrowDownward, modifier = Modifier.weight(1f), onClick = onReceive)
                    WalletActionButton("More", Icons.Default.MoreHoriz, modifier = Modifier.weight(1f), onClick = onMore)
                }
            }
            if (hygieneDue) {
                item(key = "hygiene") { Box(Modifier.padding(horizontal = 16.dp)) { BackupHygieneCard(onClick = onHygiene) } }
            }
            item(key = "historyTitle") {
                Text("Transaction History", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
            }
            when {
                isLoadingHistory && txHistory.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                    }
                }
                txHistory.isEmpty() && historyFailed -> item(key = "failed") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Could not load transactions.", color = colors.textSecondary, fontSize = 17.sp)
                        Text(stringResource(R.string.try_again), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRefresh))
                    }
                }
                txHistory.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.no_transactions_yet), color = colors.textSecondary, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                else -> {
                    if (historyFailed) {
                        item(key = "incomplete") {
                            Text(
                                "Some transactions could not be loaded. Pull to refresh to try again.",
                                color = colors.warning, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    item(key = "history") {
                        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)) {
                            txHistory.forEachIndexed { i, tx ->
                                if (i > 0) HorizontalDivider(color = colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 14.dp))
                                SpendingAddressTxHistoryRow(tx = tx, onClick = { onTransactionClick(tx) }, modifier = Modifier.padding(horizontal = 0.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One of the Wallet tab's Send / Receive / More: a 60 accent circle over its name. */
@Composable
private fun WalletActionButton(title: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(KaspaTeal), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Text(title, color = LocalAppColors.current.textPrimary, fontSize = 15.sp)
    }
}

/** The Wallet tab's More: Compound UTXOs (greyed out under two coins), the chatting address's keys
 *  and the explorer (iOS 8d5f925 `addressActionsSheet`). */
@Composable
private fun ChessWalletMoreSheet(
    coinCount: Int,
    onCompound: () -> Unit,
    onPrivateKey: () -> Unit,
    onPublicKey: () -> Unit,
    onExplorer: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionSheetContainer(title = "Chatting Address", subtitle = null, onDismiss = onDismiss) {
        // Always listed, so it's there to find; it takes two coins to combine.
        ActionSheetRow(
            icon = Icons.Default.CallMerge,
            title = "Compound UTXOs",
            subtitle = if (coinCount > 1) "Combines this address's coins into one, so later sends need fewer inputs."
            else "Combines this address's coins into one. It needs two or more coins.",
            enabled = coinCount >= 2,
            onClick = onCompound,
        )
        ActionSheetRow(icon = Icons.Default.Key, title = "View Private Key", subtitle = "The key that spends this address. Never share it.", onClick = onPrivateKey)
        ActionSheetRow(icon = Icons.Default.Numbers, title = "View Public Key", subtitle = "The public half of this address, for anyone who asks for it.", onClick = onPublicKey)
        ActionSheetRow(icon = Icons.Default.Public, title = stringResource(R.string.view_in_explorer), subtitle = "Opens this address on your chosen block explorer.", onClick = onExplorer)
    }
}

/** Receive's reminder while the account owes its backup (iOS ddec987). Since iOS 8d5f925 the
 *  strip above each tab's title says it instead; kept for screens that still want a reminder. */
@Composable
internal fun ReceiveBackupReminder() {
    val orange = LocalAppColors.current.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(orange.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.GppMaybe, contentDescription = null, tint = orange, modifier = Modifier.size(18.dp))
        Text(
            "This account isn't backed up yet. Use the card on the Wallet tab to back it up.",
            color = orange,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * A QR page presented the way iOS presents it from a sheet: a full-height sheet of its own over
 * the screen (the Chats New sheet's two, Chatting Address details' code). White while the code
 * shows, so the page reaches the sheet's edges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPageSheet(onDismiss: () -> Unit, white: Boolean, content: @Composable () -> Unit) {
    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (white) Color.White else LocalAppColors.current.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    IosToastHost(bottomInsets = SheetToastInsets)
                }
            }
        }
    }
}

/** One row in the group-member address list - supports both a raw Kaspa address and a KNS domain, resolved the same way the single-contact flow's address field does. */
/**
 * "Other domains": what the same typed name points to on the other name services, under the name
 * it resolved to, each one selectable (iOS a0dbc15, 6ac48a7 `OtherDomainsDropdown`). Every field
 * that takes an address shows it; the order is always .kachat first. Unregistered, unreachable or
 * not-yet-live ones are listed but not selectable. Opens by itself when nothing was picked but
 * another service has the name.
 */
@Composable
fun OtherDomainsDropdown(
    resolutions: List<com.kachat.app.services.NameResolution>,
    selected: com.kachat.app.services.NameServiceTLD?,
    onPick: (com.kachat.app.services.NameResolution) -> Unit,
) {
    val others = resolutions.filter { it.tld != selected }
    if (others.isEmpty()) return
    val openByItself = selected == null && resolutions.any { it.address != null }
    var expanded by remember(resolutions) { mutableStateOf(openByItself) }
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.other_domains), color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            others.forEach { resolution ->
                val address = resolution.address
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = address != null) {
                            onPick(resolution)
                            expanded = false
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            resolution.display,
                            color = if (address == null) colors.textSecondary else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MiddleEllipsisText(
                            address ?: stringResource(
                                when {
                                    resolution.notLive -> R.string.coming_soon
                                    resolution.failed -> R.string.couldnt_check
                                    else -> R.string.not_registered
                                }
                            ),
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    if (address != null) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** A send the chosen coins can't pay for, amount and fee together. */
private const val INSUFFICIENT_FOR_FEE_MESSAGE = "Not enough KAS to cover this amount and the network fee."

/** Every coin picked in coin control is gone (spent since): the send's own refusal, which the
 *  Review shows in place of a fee (iOS 4922804 `resolveReviewSelection`). */
private const val SELECTED_COINS_GONE_MESSAGE = "Selected UTXOs are no longer available - please reselect"

/** Max with no coin to spend (iOS 1751d20 `estimateMaxWithdrawalAmount`'s error, as shown). */
private const val MAX_NO_SPENDABLE_MESSAGE = "No spendable UTXOs"

/** Max when the fee takes every coin (iOS 1751d20 `maxSendAmount`'s error, as shown). */
private const val MAX_FEE_TOO_HIGH_MESSAGE = "Balance too low to cover fee"

/** Max when the wallet can't be asked yet: the REST client comes up a moment after launch (no
 *  iOS counterpart; iOS's node pool answers from the start). */
private const val MAX_NOT_READY_MESSAGE = "Still connecting. Try Max again in a moment."

/** Max failing for any other reason ([UserFacingError]'s fallback). */
private const val MAX_FAILED_FALLBACK = "Could not work out the maximum."
