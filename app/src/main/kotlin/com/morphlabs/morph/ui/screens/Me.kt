package com.morphlabs.morph.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.BuildConfig
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.sms.InboxImporter
import com.morphlabs.morph.ui.Dashboard
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.theme.LocalMorph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MeScreen(repo: MorphRepo, d: Dashboard, goAccounts: () -> Unit, goAll: () -> Unit, goPatterns: () -> Unit) {
    val s = d.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Account?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var startOver by remember { mutableStateOf(false) }
    val autoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                repo.backupUri = uri.toString()
                repo.backupNow()
            }
            Toast.makeText(ctx, err?.let { "Couldn't write there: $it" } ?: "Automatic backup is on ✓", Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) } }.isSuccess
            }
            Toast.makeText(ctx, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    val assets = s.accounts.filter { !it.archived && it.type == AccountType.ASSET }.map { it to s.balance(it) }.filter { it.second != 0L }
    val debts = s.accounts.filter { !it.archived && it.type == AccountType.LIABILITY }.map { it to s.balance(it) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Gap(12)
            MorphCard {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.level.emoji, fontSize = 48.sp)
                    Text(d.level.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${d.stars} ⭐ · 🔥 ${d.streak}-day streak")
                    Gap(10)
                    LinearProgressIndicator(
                        progress = { d.levelProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = LocalMorph.current.gold, trackColor = LocalMorph.current.track, drawStopIndicator = {},
                    )
                    d.nextLevel?.let { Text("${it.minStars - d.stars} ⭐ to ${it.name} ${it.emoji}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            SectionTitle("Net worth")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Text(Money.format(d.netWorth.net), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${Money.format(d.netWorth.assets)} you have − ${Money.format(d.netWorth.liabilities)} you owe", style = MaterialTheme.typography.bodySmall)
                    Gap(8)
                    for ((a, v) in assets) BalanceRow(a, v) { editing = a }
                }
            }
        }
        item {
            SectionTitle("Debts")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    if (debts.all { it.second == 0L }) Text("No debts recorded. 🎉", style = MaterialTheme.typography.bodyMedium)
                    for ((a, v) in debts) BalanceRow(a, v) { editing = a }
                    Text("Tap a debt to set what you owe or a monthly repayment target.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionTitle("Backup")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    if (repo.backupUri == null) {
                        Text("☁️ Automatic backup is off", fontWeight = FontWeight.SemiBold)
                        Text("Pick a file once, ideally on Google Drive. Morph updates it every day. On a new phone: install Morph → \"I have a backup\".",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { autoLauncher.launch("morph-backup.json") }) { Text("Turn on automatic backup") }
                    } else {
                        val last = repo.lastBackup
                        Text(if (last > 0) "☁️ Last backup: ${com.morphlabs.morph.ui.components.whenText(last, s.zone)}" else "☁️ Waiting for the first backup",
                            fontWeight = FontWeight.SemiBold)
                        repo.backupError?.let { Text("Last try failed: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) { repo.backupNow() }
                                    Toast.makeText(ctx, err ?: "Backed up ✓", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Back up now") }
                            TextButton(onClick = { autoLauncher.launch("morph-backup.json") }) { Text("Change file") }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Auto-sort")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    val mode = repo.autopilot
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, l) in listOf("off" to "Off", "careful" to "Careful", "bold" to "Bold")) {
                            FilterChip(selected = mode == k, onClick = {
                                repo.autopilot = k
                                if (k != "off") scope.launch {
                                    val n = withContext(Dispatchers.IO) { repo.autoSortPending() }
                                    if (n > 0) Toast.makeText(ctx, "Auto-sorted $n waiting transactions", Toast.LENGTH_SHORT).show()
                                }
                            }, label = { Text(l) })
                        }
                    }
                    Gap(6)
                    Text(
                        when (mode) {
                            "careful" -> "Morph files on its own when it's very sure: the same place again, or a strong routine (same amount, same time of day). Everything else waits for you."
                            "bold" -> "Morph files whatever it's fairly sure about, and flags the less certain ones with 🤔 so you can check them in the Sort tab."
                            else -> "Morph only files the rock-solid ones (the same person or till filed the same way twice) and asks about the rest."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.unsure.isNotEmpty()) Text("🤔 ${s.unsure.size} auto-sorted to check in the Sort tab", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (d.insights.isNotEmpty()) item { SectionTitle("What your money says", action = "Patterns", onAction = goPatterns) }
        items(d.insights, key = { it.id }) { i -> InsightCard(i); Gap(8) }
        item {
            val month = d.monthByAccount()
            val fixed = month.filterKeys { s.byId[it]?.group == AccountGroup.FIXED }.values.sum()
            val wants = month.filterKeys { s.byId[it]?.group == AccountGroup.DISCRETIONARY }.values.sum()
            SectionTitle("This month")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Row { Text("Fixed (needs)", Modifier.weight(1f)); Text(Money.format(fixed), fontWeight = FontWeight.SemiBold) }
                    Row { Text("Discretionary (wants)", Modifier.weight(1f)); Text(Money.format(wants), fontWeight = FontWeight.SemiBold) }
                    Row { Text("Money in", Modifier.weight(1f)); Text(Money.format(d.receivedThisMonth), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item {
            SectionTitle("Settings")
            MorphCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingRow("🔍  Patterns: deep look at your money", goPatterns)
                    SettingRow("🗂️  Accounts & budgets", goAccounts)
                    SettingRow("📜  All transactions", goAll)
                    SettingRow("🔄  Re-check SMS inbox") {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { InboxImporter.import(ctx, 0) }
                            Toast.makeText(ctx, "Checked ${r.read} messages, ${r.added} new", Toast.LENGTH_SHORT).show()
                        }
                    }
                    SettingRow("💾  Back up to a file (Drive, etc.)") { exportLauncher.launch("morph-backup-${d.today}.json") }
                    SettingRow("♻️  Restore from a backup") { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    SettingRow("🧹  Start over…") { startOver = true }
                    HorizontalDivider()
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Theme", Modifier.weight(1f))
                        for ((k, label) in listOf("system" to "Auto", "light" to "Light", "dark" to "Dark")) {
                            FilterChip(selected = repo.theme == k, onClick = { repo.theme = k }, label = { Text(label) })
                        }
                    }
                }
            }
            Gap()
            Text(
                "Morph ${BuildConfig.VERSION_NAME} · by Morph Labs · your data stays on this phone",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(40)
        }
    }
    editing?.let { a -> AccountEditDialog(repo, s, a) { editing = null } }
    if (startOver) AlertDialog(
        onDismissRequest = { startOver = false },
        title = { Text("Start over?") },
        text = {
            Text("All transactions, budget moves and stars are cleared, then Morph re-reads your M-Pesa SMS from the date you choose. " +
                "A copy of everything is saved first.")
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { startOver = false; Thread { repo.startOver(keepAccounts = true) }.start() }) { Text("Keep my accounts & budgets") }
                TextButton(onClick = { startOver = false; Thread { repo.startOver(keepAccounts = false) }.start() }) {
                    Text("Wipe everything", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = { startOver = false }) { Text("Cancel") } },
    )
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything in Morph now will be replaced by the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        val err = withContext(Dispatchers.IO) {
                            runCatching {
                                val json = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Couldn't read the file")
                                repo.importJson(json)
                            }.exceptionOrNull()
                        }
                        Toast.makeText(ctx, err?.message ?: "Restored", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BalanceRow(a: Account, v: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(a.label, Modifier.weight(1f))
        Text(Money.format(v), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingRow(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp))
}
