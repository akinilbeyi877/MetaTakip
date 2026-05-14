package com.example.metatakip.controllers.poupsms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.metatakip.R
import com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity
import com.example.metatakip.controllers.callphonelast.PhoneUtils.normalize
import dao.MetaTakipCustomerDao
@RequiresApi(Build.VERSION_CODES.Q)
class CallScreeningServiceImpl : CallScreeningService() {

    companion object {
        private const val TAG = "CALL_SCREENING"
        private const val NOTIFICATION_CHANNEL_ID = "call_screening_channel"
        private const val NOTIFICATION_ID = 1001

        // Çağrı durumu takibi
        private var lastNumber: String? = null
        private var callAnswered = false
        private var isRinging = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "📞 CallScreeningService oluşturuluyor")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📞 CallScreeningService başlatılıyor")

        // Foreground servis olarak başlat
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(TAG, "📞 CallScreeningService çağrıldı")

        val number = callDetails.handle?.schemeSpecificPart
        Log.d(TAG, "📞 Gelen numara: $number")

        if (number.isNullOrBlank()) {
            Log.d(TAG, "❌ Numara boş veya null")
            respondToCall(callDetails, createAllowResponse())
            return
        }

        try {
            // Çağrı durumunu al
            val callState = getCallState(callDetails)
            Log.d(TAG, "📞 Çağrı durumu: $callState")

            when (callState) {
                Call.STATE_RINGING -> {
                    lastNumber = number
                    callAnswered = false
                    isRinging = true

                    Log.d(TAG, "🔔 Çağrı çalıyor: $number")
                    showIncomingPopup(number)
                }

                Call.STATE_ACTIVE -> {
                    callAnswered = true
                    isRinging = false
                    Log.d(TAG, "📞 Çağrı cevaplandı: $number")
                }

                Call.STATE_DISCONNECTED -> {
                    Log.d(TAG, "📞 Çağrı bitti: $lastNumber, Cevaplandı mı: $callAnswered")
                    if (!lastNumber.isNullOrBlank() && callAnswered) {
                        handleCallFinished(lastNumber!!)
                    } else if (!lastNumber.isNullOrBlank() && isRinging) {
                        Log.d(TAG, "📞 Cevaplanmayan çağrı: $lastNumber")
                    }
                    reset()
                }

                else -> {
                    Log.d(TAG, "📞 Diğer durum: $callState")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Çağrı işleme hatası", e)
        }

        // Aramaya müdahale etme
        respondToCall(callDetails, createAllowResponse())
    }

    /**
     * Çağrı durumunu al (API uyumlu)
     */
    private fun getCallState(callDetails: Call.Details): Int {
        return try {
            // API 31+ için state property'si var
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callDetails.state
            } else {
                // API 29-30 için durum bilgisini Log'dan takip et
                // Varsayılan olarak RINGING döndür
                Call.STATE_RINGING
            }
        } catch (e: Exception) {
            Log.e(TAG, "Çağrı durumu alınamadı", e)
            Call.STATE_RINGING
        }
    }

    /**
     * Engelleme YOK yanıtı oluştur
     */
    private fun createAllowResponse(): CallScreeningService.CallResponse {
        return try {
            CallScreeningService.CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "CallResponse oluşturulamadı", e)
            CallScreeningService.CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
        }
    }

    /**
     * 🚨 Gelen çağrı popup'ı
     */
    private fun showIncomingPopup(number: String) {
        try {
            Log.d(TAG, "🚨 Popup açılıyor için: $number")

            val normalizedNumber = normalize(number)

            val dao = MetaTakipCustomerDao(this)
            val customer = dao.findCustomerByNormalizedPhone(normalizedNumber)

            val displayName = customer?.adSoyad ?: "Bilinmeyen Arayan"
            Log.d(TAG, "📞 Bulunan müşteri: ${customer?.adSoyad ?: "Bulunamadı"}")

            val intent = Intent(this, CallPopupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("callerName", displayName)
                putExtra("callerNumber", number)
                putExtra("normalizedNumber", normalizedNumber)
                putExtra("programKayitli", customer != null)
                putExtra("source", "SCREENING")
                putExtra("isRingingCall", true)
                putExtra("rehberKayitli", false)
            }

            startActivity(intent)
            Log.d(TAG, "✅ Popup açıldı: $displayName ($number)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Popup açılamadı: ${e.message}", e)
        }
    }
    /**
     * 🧾 Çağrı BİTİNCE açılacak ekran
     */
    private fun handleCallFinished(number: String) {
        try {
            Log.d(TAG, "🧾 Çağrı sonrası ekran açılıyor için: $number")

            val normalizedNumber = normalize(number)

            val dao = MetaTakipCustomerDao(this)
            val customer = dao.findCustomerByNormalizedPhone(normalizedNumber)

            val intent = Intent(this, GenericFormActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (customer != null) {
                    // 🧾 SİPARİŞ EKLE
                    putExtra("targetTable", "siparis")
                    putExtra("customerId", customer.id)
                    putExtra("customerName", customer.adSoyad)
                    putExtra("customerPhone", customer.ceptel ?: "")
                    Log.d(
                        TAG,
                        "✅ Müşteri bulundu, sipariş ekranı açılıyor: ${customer.adSoyad}"
                    )
                } else {
                    // ➕ MÜŞTERİ EKLE
                    putExtra("targetTable", "musteri")
                    putExtra("edit_mode", false)
                    putExtra("customerPhone", number)
                    putExtra("customerName", "Yeni Müşteri")
                    Log.d(
                        TAG,
                        "❌ Müşteri bulunamadı, yeni müşteri ekranı açılıyor"
                    )
                }
            }

            startActivity(intent)
            Log.d(TAG, "🚀 Çağrı sonrası ekran açıldı")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Çağrı sonrası ekran açılamadı: ${e.message}", e)
        }
    }


    private fun reset() {
        lastNumber = null
        callAnswered = false
        isRinging = false
        Log.d(TAG, "🔄 Durum sıfırlandı")
    }

    /**
     * 🔔 Notification Channel oluştur
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Arama Yakalama Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gelen aramaları tespit etmek için çalışıyor"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 🔔 Notification oluştur
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MetaTakip Arama Servisi")
            .setContentText("Gelen aramalar tespit ediliyor...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Kendi icon'unuzu kullanın
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "📞 CallScreeningService sonlandırılıyor")
        reset()
    }
}