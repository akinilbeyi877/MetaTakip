package com.example.metatakip.controllers.callphonelast

import android.content.Context
import android.util.Log
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import com.example.metatakip.feature_data.entityModel.CallRecord
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CallSyncService(private val context: Context) {

    private val callLogsDao = CallLogsDao(context)
    private val TAG = "CallSyncService"

    // =====================================================
    // 🔄 TÜM KAYITLARI SENKRON ET
    // =====================================================
    suspend fun syncAllCallLogs(): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val unsyncedCalls = callLogsDao.getUnsyncedCallLogs()

                if (unsyncedCalls.isEmpty()) {
                    return@withContext SyncResult(0, 0, 0, "✅ Senkron gerektiren kayıt yok")
                }

                Log.d(TAG, "🔄 Senkron başlıyor: ${unsyncedCalls.size} kayıt")

                var successCount = 0
                var failCount = 0

                unsyncedCalls.forEachIndexed { index, callLog ->
                    Log.d(TAG, "📤 Gönderiliyor ${index + 1}/${unsyncedCalls.size} | ID=${callLog.id}")

                    try {
                        val response = sendToServer(callLog)

                        if (response.success) {
                            if (callLogsDao.markAsSynced(callLog.id)) {
                                successCount++
                                Log.d(TAG, "✅ Başarılı: ${callLog.id}")
                            } else {
                                failCount++
                                Log.e(TAG, "❌ DB işaretleme hatası: ${callLog.id}")
                            }
                        } else {
                            failCount++
                            Log.e(TAG, "❌ API Hatası: ${response.message}")
                        }

                    } catch (e: Exception) {
                        failCount++
                        Log.e(TAG, "❌ Senkron hatası ID=${callLog.id}", e)
                    }

                    delay(100)
                }

                SyncResult(
                    total = unsyncedCalls.size,
                    success = successCount,
                    failed = failCount,
                    message = if (failCount == 0)
                        "✅ Tüm kayıtlar senkronlandı"
                    else
                        "⚠️ $successCount başarılı, $failCount başarısız"
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Genel senkron hatası", e)
                SyncResult(0, 0, 0, "❌ ${e.message}")
            }
        }
    }

    // =====================================================
    // 🔹 TEK KAYIT SENKRON
    // =====================================================
    suspend fun syncSingleCallLog(callLogId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val callLog = callLogsDao.getCallLogById(callLogId)
                    ?: return@withContext false

                val response = sendToServer(callLog)
                if (response.success) {
                    callLogsDao.markAsSynced(callLogId)
                    true
                } else {
                    false
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Manuel senkron hatası", e)
                false
            }
        }
    }

    // =====================================================
    // 🌐 SUNUCUYA GÖNDER (EN KRİTİK KISIM)
    // =====================================================
    private suspend fun sendToServer(callLog: CallRecord): ApiResponse {
        Log.e("IP_TEST", "KULLANILAN IP = ${DeviceManager.getCentralIp(context)}")

        // 🔴 MERKEZ CİHAZ KENDİNE GÖNDERMESİN
        if (DeviceManager.isCentralDevice(context)) {
            Log.d(TAG, "ℹ️ Merkez cihaz → senkron atlandı")
            return ApiResponse(true, "Merkez cihaz senkron atlandı")
        }

        // 🔴 CONFIG HER ZAMAN GÜNCEL OKUNUR
        val config = DeviceManager.getDeviceConfig(context)
            ?: return ApiResponse(false, "Cihaz yapılandırılmamış")

        if (config.centralIp.isBlank()) {
            return ApiResponse(false, "Merkez IP tanımlı değil")
        }

        val merkezIp = config.centralIp.trim()
        val endpoint = "http://$merkezIp:8080/api/sync/call-logs"

        Log.d(TAG, "==============================")
        Log.d(TAG, "🌐 MERKEZ IP : $merkezIp")
        Log.d(TAG, "➡️ URL      : $endpoint")
        Log.d(TAG, "➡️ Firma    : ${config.companyName}")
        Log.d(TAG, "➡️ Kullanıcı: ${config.userName}")
        Log.d(TAG, "➡️ Rol      : ${config.userRole}")
        Log.d(TAG, "==============================")

        return try {
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doInput = true
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val payload = mapOf(
                "callLog" to callLog,
                "sourceDevice" to mapOf(
                    "company" to config.companyName,
                    "user" to config.userName,
                    "role" to config.userRole
                )
            )

            val json = Gson().toJson(payload)
            Log.d(TAG, "📦 JSON → $json")

            connection.connect()
            connection.outputStream.use {
                it.write(json.toByteArray(Charsets.UTF_8))
                it.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                connection.errorStream?.bufferedReader()?.readText()
            }

            Log.d(TAG, "⬅️ HTTP $responseCode | $responseBody")

            if (responseCode in 200..299) {
                ApiResponse(true, "Başarılı")
            } else {
                ApiResponse(false, "HTTP $responseCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "🔥 Bağlantı hatası", e)
            ApiResponse(false, e.message)
        }
    }

    // =====================================================
    // 🕒 SENKRON DURUMU
    // =====================================================
    fun getSyncStatus(): SyncStatus {
        val all = callLogsDao.getAllCallLogs()
        val unsynced = callLogsDao.getUnsyncedCallLogs()

        return SyncStatus(
            totalCalls = all.size,
            syncedCalls = all.size - unsynced.size,
            unsyncedCalls = unsynced.size,
            lastSyncTime = getLastSyncTime()
        )
    }

    private fun getLastSyncTime(): String {
        val prefs = context.getSharedPreferences("call_sync", Context.MODE_PRIVATE)
        return prefs.getString("last_sync_time", "Hiç senkron edilmedi")!!
    }

    // =====================================================
    // 📦 DATA MODELLER
    // =====================================================
    data class SyncResult(
        val total: Int,
        val success: Int,
        val failed: Int,
        val message: String
    )

    data class ApiResponse(
        val success: Boolean,
        val message: String? = null
    )

    data class SyncStatus(
        val totalCalls: Int,
        val syncedCalls: Int,
        val unsyncedCalls: Int,
        val lastSyncTime: String
    ) {
        val syncPercentage: Int
            get() = if (totalCalls > 0) (syncedCalls * 100) / totalCalls else 100
    }



}
