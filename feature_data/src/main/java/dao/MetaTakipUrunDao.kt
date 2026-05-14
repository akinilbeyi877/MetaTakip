package dao

import android.content.ContentValues
import android.content.Context
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Urun

class MetaTakipUrunDao(private val context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)

    // ============================================================
    // 📋 TÜM ÜRÜNLER (SİLİNMİŞLER DAHİL)
    // ============================================================
    fun getAllUrunlerIncludingDeleted(): List<Urun> {
        val list = mutableListOf<Urun>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM urun ORDER BY id DESC",
            null
        )

        try {
            while (cursor.moveToNext()) {
                list.add(
                    Urun(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        siparisId = cursor.getLong(cursor.getColumnIndexOrThrow("siparisId")),
                        ad = cursor.getString(cursor.getColumnIndexOrThrow("ad")),
                        adet = cursor.getInt(cursor.getColumnIndexOrThrow("adet")),
                        m2 = cursor.getDouble(cursor.getColumnIndexOrThrow("m2")),
                        fiyat = cursor.getDouble(cursor.getColumnIndexOrThrow("fiyat")),
                        tutar = cursor.getDouble(cursor.getColumnIndexOrThrow("tutar")),
                        isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted"))
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
    // ➕ ÜRÜN EKLE
    // ============================================================
    fun addUrun(urun: Urun): Boolean {
        val db = dbHelper.writableDatabase

        // 🔗 KURESEL BAG: siparisin uuid'sini DB'den cek (entity'de yoksa)
        val siparisUuid: String = if (urun.siparisUuid.isNotBlank()) urun.siparisUuid else lookupSiparisUuid(db, urun.siparisId)

        // 🌍 URUN UUID: entity'de yoksa uret
        val urunUuid: String = if (urun.uuid.isNotBlank()) urun.uuid else java.util.UUID.randomUUID().toString()

        val values = ContentValues().apply {
            put("uuid", urunUuid)
            put("siparisId", urun.siparisId)
            put("siparis_uuid", siparisUuid)
            put("ad", urun.ad)
            put("adet", urun.adet)
            put("m2", urun.m2)
            put("fiyat", urun.fiyat)
            put("tutar", urun.tutar)
            put("isDeleted", 0)
            put("updatedAt", System.currentTimeMillis())
        }
        val result = db.insert("urun", null, values)
        android.util.Log.i("MetaTakipUrunDao", "✅ addUrun id=$result urunUuid=$urunUuid siparisId=${urun.siparisId} siparisUuid=$siparisUuid")
        return result != -1L
    }

    private fun lookupSiparisUuid(db: android.database.sqlite.SQLiteDatabase, siparisId: Long): String {
        if (siparisId <= 0L) return ""
        return try {
            val c = db.rawQuery("SELECT uuid FROM siparis WHERE id = ? LIMIT 1", arrayOf(siparisId.toString()))
            val u = if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            c.close()
            u
        } catch (e: Exception) {
            android.util.Log.w("MetaTakipUrunDao", "lookupSiparisUuid hata: " + e.message)
            ""
        }
    }

    // ============================================================
    // 📋 SİPARİŞE AİT AKTİF ÜRÜNLER
    // ============================================================
    fun getUrunlerBySiparisId(siparisId: Long): List<Urun> {
        val list = mutableListOf<Urun>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT * FROM urun
            WHERE siparisId=? AND isDeleted=0
            ORDER BY id DESC
            """,
            arrayOf(siparisId.toString())
        )

        try {
            while (cursor.moveToNext()) {
                list.add(
                    Urun(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        siparisId = cursor.getLong(cursor.getColumnIndexOrThrow("siparisId")),
                        ad = cursor.getString(cursor.getColumnIndexOrThrow("ad")),
                        adet = cursor.getInt(cursor.getColumnIndexOrThrow("adet")),
                        m2 = cursor.getDouble(cursor.getColumnIndexOrThrow("m2")),
                        fiyat = cursor.getDouble(cursor.getColumnIndexOrThrow("fiyat")),
                        tutar = cursor.getDouble(cursor.getColumnIndexOrThrow("tutar")),
                        isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted"))
                    )
                )
            }
        } finally {
            cursor.close()
            // ASLA db.close() yapma
        }
        return list
    }

    // ============================================================
    // 🔍 TEK ÜRÜN GETİR (DÜZENLEME İÇİN)
    // ============================================================
    fun getUrunById(urunId: Long): Urun? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM urun WHERE id=?",
            arrayOf(urunId.toString())
        )

        return try {
            if (cursor.moveToFirst()) {
                Urun(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    siparisId = cursor.getLong(cursor.getColumnIndexOrThrow("siparisId")),
                    ad = cursor.getString(cursor.getColumnIndexOrThrow("ad")),
                    adet = cursor.getInt(cursor.getColumnIndexOrThrow("adet")),
                    m2 = cursor.getDouble(cursor.getColumnIndexOrThrow("m2")),
                    fiyat = cursor.getDouble(cursor.getColumnIndexOrThrow("fiyat")),
                    tutar = cursor.getDouble(cursor.getColumnIndexOrThrow("tutar")),
                    isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted"))
                )
            } else null
        } finally {
            cursor.close()
            // ASLA db.close() yapma
        }
    }

    // ============================================================
    // ✏️ ÜRÜN GÜNCELLE
    // ============================================================
    fun updateUrun(urun: Urun): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ad", urun.ad)
            put("adet", urun.adet)
            put("m2", urun.m2)
            put("fiyat", urun.fiyat)
            put("tutar", urun.tutar)
        }
        val result = db.update(
            "urun",
            values,
            "id=?",
            arrayOf(urun.id.toString())
        )
        // ASLA db.close() yapma
        return result > 0
    }

    // ============================================================
    // 🗑️ TEK ÜRÜN SOFT DELETE
    // ============================================================
    fun softDeleteUrun(urunId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("isDeleted", 1)
        }
        val result = db.update(
            "urun",
            values,
            "id=? AND isDeleted=0",
            arrayOf(urunId.toString())
        )
        // ASLA db.close() yapma
        return result > 0
    }

    // ============================================================
    // 🗑️ SİPARİŞE AİT TÜM ÜRÜNLERİ SOFT DELETE
    // ============================================================
    fun softDeleteUrunlerBySiparisId(siparisId: Long): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("isDeleted", 1)
        }
        val result = db.update(
            "urun",
            values,
            "siparisId=? AND isDeleted=0",
            arrayOf(siparisId.toString())
        )
        // ASLA db.close() yapma
        return result
    }

    // ============================================================
    // ♻️ TEK ÜRÜN GERİ AL
    // ============================================================
    fun restoreUrun(urunId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("isDeleted", 0)
        }
        val result = db.update(
            "urun",
            values,
            "id=? AND isDeleted=1",
            arrayOf(urunId.toString())
        )
        // ASLA db.close() yapma
        return result > 0
    }

    // ============================================================
    // ♻️ SİPARİŞE AİT TÜM ÜRÜNLERİ GERİ AL
    // ============================================================
    fun restoreUrunlerBySiparisId(siparisId: Long): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("isDeleted", 0)
        }
        val result = db.update(
            "urun",
            values,
            "siparisId=? AND isDeleted=1",
            arrayOf(siparisId.toString())
        )
        // ASLA db.close() yapma
        return result
    }
}