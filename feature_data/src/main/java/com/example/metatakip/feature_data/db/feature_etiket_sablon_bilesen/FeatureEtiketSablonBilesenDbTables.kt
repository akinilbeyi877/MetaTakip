package com.example.metatakip.feature_data.db.feature_etiket_sablon_bilesen

import android.database.sqlite.SQLiteDatabase

object FeatureEtiketSablonBilesenDbTables {

    const val TABLE = "etiket_sablon_bilesen"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sablon_id INTEGER NOT NULL,
                bilesen_id TEXT NOT NULL,
                secili INTEGER DEFAULT 0,
                sira INTEGER
            );
        """)
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}
