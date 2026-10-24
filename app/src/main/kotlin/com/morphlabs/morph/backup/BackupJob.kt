package com.morphlabs.morph.backup

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.morphlabs.morph.MainActivity
import com.morphlabs.morph.R
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.notify.Notifier

/** Copies your whole Morph to the backup file you chose (e.g. on Google Drive), once a day. */
class BackupJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val error = runBackup(applicationContext)
            jobFinished(params, error != null) // retry later if it failed
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4201
        private const val DAY = 24L * 3600 * 1000

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, BackupJob::class.java))
                    .setPeriodic(DAY)
                    .setPersisted(true)
                    .build()
            )
        }

        /** Backs up if there's a file chosen and it's due; tells you when it can't. */
        fun runBackup(context: Context): String? {
            val repo = MorphRepo.get(context)
            if (repo.backupUri == null || !repo.onboarded || !repo.backupDue) return null
            val error = repo.backupNow()
            if (error != null) notifyFailure(context)
            return error
        }

        /** Runs a backup now if one is due by your chosen frequency. Called when the app opens. */
        fun backupIfDue(context: Context) {
            runBackup(context)
        }

        private fun notifyFailure(context: Context) {
            if (!Notifier.canNotify(context)) return
            val open = PendingIntent.getActivity(
                context, 4202, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(context, Notifier.CH_AUTO)
                .setSmallIcon(R.drawable.ic_stat_morph)
                .setContentTitle("Morph couldn't back up")
                .setContentText("Open Me → Automatic backup and choose the file again.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)?.notify(4202, n)
        }
    }
}
