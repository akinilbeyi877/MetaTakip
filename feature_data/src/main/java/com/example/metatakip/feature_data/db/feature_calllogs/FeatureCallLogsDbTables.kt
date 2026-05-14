package com.example.metatakip.feature_data.db.feature_calllogs

import android.database.sqlite.SQLiteDatabase

object FeatureCallLogsDbTables {

    const val TABLE = "call_logs"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,

                musteriTelefonu TEXT NOT NULL,
                musteriAdi TEXT,

                arananFirmaAdi TEXT NOT NULL,
                arananHatAdi TEXT NOT NULL,
                arananTelefon TEXT NOT NULL,

                cihazAdi TEXT NOT NULL,
                cihazFirmaAdi TEXT NOT NULL DEFAULT 'Yapılandırılmamış',
                cihazKullaniciAdi TEXT NOT NULL DEFAULT 'Bilinmiyor',
                cihazRolu TEXT NOT NULL,
                cihazMerkezMi INTEGER NOT NULL,
                simYuvasi TEXT NOT NULL,

                cagriTuru TEXT NOT NULL,
                cagriZamani INTEGER NOT NULL,

                merkezeIletildiMi INTEGER NOT NULL DEFAULT 0,
                merkezeIletilmeZamani INTEGER,
                merkezHataMesaji TEXT,

                createdAt INTEGER DEFAULT (strftime('%s','now')),
                updatedAt INTEGER DEFAULT (strftime('%s','now'))
            );
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_logs_uuid ON $TABLE(uuid)")
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // uuid + updatedAt MetaTakipDb.onOpen() içinde garanti ediliyor.
    }
}
