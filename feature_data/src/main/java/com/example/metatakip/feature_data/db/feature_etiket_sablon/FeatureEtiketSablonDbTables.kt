package com.example.metatakip.feature_data.db.feature_etiket_sablon

import android.database.sqlite.SQLiteDatabase

object FeatureEtiketSablonDbTables {

    const val TABLE = "etiket_sablon"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                adi TEXT NOT NULL,
                varsayilan INTEGER DEFAULT 0,
                createdAt INTEGER DEFAULT (strftime('%s','now')),
                manual_text TEXT,
                comp_text TEXT,
                updatedAt INTEGER DEFAULT 0
            );
        """)
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (!columnExists(db, TABLE, "manual_text")) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN manual_text TEXT") } catch (_: Exception) {}
        }
        if (!columnExists(db, TABLE, "comp_text")) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN comp_text TEXT") } catch (_: Exception) {}
        }
        if (!columnExists(db, TABLE, "updatedAt")) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN updatedAt INTEGER DEFAULT 0") } catch (_: Exception) {}
        }
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        try {
            while (c.moveToNext()) if (c.getString(1) == column) return true
        } finally { c.close() }
        return false
    }
}
