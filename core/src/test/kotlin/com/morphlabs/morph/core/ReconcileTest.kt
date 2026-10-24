package com.morphlabs.morph.core

import com.morphlabs.morph.core.TestBook.role
import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.ledger.Reconcile
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.Line
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.sms.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileTest {

    private val mpesa = role(SystemRole.MPESA)
    private var nextId = 1L
    private fun post(sms: String): Entry {
        val t = MpesaParser.parse(sms)!!
        return Posting.fromSms(t, TestBook.ts(t.time!!), ::role, null, EntryStatus.NEEDS_REVIEW).copy(id = nextId++)
    }

    @Test fun orderOfArrivalDoesNotMatter() {
        // Newest first, the way a re-import of older history would add them.
        val e = listOf(Fixtures.PAYBILL_AIRTEL, Fixtures.PAYBILL_DIRECT_PAY_EARLIER, Fixtures.TILL_SHOP, Fixtures.TILL_CARREFOUR, Fixtures.SEND_NO_PHONE).map(::post)
        val r = Reconcile.mpesa(e, mpesa)!!
        assertEquals(emptyList<Reconcile.Gap>(), r.gaps)
        // 641.33 after sending 30 → started at 671.33
        assertEquals(67133L, r.opening)
        val all = e + Posting.opening(r.openingTimestamp, mpesa, true, r.opening, ::role)
        assertEquals(31133L, Balances.natural(TestBook.accounts.first { it.id == mpesa }, Balances.raw(all)))
    }

    @Test fun aMissingMessageBecomesOneGapInTheRightPlace() {
        // DIRECT PAY 23 between SHOP and AIRTEL is missing (SMS deleted).
        val e = listOf(Fixtures.SEND_NO_PHONE, Fixtures.TILL_CARREFOUR, Fixtures.TILL_SHOP, Fixtures.PAYBILL_AIRTEL).map(::post)
        val r = Reconcile.mpesa(e, mpesa)!!
        assertEquals(1, r.gaps.size)
        assertEquals(-2300L, r.gaps.first().amount)
        assertEquals(e.last().id, r.gaps.first().atEntryId)
        // With the gap posted, Morph matches M-Pesa again.
        val fixed = e + Posting.opening(r.openingTimestamp, mpesa, true, r.opening, ::role) +
            Entry(timestamp = r.gaps.first().timestamp, description = "gap", lines = listOf(Line(mpesa, -2300), Line(role(SystemRole.UNCATEGORIZED), 2300)))
        assertEquals(31133L, Balances.natural(TestBook.accounts.first { it.id == mpesa }, Balances.raw(fixed)))
        assertEquals(emptyList<Reconcile.Gap>(), Reconcile.mpesa(fixed.filter { it.description != "Opening balance" }, mpesa)!!.gaps)
    }

    @Test fun recentGapsWait() {
        val e = listOf(Fixtures.SEND_NO_PHONE, Fixtures.TILL_CARREFOUR, Fixtures.TILL_SHOP, Fixtures.PAYBILL_AIRTEL).map(::post)
        val r = Reconcile.mpesa(e, mpesa, settleBefore = e.last().timestamp)!!
        assertEquals(0, r.gaps.size)
    }

    @Test fun sameMinuteAtTheVeryStart() {
        // The two 9:09 PM messages are the oldest ones, and arrive newest first.
        val e = listOf(Fixtures.PAYBILL_AIRTEL, Fixtures.PAYBILL_DIRECT_PAY_EARLIER).map(::post)
        val r = Reconcile.mpesa(e, mpesa)!!
        assertEquals(emptyList<Reconcile.Gap>(), r.gaps)
        assertEquals(38433L, r.opening) // 361.33 + 23
    }
}
