package ie.spnvoip.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.spnvoip.phone.CallPhase
import ie.spnvoip.phone.CallUi
import ie.spnvoip.phone.R
import ie.spnvoip.phone.SipManager
import kotlinx.coroutines.delay

@Composable
fun CallScreen(call: CallUi) {
    var showKeypad by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Ticks only while a call is connected and this screen is visible.
    LaunchedEffect(call.phase) {
        while (call.phase == CallPhase.Active) {
            now = System.currentTimeMillis(); delay(1000)
        }
    }

    val subtitle = when (call.phase) {
        CallPhase.Incoming -> stringResource(R.string.incoming_call)
        CallPhase.Outgoing -> stringResource(R.string.calling_short)
        CallPhase.Active -> formatDuration(now - call.connectedAt)
        CallPhase.Ended -> stringResource(R.string.call_ended)
        CallPhase.Idle -> ""
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Text(call.remote, fontSize = 30.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.weight(1f))

            if (call.phase == CallPhase.Incoming) {
                Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                    RoundButton(Icons.Filled.CallEnd, HangRed, stringResource(R.string.decline)) { SipManager.hangUp() }
                    RoundButton(Icons.Filled.Call, CallGreen, stringResource(R.string.answer)) { SipManager.answer() }
                }
            } else if (call.phase != CallPhase.Ended) {
                if (showKeypad) {
                    Dialpad(onKey = { SipManager.sendDtmf(it.first()) })
                    Spacer(Modifier.height(24.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Toggle(if (call.muted) Icons.Filled.MicOff else Icons.Filled.Mic, call.muted, stringResource(R.string.mute)) {
                        SipManager.toggleMute()
                    }
                    Toggle(Icons.Filled.Dialpad, showKeypad, stringResource(R.string.keypad)) { showKeypad = !showKeypad }
                    Toggle(Icons.AutoMirrored.Filled.VolumeUp, call.speaker, stringResource(R.string.speaker)) {
                        SipManager.toggleSpeaker()
                    }
                }
                Spacer(Modifier.height(36.dp))
                RoundButton(Icons.Filled.CallEnd, HangRed, stringResource(R.string.hang_up)) { SipManager.hangUp() }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, color: Color, label: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp)) }
}

@Composable
private fun Toggle(icon: ImageVector, on: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(checked = on, onCheckedChange = { onClick() }, modifier = Modifier.size(60.dp)) {
            Icon(icon, label)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
}
