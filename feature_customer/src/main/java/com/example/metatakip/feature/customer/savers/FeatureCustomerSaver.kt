package com.example.metatakip.feature.customer.savers

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.metatakip.feature_data.common.PhoneUtils
import com.example.metatakip.feature_data.entityModel.Customer
import dao.MetaTakipCustomerDao
import dao.MetaTakipFirmaDao
import dao.SMSHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🧩 FeatureCustomerSaver
 * GenericFormSaver içinde kullanılır.
 *
 * ✅ Müşteri kaydet/güncelle
 * ✅ Telefon varsa SMS + WhatsApp akışı
 * ✅ CallPopup'tan "rehbere ekle" seçeneği geldiyse rehbere kaydetme
 *
 * Not:
 * - Runtime SEND_SMS izni yoksa direct SMS denenmez, SMS uygulaması açılır (fallback).
 * - Rehbere eklemek için READ/WRITE_CONTACTS izni gerekir. Saver permission request yapamaz;
 *   izin yoksa ayarlara yönlendirir.
 */
object FeatureCustomerSaver {

    private const val TAG = "FeatureCustomerSaver"

    fun canHandle(table: String): Boolean {
        return table.equals("musteri", true) ||
                table.equals("customer", true) ||
                table.equals("customers", true)
    }

    fun save(
        context: Context,
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long,
        intent: Intent? = null,
        messageProvider: (firmaId: Long, customerId: Long) -> String?,
        onFlowFinished: (() -> Unit)? = null
    ): Long {
        return try {
            val customerDao = MetaTakipCustomerDao(context)
            val firmaDao = MetaTakipFirmaDao(context)

            // --- form key'leri FeatureCustomerTableFormProvider ile aynı ---
            val firmaId = data["firmaid"]?.toString()?.toLongOrNull() ?: 0L
            val adSoyad = data["adSoyad"]?.toString()?.trim().orEmpty()

            // ✅ normalize
            val ceptelRaw = data["ceptel"]?.toString()?.trim().orEmpty()
            val ceptel = PhoneUtils.toLocalTR(ceptelRaw)

            val ceptel2Raw = data["ceptel2"]?.toString()?.trim().orEmpty()
            val ceptel2 = PhoneUtils.toLocalTR(ceptel2Raw)

            val bolge = data["bolge"]?.toString()?.trim()
            val adres = data["adres"]?.toString()?.trim()
            val musteriNotu = data["musteriNotu"]?.toString()?.trim()

            if (firmaId <= 0L) {
                Toast.makeText(context, "❌ Firma seçilmelidir!", Toast.LENGTH_LONG).show()
                return -1L
            }

            if (adSoyad.isBlank()) {
                Toast.makeText(context, "❌ Ad Soyad boş olamaz!", Toast.LENGTH_LONG).show()
                return -1L
            }

            // Telefon zorunlu (provider'da required)
            val digits = PhoneUtils.digitsOnly(ceptel)
            if (digits.length < 10) {
                Toast.makeText(context, "❌ Telefon en az 10 hane olmalı!", Toast.LENGTH_LONG).show()
                return -1L
            }

            val firmaAdi = runCatching { firmaDao.getFirmaById(firmaId)?.firmaAdi }
                .getOrNull()
                .orEmpty()

            val customer = Customer(
                id = if (editMode) recordId else 0L,
                adSoyad = adSoyad,
                ceptel = ceptel,
                ceptel2 = ceptel2.ifBlank { null },
                adres = adres,
                bolge = bolge,
                musteriNotu = musteriNotu,
                firmaAdi = firmaAdi,
                firmaid = firmaId,
                isDeleted = 0,
                deletedAt = null,
                deleteReason = null,
                deletedBy = null,
                latitude = null,
                longitude = null,
                locationTimestamp = null,
                locationAddress = null
            )

            val customerId: Long = if (editMode) {
                Log.d(TAG, "✏️ updateCustomer id=$recordId")
                val ok = customerDao.updateCustomerById(recordId, customer)
                if (ok) recordId else -1L
            } else {
                Log.d(TAG, "➕ addCustomer")
                customerDao.addCustomerAndReturnId(customer)
            }

            if (customerId <= 0L) {
                Toast.makeText(context, "❌ Müşteri kaydedilemedi!", Toast.LENGTH_SHORT).show()
                return -1L
            }

            Toast.makeText(
                context,
                if (editMode) "✅ Müşteri güncellendi" else "✅ Müşteri oluşturuldu",
                Toast.LENGTH_SHORT
            ).show()

            // ✅ CallPopup "rehbere ekle" geldiyse rehbere kaydet (yalnız yeni kayıtta)
            tryAddToContactsIfNeeded(
                context = context,
                intent = intent,
                editMode = editMode,
                customerName = adSoyad,
                phone = ceptel,
                company = firmaAdi
            )

            // ✅ Telefon yoksa akış yok
            val telForSend = ceptel.trim()
            if (telForSend.isBlank()) {
                onFlowFinished?.invoke()
                return customerId
            }

            // ✅ Mesaj boş gelirse fallback üret
            val mesaj = messageProvider(firmaId, customerId).takeUnless { it.isNullOrBlank() }
                ?: buildFallbackCustomerMessage(
                    musteriAdi = adSoyad,
                    firmaAdi = firmaAdi,
                    editMode = editMode
                )

            // ✅ Tek noktadan SMS + WhatsApp akışı
            startSmsWhatsappFlow(
                context = context,
                telefon = telForSend,
                mesaj = mesaj,
                onFinished = onFlowFinished
            )

            customerId
        } catch (e: Exception) {
            Log.e(TAG, "❌ save hatası: ${e.message}", e)
            Toast.makeText(context, "❌ Hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            -1L
        }
    }

    // ==========================
    // 📞 CONTACTS (REHBER)
    // ==========================

    private fun tryAddToContactsIfNeeded(
        context: Context,
        intent: Intent?,
        editMode: Boolean,
        customerName: String,
        phone: String,
        company: String?
    ) {
        if (intent == null) return

        val fromCallPopup = intent.getBooleanExtra("fromCallPopup", false)
        val addToContacts = intent.getBooleanExtra("addToContactsAfterSave", false)
        val originalCallerName = intent.getStringExtra("originalCallerName")

        // ✅ sadece CallPopup + kullanıcı istedi + yeni kayıtsa
        if (!fromCallPopup || !addToContacts || editMode) return

        val localPhone = PhoneUtils.toLocalTR(phone)
        if (localPhone.isBlank()) return

        // ✅ izin yoksa saver request edemez -> ayarlara yönlendir
        if (!hasContactsPermission(context)) {
            Log.w(TAG, "⚠️ Contacts izni yok. Ayarlara yönlendiriliyor.")
            openAppSettings(context)
            Toast.makeText(context, "Rehbere eklemek için Rehber izni verin", Toast.LENGTH_LONG).show()
            return
        }

        // ✅ zaten rehberde varsa ekleme
        if (isNumberInContacts(context, localPhone)) {
            Log.d(TAG, "📒 Numara rehberde zaten var: $localPhone")
            return
        }

        addCustomerToAndroidContacts(
            context = context,
            customerName = customerName,
            phoneNumber = localPhone,
            originalName = originalCallerName,
            company = company
        )
    }

    private fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNumberInContacts(context: Context, phone: String): Boolean {
        return try {
            val normalized = PhoneUtils.toLocalTR(phone)
            if (normalized.isBlank()) return false

            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            val lookupUri = Uri.withAppendedPath(uri, Uri.encode(normalized))
            context.contentResolver.query(lookupUri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun addCustomerToAndroidContacts(
        context: Context,
        customerName: String,
        phoneNumber: String,
        originalName: String? = null,
        company: String? = null
    ) {
        try {
            val contactName = when {
                customerName.isNotBlank() -> customerName
                !originalName.isNullOrBlank() -> originalName
                else -> "Müşteri"
            }

            val ops = ArrayList<ContentProviderOperation>()

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        contactName
                    )
                    .build()
            )

            val local = PhoneUtils.toLocalTR(phoneNumber)
            if (local.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, local)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            }

            if (!company.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                        .withValue(
                            ContactsContract.CommonDataKinds.Organization.TYPE,
                            ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                        )
                        .build()
                )
            }

            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Note.NOTE,
                        "MetaTakip'ten eklendi - $dateString"
                    )
                    .build()
            )

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(context, "✅ Müşteri rehbere eklendi: $contactName", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Rehber ekleme hatası", e)
            Toast.makeText(context, "⚠️ Rehbere eklenemedi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================
    // 📩 SMS + WhatsApp
    // ==========================

    private fun buildFallbackCustomerMessage(
        musteriAdi: String,
        firmaAdi: String,
        editMode: Boolean
    ): String {
        val safeFirma = if (firmaAdi.isBlank()) "Firmamız" else firmaAdi
        val safeMusteri = if (musteriAdi.isBlank()) "Müşterimiz" else musteriAdi

        return if (editMode) {
            "Sayın $safeMusteri,\nBilgileriniz güncellenmiştir.\n\n$safeFirma"
        } else {
            "Sayın $safeMusteri,\nKaydınız oluşturulmuştur.\n\n$safeFirma"
        }
    }

    private fun canShowDialog(context: Context): Boolean {
        return (context is Activity) && !context.isFinishing
    }

    private fun hasSendSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings(context: Context) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun startSmsWhatsappFlow(
        context: Context,
        telefon: String,
        mesaj: String,
        onFinished: (() -> Unit)? = null
    ) {
        val smsHelper = SMSHelper(context)

        if (!canShowDialog(context)) {
            Log.w(TAG, "⚠️ Dialog gösterilemedi. SMS app fallback açılıyor.")
            smsHelper.openDefaultSmsApp(telefon, mesaj)
            onFinished?.invoke()
            return
        }

        val networkStatus = smsHelper.checkNetworkAndSimStatus()
        if (!networkStatus.first) {
            AlertDialog.Builder(context)
                .setTitle("📶 Şebeke Bağlantısı Yok")
                .setMessage(
                    "Müşteri kaydı tamamlandı.\n\n" +
                            "Ancak şebeke bağlantısı olmadığı için SMS doğrudan gönderilemiyor:\n\n" +
                            "${networkStatus.second}\n\n" +
                            "SMS uygulamasını açıp manuel göndermek ister misiniz?"
                )
                .setPositiveButton("EVET, AÇ") { _, _ ->
                    smsHelper.openDefaultSmsApp(telefon, mesaj)
                    showWhatsappDialog(context, telefon, mesaj, onFinished)
                }
                .setNegativeButton("HAYIR") { _, _ ->
                    showWhatsappDialog(context, telefon, mesaj, onFinished)
                }
                .setCancelable(false)
                .show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("SMS")
            .setMessage("SMS gönderilsin mi?\n\n$mesaj")
            .setPositiveButton("EVET") { _, _ ->
                if (!hasSendSmsPermission(context)) {
                    AlertDialog.Builder(context)
                        .setTitle("SMS İzni Gerekli")
                        .setMessage("SMS'i otomatik göndermek için SMS izni gerekli. Ne yapmak istersiniz?")
                        .setPositiveButton("SMS UYGULAMASI AÇ") { _, _ ->
                            smsHelper.openDefaultSmsApp(telefon, mesaj)
                            showWhatsappDialog(context, telefon, mesaj, onFinished)
                        }
                        .setNeutralButton("İZİN VER") { _, _ ->
                            openAppSettings(context)
                            showWhatsappDialog(context, telefon, mesaj, onFinished)
                        }
                        .setNegativeButton("İPTAL") { _, _ ->
                            showWhatsappDialog(context, telefon, mesaj, onFinished)
                        }
                        .setCancelable(false)
                        .show()
                    return@setPositiveButton
                }

                val success = smsHelper.sendSMS(telefon, mesaj)
                if (!success) {
                    smsHelper.openDefaultSmsApp(telefon, mesaj)
                }
                showWhatsappDialog(context, telefon, mesaj, onFinished)
            }
            .setNegativeButton("HAYIR") { _, _ ->
                showWhatsappDialog(context, telefon, mesaj, onFinished)
            }
            .setCancelable(false)
            .show()
    }

    private fun showWhatsappDialog(
        context: Context,
        telefon: String,
        mesaj: String,
        onFinished: (() -> Unit)? = null
    ) {
        if (!canShowDialog(context)) {
            onFinished?.invoke()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("WhatsApp")
            .setMessage("WhatsApp'tan da gönderilsin mi?")
            .setPositiveButton("EVET") { _, _ ->
                try {
                    val whatsappNum = PhoneUtils.toE164TR(telefon)
                    val url = "https://wa.me/$whatsappNum?text=${Uri.encode(mesaj)}"

                    val w = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (w.resolveActivity(context.packageManager) != null) {
                        context.startActivity(w)
                    } else {
                        val b = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(b)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "WhatsApp hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                onFinished?.invoke()
            }
            .setNegativeButton("HAYIR") { _, _ ->
                onFinished?.invoke()
            }
            .setCancelable(false)
            .show()
    }
}