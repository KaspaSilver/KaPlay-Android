package com.kachat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.Screen
import com.kachat.app.ui.tabIconModifier
import com.kachat.app.ui.tabIconPainter
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors

// ---------------------------------------------------------------------------------------------
// Wallet > Help (iOS 1751d20 `ProfileHelpView`) and the .kachat names guide it opens
// (Views/Wallet/KachatSetupGuideView.swift). Routes "wallet_help" and "wallet_help_guide".
// ---------------------------------------------------------------------------------------------

/**
 * The app's guides, reached from Wallet > Help (iOS 1751d20 `ProfileHelpView`): the .kachat names
 * guide. A large title, pushed inside the Wallet tab: the dock stays.
 */
@Composable
fun WalletHelpScreen(onBack: () -> Unit, onKachatGuide: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = { MainPageHeader(title = stringResource(R.string.help), onBack = onBack, showStatus = false) },
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = LocalDockInset.current),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
            ) {
                WalletHelpRow(
                    icon = Icons.Default.Badge,
                    title = ".kachat Names Guide",
                    subtitle = "Claim a .kachat name, make it your name, and keep it.",
                    onClick = onKachatGuide,
                )
            }
        }
    }
}

/** iOS `helpRow`: a 20pt icon in a 30pt frame, 14 apart; subheadline semibold over caption, and a
 *  chevron. */
@Composable
private fun WalletHelpRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = KaspaTeal, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/**
 * The .kachat names guide (iOS 1751d20 `KachatSetupGuideView`, KaPay's): claim a name, how a name
 * works, make it your name, keep it. Opened from Wallet > Help, over the whole screen (iOS's
 * fullScreenCover). KaPlay has no .kachat tab, so claiming points to KaPay or KaChat. About the
 * name only - an avatar, banner and bio are not part of a name (they are the address's profile,
 * set in KaChat or KaPost).
 */
@Composable
fun KachatSetupGuideScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val colors = LocalAppColors.current
    var step by remember { mutableIntStateOf(0) }
    /** The last step, "You're all set" - it has no dot. */
    val last = 4
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // The inline bar: ".kachat Names", Close leading on its glass.
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            SheetBarButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                ".kachat Names",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // One dot per step before the last, filled up to the current one.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (dot in 0 until last) {
                    val width by animateDpAsState(if (dot == step) 22.dp else 8.dp, tween(200), label = "guideDot")
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(50))
                            .background(if (dot <= step) KaspaTeal else colors.surfaceVariant)
                    )
                }
            }
            when (step) {
                0 -> {
                    GuideStepHeader(
                        title = "Claim your .kachat name",
                        subtitle = "It's the name people see you as across KaChat and on your profile link. Without one, people see your address.",
                    ) {
                        Icon(
                            painter = Screen.KachatNames.tabIconPainter(),
                            contentDescription = ".kachat",
                            tint = KaspaTeal,
                            modifier = Screen.KachatNames.tabIconModifier(56.dp),
                        )
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("yourname", color = colors.textTertiary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text(".kachat", color = colors.textSecondary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Search for the name you want in the .kachat tab of KaPay or KaChat, then claim it.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                1 -> {
                    GuideStepHeader(
                        title = "How a name works",
                        subtitle = "Your name lives on the Kaspa network, held by your chatting address. Nobody can take it or change it while it's yours.",
                    ) { Icon(Icons.Default.Verified, null, tint = KaspaTeal, modifier = Modifier.size(48.dp)) }
                    GuideBulletList(
                        listOf(
                            "Claiming takes two quick transactions: the first keeps your pick secret, the second registers it.",
                            "You pay for one period at a time. Shorter names cost more.",
                            "The price goes to the network's miners, not to KaChat.",
                        )
                    )
                }
                2 -> {
                    GuideStepHeader(
                        title = "Make it your name",
                        subtitle = "Set it as your primary name and it's how you appear everywhere in KaChat.",
                    ) { Icon(Icons.Default.Badge, null, tint = KaspaTeal, modifier = Modifier.size(46.dp)) }
                    GuideBulletList(
                        listOf(
                            "People can find you by typing your name.",
                            "They can send you Kaspa to yourname.kachat instead of a long address.",
                            "Your profile link shows your name.",
                        )
                    )
                }
                3 -> {
                    GuideStepHeader(
                        title = "Keep it",
                        subtitle = "Your names are in Wallet > Your Domains > .kachat.",
                    ) { Icon(Icons.Default.Autorenew, null, tint = KaspaTeal, modifier = Modifier.size(46.dp)) }
                    GuideBulletList(
                        listOf(
                            "Renew before it expires. It shows when, on each name.",
                            "After it expires there's a grace period to renew it before anyone else can claim it.",
                            "You can transfer it, list it for sale, or accept an offer for it.",
                        )
                    )
                }
                else -> GuideStepHeader(
                    title = "You're all set",
                    subtitle = "Claim your name in KaPay or KaChat whenever you're ready.",
                ) { Icon(Icons.Default.Verified, null, tint = KaspaTeal, modifier = Modifier.size(52.dp)) }
            }
        }
        KnsWizardBottomBar(
            nextTitle = if (step == last) "Done" else "Next",
            onBack = if (step > 0) ({ step -= 1 }) else null,
            onNext = { if (step < last) step += 1 else onClose() },
        )
    }
}

/** A step's icon (64 tall), title (title2 bold) and subtitle, centred (iOS `stepHeader`). */
@Composable
private fun GuideStepHeader(title: String, subtitle: String, icon: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) { icon() }
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(subtitle, color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** A step's points, each behind a check, on the field card (iOS `bulletList`). */
@Composable
private fun GuideBulletList(lines: List<String>) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                Text(line, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The wizard's fixed two-button footer: Previous left, Next right, same place on every step so it
 * can be tapped through without chasing buttons (iOS 1751d20 `KNSWizardBottomBar`). A disabled
 * Previous renders dimmed rather than vanishing, so Next never moves between steps.
 */
@Composable
fun KnsWizardBottomBar(
    nextTitle: String = "Next",
    onBack: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .alpha(if (onBack == null) 0.35f else 1f)
                .clip(RoundedCornerShape(14.dp))
                // iOS systemGray5
                .background(if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                .clickable(enabled = onBack != null, role = Role.Button) { onBack?.invoke() }
                .padding(vertical = 14.dp),
        ) {
            Text("Previous", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .alpha(if (onNext == null) 0.5f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(KaspaTeal)
                .clickable(enabled = onNext != null, role = Role.Button) { onNext?.invoke() }
                .padding(vertical = 14.dp),
        ) {
            Text(nextTitle, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
