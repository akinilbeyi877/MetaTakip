package com.example.metatakip.feature_data.db.feature_urun

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.UUID

object FeatureUrunDbTables {

    const val TABLE = "urun"
    private const val TAG = "FeatureUrunDb"

    // ============================================================
    // 🏗️ TABLO OLUŞTURMA (CREATE)
    // ============================================================
    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                
                -- 🌍 ÜRÜNÜN KENDİ KİMLİĞİ (Küresel Tekil Anahtar)
                uuid TEXT UNIQUE, 
                
                -- 🔗 YEREL BAĞ (SQLite performansı için)
                siparisId INTEGER NOT NULL,
                
                -- 🔗 KÜRESEL BAĞ (Senkronizasyonda siparişin kaybolmaması için)
                siparis_uuid TEXT,

                ad TEXT NOT NULL,
                urunTipi TEXT DEFAULT '',
                adet INTEGER,
                m2 REAL,
                fiyat REAL,
                tutar REAL,

                isDeleted INTEGER DEFAULT 0,
                createdAt INTEGER DEFAULT (strftime('%s','now')),
                
                -- ⏱️ SENKRONİZASYON ZAMAN DAMGASI (Çakışma yönetimi için)
                updatedAt INTEGER DEFAULT (strftime('%s','now')),

                FOREIGN KEY (siparisId)
                    REFERENCES siparis(id)
                    ON DELETE CASCADE
            );
        """.trimIndent())

        createIndexes(db)
        Log.d(TAG, "✅ $TABLE tablosu ve indeksleri oluşturuldu.")
    }

    // ============================================================
    // 🔄 VERSİYON YÜKSELTME (MIGRATION)
    // ============================================================
    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "🔄 Upgrade Urun: $oldVersion -> $newVersion")

        // 1. Kendi UUID Kolonu (Ürün bazlı tekil takip)
        if (!columnExists(db, TABLE, "uuid")) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN uuid TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_urun_uuid ON $TABLE(uuid)")
                generateUuidForExistingProducts(db)
                Log.d(TAG, "✅ Ürünlere uuid eklendi.")
            } catch (e: Exception) { Log.e(TAG, "uuid hatası: ${e.message}") }
        }

        // 2. Sipariş UUID Bağlantı Kolonu (Kritik Bağlantı)
        if (!columnExists(db, TABLE, "siparis_uuid")) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN siparis_uuid TEXT")
                syncSiparisUuids(db)
                Log.d(TAG, "✅ Ürünlere siparis_uuid eklendi.")
            } catch (e: Exception) { Log.e(TAG, "siparis_uuid hatası: ${e.message}") }
        }

        // 3. updatedAt Kolonu (Zaman damgası)
        if (!columnExists(db, TABLE, "updatedAt")) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN updatedAt INTEGER DEFAULT (strftime('%s','now'))")
        }

        // Her durumda indekslerin güncel olduğundan emin ol
        createIndexes(db)
    }

    // ============================================================
    // 📈 PERFORMANS İNDEKSLERİ
    // ============================================================
    fun createIndexes(db: SQLiteDatabase) {
        // 🌍 Hibrit Yapı İndeksleri (Hız ve Tekillik)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_urun_uuid ON $TABLE(uuid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_urun_siparis_uuid ON $TABLE(siparis_uuid)")

        // 🏠 Yerel Performans İndeksleri
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_urun_siparisId ON $TABLE(siparisId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_urun_isDeleted ON $TABLE(isDeleted)")
    }

    // ============================================================
    // 🛠️ MIGRATION HELPERS (YARDIMCILAR)
    // ============================================================

    /** Mevcut ürünlere (halılara vb.) benzersiz kimlik atar */
    private fun generateUuidForExistingProducts(db: SQLiteDatabase) {
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

    /** Ürünleri, bağlı oldukları siparişin küresel UUID'si ile mühürler */
    private fun syncSiparisUuids(db: SQLiteDatabase) {
        db.execSQL("""
            UPDATE $TABLE 
            SET siparis_uuid = (
                SELECT s.uuid FROM siparis s WHERE s.id = $TABLE.siparisId
            )
            WHERE siparis_uuid IS NULL OR siparis_uuid = ''
        """.trimIndent())
    }

    /** Kolon varlığını isme göre güvenli kontrol eder */
    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        val nameIndex = c.getColumnIndex("name")
        if (nameIndex != -1) {
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == column) {
                    exists = true
                    break
                }
            }
        }
        c.close()
        return exists
    }
}