package com.example.metatakip.feature.order.savers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.common.PhoneUtils
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.feature_data.entityModel.Order
import dao.MetaTakipCustomerDao
import dao.MetaTakipFirmaDao
import dao.SMSHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FeatureOrderSaver {

    private const val TAG = "FeatureOrderSaver"

    fun canHandle(table: String): Boolean {
        return table.equals("siparis", true) ||
                table.equals("order", true) ||
                table.equals("orders", true)
    }

    /**
     * Sipariş formunu seçili müşteri ile doldurur.
     */
    fun prefillSiparisWithCustomer(
        context: Context,
        fields: MutableList<FormField>,
        linkedCustomerId: Long
    ): Boolean {
        return try {
            if (linkedCustomerId <= 0L) return false

            val customerDao = MetaTakipCustomerDao(context)
            val customer = customerDao.getCustomerById(linkedCustomerId) ?: return false

            fields.forEach { field ->
                when (field.key) {
                    "musteriAdi" -> if (field.value.isNullOrEmpty()) field.value = customer.adSoyad
                    "musteriTelefon" -> if (field.value.isNullOrEmpty()) field.value = PhoneUtils.toLocalTR(customer.ceptel)
                    "firmaAdi" -> if (field.value.isNullOrEmpty()) field.value = customer.firmaAdi ?: ""
                    "firmaid" -> if (field.value.isNullOrEmpty()) field.value = customer.firmaid?.toString() ?: "0"
                    "adres" -> if (field.value.isNullOrEmpty()) field.value = customer.adres ?: ""
                    "notlar" -> if (field.value.isNullOrEmpty()) field.value = customer.musteriNotu ?: ""
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "prefillSiparisWithCustomer hata: ${e.message}", e)
            false
        }
    }

    /**
     * Edit mode'da siparişi yükler.
     */
    fun loadSiparisData(
        context: Context,
        siparisId: Long,
        fields: MutableList<FormField>
    ): Boolean {
        return try {
            if (siparisId <= 0L) return false

            val dao = OrderDaoImpl(context)
            val customerDao = MetaTakipCustomerDao(context)
            val firmaDao = MetaTakipFirmaDao(context)

            val siparis = dao.getSiparisById(siparisId) ?: return false
            val customer = customerDao.getCustomerById(siparis.musteriId)
            val firma = siparis.firmaId.takeIf { it > 0L }?.let { firmaDao.getFirmaById(it) }

            val musteriAdi = customer?.adSoyad.orEmpty()
            val musteriTelefon = PhoneUtils.toLocalTR(customer?.ceptel)
            val firmaAdi = firma?.firmaAdi ?: customer?.firmaAdi.orEmpty()

            fields.forEach { field ->
                when (field.key) {
                    "musteriAdi" -> field.value = musteriAdi
                    "musteriTelefon" -> field.value = musteriTelefon
                    "notlar" -> field.value = siparis.notlar ?: ""
                    "durum" -> field.value = siparis.durum ?: ""
                    "firmaid" -> field.value = siparis.firmaId.toString()
                    "firmaAdi" -> field.value = firmaAdi
                    "urunTipi" -> field.value = siparis.urunTipi ?: ""
                    "yetkili" -> field.value = siparis.yetkili ?: ""
                    "teslimAlmaTarihi" -> field.value = siparis.teslimAlmaTarihi ?: ""
                    "teslimTarihi" -> field.value = siparis.teslimTarihi ?: ""
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "loadSiparisData hata: ${e.message}", e)
            false
        }
    }

    fun save(
        context: Context,
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long,
        intent: Intent?,
        messageProvider: (firmaId: Long, siparisId: Long) -> String?,
        onFlowFinished: (() -> Unit)? = null
    ): Long {
        return try {
            val linkedCustomerId = intent?.getLongExtra("linkedCustomerId", 0L) ?: 0L

            if (linkedCustomerId <= 0L) {
                Toast.makeText(context, "❌ Sipariş için müşteri seçilmelidir", Toast.LENGTH_LONG).show()
                return -1L
            }

            val customerDao = MetaTakipCustomerDao(context)
            val firmaDao = MetaTakipFirmaDao(context)

            val customer = customerDao.getCustomerById(linkedCustomerId)
            if (customer == null) {
                Toast.makeText(context, "❌ Müşteri bulunamadı", Toast.LENGTH_LONG).show()
                return -1L
            }

            // 🔥 YENİ: Formdan gelen firma ID'sini kontrol et
            val formFirmaId = data["firmaid"]?.toString()?.toLongOrNull() ?: 0L
            
            // 🚩 AKILLI FİRMA VE YÖNLENDİRME KONTROLÜ (KRİTİK BÖLÜM)
            var finalFirmaId = if (formFirmaId > 0L) formFirmaId else (customer.firmaid ?: 0L)
            val mevcutFirmaAdi = customer.firmaAdi ?: ""

            // Durum 1: ID yok ama isim var mı? İsimden otomatik ID bulmayı dene.
            if (finalFirmaId <= 0L && mevcutFirmaAdi.isNotBlank()) {
                val potentialFirma = firmaDao.getAllFirmas().find {
                    it.firmaAdi?.trim().equals(mevcutFirmaAdi.trim(), true)
                }
                if (potentialFirma != null) {
                    finalFirmaId = potentialFirma.id
                    Log.d(TAG, "🔧 Otomatik Onarım: '$mevcutFirmaAdi' metni ID:$finalFirmaId ile eşleşti.")
                }
            }

            // Durum 2: Hala ID yoksa (İsim var ama veritabanında karşılığı yoksa)
            if (finalFirmaId <= 0L) {
                if (context is Activity && !context.isFinishing) {
                    context.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle("⚠️ Firma Bağlantısı Eksik")
                            .setMessage("Müşteri listesinde '$mevcutFirmaAdi' yazıyor ancak arka planda bu isme ait bir kayıt bulunamadı.\n\nSipariş ekleyebilmek için lütfen müşteriyi düzenleyip firmayı yeniden seçin.")
                            .setPositiveButton("MÜŞTERİYİ DÜZENLE") { _, _ ->
                                try {
                                    val editIntent = Intent(context, Class.forName("com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity")).apply {
                                        putExtra("table", "musteri")
                                        putExtra("recordId", linkedCustomerId)
                                        putExtra("editMode", true)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(editIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Düzenleme ekranı açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("İPTAL", null)
                            .setCancelable(false)
                            .show()
                    }
                }
                return -1L
            }

            // Buraya gelindiyse firma başarıyla bulunmuştur
            val finalFirma    = firmaDao.getFirmaById(finalFirmaId)
            val finalFirmaAdi = finalFirma?.firmaAdi ?: mevcutFirmaAdi
            val finalFirmaUuid = finalFirma?.uuid ?: ""

            val finalMusteriAdi = customer.adSoyad?.trim().orEmpty()
            if (finalMusteriAdi.isBlank()) {
                Toast.makeText(context, "❌ Müşteri adı boş olamaz!", Toast.LENGTH_LONG).show()
                return -1L
            }

            val finalMusteriTelefon = PhoneUtils.toLocalTR(customer.ceptel)
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            val siparis = Order().apply {
                if (editMode) id = recordId
                musteriId   = linkedCustomerId
                firmaId     = finalFirmaId
                // 🔑 UUID bağlantıları — senkronizasyon için kritik
                musteriUuid = customer.uuid
                firmaUuid   = finalFirmaUuid
                this.musteriAdi = finalMusteriAdi
                this.musteriTelefon = finalMusteriTelefon
                this.firmaAdi = finalFirmaAdi
                this.notlar = data["notlar"]?.toString().orEmpty()
                this.durum = data["durum"]?.toString()?.ifBlank { "Yeni Sipariş" } ?: "Yeni Sipariş"
                this.urunTipi = data["urunTipi"]?.toString().orEmpty()
                this.yetkili = data["yetkili"]?.toString().orEmpty()

                if (data["teslimAlmaTarihi"]?.toString()?.isNotBlank() == true) {
                    this.teslimAlmaTarihi = data["teslimAlmaTarihi"].toString()
                } else if (!editMode) {
                    this.teslimAlmaTarihi = today
                }
                this.teslimTarihi = data["teslimTarihi"]?.toString().orEmpty()
                if (editMode) this.duzenlemeTarihi = today
            }

            val dao = OrderDaoImpl(context)
            val siparisId: Long = if (editMode) {
                if (dao.updateSiparisById(recordId, siparis)) recordId else -1L
            } else {
                dao.addSiparis(siparis)
            }

            if (siparisId <= 0L) {
                Toast.makeText(context, "❌ Sipariş kaydedilemedi!", Toast.LENGTH_SHORT).show()
                return -1L
            }

            Toast.makeText(context, "✅ Sipariş kaydedildi", Toast.LENGTH_SHORT).show()

            if (finalMusteriTelefon.isNotBlank()) {
                val mesaj = messageProvider(finalFirmaId, siparisId).takeUnless { it.isNullOrBlank() }
                    ?: buildFallbackOrderMessage(finalMusteriAdi, siparisId, finalFirmaAdi)
                handleSmsWhatsappFlowGuaranteed(context, finalMusteriTelefon, mesaj, finalFirmaId, onFlowFinished)
            } else {
                onFlowFinished?.invoke()
            }

            return siparisId
        } catch (e: Exception) {
            Log.e(TAG, "❌ save hatası: ${e.message}", e)
            Toast.makeText(context, "❌ Hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            -1L
        }
    }

    private fun buildFallbackOrderMessage(m: String, s: Long, f: String): String =
        "Sayın $m,\nSiparişiniz alınmıştır.\nSipariş No: $s\n\n$f"

    private fun canShowDialog(context: Context): Boolean = (context is Activity) && !context.isFinishing

    private fun handleSmsWhatsappFlowGuaranteed(
        context: Context,
        telefon: String,
        mesaj: String,
        firmaId: Long,
        onFinished: (() -> Unit)? = null
    ) {
        val smsHelper = SMSHelper(context)
        if (!canShowDialog(context)) {
            smsHelper.openDefaultSmsApp(telefon, mesaj)
            onFinished?.invoke()
            return
        }

        val networkStatus = smsHelper.checkNetworkAndSimStatus()
        if (!networkStatus.first) {
            AlertDialog.Builder(context)
                .setTitle("📶 Şebeke Yok")
                .setMessage("Bağlantı hatası nedeniyle SMS gönderilemiyor. Manuel devam edilsin mi?")
                .setPositiveButton("EVET, UYGULAMAYI AÇ") { _, _ ->
                    smsHelper.openDefaultSmsApp(telefon, mesaj)
                    showWhatsappDialog(context, telefon, mesaj, onFinished)
                }
                .setNegativeButton("HAYIR") { _, _ -> showWhatsappDialog(context, telefon, mesaj, onFinished) }
                .setCancelable(false).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("SMS")
            .setMessage("Müşteriye bilgi SMS'i gönderilsin mi?\n\n$mesaj")
            .setPositiveButton("GÖNDER") { _, _ ->
                if (!smsHelper.sendSMS(telefon, mesaj)) smsHelper.openDefaultSmsApp(telefon, mesaj)
                showWhatsappDialog(context, telefon, mesaj, onFinished)
            }
            .setNegativeButton("HAYIR") { _, _ -> showWhatsappDialog(context, telefon, mesaj, onFinished) }
            .setCancelable(false).show()
    }

    private fun showWhatsappDialog(context: Context, telefon: String, mesaj: String, onFinished: (() -> Unit)?) {
        AlertDialog.Builder(context)
            .setTitle("WhatsApp")
            .setMessage("WhatsApp'tan da bilgi gönderilsin mi?")
            .setPositiveButton("EVET") { _, _ ->
                try {
                    val temizTel = telefon.replace("[^0-9]".toRegex(), "").let {
                        if (it.startsWith("0")) "90${it.substring(1)}" else if (it.length == 10) "90$it" else it
                    }
                    val url = "https://wa.me/$temizTel?text=${Uri.encode(mesaj)}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { Toast.makeText(context, "WhatsApp yüklü değil", Toast.LENGTH_SHORT).show() }
                onFinished?.invoke()
            }
            .setNegativeButton("HAYIR") { _, _ -> onFinished?.invoke() }
            .setCancelable(false).show()
    }
}