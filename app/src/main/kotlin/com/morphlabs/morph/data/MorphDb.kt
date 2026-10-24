package com.morphlabs.morph.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite storage. Everything stays on the phone. When the schema changes, bump [VERSION]
 * and add a step to [onUpgrade]; never drop user data. MorphApp copies the database file to a
 * backup before the first run of every new app version.
 */
class MorphDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE accounts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL, emoji TEXT NOT NULL, grp TEXT NOT NULL,
                budget INTEGER NOT NULL DEFAULT 0, period TEXT NOT NULL DEFAULT 'NONE',
                role TEXT, hint TEXT, archived INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL,
                counterparty TEXT, cp_key TEXT, kind TEXT, code TEXT, sms TEXT, sms_balance INTEGER,
                category_id INTEGER, dedupe TEXT UNIQUE)"""
        )
        db.execSQL(
            """CREATE TABLE lines(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id INTEGER NOT NULL, account_id INTEGER NOT NULL, amount INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX lines_entry ON lines(entry_id)")
        db.execSQL("CREATE INDEX entries_ts ON entries(ts)")
        db.execSQL(
            """CREATE TABLE budget_moves(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_key TEXT NOT NULL, from_id INTEGER NOT NULL, to_id INTEGER NOT NULL,
                amount INTEGER NOT NULL, ts INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE TABLE rewards(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, stars INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes go here, one `if (oldVersion < N)` block per version.
    }

    companion object {
        const val NAME = "morph.db"
        const val VERSION = 1
    }
}
