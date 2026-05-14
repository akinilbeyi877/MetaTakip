// File: GenericBuildFormForTable.kt
package com.example.metatakip.builders

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.metatakip.feature.admin.builders.FeatureAdminFormProvider
import com.example.metatakip.feature.customer.providers.FeatureCustomerTableFormProvider
import com.example.metatakip.feature.firma.providers.FeatureFirmaTableFormProvider
import com.example.metatakip.feature.label.providers.FeatureLabelTableFormProvider
import com.example.metatakip.feature.order.providers.FeatureOrderTableFormProvider
import com.example.metatakip.feature.personel.providers.FeaturePersonelTableFormProvider
import com.example.metatakip.feature.unvan.providers.FeatureUnvanTableFormProvider
import com.example.metatakip.feature.uruntipi.builders.FeatureUrunTipiTableFormProvider
import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.feature_data.unvan.UnvanDaoInterface
import dao.MetaTakipFirmaDao

class GenericBuildFormForTable(
    private val context: Context,
    private val firmaDao: MetaTakipFirmaDao,
    private val unvanDao: UnvanDaoInterface
) {

    private val adminFormProvider = FeatureAdminFormProvider(context)

    fun build(table: String): MutableList<FormField> {

        // 1) ✅ ÖNCE FEATURE PROVIDER'LAR
        val featureForm = tryLoadFromFeatureProviders(table)
        if (featureForm.isNotEmpty()) return featureForm

        // 2) ✅ ADMIN FEATURE PROVIDER
        if (adminFormProvider.canHandle(table)) {
            return buildAdminFormFields(table)
        }

        // 3) 🧯 FALLBACK
        val formFields = mutableListOf<FormField>()

        when (table.lowercase()) {

            "unvan" -> {
                formFields += FormField("Ad", "ad", FieldType.TEXT, isRequired = true)
                formFields += FormField("Açıklama", "aciklama", FieldType.TEXTAREA)
            }

            "siparis", "order", "orders" -> {
                formFields += FormField("Müşteri Adı", "musteriAdi", FieldType.TEXT, isRequired = true)
                formFields += FormField("Müşteri Telefonu", "musteriTelefon", FieldType.PHONE)
                formFields += FormField("Notlar", "notlar", FieldType.TEXTAREA)
                formFields += FormField(
                    label = "Durum",
                    key = "durum",
                    type = FieldType.DROPDOWN,
                    value = "Yeni Sipariş",
                    options = listOf(
                        "Yeni Sipariş", "Beklemede", "Alındı", "Yıkanıyor",
                        "Kuruyor", "Paketleniyor", "Teslim Edildi", "İptal Edildi"
                    )
                )
            }

            "siparis_bilgi_ekle" -> {
                formFields += FormField("Ürün Adı", "urunAdi", FieldType.TEXT, isRequired = true)
                formFields += FormField("Adet", "adet", FieldType.NUMBER)
                formFields += FormField("Fiyat (₺)", "fiyat", FieldType.NUMBER)
                formFields += FormField("En (cm)", "en", FieldType.NUMBER)
                formFields += FormField("Boy (cm)", "boy", FieldType.NUMBER)
                formFields += FormField("Metrekare", "metrekare", FieldType.NUMBER)
                formFields += FormField("Ek Not", "ekNot", FieldType.TEXTAREA)
            }

            else -> Toast.makeText(context, "⚠️ Geçersiz tablo adı: $table", Toast.LENGTH_SHORT).show()
        }

        return formFields
    }

    private fun tryLoadFromFeatureProviders(table: String): MutableList<FormField> {

        if (FeatureCustomerTableFormProvider.canHandle(table)) {
            val formFields = FeatureCustomerTableFormProvider.getFormFields(firmaDao).toMutableList()
            Log.d("FormBuilder", "✅ Customer formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        if (FeatureFirmaTableFormProvider.canHandle(table)) {
            val formFields = FeatureFirmaTableFormProvider.getFormFields().toMutableList()
            Log.d("FormBuilder", "✅ Firma formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        if (FeatureOrderTableFormProvider.canHandle(table)) {
            val formFields = FeatureOrderTableFormProvider.getFormFields(firmaDao).toMutableList()
            Log.d("FormBuilder", "✅ Order formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        if (FeatureUnvanTableFormProvider.canHandle(table)) {
            val formFields = FeatureUnvanTableFormProvider.getFormFields().toMutableList()
            Log.d("FormBuilder", "✅ Unvan formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        if (FeaturePersonelTableFormProvider.canHandle(table)) {
            val formFields = FeaturePersonelTableFormProvider.getFormFields(unvanDao).toMutableList()
            Log.d("FormBuilder", "✅ Personel formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        if (FeatureUrunTipiTableFormProvider.canHandle(table)) {
            val formFields = FeatureUrunTipiTableFormProvider.getFormFields().toMutableList()
            Log.d("FormBuilder", "✅ Ürün tipi formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        // ✅ LABEL / ETİKET ŞABLON — firmaDao geçiliyor
        if (FeatureLabelTableFormProvider.canHandle(table)) {
            val formFields = FeatureLabelTableFormProvider.getFormFields(firmaDao).toMutableList()
            Log.d("FormBuilder", "✅ Etiket şablon formu feature'dan yüklendi (${formFields.size} alan)")
            return formFields
        }

        return mutableListOf()
    }

    private fun buildAdminFormFields(table: String): MutableList<FormField> {
        val formFields = mutableListOf<FormField>()

        when (table.lowercase()) {

            "mesaj_sablon" -> {
                val firmaList = runCatching { firmaDao.getAllFirmalar() }.getOrDefault(emptyList())
                val firmaOptionMap =
                    firmaList.associate { (it.firmaAdi ?: "Firma #${it.id}") to it.id.toString() }

                formFields += FormField(
                    label = "Firma",
                    key = "firmaid",
                    type = FieldType.DROPDOWN,
                    optionMap = if (firmaOptionMap.isEmpty()) mapOf("Firma bulunamadı" to "0") else firmaOptionMap,
                    value = if (firmaOptionMap.isNotEmpty()) firmaOptionMap.values.first() else "0",
                    isRequired = true
                )

                formFields += FormField(
                    label = "Firma Adı",
                    key = "firma_adi",
                    type = FieldType.HIDDEN,
                    value = if (firmaOptionMap.isNotEmpty()) firmaOptionMap.keys.first() else ""
                )

                formFields += FormField(
                    label = "Başlık",
                    key = "baslik",
                    type = FieldType.TEXT,
                    value = "Varsayılan Mesaj Şablonları",
                    isRequired = true
                )

                formFields += FormField(
                    label = "Müşteri Oluşturuldu Mesajı",
                    key = "musteri_olustu_mesaj",
                    type = FieldType.TEXTAREA,
                    value = "Sayın {{musteri_adi}},\nKaydınız oluşturulmuştur.\n\n{{firma_adi}}\n{{firma_tel}}"
                )

                formFields += FormField(
                    label = "Müşteri Güncellendi Mesajı",
                    key = "musteri_guncellendi_mesaj",
                    type = FieldType.TEXTAREA,
                    value = "Sayın {{musteri_adi}},\nBilgileriniz güncellenmiştir.\n\n{{firma_adi}}\n{{firma_tel}}"
                )

                formFields += FormField(
                    label = "Sipariş Oluşturuldu Mesajı",
                    key = "siparis_olustu_mesaj",
                    type = FieldType.TEXTAREA,
                    value = "Sayın {{musteri_adi}},\nSiparişiniz alınmıştır.\nSipariş No: {{siparis_no}}\n\n{{firma_adi}}\n{{firma_tel}}"
                )

                formFields += FormField(
                    label = "Siparişe Ürün Eklendi Mesajı",
                    key = "siparis_urun_eklendi_mesaj",
                    type = FieldType.TEXTAREA,
                    value = "Sayın {{musteri_adi}},\nSiparişinize yeni bir ürün eklenmiştir.\n\nÜrün: {{urun_adi}}\nAdet: {{adet}}\n\n{{firma_adi}}\n{{firma_tel}}"
                )

                formFields += FormField(
                    label = "SMS Onay Mesajı",
                    key = "sms_onay_mesaj",
                    type = FieldType.TEXTAREA,
                    value = "SMS gönderilsin mi?"
                )

                formFields += FormField(
                    label = "WhatsApp Onay Mesajı",
                    key = "whatsapp_onay_mesaj",
                    type = FieldType.TEXTAREA,
                    value = "WhatsApp üzerinden de gönderilsin mi?"
                )

                formFields += FormField(
                    label = "Varsayılan Şablon Olarak Kullan",
                    key = "varsayilan",
                    type = FieldType.CHECKBOX,
                    value = "false"
                )
            }

            "admin_firma" -> {
                formFields += FormField("Firma Adı", "adi", FieldType.TEXT, isRequired = true)
                formFields += FormField("Vergi No", "vergi_no", FieldType.TEXT)
                formFields += FormField("Adres", "adres", FieldType.TEXTAREA)
                formFields += FormField("Telefon", "telefon", FieldType.PHONE)
                formFields += FormField("E-posta", "email", FieldType.EMAIL)
                formFields += FormField("Aktif", "aktif", FieldType.CHECKBOX, value = "true")
            }
        }

        return formFields
    }
}
