package com.example.softdial

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private var pendingPass = ""
    private var afterGrant: (() -> Unit)? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            refreshPerms()
            if (ok) afterGrant?.invoke()
            afterGrant = null
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = Backup.build(pendingPass)
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.backup_export_failed, e.message), Toast.LENGTH_LONG).show()
            }
            pendingPass = ""
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val text = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                Toast.makeText(this, R.string.backup_cannot_read, Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            if (Backup.isEncrypted(text)) {
                askPass(getString(R.string.pass_title), getString(R.string.pass_encrypted_msg)) { doImport(text, it) }
            } else {
                doImport(text, "")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            BottomNav.wrap(this, layoutInflater.inflate(R.layout.activity_settings, null), BottomNav.SETTINGS)
        )
        title = getString(R.string.nav_settings)
        Prefs.init(this)

        bindAccount()
        bindTheme()
        bindLanguage()
        bindRecording()
        bindForwarding()
        bindBlock()
        bindPermissions()
        bindBackup()
        bindAbout()
    }

    private val regCb: (String) -> Unit = { _ -> runOnUiThread { refreshAccount() } }

    override fun onStart() {
        super.onStart()
        BottomNav.refreshBadge(this)
        SipManager.addRegListener(regCb)
    }

    override fun onStop() {
        SipManager.removeRegListener(regCb)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshAccount()
        refreshBlocked()
        refreshPerms()
    }

    private fun <T : View> v(id: Int): T = findViewById(id)

    // ---- Account ----

    private fun bindAccount() {
        v<Button>(R.id.btnAccounts).setOnClickListener {
            startActivity(Intent(this, AccountsActivity::class.java))
        }
        v<SwitchCompat>(R.id.swPush).apply {
            isChecked = Prefs.pushEnabled
            setOnCheckedChangeListener { _, checked ->
                Prefs.pushEnabled = checked
                SipManager.init(this@SettingsActivity)
                SipManager.register()
            }
        }
        v<TextView>(R.id.tvPush).text = getString(
            if (!Push.available(this)) R.string.set_push_off
            else if (Prefs.fcmToken.isBlank()) R.string.set_push_waiting
            else R.string.set_push_ready
        )
        v<Button>(R.id.btnCopyToken).setOnClickListener {
            if (Prefs.fcmToken.isBlank()) {
                Toast.makeText(this, R.string.set_no_token, Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("fcm", Prefs.fcmToken))
                Toast.makeText(this, R.string.set_token_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshAccount() {
        val accs = Store.accounts()
        v<TextView>(R.id.tvAccountSummary).text =
            if (accs.isEmpty()) getString(R.string.set_account_none)
            else {
                val sel = Store.selectedAccount()
                getString(
                    R.string.set_account_summary_status, accs.size,
                    sel?.let { AccountBar.displayName(it) } ?: "",
                    sel?.let { regText(this, it) } ?: ""
                )
            }
    }

    // ---- Theme / Language (applied immediately) ----

    private fun bindTheme() {
        val rg = v<RadioGroup>(R.id.rgTheme)
        rg.check(
            when (Prefs.theme) { "light" -> R.id.rbLight; "dark" -> R.id.rbDark; else -> R.id.rbSystem }
        )
        rg.setOnCheckedChangeListener { _, id ->
            val t = when (id) { R.id.rbLight -> "light"; R.id.rbDark -> "dark"; else -> "system" }
            if (t != Prefs.theme) { Prefs.theme = t; Prefs.applyTheme() }
        }
    }

    private fun bindLanguage() {
        val rg = v<RadioGroup>(R.id.rgLang)
        rg.check(
            when (Prefs.language) { "en" -> R.id.rbLangEn; "bn" -> R.id.rbLangBn; else -> R.id.rbLangSystem }
        )
        rg.setOnCheckedChangeListener { _, id ->
            val l = when (id) { R.id.rbLangEn -> "en"; R.id.rbLangBn -> "bn"; else -> "system" }
            if (l != Prefs.language) { Prefs.language = l; Prefs.applyLanguage() }
        }
    }

    // ---- Call recording ----

    private fun bindRecording() {
        v<SwitchCompat>(R.id.swAutoRecord).apply {
            isChecked = Prefs.autoRecord
            setOnCheckedChangeListener { _, checked -> Prefs.autoRecord = checked }
        }
        v<Button>(R.id.btnRecordings).setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
    }

    // ---- Call forwarding ----

    private fun bindForwarding() {
        val rg = v<RadioGroup>(R.id.rgFwd)
        rg.check(
            when (Prefs.fwdMode) {
                "always" -> R.id.rbFwdAlways
                "busy" -> R.id.rbFwdBusy
                "noanswer" -> R.id.rbFwdNoAnswer
                else -> R.id.rbFwdOff
            }
        )
        val etNumber = v<EditText>(R.id.etFwdNumber)
        val etDelay = v<EditText>(R.id.etFwdDelay)
        etNumber.setText(Prefs.fwdNumber)
        etDelay.setText(Prefs.fwdDelay.toString())

        v<Button>(R.id.btnFwdSave).setOnClickListener {
            val mode = when (rg.checkedRadioButtonId) {
                R.id.rbFwdAlways -> "always"
                R.id.rbFwdBusy -> "busy"
                R.id.rbFwdNoAnswer -> "noanswer"
                else -> "off"
            }
            val number = etNumber.text.toString().trim()
            if (mode != "off" && number.isEmpty()) {
                Toast.makeText(this, R.string.set_fwd_need_number, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.fwdMode = mode
            Prefs.fwdNumber = number
            Prefs.fwdDelay = etDelay.text.toString().toIntOrNull()?.coerceIn(5, 120) ?: 20
            Toast.makeText(this, R.string.set_fwd_saved, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Number block + Do Not Disturb ----

    private fun bindBlock() {
        v<SwitchCompat>(R.id.swDnd).apply {
            isChecked = Prefs.dnd
            setOnCheckedChangeListener { _, checked -> Prefs.dnd = checked }
        }
        v<SwitchCompat>(R.id.swDndContacts).apply {
            isChecked = Prefs.dndAllowContacts
            setOnCheckedChangeListener { _, checked -> Prefs.dndAllowContacts = checked }
        }
        v<Button>(R.id.btnBlocked).setOnClickListener {
            startActivity(Intent(this, BlockedActivity::class.java))
        }
    }

    private fun refreshBlocked() {
        v<Button>(R.id.btnBlocked).text = getString(R.string.set_blocked_manage, Store.blocked().size)
    }

    // ---- Permissions ----

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun setStatus(tv: TextView, ok: Boolean) {
        tv.text = (if (ok) "✓ " else "✗ ") + getString(if (ok) R.string.set_perm_ok else R.string.set_perm_no)
        tv.setTextColor(ContextCompat.getColor(this, if (ok) R.color.nav_active else R.color.perm_bad))
    }

    private fun refreshPerms() {
        setStatus(v(R.id.tvMic), granted(Manifest.permission.RECORD_AUDIO))
        setStatus(
            v(R.id.tvNotif),
            Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        )
        setStatus(v(R.id.tvContacts), granted(Manifest.permission.READ_CONTACTS))
        setStatus(
            v(R.id.tvBluetooth),
            Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)
        )
        setStatus(v(R.id.tvBattery), batteryOk())
    }

    private fun bindPermissions() {
        v<View>(R.id.rowMic).setOnClickListener {
            if (!granted(Manifest.permission.RECORD_AUDIO)) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        v<View>(R.id.rowNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        v<View>(R.id.rowContacts).setOnClickListener {
            if (!granted(Manifest.permission.READ_CONTACTS)) {
                afterGrant = { if (!Prefs.contactsImported) importPhoneContacts() }
                permLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        v<View>(R.id.rowBluetooth).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        v<View>(R.id.rowBattery).setOnClickListener {
            if (!batteryOk()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
        v<Button>(R.id.btnAppSettings).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    // ---- Backup & restore ----

    private fun bindBackup() {
        v<Button>(R.id.btnExport).setOnClickListener {
            askPass(
                getString(R.string.export_dialog_title),
                getString(R.string.export_dialog_msg)
            ) { pass ->
                pendingPass = pass
                val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                exportLauncher.launch("softdial-backup-$date.json")
            }
        }
        v<Button>(R.id.btnImport).setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
        v<Button>(R.id.btnImportContacts).setOnClickListener {
            if (PhoneBook.hasPermission(this)) {
                importPhoneContacts()
            } else {
                afterGrant = { importPhoneContacts() }
                permLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun importPhoneContacts() {
        Prefs.contactsImported = true
        Thread {
            val n = PhoneBook.importAll(this)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.imported_new_contacts, n), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun askPass(title: String, msg: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            hint = getString(R.string.pass_title)
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
            .setPositiveButton(getString(R.string.ok)) { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doImport(text: String, pass: String) {
        try {
            val msg = Backup.restore(this, text, pass)
            SipManager.init(this)
            SipManager.register()
            Prefs.applyTheme()
            Prefs.applyLanguage()
            CallService.start(this)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    // ---- About ----

    private fun bindAbout() {
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
        v<TextView>(R.id.tvVersion).text = getString(R.string.app_name) + " · " + getString(R.string.set_about_version, ver)
    }
}
