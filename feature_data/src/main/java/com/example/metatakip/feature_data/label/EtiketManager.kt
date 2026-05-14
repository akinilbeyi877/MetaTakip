package com.example.metatakip.feature_data.label

import android.content.Context
import com.example.metatakip.feature_data.entityModel.Customer
import com.example.metatakip.feature_data.entityModel.Firma
import com.example.metatakip.feature_data.entityModel.Order
import com.example.metatakip.feature_data.entityModel.Personel
import com.example.metatakip.feature_data.entityModel.Unvan
import com.example.metatakip.feature_data.entityModel.Urun
import com.example.metatakip.feature_data.entityModel.User
import dao.MetaTakipCustomerDao
import dao.MetaTakipFirmaDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EtiketManager(private val context: Context) {

    // =============================================================
    // 🔹 AKORDİYON GRUPLARI
    // =============================================================
    enum class EtiketKaynak {
        SIPARIS,
        MUSTERI,
        FIRMA,
        URUN,
        PERSONEL,
        USER,
        UNVAN,
        SABIT
    }

    // =============================================================
    // 🔹 ETİKET BİLEŞENİ
    // =============================================================
    data class EtiketBileseni(
        val id: String,
        val baslik: String,
        val kaynak: EtiketKaynak,
        val source: Any,                 // 🔥 gerçek veri kaynağı
        var secili: Boolean = true,
        val valueProvider: (Any) -> String
    )

    private val prefs =
        context.getSharedPreferences("etiket_prefs", Context.MODE_PRIVATE)

    // =============================================================
    // 🧾 SİPARİŞ İÇİN İNSANİ BAŞLIKLAR
    // =============================================================
    private val siparisLabelMap = mapOf(
        "id" to "Sipariş ID",
        "musteriId" to "Siparişe Ait Müşteri ID",

        "musteriAdi" to "Müşteri Adı",
        "musteriTelefon" to "Müşteri Telefonu",
        "firmaAdi" to "Firma",

        "urunTipi" to "Ürün Tipi",
        "en" to "Ürün Eni (cm)",
        "boy" to "Ürün Boyu (cm)",
        "metrekare" to "Toplam Metrekare",
        "ucret" to "Toplam Ücret",

        "teslimAlmaTarihi" to "Teslim Alma Tarihi",
        "teslimTarihi" to "Teslim Tarihi",
        "notlar" to "Sipariş Notu",
        "durum" to "Sipariş Durumu",
        "yetkili" to "Sorumlu Yetkili",

        "etiketSablonId" to "Etiket Şablon ID",

        // 🗑️ Soft delete
        "isDeleted" to "Sipariş Silinmiş mi",
        "deletedAt" to "Silinme Tarihi",
        "deletedBy" to "Silen Kullanıcı",
        "deleteReason" to "Silinme Nedeni"
    )

    // =============================================================
    // 🔥 MODEL → ETİKET DÖNÜŞTÜRÜCÜ
    // =============================================================
    private fun buildAllFromModel(
        model: Any,
        prefix: String,
        kaynak: EtiketKaynak,
        labelMap: Map<String, String>? = null
    ): List<EtiketBileseni> {

        return model.javaClass.declaredFields.map { field ->
            field.isAccessible = true

            val baslik = labelMap?.get(field.name)
                ?: field.name
                    .replaceFirstChar { it.uppercaseChar() }
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")

            EtiketBileseni(
                id = "${prefix}_${field.name}",
                baslik = baslik,
                kaynak = kaynak,
                source = model,
                secili = true
            ) {
                try {
                    field.get(model)?.toString() ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    // =============================================================
    // 🔥 MERKEZ METOT
    // =============================================================
    fun getEtiketBilesenleri(item: Any): MutableList<EtiketBileseni> {

        val result = mutableListOf<EtiketBileseni>()

        when (item) {

            is Order -> {

                // 1️⃣ Sipariş
                result += buildAllFromModel(
                    model = item,
                    prefix = "siparis",
                    kaynak = EtiketKaynak.SIPARIS,
                    labelMap = siparisLabelMap
                )

                // 2️⃣ Müşteri (gerçek ilişki)
                MetaTakipCustomerDao(context)
                    .getCustomerById(item.musteriId)
                    ?.let {
                        result += buildAllFromModel(
                            it, "musteri", EtiketKaynak.MUSTERI
                        )
                    }

                // 3️⃣ Firma (isim eşleşmesiyle)
                MetaTakipFirmaDao(context)
                    .getAllFirmalar()
                    .firstOrNull { it.firmaAdi == item.firmaAdi }
                    ?.let {
                        result += buildAllFromModel(
                            it, "firma", EtiketKaynak.FIRMA
                        )
                    }
            }

            is Customer ->
                result += buildAllFromModel(item, "musteri", EtiketKaynak.MUSTERI)

            is Firma ->
                result += buildAllFromModel(item, "firma", EtiketKaynak.FIRMA)

            is Urun ->
                result += buildAllFromModel(item, "urun", EtiketKaynak.URUN)

            is Personel ->
                result += buildAllFromModel(item, "personel", EtiketKaynak.PERSONEL)

            is User ->
                result += buildAllFromModel(item, "user", EtiketKaynak.USER)

            is Unvan ->
                result += buildAllFromModel(item, "unvan", EtiketKaynak.UNVAN)
        }

        applyDefaultSelection(result)
        return result
    }

    // =============================================================
    // ⭐ VARSAYILAN SEÇİMLER
    // =============================================================
    private fun applyDefaultSelection(list: MutableList<EtiketBileseni>) {
        val saved = prefs.getStringSet("default_etiket_ids", null) ?: return
        list.forEach { it.secili = saved.contains(it.id) }
    }

    // =============================================================
    // 🧾 ETİKET / FİŞ (BOŞ ALAN YAZMAZ)
    // =============================================================
    fun buildCustomReceipt(
        item: Any,
        secili: List<EtiketBileseni>
    ): String {

        val lines = mutableListOf<String>()

        lines += "========================================"
        lines += "ETİKET / FİŞ"
        lines += "========================================"

        secili.forEach {
            val value = it.valueProvider(it.source)
            if (value.isNotBlank()) {
                lines += "${it.baslik}: $value"
            }
        }

        lines += "========================================"
        lines += "Tarih: ${
            SimpleDateFormat(
                "dd.MM.yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(Date())
        }"

        return lines.joinToString("\n")
    }
}