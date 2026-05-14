package com.example.metatakip.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.feature_data.entityModel.Order
import java.text.SimpleDateFormat
import java.util.*

class DeleteHistoryAdapter(
    private val items: List<Order>,
    private val isAdmin: Boolean,
    private val onRestore: (Long) -> Unit
) : RecyclerView.Adapter<DeleteHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMusteriAdi: TextView = view.findViewById(R.id.tvMusteriAdi)
        val tvSiparisId: TextView = view.findViewById(R.id.tvSiparisId)
        val tvSilinmeTarihi: TextView = view.findViewById(R.id.tvSilinmeTarihi)
        val tvSilenKullanici: TextView = view.findViewById(R.id.tvSilenKullanici)
        val btnRestore: View = view.findViewById(R.id.btnRestore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delete_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val siparis = items[position]

        holder.tvMusteriAdi.text = siparis.musteriAdi ?: "İsimsiz Müşteri"
        holder.tvSiparisId.text = "Sipariş ID: ${siparis.id}"

        // Silinme tarihini formatla
        val deletedAt = siparis.deletedAt
        if (deletedAt != null) {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            holder.tvSilinmeTarihi.text = "Silinme: ${sdf.format(Date(deletedAt))}"
        } else {
            holder.tvSilinmeTarihi.text = "Silinme tarihi: Bilinmiyor"
        }

        // Silen kullanıcı ID'si
        val deletedBy = siparis.deletedBy
        holder.tvSilenKullanici.text = if (deletedBy != null) "Silen: Kullanıcı $deletedBy" else "Silen: Bilinmiyor"

        // Geri yükle butonu - sadece admin görebilir
        holder.btnRestore.visibility = if (isAdmin) View.VISIBLE else View.GONE

        holder.btnRestore.setOnClickListener {
            onRestore.invoke(siparis.id)
        }
    }

    override fun getItemCount(): Int = items.size
}