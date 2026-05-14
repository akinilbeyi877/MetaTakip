package com.example.metatakip.feature_data.db.feature_deletelog

import android.database.sqlite.SQLiteDatabase

object FeatureDeleteLogDbTables {

    const val TABLE = "delete_log"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entityType TEXT NOT NULL,
                entityId INTEGER NOT NULL,
                deletedBy INTEGER,
                deletedAt INTEGER DEFAULT (strftime('%s','now')),
                reason TEXT
            );
        """)
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // şimdilik boş
        // ileride kolon ekleme vs buraya
    }
}
