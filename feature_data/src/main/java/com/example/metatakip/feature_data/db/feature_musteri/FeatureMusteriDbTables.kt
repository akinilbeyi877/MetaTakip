package com.example.metatakip.feature_data.db.feature_musteri

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.UUID

object FeatureMusteriDbTables {

    const val TABLE = "musteri"
    private const val TAG = "FeatureMusteriDb"

    // ============================================================
    // 🏗️ TABLO OLUŞTURMA (CREATE)
    // ============================================================
    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                
                -- 🌍 HİBRİT UNIQUE ANAHTARI: Cihazlar arası kimlik kartı
                uuid TEXT UNIQUE, 
                
                adSoyad TEXT NOT NULL,
                ceptel TEXT,
                ceptel2 TEXT,
                bolge TEXT,

                adres TEXT,
                musteriNotu TEXT,
                firmaAdi TEXT,
                firmaid INTEGER,
                firma_uuid TEXT,

                isDeleted INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER,
                deleteReason TEXT,
                deletedBy INTEGER,

                latitude REAL,
                longitude REAL,
                locationTimestamp INTEGER,
                locationAddress TEXT,

                photoPath TEXT,

                createdAt INTEGER DEFAULT (strftime('%s','now')),
                
                -- ⏱️ SENKRONİZASYON ZAMANI (Çakışma yönetimi için)
                updatedAt INTEGER DEFAULT (strftime('%s','now')),

                FOREIGN KEY (firmaid)
                    REFERENCES firma(id)
                    ON DELETE SET NULL
            );
        """.trimIndent())

        // 🚀 Arama hızını artırmak için indeks oluştur
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_musteri_uuid ON $TABLE(uuid)")
        Log.d(TAG, "✅ $TABLE tablosu hibrit yapı ile oluşturuldu.")
    }

    // ============================================================
    // 🔄 VERSİYON YÜKSELTME (UPGRADE)
    // ============================================================
    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "🔄 Upgrade: $oldVersion -> $newVersion")

        // 1. UUID Kolonu Ekleme ve Eski Kayıtları Kimliklendirme
        if (!columnExists(db, "uuid")) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN uuid TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_musteri_uuid ON $TABLE(uuid)")

                // 🔥 KRİTİK: Mevcut Ahmetlere (eski kayıtlara) UUID ata
                generateUuidForExistingRecords(db)

                Log.d(TAG, "✅ uuid kolonu eklendi ve eski kayıtlar güncellendi.")
            } catch (e: Exception) {
                Log.e(TAG, "💥 uuid eklenirken hata: ${e.message}")
            }
        }

        // 2. updatedAt Kolonu Ekleme
        if (!columnExists(db, "updatedAt")) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN updatedAt INTEGER DEFAULT (strftime('%s','now'))")
                Log.d(TAG, "✅ updatedAt kolonu eklendi.")
            } catch (e: Exception) {
                Log.e(TAG, "💥 updatedAt hatası: ${e.message}")
            }
        }

        // 3. Eski Versiyon Kontrolleri (v146 ve v130)
        if (oldVersion < 146 && !columnExists(db, "firmaid")) {
            // SQLite: tek ALTER TABLE'da sadece 1 kolon eklenebilir
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN firmaid INTEGER")
        }
        // firma_uuid cross-reference kolonu (multi-device sync)
        if (!columnExists(db, "firma_uuid")) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN firma_uuid TEXT")
        }
        if (!columnExists(db, "photoPath")) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN photoPath TEXT")
        }
        if (oldVersion < 130) {
            fixLocationColumns(db)
        }
    }

    // ============================================================
    // 🛠️ YARDIMCI METOTLAR (HELPERS)
    // ============================================================

    /** Mevcut müşterilere senkronizasyon için benzersiz kimlik atar */
    private fun generateUuidForExistingRecords(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT id FROM $TABLE WHERE uuid IS NULL OR uuid = ''", null)
        db.beginTransaction()
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val newUuid = UUID.randomUUID().toString()
                db.execSQL("UPDATE $TABLE SET uuid = ? WHERE id = ?", arrayOf(newUuid, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            cursor.close()
        }
    }

    private fun fixLocationColumns(db: SQLiteDatabase) {
        if (columnExists(db, "location_timestamp")) {
            db.execSQL("ALTER TABLE $TABLE RENAME COLUMN location_timestamp TO locationTimestamp")
        }
        if (columnExists(db, "location_address")) {
            db.execSQL("ALTER TABLE $TABLE RENAME COLUMN location_address TO locationAddress")
        }
    }

    /** Kolonun varlığını daha güvenli (getColumnIndex) kontrol eder */
    private fun columnExists(db: SQLiteDatabase, column: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($TABLE)", null)
        var exists = false
        try {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) {
                        exists = true
                        break
                    }
                }
            }
        } finally {
            cursor.close()
        }
        return exists
    }
}