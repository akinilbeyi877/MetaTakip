// File: UnvanDaoImpl.kt
package com.example.metatakip.feature.unvan.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Unvan
import com.example.metatakip.feature_data.unvan.UnvanDaoInterface

class UnvanDaoImpl(context: Context) : UnvanDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)

    override fun addUnvan(unvan: Unvan): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("ad", unvan.ad)
            put("aciklama", unvan.aciklama)
        }
        val id = db.insert("unvan", null, values)
        db.close()
        return id // -1L başarısız
    }

    override fun updateUnvanById(id: Long, unvan: Unvan): Boolean {
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
        db.close()
        return result > 0
    }

    override fun deleteUnvanById(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete(
            "unvan",
            "id=?",
            arrayOf(id.toString())
        )
        db.close()
        return result > 0
    }

    override fun getUnvanById(id: Long): Unvan? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM unvan WHERE id=?",
            arrayOf(id.toString())
        )

        val unvan = if (cursor.moveToFirst()) mapUnvan(cursor) else null
        cursor.close()
        db.close()
        return unvan
    }

    override fun getAllUnvanlar(): List<Unvan> {
        val list = mutableListOf<Unvan>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM unvan ORDER BY id DESC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(mapUnvan(cursor))
        }
        cursor.close()
        db.close()
        return list
    }

    private fun mapUnvan(cursor: Cursor): Unvan {
        fun getStringOrEmpty(col: String): String {
            val i = cursor.getColumnIndex(col)
            return if (i != -1 && !cursor.isNull(i)) cursor.getString(i) else ""
        }

        return Unvan(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            ad = getStringOrEmpty("ad"),
            aciklama = getStringOrEmpty("aciklama")
        )
    }
}