package com.morphlabs.morph.core.model

/** The five accounting families. Assets and expenses grow with debits; the rest grow with credits. */
enum class AccountType {
    ASSET, LIABILITY, INCOME, EXPENSE, EQUITY;

    val debitNormal: Boolean get() = this == ASSET || this == EXPENSE
}

/** Fun wording in the app and the Money Dojo (a setting in Me, off by default). Account types always use standard names. */
object Naming {
    @Volatile var playful: Boolean = false

    fun pick(fun_: String, plain: String): String = if (playful) fun_ else plain
}

/** How an account is shown and reasoned about in the app. Users can change it. */
enum class AccountGroup(val label: String, val type: AccountType, private val alsoKnownAs: String = "") {
    WALLET("Wallets", AccountType.ASSET),
    SAVINGS("Savings", AccountType.ASSET),
    OWED_TO_ME("Owed to me", AccountType.ASSET),
    /** Due within about a year: Fuliza, Hustler Fund, friends. */
    DEBT("Short-term liabilities", AccountType.LIABILITY, "debts loans"),
    /** Paid back over years: HELB, mortgages, car and long bank loans. */
    LONG_TERM_DEBT("Long-term liabilities", AccountType.LIABILITY, "debts loans"),
    INCOME("Income", AccountType.INCOME),
    FIXED("Fixed expenses", AccountType.EXPENSE),
    DISCRETIONARY("Discretionary", AccountType.EXPENSE),
    SYSTEM("System", AccountType.EQUITY);

    val searchText: String get() = "$label $alsoKnownAs"
}

enum class BudgetPeriod(val label: String) { NONE("No budget"), DAILY("Daily"), MONTHLY("Monthly") }

/** Accounts the app needs to find on its own (e.g. where M-Pesa fees go). One account per role. */
enum class SystemRole {
    MPESA, CASH, MSHWARI, FULIZA, TRANSACTION_COSTS, WITHDRAWAL_CHARGES, FULIZA_CHARGES,
    UNCATEGORIZED, OPENING_BALANCE
}

data class Account(
    val id: Long,
    val name: String,
    val emoji: String,
    val group: AccountGroup,
    /** Planned amount per [period], in cents. For expenses it's a limit; for savings, debts and income it's a target. */
    val budget: Long = 0,
    val period: BudgetPeriod = BudgetPeriod.NONE,
    val role: SystemRole? = null,
    /** A built-in tag ("food", "transport", ...) that lets Morph's starter knowledge find this account. */
    val hint: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** Your own type name ("Church", "Business"), shown instead of the group's name. The group still decides the maths. */
    val section: String? = null,
    /** An extra per-day limit on top of a monthly budget (0 = none), e.g. Ksh 12,000 a month but at most Ksh 500 a day. */
    val dailyCap: Long = 0,
) {
    /** The per-day amount, whether it's the budget itself or a cap on a monthly one (0 = none). */
    val perDay: Long get() = if (period == BudgetPeriod.DAILY) budget else dailyCap

    /** The per-month amount (0 = none). */
    val perMonth: Long get() = if (period == BudgetPeriod.MONTHLY) budget else 0

    /** Builds the budget fields from "per month" and "per day" amounts; either or both may be 0. */
    fun withBudget(perMonth: Long, perDay: Long): Account = when {
        perMonth > 0 -> copy(budget = perMonth, period = BudgetPeriod.MONTHLY, dailyCap = perDay.coerceAtLeast(0))
        perDay > 0 -> copy(budget = perDay, period = BudgetPeriod.DAILY, dailyCap = 0)
        else -> copy(budget = 0, period = BudgetPeriod.NONE, dailyCap = 0)
    }

    val typeLabel: String get() = section ?: group.label

    val type: AccountType get() = group.type

    /** Accounts a transaction can be filed under. Uncategorized and Opening Balance are bookkeeping only. */
    val pickable: Boolean get() = !archived && role != SystemRole.UNCATEGORIZED && role != SystemRole.OPENING_BALANCE

    val label: String get() = "$emoji $name"

    /** Search matches the name, the type, and words like "debt" for liabilities. */
    fun matches(needle: String): Boolean {
        val n = needle.trim().lowercase()
        return n.isEmpty() || listOf(name, typeLabel, group.searchText).any { it.lowercase().contains(n) }
    }
}
