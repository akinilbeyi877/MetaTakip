package com.example.metatakip.adapters

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.controllers.genericListFolder.RightClickMenuHandler
import dao.MetaTakipPersonelDao
import java.util.Calendar
import java.util.Locale

class GenericFormAdapter(
    private val context: Context,
    private var fields: MutableList<FormField> = mutableListOf(),
    private val onMicClick: (EditText) -> Unit
) : RecyclerView.Adapter<GenericFormAdapter.FormViewHolder>() {

    class FormViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textLabel: TextView = view.findViewById(R.id.textLabel)
        
        val textContainer: View = view.findViewById(R.id.textContainer)
        val editText: EditText = view.findViewById(R.id.editText)
        val btnMic: ImageButton = view.findViewById(R.id.btnMic)

        val btnModernDropdown: View = view.findViewById(R.id.btnModernDropdown)
        val tvSelectedOption: TextView = view.findViewById(R.id.tvSelectedOption)
        val spinner: Spinner = view.findViewById(R.id.spinner)

        val textareaContainer: View = view.findViewById(R.id.textareaContainer)
        val editTextMultiLine: EditText = view.findViewById(R.id.editTextMultiLine)
        val btnMicMultiLine: ImageButton = view.findViewById(R.id.btnMicMultiLine)

        val dateContainer: View = view.findViewById(R.id.dateContainer)
        val editTextDate: EditText = view.findViewById(R.id.editTextDate)
        val btnCalendar: ImageButton = view.findViewById(R.id.btnCalendar)

        val phoneContainer: View = view.findViewById(R.id.phoneContainer)
        val editTextPhone: EditText = view.findViewById(R.id.editTextPhone)
        val btnMicPhone: ImageButton = view.findViewById(R.id.btnMicPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_form_generic_field, parent, false)
        return FormViewHolder(view)
    }

    override fun getItemCount(): Int = fields.size

    override fun onBindViewHolder(holder: FormViewHolder, position: Int) {
        val field = fields[position]
        
        // Gizli alanları tamamen gizle
        if (field.type == FieldType.HIDDEN) {
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        holder.textLabel.text = field.label + if (field.isRequired) " *" else ""
        
        // Tüm container'ları gizle
        holder.textContainer.visibility = View.GONE
        holder.btnModernDropdown.visibility = View.GONE
        holder.spinner.visibility = View.GONE
        holder.textareaContainer.visibility = View.GONE
        holder.dateContainer.visibility = View.GONE
        holder.phoneContainer.visibility = View.GONE

        when (field.type) {
            FieldType.DROPDOWN -> {
                holder.btnModernDropdown.visibility = View.VISIBLE
                setupModernDropdown(holder.btnModernDropdown, holder.tvSelectedOption, field)
            }
            FieldType.DATE -> {
                holder.dateContainer.visibility = View.VISIBLE
                setupDateField(holder.editTextDate, holder.btnCalendar, field)
            }
            FieldType.TEXT -> {
                if (field.key == "notlar" || field.key == "adres" || field.key == "musteriNotu") {
                    holder.textareaContainer.visibility = View.VISIBLE
                    setupTextField(holder.editTextMultiLine, holder.btnMicMultiLine, field)
                } else if (field.key == "ceptel" || field.key == "musteriTelefon") {
                    holder.phoneContainer.visibility = View.VISIBLE
                    setupTextField(holder.editTextPhone, holder.btnMicPhone, field)
                } else {
                    holder.textContainer.visibility = View.VISIBLE
                    setupTextField(holder.editText, holder.btnMic, field)
                }
            }
            else -> {
                holder.textContainer.visibility = View.VISIBLE
                setupTextField(holder.editText, holder.btnMic, field)
            }
        }
    }

    private fun setupTextField(editText: EditText, micButton: ImageButton, field: FormField) {
        editText.setText(field.value ?: "")
        
        editText.removeTextChangedListener(editText.tag as? TextWatcher)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                field.value = s?.toString() ?: ""
            }
        }
        editText.addTextChangedListener(watcher)
        editText.tag = watcher

        micButton.setOnClickListener { onMicClick(editText) }
    }

    private fun setupDateField(editText: EditText, calendarButton: ImageButton, field: FormField) {
        editText.setText(field.value ?: "")
        
        val clickListener = View.OnClickListener {
            showDatePicker(editText, field)
        }
        editText.setOnClickListener(clickListener)
        calendarButton.setOnClickListener(clickListener)
    }

    private fun setupModernDropdown(btnSelect: View, tvSelected: TextView, field: FormField) {
        val labelList: List<String>
        val valueMap: Map<String, String>

        when {
            field.optionMap != null -> {
                labelList = field.optionMap!!.keys.toList()
                valueMap = field.optionMap!!
            }
            field.key == "urunTipi" -> {
                labelList = listOf("Halı", "Koltuk", "Stor Perde", "Yorgan", "Battaniye", "Yastık", "Yatak", "Diğer")
                valueMap = labelList.associateWith { it }
            }
            field.key == "durum" -> {
                labelList = listOf("Yeni Sipariş", "Alınacaklar", "Teslim Alındı", "Teslim Edildi", "Tekrar İşleme Alındı", "Sipariş İptal Edildi")
                valueMap = labelList.associateWith { it }
            }
            field.key == "yetkili" -> {
                labelList = try { MetaTakipPersonelDao(context).getAllPersonel().map { it.adSoyad } } catch(e:Exception) { emptyList() }
                valueMap = labelList.associateWith { it }
            }
            else -> {
                labelList = field.options ?: emptyList()
                valueMap = labelList.associateWith { it }
            }
        }

        // Başlangıç değerini ayarla
        if (field.value.isNotBlank()) {
            val label = if (field.optionMap != null) {
                field.optionMap!!.entries.find { it.value == field.value }?.key
            } else {
                field.value
            }
            tvSelected.text = label ?: "Seçim Yapın..."
        } else {
            tvSelected.text = "Seçim Yapın..."
        }

        btnSelect.setOnClickListener {
            val menuHandler = RightClickMenuHandler(context as android.app.Activity)
            val menuItems = labelList.map { label ->
                RightClickMenuHandler.ModernMenuItem(label, R.drawable.ic_chevron_right, 0xFF1976D2.toInt()) {
                    tvSelected.text = label
                    field.value = valueMap[label] ?: ""
                }
            }
            menuHandler.showModernMenu(field.label ?: "Seçim Yapın", menuItems, autoDismiss = true)
        }
    }

    private fun setupDropdown(spinner: Spinner, field: FormField) {
        val labelList: List<String>
        val valueMap: Map<String, String>

        when {
            field.optionMap != null -> {
                labelList = field.optionMap!!.keys.toList()
                valueMap = field.optionMap!!
            }
            field.key == "urunTipi" -> {
                labelList = listOf("Halı", "Koltuk", "Stor Perde", "Yorgan", "Battaniye", "Yastık", "Yatak", "Diğer")
                valueMap = labelList.associateWith { it }
            }
            field.key == "durum" -> {
                labelList = listOf("Yeni Sipariş", "Alınacaklar", "Teslim Alındı", "Teslim Edildi", "Tekrar İşleme Alındı", "Sipariş İptal Edildi")
                valueMap = labelList.associateWith { it }
            }
            else -> {
                labelList = field.options ?: emptyList()
                valueMap = labelList.associateWith { it }
            }
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labelList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val savedValue = field.value
        if (savedValue.isNotBlank()) {
            val index = if (field.optionMap != null) {
                labelList.indexOfFirst { field.optionMap!![it] == savedValue }
            } else {
                labelList.indexOf(savedValue)
            }
            if (index >= 0) spinner.setSelection(index, false)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val label = labelList.getOrNull(pos)
                field.value = if (field.optionMap != null && label != null) field.optionMap!![label] ?: "" else label ?: ""
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun showDatePicker(editText: EditText, field: FormField) {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            val selected = "%02d/%02d/%04d".format(d, m + 1, y)
            editText.setText(selected)
            field.value = selected
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
}
