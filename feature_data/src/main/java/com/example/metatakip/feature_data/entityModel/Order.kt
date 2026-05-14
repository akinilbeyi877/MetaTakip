package com.example.metatakip.feature_data.entityModel

import java.util.UUID

data class Order(
    // 🏠 Yerel Anahtar
    var id: Long = 0L,

    // 🌍 KÜRESEL KİMLİK: Cihazlar arası çakışmayı önler
    var uuid: String = UUID.randomUUID().toString(),

    // 🔗 ZORUNLU İLİŞKİLER (Sayısal ID'ler yerel SQLite JOIN işlemleri içindir)
    var musteriId: Long = 0L,
    var firmaId: Long = 0L,

    // 🔗 HİBRİT BAĞ: Senkronizasyon sırasında bu UUID kullanılacak
    var musteriUuid: String = "",

    var musteriAdi: String = "",
    var musteriTelefon: String = "",
    var firmaAdi: String = "",

    var urunTipi: String = "",
    var en: Double = 0.0,
    var boy: Double = 0.0,
    var metrekare: Double = 0.0,
    var ucret: Double = 0.0,

    // İndirim ve Ek Ücret — kaydet/yükle için DB'ye yazılır
    var indirim: Double = 0.0,
    var ekUcret: Double = 0.0,
    var firmaUuid: String = "",

    var duzenlemeTarihi: String = "",

    var siparisNoSeq: Long = 0L,
    var teslimAlmaTarihi: String = "",
    var teslimTarihi: String = "",
    var notlar: String = "",
    var durum: String = "Hazırlanıyor",
    var yetkili: String = "",
    var etiketSablonId: Long? = null,

    // 🗑️ SOFT DELETE
    var isDeleted: Int = 0,
    var deletedAt: Long? = null,
    var deleteReason: String? = null,
    var deletedBy: Long? = null,

    // ⏱️ SENKRONİZASYON ZAMAN DAMGASI
    var updatedAt: Long = System.currentTimeMillis(),

    // ⏱️ OLUŞTURULMA ZAMAN DAMGASI (saniye cinsinden DB'den gelir, milisaniyeye çevrilir)
    var createdAt: Long = 0L,

    // =============================================================
    // 🆕 YENİ ALANLAR (Çoklu Kullanıcı / Çoklu Cihaz Yönetimi)
    // =============================================================

    // 👁️ GÖRÜLDÜ DURUMU
    var isSeen: Int = 0,                    // 0 = Görülmedi, 1 = Görüldü
    var seenBy: String = "",               // Kimin gördüğü (deviceId)
    var seenAt: Long = 0L,                 // Görülme zamanı
    var seenDeviceName: String = "",       // Gören cihazın adı

    // 🔒 KİLİT DURUMU (birisi işlem yapıyorsa)
    var isLocked: Int = 0,                 // 0 = Kilit yok, 1 = Kilitli
    var lockedBy: String = "",             // Kilitleyen cihaz (deviceId)
    var lockedAt: Long = 0L,               // Kilitlenme zamanı
    var lockedDeviceName: String = "",     // Kilitleyen cihazın adı
    var lockedForMinutes: Int = 15,        // Kaç dakika kilitli kalacağı (varsayılan 15 dk)

    // 📸 FOTOĞRAF ALANI
    var photoPath: String? = null,

    // 📍 MÜŞTERİ ADRESİ (musteri tablosundan JOIN ile gelir, siparis tablosunda yok)
    var adres: String = "",

    // 🆕 HİBRİT ALAN: Sipariş listesinde görünmesi için (DB'de siparis tablosunda yok, JOIN/Subquery ile gelir)
    var toplamAdet: Int = 0
)
