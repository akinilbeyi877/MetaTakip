package com.example.metatakip.controllers.poupsms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.metatakip.R
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import dao.MetaTakipCustomerDao
import com.example.metatakip.controllers.callphonelast.DeviceManager

import com.example.metatakip.feature_data.entityModel.CallRecord

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CALL_RECEIVER"
        private const val CHANNEL_ID = "missed_call_channel"
        private const val NOTIFICATION_ID = 1001

        private var lastNumber: String? = null
        private var wasRinging = false
        private var callAnswered = false

        // REHBER GÜNCELLEME BROADCAST İŞLEMCİSİ
        private var contactUpdateListener: ((String) -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {

        // 🔄 REHBER GÜNCELLEME BROADCAST'İNİ DİNLE
        if (intent.action == "com.example.metatakip.CONTACT_UPDATED") {
            Log.d(TAG, "🔔 Rehber güncelleme sinyali alındı")
            // Gerekiyorsa cache temizle veya yenile
            lastNumber = null
            wasRinging = false
            callAnswered = false
            return
        }

        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        when (state) {
            // 📞 ÇALIYOR
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val number =
                    intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                if (!number.isNullOrBlank()) {
                    lastNumber = number
                    wasRinging = true
                    callAnswered = false

                    Log.d(TAG, "📞 Gelen çağrı: $number")

                    // 🔥 ÖNEMLİ: Çağrıyı hemen veritabanına kaydet
                    saveIncomingCall(context, number, true)

                    // Popup'ı aç
                    openPopup(context, number, isRinging = true)
                }
            }

            // ☎️ CEVAPLANDI
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                callAnswered = true
                wasRinging = false
                Log.d(TAG, "☎️ Çağrı cevaplandı")

                // Cevaplanan çağrıyı işaretle
                lastNumber?.let {
                    markCallAsAnswered(context, it)
                }
            }

            // 📴 BİTTİ
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (wasRinging && !callAnswered && !lastNumber.isNullOrBlank()) {
                    Log.d(TAG, "📵 Cevapsız çağrı: $lastNumber")

                    // 🔥 CEVAPSIZ ÇAĞRI KAYDI
                    saveIncomingCall(context, lastNumber!!, false)

                    showMissedCallNotification(context, lastNumber!!)
                }

                reset()
            }
        }
    }

    private fun reset() {
        lastNumber = null
        wasRinging = false
        callAnswered = false
    }

    // ===============================
    // 🔥 ÇAĞRI KAYDETME FONKSİYONLARI
    // ===============================

    private fun saveIncomingCall(
        context: Context,
        rawNumber: String,
        isRinging: Boolean = true
    ) {
        try {
            // 📞 Numara normalize edilir (boşluk, -, () temizlenmiş)
            val normalizedNumber = normalize(rawNumber)

            val callLogsDao = CallLogsDao(context)
            val customerDao = MetaTakipCustomerDao(context)

            // ✅ YENİ ve DOĞRU METOT
            val customer = customerDao.findCustomerByNormalizedPhone(normalizedNumber)

            // 📒 Rehber adı (varsa)
            val contactName = getContactName(context, normalizedNumber)

            // 👤 Gösterilecek isim öncelik sırası:
            // 1️⃣ Programdaki müşteri
            // 2️⃣ Rehberdeki kişi
            // 3️⃣ Ham numara
            val displayName = when {
                customer != null && !customer.adSoyad.isNullOrBlank() ->
                    customer.adSoyad
                !contactName.isNullOrBlank() ->
                    contactName
                else ->
                    rawNumber
            }

            // 🏢 Firma adı
            val firmName = when {
                customer != null && !customer.firmaAdi.isNullOrBlank() ->
                    customer.firmaAdi!!
                else ->
                    "Arayan Müşteri"
            }

            // 📱 Cihaz konfigürasyonu
            val deviceConfig = DeviceManager.getDeviceConfig(context)

            // 🧾 Çağrı kaydı modeli
            val callRecord = CallRecord(
                musteriTelefonu = rawNumber,
                musteriAdi = displayName,
                arananFirmaAdi = firmName,
                arananHatAdi = "Ana Hat",
                arananTelefon = rawNumber,

                cihazAdi = Build.MODEL,
                cihazFirmaAdi = deviceConfig?.companyName ?: "Yapılandırılmamış",
                cihazKullaniciAdi = deviceConfig?.userName ?: "Bilinmiyor",
                cihazRolu = deviceConfig?.userRole ?: "SAHA",
                cihazMerkezMi = deviceConfig?.isCentralDevice ?: false,

                simYuvasi = "SIM1",
                cagriTuru = if (isRinging) "GELEN" else "CEVAPSIZ",
                cagriZamani = System.currentTimeMillis() / 1000,
                createdAt = System.currentTimeMillis() / 1000
            )

            // 💾 Veritabanına kaydet
            val id = callLogsDao.addCallLog(callRecord)

            if (id > 0) {
                Log.d(
                    TAG,
                    "✅ Çağrı kaydedildi | ID=$id | Tür=${if (isRinging) "GELEN" else "CEVAPSIZ"} | $displayName"
                )
            } else {
                Log.e(TAG, "❌ Çağrı kaydedilemedi: $rawNumber")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Çağrı kaydetme hatası", e)
        }
    }


    private fun markCallAsAnswered(context: Context, rawNumber: String) {
        try {
            // Son kaydedilen çağrıyı "GIDEN" olarak güncelleyebilirsiniz
            // Veya başka bir işlem yapabilirsiniz
            Log.d(TAG, "📞 Cevaplanan çağrı: $rawNumber")

            // İsterseniz burada son kaydı güncelleyebilirsiniz
            // val callLogsDao = CallLogsDao(context)
            // val lastCall = callLogsDao.getLastCallByNumber(rawNumber)
            // lastCall?.let {
            //     // Güncelleme işlemi
            // }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Cevaplanan çağrı işaretleme hatası", e)
        }
    }

    // ===============================
    // 📞 POPUP AÇ
    // ===============================
    private fun openPopup(
        context: Context,
        rawNumber: String,
        isRinging: Boolean
    ) {
        try {
            // 📞 Numara normalize edilir
            val normalizedNumber = normalize(rawNumber)

            val dao = MetaTakipCustomerDao(context)

            // ✅ DOĞRU METOT (yeni mimari)
            val customer = dao.findCustomerByNormalizedPhone(normalizedNumber)

            // 📒 Rehber adı (varsa)
            val contactName = getContactName(context, normalizedNumber)

            // ⭐ Gösterilecek isim öncelik sırası:
            // 1️⃣ Programdaki müşteri
            // 2️⃣ Rehberdeki isim
            // 3️⃣ Ham numara
            val displayName = when {
                customer != null && !customer.adSoyad.isNullOrBlank() ->
                    customer.adSoyad
                !contactName.isNullOrBlank() ->
                    contactName
                else ->
                    rawNumber
            }

            val intent = Intent(context, CallPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

                // 📌 Temel bilgiler
                putExtra("callerName", displayName)
                putExtra("callerNumber", rawNumber)
                putExtra("normalizedNumber", normalizedNumber)

                // ⭐ Orijinal rehber ismi (popup içinde gerekirse)
                if (!contactName.isNullOrBlank()) {
                    putExtra("originalContactName", contactName)
                }

                // 🧠 Durum bilgileri
                putExtra("programKayitli", customer != null)
                putExtra("rehberKayitli", contactName != null)

                // 📞 Çağrı türü
                putExtra("isRingingCall", isRinging)
                putExtra("isMissedCall", !isRinging)

                // 🔔 Bildirimden mi geldi
                putExtra("fromNotification", false)
            }

            context.startActivity(intent)

            Log.d(
                TAG,
                "✅ Popup açıldı | $displayName | Programda=${customer != null} | Rehberde=${contactName != null}"
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Popup açılamadı", e)
        }
    }


    // ===============================
    // 🔔 TEK CEVAPSIZ ÇAĞRI BİLDİRİMİ
    // ===============================
    private fun showMissedCallNotification(
        context: Context,
        rawNumber: String
    ) {
        try {
            createChannel(context)

            // 📞 Numara normalize edilir
            val normalizedNumber = normalize(rawNumber)

            val dao = MetaTakipCustomerDao(context)

            // ✅ DOĞRU METOT
            val customer = dao.findCustomerByNormalizedPhone(normalizedNumber)

            // 📒 Rehber ismi (varsa)
            val contactName = getContactName(context, normalizedNumber)

            // ⭐ Gösterilecek isim önceliği:
            // 1️⃣ Programdaki müşteri
            // 2️⃣ Rehber ismi
            // 3️⃣ Numara
            val displayName = when {
                customer != null && !customer.adSoyad.isNullOrBlank() ->
                    customer.adSoyad
                !contactName.isNullOrBlank() ->
                    contactName
                else ->
                    rawNumber
            }

            val intent = Intent(context, CallPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra("callerName", displayName)
                putExtra("callerNumber", rawNumber)
                putExtra("normalizedNumber", normalizedNumber)

                // ⭐ Rehberdeki orijinal isim
                if (!contactName.isNullOrBlank()) {
                    putExtra("originalContactName", contactName)
                }

                putExtra("programKayitli", customer != null)
                putExtra("rehberKayitli", contactName != null)

                putExtra("isMissedCall", true)
                putExtra("isRingingCall", false)
                putExtra("fromNotification", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("📵 Cevapsız Çağrı")
                .setContentText(displayName)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_launcher_foreground,
                    "📞 Geri Ara",
                    pendingIntent
                )
                .build()

            val nm =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // ❗ TEK BİLDİRİM (üst üste binmez)
            nm.notify(NOTIFICATION_ID, notification)

            Log.d(TAG, "📵 Cevapsız çağrı bildirimi gösterildi: $displayName")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Cevapsız çağrı bildirimi hatası", e)
        }
    }


    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cevapsız Çağrılar",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Cevapsız çağrı bildirimleri"
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 500, 200, 500)
            channel.setShowBadge(true)

            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ===============================
    // 📒 REHBERDEN İSİM BUL
    // ===============================
    private fun getContactName(context: Context, phone: String): String? {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        // Farklı formatlarda dene
        val variants = listOf(
            phone,
            "0$phone",
            "+90$phone",
            "90$phone",
            phone.replace("^0".toRegex(), ""),
            phone.replace("^\\+90".toRegex(), ""),
            phone.replace("^90".toRegex(), "")
        ).distinct()

        for (v in variants) {
            if (v.isEmpty()) continue

            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(v)
                )

                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        Log.d(TAG, "✅ Rehberde bulundu: $v -> $name")
                        return name
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rehber sorgu hatası ($v): ${e.message}")
            }
        }

        Log.d(TAG, "❌ Rehberde bulunamadı: $phone")
        return null
    }

    // ===============================
    // 🔄 NUMARA NORMALİZASYONU
    // ===============================
    private fun normalize(n: String): String {
        if (n.isNullOrBlank()) return ""

        var s = n.replace("[^0-9+]".toRegex(), "")

        when {
            s.startsWith("+90") && s.length > 3 -> {
                s = s.substring(3)
            }
            s.startsWith("90") && s.length > 2 -> {
                s = s.substring(2)
            }
            s.startsWith("0") && s.length > 1 -> {
                s = s.substring(1)
            }
        }

        s = s.replace("[^0-9]".toRegex(), "")
        return s
    }

    // ===============================
    // 📱 REHBERDE VAR MI?
    // ===============================
    private fun isNumberInContacts(context: Context, phone: String): Boolean {
        if (phone.isEmpty()) return false

        return try {
            val normalized = normalize(phone)
            val variants = listOf(
                normalized,
                "0$normalized",
                "+90$normalized",
                "90$normalized"
            ).distinct()

            for (v in variants) {
                if (v.isEmpty()) continue

                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(v)
                )

                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        Log.d(TAG, "✅ Rehberde var: $v")
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Rehber kontrol hatası", e)
            false
        }
    }

    // ===============================
    // 🔄 BROADCAST DİNLETİCİ EKLE
    // ===============================
    fun setContactUpdateListener(listener: (String) -> Unit) {
        contactUpdateListener = listener
    }
}