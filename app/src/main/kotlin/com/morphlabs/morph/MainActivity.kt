package com.morphlabs.morph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.morphlabs.morph.backup.BackupJob
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.sms.InboxImporter
import com.morphlabs.morph.ui.MorphRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = MorphRepo.get(this)
        setContent { MorphRoot(repo) }
    }

    override fun onResume() {
        super.onResume()
        // Safety net: pick up any M-Pesa SMS that arrived while Morph was asleep.
        val app = applicationContext
        Thread {
            val repo = MorphRepo.get(app)
            if (repo.onboarded && InboxImporter.hasPermission(app)) InboxImporter.import(app, repo.lastSmsDate)
            repo.relearnIfDue()
            BackupJob.backupIfDue(app)
        }.start()
    }
}
