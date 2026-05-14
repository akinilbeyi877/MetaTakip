package com.example.metatakip.controllers.callphonelast

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.R
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import dao.MetaTakipCustomerDao
import com.example.metatakip.feature_data.entityModel.CallRecord
import com.example.metatakip.controllers.genericListFolder.RightClickMenuHandler
import java.util.Random

class AddCallLogActivity : AppCompatActivity() {

    private lateinit var callLogsDao: CallLogsDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_call_log)

        // 🔧 Device bilgilerini hazırla
        DeviceManager.initialize(this)

        callLogsDao = CallLogsDao(this)

        // 💾 Kaydet butonu
        findViewById<Button>(R.id.btnKaydet).setOnClickListener {
            saveCallLog()
        }

        // 🧪 Test verisi doldur
        findViewById<Button>(R.id.btnTestDoldur).setOnClickListener {
            fillTestData()
        }

        // 📞 Intent'ten gelen çağrı bilgileri
        val incomingNumber = intent.getStringExtra("callerNumber")
        val callerName = intent.getStringExtra("callerName")

        if (!incomingNumber.isNullOrBlank()) {

            // 📱 Telefon alanı
            findViewById<EditText>(R.id.etMusteriTelefonu)
                .setText(incomingNumber)

            // 👤 İsim alanı
            findViewById<EditText>(R.id.etMusteriAdi)
                .setText(callerName ?: "")

            // 📞 Çağrı türü → GELEN
            findViewById<RadioGroup>(R.id.rgCagriTuru)
                .check(R.id.rbGelen)

            // 🧠 Müşteri kontrolü (YENİ METOT)
            val customerDao = MetaTakipCustomerDao(this)

            val normalizedNumber = normalizePhoneNumber(incomingNumber)

            val customer =
                customerDao.findCustomerByNormalizedPhone(normalizedNumber)

            // 🏢 Firma adını otomatik doldur
            customer?.let {
                if (!it.firmaAdi.isNullOrBlank()) {
                    findViewById<EditText>(R.id.etFirmaAdi)
                        .setText(it.firmaAdi)
                }
            }
        }
    }


    private fun fillTestData() {
        val random = Random()
        val testNames = listOf("Ahmet", "Mehmet", "Ayşe", "Fatma", "Ali", "Veli")
        val testCompanies = listOf("ABC Şirketi", "XYZ Ltd.", "TechSoft", "Global Corp")
        val testDepartments = listOf("Satış", "Müşteri Hizmetleri", "Teknik Destek", "İnsan Kaynakları")

        findViewById<EditText>(R.id.etMusteriTelefonu).setText("0555${1000000 + random.nextInt(9000000)}")
        findViewById<EditText>(R.id.etMusteriAdi).setText("${testNames.random()} ${testNames.random()}")
        findViewById<EditText>(R.id.etFirmaAdi).setText(testCompanies.random())
        findViewById<EditText>(R.id.etHatAdi).setText(testDepartments.random())
        findViewById<EditText>(R.id.etArananTelefon).setText("0216${2000000 + random.nextInt(8000000)}")
        findViewById<EditText>(R.id.etCihazAdi).setText("SahaCihaz-${1 + random.nextInt(5)}")

        Toast.makeText(this, "✅ Test verileri dolduruldu", Toast.LENGTH_SHORT).show()
    }

    private fun saveCallLog() {
        // Form verilerini al
        val musteriTelefonu = findViewById<EditText>(R.id.etMusteriTelefonu).text.toString().trim()
        val musteriAdi = findViewById<EditText>(R.id.etMusteriAdi).text.toString().trim()
        val firmaAdi = findViewById<EditText>(R.id.etFirmaAdi).text.toString().trim()
        val hatAdi = findViewById<EditText>(R.id.etHatAdi).text.toString().trim()
        val arananTelefon = findViewById<EditText>(R.id.etArananTelefon).text.toString().trim()
        val cihazAdi = findViewById<EditText>(R.id.etCihazAdi).text.toString().trim()
        val cihazRolu = findViewById<EditText>(R.id.etCihazRolu).text.toString().trim()
        val simYuvasi = findViewById<EditText>(R.id.etSimYuvasi).text.toString().trim()

        // Çağrı türü
        val cagriTuru = when (findViewById<RadioGroup>(R.id.rgCagriTuru).checkedRadioButtonId) {
            R.id.rbGelen -> "GELEN"
            R.id.rbGiden -> "GIDEN"
            R.id.rbCevapsiz -> "CEVAPSIZ"
            else -> "GELEN"
        }

        // Cihaz konumu
        val cihazMerkezMi = findViewById<RadioGroup>(R.id.rgCihazMerkezMi).checkedRadioButtonId == R.id.rbMerkez

        // Validasyon
        if (musteriTelefonu.isBlank() || firmaAdi.isBlank() || hatAdi.isBlank() ||
            arananTelefon.isBlank() || cihazAdi.isBlank()) {
            Toast.makeText(this, "Lütfen zorunlu alanları doldurun (*)", Toast.LENGTH_LONG).show()
            return
        }

        // DeviceManager'den cihaz bilgilerini al
        val deviceConfig = DeviceManager.getDeviceConfig(this)
        val cihazFirmaAdi = deviceConfig?.companyName ?: "Yapılandırılmamış"
        val cihazKullaniciAdi = deviceConfig?.userName ?: "Bilinmiyor"

        // Cihaz rolü: Eğer kullanıcı girdiyse onu kullan, yoksa DeviceManager'dan al
        val finalCihazRolu = when {
            cihazRolu.isNotBlank() -> cihazRolu
            deviceConfig?.userRole?.isNotBlank() == true -> deviceConfig.userRole
            else -> if (cihazMerkezMi) "MERKEZ" else "SAHA"
        }

        // Cihaz adı: Eğer kullanıcı girdiyse onu kullan, yoksa telefon modelini kullan
        val finalCihazAdi = when {
            cihazAdi.isNotBlank() -> cihazAdi
            else -> android.os.Build.MODEL
        }

        // CallRecord oluştur (TÜM YENİ ALANLARLA BİRLİKTE)
        val callRecord = CallRecord(
            musteriTelefonu = musteriTelefonu,
            musteriAdi = if (musteriAdi.isNotBlank()) musteriAdi else null,
            arananFirmaAdi = firmaAdi,
            arananHatAdi = hatAdi,
            arananTelefon = arananTelefon,
            cihazAdi = finalCihazAdi,
            cihazFirmaAdi = cihazFirmaAdi,
            cihazKullaniciAdi = cihazKullaniciAdi,
            cihazRolu = finalCihazRolu,
            cihazMerkezMi = cihazMerkezMi,
            simYuvasi = if (simYuvasi.isNotBlank()) simYuvasi else "SIM1",
            cagriTuru = cagriTuru,
            cagriZamani = System.currentTimeMillis() / 1000,
            createdAt = System.currentTimeMillis() / 1000
        )

        // Veritabanına kaydet
        val id = callLogsDao.addCallLog(callRecord)

        if (id > 0) {
            Toast.makeText(this, "✅ Çağrı kaydedildi", Toast.LENGTH_SHORT).show()
            clearForm()
            askGoHome()
        } else {
            Toast.makeText(this, "❌ Kayıt başarısız", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askGoHome() {
        val menuHandler = RightClickMenuHandler(this)
        val menuItems = listOf(
            RightClickMenuHandler.ModernMenuItem("Ana Sayfaya Dön", R.drawable.ic_chevron_right, 0xFF1976D2.toInt()) { 
                finish() 
            },
            RightClickMenuHandler.ModernMenuItem("Burada Kal", android.R.drawable.ic_menu_add, 0xFF43A047.toInt()) { 
                // Zaten form temizlendi
            }
        )
        menuHandler.showModernMenu("✅ İşlem Tamamlandı", menuItems, autoDismiss = true)
    }

    private fun clearForm() {
        findViewById<EditText>(R.id.etMusteriTelefonu).text.clear()
        findViewById<EditText>(R.id.etMusteriAdi).text.clear()
        findViewById<EditText>(R.id.etFirmaAdi).text.clear()
        findViewById<EditText>(R.id.etHatAdi).text.clear()
        findViewById<EditText>(R.id.etArananTelefon).text.clear()
        findViewById<EditText>(R.id.etCihazAdi).text.clear()
        findViewById<EditText>(R.id.etCihazRolu).text.clear()
        findViewById<EditText>(R.id.etSimYuvasi).text.clear()

        // Radio button'ları varsayılan değerlere sıfırla
        findViewById<RadioGroup>(R.id.rgCagriTuru).check(R.id.rbGelen)
        findViewById<RadioGroup>(R.id.rgCihazMerkezMi).check(R.id.rbSaha)
    }

    private fun normalizePhoneNumber(phone: String): String {
        if (phone.isEmpty()) return ""

        var normalized = phone.replace("[^0-9+]".toRegex(), "")

        when {
            normalized.startsWith("+90") && normalized.length > 3 -> {
                normalized = normalized.substring(3)
            }
            normalized.startsWith("90") && normalized.length > 2 -> {
                normalized = normalized.substring(2)
            }
            normalized.startsWith("0") -> {
                normalized = normalized.substring(1)
            }
        }

        return normalized.replace("[^0-9]".toRegex(), "")
    }
}