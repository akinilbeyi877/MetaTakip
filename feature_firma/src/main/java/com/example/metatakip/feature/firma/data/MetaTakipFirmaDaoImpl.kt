package com.example.metatakip.feature.firma.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Firma
import java.util.UUID

class MetaTakipFirmaDaoImpl(context: Context) : MetaTakipFirmaDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)

    companion object {
        private const val TAG = "MetaTakipFirmaDaoImpl"
    }

    // ─────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────
    override fun addFirma(firma: Firma): Long {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                // uuid: gelen değer varsa kullan, yoksa yeni üret
                put("uuid", firma.uuid.ifBlank { UUID.randomUUID().toString() })
                put("firmaAdi", firma.firmaAdi)
                put("adres", firma.adres)
                put("telefon", firma.telefon)
                put("vergiNo", firma.vergiNo)
                put("isDeleted", 0)
                put("updatedAt", System.currentTimeMillis())
            }
            db.insert("firma", null, values)
        } catch (e: Exception) {
            Log.e(TAG, "addFirma hata: ${e.message}")
            -1L
        } finally { db.close() }
    }

    // ─────────────────────────────────────────────
    // UPDATE — local id ile (aynı cihaz)
    // ─────────────────────────────────────────────
    override fun updateFirmaById(id: Long, firma: Firma): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                put("firmaAdi", firma.firmaAdi)
                put("adres", firma.adres)
                put("telefon", firma.telefon)
                put("vergiNo", firma.vergiNo)
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("firma", values, "id=?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            Log.e(TAG, "updateFirmaById hata: ${e.message}")
            false
        } finally { db.close() }
    }

    /**
     * UUID ile güncelle — çok cihazlı senkronizasyonda kullanılır.
     * Karşı cihazda local id farklı olabileceği için uuid üzerinden kayıt bulunur.
     */
    fun updateFirmaByUuid(uuid: String, firma: Firma): Boolean {
        // UUID ile yerel id bul, sonra normal updateFirmaById çağır
        var localId = -1L
        val db = dbHelper.readableDatabase
        try {
            db.rawQuery(
                "SELECT id FROM firma WHERE uuid=? AND isDeleted=0 LIMIT 1",
                arrayOf(uuid)
            ).use { c -> if (c.moveToFirst()) localId = c.getLong(0) }
        } catch (e: Exception) {
            Log.e(TAG, "updateFirmaByUuid sorgu hatası: \${e.message}")
        } finally {
            db.close()
        }
        if (localId <= 0L) {
            Log.w(TAG, "updateFirmaByUuid: uuid=$uuid bulunamadı — yeni kayıt oluşturuluyor")
            addFirma(firma.copy(uuid = uuid))
            return true
        }
        return updateFirmaById(localId, firma)
    }

    // ─────────────────────────────────────────────
    // DELETE — soft delete
    // ─────────────────────────────────────────────
    override fun deleteFirma(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                put("isDeleted", 1)
                put("deletedAt", System.currentTimeMillis())
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("firma", values, "id=?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            Log.e(TAG, "deleteFirma hata: ${e.message}")
            false
        } finally { db.close() }
    }

    // ─────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────
    override fun getAllFirmalar(): List<Firma> {
        val list = mutableListOf<Firma>()
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("SELECT * FROM firma WHERE isDeleted=0 ORDER BY firmaAdi ASC", null)
                .use { c -> while (c.moveToNext()) list.add(mapFirma(c)) }
            list
        } catch (e: Exception) {
            // isDeleted kolonu henüz yoksa eski sorguya düş
            try {
                db.rawQuery("SELECT * FROM firma ORDER BY id DESC", null)
                    .use { c -> while (c.moveToNext()) list.add(mapFirma(c)) }
            } catch (_: Exception) {}
            list
        } finally { db.close() }
    }

    override fun getFirmaById(id: Long): Firma? {
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("SELECT * FROM firma WHERE id=?", arrayOf(id.toString()))
                .use { c -> if (c.moveToFirst()) mapFirma(c) else null }
        } finally { db.close() }
    }

    /** UUID ile firma bul — sync için */
    fun getFirmaByUuid(uuid: String): Firma? {
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("SELECT * FROM firma WHERE uuid=? LIMIT 1", arrayOf(uuid))
                .use { c -> if (c.moveToFirst()) mapFirma(c) else null }
        } finally { db.close() }
    }

    // ─────────────────────────────────────────────
    // MAPPING
    // ─────────────────────────────────────────────
    private fun mapFirma(c: android.database.Cursor): Firma {
        fun str(col: String): String? = c.getColumnIndex(col).let { if (it != -1 && !c.isNull(it)) c.getString(it) else null }
        fun lng(col: String): Long? = c.getColumnIndex(col).let { if (it != -1 && !c.isNull(it)) c.getLong(it) else null }
        fun int0(col: String): Int = c.getColumnIndex(col).let { if (it != -1 && !c.isNull(it)) c.getInt(it) else 0 }
        return Firma(
            id        = c.getLong(c.getColumnIndexOrThrow("id")),
            uuid      = str("uuid") ?: UUID.randomUUID().toString(),
            firmaAdi  = str("firmaAdi") ?: "",
            adres     = str("adres"),
            telefon   = str("telefon"),
            vergiNo   = str("vergiNo"),
            isDeleted = int0("isDeleted"),
            deletedAt = lng("deletedAt"),
            updatedAt = lng("updatedAt") ?: 0L,
            createdAt = lng("createdAt") ?: 0L
        )
    }
}
