package ie.spnvoip.phone

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

enum class RegStatus { NotConfigured, Connecting, Registered, Failed, Offline }

enum class CallPhase { Idle, Incoming, Outgoing, Active, Ended }

data class CallUi(
    val phase: CallPhase = CallPhase.Idle,
    val remote: String = "",
    val connectedAt: Long = 0L,
    val muted: Boolean = false,
    val speaker: Boolean = false,
)

/**
 * Owns the SIP engine and the single account.
 *
 * Battery strategy for staying registered:
 *  - TCP by default: NAT keeps TCP mappings for minutes, so a tiny CRLF keep-alive
 *    every ~2 min is enough (UDP would need one every ~30 s).
 *  - Long REGISTER expiry (1 h) so re-registrations are rare; keep-alives hold the NAT open.
 *  - No polling, timers or wake locks in app code. The engine only wakes on its
 *    keep-alive tick or on incoming packets, and reacts to network changes itself
 *    (Android connectivity callbacks) rather than checking periodically.
 *  - When no UI is visible the engine is put into background mode, which slows its
 *    internal housekeeping.
 *  - Video, presence and push are switched off so nothing else generates traffic.
 */
object SipManager {
    private const val TAG = "Spnvoip"
    private const val REGISTER_EXPIRY_SECONDS = 3600

    lateinit var core: Core
        private set
    private lateinit var store: AccountStore

    private val _reg = MutableStateFlow(RegStatus.NotConfigured)
    val registration: StateFlow<RegStatus> = _reg.asStateFlow()

    private val _call = MutableStateFlow(CallUi())
    val call: StateFlow<CallUi> = _call.asStateFlow()

    private val _account = MutableStateFlow<AccountConfig?>(null)
    val account: StateFlow<AccountConfig?> = _account.asStateFlow()

    fun init(context: Context) {
        if (::core.isInitialized) return
        store = AccountStore(context)

        val factory = Factory.instance()
        factory.setDebugMode(false, TAG)
        // null config path = nothing persisted by the engine; AccountStore is the source of truth.
        core = factory.createCore(null, null, context)

        core.isVideoCaptureEnabled = false
        core.isVideoDisplayEnabled = false
        core.isNativeRingingEnabled = true          // use the phone's own ringtone
        core.isKeepAliveEnabled = true
        core.config.setInt("sip", "tcp_tls_keepalive", 1) // CRLF keep-alives on TCP/TLS
        core.config.setBool("sip", "use_info", false)
        core.config.setInt("misc", "max_calls", 2)

        core.addListener(listener)
        core.start()

        _account.value = store.load()
        _account.value?.let { applyToCore(it) }
    }

    // ---------- account (exactly one) ----------

    fun saveAccount(cfg: AccountConfig) {
        store.save(cfg)
        _account.value = cfg
        applyToCore(cfg)
    }

    fun removeAccount() {
        store.clear()
        _account.value = null
        core.defaultAccount?.let { acc ->
            acc.params.clone().apply { isRegisterEnabled = false }.also { acc.params = it }
        }
        core.clearAccounts()
        core.clearAllAuthInfo()
        _reg.value = RegStatus.NotConfigured
    }

    private fun applyToCore(cfg: AccountConfig) {
        // Single-account rule: wipe anything that exists before adding the new one.
        core.clearAccounts()
        core.clearAllAuthInfo()

        val factory = Factory.instance()
        val host = cfg.domain.substringBefore(':')

        core.config.setInt("sip", "keepalive_period", cfg.keepAliveSeconds * 1000)

        val auth = factory.createAuthInfo(cfg.username, null, cfg.password, null, null, host, null)
        val params = core.createAccountParams()
        params.identityAddress = factory.createAddress("sip:${cfg.username}@$host")
        params.serverAddress = factory.createAddress("sip:${cfg.domain}")?.apply {
            transport = when (cfg.transport) {
                Transport.TCP -> TransportType.Tcp
                Transport.UDP -> TransportType.Udp
                Transport.TLS -> TransportType.Tls
            }
        }
        params.isRegisterEnabled = true
        params.expires = REGISTER_EXPIRY_SECONDS
        params.isPublishEnabled = false
        params.pushNotificationAllowed = false
        params.remotePushNotificationAllowed = false

        val account = core.createAccount(params)
        core.addAuthInfo(auth)
        core.addAccount(account)
        core.defaultAccount = account
        _reg.value = RegStatus.Connecting
    }

    // ---------- app visibility (battery) ----------

    fun onAppForeground() {
        if (::core.isInitialized) core.enterForeground()
    }

    fun onAppBackground() {
        if (::core.isInitialized) core.enterBackground()
    }

    // ---------- calls ----------

    fun dial(number: String) {
        if (number.isBlank()) return
        val address = core.interpretUrl(number.trim(), false) ?: return
        val params = core.createCallParams(null) ?: return
        params.mediaEncryption = MediaEncryption.None
        params.isVideoEnabled = false
        core.inviteAddressWithParams(address, params)
    }

    fun answer() {
        core.calls.firstOrNull { it.state == Call.State.IncomingReceived }?.accept()
    }

    fun hangUp() {
        val c = core.currentCall ?: core.calls.firstOrNull() ?: return
        if (c.state == Call.State.IncomingReceived) c.decline(Reason.Declined) else c.terminate()
    }

    fun toggleMute() {
        val c = core.currentCall ?: return
        c.microphoneMuted = !c.microphoneMuted
        _call.value = _call.value.copy(muted = c.microphoneMuted)
    }

    fun toggleSpeaker() {
        val c = core.currentCall ?: return
        val wantSpeaker = !_call.value.speaker
        val type = if (wantSpeaker) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        core.audioDevices.firstOrNull {
            it.type == type && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        }?.let {
            c.outputAudioDevice = it
            _call.value = _call.value.copy(speaker = wantSpeaker)
        }
    }

    fun sendDtmf(digit: Char) {
        core.currentCall?.sendDtmf(digit)
    }

    // ---------- engine callbacks ----------

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core, account: Account, state: RegistrationState?, message: String
        ) {
            Log.i(TAG, "Registration: $state $message")
            _reg.value = when (state) {
                RegistrationState.Ok -> RegStatus.Registered
                RegistrationState.Progress, RegistrationState.Refreshing -> RegStatus.Connecting
                RegistrationState.Failed -> RegStatus.Failed
                else -> if (_account.value == null) RegStatus.NotConfigured else RegStatus.Offline
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
            val addr = call.remoteAddress
            val remote = addr.displayName?.takeIf { it.isNotBlank() } ?: addr.username ?: addr.asStringUriOnly()
            val cur = _call.value
            _call.value = when (state) {
                Call.State.IncomingReceived ->
                    CallUi(CallPhase.Incoming, remote)
                Call.State.OutgoingInit, Call.State.OutgoingProgress,
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia ->
                    CallUi(CallPhase.Outgoing, remote)
                Call.State.Connected, Call.State.StreamsRunning ->
                    cur.copy(
                        phase = CallPhase.Active, remote = remote,
                        connectedAt = if (cur.phase == CallPhase.Active) cur.connectedAt else System.currentTimeMillis(),
                    )
                Call.State.End, Call.State.Error, Call.State.Released ->
                    if (core.callsNb == 0) CallUi(CallPhase.Ended, remote) else cur
                else -> cur
            }
            if (_call.value.phase == CallPhase.Ended) {
                // Brief "Call ended" then back to idle.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (_call.value.phase == CallPhase.Ended) _call.value = CallUi()
                }, 1500)
            }
        }
    }
}
