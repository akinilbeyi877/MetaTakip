package com.example.metatakip.feature_backup.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.data.TableCatalog
import com.example.metatakip.feature_data.db.MetaTakipDb
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PartialBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "PartialBackupManager"
        private val RESERVED_OR_METADATA_KEYS = setOf(
            "id", "table", "tableName", "actionType", "recordId", "changedAt", "synced", "deviceId"
        )

        // UUID sütun adı → (integer FK sütun adı, aranacak tablo)
        private val UUID_TO_FK_MAP = mapOf(
            "musteri_uuid" to Pair("musteriId", "musteri"),
            "siparis_uuid" to Pair("siparisId", "siparis"),
            "firma_uuid"   to Pair("firmaid",   "firma")
        )
    }

    // ==================== 🔥 UPSERT FONKSİYONLARI (CSV IMPORT İÇİN) ====================

    /**
     * 🔥 UPSERT: CSV import için özel - adSoyad ve ceptel bazında kontrol eder
     * Varsa günceller, yoksa ekler.
     * @return Etkilenen kaydın ID'si, hata varsa -1
     */
    fun upsertFromCsv(
        db: SQLiteDatabase,
        tableName: String,
        rowMap: Map<String, Any?>,
        uniqueFields: List<String> = listOf("adSoyad", "ceptel")
    ): Long {
        try {
            if (tableName != "musteri") {
                return insertDirectly(db, tableName, rowMap)
            }

            val existingId = findExistingCustomerByFields(db, tableName, rowMap, uniqueFields)

            return if (existingId != null) {
                updateFromCsv(db, tableName, existingId, rowMap)
                existingId
            } else {
                insertDirectly(db, tableName, rowMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ upsertFromCsv hatası: ${e.message}")
            return -1L
        }
    }

    /**
     * Mevcut müşteriyi adSoyad ve/veya ceptel bazında bul
     */
    private fun findExistingCustomerByFields(
        db: SQLiteDatabase,
        tableName: String,
        rowMap: Map<String, Any?>,
        uniqueFields: List<String>
    ): Long? {
        if (tableName != "musteri" || uniqueFields.isEmpty()) return null

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (field in uniqueFields) {
            val value = rowMap[field] as? String ?: continue
            if (value.isBlank()) continue
            conditions.add("$field = ?")
            args.add(value)
        }

        if (conditions.isEmpty()) return null

        val query = "SELECT id FROM $tableName WHERE ${conditions.joinToString(" AND ")} LIMIT 1"
        db.rawQuery(query, args.toTypedArray()).use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                Log.d(TAG, "✅ Mevcut kayıt bulundu: ID=$id (${conditions.joinToString(" AND ")})")
                return id
            }
        }
        return null
    }

    /**
     * Mevcut kaydı güncelle ve ChangeLog oluştur
     */
    private fun updateFromCsv(
        db: SQLiteDatabase,
        tableName: String,
        id: Long,
        rowMap: Map<String, Any?>
    ) {
        val realColumns = getTableColumns(db, tableName)
        val values = ContentValues()

        rowMap.forEach { (key, value) ->
            if (key in RESERVED_OR_METADATA_KEYS || !realColumns.contains(key)) return@forEach

            when (value) {
                null -> values.putNull(key)
                is Long -> values.put(key, value)
                is Int -> values.put(key, value)
                is String -> values.put(key, value)
                is Boolean -> values.put(key, if (value) 1 else 0)
                else -> values.put(key, value.toString())
            }
        }

        if (realColumns.contains("updatedAt")) {
            values.put("updatedAt", System.currentTimeMillis())
        }

        val rowsAffected = db.update(tableName, values, "id = ?", arrayOf(id.toString()))

        if (rowsAffected > 0) {
            Log.d(TAG, "✅ Müşteri güncellendi: ID=$id")
            try {
                val changeLogValues = ContentValues().apply {
                    put("table_name", tableName)
                    put("action_type", "UPDATE")
                    put("record_id", id)
                    put("changed_at", System.currentTimeMillis() / 1000)
                    put("details", JSONObject(rowMap).toString())
                    put("synced", 0)
                    put("user_id", 0)
                }
                db.insert("change_log", null, changeLogValues)
                Log.d(TAG, "✅ ChangeLog güncelleme kaydı eklendi: $tableName (ID: $id)")
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ ChangeLog güncelleme kaydı eklenemedi: ${e.message}")
            }
        } else {
            Log.e(TAG, "❌ Güncelleme başarısız: ID=$id")
        }
    }

    // ==================== MEVCUT FONKSİYONLAR (KORUNAN) ====================

    /**
     * 🔥 Tekil Değişikliği İşle (Conflict Resolution)
     */
    fun applySingleChangeLogToDb(db: SQLiteDatabase, change: ChangeLog): Boolean {
        return try {
            val tableName = change.tableName
            val realColumns = getTableColumns(db, tableName)
            val jsonDetails = JSONObject(change.details ?: "{}")

            val rootJson = if (jsonDetails.has("data")) jsonDetails.getJSONObject("data") else jsonDetails

            val incomingUuid = if (rootJson.has("uuid")) rootJson.optString("uuid", "")
            else jsonDetails.optString("uuid", "")

            val incomingUpdatedAt = if (rootJson.has("updatedAt")) rootJson.optLong("updatedAt", 0L)
            else jsonDetails.optLong("updatedAt", 0L)

            if (incomingUuid.isEmpty() && tableName != "firma") {
                Log.w(TAG, "⚠️ Kayıt atlanıyor: UUID bulunamadı ($tableName)")
                return false
            }

            val localRecord = findLocalRecordByUuid(db, tableName, incomingUuid)

            val values = convertJsonToContentValues(rootJson, realColumns, db)

            if (incomingUuid.isNotEmpty() && realColumns.contains("uuid")) {
                values.put("uuid", incomingUuid)
            }

            values.remove("id")

            if (localRecord != null) {
                val (localId, localUpdatedAt) = localRecord
                if (incomingUpdatedAt > localUpdatedAt) {
                    db.update(tableName, values, "id = ?", arrayOf(localId.toString())) > 0
                } else true
            } else {
                if (tableName == "siparis" && !values.containsKey("musteriId")) {
                    Log.e(TAG, "❌ KRİTİK: Sipariş için musteriId eşleşemedi! UUID: $incomingUuid")
                    return false
                }

                val newId = db.insert(tableName, null, values)
                if (newId == -1L) {
                    Log.e(TAG, "❌ INSERT başarısız: $tableName (Constraint hatası)")
                    false
                } else true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ applySingleChangeLogToDb hatası: ${e.message}")
            false
        }
    }

    /**
     * 🔥 Doğrudan veritabanına INSERT yapar.
     * CSV import gibi toplu veri eklemelerinde kullanılır.
     */
    fun insertDirectly(db: SQLiteDatabase, tableName: String, rowMap: Map<String, Any?>): Long {
        return try {
            val realColumns = getTableColumns(db, tableName)
            val values = ContentValues()

            rowMap.forEach { (key, value) ->
                if (key in RESERVED_OR_METADATA_KEYS || !realColumns.contains(key)) return@forEach

                when (value) {
                    null -> values.putNull(key)
                    is Long -> values.put(key, value)
                    is Int -> values.put(key, value)
                    is String -> values.put(key, value)
                    is Boolean -> values.put(key, if (value) 1 else 0)
                    else -> values.put(key, value.toString())
                }
            }

            if (!values.containsKey("uuid") && realColumns.contains("uuid")) {
                values.put("uuid", UUID.randomUUID().toString())
            }
            if (!values.containsKey("updatedAt") && realColumns.contains("updatedAt")) {
                values.put("updatedAt", System.currentTimeMillis())
            }

            val newId = db.insert(tableName, null, values)
            if (newId == -1L) {
                Log.e(TAG, "❌ Direct INSERT başarısız: $tableName")
            } else {
                Log.d(TAG, "✅ Direct INSERT başarılı: $tableName (ID: $newId)")
                try {
                    val changeLogValues = ContentValues().apply {
                        put("table_name", tableName)
                        put("action_type", "INSERT")
                        put("record_id", newId)
                        put("changed_at", System.currentTimeMillis() / 1000)
                        put("details", JSONObject(rowMap).toString())
                        put("synced", 0)
                        put("user_id", 0)
                    }
                    db.insert("change_log", null, changeLogValues)
                    Log.d(TAG, "✅ ChangeLog kaydı eklendi: $tableName (ID: $newId)")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ ChangeLog kaydı eklenemedi: ${e.message}")
                }
            }
            newId
        } catch (e: Exception) {
            Log.e(TAG, "❌ insertDirectly hatası: ${e.message}")
            -1L
        }
    }

    /**
     * 🔥 Toplu INSERT işlemi için (Batch)
     */
    fun insertBatchDirectly(db: SQLiteDatabase, tableName: String, rows: List<Map<String, Any?>>): Int {
        var successCount = 0
        try {
            db.beginTransaction()
            rows.forEach { rowMap ->
                val result = insertDirectly(db, tableName, rowMap)
                if (result != -1L) successCount++
            }
            db.setTransactionSuccessful()
            Log.d(TAG, "✅ Batch INSERT tamamlandı: $successCount/${rows.size} başarılı")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Batch INSERT hatası: ${e.message}")
        } finally {
            db.endTransaction()
        }
        return successCount
    }

    /**
     * 🔥 Toplu UPSERT işlemi için (Batch)
     */
    fun upsertBatchFromCsv(
        db: SQLiteDatabase,
        tableName: String,
        rows: List<Map<String, Any?>>,
        uniqueFields: List<String> = listOf("adSoyad", "ceptel")
    ): Int {
        var successCount = 0
        try {
            db.beginTransaction()
            rows.forEach { rowMap ->
                val result = upsertFromCsv(db, tableName, rowMap, uniqueFields)
                if (result != -1L) successCount++
            }
            db.setTransactionSuccessful()
            Log.d(TAG, "✅ Batch UPSERT tamamlandı: $successCount/${rows.size} başarılı")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Batch UPSERT hatası: ${e.message}")
        } finally {
            db.endTransaction()
        }
        return successCount
    }

    // ==================== YARDIMCI METOTLAR ====================

    /**
     * ✅ DÜZELTİLDİ: firma_uuid → firmaid çözümlemesi eklendi.
     * UUID_TO_FK_MAP üzerinden tüm ilişkisel UUID→integer FK dönüşümleri yapılır.
     */
    private fun convertJsonToContentValues(
        jsonObj: JSONObject,
        validColumns: Set<String>,
        db: SQLiteDatabase
    ): ContentValues {
        val values = ContentValues()
        val keys = jsonObj.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            // UUID sütununu integer FK sütununa eşle
            val fkInfo = UUID_TO_FK_MAP[key]
            val targetKey = when (key) {
                "musteri_uuid" -> "musteriId"
                "siparis_uuid" -> "siparisId"
                "firma_uuid"   -> "firmaid"
                else -> key
            }

            if (targetKey in RESERVED_OR_METADATA_KEYS || !validColumns.contains(targetKey)) continue

            val value = jsonObj.opt(key)

            // UUID sütunuysa → integer FK'ya çevir
            if (fkInfo != null) {
                if (value != null && value != JSONObject.NULL) {
                    val localId = findIdByUuid(db, fkInfo.second, value.toString())
                    if (localId != -1L) {
                        values.put(fkInfo.first, localId)
                        // UUID sütununu da koru (varsa)
                        if (validColumns.contains(key)) values.put(key, value.toString())
                    } else {
                        Log.w(TAG, "⚠️ UUID ile kayıt bulunamadı: tablo=${fkInfo.second}, uuid=$value")
                        // Integer FK bulunamadı ama UUID sütununu yine de kaydet
                        if (validColumns.contains(key)) values.put(key, value.toString())
                    }
                }
                continue
            }

            // Normal sütun
            when (value) {
                null, JSONObject.NULL -> values.putNull(targetKey)
                is Number -> values.put(targetKey, value.toLong())
                is String -> values.put(targetKey, value)
                is Boolean -> values.put(targetKey, if (value) 1 else 0)
                else -> values.put(targetKey, value.toString())
            }
        }
        return values
    }

    // 📦 PARTIAL YEDEKLEME / GERİ YÜKLEME

    fun restoreFromPartialZip(zipInput: InputStream, selectedTables: List<String>): Boolean {
        val tableRowsMap = mutableMapOf<String, JSONArray>()
        return try {
            ZipInputStream(BufferedInputStream(zipInput)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name.startsWith("json/")) {
                        val table = entry!!.name.substringAfter("json/").substringBefore(".json")
                        if (selectedTables.contains(table)) {
                            tableRowsMap[table] = JSONObject(zis.readBytes().toString(Charsets.UTF_8)).getJSONArray("rows")
                        }
                    }
                    zis.closeEntry()
                }
            }

            synchronized(MetaTakipDbLock.lock) {
                val db = MetaTakipDb.getInstance(context).writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL("PRAGMA foreign_keys=OFF")
                    TableCatalog.ALL_TABLES.filter { selectedTables.contains(it) }.forEach { t ->
                        val rows = tableRowsMap[t] ?: JSONArray()
                        insertJsonRows(db, t, rows)
                    }
                    db.execSQL("PRAGMA foreign_keys=ON")
                    db.setTransactionSuccessful()
                    true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore hatası: ${e.message}")
            false
        }
    }

    private fun insertJsonRows(db: SQLiteDatabase, table: String, rows: JSONArray) {
        val validColumns = getTableColumns(db, table)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val uuid = row.optString("uuid", "")
            val updatedAt = row.optLong("updatedAt", 0L)
            val local = if (uuid.isNotEmpty()) findLocalRecordByUuid(db, table, uuid) else null
            val values = convertJsonToContentValues(row, validColumns, db)
            if (uuid.isNotEmpty() && validColumns.contains("uuid")) values.put("uuid", uuid)
            values.remove("id")
            if (local != null) {
                if (local.second < updatedAt) db.update(table, values, "id = ?", arrayOf(local.first.toString()))
            } else {
                db.insert(table, null, values)
            }
        }
    }

    fun createPartialZip(selectedTables: List<String>): File {
        val tempDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val zipFile = File(tempDir, "partial_${System.currentTimeMillis()}.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            val info = JSONObject().apply {
                put("type", "partial")
                put("createdAt", System.currentTimeMillis())
                put("tables", JSONArray(selectedTables))
            }
            zip.putNextEntry(ZipEntry("backup_info.json"))
            zip.write(info.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            synchronized(MetaTakipDbLock.lock) {
                val db = MetaTakipDb.getInstance(context).readableDatabase
                selectedTables.forEach { exportTableAsJson(db, it, zip) }
            }
        }
        return zipFile
    }

    private fun exportTableAsJson(db: SQLiteDatabase, table: String, zip: ZipOutputStream) {
        db.rawQuery("SELECT * FROM \"$table\"", null).use { cursor ->
            val arr = JSONArray()
            while (cursor.moveToNext()) {
                val o = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    val col = cursor.getColumnName(i)
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> o.put(col, cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT   -> o.put(col, cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING  -> o.put(col, cursor.getString(i))
                        Cursor.FIELD_TYPE_NULL    -> o.put(col, JSONObject.NULL)
                        else                      -> o.put(col, cursor.getString(i))
                    }
                }
                arr.put(o)
            }
            zip.putNextEntry(ZipEntry("json/$table.json"))
            zip.write(
                JSONObject().apply { put("table", table); put("rows", arr) }
                    .toString().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
    }

    private fun findLocalRecordByUuid(db: SQLiteDatabase, table: String, uuid: String): Pair<Long, Long>? {
        return try {
            db.rawQuery(
                "SELECT id, updatedAt FROM \"$table\" WHERE uuid = ? LIMIT 1",
                arrayOf(uuid)
            ).use { c ->
                if (c.moveToFirst()) Pair(c.getLong(0), c.getLong(1)) else null
            }
        } catch (e: Exception) { null }
    }

    private fun findIdByUuid(db: SQLiteDatabase, table: String, uuid: String): Long {
        if (uuid.isEmpty()) return -1L
        return try {
            db.rawQuery(
                "SELECT id FROM \"$table\" WHERE uuid = ? LIMIT 1",
                arrayOf(uuid)
            ).use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        } catch (e: Exception) { -1L }
    }

    private fun getTableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val res = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info(\"$table\")", null).use { c ->
                while (c.moveToNext()) res.add(c.getString(c.getColumnIndexOrThrow("name")))
            }
        } catch (_: Exception) {}
        return res
    }

    /**
     * Anlık yedek klasöründeki tüm JSON dosyalarını geri yükler.
     */
    fun restoreFromInstantFolder(context: Context): Boolean {
        val instantFiles = InstantBackupManager.listAllInstantBackups(context)
        if (instantFiles.isEmpty()) return true

        synchronized(MetaTakipDbLock.lock) {
            val db = MetaTakipDb.getInstance(context).writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("PRAGMA foreign_keys=OFF")
                instantFiles.sortedBy { it.name }.forEach { file ->
                    try {
                        val json = JSONObject(file.readText(Charsets.UTF_8))
                        val change = ChangeLog(
                            id = json.getLong("changeLogId"),
                            tableName = json.getString("tableName"),
                            actionType = ChangeLog.ActionType.valueOf(json.getString("actionType")),
                            recordId = json.getLong("recordId"),
                            changedAt = json.getLong("changedAt"),
                            details = json.getJSONObject("data").toString(),
                            synced = false
                        )
                        applySingleChangeLogToDb(db, change)
                        file.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Instant restore hatası ${file.name}: ${e.message}")
                    }
                }
                db.execSQL("PRAGMA foreign_keys=ON")
                db.setTransactionSuccessful()
                return true
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * 🔥 Müşteri sayısını logla (debug için)
     */
    fun logCustomerCount(db: SQLiteDatabase, tag: String) {
        try {
            db.rawQuery("SELECT COUNT(*) FROM musteri", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d(TAG, "$tag - Müşteri sayısı: ${cursor.getInt(0)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$tag - Müşteri sayısı alınamadı: ${e.message}")
        }
    }
}