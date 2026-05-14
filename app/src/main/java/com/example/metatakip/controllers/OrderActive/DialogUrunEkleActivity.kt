package com.example.metatakip.controllers.OrderActive

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.R
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoImpl
import com.example.metatakip.feature_data.entityModel.UrunTipi
import java.util.Locale

class DialogUrunEkleActivity : AppCompatActivity() {

    private var siparisId: Long = -1L
    private lateinit var urunTipiDao: UrunTipiDaoImpl

    // UI Elements
    private lateinit var spUrunTipi: Spinner
    private lateinit var tvFiyat: TextView
    private lateinit var etAdet: EditText
    private lateinit var etM2: EditText
    private lateinit var etEn: EditText
    private lateinit var etBoy: EditText
    private lateinit var btnEkle: Button
    private lateinit var btnIptal: Button
    private lateinit var tvUyari: TextView

    private var urunTipleri: List<UrunTipi> = emptyList()
    private var icGuncelleme = false // Sonsuz döngüyü engellemek için

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_urun_ekle) // Sizin paylaştığınız En/Boy içeren XML

        siparisId = intent.getLongExtra("siparisId", -1L)
        urunTipiDao = UrunTipiDaoImpl(this)

        initViews()
        loadUrunTipleri()
        setupListeners()
    }

    private fun initViews() {
        spUrunTipi = findViewById(R.id.spUrun)
        tvFiyat = findViewById(R.id.tvFiyat)
        etAdet = findViewById(R.id.etAdet)
        etM2 = findViewById(R.id.etM2)
        etEn = findViewById(R.id.etEn)
        etBoy = findViewById(R.id.etBoy)
        btnEkle = findViewById(R.id.btnEkle)
        btnIptal = findViewById(R.id.btnIptal)
        tvUyari = findViewById(R.id.tvUyari)

        // Varsayılan adet
        etAdet.setText("1")
    }

    private fun setupListeners() {
        // Ürün tipi seçildiğinde birim fiyatı güncelle
        spUrunTipi.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                updateFiyat()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Otomatik M2 Hesaplama Watcher (En * Boy * Adet)
        val autoCalculateWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!icGuncelleme) {
                    calculateM2()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etEn.addTextChangedListener(autoCalculateWatcher)
        etBoy.addTextChangedListener(autoCalculateWatcher)
        etAdet.addTextChangedListener(autoCalculateWatcher)

        // Buton Dinleyicileri
        btnEkle.setOnClickListener { validateAndAddToTempBasket() }
        btnIptal.setOnClickListener { finish() }
    }

    private fun loadUrunTipleri() {
        try {
            urunTipleri = urunTipiDao.getAll().filter { it.aktif == 1 }
            if (urunTipleri.isNotEmpty()) {
                val names = urunTipleri.map { it.ad }
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                spUrunTipi.adapter = adapter
                updateFiyat()
            } else {
                tvUyari.visibility = View.VISIBLE
                tvUyari.text = "Ürün tipi bulunamadı! Lütfen ayarlardan ekleyin."
                btnEkle.isEnabled = false
            }
        } catch (e: Exception) {
            Log.e("DialogUrunEkle", "Yükleme hatası: ${e.message}")
        }
    }

    private fun updateFiyat() {
        val selectedName = spUrunTipi.selectedItem?.toString() ?: ""
        val urunTipi = urunTipleri.find { it.ad == selectedName }
        tvFiyat.text = String.format(Locale.US, "%.1f", urunTipi?.birimFiyat ?: 0.0)
    }

    private fun calculateM2() {
        val en = etEn.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        val boy = etBoy.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        val adet = etAdet.text.toString().toIntOrNull() ?: 1

        if (en > 0 && boy > 0) {
            val totalM2 = en * boy * adet
            icGuncelleme = true
            etM2.setText(String.format(Locale.US, "%.2f", totalM2))
            icGuncelleme = false
        }
    }

    private fun validateAndAddToTempBasket() {
        val urunAdi = spUrunTipi.selectedItem?.toString() ?: ""
        val adet = etAdet.text.toString().toIntOrNull() ?: 0
        val m2 = etM2.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        val fiyat = tvFiyat.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0

        // Basit doğrulamalar
        if (urunAdi.isEmpty()) return
        if (adet <= 0) {
            Toast.makeText(this, "Geçerli bir adet giriniz", Toast.LENGTH_SHORT).show()
            return
        }
        if (m2 <= 0) {
            Toast.makeText(this, "M² hesaplanamadı", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥🔥🔥 SENİN TempUrunSepeti NESNENE EKLEME YAPIYORUZ 🔥🔥🔥
        TempUrunSepeti.addItem(
            siparisId = siparisId,
            ad = urunAdi,
            adet = adet,
            m2 = m2,
            fiyat = fiyat,
            urunIndirim = 0.0, // Ürün bazlı indirim istersen buraya EditText bağlayabilirsin
            urunIndirimAciklamasi = "",
            urunEkUcret = 0.0,
            urunEkUcretAciklamasi = ""
        )

        Log.d("DialogUrunEkle", "✅ Ürün sepete eklendi: $urunAdi")
        setResult(RESULT_OK)
        finish()
    }
}