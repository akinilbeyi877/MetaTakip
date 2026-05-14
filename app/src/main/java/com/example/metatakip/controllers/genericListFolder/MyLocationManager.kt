package com.example.metatakip.controllers.genericListFolder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.*

class MyLocationManager(private val context: Context) {

    companion object {
        const val REQUEST_LOCATION_PERMISSION = 1003
    }

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    /* =============================================================
       İZİN & SERVİS KONTROLLERİ
       ============================================================= */

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val lm =
            context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    }

    private fun requestPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    /* =============================================================
       DIŞARIDAN ÇAĞRILAN TEK METOT
       ============================================================= */

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        activity: Activity,
        onSuccess: (Location, String?) -> Unit,
        onError: (String) -> Unit
    ) {

        /* ❌ İZİN YOK */
        if (!hasLocationPermission()) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                showPermissionDialog(activity)
            } else {
                requestPermission(activity)
            }

            onError("Konum izni gerekli")
            return
        }

        /* ❌ GPS KAPALI */
        if (!isLocationEnabled()) {
            showGpsDialog(activity)
            onError("Konum servisleri kapalı")
            return
        }

        /* ✅ KONUM AL */
        requestSingleLocation(onSuccess, onError)
    }

    /* =============================================================
       KONUM ALMA
       ============================================================= */

    @SuppressLint("MissingPermission")
    private fun requestSingleLocation(
        onSuccess: (Location, String?) -> Unit,
        onError: (String) -> Unit
    ) {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        )
            .setMaxUpdates(1)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                val location = result.lastLocation
                stopUpdates()

                if (location == null) {
                    onError("Konum alınamadı")
                    return
                }

                resolveAddress(location) { address ->
                    onSuccess(location, address)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun stopUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    /* =============================================================
       ADRES ÇÖZÜMLEME (BACKGROUND)
       ============================================================= */

    private fun resolveAddress(
        location: Location,
        callback: (String?) -> Unit
    ) {
        if (!Geocoder.isPresent()) {
            callback(null)
            return
        }

        Thread {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val list = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                callback(list?.firstOrNull()?.getAddressLine(0))
            } catch (e: Exception) {
                callback(null)
            }
        }.start()
    }

    /* =============================================================
       DIALOG'LAR
       ============================================================= */

    private fun showPermissionDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Konum İzni Gerekli")
            .setMessage("Konum alabilmek için izin vermeniz gerekiyor.")
            .setPositiveButton("İzin Ver") { _, _ ->
                requestPermission(activity)
            }
            .setNegativeButton("İptal", null)
            .setCancelable(false)
            .show()
    }

    private fun showGpsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("GPS Kapalı")
            .setMessage("Konum servisleri kapalı. Açmak ister misiniz?")
            .setPositiveButton("Ayarlar") { _, _ ->
                activity.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )
            }
            .setNegativeButton("İptal", null)
            .setCancelable(false)
            .show()
    }
}
