package com.morphlabs.morph.core.model

/** The five accounting families. Assets and expenses grow with debits; the rest grow with credits. */
enum class AccountType {
    ASSET, LIABILITY, INCOME, EXPENSE, EQUITY;

    val debitNormal: Boolean get() = this == ASSET || this == EXPENSE
}

/** Fun names on or off for the whole app (a setting in Me). Plain names are always used for search too. */
object Naming {
    @Volatile var playful: Boolean = true

    fun pick(fun_: String, plain: String): String = if (playful) fun_ else plain
}

/** How an account is shown and reasoned about in the app. Users can change it. */
enum class AccountGroup(val plainLabel: String, val type: AccountType, val funLabel: String) {
    WALLET("Wallets", AccountType.ASSET, "Pockets"),
    SAVINGS("Savings", AccountType.ASSET, "Stash"),
    OWED_TO_ME("Owed to me", AccountType.ASSET, "IOUs to chase"),
    DEBT("Debts", AccountType.LIABILITY, "Madeni Monsters"),
    INCOME("Income", AccountType.INCOME, "Money Rain"),
    FIXED("Fixed expenses", AccountType.EXPENSE, "Must-pays"),
    DISCRETIONARY("Discretionary", AccountType.EXPENSE, "Treats & extras"),
    SYSTEM("System", AccountType.EQUITY, "Behind the scenes");

    val label: String get() = Naming.pick(funLabel, plainLabel)
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
) {
    val typeLabel: String get() = section ?: group.label

    val type: AccountType get() = group.type

    /** Accounts a transaction can be filed under. Uncategorized and Opening Balance are bookkeeping only. */
    val pickable: Boolean get() = !archived && role != SystemRole.UNCATEGORIZED && role != SystemRole.OPENING_BALANCE

    val label: String get() = "$emoji $name"

    /** Search matches the name and both the fun and plain type names. */
    fun matches(needle: String): Boolean {
        val n = needle.trim().lowercase()
        return n.isEmpty() || listOf(name, typeLabel, group.plainLabel, group.funLabel).any { it.lowercase().contains(n) }
    }
}
