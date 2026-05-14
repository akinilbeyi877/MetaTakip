package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel

import com.example.metatakip.feature_data.entityModel.Customer

/**
 * 🔹 DB → Builder arası SADE DTO
 * 🔹 UI bilgisi içermez
 * 🔹 isDeleted bilgisi repository tarafından verilir
 */
data class DbCustomerDto(
    val id: Long,
    val name: String,
    val isDeleted: Boolean
) {
    companion object {
        fun from(customer: Customer): DbCustomerDto {
            return DbCustomerDto(
                id = customer.id,
                name = customer.adSoyad,
                isDeleted = customer.isDeleted == 1   // 🔥 KRİTİK SATIR
            )
        }
    }
}

