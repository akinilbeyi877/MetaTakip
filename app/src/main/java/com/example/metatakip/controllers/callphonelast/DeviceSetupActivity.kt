package com.example.metatakip.controllers.callphonelast

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.metatakip.R
import com.example.metatakip.feature_data.entityModel.DeviceConfig
import java.net.Inet4Address
import java.net.NetworkInterface

class DeviceSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_device)

        val spCompany = findViewById<Spinner>(R.id.spCompany)
        val etUserName = findViewById<EditText>(R.id.etUserName)
        val rgRole = findViewById<RadioGroup>(R.id.rgDeviceRole)
        val rgIpSource = findViewById<RadioGroup>(R.id.rgIpSource)
        val etCentralIp = findViewById<EditText>(R.id.etCentralIp)
        val btnSave = findViewById<Button>(R.id.btnSave)

        spCompany.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Pars Halı", "Mega Halı")
        )

        findViewById<RadioButton>(R.id.rbWifi).isChecked = true
        rgRole.check(R.id.rbSaha)

        rgRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbMerkez) {
                etCentralIp.isEnabled = false
                val ip = when (rgIpSource.checkedRadioButtonId) {
                    R.id.rbWifi -> getWifiIp()
                    R.id.rbMobile -> getMobileIp()
                    else -> ""
                }
                if (ip.isNotBlank()) etCentralIp.setText(ip)
            } else {
                etCentralIp.isEnabled = true
                etCentralIp.text.clear()
            }
        }

        btnSave.setOnClickListener {
            val ip = etCentralIp.text.toString().trim()
            if (!isValidIp(ip)) {
                toast("Geçerli IP gir")
                return@setOnClickListener
            }

            val config = DeviceConfig(
                companyName = spCompany.selectedItem.toString(),
                userName = etUserName.text.toString(),
                userRole = if (rgRole.checkedRadioButtonId == R.id.rbMerkez) "MERKEZ" else "SAHA",
                simSlot1Number = "",
                simSlot2Number = "",
                isCentralDevice = rgRole.checkedRadioButtonId == R.id.rbMerkez,
                centralIp = ip
            )

            DeviceManager.saveConfig(this, config)
            toast("Kaydedildi")
            finish()
        }
    }

    private fun getWifiIp(): String =
        getIpByInterfacePrefix("wlan")

    private fun getMobileIp(): String =
        getIpByInterfacePrefix("rmnet", "ccmni")

    private fun getIpByInterfacePrefix(vararg prefixes: String): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
                if (prefixes.none { iface.name.startsWith(it) }) return@forEach
                iface.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun isValidIp(ip: String): Boolean =
        Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
            .matches(ip)

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
