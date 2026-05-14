package com.example.metatakip.feature.uruntipi.data

import com.example.metatakip.feature_data.entityModel.UrunTipi

interface UrunTipiDaoInterface {

    // 📥 GET OPERATIONS
    fun getAll(): List<UrunTipi>
    fun getActive(): List<UrunTipi>
    fun getById(id: Long): UrunTipi?
    fun searchByName(name: String): List<UrunTipi>

    // ✏️ CRUD OPERATIONS
    fun insert(item: UrunTipi): Long
    fun update(id: Long, item: UrunTipi): Boolean
    fun delete(id: Long): Boolean

    // ✅ VALIDATION
    fun adExists(ad: String, excludeId: Long = 0): Boolean
}