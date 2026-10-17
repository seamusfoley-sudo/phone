package ie.spnvoip.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service whose only job is to keep the process alive so the SIP
 * registration survives. It does no work of its own: no timers, no wake locks,
 * no polling. The "idle" notification is silent and low priority.
 */
class SipService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var inCallMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        goForeground(statusNotification(SipManager.registration.value, SipManager.call.value), inCall = false)

        scope.launch {
            combine(SipManager.registration, SipManager.call) { r, c -> r to c }.collect { (reg, call) ->
                val nm = NotificationManagerCompat.from(this@SipService)
                when (call.phase) {
                    CallPhase.Incoming -> postSafely(nm, ID_INCOMING, incomingNotification(call.remote))
                    else -> nm.cancel(ID_INCOMING)
                }
                if (call.phase == CallPhase.Idle && inCallMode) {
                    goForeground(statusNotification(reg, call), inCall = false)
                } else {
                    postSafely(nm, ID_STATUS, statusNotification(reg, call))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_IN_CALL -> goForeground(
                statusNotification(SipManager.registration.value, SipManager.call.value), inCall = true
            )
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Idle = specialUse only. During a call we add phoneCall|microphone so audio keeps working in the background. */
    private fun goForeground(n: Notification, inCall: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            if (inCall) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= 30) {
            if (inCall) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else 0
        } else 0
        try {
            ServiceCompat.startForeground(this, ID_STATUS, n, type)
            inCallMode = inCall
        } catch (e: Exception) {
            Log.w("Spnvoip", "startForeground failed: ${e.message}")
            if (inCall) ServiceCompat.startForeground(this, ID_STATUS, n, idleType())
        }
    }

    private fun idleType() =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    // ---------- notifications ----------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, getString(R.string.channel_status), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the SIP engine plays the device ringtone
            }
        )
    }

    private fun openAppIntent(action: String? = null): PendingIntent =
        PendingIntent.getActivity(
            this, action?.hashCode() ?: 0,
            Intent(this, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(this, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun statusNotification(reg: RegStatus, call: CallUi): Notification {
        val inCall = call.phase == CallPhase.Active || call.phase == CallPhase.Outgoing
        val text = when {
            call.phase == CallPhase.Active -> getString(R.string.in_call_with, call.remote)
            call.phase == CallPhase.Outgoing -> getString(R.string.calling, call.remote)
            else -> getString(reg.label())
        }
        return NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(if (inCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent())
            .apply { if (inCall) addAction(0, getString(R.string.hang_up), broadcast(ACTION_HANGUP)) }
            .build()
    }

    private fun incomingNotification(remote: String): Notification {
        val caller = Person.Builder().setName(remote).setImportant(true).build()
        return NotificationCompat.Builder(this, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setContentTitle(remote)
            .setContentText(getString(R.string.incoming_call))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(openAppIntent(), true)
            .setContentIntent(openAppIntent())
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller, broadcast(ACTION_DECLINE), openAppIntent(ACTION_ANSWER)
                )
            )
            .build()
    }

    private fun postSafely(nm: NotificationManagerCompat, id: Int, n: Notification) {
        try { nm.notify(id, n) } catch (_: SecurityException) { /* notifications permission denied */ }
    }

    companion object {
        const val ACTION_IN_CALL = "ie.spnvoip.phone.IN_CALL"
        const val ACTION_STOP = "ie.spnvoip.phone.STOP"
        const val ACTION_ANSWER = "ie.spnvoip.phone.ANSWER"
        const val ACTION_DECLINE = "ie.spnvoip.phone.DECLINE"
        const val ACTION_HANGUP = "ie.spnvoip.phone.HANGUP"

        private const val CH_STATUS = "status"
        private const val CH_INCOMING = "incoming"
        private const val ID_STATUS = 1
        private const val ID_INCOMING = 2

        fun start(context: Context, action: String? = null) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, SipService::class.java).setAction(action)
                )
            } catch (e: Exception) {
                // Android may refuse a start from the background; the service restarts
                // next time the app or the boot receiver runs.
                Log.w("Spnvoip", "Could not start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SipService::class.java))
        }
    }
}

fun RegStatus.label(): Int = when (this) {
    RegStatus.NotConfigured -> R.string.status_not_configured
    RegStatus.Connecting -> R.string.status_connecting
    RegStatus.Registered -> R.string.status_registered
    RegStatus.Failed -> R.string.status_failed
    RegStatus.Offline -> R.string.status_offline
}
