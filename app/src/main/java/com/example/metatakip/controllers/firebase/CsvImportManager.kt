package com.example.metatakip.feature_backup.local

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.data.ImportRowResult
import com.example.metatakip.feature_backup.data.TableCatalog
import com.example.metatakip.feature_backup.firebase.FirebaseRealtimeBridgeManager
import com.example.metatakip.feature_backup.util.MetaTakipDbLock
import com.example.metatakip.feature_backup.util.PartialBackupManager
import com.example.metatakip.feature_data.db.MetaTakipDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object CsvImportManager {

    private const val TAG = "CsvImportManager"

    // 🔥 IMPORT BAŞLADIĞINDA SENKRONİZASYONU ENGELLEMEK İÇİN FLAG
    @Volatile
    var isImportInProgress = false
        private set

    data class CsvImportResult(
        val table: String,
        val rowIndex: Int,
        val status: ImportRowResult.Status,
        val message: String
    )

    interface FirmaMatchCallback {
        suspend fun onFirmaMatch(excelFirmaAdi: String, possibleMatches: List<Pair<Long, String>>): Long?
    }

    // ==================== NORMALİZASYON YARDIMCILARI ====================

    private fun normalizeString(input: String?): String {
        if (input == null) return ""
        return input.trim()
            .uppercase()
            .replace("İ", "I")
            .replace(Regex("\\s+"), " ")
    }

    private fun normalizeAddress(input: String?): String {
        if (input == null) return ""
        var result = input.trim()
            .uppercase()
            .replace("İ", "I")
            .replace(Regex("\\s+"), " ")
        result = result.replace("CADDESİ", "CAD")
        result = result.replace("SOKAK", "SOK")
        result = result.replace("DAİRE", "D")
        result = result.replace("NO", "NO")
        return result.trim()
    }

    private fun normalizePhone(input: String?): String {
        if (input == null) return ""
        val digits = input.replace(Regex("[^0-9]"), "")
        return if (digits.length == 10) "0$digits" else digits
    }

    private fun isMeaningfulValue(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        return !(trimmed.equals("0", ignoreCase = true) ||
                trimmed.equals("0.0", ignoreCase = true) ||
                trimmed.equals("null", ignoreCase = true) ||
                trimmed.equals("NULL", ignoreCase = true))
    }

    // ==================== DİNAMİK TEKRAR KONTROLÜ (SADECE UYARI İÇİN) ====================

    private fun isDuplicateCustomerDynamic(
        db: SQLiteDatabase,
        rowMap: Map<String, Any?>,
        uniqueFields: List<String>
    ): Boolean {
        if (uniqueFields.isEmpty()) return false

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        var meaningfulFieldCount = 0

        for (field in uniqueFields) {
            val rawValue = rowMap[field] as? String ?: ""
            if (!isMeaningfulValue(rawValue)) {
                Log.d(TAG, "Tekrar kontrolü: '$field' değeri boş/anlamsız, bu alan atlanıyor: '$rawValue'")
                continue
            }
            meaningfulFieldCount++
            val normalizedValue = when (field) {
                "adSoyad" -> normalizeString(rawValue)
                "ceptel", "ceptel2" -> normalizePhone(rawValue)
                "adres" -> normalizeAddress(rawValue)
                else -> normalizeString(rawValue)
            }
            conditions.add("TRIM(UPPER(COALESCE($field, ''))) = ?")
            args.add(normalizedValue)
        }

        if (meaningfulFieldCount == 0) {
            Log.d(TAG, "Tekrar kontrolü: Hiç anlamlı alan yok, atlanıyor.")
            return false
        }

        val query = "SELECT 1 FROM musteri WHERE ${conditions.joinToString(" AND ")} LIMIT 1"
        db.rawQuery(query, args.toTypedArray()).use { cursor ->
            if (cursor.count > 0) {
                Log.d(TAG, "⚠️ Tekrar uyarısı: ${conditions.joinToString(" AND ")} (kayıt güncellenecek, atlanmayacak)")
                return true
            }
        }
        return false
    }

    // ==================== 🔥 YENİ: MÜŞTERİ SAYISINI LOGLA ====================

    private fun logCustomerCount(db: SQLiteDatabase, tag: String) {
        try {
            db.rawQuery("SELECT COUNT(*) FROM musteri", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.w(TAG, "$tag - 📊 Müşteri sayısı: ${cursor.getInt(0)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$tag - Müşteri sayısı alınamadı: ${e.message}")
        }
    }

    // ==================== MANUEL SÜTUN EŞLEME İLE İÇE AKTARMA (UPSERT DESTEKLİ) ====================

    suspend fun importWithManualColumnMapping(
        context: Context,
        fileUri: Uri,
        tableName: String,
        columnMapping: Map<String, String>,
        firmaIdMapping: Map<String, Long?>,
        uniqueFields: List<String> = listOf("adSoyad", "ceptel"),
        defaultValues: Map<String, Any?>? = null
    ): List<CsvImportResult> = withContext(Dispatchers.IO) {
        // 🔥 IMPORT BAŞLADI - FLAG TRUE
        isImportInProgress = true
        Log.d(TAG, "🔐 CSV import başladı, senkronizasyon bekletilecek")

        val results = mutableListOf<CsvImportResult>()
        val fileName = getFileName(context.contentResolver, fileUri) ?: "unknown"

        val isExcel = fileName.endsWith(".xlsx", ignoreCase = true) ||
                fileName.endsWith(".xls", ignoreCase = true)

        val tempFile = if (isExcel) {
            convertExcelToCsv(context, fileUri)
        } else {
            File(context.cacheDir, "temp_manual_${System.currentTimeMillis()}.csv").apply {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(this).use { output -> input.copyTo(output) }
                } ?: return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya açılamadı"))
            }
        }

        if (tempFile == null) {
            isImportInProgress = false
            return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya okunamadı veya dönüştürülemedi"))
        }

        try {
            val db = synchronized(MetaTakipDbLock.lock) {
                MetaTakipDb.getInstance(context).writableDatabase
            }
            val partialManager = PartialBackupManager(context)

            // 🔥 IMPORT ÖNCESİ MÜŞTERİ SAYISINI LOGLA
            logCustomerCount(db, "🔴 IMPORT ÖNCESİ")

            db.beginTransaction()
            try {
                val lines = tempFile.readLines(Charsets.UTF_8)
                if (lines.isEmpty()) {
                    isImportInProgress = false
                    return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya boş"))
                }

                val firstLine = lines[0]
                val delimiter = if (firstLine.contains("\t")) "\t" else if (firstLine.contains(";")) ";" else ","
                val excelHeaders = firstLine.split(delimiter).map {
                    it.trim().replace("\"", "").replace(Regex("^[\\uFEFF\\u200B\\u200C\\u200D]"), "").trim()
                }.filter { it.isNotEmpty() }

                if (excelHeaders.isEmpty()) {
                    isImportInProgress = false
                    return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Başlık satırı okunamadı"))
                }

                val firmaExcelColumn = columnMapping.entries.find { it.value == "firmaid" }?.key
                val firmaColumnIndex = if (firmaExcelColumn != null) {
                    findHeaderIndex(excelHeaders, firmaExcelColumn)
                } else -1

                val columnIndexMap = mutableMapOf<String, Int>()
                columnMapping.forEach { (excelCol, dbCol) ->
                    if (dbCol != "firmaid") {
                        val idx = findHeaderIndex(excelHeaders, excelCol)
                        if (idx != -1) {
                            columnIndexMap[dbCol] = idx
                        } else {
                            Log.w(TAG, "⚠️ Uyarı: '$excelCol' sütunu dosyada bulunamadı, bu alan atlanacak.")
                        }
                    }
                }

                var successCount = 0
                var errorCount = 0
                var skippedFirmaCount = 0
                var duplicateWarningCount = 0
                var updateCount = 0
                var firmaColumnWarningLogged = false

                lines.drop(1).forEachIndexed { rowIndex, line ->
                    if (line.isBlank()) return@forEachIndexed
                    try {
                        val values = parseCsvLine(line)
                        val rowMap = mutableMapOf<String, Any?>()

                        columnIndexMap.forEach { (dbField, excelIdx) ->
                            if (excelIdx < values.size) {
                                val rawValue = values[excelIdx].replace("\"", "").trim()
                                if (rawValue.isNotEmpty() && rawValue != "null" && rawValue != "NULL") {
                                    val normalized = when (dbField) {
                                        "adSoyad" -> normalizeString(rawValue)
                                        "ceptel", "ceptel2" -> normalizePhone(rawValue)
                                        "adres" -> normalizeAddress(rawValue)
                                        else -> normalizeString(rawValue)
                                    }
                                    rowMap[dbField] = normalized
                                }
                            }
                        }

                        if (firmaColumnIndex != -1 && firmaColumnIndex < values.size) {
                            val excelFirma = values[firmaColumnIndex].replace("\"", "").trim()
                            if (excelFirma.isNotBlank()) {
                                val firmaId = firmaIdMapping[excelFirma]
                                if (firmaId != null) {
                                    rowMap["firmaid"] = firmaId
                                    rowMap["firmaAdi"] = excelFirma
                                } else {
                                    skippedFirmaCount++
                                    Log.w(TAG, "Firma eşleşemedi, satır atlandı: $excelFirma")
                                    return@forEachIndexed
                                }
                            }
                        } else if (firmaExcelColumn != null && !firmaColumnWarningLogged) {
                            Log.w(TAG, "⚠️ Eşleme hatası: '$firmaExcelColumn' sütunu dosyada bulunamadı.")
                            firmaColumnWarningLogged = true
                        }

                        defaultValues?.forEach { (key, value) ->
                            if (!rowMap.containsKey(key)) {
                                val rawDefault = value as? String ?: return@forEach
                                val normalizedDefault = when (key) {
                                    "adSoyad" -> normalizeString(rawDefault)
                                    "ceptel", "ceptel2" -> normalizePhone(rawDefault)
                                    "adres" -> normalizeAddress(rawDefault)
                                    else -> normalizeString(rawDefault)
                                }
                                rowMap[key] = normalizedDefault
                            }
                        }

                        if (rowMap["uuid"] == null) rowMap["uuid"] = UUID.randomUUID().toString()
                        if (rowMap["updatedAt"] == null) rowMap["updatedAt"] = System.currentTimeMillis()

                        // 🔥🔥🔥 KRİTİK: UPSERT KULLAN (INSERT OR UPDATE)
                        val isDuplicate = uniqueFields.isNotEmpty() && isDuplicateCustomerDynamic(db, rowMap, uniqueFields)
                        if (isDuplicate) {
                            duplicateWarningCount++
                        }

                        val newId = if (tableName == "musteri") {
                            partialManager.upsertFromCsv(db, tableName, rowMap, uniqueFields)
                        } else {
                            partialManager.insertDirectly(db, tableName, rowMap)
                        }

                        if (newId != -1L) {
                            successCount++
                            if (isDuplicate) {
                                updateCount++
                                Log.d(TAG, "🔄 Müşteri güncellendi: ${rowMap["adSoyad"]} (ID: $newId)")
                            } else {
                                Log.d(TAG, "✅ Müşteri eklendi: ${rowMap["adSoyad"]} (ID: $newId)")
                            }
                        } else {
                            errorCount++
                            Log.e(TAG, "❌ UPSERT başarısız: ${rowMap["adSoyad"]}")
                        }

                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Satır ${rowIndex + 1} hatası: ${e.message}")
                    }
                }

                // 🔧 firma_uuid onarımı: tüm satırlar işlendikten sonra bir kerede doldur
                if (tableName == "musteri") {
                    db.execSQL("""
                        UPDATE musteri
                        SET firma_uuid = (SELECT uuid FROM firma WHERE firma.id = musteri.firmaid)
                        WHERE (firma_uuid IS NULL OR firma_uuid = '') AND firmaid IS NOT NULL
                    """.trimIndent())
                    Log.d(TAG, "✅ musteri.firma_uuid onarıldı")
                }

                db.setTransactionSuccessful()

                val summary = buildString {
                    append("Başarılı: $successCount")
                    append(", Hatalı: $errorCount")
                    append(", Firma eşleşmeyen: $skippedFirmaCount")
                    if (updateCount > 0) append(", Güncellenen: $updateCount")
                    if (duplicateWarningCount > 0) append(", Tekrar uyarısı: $duplicateWarningCount")
                }

                results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.OK, summary))

                Log.d(TAG, "✅ CSV import tamamlandı: $summary")

            } finally {
                db.endTransaction()
            }

            // 🔥 IMPORT SONRASI MÜŞTERİ SAYISINI LOGLA
            logCustomerCount(db, "🔴 IMPORT SONRASI")

        } catch (e: Exception) {
            Log.e(TAG, "Genel hata: ${e.message}")
            results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Genel hata: ${e.message}"))
        } finally {
            tempFile.delete()
            // 🔥 IMPORT BİTTİ - FLAG FALSE
            isImportInProgress = false
            Log.d(TAG, "🔓 CSV import bitti, senkronizasyon tekrar aktif")

            delay(1000)

            try {
                Log.d(TAG, "🔄 CSV import tamamlandı, manuel senkronizasyon tetikleniyor...")
                FirebaseRealtimeBridgeManager.forceSyncNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Manuel senkronizasyon hatası: ${e.message}")
            }
        }

        return@withContext results
    }

    // ==================== CSV DOSYASINI DOĞRUDAN İÇE AKTAR (UPSERT DESTEKLİ) ====================

    suspend fun importCsvFile(
        context: Context,
        fileUri: Uri,
        contentResolver: ContentResolver
    ): List<CsvImportResult> = withContext(Dispatchers.IO) {
        isImportInProgress = true
        Log.d(TAG, "🔐 CSV import (doğrudan) başladı, senkronizasyon bekletilecek")

        val results = mutableListOf<CsvImportResult>()
        val fileName = getFileName(contentResolver, fileUri) ?: "unknown.csv"

        if (!fileName.endsWith(".csv")) {
            isImportInProgress = false
            return@withContext listOf(CsvImportResult("unknown", 0, ImportRowResult.Status.ERROR, "CSV dosyası değil: $fileName"))
        }

        val table = fileName.removeSuffix(".csv").lowercase()

        if (!TableCatalog.ALL_TABLES.contains(table)) {
            isImportInProgress = false
            return@withContext listOf(CsvImportResult(table, 0, ImportRowResult.Status.ERROR, "Geçersiz tablo adı: $table"))
        }

        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.csv")
        try {
            contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Dosya açılamadı")

            val db = synchronized(MetaTakipDbLock.lock) {
                MetaTakipDb.getInstance(context).writableDatabase
            }
            val partialManager = PartialBackupManager(context)

            try {
                db.beginTransaction()
                val lines = tempFile.readLines(Charsets.UTF_8)
                if (lines.isNotEmpty()) {
                    val headers = lines[0].split(",").map { it.trim().replace("\"", "") }

                    lines.drop(1).forEachIndexed { index, line ->
                        if (line.isBlank()) return@forEachIndexed
                        try {
                            val values = parseCsvLine(line)
                            val rowMap = mutableMapOf<String, Any?>()
                            headers.forEachIndexed { i, header ->
                                val value = values.getOrNull(i)?.trim()?.replace("\"", "")
                                rowMap[header] = if (value.isNullOrBlank() || value == "null") null else value
                            }
                            if (rowMap["uuid"] == null) rowMap["uuid"] = UUID.randomUUID().toString()
                            if (rowMap["updatedAt"] == null) rowMap["updatedAt"] = System.currentTimeMillis()

                            val validation = TableCatalog.validateRow(table, rowMap, index + 1)
                            if (validation.status != ImportRowResult.Status.ERROR) {
                                // 🔥 UPSERT KULLAN
                                val newId = if (table == "musteri") {
                                    partialManager.upsertFromCsv(db, table, rowMap, listOf("adSoyad", "ceptel"))
                                } else {
                                    partialManager.insertDirectly(db, table, rowMap)
                                }
                                if (newId != -1L) {
                                    results.add(CsvImportResult(table, index + 1, ImportRowResult.Status.OK, "İşlendi (UUID: ${rowMap["uuid"]})"))
                                } else {
                                    results.add(CsvImportResult(table, index + 1, ImportRowResult.Status.ERROR, "Veritabanı yazma hatası"))
                                }
                            } else {
                                results.add(CsvImportResult(table, index + 1, validation.status, validation.message))
                            }
                        } catch (e: Exception) {
                            results.add(CsvImportResult(table, index + 1, ImportRowResult.Status.ERROR, "Satır hatası: ${e.localizedMessage}"))
                        }
                    }
                }
                // 🔧 firma_uuid onarımı
                if (table == "musteri") {
                    db.execSQL("""
                        UPDATE musteri
                        SET firma_uuid = (SELECT uuid FROM firma WHERE firma.id = musteri.firmaid)
                        WHERE (firma_uuid IS NULL OR firma_uuid = '') AND firmaid IS NOT NULL
                    """.trimIndent())
                    Log.d(TAG, "✅ musteri.firma_uuid onarıldı (importCsvFile)")
                }

                db.setTransactionSuccessful()
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }
        } catch (e: Exception) {
            results.add(CsvImportResult(table, 0, ImportRowResult.Status.ERROR, "Genel hata: ${e.message}"))
        } finally {
            tempFile.delete()
            isImportInProgress = false
            Log.d(TAG, "🔓 CSV import (doğrudan) bitti, senkronizasyon tekrar aktif")

            delay(1000)

            try {
                Log.d(TAG, "🔄 CSV import tamamlandı (doğrudan), manuel senkronizasyon tetikleniyor...")
                FirebaseRealtimeBridgeManager.forceSyncNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Manuel senkronizasyon hatası: ${e.message}")
            }
        }
        return@withContext results
    }

    // ==================== EXCEL => CSV DÖNÜŞÜMÜ ====================

    private fun convertExcelToCsv(context: Context, uri: Uri): File? {
        return try {
            val tempCsvFile = File(context.cacheDir, "converted_${System.currentTimeMillis()}.csv")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val dataFormatter = DataFormatter()
                FileOutputStream(tempCsvFile).use { outputStream ->
                    for (row in sheet) {
                        val rowData = mutableListOf<String>()
                        for (cell in row) {
                            val value = dataFormatter.formatCellValue(cell)
                            rowData.add("\"${value.replace("\"", "\"\"")}\"")
                        }
                        outputStream.write(rowData.joinToString(",").toByteArray(Charsets.UTF_8))
                        outputStream.write("\n".toByteArray())
                    }
                }
                workbook.close()
            }
            tempCsvFile
        } catch (e: Exception) {
            Log.e(TAG, "Excel -> CSV dönüşüm hatası: ${e.message}")
            null
        }
    }

    // ==================== CSV BAŞLIKLARINI OKU ====================

    fun getCsvHeaders(context: Context, uri: Uri): List<String>? {
        return try {
            val fileName = getFileName(context.contentResolver, uri) ?: return null
            val actualFile = if (fileName.endsWith(".xlsx", ignoreCase = true) ||
                fileName.endsWith(".xls", ignoreCase = true)) {
                Log.d(TAG, "Excel dosyası algılandı, CSV'ye dönüştürülüyor: $fileName")
                convertExcelToCsv(context, uri) ?: return null
            } else {
                val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.csv")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                tempFile
            }
            val headers = actualFile.bufferedReader(Charsets.UTF_8).use { reader ->
                val firstLine = reader.readLine() ?: return null
                val delimiter = if (firstLine.contains("\t")) "\t" else if (firstLine.contains(";")) ";" else ","
                firstLine.split(delimiter).map {
                    it.trim().replace("\"", "").replace(Regex("[\\r\\n\\t]"), "").trim()
                }.filter { it.isNotEmpty() }
            }
            if (!fileName.endsWith(".xlsx", ignoreCase = true) && !fileName.endsWith(".xls", ignoreCase = true)) {
                actualFile.delete()
            }
            headers
        } catch (e: Exception) {
            Log.e(TAG, "getCsvHeaders hata: ${e.message}")
            null
        }
    }

    // ==================== DOSYADAN EŞSİZ FİRMA ADLARINI ÇIKAR ====================

    suspend fun extractUniqueFirmalarFromFile(
        context: Context,
        fileUri: Uri,
        contentResolver: ContentResolver,
        firmaColumnName: String
    ): List<String> = withContext(Dispatchers.IO) {
        val fileName = getFileName(contentResolver, fileUri) ?: return@withContext emptyList()
        val isExcel = fileName.endsWith(".xlsx", ignoreCase = true) ||
                fileName.endsWith(".xls", ignoreCase = true)
        val tempFile = if (isExcel) {
            convertExcelToCsv(context, fileUri) ?: return@withContext emptyList()
        } else {
            File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}.csv").apply {
                contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(this).use { output -> input.copyTo(output) }
                } ?: return@withContext emptyList()
            }
        }
        val lines = tempFile.readLines(Charsets.UTF_8)
        if (lines.isEmpty()) return@withContext emptyList()
        val headers = parseCsvLine(lines[0]).map { it.replace("\"", "").trim() }
        val firmaIndex = findHeaderIndex(headers, firmaColumnName)
        if (firmaIndex == -1) return@withContext emptyList()
        val uniqueFirmalar = mutableSetOf<String>()
        lines.drop(1).forEach { line ->
            if (line.isNotBlank()) {
                val values = parseCsvLine(line)
                if (firmaIndex < values.size) {
                    val firma = values[firmaIndex].replace("\"", "").trim()
                    if (firma.isNotBlank() && firma != "null" && firma != "NULL") {
                        uniqueFirmalar.add(firma)
                    }
                }
            }
        }
        tempFile.delete()
        uniqueFirmalar.toList()
    }

    // ==================== FİRMA EŞLEMESİZ İÇE AKTAR ====================

    suspend fun importWithoutFirma(
        context: Context,
        fileUri: Uri,
        tableName: String,
        columnMapping: Map<String, String>,
        defaultValues: Map<String, Any?>? = null
    ): List<CsvImportResult> = withContext(Dispatchers.IO) {
        isImportInProgress = true
        Log.d(TAG, "🔐 Import without firma başladı")

        val results = mutableListOf<CsvImportResult>()
        val fileName = getFileName(context.contentResolver, fileUri) ?: "unknown"
        val isExcel = fileName.endsWith(".xlsx", ignoreCase = true) || fileName.endsWith(".xls", ignoreCase = true)
        val tempFile = if (isExcel) {
            convertExcelToCsv(context, fileUri)
        } else {
            File(context.cacheDir, "temp_no_firma_${System.currentTimeMillis()}.csv").apply {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(this).use { output -> input.copyTo(output) }
                } ?: return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya açılamadı"))
            }
        }
        if (tempFile == null) {
            isImportInProgress = false
            return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya okunamadı"))
        }
        try {
            val db = synchronized(MetaTakipDbLock.lock) {
                MetaTakipDb.getInstance(context).writableDatabase
            }
            val partialManager = PartialBackupManager(context)
            db.beginTransaction()
            try {
                val lines = tempFile.readLines(Charsets.UTF_8)
                if (lines.isEmpty()) {
                    isImportInProgress = false
                    return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya boş"))
                }
                val firstLine = lines[0]
                val delimiter = if (firstLine.contains("\t")) "\t" else if (firstLine.contains(";")) ";" else ","
                val excelHeaders = firstLine.split(delimiter).map {
                    it.trim().replace("\"", "").replace(Regex("^[\\uFEFF\\u200B\\u200C\\u200D]"), "").trim()
                }.filter { it.isNotEmpty() }
                val columnIndexMap = mutableMapOf<String, Int>()
                columnMapping.forEach { (excelCol, dbCol) ->
                    val idx = findHeaderIndex(excelHeaders, excelCol)
                    if (idx != -1) {
                        columnIndexMap[dbCol] = idx
                    } else {
                        Log.w(TAG, "⚠️ Uyarı: '$excelCol' sütunu dosyada bulunamadı, bu alan atlanacak.")
                    }
                }
                var successCount = 0
                var errorCount = 0
                lines.drop(1).forEachIndexed { rowIndex, line ->
                    if (line.isBlank()) return@forEachIndexed
                    try {
                        val values = parseCsvLine(line)
                        val rowMap = mutableMapOf<String, Any?>()
                        columnIndexMap.forEach { (dbField, excelIdx) ->
                            if (excelIdx < values.size) {
                                val rawValue = values[excelIdx].replace("\"", "").trim()
                                if (rawValue.isNotEmpty() && rawValue != "null" && rawValue != "NULL") {
                                    rowMap[dbField] = rawValue
                                }
                            }
                        }
                        defaultValues?.forEach { (key, value) ->
                            if (!rowMap.containsKey(key)) rowMap[key] = value
                        }
                        if (rowMap["uuid"] == null) rowMap["uuid"] = UUID.randomUUID().toString()
                        if (rowMap["updatedAt"] == null) rowMap["updatedAt"] = System.currentTimeMillis()

                        val newId = if (tableName == "musteri") {
                            partialManager.upsertFromCsv(db, tableName, rowMap, listOf("adSoyad", "ceptel"))
                        } else {
                            partialManager.insertDirectly(db, tableName, rowMap)
                        }
                        if (newId != -1L) {
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Satır ${rowIndex + 1} hatası: ${e.message}")
                    }
                }
                db.setTransactionSuccessful()
                results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.OK, "Başarılı: $successCount, Hatalı: $errorCount"))
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Genel hata: ${e.message}"))
        } finally {
            tempFile.delete()
            isImportInProgress = false
            Log.d(TAG, "🔓 Import without firma bitti")

            delay(1000)

            try {
                FirebaseRealtimeBridgeManager.forceSyncNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Manuel senkronizasyon hatası: ${e.message}")
            }
        }
        return@withContext results
    }

    // ==================== ÖNCEDEN EŞLENMİŞ FİRMALAR İLE İÇE AKTAR ====================

    suspend fun importByMappingWithPreMatchedFirmas(
        context: Context,
        fileUri: Uri,
        mapping: Map<String, String>,
        tableName: String,
        firmaMapping: Map<String, Long?>,
        defaultValues: Map<String, Any?>? = null
    ): List<CsvImportResult> = withContext(Dispatchers.IO) {
        isImportInProgress = true
        Log.d(TAG, "🔐 Import with pre-matched firmas başladı")

        val results = mutableListOf<CsvImportResult>()
        val fileName = getFileName(context.contentResolver, fileUri) ?: "unknown.csv"
        val isExcel = fileName.endsWith(".xlsx", ignoreCase = true) || fileName.endsWith(".xls", ignoreCase = true)
        val tempFile = if (isExcel) {
            convertExcelToCsv(context, fileUri) ?: return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Excel dosyası okunamadı!"))
        } else {
            File(context.cacheDir, "temp_mapping_${System.currentTimeMillis()}.csv").apply {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(this).use { output -> input.copyTo(output) }
                } ?: return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya açılamadı"))
            }
        }
        try {
            val db = synchronized(MetaTakipDbLock.lock) {
                MetaTakipDb.getInstance(context).writableDatabase
            }
            val partialManager = PartialBackupManager(context)
            db.beginTransaction()
            try {
                val lines = tempFile.readLines(Charsets.UTF_8)
                if (lines.isEmpty()) {
                    isImportInProgress = false
                    return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya boş"))
                }
                val firstLine = lines[0]
                val delimiter = if (firstLine.contains("\t")) "\t" else if (firstLine.contains(";")) ";" else ","
                val cleanHeaders = firstLine.split(delimiter).map {
                    it.trim().replace("\"", "").replace(Regex("^[\\uFEFF\\u200B\\u200C\\u200D]"), "").trim()
                }.filter { it.isNotEmpty() }
                val firmaAdiIndex = cleanHeaders.indexOfFirst {
                    it.equals("firmaAdi", ignoreCase = true) ||
                            it.equals("firma", ignoreCase = true) ||
                            it.equals("company", ignoreCase = true)
                }
                var successCount = 0
                var errorCount = 0
                var skippedCount = 0
                lines.drop(1).forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    try {
                        val values = parseCsvLine(line)
                        val rowMap = mutableMapOf<String, Any?>()
                        mapping.forEach { (myField, otherField) ->
                            val otherIdx = findHeaderIndex(cleanHeaders, otherField)
                            if (otherIdx != -1 && otherIdx < values.size) {
                                val rawValue = values[otherIdx].replace("\"", "").trim()
                                if (rawValue.isNotEmpty() && rawValue != "null" && rawValue != "NULL") {
                                    rowMap[myField] = rawValue
                                }
                            }
                        }
                        if (firmaAdiIndex != -1 && firmaAdiIndex < values.size) {
                            val excelFirma = values[firmaAdiIndex].replace("\"", "").trim()
                            if (excelFirma.isNotBlank()) {
                                val firmaId = firmaMapping[excelFirma]
                                if (firmaId != null) {
                                    rowMap["firmaid"] = firmaId
                                    rowMap["firmaAdi"] = excelFirma
                                } else {
                                    skippedCount++
                                    return@forEachIndexed
                                }
                            }
                        }
                        defaultValues?.forEach { (key, value) ->
                            if (!rowMap.containsKey(key)) rowMap[key] = value
                        }
                        if (rowMap["uuid"] == null) rowMap["uuid"] = UUID.randomUUID().toString()
                        if (rowMap["updatedAt"] == null) rowMap["updatedAt"] = System.currentTimeMillis()

                        val newId = if (tableName == "musteri") {
                            partialManager.upsertFromCsv(db, tableName, rowMap, listOf("adSoyad", "ceptel"))
                        } else {
                            partialManager.insertDirectly(db, tableName, rowMap)
                        }
                        if (newId != -1L) {
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Satır ${index + 1} hatası: ${e.message}")
                    }
                }
                db.setTransactionSuccessful()
                results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.OK,
                    "Başarılı: $successCount, Hatalı: $errorCount, Atlanan: $skippedCount"))
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Genel hata: ${e.message}")
            results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Genel hata: ${e.message}"))
        } finally {
            tempFile.delete()
            isImportInProgress = false
            Log.d(TAG, "🔓 Import with pre-matched firmas bitti")

            delay(1000)

            try {
                FirebaseRealtimeBridgeManager.forceSyncNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Manuel senkronizasyon hatası: ${e.message}")
            }
        }
        return@withContext results
    }

    // ==================== FİRMA EŞLEMESİZ BASİT MAPPING ====================

    suspend fun importByMappingWithoutFirma(
        context: Context,
        fileUri: Uri,
        mapping: Map<String, String>,
        tableName: String,
        defaultValues: Map<String, Any?>? = null
    ): List<CsvImportResult> = withContext(Dispatchers.IO) {
        isImportInProgress = true
        Log.d(TAG, "🔐 Import by mapping without firma başladı")

        val results = mutableListOf<CsvImportResult>()
        val fileName = getFileName(context.contentResolver, fileUri) ?: "unknown.csv"
        val isExcel = fileName.endsWith(".xlsx", ignoreCase = true) || fileName.endsWith(".xls", ignoreCase = true)
        val tempFile = if (isExcel) {
            convertExcelToCsv(context, fileUri) ?: return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Excel dosyası okunamadı!"))
        } else {
            File(context.cacheDir, "temp_mapping_${System.currentTimeMillis()}.csv").apply {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(this).use { output -> input.copyTo(output) }
                } ?: return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya açılamadı"))
            }
        }
        try {
            val db = synchronized(MetaTakipDbLock.lock) {
                MetaTakipDb.getInstance(context).writableDatabase
            }
            val partialManager = PartialBackupManager(context)
            db.beginTransaction()
            try {
                val lines = tempFile.readLines(Charsets.UTF_8)
                if (lines.isEmpty()) {
                    isImportInProgress = false
                    return@withContext listOf(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Dosya boş"))
                }
                val firstLine = lines[0]
                val delimiter = if (firstLine.contains("\t")) "\t" else if (firstLine.contains(";")) ";" else ","
                val cleanHeaders = firstLine.split(delimiter).map {
                    it.trim().replace("\"", "").replace(Regex("^[\\uFEFF\\u200B\\u200C\\u200D]"), "").trim()
                }.filter { it.isNotEmpty() }
                var successCount = 0
                var errorCount = 0
                lines.drop(1).forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    try {
                        val values = parseCsvLine(line)
                        val rowMap = mutableMapOf<String, Any?>()
                        mapping.forEach { (myField, otherField) ->
                            val otherIdx = findHeaderIndex(cleanHeaders, otherField)
                            if (otherIdx != -1 && otherIdx < values.size) {
                                val rawValue = values[otherIdx].replace("\"", "").trim()
                                if (rawValue.isNotEmpty() && rawValue != "null" && rawValue != "NULL") {
                                    rowMap[myField] = rawValue
                                }
                            }
                        }
                        defaultValues?.forEach { (key, value) ->
                            if (!rowMap.containsKey(key)) rowMap[key] = value
                        }
                        if (rowMap["uuid"] == null) rowMap["uuid"] = UUID.randomUUID().toString()
                        if (rowMap["updatedAt"] == null) rowMap["updatedAt"] = System.currentTimeMillis()

                        val newId = if (tableName == "musteri") {
                            partialManager.upsertFromCsv(db, tableName, rowMap, listOf("adSoyad", "ceptel"))
                        } else {
                            partialManager.insertDirectly(db, tableName, rowMap)
                        }
                        if (newId != -1L) {
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Satır ${index + 1} hatası: ${e.message}")
                    }
                }
                db.setTransactionSuccessful()
                results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.OK, "Başarılı: $successCount, Hatalı: $errorCount"))
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            results.add(CsvImportResult(tableName, 0, ImportRowResult.Status.ERROR, "Genel hata: ${e.message}"))
        } finally {
            tempFile.delete()
            isImportInProgress = false
            Log.d(TAG, "🔓 Import by mapping without firma bitti")

            delay(1000)

            try {
                FirebaseRealtimeBridgeManager.forceSyncNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Manuel senkronizasyon hatası: ${e.message}")
            }
        }
        return@withContext results
    }

    // ==================== FİRMA EŞLEME YARDIMCILARI ====================

    suspend fun importByMapping(
        context: Context,
        fileUri: Uri,
        mapping: Map<String, String>,
        tableName: String,
        customFirmaName: String? = null,
        defaultValues: Map<String, Any?>? = null,
        firmaMatchCallback: FirmaMatchCallback? = null
    ): List<CsvImportResult> {
        return emptyList()
    }

    suspend fun matchOrAskFirma(
        context: Context,
        excelFirmaAdi: String,
        callback: FirmaMatchCallback?
    ): Long? = withContext(Dispatchers.IO) {
        if (excelFirmaAdi.isBlank()) return@withContext null
        val db = synchronized(MetaTakipDbLock.lock) {
            MetaTakipDb.getInstance(context).readableDatabase
        }
        try {
            var cursor = db.rawQuery(
                "SELECT id, firmaAdi FROM firma WHERE lower(firmaAdi) = lower(?) LIMIT 1",
                arrayOf(excelFirmaAdi)
            )
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                cursor.close()
                Log.d(TAG, "✅ Tam eşleşme: $excelFirmaAdi -> ID:$id")
                return@withContext id
            }
            cursor.close()
            val excelLowerCase = excelFirmaAdi.lowercase()
            cursor = db.rawQuery("SELECT id, firmaAdi FROM firma", null)
            val matches = mutableListOf<Pair<Long, String>>()
            while (cursor.moveToNext()) {
                val dbId = cursor.getLong(0)
                val dbFirmaAdi = cursor.getString(1).lowercase()
                if (dbFirmaAdi.contains(excelLowerCase) || excelLowerCase.contains(dbFirmaAdi)) {
                    matches.add(Pair(dbId, cursor.getString(1)))
                }
            }
            cursor.close()
            if (matches.size == 1) {
                Log.d(TAG, "✅ İçerik eşleşmesi: $excelFirmaAdi -> ${matches[0].second}")
                return@withContext matches[0].first
            }
            if (callback != null) {
                val allFirmalar = mutableListOf<Pair<Long, String>>()
                val allCursor = db.rawQuery("SELECT id, firmaAdi FROM firma ORDER BY firmaAdi", null)
                while (allCursor.moveToNext()) {
                    allFirmalar.add(Pair(allCursor.getLong(0), allCursor.getString(1)))
                }
                allCursor.close()
                return@withContext callback.onFirmaMatch(excelFirmaAdi, allFirmalar)
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Firma eşleştirme hatası: ${e.message}")
            return@withContext null
        }
    }

    suspend fun createNewFirma(context: Context, firmaAdi: String): Long = withContext(Dispatchers.IO) {
        val db = synchronized(MetaTakipDbLock.lock) {
            MetaTakipDb.getInstance(context).writableDatabase
        }
        try {
            val values = ContentValues().apply {
                put("firmaAdi", firmaAdi)
                put("uuid", UUID.randomUUID().toString())
                put("updatedAt", System.currentTimeMillis())
            }
            db.insert("firma", null, values)
        } finally {
            // db.close() çağrılmaz
        }
    }

    suspend fun getMevcutFirmalar(context: Context): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        val db = synchronized(MetaTakipDbLock.lock) {
            MetaTakipDb.getInstance(context).readableDatabase
        }
        try {
            val cursor = db.rawQuery("SELECT id, firmaAdi FROM firma ORDER BY firmaAdi", null)
            val list = mutableListOf<Pair<Long, String>>()
            while (cursor.moveToNext()) {
                list.add(Pair(cursor.getLong(0), cursor.getString(1)))
            }
            cursor.close()
            list
        } finally {
            // db.close() çağrılmaz
        }
    }

    // ==================== YARDIMCI FONKSİYONLAR ====================

    private fun findHeaderIndex(headers: List<String>, target: String): Int {
        val normalizedTarget = target.trim().lowercase()
        return headers.indexOfFirst { it.trim().lowercase() == normalizedTarget }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) fileName = cursor.getString(nameIndex)
        }
        return fileName
    }

    internal fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i += 2
                } else {
                    inQuotes = !inQuotes
                    i++
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current.clear()
                i++
            } else {
                current.append(c)
                i++
            }
        }
        result.add(current.toString().trim())
        return result
    }
}