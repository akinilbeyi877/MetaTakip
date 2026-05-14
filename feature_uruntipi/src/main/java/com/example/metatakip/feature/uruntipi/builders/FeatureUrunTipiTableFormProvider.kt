package com.example.metatakip.feature.uruntipi.builders

import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField

/**
 * 🏗️ FeatureUrunTipiTableFormProvider
 * GenericBuildFormForTable için ürün tipi form alanlarını sağlar
 * Table-specific form provider
 */
object FeatureUrunTipiTableFormProvider {

    /**
     * Bu provider hangi tabloları destekler?
     */
    fun canHandle(table: String): Boolean {
        return table.equals("urun_tipi", ignoreCase = true) ||
                table.equals("urun tipi", ignoreCase = true) ||
                table.equals("product_type", ignoreCase = true)
    }

    /**
     * 📝 Ürün tipi form alanlarını getir
     */
    fun getFormFields(): List<FormField> {
        return mutableListOf<FormField>().apply {
            // 1. AD - Kullanıcıya Görünen Ad (TEK ANA ALAN)
            add(
                FormField(
                    label = "Ürün Tipi Adı",
                    key = "ad",
                    type = FieldType.TEXT,
                    isRequired = true,
                    placeholder = "Halı, Koltuk, Perde, Minder, Yorgan",
                    maxLength = 50,
                    helperText = """
                        👤 Müşterilere görünecek isim:
                        • Sipariş formlarında gösterilir
                        • Faturalarda yazar
                        • Müşteri mesajlarında kullanılır
                        
                        📝 Örnekler:
                        - "Yün Halı"
                        - "Deri Koltuk"  
                        - "Tül Perde"
                        - "Pamuk Yorgan"
                    """.trimIndent(),
                    icon = "category"
                )
            )

            // 2. Birim Fiyat - Hesaplama Birim Fiyatı
            add(
                FormField(
                    label = "Birim Fiyat (₺)",
                    key = "birimFiyat",
                    type = FieldType.NUMBER,
                    value = "0.0",
                    isRequired = true,
                    placeholder = "150.00, 250.50, 75.25 gibi",
                    helperText = """
                        💰 Varsayılan birim fiyatı:
                        • Siparişlerde otomatik gelecek
                        • Hesaplamalarda kullanılacak
                        • İsterseniz her siparişte değiştirilebilir
                        
                        ⚠️ Dikkat: Negatif olamaz!
                        Sıfır (0) girerseniz fiyat manuel girilecek
                    """.trimIndent(),
                    icon = "money",
                    minValue = 0.0,
                    step = 0.01
                )
            )

            // 3. Hesap Tipi - Hesaplama Birimi
            add(
                FormField(
                    label = "Hesaplama Birimi",
                    key = "hesapTipi",
                    type = FieldType.DROPDOWN,
                    value = "M2",
                    options = listOf("M2", "ADET", "METRE", "KG", "LİTRE", "PAKET"),
                    isRequired = true,
                    helperText = """
                        📐 Bu ürün nasıl hesaplanacak?
                        
                        M2 → Metrekare (Halı, Kilim için)
                        ADET → Tane (Koltuk, Minder için)  
                        METRE → Uzunluk (Perde, Kumaş için)
                        KG → Kilo (Yorgan, Battaniye için)
                        LİTRE → Sıvı ölçüsü (Şampuan, Deterjan için)
                        PAKET → Paket (Temizlik seti için)
                        
                        💡 Siparişte miktar girilirken bu birim kullanılacak
                    """.trimIndent(),
                    icon = "calculate"
                )
            )

            // 4. Durum - Aktif/Pasif
            add(
                FormField(
                    label = "Sistem Durumu",
                    key = "aktif",
                    type = FieldType.DROPDOWN,
                    value = "1",
                    optionMap = mapOf("Aktif" to "1", "Pasif" to "0"),
                    helperText = """
                        ⚡ Bu ürün tipini aktif/pasif yap:
                        
                        ✅ AKTİF (Açık):
                        • Yeni siparişlerde seçilebilir
                        • Listelerde görünür
                        • Dropdown'larda yer alır
                        
                        ❌ PASİF (Kapalı):
                        • Yeni siparişlerde GÖRÜNMEZ
                        • Eski siparişlerde kalır
                        • Geçici olarak kaldırmak için
                        
                        🗑️ Silmek yerine pasif yapın!
                    """.trimIndent(),
                    icon = "power"
                )
            )

            // 5. Açıklama - Ek Notlar
            add(
                FormField(
                    label = "Ek Açıklamalar",
                    key = "aciklama",
                    type = FieldType.TEXTAREA,
                    placeholder = "Özel notlar, detaylar, kurallar...",
                    helperText = """
                        📝 İsteğe bağlı ek bilgiler:
                        
                        • Özel yıkama talimatları
                        • Malzeme detayları  
                        • Ölçü sınırlamaları
                        • Renk seçenekleri
                        • Teslimat notları
                        • İndirim koşulları
                        
                        💬 Bu alan sadece iç not içindir, 
                        müşterilere gösterilmez.
                    """.trimIndent(),
                    icon = "description",
                    rows = 4
                )
            )
        }
    }
}