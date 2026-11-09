package com.kachat.app.services.kachatnames

import android.content.Context
import com.google.gson.Gson

/**
 * Your Domains' .kachat names and offers, kept on the device per wallet address and network so
 * the list shows at once instead of waiting on the registry every time (iOS 1751d20
 * `KachatOwnedNamesCache`). They rarely change, so the screen shows this copy first and the
 * registry read runs behind it, replacing the copy only when it answers - a new name, a sale, a
 * transfer or a lapse lands then; a failed read leaves the copy as it was.
 */
object KachatOwnedNamesCache {
    class Entry(val names: List<NameInfo>, val offers: List<OfferInfo>, val savedAtMs: Long)

    // Stored as plain fields with the byte arrays in hex, so the file never depends on how the
    // registry's own classes are laid out.
    private class StoredOutpoint(val txid: String, val index: Int)

    private class StoredName(
        val name: String,
        val key: String,
        val owner: String,
        val price: Long,
        val expiresAt: Long,
        val outpoint: StoredOutpoint,
        val registeredAt: Long?,
        val registeredTxId: String?,
        val updatedAt: Long?,
        val periodStart: Long?,
    )

    private class StoredOffer(
        val outpoint: StoredOutpoint,
        val key: String,
        val name: String?,
        val buyer: String,
        val seller: String,
        val amount: Long,
        val refundAfter: Long,
        val createdAt: Long?,
    )

    private class Stored(val names: List<StoredName>, val offers: List<StoredOffer>, val savedAtMs: Long)

    private const val PREFS = "kachat_owned_names"
    private val gson = Gson()

    private fun key(address: String): String {
        val network = if (com.kachat.app.util.KaspaNetwork.isTestnet) "testnet" else "mainnet"
        return "kachat_owned_names_${network}_${address.lowercase()}"
    }

    fun load(context: Context, address: String): Entry? = runCatching {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(address), null)
            ?: return null
        val stored = gson.fromJson(json, Stored::class.java) ?: return null
        Entry(
            names = stored.names.map { n ->
                NameInfo(
                    name = n.name,
                    key = unhex(n.key),
                    owner = unhex(n.owner),
                    price = n.price,
                    expiresAt = n.expiresAt,
                    outpoint = n.outpoint.toOutpoint(),
                    registeredAt = n.registeredAt,
                    registeredTxId = n.registeredTxId,
                    updatedAt = n.updatedAt,
                    periodStart = n.periodStart,
                )
            },
            offers = stored.offers.map { o ->
                OfferInfo(
                    outpoint = o.outpoint.toOutpoint(),
                    key = unhex(o.key),
                    name = o.name,
                    buyer = unhex(o.buyer),
                    seller = unhex(o.seller),
                    amount = o.amount,
                    refundAfter = o.refundAfter,
                    createdAt = o.createdAt,
                )
            },
            savedAtMs = stored.savedAtMs,
        )
    }.getOrNull()

    fun save(context: Context, names: List<NameInfo>, offers: List<OfferInfo>, address: String) {
        val stored = Stored(
            names = names.map { n ->
                StoredName(
                    n.name, KachatNames.hex(n.key), KachatNames.hex(n.owner), n.price, n.expiresAt,
                    n.outpoint.toStored(), n.registeredAt, n.registeredTxId, n.updatedAt, n.periodStart,
                )
            },
            offers = offers.map { o ->
                StoredOffer(
                    o.outpoint.toStored(), KachatNames.hex(o.key), o.name, KachatNames.hex(o.buyer),
                    KachatNames.hex(o.seller), o.amount, o.refundAfter, o.createdAt,
                )
            },
            savedAtMs = System.currentTimeMillis(),
        )
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key(address), gson.toJson(stored)).apply()
        }
    }

    /** The account is gone from this phone: what was saved for it, on either network (iOS da59726). */
    fun remove(context: Context, address: String) {
        runCatching {
            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            for (network in listOf("mainnet", "testnet")) edit.remove("kachat_owned_names_${network}_${address.lowercase()}")
            edit.apply()
        }
    }

    private fun Outpoint.toStored() = StoredOutpoint(KachatNames.hex(txid), index)

    private fun StoredOutpoint.toOutpoint() = Outpoint(unhex(txid), index)

    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}
