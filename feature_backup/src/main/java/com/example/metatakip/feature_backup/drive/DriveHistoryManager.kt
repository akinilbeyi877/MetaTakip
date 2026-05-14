package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.TimeUtils
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Drive üzerindeki 'system/history.json' dosyasını yönetir.
 * Hibrit yapıda cihazlar arası son durum bilgisini senkronize tutar.
 */
object DriveHistoryManager {

    private const val TAG = "DriveHistoryManager"
    private const val SYSTEM_FOLDER_NAME = "system"
    private const val HISTORY_FILE_NAME = "history.json"

    data class HistoryState(
        val lastBackupDeviceId: String = "",
        val lastBackupDeviceName: String = "",
        val lastBackupType: String = "",
        val lastBackupTime: Long = 0L,
        val lastBackupFileName: String = ""
    ) {
        fun displayText(): String {
            if (lastBackupTime <= 0L) return "Henüz bulutta bir yedek kaydı yok"
            return "Son Senkronizasyon: $lastBackupDeviceName • $lastBackupType • ${TimeUtils.formatTime(lastBackupTime)}"
        }
    }

    /**
     * ✍️ Yapılan son yedekleme bilgisini Drive'a mühürler.
     */
    suspend fun recordBackup(context: Context, type: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext false

            // 1. Klasör Yapısını Doğrula (Root -> System)
            val rootId = DriveUploadHelper.findOrCreateFolder(driveService, BackupPreferences.getBackupFolderName(), null)
            val systemId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)

            // 2. Mevcut History dosyasını bul
            val existing = findFile(driveService, HISTORY_FILE_NAME, systemId)

            // 3. Hibrit Payload Hazırla
            val payload = JSONObject().apply {
                put("lastBackupDeviceId", BackupPreferences.getOrCreateDeviceId())
                put("lastBackupDeviceName", BackupPreferences.getDeviceName())
                put("lastBackupType", type)
                put("lastBackupTime", System.currentTimeMillis())
                put("lastBackupFileName", fileName)
            }.toString()

            val media = ByteArrayContent("application/json", payload.toByteArray(Charsets.UTF_8))

            if (existing == null) {
                // Yeni oluştur
                val meta = File().apply {
                    name = HISTORY_FILE_NAME
                    parents = listOf(systemId)
                    mimeType = "application/json"
                }
                driveService.files().create(meta, media).setFields("id").execute()
                Log.d(TAG, "✅ Yeni history.json oluşturuldu.")
            } else {
                // Mevcut olanı güncelle
                driveService.files().update(existing.id, null, media).setFields("id").execute()
                Log.d(TAG, "♻️ history.json güncellendi.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ recordBackup Hatası: ${e.message}")
            false
        }
    }

    /**
     * 📖 Drive'dan en son kimin yedek aldığını okur.
     */
    suspend fun getHistory(context: Context): HistoryState? = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext null

            val rootId = DriveUploadHelper.findOrCreateFolder(driveService, BackupPreferences.getBackupFolderName(), null)
            val systemId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)

            val existing = findFile(driveService, HISTORY_FILE_NAME, systemId) ?: return@withContext HistoryState()

            val text = driveService.files().get(existing.id).executeMediaAsInputStream().use {
                it.readBytes().toString(Charsets.UTF_8)
            }

            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }

            HistoryState(
                lastBackupDeviceId = json.optString("lastBackupDeviceId", ""),
                lastBackupDeviceName = json.optString("lastBackupDeviceName", ""),
                lastBackupType = json.optString("lastBackupType", ""),
                lastBackupTime = json.optLong("lastBackupTime", 0L),
                lastBackupFileName = json.optString("lastBackupFileName", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ getHistory Hatası: ${e.message}")
            null
        }
    }

    /**
     * 🔍 Drive içerisinde dosya ararken daha spesifik ve güvenli sorgu kullanır.
     */
    private fun findFile(
        driveService: com.google.api.services.drive.Drive,
        fileName: String,
        parentId: String
    ): File? {
        return try {
            val escaped = fileName.replace("'", "\\'")
            val query = "trashed = false and name = '$escaped' and '$parentId' in parents"

            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            result.files?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}