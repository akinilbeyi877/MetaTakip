package com.example.metatakip.feature_data.db.feature_siparis

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.UUID

object FeatureSiparisDbTables {

    const val TABLE = "siparis"
    private const val TAG = "FeatureSiparisDb"

    /**
     * 🏗️ TABLO OLUŞTURMA (İlk Kurulum)
     * Uygulama silinip yüklendiğinde veya ilk kurulumda burası çalışır.
     * Tüm yeni kolonlar (metrekare, ucret, isSeen vb.) buraya eklenmiştir.
     */
    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT UNIQUE, 
                musteriId INTEGER NOT NULL,
                firmaid INTEGER,
                musteri_uuid TEXT,
                firma_uuid TEXT,
                durum TEXT DEFAULT 'Yeni Sipariş',
                urunTipi TEXT,
                
                -- 📊 Sayısal Bilgiler (Hata veren kolonlar)
                ucret REAL DEFAULT 0,
                indirim REAL DEFAULT 0,
                ekUcret REAL DEFAULT 0,
                metrekare REAL DEFAULT 0,
                en REAL DEFAULT 0,
                boy REAL DEFAULT 0,
                
                -- 📅 Tarih Bilgileri
                duzenlemeTarihi TEXT,
                teslimAlmaTarihi TEXT,
                teslimTarihi TEXT,
                
                -- 📝 Detaylar
                notlar TEXT,
                yetkili TEXT,
                etiket_sablon_id INTEGER,
                
                -- 🗑️ Soft Delete
                isDeleted INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER,
                deleteReason TEXT,
                deletedBy INTEGER,
                
                -- ⏱️ Sistem Zaman Damgaları
                createdAt INTEGER DEFAULT (strftime('%s','now')),
                updatedAt INTEGER DEFAULT (strftime('%s','now')),
                
                -- 🆕 Çoklu Kullanıcı / Senkronizasyon Alanları
                isSeen INTEGER DEFAULT 0,
                seenBy TEXT DEFAULT '',
                seenAt INTEGER DEFAULT 0,
                seenDeviceName TEXT DEFAULT '',
                isLocked INTEGER DEFAULT 0,
                lockedBy TEXT DEFAULT '',
                lockedAt INTEGER DEFAULT 0,
                lockedDeviceName TEXT DEFAULT '',
                lockedForMinutes INTEGER DEFAULT 15,
                
                photoPath TEXT,
                
                FOREIGN KEY (musteriId) REFERENCES musteri(id) ON DELETE CASCADE,
                FOREIGN KEY (firmaid) REFERENCES firma(id) ON DELETE SET NULL
            );
        """.trimIndent())
        createIndexes(db)
        Log.d(TAG, "✅ Siparis tablosu tüm eksik kolonlarla (metrekare, ucret, duzenlemeTarihi vb.) oluşturuldu.")
    }

    /**
     * 🔄 VERSİYON YÜKSELTME (Mevcut Kullanıcılar İçin)
     * Veritabanı versiyonu arttığında eski verileri bozmadan yeni kolonları ekler.
     */
    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "🔄 Upgrade işlemi başlatıldı: $oldVersion -> $newVersion")

        try {
            // 1. ADIM: Temel ve Yeni Kolonları Tek Tek Kontrol Edip Ekle
            addColumnSafely(db, "uuid", "TEXT")
            addColumnSafely(db, "musteri_uuid", "TEXT")
            addColumnSafely(db, "firma_uuid", "TEXT")
            addColumnSafely(db, "updatedAt", "INTEGER DEFAULT (strftime('%s','now') * 1000)")
            addColumnSafely(db, "etiket_sablon_id", "INTEGER")

            // 📊 Sayısal Kolonlar
            addColumnSafely(db, "ucret", "REAL DEFAULT 0")
            addColumnSafely(db, "indirim", "REAL DEFAULT 0")
            addColumnSafely(db, "ekUcret", "REAL DEFAULT 0")
            addColumnSafely(db, "metrekare", "REAL DEFAULT 0")
            addColumnSafely(db, "en", "REAL DEFAULT 0")
            addColumnSafely(db, "boy", "REAL DEFAULT 0")

            // 📅 Eksik Tarih Kolonu
            addColumnSafely(db, "duzenlemeTarihi", "TEXT")

            // 🆕 Çoklu Cihaz Yönetimi Kolonları
            addColumnSafely(db, "isSeen", "INTEGER DEFAULT 0")
            addColumnSafely(db, "seenBy", "TEXT DEFAULT ''")
            addColumnSafely(db, "seenAt", "INTEGER DEFAULT 0")
            addColumnSafely(db, "seenDeviceName", "TEXT DEFAULT ''")
            addColumnSafely(db, "isLocked", "INTEGER DEFAULT 0")
            addColumnSafely(db, "lockedBy", "TEXT DEFAULT ''")
            addColumnSafely(db, "lockedAt", "INTEGER DEFAULT 0")
            addColumnSafely(db, "lockedDeviceName", "TEXT DEFAULT ''")
            addColumnSafely(db, "lockedForMinutes", "INTEGER DEFAULT 15")
            addColumnSafely(db, "photoPath", "TEXT")

            // 2. ADIM: Eksik UUID'leri ve ilişkileri onar
            patchMissingDataParanoid(db)

            // 3. ADIM: İndeksleri Yenile
            createIndexes(db)

            Log.i(TAG, "✅ Sipariş tablosu başarıyla güncellendi.")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Upgrade sırasında hata: ${e.message}")
        }
    }

    private fun addColumnSafely(db: SQLiteDatabase, columnName: String, columnType: String) {
        if (!columnExists(db, TABLE, columnName)) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $columnName $columnType")
                Log.d(TAG, "➕ Kolon eklendi: $columnName")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Kolon zaten var veya eklenemedi: $columnName")
            }
        }
    }

    private fun patchMissingDataParanoid(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT id, musteriId FROM $TABLE WHERE uuid IS NULL OR uuid = '' OR musteri_uuid IS NULL", null)
        db.beginTransaction()
        try {
            val idIdx = cursor.getColumnIndex("id")
            val mIdIdx = cursor.getColumnIndex("musteriId")

            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(idIdx)
                val mId = cursor.getLong(mIdIdx)
                val newUuid = UUID.randomUUID().toString()

                // Müşterinin UUID'sini bul
                var mUuid = ""
                val mCursor = db.rawQuery("SELECT uuid FROM musteri WHERE id = ?", arrayOf(mId.toString()))
                if (mCursor.moveToFirst()) mUuid = mCursor.getString(0) ?: ""
                mCursor.close()

                db.execSQL(
                    "UPDATE $TABLE SET uuid = COALESCE(uuid, ?), musteri_uuid = COALESCE(musteri_uuid, ?) WHERE id = ?",
                    arrayOf(newUuid, mUuid, rowId)
                )
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Veri yamama hatası: ${e.message}")
        } finally {
            db.endTransaction()
            cursor.close()
        }
    }

    fun createIndexes(db: SQLiteDatabase) {
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_siparis_uuid ON $TABLE(uuid)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_siparis_musteri_uuid ON $TABLE(musteri_uuid)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_siparis_musteriId ON $TABLE(musteriId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_siparis_firma_uuid ON $TABLE(firma_uuid)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_siparis_isDeleted ON $TABLE(isDeleted)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ İndeks oluşturma uyarısı: ${e.message}")
        }
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        try {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
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