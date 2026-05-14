package com.example.metatakip.feature_data.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.UUID

object FeaturePersonelDbTables {

    const val TABLE = "personel"
    private const val TAG = "FeaturePersonelDb"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT UNIQUE,
                adSoyad TEXT NOT NULL,
                unvan TEXT NOT NULL,
                isDeleted INTEGER DEFAULT 0,
                deletedAt INTEGER,
                updatedAt INTEGER DEFAULT (strftime('%s','now') * 1000),
                createdAt INTEGER DEFAULT (strftime('%s','now') * 1000)
            );
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_personel_uuid ON $TABLE(uuid)")
        patchMissingUuids(db)
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        addColumnSafely(db, "uuid", "TEXT")
        addColumnSafely(db, "isDeleted", "INTEGER DEFAULT 0")
        addColumnSafely(db, "deletedAt", "INTEGER")
        addColumnSafely(db, "updatedAt", "INTEGER DEFAULT (strftime('%s','now') * 1000)")
        try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_personel_uuid ON $TABLE(uuid)") } catch (_: Exception) {}
        patchMissingUuids(db)
    }

    private fun patchMissingUuids(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT id FROM $TABLE WHERE uuid IS NULL OR uuid = ''", null)
        db.beginTransaction()
        try {
            while (cursor.moveToNext()) {
                db.execSQL("UPDATE $TABLE SET uuid = ? WHERE id = ?",
                    arrayOf(UUID.randomUUID().toString(), cursor.getLong(0)))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction(); cursor.close() }
    }

    private fun addColumnSafely(db: SQLiteDatabase, col: String, type: String) {
        try {
            val c = db.rawQuery("PRAGMA table_info($TABLE)", null)
            var exists = false
            while (c.moveToNext()) { if (c.getString(1) == col) { exists = true; break } }
            c.close()
            if (!exists) db.execSQL("ALTER TABLE $TABLE ADD COLUMN $col $type")
        } catch (e: Exception) { Log.w(TAG, "addColumn $col: ${e.message}") }
    }
}
