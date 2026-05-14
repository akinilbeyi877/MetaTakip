package com.example.metatakip.feature_backup.ui

import android.app.Activity
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.feature_backup.R
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.data.ImportRowResult
import com.example.metatakip.feature_backup.data.TableCatalog
import com.example.metatakip.feature_backup.drive.DriveAuthManager
import com.example.metatakip.feature_backup.drive.DriveBackupManager
import com.example.metatakip.feature_backup.drive.DriveDownloadHelper
import com.example.metatakip.feature_backup.drive.DriveHistoryManager
import com.example.metatakip.feature_backup.drive.DriveLockManager
import com.example.metatakip.feature_backup.drive.DriveQueueManager
import com.example.metatakip.feature_backup.drive.DriveUploadHelper
import com.example.metatakip.feature_backup.drive.ZipRestoreHelper
import com.example.metatakip.feature_backup.firebase.FirebaseRealtimeBridgeManager
import com.example.metatakip.feature_backup.local.CsvImportManager
import com.example.metatakip.feature_backup.local.LocalBackupManager
import com.example.metatakip.feature_backup.util.AppRestartUtil
import com.example.metatakip.feature_backup.util.BackupFolderType
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.ChangeLogManager
import com.example.metatakip.feature_backup.util.CsvExporter
import com.example.metatakip.feature_backup.util.DbFilePaths
import com.example.metatakip.feature_backup.util.FullBackupManager
import com.example.metatakip.feature_backup.util.MetaTakipDbLock
import com.example.metatakip.feature_backup.util.PartialBackupManager
import com.example.metatakip.feature_backup.util.TimeUtils
import com.example.metatakip.feature_backup.worker.AutoBackupLogStore
import com.example.metatakip.feature_backup.worker.AutoBackupScheduler
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BackupRestoreActivity : AppCompatActivity() {

    // ==================== DEĞİŞKENLER ====================
    private var selectedExternalFirmaName: String = ""
    private var isExternalMappingMode: Boolean = false
    private var isTemplateSelectionMode = false
    private var myTemplateHeaders = listOf<String>()

    private lateinit var tvAccount: TextView
    private lateinit var tvDriveStatusBadge: TextView
    private lateinit var tvImportPreview: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvCurrentFolder: TextView
    private lateinit var tvEmptyChanges: TextView
    private lateinit var tvAutoStatus: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvQueueStatus: TextView
    private lateinit var tvLastBackupInfo: TextView

    private lateinit var tvAutoTime1: TextView
    private lateinit var tvAutoTime2: TextView
    private lateinit var tvAutoTime3: TextView

    private lateinit var etFolderName: EditText
    private lateinit var etDeviceName: EditText

    private lateinit var btnSaveFolderName: Button
    private lateinit var btnSaveDeviceName: Button
    private lateinit var btnDriveFolderRow: LinearLayout
    private lateinit var tvDriveFolderRowSubtitle: TextView
    private lateinit var btnPickBackupFolder: LinearLayout
    private lateinit var tvPhoneFolderSubtitle: TextView
    private lateinit var tvPhoneFolderPath: TextView
    private lateinit var btnDeviceNameInfo: TextView
    private lateinit var btnBack: Button

    // Stepper
    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var layoutStep3: LinearLayout
    private lateinit var layoutStep4: LinearLayout
    private lateinit var layoutStep5: LinearLayout
    private lateinit var btnStep1Next: Button
    private lateinit var btnStep2Next: Button
    private lateinit var btnStep3Next: Button
    private lateinit var btnStep4Next: Button
    private lateinit var stepCircle1: androidx.cardview.widget.CardView
    private lateinit var stepCircle2: androidx.cardview.widget.CardView
    private lateinit var stepCircle3: androidx.cardview.widget.CardView
    private lateinit var stepCircle4: androidx.cardview.widget.CardView
    private lateinit var stepCircle5: androidx.cardview.widget.CardView
    private lateinit var tvStep1Num: TextView
    private lateinit var tvStep2Num: TextView
    private lateinit var tvStep3Num: TextView
    private lateinit var tvStep4Num: TextView
    private lateinit var tvStep5Num: TextView
    private lateinit var stepLine1: View
    private lateinit var stepLine2: View
    private lateinit var stepLine3: View
    private lateinit var stepLine4: View
    private lateinit var layoutStepTab1: LinearLayout
    private lateinit var layoutStepTab2: LinearLayout
    private lateinit var layoutStepTab3: LinearLayout
    private lateinit var layoutStepTab4: LinearLayout
    private lateinit var layoutStepTab5: LinearLayout
    private lateinit var tvSummaryContent: TextView
    private lateinit var btnShareSettings: Button
    private lateinit var btnCopySettings: Button
    private lateinit var btnClearDatabaseStep5: Button
    private var activeStep = 1

    private lateinit var btnConnectDrive: LinearLayout
    private lateinit var btnDisconnectDrive: LinearLayout
    private lateinit var btnEnableAuto: LinearLayout
    private lateinit var btnFullBackup: LinearLayout
    private lateinit var btnPartialBackup: LinearLayout
    private lateinit var btnExportCsvAll: LinearLayout
    private lateinit var btnImportCsv: LinearLayout
    private lateinit var btnRestoreFromZip: LinearLayout

    private lateinit var layoutTime1: LinearLayout
    private lateinit var layoutTime2: LinearLayout
    private lateinit var layoutTime3: LinearLayout

    private lateinit var rvRecentChanges: RecyclerView
    private lateinit var nestedScrollViewLog: NestedScrollView
    private lateinit var changeLogAdapter: ChangeLogAdapter

    private lateinit var driveAuthManager: DriveAuthManager

    private val checkHandler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private val checkInterval = 2000L

    private var progressDialog: AlertDialog? = null

    // ==================== LAUNCHER'LAR ====================

    private val pickCsvFileForTemplate = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val headers = withContext(Dispatchers.IO) {
                CsvImportManager.getCsvHeaders(this@BackupRestoreActivity, uri)
            }
            myTemplateHeaders = headers ?: emptyList()
            if (myTemplateHeaders.isEmpty()) {
                toast("Şablon başlıkları okunamadı!")
                return@launch
            }
            log("📋 Rehber şablon okundu: ${myTemplateHeaders.size} sütun.")
            toast("ŞİMDİ: Aktarılacak (X Firması) dosyasını seçin")
            pickCsvFileForData.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
        }
    }

    private val pickCsvFileForData = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val otherHeaders = withContext(Dispatchers.IO) {
                CsvImportManager.getCsvHeaders(this@BackupRestoreActivity, uri) ?: emptyList()
            }
            if (otherHeaders.isEmpty()) {
                toast("Dosya başlıkları okunamadı!")
                return@launch
            }
            showMappingDialog(uri, myTemplateHeaders, otherHeaders)
            isExternalMappingMode = false
        }
    }

    private val pickCsvFileForImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val fileName = uri.lastPathSegment ?: "dosya.csv"
            log("📂 Standart CSV import başlatıldı: $fileName")
            val results = withContext(Dispatchers.IO) {
                CsvImportManager.importCsvFile(
                    context = this@BackupRestoreActivity,
                    fileUri = uri,
                    contentResolver = contentResolver
                )
            }
            val successCount = results.count { it.status == ImportRowResult.Status.OK }
            showCsvImportPreview(results)
            toast("CSV Import tamam: $successCount satır işlendi")
            checkForNewChanges()
        }
    }

    private val pickBackupFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val treeUri = data.data ?: return@registerForActivityResult
            try {
                val takeFlags = data.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                BackupPreferences.setBackupFolderUri(treeUri.toString())
                val pickedFolder = DocumentFile.fromTreeUri(this, treeUri)
                val folderDisplayName = pickedFolder?.name ?: ""
                // Drive klasör adına DOKUNMA — sadece telefon klasörü bilgisini güncelle
                tvCurrentFolder.text = "Klasör adı: ${BackupPreferences.getBackupFolderName()}"
                updatePhoneFolderPathDisplay()
                lifecycleScope.launch { refreshDevicePanels() }
                log("📁 Telefon klasörü seçildi: $folderDisplayName  ($treeUri)")
                toast("✅ Telefon klasörü seçildi: $folderDisplayName")
            } catch (e: Exception) {
                log("❌ Klasör seçme hatası: ${e.message}")
                toast("Klasör seçme hatası: ${e.message}")
            }
        }

    private val pickRestoreZipLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            val fileName = DocumentFile.fromSingleUri(this, uri)?.name
                ?: uri.lastPathSegment
                ?: "backup.zip"
            AlertDialog.Builder(this)
                .setTitle("📦 Geri Yükleme Tipi")
                .setMessage("Dosya: $fileName\n\nBu dosyayı nasıl geri yüklemek istiyorsunuz?")
                .setPositiveButton("📦 Tam Restore") { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("Tam Geri Yükleme")
                        .setMessage("Bu işlem mevcut veritabanını tamamen değiştirir. Devam edilsin mi?")
                        .setPositiveButton("Evet") { _, _ -> restoreFullBackup(uri, fileName) }
                        .setNegativeButton("Vazgeç", null)
                        .show()
                }
                .setNeutralButton("🧩 Kısmi Restore") { _, _ ->
                    showTablePickerDialogForRestore(uri, fileName)
                }
                .setNegativeButton("İptal", null)
                .show()
        }

    // 🔧 DÜZELTME: Drive bağlandıktan sonra Firebase dinleyicisi yeni groupId ile yeniden başlatılıyor.
    // NEDEN: Uygulama ilk açılışında Drive email henüz kayıtlı değilse groupId eksik üretilir
    // (örn: "metatakip_yedek01"). Email kaydedilince groupId değişir ama dinleyici güncellenmez.
    // Bu satır olmadan iki cihaz farklı Firestore yollarını izler — sinyal hiç alınamaz.
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                BackupPreferences.setDriveConnected(true)
                BackupPreferences.setDriveEmail(account.email)
                // 🔧 KRİTİK: Email kaydedildikten HEMEN SONRA dinleyiciyi yeni groupId ile yeniden başlat
                FirebaseRealtimeBridgeManager.onDriveAccountChanged(this)
                updateAccountInfo()
                lifecycleScope.launch { refreshDevicePanels() }
                log("✅ Drive bağlandı: ${account.email}")
                toast("Drive bağlantısı başarılı")
            } catch (e: Exception) {
                log("❌ Drive bağlantı hatası: ${e.message}")
                toast("Drive bağlantısı başarısız")
            }
        }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_restore)

        driveAuthManager = DriveAuthManager(this)

        bindViews()
        setupRecyclerView()
        updateAccountInfo()
        updateFolderNameDisplay()
        updatePhoneFolderPathDisplay()
        loadSavedTimes()
        updateAutoBackupButtonText()
        etDeviceName.setText(BackupPreferences.getDeviceName())
        setClickListeners()
        // Daha önce kurulum yapılmışsa ilgili adımdan başla
        activeStep = when {
            BackupPreferences.getBackupFolderUri().isNullOrBlank() -> 1
            BackupPreferences.getBackupFolderName().isBlank() -> 2
            !BackupPreferences.isDriveConnected() -> 2
            !BackupPreferences.isAutoBackupEnabled() -> 3
            BackupPreferences.getDeviceName().isBlank() -> 4
            else -> 5
        }
        showStep(activeStep)

        lifecycleScope.launch {
            AutoBackupLogStore.logs.collect { line ->
                log(line)
            }
        }

        ChangeLogManager.initialize(this)

        loadRecentChanges()
        startPeriodicCheck()
        lifecycleScope.launch { refreshDevicePanels() }

        log("🚀 BackupRestoreActivity başlatıldı")
        log("📁 Klasör adı: ${BackupPreferences.getBackupFolderName()}")
        log("📂 Klasör URI: ${BackupPreferences.getBackupFolderUri() ?: "Seçilmedi"}")
        log("📱 Cihaz: ${BackupPreferences.getDeviceLabel()}")
    }

    override fun onResume() {
        super.onResume()
        checkForNewChanges()
        updateFolderNameDisplay()
        updatePhoneFolderPathDisplay()
        loadSavedTimes()
        updateAutoBackupButtonText()
        etDeviceName.setText(BackupPreferences.getDeviceName())
        lifecycleScope.launch { refreshDevicePanels() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicCheck()
        ChangeLogManager.removeListener(changeListener)
    }

    // ==================== VIEW BINDINGS ====================

    private fun bindViews() {
        tvAccount = findViewById(R.id.tvAccount)
        tvDriveStatusBadge = findViewById(R.id.tvDriveStatusBadge)
        tvImportPreview = findViewById(R.id.tvImportPreview)
        tvLog = findViewById(R.id.tvLog)
        tvCurrentFolder = findViewById(R.id.tvCurrentFolder)
        tvEmptyChanges = findViewById(R.id.tvEmptyChanges)
        tvAutoStatus = findViewById(R.id.tvAutoStatus)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvQueueStatus = findViewById(R.id.tvQueueStatus)
        tvLastBackupInfo = findViewById(R.id.tvLastBackupInfo)

        tvAutoTime1 = findViewById(R.id.tvAutoTime1)
        tvAutoTime2 = findViewById(R.id.tvAutoTime2)
        tvAutoTime3 = findViewById(R.id.tvAutoTime3)

        etFolderName = findViewById(R.id.etFolderName)
        etDeviceName = findViewById(R.id.etDeviceName)

        btnSaveFolderName = findViewById(R.id.btnSaveFolderName)
        btnSaveDeviceName = findViewById(R.id.btnSaveDeviceName)
        btnDriveFolderRow = findViewById(R.id.btnDriveFolderRow)
        tvDriveFolderRowSubtitle = findViewById(R.id.tvDriveFolderRowSubtitle)
        btnPickBackupFolder = findViewById(R.id.btnPickBackupFolder)
        tvPhoneFolderSubtitle = findViewById(R.id.tvPhoneFolderSubtitle)
        tvPhoneFolderPath = findViewById(R.id.tvPhoneFolderPath)
        btnDeviceNameInfo = findViewById(R.id.btnDeviceNameInfo)
        btnBack = findViewById(R.id.btnBack)

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        layoutStep3 = findViewById(R.id.layoutStep3)
        layoutStep4 = findViewById(R.id.layoutStep4)
        layoutStep5 = findViewById(R.id.layoutStep5)
        btnStep1Next = findViewById(R.id.btnStep1Next)
        btnStep2Next = findViewById(R.id.btnStep2Next)
        btnStep3Next = findViewById(R.id.btnStep3Next)
        btnStep4Next = findViewById(R.id.btnStep4Next)
        stepCircle1 = findViewById(R.id.stepCircle1)
        stepCircle2 = findViewById(R.id.stepCircle2)
        stepCircle3 = findViewById(R.id.stepCircle3)
        stepCircle4 = findViewById(R.id.stepCircle4)
        stepCircle5 = findViewById(R.id.stepCircle5)
        tvStep1Num = findViewById(R.id.tvStep1Num)
        tvStep2Num = findViewById(R.id.tvStep2Num)
        tvStep3Num = findViewById(R.id.tvStep3Num)
        tvStep4Num = findViewById(R.id.tvStep4Num)
        tvStep5Num = findViewById(R.id.tvStep5Num)
        stepLine1 = findViewById(R.id.stepLine1)
        stepLine2 = findViewById(R.id.stepLine2)
        stepLine3 = findViewById(R.id.stepLine3)
        stepLine4 = findViewById(R.id.stepLine4)
        layoutStepTab1 = findViewById(R.id.layoutStepTab1)
        layoutStepTab2 = findViewById(R.id.layoutStepTab2)
        layoutStepTab3 = findViewById(R.id.layoutStepTab3)
        layoutStepTab4 = findViewById(R.id.layoutStepTab4)
        layoutStepTab5 = findViewById(R.id.layoutStepTab5)
        tvSummaryContent = findViewById(R.id.tvSummaryContent)
        btnShareSettings = findViewById(R.id.btnShareSettings)
        btnCopySettings = findViewById(R.id.btnCopySettings)
        btnClearDatabaseStep5 = findViewById(R.id.btnClearDatabaseStep5)

        btnConnectDrive = findViewById(R.id.btnConnectDrive)
        btnDisconnectDrive = findViewById(R.id.btnDisconnectDrive)
        btnEnableAuto = findViewById(R.id.btnEnableAuto)
        btnFullBackup = findViewById(R.id.btnFullBackup)
        btnPartialBackup = findViewById(R.id.btnPartialBackup)
        btnExportCsvAll = findViewById(R.id.btnExportCsvAll)
        btnImportCsv = findViewById(R.id.btnImportCsv)
        btnRestoreFromZip = findViewById(R.id.btnRestoreFromZip)

        layoutTime1 = findViewById(R.id.layoutTime1)
        layoutTime2 = findViewById(R.id.layoutTime2)
        layoutTime3 = findViewById(R.id.layoutTime3)

        rvRecentChanges = findViewById(R.id.rvRecentChanges)
        nestedScrollViewLog = findViewById(R.id.nestedScrollViewLog)
    }

    private fun setupRecyclerView() {
        changeLogAdapter = ChangeLogAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        layoutManager.reverseLayout = true
        rvRecentChanges.layoutManager = layoutManager
        rvRecentChanges.adapter = changeLogAdapter
    }

    // ==================== CLICK LISTENERS ====================

    private fun setClickListeners() {

        // ── Stepper tab'larına tıklama — her zaman serbest ──
        layoutStepTab1.setOnClickListener { showStep(1) }
        layoutStepTab2.setOnClickListener { showStep(2) }
        layoutStepTab3.setOnClickListener { showStep(3) }
        layoutStepTab4.setOnClickListener { showStep(4) }
        layoutStepTab5.setOnClickListener { showStep(5) }

        // ── İleri butonları ──
        btnStep1Next.setOnClickListener {
            val uri = BackupPreferences.getBackupFolderUri()
            if (uri.isNullOrBlank()) {
                toast("Lütfen önce telefon klasörünü seçin")
            } else {
                showStep(2)
            }
        }

        btnStep2Next.setOnClickListener {
            val name = BackupPreferences.getBackupFolderName()
            if (name.isBlank()) {
                toast("Lütfen önce klasör adını kaydedın")
            } else {
                showStep(3)
            }
        }

        btnStep3Next.setOnClickListener {
            showStep(4)
        }

        btnStep4Next.setOnClickListener {
            showStep(5)
        }

        // ── Özet paylaşım butonları ──
        btnShareSettings.setOnClickListener {
            shareSettings()
        }

        btnCopySettings.setOnClickListener {
            val text = buildSummaryText()
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MetaTakip Ayarları", text))
            toast("Panoya kopyalandı ✓")
        }

        btnClearDatabaseStep5.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ DİKKAT: SİSTEMİ SIFIRLA")
                .setMessage("Tüm müşteriler, siparişler ve kayıtlar kalıcı olarak silinecek.\nDrive yedekleri de silinecek.\n\nEmin misiniz?")
                .setPositiveButton("EVET, TERTEMİZ YAP") { _, _ ->
                    clearAllDataFromBackupScreen()
                }
                .setNegativeButton("VAZGEÇ", null)
                .setIcon(android.R.drawable.ic_delete)
                .show()
        }

        // 🔧 DÜZELTME: Klasör adı değişince Drive kök klasör ID önbelleği temizlenir,
        // Firebase dinleyicisi yeni groupId ile yeniden başlatılır ve kullanıcı
        // diğer cihazlar için ne yapması gerektiği konusunda bilgilendirilir.
        btnSaveFolderName.setOnClickListener {
            val newFolderName = etFolderName.text.toString().trim()
            if (newFolderName.isBlank()) {
                toast("Lütfen klasör adı girin")
                return@setOnClickListener
            }
            val oldFolderName = BackupPreferences.getBackupFolderName()
            BackupPreferences.setBackupFolderName(newFolderName)

            if (newFolderName != oldFolderName) {
                // Eski Drive klasör ID'si artık geçersiz — temizle
                BackupPreferences.clearDriveRootFolderId()

                if (BackupPreferences.isDriveConnected()) {
                    // 🔧 KRİTİK: Bu cihazda Firebase dinleyicisini yeni groupId ile yeniden başlat
                    FirebaseRealtimeBridgeManager.onDriveAccountChanged(this)
                    log("🔄 Klasör adı değişti → Firebase dinleyici yeniden başlatıldı: $newFolderName")

                    // Kullanıcıya diğer cihazlar için ne yapması gerektiğini göster
                    AlertDialog.Builder(this)
                        .setTitle("✅ Klasör Adı Güncellendi")
                        .setMessage(
                            "Bu cihazda \"$newFolderName\" olarak ayarlandı ve " +
                            "senkronizasyon otomatik olarak yenilendi.\n\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                            "⚠️  DİĞER CİHAZLARDA YAPILMASI GEREKENLER:\n\n" +
                            "1️⃣  Yedekleme ekranını açın\n" +
                            "2️⃣  Klasör adını \"$newFolderName\" olarak girin ve kaydedin\n" +
                            "3️⃣  Drive bağlantısını kesin\n" +
                            "4️⃣  Drive'ı tekrar bağlayın\n\n" +
                            "Bu adımlar yapılmadan diğer cihazlar verileri göremez."
                        )
                        .setPositiveButton("Anladım", null)
                        .show()
                } else {
                    log("📁 Klasör adı değişti (Drive bağlı değil): $newFolderName")
                }
            }

            updateFolderNameDisplay()
            lifecycleScope.launch { refreshDevicePanels() }
            log("📁 Klasör adı kaydedildi: $newFolderName")
            toast("✅ Klasör adı kaydedildi")
        }

        btnSaveDeviceName.setOnClickListener {
            val newDeviceName = etDeviceName.text.toString().trim()
            if (newDeviceName.isBlank()) {
                toast("Cihaz adı boş olamaz")
                return@setOnClickListener
            }
            BackupPreferences.setDeviceName(newDeviceName)
            lifecycleScope.launch { refreshDevicePanels() }
            log("📱 Cihaz adı kaydedildi: ${BackupPreferences.getDeviceLabel()}")
            toast("✅ Cihaz adı kaydedildi")
        }

        btnDriveFolderRow.setOnClickListener {
            val mevcutAd = BackupPreferences.getBackupFolderName()

            val seçenekler = mutableListOf<String>()
            seçenekler.add("📁  Yeni klasör adı yaz")
            if (mevcutAd.isNotEmpty())
                seçenekler.add("✅  \"$mevcutAd\" olarak kaydet")
            seçenekler.add("📂  Drive'dan klasör seç")

            fun kaydet(ad: String) {
                if (ad.isBlank()) { toast("Klasör adı boş olamaz"); return }
                etFolderName.setText(ad)
                btnSaveFolderName.performClick()
            }

            val driveIdx = if (mevcutAd.isNotEmpty()) 2 else 1

            AlertDialog.Builder(this)
                .setTitle("📁 Drive Klasör Adı")
                .setItems(seçenekler.toTypedArray()) { _, idx ->
                    when {
                        idx == 0 -> {
                            val input = android.widget.EditText(this).apply {
                                hint = "Klasör adı"
                                setText(mevcutAd)
                                setPadding(48, 32, 48, 16)
                            }
                            AlertDialog.Builder(this)
                                .setTitle("Yeni Klasör Adı")
                                .setView(input)
                                .setPositiveButton("Kaydet") { _, _ ->
                                    kaydet(input.text.toString().trim())
                                }
                                .setNegativeButton("İptal", null)
                                .show()
                        }
                        mevcutAd.isNotEmpty() && idx == 1 -> kaydet(mevcutAd)
                        else -> showDriveFolderPicker(::kaydet)
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        btnDeviceNameInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("📱 Cihaz Adı Neden Gerekli?")
                .setMessage(
                    "Her cihaza farklı bir isim vermeniz gerekir.\n\n" +
                    "Örnek:\n" +
                    "  • Kasa-1\n" +
                    "  • Kasa-2\n" +
                    "  • Depo-Tablet\n\n" +
                    "Bu isim;\n" +
                    "  ✅ Senkronizasyon loglarında hangi cihazın veri gönderdiğini gösterir\n" +
                    "  ✅ Firebase'de cihazları birbirinden ayırt eder\n" +
                    "  ✅ Birden fazla cihaz aynı Drive klasörünü kullanırken karışıklığı önler\n\n" +
                    "⚠️ İki cihaza aynı isim verilirse senkronizasyon hatalı çalışabilir."
                )
                .setPositiveButton("Anladım", null)
                .show()
        }

        btnPickBackupFolder.setOnClickListener {
            val yazılanAd = etFolderName.text.toString().trim()
                .ifEmpty { BackupPreferences.getBackupFolderName() }

            val seçenekler = mutableListOf<String>()
            seçenekler.add("📁  Yeni klasör oluştur — adını yaz")
            if (yazılanAd.isNotEmpty())
                seçenekler.add("✅  \"$yazılanAd\" adıyla telefonda klasör oluştur")
            seçenekler.add("📂  Telefondan farklı bir klasör seç")

            AlertDialog.Builder(this)
                .setTitle("📂 Telefon Klasörü")
                .setItems(seçenekler.toTypedArray()) { _, idx ->
                    when {
                        idx == 0 -> {
                            val input = android.widget.EditText(this).apply {
                                hint = "Klasör adı"
                                setText(yazılanAd)
                                setPadding(48, 32, 48, 16)
                            }
                            AlertDialog.Builder(this)
                                .setTitle("Yeni Klasör Adı")
                                .setView(input)
                                .setPositiveButton("Oluştur ve Seç") { _, _ ->
                                    val ad = input.text.toString().trim()
                                    if (ad.isNotEmpty()) createAndPickFolder(ad)
                                    else toast("Klasör adı boş olamaz")
                                }
                                .setNegativeButton("İptal", null)
                                .show()
                        }
                        yazılanAd.isNotEmpty() && idx == 1 -> createAndPickFolder(yazılanAd)
                        else -> openBackupFolderPicker()
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        btnConnectDrive.setOnClickListener {
            val signInIntent = driveAuthManager.getSignInClient().signInIntent
            signInLauncher.launch(signInIntent)
        }

        // 🔧 DÜZELTME: Drive kesildikten sonra Firebase dinleyicisi güncelleniyor.
        // setDriveConnected(false) içinde clearDriveRootFolderId() de çağrılıyor (BackupPreferences'te).
        btnDisconnectDrive.setOnClickListener {
            driveAuthManager.signOut(this) {
                BackupPreferences.setDriveConnected(false)
                BackupPreferences.setDriveEmail(null)
                BackupPreferences.setAutoBackupEnabled(false)
                AutoBackupScheduler.cancelAll(this)
                // 🔧 KRİTİK: Drive kesildikten sonra dinleyiciyi güncelle
                FirebaseRealtimeBridgeManager.onDriveAccountChanged(this)
                updateAccountInfo()
                updateAutoBackupButtonText()
                lifecycleScope.launch { refreshDevicePanels() }
                log("🔌 Drive bağlantısı kaldırıldı")
                toast("Bağlantı kaldırıldı")
            }
        }

        layoutTime1.setOnClickListener { showTimePicker(tvAutoTime1) }
        layoutTime2.setOnClickListener { showTimePicker(tvAutoTime2) }
        layoutTime3.setOnClickListener { showTimePicker(tvAutoTime3) }

        btnEnableAuto.setOnClickListener {
            val current = BackupPreferences.isAutoBackupEnabled()
            if (!current) {
                if (!BackupPreferences.isDriveConnected()) {
                    toast("Önce Drive hesabı bağlanmalı")
                    return@setOnClickListener
                }
                val t1 = normalizeTime(tvAutoTime1.text.toString(), "09:00")
                val t2 = normalizeTime(tvAutoTime2.text.toString(), "14:00")
                val t3 = normalizeTime(tvAutoTime3.text.toString(), "21:00")
                try {
                    BackupPreferences.setAutoBackupTime1(t1)
                    BackupPreferences.setAutoBackupTime2(t2)
                    BackupPreferences.setAutoBackupTime3(t3)
                    tvAutoTime1.text = t1
                    tvAutoTime2.text = t2
                    tvAutoTime3.text = t3
                    AutoBackupScheduler.scheduleAll(this, t1, t2, t3)
                    BackupPreferences.setAutoBackupEnabled(true)
                    updateAutoBackupButtonText()
                    log("🤖 Otomatik yedek AÇILDI")
                    log("🕒 1. zaman: $t1")
                    log("🕒 2. zaman: $t2")
                    log("🕒 3. zaman: $t3")
                } catch (e: Exception) {
                    log("❌ Saat ayarı hatası: ${e.message}")
                    toast("Saat formatı HH:mm olmalı")
                }
            } else {
                AutoBackupScheduler.cancelAll(this)
                BackupPreferences.setAutoBackupEnabled(false)
                updateAutoBackupButtonText()
                log("🤖 Otomatik yedek KAPATILDI")
            }
        }

        btnFullBackup.setOnClickListener { takeFullBackup() }
        btnPartialBackup.setOnClickListener { takePartialBackup() }
        btnExportCsvAll.setOnClickListener { exportAllTablesCsv() }

        btnImportCsv.setOnClickListener {
            val options = arrayOf("Uygulamanın Kendi Yedeği", "Dış Firmadan Veri Aktar (Eşleştirme)")
            AlertDialog.Builder(this)
                .setTitle("CSV İçe Aktarma")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        pickCsvFileForImport.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                    } else {
                        isExternalMappingMode = true
                        isTemplateSelectionMode = true
                        pickCsvFileForTemplate.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                    }
                }.show()
        }

        btnRestoreFromZip.setOnClickListener {
            openRestoreZipPickerFromLastFolder()
        }

        btnBack.setOnClickListener { finish() }
    }

    // ==================== YARDIMCI FONKSİYONLAR (Zaman, Klasör vb.) ====================

    private fun loadSavedTimes() {
        tvAutoTime1.text = normalizeTime(BackupPreferences.getAutoBackupTime1(), "09:00")
        tvAutoTime2.text = normalizeTime(BackupPreferences.getAutoBackupTime2(), "14:00")
        tvAutoTime3.text = normalizeTime(BackupPreferences.getAutoBackupTime3(), "21:00")
    }

    private fun normalizeTime(value: String?, defaultValue: String): String {
        val text = value?.trim().orEmpty()
        return if (text.matches(Regex("^\\d{2}:\\d{2}$"))) text else defaultValue
    }

    private fun showTimePicker(target: TextView) {
        val current = target.text.toString().trim()
        val parts = current.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                target.text = formatted
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    private fun createAndPickFolder(ad: String) {
        // Önce Belgeler klasörünü dene, olmazsa İndirmeler, o da olmazsa uygulama klasörü
        data class Aday(val ust: java.io.File, val docId: String?)
        val adaylar = listOf(
            Aday(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "primary:Documents/$ad"),
            Aday(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "primary:Download/$ad"),
            Aday(getExternalFilesDir(null) ?: filesDir, null)
        )
        var olusturulan: java.io.File? = null
        var initialDocId: String? = null
        for (aday in adaylar) {
            try {
                val klasor = java.io.File(aday.ust, ad)
                if (klasor.exists() || klasor.mkdirs()) {
                    olusturulan = klasor
                    initialDocId = aday.docId
                    break
                }
            } catch (_: Exception) { }
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            // Direkt oluşturulan klasörü aç
            if (initialDocId != null) {
                try {
                    val initialUri = android.provider.DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", initialDocId
                    )
                    putExtra("android.provider.extra.INITIAL_URI", initialUri)
                } catch (_: Exception) { }
            }
        }

        if (olusturulan != null) {
            log("📁 Telefon klasörü oluşturuldu: ${olusturulan.absolutePath}")
            toast("✅ \"$ad\" oluşturuldu — seçim ekranı açılıyor")
        } else {
            log("⚠️ Klasör oluşturulamadı: $ad")
            toast("Klasör oluşturulamadı, manuel seçin.")
        }
        pickBackupFolderLauncher.launch(intent)
    }

    private fun openBackupFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            val currentUri = BackupPreferences.getBackupFolderUri()
            if (!currentUri.isNullOrBlank()) {
                try {
                    putExtra("android.provider.extra.INITIAL_URI", Uri.parse(currentUri))
                } catch (_: Exception) { }
            }
        }
        pickBackupFolderLauncher.launch(intent)
    }

    private fun openRestoreZipPickerFromLastFolder() {
        val savedFolderUriString = BackupPreferences.getBackupFolderUri()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!savedFolderUriString.isNullOrBlank()) {
                try {
                    putExtra("android.provider.extra.INITIAL_URI", Uri.parse(savedFolderUriString))
                } catch (_: Exception) { }
            }
        }
        pickRestoreZipLauncher.launch(intent)
    }

    private fun showMappingDialog(sourceUri: Uri, myHeaders: List<String>, otherHeaders: List<String>) {
        val mappingResult = mutableMapOf<String, String>()
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
        }

        val requiredFields = listOf("adSoyad", "firmaid")
        val warningTv = TextView(this).apply {
            text = "⚠️ KIRMIZI ile gösterilen alanlar ZORUNLUDUR! (firmaid için Excel'deki firma adı sütununu seçin)"
            setTextColor(0xFFD32F2F.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(warningTv)

        val leftHeader = TextView(this).apply {
            text = "⬅️ SENİN SİSTEMİN (Şablon)"
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF2196F3.toInt())
            setPadding(0, 10, 0, 10)
        }
        rootLayout.addView(leftHeader)

        val rightHeader = TextView(this).apply {
            text = "➡️ KARŞI FİRMANIN DOSYASI ➡️"
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFF9800.toInt())
            setPadding(0, 10, 0, 20)
        }
        rootLayout.addView(rightHeader)

        val emptyOption = "——— BOŞ BIRAK ———"
        val otherHeadersWithEmpty = listOf(emptyOption) + otherHeaders
        val dbFields = myHeaders.filter { it !in listOf("id", "uuid", "updatedAt", "isDeleted") } + listOf("firmaid")

        dbFields.forEach { dbField ->
            val isRequired = dbField in requiredFields
            val displayName = when (dbField) {
                "firmaid" -> "⭐ Firma Adı (Excel'deki firma adı sütununu seçin) ⭐"
                else -> if (isRequired) "⭐ $dbField (ZORUNLU)" else dbField
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
                if (isRequired) setBackgroundColor(0x10D32F2F.toInt())
            }
            val tv = TextView(this).apply {
                text = displayName
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setTypeface(null, if (isRequired) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isRequired) 0xFFD32F2F.toInt() else 0xFF000000.toInt())
            }
            val spinner = Spinner(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
                val adapter = ArrayAdapter(this@BackupRestoreActivity, android.R.layout.simple_spinner_dropdown_item, otherHeadersWithEmpty)
                this.adapter = adapter
                val autoIdx = if (dbField == "firmaid") {
                    otherHeadersWithEmpty.indexOfFirst {
                        it != emptyOption && (it.lowercase().contains("firma") || it.lowercase().contains("company") || it.lowercase().contains("müşteri"))
                    }
                } else {
                    otherHeadersWithEmpty.indexOfFirst { it != emptyOption && it.lowercase().contains(dbField.lowercase().take(3)) }
                }
                if (autoIdx != -1) setSelection(autoIdx) else setSelection(0)

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selected = otherHeadersWithEmpty[position]
                        if (selected == emptyOption) mappingResult[dbField] = ""
                        else mappingResult[dbField] = selected
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            row.addView(tv)
            row.addView(spinner)
            rootLayout.addView(row)
        }

        val scroll = ScrollView(this).apply { addView(rootLayout) }
        AlertDialog.Builder(this)
            .setTitle("🔄 Dinamik Veri Eşleştirme")
            .setView(scroll)
            .setPositiveButton("✅ DEVAM ET (Firma Eşleştirme)") { _, _ ->
                val missingRequired = requiredFields.filter { field ->
                    mappingResult[field].isNullOrEmpty()
                }
                if (missingRequired.isNotEmpty()) {
                    toast("Zorunlu alanları doldurun: ${missingRequired.joinToString()}")
                    return@setPositiveButton
                }
                val nonEmptyMapping = mappingResult.filter { it.value.isNotEmpty() }
                if (nonEmptyMapping.isEmpty()) {
                    toast("En az bir alan eşleştirmelisiniz!")
                    return@setPositiveButton
                }
                findAndShowFirmaMatchDialog(sourceUri, nonEmptyMapping)
            }
            .setNegativeButton("❌ İPTAL", null)
            .show()
    }

    // ==================== TOPLU FİRMA EŞLEŞTİRME DİYALOĞU ====================

    private fun findAndShowFirmaMatchDialog(uri: Uri, mapping: Map<String, String>) {
        lifecycleScope.launch {
            val firmaExcelColumn = mapping["firmaid"]
            if (firmaExcelColumn.isNullOrEmpty()) {
                log("⚠️ Mapping içinde 'firmaid' alanı eşleştirilmemiş. Firmasız aktarım yapılacak.")
                executeMappingImportWithoutFirmaMatch(uri, mapping)
                return@launch
            }
            log("🔍 Excel/CSV dosyası taranıyor, '$firmaExcelColumn' sütunundan firmalar çıkarılıyor...")
            val excelFirmalar = withContext(Dispatchers.IO) {
                CsvImportManager.extractUniqueFirmalarFromFile(
                    context = this@BackupRestoreActivity,
                    fileUri = uri,
                    contentResolver = contentResolver,
                    firmaColumnName = firmaExcelColumn
                )
            }
            if (excelFirmalar.isEmpty()) {
                log("⚠️ Dosyada firma bilgisi bulunamadı! Tüm müşteriler doğrudan aktarılacak.")
                executeMappingImportWithoutFirmaMatch(uri, mapping)
                return@launch
            }
            log("📋 Dosyada ${excelFirmalar.size} benzersiz firma bulundu: ${excelFirmalar.joinToString()}")
            showBatchFirmaMatchDialog(excelFirmalar) { firmaMapping ->
                executeMappingImportWithFirmaMap(uri, mapping, firmaMapping)
            }
        }
    }

    private fun executeMappingImportWithoutFirmaMatch(uri: Uri, mapping: Map<String, String>) {
        lifecycleScope.launch {
            try {
                val uniqueFields = showUniqueFieldsDialog()
                if (uniqueFields.isEmpty()) {
                    toast("İşlem iptal edildi (tekrar kontrolü için alan seçilmedi)")
                    return@launch
                }
                log("🔍 Benzersizlik kontrolü yapılacak alanlar: ${uniqueFields.joinToString()}")

                val defaultValues = mapOf("adSoyad" to "İsimsiz Müşteri")
                val reversedMapping = mapping.entries.associate { it.value to it.key }

                val results = withContext(Dispatchers.IO) {
                    CsvImportManager.importWithoutFirma(
                        context = this@BackupRestoreActivity,
                        fileUri = uri,
                        tableName = "musteri",
                        columnMapping = reversedMapping,
                        defaultValues = defaultValues
                    )
                }

                val successCount = results.count { it.status == ImportRowResult.Status.OK }
                log("✅ Aktarılan (yeni): $successCount satır")
                toast("Aktarım tamam: $successCount yeni kayıt eklendi")
                checkForNewChanges()
            } catch (e: Exception) {
                log("❌ Aktarım hatası: ${e.message}")
                toast("Hata: ${e.message}")
            }
        }
    }

    private fun showBatchFirmaMatchDialog(
        excelFirmalar: List<String>,
        onComplete: (Map<String, Long?>) -> Unit
    ) {
        val existingFirmalar = mutableListOf<Pair<Long, String>>()
        var db: SQLiteDatabase? = null
        try {
            db = openDbReadOnly()
            val cursor = db.rawQuery("SELECT id, firmaAdi FROM firma ORDER BY firmaAdi", null)
            Log.d("FIRMA", "Sorgu çalıştı, satır sayısı: ${cursor.count}")
            while (cursor.moveToNext()) {
                existingFirmalar.add(Pair(cursor.getLong(0), cursor.getString(1)))
            }
            cursor.close()
            log("📋 Veritabanında ${existingFirmalar.size} firma bulundu: ${existingFirmalar.map { it.second }}")
        } catch (e: Exception) {
            log("❌ Firmalar okunamadı: ${e.message}")
            toast("Veritabanı hatası: ${e.message}")
            return
        } finally {
            // ❌ SAKIN db.close() YAPMA – bağlantı havuzda kalmalı
        }

        if (existingFirmalar.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Uyarı")
                .setMessage("Sistemde hiç firma kaydı bulunamadı.\nExcel'deki tüm firmalar yeni firma olarak eklenecek. Devam etmek istiyor musunuz?")
                .setPositiveButton("Evet, tümünü ekle") { _, _ ->
                    val newFirmaNames = excelFirmalar.associateWith { it }
                    showProgressDialog("Yeni firmalar oluşturuluyor...")
                    createNewFirmasAndGetMapping(newFirmaNames) { newFirmaIds ->
                        hideProgressDialog()
                        val firmaMapping = newFirmaIds.mapValues { it.value as Long? }
                        onComplete(firmaMapping)
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
            return
        }

        val firmaList = existingFirmalar.map { it.second }
        val firmaIdList = existingFirmalar.map { it.first }

        val emptyOption = "——— SEÇİM YAPIN ———"
        val newFirmaOption = "➕ YENİ FİRMA OLUŞTUR"
        val spinnerOptions = listOf(emptyOption) + firmaList + listOf(newFirmaOption)

        val selectedFirmaNames = mutableMapOf<String, String>()
        val newFirmaNames = mutableMapOf<String, String>()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
        }

        val titleTv = TextView(this).apply {
            text = "📋 Excel'de Bulunan Firmalar (${excelFirmalar.size})"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        rootLayout.addView(titleTv)

        val infoTv = TextView(this).apply {
            text = "Her firmayı sisteminizdeki bir firmayla eşleştirin veya yeni firma oluşturun.\n(Eşleşmeyen firma bırakılamaz!)"
            textSize = 12f
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(infoTv)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            setBackgroundColor(0xFFEEEEEE.toInt())
        }
        val headerExcel = TextView(this).apply {
            text = "Excel'deki Firma"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setTypeface(null, Typeface.BOLD)
            setPadding(10, 10, 10, 10)
        }
        val headerSystem = TextView(this).apply {
            text = "Sistemdeki Firma (Eşleşme) / Yeni Firma Adı"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
            setTypeface(null, Typeface.BOLD)
            setPadding(10, 10, 10, 10)
        }
        headerRow.addView(headerExcel)
        headerRow.addView(headerSystem)
        rootLayout.addView(headerRow)

        val spinnerMap = mutableMapOf<String, Spinner>()
        val newFirmaLayoutMap = mutableMapOf<String, LinearLayout>()
        val etNewFirmaMap = mutableMapOf<String, EditText>()

        excelFirmalar.forEachIndexed { index, excelFirma ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 5, 0, 5)
                if (index % 2 == 0) setBackgroundColor(0x0F000000.toInt())
            }

            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            val tvExcelFirma = TextView(this).apply {
                text = excelFirma
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setPadding(10, 15, 10, 15)
                textSize = 14f
            }
            row.addView(tvExcelFirma)

            val spinner = Spinner(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
                val adapter = ArrayAdapter(this@BackupRestoreActivity,
                    android.R.layout.simple_spinner_dropdown_item, spinnerOptions)
                this.adapter = adapter

                val autoMatchIndex = spinnerOptions.indexOfFirst {
                    it != emptyOption && it != newFirmaOption && it.equals(excelFirma, ignoreCase = true)
                }
                if (autoMatchIndex != -1) {
                    setSelection(autoMatchIndex)
                    selectedFirmaNames[excelFirma] = spinnerOptions[autoMatchIndex]
                } else {
                    setSelection(0)
                }
            }

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = spinnerOptions[position]
                    when {
                        selected == emptyOption -> {
                            selectedFirmaNames.remove(excelFirma)
                            newFirmaLayoutMap[excelFirma]?.visibility = View.GONE
                        }
                        selected == newFirmaOption -> {
                            selectedFirmaNames.remove(excelFirma)
                            newFirmaLayoutMap[excelFirma]?.visibility = View.VISIBLE
                        }
                        else -> {
                            selectedFirmaNames[excelFirma] = selected
                            newFirmaLayoutMap[excelFirma]?.visibility = View.GONE
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            row.addView(spinner)
            spinnerMap[excelFirma] = spinner
            rowLayout.addView(row)

            val newFirmaLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                visibility = View.GONE
                setPadding(10, 10, 10, 10)
            }
            val etNewFirma = EditText(this).apply {
                hint = "Yeni firma adı girin"
                layoutParams = LinearLayout.LayoutParams(0, -2, 3f)
                setText(excelFirma)
            }
            newFirmaLayout.addView(etNewFirma)
            etNewFirmaMap[excelFirma] = etNewFirma

            val btnUseOriginal = Button(this).apply {
                text = "Orijinal"
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { etNewFirma.setText(excelFirma) }
            }
            newFirmaLayout.addView(btnUseOriginal)

            rowLayout.addView(newFirmaLayout)
            newFirmaLayoutMap[excelFirma] = newFirmaLayout
            rootLayout.addView(rowLayout)

            if (index < excelFirmalar.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 1)
                    setBackgroundColor(0x20000000.toInt())
                }
                rootLayout.addView(divider)
            }
        }

        val btnMarkAllNew = Button(this).apply {
            text = "🔄 EŞLEŞMEYEN TÜM FİRMALARI YENİ FİRMA OLARAK İŞARETLE"
            setPadding(20, 15, 20, 15)
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                excelFirmalar.forEach { excelFirma ->
                    val spinner = spinnerMap[excelFirma]
                    val newFirmaIndex = (spinner?.adapter as? ArrayAdapter<String>)?.let {
                        (0 until it.count).firstOrNull { pos -> it.getItem(pos) == newFirmaOption }
                    } ?: -1
                    if (newFirmaIndex != -1) {
                        spinner?.setSelection(newFirmaIndex)
                    }
                }
            }
        }
        rootLayout.addView(btnMarkAllNew)

        val scrollView = ScrollView(this).apply { addView(rootLayout) }

        AlertDialog.Builder(this)
            .setTitle("🔄 Toplu Firma Eşleştirme")
            .setView(scrollView)
            .setPositiveButton("✅ AKTARIMI BAŞLAT") { _, _ ->
                val unselectedFirmalar = mutableListOf<String>()
                val firmaMapping = mutableMapOf<String, Long?>()

                excelFirmalar.forEach { excelFirma ->
                    val selectedName = selectedFirmaNames[excelFirma]
                    if (selectedName != null) {
                        val firmaIndex = firmaList.indexOf(selectedName)
                        if (firmaIndex != -1) {
                            firmaMapping[excelFirma] = firmaIdList[firmaIndex]
                        } else {
                            unselectedFirmalar.add(excelFirma)
                        }
                    } else {
                        val newFirmaLayout = newFirmaLayoutMap[excelFirma]
                        if (newFirmaLayout?.visibility == View.VISIBLE) {
                            val newFirmaAdi = etNewFirmaMap[excelFirma]?.text?.toString()?.trim()
                            if (!newFirmaAdi.isNullOrBlank()) {
                                firmaMapping[excelFirma] = null
                                newFirmaNames[excelFirma] = newFirmaAdi
                            } else {
                                unselectedFirmalar.add(excelFirma)
                            }
                        } else {
                            unselectedFirmalar.add(excelFirma)
                        }
                    }
                }

                if (unselectedFirmalar.isNotEmpty()) {
                    toast("Lütfen tüm firmaları eşleştirin veya yeni firma oluşturun:\n${unselectedFirmalar.take(5).joinToString()}")
                    return@setPositiveButton
                }

                if (newFirmaNames.isNotEmpty()) {
                    showProgressDialog("Yeni firmalar oluşturuluyor...")
                    createNewFirmasAndGetMapping(newFirmaNames) { newFirmaIds ->
                        hideProgressDialog()
                        newFirmaIds.forEach { (excelFirma, firmaId) ->
                            firmaMapping[excelFirma] = firmaId
                        }
                        onComplete(firmaMapping)
                    }
                } else {
                    onComplete(firmaMapping)
                }
            }
            .setNegativeButton("❌ İPTAL", null)
            .show()
    }

    private fun executeMappingImportWithFirmaMap(
        uri: Uri,
        mapping: Map<String, String>,
        firmaMapping: Map<String, Long?>
    ) {
        lifecycleScope.launch {
            try {
                val uniqueFields = showUniqueFieldsDialog()
                if (uniqueFields.isEmpty()) {
                    toast("İşlem iptal edildi (tekrar kontrolü için alan seçilmedi)")
                    return@launch
                }
                log("🔍 Benzersizlik kontrolü yapılacak alanlar: ${uniqueFields.joinToString()}")

                val defaultValues = mapOf("adSoyad" to "İsimsiz Müşteri")
                val reversedMapping = mapping.entries.associate { it.value to it.key }

                val results = withContext(Dispatchers.IO) {
                    CsvImportManager.importWithManualColumnMapping(
                        context = this@BackupRestoreActivity,
                        fileUri = uri,
                        tableName = "musteri",
                        columnMapping = reversedMapping,
                        firmaIdMapping = firmaMapping,
                        uniqueFields = uniqueFields,
                        defaultValues = defaultValues
                    )
                }

                val successCount = results.count { it.status == ImportRowResult.Status.OK }
                val duplicateCount = results.firstOrNull()?.message?.let {
                    if (it.contains("Tekrar nedeniyle atlanan:")) {
                        it.substringAfter("Tekrar nedeniyle atlanan:").trim().toIntOrNull() ?: 0
                    } else 0
                } ?: 0

                log("✅ Aktarılan (yeni): $successCount satır")
                log("⚠️ Tekrar nedeniyle atlanan: $duplicateCount satır")
                toast("Aktarım tamam: $successCount yeni kayıt eklendi, $duplicateCount tekrar engellendi")
                checkForNewChanges()
            } catch (e: Exception) {
                log("❌ Aktarım hatası: ${e.message}")
                toast("Hata: ${e.message}")
            }
        }
    }

    // ==================== DİNAMİK ALAN SEÇİMİ İÇİN YARDIMCI FONKSİYONLAR ====================

    private fun createNewFirmasAndGetMapping(
        newFirmalar: Map<String, String>,
        onComplete: (Map<String, Long>) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mutableMapOf<String, Long>()
            val db = openDbReadWrite()
            try {
                db.beginTransaction()
                newFirmalar.forEach { (excelFirma, yeniFirmaAdi) ->
                    val values = ContentValues().apply {
                        put("firmaAdi", yeniFirmaAdi)
                        put("uuid", UUID.randomUUID().toString())
                        put("updatedAt", System.currentTimeMillis())
                    }
                    val firmaId = db.insert("firma", null, values)
                    result[excelFirma] = firmaId
                    log("✅ Yeni firma oluşturuldu: $yeniFirmaAdi (ID:$firmaId)")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                // ASLA db.close() yapma - bağlantı havuzda kalır
            }
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    private fun getCustomerTableColumns(): List<String> {
        return try {
            Log.d("CustomerColumns", "DB dosyası kontrol ediliyor...")
            val db = synchronized(MetaTakipDbLock.lock) {
                MetaTakipDb.getInstance(this).readableDatabase
            }
            val cursor = db.rawQuery("PRAGMA table_info(musteri)", null)
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name !in listOf(
                        "id", "uuid", "updatedAt", "isDeleted", "deletedAt",
                        "deleteReason", "deletedBy", "latitude", "longitude",
                        "locationTimestamp", "locationAddress"
                    )) {
                    columns.add(name)
                }
            }
            cursor.close()
            // ASLA db.close() yapma – bağlantı havuzda kalır
            Log.d("CustomerColumns", "✅ Sütunlar: $columns")
            columns
        } catch (e: Exception) {
            Log.e("CustomerColumns", "❌ Hata: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun showUniqueFieldsDialog(): List<String> = suspendCoroutine { continuation ->
        var columns = getCustomerTableColumns()
        if (columns.isEmpty()) {
            Log.w("UniqueFields", "Sütunlar boş, varsayılan listeye düşülüyor")
            columns = listOf("adSoyad", "ceptel", "adres")
        }

        val dialog = Dialog(this)
        dialog.setTitle("Mükerrer Kayıt Kontrolü")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val messageTv = TextView(this).apply {
            text = "Hangi alanlara göre tekrar kontrolü yapılsın?\n(En az 1 alan seçin)"
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(messageTv)

        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, columns)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        val btnOk = Button(this).apply {
            text = "Tamam"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(10, 0, 10, 0) }
            setOnClickListener {
                val fields = mutableListOf<String>()
                for (i in 0 until columns.size) {
                    if (listView.isItemChecked(i)) {
                        fields.add(columns[i])
                    }
                }
                if (fields.isEmpty()) {
                    Toast.makeText(this@BackupRestoreActivity, "Lütfen en az bir alan seçin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                continuation.resume(fields)
            }
        }
        buttonLayout.addView(btnOk)

        val btnCancel = Button(this).apply {
            text = "İptal"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(10, 0, 10, 0) }
            setOnClickListener {
                dialog.dismiss()
                continuation.resume(emptyList())
            }
        }
        buttonLayout.addView(btnCancel)

        layout.addView(buttonLayout)

        dialog.setContentView(layout)
        dialog.setCancelable(false)
        dialog.show()

        btnOk.isEnabled = false
        listView.setOnItemClickListener { _, _, _, _ ->
            val anyChecked = (0 until columns.size).any { listView.isItemChecked(it) }
            btnOk.isEnabled = anyChecked
        }
    }

    // ==================== YEDEKLEME FONKSİYONLARI ====================

    private fun startPeriodicCheck() {
        checkRunnable = object : Runnable {
            override fun run() {
                lifecycleScope.launch {
                    try {
                        val newChanges = withContext(Dispatchers.IO) {
                            ChangeLogManager.checkForNewChanges(this@BackupRestoreActivity)
                        }
                        if (newChanges.isNotEmpty()) updateRecentChanges()
                        refreshDevicePanels()
                    } catch (_: Exception) { }
                }
                checkHandler.postDelayed(this, checkInterval)
            }
        }
        checkHandler.post(checkRunnable!!)
    }

    private fun stopPeriodicCheck() {
        checkRunnable?.let { checkHandler.removeCallbacks(it) }
        checkRunnable = null
    }

    private fun checkForNewChanges() {
        lifecycleScope.launch {
            val newChanges = withContext(Dispatchers.IO) {
                ChangeLogManager.checkForNewChanges(this@BackupRestoreActivity)
            }
            if (newChanges.isNotEmpty()) withContext(Dispatchers.Main) { updateRecentChanges() }
        }
    }

    private fun takeFullBackup() {
        lifecycleScope.launch {
            runWithDriveLock("Tam yedek", BackupFolderType.FULL) {
                try {
                    val startTime = System.currentTimeMillis()
                    val folderName = BackupPreferences.getBackupFolderName()
                    val todayStr = TimeUtils.todayFolder()
                    val downloadsPath = "/storage/emulated/0/Download"
                    val fileName = "tam_${TimeUtils.timestamp()}.zip"
                    log("════════════════════════════════════════════════════")
                    log("📦 Tam yedek başlatıldı")
                    log("📄 Dosya adı: $fileName")
                    log("📱 Telefon: $downloadsPath/$folderName/full/$todayStr/")
                    if (BackupPreferences.isDriveConnected()) {
                        log("☁️ Drive: $folderName/full/$todayStr/")
                    }
                    val savedFile = withContext(Dispatchers.IO) {
                        val tempZip = FullBackupManager(this@BackupRestoreActivity).createBackupZip()
                        LocalBackupManager.saveToTodayFolder(
                            context = this@BackupRestoreActivity,
                            sourceFile = tempZip,
                            fileName = fileName,
                            type = BackupFolderType.FULL
                        )
                    }
                    val fileSizeKB = savedFile.length() / 1024
                    log("✅ Telefona kaydedildi: ${savedFile.absolutePath} ($fileSizeKB KB)")
                    if (BackupPreferences.isDriveConnected()) {
                        val uploaded = DriveUploadHelper.uploadToDrive(
                            context = this@BackupRestoreActivity,
                            localFile = savedFile,
                            type = BackupFolderType.FULL
                        )
                        if (uploaded) {
                            DriveHistoryManager.recordBackup(this@BackupRestoreActivity, "full", savedFile.name)
                            log("✅ Drive'a yüklendi: $folderName/full/$todayStr/${savedFile.name}")
                        } else log("❌ Drive yükleme başarısız")
                    }
                    LocalBackupManager.deleteOldLocalBackups(this@BackupRestoreActivity)
                    BackupPreferences.setLastBackupTime(System.currentTimeMillis())
                    val totalTime = (System.currentTimeMillis() - startTime) / 1000
                    log("✅ Tam yedek tamamlandı ($totalTime sn)")
                    log("════════════════════════════════════════════════════")
                    toast("✅ Tam yedek alındı: ${savedFile.name}")
                    loadRecentChanges()
                    refreshDevicePanels()
                } catch (e: Exception) {
                    log("❌ Hata: ${e.message}")
                    toast("Hata: ${e.message}")
                }
            }
        }
    }

    private fun takePartialBackup() {
        showTablePickerDialog(
            title = "Kısmi Yedek - Tabloları Seç",
            defaultSelected = defaultPartialSelection()
        ) { selectedTables ->
            lifecycleScope.launch {
                runWithDriveLock("Kısmi yedek", BackupFolderType.PARTIAL) {
                    try {
                        val startTime = System.currentTimeMillis()
                        val folderName = BackupPreferences.getBackupFolderName()
                        val todayStr = TimeUtils.todayFolder()
                        val downloadsPath = "/storage/emulated/0/Download"
                        val fileName = "kismi_${TimeUtils.timestamp()}.zip"
                        log("════════════════════════════════════════════════════")
                        log("🧩 Kısmi yedek başlatıldı")
                        log("📄 Dosya adı: $fileName")
                        log("📋 Seçilen tablolar: ${selectedTables.size}")
                        log("📱 Telefon: $downloadsPath/$folderName/partial/$todayStr/")
                        val savedFile = withContext(Dispatchers.IO) {
                            val tempZip = PartialBackupManager(this@BackupRestoreActivity).createPartialZip(selectedTables)
                            LocalBackupManager.saveToTodayFolder(
                                context = this@BackupRestoreActivity,
                                sourceFile = tempZip,
                                fileName = fileName,
                                type = BackupFolderType.PARTIAL
                            )
                        }
                        val fileSizeKB = savedFile.length() / 1024
                        log("✅ Telefona kaydedildi: ${savedFile.absolutePath} ($fileSizeKB KB)")
                        if (BackupPreferences.isDriveConnected()) {
                            val uploaded = DriveUploadHelper.uploadToDrive(
                                context = this@BackupRestoreActivity,
                                localFile = savedFile,
                                type = BackupFolderType.PARTIAL
                            )
                            if (uploaded) {
                                DriveHistoryManager.recordBackup(this@BackupRestoreActivity, "partial", savedFile.name)
                                log("✅ Drive'a yüklendi: $folderName/partial/$todayStr/${savedFile.name}")
                            } else log("❌ Drive yükleme başarısız")
                        }
                        LocalBackupManager.deleteOldLocalBackups(this@BackupRestoreActivity)
                        BackupPreferences.setLastBackupTime(System.currentTimeMillis())
                        val totalTime = (System.currentTimeMillis() - startTime) / 1000
                        log("✅ Kısmi yedek tamamlandı ($totalTime sn)")
                        log("════════════════════════════════════════════════════")
                        toast("✅ Kısmi yedek alındı: ${savedFile.name}")
                        loadRecentChanges()
                        refreshDevicePanels()
                    } catch (e: Exception) {
                        log("❌ Hata: ${e.message}")
                        toast("Hata: ${e.message}")
                    }
                }
            }
        }
    }

    private fun exportAllTablesCsv() {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val folderName = BackupPreferences.getBackupFolderName()
                val todayStr = TimeUtils.todayFolder()
                val downloadsPath = "/storage/emulated/0/Download"
                val exportFolderName = "csv_export_${TimeUtils.timestamp()}"
                log("════════════════════════════════════════════════════")
                log("📄 CSV export başlatıldı")
                log("📱 Telefon: $downloadsPath/$folderName/csv/$todayStr/$exportFolderName/")
                val exportFolder = withContext(Dispatchers.IO) {
                    val folder = LocalBackupManager.createTodaySubFolder(
                        context = this@BackupRestoreActivity,
                        type = BackupFolderType.CSV,
                        subFolderName = exportFolderName
                    )
                    val db = openDbReadOnly()
                    TableCatalog.ALL_TABLES.forEach { table ->
                        val csvFile = File(folder, "$table.csv")
                        CsvExporter.exportTableToFile(db, table, csvFile)
                    }
                    folder
                }
                log("✅ CSV export tamamlandı: ${exportFolder.absolutePath}")
                if (BackupPreferences.isDriveConnected()) {
                    withContext(Dispatchers.IO) {
                        val zipFile = File(cacheDir, "csv_export_${TimeUtils.timestamp()}.zip")
                        zipFolder(exportFolder, zipFile)
                        val uploaded = DriveUploadHelper.uploadToDrive(
                            context = this@BackupRestoreActivity,
                            localFile = zipFile,
                            type = BackupFolderType.CSV
                        )
                        if (uploaded) DriveHistoryManager.recordBackup(this@BackupRestoreActivity, "csv", zipFile.name)
                        zipFile.delete()
                    }
                    log("☁️ Drive'a yedeklendi")
                    refreshDevicePanels()
                }
                val totalTime = (System.currentTimeMillis() - startTime) / 1000
                log("⏱️ Süre: $totalTime sn")
                log("════════════════════════════════════════════════════")
                toast("✅ CSV Export tamam")
                loadRecentChanges()
            } catch (e: Exception) {
                log("❌ CSV export hatası: ${e.message}")
                toast("CSV export hatası")
            }
        }
    }

    private fun zipFolder(folder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            folder.walk().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(folder).path
                    zip.putNextEntry(ZipEntry(relativePath))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun restoreFullBackup(uri: Uri, fileName: String = "") {
        lifecycleScope.launch {
            try {
                log("📦 Tam restore başlatıldı")
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FullBackupManager(this@BackupRestoreActivity).restoreDbFromZip(input)
                    } ?: throw IllegalStateException("Dosya açılamadı")
                }
                if (fileName.isNotBlank()) BackupPreferences.setLastRestoreFile(fileName)
                (BackupPreferences.getActualDriveEmail() ?: BackupPreferences.getDriveEmail())
                    ?.let { BackupPreferences.setLastRestoreEmail(it) }
                BackupPreferences.setLastRestoreFolder(BackupPreferences.getBackupFolderName())
                log("✅ Restore tamamlandı, uygulama yeniden başlatılıyor")
                toast("Restore tamam ✅")
                AppRestartUtil.restartApp(this@BackupRestoreActivity)
            } catch (e: Exception) {
                log("❌ Restore hatası: ${e.message}")
                toast("Restore hatası: ${e.message}")
            }
        }
    }

    private fun restorePartialBackup(uri: Uri, selectedTables: List<String>, fileName: String = "") {
        lifecycleScope.launch {
            try {
                log("🧩 Kısmi geri yükleme başlatıldı...")
                val result = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        PartialBackupManager(this@BackupRestoreActivity).restoreFromPartialZip(
                            zipInput = input,
                            selectedTables = selectedTables
                        )
                    } ?: false
                }
                if (result) {
                    if (fileName.isNotBlank()) BackupPreferences.setLastRestoreFile(fileName)
                    (BackupPreferences.getActualDriveEmail() ?: BackupPreferences.getDriveEmail())
                        ?.let { BackupPreferences.setLastRestoreEmail(it) }
                    BackupPreferences.setLastRestoreFolder(BackupPreferences.getBackupFolderName())
                    log("✅ Kısmi geri yükleme başarıyla tamamlandı")
                    toast("Geri yükleme tamamlandı ✅")
                    sendBroadcast(Intent("com.example.metatakip.REFRESH_UI"))
                } else {
                    log("⚠️ Geri yükleme başarısız oldu (Dosya boş veya uyumsuz olabilir)")
                    toast("İşlem başarısız ❌")
                }
            } catch (e: Exception) {
                log("❌ Kısmi geri yükleme hatası: ${e.message}")
                toast("Hata oluştu: ${e.message}")
            }
        }
    }

    private fun showTablePickerDialogForRestore(uri: Uri, fileName: String = "") {
        val tables = TableCatalog.ALL_TABLES.toTypedArray()
        val selected = BooleanArray(tables.size) { idx -> tables[idx] != "user" }
        AlertDialog.Builder(this)
            .setTitle("🧩 Kısmi Restore - Tabloları Seç")
            .setMultiChoiceItems(tables, selected) { _, which, isChecked -> selected[which] = isChecked }
            .setPositiveButton("Tamam") { _, _ ->
                val picked = tables.indices.filter { selected[it] }.map { tables[it] }
                if (picked.isEmpty()) {
                    toast("En az 1 tablo seçmelisin")
                    return@setPositiveButton
                }
                AlertDialog.Builder(this)
                    .setTitle("Kısmi Geri Yükleme")
                    .setMessage("Seçili tablolar temizlenip yedekten geri yüklenecek. Devam edilsin mi?")
                    .setPositiveButton("Evet") { _, _ -> restorePartialBackup(uri, picked, fileName) }
                    .setNegativeButton("Vazgeç", null)
                    .show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun loadRecentChanges() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ChangeLogManager.checkForNewChanges(this@BackupRestoreActivity) }
            updateRecentChanges()
        }
    }

    private fun updateRecentChanges() {
        val changes = ChangeLogManager.recentChanges.value
        if (changes.isEmpty()) {
            rvRecentChanges.visibility = View.GONE
            tvEmptyChanges.visibility = View.VISIBLE
        } else {
            rvRecentChanges.visibility = View.VISIBLE
            tvEmptyChanges.visibility = View.GONE
            changeLogAdapter.submitList(changes)
        }
    }

    private fun updateAccountInfo() {
        val email = BackupPreferences.getDriveEmail()
        val isConnected = BackupPreferences.isDriveConnected()

        if (isConnected && !email.isNullOrBlank()) {
            tvDriveStatusBadge.text = "✅  BAĞLI — $email"
            tvDriveStatusBadge.setBackgroundColor(0xFF1B5E20.toInt())
        } else {
            tvDriveStatusBadge.text = "❌  BAĞLI DEĞİL"
            tvDriveStatusBadge.setBackgroundColor(0xFFB71C1C.toInt())
        }

        tvAccount.text = if (email.isNullOrBlank()) "Hesap bağlı değil" else email

        btnConnectDrive.visibility = if (isConnected) View.GONE else View.VISIBLE
        btnDisconnectDrive.visibility = if (isConnected) View.VISIBLE else View.GONE
    }

    // Her klasör: first=Drive ID, second=klasör adı
    private data class DriveFolderItem(val id: String, val name: String, val copyCount: Int)

    private fun showDriveFolderPicker(onSeçildi: (String) -> Unit) {
        if (!BackupPreferences.isDriveConnected()) {
            toast("Önce Drive'ı bağlayın")
            return
        }

        val yükleniyor = AlertDialog.Builder(this)
            .setMessage("☁️ Drive klasörleri yükleniyor...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            // id + name çifti olarak al, sonra ada göre tekilleştir
            val klasörler: List<DriveFolderItem>? = withContext(Dispatchers.IO) {
                try {
                    val driveService = DriveBackupManager.getDriveService(this@BackupRestoreActivity)
                        ?: return@withContext null

                    val tümü = driveService.files()
                        .list()
                        .setQ("mimeType = 'application/vnd.google-apps.folder' and trashed = false and 'root' in parents")
                        .setSpaces("drive")
                        .setFields("files(id,name)")
                        .setOrderBy("name")
                        .execute()
                        .files
                        ?: emptyList()

                    // Ada göre grupla: her ad için ilk ID'yi tut, kaç kopya olduğunu say
                    tümü.groupBy { it.name }
                        .map { (ad, grup) ->
                            DriveFolderItem(
                                id = grup.first().id,
                                name = ad,
                                copyCount = grup.size
                            )
                        }
                        .sortedBy { it.name }
                } catch (e: Exception) {
                    log("⚠️ Drive klasör listesi alınamadı: ${e.message}")
                    null
                }
            }

            yükleniyor.dismiss()

            when {
                klasörler == null -> toast("⚠️ Drive'a bağlanılamadı")
                klasörler.isEmpty() -> toast("Drive'da henüz klasör yok — yeni isim yazın")
                else -> {
                    val seçenekler = mutableListOf<String>()
                    seçenekler.add("➕  Yeni klasör adı yaz")
                    val dupUyarısı = klasörler.any { it.copyCount > 1 }
                    klasörler.forEach { f ->
                        val etiket = if (f.copyCount > 1)
                            "📁  ${f.name}  ⚠️ (${f.copyCount} kopya)"
                        else
                            "📁  ${f.name}"
                        seçenekler.add(etiket)
                    }

                    val başlık = if (dupUyarısı)
                        "📂 Drive Klasörleri  ⚠️ Mükerrer var"
                    else
                        "📂 Drive Klasörleri"

                    AlertDialog.Builder(this@BackupRestoreActivity)
                        .setTitle(başlık)
                        .setItems(seçenekler.toTypedArray()) { _, idx ->
                            if (idx == 0) {
                                val input = android.widget.EditText(this@BackupRestoreActivity).apply {
                                    hint = "Klasör adı"
                                    setPadding(48, 32, 48, 16)
                                }
                                AlertDialog.Builder(this@BackupRestoreActivity)
                                    .setTitle("Yeni Klasör Adı")
                                    .setView(input)
                                    .setPositiveButton("Kaydet") { _, _ ->
                                        val ad = input.text.toString().trim()
                                        if (ad.isNotBlank()) onSeçildi(ad)
                                        else toast("Klasör adı boş olamaz")
                                    }
                                    .setNegativeButton("İptal", null)
                                    .show()
                            } else {
                                val seçilen = klasörler[idx - 1]
                                // Seçilen klasörün Drive ID'sini kaydet — bir daha yanlış klasöre gitmesin
                                BackupPreferences.setDriveRootFolderId(seçilen.id)
                                if (seçilen.copyCount > 1) {
                                    AlertDialog.Builder(this@BackupRestoreActivity)
                                        .setTitle("⚠️ Mükerrer Klasör")
                                        .setMessage(
                                            "\"${seçilen.name}\" adında Drive'da ${seçilen.copyCount} adet klasör var.\n\n" +
                                            "Uygulama bunlardan birini seçti ve ID'sini kaydetti — artık hep aynısını kullanacak.\n\n" +
                                            "Diğer gereksiz kopyaları temizlemek için:\n" +
                                            "• drive.google.com adresine girin\n" +
                                            "• \"${seçilen.name}\" klasörlerini bulun\n" +
                                            "• Fazlalıkları çöpe taşıyın"
                                        )
                                        .setPositiveButton("Tamam") { _, _ -> onSeçildi(seçilen.name) }
                                        .setNegativeButton("İptal", null)
                                        .show()
                                } else {
                                    onSeçildi(seçilen.name)
                                }
                            }
                        }
                        .setNegativeButton("İptal", null)
                        .show()
                }
            }
        }
    }

    private fun updateFolderNameDisplay() {
        val folderName = BackupPreferences.getBackupFolderName()
        val folderUri = BackupPreferences.getBackupFolderUri()
        tvCurrentFolder.text = buildString {
            append("Klasör adı: ")
            append(folderName)
            append("\n")
            append("Seçili klasör: ")
            append(if (folderUri.isNullOrBlank()) "Seçilmedi" else "Ayarlı")
        }
        etFolderName.setText(folderName)
        if (folderName.isBlank()) {
            tvDriveFolderRowSubtitle.text = "Tüm cihazlarda aynı olmalı"
            tvDriveFolderRowSubtitle.setTextColor(0xFF90A4AE.toInt())
        } else {
            tvDriveFolderRowSubtitle.text = "📁 $folderName"
            tvDriveFolderRowSubtitle.setTextColor(0xFF1976D2.toInt())
        }
    }

    private fun updatePhoneFolderPathDisplay() {
        val uri = BackupPreferences.getBackupFolderUri()
        if (uri.isNullOrBlank()) {
            tvPhoneFolderPath.text = "Henüz seçilmedi"
            tvPhoneFolderPath.setTextColor(0xFFBDBDBD.toInt())
        } else {
            try {
                val docFile = DocumentFile.fromTreeUri(this, android.net.Uri.parse(uri))
                val ad = docFile?.name ?: uri
                tvPhoneFolderPath.text = "📂 $ad"
                tvPhoneFolderPath.setTextColor(0xFFF9A825.toInt())
            } catch (_: Exception) {
                tvPhoneFolderPath.text = "📂 Ayarlı"
                tvPhoneFolderPath.setTextColor(0xFFF9A825.toInt())
            }
        }
    }

    private fun showStep(n: Int) {
        activeStep = n

        layoutStep1.visibility = if (n == 1) android.view.View.VISIBLE else android.view.View.GONE
        layoutStep2.visibility = if (n == 2) android.view.View.VISIBLE else android.view.View.GONE
        layoutStep3.visibility = if (n == 3) android.view.View.VISIBLE else android.view.View.GONE
        layoutStep4.visibility = if (n == 4) android.view.View.VISIBLE else android.view.View.GONE
        layoutStep5.visibility = if (n == 5) android.view.View.VISIBLE else android.view.View.GONE

        if (n == 5) updateSummary()

        fun applyCircle(
            circle: androidx.cardview.widget.CardView,
            label: TextView,
            state: Int,
            num: String
        ) {
            when (state) {
                0 -> {
                    circle.setCardBackgroundColor(0xFF2E7D32.toInt())
                    label.text = "✓"
                    label.setTextColor(0xFFFFFFFF.toInt())
                }
                1 -> {
                    circle.setCardBackgroundColor(0xFF1976D2.toInt())
                    label.text = num
                    label.setTextColor(0xFFFFFFFF.toInt())
                }
                2 -> {
                    circle.setCardBackgroundColor(0xFFE0E0E0.toInt())
                    label.text = num
                    label.setTextColor(0xFF9E9E9E.toInt())
                }
            }
        }

        when (n) {
            1 -> {
                applyCircle(stepCircle1, tvStep1Num, 1, "1")
                applyCircle(stepCircle2, tvStep2Num, 2, "2")
                applyCircle(stepCircle3, tvStep3Num, 2, "3")
                applyCircle(stepCircle4, tvStep4Num, 2, "4")
                applyCircle(stepCircle5, tvStep5Num, 2, "5")
                stepLine1.setBackgroundColor(0xFFE0E0E0.toInt())
                stepLine2.setBackgroundColor(0xFFE0E0E0.toInt())
                stepLine3.setBackgroundColor(0xFFE0E0E0.toInt())
                stepLine4.setBackgroundColor(0xFFE0E0E0.toInt())
            }
            2 -> {
                applyCircle(stepCircle1, tvStep1Num, 0, "1")
                applyCircle(stepCircle2, tvStep2Num, 1, "2")
                applyCircle(stepCircle3, tvStep3Num, 2, "3")
                applyCircle(stepCircle4, tvStep4Num, 2, "4")
                applyCircle(stepCircle5, tvStep5Num, 2, "5")
                stepLine1.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine2.setBackgroundColor(0xFFE0E0E0.toInt())
                stepLine3.setBackgroundColor(0xFFE0E0E0.toInt())
                stepLine4.setBackgroundColor(0xFFE0E0E0.toInt())
            }
            3 -> {
                applyCircle(stepCircle1, tvStep1Num, 0, "1")
                applyCircle(stepCircle2, tvStep2Num, 0, "2")
                applyCircle(stepCircle3, tvStep3Num, 1, "3")
                applyCircle(stepCircle4, tvStep4Num, 2, "4")
                applyCircle(stepCircle5, tvStep5Num, 2, "5")
                stepLine1.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine2.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine3.setBackgroundColor(0xFFE0E0E0.toInt())
                stepLine4.setBackgroundColor(0xFFE0E0E0.toInt())
            }
            4 -> {
                applyCircle(stepCircle1, tvStep1Num, 0, "1")
                applyCircle(stepCircle2, tvStep2Num, 0, "2")
                applyCircle(stepCircle3, tvStep3Num, 0, "3")
                applyCircle(stepCircle4, tvStep4Num, 1, "4")
                applyCircle(stepCircle5, tvStep5Num, 2, "5")
                stepLine1.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine2.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine3.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine4.setBackgroundColor(0xFFE0E0E0.toInt())
            }
            5 -> {
                applyCircle(stepCircle1, tvStep1Num, 0, "1")
                applyCircle(stepCircle2, tvStep2Num, 0, "2")
                applyCircle(stepCircle3, tvStep3Num, 0, "3")
                applyCircle(stepCircle4, tvStep4Num, 0, "4")
                applyCircle(stepCircle5, tvStep5Num, 1, "5")
                stepLine1.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine2.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine3.setBackgroundColor(0xFF2E7D32.toInt())
                stepLine4.setBackgroundColor(0xFF2E7D32.toInt())
            }
        }
    }

    private fun buildSummaryText(): String {
        val phoneFolder = BackupPreferences.getBackupFolderUri()
            ?.let { android.net.Uri.parse(it).lastPathSegment ?: it }
            ?: "-"
        val driveFolder = BackupPreferences.getBackupFolderName().ifBlank { "-" }
        val account = tvAccount.text.toString().ifBlank { "-" }
        val device = BackupPreferences.getDeviceName().ifBlank { "-" }
        val autoOn = BackupPreferences.isAutoBackupEnabled()
        val times = listOf(
            BackupPreferences.getAutoBackupTime1(),
            BackupPreferences.getAutoBackupTime2(),
            BackupPreferences.getAutoBackupTime3()
        ).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "-" }
        val autoStr = if (autoOn) "AÇIK — $times" else "KAPALI"

        return buildString {
            appendLine("📱 MetaTakip Yedekleme Ayarları")
            appendLine("─────────────────────────")
            appendLine("📂 Telefon Klasörü : $phoneFolder")
            appendLine("📁 Drive Klasör    : $driveFolder")
            appendLine("👤 Drive Hesabı    : $account")
            appendLine("📱 Bu Cihaz        : $device")
            appendLine("⏰ Otomatik Yedek  : $autoStr")
            appendLine("─────────────────────────")
            appendLine("⚠️ Diğer cihazlarda da aynı")
            appendLine("   Drive klasör adını kullanın!")
        }
    }

    private fun updateSummary() {
        tvSummaryContent.text = buildSummaryText()
    }

    private fun shareSettings() {
        val text = buildSummaryText()
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "MetaTakip Yedekleme Ayarları")
        }
        startActivity(android.content.Intent.createChooser(intent, "Paylaş"))
    }

    private fun clearAllDataFromBackupScreen() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast("⚙️ Temizlik başlıyor...")
                }
                synchronized(com.example.metatakip.feature_backup.util.MetaTakipDbLock.lock) {
                    val db = com.example.metatakip.feature_data.db.MetaTakipDb.getInstance(this@BackupRestoreActivity).writableDatabase
                    db.beginTransaction()
                    try {
                        db.execSQL("PRAGMA foreign_keys=OFF")
                        val tables = listOf(
                            "musteri", "siparis", "urun", "firma",
                            "personel", "unvan", "urun_tipi",
                            "mesaj_sablon", "call_logs", "change_log", "delete_log",
                            "etiket_sablon", "etiket_sablon_bilesen", "etiket_sayfa_ayar"
                        )
                        tables.forEach { table ->
                            try {
                                db.execSQL("DELETE FROM \"$table\"")
                                db.execSQL("DELETE FROM sqlite_sequence WHERE name=\"$table\"")
                            } catch (_: Exception) {}
                        }
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
                val driveOk = DriveBackupManager.deleteAllBackupsSync(this@BackupRestoreActivity)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = if (driveOk) "✅ Sistem sıfırlandı! Drive yedekleri de silindi."
                              else "✅ Yerel veri silindi. Drive silinemedi (oturum kapalı olabilir)."
                    toast(msg)
                    updateSummary()
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast("❌ Hata: ${e.message}")
                }
            }
        }
    }

    private fun updateAutoBackupButtonText() {
        val enabled = BackupPreferences.isAutoBackupEnabled()
        tvAutoStatus.text = if (enabled) "Durum: Açık" else "Durum: Kapalı"
        tvAutoStatus.setBackgroundColor(if (enabled) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())
        tvAutoStatus.setTextColor(0xFFFFFFFF.toInt())
        tvAutoStatus.setPadding(24, 14, 24, 14)
    }

    private fun showCsvImportPreview(results: List<CsvImportManager.CsvImportResult>) {
        val successCount = results.count { it.status == ImportRowResult.Status.OK }
        val errorCount = results.count { it.status == ImportRowResult.Status.ERROR }
        tvImportPreview.text = "CSV Import Sonuçları\nBaşarılı: $successCount\nHatalı: $errorCount"
        tvImportPreview.visibility = View.VISIBLE
    }

    private fun showTablePickerDialog(
        title: String,
        defaultSelected: BooleanArray,
        onResult: (List<String>) -> Unit
    ) {
        val tables = TableCatalog.ALL_TABLES.toTypedArray()
        val selected = defaultSelected.clone()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(tables, selected) { _, which, isChecked -> selected[which] = isChecked }
            .setPositiveButton("Tamam") { _, _ ->
                val picked = tables.indices.filter { selected[it] }.map { tables[it] }
                if (picked.isEmpty()) toast("En az 1 tablo seçmelisin")
                else onResult(picked)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun defaultPartialSelection(): BooleanArray {
        return BooleanArray(TableCatalog.ALL_TABLES.size) { idx -> TableCatalog.ALL_TABLES[idx] != "user" }
    }

    private suspend fun <T> runWithDriveLock(
        operationName: String,
        syncType: BackupFolderType,
        block: suspend () -> T
    ): T? {
        if (!BackupPreferences.isDriveConnected()) return block()
        DriveQueueManager.enqueue(this, operationName)
        refreshDevicePanels()
        withContext(Dispatchers.Main) {
            log("⏳ $operationName için sıra kontrol ediliyor...")
            toast("İşleminiz sıraya alındı")
        }
        val acquire = DriveLockManager.acquireLock(
            this,
            action = operationName,
            waitTimeoutMs = 30_000L
        ) { message ->
            withContext(Dispatchers.Main) {
                log("⏳ $message")
                refreshDevicePanels()
            }
        }
        if (!acquire.acquired) {
            DriveQueueManager.removeCurrentDevice(this)
            val queueState = DriveQueueManager.getQueueState(this)
            val myId = BackupPreferences.getOrCreateDeviceId()
            val myPosition = queueState?.positionOf(myId) ?: -1
            withContext(Dispatchers.Main) {
                log("⚠️ $operationName başlatılamadı: ${acquire.reason}")
                if (myPosition > 0) toast("Başka cihaz işlem yapıyor. Sıranız: $myPosition")
                else toast("Başka cihaz işlem yapıyor. Lütfen bekleyin.")
                refreshDevicePanels()
            }
            return null
        }
        withContext(Dispatchers.Main) {
            log("🔒 Sıra alındı: $operationName (${BackupPreferences.getDeviceName()})")
            log("☁️ Güncel yedek üstünden işlem devam edecek")
            toast("Sıra size geldi, işlem başladı")
            refreshDevicePanels()
        }
        return try {
            syncLatestDriveZipBeforeWrite(syncType, operationName)
            block()
        } finally {
            val released = DriveLockManager.releaseLock(this)
            DriveQueueManager.removeCurrentDevice(this)
            withContext(Dispatchers.Main) {
                if (released) log("🔓 Kilit bırakıldı")
                else log("⚠️ Kilit bırakılırken kontrol gerekli")
                refreshDevicePanels()
            }
        }
    }

    private suspend fun mergeUnsyncedChangesBack(changes: List<ChangeLog>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val partialManager = PartialBackupManager(this@BackupRestoreActivity)
                val db = openDbReadWrite()
                var successCount = 0
                db.beginTransaction()
                try {
                    changes.forEach { change ->
                        if (partialManager.applySingleChangeLogToDb(db, change)) successCount++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                    // ASLA db.close() yapma – bağlantı havuzda kalır
                }
                Log.d("BackupRestore", "Merged $successCount / ${changes.size} changes")
                successCount > 0
            } catch (e: Exception) {
                Log.e("BackupRestore", "Merge Error: ${e.message}")
                false
            }
        }

    private suspend fun syncLatestDriveZipBeforeWrite(type: BackupFolderType, operationName: String) {
        if (!BackupPreferences.isDriveConnected()) return
        withContext(Dispatchers.Main) { log("☁️ Drive'daki en güncel ${type.folderName} zip aranıyor...") }
        val localUnsyncedChanges = withContext(Dispatchers.IO) {
            ChangeLogManager.getUnsyncedChanges(this@BackupRestoreActivity)
        }
        val downloaded = DriveDownloadHelper.downloadLatestBackup(
            context = this,
            type = type,
            cachePrefix = "sync"
        )
        if (!downloaded.success || downloaded.file == null) {
            withContext(Dispatchers.Main) { log("ℹ️ $operationName için güncel zip bulunamadı: ${downloaded.message}") }
            return
        }
        withContext(Dispatchers.Main) {
            log("⬇️ En güncel zip indirildi: ${downloaded.latest?.fileName ?: downloaded.file.name}")
            log("♻️ Yerel veritabanı güncel snapshot ile eşitleniyor...")
        }
        val restored = ZipRestoreHelper.restoreFullZipToActiveDatabase(this, downloaded.file)
        if (restored) {
            val autoFileName = downloaded.latest?.fileName ?: downloaded.file.name
            if (autoFileName.isNotBlank()) BackupPreferences.setLastRestoreFile(autoFileName)
            (BackupPreferences.getActualDriveEmail() ?: BackupPreferences.getDriveEmail())
                ?.let { BackupPreferences.setLastRestoreEmail(it) }
            BackupPreferences.setLastRestoreFolder(BackupPreferences.getBackupFolderName())
            if (localUnsyncedChanges.isNotEmpty()) {
                withContext(Dispatchers.Main) { log("⚠️ Yerel ${localUnsyncedChanges.size} işlem yeni yedeğe birleştiriliyor...") }
                val mergeResult = mergeUnsyncedChangesBack(localUnsyncedChanges)
                withContext(Dispatchers.Main) {
                    if (mergeResult) log("✅ Yerel veriler başarıyla korundu ve birleştirildi")
                    else log("❌ Veri birleştirme sırasında hata oluştu!")
                }
            } else withContext(Dispatchers.Main) { log("✅ Yerel veritabanı güncel snapshot ile eşitlendi (Yerel fark yok)") }
        } else withContext(Dispatchers.Main) { log("⚠️ Güncel zip indirildi ama aktif veritabanına uygulanamadı") }
    }

    private fun openDbReadOnly(): SQLiteDatabase {
        return MetaTakipDb.getInstance(this).readableDatabase
    }

    private fun openDbReadWrite(): SQLiteDatabase {
        return MetaTakipDb.getInstance(this).writableDatabase
    }

    private suspend fun refreshDevicePanels() {
        withContext(Dispatchers.IO) {
            val context = this@BackupRestoreActivity
            val deviceLabel = BackupPreferences.getDeviceLabel()
            val myId = BackupPreferences.getOrCreateDeviceId()
            val isConnected = BackupPreferences.isDriveConnected()
            val lockState = if (isConnected) DriveLockManager.getLockState(context) else null
            val queueState = if (isConnected) DriveQueueManager.getQueueState(context) else null
            val history = if (isConnected) DriveHistoryManager.getHistory(context) else null
            withContext(Dispatchers.Main) {
                tvDeviceInfo.text = buildString {
                    append("Cihaz: $deviceLabel\n")
                    append("Durum: ")
                    if (!isConnected) append("Drive bağlı değil")
                    else {
                        if (lockState == null || lockState.isFree) append("Boş")
                        else append("${lockState.ownerName.ifBlank { "Bilinmeyen Cihaz" }} çalışıyor")
                    }
                }
                val myPosition = queueState?.positionOf(myId) ?: -1
                tvQueueStatus.text = buildString {
                    append("Kuyruk: ")
                    if (!isConnected) append("Drive bağlı değil")
                    else if (queueState == null || queueState.items.isEmpty()) append("Boş")
                    else append("${queueState.items.size} cihaz sırada")
                    if (myPosition > 0) append("\nSıram: $myPosition")
                }
                tvLastBackupInfo.text = "Son yedek: ${history?.displayText() ?: "Yok"}"
            }
        }
    }

    private fun showProgressDialog(message: String) {
        progressDialog?.dismiss()
        progressDialog = AlertDialog.Builder(this)
            .setTitle("İşlem Devam Ediyor")
            .setMessage(message)
            .setCancelable(false)
            .create()
            .apply { show() }
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLog.append("$msg\n")
            nestedScrollViewLog.post { nestedScrollViewLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private val changeListener = object : ChangeLogManager.OnChangeListener {
        override fun onNewChange(change: ChangeLog) {
            runOnUiThread {
                updateRecentChanges()
                log("📝 ${change.getDisplayMessage()}")
            }
        }
        override fun onChangesUpdated(changes: List<ChangeLog>) {
            runOnUiThread { updateRecentChanges() }
        }
    }
}
