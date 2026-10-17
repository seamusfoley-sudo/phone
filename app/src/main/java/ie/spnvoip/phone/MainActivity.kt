package ie.spnvoip.phone

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ie.spnvoip.phone.ui.CallScreen
import ie.spnvoip.phone.ui.SpnvoipTheme
import ie.spnvoip.phone.ui.DialScreen
import ie.spnvoip.phone.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* the app works with whatever is granted; mic is needed for calls */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        handleIntent(intent)

        setContent {
            SpnvoipTheme {
                val reg by SipManager.registration.collectAsStateWithLifecycle()
                val call by SipManager.call.collectAsStateWithLifecycle()
                val account by SipManager.account.collectAsStateWithLifecycle()
                var showSettings by remember { mutableStateOf(account == null) }

                // While a call is up, let the service hold mic access in the background.
                LaunchedEffect(call.phase) {
                    if (call.phase == CallPhase.Active || call.phase == CallPhase.Outgoing) {
                        SipService.start(this@MainActivity, SipService.ACTION_IN_CALL)
                    }
                }

                when {
                    call.phase != CallPhase.Idle -> CallScreen(call)
                    showSettings -> SettingsScreen(
                        current = account,
                        onSave = {
                            SipManager.saveAccount(it)
                            SipService.start(this)
                            showSettings = false
                        },
                        onRemove = {
                            SipManager.removeAccount()
                            SipService.stop(this)
                        },
                        onBack = if (account != null) ({ showSettings = false }) else null,
                    )
                    else -> DialScreen(reg = reg, onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == SipService.ACTION_ANSWER) {
            SipManager.answer()
            SipService.start(this, SipService.ACTION_IN_CALL)
        }
    }

    private fun requestPermissions() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) list += Manifest.permission.BLUETOOTH_CONNECT
        permissions.launch(list.toTypedArray())
    }
}
