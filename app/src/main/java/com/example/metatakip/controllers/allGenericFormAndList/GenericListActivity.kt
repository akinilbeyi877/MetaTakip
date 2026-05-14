package com.example.metatakip.controllers.allGenericFormAndList

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.adapters.DateStripAdapter
import com.example.metatakip.builders.GenericListDataProvider
import com.example.metatakip.controllers.services.OrderPopupActivity
import com.example.metatakip.feature.admin.data.MesajSablonDaoImpl
import com.example.metatakip.feature.firma.data.MetaTakipFirmaDaoImpl
import com.example.metatakip.feature.label.data.EtiketSablonDaoImpl
import com.example.metatakip.feature.label.navigation.LabelFlowController
import com.example.metatakip.feature.label.navigation.LabelMode
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoImpl
import com.example.metatakip.feature_data.entityModel.CallRecord
import com.example.metatakip.feature_data.entityModel.Customer
import com.example.metatakip.feature_data.entityModel.EtiketSablon
import com.example.metatakip.feature_data.entityModel.Firma
import com.example.metatakip.feature_data.entityModel.MesajSablon
import com.example.metatakip.feature_data.entityModel.Order
import com.example.metatakip.feature_data.entityModel.Personel
import com.example.metatakip.feature_data.entityModel.SessionManager
import com.example.metatakip.feature_data.entityModel.Unvan
import com.example.metatakip.feature_data.entityModel.Urun
import com.example.metatakip.feature_data.entityModel.UrunTipi
import com.example.metatakip.feature_data.helpers.GenericListHelperImpl
import com.example.metatakip.feature_data.helpers.IGenericListHelper
import com.example.metatakip.feature_data.label.EtiketManager
import com.example.metatakip.feature_data.label.EtiketSablonDaoInterface
import com.example.metatakip.feature_data.ui.GenericListRowAdapterUiModel
import com.example.metatakip.feature_data.ui.adapter.GenericListAdapter
import com.example.metatakip.feature_data.ui.mapper.GenericListUiMapper
import com.example.metatakip.controllers.genericListFolder.RightClickMenuHandler
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import dao.MetaTakipCustomerDao
import dao.MetaTakipPersonelDao
import dao.MetaTakipUnvanDao
import dao.MetaTakipUrunDao
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GenericListActivity : AppCompatActivity() {

    private lateinit var uiMapper: GenericListUiMapper

    private lateinit var firmaDao: MetaTakipFirmaDaoImpl
    private lateinit var etiketSablonDao: EtiketSablonDaoInterface
    private lateinit var unvanDao: MetaTakipUnvanDao
    private lateinit var personelDao: MetaTakipPersonelDao

    private var pageTitle: String? = null
    private lateinit var etiketManager: EtiketManager
    private lateinit var listView: ListView
    private lateinit var adapter: GenericListAdapter
    private lateinit var searchEditText: EditText
    private lateinit var btnClearSearch: ImageButton
    private var allItems: MutableList<GenericListRowAdapterUiModel> = mutableListOf()
    private var allRawItems: List<Any> = emptyList()   // aramada stats için ham liste
    private lateinit var menuHandler: RightClickMenuHandler

    private var listType: String = "siparis"
    private var durumFilter: String = "Yeni Sipariş"

    private var isDeletedCustomerMode: Boolean = false
    private var isDeletedOrderMode: Boolean = false

    private lateinit var helper: IGenericListHelper
    private var refreshReceiverRegistered = false
    // Filtreleme değişkenleri
    private var filterMusteriUuid: String? = null
    private var filterMusteriAdi: String? = null
    private var filterTelefon: String? = null
    private var showAllOrders: Boolean = false

    // 📅 Tarih Filtreleme
    private var selectedDate = Calendar.getInstance().time
    private lateinit var tvCurrentDate: TextView
    private lateinit var rvDateStrip: RecyclerView
    private lateinit var dateStripAdapter: DateStripAdapter
    private val dateFullFormat = SimpleDateFormat("d MMMM yyyy EEE", Locale("tr", "TR"))
    private var useDateFilter = true

    // 📄 Sayfalama (Pagination)
    private var pageSize = 15
    private var itemsToShow = 15
    private lateinit var footerLoadMore: View

    // 📸 Kamera ve Fotoğraf
    private var pendingPhotoItem: Any? = null
    private var currentPhotoPath: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoPath != null) {
            savePhotoPathToDb(pendingPhotoItem, currentPhotoPath!!)
            loadData()
        }
    }

    // ---- İstatistik paneli view'leri ----
    private lateinit var tvStatsPanelTitle: TextView
    private lateinit var tvStat1Icon: TextView
    private lateinit var tvStat1Value: TextView
    private lateinit var tvStat1Label: TextView
    private lateinit var tvStat2Icon: TextView
    private lateinit var tvStat2Value: TextView
    private lateinit var tvStat2Label: TextView
    private lateinit var tvStat3Icon: TextView
    private lateinit var tvStat3Value: TextView
    private lateinit var tvStat3Label: TextView
    private lateinit var tvStat4Icon: TextView
    private lateinit var tvStat4Value: TextView
    private lateinit var tvStat4Label: TextView

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.metatakip.REFRESH_UI") {
                runOnUiThread {
                    Log.d("GENERIC_LIST", "♻️ Senkronizasyon sinyali alındı, liste yenileniyor...")
                    loadData()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!refreshReceiverRegistered) {
            val filter = IntentFilter("com.example.metatakip.REFRESH_UI")
            ContextCompat.registerReceiver(
                this,
                refreshReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            refreshReceiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (refreshReceiverRegistered) {
            unregisterReceiver(refreshReceiver)
            refreshReceiverRegistered = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { com.example.metatakip.feature_data.ui.mapper.GenericListUiMapper.invalidateFirmaLookup(); com.example.metatakip.feature_data.ui.mapper.GenericListUiMapper.ensureFirmaLookup(this) } catch (_: Exception) {}
        setContentView(R.layout.activity_generic_list)

        helper = GenericListHelperImpl(this)

        firmaDao = MetaTakipFirmaDaoImpl(this)
        etiketSablonDao = EtiketSablonDaoImpl(this)
        unvanDao = MetaTakipUnvanDao(this)
        personelDao = MetaTakipPersonelDao(this)

        pageTitle = intent.getStringExtra("pageTitle")
        listType = intent.getStringExtra("listType") ?: "siparis"
        durumFilter = intent.getStringExtra("filterDurum") ?: "Yeni Sipariş"
        val source = intent.getStringExtra("source") ?: "admin"
// 🔥 YENİ: OrderPopupActivity'den gelen filtreleri al
        filterMusteriUuid = intent.getStringExtra("filterMusteriUuid")
        filterMusteriAdi = intent.getStringExtra("filterMusteriAdi")
        filterTelefon = intent.getStringExtra("filterTelefon")
        showAllOrders = intent.getBooleanExtra("showAllOrders", false)

        Log.d("GENERIC_LIST", "🎯 Filtreler - UUID: $filterMusteriUuid, Ad: $filterMusteriAdi, Tel: $filterTelefon, TümSiparişler: $showAllOrders")
        val normalizedListType = normalize(listType)

        isDeletedCustomerMode = normalizedListType in setOf(
            "musteri_silinen", "musteriler_silinen", "deleted_customers", "customers_deleted"
        )

        isDeletedOrderMode = normalizedListType in setOf(
            "siparis_silinen", "siparisler_silinen", "deleted_orders", "orders_deleted"
        )

        Log.e(
            "GENERIC_LIST",
            "🚀 OPENED | listType=$listType | filter=$durumFilter | source=$source | deletedCustomerMode=$isDeletedCustomerMode | deletedOrderMode=$isDeletedOrderMode"
        )

        val titleText = findViewById<TextView>(R.id.pageTitleText)
        val addButton = findViewById<Button>(R.id.btnAddNew)
        listView = findViewById(R.id.genericListView)
        searchEditText = findViewById(R.id.searchEditText)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        uiMapper = GenericListUiMapper()

        // Sayfalama Footer
        footerLoadMore = layoutInflater.inflate(R.layout.list_footer_load_more, listView, false)
        footerLoadMore.findViewById<Button>(R.id.btnLoadMore).setOnClickListener {
            itemsToShow += pageSize
            updateAdapter(allItems.take(itemsToShow))
        }
        listView.addFooterView(footerLoadMore)
        footerLoadMore.visibility = View.GONE

        // ---- İstatistik paneli view bağlantıları ----
        tvStatsPanelTitle = findViewById(R.id.tvStatsPanelTitle)
        tvStat1Icon  = findViewById(R.id.tvStat1Icon)
        tvStat1Value = findViewById(R.id.tvStat1Value)
        tvStat1Label = findViewById(R.id.tvStat1Label)
        tvStat2Icon  = findViewById(R.id.tvStat2Icon)
        tvStat2Value = findViewById(R.id.tvStat2Value)
        tvStat2Label = findViewById(R.id.tvStat2Label)
        tvStat3Icon  = findViewById(R.id.tvStat3Icon)
        tvStat3Value = findViewById(R.id.tvStat3Value)
        tvStat3Label = findViewById(R.id.tvStat3Label)
        tvStat4Icon  = findViewById(R.id.tvStat4Icon)
        tvStat4Value = findViewById(R.id.tvStat4Value)
        tvStat4Label = findViewById(R.id.tvStat4Label)

        initDateFilter()

        if (!pageTitle.isNullOrBlank()) {
            titleText.text = pageTitle
            titleText.visibility = View.VISIBLE
        } else {
            titleText.visibility = View.GONE
        }

        menuHandler = RightClickMenuHandler(this)
        etiketManager = EtiketManager(this)

        addButton.visibility = View.VISIBLE

        when {
            isDeletedCustomerMode -> {
                addButton.text = "🗑️ Silinen Müşteriler"
                addButton.isEnabled = false
                addButton.alpha = 0.6f
            }
            isDeletedOrderMode -> {
                addButton.text = "🗑️ Silinen Siparişler"
                addButton.isEnabled = false
                addButton.alpha = 0.6f
            }
            else -> {
                when (normalizedListType) {
                    "firma" -> addButton.text = "+ Yeni Firma"
                    "unvan" -> addButton.text = "+ Yeni Ünvan"
                    "personel" -> addButton.text = "+ Yeni Personel"
                    "etiket_sablon" -> addButton.text = "+ Yeni Etiket Şablonu"
                    "mesaj_sablon" -> addButton.text = "+ Yeni Mesaj Şablonu"
                    "urun_tipi", "urun_tipi_" -> addButton.text = "+ Yeni Ürün Tipi"
                    "musteri", "musteriler", "customer", "customers" -> addButton.text = "+ Yeni Müşteri"
                    "siparis", "siparisler", "order", "orders" -> addButton.text = "+ Yeni Sipariş"
                    "call_log", "cagri_kaydi", "cagri" -> {
                        addButton.text = "🗑️ Çağrıları Temizle"
                        addButton.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
                    }
                    else -> addButton.text = "+ Yeni Kayıt"
                }
                addButton.setOnClickListener {
                    Log.e("GENERIC_LIST", "➕ ADD CLICKED | targetTable=$listType")
                    if (normalizedListType in setOf("call_log", "cagri_kaydi", "cagri")) {
                        showClearCallLogsDialog()
                    } else {
                        startActivity(
                            Intent(this, GenericFormActivity::class.java).apply {
                                putExtra("targetTable", listType)
                            }
                        )
                    }
                }
            }
        }

        loadData()

        btnClearSearch.setOnClickListener {
            searchEditText.setText("")
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                filterList(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 🔍 Arama Filtreleme Menüsü
        findViewById<View>(R.id.btnFilterMenu).setOnClickListener {
            showSearchFilterMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (OrderPopupActivity.shouldReopenPopup(this)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                OrderPopupActivity.reopenPopup(this)
            }, 500)
        }
    }

    private fun initDateFilter() {
        val n = normalize(listType)
        useDateFilter = n in setOf("siparis", "siparisler", "order", "orders", "call_log", "cagri_kaydi", "cagri", "musteri", "musteriler", "customer", "customers")
        
        val panel = findViewById<View>(R.id.dateFilterPanel)
        if (!useDateFilter) {
            panel.visibility = View.GONE
            return
        }

        tvCurrentDate = findViewById(R.id.tvCurrentDate)
        rvDateStrip = findViewById(R.id.rvDateStrip)

        updateDateHeader()

        findViewById<View>(R.id.btnPrevDay).setOnClickListener { changeDate(-1) }
        findViewById<View>(R.id.btnNextDay).setOnClickListener { changeDate(1) }
        findViewById<View>(R.id.btnPickDate).setOnClickListener { showDatePickerDialog() }

        // 7 günlük şerit (bugünden itibaren +- 3 gün)
        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -15)
        for (x in 0..30) {
            dates.add(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        dateStripAdapter = DateStripAdapter(dates, selectedDate) { date ->
            selectedDate = date
            updateDateHeader()
            loadData()
        }
        rvDateStrip.adapter = dateStripAdapter
        rvDateStrip.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvDateStrip.scrollToPosition(15) // Bugünün merkezi

        // 📋 TÜMÜNÜ GETİR butonu — tarih filtresini bypass et
        try {
            findViewById<View?>(R.id.btnShowAllOrders)?.setOnClickListener {
                useDateFilter = !useDateFilter
                showAllOrders = !useDateFilter
                Toast.makeText(this, if (useDateFilter) "📅 Tarih filtresi aktif" else "📋 Tüm siparişler gösteriliyor", Toast.LENGTH_SHORT).show()
                loadData()
            }
        } catch (_: Exception) {}
    }

    private fun updateDateHeader() {
        tvCurrentDate.text = dateFullFormat.format(selectedDate)
    }

    private fun changeDate(days: Int) {
        val cal = Calendar.getInstance()
        cal.time = selectedDate
        cal.add(Calendar.DAY_OF_YEAR, days)
        selectedDate = cal.time
        updateDateHeader()
        dateStripAdapter.updateSelectedDate(selectedDate)
        loadData()
    }

    private fun showDatePickerDialog() {
        val cal = Calendar.getInstance()
        cal.time = selectedDate
        android.app.DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            selectedDate = cal.time
            updateDateHeader()
            dateStripAdapter.updateSelectedDate(selectedDate)
            loadData()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadData() {
        Log.d("GENERIC_LIST", "📋 Listeleme başlıyor | type=$listType | filter=$durumFilter")

        // 🔥 ÖZEL FİLTRELEME: Müşteri bazlı sipariş geçmişi
        if (listType == "siparis" && (filterMusteriUuid != null || filterMusteriAdi != null || filterTelefon != null || showAllOrders)) {
            loadFilteredOrders()
            return
        }

        val provider = GenericListDataProvider(this)
        var rawItems: List<Any> = provider.load(listType, durumFilter)

        // 📅 TARİH FİLTRESİ UYGULA
        if (useDateFilter) {
            rawItems = rawItems.filter { item ->
                isSameDay(getItemDate(item), selectedDate)
            }
        }

        allItems    = rawItems.map { uiMapper.map(item = it) }.toMutableList()
        allRawItems = rawItems   // aramada stats için sakla

        itemsToShow = pageSize // Sayfalamayı sıfırla
        filterList(searchEditText.text.toString())

        Toast.makeText(
            this,
            if (allItems.isEmpty()) "Kayıt bulunamadı" else "${allItems.size} kayıt listelendi",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isSameDay(d1: Date?, d2: Date): Boolean {
        if (d1 == null) return false
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun getItemDate(item: Any): Date? {
        return try {
            when (item) {
                is Order -> {
                    // "14/04/2025" formatını parse et; yoksa createdAt (Unix saniye) kullan
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    when {
                        item.duzenlemeTarihi.isNotBlank() -> sdf.parse(item.duzenlemeTarihi)
                        item.teslimAlmaTarihi.isNotBlank() -> sdf.parse(item.teslimAlmaTarihi)
                        item.createdAt > 0L -> {
                            val millis = if (item.createdAt > 1_000_000_000_000L) item.createdAt else item.createdAt * 1000L
                            Date(millis)
                        }
                        else -> null
                    }
                }
                is CallRecord -> {
                    if (item.cagriZamani != null) Date(item.cagriZamani!! * 1000) else null
                }
                is Customer -> {
                    Date(item.updatedAt)
                }
                else -> null
            }
        } catch (ignored: Exception) { null }
    }

    private fun loadFilteredOrders() {
        val orderDao = OrderDaoImpl(this)

        val orders = when {
            !filterMusteriUuid.isNullOrEmpty() -> {
                Log.d("GENERIC_LIST", "🔍 UUID ile filtreleme: $filterMusteriUuid")
                orderDao.getOrdersByMusteriUuid(filterMusteriUuid!!, showAllOrders)
            }
            !filterMusteriAdi.isNullOrEmpty() && !filterTelefon.isNullOrEmpty() -> {
                Log.d("GENERIC_LIST", "🔍 İsim+Telefon ile filtreleme: $filterMusteriAdi / $filterTelefon")
                orderDao.getOrdersByMusteriAdiVeTel(filterMusteriAdi!!, filterTelefon!!, showAllOrders)
            }
            !filterTelefon.isNullOrEmpty() -> {
                Log.d("GENERIC_LIST", "🔍 Telefon ile filtreleme: $filterTelefon")
                orderDao.getOrdersByTelefon(filterTelefon!!, showAllOrders)
            }
            !filterMusteriAdi.isNullOrEmpty() -> {
                Log.d("GENERIC_LIST", "🔍 İsim ile filtreleme: $filterMusteriAdi")
                orderDao.getOrdersByMusteriAdi(filterMusteriAdi!!, showAllOrders)
            }
            showAllOrders -> {
                Log.d("GENERIC_LIST", "🔍 Tüm siparişler gösteriliyor")
                orderDao.getAllOrders()
            }
            else -> {
                Log.d("GENERIC_LIST", "⚠️ Varsayılan: Yeni siparişler")
                orderDao.getOrdersByDurum("Yeni Sipariş")
            }
        }

        allItems    = orders.map { uiMapper.map(item = it) }.toMutableList()
        allRawItems = orders
        
        itemsToShow = pageSize
        filterList(searchEditText.text.toString())

        Toast.makeText(
            this,
            if (allItems.isEmpty()) "Bu müşteriye ait sipariş bulunamadı" else "${allItems.size} sipariş listelendi",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ================================================================
    // GERÇEK VERİ İSTATİSTİKLERİ — liste türüne göre ayrı hesaplama
    // ================================================================
    private fun updateStatsPanel(rawItems: List<Any>) {
        val n = normalize(listType)
        try {
            when (n) {
                // SİPARİŞ: Toplam | Toplam M² | Toplam Tutar | Bekleyen
                "siparis","siparisler","order","orders",
                    "siparis_silinen","siparisler_silinen","deleted_orders","orders_deleted" -> {
                    val list     = rawItems.filterIsInstance<Order>()
                    val toplam   = list.size
                    val toplamM2 = list.sumOf { it.metrekare }
                    val toplamTL = list.sumOf { it.ucret }
                    val bekleyen = list.count { it.durum != "Teslim Edildi" && it.isDeleted == 0 }
                    tvStatsPanelTitle.text = "📦 Sipariş İstatistikleri"
                    setStat(1, "📋", "$toplam",      "Sipariş")
                    setStat(2, "📐", f1(toplamM2),   "Toplam M²")
                    setStat(3, "💰", f0(toplamTL),   "Tutar (₺)")
                    setStat(4, "⏳", "$bekleyen",    "Bekleyen")
                }

                // MÜŞTERİ: Toplam | Firmalı | Telefonlu | Adresli
                "musteri","musteriler","customer","customers",
                    "musteri_silinen","musteriler_silinen","deleted_customers","customers_deleted" -> {
                    val list      = rawItems.filterIsInstance<Customer>()
                    val toplam    = list.size
                    val firmali   = list.count { !it.firmaAdi.isNullOrBlank() && it.firmaAdi != "MEGA" }
                    val telefonlu = list.count { !it.ceptel.isNullOrBlank() }
                    val adresli   = list.count { !it.adres.isNullOrBlank() || !it.bolge.isNullOrBlank() }
                    tvStatsPanelTitle.text = "👤 Müşteri İstatistikleri"
                    setStat(1, "👤", "$toplam",    "Müşteri")
                    setStat(2, "🏢", "$firmali",   "Firmalı")
                    setStat(3, "📞", "$telefonlu", "Telefonlu")
                    setStat(4, "📍", "$adresli",   "Adresli")
                }

                // FİRMA: Toplam | Telefonlu | Vergi Nolu | Müşterili (DB sorgusu)
                "firma" -> {
                    val list      = rawItems.filterIsInstance<Firma>()
                    val toplam    = list.size
                    val telefonlu = list.count { !it.telefon.isNullOrBlank() }
                    val vergiNolu = list.count { !it.vergiNo.isNullOrBlank() }
                    val statMap   = MetaTakipCustomerDao(this).getCustomerStatsByFirma()
                    val musterili = list.count { f -> (statMap[f.firmaAdi] ?: 0) > 0 }
                    tvStatsPanelTitle.text = "🏢 Firma İstatistikleri"
                    setStat(1, "🏢", "$toplam",    "Firma")
                    setStat(2, "📞", "$telefonlu", "Telefonlu")
                    setStat(3, "🔢", "$vergiNolu", "Vergi Nolu")
                    setStat(4, "👤", "$musterili", "Müşterili")
                }

                // ÜRÜN TİPİ: Toplam | Aktif | Pasif | Ort. Fiyat
                "urun_tipi","urun_tipi_","uruntipi" -> {
                    val list     = rawItems.filterIsInstance<UrunTipi>()
                    val toplam   = list.size
                    val aktif    = list.count { it.aktif == 1 }
                    val pasif    = toplam - aktif
                    val ortFiyat = if (list.isNotEmpty()) list.sumOf { it.birimFiyat } / list.size else 0.0
                    tvStatsPanelTitle.text = "🧺 Ürün Tipi İstatistikleri"
                    setStat(1, "🧺", "$toplam",    "Toplam Tür")
                    setStat(2, "✅", "$aktif",     "Aktif")
                    setStat(3, "❌", "$pasif",     "Pasif")
                    setStat(4, "💰", f1(ortFiyat), "Ort. Fiyat")
                }

                // MESAJ ŞABLONU: Toplam | Varsayılan | SMS'li | WApp'li
                "mesaj_sablon","mesajsablon","mesaj" -> {
                    val list       = rawItems.filterIsInstance<MesajSablon>()
                    val toplam     = list.size
                    val varsayilan = list.count { it.varsayilan }
                    val smsli      = list.count { it.smsOnayMesaj.isNotBlank() || it.musteriOlustuMesaj.isNotBlank() }
                    val wapli      = list.count { it.whatsappOnayMesaj.isNotBlank() }
                    tvStatsPanelTitle.text = "💬 Mesaj Şablonu İstatistikleri"
                    setStat(1, "💬", "$toplam",     "Şablon")
                    setStat(2, "⭐", "$varsayilan",  "Varsayılan")
                    setStat(3, "📱", "$smsli",       "SMS'li")
                    setStat(4, "📲", "$wapli",       "WApp'li")
                }

                // ETİKET ŞABLONU: Toplam | Varsayılan
                "etiket_sablon","etiket","label" -> {
                    val list       = rawItems.filterIsInstance<EtiketSablon>()
                    val toplam     = list.size
                    val varsayilan = list.count { it.varsayilan }
                    tvStatsPanelTitle.text = "🏷️ Etiket Şablonu İstatistikleri"
                    setStat(1, "🏷️", "$toplam",     "Şablon")
                    setStat(2, "⭐", "$varsayilan",  "Varsayılan")
                    setStat(3, "—",  "—",            "—")
                    setStat(4, "—",  "—",            "—")
                }

                // PERSONEL
                "personel" -> {
                    val toplam = rawItems.filterIsInstance<Personel>().size
                    tvStatsPanelTitle.text = "👷 Personel İstatistikleri"
                    setStat(1, "👷", "$toplam", "Personel")
                    setStat(2, "—", "—", "—"); setStat(3, "—", "—", "—"); setStat(4, "—", "—", "—")
                }

                // ÜNVAN
                "unvan" -> {
                    val toplam = rawItems.filterIsInstance<Unvan>().size
                    tvStatsPanelTitle.text = "🎖️ Ünvan İstatistikleri"
                    setStat(1, "🎖️", "$toplam", "Ünvan")
                    setStat(2, "—", "—", "—"); setStat(3, "—", "—", "—"); setStat(4, "—", "—", "—")
                }

                // ÇAĞRI KAYDI
                "call_log", "cagri_kaydi", "cagri" -> {
                    val list = rawItems.filterIsInstance<com.example.metatakip.feature_data.entityModel.CallRecord>()
                    val toplam = list.size
                    val gelen = list.count { it.cagriTuru.uppercase() == "GELEN" }
                    val cevapsiz = list.count { it.cagriTuru.uppercase() == "CEVAPSIZ" }
                    val giden = list.count { it.cagriTuru.uppercase() == "GIDEN" }
                    tvStatsPanelTitle.text = "📞 Çağrı İstatistikleri"
                    setStat(1, "📞", "$toplam", "Toplam")
                    setStat(2, "📥", "$gelen", "Gelen")
                    setStat(3, "❌", "$cevapsiz", "Cevapsiz")
                    setStat(4, "📤", "$giden", "Giden")
                }

                // GENEL FALLBACK
                else -> {
                    tvStatsPanelTitle.text = "📊 İstatistikler"
                    setStat(1, "📋", "${rawItems.size}", "Toplam")
                    setStat(2, "—", "—", "—"); setStat(3, "—", "—", "—"); setStat(4, "—", "—", "—")
                }
            }
        } catch (e: Exception) {
            Log.e("GENERIC_LIST", "Stats panel hatası: ${e.message}")
        }
    }

    private fun setStat(slot: Int, icon: String, value: String, label: String) {
        when (slot) {
            1 -> { tvStat1Icon.text = icon; tvStat1Value.text = value; tvStat1Label.text = label }
            2 -> { tvStat2Icon.text = icon; tvStat2Value.text = value; tvStat2Label.text = label }
            3 -> { tvStat3Icon.text = icon; tvStat3Value.text = value; tvStat3Label.text = label }
            4 -> { tvStat4Icon.text = icon; tvStat4Value.text = value; tvStat4Label.text = label }
        }
    }

    private fun f1(d: Double) = String.format(Locale.US, "%.1f", d)
    private fun f0(d: Double) = String.format(Locale.US, "%.0f", d)

    private fun filterList(query: String) {
        val searchText = query.lowercase(Locale.getDefault()).trim()

        if (searchText.isEmpty()) {
            // Arama boşsa sayfalamalı listeye geri dön
            updateAdapter(allItems.take(itemsToShow))
            updateStatsPanel(allRawItems)
            return
        }

        val filteredItems = allItems.filter { uiItem ->
            val titleMatch = uiItem.title.lowercase(Locale.getDefault()).contains(searchText)
            val subtitleMatch = uiItem.subtitle?.lowercase(Locale.getDefault())?.contains(searchText) == true
            titleMatch || subtitleMatch
        }

        // Filtrelenmiş adapter'ı kur
        adapter = GenericListAdapter(
            context = this,
            items = filteredItems,
            onCameraClick = { uiItem -> handleCameraClick(uiItem.payload) },
            onItemClick = { uiItem ->
                showActionMenu(uiItem.payload)
            }
        )
        listView.adapter = adapter

        // Arama yaparken pagination footer'ı gizle
        footerLoadMore.visibility = View.GONE

        // Arama sonucuna göre stats panelini güncelle
        val filteredRaw = filteredItems.mapNotNull { it.payload }
        updateStatsPanel(filteredRaw)
    }

    private fun handleCameraClick(item: Any) {
        Log.d("CAMERA_DEBUG", "Kamera butonuna tıklandı: $item")
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingPhotoItem = item
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }

        pendingPhotoItem = item
        val photoFile = try {
            createImageFile()
        } catch (ex: Exception) {
            null
        }

        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                it
            )
            currentPhotoPath = it.absolutePath
            takePictureLauncher.launch(photoURI)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pendingPhotoItem?.let { handleCameraClick(it) }
        } else {
            Toast.makeText(this, "Fotoğraf çekmek için kamera izni gerekiyor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PHOTO_${timeStamp}_", ".jpg", storageDir)
    }

    private fun savePhotoPathToDb(item: Any?, path: String) {
        when (item) {
            is Order -> {
                OrderDaoImpl(this).updateOrderPhoto(item.id, path)
                Toast.makeText(this, "Sipariş fotoğrafı kaydedildi", Toast.LENGTH_SHORT).show()
            }
            is Customer -> {
                MetaTakipCustomerDao(this).updateCustomerPhoto(item.id, path)
                Toast.makeText(this, "Müşteri fotoğrafı kaydedildi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearCallLogsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Çağrıları Temizle")
            .setMessage("Tüm çağrı kayıtlarını silmek istediğinize emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Hepsini Sil") { _, _ ->
                val dao = CallLogsDao(this)
                if (dao.deleteAllCallLogs()) {
                    Toast.makeText(this, "Tüm çağrı kayıtları temizlendi", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showSearchFilterMenu() {
        val menuItems = mutableListOf<RightClickMenuHandler.ModernMenuItem>()

        // 1. Tarih Filtresi Toggle
        val dateToggleText = if (useDateFilter) "📅 Tarih Filtresini Kapat" else "📅 Tarih Filtresini Aç"
        menuItems.add(RightClickMenuHandler.ModernMenuItem(dateToggleText, R.drawable.ic_calendar, 0xFF1976D2.toInt()) {
            useDateFilter = !useDateFilter
            findViewById<View>(R.id.dateFilterPanel).visibility = if (useDateFilter) View.VISIBLE else View.GONE
            loadData()
            Toast.makeText(this, if (useDateFilter) "Tarih filtresi aktif" else "Tarih filtresi kapatıldı", Toast.LENGTH_SHORT).show()
        })

        // 2. Sıralama Seçenekleri
        menuItems.add(RightClickMenuHandler.ModernMenuItem("🔤 İsim (A-Z)", R.drawable.ic_chevron_right, 0xFF64748B.toInt()) {
            allItems.sortBy { it.title.lowercase(Locale.getDefault()) }
            updateAdapter(allItems.take(itemsToShow))
        })

        menuItems.add(RightClickMenuHandler.ModernMenuItem("🔤 İsim (Z-A)", R.drawable.ic_chevron_right, 0xFF64748B.toInt()) {
            allItems.sortByDescending { it.title.lowercase(Locale.getDefault()) }
            updateAdapter(allItems.take(itemsToShow))
        })

        // 3. Sipariş Özel Filtreleri
        val n = normalize(listType)
        if (n in setOf("siparis", "siparisler", "order", "orders")) {
            menuItems.add(RightClickMenuHandler.ModernMenuItem("💰 Tutar (Azalan)", R.drawable.badge_success, 0xFF43A047.toInt()) {
                allItems.sortByDescending { it.extra("tutar")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0 }
                updateAdapter(allItems.take(itemsToShow))
            })
            
            menuItems.add(RightClickMenuHandler.ModernMenuItem("📐 Alan (Azalan)", R.drawable.ic_product_24, 0xFFF57C00.toInt()) {
                allItems.sortByDescending { it.extra("m2")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0 }
                updateAdapter(allItems.take(itemsToShow))
            })
        }

        menuHandler.showModernMenu("Arama ve Filtreleme", menuItems, autoDismiss = true)
    }

    private fun showActionMenu(selectedItem: Any) {
        when (selectedItem) {
            is Order -> {
                menuHandler.showSiparisMenu(
                    siparis = selectedItem,
                    onDataChanged = { loadData() },
                    onEtiketYazdir = {
                        LabelFlowController(
                            context = this,
                            etiketManager = etiketManager,
                            mode = LabelMode.PRINT,
                            onPrint = { printText -> helper.startPrintFlow(printText) }
                        ).startWithSablonPicker(selectedItem)
                    }
                )
            }
            is Customer -> {
                menuHandler.showCustomerMenu(
                    customer = selectedItem,
                    onDataChanged = { loadData() },
                    onEtiketYazdir = {
                        LabelFlowController(
                            context = this,
                            etiketManager = etiketManager,
                            mode = LabelMode.PRINT,
                            onPrint = { printText -> helper.startPrintFlow(printText) }
                        ).startWithSablonPicker(selectedItem)
                    }
                )
            }
            is Unvan -> {
                menuHandler.showAdminMenu(
                    title = selectedItem.ad,
                    targetTable = "unvan",
                    recordId = selectedItem.id,
                    onDelete = { id -> unvanDao.deleteUnvanById(id) },
                    onDataChanged = { loadData() }
                )
            }
            is Personel -> {
                menuHandler.showAdminMenu(
                    title = selectedItem.adSoyad,
                    targetTable = "personel",
                    recordId = selectedItem.id,
                    onDelete = { id -> personelDao.deletePersonel(id) },
                    onDataChanged = { loadData() }
                )
            }
            is Firma -> {
                menuHandler.showAdminMenu(
                    title = selectedItem.firmaAdi,
                    targetTable = "firma",
                    recordId = selectedItem.id,
                    onDelete = { id -> firmaDao.deleteFirma(id) },
                    onDataChanged = { loadData() }
                )
            }
            is MesajSablon -> showMesajSablonMenu(selectedItem)
            is EtiketSablon -> showEtiketSablonMenu(selectedItem)
            is UrunTipi -> showUrunTipiMenu(selectedItem)
            is CallRecord -> menuHandler.showCallRecordMenu(selectedItem, onDataChanged = { loadData() })
            else -> Toast.makeText(this, "Bu kayıt için menü tanımlı değil", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMesajSablonMenu(sablon: MesajSablon) {
        val sessionManager = SessionManager(this)
        val ops = arrayOf("✏️ Düzenle", "🗑️ Sil")

        AlertDialog.Builder(this)
            .setTitle(sablon.baslik ?: "Mesaj Şablonu")
            .setItems(ops) { _, which ->
                when (ops[which]) {
                    "✏️ Düzenle" -> {
                        val intent = Intent(this, GenericFormActivity::class.java).apply {
                            putExtra("targetTable", "mesaj_sablon")
                            putExtra("edit_mode", true)
                            putExtra("id", sablon.id)
                        }
                        startActivity(intent)
                    }
                    "🗑️ Sil" -> {
                        if (!sessionManager.isAdmin) {
                            Toast.makeText(this, "❌ Sadece admin mesaj şablonu silebilir", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Silme Onayı")
                            .setMessage("'${sablon.baslik}' mesaj şablonunu silmek istiyor musunuz?")
                            .setPositiveButton("EVET, SİL") { _, _ ->
                                try {
                                    val dao = MesajSablonDaoImpl(this)
                                    val success = dao.delete(sablon.id)
                                    Toast.makeText(
                                        this,
                                        if (success) "✅ Mesaj şablonu silindi" else "❌ Silme başarısız",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (success) loadData()
                                } catch (e: Exception) {
                                    Toast.makeText(this, "❌ Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("İptal", null)
                            .show()
                    }
                }
            }
            .show()
    }

    // ✅ Doğru - updateAdapter fonksiyonu
    private fun updateAdapter(items: List<GenericListRowAdapterUiModel>) {
        adapter = GenericListAdapter(
            context = this,
            items = items,
            onCameraClick = { uiItem -> handleCameraClick(uiItem.payload) },
            onItemClick = { uiItem ->
                showActionMenu(uiItem.payload)
            }
        )
        listView.adapter = adapter
        
        // Footer görünürlüğünü güncelle
        footerLoadMore.visibility = if (allItems.size > items.size) View.VISIBLE else View.GONE
    }

    private fun showUrunTipiMenu(urunTipi: UrunTipi) {
        val ops = arrayOf("✏️ Düzenle", "🗑️ Sil")

        AlertDialog.Builder(this)
            .setTitle(urunTipi.ad ?: "Ürün Tipi")
            .setItems(ops) { _, which ->
                when (ops[which]) {
                    "✏️ Düzenle" -> {
                        val intent = Intent(this, GenericFormActivity::class.java).apply {
                            putExtra("targetTable", "urun_tipi")
                            putExtra("edit_mode", true)
                            putExtra("id", urunTipi.id)
                        }
                        startActivity(intent)
                    }
                    "🗑️ Sil" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Silme Onayı")
                            .setMessage("'${urunTipi.ad}' ürün tipini silmek istiyor musunuz?")
                            .setPositiveButton("EVET, SİL") { _, _ ->
                                try {
                                    val dao = UrunTipiDaoImpl(this)
                                    val success = dao.delete(urunTipi.id)
                                    Toast.makeText(
                                        this,
                                        if (success) "✅ Ürün tipi silindi" else "❌ Silme başarısız",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (success) loadData()
                                } catch (e: Exception) {
                                    Toast.makeText(this, "❌ Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("İptal", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showEtiketSablonMenu(sablon: EtiketSablon) {
        val sessionManager = SessionManager(this)
        val userId = sessionManager.userId.toInt()

        val ops = if (sablon.varsayilan) {
            arrayOf("🧾 Etiket Ayarlarını Aç", "✏️ Düzenle", "✅ Varsayılan (Aktif)", "🗑️ Sil")
        } else {
            arrayOf("🧾 Etiket Ayarlarını Aç", "✏️ Düzenle", "⭐ Varsayılan Yap", "🗑️ Sil")
        }

        AlertDialog.Builder(this)
            .setTitle(sablon.adi ?: "Etiket Şablonu")
            .setItems(ops) { _, which ->
                when (ops[which]) {
                    "🧾 Etiket Ayarlarını Aç" -> openLabelFlowForTemplate(sablon.id)
                    "✏️ Düzenle" -> {
                        val intent = Intent(this, GenericFormActivity::class.java).apply {
                            putExtra("targetTable", "etiket_sablon")
                            putExtra("edit_mode", true)
                            putExtra("id", sablon.id)
                        }
                        startActivity(intent)
                    }
                    "⭐ Varsayılan Yap" -> {
                        val success = etiketSablonDao.setVarsayilanSablon(userId, sablon.id)
                        Toast.makeText(
                            this,
                            if (success) "⭐ '${sablon.adi}' varsayılan şablon yapıldı" else "❌ Varsayılan yapılamadı",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadData()
                    }
                    "✅ Varsayılan (Aktif)" -> {
                        Toast.makeText(this, "✅ '${sablon.adi}' zaten varsayılan şablon", Toast.LENGTH_SHORT).show()
                    }
                    "🗑️ Sil" -> {
                        if (!sessionManager.isAdmin) {
                            Toast.makeText(this, "❌ Sadece admin silebilir", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Silme Onayı")
                            .setMessage("'${sablon.adi}' etiket şablonunu silmek istiyor musunuz?")
                            .setPositiveButton("EVET, SİL") { _, _ ->
                                etiketSablonDao.deleteSablon(sablon.id)
                                Toast.makeText(this, "✅ Şablon silindi", Toast.LENGTH_SHORT).show()
                                loadData()
                            }
                            .setNegativeButton("İptal", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun openLabelFlowForTemplate(sablonId: Long) {
        val controller = LabelFlowController(
            context = this,
            etiketManager = etiketManager,
            mode = LabelMode.CONFIG
        )
        controller.setActiveSablon(sablonId)
        controller.start(Order())
    }

    private fun normalize(s: String): String {
        return s.trim()
            .lowercase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_")
    }

    private fun addTestSiparis(): Boolean {
        val dao = OrderDaoImpl(this)
        val customerDao = MetaTakipCustomerDao(this)
        val customers = customerDao.getAllCustomers()
        val testCustomerId = if (customers.isNotEmpty()) customers.first().id else 1L

        val testSiparis = Order().apply {
            musteriId = testCustomerId
            musteriAdi = "TEST Müşteri"
            musteriTelefon = "555-123-4567"
            firmaAdi = "TEST Firma"
            urunTipi = "TEST Ürün"
            durum = "Yeni Sipariş"
            isDeleted = 0
        }

        val id = dao.addSiparis(testSiparis)

        return if (id > 0) {
            Log.d("TEST_SIPARIS", "✅ Test siparişi eklendi: ID=$id")
            Toast.makeText(this, "Test siparişi eklendi! (ID: $id)", Toast.LENGTH_LONG).show()
            loadData()
            true
        } else {
            Log.d("TEST_SIPARIS", "❌ Test siparişi eklenemedi")
            Toast.makeText(this, "Test siparişi eklenemedi!", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun showDeletedSiparis() {
        val dao = OrderDaoImpl(this)
        val deletedSiparis = dao.getDeletedSiparis()

        if (deletedSiparis.isEmpty()) {
            Toast.makeText(this, "Silinmiş sipariş bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val items = deletedSiparis.map {
            "${it.id} - ${it.musteriAdi} (${it.durum}) - ${it.deleteReason ?: "Sebep belirtilmemiş"}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Silinmiş Siparişler (${deletedSiparis.size} adet)")
            .setItems(items) { _, which ->
                val selected = deletedSiparis[which]
                showRestoreDialog(selected)
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showRestoreDialog(siparis: Order) {
        AlertDialog.Builder(this)
            .setTitle("Siparişi Geri Yükle")
            .setMessage(
                "'${siparis.musteriAdi}' siparişini geri yüklemek istiyor musunuz?\n" +
                        "ID: ${siparis.id}\n" +
                        "Silinme sebebi: ${siparis.deleteReason}"
            )
            .setPositiveButton("Evet, Geri Yükle") { _, _ ->
                val dao = OrderDaoImpl(this)
                val success = dao.restoreSiparis(siparis.id)
                if (success) {
                    Toast.makeText(this, "✅ Sipariş geri yüklendi!", Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Toast.makeText(this, "❌ Geri yükleme başarısız", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        helper.onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        helper.onActivityResult(requestCode, resultCode, data)
    }
}
