package ie.spnvoip.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ie.spnvoip.phone.AccountConfig
import ie.spnvoip.phone.R
import ie.spnvoip.phone.Transport

@Composable
fun SettingsScreen(
    current: AccountConfig?,
    onSave: (AccountConfig) -> Unit,
    onRemove: () -> Unit,
    onBack: (() -> Unit)?,
) {
    var username by rememberSaveable { mutableStateOf(current?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(current?.password ?: "") }
    var domain by rememberSaveable { mutableStateOf(current?.domain ?: "") }
    var transport by rememberSaveable { mutableStateOf(current?.transport ?: Transport.TCP) }
    var keepAlive by rememberSaveable {
        mutableStateOf((current?.keepAliveSeconds ?: AccountConfig.DEFAULT_KEEPALIVE_TCP).toString())
    }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val valid = username.isNotBlank() && domain.isNotBlank()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
                Text(
                    stringResource(R.string.account),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(username, { username = it.trim() }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.username)) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(domain, { domain = it.trim() }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server)) }, singleLine = true,
                placeholder = { Text("pbx.example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))

            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(if (showAdvanced) R.string.hide_advanced else R.string.advanced))
            }

            if (showAdvanced) {
                Text(stringResource(R.string.transport), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Transport.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = transport == t,
                            onClick = {
                                transport = t
                                keepAlive = (if (t == Transport.UDP) AccountConfig.DEFAULT_KEEPALIVE_UDP
                                else AccountConfig.DEFAULT_KEEPALIVE_TCP).toString()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, Transport.entries.size),
                        ) { Text(t.name) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(keepAlive, { v -> keepAlive = v.filter(Char::isDigit).take(4) },
                    Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.keepalive_seconds)) },
                    supportingText = { Text(stringResource(R.string.keepalive_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(
                        AccountConfig(
                            username, password, domain, transport,
                            (keepAlive.toIntOrNull() ?: AccountConfig.DEFAULT_KEEPALIVE_TCP).coerceIn(15, 900),
                        )
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.save)) }

            if (current != null) {
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.remove_account), color = HangRed)
                }
            }
        }
    }
}
