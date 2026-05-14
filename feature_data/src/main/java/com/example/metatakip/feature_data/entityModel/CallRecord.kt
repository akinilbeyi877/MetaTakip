package com.example.metatakip.feature_data.entityModel

import android.text.format.DateFormat
import java.util.*

data class CallRecord(
    val id: Long = 0,

    // 🌐 Senkronizasyon kimliği (clientler arası)
    val uuid: String? = null,

    // 📞 Müşteri
    val musteriTelefonu: String,
    val musteriAdi: String? = null,

    // 🏢 Aranan/Arayan taraf
    val arananFirmaAdi: String,
    val arananHatAdi: String,
    val arananTelefon: String,

    // 📱 Kaynak cihaz (BU TELEFONUN BİLGİLERİ)
    val cihazAdi: String,
    val cihazFirmaAdi: String, // Pars Halı, Ak Halı, Mega Halı
    val cihazKullaniciAdi: String, // Yağız, Aşkın, Akın
    val cihazRolu: String, // MERKEZ / SAHA
    val cihazMerkezMi: Boolean,
    val simYuvasi: String,

    // 📊 Çağrı
    val cagriTuru: String, // GELEN / CEVAPSIZ / GIDEN
    val cagriZamani: Long? = null,

    // 🌐 Merkez senkron
    val merkezeIletildiMi: Boolean = false,
    val merkezeIletilmeZamani: Long? = null,
    val merkezHataMesaji: String? = null,

    // 📅 Oluşturma zamanı
    val createdAt: Long? = null
) {
    // Yardımcı metodlar
    fun getFormattedCallTime(): String {
        return if (cagriZamani != null) {
            val date = Date(cagriZamani * 1000)
            DateFormat.format("dd.MM.yyyy HH:mm", date).toString()
        } else {
            "Bilinmiyor"
        }
    }

    fun isSynced(): Boolean = merkezeIletildiMi

    fun getCallTypeText(): String {
        return when (cagriTuru) {
            "GELEN" -> "📥 Gelen"
            "GIDEN" -> "📤 Giden"
            "CEVAPSIZ" -> "❌ Cevapsız"
            else -> cagriTuru
        }
    }

    fun getDeviceInfo(): String {
        return "$cihazAdi - $cihazFirmaAdi ($cihazKullaniciAdi)"
    }

    fun isFromCentral(): Boolean = cihazMerkezMi
}