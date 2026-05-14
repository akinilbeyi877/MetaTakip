package com.example.metatakip.data.metaTakipDb.crud

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.metatakip.DeledLog.DeleteLogItem
import com.example.metatakip.feature_data.db.MetaTakipDb

class MetaTakipDeleteLogDao(context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val TAG = "DeleteLogDao"

    // ------------------------------------------------------------
    // ➕ LOG EKLE
    // ------------------------------------------------------------
    fun insertLog(
        entityType: String,
        entityId: Long,
        deletedBy: Long,
        reason: String?
    ): Long {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("entityType", entityType)
            put("entityId", entityId)
            put("deletedBy", deletedBy)
            put("deletedAt", System.currentTimeMillis())
            put("reason", reason)
        }

        val id = db.insert("delete_log", null, values)
        db.close()

        Log.d(TAG, "🗑️ Log eklendi: $entityType / $entityId")
        return id
    }

    // ------------------------------------------------------------
    // 📋 TÜM SİLME GEÇMİŞİ
    // ------------------------------------------------------------
    fun getAllLogs(): List<DeleteLogItem> {

        val list = mutableListOf<DeleteLogItem>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM delete_log ORDER BY deletedAt DESC",
            null
        )

        while (cursor.moveToNext()) {
            list.add(
                DeleteLogItem(
                    entityType = cursor.getString(cursor.getColumnIndexOrThrow("entityType")),
                    entityId = cursor.getLong(cursor.getColumnIndexOrThrow("entityId")),
                    deletedBy = cursor.getLong(cursor.getColumnIndexOrThrow("deletedBy")),
                    deletedAt = cursor.getLong(cursor.getColumnIndexOrThrow("deletedAt")),
                    reason = cursor.getString(cursor.getColumnIndexOrThrow("reason"))
                )
            )
        }

        cursor.close()
        db.close()
        return list
    }

    // ------------------------------------------------------------
    // 🔍 ENTITY'E GÖRE LOG
    // ------------------------------------------------------------
    fun getLogsByEntity(entityType: String): List<DeleteLogItem> {

        val list = mutableListOf<DeleteLogItem>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM delete_log WHERE entityType=? ORDER BY deletedAt DESC",
            arrayOf(entityType)
        )

        while (cursor.moveToNext()) {
            list.add(
                DeleteLogItem(
                    entityType = cursor.getString(cursor.getColumnIndexOrThrow("entityType")),
                    entityId = cursor.getLong(cursor.getColumnIndexOrThrow("entityId")),
                    deletedBy = cursor.getLong(cursor.getColumnIndexOrThrow("deletedBy")),
                    deletedAt = cursor.getLong(cursor.getColumnIndexOrThrow("deletedAt")),
                    reason = cursor.getString(cursor.getColumnIndexOrThrow("reason"))
                )
            )
        }

        cursor.close()
        db.close()
        return list
    }

    // ------------------------------------------------------------
    // 🧹 30 GÜNÜ GEÇENLERİ GETİR (KALICI SİLME İÇİN)
    // ------------------------------------------------------------
    fun getExpiredLogs(days: Int = 30): List<DeleteLogItem> {

        val expireTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val list = mutableListOf<DeleteLogItem>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM delete_log WHERE deletedAt < ?",
            arrayOf(expireTime.toString())
        )

        while (cursor.moveToNext()) {
            list.add(
                DeleteLogItem(
                    entityType = cursor.getString(cursor.getColumnIndexOrThrow("entityType")),
                    entityId = cursor.getLong(cursor.getColumnIndexOrThrow("entityId")),
                    deletedBy = cursor.getLong(cursor.getColumnIndexOrThrow("deletedBy")),
                    deletedAt = cursor.getLong(cursor.getColumnIndexOrThrow("deletedAt")),
                    reason = cursor.getString(cursor.getColumnIndexOrThrow("reason"))
                )
            )
        }

        cursor.close()
        db.close()
        return list
    }

    // ------------------------------------------------------------
    // ❌ LOG SİL (KALICI TEMİZLEME)
    // ------------------------------------------------------------
    fun deleteLog(entityType: String, entityId: Long): Boolean {

        val db = dbHelper.writableDatabase
        val result = db.delete(
            "delete_log",
            "entityType=? AND entityId=?",
            arrayOf(entityType, entityId.toString())
        )
        db.close()

        return result > 0
    }
}
