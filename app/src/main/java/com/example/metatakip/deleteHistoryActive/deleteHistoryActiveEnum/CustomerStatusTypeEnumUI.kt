package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum



enum class CustomerStatusTypeEnumUI {
    DELETED,                     // Müşteri silinmiş
    ACTIVE,                      // Aktif müşteri
    ACTIVE_WITH_DELETED_ORDERS,  // Aktif ama silinmiş siparişleri var
    INACTIVE                     // Pasif müşteri (uzun süredir aktif değil)
}