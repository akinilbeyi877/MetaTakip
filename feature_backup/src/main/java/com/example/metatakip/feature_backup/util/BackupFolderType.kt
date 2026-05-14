package com.example.metatakip.feature_backup.util

/**
 * 📂 Yedekleme Klasör Türleri
 * Dosyaların Drive ve Yerel Depolamadaki konumlarını ve kategorilerini belirler.
 */
enum class BackupFolderType(val folderName: String) {
    /** Tüm veritabanının (.db) tam kopyası. Genelde manuel yedeklerde kullanılır. */
    FULL("full"),

    /** Belirli aralıklarla alınan, sadece son değişiklikleri içeren yedekler. */
    PARTIAL("partial"),

    /** 🛰️ Hibrit Canlı Senkronizasyon: Her veri girişinde anlık oluşturulan paketler. */
    INSTANT("instant"),

    /** Excel/CSV dışa aktarma ve içe aktarma dosyaları için kullanılan klasör. */
    CSV("csv")
}