package com.morphlabs.morph.core

import com.morphlabs.morph.core.TestBook.hint
import com.morphlabs.morph.core.TestBook.role
import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.Line
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.sms.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PostingTest {

    private fun post(sms: String, category: Long? = null) =
        Posting.fromSms(MpesaParser.parse(sms)!!, 0L, ::role, category, EntryStatus.AUTO)

    @Test fun sendWithFeeSplitsTheFee() {
        val e = post(Fixtures.SEND_WITH_FEE, hint("family"))
        assertEquals(
            setOf(Line(hint("family"), 20000), Line(role(SystemRole.TRANSACTION_COSTS), 700), Line(role(SystemRole.MPESA), -20700)),
            e.lines.toSet(),
        )
        assertEquals(EntryStatus.AUTO, e.status)
        assertEquals("Sent to Peter Kamau", e.description)
    }

    @Test fun unknownCategoryWaitsInUncategorized() {
        val e = post(Fixtures.TILL_HOTEL)
        assertEquals(EntryStatus.NEEDS_REVIEW, e.status)
        assert(e.lines.contains(Line(role(SystemRole.UNCATEGORIZED), 20000)))
    }

    @Test fun withdrawalMovesMoneyToCashAndChargesAreTracked() {
        val e = post(Fixtures.WITHDRAW)
        assertEquals(EntryStatus.CONFIRMED, e.status)
        assertEquals(
            setOf(Line(role(SystemRole.CASH), 100000), Line(role(SystemRole.WITHDRAWAL_CHARGES), 2900), Line(role(SystemRole.MPESA), -102900)),
            e.lines.toSet(),
        )
    }

    @Test fun fulizaCreatesDebtAndCharge() {
        val e = post(Fixtures.FULIZA)
        assertEquals(
            setOf(Line(role(SystemRole.MPESA), 5000), Line(role(SystemRole.FULIZA_CHARGES), 50), Line(role(SystemRole.FULIZA), -5050)),
            e.lines.toSet(),
        )
        val repay = post(Fixtures.FULIZA_REPAY)
        val raw = Balances.raw(listOf(e, repay))
        val fuliza = TestBook.accounts.first { it.role == SystemRole.FULIZA }
        assertEquals(0L, Balances.natural(fuliza, raw))
    }

    @Test fun receivedMoneyIsCreditedToTheChosenSource() {
        val e = post(Fixtures.RECEIVED, hint("family"))
        assertEquals(setOf(Line(role(SystemRole.MPESA), 200000), Line(hint("family"), -200000)), e.lines.toSet())
    }

    @Test fun recategorizeKeepsWalletAndFees() {
        val e = post(Fixtures.SEND_WITH_FEE)
        val fixed = Posting.recategorize(e, hint("friends"))
        assertEquals(EntryStatus.CONFIRMED, fixed.status)
        assert(fixed.lines.contains(Line(hint("friends"), 20000)))
        assertEquals(hint("friends"), fixed.categoryId)
        assert(fixed.lines.contains(Line(role(SystemRole.TRANSACTION_COSTS), 700)))
    }

    @Test fun runningBalanceMatchesTheSms() {
        // Opening balance taken from the first SMS, then each message should land exactly on its stated balance.
        val msgs = listOf(Fixtures.SEND_NO_PHONE, Fixtures.TILL_CARREFOUR, Fixtures.TILL_SHOP, Fixtures.PAYBILL_DIRECT_PAY_EARLIER, Fixtures.PAYBILL_AIRTEL).map { MpesaParser.parse(it)!! }
        val first = msgs.first()
        val opening = first.balance!! + first.amount + first.fee
        val entries = mutableListOf(Posting.opening(0, role(SystemRole.MPESA), true, opening, ::role))
        val mpesa = TestBook.accounts.first { it.role == SystemRole.MPESA }
        for (m in msgs) {
            entries += Posting.fromSms(m, 0, ::role, null, EntryStatus.AUTO)
            assertEquals("balance after ${m.code}", m.balance, Balances.natural(mpesa, Balances.raw(entries)))
        }
    }

    private val structural = setOf(role(SystemRole.MPESA), role(SystemRole.TRANSACTION_COSTS), role(SystemRole.WITHDRAWAL_CHARGES), role(SystemRole.FULIZA_CHARGES))

    @Test fun splitKeepsWalletAndFeesAndPicksTheBiggestPart() {
        val e = post(Fixtures.SEND_WITH_FEE, hint("family")) // 200 + 7 fee
        val s = Posting.split(e, listOf(hint("family") to 15000, hint("friends") to 5000), structural)
        assertEquals(0L, s.lines.sumOf { it.amount })
        assertEquals(hint("family"), s.categoryId)
        assertEquals(20000L, Posting.categoryAmount(s, structural))
        assert(s.lines.contains(Line(role(SystemRole.TRANSACTION_COSTS), 700)))
        assert(s.lines.contains(Line(role(SystemRole.MPESA), -20700)))
        assert(s.lines.contains(Line(hint("friends"), 5000)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitMustAddUp() {
        Posting.split(post(Fixtures.TILL_HOTEL, hint("food")), listOf(hint("food") to 100), structural)
    }

    @Test fun categoryAmountForMoneyIn() {
        val e = post(Fixtures.RECEIVED, hint("salary"))
        assertEquals(200000L, Posting.categoryAmount(e, structural))
        val split = Posting.split(e, listOf(hint("salary") to 150000, hint("gifts") to 50000), structural)
        assertEquals(200000L, Posting.categoryAmount(split, structural))
        assertEquals(hint("salary"), split.categoryId)
    }
}
