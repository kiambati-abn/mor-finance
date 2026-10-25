package com.morphlabs.morph.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import com.morphlabs.morph.ui.components.tapToHideKeyboard
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

enum class Page { ACCOUNTS, TRANSACTIONS, PATTERNS, LEARN }

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
        Surface(Modifier.fillMaxSize().tapToHideKeyboard(), color = MaterialTheme.colorScheme.background) {
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
    // A drill-down list sits on top of whatever screen opened it; Back returns there.
    var listFilter by remember { mutableStateOf<TxFilter?>(null) }
    val dash = remember(s) { Dashboard(s, repo.dayOf(System.currentTimeMillis())) }

    BackHandler(enabled = listFilter != null) { listFilter = null }
    BackHandler(enabled = listFilter == null && page != null) { page = null }
    BackHandler(enabled = page == null && tab != Tab.HOME) { tab = Tab.HOME }

    val reveal = remember { mutableStateOf(false) }
    androidx.compose.runtime.CompositionLocalProvider(LocalOpenList provides { f: TxFilter -> listFilter = f }, LocalReveal provides reveal) {
        MainBody(repo, s, dash, tab, { tab = it }, page, { page = it }, listFilter, { listFilter = it }, adding, { adding = it })
    }
}

@Composable
private fun MainBody(
    repo: MorphRepo, s: Snapshot, dash: Dashboard,
    tab: Tab, setTab: (Tab) -> Unit, page: Page?, setPage: (Page?) -> Unit,
    listFilter: TxFilter?, setList: (TxFilter?) -> Unit, adding: Boolean, setAdding: (Boolean) -> Unit,
) {
    var budgetsFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }
    if (listFilter != null) { TransactionsScreen(repo, s, onBack = { setList(null) }, filter = listFilter); return }
    when (page) {
        Page.ACCOUNTS -> { AccountsScreen(repo, s, onBack = { setPage(null) }); return }
        Page.TRANSACTIONS -> { TransactionsScreen(repo, s, onBack = { setPage(null) }); return }
        Page.PATTERNS -> { PatternsScreen(repo, s, onBack = { setPage(null) }); return }
        Page.LEARN -> {
            com.morphlabs.morph.ui.screens.LearnScreen(repo, s, onBack = { setPage(null) }) { a ->
                when (a) {
                    com.morphlabs.morph.core.academy.Action.BUDGETS -> { budgetsFilter = "all"; setTab(Tab.ENVELOPES); setPage(null) }
                    com.morphlabs.morph.core.academy.Action.ACCOUNTS -> setPage(Page.ACCOUNTS)
                    com.morphlabs.morph.core.academy.Action.PATTERNS -> setPage(Page.PATTERNS)
                    com.morphlabs.morph.core.academy.Action.AUTOSORT -> { setTab(Tab.ME); setPage(null) }
                    com.morphlabs.morph.core.academy.Action.NONE -> Unit
                }
            }
            return
        }
        null -> Unit
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) NavigationBarItem(
                    selected = tab == t,
                    onClick = { setTab(t) },
                    icon = {
                        if (t == Tab.SORT && s.pending.isNotEmpty()) BadgedBox(badge = { Badge { Text("${s.pending.size}") } }) { Text(t.emoji, fontSize = 20.sp) }
                        else Text(t.emoji, fontSize = 20.sp)
                    },
                    label = { Text(t.label) },
                )
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME) FloatingActionButton(onClick = { setAdding(true) }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Filled.Add, contentDescription = "Add a cash or other transaction")
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
            when (tab) {
                Tab.HOME -> HomeScreen(repo, dash, goSort = { setTab(Tab.SORT) }, goMe = { setTab(Tab.ME) }, goAll = { setPage(Page.TRANSACTIONS) },
                    goPatterns = { setPage(Page.PATTERNS) }, goBudgets = { budgetsFilter = "all"; setTab(Tab.ENVELOPES) },
                    goLowBudgets = { budgetsFilter = "low"; setTab(Tab.ENVELOPES) }, goAccounts = { setPage(Page.ACCOUNTS) }, goLearn = { setPage(Page.LEARN) })
                Tab.SORT -> SortScreen(repo, s)
                Tab.ENVELOPES -> androidx.compose.runtime.key(budgetsFilter) { EnvelopesScreen(repo, dash, goAccounts = { setPage(Page.ACCOUNTS) }, startFilter = budgetsFilter) }
                Tab.ME -> MeScreen(repo, dash, goAccounts = { setPage(Page.ACCOUNTS) }, goAll = { setPage(Page.TRANSACTIONS) }, goPatterns = { setPage(Page.PATTERNS) }, goLearn = { setPage(Page.LEARN) })
            }
        }
    }
    if (adding) AddEntryDialog(repo, s, onDismiss = { setAdding(false) })
}

/** Today in Morph's time zone. */
fun today(repo: MorphRepo): LocalDate = repo.dayOf(System.currentTimeMillis())
