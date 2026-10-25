package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.whenText
import java.time.LocalDate

@Composable
fun AmountField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        prefix = { Text("Ksh ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Accounts grouped the way people think about them: spending first, then savings, debts, income. */
fun pickOrder(accounts: List<Account>, outgoing: Boolean?): List<Pair<AccountGroup, List<Account>>> {
    val order = if (outgoing == false) listOf(AccountGroup.INCOME, AccountGroup.OWED_TO_ME, AccountGroup.DEBT, AccountGroup.LONG_TERM_DEBT, AccountGroup.SAVINGS, AccountGroup.WALLET)
    else listOf(AccountGroup.FIXED, AccountGroup.DISCRETIONARY, AccountGroup.SAVINGS, AccountGroup.DEBT, AccountGroup.LONG_TERM_DEBT, AccountGroup.OWED_TO_ME, AccountGroup.WALLET, AccountGroup.INCOME)
    val allowed = accounts.filter { it.pickable }.filter {
        when (outgoing) {
            true -> it.type != AccountType.INCOME
            false -> it.type != AccountType.EXPENSE
            null -> true
        }
    }
    return order.mapNotNull { g -> allowed.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountGrid(accounts: List<Account>, outgoing: Boolean?, exclude: Set<Long> = emptySet(), onPick: (Account) -> Unit) {
    Column {
        for ((_, list) in pickOrder(accounts, outgoing)) {
            for ((label, shown) in list.filter { it.id !in exclude }.groupBy { it.typeLabel }) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in shown) OutlinedButton(onClick = { onPick(a) }, shape = RoundedCornerShape(14.dp)) { Text(a.label) }
                }
            }
        }
    }
}

@Composable
fun AccountPickerDialog(title: String, accounts: List<Account>, outgoing: Boolean?, exclude: Set<Long> = emptySet(), onDismiss: () -> Unit, onPick: (Account) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { AccountGrid(accounts, outgoing, exclude, onPick) } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** For cash and anything without an SMS: spent, received, or moved between your own accounts. */
@Composable
fun AddEntryDialog(repo: MorphRepo, s: Snapshot, onDismiss: () -> Unit) {
    var kind by remember { mutableIntStateOf(0) } // 0 spent, 1 received, 2 moved
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cash = s.account(SystemRole.CASH)
    var from by remember { mutableStateOf<Account?>(cash) }
    var to by remember { mutableStateOf<Account?>(null) }
    var yesterday by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Spent", "Received", "Transfer").forEachIndexed { i, l ->
                        FilterChip(selected = kind == i, onClick = {
                            kind = i
                            when (i) { 0 -> { from = cash; to = null }; 1 -> { from = null; to = cash }; else -> { from = cash; to = null } }
                        }, label = { Text(l) })
                    }
                }
                Gap(8)
                AmountField(amount, { amount = it }, "Amount", imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                Gap(8)
                OutlinedTextField(note, { note = it }, label = { Text("What for? (e.g. lunch, fare)") }, singleLine = true, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys, modifier = Modifier.fillMaxWidth())
                Gap(8)
                PickRow(if (kind == 1) "From (source)" else "From", from) { picking = "from" }
                PickRow(if (kind == 0) "On (category)" else "To", to) { picking = "to" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text("Today") })
                    Text("  ")
                    FilterChip(selected = yesterday, onClick = { yesterday = true }, label = { Text("Yesterday") })
                }
            }
        },
        confirmButton = {
            val cents = Money.parse(amount)
            TextButton(enabled = cents != null && cents > 0 && from != null && to != null && from != to, onClick = {
                val now = System.currentTimeMillis()
                val ts = if (yesterday) now - 24 * 3600 * 1000 else now
                val f = from!!
                val t = to!!
                val desc = note.ifBlank { "${f.name} → ${t.name}" }
                Thread { repo.addManual(ts, desc, cents!!, f.id, t.id) }.start()
                onDismiss()
            }) { Text("Save +1⭐") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picking) {
        "from" -> AccountPickerDialog(
            "From", s.accounts.filter {
                when (kind) {
                    0 -> it.type == AccountType.ASSET || it.type == AccountType.LIABILITY
                    1 -> it.type == AccountType.INCOME || it.type == AccountType.LIABILITY || it.group == AccountGroup.OWED_TO_ME
                    else -> true // a transfer can come from anywhere
                }
            },
            null, onDismiss = { picking = null },
        ) { from = it; picking = null }
        "to" -> AccountPickerDialog(
            "To", s.accounts.filter {
                when (kind) {
                    0 -> it.type == AccountType.EXPENSE
                    1 -> it.type == AccountType.ASSET
                    else -> true
                }
            },
            null, onDismiss = { picking = null },
        ) { to = it; picking = null }
    }
}

@Composable
private fun PickRow(label: String, a: Account?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(a?.label ?: "Choose…", fontWeight = FontWeight.SemiBold, color = if (a == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
    HorizontalDivider()
}

@Composable
fun EntryDialog(repo: MorphRepo, s: Snapshot, e: Entry, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(e.note ?: "") }
    val cats = s.categories(e)
    fun saveNote() { if (note.trim() != (e.note ?: "")) { val n = note; Thread { repo.setNote(e.id, n) }.start() } }
    AlertDialog(
        onDismissRequest = { saveNote(); onDismiss() },
        title = { Text((if (s.outgoing(e)) "−" else "+") + Money.format(s.amount(e))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(e.description, style = MaterialTheme.typography.titleMedium)
                Text(whenText(e.timestamp, s.zone), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap()
                if (cats.size > 1) {
                    Text("Split between:", fontWeight = FontWeight.SemiBold)
                    for (l in Posting.categorySide(e, s.structural)) Text("  ${s.byId[l.accountId]?.label}  ${Money.format(kotlin.math.abs(l.amount))}")
                } else Text("Filed under: ${cats.firstOrNull()?.label ?: "—"}  (${e.status.name.lowercase().replace('_', ' ')})")
                Gap(8)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys, modifier = Modifier.fillMaxWidth())
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.kind == com.morphlabs.morph.core.model.TxKind.WITHDRAW) OutlinedButton(onClick = { cash = true }) { Text("💵 Spread this cash") }
                    if (e.source == EntrySource.MANUAL && e.kind == null) OutlinedButton(onClick = { editing = true }) { Text("Edit") }
                    if (e.source != EntrySource.SYSTEM) OutlinedButton(onClick = { splitting = true }) { Text("Split") }
                }
                if (e.code != null) Text("M-Pesa code: ${e.code}", style = MaterialTheme.typography.bodySmall)
                Gap()
                Text("Journal", style = MaterialTheme.typography.labelLarge)
                for (l in e.lines) Text(
                    "${if (l.amount > 0) "Dr" else "Cr"}  ${s.byId[l.accountId]?.name}  ${Money.format(kotlin.math.abs(l.amount))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (e.smsBody != null) {
                    Gap()
                    Text("SMS", style = MaterialTheme.typography.labelLarge)
                    Text(e.smsBody!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("The amount comes from M-Pesa, so it can't be changed here. Use Split to spread it over several accounts.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (e.source != EntrySource.SYSTEM) TextButton(onClick = { picking = true }) { Text("Change category") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { saveNote(); onDismiss() }) { Text("Close") }
            }
        },
    )
    if (picking) AccountPickerDialog("What was it?", s.accounts, s.outgoing(e), onDismiss = { picking = false }) { acc ->
        saveNote()
        Thread { repo.categorize(e.id, acc.id) }.start()
        picking = false
        onDismiss()
    }
    if (editing) EditManualDialog(repo, s, e, onDismiss = { editing = false; onDismiss() })
    if (splitting) SplitDialog(repo, s, e, onDismiss = { splitting = false; onDismiss() })
    if (cash) CashSpreadDialog(repo, s, e, s.cashToSpread.firstOrNull { it.first.id == e.id }?.second ?: s.amount(e)) { cash = false; onDismiss() }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this transaction?") },
        text = { Text(if (e.source == EntrySource.SMS) "It came from an SMS, so your M-Pesa balance in Morph will no longer match. Usually \"Change category\" is what you want." else "This can't be undone.") },
        confirmButton = { TextButton(onClick = { Thread { repo.deleteEntry(e.id) }.start(); confirmDelete = false; onDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

/** Hand-added transactions can change everything: amount, what for, from, to and day. */
@Composable
private fun EditManualDialog(repo: MorphRepo, s: Snapshot, e: Entry, onDismiss: () -> Unit) {
    val toLine = e.lines.firstOrNull { it.amount > 0 }
    val fromLine = e.lines.firstOrNull { it.amount < 0 }
    var amount by remember { mutableStateOf(Money.format(toLine?.amount ?: 0, withCurrency = false).replace(",", "")) }
    var desc by remember { mutableStateOf(e.description) }
    var from by remember { mutableStateOf(fromLine?.let { s.byId[it.accountId] }) }
    var to by remember { mutableStateOf(toLine?.let { s.byId[it.accountId] }) }
    var daysBack by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AmountField(amount, { amount = it }, "Amount", imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                Gap(8)
                OutlinedTextField(desc, { desc = it }, label = { Text("What for?") }, singleLine = true, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys, modifier = Modifier.fillMaxWidth())
                Gap(8)
                PickRow("From", from) { picking = "from" }
                PickRow("To", to) { picking = "to" }
                Gap(8)
                Text("Move the date", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((d, l) in listOf(0 to "Keep", 1 to "−1 day", 2 to "−2 days", 7 to "−1 week")) {
                        FilterChip(selected = daysBack == d, onClick = { daysBack = d }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = {
            val cents = Money.parse(amount)
            TextButton(enabled = cents != null && cents > 0 && from != null && to != null && from != to && desc.isNotBlank(), onClick = {
                val f = from!!.id; val t = to!!.id; val d = desc.trim()
                val ts = e.timestamp - daysBack * 24L * 3600 * 1000
                Thread { repo.updateManual(e.id, ts, d, cents!!, f, t) }.start()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    when (picking) {
        "from" -> AccountPickerDialog("From", s.accounts, null, onDismiss = { picking = null }) { from = it; picking = null }
        "to" -> AccountPickerDialog("To", s.accounts, null, onDismiss = { picking = null }) { to = it; picking = null }
    }
}

/** Spread one transaction over several accounts. The parts must add up, so balances never change. */
@Composable
fun SplitDialog(repo: MorphRepo, s: Snapshot, e: Entry, onSorted: ((com.morphlabs.morph.core.game.Game.Reward) -> Unit)? = null, onDismiss: () -> Unit) {
    val total = s.amount(e)
    val existing = Posting.categorySide(e, s.structural)
    // The first account always takes "the rest": you type the others, it adjusts by itself.
    val parts = remember {
        androidx.compose.runtime.mutableStateListOf<Pair<Account, String>>().apply {
            for (l in existing) s.byId[l.accountId]?.takeIf { it.pickable }?.let { add(it to Money.format(kotlin.math.abs(l.amount), withCurrency = false).replace(",", "")) }
        }
    }
    var adding by remember { mutableStateOf(parts.isEmpty()) }
    val others = parts.drop(1).sumOf { Money.parse(it.second) ?: 0L }
    val rest = total - others
    val ok = parts.isNotEmpty() && rest >= 0 && (parts.size == 1 || rest > 0 || others == total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${Money.format(total)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (parts.isEmpty()) Text("Add the first account; it takes the whole amount. Add more and type their amounts; the first one adjusts by itself.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                parts.forEachIndexed { i, (a, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(a.label, modifier = Modifier.weight(1f))
                        if (i == 0) Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(Money.format(rest), fontWeight = FontWeight.Bold,
                                color = if (rest < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Text("the rest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else AmountField(v, { parts[i] = a to it }, "", modifier = Modifier.weight(1f), imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                        TextButton(onClick = { parts.removeAt(i) }) { Text("✕") }
                    }
                }
                TextButton(onClick = { adding = true }) { Text(if (parts.isEmpty()) "+ Choose the first account" else "+ Add another account") }
                if (rest < 0) Text("The others add up to ${Money.format(-rest)} more than the total.", color = MaterialTheme.colorScheme.error)
                else if (parts.size > 1) Text("Adds up ✓", color = com.morphlabs.morph.ui.theme.LocalMorph.current.good)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val list = parts.mapIndexed { i, (a, v) -> a.id to (if (i == 0) rest else Money.parse(v) ?: 0L) }.filter { it.second > 0 }
                val pending = e.status == com.morphlabs.morph.core.model.EntryStatus.NEEDS_REVIEW
                Thread {
                    if (pending) {
                        val r = repo.splitAndSort(e.id, list)
                        onSorted?.invoke(r)
                    } else repo.split(e.id, list)
                }.start()
                onDismiss()
            }) { Text("Save split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (adding) AccountPickerDialog(if (parts.isEmpty()) "First account (takes the rest)" else "Add another", s.accounts, s.outgoing(e),
        exclude = parts.map { it.first.id }.toSet(), onDismiss = { adding = false }) { a ->
        parts.add(a to "")
        adding = false
    }
}

private val emojiChoices = listOf("🍔", "🛒", "🚌", "🏠", "💡", "📶", "💊", "📚", "👨‍👩‍👧", "❤️", "🍻", "🎉", "🎧", "🛍️", "🚀", "🤲", "✨", "💈", "🐷", "💰", "🤝", "🏦", "📱", "💵", "🎓", "🇰🇪", "🙏", "💼", "🛠️", "🎁", "⛪", "🐶", "⚽", "🎮", "☕", "🚗", "👶", "🧾")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditDialog(repo: MorphRepo, s: Snapshot, original: Account?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var emoji by remember { mutableStateOf(original?.emoji ?: "✨") }
    var group by remember { mutableStateOf(original?.group ?: AccountGroup.DISCRETIONARY) }
    var section by remember { mutableStateOf(original?.section) }
    var newType by remember { mutableStateOf(false) }
    // Your own types: every name in use, with the group it belongs to.
    val customTypes = remember(s) { s.accounts.mapNotNull { a -> a.section?.let { it to a.group } }.distinct() }
    var budget by remember { mutableStateOf(original?.budget?.takeIf { it > 0 }?.let { Money.format(it, withCurrency = false) } ?: "") }
    var period by remember { mutableStateOf(original?.period ?: BudgetPeriod.MONTHLY) }
    var archived by remember { mutableStateOf(original?.archived ?: false) }
    val current = original?.let { s.balance(it) }
    var balance by remember { mutableStateOf("") }
    val isSystem = original?.role != null
    val budgetLabel = when (group.type) {
        AccountType.EXPENSE -> "Budget (limit)"
        AccountType.LIABILITY -> "Repay target"
        AccountType.INCOME -> "Expected income"
        else -> "Save target"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New account" else "Edit ${original.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys, modifier = Modifier.fillMaxWidth())
                Gap(8)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (e in emojiChoices) Text(
                        e, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { emoji = e }.padding(4.dp),
                        color = if (e == emoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text("Chosen: $emoji")
                if (!isSystem) {
                    Gap(8)
                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (g in AccountGroup.entries.filter { it != AccountGroup.SYSTEM }) {
                            FilterChip(selected = group == g && section == null, onClick = { group = g; section = null }, label = { Text(g.label) })
                        }
                        for ((name, g) in customTypes) {
                            FilterChip(selected = section == name, onClick = { group = g; section = name }, label = { Text("$name · ${g.label}") })
                        }
                        FilterChip(selected = false, onClick = { newType = true }, label = { Text("+ New type") })
                    }
                }
                if (group != AccountGroup.SYSTEM && group != AccountGroup.WALLET) {
                    Gap(8)
                    AmountField(budget, { budget = it }, budgetLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = period == BudgetPeriod.DAILY, onClick = { period = BudgetPeriod.DAILY }, label = { Text("Per day") })
                        FilterChip(selected = period == BudgetPeriod.MONTHLY, onClick = { period = BudgetPeriod.MONTHLY }, label = { Text("Per month") })
                    }
                }
                if (original != null && (group.type == AccountType.ASSET || group.type == AccountType.LIABILITY)) {
                    Gap(8)
                    Text("Balance now: ${Money.format(current ?: 0)}", fontWeight = FontWeight.SemiBold)
                    AmountField(balance, { balance = it }, "Correct the balance to…")
                }
                if (original != null && !isSystem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Archived (hidden)", modifier = Modifier.weight(1f))
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val cents = Money.parse(budget) ?: 0L
                val acc = (original ?: Account(id = 0, name = name, emoji = emoji, group = group, sortOrder = s.accounts.size)).copy(
                    name = name.trim(), emoji = emoji, group = group, section = section, budget = cents,
                    period = if (cents > 0) period else BudgetPeriod.NONE, archived = archived,
                )
                val target = Money.parse(balance)
                Thread {
                    val id = repo.saveAccount(acc)
                    if (target != null) repo.setBalanceNow(id, target)
                }.start()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (newType) NewTypeDialog(onDismiss = { newType = false }) { n, g -> section = n; group = g; newType = false }
}

/** Your own type name, filed under one of the basic groups so the maths stays right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTypeDialog(onDismiss: () -> Unit, onCreate: (String, AccountGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(AccountGroup.DISCRETIONARY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New type") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Church, Business, Farm") }, singleLine = true, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys, modifier = Modifier.fillMaxWidth())
                Gap(8)
                Text("It works like…", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (g in AccountGroup.entries.filter { it != AccountGroup.SYSTEM }) {
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), group) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MoveBudgetDialog(repo: MorphRepo, s: Snapshot, toAccount: Account, periodKey: String, envelopes: List<com.morphlabs.morph.core.budget.Envelope>, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var from by remember { mutableStateOf<Account?>(null) }
    val sources = envelopes.filter { it.account.id != toAccount.id && it.period == toAccount.period && it.isLimit && it.left > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move budget to ${toAccount.label}") },
        text = {
            Column {
                Text("Real money doesn't move. You're changing the plan for this ${if (toAccount.period == BudgetPeriod.DAILY) "day" else "month"}.", style = MaterialTheme.typography.bodySmall)
                Gap(8)
                AmountField(amount, { amount = it }, "Amount")
                Gap(8)
                Text("Take it from", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(sources) { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { from = e.account }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text((if (from?.id == e.account.id) "● " else "○ ") + e.account.label, modifier = Modifier.weight(1f))
                            Text("${Money.format(e.left)} left", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (sources.isEmpty()) Text("No other envelope has money left to give.")
            }
        },
        confirmButton = {
            val cents = Money.parse(amount)
            TextButton(enabled = cents != null && cents > 0 && from != null, onClick = {
                val f = from!!.id
                Thread { repo.moveBudget(periodKey, f, toAccount.id, cents!!) }.start()
                onDismiss()
            }) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun yesterdayOf(d: LocalDate): LocalDate = d.minusDays(1)
