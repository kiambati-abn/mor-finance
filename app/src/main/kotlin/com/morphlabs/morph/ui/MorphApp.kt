package com.morphlabs.morph.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.screens.AccountsScreen
import com.morphlabs.morph.ui.screens.AddEntryDialog
import com.morphlabs.morph.ui.screens.EnvelopesScreen
import com.morphlabs.morph.ui.screens.HomeScreen
import com.morphlabs.morph.ui.screens.MeScreen
import com.morphlabs.morph.ui.screens.OnboardingScreen
import com.morphlabs.morph.ui.screens.PatternsScreen
import com.morphlabs.morph.ui.screens.SortScreen
import com.morphlabs.morph.ui.screens.TransactionsScreen
import com.morphlabs.morph.ui.theme.MorphTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Tab(val label: String, val emoji: String) { HOME("Home", "🏠"), SORT("Sort", "⭐"), ENVELOPES("Budgets", "✉️"), ME("Me", "🦋") }

enum class Page { ACCOUNTS, TRANSACTIONS, PATTERNS }

private data class Root(val snapshot: Snapshot, val onboarded: Boolean, val theme: String)

@Composable
fun MorphRoot(repo: MorphRepo) {
    val version by repo.version.collectAsState()
    var root by remember { mutableStateOf<Root?>(null) }
    LaunchedEffect(version) {
        root = withContext(Dispatchers.IO) { Root(repo.snapshot(), repo.onboarded, repo.theme) }
    }
    val r = root
    MorphTheme(r?.theme ?: "system") {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                r == null -> Box(Modifier.fillMaxSize())
                !r.onboarded -> OnboardingScreen(repo, r.snapshot)
                else -> Main(repo, r.snapshot)
            }
        }
    }
}

@Composable
private fun Main(repo: MorphRepo, s: Snapshot) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var adding by remember { mutableStateOf(false) }
    val dash = remember(s) { Dashboard(s, repo.dayOf(System.currentTimeMillis())) }

    BackHandler(enabled = page != null) { page = null }
    BackHandler(enabled = page == null && tab != Tab.HOME) { tab = Tab.HOME }

    when (page) {
        Page.ACCOUNTS -> { AccountsScreen(repo, s, onBack = { page = null }); return }
        Page.TRANSACTIONS -> { TransactionsScreen(repo, s, onBack = { page = null }); return }
        Page.PATTERNS -> { PatternsScreen(repo, s, onBack = { page = null }); return }
        null -> Unit
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) NavigationBarItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = {
                        if (t == Tab.SORT && s.pending.isNotEmpty()) BadgedBox(badge = { Badge { Text("${s.pending.size}") } }) { Text(t.emoji, fontSize = 20.sp) }
                        else Text(t.emoji, fontSize = 20.sp)
                    },
                    label = { Text(t.label) },
                )
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME) FloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Filled.Add, contentDescription = "Add a cash or other transaction")
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.HOME -> HomeScreen(repo, dash, goSort = { tab = Tab.SORT }, goMe = { tab = Tab.ME }, goAll = { page = Page.TRANSACTIONS }, goPatterns = { page = Page.PATTERNS }, goBudgets = { tab = Tab.ENVELOPES })
                Tab.SORT -> SortScreen(repo, s)
                Tab.ENVELOPES -> EnvelopesScreen(repo, dash, goAccounts = { page = Page.ACCOUNTS })
                Tab.ME -> MeScreen(repo, dash, goAccounts = { page = Page.ACCOUNTS }, goAll = { page = Page.TRANSACTIONS }, goPatterns = { page = Page.PATTERNS })
            }
        }
    }
    if (adding) AddEntryDialog(repo, s, onDismiss = { adding = false })
}

/** Today in Morph's time zone. */
fun today(repo: MorphRepo): LocalDate = repo.dayOf(System.currentTimeMillis())
