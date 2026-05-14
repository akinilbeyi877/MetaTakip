package com.example.metatakip.feature_backup.worker

import android.util.Log
import com.example.metatakip.feature_backup.util.TimeUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AutoBackupLogStore {
    private const val TAG = "AutoBackupLogStore"
    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 200)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    fun addLog(message: String) {
        val time = TimeUtils.formatTime(System.currentTimeMillis()).substringAfter(" ")
        val formattedMessage = "[$time] $message"
        _logs.tryEmit(formattedMessage)

        if (message.contains("❌") || message.contains("⚠️")) Log.e(TAG, message)
        else Log.d(TAG, message)
    }

    // --- Yeni Yardımcı Metodlar (Daha Anlaşılır Mesajlar İçin) ---

    fun addUploadLog(deviceName: String, fileCount: Int) {
        addLog("⬆️ GÖNDERİM: $deviceName cihazından $fileCount yeni işlem buluta aktarıldı.")
    }

    fun addDownloadLog(remoteDevice: String, fileName: String) {
        addLog("⬇️ ALIM: $remoteDevice cihazının güncellemeleri indirildi. ($fileName)")
    }

    fun addMergeLog(insertCount: Int, updateCount: Int) {
        addLog("♻️ BİRLEŞTİRME: $insertCount yeni kayıt eklendi, $updateCount kayıt güncellendi.")
    }
}