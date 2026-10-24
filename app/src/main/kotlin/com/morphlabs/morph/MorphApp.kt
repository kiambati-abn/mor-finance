package com.morphlabs.morph

import android.app.Application
import com.morphlabs.morph.backup.BackupJob
import com.morphlabs.morph.data.MorphDb
import com.morphlabs.morph.notify.Notifier
import java.io.File

class MorphApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        backupDatabaseOnUpdate()
        BackupJob.schedule(this)
    }

    /**
     * Before the first launch of each new version, keep a copy of the database file so an update
     * can never cost you your history. Keeps the last 5 copies in the app's private storage.
     */
    private fun backupDatabaseOnUpdate() {
        val prefs = getSharedPreferences("morph", MODE_PRIVATE)
        val current = BuildConfig.VERSION_CODE
        val last = prefs.getInt("last_version", -1)
        if (last == current) return
        val db = getDatabasePath(MorphDb.NAME)
        if (db.exists() && last != -1) {
            runCatching {
                val dir = File(filesDir, "backups").apply { mkdirs() }
                db.copyTo(File(dir, "morph-before-v$current.db"), overwrite = true)
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
            }
        }
        prefs.edit().putInt("last_version", current).apply()
    }
}
