package com.example.softdial

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import androidx.core.content.ContextCompat
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
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
    private lateinit var bottomNav: View
    private lateinit var tvSugTitle: TextView
    private lateinit var suggestList: ListView
    private lateinit var sugAdapter: EntryAdapter
    private val sugs = mutableListOf<EntryRow>()

    private fun mkSug(name: String, number: String, tag: String) =
        EntryRow(name, "$number  ·  $tag", number)
    private lateinit var tvNumber: TextView
    private lateinit var tvContact: TextView
    private lateinit var tvCallInfo: TextView
    private lateinit var tvCallState: TextView
    private lateinit var tvDtmf: TextView
    private lateinit var tvWaiting: TextView
    private lateinit var dialerGroup: View
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

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            CallService.start(this)
            if (result[Manifest.permission.READ_CONTACTS] == true) importPhoneContactsOnce()
        }

    /** First time the contacts permission is granted: copy the phone book into the app. */
    private fun importPhoneContactsOnce() {
        if (Prefs.contactsImported) return
        Prefs.contactsImported = true
        Thread {
            val n = PhoneBook.importAll(this)
            runOnUiThread {
                Toast.makeText(this, "Imported $n contacts", Toast.LENGTH_SHORT).show()
                refreshSuggestions()
            }
        }.start()
    }

    private val regCb: (String) -> Unit = { _ -> runOnUiThread { updateReg() } }
    private val callCb: (Call.State, Call) -> Unit = { _, _ -> runOnUiThread { render(); BottomNav.refreshBadge(this) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            BottomNav.wrap(this, layoutInflater.inflate(R.layout.activity_main, null), BottomNav.DIALER)
        )
        bottomNav = findViewById(R.id.bottomNav)
        SipManager.init(this)
        Push.refreshToken(this)

        tvReg = findViewById(R.id.tvReg)
        tvSugTitle = findViewById(R.id.tvSugTitle)
        suggestList = findViewById(R.id.suggestList)
        sugAdapter = EntryAdapter(this, sugs) { r -> r.number?.let { SipManager.call(it) } }
        suggestList.adapter = sugAdapter
        suggestList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val s = sugs.getOrNull(pos) ?: return@OnItemClickListener
            val num = s.number ?: return@OnItemClickListener
            number.clear()
            number.append(num.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
            refreshNumber()
        }
        tvNumber = findViewById(R.id.tvNumber)
        tvContact = findViewById(R.id.tvContact)
        tvCallInfo = findViewById(R.id.tvCallInfo)
        tvCallState = findViewById(R.id.tvCallState)
        tvDtmf = findViewById(R.id.tvDtmf)
        tvWaiting = findViewById(R.id.tvWaiting)
        dialerGroup = findViewById(R.id.dialerGroup)
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

        findViewById<View>(R.id.btnCall).setOnClickListener { SipManager.call(number.toString()) }
        findViewById<View>(R.id.btnBack).apply {
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

        btnAnswer.setOnClickListener { SipManager.answer() }
        findViewById<Button>(R.id.btnAnswerWaiting).setOnClickListener { SipManager.answer() }
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

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
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
            SipManager.answer()
        }
    }

    override fun onStart() {
        super.onStart()
        SipManager.addRegListener(regCb)
        SipManager.addCallListener(callCb)
        updateReg()
        refreshFrom()
        refreshSuggestions()
        BottomNav.refreshBadge(this)
        render()
    }

    override fun onStop() {
        SipManager.removeRegListener(regCb)
        SipManager.removeCallListener(callCb)
        super.onStop()
    }

    // ---- dialpad ----

    private fun buildPad() {
        fillPad(findViewById(R.id.pad), 60, 26f, true) { number.append(it); refreshNumber() }
        fillPad(dtmfPad, 52, 20f, false) {
            SipManager.sendDtmf(it[0])
            tvDtmf.append(it)
        }
    }

    private val keyLetters = mapOf(
        "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
        "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+"
    )

    private fun keyLabel(k: String): CharSequence {
        val sub = keyLetters[k] ?: return k
        val sb = SpannableStringBuilder(k).append("\n").append(sub)
        sb.setSpan(RelativeSizeSpan(0.36f), k.length + 1, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    /** Round keys with small letters under the digits. */
    private fun fillPad(
        pad: GridLayout, diameterDp: Int, sizeSp: Float, plusOnZero: Boolean, onKey: (String) -> Unit
    ) {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val d = dp(diameterDp)
        val m = dp(4)
        for (k in keys) {
            val b = Button(this).apply {
                text = keyLabel(k)
                textSize = sizeSp
                setAllCaps(false)
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setLineSpacing(0f, 0.85f)
                stateListAnimator = null
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_key)
                setOnClickListener { onKey(k) }
                if (plusOnZero && k == "0") setOnLongClickListener { number.append("+"); refreshNumber(); true }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = d
                height = d
                setGravity(Gravity.CENTER)
                setMargins(m, m, m, m)
            }
            pad.addView(b, lp)
        }
    }

    private fun refreshNumber() {
        tvNumber.text = number.toString()
        tvContact.text = Store.nameFor(number.toString()) ?: ""
        refreshSuggestions()
    }

    private fun digitsOf(s: String) = s.filter { it.isDigit() || it == '+' }

    /** Letters on a phone keypad: "Jon" -> "566". */
    private fun t9(name: String): String = name.lowercase().map {
        when (it) {
            in 'a'..'c' -> '2'; in 'd'..'f' -> '3'; in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'; in 'm'..'o' -> '6'; in 'p'..'s' -> '7'
            in 't'..'v' -> '8'; in 'w'..'z' -> '9'
            ' ' -> ' '
            else -> if (it.isDigit()) it else '\u0000'
        }
    }.filter { it != '\u0000' }.joinToString("")

    private fun nameMatchesKeys(name: String, keys: String): Boolean {
        if (keys.length < 2 || !keys.all { it.isDigit() }) return false
        return t9(name).split(" ").any { it.startsWith(keys) }
    }

    /** Middle area: recent calls when empty, matching contacts / numbers while typing. */
    private fun refreshSuggestions() {
        val q = number.toString()
        sugs.clear()
        if (q.isEmpty()) {
            val seen = mutableSetOf<String>()
            for (e in Store.history()) {
                if (!seen.add(digitsOf(e.number))) continue
                val tag = if (e.missed) "missed" else if (e.incoming) "incoming" else "outgoing"
                sugs.add(mkSug(Store.nameFor(e.number) ?: e.number, e.number, tag))
                if (sugs.size >= 8) break
            }
            tvSugTitle.text = if (sugs.isEmpty()) "No recent calls" else "Recent"
        } else {
            val digits = digitsOf(q)
            val used = mutableSetOf<String>()
            if (digits.isNotEmpty()) {
                for (c in Store.contacts()) {
                    val nd = digitsOf(c.number)
                    if (nd.contains(digits) || nameMatchesKeys(c.name, digits)) {
                        sugs.add(mkSug(c.name, c.number, "contact")); used.add(nd)
                    }
                }
                for (e in Store.history()) {
                    val nd = digitsOf(e.number)
                    if (nd.contains(digits) && used.add(nd)) sugs.add(mkSug(e.number, e.number, "recent"))
                }
            }
            tvSugTitle.text = if (sugs.isEmpty()) "No matches" else "Matches"
        }
        sugAdapter.notifyDataSetChanged()
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
}
