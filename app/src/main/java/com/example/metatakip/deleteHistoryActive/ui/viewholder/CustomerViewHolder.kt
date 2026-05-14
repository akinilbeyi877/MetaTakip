package com.example.metatakip.deleteHistoryActive.ui.viewholder

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.CustomerStatusTypeEnumUI
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode

class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    // =====================================================
    // Views
    // =====================================================

    private val iconCustomer: ImageView =
        itemView.findViewById(R.id.iconCustomer)

    private val textCustomerName: TextView =
        itemView.findViewById(R.id.textCustomerName)

    private val textStatus: TextView =
        itemView.findViewById(R.id.textStatus)

    private val buttonExpand: ImageButton =
        itemView.findViewById(R.id.buttonExpand)

    private val textItemCount: TextView =
        itemView.findViewById(R.id.textItemCount)

    private val buttonRestoreCustomer: Button =
        itemView.findViewById(R.id.buttonRestoreCustomer)

    // =====================================================
    // State
    // =====================================================

    private var lastClickTime = 0L
    private var currentCustomer: CustomerNode? = null

    // =====================================================
    // Bind
    // =====================================================

    fun bind(
        customer: CustomerNode,
        onCustomerClick: (CustomerNode) -> Unit,
        onRestoreClick: (CustomerNode) -> Unit,
        isRestoring: Boolean
    ) {
        currentCustomer = customer

        // =========================
        // MÜŞTERİ ADI + "Müşteri Adı:" ön eki
        // =========================
        val customerName = customer.customerName
            .takeIf { it.isNotBlank() }
            ?: "-"

        // ✅ "Müşteri Adı:" ön ekini ekleyelim
        textCustomerName.text = "Müşteri Adı: $customerName"

        // Alternatif: R.string kullanmak isterseniz
        // textCustomerName.text = itemView.context.getString(
        //     R.string.label_customer_with_prefix,  // "Müşteri Adı: %s"
        //     customerName
        // )

        // =========================
        // DİĞER BİLGİLER (MEVCUT AKIŞ)
        // =========================
        updateStatusBadge(customer)
        textItemCount.text = customer.getDetailedItemCount()
        updateCustomerIcon(customer)
        updateRestoreButton(customer, isRestoring, onRestoreClick)
        updateExpandIcon(customer)
        setupClickListeners(customer, onCustomerClick, isRestoring)
    }


    // =====================================================
    // Status Badge
    // =====================================================

    private fun updateStatusBadge(customer: CustomerNode) {

        // =========================
        // BADGE METNİ (AKTİF / PASİF / SİLİNDİ)
        // =========================
        val badgeText = when {
            customer.isDeleted -> "SİLİNDİ"
            customer.status == CustomerStatusTypeEnumUI.INACTIVE -> "PASİF"
            else -> "AKTİF"
        }

        textStatus.text = badgeText

        // =========================
        // BADGE ARKAPLANI (UYARI RENKLERİ)
        // =========================
        val backgroundRes = when {
            customer.isDeleted ->
                R.drawable.bg_status_tag_small_deleted

            customer.status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS ->
                R.drawable.bg_status_tag_small_warning

            customer.status == CustomerStatusTypeEnumUI.INACTIVE ->
                R.drawable.bg_status_tag_small_inactive

            else ->
                R.drawable.bg_status_tag_small_active
        }

        textStatus.setBackgroundResource(backgroundRes)
        textStatus.setTextColor(
            ContextCompat.getColor(itemView.context, R.color.white)
        )
    }


    // =====================================================
    // Icon
    // =====================================================

    private fun updateCustomerIcon(customer: CustomerNode) {
        iconCustomer.setImageResource(customer.iconResId)

        val iconColorRes = when {
            customer.isDeleted -> R.color.icon_warning
            customer.status == CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS ->
                R.color.icon_warning
            customer.status == CustomerStatusTypeEnumUI.INACTIVE ->
                R.color.status_inactive
            else -> R.color.icon_success
        }

        iconCustomer.setColorFilter(
            ContextCompat.getColor(itemView.context, iconColorRes)
        )
    }

    // =====================================================
    // Restore Button
    // =====================================================

    private fun updateRestoreButton(
        customer: CustomerNode,
        isRestoring: Boolean,
        onRestoreClick: (CustomerNode) -> Unit
    ) {
        if (isRestoring) {
            buttonRestoreCustomer.visibility = View.VISIBLE
            buttonRestoreCustomer.isEnabled = false
            buttonRestoreCustomer.text = "İŞLEMDE..."
            buttonRestoreCustomer.setTextColor(
                ContextCompat.getColor(itemView.context, R.color.gray)
            )
            return
        }

        when {
            !customer.canRestore -> {
                buttonRestoreCustomer.visibility = View.GONE
            }
            customer.isDeleted -> {
                buttonRestoreCustomer.visibility = View.VISIBLE
                buttonRestoreCustomer.isEnabled = true
                buttonRestoreCustomer.text = "GERİ AL"
                buttonRestoreCustomer.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.color_red)
                )
            }
            customer.getDeletedOrderCount() > 0 -> {
                buttonRestoreCustomer.visibility = View.VISIBLE
                buttonRestoreCustomer.isEnabled = true
                buttonRestoreCustomer.text = "SİLİNENLERİ GÖR"
                buttonRestoreCustomer.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.warning_orange)
                )
            }
            else -> {
                buttonRestoreCustomer.visibility = View.GONE
            }
        }

        buttonRestoreCustomer.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 1000) {
                lastClickTime = now
                onRestoreClick(customer)
            }
        }
    }

    // =====================================================
    // Expand Icon
    // =====================================================

    private fun updateExpandIcon(customer: CustomerNode) {
        buttonExpand.setImageResource(customer.getExpandIconResId())
        buttonExpand.setColorFilter(
            ContextCompat.getColor(itemView.context, R.color.icon_primary)
        )
    }

    // =====================================================
    // Click Handling
    // =====================================================

    private fun setupClickListeners(
        customer: CustomerNode,
        onCustomerClick: (CustomerNode) -> Unit,
        isRestoring: Boolean
    ) {
        if (isRestoring) {
            itemView.isEnabled = false
            buttonExpand.isEnabled = false
            return
        }

        itemView.isEnabled = true
        buttonExpand.isEnabled = true

        val clickListener = View.OnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 500) {
                lastClickTime = now
                onCustomerClick(customer)
            }
        }

        itemView.setOnClickListener(clickListener)
        buttonExpand.setOnClickListener(clickListener)
    }

    // =====================================================
    // Clear (Adapter içinden çağrılabilir)
    // =====================================================

    fun clear() {
        currentCustomer = null
        itemView.setOnClickListener(null)
        buttonExpand.setOnClickListener(null)
        buttonRestoreCustomer.setOnClickListener(null)
    }
}