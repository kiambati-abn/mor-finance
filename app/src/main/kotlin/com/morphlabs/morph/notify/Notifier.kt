package com.morphlabs.morph.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.morphlabs.morph.R
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.IngestResult
import com.morphlabs.morph.data.MorphRepo

/**
 * The game's front door: a notification right after you pay, with your most likely answers as
 * buttons. One tap files it and pays out stars.
 */
object Notifier {

    const val CH_SORT = "sort"
    const val CH_AUTO = "auto"
    const val CH_REWARD = "reward"

    const val EXTRA_ENTRY = "entry_id"
    const val EXTRA_ACCOUNT = "account_id"
    const val ACTION_FILE = "com.morphlabs.morph.FILE"
    const val ACTION_CONFIRM = "com.morphlabs.morph.CONFIRM"
    const val ACTION_UNDO = "com.morphlabs.morph.UNDO"

    private const val GOLD = 0xFFE8A020.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_SORT, "What was it?", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Asks you to sort a new M-Pesa transaction with one tap"
        })
        nm.createNotificationChannel(NotificationChannel(CH_AUTO, "Filed automatically", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Transactions Morph recognised on its own"
        })
        nm.createNotificationChannel(NotificationChannel(CH_REWARD, "Stars", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Your reward after sorting"
        })
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun onNewTransaction(context: Context, result: IngestResult) {
        if (!canNotify(context)) return
        val e = result.entry
        when (e.status) {
            EntryStatus.NEEDS_REVIEW -> showSort(context, e)
            EntryStatus.AUTO -> showAuto(context, e)
            EntryStatus.CONFIRMED -> if (e.kind == com.morphlabs.morph.core.model.TxKind.WITHDRAW) showCash(context, e)
            // M-Shwari, Fuliza: nothing to ask
        }
    }

    private fun headline(context: Context, e: Entry): String {
        val s = MorphRepo.get(context).snapshot()
        val amount = Money.format(s.amount(e))
        val who = e.counterparty ?: e.description
        return if (s.outgoing(e)) "$amount → $who" else "$amount ← $who"
    }

    fun showSort(context: Context, e: Entry) {
        val repo = MorphRepo.get(context)
        val s = repo.snapshot()
        val guess = s.guess(e, limit = 2)
        val b = Notification.Builder(context, CH_SORT)
            .setSmallIcon(R.drawable.ic_stat_morph)
            .setColor(GOLD)
            .setContentTitle(headline(context, e))
            .setContentText("What was it? Tap an answer and earn ⭐")
            .setContentIntent(quickSortIntent(context, e.id))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
        guess.suggestions.forEachIndexed { i, sug ->
            val a = s.byId[sug.accountId] ?: return@forEachIndexed
            b.addAction(action(context, a.label, fileIntent(context, e.id, a.id, i)))
        }
        b.addAction(action(context, "More…", quickSortIntent(context, e.id)))
        notify(context, e.id, b.build())
    }

    /** After a withdrawal: "what's this cash for?", answered by spreading it over categories. */
    private fun showCash(context: Context, e: Entry) {
        val s = MorphRepo.get(context).snapshot()
        val n = Notification.Builder(context, CH_SORT)
            .setSmallIcon(R.drawable.ic_stat_morph)
            .setColor(GOLD)
            .setContentTitle("💵 ${Money.format(s.amount(e))} cash withdrawn")
            .setContentText("What's it for? Tap to spread it over a few categories (+⭐ each)")
            .setContentIntent(quickSortIntent(context, e.id))
            .setAutoCancel(true)
            .build()
        notify(context, e.id, n)
    }

    private fun showAuto(context: Context, e: Entry) {
        val s = MorphRepo.get(context).snapshot()
        val a = e.categoryId?.let { s.byId[it] } ?: return
        val b = Notification.Builder(context, CH_AUTO)
            .setSmallIcon(R.drawable.ic_stat_morph)
            .setColor(GOLD)
            .setContentTitle(headline(context, e))
            .setContentText("Filed under ${a.label}" + (s.envelopeLeft(a.id, MorphRepo.get(context).dayOf(System.currentTimeMillis()))?.let { " · ${Money.format(it)} left" } ?: "") + ". Right?")
            .setContentIntent(quickSortIntent(context, e.id))
            .setAutoCancel(true)
            .addAction(action(context, "👍 Yes (+1⭐)", confirmIntent(context, e.id)))
            .addAction(action(context, "Change", quickSortIntent(context, e.id)))
        notify(context, e.id, b.build())
    }

    /** Replaces the question with a short, quiet "well done" that disappears on its own. */
    fun showReward(context: Context, entryId: Long, reward: Game.Reward, filedUnder: String?) {
        if (!canNotify(context)) return
        val repo = MorphRepo.get(context)
        val s = repo.snapshot()
        val streak = Game.streak(s.entries, repo.dayOf(System.currentTimeMillis()), repo.zone)
        val level = Game.level(s.stars)
        val entry = s.entries.firstOrNull { it.id == entryId }
        val left = entry?.categoryId?.let { s.envelopeLeft(it, repo.dayOf(System.currentTimeMillis())) }
        val text = buildString {
            if (filedUnder != null) append("Filed under $filedUnder")
            if (left != null) append(" · ${Money.format(left)} left")
            if (streak > 1) append(" · 🔥 $streak-day streak")
        }
        val b = Notification.Builder(context, CH_REWARD)
            .setSmallIcon(R.drawable.ic_stat_morph)
            .setColor(GOLD)
            .setContentTitle(if (reward.stars > 0) "+${reward.stars} ⭐  ${reward.reasons.joinToString(" · ")}" else "Saved")
            .setContentText(text.ifEmpty { "${level.emoji} ${level.name} · ${s.stars} ⭐" })
            .setSubText("${level.emoji} ${s.stars} ⭐")
            .setTimeoutAfter(8_000)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(action(context, "↩ Undo", undoIntent(context, entryId)))
        notify(context, entryId, b.build())
    }

    /** After an undo, ask the question again. */
    fun showAgain(context: Context, entryId: Long) {
        val e = MorphRepo.get(context).snapshot().entries.firstOrNull { it.id == entryId } ?: return
        if (e.status == EntryStatus.NEEDS_REVIEW) showSort(context, e) else cancel(context, entryId)
    }

    fun cancel(context: Context, entryId: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(entryId.toInt())
    }

    private fun notify(context: Context, entryId: Long, n: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(entryId.toInt(), n)
        } catch (_: SecurityException) {
            // Notifications were turned off; the transaction still waits in the Sort tab.
        }
    }

    private fun action(context: Context, label: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_morph), label, intent).build()

    private fun fileIntent(context: Context, entryId: Long, accountId: Long, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(entryId, slot),
            Intent(context, SortActionReceiver::class.java).setAction(ACTION_FILE)
                .putExtra(EXTRA_ENTRY, entryId).putExtra(EXTRA_ACCOUNT, accountId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun confirmIntent(context: Context, entryId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(entryId, 8),
            Intent(context, SortActionReceiver::class.java).setAction(ACTION_CONFIRM).putExtra(EXTRA_ENTRY, entryId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun undoIntent(context: Context, entryId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(entryId, 7),
            Intent(context, SortActionReceiver::class.java).setAction(ACTION_UNDO).putExtra(EXTRA_ENTRY, entryId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun quickSortIntent(context: Context, entryId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode(entryId, 9),
            Intent(context, QuickSortActivity::class.java).putExtra(EXTRA_ENTRY, entryId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun requestCode(entryId: Long, slot: Int): Int = (entryId * 10 + slot).toInt()
}
