package com.kachat.app.services

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.google.gson.GsonBuilder
import com.kachat.app.models.WalletSourceFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

/**
 * Client for a KaChat-NFC card: an optional backup of a seed phrase the user already has, saved and
 * restored by tapping a Java Card. The card never generates or uses a key. It holds bytes this file
 * has already encrypted, behind a PIN. The commands and backup format are public, in
 * docs/PROTOCOL.md of https://github.com/KaspaSilver/KaChat-NFC, so a card works across apps
 * (iOS 0877f88 NFCCard.swift).
 */
object NfcCard {
    val APPLET_AID = byteArrayOf(0xF0.toByte(), 0x4B, 0x41, 0x43, 0x48, 0x41, 0x54, 0x4E, 0x46, 0x43, 0x01)

    const val PIN_LENGTH = 6
    const val TAP_PROMPT = "Hold your card flat against the back of your phone."
    /** How long a scan waits for a card, as an iOS NFC session does. */
    private const val SCAN_TIMEOUT_MS = 60_000L
    /** Long enough for the slowest card's write, not so long a card pulled away hangs the sheet. */
    private const val TRANSCEIVE_TIMEOUT_MS = 5_000

    /** A phone without NFC has no adapter. In Play builds too since 2026-11-06, as in KaChat. */
    fun isAvailable(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /** Waits for one card and runs [work] against it. A tap lasts as long as [work] does, so slow
     *  work such as key derivation belongs outside it. Cancelling the caller stops the scan. */
    @SuppressLint("MissingPermission")
    suspend fun <T> run(activity: Activity, work: suspend (NfcCardConnection) -> T): T {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: throw NfcCardException.NfcUnavailable()
        if (!adapter.isEnabled) throw NfcCardException.NfcOff()
        try {
            val tag = withTimeoutOrNull(SCAN_TIMEOUT_MS) { awaitTag(adapter, activity) }
                ?: throw NfcCardException.TimedOut()
            val isoDep = IsoDep.get(tag) ?: throw NfcCardException.NotLoaded()
            try {
                withContext(Dispatchers.IO) {
                    try {
                        isoDep.connect()
                        isoDep.timeout = TRANSCEIVE_TIMEOUT_MS
                    } catch (e: IOException) {
                        throw NfcCardException.CardRemoved()
                    }
                }
                return work(NfcCardConnection(isoDep))
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { runCatching { isoDep.close() } }
            }
        } finally {
            runCatching { adapter.disableReaderMode(activity) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitTag(adapter: NfcAdapter, activity: Activity): Tag =
        suspendCancellableCoroutine { continuation ->
            val claimed = java.util.concurrent.atomic.AtomicBoolean(false)
            // Presence checks every second, not the default 125 ms, so they don't get between the
            // commands of a save.
            val options = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1_000) }
            adapter.enableReaderMode(
                activity,
                // Taken once: two tag callbacks on different binder threads must not both resume.
                { tag -> if (claimed.compareAndSet(false, true)) continuation.resume(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                options,
            )
        }
}

/** What goes wrong with a card, in words to show. */
sealed class NfcCardException(message: String) : Exception(message) {
    class NfcUnavailable : NfcCardException("This phone can't read NFC cards.")
    class NfcOff : NfcCardException("NFC is off. Turn it on in your phone's settings and try again.")
    class TimedOut : NfcCardException("No card was found in time. Try again, and keep the card flat against the back of your phone.")
    class CardRemoved : NfcCardException("The card moved away too soon. Hold it still and try again. If a save was interrupted, your previous backup on the card is still there.")
    class NotLoaded : NfcCardException("This card doesn't have the KaChat-NFC software yet. Load it from the NFC Card page in Kaspa Quick Start.")
    class WrongPin(val triesRemaining: Int) : NfcCardException(
        if (triesRemaining == 1) "Wrong PIN. 1 try left before this card's PIN is blocked for good." else "Wrong PIN. $triesRemaining tries left."
    )
    class PinBlocked : NfcCardException("This card's PIN is blocked after too many wrong tries, so any backup on it can no longer be read. Use your 12 or 24 words instead.")
    class NoBackup : NfcCardException("There's no backup on this card.")
    class NoPin : NfcCardException("This card has no PIN yet, so it has no backup on it.")
    class CardChanged : NfcCardException("This isn't the card you scanned first. Start again with the card you want to use.")
    class BackupTooLarge : NfcCardException("This account's backup is too large for the card.")
    class WrongPassphrase : NfcCardException("That passphrase doesn't match this backup. It has to be exact, including capitals and spaces.")
    class NoPassphrase : NfcCardException("This account has no passphrase, so it can't be backed up to a card.")
    class UnreadableBackup : NfcCardException("This backup can't be read. It may have been made by a newer version of KaPlay.")
    class SaveNotConfirmed : NfcCardException("The save couldn't be confirmed, so don't rely on this card. Try again.")
    class SeedUnavailable : NfcCardException("This account's 12 or 24 words couldn't be read on this device.")
    class CardFailed(detail: String) : NfcCardException("The card reported a problem ($detail). Try again.")
}

/** The applet's answer to SELECT. */
data class NfcCardStatus(
    val pinSet: Boolean,
    val hasBackup: Boolean,
    val pinBlocked: Boolean,
    val triesRemaining: Int,
    val maxBackup: Int,
)

/** One card, for the length of one tap. */
class NfcCardConnection internal constructor(private val isoDep: IsoDep) {
    private suspend fun send(apdu: ByteArray): Pair<ByteArray, Int> = withContext(Dispatchers.IO) {
        val reply = try {
            isoDep.transceive(apdu)
        } catch (e: TagLostException) {
            throw NfcCardException.CardRemoved()
        } catch (e: IOException) {
            throw NfcCardException.CardRemoved()
        }
        if (reply.size < 2) throw NfcCardException.CardFailed("short reply")
        val status = (reply[reply.size - 2].toInt() and 0xFF shl 8) or (reply[reply.size - 1].toInt() and 0xFF)
        reply.copyOf(reply.size - 2) to status
    }

    private suspend fun command(
        instruction: Int,
        p1p2: Int = 0,
        data: ByteArray = ByteArray(0),
        expectsReply: Boolean = false,
    ): ByteArray {
        var apdu = byteArrayOf(0x80.toByte(), instruction.toByte(), (p1p2 shr 8 and 0xFF).toByte(), (p1p2 and 0xFF).toByte())
        if (data.isNotEmpty()) apdu += data.size.toByte()
        apdu += data
        if (expectsReply) apdu += 0x00.toByte()
        val (reply, status) = send(apdu)
        return when (status) {
            0x9000 -> reply
            in 0x63C0..0x63CF -> throw NfcCardException.WrongPin(status and 0x0F)
            0x6983 -> throw NfcCardException.PinBlocked()
            0x6A88 -> throw NfcCardException.NoBackup()
            else -> throw NfcCardException.CardFailed("%04X".format(status))
        }
    }

    suspend fun select(): NfcCardStatus {
        val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, NfcCard.APPLET_AID.size.toByte()) + NfcCard.APPLET_AID + 0x00.toByte()
        val (reply, status) = send(apdu)
        if (status == 0x6A82) throw NfcCardException.NotLoaded()
        if (status != 0x9000) throw NfcCardException.CardFailed("%04X".format(status))
        if (reply.size != 7 || reply[0].toInt() != 1) throw NfcCardException.UnreadableBackup()
        val flags = reply[3].toInt()
        return NfcCardStatus(
            pinSet = flags and 0x01 != 0,
            hasBackup = flags and 0x02 != 0,
            pinBlocked = flags and 0x04 != 0,
            triesRemaining = reply[4].toInt() and 0xFF,
            maxBackup = (reply[5].toInt() and 0xFF shl 8) or (reply[6].toInt() and 0xFF),
        )
    }

    suspend fun setPin(pin: String) {
        command(0x21, data = pin.toByteArray(Charsets.UTF_8))
    }

    suspend fun verifyPin(pin: String) {
        command(0x20, data = pin.toByteArray(Charsets.UTF_8))
    }

    /** The card checks the hash before the new copy replaces the old one, so an interrupted save
     *  leaves the previous backup in place. */
    suspend fun writeBackup(backup: ByteArray) {
        command(0xD0, p1p2 = backup.size)
        var offset = 0
        while (offset < backup.size) {
            val end = minOf(offset + CHUNK_SIZE, backup.size)
            command(0xD1, p1p2 = offset, data = backup.copyOfRange(offset, end))
            offset = end
        }
        command(0xD2, data = MessageDigest.getInstance("SHA-256").digest(backup))
    }

    suspend fun readBackup(): ByteArray {
        var backup = ByteArray(0)
        while (true) {
            val chunk = command(0xB0, p1p2 = backup.size, expectsReply = true)
            if (chunk.isEmpty()) return backup
            backup += chunk
        }
    }

    suspend fun wipe() {
        command(0xE0)
    }

    private companion object {
        const val CHUNK_SIZE = 200
    }
}

/**
 * What's encrypted on the card: everything an account needs beyond the words, because some accounts
 * can't be restored correctly from the words alone. The BIP39 passphrase is deliberately not here:
 * it's what protects the account if someone gets past the PIN. [chattingAddress] lets a restore catch
 * a mistyped passphrase, which would otherwise open a different, empty account. Nullable because it
 * comes off a card; [NfcCardBackupFormat.open] refuses one with anything missing.
 */
data class NfcCardBackupPayload(
    val version: Int? = 2,
    val words: List<String>?,
    val chattingAddress: String?,
    val family: String?,
    val chattingAddressIndex: Int?,
    val alias: String?,
)

/**
 * The backup format in docs/PROTOCOL.md: magic, PBKDF2 iterations, salt, nonce, then AES-256-GCM. The
 * key comes from the card's PIN, so nothing extra to remember. A six-digit PIN can be guessed offline
 * by anyone who extracts the chip, which is why the passphrase stays off the card. Key derivation
 * takes a moment, so call these away from the main thread.
 */
object NfcCardBackupFormat {
    private val MAGIC = "KNB1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val MAXIMUM_ITERATIONS = 10_000_000
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH = 16
    private const val HEADER_LENGTH = 4 + 4 + SALT_LENGTH + NONCE_LENGTH
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /** `family` in a backup: the names iOS writes, so a card works across apps. */
    private val FAMILY_NAMES = mapOf(
        WalletSourceFamily.KASPA_STANDARD to "kaspaStandard",
        WalletSourceFamily.KASPA_LEGACY_972 to "kaspaLegacy972",
        WalletSourceFamily.CHAINGE to "chainge",
        WalletSourceFamily.ONE_KEY to "oneKey",
    )

    fun familyRaw(family: WalletSourceFamily): String = FAMILY_NAMES.getValue(family)

    fun family(raw: String?): WalletSourceFamily? = FAMILY_NAMES.entries.firstOrNull { it.value == raw }?.key

    fun seal(payload: NfcCardBackupPayload, pin: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        val header = MAGIC + ByteBuffer.allocate(4).putInt(ITERATIONS).array() + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pin, salt, ITERATIONS), GCMParameterSpec(TAG_LENGTH * 8, nonce))
        cipher.updateAAD(header)
        // doFinal returns the ciphertext with its tag on the end, the format's own order.
        return header + cipher.doFinal(gson.toJson(payload).toByteArray(Charsets.UTF_8))
    }

    fun open(backup: ByteArray, pin: String): NfcCardBackupPayload {
        if (backup.size <= HEADER_LENGTH + TAG_LENGTH || !backup.copyOf(4).contentEquals(MAGIC)) {
            throw NfcCardException.UnreadableBackup()
        }
        val count = ByteBuffer.wrap(backup, 4, 4).int
        if (count < ITERATIONS || count > MAXIMUM_ITERATIONS) throw NfcCardException.UnreadableBackup()
        val salt = backup.copyOfRange(8, 8 + SALT_LENGTH)
        val nonce = backup.copyOfRange(8 + SALT_LENGTH, HEADER_LENGTH)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(pin, salt, count), GCMParameterSpec(TAG_LENGTH * 8, nonce))
            cipher.updateAAD(backup, 0, HEADER_LENGTH)
            cipher.doFinal(backup, HEADER_LENGTH, backup.size - HEADER_LENGTH)
        } catch (e: java.security.GeneralSecurityException) {
            throw NfcCardException.UnreadableBackup()
        }
        val payload = runCatching {
            gson.fromJson(String(plaintext, Charsets.UTF_8), NfcCardBackupPayload::class.java)
        }.getOrNull() ?: throw NfcCardException.UnreadableBackup()
        if (payload.version != 2 || payload.words.isNullOrEmpty() || payload.chattingAddress == null ||
            payload.family == null || payload.chattingAddressIndex == null || payload.alias == null
        ) {
            throw NfcCardException.UnreadableBackup()
        }
        return payload
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        // NFKD, so the same password typed on another device or keyboard gives the same key.
        val spec = PBEKeySpec(Normalizer.normalize(password, Normalizer.Form.NFKD).toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
