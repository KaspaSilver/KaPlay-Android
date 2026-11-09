package com.kachat.app.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.rememberHaptics
import com.kachat.app.viewmodels.WalletViewModel

// Backing up an account, in plain steps, and checking on it every six months (iOS ddec987
// AccountBackupView.swift). The text is English only for now, as on iOS.

/** Opens the backup steps behind the seed phrase's biometric gate, when enabled. */
internal fun unlockAccountBackup(context: Context, biometricEnabled: Boolean, show: () -> Unit) {
    if (biometricEnabled) {
        context.authenticateWithDeviceCredential(title = "Unlock to view your 12 or 24 words", onSuccess = show)
    } else {
        show()
    }
}

/**
 * The strip above the title on every tab while an account isn't backed up. It stays until the backup
 * steps are finished, and turns red once the chatting address holds funds (iOS 8d5f925
 * `AccountBackupBanner`).
 */
@Composable
internal fun AccountBackupBanner(viewModel: WalletViewModel, onOpen: () -> Unit) {
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val address by viewModel.address.collectAsState()
    val pending by viewModel.accountBackupStatus.pendingAddresses.collectAsState()
    val balance by viewModel.balanceSompi.collectAsState()
    val biometric by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val owed = address?.let { it in pending } == true
    if (!owed) return
    val hasFunds = balance > 0L
    val tint = if (hasFunds) colors.danger else colors.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f))
            .clickable { unlockAccountBackup(context, biometric, onOpen) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.GppMaybe, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Backup Your Account To Prevent Loss Of Funds", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (hasFunds) "You now have funds that aren't backed up."
                else "If you lose this phone, your 12 or 24 words are the only way back.",
                color = if (hasFunds) colors.danger else colors.textSecondary,
                fontSize = 11.sp,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/** The Wallet tab's card above Address Actions while an account isn't backed up (before iOS 8d5f925). It stays until
 *  the backup steps are finished, and turns red once the account holds funds. */
@Composable
internal fun AccountBackupCard(hasFunds: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tint = if (hasFunds) colors.danger else colors.warning
    BackupCardFrame(tint = tint, icon = Icons.Filled.GppMaybe, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Backup Your Account To Prevent Loss Of Funds",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (hasFunds) "You now have funds that aren't backed up."
                else "If you lose this phone, your 12 or 24 words are the only way back.",
                color = if (hasFunds) colors.danger else colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

/** The Wallet tab's card above Address Actions every six months after a finished backup. */
@Composable
internal fun BackupHygieneCard(onClick: () -> Unit) {
    BackupCardFrame(tint = KaspaTeal, icon = Icons.Filled.VerifiedUser, onClick = onClick) {
        Text(
            "Backup Hygiene",
            color = LocalAppColors.current.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BackupCardFrame(
    tint: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.weight(1f)) { content() }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LocalAppColors.current.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The backup screens' frame: the inline title, an optional Back, and Close. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    if (onBack != null) {
                        SheetBarButton(stringResource(R.string.back), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    SheetBarButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        content = content,
    )
}

/** A passphrase being typed back: the Password keyboard even while revealed, so no keyboard
 *  learns it, with an eye to show or hide it. */
@Composable
private fun BackupPassphraseField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    var reveal by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        placeholder = { Text("Passphrase", color = colors.textSecondary) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        trailingIcon = {
            IconButton(onClick = { reveal = !reveal }) {
                Icon(
                    if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (reveal) "Hide" else "Show",
                    tint = colors.textSecondary,
                )
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = KaspaTeal,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

private enum class BackupStep { WHY, PREPARE, WORDS, WORD_CHECK, PASSPHRASE, PASSPHRASE_CHECK, TIPS }

private const val WORDS_PER_PAGE = 6
private const val BACKUP_WORDS_TO_CHECK = 4
private const val HYGIENE_WORDS_TO_CHECK = 5

/**
 * Backing up an account's seed phrase and passphrase: why, getting ready, the words a few at a
 * time, proving they were written down, then where to keep them. Words are written by hand: this
 * flow never offers a copy (Settings > View Seed Phrase does). Android can stop a screenshot where
 * iOS can only say so afterwards, so every step showing a word or the passphrase is FLAG_SECURE.
 * Plain [remember] throughout: composition memory only, never saved state (audit AND-031).
 */
@Composable
fun AccountBackupScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = rememberHaptics()
    val words = remember { viewModel.getActiveMnemonic()?.split(" ")?.filter { it.isNotBlank() }.orEmpty() }
    val passphrase = remember { viewModel.getActivePassphrase() }

    if (words.isEmpty()) {
        BackHandler(onBack = onClose)
        BackupScaffold(title = "Back Up Account", onClose = onClose) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "This account's 12 or 24 words aren't available on this phone.",
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    var step by remember { mutableStateOf(BackupStep.WHY) }
    var showExplainer by remember { mutableStateOf(false) }
    var hasPenAndPaper by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }
    var noPhotos by remember { mutableStateOf(false) }
    var wordPage by remember { mutableIntStateOf(0) }
    var checkIndexes by remember { mutableStateOf(emptyList<Int>()) }
    var answers by remember { mutableStateOf(emptyMap<Int, String>()) }
    var wrongIndexes by remember { mutableStateOf(emptySet<Int>()) }
    var focusedWord by remember { mutableStateOf<Int?>(null) }
    var passphraseAnswer by remember { mutableStateOf("") }
    var passphraseWrong by remember { mutableStateOf(false) }

    val pageCount = maxOf(1, (words.size + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE)
    // This account's steps, for the progress bar: the passphrase's only when it has one.
    val steps = BackupStep.entries.filter {
        passphrase != null || (it != BackupStep.PASSPHRASE && it != BackupStep.PASSPHRASE_CHECK)
    }

    fun startWordCheck() {
        checkIndexes = words.indices.shuffled().take(BACKUP_WORDS_TO_CHECK).sorted()
        answers = emptyMap()
        wrongIndexes = emptySet()
        step = BackupStep.WORD_CHECK
    }

    fun goBack() {
        when (step) {
            BackupStep.WHY -> onClose()
            BackupStep.PREPARE -> step = BackupStep.WHY
            BackupStep.WORDS -> if (wordPage > 0) wordPage-- else step = BackupStep.PREPARE
            BackupStep.WORD_CHECK -> {
                wordPage = pageCount - 1
                step = BackupStep.WORDS
            }
            // Back to a check already passed keeps its words and answers: going back is for looking
            // again, not for being tested on new words (iOS 8d5f925).
            BackupStep.PASSPHRASE -> step = BackupStep.WORD_CHECK
            BackupStep.PASSPHRASE_CHECK -> step = BackupStep.PASSPHRASE
            BackupStep.TIPS -> step = if (passphrase != null) BackupStep.PASSPHRASE_CHECK else BackupStep.WORD_CHECK
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(step) { focusManager.clearFocus() }

    if (showExplainer) {
        BackHandler { showExplainer = false }
        SeedPhraseExplainerPage(onBack = { showExplainer = false })
        return
    }
    BackHandler(onBack = ::goBack)

    if (step == BackupStep.WORDS || step == BackupStep.WORD_CHECK || step == BackupStep.PASSPHRASE || step == BackupStep.PASSPHRASE_CHECK) {
        SecureWindowWhileShown()
    }

    BackupScaffold(
        title = "Back Up Account",
        onBack = if (step == BackupStep.WHY) null else ::goBack,
        onClose = onClose,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // One step's page: progress on top, the content scrolling, its button always in reach.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (steps.indexOf(step) + 1).toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = KaspaTeal,
                    trackColor = colors.surface,
                )
                when (step) {
                    BackupStep.WHY -> {
                        BackupHeading("Protect your funds")
                        BackupPoint(Icons.Filled.Key, "Your 12 or 24 words are your account",
                            "These secret words are created just for you. They are the master key to your funds.")
                        BackupPoint(Icons.Filled.MobileOff, "Your phone can't be your only copy",
                            "If you lose, break or replace this phone, these words are the only way back to your account.")
                        BackupPoint(Icons.Filled.PersonOff, "Nobody can reset them",
                            "There is no password reset. KaPlay never sees your words, so we can't recover them for you.")
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showExplainer = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.what_is_a_seed_phrase), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    BackupStep.PREPARE -> {
                        BackupHeading("Get ready", "Your secret words are next. Before you see them:")
                        BackupCheckbox("I have a pen and paper", hasPenAndPaper) { hasPenAndPaper = it }
                        BackupCheckbox("Nobody can see my screen", isPrivate) { isPrivate = it }
                        BackupCheckbox("I won't take a photo or screenshot", noPhotos) { noPhotos = it }
                    }
                    BackupStep.WORDS -> {
                        val first = wordPage * WORDS_PER_PAGE
                        val end = minOf(first + WORDS_PER_PAGE, words.size)
                        BackupHeading("Write down these words", "Write them on paper in this exact order, with their numbers.")
                        Text("Words ${first + 1} to $end of ${words.size}", color = KaspaTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (index in first until end) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Text("${index + 1}.", color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                                    Text(words[index], color = colors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    BackupStep.WORD_CHECK -> {
                        BackupHeading("Check your paper", "Tap each box and type that word from what you wrote down, to be sure your copy is right.")
                        for (index in checkIndexes) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Word ${index + 1}", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                val shape = RoundedCornerShape(10.dp)
                                TextField(
                                    value = answers[index].orEmpty(),
                                    onValueChange = {
                                        answers = answers + (index to it)
                                        wrongIndexes = wrongIndexes - index
                                    },
                                    placeholder = { Text("Type word ${index + 1} here", color = colors.textSecondary) },
                                    singleLine = true,
                                    // The Password keyboard, so no keyboard learns the words.
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrect = false,
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        cursorColor = KaspaTeal,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .onFocusChanged { focusedWord = if (it.isFocused) index else focusedWord.takeIf { word -> word != index } }
                                        .border(
                                            1.5.dp,
                                            when {
                                                index in wrongIndexes -> colors.danger
                                                focusedWord == index -> KaspaTeal
                                                else -> colors.textSecondary.copy(alpha = 0.5f)
                                            },
                                            shape,
                                        ),
                                )
                            }
                        }
                        if (wrongIndexes.isNotEmpty()) {
                            Text("That doesn't match. Check your paper, or go back to see the words again.", color = colors.danger, fontSize = 13.sp)
                            Text(
                                "See My Words Again",
                                color = KaspaTeal,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    wordPage = 0
                                    step = BackupStep.WORDS
                                },
                            )
                        }
                    }
                    BackupStep.PASSPHRASE -> {
                        BackupHeading(
                            "Write down your passphrase",
                            "This account also needs its passphrase. Your 12 or 24 words alone won't bring it back. Write it exactly, including capital letters and spaces.",
                        )
                        Text(
                            passphrase.orEmpty(),
                            color = colors.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .padding(14.dp),
                        )
                    }
                    BackupStep.PASSPHRASE_CHECK -> {
                        BackupHeading("Check your passphrase", "Enter it from what you wrote down. It has to match exactly.")
                        BackupPassphraseField(passphraseAnswer) {
                            passphraseAnswer = it
                            passphraseWrong = false
                        }
                        if (passphraseWrong) {
                            Text("That doesn't match. Check your paper, or go back to see it again.", color = colors.danger, fontSize = 13.sp)
                        }
                    }
                    BackupStep.TIPS -> {
                        BackupHeading("Keep it safe", "Your copy is right. Now keep it that way.")
                        BackupPoint(Icons.Filled.Description, "Keep it on paper",
                            "Store it somewhere safe and private, away from fire and water.")
                        BackupPoint(Icons.Filled.CloudOff, "Never keep it online",
                            "No photos, screenshots, cloud storage, notes apps, email or chats.")
                        BackupPoint(Icons.Filled.PanTool, "Never share it",
                            "Nobody needs your words. KaPlay and real support will never ask for them. Anyone who asks is trying to take your funds.")
                        if (passphrase != null) {
                            BackupPoint(Icons.Filled.Splitscreen, "Keep your passphrase apart",
                                "Store it away from your 12 or 24 words. You need both, and losing either one loses the account.")
                        }
                        BackupPoint(Icons.Filled.Settings, "You can see it again",
                            "Your 12 or 24 words stay available in Settings > Back Up Account.")
                    }
                }
            }
            Box(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                when (step) {
                    BackupStep.WHY -> CreateWalletNextButton("Start Backup", enabled = true) { step = BackupStep.PREPARE }
                    BackupStep.PREPARE -> CreateWalletNextButton("Show My 12 or 24 Words", enabled = hasPenAndPaper && isPrivate && noPhotos) {
                        wordPage = 0
                        step = BackupStep.WORDS
                    }
                    BackupStep.WORDS -> CreateWalletNextButton(
                        if (wordPage + 1 < pageCount) "Next Words" else "I Wrote Them Down",
                        enabled = true,
                    ) {
                        if (wordPage + 1 < pageCount) wordPage++ else startWordCheck()
                    }
                    BackupStep.WORD_CHECK -> CreateWalletNextButton(
                        "Check My Words",
                        enabled = checkIndexes.all { answers[it].orEmpty().isNotBlank() },
                    ) {
                        wrongIndexes = checkIndexes.filter { answers[it].orEmpty().trim().lowercase() != words[it] }.toSet()
                        if (wrongIndexes.isEmpty()) {
                            step = if (passphrase != null) BackupStep.PASSPHRASE else BackupStep.TIPS
                        } else {
                            haptic(IosHaptic.ERROR)
                        }
                    }
                    BackupStep.PASSPHRASE -> CreateWalletNextButton("I Wrote It Down", enabled = true) {
                        passphraseAnswer = ""
                        passphraseWrong = false
                        step = BackupStep.PASSPHRASE_CHECK
                    }
                    BackupStep.PASSPHRASE_CHECK -> CreateWalletNextButton("Check My Passphrase", enabled = passphraseAnswer.isNotEmpty()) {
                        if (passphrasesMatch(passphraseAnswer, passphrase)) {
                            step = BackupStep.TIPS
                        } else {
                            passphraseWrong = true
                            haptic(IosHaptic.ERROR)
                        }
                    }
                    BackupStep.TIPS -> CreateWalletNextButton("Finish Backup", enabled = true) {
                        viewModel.markAccountBackupConfirmed()
                        haptic(IosHaptic.SUCCESS)
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupHeading(title: String, detail: String? = null) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (detail != null) Text(detail, color = colors.textSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun BackupPoint(icon: ImageVector, title: String, detail: String) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.textSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BackupCheckbox(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) KaspaTeal else colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Text(title, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * Checks the written backup still works: five words of the seed phrase, asked in random order on
 * the app's own keyboard, then the passphrase if the account has one, since the words alone
 * wouldn't bring it back. Can't Complete This goes through the backup steps again. Either one
 * starts the six months over; closing leaves the card up.
 */
@Composable
fun BackupHygieneScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = rememberHaptics()
    val biometricEnabled by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val seedWords = remember { viewModel.getActiveMnemonic()?.split(" ")?.filter { it.isNotBlank() }.orEmpty() }
    val passphrase = remember { viewModel.getActivePassphrase() }
    // Which words are asked (positions in the phrase), in the order they're asked.
    val asked = remember { seedWords.indices.shuffled().take(HYGIENE_WORDS_TO_CHECK) }
    val wordList = remember { viewModel.bip39Words }
    var answers by remember { mutableStateOf(List(asked.size) { "" }) }
    var activeSlot by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(false) }
    var checkingPassphrase by remember { mutableStateOf(false) }
    var passphraseAnswer by remember { mutableStateOf("") }
    var passed by remember { mutableStateOf(false) }
    var redoingBackup by remember { mutableStateOf(false) }

    if (redoingBackup) {
        AccountBackupScreen(viewModel, onClose)
        return
    }
    BackHandler(onBack = onClose)
    // Nothing to ask without the words: never a keyboard over an empty phrase.
    if (seedWords.isEmpty()) {
        BackupScaffold(title = "Back Up Account", onClose = onClose) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "This account's 12 or 24 words aren't available on this phone.",
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    if (!passed) SecureWindowWhileShown()

    fun finish() {
        viewModel.markAccountBackupConfirmed()
        haptic(IosHaptic.SUCCESS)
        passed = true
    }

    @Composable
    fun cantComplete() {
        Text(
            "Can't Complete This",
            color = KaspaTeal,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { unlockAccountBackup(context, biometricEnabled) { redoingBackup = true } }
                .padding(vertical = 4.dp),
        )
    }

    BackupScaffold(title = "Backup Hygiene", onClose = onClose) { padding ->
        when {
            passed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(56.dp))
                Text("Your backup is good", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("We'll check again in 6 months.", color = colors.textSecondary, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                CreateWalletNextButton("Done", enabled = true, onClick = onClose)
            }
            checkingPassphrase -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Now enter your passphrase", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your 12 or 24 words alone won't bring this account back. Enter the passphrase from what you wrote down, exactly, including capital letters and spaces.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                BackupPassphraseField(passphraseAnswer) {
                    passphraseAnswer = it
                    wrong = false
                }
                if (wrong) Text("That doesn't match. Check your paper and try again.", color = colors.danger, fontSize = 13.sp)
                CreateWalletNextButton("Check My Passphrase", enabled = passphraseAnswer.isNotEmpty()) {
                    if (passphrasesMatch(passphraseAnswer, passphrase)) {
                        finish()
                    } else {
                        wrong = true
                        haptic(IosHaptic.ERROR)
                    }
                }
                cantComplete()
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Let's ensure your account is backed up in case you lose access to your phone.",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Enter these ${asked.size} of your 12 or 24 words. Each box shows the word's number.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                SeedPhraseKeyboard(
                    wordCount = asked.size,
                    words = answers,
                    activeSlot = activeSlot,
                    wordList = wordList,
                    modifier = Modifier.weight(1f),
                    slotNumbers = asked.map { it + 1 },
                    onWordsChange = {
                        answers = it
                        wrong = false
                    },
                    onActiveSlotChange = { activeSlot = it },
                )
                if (wrong) Text("Some of those words don't match. Check your paper and try again.", color = colors.danger, fontSize = 13.sp)
                CreateWalletNextButton(
                    "Check My Words",
                    enabled = asked.isNotEmpty() && answers.all { it.lowercase() in wordList },
                ) {
                    if (asked.indices.all { seedWords[asked[it]] == answers[it].lowercase() }) {
                        wrong = false
                        if (passphrase != null) checkingPassphrase = true else finish()
                    } else {
                        wrong = true
                        haptic(IosHaptic.ERROR)
                    }
                }
                cantComplete()
            }
        }
    }
}

/** The passphrase as typed against the one kept: saved accounts keep it normalized (NFKD) and a
 *  keyboard types precomposed letters, so "café" is compared in one form - as Swift's `==` does
 *  on iOS. */
private fun passphrasesMatch(typed: String, kept: String?): Boolean =
    kept != null && com.kachat.app.services.WalletManager.nfkd(typed) == com.kachat.app.services.WalletManager.nfkd(kept)
