package com.example.metatakip.data.metaTakipDb.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Order

class SoftDeleteService(private val context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "SoftDeleteService"

    // =========================================================
    // 🗑️ SİPARİŞ + ÜRÜNLER SOFT DELETE
    // (User + Admin kullanabilir)
    // =========================================================
    fun deleteSiparis(
        siparisId: Long,
        deletedByUserId: Long,
        reason: String? = null
    ): Boolean {

        val db = dbHelper.writableDatabase
        db.beginTransaction()

        return try {

            val siparisResult = db.update(
                "siparis",
                ContentValues().apply { put("isDeleted", 1) },
                "id=? AND isDeleted=0",
                arrayOf(siparisId.toString())
            )

            if (siparisResult == 0) {
                throw Exception("Sipariş bulunamadı veya zaten silinmiş")
            }

            db.update(
                "urun",
                ContentValues().apply { put("isDeleted", 1) },
                "siparisId=? AND isDeleted=0",
                arrayOf(siparisId.toString())
            )

            insertDeleteLog(
                db,
                entityType = "siparis",
                entityId = siparisId,
                userId = deletedByUserId,
                reason = reason
            )

            val cursor = db.rawQuery(
                "SELECT id FROM urun WHERE siparisId=?",
                arrayOf(siparisId.toString())
            )

            while (cursor.moveToNext()) {
                insertDeleteLog(
                    db,
                    entityType = "urun",
                    entityId = cursor.getLong(0),
                    userId = deletedByUserId,
                    reason = "Sipariş silindi"
                )
            }
            cursor.close()

            db.setTransactionSuccessful()
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ deleteSiparis hatası", e)
            false
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    // =========================================================
    // ♻️ SİPARİŞ + ÜRÜNLER GERİ AL
    // ❗ SADECE ADMIN
    // =========================================================
    fun restoreSiparis(
        siparisId: Long,
        restoredByUserId: Long,
        isAdmin: Boolean
    ): Boolean {

        if (!isAdmin) {
            Log.w(TAG, "❌ Yetkisiz restore denemesi")
            return false
        }

        val db = dbHelper.writableDatabase
        db.beginTransaction()

        return try {

            val siparisResult = db.update(
                "siparis",
                ContentValues().apply { put("isDeleted", 0) },
                "id=? AND isDeleted=1",
                arrayOf(siparisId.toString())
            )

            if (siparisResult == 0) {
                throw Exception("Geri alınacak sipariş bulunamadı")
            }

            db.update(
                "urun",
                ContentValues().apply { put("isDeleted", 0) },
                "siparisId=? AND isDeleted=1",
                arrayOf(siparisId.toString())
            )

            insertDeleteLog(
                db,
                entityType = "restore",
                entityId = siparisId,
                userId = restoredByUserId,
                reason = "Admin tarafından geri alındı"
            )

            db.setTransactionSuccessful()
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ restoreSiparis hatası", e)
            false
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    // =========================================================
    // 📋 ÇÖP KUTUSU – SİLİNEN SİPARİŞLER
    // =========================================================
    fun getDeletedSiparis(): List<Order> {

        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM siparis WHERE isDeleted=1 ORDER BY id DESC",
            null
        )

        while (cursor.moveToNext()) {
            list.add(
                Order(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    musteriId = cursor.getLong(cursor.getColumnIndexOrThrow("musteriId")),
                    musteriAdi = cursor.getString(cursor.getColumnIndexOrThrow("musteriAdi")),
                    musteriTelefon = cursor.getString(cursor.getColumnIndexOrThrow("musteriTelefon")),
                    firmaAdi = cursor.getString(cursor.getColumnIndexOrThrow("firmaAdi")),
                    urunTipi = cursor.getString(cursor.getColumnIndexOrThrow("urunTipi")),
                    durum = cursor.getString(cursor.getColumnIndexOrThrow("durum"))
                )
            )
        }

        cursor.close()
        db.close()
        return list
    }

    // =========================================================
    // 🧾 DELETE LOG (PRIVATE)
    // =========================================================
    private fun insertDeleteLog(
        db: SQLiteDatabase,
        entityType: String,
        entityId: Long,
        userId: Long,
        reason: String?
    ) {
        val values = ContentValues().apply {
            put("entityType", entityType)
            put("entityId", entityId)
            put("deletedBy", userId)
            put("deletedAt", System.currentTimeMillis())
            put("reason", reason)
        }
        db.insert("delete_log", null, values)
    }
}
