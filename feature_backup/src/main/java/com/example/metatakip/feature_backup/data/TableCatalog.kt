package com.example.metatakip.feature_backup.data

import com.example.metatakip.feature_backup.data.ImportRowResult

/**
 * Veritabanı tablolarının hiyerarşisini ve doğrulama kurallarını yöneten
 * senkronizasyon trafik merkezidir.
 */
object TableCatalog {

    val ALL_TABLES = listOf(
        "user", "firma", "musteri", "siparis", "urun", "urun_tipi",
        "mesaj_sablon", "unvan", "personel", "etiket_sablon",
        "etiket_sablon_bilesen", "etiket_sayfa_ayar", "call_logs", "delete_log"
    )

    /**
     * 🗑️ SİLME SIRASI (Reverse Dependency):
     * Foreign Key hatalarını önlemek için önce bağımlı tablolar silinmelidir.
     */
    val DELETE_ORDER = listOf(
        "urun", "siparis", "musteri", "personel", "etiket_sayfa_ayar",
        "etiket_sablon_bilesen", "etiket_sablon", "call_logs", "delete_log",
        "mesaj_sablon", "urun_tipi", "unvan", "firma", "user"
    )

    /**
     * 🏗️ YÜKLEME SIRASI (Dependency Order):
     * Önce ana tablolar, sonra onlara bağlanan tablolar yüklenmelidir.
     */
    val INSERT_ORDER = listOf(
        "user", "firma", "unvan", "personel", "musteri", "siparis", "urun",
        "urun_tipi", "mesaj_sablon", "etiket_sablon", "etiket_sablon_bilesen",
        "etiket_sayfa_ayar", "call_logs", "delete_log"
    )

    /**
     * ✅ HİBRİT VALIDATION:
     * Gelen verinin veritabanına girmeye uygun olup olmadığını atlamadan denetler.
     */
    fun validateRow(table: String, row: Map<String, Any?>, rowIndex: Int): ImportRowResult {

        // 🌍 EVRENSEL HİBRİT KONTROL: UUID ve updatedAt tüm senkronize tablolar için şarttır.
        if (table in listOf("musteri", "siparis", "urun", "personel", "urun_tipi", "firma")) {
            val uuid = row["uuid"] as? String
            val updatedAt = row["updatedAt"]
            if (uuid.isNullOrBlank()) {
                return ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "$table: UUID eksik!")
            }
            if (updatedAt == null) {
                return ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "$table: updatedAt eksik!")
            }
        }

        return when (table) {
            "firma" -> {
                if ((row["firmaAdi"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "firmaAdi boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "musteri" -> {
                if ((row["adSoyad"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "adSoyad boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "siparis" -> {
                // Siparişin hem kendi UUID'si hem de müşteri bağlantısı olmalı
                if (row["musteri_uuid"] == null && row["musteriId"] == null)
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Sipariş bir müşteriye bağlı değil")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "urun" -> {
                // Ürünün adı ve bağlı olduğu sipariş kontrolü
                if ((row["ad"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Ürün adı boş")
                else if (row["siparis_uuid"] == null && row["siparisId"] == null)
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Ürün bir siparişe bağlı değil")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "urun_tipi" -> {
                if ((row["ad"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Ürün tipi adı boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "personel" -> {
                if ((row["adSoyad"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Personel adı boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "mesaj_sablon" -> {
                if ((row["baslik"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Şablon başlığı boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "unvan" -> {
                if ((row["ad"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Unvan adı boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "etiket_sablon" -> {
                if ((row["adi"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Etiket şablon adı boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "etiket_sablon_bilesen" -> {
                if (row["sablon_id"] == null || row["bilesen_id"] == null)
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Bileşen bağlantısı eksik")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "etiket_sayfa_ayar" -> {
                if (row["sablon_id"] == null)
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Sayfa ayarı şablona bağlı değil")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "call_logs" -> {
                if ((row["musteriTelefonu"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Arama kaydı telefon numarası eksik")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "delete_log" -> {
                if (row["entityId"] == null || (row["entityType"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Silme logu eksik veri içeriyor")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            "user" -> {
                if ((row["username"] as? String).isNullOrBlank())
                    ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Kullanıcı adı boş")
                else ImportRowResult(rowIndex, ImportRowResult.Status.OK, "Uygun")
            }
            else -> ImportRowResult(rowIndex, ImportRowResult.Status.ERROR, "Bilinmeyen Tablo: $table")
        }
    }
}