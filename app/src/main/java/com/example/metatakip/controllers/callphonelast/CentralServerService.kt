package com.example.metatakip.controllers.callphonelast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.metatakip.R
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import com.example.metatakip.feature_backup.sync.UiLogManager
import com.example.metatakip.feature_data.entityModel.CallRecord
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.IOException

class CentralServerService : Service() {

    companion object {
        private const val TAG = "CentralServer"
        private const val PORT = 8080
        private const val CHANNEL_ID = "central_server_channel"
        private const val NOTIFICATION_ID = 9001
    }

    private var httpServer: CentralHttpServer? = null
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()

        UiLogManager.log("🚀 Merkez servis oluşturuluyor")
        Log.d(TAG, "🚀 Merkez servis oluşturuluyor")

        startForegroundServiceInternal()

        try {
            httpServer = CentralHttpServer(PORT).also {
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }

            UiLogManager.log("🌐 Merkez sunucu çalışıyor: http://0.0.0.0:$PORT")
            Log.d(TAG, "✅ Merkez sunucu çalışıyor → http://0.0.0.0:$PORT")
        } catch (e: IOException) {
            UiLogManager.log("❌ Sunucu başlatılamadı: ${e.message}")
            Log.e(TAG, "❌ Sunucu başlatılamadı", e)
            stopSelf()
        } catch (e: Exception) {
            UiLogManager.log("❌ Beklenmeyen sunucu hatası: ${e.message}")
            Log.e(TAG, "❌ Beklenmeyen sunucu hatası", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UiLogManager.log("▶️ Merkez servis aktif")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        UiLogManager.log("🛑 Merkez servis kapanıyor")

        try {
            httpServer?.stop()
            UiLogManager.log("🛑 Merkez sunucu durduruldu")
        } catch (e: Exception) {
            UiLogManager.log("❌ Sunucu durdurulamadı: ${e.message}")
            Log.e(TAG, "❌ Sunucu durdurulamadı", e)
        } finally {
            httpServer = null
        }

        Log.d(TAG, "🛑 Merkez sunucu durduruldu")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceInternal() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Merkez Sunucu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Saha cihazlarından gelen çağrılar için merkez sunucu servisi"
            }
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📡 Merkez Sunucu Aktif")
            .setContentText("Saha cihazlarından çağrılar alınıyor")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    inner class CentralHttpServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            UiLogManager.log("🌐 İstek geldi: ${session.method} ${session.uri}")

            return when (session.uri) {
                "/api/sync/call-logs" -> handleSyncCallLogs(session)
                "/api/status" -> {
                    UiLogManager.log("✅ Status endpoint çağrıldı")
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/plain; charset=utf-8",
                        "✅ Merkez çalışıyor"
                    )
                }
                else -> {
                    UiLogManager.log("⚠️ Bilinmeyen endpoint: ${session.uri}")
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain; charset=utf-8",
                        "404"
                    )
                }
            }
        }

        private fun handleSyncCallLogs(session: IHTTPSession): Response {
            return try {
                val json = readRequestBody(session).trim()

                if (json.isBlank()) {
                    UiLogManager.log("❌ Boş JSON geldi")
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json; charset=utf-8",
                        """{"success":false,"error":"Boş istek gövdesi"}"""
                    )
                }

                Log.d(TAG, "📥 Gelen JSON: $json")
                UiLogManager.log("📥 Yeni çağrı senkron verisi alındı")

                val data = gson.fromJson(json, Map::class.java)
                    ?: throw IllegalArgumentException("JSON çözümlenemedi")

                val callLogData = data["callLog"] as? Map<*, *>
                    ?: throw IllegalArgumentException("callLog alanı eksik")

                val sourceDevice = data["sourceDevice"] as? Map<*, *>
                    ?: emptyMap<String, Any>()

                val cagriZamani = (callLogData["cagriZamani"] as? Number)?.toLong()
                    ?: throw IllegalArgumentException("cagriZamani eksik veya geçersiz")

                val callRecord = CallRecord(
                    musteriTelefonu = callLogData["musteriTelefonu"]?.toString()
                        ?: throw IllegalArgumentException("musteriTelefonu eksik"),
                    musteriAdi = callLogData["musteriAdi"]?.toString(),
                    arananFirmaAdi = callLogData["arananFirmaAdi"]?.toString()
                        ?: throw IllegalArgumentException("arananFirmaAdi eksik"),
                    arananHatAdi = callLogData["arananHatAdi"]?.toString()
                        ?: throw IllegalArgumentException("arananHatAdi eksik"),
                    arananTelefon = callLogData["arananTelefon"]?.toString()
                        ?: throw IllegalArgumentException("arananTelefon eksik"),
                    cihazAdi = callLogData["cihazAdi"]?.toString()
                        ?: throw IllegalArgumentException("cihazAdi eksik"),
                    cihazFirmaAdi = callLogData["cihazFirmaAdi"]?.toString()
                        ?: throw IllegalArgumentException("cihazFirmaAdi eksik"),
                    cihazKullaniciAdi = callLogData["cihazKullaniciAdi"]?.toString()
                        ?: throw IllegalArgumentException("cihazKullaniciAdi eksik"),
                    cihazRolu = callLogData["cihazRolu"]?.toString()
                        ?: throw IllegalArgumentException("cihazRolu eksik"),
                    cihazMerkezMi = true,
                    simYuvasi = callLogData["simYuvasi"]?.toString()
                        ?: throw IllegalArgumentException("simYuvasi eksik"),
                    cagriTuru = callLogData["cagriTuru"]?.toString()
                        ?: throw IllegalArgumentException("cagriTuru eksik"),
                    cagriZamani = cagriZamani,
                    merkezeIletildiMi = true,
                    merkezeIletilmeZamani = System.currentTimeMillis() / 1000,
                    merkezHataMesaji = buildSourceMessage(sourceDevice)
                )

                val dao = CallLogsDao(this@CentralServerService)
                val id = dao.addCallLog(callRecord)

                UiLogManager.log("✅ Çağrı kaydı alındı. ID=$id")
                Log.d(TAG, "✅ Kayıt alındı ID=$id")

                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=utf-8",
                    gson.toJson(
                        mapOf(
                            "success" to true,
                            "id" to id
                        )
                    )
                )
            } catch (e: IllegalArgumentException) {
                UiLogManager.log("❌ Geçersiz veri: ${e.message}")
                Log.e(TAG, "❌ Geçersiz veri", e)

                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json; charset=utf-8",
                    gson.toJson(
                        mapOf(
                            "success" to false,
                            "error" to (e.message ?: "Geçersiz veri")
                        )
                    )
                )
            } catch (e: Exception) {
                UiLogManager.log("❌ Sync hatası: ${e.message}")
                Log.e(TAG, "❌ Sync hatası", e)

                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json; charset=utf-8",
                    gson.toJson(
                        mapOf(
                            "success" to false,
                            "error" to (e.message ?: "Bilinmeyen hata")
                        )
                    )
                )
            }
        }

        private fun readRequestBody(session: IHTTPSession): String {
            val contentLength = session.headers["content-length"]?.toIntOrNull()

            return if (contentLength != null && contentLength > 0) {
                val buffer = ByteArray(contentLength)
                var totalRead = 0

                while (totalRead < contentLength) {
                    val read = session.inputStream.read(buffer, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }

                String(buffer, 0, totalRead, Charsets.UTF_8)
            } else {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)

                while (true) {
                    val read = session.inputStream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)

                    if (read < buffer.size) break
                }

                output.toString(Charsets.UTF_8.name())
            }
        }

        private fun buildSourceMessage(sourceDevice: Map<*, *>): String {
            val company = sourceDevice["company"]?.toString()?.ifBlank { "-" } ?: "-"
            val user = sourceDevice["user"]?.toString()?.ifBlank { "-" } ?: "-"
            return "Kaynak: $company - $user"
        }
    }
}