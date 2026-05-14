package com.example.metatakip.feature_data.db.feature_user

import android.database.sqlite.SQLiteDatabase
import android.util.Log

object FeatureUserDbTables {

    const val TABLE = "user"
    private const val TAG = "FeatureUserDb"

    // ============================================================
    // 🆕 CREATE
    // ============================================================
    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE,
                password TEXT,
                fullName TEXT,
                role TEXT,
                isActive INTEGER DEFAULT 1,
                createdAt INTEGER DEFAULT (strftime('%s','now'))
            );
        """)
        Log.d(TAG, "✅ user table created")
    }

    // ============================================================
    // 🔄 UPGRADE
    // ============================================================
    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        // 🔹 ÖRNEK: 150'de email kolonu eklenecek diyelim
        if (oldVersion < 150) {
            addEmailColumn(db)
        }

        // ileride:
        // if (oldVersion < 155) { ... }
    }

    // ============================================================
    // 🔧 MIGRATIONS
    // ============================================================
    private fun addEmailColumn(db: SQLiteDatabase) {
        if (!columnExists(db, TABLE, "email")) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN email TEXT")
            Log.d(TAG, "✅ email column added to user table")
        }
    }

    // ============================================================
    // 🔍 HELPERS
    // ============================================================
    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        while (c.moveToNext()) {
            if (c.getString(1) == column) {
                exists = true
                break
            }
        }
        c.close()
        return exists
    }
}
