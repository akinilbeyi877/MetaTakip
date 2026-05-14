package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupFolderType
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.google.api.services.drive.model.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 🔍 En Güncel Yedek Bulucu
 * Drive hiyerarşisinde geriye dönük tarama yaparak en son yüklenen
 * geçerli Hibrit ZIP paketini tespit eder.
 */
object LatestBackupFinder {

    private const val TAG = "LatestBackupFinder"

    data class LatestBackup(
        val fileId: String,
        val fileName: String,
        val dayFolder: String,
        val modifiedTimeMs: Long,
        val sizeBytes: Long
    )

    /**
     * Drive'daki klasör yapısını (Root/Type/YYYY-MM-DD) tarayarak en güncel yedeği bulur.
     * Hibrit sistemde 'Partial' klasörlerini taramak için kullanılır.
     */
    suspend fun findLatestBackup(
        context: Context,
        type: BackupFolderType,
        maxDaysBack: Int = 14
    ): LatestBackup? {
        try {
            val driveService = DriveBackupManager.getDriveService(context)
            if (driveService == null) {
                Log.e(TAG, "❌ Drive servisi alınamadı")
                return null
            }

            val rootFolderName = BackupPreferences.getBackupFolderName()
            Log.d(TAG, "🔍 Kök klasör adı: $rootFolderName")
            Log.d(TAG, "🔍 Aranan tip: ${type.folderName}")
            Log.d(TAG, "🔍 Maksimum geri gün sayısı: $maxDaysBack")

            // 1. Kök klasör ve Tip klasörü ID'lerini al
            val rootId = DriveUploadHelper.findOrCreateFolder(driveService, rootFolderName, null)
            if (rootId == null) {
                Log.e(TAG, "❌ Kök klasör ID alınamadı: $rootFolderName")
                return null
            }
            Log.d(TAG, "✅ Kök klasör ID: $rootId")

            val typeId = DriveUploadHelper.findOrCreateFolder(driveService, type.folderName, rootId)
            if (typeId == null) {
                Log.e(TAG, "❌ Tip klasörü ID alınamadı: ${type.folderName}")
                return null
            }
            Log.d(TAG, "✅ Tip klasörü ID: $typeId")

            // 🔥 DEBUG: Tip klasörü altındaki tüm alt klasörleri listele
            try {
                val allFoldersQuery = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and '$typeId' in parents"
                val allFolders = driveService.files()
                    .list()
                    .setQ(allFoldersQuery)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()
                Log.d(TAG, "📁 Tip klasörü altındaki tüm klasörler: ${allFolders.files?.map { "${it.name} (${it.id})" } ?: "[]"}")
            } catch (e: Exception) {
                Log.e(TAG, "Alt klasör listeleme hatası: ${e.message}")
            }

            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()

            var checkedDays = 0
            var foundBackup: LatestBackup? = null

            // 2. Belirlenen gün sayısı kadar geriye giderek klasörleri tara
            for (dayOffset in 0..maxDaysBack) {
                val dayFolder = dayFormat.format(cal.time)
                checkedDays++
                Log.d(TAG, "🔍 [$checkedDays/$maxDaysBack] Kontrol edilen klasör: $dayFolder")

                val dayId = findFolder(driveService, dayFolder, typeId)

                if (dayId != null) {
                    Log.d(TAG, "✅ Klasör bulundu: $dayFolder (ID: $dayId)")

                    // Klasör bulundu, içindeki ZIP dosyalarını listele
                    val filesInDay = listFiles(driveService, dayId)
                        .filter { it.name?.endsWith(".zip", ignoreCase = true) == true }

                    Log.d(TAG, "📁 $dayFolder içinde ${filesInDay.size} ZIP dosyası bulundu:")
                    filesInDay.forEach { file ->
                        Log.d(TAG, "   - ${file.name} (${file.size?.toLong() ?: 0} bytes, modified: ${file.modifiedTime})")
                    }

                    // 3. Dosyaları hem modifiedTime hem de isimdeki Timestamp'e göre sırala
                    val latest = filesInDay.maxByOrNull { file ->
                        val modified = file.modifiedTime?.value?.toLong() ?: 0L
                        val parsed = parseTimestampFromName(file.name ?: "")
                        val timestamp = if (parsed != 0L) parsed else modified
                        Log.d(TAG, "   ${file.name} -> modified=$modified, parsed=$parsed, kullanılan=$timestamp")
                        timestamp
                    }

                    if (latest != null) {
                        Log.d(TAG, "✅ En güncel yedek bulundu: ${latest.name} (Klasör: $dayFolder)")
                        foundBackup = LatestBackup(
                            fileId = latest.id,
                            fileName = latest.name,
                            dayFolder = dayFolder,
                            modifiedTimeMs = latest.modifiedTime?.value?.toLong() ?: 0L,
                            sizeBytes = latest.size?.toLong() ?: 0L
                        )
                        break
                    } else {
                        Log.d(TAG, "⚠️ $dayFolder klasöründe geçerli ZIP dosyası bulunamadı")
                    }
                } else {
                    Log.d(TAG, "❌ Klasör bulunamadı: $dayFolder")
                }

                // Bir gün geriye git (sadece döngü devam edecekse)
                if (dayOffset < maxDaysBack) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
            }

            if (foundBackup == null) {
                Log.w(TAG, "⚠️ Son $checkedDays gün içinde hiç yedek dosyası bulunamadı.")
                Log.w(TAG, "   Kontrol edilen klasörler: ${getLastCheckedFolders(dayFormat, checkedDays)}")
            } else {
                Log.d(TAG, "🎉 Yedek bulundu: ${foundBackup.fileName} (${foundBackup.sizeBytes} bytes)")
            }

            return foundBackup

        } catch (e: Exception) {
            Log.e(TAG, "💥 findLatestBackup kritik hata: ${e.message}", e)
            return null
        }
    }

    /**
     * Drive dizinindeki dosyaları listeler (Meta bilgilerini çekerek).
     */
    private fun listFiles(
        driveService: com.google.api.services.drive.Drive,
        parentId: String
    ): List<File> {
        return try {
            val query = "trashed = false and '$parentId' in parents"
            Log.d(TAG, "🔍 Dosya sorgusu: $query")

            val result = driveService.files()
                .list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, size)")
                .execute()

            result.files ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Dosya listeleme hatası: ${e.message}")
            emptyList()
        }
    }

    /**
     * Belirli bir isimdeki klasörü bulur.
     */
    private fun findFolder(
        driveService: com.google.api.services.drive.Drive,
        folderName: String,
        parentId: String?
    ): String? {
        return try {
            val escaped = folderName.replace("'", "\\'")
            val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
            val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and name = '$escaped'$parentClause"

            Log.d(TAG, "🔍 Klasör sorgusu: $query")

            val result = driveService.files()
                .list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val folder = result.files?.firstOrNull()
            if (folder != null) {
                Log.d(TAG, "✅ Klasör bulundu: ${folder.name} (ID: ${folder.id})")
                folder.id
            } else {
                Log.d(TAG, "❌ Klasör bulunamadı: $folderName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Klasör arama hatası: ${e.message}")
            null
        }
    }

    /**
     * Dosya ismindeki zaman damgasını parse eder.
     * Formatlar: yyyyMMdd_HHmmss_SSS (Yeni) veya yyyyMMdd_HHmmss (Eski)
     */
    private fun parseTimestampFromName(fileName: String): Long {
        val regexNew = Regex("(\\d{8}_\\d{6}_\\d{3})")
        val regexOld = Regex("(\\d{8}_\\d{6})")

        val matchResult = regexNew.find(fileName) ?: regexOld.find(fileName)
        val text = matchResult?.groupValues?.get(1) ?: return 0L

        val format = if (text.length == 18) "yyyyMMdd_HHmmss_SSS" else "yyyyMMdd_HHmmss"

        return try {
            SimpleDateFormat(format, Locale.getDefault()).parse(text)?.time ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Timestamp parse hatası: $fileName -> ${e.message}")
            0L
        }
    }

    /**
     * Debug için kontrol edilen klasörlerin listesini döndürür.
     */
    private fun getLastCheckedFolders(dayFormat: SimpleDateFormat, checkedDays: Int): String {
        val cal = Calendar.getInstance()
        val folders = mutableListOf<String>()
        for (i in 0 until checkedDays) {
            folders.add(dayFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return folders.joinToString(", ")
    }

    /**
     * 🔥 YENİ: Drive'daki tüm yedekleri listeler (debug için)
     */
    suspend fun listAllBackups(context: Context, type: BackupFolderType): List<LatestBackup> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = mutableListOf<LatestBackup>()
            try {
                val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext emptyList()
                val rootFolderName = BackupPreferences.getBackupFolderName()
                val rootId = DriveUploadHelper.findOrCreateFolder(driveService, rootFolderName, null)
                val typeId = DriveUploadHelper.findOrCreateFolder(driveService, type.folderName, rootId)

                // Query to find all zip files in the type folder structure
                val query = "mimeType != 'application/vnd.google-apps.folder' and trashed = false and '$typeId' in parents"
                val files = driveService.files()
                    .list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name, modifiedTime, size, parents)")
                    .execute()

                files.files?.forEach { file ->
                    if (file.name?.endsWith(".zip") == true) {
                        result.add(LatestBackup(
                            fileId = file.id,
                            fileName = file.name,
                            dayFolder = "unknown",
                            modifiedTimeMs = file.modifiedTime?.value?.toLong() ?: 0L,
                            sizeBytes = file.size?.toLong() ?: 0L
                        ))
                    }
                }
                Log.d(TAG, "📋 Toplam ${result.size} yedek dosyası bulundu")
                result.sortedByDescending { it.modifiedTimeMs }
            } catch (e: Exception) {
                Log.e(TAG, "listAllBackups hatası: ${e.message}")
                emptyList()
            }
        }
}