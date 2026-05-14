package dao

import android.content.ContentValues
import android.content.Context
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Unvan

class MetaTakipUnvanDao(private val context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)

    // ============================================================
    // ➕ YENİ ÜNVAN EKLE (ID DÖNDÜRÜR) ✅
    // ============================================================
    fun addUnvanReturnId(unvan: Unvan): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ad", unvan.ad)
            put("aciklama", unvan.aciklama)
        }
        val id = db.insert("unvan", null, values)
        // ASLA db.close() yapma – bağlantı havuzda kalır
        return id // -1L başarısız
    }

    // ============================================================
    // ➕ YENİ ÜNVAN EKLE (GERİYE DÖNÜK) ✅
    // ============================================================
    fun addUnvan(unvan: Unvan): Boolean {
        return addUnvanReturnId(unvan) != -1L
    }

    // ============================================================
    // ✏️ ID'YE GÖRE ÜNVAN GÜNCELLE (Long)
    // ============================================================
    fun updateUnvanById(id: Long, unvan: Unvan): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ad", unvan.ad)
            put("aciklama", unvan.aciklama)
        }
        val result = db.update(
            "unvan",
            values,
            "id=?",
            arrayOf(id.toString())
        )
        // ASLA db.close() yapma
        return result > 0
    }

    // ============================================================
    // ❌ ID'YE GÖRE ÜNVAN SİL (Long)
    // ============================================================
    fun deleteUnvanById(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete(
            "unvan",
            "id=?",
            arrayOf(id.toString())
        )
        // ASLA db.close() yapma
        return result > 0
    }

    // ============================================================
    // 📋 TÜM ÜNVANLARI GETİR
    // ============================================================
    fun getAllUnvanlar(): List<Unvan> {
        val list = mutableListOf<Unvan>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM unvan ORDER BY id DESC",
            null
        )

        try {
            while (cursor.moveToNext()) {
                val aciklamaIdx = cursor.getColumnIndex("aciklama")
                val aciklama = if (aciklamaIdx != -1 && !cursor.isNull(aciklamaIdx)) {
                    cursor.getString(aciklamaIdx)
                } else ""

                list.add(
                    Unvan(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        ad = cursor.getString(cursor.getColumnIndexOrThrow("ad")),
                        aciklama = aciklama
                    )
                )
            }
        } finally {
            cursor.close()
            // ASLA db.close() yapma – bağlantı havuzda kalır
        }
        return list
    }

    // ============================================================
    // 🔍 ID'YE GÖRE ÜNVAN GETİR (Long)
    // ============================================================
    fun getUnvanById(id: Long): Unvan? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM unvan WHERE id=?",
            arrayOf(id.toString())
        )

        return try {
            if (cursor.moveToFirst()) {
                val aciklamaIdx = cursor.getColumnIndex("aciklama")
                val aciklama = if (aciklamaIdx != -1 && !cursor.isNull(aciklamaIdx)) {
                    cursor.getString(aciklamaIdx)
                } else ""

                Unvan(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    ad = cursor.getString(cursor.getColumnIndexOrThrow("ad")),
                    aciklama = aciklama
                )
            } else null
        } finally {
            cursor.close()
            // ASLA db.close() yapma
        }
    }

    // ============================================================
    // 🔁 GEÇİŞ SÜRECİ İÇİN KÖPRÜ OVERLOAD'LAR (Int → Long)
    // ============================================================
    fun updateUnvanById(id: Int, unvan: Unvan): Boolean =
        updateUnvanById(id.toLong(), unvan)

    fun deleteUnvanById(id: Int): Boolean =
        deleteUnvanById(id.toLong())

    fun getUnvanById(id: Int): Unvan? =
        getUnvanById(id.toLong())
}