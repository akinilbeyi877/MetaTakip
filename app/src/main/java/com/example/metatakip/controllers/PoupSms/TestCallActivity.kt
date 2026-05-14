package com.example.metatakip.controllers.poupsms

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.metatakip.R
import dao.MetaTakipCustomerDao

class TestCallActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etTestNumber: EditText
    private lateinit var etTestName: EditText
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibrate: SwitchCompat
    private lateinit var btnSimulateCall: Button
    private lateinit var btnManualPopup: Button
    private lateinit var btnTestCallReceiver: Button
    private lateinit var btnCheckPermissions: Button

    private lateinit var customerDao: MetaTakipCustomerDao
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_call)

        Log.d("DEBUG_TEST", "=== TEST CALL ACTIVITY BAŞLATILDI ===")

        // DAO'yu başlat
        customerDao = MetaTakipCustomerDao(this)

        // View'leri bağla
        tvStatus = findViewById(R.id.tvStatus)
        etTestNumber = findViewById(R.id.etTestNumber)
        etTestName = findViewById(R.id.etTestName)
        switchSound = findViewById(R.id.switchSound)
        switchVibrate = findViewById(R.id.switchVibrate)
        btnSimulateCall = findViewById(R.id.btnSimulateCall)
        btnManualPopup = findViewById(R.id.btnManualPopup)
        btnTestCallReceiver = findViewById(R.id.btnTestCallReceiver)
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions)

        Log.d("DEBUG_TEST", "Butonlar bağlandı")

        // Buton tıklama işleyicileri
        btnSimulateCall.setOnClickListener {
            Log.d("DEBUG_TEST", "🔔 GERÇEKÇİ ARAMA butonuna tıklandı")
            simulateRealisticCall()
        }

        btnManualPopup.setOnClickListener {
            Log.d("DEBUG_TEST", "📱 MANUEL POPUP butonuna tıklandı")
            openManualPopup()
        }

        btnTestCallReceiver.setOnClickListener {
            Log.d("DEBUG_TEST", "📡 CALLRECEIVER TEST butonuna tıklandı")
            testCallReceiverDirectly()
        }

        btnCheckPermissions.setOnClickListener {
            Log.d("DEBUG_TEST", "🔐 İZİNLER butonuna tıklandı")
            checkPermissions()
        }

        Log.d("DEBUG_TEST", "TestCallActivity hazır")
        tvStatus.text = "✅ Test Paneli Hazır"
    }

    private fun simulateRealisticCall() {
        Log.d("DEBUG_TEST", "=== GERÇEKÇİ ARAMA SİMÜLASYONU BAŞLIYOR ===")

        val originalNumber = etTestNumber.text.toString().trim()
        val inputName = etTestName.text.toString().trim()

        Log.d("DEBUG_TEST", "Orijinal Numara: $originalNumber, Girdi İsim: $inputName")

        if (originalNumber.isEmpty()) {
            Toast.makeText(this, "Lütfen test numarası girin", Toast.LENGTH_SHORT).show()
            Log.d("DEBUG_TEST", "❌ HATA: Numara boş")
            return
        }

        // Numarayı normalize et - CALLPOPUPACTIVITY İLE TAM UYUMLU
        val normalizedNumber = normalizePhoneNumberCompat(originalNumber)
        Log.d("DEBUG_TEST", "Normalize edilmiş numara: $normalizedNumber")

        // Rehber kontrolü
        val rehberKontrolu = checkContactInRehber(normalizedNumber)
        val (isRehberKayitli, rehberName) = rehberKontrolu

        Log.d("DEBUG_TEST", "Rehber kontrolü: $isRehberKayitli, İsim: $rehberName")

        // Program kayıt kontrolü - GERÇEK DAO KULLANIMI
        val isProgramKayitli = checkInProgramDatabase(normalizedNumber)
        Log.d("DEBUG_TEST", "Program kontrolü: $isProgramKayitli")

        // Kullanılacak ismi belirle: rehber > girdi isim > CallPopup mantığı
        val callerName = determineCallerName(isRehberKayitli, rehberName, inputName, originalNumber)
        Log.d("DEBUG_TEST", "Kullanılacak isim: $callerName")

        Log.d("DEBUG_TEST", "1. Ses efekti kontrolü: ${switchSound.isChecked}")
        // Ses efekti
        if (switchSound.isChecked) {
            Log.d("DEBUG_TEST", "🔔 Ses efekti başlatılıyor...")
            playRingtone()
        } else {
            Log.d("DEBUG_TEST", "🔇 Ses efekti kapalı")
        }

        Log.d("DEBUG_TEST", "2. Titreşim kontrolü: ${switchVibrate.isChecked}")
        // Titreşim efekti
        if (switchVibrate.isChecked) {
            Log.d("DEBUG_TEST", "📳 Titreşim efekti başlatılıyor...")
            startVibration()
        } else {
            Log.d("DEBUG_TEST", "📴 Titreşim kapalı")
        }

        Log.d("DEBUG_TEST", "3. Broadcast gönderiliyor...")
        // Broadcast gönder (CallReceiver'ı tetikle)
        val intent = Intent("com.example.metatakip.TEST_CALL_RECEIVER")
        intent.putExtra("incoming_number", originalNumber)
        intent.putExtra("normalized_number", normalizedNumber)
        intent.putExtra("caller_name", callerName)
        intent.putExtra("is_rehber_kayitli", isRehberKayitli)
        intent.putExtra("is_program_kayitli", isProgramKayitli)

        try {
            sendBroadcast(intent)
            Log.d("DEBUG_TEST", "✅ Broadcast gönderildi: com.example.metatakip.TEST_CALL_RECEIVER")
        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ Broadcast gönderilemedi: ${e.message}")
        }

        Log.d("DEBUG_TEST", "4. Direkt popup açılıyor...")
        // Direkt popup aç (çift güvence) - CALLPOPUPACTIVITY İLE TAM UYUMLU
        try {
            val popupIntent = Intent(this, CallPopupActivity::class.java).apply {
                // ANA PARAMETRELER - CallPopupActivity'deki isimlerle aynı
                putExtra("callerName", callerName)
                putExtra("callerNumber", originalNumber)  // Orjinal numara
                putExtra("normalizedNumber", normalizedNumber)  // Normalize edilmiş

                // KONTROL PARAMETRELERİ - CallPopupActivity'deki isimlerle aynı
                putExtra("programKayitli", isProgramKayitli)
                putExtra("rehberKayitli", isRehberKayitli)

                // TEST PARAMETRELERİ
                putExtra("isTestCall", true)

                // FLAG'ler
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // DEBUG: Gönderilen tüm parametreleri logla
            val extras = popupIntent.extras
            if (extras != null) {
                Log.d("DEBUG_TEST", "📤 CallPopupActivity'ye gönderilen parametreler:")
                for (key in extras.keySet()) {
                    Log.d("DEBUG_TEST", "  - $key: ${extras.get(key)}")
                }
            }

            startActivity(popupIntent)
            Log.d("DEBUG_TEST", "✅ Direkt popup açıldı")
        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ Direkt popup açılamadı: ${e.message}")
            Toast.makeText(this, "Popup açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        // Durumu güncelle
        val statusMessage = buildString {
            append("📞 Simülasyon: ")
            append(callerName)
            append(" (")
            append(originalNumber)
            append(")\n")

            if (isRehberKayitli) {
                append("📱 Rehberde: ✅")
                if (rehberName.isNotEmpty()) {
                    append(" ($rehberName)")
                }
                append("\n")
            } else {
                append("📱 Rehberde: ❌\n")
            }

            if (isProgramKayitli) {
                append("📊 Programda: ✅")
            } else {
                append("📊 Programda: ❌")
            }
        }

        tvStatus.text = statusMessage
        Toast.makeText(this, "Gerçekçi arama simülasyonu başladı! Kontroller yapıldı.", Toast.LENGTH_LONG).show()

        // 5 saniye sonra efektleri durdur
        handler.postDelayed({
            stopRingtone()
            stopVibration()
            tvStatus.text = "Simülasyon tamamlandı"
            Log.d("DEBUG_TEST", "⏹️ Simülasyon sona erdi")
        }, 5000)

        Log.d("DEBUG_TEST", "=== GERÇEKÇİ ARAMA SİMÜLASYONU TAMAMLANDI ===")
    }

    /**
     * Arayan ismini belirle - CallPopupActivity mantığıyla aynı
     */
    private fun determineCallerName(
        isRehberKayitli: Boolean,
        rehberName: String,
        inputName: String,
        originalNumber: String
    ): String {
        return when {
            isRehberKayitli && rehberName.isNotEmpty() -> rehberName
            inputName.isNotEmpty() -> inputName
            else -> {
                // CallPopupActivity'deki extractNameFromCaller() mantığı
                if (originalNumber.length >= 4) {
                    "Müşteri ${originalNumber.takeLast(4)}"
                } else {
                    "Yeni Müşteri"
                }
            }
        }
    }

    /**
     * Telefon numarasını normalize eder - CALLPOPUPACTIVITY İLE TAM UYUMLU
     */
    private fun normalizePhoneNumberCompat(phoneNumber: String): String {
        if (phoneNumber.isEmpty()) return ""

        // Önce tüm boşluk ve özel karakterleri temizle
        var normalized = phoneNumber.replace("[^0-9+]".toRegex(), "")

        Log.d("DEBUG_TEST", "Normalizasyon adım 1: $normalized")

        // Türkiye numaraları için normalizasyon
        when {
            normalized.startsWith("+90") && normalized.length > 3 -> {
                normalized = normalized.substring(3) // +90'ı kaldır
            }
            normalized.startsWith("90") && normalized.length > 10 -> {
                normalized = normalized.substring(2) // 90'ı kaldır
            }
            normalized.startsWith("0") -> {
                normalized = normalized.substring(1) // 0'ı kaldır
            }
        }

        // Sadece rakam kalana kadar temizle
        normalized = normalized.replace("[^0-9]".toRegex(), "")

        Log.d("DEBUG_TEST", "Normalizasyon sonuç: $phoneNumber -> $normalized")
        return normalized
    }

    /**
     * Telefon rehberinde numarayı arar
     */
    private fun checkContactInRehber(normalizedNumber: String): Pair<Boolean, String> {
        if (normalizedNumber.isEmpty()) {
            return Pair(false, "")
        }

        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            // Daha geniş arama
            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$normalizedNumber%")

            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val contactName = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val contactNumber = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER))

                    // Numaraları normalize et ve karşılaştır
                    val normalizedContactNumber = normalizePhoneNumberCompat(contactNumber)

                    // Çeşitli karşılaştırma yöntemleri
                    val matchFound = when {
                        normalizedContactNumber == normalizedNumber -> true
                        contactNumber.contains(normalizedNumber) -> true
                        normalizedNumber.contains(normalizedContactNumber) -> true
                        else -> {
                            // Son 7-10 haneyi karşılaştır (daha esnek)
                            val lastDigitsCount = minOf(10, normalizedNumber.length, normalizedContactNumber.length)
                            val lastDigitsNumber = normalizedNumber.takeLast(lastDigitsCount)
                            val lastDigitsContact = normalizedContactNumber.takeLast(lastDigitsCount)
                            lastDigitsNumber == lastDigitsContact
                        }
                    }

                    if (matchFound) {
                        Log.d("DEBUG_TEST", "✅ Rehberde bulundu: $contactName - $contactNumber")
                        return Pair(true, contactName)
                    }
                } while (cursor.moveToNext())
            }

            Log.d("DEBUG_TEST", "❌ Rehberde bulunamadı: $normalizedNumber")
            return Pair(false, "")

        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "Rehber kontrol hatası: ${e.message}")
            return Pair(false, "")
        } finally {
            cursor?.close()
        }
    }

    /**
     * Programın kendi veritabanında numarayı kontrol eder - GERÇEK DAO KULLANIMI
     */
    private fun checkInProgramDatabase(normalizedNumber: String): Boolean {
        return try {
            // ✅ DOĞRU DAO METODU (normalized)
            val customer = customerDao.findCustomerByNormalizedPhone(normalizedNumber)

            if (customer != null) {
                Log.d(
                    "DEBUG_TEST",
                    "✅ Program veritabanında bulundu: ${customer.adSoyad} ($normalizedNumber)"
                )
                true
            } else {
                Log.d(
                    "DEBUG_TEST",
                    "❌ Program veritabanında bulunamadı: $normalizedNumber"
                )
                false
            }

        } catch (e: Exception) {
            Log.e(
                "DEBUG_TEST",
                "❌ Program veritabanı kontrol hatası: ${e.message}",
                e
            )
            false
        }
    }


    private fun playRingtone() {
        try {
            val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            Log.d("DEBUG_TEST", "🔔 Ringtone URI: $ringtoneUri")
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()
            Log.d("DEBUG_TEST", "✅ Ses çalınıyor...")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("DEBUG_TEST", "❌ Ses çalınamadı: ${e.message}")
            Toast.makeText(this, "Ses çalınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        Log.d("DEBUG_TEST", "🔇 Ses durduruldu")
    }

    private fun startVibration() {
        try {
            val vibrator = getVibrator()
            Log.d("DEBUG_TEST", "📳 Vibrator alındı")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(
                    5000, // 5 saniye
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
                Log.d("DEBUG_TEST", "✅ Titreşim başladı (API 26+)")
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(5000) // 5 saniye
                Log.d("DEBUG_TEST", "✅ Titreşim başladı (API 26-)")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("DEBUG_TEST", "❌ Titreşim çalınamadı: ${e.message}")
            Toast.makeText(this, "Titreşim çalınamadı", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            Log.d("DEBUG_TEST", "📳 VibratorManager kullanılıyor (API 31+)")
            vibratorManager.defaultVibrator
        } else {
            Log.d("DEBUG_TEST", "📳 Vibrator kullanılıyor (API 31-)")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun stopVibration() {
        try {
            getVibrator().cancel()
            Log.d("DEBUG_TEST", "📴 Titreşim durduruldu")
        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ Titreşim durdurulamadı: ${e.message}")
        }
    }

    private fun openManualPopup() {
        Log.d("DEBUG_TEST", "=== MANUEL POPUP AÇILIYOR ===")

        val originalNumber = etTestNumber.text.toString().trim()
        val inputName = etTestName.text.toString().trim()

        Log.d("DEBUG_TEST", "Orijinal Numara: $originalNumber, Girdi İsim: $inputName")

        if (originalNumber.isEmpty()) {
            Toast.makeText(this, "Lütfen test numarası girin", Toast.LENGTH_SHORT).show()
            Log.d("DEBUG_TEST", "❌ HATA: Numara boş")
            return
        }

        // Numarayı normalize et
        val normalizedNumber = normalizePhoneNumberCompat(originalNumber)

        // Rehber kontrolü
        val (isRehberKayitli, rehberName) = checkContactInRehber(normalizedNumber)

        // Program kayıt kontrolü - GERÇEK DAO
        val isProgramKayitli = checkInProgramDatabase(normalizedNumber)

        // Kullanılacak ismi belirle
        val callerName = determineCallerName(isRehberKayitli, rehberName, inputName, originalNumber)

        try {
            val intent = Intent(this, CallPopupActivity::class.java).apply {
                // ANA PARAMETRELER - CallPopupActivity'deki isimlerle aynı
                putExtra("callerName", callerName)
                putExtra("callerNumber", originalNumber)
                putExtra("normalizedNumber", normalizedNumber)

                // KONTROL PARAMETRELERİ - CallPopupActivity'deki isimlerle aynı
                putExtra("programKayitli", isProgramKayitli)
                putExtra("rehberKayitli", isRehberKayitli)

                // TEST PARAMETRELERİ
                putExtra("isTestCall", true)

                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            Log.d("DEBUG_TEST", "✅ Manuel popup açıldı")

            val statusMessage = buildString {
                append("📱 Manuel popup: ")
                append(callerName)
                append(" (")
                append(originalNumber)
                append(")")
            }

            tvStatus.text = statusMessage
            Toast.makeText(this, "Manuel popup açıldı! Kontroller yapıldı.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ Manuel popup açılamadı: ${e.message}")
            Toast.makeText(this, "Popup açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testCallReceiverDirectly() {
        Log.d("DEBUG_TEST", "=== CALLRECEIVER TESTİ BAŞLIYOR ===")

        val originalNumber = etTestNumber.text.toString().trim()
        val inputName = etTestName.text.toString().trim()

        Log.d("DEBUG_TEST", "Orijinal Numara: $originalNumber, Girdi İsim: $inputName")

        if (originalNumber.isEmpty()) {
            Toast.makeText(this, "Lütfen test numarası girin", Toast.LENGTH_SHORT).show()
            Log.d("DEBUG_TEST", "❌ HATA: Numara boş")
            return
        }

        // Numarayı normalize et
        val normalizedNumber = normalizePhoneNumberCompat(originalNumber)

        // Rehber kontrolü
        val (isRehberKayitli, rehberName) = checkContactInRehber(normalizedNumber)

        // Program kayıt kontrolü - GERÇEK DAO
        val isProgramKayitli = checkInProgramDatabase(normalizedNumber)

        // Kullanılacak ismi belirle
        val callerName = determineCallerName(isRehberKayitli, rehberName, inputName, originalNumber)

        Log.d("DEBUG_TEST", "1. PHONE_STATE broadcast gönderiliyor...")
        // Doğrudan Intent gönder
        val phoneIntent = Intent("android.intent.action.PHONE_STATE")
        phoneIntent.putExtra("state", "RINGING")
        phoneIntent.putExtra("incoming_number", originalNumber)
        phoneIntent.putExtra("normalized_number", normalizedNumber)
        phoneIntent.putExtra("is_rehber_kayitli", isRehberKayitli)
        phoneIntent.putExtra("is_program_kayitli", isProgramKayitli)

        try {
            sendBroadcast(phoneIntent)
            Log.d("DEBUG_TEST", "✅ PHONE_STATE broadcast gönderildi")
        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ PHONE_STATE broadcast gönderilemedi: ${e.message}")
        }

        Log.d("DEBUG_TEST", "2. TEST broadcast gönderiliyor...")
        // Test broadcast gönder
        val testIntent = Intent("com.example.metatakip.TEST_CALL_RECEIVER")
        testIntent.putExtra("incoming_number", originalNumber)
        testIntent.putExtra("normalized_number", normalizedNumber)
        testIntent.putExtra("caller_name", callerName)
        testIntent.putExtra("is_rehber_kayitli", isRehberKayitli)
        testIntent.putExtra("is_program_kayitli", isProgramKayitli)

        try {
            sendBroadcast(testIntent)
            Log.d("DEBUG_TEST", "✅ TEST broadcast gönderildi")
        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ TEST broadcast gönderilemedi: ${e.message}")
        }

        Log.d("DEBUG_TEST", "3. Direkt popup açılıyor...")
        // Direkt popup aç
        try {
            val popupIntent = Intent(this, CallPopupActivity::class.java).apply {
                // ANA PARAMETRELER - CallPopupActivity'deki isimlerle aynı
                putExtra("callerName", callerName)
                putExtra("callerNumber", originalNumber)
                putExtra("normalizedNumber", normalizedNumber)

                // KONTROL PARAMETRELERİ - CallPopupActivity'deki isimlerle aynı
                putExtra("programKayitli", isProgramKayitli)
                putExtra("rehberKayitli", isRehberKayitli)

                // TEST PARAMETRELERİ
                putExtra("isTestCall", true)

                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(popupIntent)
            Log.d("DEBUG_TEST", "✅ Direkt popup açıldı (CallReceiver Test)")
        } catch (e: Exception) {
            Log.e("DEBUG_TEST", "❌ Direkt popup açılamadı: ${e.message}")
        }

        val statusMessage = buildString {
            append("📡 CallReceiver test edildi: ")
            append(callerName)
            append(" (")
            append(originalNumber)
            append(")")
        }

        tvStatus.text = statusMessage
        Toast.makeText(this, "CallReceiver test edildi ve popup açıldı!", Toast.LENGTH_LONG).show()

        Log.d("DEBUG_TEST", "=== CALLRECEIVER TESTİ TAMAMLANDI ===")
    }

    private fun checkPermissions() {
        Log.d("DEBUG_TEST", "=== İZİN KONTROLÜ BAŞLIYOR ===")

        // İzin kontrolü yapılacak
        val requiredPermissions = arrayOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.ANSWER_PHONE_CALLS,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.READ_CONTACTS // Rehber okuma izni eklendi
        )

        val missingPermissions = mutableListOf<String>()

        Log.d("DEBUG_TEST", "İzinler kontrol ediliyor...")
        for (permission in requiredPermissions) {
            val hasPermission = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d("DEBUG_TEST", "$permission: ${if (hasPermission) "✅ VAR" else "❌ YOK"}")

            if (!hasPermission) {
                missingPermissions.add(permission)
            }
        }

        if (missingPermissions.isEmpty()) {
            tvStatus.text = "✅ Tüm izinler verilmiş"
            Toast.makeText(this, "Tüm izinler zaten verilmiş!", Toast.LENGTH_LONG).show()
            Log.d("DEBUG_TEST", "✅ TÜM İZİNLER VERİLMİŞ")
        } else {
            Log.d("DEBUG_TEST", "⚠️ Eksik izinler: ${missingPermissions.size} adet")
            requestPermissions(missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            tvStatus.text = "⚠️ ${missingPermissions.size} izin isteniyor..."
            Toast.makeText(this, "${missingPermissions.size} izin isteniyor...", Toast.LENGTH_SHORT).show()
        }

        Log.d("DEBUG_TEST", "=== İZİN KONTROLÜ TAMAMLANDI ===")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d("DEBUG_TEST", "İzin sonuçları alındı")

            var allGranted = true
            for ((index, result) in grantResults.withIndex()) {
                val permission = permissions.getOrNull(index) ?: "Bilinmeyen"
                val status = if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) "✅ VERİLDİ" else "❌ REDDEDİLDİ"
                Log.d("DEBUG_TEST", "$permission: $status")

                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                }
            }

            if (allGranted) {
                tvStatus.text = "✅ Tüm izinler verildi!"
                Toast.makeText(this, "Tüm izinler başarıyla verildi!", Toast.LENGTH_LONG).show()
                Log.d("DEBUG_TEST", "🎉 TÜM İZİNLER VERİLDİ!")
            } else {
                tvStatus.text = "❌ Bazı izinler reddedildi"
                Toast.makeText(this, "Bazı izinler reddedildi. Testler sınırlı çalışabilir.", Toast.LENGTH_LONG).show()
                Log.d("DEBUG_TEST", "⚠️ BAZI İZİNLER REDDEDİLDİ")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Aktivite kapanırken efektleri durdur
        stopRingtone()
        stopVibration()
        handler.removeCallbacksAndMessages(null)
        Log.d("DEBUG_TEST", "TestCallActivity kapatıldı")
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}