package com.kachat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.kachat.app.services.SendOutcomeUnknownException
import com.kachat.app.util.authenticateWithDeviceCredential
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.KaspaUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// The swipe-up send screen, copied from KaPay Android (KaChat-Wallet-Android
// `SwipeUpSendScreen.kt`; iOS `SwipeUpToSendScrollView`, `SendProgressScreen`,
// `BlockDAGAnimationView` in Views/Shared/SendKaspaComponents.swift, KaPlay 38e0455). It is the
// review step of Send Kaspa and Compound UTXOs: Back to the amount, Cancel, and swipe up to send.
// Change it here rather than in one screen.

/** The words a swipe-up screen uses (iOS `SwipeUpLabels`): a Kaspa send. */
data class SwipeUpLabels(
    /** The bar (and the title on the sending screen) once the swipe is past halfway. */
    @StringRes val release: Int,
    /** The sending screen's title while it goes out. */
    @StringRes val working: Int,
    /** The line under it while it goes out. */
    @StringRes val workingDetail: Int,
    /** The title once it went out. */
    @StringRes val done: Int,
    /** The line under the title while the swipe is still short of halfway. */
    @StringRes val hint: Int,
    /** The recipient line, with `%1$s`: "to kaspa:..." or "for name.kachat". */
    @StringRes val recipientLine: Int,
    /** Under "Failed" when the screen has no error of its own. */
    @StringRes val failedFallback: Int,
) {
    companion object {
        val Send = SwipeUpLabels(
            release = R.string.swipe_release_to_send,
            working = R.string.sending,
            workingDetail = R.string.swipe_sending_detail,
            done = R.string.sent,
            hint = R.string.swipe_hint_send,
            recipientLine = R.string.payment_to_name,
            failedFallback = R.string.swipe_send_failed_fallback,
        )
    }
}

/** iOS `.spring(response: 0.45, dampingFraction: 0.86)`: stiffness (2*pi/0.45)^2. */
private val SwipeSettleSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 195f)

/**
 * The swipe-up state (iOS `SwipeUpToSendScrollView`'s @State): how far the screen is pushed up,
 * whether letting go sends, and where the send is. Its plain fields are the screen's latest
 * parameters, refreshed after every composition, so the gesture and the timers read today's
 * values rather than the ones their closures first captured (iOS reads them through @State for
 * the same reason).
 */
@Stable
private class SwipeUpController(private val scope: CoroutineScope) {
    /** How far the screen has been pushed up, in px. */
    var lift by mutableFloatStateOf(0f)
    /** Past the threshold: letting go sends. */
    var armed by mutableStateOf(false)
    /** The phone's lock is up for a send. Until it answers the screen is still the form, so without
     *  this a second swipe in that moment asked again and could send twice (iOS 8d5f925). */
    var authPending by mutableStateOf(false)
    var authenticate: (onSuccess: () -> Unit, onFailure: () -> Unit) -> Unit = { _, fail -> fail() }
    /** iOS `phase == .sending`: from the swipe until Try Again brings the form back. */
    var sending by mutableStateOf(false)
    /** The DAG gets a moment on screen before the outcome, however fast the send was. */
    var dagShown by mutableStateOf(false)
    /** The screen went busy after the swipe: the send really started. */
    var sendStarted by mutableStateOf(false)
    /** It went out (a transaction, or a screen's own success). */
    var confirmed by mutableStateOf(false)
    /** It ended without going out: the sending screen shows "Failed". */
    var failed by mutableStateOf(false)

    var active = false
    var isBusy = false
    var heightPx = 0f
    var thresholdPx = 0f
    var haptic: (IosHaptic) -> Unit = {}
    var action: () -> Unit = {}
    var beforeSend: () -> Unit = {}

    var root: LayoutCoordinates? = null
    var bar: LayoutCoordinates? = null

    private var liftJob: Job? = null
    private val timers = mutableListOf<Job>()

    fun settle(to: Float) {
        liftJob?.cancel()
        liftJob = scope.launch {
            animate(lift, to, animationSpec = SwipeSettleSpring) { value, _ -> lift = value }
        }
    }

    fun dragStart() {
        liftJob?.cancel()
    }

    fun drag(up: Float) {
        if (!active || sending || authPending) return
        if (lift == 0f && up > 0f) haptic(IosHaptic.IMPACT_LIGHT)
        lift = max(0f, up)
        val past = lift >= thresholdPx
        if (past != armed) {
            armed = past
            haptic(if (past) IosHaptic.IMPACT_MEDIUM else IosHaptic.IMPACT_LIGHT)
        }
    }

    fun dragEnd() {
        // a touch while the lock is up leaves the waiting send as it is
        if (sending || authPending) return
        if (armed && active) {
            authenticateThenSend()
        } else {
            armed = false
            settle(0f)
        }
    }

    /** Kaspa never leaves on the swipe alone: the phone's lock first, on every Send Kaspa screen.
     *  Declined, the screen settles back with everything still filled in (iOS 8d5f925). */
    fun authenticateThenSend() {
        if (authPending || sending) return
        authPending = true
        authenticate(
            {
                authPending = false
                startSending()
            },
            {
                authPending = false
                armed = false
                settle(0f)
            },
        )
    }

    fun startSending() {
        if (sending) return
        timers.forEach { it.cancel() }
        timers.clear()
        armed = false
        dagShown = false
        sendStarted = isBusy
        confirmed = false
        failed = false
        sending = true
        beforeSend()
        // up and out: the sending screen settles over the whole screen
        settle(heightPx * 2)
        action()
        timers += scope.launch {
            delay(1_800)
            dagShown = true
        }
        // A send that never started (a check failed before it went busy) fails too.
        timers += scope.launch {
            delay(600)
            if (!sendStarted) showFailure()
        }
    }

    /** The send ended without going out: "Failed" with the error, once the DAG has had its moment. */
    fun showFailure() {
        if (!sending || confirmed || failed) return
        failed = true
        haptic(IosHaptic.ERROR)
    }

    fun confirm() {
        if (confirmed) return
        confirmed = true
        haptic(IosHaptic.SUCCESS)
    }

    /** Try Again: back down to the form, filled in as it was. */
    fun returnToForm() {
        if (!sending || confirmed) return
        settle(0f)
        // After the screen is back down, so the sending screen doesn't change on its way out.
        timers += scope.launch {
            delay(450)
            sending = false
            failed = false
        }
    }
}

/**
 * A send screen with "Swipe up to Send" pinned at the bottom (iOS 59593af
 * `SwipeUpToSendScrollView`). Press the bar and drag it up: the whole screen moves up with the
 * finger, and the sending screen rises from below in its place. Letting go about a third of the way up sends: the
 * sending screen takes over, a BlockDAG runs across it while the transaction goes out, and then it
 * shows the confirmation ([sent], or [succeeded] with [outcome] for an action that ends without a
 * plain transaction - a .kachat claim, iOS 5bab351). Letting go sooner springs it back. A send that
 * fails, or never goes busy within 0.6 s of the swipe, shows "Failed" with [failureMessage], and
 * Try Again brings the form back as it was. Only the bar answers - scrolling the form never sends.
 * Accessibility services get a plain click action on the bar.
 *
 * It draws its own header ([SendStepHeader]: Back when [onBack] is set, Cancel, and
 * [screenTitle]) in place of a top bar, so the header goes up with the rest of the screen. [insets] are the system insets it should keep clear of itself:
 * the system bars when it fills the window, none inside a sheet that already pads for them. The
 * keyboard is always kept clear of the bar.
 *
 * The screen owns the send: [onSwipe] starts it (and should go busy at once, or within 0.6 s),
 * [isBusy] is true while it is out, and [sent] / [succeeded] or [failureMessage] report how it
 * ended. Back is [onBack] (else Cancel) on the form, Try Again after a failure, Done after a send; while it is
 * going out it does nothing (iOS `interactiveDismissDisabled`). [onSendingChange] tells a
 * presenting sheet when to refuse being swiped away.
 */
@Composable
fun SwipeUpToSendScreen(
    screenTitle: String,
    onCancel: () -> Unit,
    /** A step-by-step send's review: the header shows Back (and Cancel on the right). */
    onBack: (() -> Unit)? = null,
    /** False: only Back in the header (iOS `showsCancel`). */
    showsCancel: Boolean = true,
    /** The bar's words, e.g. "Swipe up to Send". */
    barTitle: String,
    isBusy: Boolean,
    isEnabled: Boolean,
    /** What is being sent, for the sending screen; null leaves the line out. */
    amountSompi: Long?,
    recipient: String?,
    /** Set by the screen once the send went out: the confirmation shows it. */
    sent: SentTransaction?,
    /** The screen's error for a send that failed, shown under "Failed". */
    failureMessage: String?,
    /** The confirmation's Done. */
    onDone: () -> Unit,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    labels: SwipeUpLabels = SwipeUpLabels.Send,
    /** What the device-lock prompt says, when this screen does something other than send Kaspa
     *  (iOS 1751d20 `authReason`, the .kachat transactions). */
    authReason: String? = null,
    /** For an action that ends without a [SentTransaction]: true once it worked. */
    succeeded: Boolean = false,
    /** Shown under the title once it worked, in place of the transaction (a claim's progress). */
    outcome: (@Composable () -> Unit)? = null,
    /** The block explorer link for a transaction id (Settings' explorer), or null for none. */
    explorerUrl: (String) -> String? = { null },
    insets: WindowInsets = WindowInsets.systemBars,
    onSendingChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val c = remember { SwipeUpController(scope) }
    val swipeContext = androidx.compose.ui.platform.LocalContext.current
    val currentOnSendingChange by rememberUpdatedState(onSendingChange)

    LaunchedEffect(isBusy) {
        if (isBusy) {
            c.sendStarted = true
        } else {
            // The screen sets `sent` as it stops being busy; give that a moment to land.
            delay(300)
            if (!c.confirmed) c.showFailure()
        }
    }
    LaunchedEffect(sent != null, succeeded) {
        if (sent != null || succeeded) c.confirm()
    }
    LaunchedEffect(c.sending) { currentOnSendingChange(c.sending) }

    val done = c.dagShown && (sent != null || succeeded)
    val failure = if (c.failed && c.dagShown) (failureMessage ?: stringResource(labels.failedFallback)) else null

    BackHandler {
        when {
            !c.sending -> (onBack ?: onCancel)()
            done -> onDone()
            // it may have gone through, so there's nothing to try again
            failure == SendOutcomeUnknownException.MESSAGE -> onDone()
            failure != null -> c.returnToForm()
            // going out: nothing to go back to
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .onGloballyPositioned { c.root = it }
            // The bar's drag, read here on the screen that stays put: the bar moves with the
            // finger, so its own coordinates would chase it (iOS: the global coordinate space).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val root = c.root
                    val bar = c.bar
                    if (root == null || bar == null || !root.isAttached || !bar.isAttached) return@awaitEachGesture
                    if (!root.localBoundingBoxOf(bar).contains(down.position)) return@awaitEachGesture
                    if (!c.active || c.sending) return@awaitEachGesture
                    down.consume()
                    c.dragStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        c.drag(down.position.y - change.position.y)
                    }
                    c.dragEnd()
                }
            },
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        // About a third of the way up sends (iOS 8d5f925).
        val thresholdPx = max(with(density) { 120.dp.toPx() }, heightPx * 0.3f)
        val active = isEnabled && !isBusy && !c.authPending
        SideEffect {
            c.authenticate = { ok, fail ->
                swipeContext.authenticateWithDeviceCredential(title = authReason ?: "Confirm sending Kaspa", onSuccess = ok, onFailure = fail)
            }
            c.active = active
            c.isBusy = isBusy
            c.heightPx = heightPx
            c.thresholdPx = thresholdPx
            c.haptic = haptic
            c.action = onSwipe
            c.beforeSend = { focusManager.clearFocus() }
        }

        // The form: header, the screen's scrolling content, the bar - all moving up with the swipe.
        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, -c.lift.roundToInt()) }
                .background(colors.background),
        ) {
            SendStepHeader(
                title = screenTitle,
                onBack = onBack,
                onCancel = if (showsCancel || onBack == null) onCancel else null,
                modifier = Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
            SwipeUpBar(
                title = if (c.armed) stringResource(labels.release) else barTitle,
                accessibilityTitle = barTitle,
                armed = c.armed,
                isEnabled = isEnabled,
                onAccessibilityClick = { if (c.active && !c.sending) c.authenticateThenSend() },
                modifier = Modifier
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom).union(WindowInsets.ime))
                    .onGloballyPositioned { c.bar = it },
            )
        }

        if (c.lift > 0f || c.sending) {
            SendProgressScreen(
                amountSompi = amountSompi,
                recipient = recipient,
                sent = if (c.dagShown) sent else null,
                done = done,
                outcome = outcome,
                labels = labels,
                failure = failure,
                isSending = c.sending,
                armed = c.armed,
                explorerUrl = explorerUrl,
                insets = insets,
                onDone = onDone,
                onTryAgain = { c.returnToForm() },
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, max(0f, heightPx - c.lift).roundToInt()) },
            )
        }
    }
}

/** "Swipe up to Send" pinned under the form: a chevron (a check once past halfway) over the words,
 *  black on a 64-high accent capsule, faded while the send isn't ready (iOS 59593af `bar`). */
@Composable
private fun SwipeUpBar(
    title: String,
    accessibilityTitle: String,
    armed: Boolean,
    isEnabled: Boolean,
    onAccessibilityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.background)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CircleShape)
                .background(KaspaTeal.copy(alpha = if (isEnabled) 1f else 0.4f))
                .clearAndSetSemantics {
                    contentDescription = accessibilityTitle
                    role = Role.Button
                    onClick(label = accessibilityTitle) {
                        onAccessibilityClick()
                        true
                    }
                },
        ) {
            Icon(
                if (armed) Icons.Default.Check else Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(24.dp),
            )
            Text(title, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
        }
    }
}

/**
 * The full screen a swipe-up send moves to (iOS 59593af `SendProgressScreen`): a BlockDAG running
 * across while the transaction goes out, then the confirmation - what was sent, to whom, and the
 * transaction id as a link to the block explorer Settings names (or a screen's own [outcome]) -
 * or "Failed" with the error and Try Again.
 */
@Composable
private fun SendProgressScreen(
    amountSompi: Long?,
    recipient: String?,
    /** Non-null once confirmed. */
    sent: SentTransaction?,
    /** It worked: [sent] (with its transaction) or a screen's own [outcome]. */
    done: Boolean,
    outcome: (@Composable () -> Unit)?,
    labels: SwipeUpLabels,
    /** Non-null once the send failed: the error to show. */
    failure: String?,
    /** False while the swipe is still being dragged (a preview of what's coming). */
    isSending: Boolean,
    armed: Boolean,
    explorerUrl: (String) -> String?,
    insets: WindowInsets,
    onDone: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val uriHandler = LocalUriHandler.current
    // Sent or failed: the DAG steps back and the outcome takes over.
    val finished = done || failure != null
    // It may have gone through, so there's nothing to try again: "Not confirmed" and only Done.
    val outcomeUnknown = failure == SendOutcomeUnknownException.MESSAGE
    val dagAlpha by animateFloatAsState(if (finished) 0.35f else 1f, animationSpec = tween(350), label = "dagAlpha")

    Column(
        modifier
            .background(colors.background)
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
            // From the top rather than centred: while the swipe is still rising, the DAG is the
            // first thing that comes into view.
            .padding(top = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            BlockDagAnimation(
                highlight = isSending && failure == null,
                modifier = Modifier.fillMaxSize().alpha(dagAlpha),
            )
            androidx.compose.animation.AnimatedVisibility(visible = done, enter = scaleIn(initialScale = 0.4f) + fadeIn(), exit = fadeOut()) {
                // iOS "checkmark.circle.fill" in black on the accent
                Box(Modifier.size(72.dp).clip(CircleShape).background(KaspaTeal), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(44.dp))
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = outcomeUnknown && !done, enter = scaleIn(initialScale = 0.4f) + fadeIn(), exit = fadeOut()) {
                // iOS "questionmark.circle.fill" in black on orange
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFFF9500)), contentAlignment = Alignment.Center) {
                    Text("?", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = failure != null && !outcomeUnknown && !done, enter = scaleIn(initialScale = 0.4f) + fadeIn(), exit = fadeOut()) {
                // iOS "xmark.circle.fill" in white on red
                Box(Modifier.size(72.dp).clip(CircleShape).background(colors.danger), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }

        Column(
            Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(
                    when {
                        done -> labels.done
                        outcomeUnknown -> R.string.swipe_not_confirmed
                        failure != null -> R.string.swipe_failed
                        isSending -> labels.working
                        armed -> labels.release
                        else -> R.string.swipe_keep_swiping
                    }
                ),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            (sent?.amountSompi ?: amountSompi)?.let { amount ->
                Text(
                    "${KaspaUnit.plain(amount)} ${KaspaUnit.symbol}",
                    color = KaspaTeal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                )
            }
            (sent?.recipient ?: recipient)?.takeIf { it.isNotEmpty() }?.let { to ->
                MiddleEllipsisText(
                    stringResource(labels.recipientLine, to),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            if (!finished) {
                Text(
                    stringResource(if (isSending) labels.workingDetail else labels.hint),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                )
            }
        }

        AnimatedVisibility(visible = failure != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth()
                    .sendKaspaGlass(14.dp)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Orange for a send that may still land, red for one that failed (iOS).
                val tone = if (outcomeUnknown) colors.warning else colors.danger
                Icon(Icons.Default.Warning, contentDescription = null, tint = tone, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(failure.orEmpty(), color = tone, fontSize = 15.sp)
            }
        }

        if (sent != null) {
            val url = explorerUrl(sent.txId)
            Column(
                Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (url != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .sendKaspaGlass(14.dp)
                            .clickable { uriHandler.openUri(url) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        MiddleEllipsisText(
                            sent.txId,
                            color = KaspaTeal,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(15.dp))
                    }
                    Text(stringResource(R.string.kn_tap_tx_explorer), color = colors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                } else {
                    MiddleEllipsisText(sent.txId, color = colors.textSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (done && outcome != null) {
            Column(
                Modifier
                    .padding(top = 20.dp)
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) { outcome() }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = done || failure != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(KaspaTeal)
                    .clickable(onClick = if (done || outcomeUnknown) onDone else onTryAgain),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (done || outcomeUnknown) R.string.done else R.string.try_again),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

// MARK: - BlockDAG

/** A stable pseudo-random number for (column, salt) - iOS `BlockDAGAnimationView.hash`, bit for
 *  bit (wrapping 64-bit multiplies, unsigned shifts and remainder), so both apps draw the same DAG. */
private fun dagHash(column: Int, salt: Int): Int {
    var x = (column.toLong() * 73_856_093L) xor (salt.toLong() * 19_349_663L)
    x = x xor (x ushr 33)
    x *= 0xff51afd7ed558ccdUL.toLong()
    x = x xor (x ushr 33)
    return java.lang.Long.remainderUnsigned(x, 1_000_003L).toInt()
}

/** How many blocks [column] has: one to three. */
private fun dagCount(column: Int): Int = 1 + dagHash(column, 1) % 3

/** Block [row] of [column]'s centre height, as a fraction of the view. */
private fun dagY(column: Int, row: Int): Float {
    val n = dagCount(column)
    val jitter = (dagHash(column, 10 + row) % 100) / 100f * 0.12f - 0.06f
    return (row + 1f) / (n + 1f) + jitter
}

/** The parents of block [row] in [column]: rows of `column - 1`. */
private fun dagParents(column: Int, row: Int): List<Int> {
    val previous = dagCount(column - 1)
    val first = dagHash(column, 20 + row) % previous
    return if (previous > 1 && dagHash(column, 30 + row) % 2 == 0) listOf(first, (first + 1) % previous) else listOf(first)
}

/**
 * Kaspa's BlockDAG drifting right to left (iOS 59593af `BlockDAGAnimationView`): columns of
 * blocks, each pointing back at one to three blocks of the column before (several parents, not a
 * chain). With [highlight], one block - the transaction going out - lights up as it enters on the
 * right, and the blocks after it point back at it in the same colour. Laid out from each column's
 * index, so the picture is stable as it moves.
 */
@Composable
fun BlockDagAnimation(highlight: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    // seconds since it appeared, read only while drawing, so a frame redraws without recomposing
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { elapsed = (it - start) / 1_000_000_000f }
    }
    // When `highlight` turned on: the block entering on the right then is the transaction's.
    var highlightAt by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(highlight) { highlightAt = if (highlight) elapsed else null }

    val accent = KaspaTeal
    val primary = colors.textPrimary
    Canvas(modifier.clearAndSetSemantics { }) {
        val t = elapsed
        val spacing = 54.dp.toPx()
        val speed = 60.dp.toPx()
        val block = 18.dp.toPx()
        val corner = CornerRadius(5.dp.toPx())
        val scroll = t * speed
        val firstVisible = floor(scroll / spacing).toInt() - 1
        val lastVisible = firstVisible + (size.width / spacing).toInt() + 3
        // The column just off the right edge when the send started is the transaction's.
        val txColumn = (((highlightAt ?: 0f) * speed + size.width) / spacing).toInt() + 1
        fun point(column: Int, row: Int) = Offset(column * spacing - scroll, dagY(column, row) * size.height)
        val dim = primary.copy(alpha = 0.28f)

        // Edges first, under the blocks.
        for (column in firstVisible..lastVisible) {
            for (row in 0 until dagCount(column)) {
                for (parent in dagParents(column, row)) {
                    val lit = highlight && column - 1 == txColumn && parent == 0
                    drawLine(
                        color = if (lit) accent.copy(alpha = 0.8f) else dim,
                        start = point(column, row),
                        end = point(column - 1, parent),
                        strokeWidth = (if (lit) 2.dp else 1.dp).toPx(),
                    )
                }
            }
        }
        for (column in firstVisible..lastVisible) {
            for (row in 0 until dagCount(column)) {
                val center = point(column, row)
                val topLeft = Offset(center.x - block / 2, center.y - block / 2)
                if (highlight && column == txColumn && row == 0) {
                    val glow = 7.dp.toPx()
                    val pulse = 0.25f + 0.2f * sin(t * 4f)
                    drawRoundRect(
                        color = accent.copy(alpha = pulse),
                        topLeft = Offset(topLeft.x - glow, topLeft.y - glow),
                        size = Size(block + glow * 2, block + glow * 2),
                        cornerRadius = CornerRadius(9.dp.toPx()),
                    )
                    drawRoundRect(color = accent, topLeft = topLeft, size = Size(block, block), cornerRadius = corner)
                } else {
                    drawRoundRect(color = primary.copy(alpha = 0.16f), topLeft = topLeft, size = Size(block, block), cornerRadius = corner)
                    drawRoundRect(
                        color = primary.copy(alpha = 0.45f), topLeft = topLeft, size = Size(block, block),
                        cornerRadius = corner, style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
    }
}
