package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum

// 🧾 Ürün durumunu temsil eder
// Renk, ikon ve restore butonu kararları buradan türetilir
// UI bu enum'u OKUR, YORUMLAMAZ

enum class ProductStatusTypeEnumUI {
    DELETED,    // Silinmiş
    ACTIVE,     // Aktif
    INACTIVE,   // Pasif
    PENDING,    // Beklemede
    DELIVERED   // Teslim Edildi
}
