package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.CustomerStatusTypeEnumUI

data class CustomerNode(
    val customerId: String,
    val customerName: String,
    val isDeleted: Boolean = false,
    val status: CustomerStatusTypeEnumUI = CustomerStatusTypeEnumUI.ACTIVE,
    val statusLabel: String = "Aktif Müşteri",
    val canRestore: Boolean = false,
    val orders: List<OrderNode> = emptyList(),

    // UI özellikleri
    @DrawableRes
    val iconResId: Int = R.drawable.ic_customer_24,
    val colorResId: Int = R.color.status_active,
    val itemCount: String = "0 sipariş",

    // Ek bilgiler
    val email: String? = null,
    val phone: String? = null,
    val registrationDate: String? = null,
    val lastOrderDate: String? = null,
    val totalSpent: String? = null,

    // UI state
    var isExpanded: Boolean = false
) {

    fun getBadgeText(): String {
        return when {
            isDeleted -> "SİLİNMİŞ MÜŞTERİ"
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS -> "SİLİNMİŞ SİPARİŞLER"
            status == CustomerStatusTypeEnumUI.INACTIVE -> "PASİF"
            else -> "AKTİF"
        }
    }

    fun getBadgeResId(): Int {
        return when {
            isDeleted -> R.drawable.badge_deleted
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS -> R.drawable.badge_warning
            status == CustomerStatusTypeEnumUI.INACTIVE -> R.drawable.badge_inactive
            else -> R.drawable.badge_active
        }
    }

    fun getIconColorResId(): Int {
        return when {
            isDeleted -> R.color.icon_warning
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS -> R.color.icon_warning
            status == CustomerStatusTypeEnumUI.INACTIVE -> R.color.status_inactive
            else -> R.color.icon_success
        }
    }

    fun getDetailedItemCount(): String {
        if (orders.isEmpty()) return itemCount

        val activeOrders = orders.count { !it.isDeleted }
        val deletedOrders = orders.count { it.isDeleted }

        return when {
            activeOrders > 0 && deletedOrders > 0 ->
                "$activeOrders aktif, $deletedOrders silinmiş sipariş"
            deletedOrders > 0 ->
                "$deletedOrders silinmiş sipariş"
            else ->
                "$activeOrders sipariş"
        }
    }

    fun getDeletedOrderCount(): Int =
        orders.count { it.isDeleted }

    fun getActiveOrderCount(): Int =
        orders.count { !it.isDeleted }

    fun getCustomerSummary(): String {
        val statusText = if (isDeleted) "Silinmiş" else "Aktif"
        val orderText = "${orders.size} sipariş"
        val lastOrderText = lastOrderDate?.let { ", Son sipariş: $it" } ?: ""
        return "$statusText müşteri • $orderText$lastOrderText"
    }

    fun getContactInfo(): String {
        val emailText = email?.let { "✉️ $it" } ?: ""
        val phoneText = phone?.let { "📱 $it" } ?: ""

        return when {
            emailText.isNotEmpty() && phoneText.isNotEmpty() ->
                "$emailText • $phoneText"
            else ->
                emailText + phoneText
        }
    }

    fun getRestoreButtonText(): String {
        return when {
            isDeleted -> "MÜŞTERİYİ GERİ YÜKLE"
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS ->
                "SİLİNEN SİPARİŞLERİ GÖRÜNTÜLE"
            else -> "MÜŞTERİ DETAYLARI"
        }
    }

    fun getStatusDescription(): String {
        return when {
            isDeleted -> "Bu müşteri silinmiş durumda"
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS ->
                "Bu müşteride silinmiş sipariş(ler) var"
            status == CustomerStatusTypeEnumUI.INACTIVE ->
                "Bu müşteri uzun süredir aktif değil"
            else -> "Aktif müşteri"
        }
    }

    @ColorInt
    fun getStatusColorInt(): Int {
        return when {
            isDeleted -> Color.parseColor("#DC3545")
            status == CustomerStatusTypeEnumUI.ACTIVE -> Color.parseColor("#28A745")
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS -> Color.parseColor("#FD7E14")
            status == CustomerStatusTypeEnumUI.INACTIVE -> Color.parseColor("#6C757D")
            else -> Color.parseColor("#28A745")
        }
    }

    fun getExpandIconResId(): Int =
        if (isExpanded) R.drawable.ic_expand_less_24
        else R.drawable.ic_expand_more_24

    fun getStatusColorHex(): String {
        return when {
            isDeleted -> "#DC3545"
            status == CustomerStatusTypeEnumUI.ACTIVE -> "#28A745"
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS -> "#FD7E14"
            status == CustomerStatusTypeEnumUI.INACTIVE -> "#6C757D"
            else -> "#8E8E93"
        }
    }

    fun getExpandIconColorResId(): Int =
        if (isExpanded) R.color.icon_primary else R.color.icon_secondary

    fun getCardBackgroundResId(): Int {
        return when {
            isDeleted -> R.drawable.card_deleted
            status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS -> R.drawable.card_warning
            status == CustomerStatusTypeEnumUI.INACTIVE -> R.drawable.card_inactive
            else -> R.drawable.card_normal
        }
    }

    // ✅ YENİ: "Müşteri Adı:" formatlı müşteri adı
    fun getFormattedCustomerName(): String {
        return "Müşteri Adı: $customerName"
    }

    // ✅ YENİ: Detaylı formatlı müşteri adı
    fun getDetailedCustomerName(): String {
        return buildString {
            append("Müşteri Adı: ")
            append(customerName)

            // Ek bilgiler
            if (isDeleted) {
                append(" [Silinmiş]")
            } else if (status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS) {
                append(" [Silinmiş Siparişler]")
            } else if (status == CustomerStatusTypeEnumUI.INACTIVE) {
                append(" [Pasif]")
            }
        }
    }

    // ✅ YENİ: İletişim bilgili format
    fun getCustomerNameWithContact(): String {
        return buildString {
            append("Müşteri Adı: $customerName")

            phone?.let {
                if (it.isNotBlank()) {
                    append("\n📱 $it")
                }
            }

            email?.let {
                if (it.isNotBlank()) {
                    if (phone.isNullOrBlank()) append("\n")
                    else append(" • ")
                    append("✉️ $it")
                }
            }
        }
    }

    // ✅ YENİ: Sipariş bilgili format
    fun getCustomerNameWithOrderInfo(): String {
        return buildString {
            append("Müşteri Adı: $customerName")

            if (orders.isNotEmpty()) {
                append("\n${orders.size} sipariş")

                val deletedCount = getDeletedOrderCount()
                if (deletedCount > 0) {
                    append(" ($deletedCount silinmiş)")
                }
            }
        }
    }

    companion object {

        fun createExample(): CustomerNode {
            return CustomerNode(
                customerId = 1L.toString(),
                customerName = "Ahmet Yılmaz",
                orders = listOf(
                    OrderNode.createExample(),
                    OrderNode.createFromImageExample()
                ),
                itemCount = "2 sipariş",
                email = "ahmet@example.com",
                phone = "+90 555 123 4567",
                registrationDate = "15.01.2023",
                lastOrderDate = "10.03.2024",
                totalSpent = "₺2.499,98"
            )
        }

        fun createDeletedExample(): CustomerNode {
            return CustomerNode(
                customerId = 2L.toString(),
                customerName = "Ayşe Kaya",
                isDeleted = true,
                status = CustomerStatusTypeEnumUI.DELETED,
                statusLabel = "Silinmiş Müşteri",
                canRestore = true,
                colorResId = R.color.status_deleted,
                email = "ayse@example.com",
                phone = "+90 555 987 6543",
                registrationDate = "20.02.2023",
                lastOrderDate = "05.02.2024",
                totalSpent = "₺1.299,99"
            )
        }

        fun createActiveWithDeletedOrdersExample(): CustomerNode {
            return CustomerNode(
                customerId = 3L.toString(),
                customerName = "Mehmet Demir",
                status = CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS,
                statusLabel = "Aktif (Silinmiş Siparişler)",
                orders = listOf(OrderNode.createFromImageExample()),
                colorResId = R.color.status_warning,
                itemCount = "1 sipariş (silinmiş)",
                email = "mehmet@example.com",
                phone = "+90 555 111 2233",
                registrationDate = "10.03.2023",
                lastOrderDate = "01.03.2024",
                totalSpent = "₺1.299,99"
            )
        }
    }
}