package com.example.metatakip.feature_data.ui.mapper

import android.graphics.Color
import com.example.metatakip.feature_data.entityModel.*
import com.example.metatakip.feature_data.ui.GenericListRowAdapterUiModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenericListUiMapper {
    companion object {
        /** Provider'lar tarafindan doldurulur; EtiketSablon kartinda firma adi gostermek icin kullanilir. */
        @JvmStatic var firmaNameById: Map<Long, String> = emptyMap()
        @JvmStatic var firmaNameByUuid: Map<String, String> = emptyMap()

        @JvmStatic
        fun ensureFirmaLookup(context: android.content.Context) {
            if (firmaNameById.isNotEmpty()) return
            try {
                val klass = Class.forName("com.example.metatakip.feature.firma.data.MetaTakipFirmaDaoImpl")
                val instance = klass.getConstructor(android.content.Context::class.java).newInstance(context)
                @Suppress("UNCHECKED_CAST")
                val firmas = klass.getMethod("getAllFirmalar").invoke(instance) as List<Any>
                val byId = HashMap<Long, String>()
                val byUuid = HashMap<String, String>()
                for (f in firmas) {
                    val id = (f.javaClass.getMethod("getId").invoke(f) as? Long) ?: 0L
                    val adi = try { f.javaClass.getMethod("getFirmaAdi").invoke(f) as? String ?: "" } catch (_: Exception) { "" }
                    val uuid = try { f.javaClass.getMethod("getUuid").invoke(f) as? String ?: "" } catch (_: Exception) { "" }
                    if (id > 0L && adi.isNotBlank()) byId[id] = adi
                    if (uuid.isNotBlank() && adi.isNotBlank()) byUuid[uuid] = adi
                }
                firmaNameById = byId
                firmaNameByUuid = byUuid
                android.util.Log.i("GenericListUiMapper", "🔧 ensureFirmaLookup: byId=${byId.size} byUuid=${byUuid.size}")
            } catch (e: Exception) {
                android.util.Log.w("GenericListUiMapper", "ensureFirmaLookup hata: ${e.message}")
            }
        }

        /** Liste yenilenince firma cache'i temizle (yeni firma eklendiyse). */
        @JvmStatic
        fun invalidateFirmaLookup() {
            firmaNameById = emptyMap()
            firmaNameByUuid = emptyMap()
        }

        @JvmStatic
        fun kod6(uuid: String?, fallback: Any? = null): String {
            val u = uuid?.trim().orEmpty()
            return if (u.length >= 6) u.takeLast(6).uppercase()
                   else if (u.isNotBlank()) u.uppercase()
                   else fallback?.toString() ?: "------"
        }

    }
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val dateShortFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun map(item: Any): GenericListRowAdapterUiModel {
        return when (item) {

// =========================
// 🧾 SİPARİŞ (Order)
// =========================
            is Order -> {
                val sNo = kod6(item.uuid, item.id)

                // Ürün durumu: toplamAdet == 0 ise uyarı göster
                val urunBilgisi = when {
                    item.toplamAdet == 0 -> "⚠️ Ürün Eklenmemiş"
                    item.urunTipi.isNotBlank() -> item.urunTipi
                    else -> "Ürün Belirtilmemiş"
                }

                // Adres: müşteri adresinden gelir, yoksa "Adres Yok"
                val adresBilgisi = item.adres.trim().ifBlank { "Adres Yok" }

                // Oluşturulma tarihi formatla
                val olusturulmaTarihi = if (item.createdAt > 0L) {
                    dateShortFormat.format(Date(item.createdAt))
                } else {
                    "---"
                }

                // Adet gösterimi
                val adetGoster = if (item.toplamAdet == 0) "⚠️ 0" else item.toplamAdet.toString()

                // m2 gösterimi
                val m2Goster = if (item.metrekare > 0) {
                    String.format(Locale.US, "%.1f", item.metrekare)
                } else "---"

                // Birim fiyat (ucret/m2)
                val birimFiyat = if (item.metrekare > 0 && item.ucret > 0) {
                    String.format(Locale.US, "%.1f ₺", item.ucret / item.metrekare)
                } else "---"

                // Toplam tutar
                val tutarGoster = if (item.ucret > 0) {
                    String.format(Locale.US, "%.1f ₺", item.ucret)
                } else "---"

                GenericListRowAdapterUiModel(
                    id = item.id,
                    title = "(Sipariş Kodu: $sNo) ${item.musteriAdi.ifBlank { "İsimsiz" }}",
                    subtitle = urunBilgisi,
                    badgeText = item.durum,
                    badgeColor = when (item.durum) {
                        "Teslim Edildi" -> Color.parseColor("#43A047")
                        "Hazırlanıyor" -> Color.parseColor("#FB8C00")
                        "Yeni Sipariş" -> Color.parseColor("#1565C0")
                        "Tekrar İşleme Alındı" -> Color.parseColor("#6A1B9A")
                        else -> Color.parseColor("#1565C0")
                    },
                    payload = item,
                    extraFields = mapOf(
                        "siparisNo" to sNo,
                        "adet" to adetGoster,
                        "m2" to m2Goster,
                        "tutar" to tutarGoster,
                        "odeme" to birimFiyat,
                        "telefon" to (item.musteriTelefon.ifBlank { "---" }),
                        "firma" to (item.firmaAdi.ifBlank { firmaNameById[item.firmaId] ?: firmaNameByUuid[item.firmaUuid] ?: "Firma yok" }),
                        "adres" to adresBilgisi,
                        "not" to (item.notlar.ifBlank { "---" }),
                        "tarihler" to "📅 Oluşturuldu: $olusturulmaTarihi  📦 Teslim: ${item.teslimTarihi.ifBlank { "---" }}",
                        // Paylaşım / kopyalama için hazır metin
                        "paylasimMetni" to buildShareText(
                            siparisNo = sNo,
                            musteriAdi = item.musteriAdi,
                            telefon = item.musteriTelefon,
                            urunBilgisi = urunBilgisi,
                            adres = adresBilgisi,
                            durum = item.durum,
                            adet = item.toplamAdet.toString(),
                            tutar = tutarGoster,
                            teslimTarihi = item.teslimTarihi,
                            notlar = item.notlar
                        )
                    ),
                    photoPath = item.photoPath
                )
            }

// =========================
// 📦 ÜRÜN (Urun)
// =========================
            is Urun -> {
                val uSno = if (item.siparisUuid.isNotBlank()) item.siparisUuid.takeLast(4).uppercase() else "---"
                val urunKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                    id = item.id,
                    title = "(Ürün Kodu: $urunKod) ${item.ad.ifBlank { "Parça Ürün" }}",
                    subtitle = "${item.urunTipi} Parçası",
                    badgeText = "Ürün",
                    badgeColor = Color.parseColor("#7B1FA2"), // Mor
                    payload = item,
                    extraFields = mapOf(
                        "siparisNo" to uSno,
                        "adet" to item.adet.toString(),
                        "m2" to String.format(Locale.US, "%.1f", item.m2),
                        "tutar" to String.format(Locale.US, "%.1f ₺", item.tutar),
                        "odeme" to "---",
                        "telefon" to "---",
                        "firma" to "ÜRÜN",
                        "adres" to "Siparişe Bağlı",
                        "not" to "Birim Fiyat: ${item.fiyat} ₺",
                        "tarihler" to "Güncelleme: ${dateFormat.format(Date(item.updatedAt))}"
                    )
                )
            }

// =========================
// 👤 MÜŞTERİ (Customer)
// =========================
            is Customer -> {
                val uniqueId = kod6(item.uuid, item.id)
                GenericListRowAdapterUiModel(
                    id = item.id,
                    title = "(Müşteri Kodu: $uniqueId) ${item.adSoyad.ifBlank { "İsimsiz" }}",
                    subtitle = "Kayıtlı Müşteri",
                    badgeText = "Müşteri",
                    badgeColor = Color.parseColor("#1565C0"),
                    payload = item,
                    extraFields = mapOf(
                        "siparisNo" to uniqueId,
                        "adet" to "---",
                        "m2" to "---",
                        "tutar" to "---",
                        "odeme" to "---",
                        "adres" to (item.adres ?: item.bolge ?: "Adres Yok"),
                        "telefon" to (item.ceptel ?: "---"),
                        "firma" to (item.firmaAdi?.takeIf { it.isNotBlank() } ?: firmaNameById[item.firmaid ?: 0L] ?: "Firma yok"),
                        "not" to (item.musteriNotu ?: "---"),
                        "tarihler" to "Son İşlem: ${dateFormat.format(Date(item.updatedAt))}"
                    ),
                    photoPath = item.photoPath
                )
            }

            // =========================
            // 🏢 FİRMA
            // =========================
            is Firma -> {
                val fKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                id = item.id,
                title = "(Firma Kodu: $fKod) ${item.firmaAdi ?: "İsimsiz Firma"}",
                subtitle = listOfNotNull(
                    item.telefon?.takeIf { it.isNotBlank() },
                    item.vergiNo?.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { null },
                description = "ID: ${item.id}",
                payload = item,
                extraFields = mapOf(
                    "kod" to fKod,
                    "telefon" to (item.telefon ?: ""),
                    "vergiNo" to (item.vergiNo ?: "")
                )
            )}

            // =========================
            // 👤 PERSONEL
            // =========================
            is Personel -> {
                val pKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                id = item.id,
                title = "(Personel Kodu: $pKod) ${item.adSoyad}",
                subtitle = item.unvan,
                description = "ID: ${item.id}",
                payload = item
            )}

            // =========================
            // 🏷️ ÜNVAN
            // =========================
            is Unvan -> {
                val unKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                id = item.id,
                title = "(Ünvan Kodu: $unKod) ${item.ad}",
                subtitle = item.aciklama,
                description = "ID: ${item.id}",
                payload = item
            )}

            // =========================
            // 🏷️ ETİKET ŞABLONU
            // =========================
            is EtiketSablon -> {
                val firmaAdi = item.firmaAdi.takeIf { it.isNotBlank() }
                    ?: firmaNameById[item.firmaId]
                    ?: firmaNameByUuid[item.firmaUuid]
                val firmaText = when {
                    !firmaAdi.isNullOrBlank() -> "🏢 $firmaAdi"
                    item.firmaId > 0L -> "🏢 Firma #${item.firmaId}"
                    item.firmaUuid.isNotBlank() -> "🏢 ${item.firmaUuid.take(8)}…"
                    else -> "🏢 Firma yok"
                }
                val typeText = if (item.varsayilan) "⭐ Varsayılan Etiket" else "Etiket Şablonu"
                val etKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                    id = item.id,
                    title = "(Etiket Kodu: $etKod) ${item.adi}",
                    subtitle = "$typeText  •  $firmaText",
                    badgeText = if (item.varsayilan) "Varsayılan" else null,
                    badgeColor = if (item.varsayilan) Color.parseColor("#2E7D32") else null,
                    description = "ID: ${item.id}" +
                        (if (item.firmaId > 0L) "  •  Firma ID: ${item.firmaId}" else "") +
                        (if (item.firmaUuid.isNotBlank()) "  •  UUID: ${item.firmaUuid.take(8)}…" else ""),
                    extraFields = mapOf(
                        "firma" to (firmaAdi ?: if (item.firmaId > 0L) "Firma #${item.firmaId}" else "---")
                    ),
                    payload = item
                )
            }

            // =========================
            // 💬 MESAJ ŞABLONU
            // =========================
            is MesajSablon -> {
                val previewText = listOfNotNull(
                    item.musteriOlustuMesaj,
                    item.musteriGuncellendiMesaj,
                    item.siparisOlustuMesaj,
                    item.siparisUrunEklendiMesaj,
                    item.smsOnayMesaj,
                    item.whatsappOnayMesaj
                ).firstOrNull()?.take(80) ?: "Mesaj içeriği yok"

                val msKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                    id = item.id,
                    title = "(Mesaj Kodu: $msKod) ${item.baslik}",
                    subtitle = previewText,
                    badgeText = if (item.varsayilan) "Varsayılan" else null,
                    badgeColor = if (item.varsayilan) Color.parseColor("#2E7D32") else null,
                    description = "ID: ${item.id}",
                    payload = item
                )
            }

            // =========================
            // 🏷️ ÜRÜN TİPİ
            // =========================
            is UrunTipi -> mapUrunTipi(item)

            // =========================
            // 📞 ÇAĞRI KAYDI
            // =========================
            is CallRecord -> {
                val crKod = kod6(try { item.javaClass.getMethod("getUuid").invoke(item) as? String } catch (_: Exception) { null }, item.id)
                GenericListRowAdapterUiModel(
                id = item.id,
                title = "(Çağrı Kodu: $crKod) ${item.musteriAdi ?: item.musteriTelefonu}",
                subtitle = "${item.musteriTelefonu} • ${item.cagriTuru}",
                badgeText = item.cagriTuru,
                badgeColor = when(item.cagriTuru.uppercase()) {
                    "GELEN" -> Color.parseColor("#43A047")
                    "CEVAPSIZ" -> Color.parseColor("#D32F2F")
                    else -> Color.parseColor("#1565C0")
                },
                payload = item,
                extraFields = mapOf(
                    "telefon" to item.musteriTelefonu,
                    "firma" to item.cihazFirmaAdi,
                    "not" to (item.merkezHataMesaji ?: "Not Yok"),
                    "tarihler" to (item.getFormattedCallTime() ?: "---"),
                    "siparisNo" to item.id.toString(),
                    "adet" to if (item.merkezeIletildiMi) "OK" else "WAIT",
                    "m2" to "---",
                    "tutar" to "---",
                    "odeme" to "---",
                    "adres" to item.arananFirmaAdi
                )
            )}

            // =========================
            // ❓ FALLBACK
            // =========================
            else -> GenericListRowAdapterUiModel(
                id = -1,
                title = "Bilinmeyen Kayıt",
                subtitle = null,
                payload = item
            )
        }
    }

    // =========================
    // 🏷️ ÜRÜN TİPİ MAPPER
    // =========================
    private fun mapUrunTipi(urunTipi: UrunTipi): GenericListRowAdapterUiModel {

        val subtitleParts = mutableListOf<String>()

        if (urunTipi.birimFiyat > 0)
            subtitleParts.add("💵 ${urunTipi.birimFiyat} ₺")

        subtitleParts.add(urunTipi.hesapTipi ?: "📐 M2")

        subtitleParts.add(
            if (urunTipi.aktif == 1) "✅ Aktif"
            else "❌ Pasif"
        )

        val badgeColor =
            if (urunTipi.aktif == 1)
                Color.parseColor("#2E7D32")
            else
                Color.parseColor("#D32F2F")

        return GenericListRowAdapterUiModel(
            id = urunTipi.id ?: 0L,
            title = "(Tip Kodu: ${kod6(try { urunTipi.javaClass.getMethod("getUuid").invoke(urunTipi) as? String } catch (_: Exception) { null }, urunTipi.id)}) ${urunTipi.ad ?: "İsimsiz Ürün Tipi"}",
            subtitle = subtitleParts.joinToString(" • "),
            badgeText = if (urunTipi.aktif == 1) "Aktif" else "Pasif",
            badgeColor = badgeColor,
            badgeTextColor = Color.WHITE,
            payload = urunTipi,
            isActive = urunTipi.aktif == 1,
            description = "ID: ${urunTipi.id}",
            extraFields = mapOf(
                "fiyat" to urunTipi.birimFiyat.toString(),
                "hesapTipi" to (urunTipi.hesapTipi ?: ""),
                "aktif" to urunTipi.aktif.toString(),
                "aciklama" to (urunTipi.aciklama ?: "")
            )
        )
    }

    // =========================
    // 📋 PAYLAŞIM METNİ
    // =========================
    private fun buildShareText(
        siparisNo: String,
        musteriAdi: String,
        telefon: String,
        urunBilgisi: String,
        adres: String,
        durum: String,
        adet: String,
        tutar: String,
        teslimTarihi: String,
        notlar: String
    ): String = buildString {
        appendLine("🧾 SİPARİŞ BİLGİSİ")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("🎫 Sipariş No : $siparisNo")
        appendLine("👤 Müşteri    : ${musteriAdi.ifBlank { "---" }}")
        appendLine("📞 Telefon    : ${telefon.ifBlank { "---" }}")
        appendLine("📦 Ürün       : $urunBilgisi")
        appendLine("📌 Durum      : $durum")
        appendLine("🔢 Adet       : $adet")
        appendLine("💰 Tutar      : $tutar")
        appendLine("📍 Adres      : $adres")
        if (teslimTarihi.isNotBlank()) appendLine("🗓️ Teslim     : $teslimTarihi")
        if (notlar.isNotBlank()) appendLine("📝 Not        : $notlar")
        append("━━━━━━━━━━━━━━━━━━━━")
    }.trim()

    fun mapList(items: List<Any>): List<GenericListRowAdapterUiModel> =
        items.map { map(it) }
}
