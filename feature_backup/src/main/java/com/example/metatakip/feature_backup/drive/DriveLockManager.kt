package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * 🚦 Drive Lock Manager
 * Birden fazla cihazın aynı anda yedekleme/yükleme yapmasını engeller.
 * Drive üzerinde 'system/lock.json' dosyasını bayrak olarak kullanır.
 */
object DriveLockManager {

    private const val TAG = "DriveLockManager"
    private const val SYSTEM_FOLDER_NAME = "system"
    private const val LOCK_FILE_NAME = "lock.json"

    private const val DEFAULT_WAIT_MS = 45_000L // 30'dan 45'e çıkarıldı (yavaş bağlantılar için)
    private const val POLL_INTERVAL_MS = 2_000L // Drive API kotasını korumak için 2 saniyeye çıkarıldı
    private const val STALE_LOCK_MS = 180_000L  // 3 dakika boyunca yanıt vermeyen kilit 'bayat' sayılır

    data class LockState(
        val status: String = "free",
        val ownerId: String = "",
        val ownerName: String = "",
        val timestamp: Long = 0L,
        val action: String? = null,
        val fileId: String? = null
    ) {
        val isBusy: Boolean get() = status.equals("busy", ignoreCase = true)
        val isFree: Boolean get() = !isBusy
    }

    data class AcquireResult(
        val acquired: Boolean,
        val reason: String,
        val state: LockState? = null
    )

    /**
     * 🔐 Kilidi almaya çalışır. Başka cihaz işlem yapıyorsa [waitTimeoutMs] kadar bekler.
     */
    suspend fun acquireLock(
        context: Context,
        waitTimeoutMs: Long = DEFAULT_WAIT_MS,
        action: String = "backup",
        onStatus: (suspend (String) -> Unit)? = null
    ): AcquireResult = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context)
                ?: return@withContext AcquireResult(false, "Drive bağlantısı kurulamadı.")

            val ownerId = BackupPreferences.getOrCreateDeviceId()
            val ownerName = BackupPreferences.getDeviceName()
            val startedAt = System.currentTimeMillis()

            var result: AcquireResult? = null

            while (result == null) {
                val rootId = ensureRootFolderId(context) ?: throw Exception("Kök dizin hatası")
                val systemFolderId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)

                // Mevcut kilidi oku veya yoksa 'free' olarak oluştur
                val current = getOrCreateLockState(driveService, systemFolderId)

                val now = System.currentTimeMillis()
                // Bayat kilit kontrolü: Cihaz kilitledi ama interneti koptuysa 3 dk sonra kilidi kırabiliriz.
                val isStale = current.isBusy && (now - current.timestamp > STALE_LOCK_MS)

                result = when {
                    // Kilit boşsa, bayatsa veya zaten bizdeyse kilitle!
                    current.isFree || isStale || current.ownerId == ownerId -> {
                        val payload = lockJson("busy", ownerId, ownerName, now, action)
                        val fileId = upsertLockFile(driveService, systemFolderId, current.fileId, payload)

                        Log.d(TAG, "🔐 Kilit Alındı: $ownerName ($action)")
                        AcquireResult(true, if (isStale) "Eski kilit kırıldı" else "Kilit alındı",
                            current.copy(status = "busy", ownerId = ownerId, ownerName = ownerName, timestamp = now, fileId = fileId))
                    }

                    // Bekleme süresi dolduysa pes et
                    now - startedAt >= waitTimeoutMs -> {
                        Log.w(TAG, "⚠️ Kilit alınamadı, zaman aşımı.")
                        AcquireResult(false, "Sistem şu an meşgul: ${current.ownerName}", current)
                    }

                    // Diğer cihazın bitirmesini bekle
                    else -> {
                        onStatus?.invoke("Bekleniyor: ${current.ownerName} işlem yapıyor...")
                        delay(POLL_INTERVAL_MS)
                        null // Döngüyü devam ettir
                    }
                }
            }
            result!!
        } catch (e: Exception) {
            Log.e(TAG, "💥 acquireLock Hatası: ${e.message}")
            AcquireResult(false, "Bağlantı hatası: ${e.localizedMessage}")
        }
    }

    /**
     * 🔓 Kilidi serbest bırakır.
     */
    suspend fun releaseLock(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext false
            val rootId = ensureRootFolderId(context) ?: return@withContext false
            val systemFolderId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)
            val current = getOrCreateLockState(driveService, systemFolderId)

            val ownerId = BackupPreferences.getOrCreateDeviceId()

            // Kilit zaten yoksa veya kilit sahibi biz değilsek (başka cihaz kırmışsa) işlem yapma
            if (current.fileId == null || (current.ownerId.isNotBlank() && current.ownerId != ownerId)) {
                return@withContext true
            }

            val payload = lockJson("free", "", "", System.currentTimeMillis(), null)
            upsertLockFile(driveService, systemFolderId, current.fileId, payload)

            Log.d(TAG, "🔓 Kilit Serbest Bırakıldı.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "💥 releaseLock Hatası: ${e.message}")
            false
        }
    }

    // --- YARDIMCI METOTLAR (Modüler ve Hata Korumalı) ---

    private fun getOrCreateLockState(driveService: Drive, systemFolderId: String): LockState {
        val existing = findLockFile(driveService, systemFolderId) ?: return createNewLockFile(driveService, systemFolderId)

        return try {
            val jsonText = driveService.files().get(existing.id).executeMediaAsInputStream()
                .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            parseLockState(jsonText, existing.id)
        } catch (e: Exception) {
            LockState(fileId = existing.id) // Okunamazsa boş kilit dön
        }
    }

    private fun createNewLockFile(driveService: Drive, systemFolderId: String): LockState {
        val payload = lockJson("free", "", "", 0L, null)
        val fileId = upsertLockFile(driveService, systemFolderId, null, payload)
        return LockState(status = "free", fileId = fileId)
    }

    private fun parseLockState(jsonText: String, fileId: String?): LockState {
        return try {
            val obj = JSONObject(jsonText)
            LockState(
                status = obj.optString("status", "free"),
                ownerId = obj.optString("ownerId", ""),
                ownerName = obj.optString("ownerName", ""),
                timestamp = obj.optLong("timestamp", 0L),
                action = if (obj.isNull("action")) null else obj.optString("action", null),
                fileId = fileId
            )
        } catch (e: Exception) {
            LockState(fileId = fileId)
        }
    }

    private fun lockJson(status: String, ownerId: String, ownerName: String, timestamp: Long, action: String?): String {
        return JSONObject().apply {
            put("status", status)
            put("ownerId", ownerId)
            put("ownerName", ownerName)
            put("timestamp", timestamp)
            put("action", action ?: JSONObject.NULL)
        }.toString()
    }

    private fun findLockFile(driveService: Drive, systemFolderId: String): File? {
        val query = "trashed = false and name = '$LOCK_FILE_NAME' and '$systemFolderId' in parents"
        return driveService.files().list().setQ(query).setSpaces("drive").setFields("files(id,name)").execute().files?.firstOrNull()
    }

    private fun upsertLockFile(driveService: Drive, systemFolderId: String, existingFileId: String?, payload: String): String {
        val media = ByteArrayContent.fromString("application/json", payload)
        return if (!existingFileId.isNullOrBlank()) {
            driveService.files().update(existingFileId, null, media).execute().id
            existingFileId
        } else {
            val fileMeta = File().apply {
                name = LOCK_FILE_NAME
                parents = listOf(systemFolderId)
                mimeType = "application/json"
            }
            driveService.files().create(fileMeta, media).setFields("id").execute().id
        }
    }

    /**
     * 🔍 Mevcut kilit durumunu sadece okur (Değiştirmez).
     * Activity ekranında durumu göstermek için kullanılır.
     */
    suspend fun getLockState(context: Context): LockState? = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext null
            val rootId = ensureRootFolderId(context) ?: return@withContext null
            val systemFolderId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)

            val file = findLockFile(driveService, systemFolderId) ?: return@withContext LockState(status = "free")

            val jsonText = driveService.files().get(file.id).executeMediaAsInputStream()
                .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            parseLockState(jsonText, file.id)
        } catch (e: Exception) {
            Log.e(TAG, "getLockState okunurken hata: ${e.message}")
            null
        }
    }

    private fun ensureRootFolderId(context: Context): String? {
        val driveService = DriveBackupManager.getDriveService(context) ?: return null
        return DriveUploadHelper.findOrCreateFolder(driveService, BackupPreferences.getBackupFolderName(), null)
    }
}