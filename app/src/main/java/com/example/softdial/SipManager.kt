package com.example.softdial

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object SipManager {
    private var core: Core? = null
    private lateinit var appContext: Context

    @Volatile var lastRegistration: String = "No account"
        private set

    private val regStates = ConcurrentHashMap<String, String>()
    private var pendingResume: Call? = null
    private var pendingDial: String? = null
    private val handler = Handler(Looper.getMainLooper())
    /** callId -> "dnd" or "fwd" for calls handled automatically */
    private val autoNotes = ConcurrentHashMap<String, String>()

    private val regListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val callListeners = CopyOnWriteArrayList<(Call.State, Call) -> Unit>()

    fun addRegListener(l: (String) -> Unit) { regListeners.add(l) }
    fun removeRegListener(l: (String) -> Unit) { regListeners.remove(l) }
    fun addCallListener(l: (Call.State, Call) -> Unit) { callListeners.add(l) }
    fun removeCallListener(l: (Call.State, Call) -> Unit) { callListeners.remove(l) }

    private fun key(user: String, domain: String) = "$user@$domain"

    private fun updateSummary() {
        val accs = Store.accounts()
        lastRegistration = if (accs.isEmpty()) appContext.getString(R.string.main_no_account)
        else accs.joinToString(" · ") { "${it.label}: ${regStates[key(it.user, it.server)] ?: "…"}" }
        regListeners.forEach { it(lastRegistration) }
    }

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core, account: Account, state: RegistrationState, message: String
        ) {
            val id = account.params.identityAddress
            regStates[key(id?.username ?: "", id?.domain ?: "")] = state.name
            updateSummary()
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            if (state == Call.State.IncomingReceived && applyRules(core, call)) {
                return   // rejected or forwarded automatically: do not ring
            }
            if (state == Call.State.StreamsRunning && Prefs.autoRecord && !call.isRecording) {
                call.startRecording()
            }
            // swap: once the running call is on hold, resume the other one
            if (state == Call.State.Paused) {
                pendingDial?.let { n -> pendingDial = null; this@SipManager.call(n) }
                val p = pendingResume
                if (p != null && p != call) {
                    pendingResume = null
                    p.resume()
                }
            }
            if (state == Call.State.End || state == Call.State.Error || state == Call.State.Released) {
                core.isMicEnabled = true
                pendingDial = null
            }
            if (state == Call.State.Released) {
                call.callLog?.let { log ->
                    val num = log.remoteAddress.username ?: log.remoteAddress.asStringUriOnly()
                    val note = log.callId?.let { autoNotes[it] } ?: ""
                    Store.addEntry(
                        Store.Entry(
                            num,
                            log.dir == Call.Dir.Incoming,
                            log.status == Call.Status.Missed || note == "dnd",
                            log.duration,
                            System.currentTimeMillis(),
                            note
                        )
                    )
                }
            }
            callListeners.forEach { it(state, call) }
            if (state == Call.State.Released) call.callLog?.callId?.let { autoNotes.remove(it) }
        }
    }

    fun init(context: Context) {
        if (core != null) return
        appContext = context.applicationContext
        Prefs.init(appContext)
        Store.init(appContext)
        val factory = Factory.instance()
        factory.setDebugMode(true, "SoftDial")
        core = factory.createCore(null, null, appContext).apply {
            isNativeRingingEnabled = true
            isVibrationOnIncomingCallEnabled = true
            isEchoCancellationEnabled = true
            addListener(listener)
            start()
        }
        register()
    }

    /** Registers every saved account. Call again after accounts or settings change. */
    fun register() {
        val c = core ?: return
        val factory = Factory.instance()

        c.clearAllAuthInfo()
        c.clearAccounts()
        regStates.clear()
        c.isPushNotificationEnabled = Prefs.pushEnabled

        val selected = Store.selectedAccount()
        var defaultAcc: Account? = null

        for (a in Store.accounts()) {
            c.addAuthInfo(factory.createAuthInfo(a.user, null, a.pass, null, null, a.server))

            val params = c.createAccountParams()
            params.identityAddress = factory.createAddress("sip:${a.user}@${a.server}")
            val serverAddr = factory.createAddress("sip:${a.server}")
            serverAddr?.transport = when (a.transport) {
                "tcp" -> TransportType.Tcp
                "tls" -> TransportType.Tls
                else -> TransportType.Udp
            }
            params.serverAddress = serverAddr
            params.isRegisterEnabled = true

            // Push (FCM): only works if the SIP server/proxy supports push notifications
            if (Prefs.pushEnabled && Prefs.fcmToken.isNotBlank() && Push.available(appContext)) {
                try {   // called by name so a different SDK property name cannot break the build
                    params.javaClass.getMethod("setPushNotificationAllowed", java.lang.Boolean.TYPE)
                        .invoke(params, true)
                } catch (e: Exception) { /* push stays off */ }
                params.pushNotificationConfig?.let {
                    it.provider = "fcm"
                    it.param = Push.projectId(appContext)
                    it.prid = Prefs.fcmToken
                }
            }

            val acc = c.createAccount(params)
            c.addAccount(acc)
            if (a.id == selected?.id) defaultAcc = acc
        }
        defaultAcc?.let { c.defaultAccount = it }
        updateSummary()
    }

    /** Registration state name of an account: "Ok", "Failed", "Progress", ... or null if unknown. */
    fun regStateOf(a: Store.SipAccount): String? = regStates[key(a.user, a.server)]

    fun isOnline(a: Store.SipAccount?): Boolean = a != null && regStateOf(a) == "Ok"

    private fun coreAccountFor(a: Store.SipAccount): Account? =
        core?.accountList?.firstOrNull {
            val id = it.params.identityAddress
            id?.username == a.user && id?.domain == a.server
        }

    /** Call after the user picks another account for outgoing calls. */
    fun applySelected() {
        val a = Store.selectedAccount() ?: return
        coreAccountFor(a)?.let { core?.defaultAccount = it }
        updateSummary()
    }

    // ---- calls ----

    // ---- Do Not Disturb and forwarding (done by this app, only while it is running) ----

    /** @return true if the call was rejected or forwarded and must not ring. */
    private fun applyRules(core: Core, call: Call): Boolean {
        val id = call.callLog?.callId ?: return false
        val num = call.remoteAddress.username ?: ""
        val busy = liveCalls().any { !isIncomingState(it.state) }

        if (Store.isBlocked(num)) {
            autoNotes[id] = "blocked"
            call.decline(Reason.Declined)
            return true
        }

        when (Prefs.fwdMode) {
            "always" -> if (forward(call, id)) return true
            "busy" -> if (busy && forward(call, id)) return true
        }
        if (Prefs.dnd && !(Prefs.dndAllowContacts && Store.nameFor(num) != null)) {
            autoNotes[id] = "dnd"
            call.decline(Reason.Declined)
            return true
        }
        if (Prefs.fwdMode == "noanswer") {
            handler.postDelayed({
                try {
                    if (isIncomingState(call.state)) forward(call, id)
                } catch (e: Exception) { /* call already gone */ }
            }, Prefs.fwdDelay.coerceAtLeast(5) * 1000L)
        }
        return false
    }

    private fun forward(call: Call, id: String): Boolean {
        val target = Prefs.fwdNumber.trim()
        if (target.isEmpty()) return false
        val domain = call.toAddress?.domain ?: Store.selectedAccount()?.server ?: return false
        val uri = if (target.contains("@")) "sip:$target" else "sip:$target@$domain"
        val addr = Factory.instance().createAddress(uri) ?: return false
        autoNotes[id] = "fwd"
        call.redirectTo(addr)
        return true
    }

    /** "", "dnd" or "fwd" */
    fun autoNote(call: Call): String = call.callLog?.callId?.let { autoNotes[it] } ?: ""

    /** Second call: puts the running call on hold, then dials. */
    fun addCall(number: String) {
        if (number.isBlank()) return
        val running = liveCalls().firstOrNull {
            !isIncomingState(it.state) &&
                (it.state == Call.State.StreamsRunning || it.state == Call.State.Connected)
        }
        if (running == null) {
            call(number)
        } else {
            pendingDial = number
            running.pause()
        }
    }

    fun isIncomingState(s: Call.State) =
        s == Call.State.IncomingReceived || s == Call.State.IncomingEarlyMedia

    fun liveCalls(): List<Call> = core?.calls?.filter {
        it.state != Call.State.End && it.state != Call.State.Error && it.state != Call.State.Released
    } ?: emptyList()

    fun incomingCall(): Call? = liveCalls().firstOrNull { isIncomingState(it.state) }

    /** The call shown on screen: prefers one that is not on hold, then any, then a ringing one. */
    fun activeCall(): Call? {
        val others = liveCalls().filter { !isIncomingState(it.state) }
        return others.firstOrNull { it.state != Call.State.Paused && it.state != Call.State.Pausing }
            ?: others.firstOrNull()
            ?: incomingCall()
    }

    fun call(number: String) {
        val c = core ?: return
        if (number.isBlank()) return
        val acc = Store.selectedAccount()
        if (acc == null && !number.contains("@")) {
            Toast.makeText(appContext, R.string.add_account_first, Toast.LENGTH_LONG).show()
            return
        }
        val uri = if (number.contains("@")) "sip:$number" else "sip:$number@${acc?.server}"
        val addr = Factory.instance().createAddress(uri) ?: return
        val params = c.createCallParams(null) ?: return
        params.recordFile = newRecordingPath(number)
        acc?.let { a -> coreAccountFor(a)?.let { params.account = it } }
        c.inviteAddressWithParams(addr, params)
    }

    fun answer() {
        val c = core ?: return
        val call = incomingCall() ?: return
        // call waiting: put the current call on hold first
        liveCalls().filter {
            !isIncomingState(it.state) &&
                (it.state == Call.State.StreamsRunning || it.state == Call.State.Connected)
        }.forEach { it.pause() }
        val params = c.createCallParams(call) ?: return
        params.recordFile = newRecordingPath(call.remoteAddress.username ?: "unknown")
        call.acceptWithParams(params)
    }

    fun declineIncoming() { incomingCall()?.terminate() }

    fun hangUp() { activeCall()?.terminate() }

    fun toggleHold() {
        val call = activeCall() ?: return
        when (call.state) {
            Call.State.Paused -> call.resume()
            Call.State.StreamsRunning, Call.State.Connected -> call.pause()
            else -> {}
        }
    }

    /** With two calls: put the running one on hold and resume the held one. */
    fun swap() {
        val calls = liveCalls().filter { !isIncomingState(it.state) }
        val running = calls.firstOrNull {
            it.state == Call.State.StreamsRunning || it.state == Call.State.Connected
        }
        val held = calls.firstOrNull { it.state == Call.State.Paused } ?: return
        if (running != null) {
            pendingResume = held
            running.pause()
        } else {
            held.resume()
        }
    }

    fun sendDtmf(key: Char) { activeCall()?.sendDtmf(key) }

    /** @return true if the mic is now muted */
    fun toggleMute(): Boolean {
        val c = core ?: return false
        c.isMicEnabled = !c.isMicEnabled
        return !c.isMicEnabled
    }

    private val routeTypes = setOf("Earpiece", "Speaker", "Bluetooth", "Headset", "Headphones")

    /** Audio outputs that can be chosen during a call (earpiece, speaker, bluetooth, headset). */
    fun audioRoutes(): List<AudioDevice> =
        core?.audioDevices?.filter { it.type.name in routeTypes } ?: emptyList()

    fun currentRoute(): AudioDevice? = activeCall()?.outputAudioDevice

    fun setRoute(d: AudioDevice) {
        val call = activeCall() ?: return
        call.outputAudioDevice = d
        if (d.type.name == "Bluetooth" || d.type.name == "Headset") {
            try {   // microphone follows the headset (called by name, optional on some SDK versions)
                call.javaClass.getMethod("setInputAudioDevice", AudioDevice::class.java).invoke(call, d)
            } catch (e: Exception) { /* output only */ }
        }
    }

    /** @return true if speaker is now on */
    fun toggleSpeaker(): Boolean {
        val c = core ?: return false
        val call = activeCall() ?: return false
        val usingSpeaker = call.outputAudioDevice?.type == AudioDevice.Type.Speaker
        val wanted = if (usingSpeaker) AudioDevice.Type.Earpiece else AudioDevice.Type.Speaker
        c.audioDevices.firstOrNull { it.type == wanted }?.let { call.outputAudioDevice = it }
        return !usingSpeaker
    }

    /** @return true if recording is now running */
    fun toggleRecording(): Boolean {
        val call = activeCall() ?: return false
        if (call.isRecording) call.stopRecording() else call.startRecording()
        return call.isRecording
    }

    fun isRecording(): Boolean = activeCall()?.isRecording == true

    fun recordingsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    private fun newRecordingPath(remote: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = remote.replace(Regex("[^0-9+A-Za-z]"), "_")
        return File(recordingsDir(appContext), "${stamp}_$safe.wav").absolutePath
    }
}
