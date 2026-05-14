package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel

import android.util.Log
import com.example.metatakip.feature_data.entityModel.Order

data class DbOrderDto(
    val id: Long,
    val customerId: Long,
    val orderNumber: String?,      // "SIP-123"
    val orderStatus: String?,      // "Teslim Alındı"
    val isDeleted: Boolean
) {
    companion object {
        fun from(siparis: Order): DbOrderDto {
            // 🔴 DEBUG LOG
            Log.e("DB_ORDER_DTO_DEBUG",
                "Siparis ID: ${siparis.id}, Durum: '${siparis.durum}'")

            return DbOrderDto(
                id = siparis.id,
                customerId = siparis.musteriId,
                // ❌ ESKİ: orderNumber = siparis.durum,
                // ✅ YENİ: ID'den sipariş numarası oluştur
                orderNumber = "SIP-${siparis.id}",
                // Durum bilgisini ayrı alanda sakla
                orderStatus = siparis.durum,
                isDeleted = siparis.isDeleted == 1
            )
        }
    }
}