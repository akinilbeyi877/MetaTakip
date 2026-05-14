// DeviceConfig.kt
package com.example.metatakip.feature_data.entityModel

import android.os.Build

/**
 * 📱 Cihaz yapılandırma modeli
 *
 * Bu model:
 * - Cihazın hangi firmaya ait olduğunu
 * - Kullanıcıyı
 * - Rolünü (SAHA / MERKEZ / SUBE)
 * - SIM bilgilerini
 * - Merkez cihaz olup olmadığını
 * - Merkez IP adresini
 * tutar.
 */
data class DeviceConfig(

    // 🔑 Cihaza özel benzersiz ID
    val deviceId: String = generateDeviceId(),

    // 📱 Cihaz modeli (örn: Samsung S23)
    val deviceName: String = Build.MODEL,

    // 🏢 Firma adı (Pars Halı, Mega Halı vb.)
    val companyName: String = "",

    // 👤 Kullanıcı adı (Yağız, Aşkın, Akın vb.)
    val userName: String = "",

    // 🎯 Rol (SAHA / MERKEZ / SUBE)
    val userRole: String = "SAHA",

    // 📞 SIM 1 numarası
    val simSlot1Number: String = "",

    // 📞 SIM 2 numarası
    val simSlot2Number: String = "",

    // 🏢 Bu cihaz merkez mi?
    val isCentralDevice: Boolean = false,

    // 🌐 MERKEZ IP ADRESİ
    // - MERKEZ cihazda: otomatik algılanır ama kullanıcı değiştirebilir
    // - SAHA cihazda : manuel girilir (merkeze veri göndermek için)
    val centralIp: String = "",

    // ⏱ Yapılandırma zamanı (timestamp)
    val configuredAt: Long = System.currentTimeMillis()

) {

    companion object {

        /**
         * 🔧 Benzersiz cihaz ID üretir
         *
         * Örnek çıktı:
         * SAMSUNG_SM-G991B_1700000000000
         */
        fun generateDeviceId(): String {
            return "${Build.BRAND}_${Build.MODEL}_${System.currentTimeMillis()}"
                .replace(" ", "_")
                .uppercase()
        }
    }
}
