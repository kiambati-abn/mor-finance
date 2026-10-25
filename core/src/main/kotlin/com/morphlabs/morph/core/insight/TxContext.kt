package com.morphlabs.morph.core.insight

import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.core.sms.MpesaParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * "What was this again?" A few short lines that jog your memory on the Sort card: what the SMS
 * says in plain words, when it happened, your history with this person or till, and what
 * happened just before or after.
 */
object TxContext {

    private val phoneLike = Regex("""(\+?254|0)[17]\d{8}""")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun explain(e: Entry, all: List<Entry>, accounts: Map<Long, Account>, structural: Set<Long>, zone: ZoneId): List<String> {
        fun amount(x: Entry) = Posting.categoryAmount(x, structural).takeIf { it > 0 } ?: x.size()
        val who = e.counterparty?.trim()?.takeIf { it.isNotEmpty() } ?: "them"
        val out = mutableListOf<String>()

        // 1. What the SMS says, in plain words.
        val sms = e.smsBody?.let { MpesaParser.parse(it) }
        val phone = sms?.phone?.let { " ($it)" } ?: ""
        when (e.kind) {
            TxKind.SEND_MONEY -> out += "You sent money to a person: $who$phone."
            TxKind.PAYBILL -> {
                val acc = sms?.accountRef
                out += "You paid a bill (Paybill) to $who" + (acc?.let { ", account $it" } ?: "") + "." +
                    if (acc != null && acc.matches(phoneLike)) " The account looks like a phone number, often a top-up or paying someone." else ""
            }
            TxKind.BUY_GOODS -> out += "You paid a shop or business till: $who."
            TxKind.WITHDRAW -> out += "You withdrew cash at an agent ($who). Where did the cash go?"
            TxKind.AIRTIME -> out += "You bought airtime."
            TxKind.TO_MSHWARI -> out += "You moved money into M-Shwari."
            TxKind.FROM_MSHWARI -> out += "You moved money out of M-Shwari."
            TxKind.FULIZA_DRAW -> out += "You went below zero and Fuliza covered the gap."
            TxKind.FULIZA_REPAY -> out += "M-Pesa took money to repay Fuliza."
            TxKind.RECEIVED -> out += "$who sent you money$phone."
            TxKind.DEPOSIT -> out += "You deposited cash at an agent ($who)."
            TxKind.REVERSAL -> out += "A transaction was reversed and the money came back."
            else -> Unit
        }

        // 2. When, in words people remember.
        val t = Instant.ofEpochMilli(e.timestamp).atZone(zone)
        val part = when (t.hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" }
        out += "${t.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} $part, ${timeFmt.format(t)}."

        // 3. Your history with this person or till.
        val unc = accounts.values.firstOrNull { it.role == SystemRole.UNCATEGORIZED }?.id
        val past = e.counterpartyKey?.let { k -> all.filter { it.id != e.id && it.counterpartyKey == k && it.timestamp < e.timestamp } }.orEmpty()
        if (e.counterpartyKey != null && e.kind != TxKind.AIRTIME && e.kind != TxKind.FULIZA_REPAY && e.kind != TxKind.FULIZA_DRAW) {
            if (past.isEmpty()) out += "First time with $who in Morph."
            else {
                val last = past.maxBy { it.timestamp }
                val filed = past.filter { it.status != EntryStatus.NEEDS_REVIEW && it.categoryId != null && it.categoryId != unc }
                    .groupingBy { it.categoryId!! }.eachCount().maxByOrNull { it.value }
                out += "${past.size + 1}${suffix(past.size + 1)} time. Last: ${Money.format(amount(last))} on ${dayFmt.format(Instant.ofEpochMilli(last.timestamp).atZone(zone))}" +
                    (filed?.let { (id, n) -> accounts[id]?.let { a -> ". You filed it as ${a.label}" + if (n > 1) " ($n×)" else "" } } ?: "") + "."
                if (past.size >= 2) {
                    val usual = past.map { amount(it) }.sorted()[past.size / 2]
                    val now = amount(e)
                    if (usual > 0 && now >= usual * 2) out += "Bigger than usual (normally about ${Money.format(usual)})."
                    else if (usual > 0 && now * 2 <= usual) out += "Smaller than usual (normally about ${Money.format(usual)})."
                }
            }
        }

        // 4. What happened just before or after (within 2 hours).
        val window = 2 * 3600 * 1000L
        all.asSequence()
            .filter { it.id != e.id && it.source != EntrySource.SYSTEM && abs(it.timestamp - e.timestamp) <= window && it.counterpartyKey != e.counterpartyKey }
            .sortedBy { abs(it.timestamp - e.timestamp) }
            .take(1)
            .forEach { n ->
                val mins = abs(n.timestamp - e.timestamp) / 60000
                val gap = if (mins < 1) "Same minute" else if (mins < 60) "$mins min ${if (n.timestamp < e.timestamp) "earlier" else "later"}" else "${mins / 60} h ${if (n.timestamp < e.timestamp) "earlier" else "later"}"
                val what = when (n.kind) {
                    TxKind.RECEIVED -> "received ${Money.format(amount(n))} from ${n.counterparty ?: "someone"}"
                    TxKind.WITHDRAW -> "withdrew ${Money.format(amount(n))} cash"
                    TxKind.AIRTIME -> "bought ${Money.format(amount(n))} airtime"
                    else -> "${if (n.kind?.outgoing == true) "paid" else "got"} ${Money.format(amount(n))} ${if (n.kind?.outgoing == true) "to" else "from"} ${n.counterparty ?: n.description}"
                }
                out += "$gap: $what."
            }

        // 5. Where it left you.
        if (e.kind?.outgoing == true) e.smsBalance?.let { out += "It left ${Money.format(it)} on M-Pesa." }
        return out
    }

    private fun suffix(n: Int) = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
}
