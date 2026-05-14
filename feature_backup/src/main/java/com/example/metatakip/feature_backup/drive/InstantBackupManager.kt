package com.example.metatakip.feature_backup.util

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_data.db.MetaTakipDb
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object InstantBackupManager {

    private const val TAG = "InstantBackupManager"
    private const val INSTANT_DIR = "backups/instant"
    private const val RETENTION_DAYS = 7

    /**
     * ChangeLogManager'dan gelen yeni değişiklik için anlık yedek oluşturur.
     */
    fun onNewChange(context: Context, change: ChangeLog) {
        // İsterseniz Drive bağlı olma şartını kaldırabilirsiniz
        if (!BackupPreferences.isDriveConnected()) {
            Log.d(TAG, "Drive bağlı değil, anlık yedek atlanıyor.")
            return
        }

        try {
            val dir = getInstantDir(context)
            dir.mkdirs()

            val recordJson = fetchRecordAsJson(context, change)
            val json = buildInstantBackupJson(context, change, recordJson)
            val fileName = "instant_${timestamp()}_${change.tableName}_${change.recordId}.json"
            val file = File(dir, fileName)

            FileOutputStream(file).use { fos ->
                fos.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }

            Log.d(TAG, "✅ Anlık yedek oluştu: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Anlık yedek hatası: ${e.message}")
        }
    }

    private fun fetchRecordAsJson(context: Context, change: ChangeLog): JSONObject {
        if (change.actionType == ChangeLog.ActionType.DELETE) return JSONObject()

        synchronized(MetaTakipDbLock.lock) {
            val db = MetaTakipDb.getInstance(context).readableDatabase
            val uuid = extractUuidFromDetails(change.details)
            val cursor = if (uuid != null) {
                db.rawQuery("SELECT * FROM `${change.tableName}` WHERE uuid = ? LIMIT 1", arrayOf(uuid))
            } else {
                db.rawQuery("SELECT * FROM `${change.tableName}` WHERE id = ? LIMIT 1", arrayOf(change.recordId.toString()))
            }
            return cursor.use { c ->
                if (c.moveToFirst()) cursorToJson(c) else JSONObject()
            }
        }
    }

    private fun extractUuidFromDetails(details: String?): String? {
        if (details.isNullOrBlank()) return null
        return try {
            val json = JSONObject(details)
            json.optString("uuid", null) ?: json.optString("recordUuid", null)
        } catch (e: Exception) { null }
    }

    private fun cursorToJson(cursor: Cursor): JSONObject {
        val obj = JSONObject()
        for (i in 0 until cursor.columnCount) {
            val col = cursor.getColumnName(i)
            when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> obj.put(col, cursor.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> obj.put(col, cursor.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> obj.put(col, cursor.getString(i) ?: "")
                Cursor.FIELD_TYPE_NULL -> obj.put(col, JSONObject.NULL)
                else -> obj.put(col, cursor.getString(i) ?: "")
            }
        }
        return obj
    }

    private fun buildInstantBackupJson(context: Context, change: ChangeLog, data: JSONObject): JSONObject {
        return JSONObject().apply {
            put("changeLogId", change.id)
            put("tableName", change.tableName)
            put("actionType", change.actionType.name)
            put("recordId", change.recordId)
            put("changedAt", change.changedAt)
            put("deviceId", BackupPreferences.getOrCreateDeviceId())
            put("data", data)
        }
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun getInstantDir(context: Context): File = File(context.filesDir, INSTANT_DIR)

    /** Eski anlık yedekleri temizler (7 günden eski) */
    fun cleanOldBackups(context: Context) {
        val dir = getInstantDir(context)
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("instant_") && file.lastModified() < cutoff) {
                file.delete()
                Log.d(TAG, "🗑 Eski instant yedek silindi: ${file.name}")
            }
        }
    }

    /** Tüm anlık yedek dosyalarını listeler */
    fun listAllInstantBackups(context: Context): List<File> {
        return getInstantDir(context).listFiles()?.filter { it.name.startsWith("instant_") } ?: emptyList()
    }
}