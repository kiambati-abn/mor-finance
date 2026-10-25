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
fun MeScreen(repo: MorphRepo, d: Dashboard, goAccounts: () -> Unit, goAll: () -> Unit, goPatterns: () -> Unit, goLearn: () -> Unit = {}) {
    val s = d.s
    val ctx = LocalContext.current
    val openList = com.morphlabs.morph.ui.LocalOpenList.current
    val monthStart = d.today.withDayOfMonth(1).atStartOfDay(s.zone).toInstant().toEpochMilli()
    fun account(a: Account) = openList(com.morphlabs.morph.ui.TxFilter(a.label, accountIds = setOf(a.id)))
    fun groupThisMonth(title: String, ids: Set<Long>) = openList(com.morphlabs.morph.ui.TxFilter(title, accountIds = ids, from = monthStart))
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Account?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var startOver by remember { mutableStateOf(false) }
    var startDialog by remember { mutableStateOf(false) }
    val revealState = com.morphlabs.morph.ui.LocalReveal.current
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
            if (com.morphlabs.morph.ui.budgetMode(s)) MorphCard(onClick = { revealState.value = true }) {
                Column(Modifier.padding(16.dp)) {
                    Text("•••••• 👁", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Hidden by Budget view, so you go by what's left in your envelopes. Tap to peek.", style = MaterialTheme.typography.bodySmall)
                }
            } else MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Text(Money.format(d.netWorth.net), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${Money.format(d.netWorth.assets)} you have − ${Money.format(d.netWorth.liabilities)} you owe", style = MaterialTheme.typography.bodySmall)
                    Gap(8)
                    for ((a, v) in assets) BalanceRow(a, v) { account(a) }
                }
            }
        }
        item {
            SectionTitle("Liabilities")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    if (debts.all { it.second == 0L }) Text("Nothing owed. 🎉", style = MaterialTheme.typography.bodyMedium)
                    for (g in listOf(AccountGroup.DEBT, AccountGroup.LONG_TERM_DEBT)) {
                        val inGroup = debts.filter { it.first.group == g }
                        if (inGroup.isEmpty()) continue
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(g.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(Money.format(inGroup.sumOf { it.second }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        for ((a, v) in inGroup) BalanceRow(a, v) { account(a) }
                    }
                    Text("Tap a liability to see its payments; Edit account sets what you owe or a monthly repayment target.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionTitle("Backup")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    if (repo.backupUri == null) {
                        Text("☁️ Automatic backup is off", fontWeight = FontWeight.SemiBold)
                        Text("Pick a file once, ideally on Google Drive. Morph then updates it on its own (daily, weekly or monthly; you choose). On a new phone: install Morph → \"I have a backup\".",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { autoLauncher.launch("morph-backup.json") }) { Text("Turn on automatic backup") }
                    } else {
                        // Local state so the chips and "Last backup" update the moment you tap.
                        var every by remember { mutableStateOf(repo.backupEveryDays) }
                        var last by remember { mutableStateOf(repo.lastBackup) }
                        Text(if (last > 0) "☁️ Last backup: ${com.morphlabs.morph.ui.components.whenText(last, s.zone)}" else "☁️ Waiting for the first backup",
                            fontWeight = FontWeight.SemiBold)
                        repo.backupError?.let { Text("Last try failed: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Gap(6)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("How often", style = MaterialTheme.typography.labelLarge)
                            for ((n, l) in listOf(1 to "Daily", 7 to "Weekly", 30 to "Monthly")) {
                                FilterChip(selected = every == n, onClick = { every = n; Thread { repo.backupEveryDays = n }.start() }, label = { Text(l) })
                            }
                        }
                        Text(
                            "Automatic: Morph checks once a day in the background (even after a restart) and whenever you open it, and backs up when your choice is due. " +
                                "It tells you if a backup fails.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) { repo.backupNow() }
                                    last = repo.lastBackup
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
            SectionTitle("Envelopes & Budget view")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("✉️ Envelopes", fontWeight = FontWeight.SemiBold)
                            Text("Give every shilling a job; each envelope shows what's left.", style = MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.Switch(checked = s.envelopesStart != null, onCheckedChange = { on -> Thread { repo.setEnvelopes(on) }.start() })
                    }
                    if (s.envelopesStart != null) {
                        Gap(8)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("🙈 Budget view", fontWeight = FontWeight.SemiBold)
                                Text("Hide real balances and net worth; show what's left in envelopes instead. Tap 👁 to peek any time.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            androidx.compose.material3.Switch(checked = s.budgetView, onCheckedChange = { on -> Thread { repo.setBudgetView(on) }.start() })
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Look & feel")
            MorphCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("😄 Playful wording", fontWeight = FontWeight.SemiBold)
                        Text("Playful wording such as 💣 Today's Bomb, Bahasha and Money X-ray. Off (standard names) is easiest when you share Morph. Account types always use standard names.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    var playful by remember { mutableStateOf(repo.playful) }
                    androidx.compose.material3.Switch(checked = playful, onCheckedChange = { on -> playful = on; Thread { repo.playful = on }.start() })
                }
            }
        }
        item {
            SectionTitle("Budget alerts")
            MorphCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🔔 Tell me when a budget is almost out", fontWeight = FontWeight.SemiBold)
                        Text("Once when it reaches 🔴 10% left and once if it goes ⛔ over, per day or month. Bars turn 🟡 gold at 25% left and 🔴 red at 10%.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(checked = repo.budgetAlerts, onCheckedChange = { repo.budgetAlerts = it })
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
                    Gap(6)
                    Text("🧠 Keeps improving: a few times a day Morph re-checks the waiting pile and its own earlier guesses with what it has learned. Your choices are never changed.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    repo.lastRelearn?.let { (at, n) ->
                        Text("Last check: ${com.morphlabs.morph.ui.components.whenText(at, s.zone)} · ${if (n == 0) "nothing to change" else "$n improved"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { repo.relearnIfDue(force = true) }
                            Toast.makeText(ctx, if (n > 0) "Improved $n" else "Nothing to change", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Re-check now") }
                }
            }
        }
        if (d.insights.isNotEmpty()) item { SectionTitle(com.morphlabs.morph.core.model.Naming.pick("Your money is talking 🗣️", "What your money says"), action = "Patterns", onAction = goPatterns) }
        items(d.insights, key = { it.id }) { i -> InsightCard(i, s); Gap(8) }
        item {
            val month = d.monthByAccount()
            val fixed = month.filterKeys { s.byId[it]?.group == AccountGroup.FIXED }.values.sum()
            val wants = month.filterKeys { s.byId[it]?.group == AccountGroup.DISCRETIONARY }.values.sum()
            SectionTitle("This month")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    val ids = { g: AccountGroup -> s.accounts.filter { it.group == g }.map { it.id }.toSet() }
                    Row(Modifier.fillMaxWidth().clickable { groupThisMonth("Fixed · this month", ids(AccountGroup.FIXED)) }.padding(vertical = 6.dp)) {
                        Text("Fixed (needs) ›", Modifier.weight(1f)); Text(Money.format(fixed), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().clickable { groupThisMonth("Discretionary · this month", ids(AccountGroup.DISCRETIONARY)) }.padding(vertical = 6.dp)) {
                        Text("Discretionary (wants) ›", Modifier.weight(1f)); Text(Money.format(wants), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().clickable { groupThisMonth("Money in · this month", ids(AccountGroup.INCOME)) }.padding(vertical = 6.dp)) {
                        Text("Money in ›", Modifier.weight(1f)); Text(Money.format(d.receivedThisMonth), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            SectionTitle("Settings")
            MorphCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingRow(com.morphlabs.morph.core.model.Naming.pick("🥋  Money Dojo: today's bomb & your levels", "📚  Morph Academy: learn money, 2 min a day"), goLearn)
                    SettingRow(com.morphlabs.morph.core.model.Naming.pick("🔮  Money X-ray: deep look at your money", "🔍  Patterns: deep look at your money"), goPatterns)
                    SettingRow("🗂️  Accounts & budgets", goAccounts)
                    SettingRow("📜  All transactions", goAll)
                    SettingRow("📅  Tracking starts: ${repo.startDate?.let { java.time.Instant.ofEpochMilli(it).atZone(s.zone).toLocalDate().let { d -> java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH).format(d) } } ?: "from the beginning"}") { startDialog = true }
                    SettingRow("🔄  Re-check SMS inbox") {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { InboxImporter.import(ctx, 0) }
                            Toast.makeText(ctx, r.summary(), Toast.LENGTH_LONG).show()
                        }
                    }
                    SettingRow("💾  Back up to a file (Drive, etc.)") { exportLauncher.launch("morph-backup-${d.today}.json") }
                    SettingRow("♻️  Restore from a backup") { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    SettingRow(com.morphlabs.morph.core.model.Naming.pick("🧹  Fresh start…", "🧹  Start over…")) { startOver = true }
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
    if (startDialog) StartDateDialog(repo, d.today) { startDialog = false }
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
