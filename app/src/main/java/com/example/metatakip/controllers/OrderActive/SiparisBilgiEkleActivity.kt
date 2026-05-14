package com.example.metatakip.controllers.OrderActive

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.metatakip.R
import com.example.metatakip.controllers.HomeActive.HomeActivity
import com.example.metatakip.controllers.OrderActive.DialogUrunEkleActivity
import com.example.metatakip.controllers.OrderActive.TempUrunSepeti
import com.example.metatakip.controllers.adminConfigiration.AdminConfigurationActivity
import com.example.metatakip.data.metaTakipDb.Helper.MesajPlaceholderHelper
import com.example.metatakip.feature.admin.data.MesajSablonDaoImpl
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.entityModel.Urun
import com.example.metatakip.feature_backup.util.ChangeLogManager
import com.example.metatakip.feature_backup.data.ChangeLog.ActionType
import com.example.metatakip.feature_data.entityModel.Order
import com.example.metatakip.feature_data.db.MetaTakipDb
import dao.MetaTakipCustomerDao
import dao.MetaTakipUrunDao
import dao.SMSHelper
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class SiparisBilgiEkleActivity : AppCompatActivity() {

    private lateinit var urunDao: MetaTakipUrunDao
    private lateinit var siparisDao: OrderDaoImpl
    private lateinit var customerDao: MetaTakipCustomerDao
    private lateinit var mesajSablonDao: MesajSablonDaoImpl
    private lateinit var mesajHelper: MesajPlaceholderHelper

    private lateinit var layoutUrunListe: LinearLayout
    private lateinit var spSiparisDurumu: Spinner
    private lateinit var etTeslimTarihi: EditText
    private lateinit var etSiparisNotu: EditText
    private lateinit var btnKaydet: Button
    private lateinit var btnUrunEkle: Button
    private lateinit var btnTumunuSil: Button
    private lateinit var btnIndirimEkle: Button
    private lateinit var btnUcretEkle: Button
    private lateinit var btnWhatsapp: ImageButton
    private lateinit var btnKopyalaNotlar: ImageButton

    private lateinit var tvToplamAdet: TextView
    private lateinit var tvToplamM2: TextView
    private lateinit var tvGenelToplam: TextView

    private var siparisId: Long = -1
    private var musteriId: Long = -1
    private var musteriAdi: String = ""
    private var musteriTelefon: String = ""
    private var firmaId: Long = -1

    private var indirimTutari: Double = 0.0
    private var ekUcretTutari: Double = 0.0
    private var indirimTip: String = "TL"
    private var ekUcretTip: String = "TL"
    private var sonGenelToplam: Double = 0.0
    private var sonGenelM2: Double = 0.0

    private var bekleyenTelefon: String? = null
    private var bekleyenMesaj: String? = null

    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val tel = bekleyenTelefon
            val msg = bekleyenMesaj
            if (tel != null && msg != null) {
                SMSHelper(this).sendSMS(tel, msg)
                Toast.makeText(this, "✅ SMS gönderildi", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "❌ SMS izni reddedildi", Toast.LENGTH_LONG).show()
        }
        sorWhatsApp(buildMesaj())
    }

    private val urunEkleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) listeyiYenile()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_siparis_bilgi_ekle)

        urunDao = MetaTakipUrunDao(this)
        siparisDao = OrderDaoImpl(this)
        customerDao = MetaTakipCustomerDao(this)
        mesajSablonDao = MesajSablonDaoImpl(this)
        mesajHelper = MesajPlaceholderHelper(this)

        siparisId = intent.getLongExtra("siparisId", -1)

        initViews()
        loadMusteriBilgileri()
        setupSpinner()
        setupListeners()
        loadSavedData()
        listeyiYenile()
    }

    private fun initViews() {
        layoutUrunListe = findViewById(R.id.layoutUrunListe)
        spSiparisDurumu = findViewById(R.id.spSiparisDurumu)
        etTeslimTarihi = findViewById(R.id.etTeslimTarihi)
        etSiparisNotu = findViewById(R.id.etSiparisNotu)
        btnKaydet = findViewById(R.id.btnKaydet)
        btnUrunEkle = findViewById(R.id.btnUrunEkle)
        btnTumunuSil = findViewById(R.id.btnTumunuSil)
        btnIndirimEkle = findViewById(R.id.btnIndirimEkle)
        btnUcretEkle = findViewById(R.id.btnUcretEkle)
        btnWhatsapp = findViewById(R.id.btnWhatsapp)
        btnKopyalaNotlar = findViewById(R.id.btnKopyalaNotlar)
        tvToplamAdet = findViewById(R.id.tvToplamAdet)
        tvToplamM2 = findViewById(R.id.tvToplamM2)
        tvGenelToplam = findViewById(R.id.tvGenelToplam)
    }

    private fun loadMusteriBilgileri() {
        val siparis = siparisDao.getSiparisById(siparisId)
        if (siparis != null) {
            musteriId = siparis.musteriId
            musteriAdi = siparis.musteriAdi
            val customer = customerDao.getCustomerById(musteriId)
            musteriTelefon = customer?.ceptel ?: ""
            firmaId = siparis.firmaId
            findViewById<TextView>(R.id.tvHeader).text = musteriAdi
            findViewById<TextView>(R.id.tvMusteriTelefon).text = musteriTelefon
        }
    }

    private fun setupSpinner() {
        val durumlar = arrayOf("Yeni Sipariş", "Teslim Alındı", "Dağıtılacak", "Teslim Edildi", "Tekrar İşleme Alındı")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, durumlar)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSiparisDurumu.adapter = adapter
    }

    private fun setupListeners() {
        etTeslimTarihi.setOnClickListener { tarihSec() }

        btnUrunEkle.setOnClickListener {
            if (!hasAnyUrunTipi()) {
                AlertDialog.Builder(this)
                    .setTitle("Ürün Tipi Bulunamadı")
                    .setMessage("Henüz ürün tipi tanımlanmamış.\n\nÜrün ekleyebilmek için önce Ayarlar menüsünden en az bir ürün tipi ekleyin.")
                    .setPositiveButton("⚙️  Ayarlara Git") { _, _ ->
                        startActivity(Intent(this, AdminConfigurationActivity::class.java))
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            } else {
                val i = Intent(this, DialogUrunEkleActivity::class.java).apply {
                    putExtra("siparisId", siparisId)
                }
                urunEkleLauncher.launch(i)
            }
        }

        btnTumunuSil.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sil")
                .setMessage("Tüm ürünler temizlensin mi?")
                .setPositiveButton("Evet") { _, _ ->
                    urunDao.softDeleteUrunlerBySiparisId(siparisId)
                    TempUrunSepeti.clear(siparisId)
                    indirimTutari = 0.0
                    ekUcretTutari = 0.0
                    listeyiYenile()
                }
                .setNegativeButton("Hayır", null)
                .show()
        }

        btnIndirimEkle.setOnClickListener {
            if (urunDao.getUrunlerBySiparisId(siparisId).isEmpty() && TempUrunSepeti.getItems(siparisId).isEmpty()) {
                Toast.makeText(this, "Önce ürün ekleyiniz", Toast.LENGTH_SHORT).show()
            } else showIndirimDialog()
        }
        btnUcretEkle.setOnClickListener {
            if (urunDao.getUrunlerBySiparisId(siparisId).isEmpty() && TempUrunSepeti.getItems(siparisId).isEmpty()) {
                Toast.makeText(this, "Önce ürün ekleyiniz", Toast.LENGTH_SHORT).show()
            } else showUcretDialog()
        }
        btnKaydet.setOnClickListener { kaydetVeBaslat() }
        btnWhatsapp.setOnClickListener { sendWhatsAppMessage() }
        btnKopyalaNotlar.setOnClickListener {
            val ozet = buildOrderSummary()
            etSiparisNotu.setText(ozet)
            etSiparisNotu.setSelection(ozet.length)
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sipariş", ozet))
            Toast.makeText(this, "📋 Sipariş özeti nota yazıldı", Toast.LENGTH_SHORT).show()
        }
    }

    /** urun_tipi tablosunda aktif kayıt var mı? */
    private fun hasAnyUrunTipi(): Boolean {
        return try {
            val db = MetaTakipDb.getInstance(this).readableDatabase
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM urun_tipi WHERE (aktif IS NULL OR aktif = 1)", null
            )
            cursor.use { c -> if (c.moveToFirst()) c.getInt(0) > 0 else false }
        } catch (e: Exception) {
            Log.w(TAG, "hasAnyUrunTipi hata: ${e.message}")
            true
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  KAYDET → SMS → WHATSAPP → ANA SAYFA adım adım
    // ─────────────────────────────────────────────────────────────────

    private fun kaydetVeBaslat() {
        val tempItems = TempUrunSepeti.getItems(siparisId)
        tempItems.forEach { item ->
            siparisDao.addUrun(siparisId, item.ad, item.ad, item.adet, item.m2, item.fiyat, item.toplamTutar)
        }
        TempUrunSepeti.clear(siparisId)

        val siparis = siparisDao.getSiparisById(siparisId)
        if (siparis != null) {
            siparis.ucret = sonGenelToplam
            siparis.metrekare = sonGenelM2
            siparis.indirim = indirimTutari
            siparis.ekUcret = ekUcretTutari
            // Kaydet → otomatik "Teslim Alındı" (Home'da Teslim Alınanlar listesine geçer)
            siparis.durum = "Teslim Alındı"
            siparis.teslimAlmaTarihi = etTeslimTarihi.text.toString()
            siparis.notlar = etSiparisNotu.text.toString()
            if (siparisDao.updateSiparisById(siparisId, siparis)) {
                ChangeLogManager.logChange(this, "siparis", ActionType.UPDATE, siparisId, "Kaydedildi")
                Toast.makeText(this, "✅ Kaydedildi — Teslim Alındı", Toast.LENGTH_SHORT).show()
                sorSMS()
            }
        }
    }

    /** Adım 1: SMS */
    private fun sorSMS() {
        val msg = buildMesaj()
        AlertDialog.Builder(this)
            .setTitle("📱 SMS")
            .setMessage("Müşteriye SMS gönderilsin mi?\n\n$musteriAdi\n$musteriTelefon")
            .setPositiveButton("Evet") { _, _ -> sendSmsKontrollu(musteriTelefon, msg) }
            .setNegativeButton("Hayır") { _, _ -> sorWhatsApp(msg) }
            .setCancelable(false)
            .show()
    }

    /** Adım 2: WhatsApp */
    private fun sorWhatsApp(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("💬 WhatsApp")
            .setMessage("WhatsApp mesajı gönderilsin mi?\n\n$musteriAdi\n$musteriTelefon")
            .setPositiveButton("Evet") { _, _ ->
                sendWhatsAppMessageWithText(msg)
                sorAnaSayfa()
            }
            .setNegativeButton("Hayır") { _, _ -> sorAnaSayfa() }
            .setCancelable(false)
            .show()
    }

    /** Adım 3: Ana Sayfaya Dön */
    private fun sorAnaSayfa() {
        AlertDialog.Builder(this)
            .setTitle("🏠 Ana Sayfa")
            .setMessage("Ana sayfaya dönmek ister misiniz?")
            .setPositiveButton("Evet") { _, _ -> goHome() }
            .setNegativeButton("Hayır", null)
            .setCancelable(false)
            .show()
    }

    private fun buildMesaj(): String =
        "Sayın $musteriAdi, sipariş tutarınız: ${
            String.format(Locale("tr","TR"), "%.2f", sonGenelToplam)
        } ₺. Teşekkürler."

    // ─────────────────────────────────────────────────────────────────

    private fun showIndirimDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_indirim_ekle, null)
        val etInput = dialogView.findViewById<EditText>(R.id.etIndirimDeger)
        val etAciklama = dialogView.findViewById<EditText>(R.id.etIndirimAciklama)
        val rgTip = dialogView.findViewById<RadioGroup>(R.id.rgIndirimTip)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        if (indirimTutari > 0) etInput.setText(indirimTutari.toString())
        // Mevcut notta kayıtlı açıklama varsa geri yükle
        val mevcutNot = etSiparisNotu.text.toString()
        dialogView.findViewById<Button>(R.id.btnEkle).setOnClickListener {
            val deger = etInput.text.toString().toDoubleOrNull() ?: 0.0
            indirimTip = if (rgTip.checkedRadioButtonId == R.id.rbPercent) "PERCENT" else "TL"
            indirimTutari = if (indirimTip == "PERCENT") (getHamUrunToplami() * deger / 100) else deger
            // Tutar + açıklama sipariş notuna ekle
            val aciklama = etAciklama.text.toString().trim()
            val tutarStr = String.format(Locale("tr", "TR"), "%.2f ₺", indirimTutari)
            val notSatiri = if (aciklama.isNotEmpty()) "- İndirim: $tutarStr ($aciklama)" else "- İndirim: $tutarStr"
            val yeniNot = if (mevcutNot.isEmpty()) notSatiri else "$mevcutNot\n$notSatiri"
            etSiparisNotu.setText(yeniNot)
            listeyiYenile(); dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnSil).setOnClickListener { indirimTutari = 0.0; listeyiYenile(); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnIptal).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showUcretDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ucret_ekle, null)
        val etInput = dialogView.findViewById<EditText>(R.id.etUcretDeger)
        val etAciklama = dialogView.findViewById<EditText>(R.id.etUcretAciklama)
        val rgTip = dialogView.findViewById<RadioGroup>(R.id.rgUcretTip)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        if (ekUcretTutari > 0) etInput.setText(ekUcretTutari.toString())
        val mevcutNot = etSiparisNotu.text.toString()
        dialogView.findViewById<Button>(R.id.btnEkle).setOnClickListener {
            val deger = etInput.text.toString().toDoubleOrNull() ?: 0.0
            ekUcretTip = if (rgTip.checkedRadioButtonId == R.id.rbPercent) "PERCENT" else "TL"
            ekUcretTutari = if (ekUcretTip == "PERCENT") (getHamUrunToplami() * deger / 100) else deger
            // Tutar + açıklama sipariş notuna ekle
            val aciklama = etAciklama.text.toString().trim()
            val tutarStr = String.format(Locale("tr", "TR"), "%.2f ₺", ekUcretTutari)
            val notSatiri = if (aciklama.isNotEmpty()) "+ Ek Ücret: $tutarStr ($aciklama)" else "+ Ek Ücret: $tutarStr"
            val yeniNot = if (mevcutNot.isEmpty()) notSatiri else "$mevcutNot\n$notSatiri"
            etSiparisNotu.setText(yeniNot)
            listeyiYenile(); dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnSil).setOnClickListener { ekUcretTutari = 0.0; listeyiYenile(); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnIptal).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun getHamUrunToplami(): Double {
        val db = urunDao.getUrunlerBySiparisId(siparisId).sumOf { it.tutar }
        val temp = TempUrunSepeti.getItems(siparisId).sumOf { it.toplamTutar }
        return db + temp
    }

    private fun listeyiYenile() {
        layoutUrunListe.removeAllViews()
        val dbRows = urunDao.getUrunlerBySiparisId(siparisId)
        val tempItems = TempUrunSepeti.getItems(siparisId)

        val inflater = LayoutInflater.from(this)
        val localeTR = Locale("tr", "TR")
        val df = DecimalFormat("#.##", DecimalFormatSymbols(localeTR))

        var totalAdet = 0; var totalM2 = 0.0; var urunlerTutar = 0.0

        // ── BAŞLIK SATIRI ──
        val header = inflater.inflate(R.layout.urun_satir, layoutUrunListe, false)
        header.setBackgroundResource(android.R.color.transparent)
        header.findViewById<TextView>(R.id.tvUrunAdi).apply {
            text = "ÜRÜN"; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#00695C"))
        }
        header.findViewById<TextView>(R.id.tvAdet).apply {
            text = "ADET"; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#00695C"))
        }
        header.findViewById<TextView>(R.id.tvM2).apply {
            text = "M2"; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#00695C"))
        }
        header.findViewById<TextView>(R.id.tvFiyat).apply {
            text = "FİYAT"; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#00695C"))
        }
        header.findViewById<TextView>(R.id.tvToplamTutar).apply {
            text = "TUTAR"; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#00695C"))
        }
        header.findViewById<ImageButton>(R.id.btnSil).visibility = View.INVISIBLE
        layoutUrunListe.addView(header)

        // ── VERİTABANI SATIRLARI ──
        dbRows.forEach { row ->
            val satir = inflater.inflate(R.layout.urun_satir, layoutUrunListe, false)
            satir.findViewById<TextView>(R.id.tvUrunAdi).text = row.ad
            satir.findViewById<TextView>(R.id.tvAdet).text = row.adet.toString()
            satir.findViewById<TextView>(R.id.tvM2).text = df.format(row.m2)
            satir.findViewById<TextView>(R.id.tvFiyat).text = df.format(row.fiyat)
            satir.findViewById<TextView>(R.id.tvToplamTutar).text =
                String.format(localeTR, "%.1f", row.tutar)
            satir.findViewById<ImageButton>(R.id.btnSil)
                .setOnClickListener { urunDao.softDeleteUrun(row.id); listeyiYenile() }
            layoutUrunListe.addView(satir)
            totalAdet += row.adet; totalM2 += row.m2; urunlerTutar += row.tutar
        }

        // ── GEÇİCİ SATIRLAR (henüz kaydedilmemiş) ──
        tempItems.forEach { temp ->
            val satir = inflater.inflate(R.layout.urun_satir, layoutUrunListe, false)
            satir.findViewById<TextView>(R.id.tvUrunAdi).text = "${temp.ad} *"
            satir.findViewById<TextView>(R.id.tvAdet).text = temp.adet.toString()
            satir.findViewById<TextView>(R.id.tvM2).text = df.format(temp.m2)
            satir.findViewById<TextView>(R.id.tvFiyat).text = df.format(temp.fiyat)
            satir.findViewById<TextView>(R.id.tvToplamTutar).text =
                String.format(localeTR, "%.1f", temp.toplamTutar)
            satir.findViewById<ImageButton>(R.id.btnSil)
                .setOnClickListener { TempUrunSepeti.removeItem(siparisId, temp.tempId); listeyiYenile() }
            layoutUrunListe.addView(satir)
            totalAdet += temp.adet; totalM2 += temp.m2; urunlerTutar += temp.toplamTutar
        }

        // ── EK ÜCRET / İNDİRİM ──
        if (ekUcretTutari > 0) layoutUrunListe.addView(
            createSpecialRow(inflater, "Ek Ücret", ekUcretTutari,
                Color.parseColor("#1565C0"), "+", "+") {
                ekUcretTutari = 0.0; listeyiYenile()
            }
        )
        if (indirimTutari > 0) layoutUrunListe.addView(
            createSpecialRow(inflater, "İndirim", -indirimTutari,
                Color.parseColor("#C62828"), "-", "-") {
                indirimTutari = 0.0; listeyiYenile()
            }
        )

        sonGenelToplam = urunlerTutar + ekUcretTutari - indirimTutari
        sonGenelM2 = totalM2

        tvToplamAdet.text = "$totalAdet"
        tvToplamM2.text = df.format(totalM2)
        tvGenelToplam.text = String.format(localeTR, "%.2f ₺", sonGenelToplam)
    }

    private fun createSpecialRow(
        inflater: LayoutInflater,
        title: String,
        amount: Double,
        color: Int,
        yonOku: String,   // "↑" veya "↓"
        yonSimge: String, // "+" veya "−"
        onSil: () -> Unit
    ): View {
        val satir = inflater.inflate(R.layout.urun_satir, layoutUrunListe, false)
        satir.findViewById<TextView>(R.id.tvUrunAdi).apply {
            text = "$yonOku $title"
            setTextColor(color)
            setTypeface(null, Typeface.BOLD)
        }
        satir.findViewById<TextView>(R.id.tvAdet).text = ""
        satir.findViewById<TextView>(R.id.tvM2).text = ""
        // Tutarın sol yanında yön simgesi (+ veya −)
        satir.findViewById<TextView>(R.id.tvFiyat).apply {
            text = yonSimge
            setTextColor(color)
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
        }
        satir.findViewById<TextView>(R.id.tvToplamTutar).apply {
            text = String.format(Locale("tr", "TR"), "%.2f ₺", amount)
            setTextColor(color)
        }
        satir.findViewById<ImageButton>(R.id.btnSil).setOnClickListener { onSil() }
        return satir
    }

    private fun loadSavedData() {
        siparisDao.getSiparisById(siparisId)?.let {
            etSiparisNotu.setText(it.notlar ?: "")
            etTeslimTarihi.setText(it.teslimAlmaTarihi ?: "")
            indirimTutari = it.indirim
            ekUcretTutari = it.ekUcret
            val adapter = spSiparisDurumu.adapter as? ArrayAdapter<String>
            val pos = adapter?.getPosition(it.durum) ?: -1
            if (pos != -1) spSiparisDurumu.setSelection(pos)
        }
    }

    private fun buildOrderSummary(): String {
        val localeTR = Locale("tr", "TR")
        val df = DecimalFormat("#.##", DecimalFormatSymbols(localeTR))
        val sb = StringBuilder()
        sb.appendLine("📦 SİPARİŞ ÖZETİ")
        sb.appendLine("Müşteri : $musteriAdi")
        if (musteriTelefon.isNotEmpty()) sb.appendLine("Tel     : $musteriTelefon")
        val durum = spSiparisDurumu.selectedItem?.toString() ?: ""
        if (durum.isNotEmpty()) sb.appendLine("Durum   : $durum")
        val tarih = etTeslimTarihi.text.toString()
        if (tarih.isNotEmpty()) sb.appendLine("Teslim  : $tarih")
        sb.appendLine()
        val dbRows = urunDao.getUrunlerBySiparisId(siparisId)
        val tempItems = TempUrunSepeti.getItems(siparisId)
        if (dbRows.isNotEmpty() || tempItems.isNotEmpty()) {
            sb.appendLine("── ÜRÜNLER ──")
            dbRows.forEach { row ->
                sb.appendLine("• ${row.ad}: ${row.adet} adet, ${df.format(row.m2)} m², ${df.format(row.fiyat)} ₺  →  ${String.format(localeTR, "%.2f ₺", row.tutar)}")
            }
            tempItems.forEach { temp ->
                sb.appendLine("• ${temp.ad}: ${temp.adet} adet, ${df.format(temp.m2)} m², ${df.format(temp.fiyat)} ₺  →  ${String.format(localeTR, "%.2f ₺", temp.toplamTutar)}")
            }
            sb.appendLine()
        }
        if (ekUcretTutari > 0) sb.appendLine("+ Ek Ücret : ${String.format(localeTR, "%.2f ₺", ekUcretTutari)}")
        if (indirimTutari > 0) sb.appendLine("- İndirim  : ${String.format(localeTR, "%.2f ₺", indirimTutari)}")
        sb.appendLine("══ TOPLAM  : ${String.format(localeTR, "%.2f ₺", sonGenelToplam)} ══")
        return sb.toString().trim()
    }

    private fun sendWhatsAppMessage() {
        sendWhatsAppMessageWithText("Merhaba $musteriAdi")
    }

    private fun sendWhatsAppMessageWithText(msg: String) {
        val url = "https://wa.me/90${musteriTelefon.replace(Regex("[^0-9]"), "")}?text=${Uri.encode(msg)}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (ex: Exception) {
                Toast.makeText(this, "WhatsApp bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendSmsKontrollu(tel: String, msg: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            SMSHelper(this).sendSMS(tel, msg)
            Toast.makeText(this, "✅ SMS gönderildi", Toast.LENGTH_SHORT).show()
            sorWhatsApp(msg)
        } else {
            bekleyenTelefon = tel
            bekleyenMesaj = msg
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun tarihSec() {
        val c = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                etTeslimTarihi.setText(String.format(Locale.getDefault(), "%02d.%02d.%04d", d, m + 1, y))
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    companion object { private const val TAG = "SiparisBilgiEkle" }
}
