package com.example.metatakip.feature_data.db.feature_etiket_sayfa_ayar

import android.database.sqlite.SQLiteDatabase
import android.util.Log

object FeatureEtiketSayfaAyarDbTables {

    const val TABLE = "etiket_sayfa_ayar"
    private const val TAG = "EtiketSayfaAyarDb"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sablon_id INTEGER NOT NULL,
                paddingTop INTEGER,
                paddingBottom INTEGER,
                paddingLeft INTEGER,
                paddingRight INTEGER,
                textSize REAL,
                textColor INTEGER,
                label_name TEXT DEFAULT '',
                width_mm REAL DEFAULT 100.0,
                height_mm REAL DEFAULT 80.0,
                columns INTEGER DEFAULT 1,
                spacing_mm REAL DEFAULT 0.0
            );
        """)
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 353) {
            val alter = listOf(
                "ALTER TABLE $TABLE ADD COLUMN label_name TEXT DEFAULT ''",
                "ALTER TABLE $TABLE ADD COLUMN width_mm REAL DEFAULT 100.0",
                "ALTER TABLE $TABLE ADD COLUMN height_mm REAL DEFAULT 80.0",
                "ALTER TABLE $TABLE ADD COLUMN columns INTEGER DEFAULT 1",
                "ALTER TABLE $TABLE ADD COLUMN spacing_mm REAL DEFAULT 0.0"
            )
            alter.forEach { sql ->
                try { db.execSQL(sql) }
                catch (e: Exception) { Log.d(TAG, "Sütun zaten var veya hata: ${e.message}") }
            }
        }
    }
}