package com.example.metatakip.feature.customer.providers

import dao.MetaTakipFirmaDao
import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField

/**
 * 🏗️ FeatureCustomerTableFormProvider
 * GenericBuildFormForTable için müşteri (musteri) form alanlarını sağlar
 */
object FeatureCustomerTableFormProvider {

    fun canHandle(table: String): Boolean {
        return table.equals("musteri", ignoreCase = true) ||
                table.equals("customer", ignoreCase = true) ||
                table.equals("customers", ignoreCase = true)
    }

    /**
     * Firma dropdown'u DB'den geldiği için firmaDao gerekiyor.
     *
     * ⚠️ Not:
     * - Firma adı aynı olan kayıtlar varsa optionMap key çakışabilir.
     *   Bu yüzden boş/aynı ad durumlarında id ile benzersizleştiriyoruz.
     */
    fun getFormFields(
        firmaDao: MetaTakipFirmaDao
    ): List<FormField> {

        val firmaList = runCatching { firmaDao.getAllFirmalar() }
            .getOrDefault(emptyList())
            .sortedBy { it.id } // ✅ deterministik sıralama

        // ✅ Key çakışmasını azaltmak için:
        // - firmaAdi boşsa "Firma #ID"
        // - aynı firmaAdi tekrar ediyorsa "FirmaAdi (#ID)" yap
        val usedNames = mutableSetOf<String>()
        val firmaOptionMap = linkedMapOf<String, String>()

        firmaList.forEach { f ->
            val rawName = (f.firmaAdi ?: "").trim()
            var name = if (rawName.isBlank()) "Firma #${f.id}" else rawName

            // aynı isim tekrar ederse id ile benzersizleştir
            if (usedNames.contains(name)) {
                name = "$name (#${f.id})"
            }

            usedNames.add(name)
            firmaOptionMap[name] = f.id.toString()
        }

        val firmaMapSafe: Map<String, String> = if (firmaOptionMap.isEmpty()) {
            mapOf("Firma bulunamadı" to "0")
        } else firmaOptionMap

        val defaultFirmaValue = firmaList.firstOrNull()?.id?.toString()
            ?: firmaMapSafe.values.firstOrNull()
            ?: "0"

        return mutableListOf<FormField>().apply {

            // 🏢 Firma
            add(
                FormField(
                    label = "Firma",
                    key = "firmaid",
                    type = FieldType.DROPDOWN,
                    optionMap = firmaMapSafe,
                    value = defaultFirmaValue,
                    isRequired = true,
                    placeholder = "Firma seçiniz",
                    helperText = """
                        🏢 Müşteri mutlaka bir firmaya bağlı olmalıdır.
                    """.trimIndent(),
                    icon = "business"
                )
            )

            // 👤 Ad Soyad
            add(
                FormField(
                    label = "Ad Soyad",
                    key = "adSoyad",
                    type = FieldType.TEXT,
                    isRequired = true,
                    placeholder = "Ad Soyad",
                    helperText = """
                        👤 Müşteri adı zorunludur.
                    """.trimIndent(),
                    icon = "person"
                )
            )

            // 📞 Telefon
            add(
                FormField(
                    label = "Telefon",
                    key = "ceptel",
                    type = FieldType.PHONE,
                    isRequired = true,
                    placeholder = "05xx xxx xx xx",
                    helperText = """
                        📞 Telefon numarası zorunludur (en az 10 hane).
                    """.trimIndent(),
                    icon = "phone"
                )
            )

            // 📞 Telefon 2
            add(
                FormField(
                    label = "Telefon 2",
                    key = "ceptel2",
                    type = FieldType.PHONE,
                    isRequired = false,
                    placeholder = "Opsiyonel",
                    helperText = """
                        📞 İkinci telefon opsiyoneldir.
                    """.trimIndent(),
                    icon = "phone"
                )
            )

            // 📍 Bölge
            add(
                FormField(
                    label = "Bölge",
                    key = "bolge",
                    type = FieldType.DROPDOWN,
                    isRequired = false,
                    value = "Bursa",
                    options = listOf("Bursa", "Nilüfer", "Osmangazi", "Yıldırım", "Diğer"),
                    helperText = """
                        📍 Müşterinin bulunduğu bölge.
                    """.trimIndent(),
                    icon = "location_on"
                )
            )

            // 🏠 Adres
            add(
                FormField(
                    label = "Adres",
                    key = "adres",
                    type = FieldType.TEXTAREA,
                    isRequired = false,
                    placeholder = "Adres",
                    helperText = """
                        🏠 Müşteri adresi.
                    """.trimIndent(),
                    icon = "home",
                    rows = 3
                )
            )

            // 📝 Not
            add(
                FormField(
                    label = "Not (Şahsi)",
                    key = "musteriNotu",
                    type = FieldType.TEXTAREA,
                    isRequired = false,
                    placeholder = "Not",
                    helperText = """
                        📝 Müşteri ile ilgili özel notlar.
                    """.trimIndent(),
                    icon = "notes",
                    rows = 4
                )
            )
        }
    }
}