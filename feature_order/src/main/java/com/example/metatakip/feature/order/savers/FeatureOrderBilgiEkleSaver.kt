package com.example.metatakip.feature.order.savers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.metatakip.feature.order.data.OrderDaoImpl
import dao.MetaTakipCustomerDao
import dao.SMSHelper

object FeatureOrderBilgiEkleSaver {

    private const val TAG = "FeatureOrderBilgiEkleSaver"

    fun canHandle(table: String): Boolean {
        return table.equals("siparis_bilgi_ekle", ignoreCase = true) ||
                table.equals("order_item_add", ignoreCase = true) ||
                table.equals("order_detail_add", ignoreCase = true)
    }

    /**
     * ✅ urun tablosuna kaydeder:
     * - siparisId intent ile gelmeli: putExtra("siparisId", ...)
     * - data keys (formdan): urunAdi, adet, fiyat, en, boy, metrekare, ekNot (DB'de ekNot yok)
     *
     * messageProvider: firmaId + customerId + siparisId + urunAdi + adet → mesaj döner
     */
    fun save(
        context: Context,
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long,
        intent: Intent?,
        messageProvider: (firmaId: Long, customerId: Long, siparisId: Long, urunAdi: String, adet: Int) -> String?,
        onFlowFinished: (() -> Unit)? = null
    ): Long {
        return try {
            val siparisId = intent?.getLongExtra("siparisId", 0L) ?: 0L
            if (siparisId <= 0L) {
                Toast.makeText(context, "❌ Ürün eklemek için sipariş seçilmelidir", Toast.LENGTH_LONG).show()
                return -1L
            }

            val urunAdi = data["urunAdi"]?.toString()?.trim().orEmpty()
            if (urunAdi.isBlank()) {
                Toast.makeText(context, "❌ Ürün adı boş olamaz!", Toast.LENGTH_LONG).show()
                return -1L
            }

            val adet = data["adet"]?.toString()?.toIntOrNull() ?: 1
            val fiyat = data["fiyat"]?.toString()?.toDoubleOrNull() ?: 0.0

            // DB'de en/boy yok → sadece m2 hesaplamada kullanıyoruz
            val enCm = data["en"]?.toString()?.toDoubleOrNull() ?: 0.0
            val boyCm = data["boy"]?.toString()?.toDoubleOrNull() ?: 0.0
            val hesaplananM2 = if (enCm > 0 && boyCm > 0) (enCm * boyCm) / 10000.0 else 0.0

            val metrekare = data["metrekare"]?.toString()?.toDoubleOrNull()
                ?.takeIf { it > 0.0 } ?: hesaplananM2

            // tutar: m2 varsa m2*fiyat, yoksa adet*fiyat
            val safeAdet = if (adet <= 0) 1 else adet
            val tutar = if (metrekare > 0.0) metrekare * fiyat else safeAdet * fiyat

            val dao = OrderDaoImpl(context)
            val siparis = dao.getSiparisById(siparisId)
            if (siparis == null) {
                Toast.makeText(context, "❌ Sipariş bulunamadı", Toast.LENGTH_LONG).show()
                return -1L
            }

            // urun tablosunda kolon var: urunTipi
            val urunTipi = siparis.urunTipi ?: ""

            val urunId: Long = if (editMode) {
                val ok = dao.updateUrunById(
                    urunId = recordId,
                    siparisId = siparisId,
                    ad = urunAdi,        // DB: ad
                    urunTipi = urunTipi, // DB: urunTipi
                    adet = safeAdet,
                    m2 = metrekare,
                    fiyat = fiyat,
                    tutar = tutar
                )
                if (ok) recordId else -1L
            } else {
                dao.addUrun(
                    siparisId = siparisId,
                    ad = urunAdi,
                    urunTipi = urunTipi,
                    adet = safeAdet,
                    m2 = metrekare,
                    fiyat = fiyat,
                    tutar = tutar
                )
            }

            if (urunId <= 0L) {
                Toast.makeText(context, "❌ Ürün kaydedilemedi!", Toast.LENGTH_SHORT).show()
                return -1L
            }

            // 🔥 YENİ: Ürün eklenince sipariş durumunu "Teslim Alındı" yap
            dao.updateOrderDurumu(siparisId, "Teslim Alındı", "")

            Toast.makeText(context, "✅ Ürün ${if (editMode) "güncellendi" else "eklendi"}", Toast.LENGTH_SHORT).show()

            // ✅ mesaj için firma + müşteri + telefon
            val firmaId = siparis.firmaId
            val customerId = siparis.musteriId

            if (firmaId <= 0L || customerId <= 0L) {
                onFlowFinished?.invoke()
                return urunId
            }

            val customerDao = MetaTakipCustomerDao(context)
            val telefon = customerDao.getCustomerById(customerId)?.ceptel.orEmpty()
            if (telefon.isBlank()) {
                onFlowFinished?.invoke()
                return urunId
            }

            val mesaj = messageProvider(firmaId, customerId, siparisId, urunAdi, safeAdet)
                .takeUnless { it.isNullOrBlank() }
                ?: buildFallbackItemMessage(urunAdi, safeAdet, siparisId)

            // ✅ sipariştekine benzer garanti akış
            handleSmsWhatsappFlowGuaranteed(
                context = context,
                telefon = telefon,
                mesaj = mesaj,
                onFinished = onFlowFinished
            )

            urunId

        } catch (e: Exception) {
            Log.e(TAG, "❌ save hatası: ${e.message}", e)
            Toast.makeText(context, "❌ Hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            -1L
        }
    }

    private fun buildFallbackItemMessage(urunAdi: String, adet: Int, siparisId: Long): String {
        val safeAdet = if (adet <= 0) 1 else adet
        return "Siparişinize ürün eklendi:\n$urunAdi x$safeAdet\nSipariş No: $siparisId"
    }

    private fun canShowDialog(context: Context): Boolean {
        return (context is Activity) && !context.isFinishing
    }

    /**
     * ✅ GARANTİLİ AKIŞ:
     * - Şebeke yoksa: SMS app aç / sonra WhatsApp sor
     * - Şebeke varsa: SMS sor / başarısızsa SMS app fallback / sonra WhatsApp sor
     * - Dialog gösterilemezse: SMS app açmayı dener, sonra onFinished
     */
    private fun handleSmsWhatsappFlowGuaranteed(
        context: Context,
        telefon: String,
        mesaj: String,
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
                .setTitle("📶 Şebeke Bağlantısı Yok")
                .setMessage(
                    "Ürün eklendi.\n\n" +
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
                val success = smsHelper.sendSMS(telefon, mesaj)
                if (!success) smsHelper.openDefaultSmsApp(telefon, mesaj)
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
                    val temizTel = telefon.replace("[^0-9]".toRegex(), "")
                    val whatsappNum = when {
                        temizTel.startsWith("0") -> "90${temizTel.substring(1)}"
                        temizTel.startsWith("90") -> temizTel
                        temizTel.length == 10 -> "90$temizTel"
                        else -> temizTel
                    }

                    val url = "https://wa.me/$whatsappNum?text=${Uri.encode(mesaj)}"
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (i.resolveActivity(context.packageManager) != null) {
                        context.startActivity(i)
                    } else {
                        Toast.makeText(context, "WhatsApp yüklü değil", Toast.LENGTH_SHORT).show()
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