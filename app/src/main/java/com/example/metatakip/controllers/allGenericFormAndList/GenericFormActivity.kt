package com.example.metatakip.controllers.allGenericFormAndList

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.adapters.GenericFormAdapter
import com.example.metatakip.builders.GenericBuildFormForTable
import com.example.metatakip.builders.GenericFormLoader
import com.example.metatakip.builders.GenericFormSaver
import com.example.metatakip.controllers.HomeActive.HomeActivity
import com.example.metatakip.controllers.adminConfigiration.AdminConfigurationActivity
import com.example.metatakip.controllers.services.OrderPopupActivity
import com.example.metatakip.feature.label.navigation.LabelNavigator
import com.example.metatakip.feature.order.savers.FeatureOrderSaver
import com.example.metatakip.feature_data.common.PhoneUtils
import com.example.metatakip.feature_data.entityModel.Customer
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.feature_data.helpers.GenericFormHelperImpl
import com.example.metatakip.feature_data.helpers.IGenericFormHelper
import com.example.metatakip.feature_data.unvan.UnvanDaoInterface
import com.example.metatakip.feature.unvan.data.UnvanDaoImpl
import com.example.metatakip.feature_backup.sync.SyncStatusStore
import com.example.metatakip.controllers.genericListFolder.RightClickMenuHandler
import dao.MetaTakipCustomerDao
import dao.MetaTakipFirmaDao

class GenericFormActivity : AppCompatActivity() {

    private lateinit var fields: MutableList<FormField>

    private var fromCallPopup: Boolean = false

    private lateinit var recyclerFormFields: RecyclerView
    private lateinit var btnKaydet: Button
    private lateinit var tvFormTitle: TextView

    private lateinit var formAdapter: GenericFormAdapter
    private lateinit var customerDao: MetaTakipCustomerDao
    private lateinit var firmaDao: MetaTakipFirmaDao
    private lateinit var unvanDao: UnvanDaoInterface

    private lateinit var formHelper: IGenericFormHelper

    private var editMode = false
    private var recordId: Long = -1L
    private var targetTable: String = "siparis"
    private var callerName: String? = null
    private var callerNumber: String? = null

    private var customerId: Long = -1L
    private var prefilledAdSoyad: String = ""
    private var prefilledPhone: String = ""
    private var prefilledAdres: String = ""
    private var prefilledNot: String = ""
    private var prefilledFirmaAdi: String = ""

    private var activeEditText: EditText? = null

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val text = formHelper.extractVoiceText(result.data)
                activeEditText?.setText(text)
                Toast.makeText(this, "🎤 Ses tanındı: $text", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "✅ Tüm izinler verildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Bazı izinler reddedildi", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generic_form)

        // 1. Başlatıcılar
        formHelper = GenericFormHelperImpl()
        customerDao = MetaTakipCustomerDao(this)
        firmaDao = MetaTakipFirmaDao(this)
        unvanDao = UnvanDaoImpl(this)

        // 2. Veri Yakalama
        targetTable = intent.getStringExtra("targetTable") ?: "siparis"
        editMode = intent.getBooleanExtra("edit_mode", false)
        recordId = intent.getLongExtra("id", -1L)
        callerName = intent.getStringExtra("callerName")
        callerNumber = intent.getStringExtra("callerNumber")
        fromCallPopup = intent.getBooleanExtra("fromCallPopup", false)

        customerId = intent.getLongExtra("customerId", -1L)
        prefilledAdSoyad = intent.getStringExtra("customerName") ?: intent.getStringExtra("adSoyad") ?: ""
        prefilledPhone = intent.getStringExtra("customerPhone") ?: intent.getStringExtra("phone") ?: ""
        prefilledAdres = intent.getStringExtra("customerAddress") ?: intent.getStringExtra("adres") ?: ""
        prefilledNot = intent.getStringExtra("customerNotes") ?: intent.getStringExtra("not") ?: ""
        prefilledFirmaAdi = intent.getStringExtra("customerCompany") ?: intent.getStringExtra("firmaAdi") ?: ""

        if (intent.getBooleanExtra("editMode", false)) {
            editMode = true
            if (customerId != -1L) recordId = customerId
        }

        val linkedCustomerId = intent.getLongExtra("linkedCustomerId", 0L)

        // 3. UI Kurulumu
        recyclerFormFields = findViewById(R.id.recyclerFormFields)
        btnKaydet = findViewById(R.id.btnKaydet)
        tvFormTitle = findViewById(R.id.tvFormTitle)
        recyclerFormFields.layoutManager = LinearLayoutManager(this)

        // 4. Form İnşası
        val formBuilder = GenericBuildFormForTable(this, firmaDao, unvanDao)
        fields = formBuilder.build(targetTable).toMutableList()

        if (targetTable == "siparis" && linkedCustomerId != 0L) {
            FeatureOrderSaver.prefillSiparisWithCustomer(this, fields, linkedCustomerId)
        }

        autoFillFormWithCallerInfo(fields, callerName, callerNumber)
        preFillFormWithPopupData(fields)

        tvFormTitle.text = getFormTitleText()

        formAdapter = GenericFormAdapter(this, fields) { editText -> startVoiceInput(editText) }
        recyclerFormFields.adapter = formAdapter

        // 5. 💾 KAYDET BUTONU
        btnKaydet.setOnClickListener {
            if (SyncStatusStore.isSyncing) {
                Toast.makeText(this, "⏳ Senkronizasyon devam ediyor, lütfen bekleyin...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setSaving(true)
            val genericSaver = GenericFormSaver(this)

            // 🛠️ Hibrit Hazırlık (Tip uyumsuzluğunu önlemek için .toString() eklendi)
            val fieldMap = fields.associate { it.key to (it.value?.toString() ?: "") }.toMutableMap()

            if (!editMode) {
                fieldMap["uuid"] = java.util.UUID.randomUUID().toString()
            }
            fieldMap["updatedAt"] = System.currentTimeMillis().toString() // Hata almamak için String yapıldı

            val success = when (targetTable) {
                "siparis" -> genericSaver.save(targetTable, fieldMap, editMode, recordId, intent,
                    messageProvider = { _, _ -> null }, onFlowFinished = { askGoHome() })

                "musteri" -> genericSaver.save(targetTable, fieldMap, editMode, recordId, intent,
                    messageProviderCustomer = { _, _, _ -> null },
                    onFlowFinished = {
                        maybeAddToContactsAfterSave(fieldMap)
                        askGoHome()
                    }
                ).also { res ->
                    if (res && fromCallPopup) {
                        customerId = if (editMode && recordId > 0L) recordId else resolveSavedCustomerIdFromForm(fieldMap)
                    }
                }

                "siparis_bilgi_ekle" -> genericSaver.save(targetTable, fieldMap, editMode, recordId, intent,
                    messageProviderBilgiEkle = { _, _, _, _, _ -> null }, onFlowFinished = { askGoHome() })

                "mesaj_sablon" -> genericSaver.save(targetTable, fieldMap, editMode, recordId, intent).also { res ->
                    if (res) { setResult(RESULT_OK); finish() }
                }

                else -> genericSaver.save(targetTable, fieldMap, editMode, recordId, intent, onFlowFinished = { askGoHome() })
            }

            if (success) handleSaveSuccess() else showSingleToast("❌ Kayıt başarısız!")
            setSaving(false)
        }

        // 6. Düzenleme Modu Yükleme
        if (editMode && recordId != -1L) loadFormData()

        checkAndRequestPermissions()
    }

    /** * Yardımcı Metot: Form Başlık Yazısı
     */
    private fun getFormTitleText(): String {
        return when (targetTable) {
            "musteri" -> if (editMode) "✏️ MÜŞTERİ DÜZENLE" else "👤 YENİ MÜŞTERİ"
            "siparis" -> if (editMode) "✏️ SİPARİŞ DÜZENLE" else "🧾 YENİ SİPARİŞ"
            "siparis_bilgi_ekle" -> if (editMode) "✏️ ÜRÜN DÜZENLE" else "🧾 ÜRÜN EKLE"
            "firma" -> if (editMode) "✏️ FİRMA DÜZENLE" else "🏢 YENİ FİRMA"
            else -> if (editMode) "✏️ KAYIT DÜZENLE" else "📋 YENİ KAYIT"
        }
    }
    override fun onResume() {
        super.onResume()
        // Verileri yeniden yükleme gerekmiyor, sadece popup kontrolü yap
    }

    override fun onDestroy() {
        super.onDestroy()

        // 🆕 Activity tamamen kapandıktan sonra popup'ı geri aç (GenericListActivity gibi)
        if (OrderPopupActivity.shouldReopenPopup(this)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                OrderPopupActivity.reopenPopup(this)
            }, 500)
        }
    }
    /** * Yardımcı Metot: Kayıt Başarılı Olduğunda UI Yönetimi
     */
    private fun handleSaveSuccess() {
        if (targetTable == "admin_firma") {
            showBackToAdminDialog()
        } else if (targetTable !in listOf("siparis", "musteri", "mesaj_sablon", "siparis_bilgi_ekle")) {
            showSingleToast("✅ Başarıyla kaydedildi")
            if (!fromCallPopup) {
                Handler(Looper.getMainLooper()).postDelayed({ askGoHome() }, 800)
            }
        }

        if (fromCallPopup && targetTable == "musteri") {
            openSiparisWithCustomer()
        }
    }

    /** * Yardımcı Metot: Düzenleme Modunda Verileri Yükle
     */
    private fun loadFormData() {
        val genericLoader = GenericFormLoader(this)
        var handled = false

        if (targetTable == "siparis") {
            FeatureOrderSaver.loadSiparisData(this, recordId, fields)
            handled = true
        }

        if (!handled) {
            val dataMap = mutableMapOf<String, Any?>()
            if (genericLoader.load(targetTable, recordId, dataMap)) {
                fields.forEach { field ->
                    field.value = dataMap[field.key]?.toString() ?: ""
                    field.isEditMode = true
                }
            }
        }
        formAdapter.notifyDataSetChanged()
    }

    private fun setSaving(saving: Boolean) {
        btnKaydet.isEnabled = !saving
        btnKaydet.text = if (saving) "⏳ KAYDEDİLİYOR..." else "KAYDET"
    }

    private fun resolveSavedCustomerIdFromForm(fieldMap: Map<String, Any?>): Long {
        return try {
            val tel = PhoneUtils.toLocalTR(fieldMap["ceptel"]?.toString())
            if (tel.isBlank()) return -1L

            val firmaId = fieldMap["firmaid"]?.toString()?.toLongOrNull()
            val existing = customerDao.findCustomerByNormalizedPhone(tel) ?: return -1L

            val best = listOf(existing)
                .sortedWith(
                    compareByDescending<Customer> { c ->
                        val sameFirma = (firmaId != null && firmaId > 0L && c.firmaid == firmaId)
                        if (sameFirma) 1 else 0
                    }.thenByDescending { it.id }
                )
                .firstOrNull()

            best?.id ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun startVoiceInput(editText: EditText) {
        activeEditText = editText
        try {
            val i = formHelper.buildVoiceIntent(languageTag = "tr-TR", prompt = "Konuşabilirsiniz...")
            voiceLauncher.launch(i)
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Mikrofon başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = formHelper.getMissingPhonePermissions(this)
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing)
        }
    }

    /**
     * İstersen bir yerden çağırırsın:
     * LabelNavigator().openTemplateEditor(this, sablonId)
     *
     * Not: Şu an bu Activity içinde otomatik çağıran bir yer yok.
     */
    @Suppress("unused")
    private fun openEtiketEditor(sablonId: Long) {
        LabelNavigator().openTemplateEditor(this, sablonId)
    }

    private fun showBackToAdminDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✅ Kayıt Başarılı")
            .setMessage("Kayıt başarıyla kaydedildi.\n\nAdmin ayarlarına dönmek ister misiniz?")
            .setCancelable(false)
            .setPositiveButton("Evet") { _, _ ->
                val i = Intent(this, AdminConfigurationActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(i)
                finish()
            }
            .setNegativeButton("Hayır") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showSingleToast(message: String) {
        formHelper.showSingleToast(this, message, long = true)
    }

    private fun openSiparisWithCustomer() {
        val customerIdForSiparis = if (editMode && recordId != -1L) recordId else customerId
        val i = Intent(this, GenericFormActivity::class.java).apply {
            putExtra("targetTable", "siparis")
            putExtra("edit_mode", false)
            putExtra("linkedCustomerId", customerIdForSiparis)
        }
        startActivity(i)
        finish()
    }

    private fun askGoHome() {
        val menuHandler = RightClickMenuHandler(this)
        val menuItems = listOf(
            RightClickMenuHandler.ModernMenuItem("Ana Sayfaya Dön", R.drawable.ic_chevron_right, 0xFF1976D2.toInt()) { goHome() },
            RightClickMenuHandler.ModernMenuItem("Burada Kal", android.R.drawable.ic_menu_add, 0xFF43A047.toInt()) { 
                finish()
                startActivity(intent)
            }
        )
        menuHandler.showModernMenu("✅ İşlem Tamamlandı", menuItems, autoDismiss = true)
    }

    private fun goHome() {
        val i = Intent(this, HomeActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
        finish()
    }

    private fun preFillFormWithPopupData(fields: MutableList<FormField>) {
        if (targetTable != "musteri") return

        val popupFromCall = intent.getBooleanExtra("fromCallPopup", false)
        val addToContacts = intent.getBooleanExtra("addToContactsAfterSave", false)

        fields.forEach { field ->
            when (field.key) {
                "adSoyad" -> {
                    if (popupFromCall && addToContacts && prefilledAdSoyad.isEmpty()) {
                        field.value = ""
                    } else if (prefilledAdSoyad.isNotEmpty() && field.value.isNullOrEmpty()) {
                        field.value = prefilledAdSoyad
                    }
                }

                "ceptel" -> if (prefilledPhone.isNotEmpty() && field.value.isNullOrEmpty()) {
                    field.value = PhoneUtils.toLocalTR(prefilledPhone)
                }

                "adres" -> if (prefilledAdres.isNotEmpty() && field.value.isNullOrEmpty()) {
                    field.value = prefilledAdres
                }

                "musteriNotu" -> if (prefilledNot.isNotEmpty() && field.value.isNullOrEmpty()) {
                    field.value = prefilledNot
                }

                "firmaid" -> if (prefilledFirmaAdi.isNotEmpty() && field.value.isNullOrEmpty()) {
                    val firma = firmaDao.getAllFirmalar().find { it.firmaAdi == prefilledFirmaAdi }
                    field.value = firma?.id?.toString() ?: "0"
                }
            }
        }
    }

    private fun autoFillFormWithCallerInfo(
        fields: MutableList<FormField>,
        callerName: String?,
        callerNumber: String?
    ) {
        if (callerName.isNullOrEmpty() && callerNumber.isNullOrEmpty()) return

        var isFieldUpdated = false
        fields.forEach { field ->
            when (field.key) {
                "adSoyad", "musteriAdi" -> {
                    if (!callerName.isNullOrEmpty() && callerName != "Bilinmeyen" && field.value.isNullOrEmpty()) {
                        field.value = callerName
                        isFieldUpdated = true
                    }
                }

                "ceptel", "musteriTelefon" -> {
                    if (!callerNumber.isNullOrEmpty() && field.value.isNullOrEmpty()) {
                        field.value = PhoneUtils.toLocalTR(callerNumber)
                        isFieldUpdated = true
                    }
                }
            }
        }

        if (isFieldUpdated && ::formAdapter.isInitialized) {
            formAdapter.notifyDataSetChanged()
            Toast.makeText(this, "📞 Arayan bilgileri forma yüklendi", Toast.LENGTH_SHORT).show()
        }
    }

    // Not: Bu activity içinde çağırdığın bir yer varsa aynen kullan.
    // Örn: save sonrası "addToContactsAfterSave" true ise:
    // formHelper.addCustomerToAndroidContacts(this, name, phone, originalName, company)

    private fun hasPermission(permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        } else true
    }


    private fun maybeAddToContactsAfterSave(dataMap: Map<String, Any?>) {
        val addToContacts = intent.getBooleanExtra("addToContactsAfterSave", false)
        if (!addToContacts) return

        val name = dataMap["adSoyad"]?.toString() ?: dataMap["musteriAdi"]?.toString().orEmpty()
        val phone = dataMap["ceptel"]?.toString() ?: dataMap["musteriTelefon"]?.toString().orEmpty()
        val company = prefilledFirmaAdi.ifBlank { null }

        if (name.isBlank() || phone.isBlank()) return

        formHelper.addCustomerToAndroidContacts(
            activity = this,
            customerName = name,
            phoneNumber = phone,
            originalName = callerName,
            company = company
        )}
}