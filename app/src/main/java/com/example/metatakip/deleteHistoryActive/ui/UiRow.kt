package com.example.metatakip.deleteHistoryActive.ui

import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode

sealed class UiRow(val type: Int) {

    enum class BlockType {
        DELETED_CUSTOMERS,
        ACTIVE_CUSTOMERS_WITH_ISSUES
    }

    data class Header(
        val title: String,
        val count: Int,
        val blockType: BlockType,
        val isExpanded: Boolean
    ) : UiRow(TYPE_HEADER)

    data class Customer(
        val customer: CustomerNode
    ) : UiRow(TYPE_CUSTOMER)

    data class Order(
        val order: OrderNode,
        val isLast: Boolean = false
    ) : UiRow(TYPE_ORDER)

    data class Product(
        val product: ProductNode,
        val isLast: Boolean = false
    ) : UiRow(TYPE_PRODUCT)

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CUSTOMER = 1
        const val TYPE_ORDER = 2
        const val TYPE_PRODUCT = 3
    }
}