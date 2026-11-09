package com.kachat.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.services.NfcCard
import com.kachat.app.services.NfcCardBackupFormat
import com.kachat.app.services.NfcCardBackupPayload
import com.kachat.app.services.NfcCardException
import com.kachat.app.services.NfcCardStatus
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.rememberHaptics
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Backing up to and restoring from a KaChat-NFC card, beta only (iOS 0877f88 NFCCardBackupView,
// NFCCardRestoreView). The text is English only for now, as on iOS.

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** iOS shows its own sheet while it reads a card; Android has none, so this is that sheet. */
@Composable
private fun NfcCardScanSheet(message: String, onCancel: () -> Unit) {
    ActionSheetContainer(
        title = "Ready to Scan",
        subtitle = null,
        onDismiss = onCancel,
        headerIcon = {
            Icon(Icons.Filled.Nfc, contentDescription = null, tint = KaspaTeal, modifier = Modifier.padding(bottom = 8.dp).size(36.dp))
        },
    ) {
        Text(
            message,
            color = LocalAppColors.current.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ActionSheetRow(icon = Icons.Filled.Close, title = stringResource(R.string.cancel), subtitle = "Stops looking for a card.", onClick = onCancel)
    }
}

/**
 * Import Account > Restore from NFC Card: the PIN and a tap read the backup, then the passphrase,
 * which was never on the card, opens the account. A wrong passphrase needs no second tap, since the
 * backup is in memory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcCardRestoreScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptics()
    val importState by viewModel.importWalletState.collectAsState()
    // Plain remember throughout: composition memory only, never saved state (audit AND-031).
    var pin by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<NfcCardBackupPayload?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val importing = importState.status == WalletViewModel.ImportWalletStatus.IMPORTING

    SecureWindowWhileShown()
    BackHandler(enabled = busy || importing) {}
    LaunchedEffect(importState.status) {
        if (importState.status == WalletViewModel.ImportWalletStatus.FAILED) {
            errorMessage = importState.errorMessage
            viewModel.resetImportWalletState()
        }
    }

    fun readCard() {
        val activity = context.findActivity() ?: return
        val enteredPin = pin
        if (!NfcCard.isValidPin(enteredPin)) return
        busy = true
        scanMessage = NfcCard.TAP_PROMPT
        scanJob = scope.launch {
            try {
                val backup = NfcCard.run(activity) { connection ->
                    scanMessage = "Reading. Keep the card still."
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardException.PinBlocked()
                    if (!current.pinSet) throw NfcCardException.NoPin()
                    if (!current.hasBackup) throw NfcCardException.NoBackup()
                    connection.verifyPin(enteredPin)
                    connection.readBackup()
                }
                scanMessage = null
                haptic(IosHaptic.SUCCESS)
                payload = withContext(Dispatchers.Default) { NfcCardBackupFormat.open(backup, enteredPin) }
                pin = ""
            } catch (e: CancellationException) {
                pin = ""
                throw e
            } catch (e: Exception) {
                pin = ""
                errorMessage = e.message
            } finally {
                busy = false
                scanMessage = null
            }
        }
    }

    fun restore() {
        val backup = payload ?: return
        val entered = passphrase
        busy = true
        scope.launch {
            try {
                val address = withContext(Dispatchers.Default) { viewModel.previewCardAddress(backup, entered) }
                    ?: throw NfcCardException.UnreadableBackup()
                // A wrong passphrase still derives a valid account, just not this one. Compared as an
                // account, not a string: a card saved on mainnet holds a `kaspa:` address, and this
                // phone may be on testnet (or the other way round).
                if (backup.chattingAddress !in com.kachat.app.util.KaspaNetwork.accountAddressVariants(address)) {
                    throw NfcCardException.WrongPassphrase()
                }
                viewModel.restoreFromNfcCard(backup, entered)
            } catch (e: NfcCardException) {
                errorMessage = e.message
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_account), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    SheetBarBackButton(onClick = { if (!busy && !importing) onBack() }, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val backup = payload
            if (backup == null) {
                NfcCardHeading("Restore from an NFC card", "Enter the card's PIN, then tap the card to read your backup.")
                NfcCardSecretField("Card PIN", pin, isNumeric = true) { pin = it }
                NfcCardNote(Icons.Filled.Warning, colors.warning, "After 5 wrong tries the PIN is blocked for good, and this backup can no longer be read.")
                CreateWalletNextButton("Scan Card", enabled = NfcCard.isValidPin(pin) && !busy, busy = busy) { readCard() }
            } else {
                NfcCardHeading(
                    "Enter your passphrase",
                    "The backup was read from your card. Your passphrase was never on the card, so enter it exactly as you set it, including capitals and spaces.",
                )
                NfcCardSecretField("Passphrase", passphrase, isNumeric = false) { passphrase = it }
                if (passphrase.isNotEmpty()) {
                    PassphraseAddressPreview(
                        passphrase = passphrase,
                        showsFundsHint = true,
                        previewAddress = { viewModel.previewCardAddress(backup, it) },
                        previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                    )
                }
                CreateWalletNextButton("Restore Account", enabled = passphrase.isNotEmpty() && !busy && !importing, busy = busy || importing) { restore() }
            }
        }
    }

    scanMessage?.let { message -> NfcCardScanSheet(message, onCancel = { scanJob?.cancel() }) }
    errorMessage?.let { message -> NfcCardAlert(message, onDismiss = { errorMessage = null }) }
}

private enum class NfcBackupStep { INTRO, DETAILS, ERASE, SAVED, ERASED }

/**
 * Settings > Back Up to NFC Card, behind the seed phrase's biometric gate and only offered for an
 * account with a passphrase. Two taps: the first reads the card to learn whether it needs a new PIN
 * or already holds a backup, the second saves. The passphrase is never written to the card. It never
 * clears the "not backed up" card on the Wallet tab: the written seed phrase stays the main copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcCardBackupScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptics()
    // Plain remember throughout: composition memory only, never saved state (audit AND-031).
    var step by remember { mutableStateOf(NfcBackupStep.INTRO) }
    var card by remember { mutableStateOf<NfcCardStatus?>(null) }
    var pin by remember { mutableStateOf("") }
    // A new PIN, typed again: a typo here would lock the backup behind a PIN nobody knows (iOS 4922804).
    var confirmPin by remember { mutableStateOf("") }
    var pinCleared by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEraseConfirmation by remember { mutableStateOf(false) }
    var showReplaceConfirmation by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val pinValid = NfcCard.isValidPin(pin)
    // The PIN can go to the card: a valid one, typed twice when it's a new one.
    val canSave = card?.let { pinValid && (it.pinSet || pin == confirmPin) } ?: false

    SecureWindowWhileShown()
    BackHandler { if (!busy) onClose() }

    fun scanCard() {
        val activity = context.findActivity() ?: return
        busy = true
        scanMessage = NfcCard.TAP_PROMPT
        scanJob = scope.launch {
            try {
                val status = NfcCard.run(activity) { connection -> connection.select() }
                if (status.pinBlocked) throw NfcCardException.PinBlocked()
                haptic(IosHaptic.SUCCESS)
                card = status
                step = NfcBackupStep.DETAILS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                busy = false
                scanMessage = null
            }
        }
    }

    fun backToStart() {
        pin = ""
        confirmPin = ""
        card = null
        step = NfcBackupStep.INTRO
    }

    fun save() {
        val activity = context.findActivity() ?: return
        val scanned = card ?: return
        val enteredPin = pin
        if (!canSave || busy) return
        busy = true
        scanJob = scope.launch {
            try {
                val backup = withContext(Dispatchers.Default) {
                    NfcCardBackupFormat.seal(viewModel.nfcCardBackupPayload(), enteredPin)
                }
                if (backup.size > scanned.maxBackup) throw NfcCardException.BackupTooLarge()
                scanMessage = NfcCard.TAP_PROMPT
                NfcCard.run(activity) { connection ->
                    scanMessage = "Saving. Keep the card still."
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardException.PinBlocked()
                    // The confirmations were for the card scanned first. An interrupted save can
                    // legitimately leave that card with its new PIN and no backup yet.
                    // A PIN where the scanned card had none is another card too (this save sets the
                    // PIN it then finds, and records that below): stopped before verifyPin, which
                    // would cost that card a try with a PIN that was never its own (iOS 4922804).
                    if ((current.hasBackup && !scanned.hasBackup) || (scanned.pinSet && !current.pinSet) ||
                        (!scanned.pinSet && current.pinSet)
                    ) {
                        throw NfcCardException.CardChanged()
                    }
                    if (!current.pinSet) {
                        connection.setPin(enteredPin)
                        // The card has this PIN now, whatever happens next: a retry after an
                        // interrupted save finds it set and asks for it as the card's PIN.
                        card = scanned.copy(pinSet = true, pinBlocked = false, triesRemaining = current.triesRemaining)
                        confirmPin = ""
                    }
                    connection.verifyPin(enteredPin)
                    connection.writeBackup(backup)
                    if (!connection.readBackup().contentEquals(backup)) throw NfcCardException.SaveNotConfirmed()
                }
                scanMessage = null
                // What's on the card has to open with this PIN, not just match what was sent.
                withContext(Dispatchers.Default) { NfcCardBackupFormat.open(backup, enteredPin) }
                haptic(IosHaptic.SUCCESS)
                pin = ""
                confirmPin = ""
                step = NfcBackupStep.SAVED
            } catch (e: NfcCardException.WrongPin) {
                pin = ""
                confirmPin = ""
                // pinSet as it stands now: a PIN this save set stays set.
                card = scanned.copy(pinSet = card?.pinSet ?: scanned.pinSet, pinBlocked = false, triesRemaining = e.triesRemaining)
                errorMessage = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is NfcCardException.PinBlocked || e is NfcCardException.CardChanged) backToStart()
                errorMessage = e.message
            } finally {
                busy = false
                scanMessage = null
            }
        }
    }

    fun eraseBackup() {
        val activity = context.findActivity() ?: return
        val enteredPin = pin
        if (!NfcCard.isValidPin(enteredPin)) return
        busy = true
        scanMessage = NfcCard.TAP_PROMPT
        scanJob = scope.launch {
            try {
                pinCleared = NfcCard.run(activity) { connection ->
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardException.PinBlocked()
                    if (!current.pinSet) throw NfcCardException.NoPin()
                    if (!current.hasBackup) throw NfcCardException.NoBackup()
                    connection.verifyPin(enteredPin)
                    connection.wipe()
                    // Cards loaded with older software keep their PIN. The erase has already
                    // worked, so a card pulled away here only costs the wording.
                    runCatching { connection.select() }.getOrNull()?.pinSet == false
                }
                haptic(IosHaptic.SUCCESS)
                pin = ""
                step = NfcBackupStep.ERASED
            } catch (e: CancellationException) {
                pin = ""
                throw e
            } catch (e: Exception) {
                pin = ""
                errorMessage = e.message
            } finally {
                busy = false
                scanMessage = null
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NFC Card Backup", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    if (step != NfcBackupStep.SAVED && step != NfcBackupStep.ERASED) {
                        SheetBarButton(stringResource(R.string.cancel), onClick = onClose, modifier = Modifier.padding(start = 12.dp), enabled = !busy)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (step) {
                NfcBackupStep.INTRO -> {
                    NfcCardHeading("Back up to an NFC card", "Saves this account's 12 or 24 words on your card, behind a 6-digit PIN.")
                    NfcCardNote(Icons.Filled.Key, KaspaTeal, "Your passphrase is never saved to the card. You'll enter it when you restore, and it's what keeps this account safe if someone gets past the PIN.")
                    NfcCardNote(Icons.Filled.Warning, colors.warning, "This isn't cold storage. The card only holds a copy and never creates keys, so keep your 12 or 24 words and passphrase written down too.")
                    NfcCardNote(Icons.Filled.CreditCard, KaspaTeal, "Your card needs the KaChat-NFC software first. Load it from the NFC Card page in Kaspa Quick Start.")
                    CreateWalletNextButton("Scan Card", enabled = !busy, busy = busy) { scanCard() }
                    NfcCardTextButton("Erase a Backup on a Card", enabled = !busy) { step = NfcBackupStep.ERASE }
                }
                NfcBackupStep.DETAILS -> card?.let { scanned ->
                    if (scanned.hasBackup) {
                        NfcCardNote(Icons.Filled.Warning, colors.warning, "This card already holds a backup. Saving replaces it once the new one is confirmed.")
                    }
                    if (scanned.pinSet) {
                        NfcCardSecretField("Card PIN", pin, isNumeric = true) { pin = it }
                        if (scanned.triesRemaining < 5) {
                            Text("${scanned.triesRemaining} PIN tries left on this card.", color = colors.warning, fontSize = 13.sp)
                        }
                    } else {
                        NfcCardSecretField("New 6-digit PIN", pin, isNumeric = true) { pin = it }
                        NfcCardSecretField("Re-enter PIN", confirmPin, isNumeric = true) { confirmPin = it }
                        if (NfcCard.isValidPin(confirmPin) && pin != confirmPin) {
                            Text("The PINs don't match.", color = colors.danger, fontSize = 13.sp)
                        }
                        Text(
                            "Pick one you'll remember. After 5 wrong tries the PIN is blocked, and the only fix is reloading the card in Kaspa Quick Start, which erases it.",
                            color = colors.textSecondary, fontSize = 13.sp,
                        )
                    }
                    CreateWalletNextButton(if (scanned.hasBackup) "Replace Backup" else "Save to Card", enabled = canSave && !busy, busy = busy) {
                        if (scanned.hasBackup) showReplaceConfirmation = true else save()
                    }
                }
                NfcBackupStep.ERASE -> {
                    NfcCardHeading("Erase a backup", "Enter the card's PIN, then tap the card to erase the backup on it.")
                    NfcCardSecretField("Card PIN", pin, isNumeric = true) { pin = it }
                    CreateWalletNextButton("Erase Backup", enabled = pinValid && !busy, busy = busy) { showEraseConfirmation = true }
                    NfcCardTextButton(stringResource(R.string.back), enabled = !busy) {
                        pin = ""
                        step = NfcBackupStep.INTRO
                    }
                }
                NfcBackupStep.SAVED -> NfcCardFinished(
                    "Backup saved",
                    "It was read back from the card and checked. To restore it, choose Import Existing Account, then Restore from NFC Card, and enter your PIN and passphrase. Your passphrase isn't on the card, so keep it and your written 12 or 24 words safe.",
                    onClose,
                )
                NfcBackupStep.ERASED -> NfcCardFinished(
                    "Backup erased",
                    if (pinCleared) "The backup and PIN are gone from this card. The next backup on it will ask you to set a new PIN."
                    else "The backup is gone from this card. Its PIN stays the same.",
                    onClose,
                )
            }
        }
    }

    if (showEraseConfirmation) {
        ConfirmActionSheet(
            title = "Erase the backup on this card?",
            confirmTitle = "Erase Backup",
            confirmSubtitle = "This can't be undone.",
            confirmIcon = Icons.Filled.Delete,
            onConfirm = { eraseBackup() },
            onDismiss = { showEraseConfirmation = false },
        )
    }
    if (showReplaceConfirmation) {
        ConfirmActionSheet(
            title = "Replace the backup on this card?",
            confirmTitle = "Replace Backup",
            confirmSubtitle = "The old backup is removed once the new one is confirmed.",
            confirmIcon = Icons.Filled.Nfc,
            onConfirm = { save() },
            onDismiss = { showReplaceConfirmation = false },
        )
    }
    scanMessage?.let { message -> NfcCardScanSheet(message, onCancel = { scanJob?.cancel() }) }
    errorMessage?.let { message -> NfcCardAlert(message, onDismiss = { errorMessage = null }) }
}

@Composable
private fun NfcCardHeading(title: String, detail: String) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = colors.textSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun NfcCardFinished(title: String, detail: String, onDone: () -> Unit) {
    val colors = LocalAppColors.current
    Icon(
        Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success,
        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).size(44.dp),
    )
    Text(title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(detail, color = colors.textSecondary, fontSize = 15.sp)
    CreateWalletNextButton(stringResource(R.string.done), enabled = true, onClick = onDone)
}

@Composable
private fun NfcCardTextButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = KaspaTeal.copy(alpha = if (enabled) 1f else 0.4f),
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun NfcCardAlert(message: String, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("NFC Card", color = colors.textPrimary) },
        text = { Text(message, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
            }
        },
    )
}

/** A PIN or passphrase entry, shown as typed so a mistake is caught before it costs a PIN try. The
 *  Password keyboard either way, so no keyboard learns or offers to save it. A PIN field only takes
 *  digits, up to the PIN's length. */
@Composable
private fun NfcCardSecretField(title: String, value: String, isNumeric: Boolean, onValueChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    TextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(if (isNumeric) typed.filter { it in '0'..'9' }.take(NfcCard.PIN_LENGTH) else typed)
        },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        placeholder = { Text(title, color = colors.textSecondary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password,
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
    )
}

@Composable
private fun NfcCardNote(icon: ImageVector, tint: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.1f)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, color = LocalAppColors.current.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}
