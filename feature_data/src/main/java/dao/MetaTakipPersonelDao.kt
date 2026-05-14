package dao

import android.content.ContentValues
import android.content.Context
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Personel

class MetaTakipPersonelDao(private val context: Context) {
    private val dbHelper = MetaTakipDb.getInstance(context)

    fun addPersonel(personel: Personel): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("adSoyad", personel.adSoyad)
            put("unvan", personel.unvan)
        }
        val result = db.insert("personel", null, values)
        // ASLA db.close() yapma – bağlantı havuzda kalır
        return result != -1L
    }

    // ✏️ Int → Long
    fun updatePersonelById(id: Long, personel: Personel): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("adSoyad", personel.adSoyad)
            put("unvan", personel.unvan)
        }
        val result = db.update("personel", values, "id=?", arrayOf(id.toString()))
        // ASLA db.close() yapma
        return result > 0
    }

    // ❌ Int → Long
    fun deletePersonel(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete("personel", "id=?", arrayOf(id.toString()))
        // ASLA db.close() yapma
        return result > 0
    }

    // 📋 getInt → getLong
    fun getAllPersonel(): List<Personel> {
        val list = mutableListOf<Personel>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM personel ORDER BY id DESC", null)
        try {
            while (cursor.moveToNext()) {
                list.add(
                    Personel(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        adSoyad = cursor.getString(cursor.getColumnIndexOrThrow("adSoyad")),
                        unvan = cursor.getString(cursor.getColumnIndexOrThrow("unvan"))
                    )
                )
            }
        } finally {
            cursor.close()
            // ASLA db.close() yapma – bağlantı havuzda kalır
        }
        return list
    }

    // 🔍 Int → Long, getInt → getLong
    fun getPersonelById(id: Long): Personel? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM personel WHERE id=?", arrayOf(id.toString()))
        return try {
            if (cursor.moveToFirst()) {
                Personel(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    adSoyad = cursor.getString(cursor.getColumnIndexOrThrow("adSoyad")),
                    unvan = cursor.getString(cursor.getColumnIndexOrThrow("unvan"))
                )
            } else null
        } finally {
            cursor.close()
            // ASLA db.close() yapma
        }
    }

    // 🔁 Geçiş süreci için köprü overload'lar (isteğe bağlı ama faydalı)
    fun updatePersonelById(id: Int, personel: Personel): Boolean = updatePersonelById(id.toLong(), personel)
    fun deletePersonel(id: Int): Boolean = deletePersonel(id.toLong())
    fun getPersonelById(id: Int): Personel? = getPersonelById(id.toLong())
}