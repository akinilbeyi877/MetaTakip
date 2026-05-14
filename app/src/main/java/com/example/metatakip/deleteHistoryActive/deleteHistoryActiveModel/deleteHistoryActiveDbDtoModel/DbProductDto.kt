package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel

import com.example.metatakip.feature_data.entityModel.Urun

/**
 * 🔹 DB → Builder arası saf DTO
 * 🔹 UI / Enum içermez
 * 🔹 isDeleted repository tarafından verilir
 */
data class DbProductDto(
    val id: Long,
    val orderId: Long,
    val name: String,
    val isDeleted: Boolean
) {
    companion object {
        fun from(urun: Urun): DbProductDto {
            return DbProductDto(
                id = urun.id,
                orderId = urun.siparisId,
                name = urun.ad,
                isDeleted = urun.isDeleted == 1   // 🔥 DB’den okur
            )
        }
    }
}


