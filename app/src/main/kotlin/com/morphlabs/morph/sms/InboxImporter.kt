package com.morphlabs.morph.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import com.morphlabs.morph.data.MorphRepo

/**
 * Reads M-Pesa messages already in your SMS inbox: all history at setup, then anything that
 * arrived while Morph wasn't running (a safety net for phones that kill background apps).
 */
object InboxImporter {

    data class Result(val read: Int, val added: Int, val toSort: Int)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** @param sinceMillis only messages newer than this; 0 for everything. */
    fun import(context: Context, sinceMillis: Long, onProgress: (Int) -> Unit = {}): Result {
        if (!hasPermission(context)) return Result(0, 0, 0)
        val repo = MorphRepo.get(context)
        var read = 0
        var added = 0
        var toSort = 0
        var newest = repo.lastSmsDate
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "UPPER(${Telephony.Sms.ADDRESS}) IN ('MPESA', 'M-PESA') AND ${Telephony.Sms.DATE} > ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return Result(0, 0, 0)
        cursor.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(1) ?: continue
                val date = c.getLong(2)
                read++
                val r = repo.ingest(body, date, batch = true)
                if (r != null) {
                    added++
                    if (r.entry.status == com.morphlabs.morph.core.model.EntryStatus.NEEDS_REVIEW) toSort++
                }
                if (date > newest) newest = date
                if (read % 25 == 0) onProgress(read)
            }
        }
        repo.finishBatch()
        if (newest > repo.lastSmsDate) repo.lastSmsDate = newest
        return Result(read, added, toSort)
    }
}
