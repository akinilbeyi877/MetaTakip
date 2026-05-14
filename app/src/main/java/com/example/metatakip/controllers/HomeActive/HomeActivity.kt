package com.example.metatakip.controllers.HomeActive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.metatakip.R
import com.example.metatakip.controllers.adminConfigiration.AdminConfigurationActivity
import com.example.metatakip.controllers.callphonelast.CallLogsListActivity
import com.example.metatakip.controllers.callphonelast.CallSyncService
import com.example.metatakip.controllers.callphonelast.CentralServerService
import com.example.metatakip.controllers.callphonelast.DeviceSetupActivity
import com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity
import com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity
import com.example.metatakip.controllers.poupsms.CallForegroundService
import com.example.metatakip.controllers.poupsms.CallScreeningServiceImpl
import com.example.metatakip.controllers.poupsms.TestCallActivity
import dao.MetaTakipCustomerDao
import com.example.metatakip.controllers.callphonelast.DeviceManager
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.entityModel.SessionManager
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.ScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.metatakip.controllers.services.OrderNotificationService
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.drive.DriveBackupManager
import com.example.metatakip.feature_backup.firebase.FirebaseRealtimeBridgeManager
import com.example.metatakip.feature_backup.sync.SyncStatusStore
import com.example.metatakip.feature_backup.sync.UiLogManager
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.ChangeLogManager
import com.example.metatakip.feature_backup.util.InstantBackupManager
import com.example.metatakip.feature_backup.util.MetaTakipDbLock
import com.example.metatakip.feature_backup.worker.AutoBackupLogStore
import com.example.metatakip.feature_data.db.MetaTakipDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var scrollLog: ScrollView
    private lateinit var txtLog: TextView
    private lateinit var cardSyncLogPanel: View
    private lateinit var tvSyncLogArrow: TextView
    private lateinit var layoutIpInfo: LinearLayout
    private lateinit var tvIpInfoArrow: TextView

    private lateinit var tvCentralIpInfo: TextView
    private lateinit var btnEditIp: Button
    private lateinit var btnSyncCalls: LinearLayout

    private val DIALER_PREFS = "dialer_prefs"
    private val DIALER_ASKED = "dialer_asked"

    private val CALL_SCREENING_PREFS = "call_screening_prefs"
    private val CALL_SCREENING_SHOWN = "shown"
    private lateinit var tvDagitilacaklarCount: TextView

    private lateinit var sessionManager: SessionManager

    private lateinit var tvMusterilerCount: TextView
    private lateinit var tvAlinacakSiparislerCount: TextView
    private lateinit var tvTeslimAlinanlarCount: TextView
    private lateinit var tvTeslimEdilenlerCount: TextView
    private lateinit var tvTekrarIslemeAlinanlarCount: TextView
    private lateinit var btnCallLogs: LinearLayout
    private lateinit var tvCallLogsCount: TextView
    private lateinit var btnNotificationLogs: LinearLayout
    private lateinit var tvNotificationLogCount: TextView
    private var refreshReceiverRegistered = false
    private lateinit var btnClearDatabase: Button
    private lateinit var layoutSyncBanner: LinearLayout
    private lateinit var tvSyncBanner: TextView

    // Mevcut sync durumunu dialog için tut
    private var lastSyncMsg: String = ""
    private var lastSyncIsBusy: Boolean = false

    companion object {
        private const val TAG = "HomeActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val CALL_PERMISSION_REQUEST_CODE = 1002
        private const val PREFS_NAME = "app_prefs"
        private const val FIRST_LAUNCH_KEY = "first_launch"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DeviceManager.initialize(this)
        sessionManager = SessionManager(this)

        if (!DeviceManager.isDeviceConfigured(this)) {
            startActivity(Intent(this, DeviceSetupActivity::class.java))
            finish()
            return
        }

        ChangeLogManager.addListener(object : ChangeLogManager.OnChangeListener {
            override fun onNewChange(change: ChangeLog) {
                InstantBackupManager.onNewChange(applicationContext, change)
            }
            override fun onChangesUpdated(changes: List<ChangeLog>) {}
        })

        if (DeviceManager.isCentralDevice(this)) {
            Log.d("MERKEZ", "🚀 Merkez cihaz tespit edildi, yerel IP sunucusu başlatılıyor")
            val intent = Intent(this, CentralServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        setContentView(R.layout.activity_home)

        OrderNotificationService.start(this)

        layoutIpInfo = findViewById(R.id.layoutIpInfo)
        btnSyncCalls = findViewById(R.id.btnSyncCalls)
        tvCentralIpInfo = findViewById(R.id.tvCentralIpInfo)
        btnEditIp = findViewById(R.id.btnEditIp)

        initViewReferences()
        observeSyncStatus()
        observeSyncLogs()
        refreshCentralIpUI()

        btnSyncCalls.setOnClickListener {
            lifecycleScope.launch {
                UiLogManager.log("Tam senkronizasyon baslatiliyor...")
                val result = CallSyncService(this@HomeActivity).syncAllCallLogs()
                UiLogManager.log("Cagri gonderimleri: ${result.message}")
                try {
                    UiLogManager.log("Veri degisiklikleri buluta gonderiliyor...")
                    val syncOk = FirebaseRealtimeBridgeManager.forceSyncNow(this@HomeActivity)
                    if (syncOk) {
                        UiLogManager.log("Degisiklikler buluta gonderildi.")
                    } else {
                        UiLogManager.log("Gonderilecek degisiklik yok veya Drive bagli degil.")
                    }
                } catch (e: Exception) {
                    UiLogManager.log("Bulut gonderi hatasi: ${e.message}")
                }
                updateCounts()
                Toast.makeText(this@HomeActivity, "Islem Tamamlandi", Toast.LENGTH_SHORT).show()
            }
        }

        btnEditIp.setOnClickListener {
            startActivity(Intent(this, DeviceSetupActivity::class.java))
        }

        showPermissionExplanation()
        setupButtons()
        updateCounts()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRedirectCallScreening()
            }, 2000)
        }

        val company = DeviceManager.getCompanyName(this)
        val user = DeviceManager.getUserName(this)
        val role = DeviceManager.getUserRole(this)
        Toast.makeText(this, "Hoş geldin $user ($company - $role)", Toast.LENGTH_LONG).show()
    }

    private fun observeSyncLogs() {
        // ── Toggle: Sync Log — başlangıçta gizli, başlığa tıklayınca aç/kapat ──
        findViewById<LinearLayout>(R.id.tvSyncLogToggle)?.setOnClickListener {
            val isVisible = cardSyncLogPanel.visibility == View.VISIBLE
            cardSyncLogPanel.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvSyncLogArrow.text = if (isVisible) "▼ göster" else "▲ gizle"
        }

        // ── Toggle: Merkez Cihaz Bilgileri — Sync Log ile aynı yapı ──
        findViewById<LinearLayout>(R.id.layoutIpInfoToggle)?.setOnClickListener {
            val isVisible = layoutIpInfo.visibility == View.VISIBLE
            layoutIpInfo.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvIpInfoArrow.text = if (isVisible) "▼ göster" else "▲ gizle"
        }

        val textView = findViewById<TextView>(R.id.txtLog)
        val logScrollView = findViewById<com.example.metatakip.controllers.HomeActive.LockableScrollView>(R.id.scrollLog)
        val mainRoot = findViewById<com.example.metatakip.controllers.HomeActive.HomeRootScrollView>(R.id.mainRootScrollView)
        val btnCopy = findViewById<TextView>(R.id.btnCopyLogs)

        btnCopy?.setOnClickListener {
            val fullLogs = textView?.text.toString()
            if (fullLogs.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Sync Logs", fullLogs)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "✅ Tüm detaylar kopyalandı!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val touchListener = android.view.View.OnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> {
                    mainRoot?.isScrollLocked = true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (textView != null && !textView.hasSelection()) {
                        mainRoot?.isScrollLocked = false
                    }
                }
            }
            false
        }
        textView?.setOnTouchListener(touchListener)
        logScrollView?.setOnTouchListener(touchListener)

        textView?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (textView != null && !textView.hasSelection()) {
                logScrollView?.post {
                    logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    AutoBackupLogStore.logs.collect { newLog ->
                        withContext(Dispatchers.Main) {
                            textView?.append("\n$newLog")
                            checkLogLimit(textView)
                        }
                    }
                }

                launch(Dispatchers.IO) {
                    try {
                        val filter = "DriveLockManager:D DriveDownloadHelper:D ZipRestoreHelper:E *:S"
                        val process = Runtime.getRuntime().exec("logcat -v time -t 1 $filter")
                        val reader = process.inputStream.bufferedReader()
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            val cleanMessage = when {
                                line.contains("🔐 Kilit Alındı") -> "🔐 Sunucu meşguliyet kilidi alındı."
                                line.contains("🔓 Kilit Serbest Bırakıldı") -> "🔓 İşlem bitti, bulut hattı serbest."
                                line.contains("❌") || line.contains("💥") || line.contains("Error") ->
                                    "⚠️ Teknik Hata: ${line.substringAfterLast(":")}"
                                else -> null
                            }
                            if (cleanMessage != null) {
                                withContext(Dispatchers.Main) {
                                    if (textView != null) {
                                        textView.append("\n[TEKNİK] $cleanMessage")
                                        checkLogLimit(textView)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    private fun checkLogLimit(textView: TextView?) {
        if (textView == null) return
        if (textView.lineCount > 4000) {
            val lines = textView.text.toString().lines()
            if (lines.size > 1000) {
                textView.text = lines.takeLast(2500).joinToString("\n")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UiLogManager.log("▶️ HomeActivity açıldı")
        updateCounts()
        checkCallPermissionsSilently()
        refreshCentralIpUI()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkIfCallScreeningIsActive()
        }
        // Gmail uyuşmazlığını anlık kontrol et (Firebase sinyali gelmese de uyarı görünsün)
        applyGmailMismatchWarningIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        UiLogManager.log("⏸️ HomeActivity arka plana geçti")
    }

    override fun onDestroy() {
        super.onDestroy()
        UiLogManager.log("🛑 HomeActivity kapandı")
        UiLogManager.setListener(null)
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "🔥 onReceive çalıştı: ${intent?.action}")
            if (intent?.action == "com.example.metatakip.REFRESH_UI") {
                runOnUiThread { updateCounts() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!refreshReceiverRegistered) {
            val filter = IntentFilter("com.example.metatakip.REFRESH_UI")
            ContextCompat.registerReceiver(
                this, refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
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

    private fun refreshCentralIpUI() {
        val config = DeviceManager.getDeviceConfig(this)
        if (config == null) {
            layoutIpInfo.visibility = View.GONE
            tvIpInfoArrow.text = "▼ göster"
            return
        }
        // Panel'i otomatik açmıyoruz — kullanıcı toggle ile açar
        val savedCentralIp = config.centralIp
        val deviceIp = getLocalIpAddress()

        if (config.isCentralDevice) {
            tvCentralIpInfo.text = "🏢 MERKEZ CİHAZ\n📡 Merkez IP (Kayıtlı): $savedCentralIp\n📱 Telefon IP: $deviceIp"
            btnEditIp.text = "IP Değiştir"
            btnEditIp.visibility = View.VISIBLE
            btnSyncCalls.visibility = View.GONE
            return
        }

        if (isValidIp(savedCentralIp)) {
            tvCentralIpInfo.text = "📡 Merkez IP: $savedCentralIp\n📱 Telefon IP: $deviceIp"
            btnEditIp.visibility = View.VISIBLE
            btnSyncCalls.isEnabled = true
            btnSyncCalls.alpha = 1f
        } else {
            tvCentralIpInfo.text = "❌ Hatalı Merkez IP: ${savedCentralIp ?: "-"}\n📱 Telefon IP: $deviceIp"
            btnEditIp.visibility = View.VISIBLE
            btnEditIp.text = "IP Ayarla"
            btnSyncCalls.isEnabled = false
            btnSyncCalls.alpha = 0.5f
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                for (addr in java.util.Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            "IP bulunamadı"
        } catch (e: Exception) {
            "Hata: ${e.message}"
        }
    }

    private fun isValidIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        val regex = Regex("^((25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)$")
        return regex.matches(ip)
    }

    private fun checkAndRedirectCallScreening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = getSharedPreferences(CALL_SCREENING_PREFS, MODE_PRIVATE)
        val alreadyShown = prefs.getBoolean(CALL_SCREENING_SHOWN, false)
        if (alreadyShown) return
        try {
            val intent = Intent("android.settings.CALL_SCREENING_SETTINGS")
            val activities = packageManager.queryIntentActivities(intent, 0)
            if (activities.isNotEmpty()) {
                startActivity(intent)
                Toast.makeText(this, "📞 Arama yakalama için lütfen MetaTakip'i seçin", Toast.LENGTH_LONG).show()
            } else {
                showAlternativeCallScreeningDialog()
            }
            prefs.edit { putBoolean(CALL_SCREENING_SHOWN, true) }
        } catch (e: Exception) {
            Log.e(TAG, "CallScreening ayar ekranı açılamadı", e)
            showAlternativeCallScreeningDialog()
        }
    }

    private fun showAlternativeCallScreeningDialog() {
        AlertDialog.Builder(this)
            .setTitle("📞 Arama Yakalama Ayarları")
            .setMessage("""
                Arama yakalama özelliğini etkinleştirmek için:
                
                1. Telefon Ayarları'na gidin
                2. "Uygulamalar" veya "Özel erişim" bölümünü bulun
                3. "Varsayılan telefon uygulaması" seçeneğini tıklayın
                4. "MetaTakip" uygulamasını seçin
                5. "Arama engelleme ve spam" ayarlarında MetaTakip'i etkinleştirin
                
                Bu ayarlar cihazınıza göre farklılık gösterebilir.
            """.trimIndent())
            .setPositiveButton("Ayarları Aç") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Ayarlar açılamadı", e)
                }
            }
            .setNegativeButton("Daha Sonra", null)
            .show()
    }

    private fun checkIfCallScreeningIsActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = getSharedPreferences(DIALER_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(DIALER_ASKED, false)) return
        try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            val isDefaultDialer = telecomManager.defaultDialerPackage == packageName
            if (!isDefaultDialer) {
                AlertDialog.Builder(this)
                    .setTitle("📞 Varsayılan Telefon Uygulaması")
                    .setMessage("Aramaları yakalayabilmek için MetaTakip'i varsayılan telefon uygulaması yapmanız önerilir.")
                    .setPositiveButton("Ayarla") { _, _ ->
                        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                        startActivity(intent)
                        prefs.edit { putBoolean(DIALER_ASKED, true) }
                    }
                    .setNegativeButton("Daha Sonra") { _, _ ->
                        prefs.edit { putBoolean(DIALER_ASKED, true) }
                    }
                    .setCancelable(false)
                    .show()
            } else {
                prefs.edit { putBoolean(DIALER_ASKED, true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Varsayılan dialer kontrol hatası", e)
        }
    }

    private fun showPermissionExplanation() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val explanationShown = prefs.getBoolean("permission_explanation_shown", false)
        if (!explanationShown) {
            val explanation = """
                📞 Arama Yakalama Sistemi İçin İzin Gereklidir:
                
                • Telefon Durumu: Gelen aramaları tespit etmek için
                • Çağrı Yönetimi: Aramaları ekranda göstermek için
                • Rehber: Müşteri bilgilerini kontrol etmek için
                • Bildirimler: Arama bildirimleri göstermek için
                • Ekran Üstü: Aramayı popup olarak göstermek için
                
                🔒 Bu izinler sadece arama tespiti için kullanılır.
                Hiçbir kişisel veri paylaşılmaz veya saklanmaz.
                
                Devam etmek için lütfen tüm izinleri verin.
            """.trimIndent()
            AlertDialog.Builder(this)
                .setTitle("İzin Açıklaması")
                .setMessage(explanation)
                .setPositiveButton("İzinleri İsteyin") { _, _ ->
                    prefs.edit { putBoolean("permission_explanation_shown", true) }
                    checkAndRequestCallPermissions()
                }
                .setCancelable(false)
                .show()
        } else {
            checkAndRequestCallPermissions()
        }
    }

    private fun checkAndRequestCallPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.CALL_PHONE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.MANAGE_OWN_CALLS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_CONTACTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), CALL_PERMISSION_REQUEST_CODE)
            Toast.makeText(this, "Arama özellikleri için izin gerekiyor", Toast.LENGTH_SHORT).show()
        } else {
            startCallServices()
            Toast.makeText(this, "✅ Tüm arama izinleri verilmiş", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCallServices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) startCallForegroundService()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) enableCallScreening()
        checkOverlayPermission()
    }

    private fun startCallForegroundService() {
        try {
            val intent = Intent(this, CallForegroundService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Service başlatma hatası", e)
            Toast.makeText(this, "Arama servisi başlatılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun enableCallScreening() {
        try {
            val intent = Intent(this, CallScreeningServiceImpl::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "CallScreeningService başlatılamadı", e)
            Toast.makeText(this, "Arama servisi başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Ekran Üstü Görüntü İzni")
                .setMessage("Gelen aramaları popup olarak göstermek için ekran üstünde görüntüleme izni gerekiyor. Ayarlara yönlendirilsin mi?")
                .setPositiveButton("Evet") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .setNegativeButton("Şimdi Değil", null)
                .show()
        }
    }

    private fun checkCallPermissionsSilently() {
        val hasPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasCallLogPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasContactsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasManageCallsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_OWN_CALLS) == PackageManager.PERMISSION_GRANTED
        else true
        if (!hasPhonePermission || !hasCallLogPermission || !hasContactsPermission || !hasManageCallsPermission)
            Log.w(TAG, "⚠️ Bazı arama izinleri eksik")
        else
            Log.d(TAG, "✅ Tüm arama izinleri mevcut")
    }

    // =========================
    // 📊 UI / COUNTS
    // =========================
    private fun initViewReferences() {
        tvMusterilerCount = findViewById(R.id.tvMusterilerCount)
        tvAlinacakSiparislerCount = findViewById(R.id.tvYeniSiparisCount)
        tvTeslimAlinanlarCount = findViewById(R.id.tvTeslimAlinanlarCount)
        tvDagitilacaklarCount = findViewById(R.id.tvDagitilacaklarCount)
        tvTeslimEdilenlerCount = findViewById(R.id.tvTeslimEdilenlerCount)
        tvTekrarIslemeAlinanlarCount = findViewById(R.id.tvTekrarIslemeAlinanlarCount)

        layoutSyncBanner = findViewById(R.id.layoutSyncBanner)
        tvSyncBanner = findViewById(R.id.tvSyncBanner)

        btnCallLogs = findViewById(R.id.btnCallLogs)
        tvCallLogsCount = findViewById(R.id.tvCallLogsCount)
        btnNotificationLogs = findViewById(R.id.btnNotificationLogs)
        tvNotificationLogCount = findViewById(R.id.tvNotificationLogCount)
        btnClearDatabase = findViewById(R.id.btnClearDatabase)
        scrollLog = findViewById(R.id.scrollLog)
        txtLog = findViewById(R.id.txtLog)
        cardSyncLogPanel = findViewById(R.id.cardSyncLogPanel)
        tvSyncLogArrow   = findViewById(R.id.tvSyncLogArrow)
        tvIpInfoArrow    = findViewById(R.id.tvIpInfoArrow)
    }

    // =========================
    // 🔄 SYNC BANNER (tıklanabilir detay)
    // =========================
    private fun observeSyncStatus() {
        lifecycleScope.launch {
            SyncStatusStore.state.collectLatest { state ->
                runOnUiThread {
                    // Durumu kaydet — dialog açıldığında kullanacağız
                    lastSyncMsg    = state.message
                    lastSyncIsBusy = state.isBusy

                    if (!state.isVisible) {
                        layoutSyncBanner.visibility = View.GONE
                        // Sync yokken bile Drive sorunu varsa uyarı göster
                        applyGmailMismatchWarningIfNeeded()
                        return@runOnUiThread
                    }

                    layoutSyncBanner.visibility = View.VISIBLE

                    val pos = state.queuePosition
                    val text = buildString {
                        append(state.message)
                        if (pos != null && pos > 0) append(" (Sıra: $pos)")
                        append("  ›")   // tıklanabilir göstergesi
                    }
                    tvSyncBanner.text = text

                    // Renkli banner: kırmızı=hata, sarı=meşgul, yeşil=tamam
                    val (bgColor, txtColor) = when {
                        state.message.contains("❌") || state.message.contains("başarısız") ->
                            "#FFCDD2" to "#B71C1C"
                        state.isBusy ->
                            "#FFF3CD" to "#E65100"
                        else ->
                            "#D1FAE5" to "#1B5E20"
                    }
                    layoutSyncBanner.setBackgroundColor(android.graphics.Color.parseColor(bgColor))
                    tvSyncBanner.setTextColor(android.graphics.Color.parseColor(txtColor))

                    // Gmail kontrolü burada ÇAĞRILMAZ — aktif sync mesajını ezmemek için
                }
            }
        }

        // Tıklayınca tam detay dialog'u aç
        layoutSyncBanner.setOnClickListener { showSyncDetailDialog() }
    }

    /**
     * Şu an bağlı Gmail ile son yedek indirmede kullanılan Gmail farklıysa
     * banner'ı turuncu uyarıya çevirir. Firebase sinyali gelmediğinde de görünür.
     */
    private fun applyGmailMismatchWarningIfNeeded() {
        val connected        = BackupPreferences.isActuallyDriveConnected()
        val currentEmail     = BackupPreferences.getActualDriveEmail()?.lowercase()?.trim()
        val lastRestoreEmail = BackupPreferences.getLastRestoreEmail().lowercase().trim()

        when {
            // 1) Drive hiç bağlı değil
            !connected -> {
                layoutSyncBanner.visibility = View.VISIBLE
                layoutSyncBanner.setBackgroundColor(android.graphics.Color.parseColor("#B71C1C"))
                tvSyncBanner.setTextColor(android.graphics.Color.WHITE)
                tvSyncBanner.text = "❌ Drive bağlantısı yok — Gmail girilmedi › Kontrol Et"
            }
            // 2) Drive bağlı, farklı Gmail — sinyal gelmez
            currentEmail != null && lastRestoreEmail.isNotBlank()
                    && currentEmail != lastRestoreEmail -> {
                layoutSyncBanner.visibility = View.VISIBLE
                layoutSyncBanner.setBackgroundColor(android.graphics.Color.parseColor("#E65100"))
                tvSyncBanner.setTextColor(android.graphics.Color.WHITE)
                tvSyncBanner.text = "⚠️ Gmail farklı! ($currentEmail) Sinyal alınamıyor — Detay ›"
            }
            // 3) Drive bağlı ama hiç senkronizasyon alınmamış — Gmail doğru mu?
            connected && lastRestoreEmail.isBlank() -> {
                layoutSyncBanner.visibility = View.VISIBLE
                layoutSyncBanner.setBackgroundColor(android.graphics.Color.parseColor("#F57F17"))
                tvSyncBanner.setTextColor(android.graphics.Color.WHITE)
                tvSyncBanner.text = "⚠️ Henüz senkronizasyon alınmadı — Gmail'i kontrol edin › Detay"
            }
            // 4) Her şey doğru — banner gizli kalsın
            else -> { /* sorun yok */ }
        }
    }

    private fun showSyncDetailDialog() {
        val connected         = BackupPreferences.isActuallyDriveConnected()
        val email             = BackupPreferences.getActualDriveEmail() ?: "—"
        val folder            = BackupPreferences.getBackupFolderName().ifBlank { "—" }
        val device            = BackupPreferences.getDeviceName().ifBlank { "—" }
        val lastSyncDev       = BackupPreferences.getLastSyncDevice().ifBlank { "—" }
        val autoOn            = BackupPreferences.isAutoBackupEnabled()
        val lastBackupMs      = BackupPreferences.getLastBackupTime()
        val lastBackupStr     = if (lastBackupMs > 0)
            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastBackupMs)) else "—"
        val times             = listOf(
            BackupPreferences.getAutoBackupTime1(),
            BackupPreferences.getAutoBackupTime2(),
            BackupPreferences.getAutoBackupTime3()
        ).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }
        val lastRestoreFile   = BackupPreferences.getLastRestoreFile().ifBlank { "—" }
        val lastRestoreEmail  = BackupPreferences.getLastRestoreEmail().ifBlank { "—" }
        val lastRestoreFolder = BackupPreferences.getLastRestoreFolder().ifBlank { "—" }

        // Durum renk & metin
        val (durumHeaderColor, durumBodyBg, durumLabel) = when {
            lastSyncMsg.contains("❌") || lastSyncMsg.contains("başarısız") ->
                Triple("#C62828", "#FFEBEE", "🔴  HATA\n${lastSyncMsg}")
            lastSyncMsg.contains("✅") || lastSyncMsg.contains("Tamam") ->
                Triple("#2E7D32", "#E8F5E9", "🟢  TAMAM\n${lastSyncMsg}")
            lastSyncMsg.isNotBlank() ->
                Triple("#E65100", "#FFF3E0", "🟡  İŞLEM DEVAM EDİYOR\n${lastSyncMsg}")
            else ->
                Triple("#546E7A", "#F5F5F5", "⚪  Bekleniyor")
        }

        // --- Yardımcı fonksiyonlar ---
        fun dpPx(dp: Float) = (dp * resources.displayMetrics.density).toInt()

        fun roundedBg(colorHex: String, tl: Float = 0f, tr: Float = 0f, bl: Float = 0f, br: Float = 0f): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.parseColor(colorHex))
                cornerRadii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
            }
        }

        fun makeRow(ctx: android.content.Context, label: String, value: String): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpPx(14f), dpPx(7f), dpPx(14f), dpPx(7f))
            }
            row.addView(TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#78909C"))
                layoutParams = LinearLayout.LayoutParams(dpPx(120f), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(ctx).apply {
                text = value
                textSize = 12.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.parseColor("#1A237E"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            return row
        }

        fun makeDivider(ctx: android.content.Context): View {
            return View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(dpPx(14f), 0, dpPx(14f), 0) }
                setBackgroundColor(android.graphics.Color.parseColor("#ECEFF1"))
            }
        }

        fun makeCard(
            ctx: android.content.Context,
            headerLabel: String,
            headerBgHex: String,
            bodyBgHex: String,
            rows: List<Pair<String, String>>
        ): LinearLayout {
            val r = dpPx(10f).toFloat()
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpPx(12f) }
                // Drop shadow via elevation on Lollipop+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) elevation = dpPx(2f).toFloat()
            }
            // Header
            card.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpPx(14f), dpPx(9f), dpPx(14f), dpPx(9f))
                background = roundedBg(headerBgHex, r, r, 0f, 0f)
                addView(TextView(ctx).apply {
                    text = headerLabel
                    textSize = 11.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(android.graphics.Color.WHITE)
                })
            })
            // Body
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(android.graphics.Color.parseColor(bodyBgHex))
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
                    setStroke(dpPx(0.5f), android.graphics.Color.parseColor("#CFD8DC"))
                }
            }
            rows.forEachIndexed { i, (lbl, val_) ->
                body.addView(makeRow(ctx, lbl, val_))
                if (i < rows.size - 1) body.addView(makeDivider(ctx))
            }
            card.addView(body)
            return card
        }

        // --- Dialog layout ---
        val ctx = this
        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpPx(16f), dpPx(16f), dpPx(16f), dpPx(8f))
            setBackgroundColor(android.graphics.Color.parseColor("#EEF2F7"))
        }
        scroll.addView(root)
        scroll.setBackgroundColor(android.graphics.Color.parseColor("#EEF2F7"))

        // Kart 1 — Şu Anki Durum
        root.addView(makeCard(ctx, "📊  ŞU ANKİ DURUM", durumHeaderColor, durumBodyBg, listOf(
            "Durum" to durumLabel
        )))

        // Kart 2 — Son Alınan Yedek
        root.addView(makeCard(ctx, "📥  SON ALINAN YEDEK", "#1565C0", "#FFFFFF", listOf(
            "📄 Yedek Adı"      to lastRestoreFile,
            "📲 Gönderen Cihaz" to lastSyncDev,
            "📁 Drive Klasörü"  to lastRestoreFolder,
            "📧 İndiren Gmail"  to lastRestoreEmail
        )))

        // Kart 3 — Bu Cihaz
        root.addView(makeCard(ctx, "📱  BU CİHAZ", "#4527A0", "#FFFFFF", listOf(
            "Cihaz Adı"      to device,
            "Gmail"          to email,
            "Drive Klasörü"  to folder,
            "Drive Bağlantı" to if (connected) "✅ BAĞLI" else "❌ BAĞLI DEĞİL",
            "Son Yedek"      to lastBackupStr,
            "Oto. Yedek"     to if (autoOn) "AÇIK ($times)" else "KAPALI"
        )))

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔍 Senkronizasyon Detayı")
            .setView(scroll)
            .setPositiveButton("TAMAM", null)
            .create()
        dialog.show()
        // Dialog başlığını da Home rengine uygun stilize et
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
    }

    private fun updateCounts() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val customerDao = MetaTakipCustomerDao(this@HomeActivity)
                val customerCount = customerDao.getAllCustomers().size
                val siparisDao = OrderDaoImpl(this@HomeActivity)
                val allSiparis = siparisDao.getAllSiparis()
                val callLogsDao = com.example.metatakip.data.metaTakipDb.crud.CallLogsDao(this@HomeActivity)
                val totalCalls = callLogsDao.getTotalCallCount()
                val counts = mapOf(
                    "yeni"        to allSiparis.count { it.isDeleted == 0 && it.durum == "Yeni Sipariş" },
                    "teslimAlinan" to allSiparis.count { it.isDeleted == 0 && it.durum == "Teslim Alındı" },
                    "dagitilacak" to allSiparis.count { it.isDeleted == 0 && it.durum == "Dağıtılacak" },
                    "teslimEdilen" to allSiparis.count { it.isDeleted == 0 && it.durum == "Teslim Edildi" },
                    "tekrarIsleme" to allSiparis.count { it.isDeleted == 0 && it.durum == "Tekrar İşleme Alındı" }
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    tvMusterilerCount.text = customerCount.toString()
                    findViewById<TextView>(R.id.tvMusterilerText).text = "📋 $customerCount Müşteri"
                    tvAlinacakSiparislerCount.text = counts["yeni"].toString()
                    tvTeslimAlinanlarCount.text = counts["teslimAlinan"].toString()
                    tvDagitilacaklarCount.text = counts["dagitilacak"].toString()
                    tvTeslimEdilenlerCount.text = counts["teslimEdilen"].toString()
                    tvTekrarIslemeAlinanlarCount.text = counts["tekrarIsleme"].toString()
                    tvCallLogsCount.text = totalCalls.toString()
                    tvNotificationLogCount.text =
                        com.example.metatakip.controllers.services.NotificationLogManager
                            .count(this@HomeActivity).toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sayaç güncelleme hatası", e)
            }
        }
    }

    private fun checkFirstLaunchPermissions() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean(FIRST_LAUNCH_KEY, true)
        if (firstLaunch) {
            requestInitialPermissions()
            prefs.edit { putBoolean(FIRST_LAUNCH_KEY, false) }
        }
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    // =========================
    // 🧭 BUTTONS
    // =========================
    private fun setupButtons() {
        findViewById<LinearLayout>(R.id.btnYeniMusteriEkle).setOnClickListener {
            startActivity(Intent(this, GenericFormActivity::class.java).putExtra("targetTable", "musteri"))
        }
        findViewById<LinearLayout>(R.id.btnMusteriler).setOnClickListener {
            startActivity(Intent(this, GenericListActivity::class.java)
                .putExtra("listType", "musteri").putExtra("pageTitle", "📋 Müşteriler").putExtra("source", "home"))
        }
        findViewById<LinearLayout>(R.id.btnYeniSiparisDurumu).setOnClickListener {
            startActivity(Intent(this, GenericListActivity::class.java)
                .putExtra("listType", "siparis").putExtra("filterDurum", "Yeni Sipariş").putExtra("pageTitle", "🛒 Alınacak Siparişler").putExtra("source", "home"))
        }
        findViewById<LinearLayout>(R.id.btnTeslimAlinanlar).setOnClickListener {
            startActivity(Intent(this, GenericListActivity::class.java)
                .putExtra("listType", "siparis").putExtra("filterDurum", "Teslim Alındı").putExtra("pageTitle", "📥 Teslim Alınanlar").putExtra("source", "home"))
        }
        findViewById<LinearLayout>(R.id.btnDagitilacaklar).setOnClickListener {
            startActivity(Intent(this, GenericListActivity::class.java)
                .putExtra("listType", "siparis").putExtra("filterDurum", "Dağıtılacak").putExtra("pageTitle", "🚚 DAĞITILACAK SİPARİŞLER").putExtra("source", "home"))
        }
        findViewById<LinearLayout>(R.id.btnTeslimEdilenler).setOnClickListener {
            startActivity(Intent(this, GenericListActivity::class.java)
                .putExtra("listType", "siparis").putExtra("filterDurum", "Teslim Edildi").putExtra("pageTitle", "✅ Teslim Edilenler").putExtra("source", "home"))
        }
        findViewById<LinearLayout>(R.id.btnTekrarIslemeAlinanlar).setOnClickListener {
            startActivity(Intent(this, GenericListActivity::class.java)
                .putExtra("listType", "siparis").putExtra("filterDurum", "Tekrar İşleme Alındı").putExtra("pageTitle", "🔄 Tekrar İşleme Alınanlar").putExtra("source", "home"))
        }
        btnCallLogs.setOnClickListener { showCallLogsOptionsDialog() }
        btnNotificationLogs.setOnClickListener {
            startActivity(Intent(this, com.example.metatakip.controllers.allGenericFormAndList.NotificationLogActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnAyarlar).setOnClickListener {
            startActivity(Intent(this, AdminConfigurationActivity::class.java))
        }
        findViewById<Button>(R.id.btnForceSync).setOnClickListener {
            lifecycleScope.launch {
                Toast.makeText(this@HomeActivity, "🔄 Senkronizasyon başlatılıyor...", Toast.LENGTH_SHORT).show()
                FirebaseRealtimeBridgeManager.forceSyncNow(this@HomeActivity)
            }
        }
        findViewById<LinearLayout>(R.id.btnTest).setOnClickListener {
            if (hasAllCallPermissions()) {
                startActivity(Intent(this, TestCallActivity::class.java))
            } else {
                Toast.makeText(this, "Önce tüm arama izinlerini verin", Toast.LENGTH_SHORT).show()
                checkAndRequestCallPermissions()
            }
        }
        findViewById<Button>(R.id.btnClearDatabase).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ DİKKAT: SİSTEMİ SIFIRLA")
                .setMessage("Tüm müşteriler, siparişler ve kayıtlar kalıcı olarak silinecek. Uygulama fabrika ayarlarına dönecek. Emin misiniz?")
                .setPositiveButton("EVET, TERTEMİZ YAP") { _, _ -> clearAllDataFromApp() }
                .setNegativeButton("VAZGEÇ", null)
                .setIcon(android.R.drawable.ic_delete)
                .show()
        }
        findViewById<Button>(R.id.btnCikis).setOnClickListener {
            sessionManager.clear()
            finishAffinity()
        }
    }

    private fun clearAllDataFromApp() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                UiLogManager.log("⚠️ Tam temizlik başlıyor (Yerel + Drive)...")
                synchronized(MetaTakipDbLock.lock) {
                    val db = MetaTakipDb.getInstance(this@HomeActivity).writableDatabase
                    db.beginTransaction()
                    try {
                        db.execSQL("PRAGMA foreign_keys=OFF")
                        val tables = listOf(
                            "musteri", "siparis", "urun", "firma", "personel", "unvan", "urun_tipi",
                            "mesaj_sablon", "call_logs", "change_log", "delete_log",
                            "etiket_sablon", "etiket_sablon_bilesen", "etiket_sayfa_ayar"
                        )
                        tables.forEach { table ->
                            db.execSQL("DELETE FROM \"$table\"")
                            db.execSQL("DELETE FROM sqlite_sequence WHERE name=\"$table\"")
                        }
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.setTransactionSuccessful()
                        UiLogManager.log("✅ Yerel veritabanı boşaltıldı.")
                    } finally {
                        db.endTransaction()
                    }
                }
                UiLogManager.log("☁️ Drive yedekleri siliniyor...")
                val result = DriveBackupManager.deleteAllBackupsSync(this@HomeActivity)
                if (result) {
                    UiLogManager.log("✅ Drive tertemiz: Tüm yedekler silindi.")
                } else {
                    UiLogManager.log("⚠️ Drive silme başarısız (Oturum kapalı olabilir).")
                }
                withContext(Dispatchers.Main) {
                    updateCounts()
                    Toast.makeText(this@HomeActivity, "Tüm sistem sıfırlandı!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("CLEAN", "Sıfırlama hatası", e)
                UiLogManager.log("❌ Hata: ${e.message}")
            }
        }
    }

    private fun showCallLogsOptionsDialog() {
        val menuHandler = com.example.metatakip.controllers.genericListFolder.RightClickMenuHandler(this)
        menuHandler.showCallManagementMenu()
    }

    // =========================
    // 🔄 PERMISSION RESULTS
    // =========================
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CALL_PERMISSION_REQUEST_CODE -> {
                var allGranted = true
                var grantedCount = 0
                for (result in grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) grantedCount++
                    else allGranted = false
                }
                if (allGranted) {
                    Toast.makeText(this, "✅ Tüm arama izinleri verildi!", Toast.LENGTH_SHORT).show()
                    startCallServices()
                } else {
                    val message = if (grantedCount > 0)
                        "$grantedCount izin verildi, bazı izinler reddedildi. Arama özellikleri sınırlı çalışabilir."
                    else
                        "Hiçbir izin verilmedi. Arama özellikleri çalışmayacak."
                    AlertDialog.Builder(this)
                        .setTitle("Eksik İzinler")
                        .setMessage("$message\n\nArama özelliklerinin tamamı için tüm izinleri vermelisiniz.")
                        .setPositiveButton("Tekrar İste") { _, _ -> checkAndRequestCallPermissions() }
                        .setNeutralButton("Ayarlar") { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        }
                        .setNegativeButton("İptal", null)
                        .show()
                }
            }
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Bildirim izni verildi")
                }
            }
        }
    }

    private fun hasAllCallPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.MANAGE_OWN_CALLS)
        }
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
