package com.morphlabs.morph.core.defaults

import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountGroup.DEBT
import com.morphlabs.morph.core.model.AccountGroup.LONG_TERM_DEBT
import com.morphlabs.morph.core.model.AccountGroup.DISCRETIONARY
import com.morphlabs.morph.core.model.AccountGroup.FIXED
import com.morphlabs.morph.core.model.AccountGroup.INCOME
import com.morphlabs.morph.core.model.AccountGroup.OWED_TO_ME
import com.morphlabs.morph.core.model.AccountGroup.SAVINGS
import com.morphlabs.morph.core.model.AccountGroup.SYSTEM
import com.morphlabs.morph.core.model.AccountGroup.WALLET
import com.morphlabs.morph.core.model.SystemRole

/**
 * The starter chart of accounts every new install gets, based on the Notion journal Morph replaced.
 * Everything here can be renamed, re-budgeted, archived or added to in the app.
 */
object DefaultAccounts {

    private data class Spec(val name: String, val emoji: String, val group: AccountGroup, val hint: String? = null, val role: SystemRole? = null)

    private val specs = listOf(
        Spec("M-Pesa", "📱", WALLET, "mpesa", SystemRole.MPESA),
        Spec("Cash", "💵", WALLET, "cash", SystemRole.CASH),
        Spec("Bank", "🏦", WALLET, "bank"),
        Spec("Airtel Money", "📲", WALLET, "airtel"),
        Spec("M-Shwari", "🐷", SAVINGS, "mshwari", SystemRole.MSHWARI),
        Spec("Savings", "💰", SAVINGS, "savings"),
        Spec("Chama", "🤝", SAVINGS, "chama"),
        Spec("Friends owe me", "🧾", OWED_TO_ME, "owed_to_me"),

        Spec("Fuliza", "⚡", DEBT, "fuliza", SystemRole.FULIZA),
        Spec("Hustler Fund", "🇰🇪", DEBT, "hustler"),
        Spec("HELB", "🎓", LONG_TERM_DEBT, "helb"),
        Spec("I owe friends", "🙏", DEBT, "i_owe"),

        Spec("Salary", "💼", INCOME, "salary"),
        Spec("Business & hustles", "🛠️", INCOME, "business"),
        Spec("Gifts received", "🎁", INCOME, "gifts"),
        Spec("Other income", "➕", INCOME, "income_other"),

        Spec("Rent", "🏠", FIXED, "rent"),
        Spec("Food & take out", "🍔", FIXED, "food"),
        Spec("Groceries", "🛒", FIXED, "groceries"),
        Spec("Household items", "🧺", FIXED, "household"),
        Spec("Transport", "🚌", FIXED, "transport"),
        Spec("Airtime & internet", "📶", FIXED, "airtime"),
        Spec("Bills (power & water)", "💡", FIXED, "bills"),
        Spec("School", "📚", FIXED, "school"),
        Spec("Health", "💊", FIXED, "health"),
        Spec("Family", "👨‍👩‍👧", FIXED, "family"),
        Spec("Haircut & personal care", "💈", FIXED, "personal"),
        Spec("Transaction costs", "🧾", FIXED, "tx_costs", SystemRole.TRANSACTION_COSTS),
        Spec("Withdrawal charges", "🏧", FIXED, "withdraw_charges", SystemRole.WITHDRAWAL_CHARGES),
        Spec("Fuliza charges", "⚡", FIXED, "fuliza_charges", SystemRole.FULIZA_CHARGES),

        Spec("Love", "❤️", DISCRETIONARY, "love"),
        Spec("Friends & social", "🍻", DISCRETIONARY, "friends"),
        Spec("Fun & travel", "🎉", DISCRETIONARY, "fun"),
        Spec("Gadgets & accessories", "🎧", DISCRETIONARY, "gadgets"),
        Spec("Personal shopping", "🛍️", DISCRETIONARY, "shopping"),
        Spec("Projects", "🚀", DISCRETIONARY, "projects"),
        Spec("Giving & sharing", "🤲", DISCRETIONARY, "giving"),
        Spec("Miscellaneous", "✨", DISCRETIONARY, "misc"),

        Spec("Uncategorized", "❔", SYSTEM, null, SystemRole.UNCATEGORIZED),
        Spec("Opening balance", "🏁", SYSTEM, null, SystemRole.OPENING_BALANCE),
    )

    /** Accounts without ids (0); storage assigns them. */
    fun all(): List<Account> = specs.mapIndexed { i, s ->
        Account(id = 0, name = s.name, emoji = s.emoji, group = s.group, role = s.role, hint = s.hint, sortOrder = i)
    }
}
