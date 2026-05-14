package com.example.metatakip.feature_data.entityModel

// 🧱--------------------------------------------------------------
// ENUM: FieldType
// --------------------------------------------------------------
enum class FieldType {
    TEXT,           // 📝 Normal metin alanı
    NUMBER,         // 🔢 Sayısal değer
    DATE,           // 📅 Takvimden tarih seçimi
    DROPDOWN,       // 🔽 Açılır liste
    CHECKBOX,       // ☑ Onay kutusu
    IMAGE,          // 🖼 Görsel ekleme
    SWITCH,         // 🔘 Aç/Kapa kontrolü
    TEXTAREA,       // 🧾 Çok satırlı metin
    PHONE,          // 📞 Telefon girişi
    EMAIL,          // 📧 E-posta adresi
    SIGNATURE,      // ✍️ İmza çizim alanı
    BARCODE,        // 🏷 Barkod okuma
    LOCATION,       // 📍 Konum seçimi
    MULTI_SELECT,   // 🆕 Çoklu seçim
    AUTOCOMPLETE,   // 🆕 Otomatik tamamlama
    TIME,           // 🆕 Saat seçimi
    RATING,         // 🆕 Yıldız değerlendirme
    FILE,           // 🆕 Dosya yükleme
    COLOR,          // 🆕 Renk seçici
    RANGE,          // 🆕 Aralık seçici (slider)
    HIDDEN,         // 🆕 Gizli alan (veri taşımak için)
    PASSWORD        // 🆕 Şifre alanı
}

// 🧩--------------------------------------------------------------
// DATA CLASS: FormField
// --------------------------------------------------------------
data class FormField(
    // 🏷️ Temel bilgiler
    val label: String,                 // Ekranda gösterilecek isim
    val key: String,                   // DB alan anahtarı
    val type: FieldType,               // Alan türü

    // ✍️ Değerler
    var value: String = "",            // Ana değer
    val defaultValue: String? = null,  // 🆕 Varsayılan değer
    var options: List<String>? = null, // 📜 Dropdown için (label = value)
    var optionMap: Map<String, String>? = null, // 🗺️ Label → Value mapping

    // ⚙️ Validasyon ve kısıtlar
    val isRequired: Boolean = false,   // Zorunlu alan mı?
    val minLength: Int? = null,        // 🆕 Minimum uzunluk
    val maxLength: Int? = null,        // 🆕 Maximum uzunluk
    val minValue: Double? = null,      // 🆕 Minimum değer (NUMBER için)
    val maxValue: Double? = null,      // 🆕 Maximum değer (NUMBER için)
    val pattern: String? = null,       // 🆕 Regex pattern (örn: telefon formatı)

    // 🎨 Görünüm ve davranış
    var isEditMode: Boolean = false,   // Düzenleme modu
    val placeholder: String? = null,   // 🆕 Placeholder metni
    val helperText: String? = null,    // 🆕 Yardımcı açıklama
    val icon: String? = null,          // 🆕 İkon adı (örn: "person", "phone")
    val order: Int = 0,                // 🆕 Sıralama için
    val group: String? = null,         // 🆕 Grup adı (field'ları gruplamak için)
    val hidden: Boolean = false,       // 🆕 Gizli alan
    val readonly: Boolean = false,     // 🆕 Salt okunur
    val rows: Int = 3,                 // 🆕 TEXTAREA için satır sayısı
    val step: Double? = null,          // 🆕 NUMBER için artış miktarı
    val multiline: Boolean = false,    // 🆕 TEXT için çok satırlı mı?

    // 🔗 İlişkisel
    val dependsOn: String? = null,     // 🆕 Bağımlı olduğu field (conditional logic)
    val dependsValue: String? = null,  // 🆕 Bağımlılık değeri
    val relatedTable: String? = null,  // 🆕 İlişkili tablo adı
    val relatedField: String? = null,  // 🆕 İlişkili alan adı

    // 💾 Veritabanı
    val dbColumnName: String? = null,  // 🆕 Veritabanı kolon adı (key'den farklıysa)
    val dbType: String? = null,        // 🆕 Veritabanı türü (INTEGER, TEXT, REAL)
    val isPrimaryKey: Boolean = false, // 🆕 Birincil anahtar mı?
    val isAutoIncrement: Boolean = false, // 🆕 Otomatik artan mı?

    // 🔍 Filtreleme ve Arama
    val searchable: Boolean = false,   // 🆕 Arama yapılabilir mi?
    val filterable: Boolean = false,   // 🆕 Filtrelenebilir mi?
    val sortable: Boolean = false      // 🆕 Sıralanabilir mi?
) {
    // 🎯 Yardımcı metodlar

    /** 🆕 Dropdown seçeneklerini label listesi olarak getir */
    fun getDropdownLabels(): List<String> {
        return when {
            optionMap != null -> optionMap!!.keys.toList()
            options != null -> options!!
            else -> emptyList()
        }
    }

    /** 🆕 Verilen label'a karşılık gelen value'yu getir */
    fun getValueForLabel(label: String): String {
        return when {
            optionMap != null -> optionMap!![label] ?: label
            else -> label
        }
    }

    /** 🆕 Verilen value'ya karşılık gelen label'ı getir */
    fun getLabelForValue(value: String): String {
        return when {
            optionMap != null -> optionMap!!.entries
                .find { it.value == value }?.key ?: value
            else -> value
        }
    }

    /** 🆕 Alanın geçerli olup olmadığını kontrol et */
    fun validate(): Pair<Boolean, String?> {
        // Hidden alanlar için validasyon yapma
        if (hidden || type == FieldType.HIDDEN) {
            return Pair(true, null)
        }

        return when {
            isRequired && value.trim().isEmpty() ->
                Pair(false, "'$label' alanı zorunludur")

            minLength != null && value.length < minLength ->
                Pair(false, "'$label' en az $minLength karakter olmalı")

            maxLength != null && value.length > maxLength ->
                Pair(false, "'$label' en fazla $maxLength karakter olabilir")

            pattern != null && !Regex(pattern).matches(value) ->
                Pair(false, "'$label' geçerli bir formatta değil")

            type == FieldType.EMAIL && !isValidEmail(value) ->
                Pair(false, "'$label' geçerli bir e-posta adresi değil")

            type == FieldType.PHONE && !isValidPhone(value) ->
                Pair(false, "'$label' geçerli bir telefon numarası değil")

            type == FieldType.NUMBER -> {
                try {
                    val numValue = value.toDouble()
                    if (minValue != null && numValue < minValue) {
                        Pair(false, "'$label' en az $minValue olmalı")
                    } else if (maxValue != null && numValue > maxValue) {
                        Pair(false, "'$label' en fazla $maxValue olabilir")
                    } else {
                        Pair(true, null)
                    }
                } catch (e: NumberFormatException) {
                    Pair(false, "'$label' geçerli bir sayı değil")
                }
            }

            else -> Pair(true, null)
        }
    }

    /** 🆕 Varsayılan değeri ayarla (boşsa) */
    fun applyDefaultIfEmpty() {
        if (value.isEmpty() && defaultValue != null) {
            value = defaultValue
        }
    }

    /** 🆕 Alanın görünür olup olmadığını kontrol et */
    fun isVisible(): Boolean {
        return !hidden && type != FieldType.HIDDEN
    }

    /** 🆕 Alanın değerini temizle */
    fun clear() {
        value = ""
    }

    /** 🆕 Alanın değerini güvenli şekilde ayarla */
    fun setSafeValue(newValue: Any?) {
        value = when (newValue) {
            null -> ""
            is String -> newValue
            is Number -> newValue.toString()
            is Boolean -> if (newValue) "true" else "false"
            else -> newValue.toString()
        }
    }

    /** 🆕 Checkbox için boolean değer getir */
    fun getBooleanValue(): Boolean {
        return when (value.lowercase()) {
            "true", "1", "evet", "yes" -> true
            else -> false
        }
    }

    /** 🆕 Number için numeric değer getir */
    fun getNumericValue(): Double? {
        return try {
            value.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    // 🛠️ Private yardımcı metodlar
    private fun isValidEmail(email: String): Boolean {
        if (email.isEmpty()) return true // Boşsa geçerli (zorunlu değilse)
        val pattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return pattern.matches(email)
    }

    private fun isValidPhone(phone: String): Boolean {
        if (phone.isEmpty()) return true // Boşsa geçerli (zorunlu değilse)
        // Türkiye telefon numarası formatı: 05xx xxx xx xx veya +905xx xxx xx xx
        val pattern = Regex("^(\\+90|0)?5[0-9]{9}$")
        return pattern.matches(phone.replace("\\s".toRegex(), ""))
    }

    companion object {
        /** 🆕 Basit text field oluşturma factory */
        fun textField(
            label: String,
            key: String,
            isRequired: Boolean = false,
            placeholder: String? = null,
            multiline: Boolean = false
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.TEXT,
            isRequired = isRequired,
            placeholder = placeholder,
            multiline = multiline
        )

        /** 🆕 Number field oluşturma factory */
        fun numberField(
            label: String,
            key: String,
            isRequired: Boolean = false,
            minValue: Double? = null,
            maxValue: Double? = null,
            step: Double? = null
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.NUMBER,
            isRequired = isRequired,
            minValue = minValue,
            maxValue = maxValue,
            step = step
        )

        /** 🆕 Dropdown field oluşturma factory */
        fun dropdownField(
            label: String,
            key: String,
            options: List<String>? = null, // Nullable yapıldı
            isRequired: Boolean = false,
            optionMap: Map<String, String>? = null
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.DROPDOWN,
            options = options,
            optionMap = optionMap,
            isRequired = isRequired
        )

        /** 🆕 Checkbox field oluşturma factory */
        fun checkboxField(
            label: String,
            key: String,
            defaultValue: Boolean = false
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.CHECKBOX,
            value = if (defaultValue) "true" else "false",
            defaultValue = if (defaultValue) "true" else "false"
        )

        /** 🆕 Hidden field oluşturma factory */
        fun hiddenField(
            key: String,
            value: String = ""
        ) = FormField(
            label = "",
            key = key,
            type = FieldType.HIDDEN,
            value = value,
            hidden = true
        )

        /** 🆕 Textarea field oluşturma factory */
        fun textareaField(
            label: String,
            key: String,
            isRequired: Boolean = false,
            rows: Int = 4,
            placeholder: String? = null
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.TEXTAREA,
            isRequired = isRequired,
            rows = rows,
            placeholder = placeholder
        )

        /** 🆕 Phone field oluşturma factory */
        fun phoneField(
            label: String,
            key: String,
            isRequired: Boolean = false,
            placeholder: String = "05xx xxx xx xx"
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.PHONE,
            isRequired = isRequired,
            placeholder = placeholder
        )

        /** 🆕 Email field oluşturma factory */
        fun emailField(
            label: String,
            key: String,
            isRequired: Boolean = false,
            placeholder: String = "ornek@email.com"
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.EMAIL,
            isRequired = isRequired,
            placeholder = placeholder
        )

        /** 🆕 Date field oluşturma factory */
        fun dateField(
            label: String,
            key: String,
            isRequired: Boolean = false
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.DATE,
            isRequired = isRequired
        )

        /** 🆕 Switch field oluşturma factory */
        fun switchField(
            label: String,
            key: String,
            defaultValue: Boolean = false
        ) = FormField(
            label = label,
            key = key,
            type = FieldType.SWITCH,
            value = if (defaultValue) "true" else "false",
            defaultValue = if (defaultValue) "true" else "false"
        )

        /** 🆕 Müşteri için örnek form alanları */
        fun getCustomerFormFields(): List<FormField> {
            return listOf(
                textField(
                    label = "Ad Soyad",
                    key = "adSoyad",
                    isRequired = true
                ),
                phoneField(
                    label = "Cep Telefonu",
                    key = "ceptel"
                ),
                dropdownField(
                    label = "Firma",
                    key = "firmaid",
                    optionMap = mapOf(
                        "Firma A" to "1",
                        "Firma B" to "2",
                        "Firma C" to "3"
                    )
                ),
                dropdownField(
                    label = "Bölge",
                    key = "bolge",
                    options = listOf("İstanbul", "Ankara", "İzmir", "Bursa", "Antalya")
                ),
                textareaField(
                    label = "Adres",
                    key = "adres",
                    rows = 4
                ),
                textareaField(
                    label = "Notlar",
                    key = "notlar"
                ),
                checkboxField(
                    label = "Aktif Müşteri",
                    key = "aktif",
                    defaultValue = true
                )
            )
        }

        /** 🆕 Sipariş için örnek form alanları */
        fun getOrderFormFields(): List<FormField> {
            return listOf(
                textField(
                    label = "Sipariş No",
                    key = "siparisNo",
                    isRequired = true
                ),
                dateField(
                    label = "Sipariş Tarihi",
                    key = "siparisTarihi",
                    isRequired = true
                ),
                dropdownField(
                    label = "Durum",
                    key = "durum",
                    options = listOf(
                        "Yeni",
                        "İşleniyor",
                        "Tamamlandı",
                        "İptal Edildi"
                    )
                ),
                numberField(
                    label = "Toplam Tutar",
                    key = "toplamTutar",
                    minValue = 0.0
                ),
                textareaField(
                    label = "Açıklama",
                    key = "aciklama"
                )
            )
        }

        /** 🆕 Mesaj Şablonu için örnek form alanları */
        fun getMesajSablonFormFields(): List<FormField> {
            return listOf(
                textField(
                    label = "Başlık",
                    key = "baslik",
                    isRequired = true
                ),
                dropdownField(
                    label = "Firma",
                    key = "firmaid",
                    options = emptyList() // Options nullable olduğu için boş liste gönderilebilir
                ),
                hiddenField(
                    key = "firma_adi"
                ),
                textareaField(
                    label = "Müşteri Oluşturuldu Mesajı",
                    key = "musteri_olustu_mesaj",
                    rows = 5
                ),
                textareaField(
                    label = "Müşteri Güncellendi Mesajı",
                    key = "musteri_guncellendi_mesaj",
                    rows = 5
                ),
                textareaField(
                    label = "Sipariş Oluşturuldu Mesajı",
                    key = "siparis_olustu_mesaj",
                    rows = 5
                ),
                textareaField(
                    label = "Siparişe Ürün Eklendi Mesajı",
                    key = "siparis_urun_eklendi_mesaj",
                    rows = 5
                ),
                textareaField(
                    label = "SMS Onay Mesajı",
                    key = "sms_onay_mesaj",
                    rows = 3
                ),
                textareaField(
                    label = "WhatsApp Onay Mesajı",
                    key = "whatsapp_onay_mesaj",
                    rows = 3
                ),
                checkboxField(
                    label = "Varsayılan Şablon",
                    key = "varsayilan"
                )
            )
        }
    }
}