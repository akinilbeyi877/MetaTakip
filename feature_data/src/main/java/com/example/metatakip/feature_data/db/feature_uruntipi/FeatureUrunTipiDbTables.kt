package com.example.metatakip.feature_data.db.feature_uruntipi

import android.database.sqlite.SQLiteDatabase
import java.util.UUID
import android.util.Log

object FeatureUrunTipiDbTables {

    const val TABLE = "urun_tipi"
    private const val TAG = "FeatureUrunTipiDb"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT UNIQUE,
                ad TEXT NOT NULL,
                aciklama TEXT,
                aktif INTEGER DEFAULT 1,
                created_at INTEGER DEFAULT (strftime('%s','now')),  -- ✅ DEĞİŞTİRİLDİ: created_at
                birim_fiyat REAL DEFAULT 0.0,
                hesap_tipi TEXT DEFAULT 'M2',
                updated_at INTEGER,
                is_deleted INTEGER DEFAULT 0
            );
        """.trimIndent())
        Log.d(TAG, "✅ urun_tipi tablosu oluşturuldu (created_at snake_case)")
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        addColumnSafely(db, "uuid", "TEXT")
        Log.d(TAG, "🔄 Upgrade: $oldVersion → $newVersion - URUN_TIPI")

        // 1. Tablo yoksa oluştur
        if (!tableExists(db, TABLE)) {
            Log.e(TAG, "🚨 Tablo '$TABLE' YOK! Hemen oluşturuluyor")
            create(db)
            return
        }

        // 2. KRİTİK: created_at KOLONU - EN ÖNEMLİ EKLEME
        if (!columnExists(db, TABLE, "created_at")) {
            Log.e(TAG, "❌❌❌ URUN_TIPI.created_at YOK! HEMEN EKLENİYOR...")
            try {
                // Önce camelCase version'u kontrol et
                if (columnExists(db, TABLE, "createdAt")) {
                    Log.w(TAG, "⚠️ 'createdAt' (camelCase) mevcut, 'created_at' olarak değiştiriliyor")
                    // Geçici çözüm: camelCase'i sil ve snake_case ekle
                    db.execSQL("ALTER TABLE $TABLE ADD COLUMN created_at INTEGER")
                    // createdAt değerlerini created_at'e kopyala
                    db.execSQL("UPDATE $TABLE SET created_at = createdAt WHERE created_at IS NULL")
                    Log.w(TAG, "✅ createdAt değerleri created_at'e kopyalandı")
                } else {
                    // Hiç yoksa yeni ekle
                    db.execSQL("ALTER TABLE $TABLE ADD COLUMN created_at INTEGER DEFAULT (strftime('%s','now'))")
                }
                Log.w(TAG, "✅✅✅ created_at kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 created_at EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ created_at kolonu MEVCUT")
        }

        // 3. KRİTİK: is_deleted KOLONU
        if (!columnExists(db, TABLE, "is_deleted")) {
            Log.e(TAG, "❌❌❌ URUN_TIPI.is_deleted YOK! HEMEN EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN is_deleted INTEGER DEFAULT 0")
                Log.w(TAG, "✅✅✅ is_deleted kolonu eklendi (DEFAULT 0)")
                db.execSQL("UPDATE $TABLE SET is_deleted = 0 WHERE is_deleted IS NULL")
                Log.w(TAG, "✅ Tüm urun_tipi kayıtları is_deleted=0 yapıldı")
            } catch (e: Exception) {
                Log.e(TAG, "💥 is_deleted EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ is_deleted kolonu MEVCUT")
        }

        // 4. Diğer kritik kolonlar
        val otherColumns = listOf(
            "updated_at",
            "birim_fiyat",
            "hesap_tipi",
            "aktif"
        )

        otherColumns.forEach { column ->
            if (!columnExists(db, TABLE, column)) {
                Log.w(TAG, "⚠️ Kolon '$column' eksik, ekleniyor...")
                try {
                    when (column) {
                        "updated_at" -> db.execSQL("ALTER TABLE $TABLE ADD COLUMN updated_at INTEGER")
                        "birim_fiyat" -> db.execSQL("ALTER TABLE $TABLE ADD COLUMN birim_fiyat REAL DEFAULT 0.0")
                        "hesap_tipi" -> db.execSQL("ALTER TABLE $TABLE ADD COLUMN hesap_tipi TEXT DEFAULT 'M2'")
                        "aktif" -> db.execSQL("ALTER TABLE $TABLE ADD COLUMN aktif INTEGER DEFAULT 1")
                    }
                    Log.w(TAG, "✅ Kolon '$column' eklendi")
                } catch (e: Exception) {
                    Log.e(TAG, "💥 '$column' eklenemedi: ${e.message}")
                }
            } else {
                Log.d(TAG, "✅ Kolon '$column' mevcut")
            }
        }
    }

    // Yardımcı fonksiyonlar
    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == column) {
                exists = true
                break
            }
        }
        cursor.close()
        return exists
    }

    private fun addColumnSafely(db: SQLiteDatabase, col: String, type: String) {
        try {
            val c = db.rawQuery("PRAGMA table_info($TABLE)", null)
            var exists = false
            while (c.moveToNext()) { if (c.getString(1) == col) { exists = true; break } }
            c.close()
            if (!exists) db.execSQL("ALTER TABLE $TABLE ADD COLUMN $col $type")
        } catch (e: Exception) {
            android.util.Log.w("FeatureUrunTipiDb", "addColumn $col: ${e.message}")
        }
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
}
