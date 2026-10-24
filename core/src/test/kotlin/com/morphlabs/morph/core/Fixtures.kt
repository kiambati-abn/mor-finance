package com.morphlabs.morph.core

/**
 * Real M-Pesa message formats from the owner's phone (Oct 2026), with people's names and
 * numbers replaced. Businesses are kept as-is since they're public tills/paybills.
 * Messages marked "reference" are standard Safaricom formats not yet seen on this phone.
 */
object Fixtures {
    const val SEND_WITH_PHONE = "UJMJOBL4AU Confirmed. Ksh50.00 sent to JANE  WANJIKU 0712000001 on 22/10/26 at 2:22 PM. New M-PESA balance is Ksh1,664.33. Transaction cost, Ksh0.00.  Amount you can transact within the day is 499,367.00. See all your balances now https://saf.cx/iqIzU"
    const val SEND_WITH_FEE = "UJMJ0BJV2I Confirmed. Ksh200.00 sent to peter  kamau 0728000002 on 22/10/26 at 8:50 AM. New M-PESA balance is Ksh104.33. Transaction cost, Ksh7.00.  Amount you can transact within the day is 499,800.00. See all your balances now https://saf.cx/iqIzU"
    const val SEND_NO_PHONE = "UJLJ0BHRYG Confirmed. Ksh30.00 sent to Brian  Otieno on 21/10/26 at 6:29 PM. New M-PESA balance is Ksh641.33. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,624.00. See all your balances now https://saf.cx/kWQpy"
    const val PAYBILL_AIRTEL = "UJLJ0BITD5 confirmed. Ksh50.00 sent to AIRTEL MONEY  for account 254100000003 on 21/10/26 at 9:09 PM New M-PESA balance is Ksh311.33. Transaction cost, Ksh0.00."
    const val PAYBILL_DIRECT_PAY = "UJMJ0BL163 Confirmed. Ksh23.00 sent to DIRECT PAY 05 for account ATL2126525275 on 22/10/26 at 2:08 PM New M-PESA balance is Ksh1,871.33. Transaction cost, Ksh0.00.Amount you can transact within the day is 499,567.00. See all your balances now https://saf.cx/kWQpy"
    const val PAYBILL_DIRECT_PAY_EARLIER = "UJLJ0BIUO3 Confirmed. Ksh23.00 sent to DIRECT PAY 05 for account ATL2125090853 on 21/10/26 at 9:09 PM New M-PESA balance is Ksh361.33. Transaction cost, Ksh0.00.Amount you can transact within the day is 499,344.00. See all your balances now https://saf.cx/kWQpy"
    const val PAYBILL_BANK = "UJMJ0BNJD0 Confirmed. Ksh100.00 sent to IM BANK C2B for account 79668 on 22/10/26 at 10:28 PM New M-PESA balance is Ksh1,514.33. Transaction cost, Ksh0.00.Amount you can transact within the day is 499,217.00. See all your balances now https://saf.cx/kWQpy"
    const val TILL_HOTEL = "UJMJ0BKZN2 Confirmed. Ksh200.00 paid to LAKEWOOD HOTEL. on 22/10/26 at 2:00 PM.New M-PESA balance is Ksh1,904.33. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,600.00. See all your balances now https://saf.cx/kWQpy"
    const val TILL_CARREFOUR = "UJLJ0BI4NY Confirmed. Ksh77.00 paid to CARREFOUR NGARA 04. on 21/10/26 at 7:24 PM.New M-PESA balance is Ksh564.33. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,547.00. See all your balances now https://saf.cx/kWQpy"
    const val TILL_SHOP = "UJLJ0BIBBY Confirmed. Ksh180.00 paid to BLESSED SHOP. on 21/10/26 at 7:29 PM.New M-PESA balance is Ksh384.33. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,367.00. See all your balances now https://saf.cx/kWQpy"
    const val RECEIVED = "UJM7WBDUAH Confirmed.You have received Ksh2,000.00 from Mary Njeri 0111***467 on 22/10/26 at 1:58 PM New M-PESA balance is Ksh2,104.33. Invest & earn daily interest with ZIIDI on https://saf.cx/cF6ir"
    const val SEND_MIDNIGHT = "UJOJ0BSHH0 Confirmed. Ksh50.00 sent to SAMUEL  MUTUA on 24/10/26 at 12:01 AM. New M-PESA balance is Ksh811.33. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,950.00. See all your balances now https://saf.cx/kWQpy"

    // reference formats
    const val WITHDRAW = "UJK1AB2CD3 Confirmed.on 20/10/26 at 5:00 PMWithdraw Ksh1,000.00 from 123456 - MAMA JANE AGENCIES New M-PESA balance is Ksh2,550.00. Transaction cost, Ksh29.00. Amount you can transact within the day is 499,000.00."
    const val AIRTIME = "UJK1AB2CD4 confirmed.You bought Ksh100.00 of airtime on 20/10/26 at 8:15 AM.New M-PESA balance is Ksh2,450.00. Transaction cost, Ksh0.00. Amount you can transact within the day is 498,900.00."
    const val DEPOSIT = "UJK1AB2CD5 Confirmed. On 19/10/26 at 3:00 PM Give Ksh500.00 cash to MAMA JANE AGENCIES New M-PESA balance is Ksh3,579.00. You can now access M-PESA via *334#"
    const val FULIZA = "UJK1AB2CD6 Confirmed. Fuliza M-PESA amount is Ksh 50.00. Access Fee charged Ksh 0.50. Total Fuliza M-PESA outstanding amount is Ksh 50.50 due on 22/11/26. To check daily charges, Dial *334#OK Select Fuliza M-PESA to Query Charges."
    const val FULIZA_REPAY = "UJK1AB2CD7 Confirmed. Ksh 50.50 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 1,000.00. M-PESA balance is Ksh 949.50."
    const val TO_MSHWARI = "UJK1AB2CD8 Confirmed.Ksh500.00 transferred to M-Shwari account on 20/10/26 at 9:00 PM. M-PESA balance is Ksh1,950.00 .New M-Shwari saving account balance is Ksh3,500.00. Transaction cost Ksh.0.00"
    const val REVERSAL = "UJK1AB2CD9 confirmed. Reversal of transaction UJMJ0BKZN2 has been successfully reversed on 22/10/26 at 4:00 PM and Ksh200.00 is credited to your M-PESA account. New M-PESA account balance is Ksh2,104.33."
    const val PROMO = "Dear Customer, enjoy 50% off on data bundles this weekend. Dial *544# to buy."
    const val STRANGE = "UJK9ZZ9ZZ9 Confirmed. Ksh75.00 has been moved in a new way on 20/10/26 at 1:00 PM. New M-PESA balance is Ksh925.00."
}
