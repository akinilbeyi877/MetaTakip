package com.example.metatakip.controllers.callphonelast

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.feature_data.entityModel.CallRecord

class CallRecordAdapter(
    private val records: List<CallRecord>,
    private val onClick: (CallRecord) -> Unit  // 🔥 Tek tık callback'i
) : RecyclerView.Adapter<CallRecordAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtMusteriAdi: TextView = view.findViewById(R.id.txtMusteriAdi)
        val txtTelefonVeTur: TextView = view.findViewById(R.id.txtTelefonVeTur)
        val txtFirma: TextView = view.findViewById(R.id.txtFirma)
        val txtZaman: TextView = view.findViewById(R.id.txtZaman)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = records[position]

        holder.txtMusteriAdi.text = item.musteriAdi ?: "Bilinmeyen Müşteri"
        holder.txtTelefonVeTur.text = "${item.musteriTelefonu} • ${item.getCallTypeText()}"
        holder.txtFirma.text = "🏢 ${item.cihazFirmaAdi} • ${item.cihazKullaniciAdi}"

        val syncText = if (item.isSynced()) "✅ Merkeze iletildi" else "⏳ Bekliyor"
        holder.txtZaman.text = "${item.getFormattedCallTime()} • $syncText"

        // 🔥 UZUN BASMA DEĞİL, TEK TIK ile menü aç
        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }

    override fun getItemCount(): Int = records.size
}