package com.example.metatakip.feature.label.navigation

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ExpandableListView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.metatakip.feature.label.R
import com.example.metatakip.feature.label.data.EtiketSablonDaoImpl
import com.example.metatakip.feature.label.ui.EtiketExpandableAdapter
import com.example.metatakip.feature_data.entityModel.EtiketSayfaAyar
import com.example.metatakip.feature_data.entityModel.Order
import com.example.metatakip.feature_data.label.EtiketManager
import com.example.metatakip.feature_data.label.EtiketSablonDaoInterface
import java.util.Locale

class LabelFlowController(
    private val context: Context,
    private val etiketManager: EtiketManager,
    private val mode: LabelMode = LabelMode.PRINT,
    private val onPrint: ((String) -> Unit)? = null,
    private val dao: EtiketSablonDaoImpl = EtiketSablonDaoImpl(context),
    private val userIdProvider: () -> Int = { 1 }
) {
    private var sablonId: Long = -1L
    private var previewText: String = ""
    private var currentItem: Any? = null
    private var currentAyar: EtiketSayfaAyar = EtiketSayfaAyar()

    private val dm get() = context.resources.displayMetrics
    private fun dp(v: Int) = (v * dm.density).toInt()
    private fun dpF(v: Float) = v * dm.density
    private fun blue()     = Color.parseColor("#1976D2")
    private fun blueCSL()  = ColorStateList.valueOf(blue())
    private fun lightCSL() = ColorStateList.valueOf(Color.parseColor("#E3F2FD"))

    private inner class ChipState(
        val printLabel: String,
        val displayVal: String,
        var x: Float = 0f,
        var y: Float = 0f,
        var sp: Float = 13f,
        var bold: Boolean = false,
        var align: Int = Gravity.START
    )

    fun setActiveSablon(id: Long) { sablonId = id }

    /**
     * PRINT modu icin sablon secici dialog: kullanici hangi sablonu kullanmak
     * istedigini secer (yoksa otomatik), sonra start() cagrilir.
     * Bu sayede her sipariş/musteri icin "Varsayilan" yerine kullanicinin
     * sectigi gercek sablon kullanilir.
     */
    fun startWithSablonPicker(item: Any) {
        val sablonlar = try { dao.getAllSablonlar(userIdProvider()) }
                        catch (e: Exception) { android.util.Log.e("LabelFlow", "getAllSablonlar hata", e); emptyList() }
        android.util.Log.i("LabelFlow", "▶ startWithSablonPicker: bulunan sablon sayisi=" + sablonlar.size)
        when {
            sablonlar.isEmpty() -> { start(item) }
            sablonlar.size == 1 -> {
                setActiveSablon(sablonlar[0].id)
                android.util.Log.i("LabelFlow", "▶ Tek sablon -> otomatik secildi id=" + sablonlar[0].id + " adi=" + sablonlar[0].adi)
                start(item)
            }
            else -> showSablonChooserDialog(sablonlar, item)
        }
    }

    private fun showSablonChooserDialog(sablonlar: List<com.example.metatakip.feature_data.entityModel.EtiketSablon>, item: Any) {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0F4F8"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(makeTitleBar("Hangi Sablonu Kullanmak Istersiniz?"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        sablonlar.forEach { sab ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE); setStroke(dp(2), blue())
                    cornerRadius = dpF(8f)
                }
                elevation = dpF(3f)
                addView(TextView(context).apply {
                    text = sab.adi + (if (sab.varsayilan) "  ★" else "")
                    textSize = 16f; setTypeface(null, Typeface.BOLD)
                    setTextColor(blue())
                })
                addView(TextView(context).apply {
                    text = "  id=" + sab.id
                    textSize = 11f; setTextColor(Color.parseColor("#616161"))
                    setPadding(0, dp(4), 0, 0)
                })
                setOnClickListener {
                    setActiveSablon(sab.id)
                    android.util.Log.i("LabelFlow", "▶ Picker: secildi id=" + sab.id + " adi=" + sab.adi)
                    dialog.dismiss()
                    start(item)
                }
            }
            container.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) })
        }
        val btnIp = makeBtn("Iptal", false)
        btnIp.setOnClickListener { dialog.dismiss() }
        container.addView(btnIp, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        root.addView(container, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    fun start(item: Any) {
        currentItem = item
        ensureSablon()
        if (mode == LabelMode.PRINT) {
            val manual = loadTemplateText(true)
            val comp   = loadTemplateText(false)
            when {
                manual != null && comp != null -> showPrintPickerDialog(item, manual, comp)
                manual != null -> { previewText = manual; onPrint?.invoke(manual) }
                comp   != null -> { previewText = comp;   onPrint?.invoke(comp) }
                else -> showBilesenSecimDialog(item)
            }
        } else {
            showBilesenSecimDialog(item)
        }
    }

    /** PRINT modunda hem manuel hem bileşenli kayit varsa kullaniciya seç dedirt. */
    private fun showPrintPickerDialog(item: Any, manualText: String, compText: String) {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0F4F8"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(makeTitleBar("Etiket Yazdir  Hangi Sablon?"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        fun pickerCard(baslik: String, preview: String, onClick: () -> Unit): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE); setStroke(dp(2), blue())
                    cornerRadius = dpF(8f)
                }
                elevation = dpF(3f)
                addView(TextView(context).apply {
                    text = baslik; textSize = 16f; setTypeface(null, Typeface.BOLD)
                    setTextColor(blue())
                })
                addView(TextView(context).apply {
                    val firstLine = preview.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
                    text = "  " + firstLine.take(60) + (if (firstLine.length > 60) "..." else "")
                    textSize = 12f; setTextColor(Color.parseColor("#616161"))
                    setPadding(0, dp(4), 0, 0)
                })
                setOnClickListener { onClick() }
            }
        }
        container.addView(pickerCard("ELLE YAZILAN", manualText) {
            // Editor'u dolu ac — kullanici Yazdir der, kayit YOK (PRINT modu)
            previewText = manualText
            dialog.dismiss()
            android.util.Log.i("LabelFlow", "▶ Picker: ELLE YAZILAN secildi → editor aciliyor (dolu)")
            showSablonEditorDialog(emptyList(), manualText, item)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(16) })
        container.addView(pickerCard("BILESENLI (Otomatik)", compText) {
            previewText = compText
            dialog.dismiss()
            android.util.Log.i("LabelFlow", "▶ Picker: BILESENLI secildi → editor aciliyor (dolu)")
            showSablonEditorDialog(emptyList(), compText, item)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(16) })

        // Yeniden duzenle butonu
        val btnEdit = makeBtn("Yeniden Duzenle", false)
        btnEdit.setOnClickListener { dialog.dismiss(); showBilesenSecimDialog(item) }
        container.addView(btnEdit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })

        val btnIp = makeBtn("Iptal", false)
        btnIp.setOnClickListener { dialog.dismiss() }
        container.addView(btnIp, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(4) })

        root.addView(container, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIALOG 1 — Boyut Ayarı  (XML tabanlı, dokunmuyoruz)
    // ══════════════════════════════════════════════════════════════════════════
    fun showEtiketAyarDialog() {
        ensureSablon()
        val ayar      = safeLoadSayfaAyar(); currentAyar = ayar
        val sablonAdi = try { dao.getSablonById(sablonId)?.adi ?: "" } catch (_: Exception) { "" }

        val view   = LayoutInflater.from(context).inflate(R.layout.dialog_etiket_ayar, null)
        val etAdi  = view.findViewById<EditText>(R.id.etEtiketAdi)
        val etW    = view.findViewById<EditText>(R.id.etGenislik)
        val etH    = view.findViewById<EditText>(R.id.etYukseklik)
        val etCols = view.findViewById<EditText>(R.id.etSutun)
        val etGap  = view.findViewById<EditText>(R.id.etAralik)
        val btn1   = view.findViewById<Button>(R.id.btn100x80)
        val btn2   = view.findViewById<Button>(R.id.btn60x40)
        val btn3   = view.findViewById<Button>(R.id.btn50x70)
        val btn4   = view.findViewById<Button>(R.id.btn40x30)
        val canvas = view.findViewById<FrameLayout>(R.id.labelCanvas)
        val tvBoy  = view.findViewById<TextView>(R.id.tvCanvasBoyut)
        val colDiv = view.findViewById<View>(R.id.columnDivider)
        val btnIp  = view.findViewById<Button>(R.id.btnAyarIptal)
        val btnNxt = view.findViewById<Button>(R.id.btnAyarKaydet)

        val wDef = if (ayar.widthMm > 0f) ayar.widthMm else 100f
        val hDef = if (ayar.heightMm > 0f) ayar.heightMm else 80f
        etAdi.setText(ayar.labelName.ifBlank { sablonAdi })
        etW.setText(String.format(Locale.US, "%.3f", wDef))
        etH.setText(String.format(Locale.US, "%.3f", hDef))
        etCols.setText(ayar.columns.toString())
        etGap.setText(String.format(Locale.US, "%.3f", ayar.spacingMm))

        fun refreshCanvas() {
            val w = etW.text.toString().replace(',', '.').toFloatOrNull() ?: 100f
            val h = etH.text.toString().replace(',', '.').toFloatOrNull() ?: 80f
            val cols = etCols.text.toString().toIntOrNull() ?: 1
            val maxW = 140f; val maxH = 112f
            val ratio = if (h > 0f) w / h else 1f
            val cW: Float; val cH: Float
            if (ratio > maxW / maxH) { cW = maxW; cH = maxW / ratio }
            else { cH = maxH; cW = maxH * ratio }
            val lp = canvas.layoutParams as FrameLayout.LayoutParams
            lp.width = dp(cW.toInt()); lp.height = dp(cH.toInt()); canvas.layoutParams = lp
            tvBoy.text = w.toInt().toString() + " x " + h.toInt().toString() + " mm"
            colDiv?.visibility = if (cols > 1) View.VISIBLE else View.GONE
        }
        val tw = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = refreshCanvas()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        etW.addTextChangedListener(tw); etH.addTextChangedListener(tw); etCols.addTextChangedListener(tw)
        canvas.post { refreshCanvas() }

        val allBtns = listOfNotNull(btn1, btn2, btn3, btn4)
        fun setPreset(w: Float, h: Float, a: Button?) {
            etW.setText(String.format(Locale.US, "%.3f", w))
            etH.setText(String.format(Locale.US, "%.3f", h))
            allBtns.forEach { b -> b.backgroundTintList = lightCSL(); b.setTextColor(blue()) }
            a?.backgroundTintList = blueCSL(); a?.setTextColor(Color.WHITE)
        }
        btn1?.setOnClickListener { setPreset(100f, 80f, btn1) }
        btn2?.setOnClickListener { setPreset(60f, 40f, btn2) }
        btn3?.setOnClickListener { setPreset(50f, 70f, btn3) }
        btn4?.setOnClickListener { setPreset(40f, 30f, btn4) }
        when {
            wDef == 100f && hDef == 80f -> setPreset(100f, 80f, btn1)
            wDef == 60f  && hDef == 40f -> setPreset(60f,  40f, btn2)
            wDef == 50f  && hDef == 70f -> setPreset(50f,  70f, btn3)
            wDef == 40f  && hDef == 30f -> setPreset(40f,  30f, btn4)
            else -> { btn1?.backgroundTintList = blueCSL(); btn1?.setTextColor(Color.WHITE) }
        }

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        dialog.setContentView(view)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        btnIp?.setOnClickListener { dialog.dismiss() }
        btnNxt?.setOnClickListener {
            try {
                val w       = etW.text.toString().replace(',', '.').toFloatOrNull()   ?: 100f
                val h       = etH.text.toString().replace(',', '.').toFloatOrNull()   ?: 80f
                val cols    = etCols.text.toString().toIntOrNull()                    ?: 1
                val spacing = etGap.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                currentAyar = ayar.copy(labelName = etAdi.text.toString().trim(),
                    widthMm = w, heightMm = h, columns = cols, spacingMm = spacing)
                safeSaveSayfaAyar(sablonId, currentAyar)
                dialog.dismiss()
                showBilesenSecimDialog(currentItem ?: Order())
            } catch (e: Exception) {
                Toast.makeText(context, "Hata: " + e.message, Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIALOG 2 — Bileşen Seçimi   (Dialog + kesin piksel yükseklik)
    // ══════════════════════════════════════════════════════════════════════════
    fun showBilesenSecimDialog(item: Any) {
        try {
            val effectiveItem = currentItem ?: item

            // Bileşenleri yükle
            val allB: MutableList<EtiketManager.EtiketBileseni> = try {
                etiketManager.getEtiketBilesenleri(effectiveItem)
            } catch (e: Exception) {
                Toast.makeText(context, "Bilesen yuklenemedi: " + e.message, Toast.LENGTH_LONG).show()
                mutableListOf()
            }
            try { if (sablonId > 0L) dao.loadBilesenSecimleri(sablonId, allB) } catch (_: Exception) {}

            if (allB.isEmpty()) {
                Toast.makeText(context,
                    "Bilesen bulunamadi! Item: " + effectiveItem.javaClass.simpleName,
                    Toast.LENGTH_LONG).show()
            }

            val grouped     = allB.groupBy { it.kaynak }
            val fieldCounts = mutableMapOf<EtiketManager.EtiketKaynak, Int>()
            grouped.forEach { (k, v) -> fieldCounts[k] = v.size }

            var elIleMetin = ""
            var isElIle    = false

            // ── Yükseklik hesabı (weight yok) ──────────────────────────────
            val screenH   = dm.heightPixels
            val titleH    = dp(52)
            val tabH      = dp(48)
            val btnRowH   = dp(64)
            val statusBar = dp(28)
            val contentH  = screenH - titleH - tabH - btnRowH - statusBar
            val listH     = contentH - dp(52)   // chkAll için yer

            // ── Root ────────────────────────────────────────────────────────
            val root = LinearLayout(context).apply {
                orientation  = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            }

            // Başlık
            root.addView(makeTitleBar("Bilesen Secimi"),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleH))

            // Tab bar
            val tabB = makeTabBtn("BILESEN SEC", true)
            val tabE = makeTabBtn("EL ILE YAZ",  false)
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#F5F7FA"))
                addView(tabB, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(View(context).apply { setBackgroundColor(Color.parseColor("#BDBDBD")) },
                    LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(tabE, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tabH))

            // ── Panel: Bileşen seç ──────────────────────────────────────────
            val chkAll = CheckBox(context).apply {
                text = "Tumunu Sec"; textSize = 14f
                setTextColor(Color.parseColor("#212121"))
                buttonTintList = blueCSL()
            }
            val expList = ExpandableListView(context)

            val panelB = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(4))
                addView(chkAll, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
                addView(expList, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, listH))
            }

            // Adapter bağla
            val adapter = EtiketExpandableAdapter(context, grouped, fieldCounts) {
                chkAll.isChecked = allB.isNotEmpty() && allB.all { it.secili }
                expList.invalidateViews()
            }
            expList.setAdapter(adapter)
            for (i in 0 until adapter.groupCount) expList.expandGroup(i)
            chkAll.isChecked = allB.isNotEmpty() && allB.all { it.secili }
            chkAll.setOnCheckedChangeListener { _, checked ->
                allB.forEach { it.secili = checked }
                adapter.notifyDataSetChanged()
            }

            // ── Panel: El ile yaz ───────────────────────────────────────────
            // DB'den onceki metinleri yukle (admin tekrar acinca dolu gelsin)
            val onceki = try { dao.loadTemplateText(sablonId, true) } catch (e: Exception) {
                android.util.Log.e("LabelFlow", "loadTemplateText(manual) hatasi", e); null
            }
            val oncekiComp = try { dao.loadTemplateText(sablonId, false) } catch (e: Exception) {
                android.util.Log.e("LabelFlow", "loadTemplateText(comp) hatasi", e); null
            }
            android.util.Log.i("LabelFlow", "▶ BilesenSecim YUKLE sablonId=" + sablonId +
                " manual.var=" + (onceki != null) + " manual.len=" + (onceki?.length ?: 0) +
                " comp.var=" + (oncekiComp != null) + " comp.len=" + (oncekiComp?.length ?: 0))
            android.util.Log.i("LabelFlow", "    manual.preview=\"" + (onceki ?: "").take(60) + "\"")
            android.util.Log.i("LabelFlow", "    comp.preview=\""   + (oncekiComp ?: "").take(60) + "\"")
            // Gorunur diagnostik
            Toast.makeText(context,
                "YUKLENEN sablonId=" + sablonId +
                "\nMANUEL: " + (if (onceki.isNullOrEmpty()) "BOS" else "len=" + onceki.length) +
                "\nBILESEN: " + (if (oncekiComp.isNullOrEmpty()) "BOS" else "len=" + oncekiComp.length),
                Toast.LENGTH_LONG).show()
            if (!onceki.isNullOrEmpty()) elIleMetin = onceki
            val etElIle = EditText(context).apply {
                hint = "Buraya yazin...  ne yazarsan etiket o olur"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                gravity   = Gravity.TOP or Gravity.START
                isSingleLine = false
                setBackgroundColor(Color.WHITE)
                textSize  = 14f; setTextColor(Color.parseColor("#212121"))
                typeface  = Typeface.MONOSPACE
                setPadding(dp(16), dp(12), dp(16), dp(12))
                if (!onceki.isNullOrEmpty()) setText(onceki)
            }
            val panelE = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F0F0F0"))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                addView(TextView(context).apply {
                    text = "  Word gibi yaz — ne yazarsan etiket o olur"
                    textSize = 12f; setTextColor(Color.parseColor("#E65100"))
                    setBackgroundColor(Color.parseColor("#FFF8E1"))
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)).apply {
                    bottomMargin = dp(8)
                })
                addView(FrameLayout(context).apply {
                    // Gölge
                    addView(View(context).apply { setBackgroundColor(Color.parseColor("#44000000")) },
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT).apply { topMargin = dp(3); leftMargin = dp(3) })
                    // Beyaz kağıt
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
                        addView(View(context).apply { setBackgroundColor(blue()) },
                            LinearLayout.LayoutParams(dp(40), dp(3)).apply { leftMargin = dp(14); topMargin = dp(10); bottomMargin = dp(3) })
                        addView(View(context).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) },
                            LinearLayout.LayoutParams(dp(80), dp(1)).apply { leftMargin = dp(14); bottomMargin = dp(6) })
                        addView(etElIle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, contentH - dp(80)))
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, contentH - dp(60)))
                visibility = View.GONE
            }

            // Content frame (FrameLayout, kesin yükseklik)
            val contentFrame = FrameLayout(context).apply {
                addView(panelB, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, contentH))
                addView(panelE, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, contentH))
            }
            root.addView(contentFrame, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, contentH))

            // Ayırıcı çizgi
            root.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

            // Buton satırı
            val btnIp = makeBtn("Iptal", false)
            val btnOk = makeBtn("DEVAM  SABLON", true)
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(btnIp, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(btnOk, LinearLayout.LayoutParams(0, dp(44), 2f).apply { leftMargin = dp(8) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, btnRowH))

            // ── Tab mantığı ──────────────────────────────────────────────────
            fun switchB() {
                isElIle = false
                panelB.visibility = View.VISIBLE; panelE.visibility = View.GONE
                tabB.backgroundTintList = blueCSL(); tabB.setTextColor(Color.WHITE)
                tabE.backgroundTintList = lightCSL(); tabE.setTextColor(blue())
            }
            fun switchE() {
                isElIle = true
                panelB.visibility = View.GONE; panelE.visibility = View.VISIBLE
                tabB.backgroundTintList = lightCSL(); tabB.setTextColor(blue())
                tabE.backgroundTintList = blueCSL(); tabE.setTextColor(Color.WHITE)
                etElIle.requestFocus()
            }
            tabB.setOnClickListener { switchB() }
            tabE.setOnClickListener { switchE() }
            etElIle.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { elIleMetin = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            switchB()

            // ── Dialog ───────────────────────────────────────────────────────
            val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
            dialog.setContentView(root)
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            btnIp.setOnClickListener { dialog.dismiss() }
            btnOk.setOnClickListener {
                if (isElIle) elIleMetin = etElIle.text?.toString() ?: ""
                else try { if (sablonId > 0L) dao.saveBilesenler(sablonId, allB) } catch (_: Exception) {}
                android.util.Log.i("LabelFlow", "▶ btnOk: isElIle=" + isElIle + " elIleMetin.len=" + elIleMetin.length + " sablonId=" + sablonId)
                dialog.dismiss()
                val secili = if (isElIle) listOf() else allB.filter { it.secili }
                android.util.Log.i("LabelFlow", "▶ showSablonEditorDialog cagriliyor: secili.size=" + secili.size + " elIleMetin=\"" + elIleMetin.take(40) + "\"")
                showSablonEditorDialog(secili, elIleMetin, effectiveItem)
            }
            dialog.show()

        } catch (e: Exception) {
            Toast.makeText(context,
                "SecimDialog HATA: " + e.javaClass.simpleName + " - " + e.message,
                Toast.LENGTH_LONG).show()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIALOG 3 — Şablon Canvas (Word sayfası, Drag & Drop, Format toolbar)
    // ══════════════════════════════════════════════════════════════════════════
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showSablonEditorDialog(
        seciliBilesenler: List<EtiketManager.EtiketBileseni>,
        elIleMetin: String,
        item: Any
    ) {
        try {
            val ayar = currentAyar
            val wMm  = if (ayar.widthMm  > 0f) ayar.widthMm  else 100f
            val hMm  = if (ayar.heightMm > 0f) ayar.heightMm else 80f
            val effectiveItem = currentItem ?: item

            val screenW = dm.widthPixels
            val canvasW = (screenW * 0.84f).toInt()
            val canvasH = (canvasW * hMm / wMm).toInt()

            val screenH   = dm.heightPixels
            val titleH    = dp(52)
            val infoH     = dp(28)
            val toolbarH  = dp(44)
            val btnRowH   = dp(64)
            val canvasAreaH = screenH - titleH - infoH - toolbarH - btnRowH - dp(28)

            // Chip state listesi
            val states = mutableListOf<ChipState>()
            val canvasMargin = dp(8)
            if (elIleMetin.isNotBlank()) {
                // Çok satır → her satır ayrı chip (formatlanabilir / sürüklenebilir)
                elIleMetin.split("\n").filter { it.isNotBlank() }.forEachIndexed { i, line ->
                    states.add(ChipState(printLabel = line, displayVal = line,
                        x = canvasMargin.toFloat(),
                        y = (canvasMargin + i * dp(40)).toFloat(),
                        sp = 14f))
                }
            } else {
                seciliBilesenler.forEachIndexed { i, b ->
                    val deger = try {
                        b.valueProvider(b.source).trim().ifEmpty { b.baslik }
                    } catch (_: Exception) { b.baslik }
                    states.add(ChipState(
                        printLabel = b.baslik + ": " + deger,
                        displayVal = deger.ifEmpty { b.baslik },
                        x = canvasMargin.toFloat(),
                        y = (canvasMargin + i * dp(44)).toFloat(),
                        sp = 13f))
                }
            }

            // Root
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F0F4F8"))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val titleBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; setBackgroundColor(blue())
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(16), 0)
            }
            val btnGeri = Button(context).apply {
                text = "< GERI"; textSize = 12f
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
                setTextColor(Color.WHITE); setPadding(dp(6), 0, dp(6), 0)
            }
            titleBar.addView(btnGeri, LinearLayout.LayoutParams(dp(72), dp(40)).apply { rightMargin = dp(8) })
            titleBar.addView(TextView(context).apply {
                text = "Sablon  " + wMm.toInt() + " x " + hMm.toInt() + " mm"
                textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(titleBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleH))

            // Bilgi satırı
            val tvSel = TextView(context).apply {
                text = "  Bilesen secin (dokun)  sonra bicim ayarla"
                textSize = 11f; setTextColor(Color.parseColor("#5D4037"))
                setBackgroundColor(Color.parseColor("#FFF8E1"))
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            root.addView(tvSel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, infoH))

            // Format toolbar (TUMU + B + hizala + boyut)
            val btnTumu   = makeToolBtn("TUMU", false)
            val btnBold   = makeToolBtn("B",    true)
            val btnAlL    = makeToolBtn("Sol",  false)
            val btnAlC    = makeToolBtn("Orta", false)
            val btnAlR    = makeToolBtn("Sag",  false)
            val btnVUp    = makeToolBtn("Yuk",  false)
            val btnVMid   = makeToolBtn("Ort",  false)
            val btnVDn    = makeToolBtn("Asg",  false)
            val btnSzUp   = makeToolBtn("A+",   false)
            val btnSzDn   = makeToolBtn("A-",   false)
            val toolbarInner = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#1565C0"))
                setPadding(dp(4), dp(4), dp(4), dp(4)); gravity = Gravity.CENTER_VERTICAL
                addView(btnTumu,  LinearLayout.LayoutParams(dp(54), dp(36)).apply { rightMargin = dp(8) })
                addView(btnBold,  LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(6) })
                addView(btnAlL,   LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(2) })
                addView(btnAlC,   LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(2) })
                addView(btnAlR,   LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(6) })
                addView(btnVUp,   LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(2) })
                addView(btnVMid,  LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(2) })
                addView(btnVDn,   LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(6) })
                addView(btnSzUp,  LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(2) })
                addView(btnSzDn,  LinearLayout.LayoutParams(dp(40), dp(36)))
            }
            val toolbar = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(Color.parseColor("#1565C0"))
                addView(toolbarInner, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarH))

            // Canvas kağıdı
            val canvas = FrameLayout(context).apply {
                val bg = GradientDrawable()
                bg.setColor(Color.WHITE); bg.setStroke(dp(2), blue())
                background = bg; elevation = dpF(4f)
                outlineProvider = android.view.ViewOutlineProvider.BOUNDS; clipToOutline = true
            }
            canvas.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E3F2FD")) },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))
            // Kenar boşluğu (safe area) — kesik çizgi göstergesi
            canvas.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.parseColor("#90CAF9"), dpF(4f), dpF(3f))
                }
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            canvas.addView(TextView(context).apply {
                text = wMm.toInt().toString() + " x " + hMm.toInt().toString() + " mm"
                textSize = 9f; setTextColor(Color.parseColor("#BDBDBD"))
                setPadding(0, 0, dp(6), dp(4))
                gravity = Gravity.END or Gravity.BOTTOM
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val paperWrap = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#D0D7E2"))
                setPadding(dp(20), dp(16), dp(20), dp(16))
                addView(canvas, FrameLayout.LayoutParams(canvasW, canvasH).apply {
                    gravity = Gravity.CENTER_HORIZONTAL })
            }
            val scroll = ScrollView(context).apply { addView(paperWrap) }
            root.addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, canvasAreaH))

            // Chip view'ları (ÇOKLU SEÇİM)
            val chipViews        = mutableListOf<TextView>()
            val selectedIndices  = mutableSetOf<Int>()
            val screenPos        = IntArray(2)
            fun isSel(i: Int) = selectedIndices.contains(i)
            fun anySel()      = selectedIndices.isNotEmpty()

            fun applyChipStyle(tv: TextView, st: ChipState, sel: Boolean) {
                tv.text = st.displayVal
                tv.textSize = st.sp
                tv.setTypeface(null, if (st.bold) Typeface.BOLD else Typeface.NORMAL)
                tv.gravity = st.align or Gravity.CENTER_VERTICAL
                val bg = GradientDrawable()
                bg.setColor(if (sel) Color.parseColor("#E3F2FD") else Color.WHITE)
                bg.setStroke(dp(if (sel) 2 else 1), if (sel) blue() else Color.parseColor("#BDBDBD"))
                tv.background = bg; tv.setTextColor(Color.parseColor("#212121"))
                tv.setPadding(dp(8), dp(6), dp(8), dp(6))
                tv.elevation = dpF(if (sel) 6f else 2f)
            }

            fun refreshToolbarStyle() {
                if (!anySel()) {
                    tvSel.text = "  Bilesen secin (dokun) yada TUMU"
                    listOf(btnBold, btnAlL, btnAlC, btnAlR).forEach {
                        it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#42A5F5"))
                    }
                    return
                }
                tvSel.text = "  " + selectedIndices.size + " bilesen secili"
                val first = states[selectedIndices.first()]
                btnBold.backgroundTintList = if (first.bold) blueCSL()
                    else ColorStateList.valueOf(Color.parseColor("#42A5F5"))
                listOf(btnAlL to Gravity.START, btnAlC to Gravity.CENTER_HORIZONTAL, btnAlR to Gravity.END)
                    .forEach { (btn, g) ->
                        btn.backgroundTintList = if (first.align == g) blueCSL()
                        else ColorStateList.valueOf(Color.parseColor("#42A5F5"))
                    }
            }

            fun redrawAll() {
                chipViews.forEachIndexed { i, tv -> applyChipStyle(tv, states[i], isSel(i)) }
                refreshToolbarStyle()
            }

            fun selectChip(idx: Int) {
                selectedIndices.clear()
                if (idx >= 0) selectedIndices.add(idx)
                redrawAll()
            }
            fun toggleSelectAll() {
                if (selectedIndices.size == states.size) selectedIndices.clear()
                else { selectedIndices.clear(); states.indices.forEach { selectedIndices.add(it) } }
                redrawAll()
            }

            states.forEachIndexed { i, st ->
                val tv = TextView(context).apply { maxLines = 2; setSingleLine(false); minWidth = dp(60) }
                applyChipStyle(tv, st, false); tv.x = st.x; tv.y = st.y
                tv.setOnLongClickListener {
                    val idx = chipViews.indexOf(tv)
                    if (idx >= 0) {
                        canvas.removeView(tv)
                        chipViews.removeAt(idx); states.removeAt(idx)
                        // index'leri yeniden hizala
                        val newSel = mutableSetOf<Int>()
                        selectedIndices.forEach { s ->
                            when { s == idx -> {} ; s > idx -> newSel.add(s - 1) ; else -> newSel.add(s) }
                        }
                        selectedIndices.clear(); selectedIndices.addAll(newSel)
                        redrawAll()
                        Toast.makeText(context, "Bilesen silindi", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                var downRawX = 0f; var downRawY = 0f; var moved = false
                tv.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            canvas.getLocationOnScreen(screenPos)
                            downRawX = event.rawX; downRawY = event.rawY; moved = false
                            v.elevation = dpF(10f); true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downRawX; val dy = event.rawY - downRawY
                            if (moved || Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) {
                                moved = true
                                val relX = event.rawX - screenPos[0]
                                val relY = event.rawY - screenPos[1]
                                // canvasMargin'i koruyarak sürükle
                                val minX = canvasMargin.toFloat()
                                val maxX = (canvasW - v.width - canvasMargin).toFloat().coerceAtLeast(minX)
                                val minY = canvasMargin.toFloat()
                                val maxY = (canvasH - v.height - canvasMargin).toFloat().coerceAtLeast(minY)
                                val newX = (relX - v.width / 2f).coerceIn(minX, maxX)
                                val newY = (relY - v.height / 2f).coerceIn(minY, maxY)
                                v.x = newX; v.y = newY; st.x = newX; st.y = newY
                            }; true
                        }
                        MotionEvent.ACTION_UP -> {
                            val idxNow = chipViews.indexOf(v)
                            v.elevation = dpF(if (isSel(idxNow)) 6f else 2f)
                            if (!moved) selectChip(idxNow); true
                        }
                        else -> false
                    }
                }
                canvas.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                chipViews.add(tv)
            }
            canvas.setOnClickListener { if (anySel()) selectChip(-1) }

            // Toolbar olayları (tüm seçili chip'lere uygulanır)
            btnTumu.setOnClickListener { toggleSelectAll() }
            btnBold.setOnClickListener {
                if (!anySel()) { Toast.makeText(context, "Once bilesen sec yada TUMU", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val newBold = !states[selectedIndices.first()].bold
                selectedIndices.forEach { i ->
                    states[i].bold = newBold
                    applyChipStyle(chipViews[i], states[i], true)
                }
                refreshToolbarStyle()
            }
            fun setAlignAll(g: Int) {
                if (!anySel()) { Toast.makeText(context, "Once bilesen sec yada TUMU", Toast.LENGTH_SHORT).show(); return }
                selectedIndices.forEach { i ->
                    states[i].align = g
                    applyChipStyle(chipViews[i], states[i], true)
                    // Eğer ortala/sağ ise chip'i canvas içinde kaydır
                    val tv = chipViews[i]
                    tv.post {
                        when (g) {
                            Gravity.CENTER_HORIZONTAL -> {
                                val nx = ((canvasW - tv.width) / 2f).coerceAtLeast(canvasMargin.toFloat())
                                tv.x = nx; states[i].x = nx
                            }
                            Gravity.END -> {
                                val nx = (canvasW - tv.width - canvasMargin).toFloat().coerceAtLeast(canvasMargin.toFloat())
                                tv.x = nx; states[i].x = nx
                            }
                            Gravity.START -> {
                                tv.x = canvasMargin.toFloat(); states[i].x = canvasMargin.toFloat()
                            }
                        }
                    }
                }
                refreshToolbarStyle()
            }
            btnAlL.setOnClickListener { setAlignAll(Gravity.START) }
            btnAlC.setOnClickListener { setAlignAll(Gravity.CENTER_HORIZONTAL) }
            btnAlR.setOnClickListener { setAlignAll(Gravity.END) }
            // Dikey hizalama (Yuk / Ort / Asg)
            fun setVAlignAll(mode: Int) {
                if (!anySel()) { Toast.makeText(context, "Once bilesen sec yada TUMU", Toast.LENGTH_SHORT).show(); return }
                val sortedSel = selectedIndices.sorted()
                sortedSel.forEachIndexed { order, i ->
                    val tv = chipViews[i]
                    tv.post {
                        val ny = when (mode) {
                            0 -> (canvasMargin + order * (tv.height + dp(2))).toFloat()  // Yukari (sırayla istif)
                            1 -> ((canvasH - tv.height) / 2f).coerceAtLeast(canvasMargin.toFloat())  // Orta
                            else -> (canvasH - tv.height - canvasMargin - (sortedSel.size - 1 - order) * (tv.height + dp(2))).toFloat().coerceAtLeast(canvasMargin.toFloat())  // Asagi
                        }
                        tv.y = ny; states[i].y = ny
                    }
                }
            }
            btnVUp.setOnClickListener  { setVAlignAll(0) }
            btnVMid.setOnClickListener { setVAlignAll(1) }
            btnVDn.setOnClickListener  { setVAlignAll(2) }
            btnSzUp.setOnClickListener {
                if (!anySel()) { Toast.makeText(context, "Once bilesen sec yada TUMU", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                selectedIndices.forEach { i ->
                    states[i].sp = (states[i].sp + 1f).coerceAtMost(28f)
                    applyChipStyle(chipViews[i], states[i], true)
                }
            }
            btnSzDn.setOnClickListener {
                if (!anySel()) { Toast.makeText(context, "Once bilesen sec yada TUMU", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                selectedIndices.forEach { i ->
                    states[i].sp = (states[i].sp - 1f).coerceAtLeast(8f)
                    applyChipStyle(chipViews[i], states[i], true)
                }
            }
            // Bilgi etiketini başta düzelt
            refreshToolbarStyle()

            // Alt butonlar
            val btnIp   = makeBtn("Iptal", false)
            val btnKayt = makeBtn(if (mode == LabelMode.PRINT) "YAZDIR" else "KAYDET", true)
            root.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(btnIp,   LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(btnKayt, LinearLayout.LayoutParams(0, dp(44), 2f).apply { leftMargin = dp(8) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, btnRowH))

            val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
            dialog.setContentView(root)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            btnIp.setOnClickListener { dialog.dismiss() }
            btnKayt.setOnClickListener {
                android.util.Log.i("LabelFlow", "═══════════════════════════════════════")
                android.util.Log.i("LabelFlow", "▶▶▶ btnKayt CLICK basladi")
                android.util.Log.i("LabelFlow", "    states.size=" + states.size)
                android.util.Log.i("LabelFlow", "    elIleMetin gelen=\"" + elIleMetin.take(60) + "\" len=" + elIleMetin.length)
                android.util.Log.i("LabelFlow", "    sablonId=" + sablonId + " mode=" + mode)
                val sorted = states.sortedBy { it.y }
                previewText = sorted.joinToString("\n") { it.printLabel }
                val isManual = elIleMetin.isNotBlank()
                android.util.Log.i("LabelFlow", "    isManual=" + isManual + " previewText.len=" + previewText.length)
                android.util.Log.i("LabelFlow", "    previewText icerik=\"" + previewText.take(80) + "\"")
                if (previewText.isEmpty()) {
                    android.util.Log.w("LabelFlow", "    ⚠ previewText BOS — islem iptal!")
                    Toast.makeText(context, "UYARI: Yazdirilacak metin bos!", Toast.LENGTH_LONG).show()
                } else if (mode == LabelMode.PRINT) {
                    // PRINT MODU (admin disi liste): KAYIT YOK, sadece yazdir
                    android.util.Log.i("LabelFlow", "    → PRINT modu: kayit ATLANDI, sadece yazdiriliyor")
                } else {
                    // CONFIG MODU (admin sablon duzenleme): KAYIT yapilir
                    android.util.Log.i("LabelFlow", "    → CONFIG modu: saveTemplateText cagriliyor (isManual=" + isManual + ")")
                    saveTemplateText(isManual, previewText)
                }
                dialog.dismiss()
                val tip = if (isManual) "Elle yazilan" else "Bilesenli"
                if (mode == LabelMode.PRINT) {
                    if (previewText.isNotEmpty()) onPrint?.invoke(previewText)
                } else if (previewText.isNotEmpty()) {
                    Toast.makeText(context, "Sablon kaydedildi (" + tip + ") sablonId=" + sablonId, Toast.LENGTH_LONG).show()
                }
                android.util.Log.i("LabelFlow", "▶▶▶ btnKayt CLICK bitti")
                android.util.Log.i("LabelFlow", "═══════════════════════════════════════")
            }
            btnGeri.setOnClickListener {
                dialog.dismiss()
                showBilesenSecimDialog(effectiveItem)
            }
            dialog.show()

        } catch (e: Exception) {
            Toast.makeText(context,
                "SablonEditor HATA: " + e.javaClass.simpleName + " - " + e.message,
                Toast.LENGTH_LONG).show()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIALOG 4 — Önizleme / Yazdır
    // ══════════════════════════════════════════════════════════════════════════
    private fun showOnizlemeDialog() {
        try {
            val view       = LayoutInflater.from(context).inflate(R.layout.dialog_etiket_onizleme, null)
            val tvPreview  = view.findViewById<TextView>(R.id.tvPreview)
            val btnAction  = view.findViewById<Button>(R.id.btnYazdir)
            val btnDuzenle = view.findViewById<Button>(R.id.btnDuzenle)
            val btnKapat   = view.findViewById<Button>(R.id.btnKapat)
            tvPreview.text = previewText; tvPreview.typeface = Typeface.MONOSPACE
            val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
            dialog.setContentView(view)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            btnDuzenle?.setOnClickListener { dialog.dismiss(); currentItem?.let { showBilesenSecimDialog(it) } }
            btnAction?.setOnClickListener  { dialog.dismiss(); if (mode == LabelMode.PRINT) onPrint?.invoke(previewText) }
            btnKapat?.setOnClickListener   { dialog.dismiss() }
            dialog.show()
        } catch (_: Exception) {
            if (mode == LabelMode.PRINT && previewText.isNotEmpty()) onPrint?.invoke(previewText)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI yardımcılar
    // ══════════════════════════════════════════════════════════════════════════
    private fun makeTitleBar(title: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; setBackgroundColor(blue())
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(16), 0)
        addView(TextView(context).apply {
            text = title; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun makeTabBtn(label: String, active: Boolean) = Button(context).apply {
        text = label; textSize = 12f; setPadding(0, 0, 0, 0)
        backgroundTintList = if (active) blueCSL() else lightCSL()
        setTextColor(if (active) Color.WHITE else blue())
    }

    private fun makeToolBtn(label: String, bold: Boolean) = Button(context).apply {
        text = label; textSize = if (bold) 14f else 12f
        if (bold) setTypeface(null, Typeface.BOLD)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#42A5F5"))
        setTextColor(Color.WHITE); setPadding(dp(2), 0, dp(2), 0)
    }

    private fun makeBtn(label: String, primary: Boolean) = Button(context).apply {
        text = label
        if (primary) {
            backgroundTintList = blueCSL(); setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        } else {
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#666666"))
        }
    }

    private fun safeLoadSayfaAyar(): EtiketSayfaAyar =
        try { dao.loadSayfaAyar(sablonId) ?: EtiketSayfaAyar() } catch (_: Exception) { EtiketSayfaAyar() }

    private fun safeSaveSayfaAyar(id: Long, ayar: EtiketSayfaAyar) {
        try { dao.saveSayfaAyar(id, ayar) } catch (_: Exception) {}
    }

    // ── Şablon metni kaydet/yukle — DOGRUDAN VERITABANI + change_log ile buluta sync ──
    private fun saveTemplateText(isManual: Boolean, text: String) {
        if (sablonId <= 0L) {
            android.util.Log.e("LabelFlow", "saveTemplateText IPTAL — sablonId=0!")
            Toast.makeText(context, "HATA: Sablon ID gecersiz, kayit yapilamiyor", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val rows = dao.saveTemplateText(sablonId, isManual, text)
            android.util.Log.i("LabelFlow", "saveTemplateText OK sablonId=" + sablonId +
                " isManual=" + isManual + " len=" + text.length + " rows=" + rows)
            if (rows == 0) {
                Toast.makeText(context, "UYARI: 0 satir etkilendi (sablonId=" + sablonId + ")",
                    Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("LabelFlow", "saveTemplateText DB hatasi", e)
            Toast.makeText(context, "DB HATASI: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
    private fun loadTemplateText(isManual: Boolean): String? {
        return try { dao.loadTemplateText(sablonId, isManual) }
        catch (e: Exception) { android.util.Log.e("LabelFlow", "loadTemplateText DB hatasi", e); null }
    }

    private fun ensureSablon() {
        if (sablonId > 0L) return
        try {
            val userId = userIdProvider()
            val def = dao.getAllSablonlar(userId).firstOrNull { it.varsayilan }
            sablonId = def?.id ?: dao.createSablon(userId = userId, adi = "Varsayilan Etiket", varsayilan = true)
            android.util.Log.i("LabelFlow", "ensureSablon → sablonId=" + sablonId + " userId=" + userId)
        } catch (e: Exception) {
            android.util.Log.e("LabelFlow", "ensureSablon HATA — fallback yeni sablon olusturuluyor", e)
            try {
                sablonId = dao.createSablon(userId = 0, adi = "Acil Sablon", varsayilan = true)
            } catch (e2: Exception) {
                android.util.Log.e("LabelFlow", "createSablon da basarisiz", e2)
                Toast.makeText(context, "Sablon olusturulamadi: " + e2.message, Toast.LENGTH_LONG).show()
                sablonId = 0L
            }
        }
    }
}
