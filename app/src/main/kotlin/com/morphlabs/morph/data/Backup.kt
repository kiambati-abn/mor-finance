package com.morphlabs.morph.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Whole-database export/import as one JSON file you can keep on Google Drive or send to a new phone. */
internal object Backup {

    private val tables = listOf("accounts", "entries", "lines", "budget_moves", "rewards", "meta", "allocations", "academy_progress")
    private const val FORMAT = 1

    fun export(db: SQLiteDatabase): String {
        val root = JSONObject()
        root.put("app", "morph")
        root.put("format", FORMAT)
        root.put("dbVersion", MorphDb.VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        for (t in tables) {
            val rows = JSONArray()
            db.rawQuery("SELECT * FROM $t", null).use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(name, c.getDouble(i))
                            else -> row.put(name, c.getString(i))
                        }
                    }
                    rows.put(row)
                }
            }
            root.put(t, rows)
        }
        return root.toString()
    }

    /** Replaces everything with the backup's contents. Throws (and changes nothing) if the file isn't a Morph backup. */
    fun import(db: SQLiteDatabase, json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "morph") { "This file isn't a Morph backup." }
        require(root.optInt("dbVersion") <= MorphDb.VERSION) { "This backup is from a newer Morph. Update the app first." }
        db.beginTransaction()
        try {
            for (t in tables) db.delete(t, null, null)
            for (t in tables) {
                val rows = root.optJSONArray(t) ?: continue
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val v = ContentValues()
                    for (k in row.keys()) {
                        when (val x = row.get(k)) {
                            JSONObject.NULL -> v.putNull(k)
                            is Int -> v.put(k, x.toLong())
                            is Long -> v.put(k, x)
                            is Double -> v.put(k, x)
                            is Boolean -> v.put(k, if (x) 1 else 0)
                            else -> v.put(k, x.toString())
                        }
                    }
                    db.insertOrThrow(t, null, v)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
