package com.kachat.app.ui.screens

// ---------------------------------------------------------------------------------------------
// In-app KaChat links (shared with iOS - both platforms emit and accept exactly these forms):
// iOS 1751d20 `KaChatInternalLink` (Services/PaymentRequest.swift), KaPay Android's KaChatLink.kt.
// KaPlay reads them out of pasted text: a payment request pasted into a send fills it in.
// ---------------------------------------------------------------------------------------------

/**
 * A KaChat link found in text.
 *
 * Wire forms (identical on iOS):
 *   Profile        kachat://profile/<address>   https://kachat.app/u/<address>
 *   Payment req.   kachat://pay/<address>?...   https://kachat.app/pay/<address>?amount=<KAS>&memo=<text>[&chat=1]&by=<address>&sig=<hex>
 *                  (see [com.kachat.app.models.PaymentRequest])
 *
 * A profile's `<address>` is a full Kaspa address; in the web form a mainnet address drops its
 * `kaspa:` prefix (the app writes it without, so the link reads cleanly) and a missing prefix
 * means mainnet. kapay:// and kachatwallet:// read as kachat://.
 */
sealed class KaChatLinkRef {
    /** Someone's KaChat profile. Always a full, valid address. */
    data class Profile(val address: String) : KaChatLinkRef()
    /** A request to be paid. Every field validated, and a signed one only when its signature
     *  checks out. */
    data class PaymentRequest(val request: com.kachat.app.models.PaymentRequest) : KaChatLinkRef()
}

/** [raw] is the exact link substring as it appears in the text, [range] where it sits in it. */
data class KaChatLinkMatch(val range: IntRange, val raw: String, val ref: KaChatLinkRef)

object KaChatLink {
    /** The only web host a link is written with or opened from. */
    const val WEB_HOST = "kachat.app"

    // The trailing segment deliberately excludes '/', '?' and '#' so a link can never carry a
    // second path component, a query string or a fragment into the app.
    private val LINK_REGEX = Regex(
        """(?:kachat://(profile)/|https?://(?:www\.)?kachat\.app/(u)/)([^\s/?#]+)""",
        RegexOption.IGNORE_CASE
    )
    // A payment request carries a query, so it has its own pattern: everything up to whitespace
    // or a quote/angle bracket, as iOS matches it (the memo inside is percent-encoded).
    private val PAY_LINK_REGEX = Regex(
        """(?:kachat://pay/|https?://(?:www\.)?kachat\.app/pay/)([^\s<>"']+)""",
        RegexOption.IGNORE_CASE
    )
    /** Trailing prose punctuation is not part of the link ("... pay me: kachat://pay/..."). */
    private val TRAILING_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '\'', '"', ')', ']', '}')

    /** KaPay's own schemes, read as kachat:// (iOS `KaChatInternalLink.parse`): kapay:// is what
     *  KaPost hands its payment requests over with, kachatwallet:// its name from before KaPay. */
    private val WALLET_SCHEME_REGEX = Regex("^(?:kapay|kachatwallet)://", RegexOption.IGNORE_CASE)

    /** The link [raw] is, when it is nothing but one of these links (no surrounding text). */
    fun parse(raw: String): KaChatLinkRef? {
        val trimmed = raw.trim().replaceFirst(WALLET_SCHEME_REGEX, "kachat://")
        PAY_LINK_REGEX.matchEntire(trimmed)?.let { return paymentRequestRef(it.groupValues[1]) }
        val match = LINK_REGEX.matchEntire(trimmed) ?: return null
        return refFor(match)
    }

    /** The first valid KaChat link in [text], or null. Invalid ones are skipped, not accepted
     *  (iOS `KaChatInternalLink.match(in:)`). */
    fun findFirst(text: String): KaChatLinkMatch? {
        // Neither form can appear without "kachat" - the cheap bail-out for nearly every text.
        if (!text.contains("kachat", ignoreCase = true)) return null
        // Both patterns, in the order the links appear in the text (iOS scans with one).
        val candidates = (LINK_REGEX.findAll(text) + PAY_LINK_REGEX.findAll(text)).sortedBy { it.range.first }
        for (match in candidates) {
            var end = match.range.last
            while (end >= match.range.first && text[end] in TRAILING_PUNCTUATION) end--
            if (end < match.range.first) continue
            val range = match.range.first..end
            val raw = text.substring(range.first, range.last + 1)
            val ref = parse(raw) ?: continue
            return KaChatLinkMatch(range, raw, ref)
        }
        return null
    }

    private fun refFor(match: MatchResult): KaChatLinkRef? {
        val segment = runCatching { android.net.Uri.decode(match.groupValues[3]) }.getOrNull() ?: return null
        return profileAddress(segment)?.let(KaChatLinkRef::Profile)
    }

    /** A payment request's path tail (`<address>?<query>`, any `#fragment` ignored): exactly one
     *  address segment, then [com.kachat.app.models.PaymentRequest.fromLink] checks the rest. */
    private fun paymentRequestRef(tail: String): KaChatLinkRef? {
        val withoutFragment = tail.substringBefore('#')
        val path = withoutFragment.substringBefore('?')
        val query = if ('?' in withoutFragment) withoutFragment.substringAfter('?') else null
        if (path.isEmpty() || '/' in path) return null
        val address = runCatching { android.net.Uri.decode(path) }.getOrNull() ?: return null
        return com.kachat.app.models.PaymentRequest.fromLink(address, query)?.let(KaChatLinkRef::PaymentRequest)
    }

    /** A linked address is attacker-controlled like everything else in a link: it has to be a
     *  valid Kaspa address, checksum and all, or the link is not ours. It opens on the network the
     *  app runs on (iOS `profileLink(rawAddress:)`, IOS-003). */
    fun profileAddress(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length > 100) return null
        return runCatching { com.kachat.app.util.KaspaAddress.onActiveNetwork(trimmed) }.getOrNull()
    }
}
