package dao

import android.content.ContentValues
import android.content.Context
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Firma

class MetaTakipFirmaDao(private val context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)

    /** 🧩 Yeni firma ekler */
    fun addFirma(firma: Firma): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("firmaAdi", firma.firmaAdi)
            put("adres", firma.adres)
            put("telefon", firma.telefon)
            put("vergiNo", firma.vergiNo)
            // Hibrit altyapı için: Eğer firma nesnesinde varsa ekle
            firma.uuid?.let { put("uuid", it) }
            put("updatedAt", System.currentTimeMillis())
        }
        val result = db.insert("firma", null, values)
        // ASLA db.close() yapma – bağlantı havuzda kalır
        return result != -1L
    }

    /** ✏️ ID’ye göre firma günceller (Int → Long) */
    fun updateFirmaById(id: Long, firma: Firma): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("firmaAdi", firma.firmaAdi)
            put("adres", firma.adres)
            put("telefon", firma.telefon)
            put("vergiNo", firma.vergiNo)
            put("updatedAt", System.currentTimeMillis())
        }
        val result = db.update("firma", values, "id=?", arrayOf(id.toString()))
        // ASLA db.close() yapma
        return result > 0
    }

    /** ❌ ID’ye göre firma siler (Int → Long) */
    fun deleteFirma(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete("firma", "id=?", arrayOf(id.toString()))
        // ASLA db.close() yapma
        return result > 0
    }

    /** 📋 Tüm firmaları getirir */
    fun getAllFirmas(): List<Firma> {
        val list = mutableListOf<Firma>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM firma ORDER BY id DESC", null)
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToFirma(cursor))
            }
        } finally {
            cursor.close()
            // ASLA db.close() yapma – bağlantı havuzda kalır
        }
        return list
    }

    /** 🔍 ID’ye göre firma getirir (Int → Long) */
    fun getFirmaById(id: Long): Firma? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM firma WHERE id=?", arrayOf(id.toString()))
        return try {
            if (cursor.moveToFirst()) cursorToFirma(cursor) else null
        } finally {
            cursor.close()
            // ASLA db.close() yapma
        }
    }

    /**
     * Cursor'daki veriyi güvenli bir şekilde Firma nesnesine dönüştürür.
     */
    private fun cursorToFirma(cursor: android.database.Cursor): Firma {
        return Firma(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            firmaAdi = cursor.getString(cursor.getColumnIndexOrThrow("firmaAdi")),
            adres = cursor.getString(cursor.getColumnIndexOrThrow("adres")),
            telefon = cursor.getString(cursor.getColumnIndexOrThrow("telefon")),
            vergiNo = cursor.getString(cursor.getColumnIndexOrThrow("vergiNo"))
        ).apply {
            // Şemada varsa uuid ve updatedAt alanlarını da doldur
            val uuidIdx = cursor.getColumnIndex("uuid")
            if (uuidIdx != -1) uuid = cursor.getString(uuidIdx)

            val updatedIdx = cursor.getColumnIndex("updatedAt")
            if (updatedIdx != -1) updatedAt = cursor.getLong(updatedIdx)
        }
    }

    // --- UYUMLULUK KÖPRÜLERİ ---

    /** Eski isimlendirme için alias */
    fun getAllFirmalar(): List<Firma> = getAllFirmas()

    fun updateFirmaById(id: Int, firma: Firma): Boolean = updateFirmaById(id.toLong(), firma)
    fun deleteFirma(id: Int): Boolean = deleteFirma(id.toLong())
    fun getFirmaById(id: Int): Firma? = getFirmaById(id.toLong())
}