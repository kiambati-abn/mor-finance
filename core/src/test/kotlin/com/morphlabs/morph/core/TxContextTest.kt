package com.morphlabs.morph.core

import com.morphlabs.morph.core.insight.TxContext
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.sms.MpesaParser
import org.junit.Assert.assertTrue
import org.junit.Test

class TxContextTest {
    private val structural = TestBook.accounts.filter {
        it.role in setOf(SystemRole.MPESA, SystemRole.TRANSACTION_COSTS, SystemRole.WITHDRAWAL_CHARGES, SystemRole.FULIZA_CHARGES)
    }.map { it.id }.toSet()

    private fun entry(sms: String, id: Long, category: Long? = null) =
        MpesaParser.parse(sms)!!.let { m -> Posting.fromSms(m, TestBook.ts(m.time!!), TestBook::role, category, if (category == null) EntryStatus.NEEDS_REVIEW else EntryStatus.CONFIRMED).copy(id = id) }

    private fun explain(e: com.morphlabs.morph.core.model.Entry, all: List<com.morphlabs.morph.core.model.Entry>) =
        TxContext.explain(e, all, TestBook.accounts.associateBy { it.id }, structural, TestBook.zone)

    @Test fun paybillToAPhoneNumberAccount() {
        val e = entry(Fixtures.PAYBILL_AIRTEL, 1)
        val lines = explain(e, listOf(e))
        assertTrue(lines.toString(), lines[0].contains("Paybill") && lines[0].contains("254100000003") && lines[0].contains("phone number"))
        assertTrue(lines.toString(), lines.any { it.startsWith("Wednesday night, 9:09 PM") })
        assertTrue(lines.toString(), lines.any { it.startsWith("First time") })
    }

    @Test fun historyAndNeighbours() {
        val transport = TestBook.hint("transport")
        // Earlier the same day: sent Ksh 50 to Jane (filed Transport), then got Ksh 2,000 from Mary, then sent Jane again.
        val before = entry(Fixtures.SEND_WITH_PHONE.replace("22/10/26 at 2:22 PM", "20/10/26 at 8:00 AM"), 1, transport)
        val got = entry(Fixtures.RECEIVED, 2, TestBook.hint("salary"))
        val now = entry(Fixtures.SEND_WITH_PHONE, 3)
        val lines = explain(now, listOf(before, got, now))
        assertTrue(lines.toString(), lines[0].startsWith("You sent money to a person") && lines[0].contains("0712000001"))
        assertTrue(lines.toString(), lines.any { it.startsWith("2nd time") && it.contains("Transport") })
        assertTrue(lines.toString(), lines.any { it.contains("24 min earlier: received Ksh 2,000 from Mary Njeri") })
        assertTrue(lines.toString(), lines.any { it.contains("left Ksh 1,664.33 on M-Pesa") })
    }
}
