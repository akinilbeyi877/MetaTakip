package com.example.metatakip.feature.order.providers

import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField
import dao.MetaTakipFirmaDao

/**
 * 🏗️ FeatureOrderTableFormProvider
 * GenericBuildFormForTable için sipariş (order) form alanlarını sağlar
 *
 * Not:
 * - Sipariş artık müşteri/firma adını DB'de kopya olarak tutmaz.
 * - Bu yüzden musteriAdi / musteriTelefon / firmaid / firmaAdi alanları
 *   sadece gösterim amaçlıdır.
 * - Kaydetmede esas alınan ilişki: linkedCustomerId -> musteriId
 */
object FeatureOrderTableFormProvider {

    fun canHandle(table: String): Boolean {
        return table.equals("siparis", ignoreCase = true) ||
                table.equals("order", ignoreCase = true) ||
                table.equals("orders", ignoreCase = true)
    }

    fun getFormFields(firmaDao: MetaTakipFirmaDao): List<FormField> {
        val firmaList = runCatching { firmaDao.getAllFirmalar() }.getOrDefault(emptyList())
        val firmaOptionMap = firmaList.associate { (it.firmaAdi ?: "Firma #${it.id}") to it.id.toString() }

        return mutableListOf<FormField>().apply {

            // ============================================================
            // 👤 GÖSTERİM AMAÇLI ALANLAR (ilişkili müşteri/firma verisi)
            // ============================================================

            add(
                FormField(
                    label = "Müşteri Adı",
                    key = "musteriAdi",
                    type = FieldType.TEXT,
                    isRequired = false,
                    placeholder = "Müşteri seçimiyle otomatik gelir",
                    helperText = """
                        👤 Bu alan ilişkili müşteri kaydından otomatik gelir.
                        Siparişten değiştirilmez.
                    """.trimIndent(),
                    icon = "person"
                )
            )

            add(
                FormField(
                    label = "Müşteri Telefon",
                    key = "musteriTelefon",
                    type = FieldType.PHONE,
                    isRequired = false,
                    placeholder = "Telefon otomatik gelir",
                    helperText = """
                        📞 Bu alan ilişkili müşteri kaydından otomatik gelir.
                        Siparişten değiştirilmez.
                    """.trimIndent(),
                    icon = "phone"
                )
            )

            add(
                FormField(
                    label = "Firma",
                    key = "firmaid",
                    type = FieldType.DROPDOWN,
                    optionMap = if (firmaOptionMap.isEmpty()) mapOf("Firma bulunamadı" to "0") else firmaOptionMap,
                    isRequired = false,
                    placeholder = "Firma seçin",
                    helperText = "🏢 Siparişin bağlı olduğu firma.",
                    icon = "business"
                )
            )

            // ============================================================
            // ✅ GERÇEK SİPARİŞ ALANLARI
            // ============================================================

            add(
                FormField(
                    label = "Ürün Tipi",
                    key = "urunTipi",
                    type = FieldType.DROPDOWN,
                    isRequired = false,
                    placeholder = "Halı / Koltuk / Perde ...",
                    helperText = """
                        📦 Siparişe ait ürün tipi bilgisi.
                    """.trimIndent(),
                    icon = "category"
                )
            )

            add(
                FormField(
                    label = "Yetkili",
                    key = "yetkili",
                    type = FieldType.DROPDOWN,
                    isRequired = false,
                    placeholder = "Teslim alan / görüşülen kişi",
                    helperText = """
                        👤 Siparişte görüşülen veya teslim alan kişi bilgisi.
                    """.trimIndent(),
                    icon = "badge"
                )
            )

            add(
                FormField(
                    label = "Sipariş Durumu",
                    key = "durum",
                    type = FieldType.DROPDOWN,
                    value = "Yeni Sipariş",
                    options = listOf(
                        "Yeni Sipariş",
                        "Alındı",
                        "Yıkamada",
                        "Hazır",
                        "Teslim Edildi",
                        "İptal"
                    ),
                    isRequired = true,
                    helperText = """
                        🧾 Siparişin mevcut durumunu seçin.
                    """.trimIndent(),
                    icon = "flag"
                )
            )

            add(
                FormField(
                    label = "Teslim Alma Tarihi",
                    key = "teslimAlmaTarihi",
                    type = FieldType.DATE,
                    isRequired = false,
                    placeholder = "GG/AA/YYYY",
                    helperText = """
                        📅 Siparişin teslim alındığı tarih.
                        Yeni siparişte otomatik set edilebilir.
                    """.trimIndent(),
                    icon = "event"
                )
            )

            add(
                FormField(
                    label = "Teslim Tarihi",
                    key = "teslimTarihi",
                    type = FieldType.DATE,
                    isRequired = false,
                    placeholder = "GG/AA/YYYY",
                    helperText = """
                        📅 Siparişin teslim edildiği tarih.
                    """.trimIndent(),
                    icon = "calendar_today"
                )
            )

            add(
                FormField(
                    label = "Notlar",
                    key = "notlar",
                    type = FieldType.TEXTAREA,
                    isRequired = false,
                    placeholder = "Sipariş notları...",
                    helperText = """
                        📝 Siparişe özel notlar.
                    """.trimIndent(),
                    icon = "notes",
                    rows = 4
                )
            )
        }
    }
}