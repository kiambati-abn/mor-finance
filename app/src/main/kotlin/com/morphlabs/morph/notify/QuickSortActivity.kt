package com.morphlabs.morph.notify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.StarBurst
import com.morphlabs.morph.ui.screens.SortPanel
import com.morphlabs.morph.ui.theme.MorphTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The "what was it?" card that pops over whatever you're doing when you tap a notification. */
class QuickSortActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryId = intent.getLongExtra(Notifier.EXTRA_ENTRY, -1)
        Notifier.cancel(this, entryId)
        val repo = MorphRepo.get(this)
        setContent { QuickSort(repo, entryId, theme = repo.theme) { finish() } }
    }
}

@Composable
private fun QuickSort(repo: MorphRepo, entryId: Long, theme: String, close: () -> Unit) {
    val version by repo.version.collectAsState()
    var s by remember { mutableStateOf<Snapshot?>(null) }
    var burst by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(version) { s = withContext(Dispatchers.IO) { repo.snapshot() } }
    MorphTheme(theme) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val snap = s
            val e = snap?.entries?.firstOrNull { it.id == entryId }
            if (snap != null && e != null && burst == null) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 640.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(20.dp)
                ) {
                    SortPanel(repo, snap, e, onSorted = { r, label -> burst = r.stars to label }, onSkip = null)
                }
            } else if (snap != null && e == null) {
                LaunchedEffect(Unit) { close() }
            }
            burst?.let { (stars, label) -> StarBurst(stars, label) { close() } }
        }
    }
}
