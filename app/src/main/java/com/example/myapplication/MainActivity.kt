package com.example.myapplication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.IntentFilter

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var lockerTextView: TextView
    private lateinit var openButton: Button
    private lateinit var closeButton: Button

    private val sharedPrefKey = "LOCKER_PREF"
    private val productKeyPref = "PRODUCT_KEY"
    private var assignedLocker: String? = null
    private var dataToWrite: String? = null  // Stores data to be written on NFC tap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize NFC Adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Initialize UI elements
        lockerTextView = findViewById(R.id.lockerTextView)
        openButton = findViewById(R.id.openButton)
        closeButton = findViewById(R.id.closeButton)

        checkNfcSupport()
        checkStoredProductKey()
    }

    private fun checkNfcSupport() {
        when {
            nfcAdapter == null -> showAlert("NFC not supported", true)
            nfcAdapter?.isEnabled == false -> showAlert("NFC is disabled. Enable it in settings.", true, true)
        }
    }

    private fun checkStoredProductKey() {
        val sharedPreferences = getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE)
        val storedKey = sharedPreferences.getString(productKeyPref, null)

        if (storedKey != null) {
            assignLocker(storedKey)
        } else {
            promptForProductKey()
        }
    }

    private fun promptForProductKey() {
        val input = EditText(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Product Key")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val key = input.text.toString().trim()
            if (validateProductKey(key)) {
                saveProductKey(key)
                assignLocker(key)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Invalid Product Key! Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateProductKey(key: String): Boolean {
        return key in listOf("ProductKey1", "ProductKey2", "ProductKey3")
    }

    private fun saveProductKey(key: String) {
        val sharedPreferences = getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(productKeyPref, key)
            apply()
        }
    }

    private fun assignLocker(key: String) {
        val lockerNumber = getLockerNumber(key) // Get the locker number

        lockerTextView.text = "Assigned Locker: $lockerNumber"
        assignedLocker = when (key) {
            "ProductKey1" -> "hi"
            "ProductKey2" -> "hi2"
            "ProductKey3" -> "hi3"
            else -> null
        }

        setupButtons()
    }

    // Function to get locker number from the product key
    private fun getLockerNumber(productKey: String): String {
        return if (productKey.startsWith("ProductKey")) {
            val number = productKey.removePrefix("ProductKey")
            "Locker $number"
        } else {
            "Unknown"
        }
    }

    private fun setupButtons() {
        openButton.setOnClickListener {
            assignedLocker?.let {
                dataToWrite = it
                Toast.makeText(this, "Tap your NFC tag to OPEN the locker", Toast.LENGTH_LONG).show()
            }
        }

        closeButton.setOnClickListener {
            dataToWrite = "low"
            Toast.makeText(this, "Tap your NFC tag to CLOSE the locker", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
            it.enableForegroundDispatch(this, pendingIntent, filters, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?.let { tag ->
            dataToWrite?.let { data ->
                writeNdefToTag(tag, data)
                dataToWrite = null  // Reset after writing
            }
        }
    }

    private fun writeNdefToTag(tag: Tag, data: String) {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    throw Exception("NFC tag is read-only")
                }
                val record = NdefRecord.createTextRecord("en", data)
                val message = NdefMessage(arrayOf(record))
                ndef.writeNdefMessage(message)
                ndef.close()
                Toast.makeText(this, "NFC Write Successful", Toast.LENGTH_SHORT).show()
            } else {
                val ndefFormatable = NdefFormatable.get(tag)
                if (ndefFormatable != null) {
                    ndefFormatable.connect()
                    val record = NdefRecord.createTextRecord("en", data)
                    val message = NdefMessage(arrayOf(record))
                    ndefFormatable.format(message)
                    ndefFormatable.close()
                    Toast.makeText(this, "NFC Formatted and Written Successfully", Toast.LENGTH_SHORT).show()
                } else {
                    throw Exception("Tag is not NDEF compatible")
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "NFC Write Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAlert(message: String, exitApp: Boolean, openSettings: Boolean = false) {
        val builder = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(if (openSettings) "Go to Settings" else "Exit") { _, _ ->
                if (openSettings) {
                    startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                } else {
                    finish()
                }
            }
        builder.show()
    }
}
