package com.example.metatakip.feature.admin.data

import com.example.metatakip.feature_data.entityModel.MesajSablon

interface MesajSablonDaoInterface {

    fun getAll(): List<MesajSablon>

    // ✅ Firma ID'ye göre getir
    fun getByFirmaId(firmaId: Long): List<MesajSablon>

    // ✅ Tip ve Firma ID'ye göre getir
    fun getByFirmaIdAndTip(firmaId: Long, tip: String): List<MesajSablon>

    fun getById(id: Long): MesajSablon?
    fun insert(sablon: MesajSablon): Long
    fun update(id: Long, sablon: MesajSablon): Boolean
    fun delete(id: Long): Boolean

    /**
     * Varsayılan mesaj şablonunu döner (genel varsayılan)
     */
    fun getVarsayilan(): MesajSablon?

    // ✅ Belirli firma için varsayılan şablon
    fun getVarsayilanByFirmaId(firmaId: Long): MesajSablon?

    // ✅ Belirli tip ve firma için varsayılan şablon
    fun getVarsayilanByFirmaIdAndTip(firmaId: Long, tip: String): MesajSablon?

    // ✅ Firma için varsayılan şablonu ayarla
    /**
     * Belirli bir firma için varsayılan şablonu ayarlar
     * @param firmaId Firma ID
     * @param aktifSablonId Varsayılan yapılacak şablon ID
     * @return İşlem başarılı mı
     */
    fun setVarsayilanForFirma(firmaId: Long, aktifSablonId: Long): Boolean

    // ✅ Firma ve tip için varsayılan şablonu ayarla
    fun setVarsayilanForFirmaAndTip(firmaId: Long, tip: String, aktifSablonId: Long): Boolean

    // ✅ Tüm şablonları getir (silinenler dahil)
    fun getAllWithDeleted(): List<MesajSablon>

    // ✅ Kalıcı silme (hard delete)
    fun hardDelete(id: Long): Boolean

    // ✅ Belirli tipteki şablonları getir
    fun getByTip(tip: String): List<MesajSablon>

    // ✅ Firma ve varsayılan durumuna göre getir
    fun getByFirmaIdAndVarsayilan(firmaId: Long, varsayilan: Boolean): List<MesajSablon>

    // ✅ Tip, firma ve varsayılan durumuna göre getir
    fun getByFirmaIdAndTipAndVarsayilan(firmaId: Long, tip: String, varsayilan: Boolean): List<MesajSablon>

    // ✅ Şablon sayısını getir
    fun getCountByFirmaId(firmaId: Long): Int

    // ✅ Tipe göre şablon sayısını getir
    fun getCountByFirmaIdAndTip(firmaId: Long, tip: String): Int
}