package com.example.metatakip.feature_data.helpers

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GenericListHelperImpl(
    private val activity: Activity
) : IGenericListHelper {

    companion object {
        // Bu requestCode'lar Activity'deki sabitlerle aynı olmalı.
        const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
        const val REQUEST_ENABLE_BLUETOOTH = 1002

        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var selectedTextForPrinting: String = ""
    private var isCheckingPermissions: Boolean = false

    // =============================================================
    // PUBLIC API
    // =============================================================

    override fun startPrintFlow(text: String) {
        selectedTextForPrinting = text
        if (!checkBluetoothPermissions()) return
        if (!checkBluetoothEnabled()) return
        showPairedDevicesDialog(text)
    }

    override fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                isCheckingPermissions = false

                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    if (selectedTextForPrinting.isNotEmpty()) {
                        checkBluetoothEnabled()
                    }
                } else {
                    toastLong("Bluetooth izinleri verilmedi. Yazdırma yapılamaz.")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                if (resultCode == Activity.RESULT_OK) {
                    showPairedDevicesDialog(selectedTextForPrinting)
                } else {
                    toastLong("Bluetooth açılması gerekiyor")
                }
            }
        }
    }

    override fun openWhatsAppWeb() {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")))
        } catch (e: Exception) {
            toastShort("WhatsApp açılamadı: ${e.message}")
        }
    }

    override fun openGoogleDrive() {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com")))
        } catch (e: Exception) {
            toastShort("Google Drive açılamadı: ${e.message}")
        }
    }

    override fun showColorPicker(textView: TextView) {
        val colors = arrayOf("Siyah", "Kırmızı", "Mavi", "Yeşil", "Mor", "Turuncu", "Kahverengi")
        val colorValues = arrayOf(
            Color.BLACK,
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.MAGENTA,
            Color.parseColor("#FF9800"),
            Color.parseColor("#795548")
        )

        AlertDialog.Builder(activity)
            .setTitle("Yazı Rengi Seç")
            .setItems(colors) { _, which ->
                textView.setTextColor(colorValues[which])
            }
            .show()
    }

    // =============================================================
    // INTERNALS
    // =============================================================

    private fun logPrinterError(msg: String) {
        try {
            val logFile = File(activity.getExternalFilesDir(null), "printer_errors.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            logFile.appendText("$timestamp → $msg\n")
            Log.e("PRINTER_ERROR", msg)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        if (isCheckingPermissions) return false

        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return if (permissionsToRequest.isNotEmpty()) {
            isCheckingPermissions = true
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
            false
        } else true
    }

    private fun checkBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        return if (bluetoothAdapter == null) {
            toastLong("Cihaz Bluetooth'u desteklemiyor")
            false
        } else if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            false
        } else true
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog(text: String) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            toastShort("Bluetooth adaptörü bulunamadı")
            return
        }

        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

        if (pairedDevices.isEmpty()) {
            toastLong("Eşleşmiş Bluetooth cihazı bulunamadı. Önce yazıcıyı eşleştirin.")

            AlertDialog.Builder(activity)
                .setTitle("Yazıcı Bulunamadı")
                .setMessage("Eşleşmiş yazıcı bulunamadı. Bluetooth ayarlarını açmak ister misiniz?")
                .setPositiveButton("Bluetooth Ayarları") { _, _ ->
                    try {
                        activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: Exception) {
                        toastShort("Bluetooth ayarları açılamadı")
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
            return
        }

        val deviceList = pairedDevices.toList()

        val deviceNames = deviceList.map { device ->
            val name = device.name ?: "Bilinmeyen Cihaz"
            val type = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Klasik"
                BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                else -> "Diğer"
            }
            "$name ($type) - ${device.address}"
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Yazıcı Seçin (${deviceList.size} cihaz)")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = deviceList[which]
                connectAndPrintToDevice(selectedDevice, text)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun connectAndPrintToDevice(device: BluetoothDevice, text: String) {
        Thread {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null

            try {
                runOnUiThread {
                    Toast.makeText(activity, "${device.name ?: "Yazıcı"} yazıcısına bağlanıyor...", Toast.LENGTH_SHORT).show()
                }

                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                outputStream = socket.outputStream

                // init
                outputStream.write(byteArrayOf(0x1B, 0x40))

                val cleanText = sanitize(text)

                outputStream.write(cleanText.toByteArray(Charsets.UTF_8))
                outputStream.write("\n\n\n\n".toByteArray())
                // cut
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x00))
                outputStream.flush()

                runOnUiThread {
                    Toast.makeText(activity, "✔ Yazdırma başarılı: ${device.name}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                logPrinterError("Bağlantı/Yazdırma hatası: ${e.message}")

                runOnUiThread {
                    Toast.makeText(activity, "❌ Yazdırma hatası: ${e.message}", Toast.LENGTH_LONG).show()
                }

                // fallback
                tryAlternativeConnection(device, text)

            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun tryAlternativeConnection(device: BluetoothDevice, text: String) {
        Thread {
            try {
                val socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val os = socket.outputStream
                os.write(byteArrayOf(0x1B, 0x40))

                val cleanText = sanitize(text)

                os.write(cleanText.toByteArray(Charsets.UTF_8))
                os.write("\n\n\n".toByteArray())
                os.flush()
                socket.close()

                runOnUiThread {
                    Toast.makeText(activity, "✔ Yazdırma başarılı (alternatif): ${device.name}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(activity, "❌ Yazıcıya bağlanılamadı: ${e.message}", Toast.LENGTH_LONG).show()
                    showDeviceInfoForDebug(device, e.message ?: "Bilinmeyen hata")
                }
            }
        }.start()
    }

    private fun showDeviceInfoForDebug(device: BluetoothDevice, error: String) {
        AlertDialog.Builder(activity)
            .setTitle("Cihaz Bağlantı Hatası")
            .setMessage(
                """
                Yazıcı Bağlantı Hatası

                Hata: $error

                Cihaz Bilgileri:
                • Adı: ${device.name ?: "Bilinmeyen"}
                • Adresi: ${device.address}
                • Bağlantı Durumu: ${if (device.bondState == BluetoothDevice.BOND_BONDED) "Eşleşmiş" else "Eşleşmemiş"}
                """.trimIndent()
            )
            .setPositiveButton("Bluetooth Ayarları") { _, _ ->
                try {
                    activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (_: Exception) {}
            }
            .setNeutralButton("Tekrar Dene") { _, _ ->
                if (selectedTextForPrinting.isNotEmpty()) {
                    connectAndPrintToDevice(device, selectedTextForPrinting)
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun sanitize(text: String): String {
        return text
            .replace("ğ", "g").replace("Ğ", "G")
            .replace("ü", "u").replace("Ü", "U")
            .replace("ş", "s").replace("Ş", "S")
            .replace("ö", "o").replace("Ö", "O")
            .replace("ç", "c").replace("Ç", "C")
            .replace("İ", "I").replace("ı", "i")
    }

    private fun toastShort(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun toastLong(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
    }

    private fun runOnUiThread(block: () -> Unit) {
        activity.runOnUiThread(block)
    }
}