package com.example.softdial

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.linphone.core.Call

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ANSWER = "answer"
    }

    private lateinit var tvReg: TextView
    private lateinit var tvNumber: TextView
    private lateinit var tvContact: TextView
    private lateinit var tvCallInfo: TextView
    private lateinit var tvCallState: TextView
    private lateinit var tvDtmf: TextView
    private lateinit var tvWaiting: TextView
    private lateinit var dialerGroup: View
    private lateinit var bottomNav: View
    private lateinit var callGroup: View
    private lateinit var controls: View
    private lateinit var waitingGroup: View
    private lateinit var dtmfPad: GridLayout
    private lateinit var btnFrom: Button
    private lateinit var btnAnswer: Button
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnRecord: Button
    private lateinit var btnKeypad: Button
    private lateinit var btnHold: Button
    private lateinit var btnSwap: Button
    private lateinit var btnAddCall: Button

    private val number = StringBuilder()

    private var answerAfterPermission = false
    private var pendingCallNumber: String? = null
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            CallService.start(this)
            val micGranted = result[Manifest.permission.RECORD_AUDIO] == true || hasMicPermission()
            if (answerAfterPermission) {
                answerAfterPermission = false
                if (micGranted) SipManager.answer()
                else android.widget.Toast.makeText(this, "Microphone permission is required to answer calls", android.widget.Toast.LENGTH_LONG).show()
            }
            val numberToCall = pendingCallNumber
            pendingCallNumber = null
            if (numberToCall != null) {
                if (micGranted) SipManager.call(numberToCall)
                else android.widget.Toast.makeText(this, "Microphone permission is required to place calls", android.widget.Toast.LENGTH_LONG).show()
            }
        }

    private fun hasMicPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun answerSafely() {
        if (hasMicPermission()) {
            SipManager.answer()
        } else {
            answerAfterPermission = true
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permLauncher.launch(permissions.toTypedArray())
        }
    }

    private val regCb: (String) -> Unit = { _ -> runOnUiThread { updateReg() } }
    private val callCb: (Call.State, Call) -> Unit = { _, _ -> runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SipManager.init(this)
        Push.refreshToken(this)

        tvReg = findViewById(R.id.tvReg)
        tvNumber = findViewById(R.id.tvNumber)
        tvContact = findViewById(R.id.tvContact)
        tvCallInfo = findViewById(R.id.tvCallInfo)
        tvCallState = findViewById(R.id.tvCallState)
        tvDtmf = findViewById(R.id.tvDtmf)
        tvWaiting = findViewById(R.id.tvWaiting)
        dialerGroup = findViewById(R.id.dialerGroup)
        bottomNav = findViewById(R.id.bottomNav)
        callGroup = findViewById(R.id.callGroup)
        controls = findViewById(R.id.controls)
        waitingGroup = findViewById(R.id.waitingGroup)
        dtmfPad = findViewById(R.id.dtmfPad)
        btnFrom = findViewById(R.id.btnFrom)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnRecord = findViewById(R.id.btnRecord)
        btnKeypad = findViewById(R.id.btnKeypad)
        btnHold = findViewById(R.id.btnHold)
        btnSwap = findViewById(R.id.btnSwap)
        btnAddCall = findViewById(R.id.btnAddCall)

        buildPad()

        findViewById<Button>(R.id.btnCall).setOnClickListener {
            if (hasMicPermission()) SipManager.call(number.toString()) else {
                pendingCallNumber = number.toString()
                val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                permLauncher.launch(permissions.toTypedArray())
            }
        }
        findViewById<Button>(R.id.btnBack).apply {
            setOnClickListener {
                if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
                refreshNumber()
            }
            setOnLongClickListener { number.clear(); refreshNumber(); true }
        }
        btnFrom.setOnClickListener {
            val accs = Store.accounts()
            AlertDialog.Builder(this)
                .setTitle("Call with")
                .setItems(accs.map { it.label }.toTypedArray()) { _, i ->
                    Prefs.defaultAccountId = accs[i].id
                    SipManager.applySelected()
                    refreshFrom()
                }
                .show()
        }
        findViewById<Button>(R.id.btnRecents).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        findViewById<View>(R.id.navDial).setOnClickListener {
            showDialer()
            setNavSelected(R.id.navDial)
        }
        findViewById<View>(R.id.navRecents).setOnClickListener {
            setNavSelected(R.id.navRecents)
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.navContacts).setOnClickListener {
            setNavSelected(R.id.navContacts)
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        findViewById<View>(R.id.navSettings).setOnClickListener {
            setNavSelected(R.id.navSettings)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnAnswer.setOnClickListener { answerSafely() }
        findViewById<Button>(R.id.btnAnswerWaiting).setOnClickListener { answerSafely() }
        findViewById<Button>(R.id.btnDeclineWaiting).setOnClickListener { SipManager.declineIncoming() }
        findViewById<Button>(R.id.btnHangup).setOnClickListener { SipManager.hangUp() }
        btnMute.setOnClickListener {
            btnMute.text = if (SipManager.toggleMute()) "Unmute" else "Mute"
        }
        btnSpeaker.setOnClickListener {
            btnSpeaker.text = if (SipManager.toggleSpeaker()) "Earpiece" else "Speaker"
        }
        btnRecord.setOnClickListener {
            btnRecord.text = if (SipManager.toggleRecording()) "Stop rec" else "Record"
        }
        btnKeypad.setOnClickListener {
            val show = dtmfPad.visibility != View.VISIBLE
            dtmfPad.visibility = if (show) View.VISIBLE else View.GONE
            tvDtmf.visibility = if (show) View.VISIBLE else View.GONE
        }
        btnHold.setOnClickListener { SipManager.toggleHold() }
        btnSwap.setOnClickListener { SipManager.swap() }
        btnAddCall.setOnClickListener { showAddCallDialog() }

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            i.removeExtra(EXTRA_ANSWER)
            if (hasMicPermission()) SipManager.answer() else answerAfterPermission = true
        }
    }

    override fun onStart() {
        super.onStart()
        SipManager.addRegListener(regCb)
        SipManager.addCallListener(callCb)
        updateReg()
        refreshFrom()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNav.isInitialized && bottomNav.visibility == View.VISIBLE) {
            setNavSelected(R.id.navDial)
        }
    }

    override fun onStop() {
        SipManager.removeRegListener(regCb)
        SipManager.removeCallListener(callCb)
        super.onStop()
    }

    // ---- dialpad ----

    private fun buildPad() {
        fillPad(findViewById(R.id.pad), 60, true) { number.append(it); refreshNumber() }
        fillPad(dtmfPad, 52, false) {
            SipManager.sendDtmf(it[0])
            tvDtmf.append(it)
        }
    }

    private fun fillPad(pad: GridLayout, heightDp: Int, plusOnZero: Boolean, onKey: (String) -> Unit) {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val h = dp(heightDp)
        val m = dp(4)
        for (k in keys) {
            val b = Button(this).apply {
                text = k
                textSize = 24f
                setTextColor(android.graphics.Color.rgb(30, 41, 59))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.rgb(231, 238, 243)
                )
                elevation = dp(2).toFloat()
                setOnClickListener { onKey(k); performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
                if (plusOnZero && k == "0") setOnLongClickListener { number.append("+"); refreshNumber(); true }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = h
                setMargins(m, m, m, m)
            }
            pad.addView(b, lp)
        }
    }

    private fun refreshNumber() {
        tvNumber.text = number.toString()
        tvContact.text = Store.nameFor(number.toString()) ?: ""
    }

    private fun refreshFrom() {
        val accs = Store.accounts()
        btnFrom.visibility = if (accs.size > 1) View.VISIBLE else View.GONE
        btnFrom.text = "Call with: ${Store.selectedAccount()?.label ?: ""} ▾"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- screens ----

    private fun showDialer() {
        dialerGroup.visibility = View.VISIBLE
        callGroup.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        btnMute.text = "Mute"
        btnSpeaker.text = "Speaker"
        btnRecord.text = "Record"
        dtmfPad.visibility = View.GONE
        tvDtmf.visibility = View.GONE
        tvDtmf.text = ""
    }

    private fun nameOf(call: Call): String {
        val num = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
        return Store.nameFor(num) ?: num
    }

    private fun label(s: Call.State): String = when (s) {
        Call.State.OutgoingInit, Call.State.OutgoingProgress -> "Calling…"
        Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> "Ringing…"
        Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> "Incoming call"
        Call.State.Connected, Call.State.StreamsRunning -> "Connected"
        Call.State.Pausing, Call.State.Paused -> "On hold"
        Call.State.PausedByRemote -> "Held by other side"
        Call.State.Resuming -> "Resuming…"
        else -> s.name
    }

    /** Draws the call screen from the current list of calls (handles call waiting too). */
    private fun render() {
        val live = SipManager.liveCalls()
        val main = SipManager.activeCall()
        if (live.isEmpty() || main == null) { showDialer(); return }

        dialerGroup.visibility = View.GONE
        callGroup.visibility = View.VISIBLE
        bottomNav.visibility = View.GONE

        val st = main.state
        val mainIncoming = SipManager.isIncomingState(st)
        val waiting = SipManager.incomingCall()

        tvCallInfo.text = nameOf(main)
        tvCallState.text = label(st)
        btnAnswer.visibility = if (mainIncoming) View.VISIBLE else View.GONE

        if (waiting != null && !mainIncoming) {
            waitingGroup.visibility = View.VISIBLE
            tvWaiting.text = "Waiting call: ${nameOf(waiting)}"
        } else {
            waitingGroup.visibility = View.GONE
        }

        val inCall = st in setOf(
            Call.State.Connected, Call.State.StreamsRunning, Call.State.Pausing, Call.State.Paused,
            Call.State.PausedByRemote, Call.State.Resuming, Call.State.Updating, Call.State.UpdatedByRemote
        )
        controls.visibility = if (inCall) View.VISIBLE else View.GONE
        btnHold.text = if (st == Call.State.Paused || st == Call.State.Pausing) "Resume" else "Hold"
        val talking = live.count { !SipManager.isIncomingState(it.state) }
        btnSwap.visibility = if (talking >= 2) View.VISIBLE else View.GONE
        btnAddCall.visibility = if (inCall && talking < 2) View.VISIBLE else View.GONE
        btnRecord.text = if (SipManager.isRecording()) "Stop rec" else "Record"
    }

    private fun setNavSelected(selectedId: Int) {
        val ids = listOf(R.id.navDial, R.id.navRecents, R.id.navContacts, R.id.navSettings)
        ids.forEach { id ->
            val item = findViewById<View>(id) as? android.widget.LinearLayout ?: return@forEach
            item.background = if (id == selectedId) {
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_nav_selected)
            } else null
            item.alpha = if (id == selectedId) 1f else 0.88f
            for (index in 0 until item.childCount) {
                (item.getChildAt(index) as? TextView)?.setTextColor(
                    if (id == selectedId) android.graphics.Color.WHITE
                    else android.graphics.Color.rgb(100, 116, 139)
                )
            }
        }
    }

    private fun updateReg() {
        tvReg.text = SipManager.lastRegistration + if (Prefs.dnd) " · DND on" else ""
    }

    private fun showAddCallDialog() {
        val et = EditText(this).apply { hint = "Number"; inputType = InputType.TYPE_CLASS_PHONE }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle("Add call")
            .setMessage("The current call will be put on hold.")
            .setView(box)
            .setPositiveButton("Call") { _, _ -> SipManager.addCall(et.text.toString().trim()) }
            .setNeutralButton("Contacts") { _, _ -> pickContactForCall() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickContactForCall() {
        val cs = Store.contacts()
        if (cs.isEmpty()) {
            Toast.makeText(this, "No contacts yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Add call")
            .setItems(cs.map { "${it.name}  ${it.number}" }.toTypedArray()) { _, i ->
                SipManager.addCall(cs[i].number)
            }
            .show()
    }

    // ---- menu ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_dnd)?.title =
            if (Prefs.dnd) "Do Not Disturb: ON" else "Do Not Disturb: off"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        R.id.menu_dnd -> { Prefs.dnd = !Prefs.dnd; invalidateOptionsMenu(); updateReg(); true }
        R.id.menu_recordings -> { startActivity(Intent(this, RecordingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }
}
