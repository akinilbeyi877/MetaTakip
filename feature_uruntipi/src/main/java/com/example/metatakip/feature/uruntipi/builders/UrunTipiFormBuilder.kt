package com.example.metatakip.feature.uruntipi.builders

import android.content.Context
import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoInterface
import com.example.metatakip.feature_data.entityModel.UrunTipi

/**
 * 🏗️ UrunTipiFormBuilder
 * Admin sistemi için form yapıcısı (YENİ FormField yapısına uygun)
 * ✅ KOD ALANI YOK!
 */
class UrunTipiFormBuilder(private val context: Context) {

    fun canHandle(tableName: String): Boolean {
        return tableName.equals("urun_tipi", ignoreCase = true) ||
                tableName.equals("urun tipi", ignoreCase = true) ||
                tableName.equals("product_type", ignoreCase = true)
    }

    /**
     * 📝 Form alanlarını oluştur (YENİ YAPI)
     * ✅ KOD ALANI YOK!
     */
    fun buildForm(): MutableList<FormField> {
        val formFields = mutableListOf<FormField>()

        // 1. Ad alanı (TEK ANA ALAN) ✅
        formFields.add(
            FormField.textField(
                label = "Ürün Tipi Adı",
                key = "ad",
                isRequired = true,
                placeholder = "Halı, Koltuk, Perde"
            ).copy(
                maxLength = 50,
                helperText = "Müşterilere görünen ad",
                icon = "category"
            )
        )

        // 2. Birim Fiyat
        formFields.add(
            FormField(
                label = "Birim Fiyat (₺)",
                key = "birimFiyat",
                type = FieldType.NUMBER,
                value = "0.0",
                isRequired = true,
                placeholder = "0.00",
                helperText = "Varsayılan birim fiyat",
                icon = "money",
                minValue = 0.0,
                step = 0.01
            )
        )

        // 3. Hesap Tipi (Dropdown)
        formFields.add(
            FormField.dropdownField(
                label = "Hesap Tipi",
                key = "hesapTipi",
                options = listOf("M2", "ADET", "METRE", "KG", "LİTRE", "PAKET"),
                isRequired = true
            ).copy(
                value = "M2",
                helperText = "Hesaplama birimi",
                icon = "calculate"
            )
        )

        // 4. Aktif/Pasif (Switch)
        formFields.add(
            FormField(
                label = "Durum",
                key = "aktif",
                type = FieldType.SWITCH,
                value = "1",
                helperText = "Ürün tipini aktif/pasif yap",
                icon = "power"
            )
        )

        // 5. Açıklama (Textarea)
        formFields.add(
            FormField(
                label = "Açıklama",
                key = "aciklama",
                type = FieldType.TEXTAREA,
                placeholder = "Opsiyonel açıklama ekleyin...",
                helperText = "Ek bilgiler için",
                icon = "description",
                rows = 3
            )
        )

        return formFields
    }

    /**
     * 📥 Form verisini doldur (edit mode)
     * ✅ KOD ALANI YOK!
     */
    fun loadFormData(
        dao: UrunTipiDaoInterface,
        recordId: Long,
        fields: MutableMap<String, Any?>
    ): Boolean {
        return try {
            val urunTipleri = dao.getAll()
            val urunTipi = urunTipleri.find { it.id == recordId }

            if (urunTipi != null) {
                // ✅ KOD YOK!
                fields["ad"] = urunTipi.ad
                fields["birimFiyat"] = urunTipi.birimFiyat.toString()
                fields["hesapTipi"] = urunTipi.hesapTipi
                fields["aktif"] = if (urunTipi.aktif == 1) "1" else "0"
                fields["aciklama"] = urunTipi.aciklama ?: ""
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 💾 Form verisini kaydet (YENİ FORMAT)
     * ✅ KOD ALANI YOK!
     */
    fun saveFormData(
        dao: UrunTipiDaoInterface,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long
    ): Boolean {
        return try {
            // Verileri al (nullable güvenli) - ✅ KOD YOK!
            val ad = data["ad"]?.toString()?.trim() ?: ""
            val birimFiyat = data["birimFiyat"]?.toString()?.toDoubleOrNull() ?: 0.0
            val hesapTipi = data["hesapTipi"]?.toString() ?: "M2"
            val aktif = when (data["aktif"]?.toString()) {
                "true", "1", "on" -> 1
                else -> 0
            }
            val aciklama = data["aciklama"]?.toString()?.trim()

            // Validasyon - ✅ KOD KONTROLÜ YOK!
            if (ad.isEmpty()) {
                // Ad boş olamaz
                return false
            }

            if (birimFiyat < 0) {
                // Fiyat negatif olamaz
                return false
            }

            // UrunTipi modeli oluştur - ✅ KOD YOK!
            val urunTipi = UrunTipi(
                id = if (editMode) recordId else 0,
                ad = ad,
                birimFiyat = birimFiyat,
                hesapTipi = hesapTipi,
                aktif = aktif,
                aciklama = aciklama
            )

            // Kaydet - ✅ DÜZELTİLDİ!
            if (editMode) {
                // DAO.update() iki parametre bekliyor: id ve UrunTipi
                dao.update(recordId, urunTipi)  // ← SATIR 175 DÜZELTİLDİ!
            } else {
                dao.insert(urunTipi)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 🎯 Form alanlarını validasyondan geçir
     */
    fun validateForm(data: Map<String, Any?>): Pair<Boolean, String> {
        val formFields = buildForm()

        for (field in formFields) {
            // Field'ın value'sunu data'dan al
            val value = data[field.key]?.toString() ?: ""
            field.value = value

            // Validasyon yap
            val (isValid, errorMessage) = field.validate()
            if (!isValid) {
                return Pair(false, errorMessage ?: "Geçersiz veri")
            }
        }

        return Pair(true, "Başarılı")
    }

    /**
     * 🔄 Form verilerini temizle
     */
    fun getDefaultValues(): Map<String, String> {
        val defaultValues = mutableMapOf<String, String>()
        val formFields = buildForm()

        formFields.forEach { field ->
            field.applyDefaultIfEmpty()
            defaultValues[field.key] = field.value
        }

        return defaultValues
    }
}