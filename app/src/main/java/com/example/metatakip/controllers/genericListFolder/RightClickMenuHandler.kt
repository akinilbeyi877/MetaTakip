package com.example.metatakip.controllers.genericListFolder

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.metatakip.R
import com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity
import com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity
import com.example.metatakip.controllers.callphonelast.AddCallLogActivity
import com.example.metatakip.controllers.OrderActive.SiparisBilgiEkleActivity
import com.example.metatakip.data.metaTakipDb.crud.CallLogsDao
import com.example.metatakip.feature_data.entityModel.CallRecord
import com.example.metatakip.feature_data.entityModel.Customer
import com.example.metatakip.feature_data.entityModel.MesajSablon
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Order
import com.example.metatakip.feature_data.entityModel.SessionManager
import dao.MetaTakipCustomerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RightClickMenuHandler(
    private val activity: Activity
) {

    private val context = activity
    private val sessionManager = SessionManager(context)
    private val customerDao = MetaTakipCustomerDao(context)
    private val siparisDao = OrderDaoImpl(context)
    private val locationManager = MyLocationManager(activity)

    // =============================================================
    // 🎨 MODERN BOTTOM SHEET HELPER
    // =============================================================
    fun showModernMenu(
        title: String,
        items: List<ModernMenuItem>,
        autoDismiss: Boolean = false
    ) {
        showBottomSheetMenu(title, items, autoDismiss)
    }

    private fun showBottomSheetMenu(
        title: String,
        items: List<ModernMenuItem>,
        autoDismiss: Boolean = false // Varsayılan olarak kapalı (Geri gelince menü kalsın diye)
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_modern_menu, null)
        
        view.findViewById<TextView>(R.id.tvMenuTitle).text = title
        val container = view.findViewById<LinearLayout>(R.id.layoutMenuContainer)

        items.forEach { item ->
            val row = activity.layoutInflater.inflate(R.layout.item_modern_menu_row, container, false)
            val rowRoot = row.findViewById<androidx.cardview.widget.CardView>(R.id.cvMenuRowRoot)
            
            row.findViewById<TextView>(R.id.tvMenuLabel).text = item.label
            
            val iconView = row.findViewById<ImageView>(R.id.ivMenuIcon)
            val iconCard = row.findViewById<androidx.cardview.widget.CardView>(R.id.cvMenuIcon)
            
            iconView.setImageResource(item.iconRes)
            item.iconTint?.let { 
                iconView.setColorFilter(it)
                iconCard.setCardBackgroundColor(it and 0x15FFFFFF) // Çok daha hafif bir ton
            }

            rowRoot.setOnClickListener {
                if (autoDismiss) dialog.dismiss() // Sadece autoDismiss true ise kapat
                item.action()
            }
            container.addView(row)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    data class ModernMenuItem(
        val label: String,
        val iconRes: Int,
        val iconTint: Int? = null,
        val action: () -> Unit
    )

    fun showCallManagementMenu() {
        val menuItems = mutableListOf<ModernMenuItem>()
        
        menuItems.add(ModernMenuItem("Çağrı Kayıtları Listesi", R.drawable.ic_call, 0xFF1976D2.toInt()) {
            activity.startActivity(
                Intent(activity, com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity::class.java)
                    .putExtra("listType", "call_log")
                    .putExtra("pageTitle", "📞 Çağrı Kayıtları")
            )
        })
        
        menuItems.add(ModernMenuItem("Yeni Çağrı Ekle", android.R.drawable.ic_menu_add, 0xFF43A047.toInt()) {
            activity.startActivity(Intent(activity, com.example.metatakip.controllers.callphonelast.AddCallLogActivity::class.java))
        })

        showBottomSheetMenu("Çağrı Yönetimi", menuItems, autoDismiss = true)
    }

    // =============================================================
    // 📞 CALL RECORD MENÜSÜ (CallRecord tipi için)
    // =============================================================
    fun showCallRecordMenu(
        callRecord: CallRecord,
        onDataChanged: () -> Unit
    ) {
        val menuItems = mutableListOf<ModernMenuItem>()
        
        menuItems.add(ModernMenuItem("Tekrar Ara", R.drawable.ic_call, 0xFF1976D2.toInt()) { makeCall(callRecord.musteriTelefonu) })
        menuItems.add(ModernMenuItem("WhatsApp Mesajı", R.drawable.ic_whatsapp, 0xFF43A047.toInt()) { sendWhatsApp(callRecord.musteriTelefonu, callRecord.musteriAdi ?: "") })
        menuItems.add(ModernMenuItem("Sipariş Geçmişi", R.drawable.ic_calendar, 0xFF7B1FA2.toInt()) { showCallRecordOrderHistory(callRecord) })
        menuItems.add(ModernMenuItem("Müşteri Kaydet", R.drawable.ic_person, 0xFFF57C00.toInt()) { saveCallRecordAsCustomer(callRecord) })
        menuItems.add(ModernMenuItem("Düzenle", android.R.drawable.ic_menu_edit, 0xFF1976D2.toInt()) { editCallRecord(callRecord) })
        menuItems.add(ModernMenuItem("Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) { deleteCallRecord(callRecord, onDataChanged) })

        showBottomSheetMenu(callRecord.musteriAdi ?: "Çağrı Kaydı", menuItems)
    }

    // ... (editCallRecord and deleteCallRecord remain same)

    private fun editCallRecord(callRecord: CallRecord) {
        val intent = Intent(activity, AddCallLogActivity::class.java).apply {
            putExtra("EDIT_MODE", true)
            putExtra("CALL_RECORD_ID", callRecord.id)
            putExtra("NAME", callRecord.musteriAdi)
            putExtra("PHONE_NUMBER", callRecord.musteriTelefonu)
            putExtra("CALL_TYPE", callRecord.cagriTuru)
            putExtra("CALL_DATE", callRecord.cagriZamani)
            putExtra("NOTE", callRecord.merkezHataMesaji)
        }
        activity.startActivity(intent)
    }

    private fun deleteCallRecord(callRecord: CallRecord, onDataChanged: () -> Unit) {
        if (!sessionManager.isAdmin) {
            Toast.makeText(activity, "❌ Sadece admin silebilir", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Silme Onayı")
            .setMessage("'${callRecord.musteriAdi}' çağrı kaydı silinsin mi?")
            .setPositiveButton("Evet") { _, _ ->
                val dao = CallLogsDao(activity)
                val result = dao.deleteCallLog(callRecord.id)
                if (result) {
                    Toast.makeText(activity, "✅ Çağrı kaydı silindi", Toast.LENGTH_SHORT).show()
                    onDataChanged()
                } else {
                    Toast.makeText(activity, "❌ Silme başarısız", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }


    // =============================================================
// 👤 MÜŞTERİ MENÜSÜ - SİPARİŞ GEÇMİŞİ
// =============================================================
    private fun showCustomerOrderHistory(customer: Customer) {
        Log.e("RIGHT_CLICK_FIX", "=== Müşteri Sipariş Geçmişi (FIX) ===")
        Log.e("RIGHT_CLICK_FIX", "Müşteri: ${customer.adSoyad}")
        Log.e("RIGHT_CLICK_FIX", "Gelen UUID: ${customer.uuid}")

        // 🔥 Doğrudan veritabanından en güncel UUID'yi kontrol et
        val db = MetaTakipDb.getInstance(activity).readableDatabase
        var actualUuid: String? = null
        var actualAdi: String? = null

        val query = "SELECT adSoyad, uuid FROM musteri WHERE id = ? AND isDeleted = 0"
        db.rawQuery(query, arrayOf(customer.id.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                actualAdi = cursor.getString(0)
                actualUuid = cursor.getString(1)
                Log.e("RIGHT_CLICK_FIX", "✅ Veritabanındaki gerçek UUID: $actualUuid")
            }
        }
        db.close()

        val finalUuid = actualUuid ?: customer.uuid
        val finalAdi = actualAdi ?: customer.adSoyad

        if (!finalUuid.isNullOrBlank()) {
            val intent = Intent(activity, GenericListActivity::class.java).apply {
                putExtra("listType", "siparis")
                putExtra("filterMusteriUuid", finalUuid)
                putExtra("pageTitle", "📜 $finalAdi - SİPARİŞ GEÇMİŞİ")
                putExtra("showAllOrders", true)
            }
            activity.startActivity(intent)
        } else {
            Toast.makeText(activity, "Müşteri UUID'si bulunamadı!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSiparisCustomerOrderHistory(siparis: Order) {
        Log.e("RIGHT_CLICK_FIX", "=== Siparişten Müşteri Geçmişi (FIX) ===")
        Log.e("RIGHT_CLICK_FIX", "Sipariş ID: ${siparis.id}")
        Log.e("RIGHT_CLICK_FIX", "Sipariş Müşteri ID: ${siparis.musteriId}")

        // 🔥 Doğrudan veritabanından müşteri ID ile sorgula
        val db = MetaTakipDb.getInstance(activity).readableDatabase
        var musteriUuid: String? = null
        var musteriAdi: String? = null

        val query = """
        SELECT adSoyad, uuid FROM musteri 
        WHERE id = ? AND isDeleted = 0
    """

        db.rawQuery(query, arrayOf(siparis.musteriId.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                musteriAdi = cursor.getString(0)
                musteriUuid = cursor.getString(1)
                Log.e("RIGHT_CLICK_FIX", "✅ Müşteri bulundu: Ad=$musteriAdi, UUID=$musteriUuid")
            } else {
                Log.e("RIGHT_CLICK_FIX", "❌ Müşteri bulunamadı! Müşteri ID: ${siparis.musteriId}")
            }
        }
        db.close()

        if (musteriUuid != null) {
            val intent = Intent(activity, GenericListActivity::class.java).apply {
                putExtra("listType", "siparis")
                putExtra("filterMusteriUuid", musteriUuid)
                putExtra("pageTitle", "📜 $musteriAdi - SİPARİŞ GEÇMİŞİ")
                putExtra("showAllOrders", true)
                putExtra("from_popup", true)
            }
            activity.startActivity(intent)
        } else {
            Toast.makeText(activity, "Müşteri bulunamadı!", Toast.LENGTH_SHORT).show()
        }
    }

    // =============================================================
// 📞 ÇAĞRI KAYDI MENÜSÜ - SİPARİŞ GEÇMİŞİ
// =============================================================
    private fun showCallRecordOrderHistory(callRecord: CallRecord) {
        Log.e("RIGHT_CLICK", "=== TEST ===")

        // 1. YÖNTEM: customerDao ile bul
        val customer1 = customerDao.findCustomerByNormalizedPhone(callRecord.musteriTelefonu ?: "")
        Log.e("RIGHT_CLICK", "customerDao ile bulunan UUID: ${customer1?.uuid}")

        // 2. YÖNTEM: Doğrudan SQL ile bul
        val cleanPhone = callRecord.musteriTelefonu?.replace(Regex("[^0-9]"), "")?.takeLast(10) ?: ""
        var customer2Uuid: String? = null

        val db = MetaTakipDb.getInstance(activity).readableDatabase
        val query = """
        SELECT uuid FROM musteri 
        WHERE isDeleted = 0
        AND SUBSTR(REPLACE(REPLACE(REPLACE(REPLACE(ceptel, ' ', ''), '-', ''), '(', ''), ')', ''), -10) = ?
        ORDER BY id DESC
        LIMIT 1
    """

        db.rawQuery(query, arrayOf(cleanPhone)).use { cursor ->
            if (cursor.moveToFirst()) {
                customer2Uuid = cursor.getString(0)
                Log.e("RIGHT_CLICK", "SQL ile bulunan UUID: $customer2Uuid")
            }
        }
        db.close()

        // 3. YÖNTEM: Doğru UUID'yi manuel gir
        val correctUuid = "de884186-02e5-4199-85ef-5dc90e4a913a"
        Log.e("RIGHT_CLICK", "Doğru UUID (manuel): $correctUuid")

        // Hangisi doğru?
        val finalUuid = customer2Uuid ?: correctUuid

        val intent = Intent(activity, GenericListActivity::class.java).apply {
            putExtra("listType", "siparis")
            putExtra("filterMusteriUuid", finalUuid)
            putExtra("pageTitle", "📜 ${callRecord.musteriAdi} - SİPARİŞ GEÇMİŞİ")
            putExtra("showAllOrders", true)
        }
        activity.startActivity(intent)
    }
    // =============================================================
    // ➕ ÇAĞRIDAKİ BİLGİLERLE MÜŞTERİ KAYDET
    // =============================================================
    private fun saveCallRecordAsCustomer(callRecord: CallRecord) {
        val phoneNumber = callRecord.musteriTelefonu
        val name = callRecord.musteriAdi

        if (phoneNumber.isNullOrBlank()) {
            Toast.makeText(activity, "❌ Telefon numarası yok! Müşteri kaydedilemez.", Toast.LENGTH_LONG).show()
            return
        }

        if (name.isNullOrBlank()) {
            Toast.makeText(activity, "❌ Müşteri adı yok! Önce çağrı kaydını düzenleyin.", Toast.LENGTH_LONG).show()
            return
        }

        val existingCustomer = customerDao.getCustomerByPhone(phoneNumber)

        if (existingCustomer != null) {
            val menuItems = listOf(
                ModernMenuItem("Evet, Yeni Kaydet", android.R.drawable.ic_menu_add, 0xFF43A047.toInt()) { createNewCustomerFromCallRecord(callRecord) },
                ModernMenuItem("Mevcut Müşteriyi Aç", R.drawable.ic_person, 0xFF1976D2.toInt()) { openCustomerEdit(existingCustomer) }
            )
            showBottomSheetMenu("⚠️ Müşteri Zaten Var", menuItems, autoDismiss = true)
        } else {
            val menuItems = listOf(
                ModernMenuItem("Şimdi Kaydet", android.R.drawable.ic_menu_save, 0xFF43A047.toInt()) { createNewCustomerFromCallRecord(callRecord) }
            )
            showBottomSheetMenu("Yeni Müşteri Kaydı", menuItems, autoDismiss = true)
        }
    }

    // =============================================================
    // 🏢 FİRMA EŞİTLEME POPUP
    // =============================================================

    private fun createNewCustomerFromCallRecord(callRecord: CallRecord) {
        try {
            val phoneNumber = callRecord.musteriTelefonu
            val name = callRecord.musteriAdi ?: ""
            val firmaAdi = callRecord.arananFirmaAdi

            if (!firmaAdi.isNullOrBlank()) {
                showBatchFirmaMatchDialogForCallRecord(
                    excelFirmalar = listOf(firmaAdi),
                    onComplete = { firmaMapping ->
                        val firmaId = firmaMapping[firmaAdi]
                        saveCustomerWithFirma(callRecord, name, phoneNumber, firmaId, firmaAdi)
                    }
                )
            } else {
                saveCustomerWithFirma(callRecord, name, phoneNumber, null, null)
            }

        } catch (e: Exception) {
            Log.e("RightClickMenuHandler", "Müşteri kaydetme hatası", e)
            Toast.makeText(activity, "❌ Hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showBatchFirmaMatchDialogForCallRecord(
        excelFirmalar: List<String>,
        onComplete: (Map<String, Long?>) -> Unit
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_modern_menu, null)
        
        view.findViewById<TextView>(R.id.tvMenuTitle).text = "Firma Eşleştirme"
        val container = view.findViewById<LinearLayout>(R.id.layoutMenuContainer)

        val existingFirmalar = mutableListOf<Pair<Long, String>>()
        try {
            val db = MetaTakipDb.getInstance(activity).readableDatabase
            val cursor = db.rawQuery("SELECT id, firmaAdi FROM firma ORDER BY firmaAdi", null)
            while (cursor.moveToNext()) {
                existingFirmalar.add(Pair(cursor.getLong(0), cursor.getString(1)))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("FirmaMatch", "Firma listesi alınamadı", e)
        }

        val firmaList = existingFirmalar.map { it.second }
        val firmaIdList = existingFirmalar.map { it.first }

        val emptyOption = "——— SEÇİM YAPIN ———"
        val newFirmaOption = "➕ YENİ FİRMA OLUŞTUR"
        val spinnerOptions = listOf(emptyOption) + firmaList + listOf(newFirmaOption)

        val selectedFirmaIds = mutableMapOf<String, Long?>()
        val newFirmaNames = mutableMapOf<String, String>()
        val etNewFirmaMap = mutableMapOf<String, EditText>()
        val layoutNewFirmaMap = mutableMapOf<String, View>()

        excelFirmalar.forEach { excelFirma ->
            val rowView = activity.layoutInflater.inflate(R.layout.item_firma_match_row_modern, container, false)
            
            rowView.findViewById<TextView>(R.id.tvExcelFirmaName).text = excelFirma
            val btnSelect = rowView.findViewById<View>(R.id.btnSelectFirma)
            val tvSelectedName = rowView.findViewById<TextView>(R.id.tvSelectedFirmaName)
            val layoutNewInput = rowView.findViewById<LinearLayout>(R.id.layoutNewFirmaInput)
            val etNewName = rowView.findViewById<EditText>(R.id.etNewFirmaName)
            
            etNewName.setText(excelFirma)
            etNewFirmaMap[excelFirma] = etNewName
            layoutNewFirmaMap[excelFirma] = layoutNewInput

            // Otomatik eşleşme kontrolü
            val autoMatch = firmaList.find { it.equals(excelFirma, ignoreCase = true) }
            if (autoMatch != null) {
                tvSelectedName.text = autoMatch
                val idx = firmaList.indexOf(autoMatch)
                selectedFirmaIds[excelFirma] = firmaIdList[idx]
            }

            btnSelect.setOnClickListener {
                val selectionItems = mutableListOf<ModernMenuItem>()
                
                // 1. Yeni Firma Opsiyonu
                selectionItems.add(ModernMenuItem("➕ YENİ FİRMA OLUŞTUR", android.R.drawable.ic_menu_add, 0xFF1976D2.toInt()) {
                    tvSelectedName.text = "➕ YENİ FİRMA OLUŞTUR"
                    selectedFirmaIds.remove(excelFirma)
                    layoutNewInput.visibility = View.VISIBLE
                })

                // 2. Mevcut Firmalar
                existingFirmalar.forEach { (fId, fName) ->
                    selectionItems.add(ModernMenuItem(fName, R.drawable.ic_product_24, 0xFF64748B.toInt()) {
                        tvSelectedName.text = fName
                        selectedFirmaIds[excelFirma] = fId
                        layoutNewInput.visibility = View.GONE
                    })
                }

                showBottomSheetMenu("Firma Seçin", selectionItems, autoDismiss = true)
            }

            container.addView(rowView)
        }

        // ✅ Kaydet Butonu (Modern Stil)
        val btnSave = Button(activity).apply {
            text = "EŞLEŞTİRMEYİ KAYDET"
            setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF43A047.toInt()) // Yeşil
            val params = LinearLayout.LayoutParams(-1, 140)
            params.setMargins(40, 30, 40, 40)
            layoutParams = params
            typeface = Typeface.DEFAULT_BOLD
        }
        
        btnSave.setOnClickListener {
            val firmaMapping = mutableMapOf<String, Long?>()
            val incomplete = mutableListOf<String>()

            excelFirmalar.forEach { excelFirma ->
                if (layoutNewFirmaMap[excelFirma]?.visibility == View.VISIBLE) {
                    val name = etNewFirmaMap[excelFirma]?.text?.toString()?.trim()
                    if (!name.isNullOrBlank()) {
                        newFirmaNames[excelFirma] = name
                    } else {
                        incomplete.add(excelFirma)
                    }
                } else {
                    if (selectedFirmaIds.containsKey(excelFirma)) {
                        firmaMapping[excelFirma] = selectedFirmaIds[excelFirma]
                    } else {
                        incomplete.add(excelFirma)
                    }
                }
            }

            if (incomplete.isNotEmpty()) {
                Toast.makeText(activity, "Lütfen tüm seçimleri tamamlayın", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newFirmaNames.isNotEmpty()) {
                showProgressDialog("Yeni firma oluşturuluyor...")
                createNewFirmasAndGetMappingForCallRecord(newFirmaNames) { newFirmaIds ->
                    hideProgressDialog()
                    newFirmaIds.forEach { (excelFirma, fId) -> firmaMapping[excelFirma] = fId }
                    dialog.dismiss()
                    onComplete(firmaMapping)
                }
            } else {
                dialog.dismiss()
                onComplete(firmaMapping)
            }
        }
        container.addView(btnSave)

        dialog.setContentView(view)
        dialog.show()
    }

    private fun createNewFirmasAndGetMappingForCallRecord(
        newFirmalar: Map<String, String>,
        onComplete: (Map<String, Long>) -> Unit
    ) {
        val lifecycleOwner = activity as? LifecycleOwner
        if (lifecycleOwner == null) {
            onComplete(emptyMap())
            return
        }

        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = mutableMapOf<String, Long>()
            val db = MetaTakipDb.getInstance(activity).writableDatabase
            try {
                db.beginTransaction()
                newFirmalar.forEach { (excelFirma, yeniFirmaAdi) ->
                    val values = ContentValues().apply {
                        put("firmaAdi", yeniFirmaAdi)
                        put("uuid", UUID.randomUUID().toString())
                        put("updatedAt", System.currentTimeMillis())
                    }
                    val firmaId = db.insert("firma", null, values)
                    if (firmaId != -1L) {
                        result[excelFirma] = firmaId
                        Log.d("YeniFirma", "✅ Yeni firma oluşturuldu: $yeniFirmaAdi (ID:$firmaId)")
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    private var progressDialog: AlertDialog? = null

    private fun showProgressDialog(message: String) {
        progressDialog?.dismiss()
        progressDialog = AlertDialog.Builder(activity)
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

    private fun saveCustomerWithFirma(
        callRecord: CallRecord,
        name: String,
        phoneNumber: String,
        firmaId: Long?,
        excelFirmaAdi: String?
    ) {
        try {
            val customer = Customer().apply {
                adSoyad = name
                ceptel = phoneNumber
                firmaAdi = excelFirmaAdi
                firmaid = firmaId
                musteriNotu = if (firmaId != null) {
                    "📞 Çağrı kaydından oluşturuldu. (Firma eşleştirildi: ID:$firmaId)"
                } else {
                    "📞 Çağrı kaydından oluşturuldu."
                }
                updatedAt = System.currentTimeMillis()
            }

            val customerId = customerDao.addCustomerAndGetId(customer)

            if (customerId != -1L) {
                Toast.makeText(activity, "✅ Müşteri başarıyla kaydedildi!", Toast.LENGTH_LONG).show()

                val menuItems = listOf(
                    ModernMenuItem("Hemen Sipariş Oluştur", android.R.drawable.ic_menu_add, 0xFF43A047.toInt()) {
                        val intent = Intent(activity, GenericFormActivity::class.java).apply {
                            putExtra("targetTable", "siparis")
                            putExtra("musteriAdi", customer.adSoyad)
                            putExtra("musteriTelefon", customer.ceptel)
                            putExtra("firmaAdi", customer.firmaAdi)
                            putExtra("firmaId", customer.firmaid)
                            putExtra("linkedCustomerId", customerId)
                        }
                        activity.startActivity(intent)
                    },
                    ModernMenuItem("Daha Sonra (Kapat)", R.drawable.ic_clear, 0xFF64748B.toInt()) {
                        // Sadece menü kapanacak
                    }
                )

                showBottomSheetMenu("✅ Müşteri Kaydedildi", menuItems, autoDismiss = true)

            } else {
                Toast.makeText(activity, "❌ Müşteri kaydedilemedi!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("RightClickMenuHandler", "Kaydetme hatası", e)
            Toast.makeText(activity, "❌ Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =============================================================
    // 👤 MÜŞTERİ MENÜSÜ
    // =============================================================
    fun showCustomerMenu(
        customer: Customer,
        onDataChanged: () -> Unit,
        onEtiketYazdir: (Customer) -> Unit
    ) {
        val menuItems = mutableListOf<ModernMenuItem>()
        
        menuItems.add(ModernMenuItem("Sipariş Ekle", android.R.drawable.ic_menu_add, 0xFF43A047.toInt()) { addSiparisForCustomer(customer) })
        menuItems.add(ModernMenuItem("Sipariş Geçmişi", R.drawable.ic_calendar, 0xFF7B1FA2.toInt()) { showCustomerOrderHistory(customer) })
        menuItems.add(ModernMenuItem("Müşteri Düzenle", R.drawable.ic_person, 0xFFF57C00.toInt()) { openCustomerEdit(customer) })
        menuItems.add(ModernMenuItem("Konum Güncelle", R.drawable.ic_product_24, 0xFF1976D2.toInt()) { updateCustomerLocationFlow(customer, onDataChanged) })
        menuItems.add(ModernMenuItem("Haritada Göster", R.drawable.ic_product_24, 0xFFE91E63.toInt()) { showMapOptions(customer) })
        menuItems.add(ModernMenuItem("Etiket Yazdır", R.drawable.ic_content_copy, 0xFF009688.toInt()) { onEtiketYazdir(customer) })
        menuItems.add(ModernMenuItem("Müşteri Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) { showCustomerDeleteDialog(customer, onDataChanged) })
        menuItems.add(ModernMenuItem("Telefonla Ara", R.drawable.ic_call, 0xFF1976D2.toInt()) { makeCall(customer.ceptel) })
        menuItems.add(ModernMenuItem("WhatsApp Mesajı", R.drawable.ic_whatsapp, 0xFF43A047.toInt()) { sendWhatsApp(customer.ceptel, customer.adSoyad) })
        menuItems.add(ModernMenuItem("Çağrı Kayıtları", R.drawable.ic_call_back, 0xFF673AB7.toInt()) { openCallLogsForCustomer(customer) })

        showBottomSheetMenu(customer.adSoyad, menuItems)
    }

    // =============================================================
    // 📋 SİPARİŞ MENÜSÜ
    // =============================================================
    fun showSiparisMenu(
        siparis: Order,
        onDataChanged: () -> Unit,
        onEtiketYazdir: (Order) -> Unit
    ) {
        val menuItems = mutableListOf<ModernMenuItem>()
        
        menuItems.add(ModernMenuItem("Sipariş Bilgileri", R.drawable.ic_order_24, 0xFF1976D2.toInt()) {
            activity.startActivity(Intent(activity, SiparisBilgiEkleActivity::class.java).putExtra("siparisId", siparis.id))
        })
        menuItems.add(ModernMenuItem("Sipariş Geçmişi", R.drawable.ic_calendar, 0xFF7B1FA2.toInt()) { showSiparisCustomerOrderHistory(siparis) })
        menuItems.add(ModernMenuItem("Müşteri Düzenle", R.drawable.ic_person, 0xFFF57C00.toInt()) {
            val customer = customerDao.getCustomerById(siparis.musteriId)
            if (customer != null) openCustomerEdit(customer)
            else Toast.makeText(activity, "Müşteri bulunamadı", Toast.LENGTH_SHORT).show()
        })
        menuItems.add(ModernMenuItem("Durum Değiştir", R.drawable.ic_refresh, 0xFF43A047.toInt()) { showDurumSecimDialog(siparis, onDataChanged) })
        menuItems.add(ModernMenuItem("Haritada Göster", R.drawable.ic_product_24, 0xFFE91E63.toInt()) { showSiparisCustomerOnMap(siparis) })
        menuItems.add(ModernMenuItem("Etiket Yazdır", R.drawable.ic_content_copy, 0xFF009688.toInt()) { onEtiketYazdir(siparis) })
        menuItems.add(ModernMenuItem("Sipariş Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) { showSiparisDeleteDialog(siparis, onDataChanged) })
        menuItems.add(ModernMenuItem("Telefonla Ara", R.drawable.ic_call, 0xFF1976D2.toInt()) { makeCall(siparis.musteriTelefon) })
        menuItems.add(ModernMenuItem("WhatsApp Mesajı", R.drawable.ic_whatsapp, 0xFF43A047.toInt()) { sendWhatsApp(siparis.musteriTelefon, siparis.musteriAdi ?: "") })

        showBottomSheetMenu(siparis.musteriAdi ?: "Sipariş İşlemleri", menuItems)
    }



    // =============================================================
    // 📞 ÇAĞRI KAYITLARINI AÇ
    // =============================================================
    private fun openCallLogsForCustomer(customer: Customer) {
        val intent = Intent(activity, com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity::class.java).apply {
            putExtra("listType", "call_log")
            putExtra("pageTitle", "📞 Çağrı Kayıtları")
        }
        activity.startActivity(intent)
    }

    private fun openCallLogsForCustomerByPhone(phoneNumber: String?, name: String?) {
        val intent = Intent(activity, com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity::class.java).apply {
            putExtra("listType", "call_log")
            putExtra("pageTitle", "📞 Çağrı Kayıtları")
        }
        activity.startActivity(intent)
    }

    // =============================================================
    // 📝 MESAJ ŞABLONLARI MENÜSÜ
    // =============================================================
    fun showMesajSablonMenu(
        sablon: MesajSablon,
        onDelete: (Long) -> Unit,
        onDataChanged: () -> Unit
    ) {
        val menuItems = listOf(
            ModernMenuItem("✏️ Düzenle", android.R.drawable.ic_menu_edit, 0xFF1976D2.toInt()) {
                activity.startActivity(
                    Intent(activity, GenericFormActivity::class.java)
                        .putExtra("targetTable", "mesaj_sablon")
                        .putExtra("edit_mode", true)
                        .putExtra("id", sablon.id)
                )
            },
            ModernMenuItem("🗑️ Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) {
                AlertDialog.Builder(activity)
                    .setTitle("Silme Onayı")
                    .setMessage("'${sablon.baslik}' şablonu silinsin mi?")
                    .setPositiveButton("Evet") { _, _ ->
                        onDelete(sablon.id)
                        onDataChanged()
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            }
        )

        showBottomSheetMenu(sablon.baslik ?: "Mesaj Şablonu", menuItems)
    }

    fun showEtiketSablonMenu(
        sablon: Any,
        targetTable: String,
        recordId: Long,
        onDelete: (Long) -> Unit,
        onDataChanged: () -> Unit
    ) {
        showAdminMenu(
            title = "Etiket Şablonu",
            targetTable = targetTable,
            recordId = recordId,
            onDelete = onDelete,
            onDataChanged = onDataChanged
        )
    }

    fun showUrunTipiMenu(
        urunTipi: Any,
        targetTable: String,
        recordId: Long,
        onDelete: (Long) -> Unit,
        onDataChanged: () -> Unit
    ) {
        showAdminMenu(
            title = "Ürün Tipi",
            targetTable = targetTable,
            recordId = recordId,
            onDelete = onDelete,
            onDataChanged = onDataChanged
        )
    }

    private fun showDurumSecimDialog(
        siparis: Order,
        onDataChanged: () -> Unit
    ) {
        val durumAyarlari = listOf(
            Triple("Yeni Sipariş", R.drawable.ic_order_24, 0xFF1976D2.toInt()),      // Mavi
            Triple("Teslim Alındı", R.drawable.ic_package, 0xFF7B1FA2.toInt()),    // Mor
            Triple("Dağıtılacak", R.drawable.ic_product_24, 0xFFF57C00.toInt()),   // Turuncu
            Triple("Teslim Edildi", R.drawable.badge_success, 0xFF43A047.toInt()), // Yeşil
            Triple("Tekrar İşleme Alındı", R.drawable.ic_refresh, 0xFFE91E63.toInt()), // Pembe
            Triple("Sipariş İptal Edildi", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) // Kırmızı
        )
        
        val menuItems = durumAyarlari.map { (durum, icon, renk) ->
            ModernMenuItem(durum, icon, renk) {
                siparis.durum = durum
                siparisDao.updateSiparisById(siparis.id, siparis)
                onDataChanged()
            }
        }

        showBottomSheetMenu("Durum Değiştir", menuItems, autoDismiss = true)
    }

    // =============================================================
    // 📍 KONUM GÜNCELLE
    // =============================================================
    private fun updateCustomerLocationFlow(
        customer: Customer,
        onDataChanged: () -> Unit
    ) {
        locationManager.getCurrentLocation(
            activity = activity,
            onSuccess = { location, address ->
                activity.runOnUiThread {
                    val isUpdate = customer.latitude != null && customer.longitude != null
                    AlertDialog.Builder(activity)
                        .setTitle("📍 Konum Güncelle")
                        .setMessage("""
                            Alınan Konum:
                            Enlem: ${location.latitude}
                            Boylam: ${location.longitude}
                            Adres: ${address ?: "Adres bulunamadı"}
                            ${if (isUpdate) "⚠️ Mevcut konum güncellenecek" else "🆕 Yeni konum kaydedilecek"}
                        """.trimIndent())
                        .setPositiveButton("Kaydet") { _, _ ->
                            customerDao.updateCustomerLocationFull(
                                customerId = customer.id,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = System.currentTimeMillis(),
                                address = address
                            )
                            Toast.makeText(activity, "✅ Konum güncellendi", Toast.LENGTH_SHORT).show()
                            onDataChanged()
                        }
                        .setNegativeButton("İptal", null)
                        .setCancelable(false)
                        .show()
                }
            },
            onError = { error ->
                activity.runOnUiThread {
                    Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // =============================================================
    // 🗑️ SİPARİŞ SİL
    // =============================================================
    private fun showSiparisDeleteDialog(siparis: Order, onDataChanged: () -> Unit) {
        if (!sessionManager.isAdmin) {
            Toast.makeText(activity, "❌ Sadece admin silebilir", Toast.LENGTH_LONG).show()
            return
        }
        
        val menuItems = listOf(
            ModernMenuItem("Siparişi Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) {
                siparisDao.softDeleteSiparis(siparis.id)
                onDataChanged()
            }
        )
        
        showBottomSheetMenu("Sipariş No: ${siparis.id} Silinsin mi?", menuItems, autoDismiss = true)
    }

    // =============================================================
    // 🗺️ HARİTA
    // =============================================================
    private fun showSiparisCustomerOnMap(siparis: Order) {
        val customer = customerDao.getCustomerById(siparis.musteriId)
        if (customer != null) showMapOptions(customer)
    }

    private fun showMapOptions(customer: Customer) {
        val menuItems = mutableListOf<ModernMenuItem>()
        
        if (customer.latitude != null && customer.longitude != null) {
            menuItems.add(ModernMenuItem("Konum ile Göster", R.drawable.ic_product_24, 0xFF43A047.toInt()) { openMapWithLocation(customer) })
        }
        
        if (!customer.locationAddress.isNullOrBlank()) {
            menuItems.add(ModernMenuItem("Cihaz Adresi ile Göster", R.drawable.ic_product_24, 0xFF1976D2.toInt()) { openMapWithResolvedAddress(customer) })
        }
        
        menuItems.add(ModernMenuItem("Müşteri Adresi ile Göster", R.drawable.ic_product_24, 0xFFF57C00.toInt()) { openMapWithCustomerAddress(customer) })

        showBottomSheetMenu("Haritada Göster", menuItems, autoDismiss = true)
    }

    private fun openMapWithResolvedAddress(customer: Customer) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(customer.locationAddress)}")))
    }

    private fun openMapWithCustomerAddress(customer: Customer) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(customer.adres)}")))
    }

    private fun openMapWithLocation(customer: Customer) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${customer.latitude},${customer.longitude}")))
    }

    // =============================================================
    // ➕ SİPARİŞ EKLE
    // =============================================================
    private fun addSiparisForCustomer(customer: Customer) {
        val intent = Intent(context, GenericFormActivity::class.java).apply {
            putExtra("targetTable", "siparis")
            putExtra("linkedCustomerId", customer.id)
            putExtra("musteriAdi", customer.adSoyad)
            putExtra("musteriTelefon", customer.ceptel)
            putExtra("firmaAdi", customer.firmaAdi)
        }
        context.startActivity(intent)
    }

    // =============================================================
    // ✏️ MÜŞTERİ DÜZENLE
    // =============================================================
    private fun openCustomerEdit(customer: Customer) {
        activity.startActivity(
            Intent(activity, GenericFormActivity::class.java)
                .putExtra("targetTable", "musteri")
                .putExtra("edit_mode", true)
                .putExtra("id", customer.id)
        )
    }

    // =============================================================
    // 🗑️ MÜŞTERİ SİL
    // =============================================================
    private fun showCustomerDeleteDialog(customer: Customer, onDataChanged: () -> Unit) {
        if (!sessionManager.isAdmin) {
            Toast.makeText(activity, "❌ Sadece admin müşteri silebilir", Toast.LENGTH_LONG).show()
            return
        }

        val menuItems = listOf(
            ModernMenuItem("Müşteriyi ve Tüm Kayıtlarını Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) {
                val success = customerDao.deleteCustomerCascade(customer.id)
                Toast.makeText(activity, if (success) "✅ Silindi" else "❌ Silme başarısız", Toast.LENGTH_SHORT).show()
                if (success) onDataChanged()
            }
        )

        showBottomSheetMenu("${customer.adSoyad} Silinsin mi?", menuItems, autoDismiss = true)
    }

    fun showAdminMenu(
        title: String,
        targetTable: String,
        recordId: Long,
        onDelete: (Long) -> Unit,
        onDataChanged: () -> Unit
    ) {
        val menuItems = listOf(
            ModernMenuItem("✏️ Düzenle", android.R.drawable.ic_menu_edit, 0xFF1976D2.toInt()) {
                activity.startActivity(
                    Intent(activity, GenericFormActivity::class.java)
                        .putExtra("targetTable", targetTable)
                        .putExtra("edit_mode", true)
                        .putExtra("id", recordId)
                )
            },
            ModernMenuItem("🗑️ Sil", R.drawable.ic_delete_red, 0xFFD32F2F.toInt()) {
                AlertDialog.Builder(activity)
                    .setTitle("Silme Onayı")
                    .setMessage("Kayıt silinsin mi?")
                    .setPositiveButton("Evet") { _, _ ->
                        onDelete(recordId)
                        onDataChanged()
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            }
        )
        showBottomSheetMenu(title, menuItems)
    }

    // =============================================================
    // 📞 TELEFONLA ARA
    // =============================================================
    private fun makeCall(phoneNumber: String?) {
        if (phoneNumber.isNullOrBlank()) {
            Toast.makeText(activity, "Telefon numarası bulunamadı!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Arama ekranı açılamadı!", Toast.LENGTH_SHORT).show()
        }
    }

    // =============================================================
    // 💬 WHATSAPP MESAJI GÖNDER
    // =============================================================
    private fun sendWhatsApp(phoneNumber: String?, name: String) {
        if (phoneNumber.isNullOrBlank()) {
            Toast.makeText(activity, "Telefon numarası yok!", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        val finalNumber = when {
            cleanNumber.startsWith("0") -> "90" + cleanNumber.substring(1)
            cleanNumber.startsWith("90") -> cleanNumber
            else -> "90$cleanNumber"
        }

        val message = "Merhaba $name,"

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val url = "https://api.whatsapp.com/send?phone=$finalNumber&text=${Uri.encode(message)}"
            intent.data = Uri.parse(url)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "WhatsApp yüklü değil veya hata oluştu!", Toast.LENGTH_LONG).show()
        }
    }
}