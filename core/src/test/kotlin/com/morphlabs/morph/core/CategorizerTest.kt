package com.morphlabs.morph.core

import com.morphlabs.morph.core.TestBook.hint
import com.morphlabs.morph.core.learn.Categorizer
import com.morphlabs.morph.core.learn.Observation
import com.morphlabs.morph.core.learn.Query
import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.sms.Counterparty
import com.morphlabs.morph.core.sms.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {

    private fun query(sms: String): Query {
        val t = MpesaParser.parse(sms)!!
        return Query(t.counterpartyKey, t.counterparty, t.kind, t.outgoing, t.amount, t.time!!.hour)
    }

    private fun obs(sms: String, account: Long, ts: Long, weight: Double = 1.0): Observation {
        val t = MpesaParser.parse(sms)!!
        return Observation(t.counterpartyKey, Counterparty.tokens(t.counterparty), t.kind, t.outgoing, t.amount, t.time!!.hour, account, ts, weight)
    }

    @Test fun starterKnowledgeSuggestsButDoesNotAutoFileAHotel() {
        val g = Categorizer(TestBook.accounts, emptyList()).guess(query(Fixtures.TILL_HOTEL))
        assertNull(g.autoAccountId)
        assertEquals(hint("food"), g.suggestions.first().accountId)
        assertEquals(3, g.suggestions.size)
    }

    @Test fun airtimeIsFiledAutomatically() {
        val g = Categorizer(TestBook.accounts, emptyList()).guess(query(Fixtures.AIRTIME))
        assertEquals(hint("airtime"), g.autoAccountId)
    }

    @Test fun learnsACounterpartyAfterTwoConsistentChoices() {
        val once = listOf(obs(Fixtures.PAYBILL_DIRECT_PAY_EARLIER, hint("fun"), 1))
        assertNull(Categorizer(TestBook.accounts, once).guess(query(Fixtures.PAYBILL_DIRECT_PAY)).autoAccountId)
        val twice = once + obs(Fixtures.PAYBILL_DIRECT_PAY_EARLIER, hint("fun"), 2)
        val g = Categorizer(TestBook.accounts, twice).guess(query(Fixtures.PAYBILL_DIRECT_PAY))
        assertEquals(hint("fun"), g.autoAccountId)
        assert(g.reason!!.contains("DIRECT PAY")) { g.reason!! }
    }

    @Test fun yourHistoryBeatsStarterKnowledge() {
        // You file LAKEWOOD HOTEL under Love (dates), not Food.
        val history = listOf(obs(Fixtures.TILL_HOTEL, hint("love"), 1), obs(Fixtures.TILL_HOTEL, hint("love"), 2))
        val g = Categorizer(TestBook.accounts, history).guess(query(Fixtures.TILL_HOTEL))
        assertEquals(hint("love"), g.autoAccountId)
        assertEquals(hint("love"), g.suggestions.first().accountId)
    }

    @Test fun inconsistentHistoryAsksInstead() {
        val history = listOf(
            obs(Fixtures.SEND_NO_PHONE, hint("friends"), 1),
            obs(Fixtures.SEND_NO_PHONE, hint("friends"), 2),
            obs(Fixtures.SEND_NO_PHONE, hint("owed_to_me"), 3),
        )
        val g = Categorizer(TestBook.accounts, history).guess(query(Fixtures.SEND_NO_PHONE))
        assertNull(g.autoAccountId)
        assertEquals(setOf(hint("friends"), hint("owed_to_me")), g.suggestions.take(2).map { it.accountId }.toSet())
    }

    @Test fun moneyInNeverSuggestsAnExpense() {
        val g = Categorizer(TestBook.accounts, emptyList()).guess(query(Fixtures.RECEIVED))
        val expenseIds = TestBook.accounts.filter { it.type == com.morphlabs.morph.core.model.AccountType.EXPENSE }.map { it.id }
        assert(g.suggestions.none { it.accountId in expenseIds })
        assertEquals(3, g.suggestions.size)
    }

    @Test fun habitsHelpWithANewPlace() {
        // Lunch-time tills of ~200 have always been food: a brand-new lunch till leans the same way.
        val history = (1..4).map { obs(Fixtures.TILL_HOTEL, hint("food"), it.toLong()) }
        val q = Query("till:NEW PLACE", "NEW PLACE", TxKind.BUY_GOODS, true, 18000, 13)
        val g = Categorizer(TestBook.accounts, history).guess(q)
        assertEquals(hint("food"), g.suggestions.first().accountId)
        assertNull(g.autoAccountId)
    }

    private fun at(day: Int, hh: Int, mm: Int, amount: Long, who: String, account: Long, kind: TxKind = TxKind.SEND_MONEY) =
        Observation("person:$who", Counterparty.tokens(who), kind, true, amount, hh, account, day.toLong(), 1.0, hh * 60 + mm, false)

    @Test fun morningBusFareToDifferentPeopleIsARoutine() {
        // 6 mornings of Ksh 100 around 7:30 AM to a different conductor each time, all filed as Transport.
        val history = (1..6).map { at(it, 7, 20 + it * 3, 100_00, "CONDUCTOR $it", hint("transport")) } +
            // ...and evening Ksh 100 sends are something else (friends).
            (1..3).map { at(it, 20, 0, 100_00, "FRIEND $it", hint("friends")) }
        val c = Categorizer(TestBook.accounts, history)
        val morning = c.guess(Query("person:NEW GUY", "NEW GUY", TxKind.SEND_MONEY, true, 100_00, 7, 7 * 60 + 35, false))
        assertEquals(hint("transport"), morning.suggestions.first().accountId)
        assert(morning.suggestions.first().reason.contains("7:35 AM")) { morning.suggestions.first().reason }
        assertNull(morning.autoAccountId) // not without autopilot
        assert(morning.confidence > 0.6) { "confidence ${morning.confidence}" }

        val withAutopilot = c.guess(Query("person:NEW GUY", "NEW GUY", TxKind.SEND_MONEY, true, 100_00, 7, 7 * 60 + 35, false), autopilot = 0.6)
        assertEquals(hint("transport"), withAutopilot.autoAccountId)

        val evening = c.guess(Query("person:X", "X", TxKind.SEND_MONEY, true, 100_00, 20, 20 * 60 + 10, false), autopilot = 0.6)
        assertEquals(hint("friends"), evening.suggestions.first().accountId)

        // A very different amount at the same time isn't the bus.
        val big = c.guess(Query("person:Y", "Y", TxKind.SEND_MONEY, true, 2_000_00, 7, 7 * 60 + 30, false), autopilot = 0.6)
        assertNull(big.autoAccountId)
    }

    @Test fun confidenceIsLowWithLittleOrMixedEvidence() {
        val c0 = Categorizer(TestBook.accounts, emptyList())
        val hotel = c0.guess(query(Fixtures.TILL_HOTEL), autopilot = 0.6)
        assert(hotel.confidence < 0.6) { "starter knowledge alone shouldn't be enough: ${hotel.confidence}" }
        assertNull(hotel.autoAccountId)

        val mixed = (1..3).map { at(it, 8, 0, 50_00, "P$it", hint("transport")) } + (4..6).map { at(it, 8, 0, 50_00, "P$it", hint("food")) }
        val g = Categorizer(TestBook.accounts, mixed).guess(Query("person:Z", "Z", TxKind.SEND_MONEY, true, 50_00, 8, 480, false), autopilot = 0.6)
        assertNull(g.autoAccountId)
        assert(g.confidence < 0.6) { "mixed evidence: ${g.confidence}" }
    }

    @Test fun clockAndMidnightWrap() {
        assertEquals(10, Categorizer.minuteGap(5, 1435))
        assertEquals("7:35 AM", Categorizer.clock(455))
        assertEquals("12:05 AM", Categorizer.clock(5))
    }
}
