package com.example.metatakip.controllers.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.R
import com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity
import com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity

class OrderPopupActivity : AppCompatActivity() {

    private lateinit var tvMusteriAdi: TextView
    private lateinit var tvTelefon: TextView
    private lateinit var tvUrunTipi: TextView
    private lateinit var tvMetrekare: TextView
    private lateinit var tvUcret: TextView
    private lateinit var tvFirma: TextView
    private lateinit var btnSiparisDetay: Button
    private lateinit var btnSiparisGecmisi: Button
    private lateinit var btnWhatsApp: Button
    private lateinit var btnTumSiparisler: Button
    private lateinit var btnKapat: Button

    private var orderId: Long = -1L
    private var musteriUuid: String = ""  // 🔥 YENİ: Küresel benzersiz kimlik
    private var musteriAdi: String = ""
    private var telefon: String = ""
    private var urunTipi: String = ""
    private var metrekare: Double = 0.0
    private var ucret: Double = 0.0
    private var firmaAdi: String = ""

    companion object {
        private const val PREFS_NAME = "order_popup_prefs"
        private const val KEY_ORDER_ID = "order_id"
        private const val KEY_MUSTERI_UUID = "musteri_uuid"  // 🔥 YENİ: uuid ile sakla
        private const val KEY_MUSTERI_ADI = "musteri_adi"
        private const val KEY_TELEFON = "telefon"
        private const val KEY_URUN_TIPI = "urun_tipi"
        private const val KEY_METREKARE = "metrekare"
        private const val KEY_UCRET = "ucret"
        private const val KEY_FIRMA_ADI = "firma_adi"
        private const val KEY_SHOULD_REOPEN = "should_reopen"

        fun savePopupData(
            context: Context,
            orderId: Long,
            musteriUuid: String,  // 🔥 YENİ parametre
            musteriAdi: String,
            telefon: String,
            urunTipi: String,
            metrekare: Double,
            ucret: Double,
            firmaAdi: String
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong(KEY_ORDER_ID, orderId)
                putString(KEY_MUSTERI_UUID, musteriUuid)  // 🔥 YENİ
                putString(KEY_MUSTERI_ADI, musteriAdi)
                putString(KEY_TELEFON, telefon)
                putString(KEY_URUN_TIPI, urunTipi)
                putFloat(KEY_METREKARE, metrekare.toFloat())
                putFloat(KEY_UCRET, ucret.toFloat())
                putString(KEY_FIRMA_ADI, firmaAdi)
                putBoolean(KEY_SHOULD_REOPEN, true)
                apply()
            }
        }

        fun clearPopupData(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }

        fun shouldReopenPopup(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SHOULD_REOPEN, false)
        }

        fun getSavedOrderId(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_ORDER_ID, -1L)
        }

        fun getSavedMusteriUuid(context: Context): String {  // 🔥 YENİ
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_MUSTERI_UUID, "") ?: ""
        }

        fun getSavedMusteriAdi(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_MUSTERI_ADI, "Bilinmiyor") ?: "Bilinmiyor"
        }

        fun getSavedTelefon(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_TELEFON, "Bilinmiyor") ?: "Bilinmiyor"
        }

        fun getSavedUrunTipi(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_URUN_TIPI, "Bilinmiyor") ?: "Bilinmiyor"
        }

        fun getSavedMetrekare(context: Context): Double {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_METREKARE, 0.0f).toDouble()
        }

        fun getSavedUcret(context: Context): Double {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_UCRET, 0.0f).toDouble()
        }

        fun getSavedFirmaAdi(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_FIRMA_ADI, "Bilinmiyor") ?: "Bilinmiyor"
        }

        fun reopenPopup(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val orderId = prefs.getLong(KEY_ORDER_ID, -1L)
            if (orderId != -1L) {
                val intent = Intent(context, OrderPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("order_id", orderId)
                    putExtra("musteri_uuid", prefs.getString(KEY_MUSTERI_UUID, ""))  // 🔥 YENİ
                    putExtra("musteri_adi", prefs.getString(KEY_MUSTERI_ADI, "Bilinmiyor"))
                    putExtra("musteri_telefon", prefs.getString(KEY_TELEFON, "Bilinmiyor"))
                    putExtra("urun_tipi", prefs.getString(KEY_URUN_TIPI, "Bilinmiyor"))
                    putExtra("metrekare", prefs.getFloat(KEY_METREKARE, 0.0f).toDouble())
                    putExtra("ucret", prefs.getFloat(KEY_UCRET, 0.0f).toDouble())
                    putExtra("firma_adi", prefs.getString(KEY_FIRMA_ADI, "Bilinmiyor"))
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_popup)

        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        initViews()
        loadData()

        btnSiparisDetay.setOnClickListener {
            saveCurrentData()
            val intent = Intent(this, GenericFormActivity::class.java).apply {
                putExtra("targetTable", "siparis")
                putExtra("edit_mode", true)
                putExtra("id", orderId)
                putExtra("from_popup", true)
            }
            startActivity(intent)
        }

        // 🔥 Sipariş Geçmişi - UUID ile kesin eşleştirme
        btnSiparisGecmisi.setOnClickListener {
            saveCurrentData()

            if (musteriUuid.isNotEmpty()) {
                // UUID ile filtreleme (EN GÜVENLİ YÖNTEM)
                val intent = Intent(this, GenericListActivity::class.java).apply {
                    putExtra("listType", "siparis")
                    putExtra("filterMusteriUuid", musteriUuid)  // 🔥 UUID ile filtrele
                    putExtra("pageTitle", "📜 ${musteriAdi} - SİPARİŞ GEÇMİŞİ")
                    putExtra("showAllOrders", true)  // Tüm siparişleri göster
                    putExtra("from_popup", true)
                }
                startActivity(intent)
            } else {
                // Fallback: UUID yoksa telefon ve isim ile filtrele
                val cleanPhone = telefon.replace("\\s".toRegex(), "")
                    .replace("-", "")
                    .replace("(", "")
                    .replace(")", "")

                val intent = Intent(this, GenericListActivity::class.java).apply {
                    putExtra("listType", "siparis")
                    putExtra("filterMusteriAdi", musteriAdi)
                    putExtra("filterTelefon", cleanPhone)
                    putExtra("pageTitle", "📜 ${musteriAdi} - SİPARİŞ GEÇMİŞİ")
                    putExtra("showAllOrders", true)
                    putExtra("from_popup", true)
                }
                startActivity(intent)
            }
        }

        btnWhatsApp.setOnClickListener {
            saveCurrentData()
            val cleanPhone = telefon.replace("\\s".toRegex(), "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://wa.me/$cleanPhone")
            }
            startActivity(intent)
        }

        btnTumSiparisler.setOnClickListener {
            saveCurrentData()
            val intent = Intent(this, GenericListActivity::class.java).apply {
                putExtra("listType", "siparis")
                putExtra("filterDurum", "Yeni Sipariş")
                putExtra("pageTitle", "🛒 ALINACAK SİPARİŞLER")
                putExtra("from_popup", true)
            }
            startActivity(intent)
        }

        btnKapat.setOnClickListener {
            clearPopupData(this)
            finish()
        }
    }

    private fun saveCurrentData() {
        savePopupData(
            this,
            orderId,
            musteriUuid,  // 🔥 YENİ
            tvMusteriAdi.text.toString(),
            tvTelefon.text.toString(),
            tvUrunTipi.text.toString(),
            metrekare,
            ucret,
            tvFirma.text.toString()
        )
    }

    private fun initViews() {
        tvMusteriAdi = findViewById(R.id.tvMusteriAdi)
        tvTelefon = findViewById(R.id.tvTelefon)
        tvUrunTipi = findViewById(R.id.tvUrunTipi)
        tvMetrekare = findViewById(R.id.tvMetrekare)
        tvUcret = findViewById(R.id.tvUcret)
        tvFirma = findViewById(R.id.tvFirma)
        btnSiparisDetay = findViewById(R.id.btnSiparisDetay)
        btnSiparisGecmisi = findViewById(R.id.btnSiparisGecmisi)
        btnWhatsApp = findViewById(R.id.btnWhatsApp)
        btnTumSiparisler = findViewById(R.id.btnTumSiparisler)
        btnKapat = findViewById(R.id.btnKapat)
    }

    private fun loadData() {
        orderId = intent.getLongExtra("order_id", -1L)

        musteriUuid = intent.getStringExtra("musteri_uuid") ?: ""  // 🔥 YENİ
        musteriAdi = intent.getStringExtra("musteri_adi") ?: "Bilinmiyor"
        tvMusteriAdi.text = musteriAdi

        telefon = intent.getStringExtra("musteri_telefon") ?: "Bilinmiyor"
        tvTelefon.text = telefon

        urunTipi = intent.getStringExtra("urun_tipi") ?: "Bilinmiyor"
        tvUrunTipi.text = urunTipi

        metrekare = intent.getDoubleExtra("metrekare", 0.0)
        tvMetrekare.text = String.format("%.2f m²", metrekare)

        ucret = intent.getDoubleExtra("ucret", 0.0)
        tvUcret.text = String.format("%.2f ₺", ucret)

        firmaAdi = intent.getStringExtra("firma_adi") ?: "Bilinmiyor"
        tvFirma.text = firmaAdi
    }
}