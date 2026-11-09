package com.kachat.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kachat.app.services.BroadcastScanningService
import com.kachat.app.services.CrashRecorder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class — required by Hilt for dependency injection.
 * All singleton services are initialized here via Hilt modules in the `di` package.
 *
 * KaChat's own also ran group chats, push, KaPosts, Nextcloud, the portfolio and payment requests
 * from here; KaChat Chess keeps only the wallet, the node pool, and what Chess Online reads the
 * arena and sends its moves through (iOS KaChatChessApp).
 *
 * Startup rule: nothing optional may stop the app from opening. On a hardened ROM the Keystore
 * behind EncryptedSharedPreferences can throw, so every service below is injected as
 * [dagger.Lazy] - Hilt's field injection (inside super.onCreate) creates none of them - and
 * onCreate then builds each one as its own [startupStep]: one that throws is logged and the app
 * opens without it. The lifecycle callbacks below are guarded the same way.
 */
@HiltAndroidApp
class KaChatApplication : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Before anything else - Hilt builds the whole object graph in onCreate, and a crash in
        // there is exactly the "closes on the splash screen" a user cannot describe. The record
        // is offered for sharing on the next launch.
        CrashRecorder.install(this)
        // The network this launch runs on (Settings > Connection > Testnet), known before Hilt
        // starts any service - the node pool, wallet and indexers are all set up for it.
        // [base], not `this`: inside attachBaseContext the Application's own applicationContext
        // is still null, and reading prefs through it crashed every launch.
        com.kachat.app.util.KaspaNetwork.init(base)
        // The rooms the indexer tracks beyond the curated ones: read by the scanner's prune and
        // metered gate as soon as they start, so known before Hilt does.
        com.kachat.app.services.IndexerTrackedRooms.init(base)
    }

    // The block scanner the arena is read through. Built at startup so its self-observing
    // start/stop logic (see its init block) runs for the app's whole lifetime.
    @Inject
    lateinit var broadcastScanningService: dagger.Lazy<BroadcastScanningService>

    @Inject
    lateinit var nodePoolManager: dagger.Lazy<com.kachat.app.services.NodePoolManager>

    // For mirroring the persisted "Verbose API Logging" toggle into ApiLogging.verbose (a plain
    // volatile flag the OkHttp logging interceptor reads per request) — see onCreate below.
    @Inject
    lateinit var appSettingsRepository: dagger.Lazy<com.kachat.app.repository.AppSettingsRepository>

    // Holds the app's foreground state, which the block scanner's metered gate reads.
    @Inject
    lateinit var notificationHelper: dagger.Lazy<com.kachat.app.services.NotificationHelper>

    // Identity is .kachat everywhere: every player's name and avatar reads the registry's
    // identity cache and the social image cache through their companions, so both are built at
    // startup.
    @Inject
    lateinit var kachatNamesRegistry: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesRegistry>

    @Inject
    lateinit var kachatSocialImages: dagger.Lazy<com.kachat.app.services.kachatnames.KachatSocialImageResolver>

    /** The contacts the .kachat sweep below asks about. */
    @Inject
    lateinit var chatRepository: dagger.Lazy<com.kachat.app.repository.ChatRepository>

    /** The chatting address's live UTXO subscription, for the Wallet tab's bell and balances
     *  (iOS 1751d20 WalletReceiptNotifier): held while the app is in front. */
    @Inject
    lateinit var walletReceiptNotifier: dagger.Lazy<com.kachat.app.services.WalletReceiptNotifier>

    /** One non-essential startup step: a failure is logged and the app opens without it. */
    private fun startupStep(what: String, block: () -> Unit) =
        com.kachat.app.util.StartupSafety.step(TAG, what, block)

    /** For the coroutines launched below: a failure is logged, never the end of the process. */
    private val logOnly = com.kachat.app.util.StartupSafety.logOnlyHandler(TAG)

    override fun onCreate() {
        super.onCreate()

        // How did the previous process end? A native crash or an OS kill never reaches the
        // exception handler installed in attachBaseContext; the system's own exit history does.
        startupStep("process exit history") { CrashRecorder.noteProcessExits(this) }

        startupStep("broadcast scanning") { broadcastScanningService.get() }
        startupStep("node pool") { nodePoolManager.get() }
        startupStep("settings") { appSettingsRepository.get() }
        startupStep("notifications") { notificationHelper.get() }

        // The .kachat identity caches the display rules read (see the fields above).
        startupStep(".kachat identity caches") {
            if (com.kachat.app.services.kachatnames.KachatNamesService.profilesEnabled) {
                startupStep(".kachat registry") { kachatNamesRegistry.get() }
                startupStep(".kachat social images") { kachatSocialImages.get() }
                // Players shown as their address are asked again for a .kachat name, once a
                // minute while the app is open.
                startupStep(".kachat unnamed sweep") {
                    kachatNamesRegistry.get().startUnnamedIdentitySweep(
                        isActive = {
                            ProcessLifecycleOwner.get().lifecycle.currentState
                                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                        },
                        candidates = { unnamedContactAddresses() },
                    )
                }
            }
        }

        // Keep ApiLogging.verbose (read by the OkHttp interceptors in AppModule on every request)
        // in sync with the persisted Verbose API Logging toggle for the process's whole lifetime.
        ProcessLifecycleOwner.get().lifecycleScope.launch(logOnly) {
            appSettingsRepository.get().verboseApiLogging.collect { com.kachat.app.util.ApiLogging.verbose = it }
        }

        val foregroundObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                startupStep("foreground state: background") { notificationHelper.get().setAppForeground(false) }
                startupStep("wallet receipts: stop") { walletReceiptNotifier.get().stop() }
            }

            override fun onStart(owner: LifecycleOwner) {
                startupStep("foreground state: foreground") { notificationHelper.get().setAppForeground(true) }
                // gRPC connections can die silently while the app is backgrounded: reconnect the
                // dead ones now rather than letting the next move discover it.
                startupStep("node pool: reconnect") { nodePoolManager.get().reconnectStaleConnections() }
                // the Wallet tab's bell: Kaspa arriving on the chatting address (iOS 1751d20)
                startupStep("wallet receipts: start") { walletReceiptNotifier.get().start() }
                if (runCatching { com.kachat.app.services.kachatnames.KachatNamesService.isLaunched }.getOrDefault(false)) {
                    owner.lifecycleScope.launch(Dispatchers.IO + logOnly) {
                        try {
                            kachatNamesRegistry.get().refreshIfStale()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("KaChatApplication", ".kachat registry refresh failed", e)
                        }
                    }
                }
            }
        }
        startupStep("lifecycle observer") { ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver) }
    }

    private companion object {
        const val TAG = "KaChatApplication"
    }

    /**
     * The contacts the app shows as an address unless they have a .kachat name: no name you gave
     * them, not your own address (iOS KachatNamesRegistry.refreshUnnamedIdentities).
     */
    private suspend fun unnamedContactAddresses(): List<String> {
        val contacts = chatRepository.get().getContacts().first()
        return contacts
            .filter { contact ->
                contact.alias.isNullOrBlank() &&
                    // the self-chat: a contact row of the account's own address
                    !contact.id.equals(contact.walletAddress, ignoreCase = true)
            }
            .map { it.id }
    }
}
