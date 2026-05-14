package com.example.metatakip.feature.admin.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.MesajSablon

class MesajSablonDaoImpl(
    context: Context
) : MesajSablonDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "MesajSablonDaoImpl"

    companion object {
        private const val TABLE_NAME = "mesaj_sablon"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FIRMA_ID = "firmaid"
        private const val COLUMN_FIRMA_ADI = "firmaAdi"
        private const val COLUMN_TIP = "tip"
        private const val COLUMN_BASLIK = "baslik"
        private const val COLUMN_MUSTERI_OLUSTU = "musteri_olustu_mesaj"
        private const val COLUMN_MUSTERI_GUNCELLENDI = "musteri_guncellendi_mesaj"
        private const val COLUMN_SIPARIS_OLUSTU = "siparis_olustu_mesaj"
        private const val COLUMN_SIPARIS_URUN_EKLENDI = "siparis_urun_eklendi_mesaj"
        private const val COLUMN_SMS_ONAY = "sms_onay_mesaj"
        private const val COLUMN_WHATSAPP_ONAY = "whatsapp_onay_mesaj"
        private const val COLUMN_VARSAYILAN = "varsayilan"
        private const val COLUMN_IS_DELETED = "isDeleted"
        private const val COLUMN_CREATED_AT = "created_at"      // ✅ DEĞİŞTİ
        private const val COLUMN_UPDATED_AT = "updated_at"      // ✅ DEĞİŞTİ
        private const val COLUMN_IS_DELETED_NEW = "is_deleted"
        private const val COLUMN_BIRIM_FIYAT = "birim_fiyat"
    }

    // --------------------------------------------------
    // GET ALL (soft deleted olmayanlar)
    // --------------------------------------------------
    override fun getAll(): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_IS_DELETED = 0
            ORDER BY $COLUMN_VARSAYILAN DESC, $COLUMN_ID DESC
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET BY FIRMA ID
    // --------------------------------------------------
    override fun getByFirmaId(firmaId: Long): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? AND $COLUMN_IS_DELETED = 0
            ORDER BY $COLUMN_VARSAYILAN DESC, $COLUMN_ID DESC
            """.trimIndent(),
            arrayOf(firmaId.toString())
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET BY FIRMA ID AND TIP
    // --------------------------------------------------
    override fun getByFirmaIdAndTip(firmaId: Long, tip: String): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? 
              AND $COLUMN_TIP = ?
              AND $COLUMN_IS_DELETED = 0
            ORDER BY $COLUMN_VARSAYILAN DESC, $COLUMN_ID DESC
            """.trimIndent(),
            arrayOf(firmaId.toString(), tip)
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET BY TIP
    // --------------------------------------------------
    override fun getByTip(tip: String): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_TIP = ? AND $COLUMN_IS_DELETED = 0
            ORDER BY $COLUMN_VARSAYILAN DESC, $COLUMN_ID DESC
            """.trimIndent(),
            arrayOf(tip)
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET BY ID
    // --------------------------------------------------
    override fun getById(id: Long): MesajSablon? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(id.toString())
        )

        cursor.use {
            return if (it.moveToFirst()) mapCursor(it) else null
        }
    }

    // --------------------------------------------------
    // ✅ DÜZELTİLMİŞ INSERT METODU
    // --------------------------------------------------
    override fun insert(sablon: MesajSablon): Long {
        val db = dbHelper.writableDatabase

        val currentTime = System.currentTimeMillis() / 1000 // Unix timestamp (saniye)

        val values = ContentValues().apply {
            put(COLUMN_FIRMA_ID, sablon.firmaid)
            put(COLUMN_FIRMA_ADI, sablon.firmaAdi)
            put(COLUMN_TIP, sablon.tip)
            put(COLUMN_BASLIK, sablon.baslik)
            put(COLUMN_MUSTERI_OLUSTU, sablon.musteriOlustuMesaj)
            put(COLUMN_MUSTERI_GUNCELLENDI, sablon.musteriGuncellendiMesaj)
            put(COLUMN_SIPARIS_OLUSTU, sablon.siparisOlustuMesaj)
            put(COLUMN_SIPARIS_URUN_EKLENDI, sablon.siparisUrunEklendiMesaj)
            put(COLUMN_SMS_ONAY, sablon.smsOnayMesaj)
            put(COLUMN_WHATSAPP_ONAY, sablon.whatsappOnayMesaj)
            put(COLUMN_VARSAYILAN, if (sablon.varsayilan) 1 else 0)
            put(COLUMN_IS_DELETED, 0)

            // ✅ DÜZELTME: created_at ve updated_at kullan
            put(COLUMN_CREATED_AT, currentTime)
            put(COLUMN_UPDATED_AT, currentTime)

            // ❌ BUNLARI KULLANMA: olusturulmaTarihi, guncellemeTarihi
        }

        Log.d(TAG, "📝 INSERT: $TABLE_NAME, values: ${values.size()}")

        return try {
            val result = db.insert(TABLE_NAME, null, values)
            Log.d(TAG, "✅ INSERT başarılı, ID: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ INSERT başarısız: ${e.message}")
            -1L
        } finally {
            db.close()
        }
    }

    // --------------------------------------------------
    // ✅ DÜZELTİLMİŞ UPDATE METODU
    // --------------------------------------------------
    override fun update(id: Long, sablon: MesajSablon): Boolean {
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(COLUMN_FIRMA_ID, sablon.firmaid)
            put(COLUMN_FIRMA_ADI, sablon.firmaAdi)
            put(COLUMN_TIP, sablon.tip)
            put(COLUMN_BASLIK, sablon.baslik)
            put(COLUMN_MUSTERI_OLUSTU, sablon.musteriOlustuMesaj)
            put(COLUMN_MUSTERI_GUNCELLENDI, sablon.musteriGuncellendiMesaj)
            put(COLUMN_SIPARIS_OLUSTU, sablon.siparisOlustuMesaj)
            put(COLUMN_SIPARIS_URUN_EKLENDI, sablon.siparisUrunEklendiMesaj)
            put(COLUMN_SMS_ONAY, sablon.smsOnayMesaj)
            put(COLUMN_WHATSAPP_ONAY, sablon.whatsappOnayMesaj)
            put(COLUMN_VARSAYILAN, if (sablon.varsayilan) 1 else 0)

            // ✅ DÜZELTME: updated_at kullan
            put(COLUMN_UPDATED_AT, System.currentTimeMillis() / 1000)

            // ❌ BUNU KULLANMA: guncellemeTarihi
        }

        Log.d(TAG, "📝 UPDATE: $TABLE_NAME, ID: $id")

        return try {
            val result = db.update(
                TABLE_NAME,
                values,
                "$COLUMN_ID = ?",
                arrayOf(id.toString())
            ) > 0
            Log.d(TAG, "✅ UPDATE başarılı: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ UPDATE başarısız: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    // --------------------------------------------------
    // ✅ DÜZELTİLMİŞ SOFT DELETE METODU
    // --------------------------------------------------
    override fun delete(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)

            // ✅ DÜZELTME: updated_at kullan
            put(COLUMN_UPDATED_AT, System.currentTimeMillis() / 1000)

            // ❌ BUNU KULLANMA: guncellemeTarihi
        }

        val result = db.update(
            TABLE_NAME,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        ) > 0
        db.close()
        return result
    }

    // --------------------------------------------------
    // HARD DELETE
    // --------------------------------------------------
    override fun hardDelete(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete(
            TABLE_NAME,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        ) > 0
        db.close()
        return result
    }

    // --------------------------------------------------
    // GET VARSAYILAN (Genel)
    // --------------------------------------------------
    override fun getVarsayilan(): MesajSablon? {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_VARSAYILAN = 1
              AND $COLUMN_IS_DELETED = 0
            LIMIT 1
            """.trimIndent(),
            null
        )

        cursor.use {
            return if (it.moveToFirst()) mapCursor(it) else null
        }
    }

    // --------------------------------------------------
    // GET VARSAYILAN BY FIRMA ID
    // --------------------------------------------------
    override fun getVarsayilanByFirmaId(firmaId: Long): MesajSablon? {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? 
              AND $COLUMN_VARSAYILAN = 1
              AND $COLUMN_IS_DELETED = 0
            LIMIT 1
            """.trimIndent(),
            arrayOf(firmaId.toString())
        )

        cursor.use {
            return if (it.moveToFirst()) mapCursor(it) else null
        }
    }

    // --------------------------------------------------
    // GET VARSAYILAN BY FIRMA ID AND TIP
    // --------------------------------------------------
    override fun getVarsayilanByFirmaIdAndTip(firmaId: Long, tip: String): MesajSablon? {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? 
              AND $COLUMN_TIP = ?
              AND $COLUMN_VARSAYILAN = 1
              AND $COLUMN_IS_DELETED = 0
            LIMIT 1
            """.trimIndent(),
            arrayOf(firmaId.toString(), tip)
        )

        cursor.use {
            return if (it.moveToFirst()) mapCursor(it) else null
        }
    }

    // --------------------------------------------------
    // SET VARSAYILAN FOR FIRMA
    // --------------------------------------------------
    override fun setVarsayilanForFirma(firmaId: Long, aktifSablonId: Long): Boolean {
        val db = dbHelper.writableDatabase

        return try {
            db.beginTransaction()

            var success = true

            try {
                // 1. Önce bu firma için TÜM şablonları varsayılan olmaktan çıkar
                val updateAllValues = ContentValues().apply {
                    put(COLUMN_VARSAYILAN, 0)

                    // ✅ DÜZELTME: updated_at kullan
                    put(COLUMN_UPDATED_AT, System.currentTimeMillis() / 1000)
                }

                db.update(
                    TABLE_NAME,
                    updateAllValues,
                    "$COLUMN_FIRMA_ID = ?",
                    arrayOf(firmaId.toString())
                )

                // 2. Belirtilen şablonu varsayılan yap
                val updateSingleValues = ContentValues().apply {
                    put(COLUMN_VARSAYILAN, 1)

                    // ✅ DÜZELTME: updated_at kullan
                    put(COLUMN_UPDATED_AT, System.currentTimeMillis() / 1000)
                }

                val updatedSingleRows = db.update(
                    TABLE_NAME,
                    updateSingleValues,
                    "$COLUMN_ID = ? AND $COLUMN_FIRMA_ID = ?",
                    arrayOf(aktifSablonId.toString(), firmaId.toString())
                )

                success = updatedSingleRows > 0
                db.setTransactionSuccessful()

            } catch (e: Exception) {
                e.printStackTrace()
                success = false
            } finally {
                db.endTransaction()
                db.close()
            }

            success

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --------------------------------------------------
    // SET VARSAYILAN FOR FIRMA AND TIP
    // --------------------------------------------------
    override fun setVarsayilanForFirmaAndTip(firmaId: Long, tip: String, aktifSablonId: Long): Boolean {
        val db = dbHelper.writableDatabase

        return try {
            db.beginTransaction()

            var success = true

            try {
                // 1. Önce bu firma ve tip için TÜM şablonları varsayılan olmaktan çıkar
                val updateAllValues = ContentValues().apply {
                    put(COLUMN_VARSAYILAN, 0)

                    // ✅ DÜZELTME: updated_at kullan
                    put(COLUMN_UPDATED_AT, System.currentTimeMillis() / 1000)
                }

                db.update(
                    TABLE_NAME,
                    updateAllValues,
                    "$COLUMN_FIRMA_ID = ? AND $COLUMN_TIP = ?",
                    arrayOf(firmaId.toString(), tip)
                )

                // 2. Belirtilen şablonu varsayılan yap
                val updateSingleValues = ContentValues().apply {
                    put(COLUMN_VARSAYILAN, 1)

                    // ✅ DÜZELTME: updated_at kullan
                    put(COLUMN_UPDATED_AT, System.currentTimeMillis() / 1000)
                }

                val updatedSingleRows = db.update(
                    TABLE_NAME,
                    updateSingleValues,
                    "$COLUMN_ID = ? AND $COLUMN_FIRMA_ID = ? AND $COLUMN_TIP = ?",
                    arrayOf(aktifSablonId.toString(), firmaId.toString(), tip)
                )

                success = updatedSingleRows > 0
                db.setTransactionSuccessful()

            } catch (e: Exception) {
                e.printStackTrace()
                success = false
            } finally {
                db.endTransaction()
                db.close()
            }

            success

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --------------------------------------------------
    // GET ALL WITH DELETED
    // --------------------------------------------------
    override fun getAllWithDeleted(): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            ORDER BY $COLUMN_IS_DELETED ASC, $COLUMN_VARSAYILAN DESC, $COLUMN_ID DESC
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET BY FIRMA ID AND VARSAYILAN
    // --------------------------------------------------
    override fun getByFirmaIdAndVarsayilan(firmaId: Long, varsayilan: Boolean): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val varsayilanValue = if (varsayilan) 1 else 0

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? 
              AND $COLUMN_VARSAYILAN = ?
              AND $COLUMN_IS_DELETED = 0
            ORDER BY $COLUMN_ID DESC
            """.trimIndent(),
            arrayOf(firmaId.toString(), varsayilanValue.toString())
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET BY FIRMA ID AND TIP AND VARSAYILAN
    // --------------------------------------------------
    override fun getByFirmaIdAndTipAndVarsayilan(firmaId: Long, tip: String, varsayilan: Boolean): List<MesajSablon> {
        val list = mutableListOf<MesajSablon>()
        val db = dbHelper.readableDatabase

        val varsayilanValue = if (varsayilan) 1 else 0

        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? 
              AND $COLUMN_TIP = ?
              AND $COLUMN_VARSAYILAN = ?
              AND $COLUMN_IS_DELETED = 0
            ORDER BY $COLUMN_ID DESC
            """.trimIndent(),
            arrayOf(firmaId.toString(), tip, varsayilanValue.toString())
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        db.close()
        return list
    }

    // --------------------------------------------------
    // GET COUNT BY FIRMA ID
    // --------------------------------------------------
    override fun getCountByFirmaId(firmaId: Long): Int {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT COUNT(*) as count FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? AND $COLUMN_IS_DELETED = 0
            """.trimIndent(),
            arrayOf(firmaId.toString())
        )

        cursor.use {
            return if (it.moveToFirst()) {
                it.getInt(it.getColumnIndexOrThrow("count"))
            } else {
                0
            }
        }
    }

    // --------------------------------------------------
    // GET COUNT BY FIRMA ID AND TIP
    // --------------------------------------------------
    override fun getCountByFirmaIdAndTip(firmaId: Long, tip: String): Int {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT COUNT(*) as count FROM $TABLE_NAME
            WHERE $COLUMN_FIRMA_ID = ? 
              AND $COLUMN_TIP = ?
              AND $COLUMN_IS_DELETED = 0
            """.trimIndent(),
            arrayOf(firmaId.toString(), tip)
        )

        cursor.use {
            return if (it.moveToFirst()) {
                it.getInt(it.getColumnIndexOrThrow("count"))
            } else {
                0
            }
        }
    }

    // --------------------------------------------------
    // ✅ DÜZELTİLMİŞ CURSOR MAPPER
    // --------------------------------------------------
    private fun mapCursor(c: Cursor): MesajSablon {
        // ✅ Kolonları kontrol et
        val id = c.getLong(c.getColumnIndexOrThrow(COLUMN_ID))
        val firmaid = c.getLong(c.getColumnIndexOrThrow(COLUMN_FIRMA_ID))
        val firmaAdi = c.getString(c.getColumnIndexOrThrow(COLUMN_FIRMA_ADI))
        val baslik = c.getString(c.getColumnIndexOrThrow(COLUMN_BASLIK))

        // ✅ tip sütunu var mı kontrol et
        val tipIndex = c.getColumnIndex(COLUMN_TIP)
        val tip = if (tipIndex != -1) {
            c.getString(tipIndex) ?: ""
        } else {
            "" // Varsayılan değer
        }

        // Mesaj içerikleri
        val musteriOlustuMesaj = c.getString(c.getColumnIndexOrThrow(COLUMN_MUSTERI_OLUSTU))
        val musteriGuncellendiMesaj = c.getString(c.getColumnIndexOrThrow(COLUMN_MUSTERI_GUNCELLENDI))
        val siparisOlustuMesaj = c.getString(c.getColumnIndexOrThrow(COLUMN_SIPARIS_OLUSTU))
        val siparisUrunEklendiMesaj = c.getString(c.getColumnIndexOrThrow(COLUMN_SIPARIS_URUN_EKLENDI))
        val smsOnayMesaj = c.getString(c.getColumnIndexOrThrow(COLUMN_SMS_ONAY))
        val whatsappOnayMesaj = c.getString(c.getColumnIndexOrThrow(COLUMN_WHATSAPP_ONAY))

        val varsayilan = c.getInt(c.getColumnIndexOrThrow(COLUMN_VARSAYILAN)) == 1
        val isDeleted = c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1

        // ✅ Tarihler - created_at ve updated_at kullan
        val createdAtIndex = c.getColumnIndex(COLUMN_CREATED_AT)
        val createdAt = if (createdAtIndex != -1) {
            c.getLong(createdAtIndex)
        } else {
            System.currentTimeMillis() / 1000
        }

        val updatedAtIndex = c.getColumnIndex(COLUMN_UPDATED_AT)
        val updatedAt = if (updatedAtIndex != -1) {
            c.getLong(updatedAtIndex)
        } else {
            System.currentTimeMillis() / 1000
        }

        return MesajSablon(
            id = id,
            firmaid = firmaid,
            firmaAdi = firmaAdi,
            tip = tip,
            baslik = baslik,
            musteriOlustuMesaj = musteriOlustuMesaj,
            musteriGuncellendiMesaj = musteriGuncellendiMesaj,
            siparisOlustuMesaj = siparisOlustuMesaj,
            siparisUrunEklendiMesaj = siparisUrunEklendiMesaj,
            smsOnayMesaj = smsOnayMesaj,
            whatsappOnayMesaj = whatsappOnayMesaj,
            varsayilan = varsayilan,
            isDeleted = isDeleted,
            olusturulmaTarihi = createdAt,     // created_at değerini ata
            guncellemeTarihi = updatedAt       // updated_at değerini ata
        )
    }
}