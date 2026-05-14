package com.example.metatakip.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import java.text.SimpleDateFormat
import java.util.*

class DateStripAdapter(
    private val dates: List<Date>,
    private var selectedDate: Date,
    private val onDateSelected: (Date) -> Unit
) : RecyclerView.Adapter<DateStripAdapter.ViewHolder>() {

    private val dateNumFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
    private val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cvRoot: CardView = view.findViewById(R.id.cvDateItem)
        val tvDateNum: TextView = view.findViewById(R.id.tvDateNum)
        val tvDayName: TextView = view.findViewById(R.id.tvDayName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date_strip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val date = dates[position]
        holder.tvDateNum.text = dateNumFormat.format(date)
        holder.tvDayName.text = dayNameFormat.format(date)

        val isSelected = isSameDay(date, selectedDate)
        if (isSelected) {
            holder.cvRoot.setCardBackgroundColor(Color.parseColor("#1976D2"))
            holder.tvDateNum.setTextColor(Color.WHITE)
            holder.tvDayName.setTextColor(Color.parseColor("#BBDEFB"))
        } else {
            holder.cvRoot.setCardBackgroundColor(Color.parseColor("#F8FAFC"))
            holder.tvDateNum.setTextColor(Color.parseColor("#64748B"))
            holder.tvDayName.setTextColor(Color.parseColor("#94A3B8"))
        }

        holder.itemView.setOnClickListener {
            selectedDate = date
            notifyDataSetChanged()
            onDateSelected(date)
        }
    }

    override fun getItemCount(): Int = dates.size

    fun updateSelectedDate(newDate: Date) {
        selectedDate = newDate
        notifyDataSetChanged()
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}