package com.kachat.app.services

import com.kachat.app.models.WalletSourceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The backup format of KaChat-NFC's docs/PROTOCOL.md, which a card shares across apps. */
class NfcCardBackupFormatTest {
    private val payload = NfcCardBackupPayload(
        words = listOf("abandon", "ability"),
        chattingAddress = "kaspa:example",
        family = "kaspaLegacy972",
        chattingAddressIndex = 3,
        alias = "My <Account>",
    )

    @Test
    fun `a sealed backup opens with its PIN and carries the format's header`() {
        val backup = NfcCardBackupFormat.seal(payload, "123456")
        assertArrayEquals("KNB1".toByteArray(), backup.copyOf(4))
        assertArrayEquals(byteArrayOf(0x00, 0x09, 0x27, 0xC0.toByte()), backup.copyOfRange(4, 8)) // 600,000
        assertEquals(payload, NfcCardBackupFormat.open(backup, "123456"))
    }

    @Test
    fun `the wrong PIN or a changed header does not open, and too few iterations are refused`() {
        val backup = NfcCardBackupFormat.seal(payload, "123456")
        assertThrows(NfcCardException.UnreadableBackup::class.java) { NfcCardBackupFormat.open(backup, "654321") }
        val changedSalt = backup.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertThrows(NfcCardException.UnreadableBackup::class.java) { NfcCardBackupFormat.open(changedSalt, "123456") }
        val weak = backup.copyOf().also { it[5] = 0 }
        assertThrows(NfcCardException.UnreadableBackup::class.java) { NfcCardBackupFormat.open(weak, "123456") }
    }

    @Test
    fun `family names are iOS's, and one this app lacks is refused`() {
        assertEquals("kaspaStandard", NfcCardBackupFormat.familyRaw(WalletSourceFamily.KASPA_STANDARD))
        assertEquals(WalletSourceFamily.ONE_KEY, NfcCardBackupFormat.family("oneKey"))
        assertNull(NfcCardBackupFormat.family("chainge"))
    }
}
