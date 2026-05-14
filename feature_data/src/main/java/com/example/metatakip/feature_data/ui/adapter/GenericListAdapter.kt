package com.example.metatakip.feature_data.ui.adapter

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.metatakip.feature_data.R
import com.example.metatakip.feature_data.ui.GenericListRowAdapterUiModel
import java.io.File

class GenericListAdapter(
    private val context: Context,
    private val items: List<GenericListRowAdapterUiModel>,
    private val onCameraClick: ((GenericListRowAdapterUiModel) -> Unit)? = null,
    private val onItemClick: (GenericListRowAdapterUiModel) -> Unit
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): GenericListRowAdapterUiModel = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    // Performans için ViewHolder yapısı
    private class ViewHolder(view: View) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val ivProfile: ImageView = view.findViewById(R.id.ivProfile)
        val ivCameraIcon: ImageView = view.findViewById(R.id.ivCameraIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvFirmaTag: TextView = view.findViewById(R.id.tvFirmaTag)
        val tvAdet: TextView = view.findViewById(R.id.tvAdet)
        val tvM2: TextView = view.findViewById<TextView>(R.id.tvM2)
        val tvTutar: TextView = view.findViewById(R.id.tvTutar)
        val tvOdeme: TextView = view.findViewById(R.id.tvOdeme)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val tvSiparisNo: TextView = view.findViewById(R.id.tvSiparisNo)
        val tvAdres: TextView = view.findViewById(R.id.tvAdres)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val tvBadge: TextView = view.findViewById(R.id.tvBadge)
        val tvNot: TextView = view.findViewById(R.id.tvNot)
        val tvTarihler: TextView = view.findViewById(R.id.tvTarihler)
        val tvCopyAll: TextView = view.findViewById(R.id.tvCopyAll)
        val btnCopyCard: ImageButton = view.findViewById(R.id.btnCopyCard)
        val rlHeader: View = view.findViewById(R.id.rlHeader)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_generic, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = getItem(position)

        // 1. Avatar & Başlık
        val titleText = item.title ?: "İsimsiz"
        holder.tvTitle.text = titleText
        
        // 📸 Fotoğraf İşleme
        if (!item.photoPath.isNullOrBlank()) {
            val file = File(item.photoPath)
            Log.d("IMAGE_DEBUG", "Fotoğraf yolu: ${item.photoPath} | Dosya var mı: ${file.exists()}")
            if (file.exists()) {
                holder.ivProfile.visibility = View.VISIBLE
                holder.tvAvatar.visibility = View.GONE
                try {
                    holder.ivProfile.setImageURI(Uri.fromFile(file))
                    holder.ivProfile.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                    holder.ivProfile.clipToOutline = true
                    holder.ivProfile.setOnClickListener { 
                        showFullScreenImage(item.photoPath) 
                    }
                } catch (e: Exception) {
                    Log.e("IMAGE_DEBUG", "Resim yükleme hatası: ${e.message}")
                    showLetterAvatar(holder, titleText)
                }
            } else {
                showLetterAvatar(holder, titleText)
            }
        } else {
            showLetterAvatar(holder, titleText)
            holder.tvAvatar.setOnClickListener {
                onCameraClick?.invoke(item)
            }
        }

        // 📷 Kamera İkonu Tıklama
        holder.ivCameraIcon.setOnClickListener {
            Log.d("CLICK_DEBUG", "Kamera ikonuna basıldı")
            onCameraClick?.invoke(item)
        }
        
        val firmaText = item.extra("firma")?.takeIf { it.isNotBlank() && it != "MEGA" } ?: "Firma yok"
        holder.tvFirmaTag.text = "🏢 $firmaText"
        holder.tvFirmaTag.visibility = android.view.View.VISIBLE

        // 2. Sayısal Veriler (İstatistik Grid)
        val adetValue = item.extra("adet") ?: "---"
        holder.tvAdet.text = adetValue
        // Ürün eklenmemişse adet kırmızı göster
        if (adetValue.startsWith("⚠️")) {
            holder.tvAdet.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            holder.tvAdet.setTextColor(Color.parseColor("#212121"))
        }

        holder.tvM2.text = item.extra("m2") ?: "---"
        holder.tvTutar.text = item.extra("tutar") ?: "---"
        holder.tvOdeme.text = item.extra("odeme") ?: "---"

        // 3. Sipariş ve Ürün Bilgileri
        val subtitleText = item.subtitle ?: "---"
        if (subtitleText.startsWith("⚠️")) {
            // Ürün Eklenmemiş uyarısı — öne çıkar
            holder.tvSubtitle.text = subtitleText
            holder.tvSubtitle.setTextColor(Color.parseColor("#D32F2F"))
            holder.tvSubtitle.textSize = 12f
        } else {
            holder.tvSubtitle.text = "Ürün: $subtitleText"
            holder.tvSubtitle.setTextColor(Color.parseColor("#616161"))
            holder.tvSubtitle.textSize = 11f
        }
        holder.tvSiparisNo.text = "🎫 No: ${item.extra("siparisNo") ?: "---"}"

        // 4. İletişim ve Detaylar
        val adresBilgisi = item.extra("adres") ?: "---"
        holder.tvAdres.text = if (adresBilgisi == "Adres Yok") "📍 Adres Yok" else "📍 $adresBilgisi"
        holder.tvPhone.text = "📞 ${item.extra("telefon") ?: "---"}"
        holder.tvBadge.text = item.badgeText ?: "---"
        holder.tvNot.text = "Not: ${item.extra("not") ?: "---"}"
        holder.tvTarihler.text = item.extra("tarihler") ?: "---"

        // 5. Badge Rengi
        if (item.badgeColor != null) {
            holder.tvBadge.background?.setTint(item.badgeColor)
            holder.tvBadge.setTextColor(Color.WHITE)
        }

        // 6. Kopyala / Paylaş Butonu (sağ üstte görünür)
        holder.btnCopyCard.setOnClickListener {
            showCopyShareDialog(item)
        }

        // 7. Eski compat tvCopyAll da aynı dialog'u açsın (geriye dönük)
        holder.tvCopyAll.setOnClickListener {
            showCopyShareDialog(item)
        }

        // 8. Satıra Tıklama (ana event)
        view.setOnClickListener {
            onItemClick(item)
        }

        return view
    }

    private fun showLetterAvatar(holder: ViewHolder, titleText: String) {
        holder.ivProfile.visibility = View.GONE
        holder.tvAvatar.visibility = View.VISIBLE
        holder.tvAvatar.text = if (titleText.isNotEmpty()) titleText.take(1).uppercase() else "?"
    }

    // ============================================================
    // 📋 KOPYALA / PAYLAŞ DİALOGU
    // ============================================================
    private fun showCopyShareDialog(item: GenericListRowAdapterUiModel) {
        val musteriAdi   = item.title ?: "---"
        val urunBilgisi  = item.subtitle ?: "---"
        val siparisNo    = item.extra("siparisNo") ?: "---"
        val telefon      = item.extra("telefon") ?: "---"
        val durum        = item.badgeText ?: "---"
        val adres        = item.extra("adres") ?: "---"
        val tutar        = item.extra("tutar") ?: "---"
        val paylasilacakMetin = item.extra("paylasimMetni")
            ?: buildDefaultShareText(musteriAdi, urunBilgisi, siparisNo, telefon, durum, adres, tutar)

        val density = context.resources.displayMetrics.density
        fun dp(n: Float) = (n * density).toInt()

        fun roundedBg(colorHex: String, radius: Float = 12f): GradientDrawable =
            GradientDrawable().also { it.setColor(Color.parseColor(colorHex)); it.cornerRadius = dp(radius).toFloat() }

        val dialog = Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        /* ── köklü LinearLayout ── */
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().also {
                val r = dp(20f).toFloat()
                it.setColor(Color.parseColor("#EEF2F7"))
                it.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            background = bg
        }

        /* ── MAVİ BAŞLIK ── */
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val bg = GradientDrawable().also {
                val r = dp(20f).toFloat()
                it.setColor(Color.parseColor("#1976D2"))
                it.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            background = bg
            setPadding(dp(20f), dp(16f), dp(16f), dp(16f))
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvHeaderTitle = TextView(context).apply {
            text = "📋 Kopyala / Paylaş"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvSub = TextView(context).apply {
            text = musteriAdi.take(18)
            textSize = 11f
            setTextColor(Color.parseColor("#90CAF9"))
        }
        header.addView(tvHeaderTitle)
        header.addView(tvSub)
        root.addView(header)

        /* ── İÇERİK (scroll) ── */
        val itemsLL = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(4f))
        }

        /* Bölüm başlığı */
        fun sectionLabel(text: String) {
            itemsLL.addView(TextView(context).apply {
                this.text = text
                textSize = 10f
                setTextColor(Color.parseColor("#90A4AE"))
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4f), dp(8f), 0, dp(4f))
            })
        }

        /* Bilgi satırı — kopyalanabilir kart */
        fun infoRow(emoji: String, label: String, value: String) {
            if (value == "---" || value.isBlank()) return
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedBg("#FFFFFF")
                setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, dp(6f)) }
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setOnClickListener {
                    copyToClipboard(value, "✅ $label kopyalandı")
                    dialog.dismiss()
                }
            }
            card.addView(TextView(context).apply {
                text = emoji; textSize = 18f
                layoutParams = LinearLayout.LayoutParams(dp(32f), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
            })
            val textBlock = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(8f), 0, 0, 0)
            }
            textBlock.addView(TextView(context).apply {
                text = label; textSize = 9f
                setTextColor(Color.parseColor("#90A4AE"))
                setTypeface(null, Typeface.BOLD)
            })
            textBlock.addView(TextView(context).apply {
                text = value; textSize = 13f
                setTextColor(Color.parseColor("#1A237E"))
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            card.addView(textBlock)
            card.addView(TextView(context).apply {
                text = "KOPYALA"; textSize = 9f
                setTextColor(Color.parseColor("#1976D2"))
                setTypeface(null, Typeface.BOLD)
            })
            itemsLL.addView(card)
        }

        /* Eylem butonu */
        fun actionRow(emoji: String, label: String, colorHex: String, action: () -> Unit) {
            val btn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedBg(colorHex)
                setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, dp(6f)) }
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setOnClickListener { action(); dialog.dismiss() }
            }
            btn.addView(TextView(context).apply {
                text = emoji; textSize = 17f
                layoutParams = LinearLayout.LayoutParams(dp(32f), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
            })
            btn.addView(TextView(context).apply {
                text = label; textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(8f), 0, 0, 0)
            })
            itemsLL.addView(btn)
        }

        /* Satırları ekle */
        sectionLabel("BİLGİLERİ KOPYALA")
        infoRow("👤", "MÜŞTERİ",    musteriAdi)
        infoRow("📞", "TELEFON",    telefon)
        infoRow("📦", "ÜRÜN",       urunBilgisi)
        infoRow("🎫", "SİPARİŞ NO", siparisNo)
        infoRow("📌", "DURUM",      durum)
        infoRow("📍", "ADRES",      adres)
        infoRow("💰", "TUTAR",      tutar)

        sectionLabel("PAYLAŞ")
        actionRow("📋", "Tümünü Kopyala",          "#1565C0") { copyToClipboard(paylasilacakMetin, "📋 Tüm bilgiler kopyalandı") }
        actionRow("💬", "WhatsApp ile Paylaş",      "#2E7D32") { shareViaWhatsApp(paylasilacakMetin) }
        actionRow("📱", "SMS ile Paylaş",           "#00838F") { shareViaSms(telefon.filter { it.isDigit() }, paylasilacakMetin) }
        actionRow("🔗", "Diğer Uygulamalarla Paylaş","#546E7A") { shareViaChooser(paylasilacakMetin) }

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(itemsLL)
        }
        root.addView(scrollView)

        /* ── KAPAT BUTONU ── */
        val footer = LinearLayout(context).apply {
            setPadding(dp(14f), dp(8f), dp(14f), dp(16f))
            setBackgroundColor(Color.parseColor("#EEF2F7"))
        }
        footer.addView(TextView(context).apply {
            text = "✕  Kapat"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = roundedBg("#78909C")
            setPadding(0, dp(13f), 0, dp(13f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(footer)

        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    private fun buildDefaultShareText(
        musteriAdi: String,
        urunBilgisi: String,
        siparisNo: String,
        telefon: String,
        durum: String,
        adres: String,
        tutar: String
    ): String = """
        🧾 SİPARİŞ BİLGİSİ
        ━━━━━━━━━━━━━━━━━━━━
        🎫 Sipariş No : $siparisNo
        👤 Müşteri    : $musteriAdi
        📞 Telefon    : $telefon
        📦 Ürün       : $urunBilgisi
        📌 Durum      : $durum
        💰 Tutar      : $tutar
        📍 Adres      : $adres
        ━━━━━━━━━━━━━━━━━━━━
    """.trimIndent()

    private fun copyToClipboard(text: String, message: String) {
        if (text.isBlank() || text == "---") {
            Toast.makeText(context, "❌ Kopyalanacak geçerli veri yok", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("metatakip_data", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun shareViaWhatsApp(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // WhatsApp yüklü değil — genel seçici ile dene
            shareViaChooser(text)
        }
    }

    private fun shareViaSms(telefon: String, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$telefon")
            putExtra("sms_body", text)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "❌ SMS uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareViaChooser(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Paylaş"))
    }

    private fun showFullScreenImage(photoPath: String) {
        val imageView = ImageView(context)
        imageView.setImageURI(Uri.parse(photoPath))
        imageView.adjustViewBounds = true
        
        AlertDialog.Builder(context)
            .setView(imageView)
            .setPositiveButton("Kapat", null)
            .show()
    }
}
