package com.morphlabs.morph.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.morphlabs.morph.core.budget.BudgetMove
import com.morphlabs.morph.core.defaults.DefaultAccounts
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.learn.Guess
import com.morphlabs.morph.core.ledger.Posting
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
    fun ingest(body: String, receivedAt: Long, batch: Boolean = false): IngestResult? {
        var sms = MpesaParser.parse(body) ?: return null
        val dedupe = sms.code?.let { "$it:${sms.kind}" } ?: ("body:" + sha1(MpesaParser.clean(body)))
        if (exists(dedupe)) return null

        val ts = sms.time?.atZone(zone)?.toInstant()?.toEpochMilli() ?: receivedAt
        var s = snapshot()
        val mpesa = s.role(SystemRole.MPESA)

        // Very first M-Pesa message: work out the starting balance so Morph matches M-Pesa exactly.
        val hasOpening = s.entries.any { it.source == EntrySource.SYSTEM && it.categoryId == mpesa }
        if (!hasOpening && s.lastSms == null && sms.balance != null) {
            val effect = when {
                sms.kind == TxKind.FULIZA_DRAW -> sms.amount
                sms.outgoing || sms.kind == TxKind.UNKNOWN -> -(sms.amount + sms.fee)
                else -> sms.amount - sms.fee
            }
            val opening = Posting.opening(ts - 1, mpesa, true, sms.balance!! - effect, s::role)
            s = s.with(insert(opening, null))
        }

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
        if (batch) snap = s.with(saved) else changed()
        return IngestResult(saved, guess)
    }

    fun finishBatch() = changed()

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
                val today = LocalDate.now(zone).toString()
                Game.rewardForSorting(e.timestamp, now, left == 0, today in s.bonusDays)
                    .also { if (left == 0 && today !in s.bonusDays) rewardIds += addReward(0, BONUS_PREFIX + today) }
            }
            e.categoryId == accountId -> Game.Reward(1, listOf("Confirmed"))
            else -> Game.Reward(1, listOf("Taught Morph"))
        }
        if (reward.stars > 0) rewardIds += addReward(reward.stars, reward.reasons.joinToString(" · "), now)
        remember(entryId, undo[entryId]?.takeIf { recentlySorted }?.before ?: e, rewardIds, now)
        changed()
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
            for (t in listOf("lines", "entries", "budget_moves", "rewards", "meta")) db.delete(t, null, null)
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

    /** Adds a to-be-sorted entry for M-Pesa money Morph never saw an SMS for, so balances match again. */
    fun fixMpesaGap() {
        val s = snapshot()
        val gap = s.mpesaGap
        if (gap == 0L) return
        val mpesa = s.role(SystemRole.MPESA)
        val unc = s.role(SystemRole.UNCATEGORIZED)
        val ts = s.lastSms?.timestamp ?: System.currentTimeMillis()
        val entry = Entry(
            timestamp = ts - 1,
            description = if (gap > 0) "M-Pesa money in (no SMS found)" else "M-Pesa spending (no SMS found)",
            lines = listOf(Line(mpesa, gap), Line(unc, -gap)),
            status = EntryStatus.NEEDS_REVIEW,
            source = EntrySource.MANUAL,
            kind = TxKind.UNKNOWN,
            categoryId = unc,
        )
        insert(entry, null)
        changed()
    }

    fun stars(): Int = snapshot().stars

    // ---- backup ---------------------------------------------------------------------------

    fun exportJson(): String = Backup.export(db)

    fun importJson(json: String) {
        Backup.import(db, json)
        changed()
    }

    // ---- storage details --------------------------------------------------------------------

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
        return Snapshot(accounts, entries, moves, stars, bonusDays, zone)
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
        private const val KEY_BACKUP_URI = "backup_uri"
        private const val KEY_AUTOPILOT = "autopilot"
        private const val KEY_LAST_BACKUP = "last_backup"
        private const val KEY_BACKUP_ERROR = "backup_error"

        @Volatile private var instance: MorphRepo? = null

        fun get(context: Context): MorphRepo =
            instance ?: synchronized(this) { instance ?: MorphRepo(context).also { instance = it } }
    }
}
