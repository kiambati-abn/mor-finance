package com.morphlabs.morph.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.morphlabs.morph.data.MorphRepo

/** Handles a tap on one of the answer buttons in a notification. */
class SortActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(Notifier.EXTRA_ENTRY, -1)
        if (entryId < 0) return
        val accountId = intent.getLongExtra(Notifier.EXTRA_ACCOUNT, -1)
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val repo = MorphRepo.get(app)
                if (intent.action == Notifier.ACTION_UNDO) {
                    repo.undoSort(entryId)
                    Notifier.showAgain(app, entryId)
                    return@Thread
                }
                val reward = when (intent.action) {
                    Notifier.ACTION_FILE -> if (accountId >= 0) repo.categorize(entryId, accountId) else null
                    Notifier.ACTION_CONFIRM -> repo.confirm(entryId)
                    else -> null
                } ?: return@Thread
                val s = repo.snapshot()
                val label = s.entries.firstOrNull { it.id == entryId }?.categoryId?.let { s.byId[it]?.label }
                Notifier.showReward(app, entryId, reward, label)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
