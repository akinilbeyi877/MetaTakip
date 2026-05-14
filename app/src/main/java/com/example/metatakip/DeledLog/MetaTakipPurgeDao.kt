package com.example.metatakip.data.metaTakipDb.crud

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb

class MetaTakipPurgeDao(context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "MetaTakipPurgeDao"

    /**
     * 30 günden eski soft delete kayıtlarını kalıcı siler
     */
    fun purgeDeletedData(olderThanMillis: Long): Int {

        val db = dbHelper.writableDatabase
        db.beginTransaction()

        return try {

            // 1️⃣ Silinmiş siparişleri bul
            val cursor = db.rawQuery(
                """
                SELECT id FROM siparis
                WHERE isDeleted=1 AND createdAt < ?
                """.trimIndent(),
                arrayOf(olderThanMillis.toString())
            )

            var deletedSiparisCount = 0

            while (cursor.moveToNext()) {
                val siparisId = cursor.getLong(0)

                // 2️⃣ Ürünleri GERÇEKTEN sil
                db.delete(
                    "urun",
                    "siparisId=?",
                    arrayOf(siparisId.toString())
                )

                // 3️⃣ Siparişi GERÇEKTEN sil
                db.delete(
                    "siparis",
                    "id=?",
                    arrayOf(siparisId.toString())
                )

                // 4️⃣ Log temizle
                db.delete(
                    "delete_log",
                    "entityId=? AND entityType IN ('siparis','urun')",
                    arrayOf(siparisId.toString())
                )

                deletedSiparisCount++
            }

            cursor.close()

            db.setTransactionSuccessful()
            deletedSiparisCount

        } catch (e: Exception) {
            Log.e(TAG, "❌ purgeDeletedData hatası", e)
            0
        } finally {
            db.endTransaction()
            db.close()
        }
    }
}
