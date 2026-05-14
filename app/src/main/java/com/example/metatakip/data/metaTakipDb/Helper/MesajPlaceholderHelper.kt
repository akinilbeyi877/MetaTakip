package com.example.metatakip.data.metaTakipDb.Helper

import android.content.Context
import android.util.Log
import dao.MetaTakipCustomerDao
import dao.MetaTakipFirmaDao
import com.example.metatakip.feature.order.data.OrderDaoImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MesajPlaceholderHelper(private val context: Context) {

    private val customerDao = MetaTakipCustomerDao(context)
    private val firmaDao = MetaTakipFirmaDao(context)
    private val siparisDao = OrderDaoImpl(context)

    fun replacePlaceholders(
        mesajSablon: String,
        customerId: Long,
        siparisId: Long? = null,
        urunAdi: String? = null,
        adet: String? = null,
        firmaId: Long
    ): String {
        Log.d("PLACEHOLDER_HELPER", "🔴 === PLACEHOLDER DOLDURMA BAŞLADI ===")
        Log.d("PLACEHOLDER_HELPER", "📄 ORJİNAL: $mesajSablon")
        Log.d("PLACEHOLDER_HELPER", "🔑 Parametreler: customerId=$customerId, siparisId=$siparisId, firmaId=$firmaId")

        var result = mesajSablon

        try {
            // 1. Müşteri bilgileri
            if (customerId > 0) {
                val customer = customerDao.getCustomerById(customerId)
                if (customer != null) {
                    Log.d("PLACEHOLDER_HELPER", "👤 Müşteri bulundu: ${customer.adSoyad} - ${customer.ceptel}")

                    // {{musteri_adi}}
                    if (result.contains("{{musteri_adi}}")) {
                        result = result.replace("{{musteri_adi}}", customer.adSoyad ?: "")
                        Log.d("PLACEHOLDER_HELPER", "✅ {{musteri_adi}} -> ${customer.adSoyad}")
                    }

                    // {{musteri_tel}}
                    if (result.contains("{{musteri_tel}}")) {
                        result = result.replace("{{musteri_tel}}", customer.ceptel ?: "")
                        Log.d("PLACEHOLDER_HELPER", "✅ {{musteri_tel}} -> ${customer.ceptel}")
                    }
                } else {
                    Log.e("PLACEHOLDER_HELPER", "❌ Müşteri bulunamadı: ID=$customerId")
                }
            }

            // 2. Sipariş bilgileri
            if (siparisId != null && siparisId > 0) {
                val siparis = siparisDao.getSiparisById(siparisId)
                if (siparis != null) {
                    Log.d("PLACEHOLDER_HELPER", "🧾 Sipariş bulundu: ID=$siparisId")

                    // {{siparis_no}}
                    if (result.contains("{{siparis_no}}")) {
                        result = result.replace("{{siparis_no}}", siparisId.toString())
                        Log.d("PLACEHOLDER_HELPER", "✅ {{siparis_no}} -> $siparisId")
                    }
                } else {
                    Log.e("PLACEHOLDER_HELPER", "❌ Sipariş bulunamadı: ID=$siparisId")
                }
            }

            // 3. Firma bilgileri
            if (firmaId > 0) {
                val firma = firmaDao.getFirmaById(firmaId)
                if (firma != null) {
                    Log.d("PLACEHOLDER_HELPER", "🏢 Firma bulundu: ${firma.firmaAdi} - ${firma.telefon}")

                    // {{firma_adi}}
                    if (result.contains("{{firma_adi}}")) {
                        result = result.replace("{{firma_adi}}", firma.firmaAdi ?: "")
                        Log.d("PLACEHOLDER_HELPER", "✅ {{firma_adi}} -> ${firma.firmaAdi}")
                    }

                    // {{firma_tel}}
                    if (result.contains("{{firma_tel}}")) {
                        result = result.replace("{{firma_tel}}", firma.telefon ?: "")
                        Log.d("PLACEHOLDER_HELPER", "✅ {{firma_tel}} -> ${firma.telefon}")
                    }
                }
            }

            // 4. Tarih
            if (result.contains("{{tarih}}") || result.contains("{{bugun_tarih}}")) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val today = dateFormat.format(Date())
                result = result.replace("{{tarih}}", today)
                    .replace("{{bugun_tarih}}", today)
                Log.d("PLACEHOLDER_HELPER", "📅 {{tarih}} -> $today")
            }

            // 5. Ürün bilgileri
            urunAdi?.let {
                if (result.contains("{{urun_adi}}")) {
                    result = result.replace("{{urun_adi}}", it)
                    Log.d("PLACEHOLDER_HELPER", "📦 {{urun_adi}} -> $it")
                }
            }

            adet?.let {
                if (result.contains("{{adet}}")) {
                    result = result.replace("{{adet}}", it)
                    Log.d("PLACEHOLDER_HELPER", "📊 {{adet}} -> $it")
                }
            }

            // 6. Temizle: Kalan placeholder'ları kaldır
            val placeholderRegex = "\\{\\{[^}]+\\}\\}".toRegex()
            result = result.replace(placeholderRegex, "").trim()

            Log.d("PLACEHOLDER_HELPER", "🧹 Temizleme sonrası: $result")

        } catch (e: Exception) {
            Log.e("PLACEHOLDER_HELPER", "❌ Placeholder doldurma hatası", e)
        }

        Log.d("PLACEHOLDER_HELPER", "✅ === PLACEHOLDER DOLDURMA TAMAMLANDI ===")
        Log.d("PLACEHOLDER_HELPER", "📝 SONUÇ: $result")

        return result
    }

    private fun replaceDateAndTime(text: String): String {
        var result = text

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = Date()
        val tarih = dateFormat.format(now)
        val saat = timeFormat.format(now)

        // ÇİFT SÜSLÜ PARANTEZ ÖNCELİKLİ
        result = result.replace("{{tarih}}", tarih)
        result = result.replace("{{saat}}", saat)
        result = result.replace("{{bugun}}", tarih)
        result = result.replace("{{bugun_tarihi}}", tarih)

        // TEK SÜSLÜ PARANTEZ
        result = result.replace("{tarih}", tarih)
        result = result.replace("{saat}", saat)
        result = result.replace("{bugun}", tarih)
        result = result.replace("{bugun_tarihi}", tarih)

        Log.d("PLACEHOLDER_FINAL", "📅 Tarih/Saat: $tarih $saat")

        return result
    }

    private fun replaceFirmaInfoFinal(text: String, firmaId: Long?): String {
        var result = text

        if (firmaId == null || firmaId <= 0L) {
            Log.d("PLACEHOLDER_FINAL", "⚠️ Firma ID yok")
            return result
        }

        try {
            val firma = firmaDao.getFirmaById(firmaId)
            if (firma == null) {
                Log.e("PLACEHOLDER_FINAL", "❌ Firma bulunamadı: $firmaId")
                return result
            }

            val firmaAdi = firma.firmaAdi ?: ""
            val firmaTel = firma.telefon ?: ""
            val firmaAdres = firma.adres ?: ""

            Log.d("PLACEHOLDER_FINAL", "🏢 Firma: $firmaAdi - $firmaTel")

            // ÇİFT SÜSLÜ PARANTEZ ÖNCELİKLİ
            result = result.replace("{{firma_adi}}", firmaAdi)
            result = result.replace("{{firma_tel}}", firmaTel)
            result = result.replace("{{firma_adres}}", firmaAdres)

            // TEK SÜSLÜ PARANTEZ
            result = result.replace("{firma_adi}", firmaAdi)
            result = result.replace("{firma_tel}", firmaTel)
            result = result.replace("{firma_adres}", firmaAdres)
            result = result.replace("{firmaAdi}", firmaAdi)

        } catch (e: Exception) {
            Log.e("PLACEHOLDER_FINAL", "❌ Firma hatası", e)
        }

        return result
    }

    private fun replaceCustomerInfoFinal(text: String, customerId: Long): String {
        var result = text

        try {
            val customer = customerDao.getCustomerById(customerId)
            if (customer == null) {
                Log.e("PLACEHOLDER_FINAL", "❌ Müşteri bulunamadı: $customerId")
                return result
            }

            val musteriAdi = customer.adSoyad ?: ""
            val musteriTel = customer.ceptel ?: ""
            val musteriAdres = customer.adres ?: ""

            Log.d("PLACEHOLDER_FINAL", "👤 Müşteri: $musteriAdi - $musteriTel")

            // ÇİFT SÜSLÜ PARANTEZ ÖNCELİKLİ
            result = result.replace("{{musteri_adi}}", musteriAdi)
            result = result.replace("{{musteri_telefon}}", musteriTel)
            result = result.replace("{{musteri_adres}}", musteriAdres)

            // TEK SÜSLÜ PARANTEZ
            result = result.replace("{musteri_adi}", musteriAdi)
            result = result.replace("{musteri_telefon}", musteriTel)
            result = result.replace("{musteri_adres}", musteriAdres)
            result = result.replace("{musteriAdi}", musteriAdi)

        } catch (e: Exception) {
            Log.e("PLACEHOLDER_FINAL", "❌ Müşteri hatası", e)
        }

        return result
    }

    private fun replaceSiparisInfoFinal(text: String, siparisId: Long): String {
        var result = text

        try {
            val siparisNo = siparisId.toString()
            Log.d("PLACEHOLDER_FINAL", "🧾 Sipariş No: $siparisNo")

            // ÇİFT SÜSLÜ PARANTEZ ÖNCELİKLİ
            result = result.replace("{{siparis_no}}", siparisNo)

            // TEK SÜSLÜ PARANTEZ
            result = result.replace("{siparis_no}", siparisNo)
            result = result.replace("{siparisNo}", siparisNo)
            result = result.replace("{siparis_id}", siparisNo)

            // Diğer sipariş bilgileri
            val siparis = siparisDao.getSiparisById(siparisId)
            siparis?.let {
                val durum = it.durum ?: ""
                val tarih = it.teslimAlmaTarihi ?: ""

                result = result.replace("{{siparis_durumu}}", durum)
                result = result.replace("{{siparis_tarihi}}", tarih)
                result = result.replace("{siparis_durumu}", durum)
                result = result.replace("{siparis_tarihi}", tarih)
            }

        } catch (e: Exception) {
            Log.e("PLACEHOLDER_FINAL", "❌ Sipariş hatası", e)
        }

        return result
    }

    private fun replaceProductInfoFinal(text: String, urunAdi: String?, adet: String?): String {
        var result = text

        urunAdi?.let {
            result = result.replace("{{urun_adi}}", it)
            result = result.replace("{urun_adi}", it)
            Log.d("PLACEHOLDER_FINAL", "📦 Ürün: $it")
        }

        adet?.let {
            result = result.replace("{{adet}}", it)
            result = result.replace("{adet}", it)
            Log.d("PLACEHOLDER_FINAL", "📦 Adet: $it")
        }

        return result
    }

    /**
     * 🎯 KESİN ÇÖZÜM: Çift süslü parantezleri temizle
     * Bu fonksiyon, {{...}} şeklindeki boş kalmış placeholder'ları temizler
     */
    private fun cleanDoubleBraces(text: String): String {
        var result = text

        // 1. Önce tüm {{...}} şeklindeki boş placeholder'ları temizle
        val doublePattern = Regex("\\{\\{[^{}]*\\}\\}")
        result = doublePattern.replace(result) { match ->
            val value = match.value
            // Eğer sadece {{...}} şeklindeyse ve içinde değer yoksa temizle
            if (value == "{{}}" || value.contains("{{") && value.contains("}}") &&
                value.length <= 4) {
                Log.d("PLACEHOLDER_FINAL", "🧹 Temizlenen çift parantez: $value")
                ""
            } else {
                value
            }
        }

        // 2. Kalan tüm {} şeklindeki boşlukları temizle
        result = result.replace("{}", "")
        result = result.replace("{{}}", "")

        Log.d("PLACEHOLDER_FINAL", "🧹 Temizleme sonrası: $result")

        return result
    }
}