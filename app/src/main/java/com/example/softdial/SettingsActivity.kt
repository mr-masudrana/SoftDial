package com.example.softdial

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private var pendingPass = ""

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = Backup.build(pendingPass)
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Backup saved", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            pendingPass = ""
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val text = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot read file", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            if (Backup.isEncrypted(text)) {
                askPass("Passphrase", "This backup is encrypted.") { doImport(text, it) }
            } else {
                doImport(text, "")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        Prefs.init(this)

        val rbSystem = findViewById<RadioButton>(R.id.rbSystem)
        val rbLight = findViewById<RadioButton>(R.id.rbLight)
        val rbDark = findViewById<RadioButton>(R.id.rbDark)
        val cbAuto = findViewById<CheckBox>(R.id.cbAutoRecord)
        val cbPush = findViewById<CheckBox>(R.id.cbPush)
        val tvPush = findViewById<TextView>(R.id.tvPush)
        val cbDnd = findViewById<CheckBox>(R.id.cbDnd)
        val cbDndContacts = findViewById<CheckBox>(R.id.cbDndContacts)
        val rbFwdOff = findViewById<RadioButton>(R.id.rbFwdOff)
        val rbFwdAlways = findViewById<RadioButton>(R.id.rbFwdAlways)
        val rbFwdBusy = findViewById<RadioButton>(R.id.rbFwdBusy)
        val rbFwdNoAnswer = findViewById<RadioButton>(R.id.rbFwdNoAnswer)
        val etFwdNumber = findViewById<EditText>(R.id.etFwdNumber)
        val etFwdDelay = findViewById<EditText>(R.id.etFwdDelay)

        cbAuto.isChecked = Prefs.autoRecord
        cbPush.isChecked = Prefs.pushEnabled
        cbDnd.isChecked = Prefs.dnd
        cbDndContacts.isChecked = Prefs.dndAllowContacts
        etFwdNumber.setText(Prefs.fwdNumber)
        etFwdDelay.setText(Prefs.fwdDelay.toString())
        when (Prefs.theme) {
            "light" -> rbLight.isChecked = true
            "dark" -> rbDark.isChecked = true
            else -> rbSystem.isChecked = true
        }
        when (Prefs.fwdMode) {
            "always" -> rbFwdAlways.isChecked = true
            "busy" -> rbFwdBusy.isChecked = true
            "noanswer" -> rbFwdNoAnswer.isChecked = true
            else -> rbFwdOff.isChecked = true
        }

        tvPush.text = if (Push.available(this)) {
            if (Prefs.fcmToken.isBlank()) "FCM ready, waiting for token…"
            else "FCM ready. Works only if your SIP server supports push."
        } else {
            "FCM not configured (APK was built without google-services.json)."
        }

        findViewById<Button>(R.id.btnAccounts).setOnClickListener {
            startActivity(Intent(this, AccountsActivity::class.java))
        }

        findViewById<Button>(R.id.btnCopyToken).setOnClickListener {
            if (Prefs.fcmToken.isBlank()) {
                Toast.makeText(this, "No FCM token yet", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("fcm", Prefs.fcmToken))
                Toast.makeText(this, "Token copied", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            askPass(
                "Export backup",
                "Optional passphrase to encrypt the file (it contains SIP passwords). Leave empty for a plain file."
            ) { pass ->
                pendingPass = pass
                val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                exportLauncher.launch("softdial-backup-$date.json")
            }
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val mode = when {
                rbFwdAlways.isChecked -> "always"
                rbFwdBusy.isChecked -> "busy"
                rbFwdNoAnswer.isChecked -> "noanswer"
                else -> "off"
            }
            val number = etFwdNumber.text.toString().trim()
            if (mode != "off" && number.isEmpty()) {
                Toast.makeText(this, "Enter the number to forward to", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.fwdMode = mode
            Prefs.fwdNumber = number
            Prefs.fwdDelay = etFwdDelay.text.toString().toIntOrNull()?.coerceIn(5, 120) ?: 20
            Prefs.dnd = cbDnd.isChecked
            Prefs.dndAllowContacts = cbDndContacts.isChecked
            Prefs.theme = when {
                rbLight.isChecked -> "light"
                rbDark.isChecked -> "dark"
                else -> "system"
            }
            Prefs.autoRecord = cbAuto.isChecked
            Prefs.pushEnabled = cbPush.isChecked
            SipManager.init(this)
            SipManager.register()
            CallService.start(this)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            Prefs.applyTheme()   // may recreate activities
            finish()
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
    }

    private fun askPass(title: String, msg: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            hint = "Passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doImport(text: String, pass: String) {
        try {
            val msg = Backup.restore(text, pass)
            SipManager.init(this)
            SipManager.register()
            Prefs.applyTheme()
            CallService.start(this)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
