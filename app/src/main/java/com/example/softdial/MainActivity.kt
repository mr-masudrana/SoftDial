package com.example.softdial

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Handler
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Looper
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
import android.widget.ImageButton
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
    private lateinit var tvCallInfo: TextView
    private lateinit var tvCallNumber: TextView
    private lateinit var tvCallAvatar: TextView
    private lateinit var callRing: View
    private lateinit var lblMute: TextView
    private lateinit var lblSpeaker: TextView
    private lateinit var lblRecord: TextView
    private lateinit var lblHold: TextView
    private lateinit var cellAddCall: View
    private lateinit var cellSwap: View
    private val uiHandler = Handler(Looper.getMainLooper())

    // "call ended" screen shown for a moment after the last call finishes
    private var lastName = ""
    private var lastNum = ""
    private var endedDuration = 0
    private var endedFailed = false
    private var endedSkip = false
    private var endedShown = false
    private val endRunnable = Runnable { showDialer() }
    private var pulse: ObjectAnimator? = null
    private var keypadOpen = false
    private lateinit var avatarFrame: View
    private lateinit var btnHideKeypad: View
    private lateinit var tvCallState: TextView
    private lateinit var tvDtmf: TextView
    private lateinit var tvWaiting: TextView
    private lateinit var dialerGroup: View
    private lateinit var callGroup: View
    private lateinit var controls: View
    private lateinit var waitingGroup: View
    private lateinit var dtmfPad: GridLayout
    private lateinit var btnFrom: Button
    private lateinit var btnAnswer: View
    private lateinit var btnHangup: View
    private lateinit var btnMute: View
    private lateinit var btnSpeaker: ImageButton
    private lateinit var btnRecord: View
    private lateinit var btnKeypad: View
    private lateinit var btnHold: View
    private lateinit var btnSwap: View
    private lateinit var btnAddCall: View

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
                Toast.makeText(this, getString(R.string.imported_contacts, n), Toast.LENGTH_SHORT).show()
                refreshSuggestions()
            }
        }.start()
    }

    private val regCb: (String) -> Unit = { _ -> runOnUiThread { updateReg() } }
    private val callCb: (Call.State, Call) -> Unit = { state, call ->
        runOnUiThread {
            if (state == Call.State.End || state == Call.State.Error) captureEnd(state, call)
            render()
            BottomNav.refreshBadge(this)
        }
    }

    /** Remembers who the call was with and how long it lasted, for the "call ended" screen. */
    private fun captureEnd(state: Call.State, call: Call) {
        try {
            val num = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
            val log = call.callLog
            endedDuration = call.duration
            endedFailed = state == Call.State.Error
            endedSkip = log != null && log.dir == Call.Dir.Incoming && endedDuration == 0
            lastNum = num
            lastName = Store.nameFor(num) ?: num
        } catch (e: Exception) { /* keep previous values */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // splash screen: icon on the app color, kept for a short moment
        val splash = installSplashScreen()
        val t0 = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - t0 < 700 }
        super.onCreate(savedInstanceState)
        setContentView(
            BottomNav.wrap(this, layoutInflater.inflate(R.layout.activity_main, null), BottomNav.DIALER)
        )
        bottomNav = findViewById(R.id.bottomNav)
        SipManager.init(this)
        Push.refreshToken(this)
        if (Store.accounts().isEmpty() && !Prefs.loginSkipped) {
            startActivity(Intent(this, LoginActivity::class.java))   // first run: SIP sign-in page
        }

        tvReg = findViewById(R.id.tvReg)
        tvSugTitle = findViewById(R.id.tvSugTitle)
        suggestList = findViewById(R.id.suggestList)
        sugAdapter = EntryAdapter(this, sugs, R.layout.item_card) { r -> r.number?.let { SipManager.call(it) } }
        suggestList.adapter = sugAdapter
        styleCardList(suggestList)
        suggestList.setOnItemLongClickListener { _, _, pos, _ ->
            val n = sugs.getOrNull(pos)?.number
            if (n != null) showSuggestionMenu(n)
            n != null
        }
        suggestList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val s = sugs.getOrNull(pos) ?: return@OnItemClickListener
            val num = s.number ?: return@OnItemClickListener
            number.clear()
            number.append(num.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
            refreshNumber()
        }
        tvNumber = findViewById(R.id.tvNumber)
        tvCallInfo = findViewById(R.id.tvCallInfo)
        tvCallNumber = findViewById(R.id.tvCallNumber)
        avatarFrame = findViewById(R.id.avatarFrame)
        btnHideKeypad = findViewById(R.id.btnHideKeypad)
        tvCallAvatar = findViewById(R.id.tvCallAvatar)
        callRing = findViewById(R.id.callRing)
        lblMute = findViewById(R.id.lblMute)
        lblSpeaker = findViewById(R.id.lblSpeaker)
        lblRecord = findViewById(R.id.lblRecord)
        lblHold = findViewById(R.id.lblHold)
        cellAddCall = findViewById(R.id.cellAddCall)
        cellSwap = findViewById(R.id.cellSwap)
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
        btnHangup = findViewById(R.id.btnHangup)
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
                KeyFeedback.buzz(this@MainActivity)
                if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
                refreshNumber()
            }
            setOnLongClickListener { number.clear(); refreshNumber(); true }
        }
        btnFrom.setOnClickListener {
            val accs = Store.accounts()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.call_with_title))
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
        btnHangup.setOnClickListener { SipManager.hangUp() }
        btnMute.setOnClickListener {
            val muted = SipManager.toggleMute()
            lblMute.text = getString(if (muted) R.string.btn_unmute else R.string.btn_mute)
            btnMute.isActivated = muted
        }
        btnSpeaker.setOnClickListener {
            val routes = SipManager.audioRoutes()
            if (routes.size <= 2) SipManager.toggleSpeaker() else showRouteDialog(routes)
            uiHandler.postDelayed({ updateRoute() }, 400)
        }
        btnRecord.setOnClickListener {
            val rec = SipManager.toggleRecording()
            lblRecord.text = getString(if (rec) R.string.btn_stop_rec else R.string.btn_record)
            btnRecord.isActivated = rec
        }
        btnKeypad.setOnClickListener { keypadOpen = true; render() }
        btnHideKeypad.setOnClickListener { keypadOpen = false; render() }
        btnHold.setOnClickListener { SipManager.toggleHold() }
        btnSwap.setOnClickListener { SipManager.swap() }
        btnAddCall.setOnClickListener { showAddCallDialog() }

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
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
        stopPulse()
        uiHandler.removeCallbacks(ticker)
        KeyFeedback.release()
        super.onStop()
    }

    // ---- dialpad ----

    private fun buildPad() {
        fillPad(findViewById(R.id.pad), 60, 26f, true) {
            KeyFeedback.press(this, it)
            number.append(it)
            refreshNumber()
        }
        fillPad(dtmfPad, 50, 20f, false) {
            KeyFeedback.press(this, it)
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
                if (plusOnZero && k == "0") setOnLongClickListener { KeyFeedback.buzz(this@MainActivity); number.append("+"); refreshNumber(); true }
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

    /** Long press on a card: copy, add/edit contact, delete, details. */
    private fun showSuggestionMenu(n: String) {
        val contact = Store.contactFor(n)
        val labels = arrayOf(
            getString(R.string.menu_copy_number),
            getString(if (contact != null) R.string.menu_edit_contact else R.string.menu_add_contact),
            getString(R.string.delete),
            getString(R.string.details)
        )
        AlertDialog.Builder(this)
            .setTitle(contact?.name ?: n)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> copyNumber(this, n)
                    1 -> if (contact != null) {
                        showContactDialog(this, contact.number, contact.name, contact.number) { refreshSuggestions() }
                    } else {
                        showContactDialog(this, null, "", n) { refreshSuggestions() }
                    }
                    2 -> if (contact != null) {
                        confirmDeleteContact(contact)
                    } else {
                        Store.deleteEntriesFor(n)
                        refreshSuggestions()
                    }
                    3 -> showNumberDetails(this, n)
                }
            }
            .show()
    }

    private fun confirmDeleteContact(c: Store.Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.del_contact_title)
            .setMessage(c.name + "\n" + c.number)
            .setPositiveButton(R.string.delete) { _, _ -> Store.deleteContact(c.number); refreshSuggestions() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Middle area: recent calls when empty, matching contacts / numbers while typing. */
    private fun refreshSuggestions() {
        val q = number.toString()
        sugs.clear()
        if (q.isEmpty()) {
            val seen = mutableSetOf<String>()
            for (e in Store.history()) {
                if (!seen.add(digitsOf(e.number))) continue
                val tag = getString(if (e.missed) R.string.tag_missed else if (e.incoming) R.string.tag_incoming else R.string.tag_outgoing)
                sugs.add(mkSug(Store.nameFor(e.number) ?: e.number, e.number, tag))
                if (sugs.size >= 8) break
            }
            tvSugTitle.text = getString(if (sugs.isEmpty()) R.string.sug_none_recent else R.string.sug_recent)
        } else {
            val digits = digitsOf(q)
            val used = mutableSetOf<String>()
            if (digits.isNotEmpty()) {
                for (c in Store.contacts()) {
                    val nd = digitsOf(c.number)
                    if (nd.contains(digits) || nameMatchesKeys(c.name, digits)) {
                        sugs.add(mkSug(c.name, c.number, getString(R.string.tag_contact))); used.add(nd)
                    }
                }
                for (e in Store.history()) {
                    val nd = digitsOf(e.number)
                    if (nd.contains(digits) && used.add(nd)) sugs.add(mkSug(e.number, e.number, getString(R.string.tag_recent)))
                }
            }
            tvSugTitle.text = getString(if (sugs.isEmpty()) R.string.sug_none else R.string.sug_matches)
        }
        sugAdapter.notifyDataSetChanged()
    }

    private fun refreshFrom() {
        val accs = Store.accounts()
        btnFrom.visibility = if (accs.size > 1) View.VISIBLE else View.GONE
        btnFrom.text = getString(R.string.call_with_btn, Store.selectedAccount()?.label ?: "")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- screens ----

    private fun showDialer() {
        dialerGroup.visibility = View.VISIBLE
        callGroup.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        supportActionBar?.show()
        keypadOpen = false
        avatarFrame.visibility = View.VISIBLE
        btnHideKeypad.visibility = View.GONE
        lblMute.text = getString(R.string.btn_mute)
        lblSpeaker.text = getString(R.string.btn_speaker)
        lblRecord.text = getString(R.string.btn_record)
        btnMute.isActivated = false
        btnSpeaker.isActivated = false
        btnRecord.isActivated = false
        btnKeypad.isActivated = false
        btnHold.isActivated = false
        stopPulse()
        uiHandler.removeCallbacks(ticker)
        uiHandler.removeCallbacks(endRunnable)
        endedShown = false
        btnHangup.visibility = View.VISIBLE
        dtmfPad.visibility = View.GONE
        tvDtmf.visibility = View.GONE
        tvDtmf.text = ""
    }

    private fun nameOf(call: Call): String {
        val num = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
        return Store.nameFor(num) ?: num
    }

    private fun label(s: Call.State): String = when (s) {
        Call.State.OutgoingInit, Call.State.OutgoingProgress -> getString(R.string.state_calling)
        Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> getString(R.string.state_ringing)
        Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> getString(R.string.state_incoming)
        Call.State.Connected, Call.State.StreamsRunning -> getString(R.string.state_connected)
        Call.State.Pausing, Call.State.Paused -> getString(R.string.state_hold)
        Call.State.PausedByRemote -> getString(R.string.state_held_remote)
        Call.State.Resuming -> getString(R.string.state_resuming)
        else -> s.name
    }

    /** Draws the call screen from the current list of calls (handles call waiting too). */
    private val timedStates = setOf(
        Call.State.Connected, Call.State.StreamsRunning, Call.State.Pausing, Call.State.Paused,
        Call.State.PausedByRemote, Call.State.Resuming, Call.State.Updating, Call.State.UpdatedByRemote
    )

    private val ringingStates = setOf(
        Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging,
        Call.State.OutgoingEarlyMedia, Call.State.IncomingReceived, Call.State.IncomingEarlyMedia
    )

    /** "Ringing…", or the running timer once connected ("On hold · 01:20"). */
    private fun stateText(call: Call): String {
        val st = call.state
        return when {
            st == Call.State.Connected || st == Call.State.StreamsRunning -> fmtTimer(call.duration)
            st in timedStates -> label(st) + " · " + fmtTimer(call.duration)
            else -> label(st)
        }
    }

    /** Brief "Call ended · 02:34" screen, then back to the dialer. */
    private fun showEnded() {
        endedShown = true
        dialerGroup.visibility = View.GONE
        callGroup.visibility = View.VISIBLE
        bottomNav.visibility = View.GONE
        supportActionBar?.hide()
        keypadOpen = false
        avatarFrame.visibility = View.VISIBLE
        btnHideKeypad.visibility = View.GONE

        tvCallAvatar.text = initialOf(lastName)
        tvCallInfo.text = lastName
        tvCallNumber.text = lastNum
        tvCallNumber.visibility = if (lastName == lastNum) View.GONE else View.VISIBLE
        tvCallState.text = when {
            endedFailed -> getString(R.string.state_failed)
            endedDuration > 0 -> getString(R.string.state_ended) + " · " + fmtTimer(endedDuration)
            else -> getString(R.string.state_ended)
        }
        tvCallState.setTextColor(
            ContextCompat.getColor(this, if (endedFailed) R.color.perm_bad else R.color.nav_inactive)
        )
        controls.visibility = View.GONE
        waitingGroup.visibility = View.GONE
        btnAnswer.visibility = View.GONE
        btnHangup.visibility = View.INVISIBLE
        dtmfPad.visibility = View.GONE
        tvDtmf.visibility = View.GONE
        stopPulse()
        uiHandler.removeCallbacks(ticker)
        uiHandler.removeCallbacks(endRunnable)
        uiHandler.postDelayed(endRunnable, 2200)
    }

    private fun routeLabel(t: String): Int = when (t) {
        "Speaker" -> R.string.btn_speaker
        "Bluetooth", "BluetoothA2DP" -> R.string.route_bluetooth
        "Headset", "Headphones" -> R.string.route_headset
        else -> R.string.btn_earpiece
    }

    /** Button under the call shows the current audio output (earpiece / speaker / bluetooth / headset). */
    private fun updateRoute() {
        val t = SipManager.currentRoute()?.type?.name ?: "Earpiece"
        lblSpeaker.text = getString(routeLabel(t))
        btnSpeaker.setImageResource(
            when (t) {
                "Speaker" -> R.drawable.ic_ctl_speaker
                "Bluetooth", "BluetoothA2DP" -> R.drawable.ic_ctl_bluetooth
                "Headset", "Headphones" -> R.drawable.ic_ctl_headset
                else -> R.drawable.ic_call
            }
        )
        btnSpeaker.isActivated = t != "Earpiece"
    }

    private fun showRouteDialog(routes: List<org.linphone.core.AudioDevice>) {
        val current = SipManager.currentRoute()?.type?.name
        val labels = routes.map {
            (if (it.type.name == current) "✓ " else "") + getString(routeLabel(it.type.name))
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_route_title)
            .setItems(labels) { _, i ->
                SipManager.setRoute(routes[i])
                uiHandler.postDelayed({ updateRoute() }, 400)
            }
            .show()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val main = SipManager.activeCall()
            if (main != null && callGroup.visibility == View.VISIBLE) {
                tvCallState.text = stateText(main)
                uiHandler.postDelayed(this, 1000)
            }
        }
    }

    private fun startPulse() {
        callRing.visibility = View.VISIBLE
        if (pulse != null) return
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            callRing,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.35f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.35f),
            PropertyValuesHolder.ofFloat("alpha", 0.7f, 0f)
        ).apply {
            duration = 1300
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        if (::callRing.isInitialized) callRing.visibility = View.INVISIBLE
    }

    /** Draws the call screen from the current list of calls (handles call waiting too). */
    private fun render() {
        val live = SipManager.liveCalls()
        val main = SipManager.activeCall()
        if (live.isEmpty() || main == null) {
            if (callGroup.visibility == View.VISIBLE && !endedSkip) {
                if (!endedShown) showEnded()
            } else if (!endedShown) {
                showDialer()
            }
            return
        }
        if (endedShown) {
            endedShown = false
            uiHandler.removeCallbacks(endRunnable)
        }
        endedSkip = false
        btnHangup.visibility = View.VISIBLE
        tvCallState.setTextColor(ContextCompat.getColor(this, R.color.nav_active))
        lastNum = main.remoteAddress.username ?: main.remoteAddress.asStringUriOnly()
        lastName = Store.nameFor(lastNum) ?: lastNum

        dialerGroup.visibility = View.GONE
        callGroup.visibility = View.VISIBLE
        bottomNav.visibility = View.GONE
        supportActionBar?.hide()

        val st = main.state
        val mainIncoming = SipManager.isIncomingState(st)
        val waiting = SipManager.incomingCall()
        val num = main.remoteAddress.username ?: main.remoteAddress.asStringUriOnly()
        val name = Store.nameFor(num) ?: num

        tvCallAvatar.text = initialOf(name)
        tvCallInfo.text = name
        tvCallNumber.text = num
        tvCallNumber.visibility = if (name == num) View.GONE else View.VISIBLE
        tvCallState.text = stateText(main)
        btnAnswer.visibility = if (mainIncoming) View.VISIBLE else View.GONE

        if (waiting != null && !mainIncoming) {
            waitingGroup.visibility = View.VISIBLE
            tvWaiting.text = getString(R.string.waiting_call, nameOf(waiting))
        } else {
            waitingGroup.visibility = View.GONE
        }

        val inCall = st in timedStates
        if (!inCall) keypadOpen = false
        controls.visibility = if (inCall && !keypadOpen) View.VISIBLE else View.GONE
        // keypad mode: replaces the round controls, avatar shrinks away so everything fits
        avatarFrame.visibility = if (keypadOpen) View.GONE else View.VISIBLE
        if (keypadOpen) tvCallNumber.visibility = View.GONE
        dtmfPad.visibility = if (keypadOpen) View.VISIBLE else View.GONE
        tvDtmf.visibility = if (keypadOpen) View.VISIBLE else View.GONE
        btnHideKeypad.visibility = if (keypadOpen) View.VISIBLE else View.GONE

        val held = st == Call.State.Paused || st == Call.State.Pausing
        lblHold.text = getString(if (held) R.string.btn_resume else R.string.btn_hold)
        btnHold.isActivated = held

        val talking = live.count { !SipManager.isIncomingState(it.state) }
        cellSwap.visibility = if (talking >= 2) View.VISIBLE else View.GONE
        cellAddCall.visibility = if (inCall && talking < 2) View.VISIBLE else View.GONE

        val rec = SipManager.isRecording()
        lblRecord.text = getString(if (rec) R.string.btn_stop_rec else R.string.btn_record)
        btnRecord.isActivated = rec
        updateRoute()

        if (st in ringingStates) startPulse() else stopPulse()
        uiHandler.removeCallbacks(ticker)
        uiHandler.postDelayed(ticker, 1000)
    }

    private fun updateReg() {
        tvReg.text = if (Prefs.dnd) "🔕 " + getString(R.string.dnd_on) else ""
        tvReg.visibility = if (Prefs.dnd) View.VISIBLE else View.GONE
    }

    private fun showAddCallDialog() {
        val et = EditText(this).apply { hint = getString(R.string.number_hint); inputType = InputType.TYPE_CLASS_PHONE }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.addcall_title))
            .setMessage(getString(R.string.addcall_msg))
            .setView(box)
            .setPositiveButton(getString(R.string.call)) { _, _ -> SipManager.addCall(et.text.toString().trim()) }
            .setNeutralButton(getString(R.string.nav_contacts)) { _, _ -> pickContactForCall() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickContactForCall() {
        val cs = Store.contacts()
        if (cs.isEmpty()) {
            Toast.makeText(this, R.string.no_contacts_toast, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.addcall_title))
            .setItems(cs.map { "${it.name}  ${it.number}" }.toTypedArray()) { _, i ->
                SipManager.addCall(cs[i].number)
            }
            .show()
    }
}
