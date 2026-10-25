package com.morphlabs.morph.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.morphlabs.morph.core.academy.Academy
import com.morphlabs.morph.core.academy.Outcome
import com.morphlabs.morph.core.academy.Progress
import com.morphlabs.morph.core.budget.Allocation
import com.morphlabs.morph.core.budget.BudgetMove
import com.morphlabs.morph.core.defaults.DefaultAccounts
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.learn.Guess
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.ledger.Reconcile
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.Line
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.core.sms.MpesaParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

data class IngestResult(val entry: Entry, val guess: Guess?)

/**
 * The single source of truth on the phone. All writes go through here; afterwards [version]
 * ticks so open screens refresh. Reads come from an in-memory [Snapshot].
 */
class MorphRepo private constructor(context: Context) {

    private val appContext = context.applicationContext
    val zone: ZoneId = ZoneId.of("Africa/Nairobi")
    private val helper = MorphDb(appContext)
    private val db: SQLiteDatabase by lazy { helper.writableDatabase.also { seedIfEmpty(it) } }

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    @Volatile private var snap: Snapshot? = null

    fun snapshot(): Snapshot = snap ?: synchronized(this) { snap ?: load().also { snap = it } }

    private fun changed(reloadFromDb: Boolean = true) {
        if (reloadFromDb) snap = load()
        _version.value = _version.value + 1
    }

    // ---- settings -------------------------------------------------------------------------

    fun meta(key: String): String? =
        db.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun setMeta(key: String, value: String?) {
        if (value == null) db.delete("meta", "k = ?", arrayOf(key))
        else db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", key); put("v", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        _version.value = _version.value + 1
    }

    var onboarded: Boolean
        get() = meta(KEY_ONBOARDED) == "1"
        set(v) = setMeta(KEY_ONBOARDED, if (v) "1" else null)

    /** "system", "light" or "dark". */
    var theme: String
        get() = meta(KEY_THEME) ?: "system"
        set(v) = setMeta(KEY_THEME, v)

    /** Tracking start (start of a day, epoch ms), or null for no limit. */
    var startDate: Long?
        get() = meta(KEY_START_DATE)?.toLongOrNull()
        set(v) { setMeta(KEY_START_DATE, v?.toString()); changed() }

    /** Auto-sort: "off" (only rock-solid matches), "careful" or "bold". */
    var autopilot: String
        get() = meta(KEY_AUTOPILOT) ?: "off"
        set(v) = setMeta(KEY_AUTOPILOT, v)

    /** The confidence the current Auto-sort setting needs, or null when it's off. */
    val autopilotThreshold: Double?
        get() = when (autopilot) { "bold" -> 0.55; "careful" -> 0.75; else -> null }

    var lastSmsDate: Long
        get() = meta(KEY_LAST_SMS)?.toLongOrNull() ?: 0L
        set(v) = setMeta(KEY_LAST_SMS, v.toString())

    // ---- SMS ------------------------------------------------------------------------------

    /**
     * Records one M-Pesa SMS. Returns null if it isn't a transaction or was already recorded.
     * Pass [batch] = true during big imports to skip the reload after every message; call
     * [finishBatch] at the end.
     */
    @Synchronized
    fun ingest(body: String, receivedAt: Long, batch: Boolean = false, allowBeforeStart: Boolean = false): IngestResult? {
        var sms = MpesaParser.parse(body) ?: return null
        if (!allowBeforeStart) startDate?.let { start ->
            val t = sms.time?.atZone(zone)?.toInstant()?.toEpochMilli() ?: receivedAt
            if (t < start) return null // before tracking starts: ignored until you pull earlier history
        }
        val dedupe = sms.code?.let { "$it:${sms.kind}" } ?: ("body:" + sha1(MpesaParser.clean(body)))
        if (exists(dedupe)) return null

        val ts = sms.time?.atZone(zone)?.toInstant()?.toEpochMilli() ?: receivedAt
        var s = snapshot()
        val mpesa = s.role(SystemRole.MPESA)

        // Unknown wording: the balance change tells us which way the money went and how much.
        var outgoing = sms.outgoing
        if (sms.kind == TxKind.UNKNOWN) {
            val prev = s.entries.filter { it.smsBalance != null && it.timestamp <= ts }.maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
            if (prev != null && sms.balance != null) {
                val delta = sms.balance!! - prev.smsBalance!!
                outgoing = delta < 0
                sms = sms.copy(amount = abs(delta), fee = 0)
            } else outgoing = true
        }

        var guess: Guess? = null
        var categoryId: Long? = null
        var status = EntryStatus.AUTO
        if (Posting.needsCategory(sms.kind)) {
            if (sms.kind == TxKind.REVERSAL) {
                val original = s.entries.firstOrNull { it.code == sms.reversedCode && it.kind != TxKind.REVERSAL }
                categoryId = original?.categoryId?.takeIf { it != s.role(SystemRole.UNCATEGORIZED) }
                status = EntryStatus.CONFIRMED
            } else {
                val draft = Posting.fromSms(sms, ts, s::role, null, EntryStatus.NEEDS_REVIEW, outgoing)
                guess = s.guess(draft, autopilot = autopilotThreshold)
                categoryId = guess.autoAccountId
            }
        }
        val entry = Posting.fromSms(sms, ts, s::role, categoryId, status, outgoing)
            .let { if (it.status == EntryStatus.AUTO) it.copy(confidence = guess?.confidence) else it }
        val saved = insert(entry, dedupe)
        if (batch) snap = s.with(saved) else {
            changed(); reconcileMpesa()
            if (saved.status == EntryStatus.AUTO) saved.categoryId?.let { alertBudgets(listOf(it)) }
        }
        return IngestResult(saved, guess)
    }

    fun finishBatch() {
        changed()
        reconcileMpesa()
    }

    /**
     * Makes Morph's M-Pesa balance match M-Pesa's own: re-works the starting balance from the
     * earliest SMS, and turns every unexplained jump between two SMS into an item to sort
     * ("M-Pesa money out, no SMS found"). Gaps from the last 30 minutes wait, unless [force],
     * because the message that explains them may still be on its way. Returns the number of gaps.
     */
    @Synchronized
    fun reconcileMpesa(force: Boolean = false): Int {
        val s = snapshot()
        val mpesa = s.role(SystemRole.MPESA)
        val unc = s.role(SystemRole.UNCATEGORIZED)
        val latest = s.lastSms?.timestamp ?: return 0
        val pendingGaps = db.rawQuery("SELECT id FROM entries WHERE dedupe LIKE 'gap:%' AND status = ?", arrayOf(EntryStatus.NEEDS_REVIEW.name)).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
        val openings = s.entries.filter { it.source == EntrySource.SYSTEM && it.categoryId == mpesa }
        val base = s.entries.filter { it.id !in pendingGaps && it !in openings }
        val r = Reconcile.mpesa(base, mpesa, if (force) Long.MAX_VALUE else latest - 30 * 60 * 1000) ?: return 0
        val unchanged = openings.size == 1 && openings[0].lines.first { it.accountId == mpesa }.amount == r.opening &&
            pendingGaps.isEmpty() && r.gaps.isEmpty()
        if (unchanged) return 0
        db.beginTransaction()
        try {
            for (id in pendingGaps + openings.map { it.id }) {
                db.delete("lines", "entry_id = ?", arrayOf(id.toString()))
                db.delete("entries", "id = ?", arrayOf(id.toString()))
            }
            insert(Posting.opening(r.openingTimestamp, mpesa, true, r.opening, s::role), null)
            r.gaps.forEachIndexed { i, g ->
                insert(
                    Entry(
                        timestamp = g.timestamp,
                        description = if (g.amount > 0) "M-Pesa money in (no SMS found)" else "M-Pesa money out (no SMS found)",
                        lines = listOf(Line(mpesa, g.amount), Line(unc, -g.amount)),
                        status = EntryStatus.NEEDS_REVIEW, source = EntrySource.MANUAL, kind = TxKind.UNKNOWN,
                        categoryId = unc,
                        note = "Your M-Pesa balance jumped here without an SMS Morph could read. Sort it if you remember what it was.",
                    ),
                    "gap:${g.atEntryId}:${System.nanoTime()}:$i",
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return r.gaps.size
    }

    // ---- sorting --------------------------------------------------------------------------

    /** Files [entryId] under [accountId]. Returns the stars earned. */
    @Synchronized
    fun categorize(entryId: Long, accountId: Long, now: Long = System.currentTimeMillis()): Game.Reward {
        val s = snapshot()
        val e = s.entries.firstOrNull { it.id == entryId } ?: return Game.Reward(0, emptyList())
        val wasPending = e.status == EntryStatus.NEEDS_REVIEW
        if (e.status == EntryStatus.CONFIRMED && e.categoryId == accountId) return Game.Reward(0, emptyList())
        val updated = Posting.recategorize(e, accountId, EntryStatus.CONFIRMED)
        replaceLines(updated)
        val rewardIds = mutableListOf<Long>()
        // Changing your mind right after sorting (via Undo or Back) shouldn't farm stars.
        val recentlySorted = undo[entryId]?.let { now - it.at < 10 * 60 * 1000 } == true

        val reward = when {
            recentlySorted && !wasPending -> Game.Reward(0, listOf("Changed"))
            wasPending -> {
                val left = s.pending.count { it.id != entryId }
                val todayDate = LocalDate.now(zone)
                val today = todayDate.toString()
                // Today's transactions are all sorted now (and there were some today).
                val todays = s.entries.filter { it.source != EntrySource.SYSTEM && dayOf(it.timestamp) == todayDate }
                val dayDone = todays.isNotEmpty() && todays.none { it.id != entryId && it.status == EntryStatus.NEEDS_REVIEW }
                val dayBonusAlready = (DAY_PREFIX + today) in s.bonusDays
                Game.rewardForSorting(e.timestamp, now, left == 0, today in s.bonusDays, dayDone, dayBonusAlready)
                    .also {
                        if (left == 0 && today !in s.bonusDays) rewardIds += addReward(0, BONUS_PREFIX + today)
                        if (dayDone && !dayBonusAlready) rewardIds += addReward(0, BONUS_PREFIX + DAY_PREFIX + today)
                    }
            }
            e.categoryId == accountId -> Game.Reward(1, listOf("Confirmed"))
            else -> Game.Reward(1, listOf("Taught Morph"))
        }
        if (reward.stars > 0) rewardIds += addReward(reward.stars, reward.reasons.joinToString(" · "), now)
        remember(entryId, undo[entryId]?.takeIf { recentlySorted }?.before ?: e, rewardIds, now)
        changed()
        alertBudgets(listOf(accountId))
        if (e.counterpartyKey != null) refilePending(e.counterpartyKey!!)
        return reward
    }

    /** After you teach Morph something, other waiting transactions from the same place may now be obvious. */
    private fun refilePending(key: String) {
        val s = snapshot()
        var any = false
        for (p in s.pending.filter { it.counterpartyKey == key }) {
            val g = s.guess(p, autopilot = autopilotThreshold)
            val auto = g.autoAccountId ?: continue
            replaceLines(Posting.recategorize(p, auto, EntryStatus.AUTO).copy(confidence = g.confidence)); any = true
        }
        if (any) changed()
    }

    /**
     * Runs Auto-sort over everything still waiting. Each one is judged on what Morph knew before
     * this run, so one guess can't snowball into the next. Returns how many it filed.
     */
    @Synchronized
    fun autoSortPending(): Int {
        val threshold = autopilotThreshold ?: return 0
        val s = snapshot()
        var n = 0
        for (p in s.pending) {
            val g = s.guess(p, autopilot = threshold)
            val auto = g.autoAccountId ?: continue
            replaceLines(Posting.recategorize(p, auto, EntryStatus.AUTO).copy(confidence = g.confidence)); n++
        }
        if (n > 0) changed()
        return n
    }

    /**
     * "Sort everything for me": files the whole waiting pile in rounds. First what Morph is very
     * sure of, then (having learned from that) what it's fairly sure of, then everything else gets
     * its best guess. Anything under 75% is flagged 🤔 to check; forced guesses barely teach Morph.
     * Returns (filed, unsure).
     */
    @Synchronized
    fun sortEverything(): Pair<Int, Int> {
        var filed = 0
        for (threshold in listOf(0.75, 0.6, 0.45, 0.0)) {
            val s = snapshot()
            if (s.pending.isEmpty()) break
            var n = 0
            for (p in s.pending) {
                val g = s.guess(p, limit = 1, autopilot = threshold)
                val auto = g.autoAccountId ?: g.suggestions.firstOrNull()?.accountId?.takeIf { threshold == 0.0 } ?: continue
                // Forced picks keep their (low) confidence, so they're flagged and weigh little.
                replaceLines(Posting.recategorize(p, auto, EntryStatus.AUTO).copy(confidence = minOf(g.confidence, if (threshold == 0.0) 0.5 else 1.0)))
                n++
            }
            if (n > 0) changed()
            filed += n
        }
        return filed to snapshot().unsure.size
    }

    /**
     * "Keep improving": with everything learned since, re-check the waiting pile (Auto-sort) and
     * Morph's own earlier guesses. A guess moves only when Morph is now clearly sure (75%+) of a
     * different place and surer than before. Your own choices are never touched.
     * Runs at most every 6 hours. Returns how many changed.
     */
    @Synchronized
    fun relearnIfDue(force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        if (!force && now - (meta(KEY_RELEARN)?.toLongOrNull() ?: 0L) < 6 * 3600 * 1000L) return 0
        var changedCount = autoSortPending()
        val s = snapshot()
        for (e in s.entries.filter { it.status == EntryStatus.AUTO && it.source == EntrySource.SMS }) {
            val g = s.reconsider(e)
            val best = g.suggestions.firstOrNull()?.accountId ?: continue
            if (best == e.categoryId || g.confidence < 0.75 || g.confidence <= (e.confidence ?: 0.0)) continue
            if (s.categories(e).size > 1) continue // split entries were shaped by hand
            replaceLines(Posting.recategorize(e, best, EntryStatus.AUTO).copy(confidence = g.confidence))
            changedCount++
        }
        setMeta(KEY_RELEARN, now.toString())
        setMeta(KEY_RELEARN_RESULT, "$changedCount")
        if (changedCount > 0) changed()
        return changedCount
    }

    /** How many the last "Keep improving" run changed, and when. */
    val lastRelearn: Pair<Long, Int>?
        get() = meta(KEY_RELEARN)?.toLongOrNull()?.let { it to (meta(KEY_RELEARN_RESULT)?.toIntOrNull() ?: 0) }

    /** "Yes, Morph got it right": turns an auto-filed entry into a confirmed one. */
    fun confirm(entryId: Long): Game.Reward {
        val e = snapshot().entries.firstOrNull { it.id == entryId } ?: return Game.Reward(0, emptyList())
        val id = e.categoryId ?: return Game.Reward(0, emptyList())
        return categorize(entryId, id)
    }

    private data class UndoInfo(val before: Entry, val rewardIds: List<Long>, val at: Long)

    /** What each recent sort replaced, so it can be undone. Kept in memory for the last 50 sorts. */
    private val undo = object : LinkedHashMap<Long, UndoInfo>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, UndoInfo>?) = size > 50
    }

    private fun remember(entryId: Long, before: Entry, rewardIds: List<Long>, at: Long) {
        val earlier = undo[entryId]
        undo[entryId] = UndoInfo(before, (earlier?.rewardIds.orEmpty().takeIf { earlier?.before == before } ?: emptyList()) + rewardIds, at)
    }

    /** Puts an entry back exactly as it was before it was sorted, and takes back the stars. */
    @Synchronized
    fun undoSort(entryId: Long): Boolean {
        val info = undo.remove(entryId)
        val s = snapshot()
        if (info != null) {
            replaceLines(info.before)
            info.rewardIds.forEach { db.delete("rewards", "id = ?", arrayOf(it.toString())) }
        } else {
            // App was restarted since: the best we can do is send it back to the Sort tab.
            val e = s.entries.firstOrNull { it.id == entryId } ?: return false
            if (e.source != EntrySource.SMS) return false
            replaceLines(Posting.recategorize(e, s.role(SystemRole.UNCATEGORIZED), EntryStatus.NEEDS_REVIEW))
        }
        changed()
        return true
    }

    /** How many other transactions are waiting from the same person/place as [entryId]. */
    fun similarPending(entryId: Long): List<Entry> {
        val s = snapshot()
        val key = s.entries.firstOrNull { it.id == entryId }?.counterpartyKey ?: return emptyList()
        return s.pending.filter { it.id != entryId && it.counterpartyKey == key }
    }

    /** Files [entryId] and every other waiting transaction from the same counterparty under [accountId]. */
    fun categorizeAllSimilar(entryId: Long, accountId: Long): Game.Reward {
        val others = similarPending(entryId)
        var stars = 0
        val reasons = linkedSetOf<String>()
        for (id in listOf(entryId) + others.map { it.id }) {
            val r = categorize(id, accountId)
            stars += r.stars; reasons += r.reasons
        }
        return Game.Reward(stars, reasons.toList())
    }

    fun setNote(entryId: Long, note: String?) {
        db.update("entries", ContentValues().apply { put("note", note?.trim()?.ifEmpty { null }) }, "id = ?", arrayOf(entryId.toString()))
        changed()
    }

    /** Changes a hand-added transaction completely. M-Pesa ones keep their amount so balances stay true. */
    fun updateManual(entryId: Long, timestamp: Long, description: String, amount: Long, fromId: Long, toId: Long) {
        val e = snapshot().entries.firstOrNull { it.id == entryId } ?: return
        require(e.source == EntrySource.MANUAL) { "Only hand-added transactions can change amount" }
        val fresh = Posting.manual(timestamp, description, amount, fromId, toId)
        replaceLines(fresh.copy(id = entryId, note = e.note))
        db.update("entries", ContentValues().apply {
            put("ts", timestamp); put("counterparty", fresh.counterparty); put("cp_key", fresh.counterpartyKey)
        }, "id = ?", arrayOf(entryId.toString()))
        changed()
    }

    /**
     * Sorts a waiting transaction by splitting it, e.g. Naivas 2,000 = Groceries 1,500 + Household 500.
     * Earns the same stars as sorting (and can be undone the same way).
     */
    fun splitAndSort(entryId: Long, parts: List<Pair<Long, Long>>): Game.Reward {
        val main = parts.filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: return Game.Reward(0, emptyList())
        val reward = categorize(entryId, main)
        split(entryId, parts)
        alertBudgets(parts.map { it.first })
        return reward
    }

    /** Splits one transaction across several accounts, e.g. a supermarket bill into Groceries + Household. */
    fun split(entryId: Long, parts: List<Pair<Long, Long>>) {
        val s = snapshot()
        val e = s.entries.firstOrNull { it.id == entryId } ?: return
        replaceLines(Posting.split(e, parts, s.structural))
        changed()
    }

    // ---- starting over & backups ----------------------------------------------------------

    /**
     * Wipes transactions (and optionally accounts) and sends you back to setup, where you can pull
     * history again. A copy of everything is saved first, in Morph's private backups folder.
     */
    @Synchronized
    fun startOver(keepAccounts: Boolean) {
        saveLocalCopy("before-start-over")
        val theme = theme
        val backupUri = meta(KEY_BACKUP_URI)
        db.beginTransaction()
        try {
            for (t in listOf("lines", "entries", "budget_moves", "rewards", "meta", "allocations")) db.delete(t, null, null)
            if (!keepAccounts) db.delete("accounts", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (!keepAccounts) seedIfEmpty(db)
        undo.clear()
        setMeta(KEY_THEME, theme)
        if (backupUri != null) setMeta(KEY_BACKUP_URI, backupUri)
        changed()
    }

    /** Where automatic backups go (a file you picked, e.g. on Google Drive). */
    var backupUri: String?
        get() = meta(KEY_BACKUP_URI)
        set(v) = setMeta(KEY_BACKUP_URI, v)

    val lastBackup: Long get() = meta(KEY_LAST_BACKUP)?.toLongOrNull() ?: 0L

    /** How often automatic backup runs, in days: 1 (daily, default), 7 (weekly) or 30 (monthly). */
    var backupEveryDays: Int
        get() = meta(KEY_BACKUP_EVERY)?.toIntOrNull() ?: 1
        set(v) = setMeta(KEY_BACKUP_EVERY, v.toString())

    /** True when the chosen interval has passed since the last backup (a little early is fine). */
    val backupDue: Boolean
        get() = System.currentTimeMillis() - lastBackup > backupEveryDays * 24L * 3600 * 1000 - 2L * 3600 * 1000
    val backupError: String? get() = meta(KEY_BACKUP_ERROR)

    /** Writes a full backup to the chosen file. Returns null on success, or what went wrong. */
    fun backupNow(): String? {
        saveLocalCopy("latest")
        val uri = backupUri ?: return "No backup file chosen yet"
        return try {
            val out = appContext.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: error("The file can't be opened")
            out.use { it.write(exportJson().toByteArray()) }
            setMeta(KEY_LAST_BACKUP, System.currentTimeMillis().toString())
            setMeta(KEY_BACKUP_ERROR, null)
            null
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            setMeta(KEY_BACKUP_ERROR, msg)
            msg
        }
    }

    private fun saveLocalCopy(name: String) {
        runCatching {
            val dir = File(appContext.filesDir, "backups").apply { mkdirs() }
            File(dir, "morph-$name.json").writeText(exportJson())
        }
    }

    // ---- manual entries & accounts ----------------------------------------------------------

    fun addManual(timestamp: Long, description: String, amount: Long, fromId: Long, toId: Long) {
        insert(Posting.manual(timestamp, description, amount, fromId, toId), null)
        addReward(1, "Recorded by hand")
        changed()
        alertBudgets(listOf(toId))
    }

    fun deleteEntry(id: Long) {
        db.delete("lines", "entry_id = ?", arrayOf(id.toString()))
        db.delete("entries", "id = ?", arrayOf(id.toString()))
        changed()
    }

    /** Sets how much an account holds (or how much you owe) right now, replacing any earlier starting balance. */
    fun setOpening(accountId: Long, amount: Long, timestamp: Long = 0L) {
        val s = snapshot()
        val a = s.byId[accountId] ?: return
        val existing = s.entries.filter { it.source == EntrySource.SYSTEM && it.categoryId == accountId }
        db.beginTransaction()
        try {
            existing.forEach {
                db.delete("lines", "entry_id = ?", arrayOf(it.id.toString()))
                db.delete("entries", "id = ?", arrayOf(it.id.toString()))
            }
            if (amount != 0L) insert(Posting.opening(timestamp, accountId, a.type.debitNormal, amount, s::role), null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /**
     * "This account has exactly [target] right now." Records the difference against Opening balance,
     * so it never shows up as spending or income.
     */
    fun setBalanceNow(accountId: Long, target: Long) {
        val s = snapshot()
        val a = s.byId[accountId] ?: return
        val diff = target - s.balance(a)
        if (diff == 0L) return
        val e = Posting.opening(System.currentTimeMillis(), accountId, a.type.debitNormal, diff, s::role)
        insert(e.copy(description = "Balance set to ${Money.format(target)}"), null)
        changed()
    }

    fun saveAccount(a: Account): Long {
        val v = ContentValues().apply {
            put("name", a.name.trim()); put("emoji", a.emoji); put("grp", a.group.name)
            put("budget", a.budget); put("period", a.period.name)
            put("role", a.role?.name); put("hint", a.hint)
            put("archived", if (a.archived) 1 else 0); put("sort", a.sortOrder)
            put("section", a.section?.trim()?.ifEmpty { null })
        }
        val id = if (a.id == 0L) db.insert("accounts", null, v) else {
            db.update("accounts", v, "id = ?", arrayOf(a.id.toString())); a.id
        }
        changed()
        return id
    }

    fun moveBudget(periodKey: String, fromId: Long, toId: Long, amount: Long) {
        db.insert("budget_moves", null, ContentValues().apply {
            put("period_key", periodKey); put("from_id", fromId); put("to_id", toId)
            put("amount", amount); put("ts", System.currentTimeMillis())
        })
        changed()
    }

    /** "Match M-Pesa now": reconcile including the last 30 minutes. */
    fun fixMpesaGap() {
        reconcileMpesa(force = true)
    }


    fun stars(): Int = snapshot().stars

    // ---- academy --------------------------------------------------------------------------

    fun academyProgress(): Map<String, Progress> =
        db.rawQuery("SELECT card_id, box, due, seen, right_n, wrong_n FROM academy_progress", null).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getString(0), Progress(c.getString(0), c.getInt(1), c.str(2)?.let { LocalDate.parse(it) }, c.getInt(3), c.getInt(4), c.getInt(5)))
            }
        }

    /** Saves one answered card and returns the stars it earned: 1 for the card, 1 more for a right quiz answer. */
    fun recordCard(cardId: String, outcome: Outcome, quizRight: Boolean = false): Int {
        val today = dayOf(System.currentTimeMillis())
        val before = academyProgress()[cardId] ?: Progress(cardId)
        val p = Academy.review(before, outcome, today)
        db.insertWithOnConflict("academy_progress", null, ContentValues().apply {
            put("card_id", p.cardId); put("box", p.box); put("due", p.due?.toString()); put("seen", p.seen)
            put("right_n", p.right); put("wrong_n", p.wrong)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        val stars = 1 + if (quizRight) 1 else 0
        addReward(stars, "learn:$cardId")
        return stars
    }

    /** Ends today's lesson: +3 stars the first time each day, which also counts towards the learning streak. */
    fun finishLesson(): Int {
        val day = dayOf(System.currentTimeMillis())
        val first = day !in snapshot().learnDays
        if (first) addReward(LESSON_BONUS, "$LEARN_PREFIX$day")
        changed()
        return if (first) LESSON_BONUS else 0
    }

    /** Called when you leave the Academy, so stars from cards show up everywhere. */
    fun academyClosed() = changed()

    // ---- budget alerts ----------------------------------------------------------------------

    /** Budget alerts on/off (on by default). */
    var budgetAlerts: Boolean
        get() = meta(KEY_BUDGET_ALERTS) != "off"
        set(v) = setMeta(KEY_BUDGET_ALERTS, if (v) null else "off")

    /**
     * After spending on [accountIds]: notify once per account, per day/month and per level, when a
     * budget becomes "Almost out" and when it goes over. Never nags about the same thing twice.
     */
    private fun alertBudgets(accountIds: Collection<Long>) {
        if (!budgetAlerts) return
        val s = snapshot()
        val today = dayOf(System.currentTimeMillis())
        for (id in accountIds.toSet()) {
            val h = s.health(id, today) ?: continue
            val level = h.level
            if (level != com.morphlabs.morph.core.budget.Level.CRITICAL && level != com.morphlabs.morph.core.budget.Level.OVER) continue
            val key = "alert:$id:${h.periodKey}:${level.name}"
            if (meta(key) != null) continue
            setMeta(key, "1")
            val a = s.byId[id] ?: continue
            com.morphlabs.morph.notify.Notifier.budgetAlert(appContext, a, h)
        }
    }

    // ---- cash ---------------------------------------------------------------------------------

    /**
     * Says what cash from a withdrawal is for, in one go: e.g. Food 500 + Transport 300 + Family 1,000.
     * Each part becomes a cash spend; whatever isn't spread simply stays in Cash.
     */
    fun spreadCash(withdrawalId: Long, parts: List<Pair<Long, Long>>) {
        val s = snapshot()
        val cash = s.role(SystemRole.CASH)
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for ((accountId, amount) in parts.filter { it.second > 0 }) {
                val a = s.byId[accountId] ?: continue
                insert(Posting.manual(now, "${a.name} (cash)", amount, cash, accountId).copy(counterparty = "Cash", counterpartyKey = "cash:$withdrawalId"), null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val n = parts.count { it.second > 0 }
        if (n > 0) addReward(n, "Spread cash")
        changed()
        alertBudgets(parts.map { it.first })
    }

    /** "Keep it as cash": stop asking about this withdrawal. */
    fun keepAsCash(withdrawalId: Long) {
        val done = (meta(KEY_CASH_DONE)?.split(',').orEmpty() + withdrawalId.toString()).filter { it.isNotBlank() }.takeLast(100)
        setMeta(KEY_CASH_DONE, done.joinToString(","))
        changed()
    }

    // ---- envelopes ----------------------------------------------------------------------------

    /** Turns envelopes on (from now) or off. Turning them off keeps what you assigned, for next time. */
    fun setEnvelopes(on: Boolean) {
        if (on && meta(KEY_ENVELOPES_START) == null) setMeta(KEY_ENVELOPES_START, System.currentTimeMillis().toString())
        if (!on) { setMeta(KEY_ENVELOPES_START, null); setMeta(KEY_BUDGET_VIEW, null) }
        changed()
    }

    fun setBudgetView(on: Boolean) {
        setMeta(KEY_BUDGET_VIEW, if (on) "1" else null)
        changed()
    }

    /** Puts [amount] of real money into an envelope (negative takes it back to To assign). */
    fun assign(accountId: Long, amount: Long) {
        if (amount == 0L) return
        db.insert("allocations", null, ContentValues().apply {
            put("ts", System.currentTimeMillis()); put("account_id", accountId); put("amount", amount)
        })
        changed()
    }

    /** Moves money from one envelope to another. */
    fun moveBetweenEnvelopes(fromId: Long, toId: Long, amount: Long) {
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            db.insert("allocations", null, ContentValues().apply { put("ts", now); put("account_id", fromId); put("amount", -amount) })
            db.insert("allocations", null, ContentValues().apply { put("ts", now); put("account_id", toId); put("amount", amount) })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /** "Fill my envelopes": tops each up to its daily/monthly amount, as far as To assign goes. Returns what was assigned. */
    fun fillEnvelopes(): Long {
        val book = snapshot().book ?: return 0
        val plan = book.fillPlan(dayOf(System.currentTimeMillis()))
        if (plan.isEmpty()) return 0
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for ((id, amt) in plan) db.insert("allocations", null, ContentValues().apply { put("ts", now); put("account_id", id); put("amount", amt) })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return plan.sumOf { it.second }
    }

    // ---- backup ---------------------------------------------------------------------------

    fun exportJson(): String = Backup.export(db)

    fun importJson(json: String) {
        Backup.import(db, json)
        changed()
    }

    // ---- storage details --------------------------------------------------------------------

    /** True when this M-Pesa SMS is already a transaction in Morph. */
    fun isKnown(body: String): Boolean {
        val sms = MpesaParser.parse(body) ?: return false
        return exists(sms.code?.let { "$it:${sms.kind}" } ?: ("body:" + sha1(MpesaParser.clean(body))))
    }

    private fun exists(dedupe: String): Boolean =
        db.rawQuery("SELECT 1 FROM entries WHERE dedupe = ? LIMIT 1", arrayOf(dedupe)).use { it.moveToFirst() }

    private fun insert(e: Entry, dedupe: String?): Entry {
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("entries", null, ContentValues().apply {
                put("ts", e.timestamp); put("description", e.description); put("status", e.status.name); put("source", e.source.name)
                put("counterparty", e.counterparty); put("cp_key", e.counterpartyKey); put("kind", e.kind?.name); put("code", e.code)
                put("sms", e.smsBody); put("sms_balance", e.smsBalance); put("category_id", e.categoryId); put("dedupe", dedupe)
                put("note", e.note); put("confidence", e.confidence)
            })
            for (l in e.lines) db.insertOrThrow("lines", null, ContentValues().apply {
                put("entry_id", id); put("account_id", l.accountId); put("amount", l.amount)
            })
            db.setTransactionSuccessful()
            return e.copy(id = id)
        } finally {
            db.endTransaction()
        }
    }

    private fun replaceLines(e: Entry) {
        db.beginTransaction()
        try {
            db.delete("lines", "entry_id = ?", arrayOf(e.id.toString()))
            for (l in e.lines) db.insertOrThrow("lines", null, ContentValues().apply {
                put("entry_id", e.id); put("account_id", l.accountId); put("amount", l.amount)
            })
            db.update("entries", ContentValues().apply {
                put("status", e.status.name); put("category_id", e.categoryId); put("description", e.description)
                put("confidence", if (e.status == EntryStatus.AUTO) e.confidence else null)
            }, "id = ?", arrayOf(e.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun addReward(stars: Int, reason: String, ts: Long = System.currentTimeMillis()): Long =
        db.insert("rewards", null, ContentValues().apply { put("ts", ts); put("stars", stars); put("reason", reason) })

    private fun seedIfEmpty(d: SQLiteDatabase) {
        val count = d.rawQuery("SELECT COUNT(*) FROM accounts", null).use { it.moveToFirst(); it.getInt(0) }
        if (count > 0) return
        d.beginTransaction()
        try {
            for (a in DefaultAccounts.all()) d.insert("accounts", null, ContentValues().apply {
                put("name", a.name); put("emoji", a.emoji); put("grp", a.group.name); put("budget", a.budget)
                put("period", a.period.name); put("role", a.role?.name); put("hint", a.hint); put("archived", 0); put("sort", a.sortOrder)
            })
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    private fun load(): Snapshot {
        val accounts = db.rawQuery("SELECT id, name, emoji, grp, budget, period, role, hint, archived, sort, section FROM accounts ORDER BY sort, id", null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Account(
                        id = c.getLong(0), name = c.getString(1), emoji = c.getString(2),
                        group = enumOr(c.getString(3), AccountGroup.DISCRETIONARY),
                        budget = c.getLong(4), period = enumOr(c.getString(5), BudgetPeriod.NONE),
                        role = c.str(6)?.let { r -> runCatching { SystemRole.valueOf(r) }.getOrNull() },
                        hint = c.str(7), archived = c.getInt(8) == 1, sortOrder = c.getInt(9), section = c.str(10),
                    )
                )
            }
        }
        val lines = HashMap<Long, MutableList<Line>>()
        db.rawQuery("SELECT entry_id, account_id, amount FROM lines", null).use { c ->
            while (c.moveToNext()) lines.getOrPut(c.getLong(0)) { mutableListOf() } += Line(c.getLong(1), c.getLong(2))
        }
        val entries = db.rawQuery(
            "SELECT id, ts, description, status, source, counterparty, cp_key, kind, code, sms, sms_balance, category_id, note, confidence FROM entries ORDER BY ts, id", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val ls = lines[id].orEmpty()
                    if (ls.isEmpty() || ls.sumOf { it.amount } != 0L) continue // never let one bad row break the app
                    add(
                        Entry(
                            id = id, timestamp = c.getLong(1), description = c.getString(2),
                            lines = ls, status = enumOr(c.getString(3), EntryStatus.CONFIRMED),
                            source = enumOr(c.getString(4), EntrySource.MANUAL),
                            counterparty = c.str(5), counterpartyKey = c.str(6),
                            kind = c.str(7)?.let { k -> runCatching { TxKind.valueOf(k) }.getOrNull() },
                            code = c.str(8), smsBody = c.str(9),
                            smsBalance = if (c.isNull(10)) null else c.getLong(10),
                            categoryId = if (c.isNull(11)) null else c.getLong(11),
                            note = c.str(12),
                            confidence = if (c.isNull(13)) null else c.getDouble(13),
                        )
                    )
                }
            }
        }
        val moves = db.rawQuery("SELECT id, period_key, from_id, to_id, amount, ts FROM budget_moves", null).use { c ->
            buildList { while (c.moveToNext()) add(BudgetMove(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5))) }
        }
        val stars = db.rawQuery("SELECT COALESCE(SUM(stars), 0) FROM rewards", null).use { it.moveToFirst(); it.getInt(0) }
        val bonusDays = db.rawQuery("SELECT reason FROM rewards WHERE reason LIKE ?", arrayOf("$BONUS_PREFIX%")).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0).removePrefix(BONUS_PREFIX)) }
        }
        val learnDays = db.rawQuery("SELECT reason FROM rewards WHERE reason LIKE ?", arrayOf("$LEARN_PREFIX%")).use { c ->
            buildSet { while (c.moveToNext()) runCatching { add(LocalDate.parse(c.getString(0).removePrefix(LEARN_PREFIX))) } }
        }
        val allocations = db.rawQuery("SELECT id, ts, account_id, amount FROM allocations ORDER BY ts, id", null).use { c ->
            buildList { while (c.moveToNext()) add(Allocation(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3))) }
        }
        val envStart = meta(KEY_ENVELOPES_START)?.toLongOrNull()
        val cashDone = meta(KEY_CASH_DONE)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        return Snapshot(accounts, entries, moves, allocations, envStart, meta(KEY_BUDGET_VIEW) == "1", cashDone, startDate, stars, bonusDays, zone, learnDays)
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

    private inline fun <reified T : Enum<T>> enumOr(s: String?, fallback: T): T =
        s?.let { v -> runCatching { enumValueOf<T>(v) }.getOrNull() } ?: fallback

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    companion object {
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_THEME = "theme"
        private const val KEY_LAST_SMS = "last_sms_date"
        private const val BONUS_PREFIX = "inbox-zero:"
        private const val LEARN_PREFIX = "learn-day:"
        private const val LESSON_BONUS = 3
        /** Stored after BONUS_PREFIX, so bonusDays holds "day-done:2026-10-24" for the daily bonus. */
        private const val DAY_PREFIX = "day-done:"
        private const val KEY_BACKUP_URI = "backup_uri"
        private const val KEY_AUTOPILOT = "autopilot"
        private const val KEY_BACKUP_EVERY = "backup_every_days"
        private const val KEY_RELEARN = "relearn_at"
        private const val KEY_BUDGET_ALERTS = "budget_alerts"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_RELEARN_RESULT = "relearn_result"
        private const val KEY_ENVELOPES_START = "envelopes_start"
        private const val KEY_BUDGET_VIEW = "budget_view"
        private const val KEY_CASH_DONE = "cash_done"
        private const val KEY_LAST_BACKUP = "last_backup"
        private const val KEY_BACKUP_ERROR = "backup_error"

        @Volatile private var instance: MorphRepo? = null

        fun get(context: Context): MorphRepo =
            instance ?: synchronized(this) { instance ?: MorphRepo(context).also { instance = it } }
    }
}
