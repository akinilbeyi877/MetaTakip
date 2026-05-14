package com.example.metatakip.feature_personel.data


import android.content.ContentValues
import android.content.Context
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Personel

class MetaTakipPersonelDaoImpl(
    private val context: Context
) : MetaTakipPersonelDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)

    override fun addPersonel(personel: Personel): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("adSoyad", personel.adSoyad)
            put("unvan", personel.unvan)
        }
        val result = db.insert("personel", null, values)
        db.close()
        return result != -1L
    }

    override fun updatePersonelById(id: Long, personel: Personel): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("adSoyad", personel.adSoyad)
            put("unvan", personel.unvan)
        }
        val result = db.update("personel", values, "id=?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }

    override fun deletePersonel(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete("personel", "id=?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }

    override fun getAllPersonel(): List<Personel> {
        val list = mutableListOf<Personel>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM personel ORDER BY id DESC", null)

        if (cursor.moveToFirst()) {
            do {
                list.add(
                    Personel(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        adSoyad = cursor.getString(cursor.getColumnIndexOrThrow("adSoyad")),
                        unvan = cursor.getString(cursor.getColumnIndexOrThrow("unvan"))
                    )
                )
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return list
    }

    override fun getPersonelById(id: Long): Personel? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM personel WHERE id=?", arrayOf(id.toString()))

        var result: Personel? = null

        if (cursor.moveToFirst()) {
            result = Personel(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                adSoyad = cursor.getString(cursor.getColumnIndexOrThrow("adSoyad")),
                unvan = cursor.getString(cursor.getColumnIndexOrThrow("unvan"))
            )
        }

        cursor.close()
        db.close()
        return result
    }
}