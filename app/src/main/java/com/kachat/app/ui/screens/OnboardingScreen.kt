package com.kachat.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kachat.app.R
import com.kachat.app.models.SourceWalletOption
import com.kachat.app.models.WalletSourceFamily
import com.kachat.app.services.WalletManager
import com.kachat.app.ui.theme.KaspaSubtext
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.DarkAppColors
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.CreateAccountSteps
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.launch

/**
 * Main entry point for the onboarding flow.
 */
@Composable
fun OnboardingScreen(viewModel: WalletViewModel) {
    val navController = rememberNavController()
    // The accounts are here but their keys can't be opened: the recovery screen comes first
    // (iOS ContentView's .recovery route). Fixed for this NavHost's life - after a way out it
    // moves on to the Welcome screen or the app by itself.
    val startOnRecovery = remember { viewModel.keysUnavailable.value }

    NavHost(navController = navController, startDestination = if (startOnRecovery) "key_recovery" else "welcome") {
        composable("key_recovery") {
            WalletRecoveryScreen(
                address = viewModel.accountWithoutKeysAddress.collectAsState().value,
                onEnterRecoveryPhrase = {
                    // The same flow Import Account uses: the source wallet, then the words. The
                    // unreadable store is only replaced once the phrase is actually saved.
                    viewModel.resetImportWalletState()
                    navController.navigate("import_source")
                },
                onRemove = { viewModel.removeAccountWithoutKeys() },
                onLeave = {
                    navController.navigate("welcome") { popUpTo("key_recovery") { inclusive = true } }
                },
                keysAreBack = { viewModel.keysAreBack() },
            )
        }
        composable("welcome") {
            WelcomeScreen(
                viewModel,
                onCreateWithPassphrase = {
                    viewModel.beginCreateWithPassphrase()
                    navController.navigate("passphrase_create")
                },
                onOpenAppSettings = { navController.navigate("app_settings") },
                onNavigateToImport = {
                    // A stale SUCCESS/FAILED status left over from a previous import would
                    // otherwise fire ImportWalletScreen's success LaunchedEffect immediately on
                    // entry (this state lives in the singleton WalletViewModel, not the screen),
                    // silently logging into whatever account is currently active instead of
                    // letting the user type a new phrase.
                    viewModel.resetImportWalletState()
                    // KasWare-style source-wallet chooser comes FIRST, before seed entry — the
                    // choice sets the identity derivation family the seed is read with.
                    navController.navigate("import_source")
                }
            )
        }
        composable("import_source") {
            ImportSourceWalletScreen(
                onBack = { navController.popBackStack() },
                onContinue = { family ->
                    viewModel.setPendingSourceFamily(family)
                    navController.navigate("import_name")
                },
                onRestoreFromCard = { navController.navigate("nfc_card_restore") },
            )
        }
        // Import Account > Restore from NFC Card, beta only (iOS 0877f88 NFCCardRestoreView).
        composable("nfc_card_restore") {
            NfcCardRestoreScreen(viewModel, onBack = { navController.popBackStack() })
        }
        // Import Account after the source wallet (iOS dd0aab1): the name (the same screen as
        // Create Account's first step), the seed length, the words, then the passphrase.
        composable("import_name") {
            ImportNameScreen(
                onBack = { navController.popBackStack() },
                onNext = { name -> navController.navigate("import_length?name=${android.net.Uri.encode(name)}") }
            )
        }
        composable(
            "import_length?name={name}",
            arguments = listOf(androidx.navigation.navArgument("name") { defaultValue = "" })
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            ImportLengthScreen(
                onBack = { navController.popBackStack() },
                onNext = { count -> navController.navigate("import_wallet?name=${android.net.Uri.encode(name)}&count=$count") }
            )
        }
        composable(
            "import_wallet?name={name}&count={count}",
            arguments = listOf(
                androidx.navigation.navArgument("name") { defaultValue = "Imported Account" },
                androidx.navigation.navArgument("count") { type = androidx.navigation.NavType.IntType; defaultValue = 24 }
            )
        ) { backStackEntry ->
            ImportWalletScreen(
                viewModel,
                accountName = backStackEntry.arguments?.getString("name") ?: "Imported Account",
                initialWordCount = backStackEntry.arguments?.getInt("count") ?: 24,
                onBack = { navController.popBackStack() },
                // Seed validated + stashed by prepareImport(); the actual import happens on the
                // passphrase screen (which arms the Welcome Guide + logs in on success).
                onProceed = { navController.navigate("passphrase_import") }
            )
        }
        composable("passphrase_create") {
            val createError by viewModel.createWalletError.collectAsState()
            // Nothing is shown or saved before the passphrase: leaving forgets the words, and a
            // step restored after the process was recreated starts from new ones.
            LaunchedEffect(Unit) { viewModel.ensureCreateWithPassphrase() }
            // Busy while the account is saved: nothing can leave or commit it a second time
            // (iOS PassphraseOptionView isBusy).
            val isCreating by viewModel.isCreatingAccount.collectAsState()
            val abandon: () -> Unit = {
                if (!viewModel.isCreatingAccount.value) {
                    navController.popBackStack()
                    viewModel.abandonCreatedWallet()
                }
            }
            // Declared before the screen's own handler, which wins while it is enabled (the
            // explainer steps back to the entry rather than leaving).
            BackHandler(onBack = abandon)
            PassphraseSetupScreen(
                mode = PassphraseMode.CREATE,
                isBusy = isCreating,
                errorMessage = createError,
                onDismissError = { viewModel.clearCreateWalletError() },
                onBack = abandon,
                previewAddress = { viewModel.previewChattingAddress(it) },
                previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                // commitCreatedWallet() derives + saves with the passphrase, arms the guide, and
                // logs in (which swaps onboarding for the main shell).
                onProceed = { passphrase -> viewModel.commitCreatedWallet(passphrase) }
            )
        }
        composable("passphrase_import") {
            val importState by viewModel.importWalletState.collectAsState()
            PassphraseSetupScreen(
                mode = PassphraseMode.IMPORT,
                isBusy = importState.status == WalletViewModel.ImportWalletStatus.IMPORTING,
                errorMessage = importState.errorMessage.takeIf {
                    importState.status == WalletViewModel.ImportWalletStatus.FAILED
                },
                onDismissError = { viewModel.resetImportWalletState() },
                onBack = { navController.popBackStack() },
                previewAddress = { viewModel.previewChattingAddress(it) },
                previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                findAddresses = { viewModel.findChattingAddresses(it) },
                onSelectIndex = { viewModel.setPendingChattingAddressIndex(it) },
                // commitImport() imports with the passphrase, arms the guide, and logs in on success.
                onProceed = { passphrase -> viewModel.commitImport(passphrase) }
            )
        }

        // App Settings (the gear on the accounts screen, matching iOS's gearshape sheet on
        // OnboardingView): ONLY the app-wide settings tier - Customization/Security/Connection -
        // every leaf reusing the exact screens the in-account Settings uses.
        composable("app_settings") {
            AppSettingsHubScreen(
                onBack = { navController.popBackStack() },
                onOpenCustomization = { navController.navigate("app_settings_customization") },
                onOpenSecurity = { navController.navigate("app_settings_security") },
                onOpenConnection = { navController.navigate("app_settings_connection") },
            )
        }
        composable("app_settings_customization") {
            AppCustomizationScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLanguage = { navController.navigate("app_settings_language") },
                onNavigateToCurrency = { navController.navigate("app_settings_currency") },
                walletViewModel = viewModel
            )
        }
        composable("app_settings_language") {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("app_settings_currency") {
            CurrencySettingsScreen(onBack = { navController.popBackStack() }, walletViewModel = viewModel)
        }
        composable("app_settings_security") {
            AppSecurityScreen(
                onBack = { navController.popBackStack() },
                walletViewModel = viewModel
            )
        }
        // The same Connection page as in-account Settings (iOS ConnectionHubPage): Connection
        // Settings, the Testnet switch, Kaspa Explorer.
        composable("app_settings_connection") {
            AppConnectionScreen(
                onBack = { navController.popBackStack() },
                onOpenConnectionSettings = { navController.navigate("app_settings_connection_settings") },
                onOpenExplorer = { navController.navigate("app_settings_explorer") },
            )
        }
        composable("app_settings_connection_settings") {
            ConnectionSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("app_settings_explorer") {
            KaspaExplorerSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun WelcomeScreen(
    viewModel: WalletViewModel,
    onCreateWithPassphrase: () -> Unit,
    onNavigateToImport: () -> Unit,
    onOpenAppSettings: () -> Unit = {}
) {
    // The background is dark in both themes, so the colors drawn on top of it have to be the
    // dark set too - otherwise textPrimary renders near-black on near-black in light mode.
    // Scoped to this screen.
    CompositionLocalProvider(LocalAppColors provides DarkAppColors) {
    Surface(
        color = KaChatSignInBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        // Top-right gear (matches iOS OnboardingView's gearshape toolbar button): app-wide
        // settings that make sense with no account active - see AppSettingsHubScreen.
        IconButton(
            onClick = onOpenAppSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.app_settings),
                tint = LocalAppColors.current.textSecondary
            )
        }
        // iOS keeps Create and Import 40 above the safe area: the navigation bar (gesture handle
        // or 3-button bar) is cleared first, then the 40 - a fixed chain, nothing swapped in.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top spacer to help center the middle content
            Spacer(modifier = Modifier.weight(1f))

            val hasWallet by viewModel.hasWallet.collectAsState()
            val accounts by viewModel.accounts.collectAsState()

            // Wordmark with the app mark beside it - the launcher icon itself, so the two never
            // drift apart. Replaces the 160dp logo above the title: on a device with accounts
            // saved, that block was most of why the list below had nowhere to go.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "KaPlay",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = LocalAppColors.current.textPrimary
                )
                Image(
                    // The app icon as one square picture (iOS KaChatChessLogo, 17ae537), drawn by
                    // scripts/make_app_icon.py - not R.mipmap.ic_launcher, the adaptive-icon XML
                    // painterResource cannot load.
                    painter = painterResource(id = R.drawable.kachat_chess_app_mark),
                    contentDescription = "KaPlay",
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
                )
            }

            // Shown whether or not there are accounts saved: it is what the screen is, and
            // hiding it on a returning device left the wordmark sitting on its own with nothing
            // saying what it belongs to.
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Chess on Kaspa: every move is on chain",
                style = MaterialTheme.typography.bodyLarge,
                color = KaspaSubtext,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            val biometricAccountLoginEnabled by viewModel.biometricAccountLoginEnabled.collectAsState()
            val deleteAccountError by viewModel.deleteAccountError.collectAsState()
            val isCreatingAccount by viewModel.isCreatingAccount.collectAsState()
            val createAccountError by viewModel.createAccountError.collectAsState()
            val signInFailure by viewModel.signInFailureMessage.collectAsState()
            val backupPending by viewModel.accountBackupStatus.pendingAddresses.collectAsState()

            if (hasWallet) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.saved_accounts),
                        color = LocalAppColors.current.textPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Four at a time, then this scrolls inside itself - the fifth account is
                    // where scrolling starts. A dozen saved accounts used to just make the page
                    // taller, pushing Create and Import off the bottom of the screen.
                    // heightIn(max) rather than a fixed height so three accounts still take
                    // three rows' worth of space, not four.
                    val visibleRows = minOf(accounts.size, 4)
                    // Whatever is left after the rest of the page, floored at two rows, so a short
                    // phone keeps Create, Import and the Advanced link on screen (iOS ddec987).
                    val spareHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 400).coerceAtLeast(2 * 84)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = minOf(visibleRows * 84, spareHeight).dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        accounts.forEach { account ->
                            SavedAccountCard(
                                account = account,
                                requireBiometricLogin = biometricAccountLoginEnabled,
                                notBackedUp = account.address in backupPending,
                                onLogin = { viewModel.login(account.address) },
                                onRename = { newName -> viewModel.renameAccount(account.address, newName) },
                                onDelete = { viewModel.deleteWallet(account.address) },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        // The account is already gone by the time this can be known, so this row is
                        // the only place it can be said: the archive is still on their Nextcloud and
                        // they have to remove it there.
                        deleteAccountError?.let { failure ->
                            Surface(
                                color = LocalAppColors.current.danger.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.clearDeleteAccountError() },
                            ) {
                                Text(
                                    failure,
                                    color = LocalAppColors.current.danger,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            // Create new wallet button: one tap, straight into the new account (iOS ddec987).
            Button(
                onClick = { viewModel.createAccountInOneTap() },
                enabled = !isCreatingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                // iOS: white text on the accent, the icon plus.circle.fill.
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaTeal,
                    contentColor = Color.White,
                    disabledContainerColor = KaspaTeal,
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCreatingAccount) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.create_new_account),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Import existing wallet button
            Button(
                onClick = onNavigateToImport,
                enabled = !isCreatingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = LocalAppColors.current.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.import_existing_account),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = LocalAppColors.current.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // A passphrase is the only thing Create New Account doesn't do: it moved here.
            Text(
                text = "Advanced: Create with a Passphrase",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = KaspaTeal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isCreatingAccount, onClick = onCreateWithPassphrase)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            // A saved account's sign-in that failed says why (iOS 4922804).
            signInFailure?.let { message ->
                com.kachat.app.ui.theme.IosAlertDialog(
                    onDismissRequest = { viewModel.clearSignInFailure() },
                    containerColor = LocalAppColors.current.surface,
                    title = { Text("Sign In Failed", color = LocalAppColors.current.textPrimary) },
                    text = { Text(message, color = LocalAppColors.current.textSecondary) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearSignInFailure() }) {
                            Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            createAccountError?.let { message ->
                com.kachat.app.ui.theme.IosAlertDialog(
                    onDismissRequest = { viewModel.clearCreateAccountError() },
                    containerColor = LocalAppColors.current.surface,
                    title = { Text("Couldn't Create Account", color = LocalAppColors.current.textPrimary) },
                    text = { Text(message, color = LocalAppColors.current.textSecondary) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearCreateAccountError() }) {
                            Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
        // Which build this phone runs, right where sign-in problems get reported from (iOS
        // e8f4617). Drawn inside the column's 40dp bottom padding, 10 above the navigation bar
        // (iOS: 10pt above the safe area), so the height budget that keeps Create and Import on
        // screen is untouched and a 3-button bar never covers it.
        Text(
            text = "KaPlay ${com.kachat.app.util.AppVersion.display}",
            fontSize = 11.sp,
            color = LocalAppColors.current.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )
        }
    }
}
}

@Composable
fun SavedAccountCard(
    account: WalletManager.Account,
    requireBiometricLogin: Boolean = false,
    /** This account hasn't finished the backup steps: the Delete warning says so. */
    notBackedUp: Boolean = false,
    onLogin: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val deleteContext = androidx.compose.ui.platform.LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(account.name) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KaChatSignInCard)
            .clickable {
                if (requireBiometricLogin) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock ${account.name}",
                        onSuccess = onLogin
                    )
                } else {
                    onLogin()
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF2C3E50), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                color = LocalAppColors.current.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = account.address,
                color = LocalAppColors.current.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_account),
                tint = KaspaTeal,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Half sheet rather than a dropdown, like every other chooser in the app - and Delete gets a
    // line saying what it takes with it, which a menu of bare verbs cannot.
    if (showMenu) {
        ActionSheetContainer(
            title = account.name,
            subtitle = account.address,
            onDismiss = { showMenu = false },
        ) {
            ActionSheetRow(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.rename),
                subtitle = "Gives this account a name of your own.",
            ) {
                showMenu = false
                nameInput = account.name
                showRenameDialog = true
            }
            ActionSheetRow(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.delete),
                subtitle = "Removes this account and its local data from this device.",
                tint = LocalAppColors.current.danger,
            ) {
                showMenu = false
                showDeleteConfirm = true
            }
        }
    }

    if (showRenameDialog) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.rename_account), color = LocalAppColors.current.textPrimary) },
            text = {
                // iOS's alert: its message over the field, the field's placeholder "Account name".
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter a new name for this saved account.", color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
                com.kachat.app.ui.theme.IosTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("Account name", color = LocalAppColors.current.textTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        onRename(nameInput)
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        // The warning Settings' Delete Account shows, without its seed phrase and backup rows: this
        // account is signed out (iOS ddec987). The row names the account and its short address
        // (`shortPublicAddress`: the first 10 and last 6 characters).
        val shortAddress = account.address.let { if (it.length > 16) "${it.take(10)}...${it.takeLast(6)}" else it }
        DeleteAccountSheet(
            confirmSubtitle = "Deletes ${account.name.trim().ifEmpty { "Saved Account" }} ($shortAddress) and its local data from this device.",
            notBackedUp = notBackedUp,
            // Face ID first, as on Settings' Delete Account (iOS 8d5f925).
            onConfirm = { deleteContext.authenticateWithDeviceCredential(title = "Confirm deleting this account", onSuccess = onDelete) },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * Import Account, after the source wallet ([ImportSourceWalletScreen]; iOS dd0aab1
 * `ImportNameStep`): the account's name, local to this device. Then the seed length ([ImportLengthScreen]), the words
 * ([ImportWalletScreen]), and the optional passphrase.
 */
@Composable
fun ImportNameScreen(onBack: () -> Unit, onNext: (String) -> Unit) {
    AccountNameForm(title = stringResource(R.string.import_account), onBack = onBack, onNext = onNext)
}

/**
 * The account name step of Import Account (iOS dd0aab1 `AccountNameForm`): a
 * name local to this device. Next stays off until there is a name; [onNext] gets it trimmed.
 */
@Composable
private fun AccountNameForm(title: String, onBack: () -> Unit, onNext: (String) -> Unit) {
    val colors = LocalAppColors.current
    // Starts empty on purpose: the name is the user's choice. Kept across the trip to the next
    // step and back, as iOS keeps it.
    var accountName by rememberSaveable { mutableStateOf("") }
    val canContinue = CreateAccountSteps.canContinue(accountName)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val next = {
        if (canContinue) {
            focusManager.clearFocus()
            onNext(CreateAccountSteps.accountName(accountName))
        }
    }

    CreateAccountStepScaffold(title = title, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.create_wallet_name_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.create_wallet_name_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }

        TextField(
            value = accountName,
            onValueChange = { accountName = it },
            placeholder = { Text("Enter account name", color = colors.textSecondary) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .focusRequester(focus),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = KaspaTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { next() }),
            singleLine = true
        )

        CreateWalletNextButton(title = stringResource(R.string.next), enabled = canContinue, onClick = next)
    }
}

/**
 * Import Account: how many words the seed phrase being imported has (iOS dd0aab1
 * `ImportLengthStep`) - 12 / 24 buttons, nothing chosen until the user taps one. Next goes to the words ([ImportWalletScreen]).
 */
@Composable
fun ImportLengthScreen(onBack: () -> Unit, onNext: (Int) -> Unit) {
    val colors = LocalAppColors.current
    var wordCount by rememberSaveable { mutableStateOf<Int?>(null) }

    CreateAccountStepScaffold(title = stringResource(R.string.import_account), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.choose_seed_phrase_length),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.import_seed_length_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            CreateAccountSteps.WORD_COUNTS.forEach { count ->
                SeedLengthButton(
                    title = stringResource(if (count == 12) R.string.n_12_words else R.string.n_24_words),
                    chosen = wordCount == count,
                    enabled = true,
                    onClick = { wordCount = count },
                )
            }
        }

        CreateWalletNextButton(
            title = stringResource(R.string.next),
            enabled = CreateAccountSteps.canChooseImportLength(wordCount),
        ) {
            val count = wordCount
            if (count != null && CreateAccountSteps.canChooseImportLength(count)) onNext(count)
        }
    }
}

/**
 * What a seed phrase is, in plain words (iOS 712bb4d `SeedPhraseExplainerPage`): the page behind
 * "What is this?" on the seed length step. A full page on the plain background, pushed like
 * "What is a passphrase?" and in the same headline-and-text sections, so it reads easily.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeedPhraseExplainerPage(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.what_is_a_seed_phrase), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton(onBack, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PassphraseExplainerSection(
                stringResource(R.string.seed_phrase_explainer_short_version),
                stringResource(R.string.seed_phrase_explainer_master_key)
            )
            PassphraseExplainerSection(
                stringResource(R.string.seed_phrase_explainer_anyone_title),
                stringResource(R.string.seed_phrase_explainer_anyone)
            )
            PassphraseExplainerSection(
                stringResource(R.string.seed_phrase_explainer_safe_title),
                stringResource(R.string.seed_phrase_explainer_paper)
            )
        }
    }
}

/** The Import Account steps' frame: Back, the large title ("Create Account"
 *  or "Import Account"), then the step's content 24dp apart, scrolling above the keyboard. */
@Composable
private fun CreateAccountStepScaffold(
    title: String = stringResource(R.string.create_account),
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                // Edge-to-edge: Compose reacts to the keyboard inset itself, so the field and the
                // button stay above it.
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // iOS 26: the back chevron on a glass disc.
            SheetBarIconButton(Icons.Filled.ArrowBackIosNew, stringResource(R.string.back), onClick = onBack, tint = LocalAppColors.current.textPrimary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** The accent-filled continue button of the Import Account and backup steps, dimmed while disabled
 *  (iOS `CreateWalletNextButton`). */
@Composable
internal fun CreateWalletNextButton(title: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KaspaTeal,
            // Not dimmed while busy, as on iOS.
            disabledContainerColor = if (busy) KaspaTeal else KaspaTeal.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (enabled || busy) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

/** One of the two seed length choices (12 or 24 words): full width, filled with the accent when
 *  chosen, outlined otherwise (iOS dd0aab1 `SeedLengthButton`). */
@Composable
private fun SeedLengthButton(title: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    // The whole card is the button, its padding and outline included (iOS ceca9fa), and it dims
    // while held instead of rippling (iOS `.buttonStyle(.plain)`).
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) IOS_PLAIN_PRESSED_ALPHA else 1f }
            .clip(shape)
            .background(if (chosen) KaspaTeal else Color.Transparent)
            .border(1.5.dp, KaspaTeal, shape)
            .selectable(
                selected = chosen,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(16.dp)
    ) {
        Icon(
            imageVector = if (chosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (chosen) Color.White else KaspaTeal,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = if (chosen) Color.White else KaspaTeal,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Import Account: the seed phrase's words (in-app keyboard, or Paste), then the passphrase step
 * (iOS `ImportWalletView`). The name ([ImportNameScreen]) and the length ([ImportLengthScreen])
 * come before it (iOS dd0aab1); a pasted phrase of the other length switches the length.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    viewModel: WalletViewModel,
    accountName: String,
    initialWordCount: Int,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    var wordCount by remember { mutableIntStateOf(CreateAccountSteps.importWordCount(initialWordCount)) }
    // Fixed-capacity backing store; only the first [wordCount] entries are used.
    var words by remember { mutableStateOf(List(24) { "" }) }
    var activeSlot by remember { mutableIntStateOf(0) }
    val importState by viewModel.importWalletState.collectAsState()
    val wordList = remember { viewModel.bip39Words }

    val slots = words.take(wordCount)
    val filled = slots.count { it.isNotEmpty() && wordList.contains(it.lowercase()) }
    val allValid = slots.size == wordCount && slots.all { it.isNotEmpty() && wordList.contains(it.lowercase()) }
    val canImport = allValid && accountName.isNotBlank()

    // The typed-in phrase is fully visible in the slot grid — exactly as sensitive as the reveal
    // screens, so it gets the same screenshot/recording block. The custom keyboard already keeps
    // it away from the OS keyboard and clipboard; this closes the screen-capture side. Cleared
    // on dispose like SeedPhraseScreen's identical guard.
    SecureWindowWhileShown()

    // iOS ImportWalletView: "Import Account" as an inline title with Back, then the label, Paste
    // and the count on one line, the keyboard, and Continue - 12 apart, 16 from the edges.
    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_account), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton(onBack, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(16.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.import_words_enter_recovery_phrase),
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                // A phrase on the clipboard fills the slots in one tap, and the word count
                // follows whichever length was pasted (iOS f9beba1).
                val pasteContext = LocalContext.current
                val pasteHaptic = com.kachat.app.util.rememberHaptics()
                var pasteError by remember { mutableStateOf<String?>(null) }
                pasteError?.let { message ->
                    com.kachat.app.ui.theme.IosAlertDialog(
                        onDismissRequest = { pasteError = null },
                        containerColor = LocalAppColors.current.surface,
                        title = { Text(stringResource(R.string.error), color = LocalAppColors.current.textPrimary) },
                        text = { Text(message, color = LocalAppColors.current.textSecondary) },
                        confirmButton = {
                            TextButton(onClick = { pasteError = null }) {
                                Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                val pasteClipboard = pasteContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                // A bordered button, small, as on iOS - not a bare text link.
                OutlinedButton(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTeal.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(50),
                    onClick = {
                        // iOS's pasteSeedPhrase: a refusal goes up in the "Error" alert, a
                        // success is a haptic and the filled slots.
                        val raw = pasteClipboard?.primaryClip?.getItemAt(0)?.coerceToText(pasteContext)?.toString()
                        if (raw == null) {
                            pasteError = "Nothing to paste."
                            return@OutlinedButton
                        }
                        // Cleared as soon as it is read, whether or not the words are accepted:
                        // secret words do not belong there either way (iOS 8d5f925).
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pasteClipboard?.clearPrimaryClip()
                        } else {
                            pasteClipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                        }
                        val pasted = raw.lowercase()
                            .split(Regex("[\\s,]+"))
                            .map { it.trim('.', ')') }
                            .filter { it.isNotEmpty() && !it.all { c -> c.isDigit() } }
                        when {
                            pasted.size != 12 && pasted.size != 24 ->
                                pasteError = "You need 12 or 24 words - the clipboard holds ${pasted.size}."
                            else -> {
                                val unknown = pasted.filterNot { wordList.contains(it) }
                                if (unknown.isNotEmpty()) {
                                    pasteError = if (unknown.size == 1) "One of the pasted words isn't a valid word. Check your copy and try again."
                                    else "${unknown.size} of the pasted words aren't valid words. Check your copy and try again."
                                } else {
                                    wordCount = pasted.size
                                    words = List(24) { index -> pasted.getOrElse(index) { "" } }
                                    activeSlot = (pasted.size - 1).coerceAtLeast(0)
                                    pasteHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste", color = KaspaTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$filled/$wordCount",
                    color = if (allValid) LocalAppColors.current.success else LocalAppColors.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom in-app keyboard + numbered slot grid + autocomplete (no OS keyboard, no paste)
            SeedPhraseKeyboard(
                wordCount = wordCount,
                words = words,
                activeSlot = activeSlot,
                wordList = wordList,
                modifier = Modifier.weight(1f),
                onWordsChange = { words = it },
                onActiveSlotChange = { activeSlot = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Continue to the passphrase step: the arrow and "Continue", gray until every word is
            // in (iOS ImportWalletView).
            Button(
                onClick = { if (viewModel.prepareImport(accountName, slots)) onProceed() },
                enabled = canImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, disabledContainerColor = IosGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                // white on the accent and on gray alike (iOS `.foregroundColor(.white)`)
                val contentColor = Color.White
                Icon(Icons.Default.ArrowCircleRight, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.import_words_continue),
                    color = contentColor,
                    fontSize = 17.sp
                )
            }
        }
    }

    if (importState.status == WalletViewModel.ImportWalletStatus.FAILED) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { viewModel.resetImportWalletState() },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.error), color = LocalAppColors.current.textPrimary) },
            text = { Text(importState.errorMessage ?: "Something went wrong", color = LocalAppColors.current.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetImportWalletState() }) {
                    Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** How far a control in iOS's plain button style fades while it is held. */
private const val IOS_PLAIN_PRESSED_ALPHA = 0.4f

/** iOS `Color.gray`, the disabled Next of the seed phrase step. */
private val IosGray = Color(0xFF8E8E93)

enum class PassphraseMode { CREATE, IMPORT }

/**
 * Optional BIP39 passphrase ("25th word") step: Create with a Passphrase, or after the seed entry
 * (import). Explains what a passphrase does, how it helps, and the
 * risk of forgetting it, then lets the user proceed with a passphrase or skip. The caller performs
 * the actual create/import commit via [onProceed], which receives the chosen passphrase ("" when
 * skipped). Copy is English-only for now — a follow-up can move these literals into strings.xml.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassphraseSetupScreen(
    mode: PassphraseMode,
    isBusy: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onBack: () -> Unit,
    /** Address #0 for a candidate passphrase, for the live preview on the entry step. Null when
     *  the caller has no words to derive from. */
    previewAddress: (String) -> String? = { null },
    /** What that address holds, or null when it can't be checked. */
    previewBalance: suspend (String) -> Long? = { null },
    /** Set on import: the preview looks for the address holding funds... */
    findAddresses: (suspend (String) -> List<com.kachat.app.services.FoundChattingAddress>?)? = null,
    /** ...and says which one was found or picked. */
    onSelectIndex: (Int) -> Unit = {},
    onProceed: (String) -> Unit
) {
    // Creating only reaches here through "Advanced: Create with a Passphrase", which has already
    // answered the question (iOS 8d5f925).
    var step by remember { mutableStateOf(if (mode == PassphraseMode.CREATE) PassphraseStep.ENTRY else PassphraseStep.QUESTION) }
    // Where the explainer's Back returns: the question (import) or the entry screen.
    var explainerReturnStep by remember { mutableStateOf(PassphraseStep.QUESTION) }
    // The import's search for the address holding funds is running: Import waits for it, or it
    // would import onto #0 while the funds sit on another address.
    var isSearchingAddresses by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val colors = LocalAppColors.current

    /** [skippingSearch]: import on address #0 without waiting for the funds search (iOS 4922804). */
    fun submitFromEntry(skippingSearch: Boolean = false) {
        if (isBusy || (isSearchingAddresses && !skippingSearch)) return
        when {
            passphrase.isBlank() ->
                localError = "Enter a passphrase, or go back and choose No."
            mode == PassphraseMode.CREATE && passphrase != confirm ->
                localError = "The passphrases do not match. Please re-enter them."
            else -> {
                if (skippingSearch) onSelectIndex(0)
                onProceed(passphrase)
            }
        }
    }

    // Back always lands one step nearer the question, and only leaves the flow from the question
    // itself (or, creating, from the entry screen) - reading the explainer returns to the screen it
    // was opened from, never skips past it.
    BackHandler(enabled = step == PassphraseStep.EXPLAINER || (step == PassphraseStep.ENTRY && mode == PassphraseMode.IMPORT)) {
        if (step == PassphraseStep.EXPLAINER) {
            step = explainerReturnStep
        } else {
            passphrase = ""
            confirm = ""
            step = PassphraseStep.QUESTION
        }
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        when (step) {
            PassphraseStep.QUESTION -> PassphraseQuestionStep(
                mode = mode,
                isBusy = isBusy,
                onYes = { step = PassphraseStep.ENTRY },
                onNo = { if (!isBusy) onProceed("") },
                onExplain = { explainerReturnStep = PassphraseStep.QUESTION; step = PassphraseStep.EXPLAINER },
                onBack = onBack,
            )
            PassphraseStep.ENTRY -> PassphraseEntryStep(
                mode = mode,
                isBusy = isBusy,
                passphrase = passphrase,
                onPassphraseChange = { passphrase = it },
                confirm = confirm,
                onConfirmChange = { confirm = it },
                reveal = reveal,
                onToggleReveal = { reveal = !reveal },
                previewAddress = previewAddress,
                previewBalance = previewBalance,
                findAddresses = findAddresses,
                onSelectIndex = onSelectIndex,
                isSearchingAddresses = isSearchingAddresses,
                onSearchingChange = { isSearchingAddresses = it },
                onSubmit = { submitFromEntry() },
                onSkipSearch = { submitFromEntry(skippingSearch = true) },
                onExplain = { explainerReturnStep = PassphraseStep.ENTRY; step = PassphraseStep.EXPLAINER },
                onBack = {
                    passphrase = ""
                    confirm = ""
                    if (mode == PassphraseMode.CREATE) onBack() else step = PassphraseStep.QUESTION
                },
            )
            PassphraseStep.EXPLAINER -> PassphraseExplainerStep(
                mode = mode,
                onBack = { step = explainerReturnStep },
            )
        }
    }

    val shownError = localError ?: errorMessage
    if (shownError != null) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { localError = null; onDismissError() },
            containerColor = colors.surface,
            title = { Text("Passphrase", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
            text = { Text(shownError, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { localError = null; onDismissError() }) {
                    Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private enum class PassphraseStep { QUESTION, ENTRY, EXPLAINER }

/** The question, alone on its own screen, with a way to go and read about it first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseQuestionStep(
    mode: PassphraseMode,
    isBusy: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onExplain: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Passphrase", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    SheetBarIconButton(Icons.Filled.ArrowBackIosNew, "Back", onClick = onBack, modifier = Modifier.padding(start = 12.dp), enabled = !isBusy, tint = LocalAppColors.current.textPrimary)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Did you create these words with a passphrase?",
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            // Import keeps a line of steer, because someone who has never heard of a passphrase
            // still has to answer a question about their own past. Create needs none: the choice
            // is theirs to make, and "What is a passphrase?" is right there if they want it.
            if (mode == PassphraseMode.IMPORT) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "If you are not sure, the answer is almost certainly no. A passphrase is something you would have typed in on purpose.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))

            Button(
                onClick = onYes,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Yes", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNo,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaTeal.copy(alpha = 0.15f),
                    contentColor = KaspaTeal,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isBusy) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("No", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onExplain, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("What is a passphrase?", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The field, with the chatting address it produces underneath.
 *
 * That live address is the whole point of this screen: a passphrase does not protect one account,
 * it opens a different one. Watching #0 change with every character is what makes that concrete,
 * and on import it is how someone confirms they typed the right thing before committing to an
 * account that would otherwise just look empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseEntryStep(
    mode: PassphraseMode,
    isBusy: Boolean,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    confirm: String,
    onConfirmChange: (String) -> Unit,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
    previewAddress: (String) -> String?,
    previewBalance: suspend (String) -> Long?,
    findAddresses: (suspend (String) -> List<com.kachat.app.services.FoundChattingAddress>?)?,
    onSelectIndex: (Int) -> Unit,
    isSearchingAddresses: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onSkipSearch: () -> Unit,
    onExplain: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    // The passphrase is as secret as the seed words, and "Show" puts it on screen in plain text:
    // no screenshots, screen recording or Recents thumbnail while this step is up, like the seed
    // screens (audit AND-030).
    SecureWindowWhileShown()
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Passphrase", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    SheetBarIconButton(Icons.Filled.ArrowBackIosNew, "Back", onClick = onBack, modifier = Modifier.padding(start = 12.dp), enabled = !isBusy, tint = LocalAppColors.current.textPrimary)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                if (mode == PassphraseMode.CREATE) "Choose your passphrase" else "Enter your passphrase",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (mode == PassphraseMode.CREATE)
                    "Write it down somewhere safe. Without it this account cannot be recovered, even with your 12 or 24 words."
                else
                    "It has to be exactly what you used before, including capital letters and spaces.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            PassphraseInputField(
                // Nothing changes the account once it is being made.
                value = passphrase,
                onValueChange = { if (!isBusy) onPassphraseChange(it) },
                reveal = reveal,
                onToggleReveal = onToggleReveal,
                placeholder = "Passphrase",
            )

            if (mode == PassphraseMode.CREATE) {
                Spacer(Modifier.height(12.dp))
                PassphraseInputField(
                    value = confirm,
                    onValueChange = { if (!isBusy) onConfirmChange(it) },
                    reveal = reveal,
                    onToggleReveal = onToggleReveal,
                    placeholder = "Re-enter passphrase",
                )
            }

            Spacer(Modifier.height(20.dp))

            PassphraseAddressPreview(
                passphrase = passphrase,
                showsFundsHint = mode == PassphraseMode.IMPORT,
                previewAddress = previewAddress,
                previewBalance = previewBalance,
                findAddresses = findAddresses,
                onSelectIndex = { if (!isBusy) onSelectIndex(it) },
                onSearchingChange = onSearchingChange,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = passphrase.isNotBlank() && !isBusy && !isSearchingAddresses,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isBusy) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else if (isSearchingAddresses && passphrase.isNotEmpty()) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Finding your address...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Text(
                        if (mode == PassphraseMode.CREATE) "Continue" else "Import",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
            // The search is a network round trip: never the only way forward (iOS 4922804).
            if (mode == PassphraseMode.IMPORT && isSearchingAddresses && passphrase.isNotEmpty() && !isBusy) {
                Text(
                    "Skip the Search: Import on Address #0",
                    color = KaspaTeal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onSkipSearch)
                        .padding(vertical = 4.dp),
                )
            }
            Text(
                "What is a passphrase?",
                color = KaspaTeal.copy(alpha = if (isBusy) 0.4f else 1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isBusy, onClick = onExplain)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** How long Import waits for the funds search before letting go and importing onto #0 (iOS
 *  4922804 `searchTimeoutNanoseconds`). */
private const val FUNDS_SEARCH_TIMEOUT_MS = 12_000L

/** Plain-language explainer. Back only, so reading about it always returns to the choice. */
/**
 * The whole reason this screen shows an address: a passphrase does not protect one account, it opens
 * a different one. Watching it change with every character is what makes that concrete, and on
 * import it is how someone confirms they typed the right thing before committing to an account that
 * would otherwise look simply empty (iOS 0877f88 `PassphraseAddressPreview`).
 */
@Composable
internal fun PassphraseAddressPreview(
    passphrase: String,
    showsFundsHint: Boolean,
    previewAddress: (String) -> String?,
    previewBalance: suspend (String) -> Long?,
    /** Set when the chatting address isn't known yet: the preview checks the first addresses on the
     *  identity chain, picks the first with funds, and lets the user pick another. */
    findAddresses: (suspend (String) -> List<com.kachat.app.services.FoundChattingAddress>?)? = null,
    onSelectIndex: (Int) -> Unit = {},
    /** True while that search runs, so the caller can hold its Import until the pick is made. */
    onSearchingChange: (Boolean) -> Unit = {},
) {
    val colors = LocalAppColors.current
    DisposableEffect(Unit) { onDispose { onSearchingChange(false) } }
    // Debounced: the derivation is PBKDF2 over 2048 rounds, quick but not free, and running it on
    // every keystroke would be felt.
    var shownAddress by remember { mutableStateOf<String?>(null) }
    // What [shownAddress] holds: null while looking it up, or when the lookup failed.
    var balanceSompi by remember { mutableStateOf<Long?>(null) }
    var balanceFailed by remember { mutableStateOf(false) }
    var foundAddresses by remember { mutableStateOf(emptyList<com.kachat.app.services.FoundChattingAddress>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    // The funds search failed or ran out of time: Import goes to #0, and the screen says so.
    var searchIncomplete by remember { mutableStateOf(false) }
    // Search Again: the same passphrase looked up once more.
    var searchAttempt by remember { mutableIntStateOf(0) }

    fun select(found: com.kachat.app.services.FoundChattingAddress) {
        selectedIndex = found.index
        onSelectIndex(found.index)
        shownAddress = found.address
        balanceSompi = found.balanceSompi
    }

    LaunchedEffect(passphrase, searchAttempt) {
        searchIncomplete = false
        balanceSompi = null
        balanceFailed = false
        foundAddresses = emptyList()
        selectedIndex = 0
        onSelectIndex(0)
        // Held from the first keystroke, not only once the lookup starts: an Import tapped in the
        // pause would otherwise go to #0.
        onSearchingChange(findAddresses != null)
        kotlinx.coroutines.delay(250)
        val address = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { previewAddress(passphrase) }
        shownAddress = address
        if (address == null) {
            balanceFailed = true
            onSearchingChange(false)
            return@LaunchedEffect
        }
        // a further pause, so only the passphrase someone stopped typing is looked up
        kotlinx.coroutines.delay(450)
        if (findAddresses != null) {
            // A slow or unreachable node lets Import go after the timeout, onto #0, and says so;
            // the search is dropped, so an answer that comes later is ignored (iOS 4922804).
            val search = async {
                try {
                    findAddresses(passphrase)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            val found = kotlinx.coroutines.withTimeoutOrNull(FUNDS_SEARCH_TIMEOUT_MS) { search.await() }
            search.cancel()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            onSearchingChange(false)
            val first = found?.firstOrNull { it.balanceSompi > 0 } ?: found?.firstOrNull()
            if (found == null || first == null) {
                searchIncomplete = true
                balanceFailed = true
                return@LaunchedEffect
            }
            foundAddresses = found
            select(first)
            return@LaunchedEffect
        }
        val sompi = previewBalance(address)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        balanceSompi = sompi
        balanceFailed = sompi == null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text("Your chatting address", color = colors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        // Every character, wrapped over as many lines as it takes. This is the one place
        // the address has to be readable in full: truncating it would hide exactly the
        // part that changes when the passphrase does, which is why it is on screen.
        Text(
            shownAddress ?: "Checking...",
            color = if (shownAddress == null) colors.textSecondary else colors.textPrimary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.sp,
            softWrap = true,
            overflow = TextOverflow.Visible,
        )
        // Only the chatting address's: funds can also sit on the account's other
        // addresses, so an empty one suggests a wrong passphrase rather than proving it.
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Balance", color = colors.textSecondary, fontSize = 12.sp)
            val sompi = balanceSompi
            when {
                sompi != null -> Text(
                    "${com.kachat.app.util.formatKasAmount(sompi / 100_000_000.0)} ${com.kachat.app.util.KaspaUnit.symbol}",
                    color = if (sompi > 0) colors.success else colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                balanceFailed -> Text("Couldn't check", color = colors.textSecondary, fontSize = 13.sp)
                else -> com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            }
        }
        val checkedSompi = balanceSompi
        if (showsFundsHint && checkedSompi != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (checkedSompi > 0) "This address holds funds, so this looks like the right account."
                else "No funds on this address. If you expected funds here, check your passphrase.",
                color = if (checkedSompi > 0) colors.success else colors.warning,
                fontSize = 12.sp,
            )
        }
        if (foundAddresses.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "We picked the first address with funds. Tap another if that's not the one you play from.",
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            for (found in foundAddresses) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { select(found) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (selectedIndex == found.index) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Address #${found.index}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${found.address.take(14)}...${found.address.takeLast(6)}",
                            color = colors.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        "${com.kachat.app.util.formatKasAmount(found.balanceSompi / 100_000_000.0)} ${com.kachat.app.util.KaspaUnit.symbol}",
                        color = if (found.balanceSompi > 0) colors.success else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (searchIncomplete) {
            Spacer(Modifier.height(4.dp))
            Text(
                "The search for funds on this account's other addresses didn't finish, so Import uses address #0.",
                color = colors.warning,
                fontSize = 12.sp,
            )
            Text(
                "Search Again",
                color = KaspaTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { searchAttempt++ }
                    .padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (passphrase.isEmpty())
                "This is the account your 12 or 24 words open on their own."
            else
                "A different passphrase gives a different address, and a different account.",
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseExplainerStep(mode: PassphraseMode, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("What is a passphrase?", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton(onBack, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PassphraseExplainerSection(
                "The short version",
                "A passphrase is an extra word or sentence you add on top of your 12 or 24 words. It is optional, and most people do not use one."
            )
            PassphraseExplainerSection(
                "It does not lock your account",
                "This is the part people get wrong. A passphrase does not put a password on your account. It opens a completely different account. Your 12 or 24 words with no passphrase opens one account. The same seed phrase with the word \"apple\" opens another one. With \"banana\", another one again. Every passphrase is its own separate account, with its own address and its own balance."
            )
            PassphraseExplainerSection(
                "Why anyone bothers",
                "If someone finds your written 12 or 24 words, they get the account those words open on their own. They do not get the one behind your passphrase, because they do not know there is one, and they could not guess it anyway."
            )
            PassphraseExplainerSection(
                "The catch",
                "There is no reset and no recovery. If you forget your passphrase, the account it opened is gone for good. Your 12 or 24 words alone will not bring it back, and nobody can help you. Treat it exactly like the seed phrase itself: written down, somewhere safe, before you rely on it."
            )
            PassphraseExplainerSection(
                "One more thing to know",
                "If you type the wrong passphrase, nothing will tell you. You will simply land in a different account, and it will look empty. That is not a bug and your money is not lost, it just means you are in the wrong account."
            )
            PassphraseExplainerSection(
                "So do you need one?",
                if (mode == PassphraseMode.CREATE)
                    "If you are not sure, you do not need one: go back to the start and tap Create New Account, which makes an account without a passphrase. It is still protected by your 12 or 24 words, and you can always create another account with a passphrase later."
                else
                    "If you never set one up, choose No. A passphrase is something you would have typed in on purpose, so if this is the first you are hearing of it, you do not have one."
            )
        }
    }
}

@Composable
private fun PassphraseExplainerSection(title: String, body: String) {
    val colors = LocalAppColors.current
    Column {
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseInputField(
    value: String,
    onValueChange: (String) -> Unit,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
    placeholder: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        placeholder = { Text(placeholder, color = LocalAppColors.current.textSecondary) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        // Password keyboard even while revealed: a visual transformation doesn't change what the
        // IME is told, and only the Password type keeps keyboards from learning, suggesting or
        // syncing what is typed (audit AND-030; RevealableSecureField does the same).
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false
        ),
        trailingIcon = {
            TextButton(onClick = onToggleReveal) {
                Text(if (reveal) "Hide" else "Show", color = KaspaTeal, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LocalAppColors.current.surface,
            unfocusedContainerColor = LocalAppColors.current.surface,
            focusedTextColor = LocalAppColors.current.textPrimary,
            unfocusedTextColor = LocalAppColors.current.textPrimary,
            cursorColor = KaspaTeal,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

/**
 * KasWare-style "which wallet is this seed from?" chooser, shown FIRST when the user taps Import
 * Existing Account (before seed entry, which then continues exactly as before). Ported from iOS's
 * `ImportSourceWalletView`. The selection maps a wallet name to its identity derivation-path
 * family ([WalletSourceFamily], rules replicated from KasWare's RESTORE_WALLETS/ADDRESS_TYPES +
 * hd-keyring derivation), so KaChat derives the chatting identity where that wallet actually kept
 * the user's funds and KNS domains. KaChat is preselected at the top; KaChat's own spending chain
 * always stays on the m/44'/111111'/1' branch regardless of this choice (see WalletManager's
 * decision comment).
 */
@Composable
fun ImportSourceWalletScreen(onBack: () -> Unit, onContinue: (WalletSourceFamily) -> Unit, onRestoreFromCard: () -> Unit = {}) {
    val nfcCardAvailable = com.kachat.app.services.NfcCard.isAvailable(androidx.compose.ui.platform.LocalContext.current)
    val options = remember { SourceWalletOption.ALL }
    var selectedName by remember { mutableStateOf(options.first().displayName) }
    val selectedFamily = options.firstOrNull { it.displayName == selectedName }?.family
        ?: WalletSourceFamily.KASPA_STANDARD

    Surface(color = LocalAppColors.current.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // iOS 26: the back chevron on a glass disc.
            SheetBarIconButton(Icons.Filled.ArrowBackIosNew, stringResource(R.string.back), onClick = onBack, tint = LocalAppColors.current.textPrimary)

            Spacer(Modifier.height(16.dp))
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.where_is_this_seed_phrase_from),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.different_wallets_store_your_kaspa),
                color = LocalAppColors.current.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.displayName == selectedName
                    Surface(
                        color = LocalAppColors.current.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(1.5.dp, KaspaTeal) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedName = option.displayName }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) KaspaTeal else LocalAppColors.current.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        option.displayName,
                                        color = LocalAppColors.current.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (option.isDefault) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.default_label),
                                            color = KaspaTeal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    option.family.pathDescription,
                                    color = LocalAppColors.current.textSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onContinue(selectedFamily) },
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.continue_label), color = Color.Black, fontWeight = FontWeight.Bold)
            }
            if (nfcCardAvailable) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRestoreFromCard)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Nfc, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    Text("Restore from NFC Card", color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The brand's near-black with a green cast, taken from the KaChat banner rather than the app's
 * usual true black. Scoped to the sign-in screen, and the same in both themes on purpose - it is
 * the one place the product introduces itself.
 */
internal val KaChatSignInBackground = Color(0xFF0E1614)

/** One step up from [KaChatSignInBackground], same cast, for the saved-account rows. */
internal val KaChatSignInCard = Color(0xFF1A2624)

/**
 * FLAG_SECURE (no screenshots, screen recording or Recents thumbnail) for as long as the calling
 * screen is composed. Counted per window, because the onboarding steps that use it follow one
 * another: on seed step -> passphrase step the next screen adds the flag before the previous one
 * is disposed, and a plain clear on dispose would lift it while the passphrase is on screen
 * (audit AND-030). The flag is cleared only when the last secure screen goes.
 */
@Composable
internal fun SecureWindowWhileShown() {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        if (window != null) SecureWindowCount.acquire(window)
        onDispose {
            if (window != null) SecureWindowCount.release(window)
        }
    }
}

private object SecureWindowCount {
    private val counts = java.util.WeakHashMap<android.view.Window, Int>()

    fun acquire(window: android.view.Window) {
        val n = counts[window] ?: 0
        counts[window] = n + 1
        if (n == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: android.view.Window) {
        val n = (counts[window] ?: 1) - 1
        if (n <= 0) {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            counts[window] = n
        }
    }
}

/**
 * Shown instead of the app when the accounts are on this phone but their keys are not (iOS
 * WalletRecoveryView). The keys are sealed by an Android Keystore key that never leaves the phone
 * it was made on, so data moved to a new phone arrives without them. This says what happened and
 * offers the two ways out.
 */
@Composable
private fun WalletRecoveryScreen(
    address: String?,
    onEnterRecoveryPhrase: () -> Unit,
    /** Removes the account; returns why nothing was removed, or null. */
    onRemove: () -> String?,
    /** On to the Welcome screen, after a removal or once there's nothing left to remove. */
    onLeave: () -> Unit,
    /** The saved keys can be read after all. */
    keysAreBack: () -> Boolean,
) {
    val colors = LocalAppColors.current
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    // The keys came back since this screen opened, so Remove stopped (iOS 4922804 "Couldn't remove").
    var removeError by remember { mutableStateOf<String?>(null) }
    val removeContext = androidx.compose.ui.platform.LocalContext.current
    // Back in front: its saved keys may open now, and then this screen has nothing to say (iOS
    // da59726, which reloads when the phone's protected data becomes available).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val latestKeysAreBack by androidx.compose.runtime.rememberUpdatedState(keysAreBack)
    val latestOnLeave by androidx.compose.runtime.rememberUpdatedState(onLeave)
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && latestKeysAreBack()) latestOnLeave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.KeyOff, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(56.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "12 or 24 Words Needed",
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "This phone has your account but not its keys. That happens after moving to a new phone, because KaPlay's keys are locked to the device they were created on. Enter your 12 or 24 words to unlock it here. What KaPlay saved for it on this phone is kept.",
                color = colors.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        // iOS shows the account's name over its address; here the name is inside the store that
        // can't be read, so the address alone (from the plain settings copy), when it is known.
        if (address != null) {
            Text(
                if (address.length > 20) "${address.take(12)}…${address.takeLast(8)}" else address,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.textSecondary.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Enter 12 or 24 Words",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KaspaTeal)
                    .clickable(onClick = onEnterRecoveryPhrase)
                    .padding(vertical = 14.dp),
            )
            Text(
                "Remove This Account From the Phone",
                color = Color(0xFFFF3B30),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showRemoveConfirmation = true }
                    .padding(vertical = 14.dp),
            )
        }
    }
    if (showRemoveConfirmation) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            containerColor = colors.surface,
            title = { Text("Remove this account?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The account and what KaPlay saved for it are deleted from this phone. Nothing on the blockchain changes: its Kaspa and its games stay on chain, and you can add the account again later with its 12 or 24 words.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirmation = false
                    // Face ID first, as on both Delete Account sheets (iOS 8d5f925).
                    removeContext.authenticateWithDeviceCredential(
                        title = "Confirm deleting this account",
                        onSuccess = {
                            val failure = onRemove()
                            if (failure == null) onLeave() else removeError = failure
                        },
                    )
                }) {
                    Text("Remove", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textSecondary)
                }
            },
        )
    }
    removeError?.let { message ->
        // Nothing was removed and the keys are back: the Welcome screen lists the account.
        val dismiss = {
            removeError = null
            onLeave()
        }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = dismiss,
            containerColor = colors.surface,
            title = { Text("Couldn't remove", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
