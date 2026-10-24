package com.morphlabs.morph.core.sms

import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.money.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads Safaricom M-Pesa confirmation SMS. Formats are matched loosely (spacing, case and
 * missing full stops vary between messages) and anything with a code, an amount and a balance
 * that doesn't match a known format still comes back as [TxKind.UNKNOWN] instead of being lost.
 */
object MpesaParser {

    /** Sender ids M-Pesa uses. */
    val senders = setOf("MPESA", "M-PESA")

    fun isMpesaSender(address: String?): Boolean = address?.trim()?.uppercase() in senders

    private const val AMT = """([\d,]+(?:\.\d{1,2})?)"""
    private val ksh = """Ksh\.?\s?$AMT"""
    private val phoneTail = Regex("""\s+((?:\+?254|0)\d{9}|0\d{2,3}\*{3}\d{3}|\+?254\d{0,3}\*{3}\d{3})$""")

    private val code = Regex("""^\s*([A-Z0-9]{10})\s+confirmed""", RegexOption.IGNORE_CASE)
    private val dateTime = Regex("""on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2})\s*([AaPp][Mm])""", RegexOption.IGNORE_CASE)
    private val balance = Regex("""(?:New\s+)?M-PESA\s+(?:account\s+)?balance\s+is\s+$ksh""", RegexOption.IGNORE_CASE)
    private val txCost = Regex("""Transaction\s+cost,?\s*$ksh""", RegexOption.IGNORE_CASE)

    private val paybill = Regex("""$ksh\s+sent\s+to\s+(.+?)\s+for\s+account\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val send = Regex("""$ksh\s+sent\s+to\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val buyGoods = Regex("""$ksh\s+paid\s+to\s+(.+?)\.?\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val received = Regex("""You\s+have\s+received\s+$ksh\s+from\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val withdraw = Regex("""Withdraw\s+$ksh\s+from\s+(.+?)\s+New\s+M-PESA""", RegexOption.IGNORE_CASE)
    private val deposit = Regex("""Give\s+$ksh\s+cash\s+to\s+(.+?)\s+New\s+M-PESA""", RegexOption.IGNORE_CASE)
    private val airtime = Regex("""You\s+bought\s+$ksh\s+of\s+airtime(?:\s+for\s+(\d+))?""", RegexOption.IGNORE_CASE)
    private val toMshwari = Regex("""$ksh\s+transferred\s+to\s+M-?Shwari""", RegexOption.IGNORE_CASE)
    private val fromMshwari = Regex("""$ksh\s+transferred\s+from\s+M-?Shwari""", RegexOption.IGNORE_CASE)
    private val fulizaDraw = Regex("""Fuliza\s+M-PESA\s+amount\s+is\s+$ksh""", RegexOption.IGNORE_CASE)
    private val fulizaFee = Regex("""Access\s+Fee\s+charged\s+$ksh""", RegexOption.IGNORE_CASE)
    private val fulizaRepay = Regex("""$ksh\s+from\s+your\s+M-PESA\s+has\s+been\s+used\s+to\s+(?:fully|partially)\s+pay\s+your\s+outstanding\s+Fuliza""", RegexOption.IGNORE_CASE)
    private val reversal = Regex("""Reversal\s+of\s+transaction\s+([A-Z0-9]{10}).*?$ksh\s+is\s+credited""", setOf(RegexOption.IGNORE_CASE))
    private val anyKsh = Regex(ksh, RegexOption.IGNORE_CASE)

    private val dateFormats = listOf("d/M/yy", "d/M/yyyy").map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    /** Collapses spacing, drops links, and adds the spaces M-Pesa sometimes leaves out ("PM.New"). */
    fun clean(body: String): String =
        body.replace(Regex("""https?://\S+"""), " ")
            .replace(Regex("""(?<=\d [AP]M|\d[AP]M)(?=[A-Za-z])"""), " ")
            .replace(Regex("""\.(?=New\s)"""), ". ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun parse(body: String): MpesaSms? {
        val text = clean(body)
        val code = code.find(text)?.groupValues?.get(1)?.uppercase()
        val time = parseTime(text)
        val bal = balance.find(text)?.let { money(it.groupValues[1]) }
        val cost = txCost.find(text)?.let { money(it.groupValues[1]) } ?: 0L

        fun build(kind: TxKind, amount: String, fee: Long = cost, who: String? = null, ref: String? = null, reversed: String? = null): MpesaSms? {
            val cents = money(amount) ?: return null
            val (name, phone) = splitPhone(who)
            return MpesaSms(
                code = code, kind = kind, amount = cents, fee = fee, balance = bal,
                counterparty = name, phone = phone, accountRef = ref?.trim()?.trimEnd('.'),
                time = time, reversedCode = reversed, raw = body,
            )
        }

        // Order matters: the more specific formats come first.
        fulizaRepay.find(text)?.let { return build(TxKind.FULIZA_REPAY, it.groupValues[1], fee = 0, who = "Fuliza M-PESA") }
        fulizaDraw.find(text)?.let { m ->
            val fee = fulizaFee.find(text)?.let { f -> money(f.groupValues[1]) } ?: 0L
            return build(TxKind.FULIZA_DRAW, m.groupValues[1], fee = fee, who = "Fuliza M-PESA")
        }
        reversal.find(text)?.let { return build(TxKind.REVERSAL, it.groupValues[2], fee = 0, who = "Reversal", reversed = it.groupValues[1]) }
        toMshwari.find(text)?.let { return build(TxKind.TO_MSHWARI, it.groupValues[1], who = "M-Shwari") }
        fromMshwari.find(text)?.let { return build(TxKind.FROM_MSHWARI, it.groupValues[1], who = "M-Shwari") }
        received.find(text)?.let { return build(TxKind.RECEIVED, it.groupValues[1], fee = 0, who = it.groupValues[2]) }
        withdraw.find(text)?.let { return build(TxKind.WITHDRAW, it.groupValues[1], who = agentName(it.groupValues[2])) }
        deposit.find(text)?.let { return build(TxKind.DEPOSIT, it.groupValues[1], fee = 0, who = agentName(it.groupValues[2])) }
        airtime.find(text)?.let { return build(TxKind.AIRTIME, it.groupValues[1], who = "Airtime", ref = it.groupValues.getOrNull(2)?.ifEmpty { null }) }
        paybill.find(text)?.let { return build(TxKind.PAYBILL, it.groupValues[1], who = it.groupValues[2], ref = it.groupValues[3]) }
        buyGoods.find(text)?.let { return build(TxKind.BUY_GOODS, it.groupValues[1], who = it.groupValues[2]) }
        send.find(text)?.let { return build(TxKind.SEND_MONEY, it.groupValues[1], who = it.groupValues[2]) }

        // Unknown wording, but it's clearly an M-Pesa transaction: keep it so nothing is missed.
        if (code != null && bal != null) {
            val first = anyKsh.findAll(text).map { it.groupValues[1] }.firstOrNull { money(it) != bal } ?: return null
            return build(TxKind.UNKNOWN, first)
        }
        return null
    }

    private fun money(s: String): Long? = Money.parse(s)

    private fun parseTime(text: String): LocalDateTime? {
        val m = dateTime.find(text) ?: return null
        val date = dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(m.groupValues[1], f) }.getOrNull() } ?: return null
        val t = runCatching { LocalTime.parse("${m.groupValues[2]} ${m.groupValues[3].uppercase()}", timeFormat) }.getOrNull() ?: return null
        return LocalDateTime.of(date, t)
    }

    /** "LYDIA  MASHETI 0712015738" -> ("LYDIA MASHETI", "0712015738"). */
    private fun splitPhone(who: String?): Pair<String?, String?> {
        if (who == null) return null to null
        val w = who.replace(Regex("""\s+"""), " ").trim().trimEnd('.')
        val m = phoneTail.find(w) ?: return w to null
        return w.substring(0, m.range.first).trim() to m.groupValues[1]
    }

    /** "123456 - JANE AGENCIES" -> "JANE AGENCIES". */
    private fun agentName(s: String): String = s.substringAfter(" - ", s).trim()
}
