package com.morphlabs.morph.core

import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.sms.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MpesaParserTest {

    private fun parse(s: String) = MpesaParser.parse(s) ?: error("did not parse: $s")

    @Test fun sendWithPhone() {
        val t = parse(Fixtures.SEND_WITH_PHONE)
        assertEquals("UJMJOBL4AU", t.code)
        assertEquals(TxKind.SEND_MONEY, t.kind)
        assertEquals(5000L, t.amount)
        assertEquals(0L, t.fee)
        assertEquals(166433L, t.balance)
        assertEquals("JANE WANJIKU", t.counterparty)
        assertEquals("0712000001", t.phone)
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 22), t.time)
        assertEquals("person:712000001", t.counterpartyKey)
    }

    @Test fun sendWithFeeAndLowercaseName() {
        val t = parse(Fixtures.SEND_WITH_FEE)
        assertEquals(20000L, t.amount)
        assertEquals(700L, t.fee)
        assertEquals("peter kamau", t.counterparty)
        assertEquals(LocalDateTime.of(2026, 10, 22, 8, 50), t.time)
    }

    @Test fun sendWithoutPhoneKeysOnName() {
        val t = parse(Fixtures.SEND_NO_PHONE)
        assertEquals("Brian Otieno", t.counterparty)
        assertNull(t.phone)
        assertEquals("person:BRIAN OTIENO", t.counterpartyKey)
    }

    @Test fun paybills() {
        val airtel = parse(Fixtures.PAYBILL_AIRTEL)
        assertEquals(TxKind.PAYBILL, airtel.kind)
        assertEquals("AIRTEL MONEY", airtel.counterparty)
        assertEquals("254100000003", airtel.accountRef)
        assertEquals(31133L, airtel.balance)

        val dp = parse(Fixtures.PAYBILL_DIRECT_PAY)
        assertEquals("DIRECT PAY 05", dp.counterparty)
        assertEquals("ATL2126525275", dp.accountRef)
        assertEquals(2300L, dp.amount)
        assertEquals("paybill:DIRECT PAY", dp.counterpartyKey)

        assertEquals("IM BANK C2B", parse(Fixtures.PAYBILL_BANK).counterparty)
    }

    @Test fun tills() {
        val h = parse(Fixtures.TILL_HOTEL)
        assertEquals(TxKind.BUY_GOODS, h.kind)
        assertEquals("LAKEWOOD HOTEL", h.counterparty)
        assertEquals(190433L, h.balance)
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 0), h.time)
        assertEquals("till:CARREFOUR NGARA", parse(Fixtures.TILL_CARREFOUR).counterpartyKey)
        assertEquals("BLESSED SHOP", parse(Fixtures.TILL_SHOP).counterparty)
    }

    @Test fun received() {
        val t = parse(Fixtures.RECEIVED)
        assertEquals(TxKind.RECEIVED, t.kind)
        assertEquals(200000L, t.amount)
        assertEquals("Mary Njeri", t.counterparty)
        assertEquals("0111***467", t.phone)
        assertEquals(210433L, t.balance)
        assertEquals("person:MARY NJERI", t.counterpartyKey)
    }

    @Test fun midnightIsZeroHour() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 0, 1), parse(Fixtures.SEND_MIDNIGHT).time)
    }

    @Test fun referenceFormats() {
        parse(Fixtures.WITHDRAW).let {
            assertEquals(TxKind.WITHDRAW, it.kind); assertEquals(100000L, it.amount); assertEquals(2900L, it.fee)
            assertEquals("MAMA JANE AGENCIES", it.counterparty); assertEquals(LocalDateTime.of(2026, 10, 20, 17, 0), it.time)
        }
        parse(Fixtures.AIRTIME).let { assertEquals(TxKind.AIRTIME, it.kind); assertEquals(10000L, it.amount) }
        parse(Fixtures.DEPOSIT).let { assertEquals(TxKind.DEPOSIT, it.kind); assertEquals(50000L, it.amount); assertEquals(357900L, it.balance) }
        parse(Fixtures.FULIZA).let { assertEquals(TxKind.FULIZA_DRAW, it.kind); assertEquals(5000L, it.amount); assertEquals(50L, it.fee) }
        parse(Fixtures.FULIZA_REPAY).let { assertEquals(TxKind.FULIZA_REPAY, it.kind); assertEquals(5050L, it.amount); assertEquals(94950L, it.balance) }
        parse(Fixtures.TO_MSHWARI).let { assertEquals(TxKind.TO_MSHWARI, it.kind); assertEquals(50000L, it.amount); assertEquals(195000L, it.balance) }
        parse(Fixtures.REVERSAL).let { assertEquals(TxKind.REVERSAL, it.kind); assertEquals(20000L, it.amount); assertEquals("UJMJ0BKZN2", it.reversedCode) }
    }

    @Test fun promoIsIgnoredAndStrangeIsKept() {
        assertNull(MpesaParser.parse(Fixtures.PROMO))
        val s = parse(Fixtures.STRANGE)
        assertEquals(TxKind.UNKNOWN, s.kind)
        assertEquals(7500L, s.amount)
        assertEquals(92500L, s.balance)
    }

    @Test fun senderCheck() {
        assert(MpesaParser.isMpesaSender("MPESA"))
        assert(MpesaParser.isMpesaSender("M-PESA"))
        assert(!MpesaParser.isMpesaSender("Safaricom"))
    }

    @Test fun withdrawnWording() {
        val t = parse("UJP1AB2CD3 Confirmed. You have withdrawn Ksh2,000.00 from 556677 - JANE AGENCIES KAWANGWARE on 25/10/26 at 10:15 AM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh29.00.")
        assertEquals(TxKind.WITHDRAW, t.kind)
        assertEquals(200000L, t.amount)
        assertEquals(2900L, t.fee)
        assertEquals("JANE AGENCIES KAWANGWARE", t.counterparty)
        assertEquals(LocalDateTime.of(2026, 10, 25, 10, 15), t.time)
    }
}
