package com.example.metatakip.feature_backup.drive

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.TimeUtils
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🚶 Drive Queue Manager
 * Birden fazla cihaz sıraya girdiğinde 'kimin sırası' olduğunu yönetir.
 * Drive üzerinde 'system/queue.json' dosyasını ortak bekleme listesi olarak kullanır.
 */
object DriveQueueManager {

    private const val TAG = "DriveQueueManager"
    private const val SYSTEM_FOLDER_NAME = "system"
    private const val QUEUE_FILE_NAME = "queue.json"
    private const val STALE_QUEUE_MS = 5 * 60 * 1000L // 5 dakika işlem yapmayan cihaz sıradan atılır

    data class QueueItem(
        val deviceId: String,
        val deviceName: String,
        val action: String,
        val joinedAt: Long
    )

    data class QueueState(
        val items: List<QueueItem>
    ) {
        fun currentNext(): QueueItem? = items.firstOrNull()
        fun positionOf(deviceId: String): Int = items.indexOfFirst { it.deviceId == deviceId }.let { if (it >= 0) it + 1 else -1 }

        fun displayText(myDeviceId: String): String {
            val position = positionOf(myDeviceId)
            if (position == -1) return "Sırada değilsiniz"
            if (position == 1) return "Sıra sizde, işlem başlıyor..."
            return "Sıranız bekleniyor: $position. sıradasınız"
        }
    }

    /**
     * 📥 Cihazı sıraya ekler.
     */
    suspend fun enqueue(context: Context, action: String): QueueState = withContext(Dispatchers.IO) {
        mutateQueue(context) { items ->
            val now = System.currentTimeMillis()
            val id = BackupPreferences.getOrCreateDeviceId()
            val name = BackupPreferences.getDeviceName()

            // Bayat (stale) kayıtları ve mevcut cihazın eski kaydını temizle
            val cleaned = items.filter { now - it.joinedAt <= STALE_QUEUE_MS && it.deviceId != id }.toMutableList()

            // Kendini sıraya ekle
            cleaned.add(QueueItem(id, name, action, now))
            cleaned
        }
    }

    /**
     * 📤 İşlemi biten cihazı sıradan çıkarır.
     */
    suspend fun removeCurrentDevice(context: Context): QueueState = withContext(Dispatchers.IO) {
        mutateQueue(context) { items ->
            val id = BackupPreferences.getOrCreateDeviceId()
            items.filterNot { it.deviceId == id }
        }
    }

    /**
     * 🔍 Mevcut kuyruk durumunu Drive'dan okur.
     */
    suspend fun getQueueState(context: Context): QueueState? = withContext(Dispatchers.IO) {
        try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return@withContext null
            val rootId = ensureRootFolderId(context) ?: return@withContext null
            val systemId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)
            readQueueState(driveService, systemId)
        } catch (e: Exception) {
            Log.e(TAG, "getQueueState Hatası: ${e.message}")
            null
        }
    }

    // --- ÖZEL YARDIMCI METOTLAR ---

    private fun mutateQueue(context: Context, transform: (List<QueueItem>) -> List<QueueItem>): QueueState {
        return try {
            val driveService = DriveBackupManager.getDriveService(context) ?: return QueueState(emptyList())
            val rootId = ensureRootFolderId(context) ?: return QueueState(emptyList())
            val systemId = DriveUploadHelper.findOrCreateFolder(driveService, SYSTEM_FOLDER_NAME, rootId)

            val current = readQueueState(driveService, systemId)
            val updatedList = transform(current.items)
            val updatedState = QueueState(updatedList)

            writeQueueState(driveService, systemId, updatedState)
            updatedState
        } catch (e: Exception) {
            Log.e(TAG, "mutateQueue Hatası: ${e.message}")
            QueueState(emptyList())
        }
    }

    private fun readQueueState(driveService: com.google.api.services.drive.Drive, systemId: String): QueueState {
        return try {
            val existing = findFile(driveService, QUEUE_FILE_NAME, systemId)
            if (existing == null) return QueueState(emptyList())

            val text = driveService.files().get(existing.id).executeMediaAsInputStream().use {
                it.readBytes().toString(Charsets.UTF_8)
            }

            val json = JSONObject(text)
            val arr = json.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<QueueItem>()

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                items.add(QueueItem(
                    o.optString("deviceId"),
                    o.optString("deviceName"),
                    o.optString("action"),
                    o.optLong("joinedAt")
                ))
            }
            QueueState(items)
        } catch (e: Exception) {
            QueueState(emptyList())
        }
    }

    private fun writeQueueState(driveService: com.google.api.services.drive.Drive, systemId: String, state: QueueState) {
        try {
            val arr = JSONArray()
            state.items.forEach {
                arr.put(JSONObject().apply {
                    put("deviceId", it.deviceId)
                    put("deviceName", it.deviceName)
                    put("action", it.action)
                    put("joinedAt", it.joinedAt)
                })
            }

            val payload = JSONObject().put("items", arr).toString()
            val media = ByteArrayContent("application/json", payload.toByteArray(Charsets.UTF_8))
            val existing = findFile(driveService, QUEUE_FILE_NAME, systemId)

            if (existing == null) {
                val meta = File().apply {
                    name = QUEUE_FILE_NAME
                    parents = listOf(systemId)
                    mimeType = "application/json"
                }
                driveService.files().create(meta, media).execute()
            } else {
                driveService.files().update(existing.id, null, media).execute()
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeQueueState Hatası: ${e.message}")
        }
    }

    private fun findFile(driveService: com.google.api.services.drive.Drive, fileName: String, parentId: String): File? {
        val query = "trashed = false and name = '$fileName' and '$parentId' in parents"
        return driveService.files().list().setQ(query).setSpaces("drive").setFields("files(id,name)").execute().files?.firstOrNull()
    }

    private fun ensureRootFolderId(context: Context): String? {
        val driveService = DriveBackupManager.getDriveService(context) ?: return null
        return DriveUploadHelper.findOrCreateFolder(driveService, BackupPreferences.getBackupFolderName(), null)
    }
}