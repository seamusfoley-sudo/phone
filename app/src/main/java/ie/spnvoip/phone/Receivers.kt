package ie.spnvoip.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Decline / hang up from notification buttons. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SipService.ACTION_DECLINE, SipService.ACTION_HANGUP -> SipManager.hangUp()
        }
    }
}

/** Re-register after reboot or app update, if an account is configured. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // SpnvoipApp.onCreate has already initialised the SIP engine and loaded the account.
        if (SipManager.account.value != null) SipService.start(context)
    }
}
