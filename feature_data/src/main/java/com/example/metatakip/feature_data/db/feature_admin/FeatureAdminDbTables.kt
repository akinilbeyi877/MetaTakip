package com.example.metatakip.feature_data.db.feature_admin

import android.database.sqlite.SQLiteDatabase
import android.util.Log

object FeatureAdminDbTables {

    const val TABLE = "mesaj_sablon"
    private const val TAG = "FeatureAdminDb"

    fun create(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                
                -- ✅ YENİ: FIRMA BİLGİLERİ
                firmaid INTEGER NOT NULL DEFAULT 0,
                firmaAdi TEXT DEFAULT '',
                
                baslik TEXT NOT NULL,
                
                -- ✅ YENİ: TİP BİLGİSİ
                tip TEXT DEFAULT '',

                musteri_olustu_mesaj TEXT NOT NULL,
                musteri_guncellendi_mesaj TEXT NOT NULL,

                siparis_olustu_mesaj TEXT NOT NULL,
                siparis_urun_eklendi_mesaj TEXT NOT NULL,

                sms_onay_mesaj TEXT NOT NULL,
                whatsapp_onay_mesaj TEXT NOT NULL,

                varsayilan INTEGER DEFAULT 0,
                isDeleted INTEGER DEFAULT 0,
                
                created_at INTEGER DEFAULT (strftime('%s','now')),
                updated_at INTEGER,
                is_deleted INTEGER DEFAULT 0,
                birim_fiyat REAL
            );
        """.trimIndent())
        Log.d(TAG, "✅ $TABLE tablosu oluşturuldu (firmaid, firmaAdi, tip eklendi)")
    }

    fun upgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "🔄 Upgrade: $oldVersion → $newVersion - MESAJ_SABLON")

        // 1. Tablo yoksa oluştur
        if (!tableExists(db, TABLE)) {
            Log.e(TAG, "🚨 Tablo '$TABLE' YOK! Hemen oluşturuluyor")
            create(db)
            return
        }

        // 2. KRİTİK: firmaid KOLONU
        if (!columnExists(db, TABLE, "firmaid")) {
            Log.e(TAG, "❌❌❌ MESAJ_SABLON.firmaid YOK! HEMEN EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN firmaid INTEGER DEFAULT 0")

                // Eski kayıtlara varsayılan firma atama
                val firstFirma = getFirstFirmaId(db)
                if (firstFirma != -1L) {
                    db.execSQL("UPDATE $TABLE SET firmaid = ? WHERE firmaid = 0 OR firmaid IS NULL", arrayOf(firstFirma))
                    Log.w(TAG, "✅ Eski kayıtlara firmaid = $firstFirma atandı")
                }

                Log.w(TAG, "✅✅✅ firmaid kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 firmaid EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ firmaid kolonu MEVCUT")
        }

        // 3. KRİTİK: firmaAdi KOLONU
        if (!columnExists(db, TABLE, "firmaAdi")) {
            Log.e(TAG, "❌❌❌ MESAJ_SABLON.firmaAdi YOK! HEMEN EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN firmaAdi TEXT DEFAULT ''")

                // Firma adlarını doldur
                fillFirmaAdiFromFirmaTable(db)

                Log.w(TAG, "✅✅✅ firmaAdi kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 firmaAdi EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ firmaAdi kolonu MEVCUT")
        }

        // 4. KRİTİK: tip KOLONU - HATA NEDENİ
        if (!columnExists(db, TABLE, "tip")) {
            Log.e(TAG, "❌❌❌ MESAJ_SABLON.tip YOK! HEMEN EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN tip TEXT DEFAULT ''")

                // Mevcut kayıtlar için varsayılan tip belirle
                updateTipColumnWithDefaults(db)

                Log.w(TAG, "✅✅✅ tip kolonu eklendi ve dolduruldu")
            } catch (e: Exception) {
                Log.e(TAG, "💥 tip EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ tip kolonu MEVCUT")
        }

        // 5. created_at KOLONU
        if (!columnExists(db, TABLE, "created_at")) {
            Log.e(TAG, "❌ MESAJ_SABLON.created_at YOK! EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN created_at INTEGER DEFAULT (strftime('%s','now'))")
                Log.w(TAG, "✅ created_at kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 created_at EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ created_at kolonu MEVCUT")
        }

        // 6. updated_at KOLONU
        if (!columnExists(db, TABLE, "updated_at")) {
            Log.e(TAG, "❌ MESAJ_SABLON.updated_at YOK! EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN updated_at INTEGER")
                Log.w(TAG, "✅ updated_at kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 updated_at EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ updated_at kolonu MEVCUT")
        }

        // 7. is_deleted KOLONU
        if (!columnExists(db, TABLE, "is_deleted")) {
            Log.w(TAG, "⚠️ MESAJ_SABLON.is_deleted YOK! EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN is_deleted INTEGER DEFAULT 0")
                Log.w(TAG, "✅ is_deleted kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 is_deleted EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ is_deleted kolonu MEVCUT")
        }

        // 8. birim_fiyat KOLONU
        if (!columnExists(db, TABLE, "birim_fiyat")) {
            Log.w(TAG, "⚠️ MESAJ_SABLON.birim_fiyat YOK! EKLENİYOR...")
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN birim_fiyat REAL")
                Log.w(TAG, "✅ birim_fiyat kolonu eklendi")
            } catch (e: Exception) {
                Log.e(TAG, "💥 birim_fiyat EKLENEMEDİ: ${e.message}")
            }
        } else {
            Log.d(TAG, "✅ birim_fiyat kolonu MEVCUT")
        }

        // 9. Diğer kritik kolonların varsayılan değerlerini kontrol et
        val defaultColumns = listOf(
            Pair("varsayilan", "INTEGER DEFAULT 0"),
            Pair("isDeleted", "INTEGER DEFAULT 0")
        )

        defaultColumns.forEach { (column, defaultValue) ->
            if (!columnExists(db, TABLE, column)) {
                Log.w(TAG, "⚠️ Kolon '$column' eksik, ekleniyor...")
                try {
                    db.execSQL("ALTER TABLE $TABLE ADD COLUMN $column $defaultValue")
                    Log.w(TAG, "✅ Kolon '$column' eklendi ($defaultValue)")
                } catch (e: Exception) {
                    Log.e(TAG, "💥 '$column' eklenemedi: ${e.message}")
                }
            } else {
                Log.d(TAG, "✅ Kolon '$column' mevcut")
            }
        }

        // 10. Tüm mesaj alanlarının boş olmamasını sağla
        val messageColumns = listOf(
            "musteri_olustu_mesaj",
            "musteri_guncellendi_mesaj",
            "siparis_olustu_mesaj",
            "siparis_urun_eklendi_mesaj",
            "sms_onay_mesaj",
            "whatsapp_onay_mesaj"
        )

        messageColumns.forEach { column ->
            if (!columnExists(db, TABLE, column)) {
                Log.e(TAG, "🚨 KRİTİK: Kolon '$column' YOK! EKLENİYOR...")
                try {
                    db.execSQL("ALTER TABLE $TABLE ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
                    Log.w(TAG, "✅ Kolon '$column' eklendi")
                } catch (e: Exception) {
                    Log.e(TAG, "💥 '$column' eklenemedi: ${e.message}")
                }
            } else {
                Log.d(TAG, "✅ Kolon '$column' mevcut")
            }
        }

        // 11. Eksik verileri tamamla
        completeMissingData(db)

        Log.d(TAG, "✅ MESAJ_SABLON tablosu upgrade tamamlandı")
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

    // İlk firma ID'sini getir
    private fun getFirstFirmaId(db: SQLiteDatabase): Long {
        val cursor = db.rawQuery(
            "SELECT id FROM firma WHERE isDeleted = 0 LIMIT 1",
            null
        )
        return try {
            if (cursor.moveToFirst()) {
                cursor.getLong(0)
            } else {
                -1L
            }
        } finally {
            cursor.close()
        }
    }

    // Firma adlarını doldur
    private fun fillFirmaAdiFromFirmaTable(db: SQLiteDatabase) {
        try {
            // Tüm mesaj şablonlarını dolaş
            val cursor = db.rawQuery("SELECT id, firmaid FROM $TABLE WHERE firmaAdi = '' OR firmaAdi IS NULL", null)

            cursor.use { c ->
                while (c.moveToNext()) {
                    val mesajSablonId = c.getLong(0)
                    val firmaId = c.getLong(1)

                    if (firmaId > 0) {
                        // Firma adını bul
                        val firmaCursor = db.rawQuery(
                            "SELECT firmaAdi FROM firma WHERE id = ? AND isDeleted = 0 LIMIT 1",
                            arrayOf(firmaId.toString())
                        )

                        firmaCursor.use { fc ->
                            if (fc.moveToFirst()) {
                                val firmaAdi = fc.getString(0)
                                // Firma adını güncelle
                                db.execSQL(
                                    "UPDATE $TABLE SET firmaAdi = ? WHERE id = ?",
                                    arrayOf(firmaAdi, mesajSablonId)
                                )
                                Log.d(TAG, "MesajSablon ID $mesajSablonId için firmaAdi = '$firmaAdi' atandı")
                            }
                        }
                    }
                }
            }

            Log.w(TAG, "✅ Firma adları dolduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Firma adları doldurulamadı: ${e.message}")
        }
    }

    // ✅ YENİ: tip kolonunu varsayılan değerlerle doldur
    private fun updateTipColumnWithDefaults(db: SQLiteDatabase) {
        try {
            // Mevcut kayıtlar için tip belirle
            db.execSQL("""
                UPDATE $TABLE 
                SET tip = CASE 
                    WHEN baslik LIKE '%müşteri%' OR baslik LIKE '%customer%' OR 
                         musteri_olustu_mesaj != '' OR musteri_guncellendi_mesaj != '' 
                    THEN 'musteri'
                    
                    WHEN baslik LIKE '%sipariş%' OR baslik LIKE '%order%' OR 
                         siparis_olustu_mesaj != '' OR siparis_urun_eklendi_mesaj != '' 
                    THEN 'siparis'
                    
                    WHEN baslik LIKE '%onay%' OR baslik LIKE '%confirm%' OR 
                         sms_onay_mesaj != '' OR whatsapp_onay_mesaj != '' 
                    THEN 'onay'
                    
                    ELSE 'genel'
                END
                WHERE tip = '' OR tip IS NULL
            """)

            Log.w(TAG, "✅ tip kolonu varsayılan değerlerle dolduruldu")
        } catch (e: Exception) {
            Log.e(TAG, "💥 tip kolonu doldurulamadı: ${e.message}")
        }
    }

    // ✅ YENİ: Eksik verileri tamamla
    private fun completeMissingData(db: SQLiteDatabase) {
        try {
            // 1. Boş mesaj alanlarını doldur
            val messageUpdates = mapOf(
                "musteri_olustu_mesaj" to "Hoş geldiniz sayın müşterimiz! Kaydınız başarıyla oluşturuldu.",
                "musteri_guncellendi_mesaj" to "Sayın müşterimiz, kaydınız başarıyla güncellendi.",
                "siparis_olustu_mesaj" to "Sayın müşterimiz, siparişiniz alınmıştır. Teşekkür ederiz.",
                "siparis_urun_eklendi_mesaj" to "Sayın müşterimiz, siparişinize ürün eklenmiştir.",
                "sms_onay_mesaj" to "SMS onay mesajı",
                "whatsapp_onay_mesaj" to "WhatsApp onay mesajı"
            )

            messageUpdates.forEach { (column, defaultValue) ->
                db.execSQL(
                    "UPDATE $TABLE SET $column = ? WHERE $column = '' OR $column IS NULL",
                    arrayOf(defaultValue)
                )
                Log.d(TAG, "Boş $column alanları dolduruldu")
            }

            // 2. Boş başlıkları doldur
            db.execSQL("""
                UPDATE $TABLE 
                SET baslik = CASE 
                    WHEN baslik = '' OR baslik IS NULL THEN 
                        'Mesaj Şablonu ' || id
                    ELSE baslik
                END
                WHERE baslik = '' OR baslik IS NULL
            """)

            // 3. Varsayılan şablon belirle (eğer yoksa)
            val varsayilanCount = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE WHERE varsayilan = 1 AND isDeleted = 0",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            if (varsayilanCount == 0) {
                // İlk aktif şablonu varsayılan yap
                db.execSQL("""
                    UPDATE $TABLE 
                    SET varsayilan = 1 
                    WHERE id = (
                        SELECT id FROM $TABLE 
                        WHERE isDeleted = 0 
                        ORDER BY created_at ASC 
                        LIMIT 1
                    )
                """)
                Log.w(TAG, "✅ Varsayılan şablon belirlendi")
            }

            Log.w(TAG, "✅ Eksik veriler tamamlandı")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Eksik veriler tamamlanamadı: ${e.message}")
        }
    }

    // ✅ YENİ: Database versiyonu kontrolü
    fun getCurrentSchemaVersion(): Int {
        return 2 // 'tip' sütunu eklendikten sonraki versiyon
    }
}