package com.morphlabs.morph.core

import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.Line
import com.morphlabs.morph.core.model.SystemRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiabilitiesTest {
    @Test fun helbIsLongTermAndBothCountAsWhatYouOwe() {
        val helb = TestBook.accounts.first { it.hint == "helb" }
        val fuliza = TestBook.accounts.first { it.role == SystemRole.FULIZA }
        assertEquals(AccountGroup.LONG_TERM_DEBT, helb.group)
        assertEquals(AccountGroup.DEBT, fuliza.group)
        assertTrue(helb.type == AccountType.LIABILITY && fuliza.type == AccountType.LIABILITY)
        // Owing 50,000 HELB and 1,000 Fuliza: both show up as liabilities.
        val opening = TestBook.role(SystemRole.OPENING_BALANCE)
        val entries = listOf(helb.id to 5_000_000L, fuliza.id to 100_000L).mapIndexed { i, (id, amt) ->
            Entry(id = i + 1L, timestamp = 0, description = "owe", lines = listOf(Line(id, -amt), Line(opening, amt)),
                status = EntryStatus.CONFIRMED, source = EntrySource.SYSTEM)
        }
        assertEquals(5_100_000L, Balances.netWorth(TestBook.accounts, Balances.raw(entries)).liabilities)
        // Search still finds them by "debt".
        assertTrue(helb.matches("debt") && helb.matches("long-term"))
    }
}
