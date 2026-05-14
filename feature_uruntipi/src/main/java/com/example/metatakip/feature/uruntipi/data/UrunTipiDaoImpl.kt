package com.example.metatakip.feature.uruntipi.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.UrunTipi

/**
 * 🗄 UrunTipiDaoImpl
 * MetaTakipDb kullanarak SQLite implementasyonu
 */
class UrunTipiDaoImpl(
    context: Context
) : UrunTipiDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)

    // --------------------------------------------------
    // GET ALL
    // --------------------------------------------------
    override fun getAll(): List<UrunTipi> {
        val list = mutableListOf<UrunTipi>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM urun_tipi
            WHERE is_deleted = 0
            ORDER BY ad ASC
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        return list
    }

    // --------------------------------------------------
    // GET ACTIVE
    // --------------------------------------------------
    override fun getActive(): List<UrunTipi> {
        val list = mutableListOf<UrunTipi>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM urun_tipi
            WHERE aktif = 1 AND is_deleted = 0
            ORDER BY ad ASC
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        return list
    }

    // --------------------------------------------------
    // GET BY ID
    // --------------------------------------------------
    override fun getById(id: Long): UrunTipi? {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM urun_tipi WHERE id = ? AND is_deleted = 0",
            arrayOf(id.toString())
        )

        cursor.use {
            return if (it.moveToFirst()) mapCursor(it) else null
        }
    }

    // --------------------------------------------------
    // INSERT - ✅ Long döner
    // --------------------------------------------------
    override fun insert(item: UrunTipi): Long {
        val db = dbHelper.writableDatabase

        // ✅ AD benzersiz mi kontrol et
        if (adExists(item.ad, 0)) {
            throw IllegalArgumentException("Bu ürün adı zaten mevcut: ${item.ad}")
        }

        val values = ContentValues().apply {
            put("ad", item.ad)
            put("birim_fiyat", item.birimFiyat)
            put("hesap_tipi", item.hesapTipi)
            put("aktif", item.aktif)
            put("aciklama", item.aciklama)
            put("created_at", System.currentTimeMillis() / 1000)
            put("is_deleted", 0)
        }

        return db.insert("urun_tipi", null, values)
    }

    // --------------------------------------------------
    // UPDATE - ✅ Boolean döner
    // --------------------------------------------------
    override fun update(id: Long, item: UrunTipi): Boolean {
        val db = dbHelper.writableDatabase

        // ✅ AD benzersiz mi kontrol et (kendisi hariç)
        if (adExists(item.ad, id)) {
            throw IllegalArgumentException("Bu ürün adı zaten başka kayıtta mevcut: ${item.ad}")
        }

        val values = ContentValues().apply {
            put("ad", item.ad)
            put("birim_fiyat", item.birimFiyat)
            put("hesap_tipi", item.hesapTipi)
            put("aktif", item.aktif)
            put("aciklama", item.aciklama)
            put("updated_at", System.currentTimeMillis() / 1000)
        }

        val rowsAffected = db.update(
            "urun_tipi",
            values,
            "id = ? AND is_deleted = 0",
            arrayOf(id.toString())
        )

        return rowsAffected > 0
    }

    // --------------------------------------------------
    // DELETE - ✅ Boolean döner
    // --------------------------------------------------
    override fun delete(id: Long): Boolean {
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("aktif", 0)
            put("is_deleted", 1)
            put("updated_at", System.currentTimeMillis() / 1000)
        }

        val rowsAffected = db.update(
            "urun_tipi",
            values,
            "id = ? AND is_deleted = 0",
            arrayOf(id.toString())
        )

        return rowsAffected > 0
    }

    // --------------------------------------------------
    // SEARCH BY NAME
    // --------------------------------------------------
    override fun searchByName(name: String): List<UrunTipi> {
        val list = mutableListOf<UrunTipi>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT * FROM urun_tipi
            WHERE ad LIKE ? AND is_deleted = 0
            ORDER BY ad ASC
            """.trimIndent(),
            arrayOf("%$name%")
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(mapCursor(it))
            }
        }
        return list
    }

    // --------------------------------------------------
    // AD EXISTS - ✅ DEFAULT VALUE KALDIRILDI
    // --------------------------------------------------
    override fun adExists(ad: String, excludeId: Long): Boolean {
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT COUNT(*) as count
            FROM urun_tipi
            WHERE ad = ? AND id != ? AND is_deleted = 0
            """.trimIndent(),
            arrayOf(ad, excludeId.toString())
        )

        cursor.use {
            return if (it.moveToFirst()) {
                it.getInt(0) > 0
            } else {
                false
            }
        }
    }

    // --------------------------------------------------
    // GET STATISTICS (Ekstra metod) - @Suppress("UNUSED")
    // --------------------------------------------------
    @Suppress("UNUSED")
    fun getStatistics(): Map<String, Any> {
        val db = dbHelper.readableDatabase
        val stats = mutableMapOf<String, Any>()

        // Toplam
        val totalCursor = db.rawQuery(
            "SELECT COUNT(*) FROM urun_tipi WHERE is_deleted = 0",
            null
        )
        totalCursor.use {
            if (it.moveToFirst()) stats["total"] = it.getInt(0)
        }

        // Aktif
        val activeCursor = db.rawQuery(
            "SELECT COUNT(*) FROM urun_tipi WHERE aktif = 1 AND is_deleted = 0",
            null
        )
        activeCursor.use {
            if (it.moveToFirst()) stats["active"] = it.getInt(0)
        }

        // Pasif
        stats["inactive"] = (stats["total"] as Int) - (stats["active"] as Int)

        // Ortalama fiyat
        val avgCursor = db.rawQuery(
            "SELECT AVG(birim_fiyat) FROM urun_tipi WHERE is_deleted = 0",
            null
        )
        avgCursor.use {
            if (it.moveToFirst()) stats["avgPrice"] = it.getDouble(0)
        }

        // Hesap tipi dağılımı
        val typeCursor = db.rawQuery(
            """
            SELECT hesap_tipi, COUNT(*) as count
            FROM urun_tipi 
            WHERE is_deleted = 0
            GROUP BY hesap_tipi
            """.trimIndent(),
            null
        )

        val typeMap = mutableMapOf<String, Int>()
        typeCursor.use {
            while (it.moveToNext()) {
                val tip = it.getString(0)
                val count = it.getInt(1)
                typeMap[tip] = count
            }
        }
        stats["types"] = typeMap

        return stats
    }

    // --------------------------------------------------
    // TEST DATA (Ekstra metod) - @Suppress("UNUSED")
    // --------------------------------------------------
    @Suppress("UNUSED")
    fun addTestData() {
        if (getAll().isEmpty()) {
            val testData = listOf(
                UrunTipi(
                    ad = "Halı",
                    birimFiyat = 150.0,
                    hesapTipi = "M2",
                    aktif = 1,
                    aciklama = "Yün halı",
                    isDeleted = 0,
                    createdAt = System.currentTimeMillis() / 1000,
                    updatedAt = null
                ),
                UrunTipi(
                    ad = "Koltuk",
                    birimFiyat = 200.0,
                    hesapTipi = "ADET",
                    aktif = 1,
                    aciklama = "Deri koltuk",
                    isDeleted = 0,
                    createdAt = System.currentTimeMillis() / 1000,
                    updatedAt = null
                ),
                UrunTipi(
                    ad = "Perde",
                    birimFiyat = 75.0,
                    hesapTipi = "METRE",
                    aktif = 1,
                    aciklama = "Tül perde",
                    isDeleted = 0,
                    createdAt = System.currentTimeMillis() / 1000,
                    updatedAt = null
                ),
                UrunTipi(
                    ad = "Minder",
                    birimFiyat = 50.0,
                    hesapTipi = "ADET",
                    aktif = 1,
                    aciklama = "Pamuk minder",
                    isDeleted = 0,
                    createdAt = System.currentTimeMillis() / 1000,
                    updatedAt = null
                ),
                UrunTipi(
                    ad = "Yorgan",
                    birimFiyat = 120.0,
                    hesapTipi = "ADET",
                    aktif = 0,
                    aciklama = "Eski yorgan",
                    isDeleted = 0,
                    createdAt = System.currentTimeMillis() / 1000,
                    updatedAt = null
                )
            )

            testData.forEach { insert(it) }
        }
    }

    // --------------------------------------------------
    // 🧠 Cursor → Model Mapper - NULL SAFE VERSION
    // --------------------------------------------------
    private fun mapCursor(c: Cursor): UrunTipi {
        // Helper function for safe column access
        fun getNullableString(cursor: Cursor, columnName: String): String? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }

        fun getNullableLong(cursor: Cursor, columnName: String): Long? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }

        fun getNullableInt(cursor: Cursor, columnName: String): Int? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else null
        }

        return UrunTipi(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            ad = c.getString(c.getColumnIndexOrThrow("ad")),
            birimFiyat = c.getDouble(c.getColumnIndexOrThrow("birim_fiyat")),
            hesapTipi = c.getString(c.getColumnIndexOrThrow("hesap_tipi")),
            aktif = c.getInt(c.getColumnIndexOrThrow("aktif")),
            aciklama = getNullableString(c, "aciklama"),
            isDeleted = getNullableInt(c, "is_deleted") ?: 0,
            createdAt = getNullableLong(c, "created_at"),
            updatedAt = getNullableLong(c, "updated_at")
        )
    }
}