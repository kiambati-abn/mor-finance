package com.morphlabs.morph.core.sms

import com.morphlabs.morph.core.model.TxKind

object Counterparty {

    private val trailingNumber = Regex("""(\s+\d{1,3})+$""")
    private val spaces = Regex("""\s+""")

    /** "LAKEWOOD HOTEL." -> "LAKEWOOD HOTEL", "judy  kendi" -> "JUDY KENDI", "DIRECT PAY 05" -> "DIRECT PAY". */
    fun normalize(name: String): String =
        name.uppercase()
            .replace(spaces, " ")
            .trim()
            .trimEnd('.', ',')
            .replace(trailingNumber, "")
            .trim()

    /** People are matched by phone when we have a full one, places by normalized name. */
    fun key(kind: TxKind, name: String?, phone: String?): String? {
        val family = when (kind) {
            TxKind.SEND_MONEY, TxKind.RECEIVED -> "person"
            TxKind.PAYBILL -> "paybill"
            TxKind.BUY_GOODS -> "till"
            TxKind.WITHDRAW, TxKind.DEPOSIT -> "agent"
            else -> kind.name.lowercase()
        }
        if (family == "person" && phone != null && !phone.contains('*')) return "person:${phone.takeLast(9)}"
        val n = name?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return if (family == kind.name.lowercase()) family else null
        return "$family:$n"
    }

    /** Words in a counterparty name that carry meaning for the learner. */
    fun tokens(name: String?): Set<String> {
        if (name == null) return emptySet()
        return normalize(name).split(' ', '-', '&', '/')
            .map { it.trim('.', ',', '(', ')') }
            .filter { it.length >= 3 && it !in stopWords && !it.all(Char::isDigit) }
            .toSet()
    }

    private val stopWords = setOf("LTD", "LIMITED", "THE", "AND", "KENYA", "ENTERPRISES", "ENTERPRISE", "CO", "C2B", "CENTRE", "CENTER")
}
