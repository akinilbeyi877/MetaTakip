package com.example.metatakip.feature_data.entityModel

import java.util.UUID

data class Customer(
    // 🏠 YEREL ANAHTAR (Ev içindeki numara)
    // Sadece bu cihazın veritabanında hızlı işlem yapmak için kullanılır.
    var id: Long = 0L,

    // 🌍 KÜRESEL BENZERSİZ KİMLİK (TC Kimlik Numarası gibi)
    // Cihazlar arası senkronizasyonda Ahmetleri birbirinden ayırmak için kullanılır.
    // Her yeni nesne oluşturulduğunda otomatik olarak eşsiz bir kod üretir.
    var uuid: String = UUID.randomUUID().toString(),

    // 👤 Temel bilgiler
    var adSoyad: String = "",
    var ceptel: String? = null,
    var ceptel2: String? = null,
    var bolge: String? = null,

    // 📍 Adres & Not
    var adres: String? = null,
    var musteriNotu: String? = null,

    // 🏢 Firma Bilgileri
    var firmaAdi: String? = null,
    var firmaid: Long? = null,

    // 🗑️ SOFT DELETE ALANLARI
    var isDeleted: Int = 0,
    var deletedAt: Long? = null,
    var deleteReason: String? = null,
    var deletedBy: Long? = null,

    // 📌 KONUM ALANLARI
    var latitude: Double? = null,
    var longitude: Double? = null,
    var locationTimestamp: Long? = null,
    var locationAddress: String? = null,

    // 📸 FOTOĞRAF ALANI
    var photoPath: String? = null,

    // ⏱️ SENKRONİZASYON ZAMAN DAMGASI
    // Çakışma durumunda "en son güncelleyen kazansın" mantığı için gerekli.
    var updatedAt: Long = System.currentTimeMillis()
)