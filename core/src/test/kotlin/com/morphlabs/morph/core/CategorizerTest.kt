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
}
