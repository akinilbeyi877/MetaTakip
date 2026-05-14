package com.example.metatakip.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import com.example.metatakip.helpers.FirmaResolver
import com.example.metatakip.feature_data.entityModel.CallRecord

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"

        // Çağrı durumunu tutmak için
        private var lastState: String? = null
        private var lastIncomingNumber: String? = null
        private var callStartTime: Long = 0
    }

    override fun onReceive(context: Context, intent: Intent) {

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber =
            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state == null) return

        // Aynı state tekrar gelirse işlem yapma
        if (state == lastState) return

        when (state) {

            // 📞 Telefon çalıyor
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastIncomingNumber = incomingNumber
                callStartTime = System.currentTimeMillis() / 1000

                Log.d(TAG, "📞 ÇAĞRI GELİYOR: $incomingNumber")
            }

            // 📞 Çağrı açıldı
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "📞 ÇAĞRI AÇILDI")
            }

            // 📞 Çağrı bitti (EN ÖNEMLİ YER)
            TelephonyManager.EXTRA_STATE_IDLE -> {

                if (!lastIncomingNumber.isNullOrBlank()) {

                    Log.d(TAG, "📞 ÇAĞRI BİTTİ: $lastIncomingNumber")

                    // 🔍 Firma otomatik çöz
                    val firmaResult = FirmaResolver.resolve(
                        context,
                        lastIncomingNumber!!
                    )

                    val callRecord = CallRecord(
                        musteriTelefonu = lastIncomingNumber!!,
                        musteriAdi = firmaResult.kullaniciAdi,

                        arananFirmaAdi = firmaResult.firmaAdi ?: "Firma bulunamadı",
                        arananHatAdi = "Ana Hat",
                        arananTelefon = lastIncomingNumber!!,

                        cihazAdi = Build.MODEL,
                        cihazFirmaAdi = firmaResult.firmaAdi ?: "Bilinmiyor",
                        cihazKullaniciAdi = firmaResult.kullaniciAdi ?: "Bilinmiyor",
                        cihazRolu = "SAHA",
                        cihazMerkezMi = false,
                        simYuvasi = "SIM1",

                        cagriTuru = "GELEN",
                        cagriZamani = callStartTime
                    )

                    CallLogsDao(context).addCallLog(callRecord)

                    Log.d(
                        TAG,
                        "✅ KAYIT ATILDI | Firma=${callRecord.cihazFirmaAdi} | Kişi=${callRecord.cihazKullaniciAdi}"
                    )
                }

                // Reset
                lastIncomingNumber = null
                callStartTime = 0
            }
        }

        lastState = state
    }
}
