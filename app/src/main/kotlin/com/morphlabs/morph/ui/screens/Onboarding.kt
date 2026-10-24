package com.morphlabs.morph.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.R
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.sms.InboxImporter
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(repo: MorphRepo, s: Snapshot) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${step + 1} / 5", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(8)
        when (step) {
            0 -> Welcome(repo) { step = 1 }
            1 -> Permissions { step = 2 }
            2 -> Battery { step = 3 }
            3 -> History(repo) { step = 4 }
            else -> Balances(repo, s) { repo.onboarded = true }
        }
    }
}

@Composable
private fun Title(text: String, sub: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Gap(8)
    Text(sub, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Gap(24)
}

@Composable
private fun Welcome(repo: MorphRepo, next: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            error = withContext(Dispatchers.IO) {
                runCatching {
                    val json = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Couldn't read the file")
                    repo.importJson(json)
                    // Still walk through permissions on this phone; the old phone's backup file link doesn't work here.
                    repo.onboarded = false
                    repo.backupUri = null
                }.exceptionOrNull()?.message
            }
            if (error == null) next()
        }
    }
    Gap(24)
    Image(painterResource(R.drawable.morph_wing), contentDescription = null, modifier = Modifier.height(180.dp))
    Gap(16)
    Text("Morph", fontSize = 44.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
    Gap(4)
    Title(
        "Your money, transformed",
        "Morph reads your M-Pesa messages, files what it recognises, and asks you about the rest with one tap. " +
            "Every tap earns stars and teaches Morph your habits.",
    )
    Button(onClick = next, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Let's start") }
    Gap(8)
    OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("I have a backup")
    }
    error?.let { Gap(8); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    Gap(8)
    Text("After restoring, allow SMS again in the next steps so new messages keep coming in.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun Permissions(next: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(InboxImporter.hasPermission(ctx)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[Manifest.permission.READ_SMS] == true || InboxImporter.hasPermission(ctx)
        denied = !granted
    }
    Title("Let Morph read M-Pesa SMS", "Only messages from MPESA are read. They never leave your phone.")
    MorphCard {
        Column(Modifier.padding(16.dp)) {
            Text("📩  SMS: to record each transaction the moment it happens")
            Gap(8)
            Text("🔔  Notifications: to ask \"what was it?\" right after you pay")
        }
    }
    Gap(24)
    if (!granted) Button(onClick = {
        val perms = buildList {
            add(Manifest.permission.READ_SMS); add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow") }
    else Button(onClick = next, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next") }
    if (denied) {
        Gap(16)
        Text(
            "Android blocked it. If you installed Morph from a file, open App info → ⋮ (top right) → " +
                "\"Allow restricted settings\", then come back and tap Allow again.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
        TextButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }) { Text("Open App info") }
        TextButton(onClick = next) { Text("Skip for now") }
    }
}

@Composable
private fun Battery(next: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.getSystemService(PowerManager::class.java)
    var ok by remember { mutableStateOf(pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true) }
    Title(
        "Keep Morph awake",
        "Samsung and many other phones put apps to sleep, and sleeping apps miss SMS. " +
            "Allow Morph to run in the background so every payment gets a notification.",
    )
    if (!ok) {
        Button(onClick = {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }
            ok = true
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow background") }
        Gap(8)
        Text(
            "On Samsung also check: Settings → Battery → Background usage limits → make sure Morph is not in \"Sleeping apps\".",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(8)
    }
    OutlinedButton(onClick = next, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (ok) "Next" else "Later") }
}

@Composable
private fun History(repo: MorphRepo, next: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { repo.dayOf(System.currentTimeMillis()) }
    // The start date: nothing before it is imported or asked about. Null = everything.
    var start by rememberSaveable { mutableStateOf<String?>(today.minusMonths(3).toString()) }
    var picking by remember { mutableStateOf(false) }
    val startDay = start?.let { java.time.LocalDate.parse(it) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<InboxImporter.Result?>(null) }
    Title("Bring in your history", "Morph reads the M-Pesa messages already on your phone, from your start date onwards.")
    val enabled = !running && result == null
    MorphCard(onClick = if (enabled) ({ picking = true }) else null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📅 Start date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(startDay?.let { java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH).format(it) } ?: "Everything on the phone",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (enabled) Text("Change", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
    Gap(8)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((d, label) in listOf(today to "Today", today.withDayOfMonth(1) to "This month", today.minusMonths(1) to "1 month",
            today.minusMonths(3) to "3 months", today.minusMonths(6) to "6 months", null to "All")) {
            FilterChip(selected = startDay == d, onClick = { start = d?.toString() }, label = { Text(label) }, enabled = enabled)
        }
    }
    Text("Nothing before this date is imported or asked about. You can pull in earlier history any time from Me.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    if (picking) com.morphlabs.morph.ui.components.PickDateDialog(startDay ?: today.minusMonths(3), today, onDismiss = { picking = false }) { start = it.toString() }
    Gap(24)
    val r = result
    when {
        running -> {
            CircularProgressIndicator()
            Gap(8)
            Text("Read $progress messages…")
        }
        r != null -> {
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Found ${r.added} M-Pesa transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Gap(4)
                    Text("Morph filed ${r.added - r.toSort} on its own. ${r.toSort} are waiting for you in the ⭐ Sort tab, where each one earns stars.")
                }
            }
            Gap(24)
            Button(onClick = next, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next") }
        }
        else -> {
            Button(onClick = {
                running = true
                scope.launch {
                    val since = startDay?.atStartOfDay(repo.zone)?.toInstant()?.toEpochMilli() ?: 0L
                    result = withContext(Dispatchers.IO) {
                        // Tracking starts here; earlier history can be pulled in later from Me.
                        repo.startDate = if (startDay == null) null else since
                        InboxImporter.import(ctx, since, onProgress = { progress = it })
                    }
                    running = false
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = InboxImporter.hasPermission(ctx)) { Text("Import") }
            TextButton(onClick = {
                repo.startDate = repo.dayOf(System.currentTimeMillis()).atStartOfDay(repo.zone).toInstant().toEpochMilli()
                next()
            }) { Text("Skip, start from today") }
        }
    }
}

@Composable
private fun Balances(repo: MorphRepo, s: Snapshot, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<Long, String>() }
    val asks = s.accounts.filter {
        !it.archived && it.role != SystemRole.MPESA && it.role != SystemRole.FULIZA &&
            (it.group == AccountGroup.WALLET || it.group == AccountGroup.SAVINGS || it.group == AccountGroup.OWED_TO_ME || it.group == AccountGroup.DEBT)
    }
    Title("What do you have, and owe, today?", "Fill in what applies; leave the rest empty. You can change these any time.")
    MorphCard {
        Column(Modifier.padding(16.dp)) {
            Text("📱 M-Pesa: ${Money.format(s.balance(s.account(SystemRole.MPESA)))}", fontWeight = FontWeight.SemiBold)
            Text("Taken from your latest M-Pesa SMS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Gap(12)
    for (a in asks) {
        AmountField(
            value = values[a.id] ?: "",
            onChange = { values[a.id] = it },
            label = "${a.emoji} ${a.name}" + if (a.group == AccountGroup.DEBT) " (I owe)" else "",
        )
        Gap(8)
    }
    Gap(16)
    Button(onClick = {
        scope.launch {
            withContext(Dispatchers.IO) {
                for ((id, text) in values) Money.parse(text)?.let { repo.setBalanceNow(id, it) }
            }
            done()
        }
    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Finish 🦋") }
}
