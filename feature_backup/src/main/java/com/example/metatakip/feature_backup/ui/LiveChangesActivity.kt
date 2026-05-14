package com.example.metatakip.feature_backup.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.feature_backup.R
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.util.ChangeLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🛰️ CANLI DEĞİŞİKLİK İZLEME EKRANI
 * Veritabanındaki tüm INSERT, UPDATE ve DELETE işlemlerini anlık listeler.
 */
class LiveChangesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvStats: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button

    private lateinit var adapter: ChangeLogAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // 🔔 Senkronizasyon ve Veritabanı Değişiklik Dinleyicisi
    private val changeListener = object : ChangeLogManager.OnChangeListener {
        override fun onNewChange(change: ChangeLog) {
            runOnUiThread {
                adapter.addChange(change)
                updateStats()
                recyclerView.smoothScrollToPosition(0)
                tvLastUpdate.text = "Son işlem: ${formatTime(System.currentTimeMillis())}"
            }
        }

        override fun onChangesUpdated(changes: List<ChangeLog>) {
            runOnUiThread {
                adapter.updateChanges(changes)
                updateStats()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_changes)

        initViews()
        setupRecyclerView()
        setupListeners()

        // 🚀 Sistemi dinlemeye başla
        ChangeLogManager.addListener(changeListener)

        // Verileri ilk kez yükle
        refreshChanges(initialLoad = true)

        // 🔄 Otomatik yenilemeyi başlat (5 sn)
        startAutoRefresh()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvStats = findViewById(R.id.tvStats)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnClear = findViewById(R.id.btnClear)
        tvLastUpdate.text = "Hazır"
    }

    private fun setupRecyclerView() {
        adapter = ChangeLogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnRefresh.setOnClickListener { refreshChanges() }
        btnClear.setOnClickListener { clearDisplayList() }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    /**
     * Veritabanını kontrol eder ve yeni değişiklikleri listeye ekler.
     */
    private fun refreshChanges(initialLoad: Boolean = false) {
        if (!initialLoad) {
            btnRefresh.isEnabled = false
            btnRefresh.text = "..."
        }

        lifecycleScope.launch {
            // Arka planda yeni kayıtları kontrol et
            val newChanges = withContext(Dispatchers.IO) {
                ChangeLogManager.checkForNewChanges(this@LiveChangesActivity)
            }

            withContext(Dispatchers.Main) {
                btnRefresh.isEnabled = true
                btnRefresh.text = "🔄 Yenile"
                if (newChanges.isNotEmpty()) {
                    updateStats()
                }
            }
        }
    }

    private fun clearDisplayList() {
        adapter.clear()
        updateStats()
    }

    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    refreshChanges()
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.postDelayed(refreshRunnable!!, 5000)
    }

    private fun updateStats() {
        val total = adapter.itemCount
        val insert = adapter.getCountByType(ChangeLog.ActionType.INSERT)
        val update = adapter.getCountByType(ChangeLog.ActionType.UPDATE)
        val delete = adapter.getCountByType(ChangeLog.ActionType.DELETE)

        tvStats.text = "Toplam: $total  |  🟢 $insert  |  🔄 $update  |  🔴 $delete"
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshRunnable?.let { handler.removeCallbacks(it) }
        ChangeLogManager.removeListener(changeListener)
    }

    // ================== HİBRİT ADAPTER ==================

    class ChangeLogAdapter : RecyclerView.Adapter<ChangeLogAdapter.ViewHolder>() {

        private val changes = mutableListOf<ChangeLog>()

        fun addChange(change: ChangeLog) {
            // Eğer aynı ID zaten varsa (çift kayıt önleme) ekleme
            if (changes.any { it.id == change.id }) return

            changes.add(0, change)
            notifyItemInserted(0)
            if (changes.size > 150) { // Bellek koruması için limiti 150 yaptık
                changes.removeAt(changes.lastIndex)
                notifyItemRemoved(changes.size)
            }
        }

        fun updateChanges(newChanges: List<ChangeLog>) {
            changes.clear()
            changes.addAll(newChanges)
            notifyDataSetChanged()
        }

        fun clear() {
            changes.clear()
            notifyDataSetChanged()
        }

        fun getCountByType(type: ChangeLog.ActionType): Int = changes.count { it.actionType == type }

        override fun getItemCount(): Int = changes.size

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.id.text1, parent, false)
            val textView = view as TextView
            textView.textSize = 13f
            textView.setPadding(24, 16, 24, 16)
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val change = changes[position]
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(change.changedAt))

            val icon = when (change.actionType) {
                ChangeLog.ActionType.INSERT -> "🟢"
                ChangeLog.ActionType.UPDATE -> "🔄"
                ChangeLog.ActionType.DELETE -> "🔴"
            }

            // Tablo isimlerini Türkçeleştirme (Sync UI için)
            val tableNameTr = getTableNameTr(change.tableName)
            val actionTr = when (change.actionType) {
                ChangeLog.ActionType.INSERT -> "Eklendi"
                ChangeLog.ActionType.UPDATE -> "Güncellendi"
                ChangeLog.ActionType.DELETE -> "Silindi"
            }

            // 🌍 HİBRİT DETAY: UUID'nin son kısmını göstererek doğruluğu izleyelim
            val uuidSnippet = change.getUuidFromDetails()?.takeLast(6) ?: "N/A"

            val mainText = "$icon $time - $tableNameTr $actionTr"
            val subText = "UUID: ...$uuidSnippet | Detay: ${change.details?.take(40) ?: "ID: ${change.recordId}"}"

            // simple_list_item_2 kullanılıyorsa text1 ve text2 set edilir.
            // Burada tek satırlık layout'u zenginleştirdik.
            holder.textView.text = "$mainText\n$subText"

            // Renk kodları
            val bgColor = when (change.actionType) {
                ChangeLog.ActionType.INSERT -> 0x15_2ECC71 // Yeşil tonu
                ChangeLog.ActionType.UPDATE -> 0x15_F1C40F // Sarı tonu
                ChangeLog.ActionType.DELETE -> 0x15_E74C3C // Kırmızı tonu
            }
            holder.itemView.setBackgroundColor(bgColor)
        }

        private fun getTableNameTr(table: String): String = when (table.lowercase()) {
            "musteri" -> "Müşteri"
            "siparis" -> "Sipariş"
            "firma" -> "Firma"
            "urun" -> "Ürün"
            "call_logs" -> "Arama"
            else -> table.replaceFirstChar { it.uppercase() }
        }

        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}