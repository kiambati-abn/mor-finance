package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.ui.theme.LocalMorph

/** Search text, a type filter and an optional "can I afford it?" amount, shared by both budget screens. */
@Stable
class FindState {
    var query by mutableStateOf("")
    var filter by mutableStateOf("all")
    var afford by mutableStateOf("")
    val affordCents: Long? get() = Money.parse(afford)?.takeIf { it > 0 }

    /** [low]: whether this one is running low or over. */
    fun matches(a: Account, low: Boolean): Boolean {
        val q = query.trim().lowercase()
        val byText = a.matches(q)
        val byType = when (filter) {
            "spend" -> a.type == AccountType.EXPENSE
            "save" -> a.type == AccountType.ASSET || a.type == AccountType.LIABILITY
            "low" -> low
            else -> true
        }
        return byText && byType
    }
}

@Composable
fun FindBar(f: FindState) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                f.query, { f.query = it }, singleLine = true, label = { Text("🔍 Find") }, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys,
                modifier = Modifier.weight(1.3f),
            )
            OutlinedTextField(
                f.afford, { v -> f.afford = v.filter { it.isDigit() || it == '.' || it == ',' } }, singleLine = true,
                label = { Text("Can I afford?") }, prefix = { Text("Ksh ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((k, l) in listOf("all" to "All", "spend" to "Spending", "save" to "Savings & debts", "low" to "Running low")) {
                FilterChip(selected = f.filter == k, onClick = { f.filter = k }, label = { Text(l) })
            }
        }
    }
}

/** "✓ Ksh 400 left after" or "✗ Short by Ksh 200", for a spend of [amount] from something with [left]. */
@Composable
fun AffordLine(left: Long, amount: Long?) {
    if (amount == null) return
    val after = left - amount
    val m = LocalMorph.current
    Text(
        if (after >= 0) "✓ Yes: ${Money.format(after)} left after" else "✗ No: short by ${Money.format(-after)}",
        color = if (after >= 0) m.good else m.bad,
        style = MaterialTheme.typography.labelLarge,
    )
}
