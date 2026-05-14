package com.example.metatakip.deleteHistoryActive.restore

import android.content.Context
import android.util.Log
import dao.MetaTakipCustomerDao
import dao.MetaTakipUrunDao
import com.example.metatakip.deleteHistoryActive.data.DeleteHistoryQueryRepository
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.DeleteHistoryResult
import com.example.metatakip.deleteHistoryActive.deleteHistoryBuilderFunc.DeleteHistoryBuilder
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel.DbCustomerDto
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel.DbOrderDto
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel.DbProductDto
import com.example.metatakip.feature.order.data.OrderDaoImpl

class DeleteHistoryQueryRepositoryImpl(
    context: Context
) : DeleteHistoryQueryRepository {

    private val customerDao = MetaTakipCustomerDao(context)
    private val orderDao = OrderDaoImpl(context)
    private val productDao = MetaTakipUrunDao(context)

    override fun getDeleteHistory(): DeleteHistoryResult {

        Log.e("DELETE_HISTORY_DEBUG", "📥 getDeleteHistory CALLED")

        // =====================================================
        // 🔥 1️⃣ ALL DATA (ACTIVE + DELETED)
        // =====================================================

        val customersRaw =
            customerDao.getAllCustomersIncludingDeleted()

        val ordersRaw =
            orderDao.getAllSiparisIncludingDeleted()

        val productsRaw =
            productDao.getAllUrunlerIncludingDeleted()

        Log.e(
            "DELETE_HISTORY_DEBUG",
            "📊 RAW customers=${customersRaw.size}, orders=${ordersRaw.size}, products=${productsRaw.size}"
        )

        // =====================================================
        // 🔁 2️⃣ DB → DTO
        // (isDeleted info is read inside DTO)
        // =====================================================

        val customers: List<DbCustomerDto> =
            customersRaw.map {
                DbCustomerDto.from(it)
            }

        val orders: List<DbOrderDto> =
            ordersRaw.map {
                DbOrderDto.from(it)
            }

        val products: List<DbProductDto> =
            productsRaw.map {
                DbProductDto.from(it)
            }

        // =====================================================
        // 🧠 3️⃣ BUILDER
        // =====================================================

        val result =
            DeleteHistoryBuilder().build(
                customers = customers,
                orders = orders,
                products = products
            )

        Log.e(
            "DELETE_HISTORY_DEBUG",
            "✅ RESULT deleted=${result.deletedCustomers.size}, active=${result.activeCustomers.size}"
        )

        return result
    }
}
