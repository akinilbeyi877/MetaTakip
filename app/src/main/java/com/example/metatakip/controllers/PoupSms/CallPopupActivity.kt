package com.example.metatakip.controllers.poupsms

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.metatakip.R
import com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity
import com.example.metatakip.feature_data.common.PhoneUtils
import com.example.metatakip.feature_data.entityModel.Customer
import dao.MetaTakipCustomerDao

class CallPopupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallPopup"
    }

    private lateinit var callerName: String
    private lateinit var callerNumber: String

    /**
     * ✅ Tek eşleştirme anahtarı:
     * "5xxxxxxxxx" (son 10 hane)
     */
    private lateinit var normalizedNumber: String

    private var isInProgram = false
    private var isInContacts = false
    private var isMissedCall = false
    private var isRingingCall = false
    private var fromNotification = false
    private var isTestCall = false

    private var existingCustomer: Customer? = null
    private lateinit var dao: MetaTakipCustomerDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== CALL POPUP BAŞLATILIYOR ===")

        // 🔐 Kilit ekran üzerinde aç
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_call_popup)

        dao = MetaTakipCustomerDao(this)

        readIntent()
        refreshStatus()
        updateUI()
        setupButtons()

        Log.d(TAG, "=== CALL POPUP HAZIR ===")
    }

    // ==========================
    // 📦 INTENT OKUMA
    // ==========================
    private fun readIntent() {
        callerName = intent.getStringExtra("callerName")
            ?: intent.getStringExtra("caller_name")
                    ?: "Bilinmeyen"

        callerNumber = intent.getStringExtra("callerNumber")
            ?: intent.getStringExtra("caller_number")
                    ?: intent.getStringExtra("incoming_number")
                    ?: ""

        // ✅ normalizeKeyTR ile tek standard
        normalizedNumber = intent.getStringExtra("normalizedNumber")
            ?: intent.getStringExtra("normalized_number")
                    ?: PhoneUtils.normalizeKeyTR(callerNumber)

        // Rehberden gelen isim varsa onu kullan
        val originalContactName = intent.getStringExtra("originalContactName")
        if (!originalContactName.isNullOrBlank()) {
            callerName = originalContactName
        }

        // (Eski flagler kalsa da artık refreshStatus gerçek kontrol yapıyor)
        isInProgram = intent.getBooleanExtra("programKayitli", false) ||
                intent.getBooleanExtra("is_program_kayitli", false)

        isInContacts = intent.getBooleanExtra("rehberKayitli", false) ||
                intent.getBooleanExtra("is_rehber_kayitli", false)

        isMissedCall = intent.getBooleanExtra("isMissedCall", false)
        isRingingCall = intent.getBooleanExtra("isRingingCall", false)
        fromNotification = intent.getBooleanExtra("fromNotification", false)
        isTestCall = intent.getBooleanExtra("isTestCall", false)

        Log.d(TAG, "📞 Popup açıldı →")
        Log.d(TAG, "  - İsim: $callerName")
        Log.d(TAG, "  - Numara(raw): $callerNumber")
        Log.d(TAG, "  - NormalizeKeyTR: $normalizedNumber")
        Log.d(TAG, "  - Programda(flag): $isInProgram")
        Log.d(TAG, "  - Rehberde(flag): $isInContacts")
    }

    // ==========================
    // 🔄 DURUM - GERÇEK KONTROL
    // ==========================
    private fun refreshStatus() {
        try {
            // ✅ PROGRAMDA VAR MI? -> DAO tek fonksiyonla kontrol
            existingCustomer = dao.findCustomerByIncomingPhone(callerNumber)
            isInProgram = existingCustomer != null

            // ✅ REHBERDE VAR MI? (normalize key ile aday üretip kontrol)
            isInContacts = isNumberInContacts(callerNumber)

            // Rehberdeyse ve isim numara gibiyse → rehber ismini al
            if (isInContacts && (callerName == "Bilinmeyen" || callerName == callerNumber)) {
                getContactName(callerNumber)?.let { callerName = it }
            }

            Log.d(TAG, "🔄 GERÇEK KONTROL SONUÇLARI:")
            Log.d(TAG, "  - Programda: $isInProgram (${existingCustomer?.adSoyad})")
            Log.d(TAG, "  - Rehberde: $isInContacts")
            Log.d(TAG, "  - Firma: ${existingCustomer?.firmaAdi}")
            Log.d(TAG, "  - Gösterilen İsim: $callerName")
        } catch (e: Exception) {
            Log.e(TAG, "Durum güncelleme hatası", e)
        }
    }

    // ==========================
    // 🎨 UI GÜNCELLEME
    // ==========================
    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        try {
            findViewById<TextView>(R.id.tvCallerName).text = callerName
            findViewById<TextView>(R.id.tvCallerNumber).text = PhoneUtils.toLocalTR(callerNumber)

            val statusText = buildString {
                when {
                    isTestCall -> append("🧪 TEST ÇAĞRISI\n\n")
                    isMissedCall -> append("⏰ CEVAPSIZ ÇAĞRI\n\n")
                    isRingingCall -> append("🔔 ÇALIYOR...\n\n")
                }

                if (existingCustomer?.firmaAdi?.isNotEmpty() == true) {
                    append("🏢 ${existingCustomer?.firmaAdi}\n\n")
                }

                if (isInProgram && existingCustomer != null) {
                    append("✅ PROGRAMDA KAYITLI\n")
                    append("   Müşteri ID: ${existingCustomer?.id}\n")
                    append("   İsim: ${existingCustomer?.adSoyad}\n")
                    if (!existingCustomer?.ceptel.isNullOrEmpty()) append("   Tel 1: ${existingCustomer?.ceptel}\n")
                    if (!existingCustomer?.ceptel2.isNullOrEmpty()) append("   Tel 2: ${existingCustomer?.ceptel2}\n")
                } else {
                    append("❌ PROGRAMDA KAYITLI DEĞİL\n")
                }

                if (isInContacts) {
                    append("✅ REHBERDE KAYITLI\n")
                    val contactName = getContactName(callerNumber)
                    if (!contactName.isNullOrEmpty()) {
                        append("   Rehber İsmi: $contactName\n")
                    }
                } else {
                    append("❌ REHBERDE KAYITLI DEĞİL\n")
                }
            }

            findViewById<TextView>(R.id.tvKayitDurumu).text = statusText

            findViewById<Button>(R.id.btnYeniMusteriEkle).text =
                if (isInProgram) "Müşteriyi Düzenle" else "Yeni Müşteri Ekle"

        } catch (e: Exception) {
            Log.e(TAG, "UI güncelleme hatası", e)
        }
    }

    // ==========================
    // 🔘 BUTONLAR
    // ==========================
    private fun setupButtons() {

        findViewById<Button>(R.id.btnCall).setOnClickListener {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$callerNumber"))
                startActivity(dialIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Arama başlatılamadı", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        // 🧾 SİPARİŞ EKLE
        findViewById<Button>(R.id.btnSiparisEkle).setOnClickListener {
            if (isInProgram && existingCustomer != null) {
                val i = Intent(this, GenericFormActivity::class.java).apply {
                    putExtra("targetTable", "siparis")
                    putExtra("edit_mode", false)
                    putExtra("linkedCustomerId", existingCustomer!!.id)
                    putExtra("fromCallPopup", true)
                }
                startActivity(i)
                finish()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Müşteri Gerekli")
                    .setMessage("Sipariş eklemek için önce müşteri kaydı oluşturmalısınız.")
                    .setPositiveButton("Müşteri Ekle") { _, _ ->
                        openCreate()
                    }
                    .setNegativeButton("İptal", null)
                    .setCancelable(false)
                    .show()
            }
        }

        // 👤 MÜŞTERİ DÜZENLE / EKLE
        findViewById<Button>(R.id.btnYeniMusteriEkle).setOnClickListener {
            if (isInProgram && existingCustomer != null) openEdit() else openCreate()
        }

        findViewById<Button>(R.id.btnKapat).setOnClickListener { finish() }
    }

    private fun openEdit() {
        existingCustomer?.let { customer ->
            try {
                val i = Intent(this, GenericFormActivity::class.java).apply {
                    putExtra("targetTable", "musteri")
                    putExtra("edit_mode", true)
                    putExtra("id", customer.id)
                    putExtra("fromCallPopup", true)
                }
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this, "Müşteri düzenlenemedi", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private fun openCreate() {
        try {
            val localPhone = PhoneUtils.toLocalTR(callerNumber)

            val i = Intent(this, GenericFormActivity::class.java).apply {
                putExtra("targetTable", "musteri")
                putExtra("edit_mode", false)

                // ✅ müşteri formuna LOCAL telefon gönder
                putExtra("customerPhone", localPhone)

                // rehberde kayıtlıysa isim bas, değilse boş bırak
                if (isInContacts && callerName != "Bilinmeyen") {
                    putExtra("customerName", callerName)
                } else {
                    putExtra("customerName", "")
                }

                // ✅ rehbere ekle
                putExtra("addToContactsAfterSave", true)
                putExtra("fromCallPopup", true)

                // rehberde yoksa orijinal adı sakla
                putExtra("originalCallerName", if (!isInContacts) callerName else null)
            }

            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Müşteri eklenemedi", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    // ==========================
    // 📒 REHBER
    // ==========================
    private fun getContactName(phone: String): String? {
        if (!hasContactPermission()) return null
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
            )
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNumberInContacts(phone: String): Boolean {
        if (!hasContactPermission()) return false

        val local = PhoneUtils.toLocalTR(phone)          // 05xxxxxxxxx
        val e164 = PhoneUtils.toE164TR(phone)            // 90xxxxxxxxxx
        val key10 = PhoneUtils.normalizeKeyTR(phone)     // 5xxxxxxxxx

        val candidates = listOf(
            phone,
            local,
            "+$e164",
            e164,
            "0$key10"
        ).filter { it.isNotBlank() }.distinct()

        return try {
            for (candidate in candidates) {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(candidate)
                )
                contentResolver.query(lookupUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        Log.d(TAG, "Rehberde bulundu: $candidate")
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun hasContactPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    override fun onBackPressed() {
        super.onBackPressed()
    }
}