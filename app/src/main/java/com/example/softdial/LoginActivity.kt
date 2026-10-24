package com.example.softdial

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** First-run SIP sign-in screen. Also reachable until an account exists or the user skips. */
class LoginActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etLabel: EditText
    private lateinit var tvStatus: TextView
    private lateinit var progress: View
    private lateinit var btnLogin: Button
    private lateinit var btnContinue: Button

    private var pending: Store.SipAccount? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { onTimeout() }
    private val regCb: (String) -> Unit = { _ -> runOnUiThread { checkRegistration() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_login)
        Prefs.init(this)

        etServer = findViewById(R.id.etLoginServer)
        etUser = findViewById(R.id.etLoginUser)
        etPass = findViewById(R.id.etLoginPass)
        etLabel = findViewById(R.id.etLoginLabel)
        tvStatus = findViewById(R.id.tvLoginStatus)
        progress = findViewById(R.id.loginProgress)
        btnLogin = findViewById(R.id.btnLogin)
        btnContinue = findViewById(R.id.btnLoginContinue)

        findViewById<CheckBox>(R.id.cbLoginShowPass).setOnCheckedChangeListener { _, checked ->
            etPass.inputType = InputType.TYPE_CLASS_TEXT or
                if (checked) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPass.setSelection(etPass.text.length)
        }

        val advanced = findViewById<View>(R.id.loginAdvancedGroup)
        val tvAdvanced = findViewById<TextView>(R.id.tvLoginAdvanced)
        tvAdvanced.setOnClickListener {
            val open = advanced.visibility != View.VISIBLE
            advanced.visibility = if (open) View.VISIBLE else View.GONE
            tvAdvanced.text = getString(if (open) R.string.login_advanced_open else R.string.login_advanced)
        }

        btnLogin.setOnClickListener { login() }
        btnContinue.setOnClickListener { finish() }
        findViewById<Button>(R.id.btnLoginSkip).setOnClickListener {
            Prefs.loginSkipped = true
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        SipManager.addRegListener(regCb)
    }

    override fun onStop() {
        SipManager.removeRegListener(regCb)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        super.onDestroy()
    }

    private fun showStatus(text: String, ok: Boolean?) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = text
        tvStatus.setTextColor(
            ContextCompat.getColor(
                this, when (ok) { true -> R.color.nav_active; false -> R.color.perm_bad; else -> R.color.nav_inactive }
            )
        )
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !busy
        btnLogin.alpha = if (busy) 0.6f else 1f
    }

    private fun login() {
        val server = etServer.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()
        if (server.isEmpty() || user.isEmpty()) {
            showStatus(getString(R.string.login_need_fields), false)
            return
        }
        val transport = when {
            findViewById<RadioButton>(R.id.rbLoginTcp).isChecked -> "tcp"
            findViewById<RadioButton>(R.id.rbLoginTls).isChecked -> "tls"
            else -> "udp"
        }
        val label = etLabel.text.toString().trim().ifEmpty { "$user@$server" }
        val acc = Store.SipAccount(pending?.id ?: Store.newId(), label, server, user, pass, transport)
        pending = acc

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(btnLogin.windowToken, 0)

        Store.saveAccount(acc)
        Prefs.defaultAccountId = acc.id
        AppLog.d("LOGIN", "Signing in as ${AppLog.mask(user)} on $server ($transport)")
        SipManager.init(this)
        SipManager.register()
        CallService.start(this)

        btnContinue.visibility = View.GONE
        setBusy(true)
        showStatus(getString(R.string.reg_connecting), null)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 15000)
        checkRegistration()
    }

    private fun checkRegistration() {
        val acc = pending ?: return
        when (SipManager.regStateOf(acc)) {
            "Ok" -> {
                handler.removeCallbacks(timeout)
                setBusy(false)
                showStatus("✓ " + getString(R.string.login_ok), true)
                handler.postDelayed({ finish() }, 800)
            }
            "Failed" -> {
                handler.removeCallbacks(timeout)
                setBusy(false)
                val why = SipManager.regMessageOf(acc).ifEmpty { getString(R.string.reg_failed) }
                showStatus("✗ " + getString(R.string.login_failed, why), false)
                btnContinue.visibility = View.VISIBLE
            }
        }
    }

    private fun onTimeout() {
        setBusy(false)
        showStatus(getString(R.string.login_timeout), false)
        btnContinue.visibility = View.VISIBLE
    }
}
