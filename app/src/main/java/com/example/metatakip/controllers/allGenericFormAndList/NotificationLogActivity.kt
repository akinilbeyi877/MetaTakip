package com.example.metatakip.controllers.allGenericFormAndList

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.metatakip.R
import com.example.metatakip.controllers.services.NotificationLogEntry
import com.example.metatakip.controllers.services.NotificationLogManager
import com.example.metatakip.controllers.services.OrderPopupActivity
import com.example.metatakip.feature.order.data.OrderDaoImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationLogActivity : AppCompatActivity() {

    // ── Tarih ──
    private val cal = Calendar.getInstance()
    private var selectedDayStart = 0L
    private var selectedDayEnd   = 0L

    // ── Sıralama ──
    private var sortNewestFirst = true

    // ── Format ──
    private val sdfDate  = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
    private val sdfDay   = SimpleDateFormat("dd/MM", Locale("tr"))
    private val sdfDow   = SimpleDateFormat("EEE", Locale("tr"))
    private val sdfTime  = SimpleDateFormat("HH:mm", Locale("tr"))
    private val sdfFull  = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))

    // ── View refs ──
    private lateinit var tvCurrentDate: TextView
    private lateinit var layoutNotifList: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatToday: TextView
    private lateinit var tvStatWeek: TextView
    private lateinit var tvStatSelected: TextView
    private lateinit var etSearch: EditText
    private val dayCells     = arrayOfNulls<LinearLayout>(5)
    private val tvDayNums    = arrayOfNulls<TextView>(5)
    private val tvDayNames   = arrayOfNulls<TextView>(5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_notification_log)

        bindViews()
        setupListeners()
        selectDay(Calendar.getInstance())
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    // ─────────────────────────────────────────────────
    // View bağlama
    // ─────────────────────────────────────────────────

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvCurrentDate    = findViewById(R.id.tvCurrentDate)
        layoutNotifList  = findViewById(R.id.layoutNotifList)
        tvEmpty          = findViewById(R.id.tvEmpty)
        tvStatTotal      = findViewById(R.id.tvStatTotal)
        tvStatToday      = findViewById(R.id.tvStatToday)
        tvStatWeek       = findViewById(R.id.tvStatWeek)
        tvStatSelected   = findViewById(R.id.tvStatSelected)
        etSearch         = findViewById(R.id.etSearch)

        for (i in 0..4) {
            dayCells[i]   = findViewById(resources.getIdentifier("dayCell$i", "id", packageName))
            tvDayNums[i]  = findViewById(resources.getIdentifier("tvDay${i}Num", "id", packageName))
            tvDayNames[i] = findViewById(resources.getIdentifier("tvDay${i}Name", "id", packageName))
        }
    }

    // ─────────────────────────────────────────────────
    // Dinleyiciler
    // ─────────────────────────────────────────────────

    private fun setupListeners() {
        // Temizle butonu
        findViewById<Button>(R.id.btnClearNotifications).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Tümünü Temizle")
                .setMessage("Tüm bildirim kayıtları silinecek. Emin misiniz?")
                .setPositiveButton("Evet") { _, _ ->
                    NotificationLogManager.clear(this)
                    refreshAll()
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        // Tarih önceki / sonraki
        findViewById<TextView>(R.id.btnDatePrev).setOnClickListener {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            selectDay(cal)
        }
        findViewById<TextView>(R.id.btnDateNext).setOnClickListener {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            selectDay(cal)
        }

        // Sıralama toggle
        findViewById<TextView>(R.id.btnSortToggle).setOnClickListener {
            sortNewestFirst = !sortNewestFirst
            refreshList(etSearch.text.toString())
        }

        // Arama
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshList(s?.toString() ?: "") }
        })

        // Gün hücresi tıklamaları (strip)
        for (i in 0..4) {
            val idx = i
            dayCells[i]?.setOnClickListener {
                val stripCal = buildStripCalendar()[idx]
                cal.time = stripCal.time
                selectDay(cal)
            }
        }
    }

    // ─────────────────────────────────────────────────
    // Gün seçimi
    // ─────────────────────────────────────────────────

    private fun selectDay(selectedCal: Calendar) {
        val d = selectedCal.clone() as Calendar
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0)
        d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0)
        selectedDayStart = d.timeInMillis
        selectedDayEnd   = selectedDayStart + 86_400_000L - 1L

        // Başlık tarih
        val dowStr = SimpleDateFormat("EEE", Locale("tr")).format(selectedCal.time)
        tvCurrentDate.text = "${sdfDate.format(selectedCal.time)} $dowStr"

        updateDayStrip()
        refreshAll()
    }

    private fun buildStripCalendar(): List<Calendar> {
        // Seçili günü ortaya koy → -2 ... +2
        return (-2..2).map { offset ->
            (cal.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, offset) }
        }
    }

    private fun updateDayStrip() {
        val strip = buildStripCalendar()
        val todayMs = run {
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0)
            t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0)
            t.timeInMillis
        }

        strip.forEachIndexed { i, c ->
            val isSelected = c.timeInMillis == selectedDayStart
            val isToday    = c.timeInMillis == todayMs
            val cell       = dayCells[i] ?: return@forEachIndexed
            val numTv      = tvDayNums[i] ?: return@forEachIndexed
            val nameTv     = tvDayNames[i] ?: return@forEachIndexed

            numTv.text  = sdfDay.format(c.time)
            nameTv.text = sdfDow.format(c.time).replaceFirstChar { it.uppercase() }

            val rd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpPx(10f).toFloat()
            }
            when {
                isSelected -> {
                    rd.setColor(Color.parseColor("#1565C0"))
                    cell.background = rd
                    numTv.setTextColor(Color.WHITE)
                    nameTv.setTextColor(Color.parseColor("#BBDEFB"))
                }
                isToday -> {
                    rd.setColor(Color.parseColor("#E3F2FD"))
                    cell.background = rd
                    numTv.setTextColor(Color.parseColor("#1565C0"))
                    nameTv.setTextColor(Color.parseColor("#1565C0"))
                }
                else -> {
                    cell.background = null
                    numTv.setTextColor(Color.parseColor("#424242"))
                    nameTv.setTextColor(Color.parseColor("#757575"))
                }
            }
        }
    }

    // ─────────────────────────────────────────────────
    // Veri yenileme
    // ─────────────────────────────────────────────────

    private fun refreshAll() {
        updateStats()
        refreshList(etSearch.text.toString())
    }

    private fun updateStats() {
        val all   = NotificationLogManager.getAll(this)
        val now   = System.currentTimeMillis()
        val todayStart = run {
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0)
            t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0)
            t.timeInMillis
        }
        val weekStart = todayStart - 6 * 86_400_000L

        tvStatTotal.text    = all.size.toString()
        tvStatToday.text    = all.count { it.tarih in todayStart..now }.toString()
        tvStatWeek.text     = all.count { it.tarih in weekStart..now }.toString()
        tvStatSelected.text = all.count { it.tarih in selectedDayStart..selectedDayEnd }.toString()
    }

    private fun refreshList(query: String) {
        val allEntries = NotificationLogManager.getAll(this)

        // 1. Seçili güne göre filtrele
        var filtered = allEntries.filter { it.tarih in selectedDayStart..selectedDayEnd }

        // 2. Arama filtresi
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            filtered = filtered.filter { e ->
                e.musteriAdi.lowercase().contains(q) ||
                e.firmaAdi.lowercase().contains(q) ||
                e.urunTipi.lowercase().contains(q)
            }
        }

        // 3. Sıralama
        filtered = if (sortNewestFirst) filtered.sortedByDescending { it.tarih }
                   else filtered.sortedBy { it.tarih }

        // 4. Listeyi doldur
        layoutNotifList.removeAllViews()

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (q.isNotEmpty()) "Arama sonucu bulunamadı."
                           else "Bu gün için bildirim kaydı yok."
        } else {
            tvEmpty.visibility = View.GONE
            // Toplam kaydı hesaplamalarda kullanalım (orijinal listedeki sıra indeksi)
            val fullList = allEntries.toMutableList()
            filtered.forEach { entry ->
                val origIdx = fullList.indexOf(entry)
                layoutNotifList.addView(buildItemView(entry, origIdx))
            }
        }
    }

    // ─────────────────────────────────────────────────
    // Kart görünümü
    // ─────────────────────────────────────────────────

    private fun buildItemView(entry: NotificationLogEntry, origIdx: Int): View {
        val card = CardView(this).apply {
            radius = dpPx(10f).toFloat()
            cardElevation = dpPx(2f).toFloat()
            setCardBackgroundColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpPx(8f))
            layoutParams = lp
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpPx(14f), dpPx(10f), dpPx(14f), dpPx(10f))
        }

        // Satır 1: Müşteri + saat
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row1.addView(TextView(this).apply {
            text = "📦 ${entry.musteriAdi}"
            textSize = 14f
            setTextColor(Color.parseColor("#1A237E"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row1.addView(TextView(this).apply {
            text = sdfTime.format(Date(entry.tarih))
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
        })

        // Satır 2: Firma
        val tvFirma = TextView(this).apply {
            text = "🏢 ${entry.firmaAdi}"
            textSize = 12f
            setTextColor(Color.parseColor("#455A64"))
        }

        // Satır 3: Ürün
        val tvUrun = TextView(this).apply {
            text = if (entry.urunTipi.isNotBlank()) "📌 ${entry.urunTipi}" else ""
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            visibility = if (entry.urunTipi.isNotBlank()) View.VISIBLE else View.GONE
        }

        inner.addView(row1)
        inner.addView(tvFirma)
        inner.addView(tvUrun)
        card.addView(inner)

        // Tek tıkla → OrderPopupActivity aç
        card.setOnClickListener           { openOrderPopup(entry.orderId) }
        // Uzun basış → sağ menü (sil, sipariş listesi)
        card.setOnLongClickListener       { showContextMenu(entry, origIdx); true }
        card.isClickable = true
        card.isFocusable = true
        card.foreground = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            .getDrawable(0)

        return card
    }

    // ─────────────────────────────────────────────────
    // OrderPopupActivity aç (tek tıkta)
    // ─────────────────────────────────────────────────

    private fun openOrderPopup(orderId: Long) {
        lifecycleScope.launch {
            val order = withContext(Dispatchers.IO) {
                try { OrderDaoImpl(this@NotificationLogActivity).getOrderById(orderId) }
                catch (e: Exception) { null }
            }
            if (order == null) {
                Toast.makeText(this@NotificationLogActivity,
                    "Sipariş artık bulunamadı (silinmiş olabilir).",
                    Toast.LENGTH_SHORT).show()
                return@launch
            }
            startActivity(
                Intent(this@NotificationLogActivity, OrderPopupActivity::class.java).apply {
                    putExtra("order_id",        order.id)
                    putExtra("musteri_uuid",     order.musteriUuid)
                    putExtra("musteri_adi",      order.musteriAdi)
                    putExtra("musteri_telefon",  order.musteriTelefon)
                    putExtra("urun_tipi",        order.urunTipi)
                    putExtra("metrekare",        order.metrekare)
                    putExtra("ucret",            order.ucret)
                    putExtra("firma_adi",        order.firmaAdi)
                }
            )
        }
    }

    // ─────────────────────────────────────────────────
    // Sağ menü (uzun basış)
    // ─────────────────────────────────────────────────

    private fun showContextMenu(entry: NotificationLogEntry, origIdx: Int) {
        val options = arrayOf(
            "📦  Siparişe Git",
            "🛒  Sipariş Listesine Git",
            "🗑️  Bu Kaydı Sil",
            "❌  İptal"
        )
        AlertDialog.Builder(this)
            .setTitle("${entry.musteriAdi}  ·  ${sdfFull.format(Date(entry.tarih))}")
            .setMessage("🏢 ${entry.firmaAdi}\n📌 ${entry.urunTipi}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, GenericFormActivity::class.java).apply {
                            putExtra("targetTable", "siparis")
                            putExtra("edit_mode", true)
                            putExtra("id", entry.orderId)
                        }
                    )
                    1 -> startActivity(
                        Intent(this, GenericListActivity::class.java).apply {
                            putExtra("listType", "siparis")
                            putExtra("filterDurum", "Yeni Sipariş")
                            putExtra("pageTitle", "🛒 ALINACAK SİPARİŞLER")
                        }
                    )
                    2 -> {
                        NotificationLogManager.removeAt(this, origIdx)
                        refreshAll()
                    }
                }
            }
            .show()
    }

    // ─────────────────────────────────────────────────
    // Yardımcı
    // ─────────────────────────────────────────────────

    private fun dpPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()
}
