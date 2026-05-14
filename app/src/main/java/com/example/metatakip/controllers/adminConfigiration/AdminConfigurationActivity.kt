package com.example.metatakip.controllers.adminConfigiration

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.metatakip.R
import com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import com.example.metatakip.data.metaTakipDb.crud.EtiketSablonDao
import com.example.metatakip.feature.admin.data.MesajSablonDaoImpl
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoImpl
import com.example.metatakip.feature_backup.ui.BackupRestoreActivity
// import com.example.metatakip.feature_backup_drive.ui.DriveBackupActivity
import com.example.metatakip.feature_data.entityModel.SessionManager
import com.example.metatakip.test.DeleteHistoryActivity
import dao.MetaTakipCustomerDao
import dao.MetaTakipFirmaDao
import dao.MetaTakipPersonelDao
import dao.MetaTakipUnvanDao
import kotlinx.coroutines.launch

class AdminConfigurationActivity : AppCompatActivity() {

    // COUNT TEXTVIEW'LERİ
    private lateinit var tvPersonelCount: TextView
    private lateinit var tvFirmaCount: TextView
    private lateinit var tvUrunTipiCount: TextView
    private lateinit var tvMesajSablonCount: TextView
    private lateinit var tvUnvanCount: TextView
    private lateinit var tvEtiketSablonCount: TextView
    private lateinit var tvIptalSiparisCount: TextView
    private lateinit var tvCallLogsCount: TextView
    private lateinit var tvAddCallCount: TextView
    private lateinit var tvSilinenSiparisCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_configuration)

        // TEXTVIEW'LERİ BAĞLA
        tvPersonelCount = findViewById(R.id.tvPersonelCount)
        tvFirmaCount = findViewById(R.id.tvFirmaCount)
        tvUrunTipiCount = findViewById(R.id.tvUrunTipiCount)
        tvMesajSablonCount = findViewById(R.id.tvMesajSablonCount)
        tvUnvanCount = findViewById(R.id.tvUnvanCount)
        tvEtiketSablonCount = findViewById(R.id.tvEtiketSablonCount)
        tvIptalSiparisCount = findViewById(R.id.tvIptalSiparisCount)
        tvCallLogsCount = findViewById(R.id.tvCallLogsCount)
        tvAddCallCount = findViewById(R.id.tvAddCallCount)
        tvSilinenSiparisCount = findViewById(R.id.tvSilinenSiparisCount)

        // SAYILARI GÜNCELLE
        updateCounts()

        // 👤 YETKİLİ PERSONEL
        findViewById<LinearLayout>(R.id.btnPersonelEkle).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "personel")
                    .putExtra("pageTitle", "👤 Personel Yönetimi")
                    .putExtra("source", "admin")
            )
        }

        // 🏢 FIRMA BİLGİLERİ
        findViewById<LinearLayout>(R.id.btnFirmaBilgileri).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "firma")
                    .putExtra("pageTitle", "🏢 Firma Bilgileri")
                    .putExtra("source", "admin")
            )
        }

        // 🏷️ ÜRÜN TİPİ YÖNETİMİ
        findViewById<LinearLayout>(R.id.btnUrunTipiYonetimi).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "urun_tipi")
                    .putExtra("pageTitle", "🏷️ Ürün Tipi Yönetimi")
                    .putExtra("source", "admin")
            )
        }

        // 📝 MESAJ ŞABLONLARI
        findViewById<LinearLayout>(R.id.btnMesajSablonlari).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "mesaj_sablon")
                    .putExtra("pageTitle", "📝 Mesaj Şablonları")
                    .putExtra("source", "admin")
            )
        }

        // 🏷 ÜNVAN YÖNETİMİ
        findViewById<LinearLayout>(R.id.btnUnvanYonetimi).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "unvan")
                    .putExtra("pageTitle", "🏷 Ünvan Yönetimi")
                    .putExtra("source", "admin")
            )
        }

        // 🏷️ ETİKET AYARLARI
        findViewById<LinearLayout>(R.id.btnEtiketAyarlari).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "etiket_sablon")
                    .putExtra("pageTitle", "🏷️ Etiket Şablonları")
                    .putExtra("source", "admin")
            )
        }

        // ❌ İPTAL EDİLEN SİPARİŞLER
        findViewById<LinearLayout>(R.id.btnIptalEdilenler).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "siparis")
                    .putExtra("filterDurum", "Sipariş İptal Edildi")
                    .putExtra("pageTitle", "❌ İptal Edilen Siparişler")
                    .putExtra("source", "admin")
            )
        }

        // 📞 ÇAĞRI KAYITLARI
        findViewById<LinearLayout>(R.id.btnCallLogs).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "call_log")
                    .putExtra("pageTitle", "📞 Çağrı Kayıtları")
                    .putExtra("source", "admin")
            )
        }

        // ➕ ÇAĞRI EKLE
        findViewById<LinearLayout>(R.id.btnAddCallLog).setOnClickListener {
            startActivity(
                Intent(this, GenericListActivity::class.java)
                    .putExtra("listType", "call_log")
                    .putExtra("showAddButton", true)
                    .putExtra("pageTitle", "📞 Çağrı Kayıtları")
                    .putExtra("source", "admin")
            )
        }

        // 🗑️ SİLME GEÇMİŞİ
        findViewById<LinearLayout>(R.id.btnSilmeGecmisi).setOnClickListener {
            startActivity(Intent(this, DeleteHistoryActivity::class.java))
        }

        // 💾 VERİTABANI YÖNETİMİ
        findViewById<Button>(R.id.btnVeritabani).setOnClickListener {
            startActivity(Intent(this, BackupRestoreActivity::class.java))
        }

        // ☁️ DRIVE OTOMATİK YEDEK
        // ☁️ DRIVE OTOMATİK YEDEK (kullanılmıyor)
        // findViewById<Button>(R.id.btnDriveBackup).setOnClickListener {
        //     startActivity(Intent(this, DriveBackupActivity::class.java))
        // }

        // 🔙 GERİ
        findViewById<Button>(R.id.btnGeri).setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // SAYFAYA DÖNÜŞTE SAYILARI TEKRAR GÜNCELLE
        updateCounts()
    }

    private fun updateCounts() {
        lifecycleScope.launch {
            try {
                // 1️⃣ PERSONEL SAYISI
                updatePersonelCount()

                // 2️⃣ FIRMA SAYISI
                updateFirmaCount()

                // 3️⃣ ÜRÜN TİPİ SAYISI
                updateUrunTipiCount()

                // 4️⃣ MESAJ ŞABLONU SAYISI
                updateMesajSablonCount()

                // 5️⃣ ÜNVAN SAYISI
                updateUnvanCount()

                // 6️⃣ ETİKET ŞABLONU SAYISI
                updateEtiketSablonCount()

                // 7️⃣ İPTAL EDİLEN SİPARİŞ SAYISI
                updateIptalSiparisCount()

                // 8️⃣ ÇAĞRI KAYIT SAYISI
                updateCallLogsCount()

                // 9️⃣ SİLİNEN SİPARİŞ SAYISI
                updateSilmeGecmisiCount()
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Sayılar alınamadı", e)
                Toast.makeText(
                    this@AdminConfigurationActivity,
                    "Sayılar yüklenemedi: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updatePersonelCount() {
        lifecycleScope.launch {
            try {
                val personelDao = MetaTakipPersonelDao(this@AdminConfigurationActivity)
                val personelList = personelDao.getAllPersonel()
                val count = personelList.size

                tvPersonelCount.text = count.toString()
                Log.d("AdminConfig", "✅ Personel sayısı: $count")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Personel sayısı alınamadı", e)
                tvPersonelCount.text = "0"
            }
        }
    }

    private fun updateFirmaCount() {
        lifecycleScope.launch {
            try {
                val firmaDao = MetaTakipFirmaDao(this@AdminConfigurationActivity)
                val firmaList = firmaDao.getAllFirmalar()
                val count = firmaList.size

                tvFirmaCount.text = count.toString()
                Log.d("AdminConfig", "✅ Firma sayısı: $count")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Firma sayısı alınamadı", e)
                tvFirmaCount.text = "0"
            }
        }
    }

    private fun updateUrunTipiCount() {
        lifecycleScope.launch {
            try {
                val urunTipiDao = UrunTipiDaoImpl(this@AdminConfigurationActivity)
                val urunTipiList = urunTipiDao.getAll()
                val count = urunTipiList.size

                tvUrunTipiCount.text = count.toString()
                Log.d("AdminConfig", "✅ Ürün tipi sayısı: $count")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Ürün tipi sayısı alınamadı", e)
                tvUrunTipiCount.text = "0"
            }
        }
    }

    private fun updateMesajSablonCount() {
        lifecycleScope.launch {
            try {
                val mesajSablonDao = MesajSablonDaoImpl(this@AdminConfigurationActivity)
                val mesajSablonList = mesajSablonDao.getAll()
                val count = mesajSablonList.size

                tvMesajSablonCount.text = count.toString()
                Log.d("AdminConfig", "✅ Mesaj şablonu sayısı: $count")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Mesaj şablonu sayısı alınamadı", e)
                tvMesajSablonCount.text = "0"
            }
        }
    }

    private fun updateUnvanCount() {
        lifecycleScope.launch {
            try {
                val unvanDao = MetaTakipUnvanDao(this@AdminConfigurationActivity)
                val unvanList = unvanDao.getAllUnvanlar()
                val count = unvanList.size

                tvUnvanCount.text = count.toString()
                Log.d("AdminConfig", "✅ Ünvan sayısı: $count")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Ünvan sayısı alınamadı", e)
                tvUnvanCount.text = "0"
            }
        }
    }

    private fun updateEtiketSablonCount() {
        lifecycleScope.launch {
            try {
                val sessionManager = SessionManager(this@AdminConfigurationActivity)
                val userId = sessionManager.userId

                val etiketSablonDao = EtiketSablonDao(this@AdminConfigurationActivity)
                val etiketSablonList = etiketSablonDao.getAllSablonlar()
                val count = etiketSablonList.size

                tvEtiketSablonCount.text = count.toString()
                Log.d("AdminConfig", "✅ Etiket şablonu sayısı: $count (Kullanıcı ID: $userId)")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Etiket şablonu sayısı alınamadı", e)
                tvEtiketSablonCount.text = "0"
            }
        }
    }

    private fun updateIptalSiparisCount() {
        lifecycleScope.launch {
            try {
                val siparisDao = OrderDaoImpl(this@AdminConfigurationActivity)
                val tumSiparisler = siparisDao.getAllSiparis()

                // "Sipariş İptal Edildi" durumundaki siparişleri say
                val count = tumSiparisler.count { siparis ->
                    siparis.durum == "Sipariş İptal Edildi" && siparis.isDeleted == 0
                }

                tvIptalSiparisCount.text = count.toString()
                Log.d("AdminConfig", "✅ İptal edilen sipariş sayısı: $count")
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ İptal edilen sipariş sayısı alınamadı", e)
                tvIptalSiparisCount.text = "0"
            }
        }
    }

    private fun updateCallLogsCount() {
        lifecycleScope.launch {
            try {
                val callLogDao = CallLogsDao(this@AdminConfigurationActivity)
                val callLogList = callLogDao.getAllCallLogs()
                val count = callLogList.size

                tvCallLogsCount.text = count.toString()
                Log.d("AdminConfig", "✅ Çağrı kaydı sayısı: $count")

                // ➕ butonunu sabit "+" olarak ayarla
                tvAddCallCount.text = "+"
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Çağrı kaydı sayısı alınamadı", e)
                tvCallLogsCount.text = "0"
                tvAddCallCount.text = "+"
            }
        }
    }

    private fun updateSilmeGecmisiCount() {
        lifecycleScope.launch {
            try {
                val customerDao = MetaTakipCustomerDao(this@AdminConfigurationActivity)

                // DAO'dan direkt sayıları al
                val tamSilinen = customerDao.getDeletedCustomerCount()
                val aktifteSilinenli = customerDao.getActiveCustomersWithDeletedOrdersCount()

                // TOPLAM
                val toplam = tamSilinen + aktifteSilinenli

                // BUTON COUNT GÜNCELLE
                tvSilinenSiparisCount.text = toplam.toString()

                Log.d(
                    "AdminConfig",
                    """
                    ✅ Silme geçmişi sayıları:
                    - Tam silinmiş müşteriler: $tamSilinen
                    - Aktifte silinen siparişli müşteriler: $aktifteSilinenli
                    - Toplam: $toplam
                    """.trimIndent()
                )
            } catch (e: Exception) {
                Log.e("AdminConfig", "❌ Silme geçmişi sayısı alınamadı", e)
                tvSilinenSiparisCount.text = "0"
            }
        }
    }
}