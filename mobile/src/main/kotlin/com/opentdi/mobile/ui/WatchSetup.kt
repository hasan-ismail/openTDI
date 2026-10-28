package com.opentdi.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opentdi.mobile.link.WatchInstaller
import kotlinx.coroutines.launch

private val STEPS = listOf(
    "On the watch, connect to the same Wi-Fi as this phone: Settings > Connections > Wi-Fi.",
    "Turn on developer options: Settings > About watch > Software information, then tap Software version 5–7 times.",
    "Settings > Developer options: turn on ADB debugging, then Wireless debugging.",
    "In Wireless debugging, tap Pair new device. The watch shows a 6-digit code.",
)

/**
 * Installs the watch app from this phone (owner, 2026-10-28): the steps to turn on the watch's Wireless debugging, then
 * pairing with the code the watch shows and installing the watch app the phone carries. Pairing is needed once.
 */
@Composable
fun WatchSetupCard() {
    val context = LocalContext.current
    val installer = remember { WatchInstaller(context.applicationContext) }
    var open by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(open) { // looks for the watch only while the steps are open
        if (open) installer.start()
        onDispose { installer.stop() }
    }
    val found by installer.found.collectAsState()
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("watch_setup", Context.MODE_PRIVATE) }
    var pairedHost by remember { mutableStateOf(prefs.getString("host", null)) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Only the watch this phone paired with: the connection on the pairing dialog's address, or the one paired before.
    val watchHost = found.pairing?.host ?: pairedHost
    val connect = found.connect.values.firstOrNull { it.host == watchHost }

    Section("Install the watch app") {
        Help("Put OpenTDI on your Galaxy Watch from this phone, without a computer. Needed once, and after updates.")
        if (!open) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Show me how") }
            return@Section
        }
        STEPS.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
        Help(
            when {
                found.pairing != null -> "Found the watch's pairing screen at ${found.pairing!!.host}."
                connect != null -> "Found your paired watch at ${connect.host}."
                else -> "Looking for the watch on the Wi-Fi…"
            },
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text("Code shown on the watch") },
            supportingText = {
                Text("Type it carefully: on some Galaxy Watches a wrong code briefly restarts the watch's screen.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val pairing = found.pairing ?: return@Button
                    busy = true
                    message = "Pairing…"
                    scope.launch {
                        val problem = installer.pair(pairing, code)
                        if (problem == null) {
                            pairedHost = pairing.host
                            prefs.edit().putString("host", pairing.host).apply()
                        }
                        message = problem ?: "Paired. Close the code on the watch, then tap Install."
                        busy = false
                    }
                },
                enabled = !busy && code.length == 6 && found.pairing != null,
                modifier = Modifier.weight(1f),
            ) { Text("Pair") }
            Button(
                onClick = {
                    val target = connect ?: return@Button
                    busy = true
                    message = "Installing… this takes about a minute."
                    scope.launch {
                        message = installer.install(target) ?: "Installed. OpenTDI is open on the watch, waiting for the phone."
                        busy = false
                    }
                },
                enabled = !busy && connect != null,
                modifier = Modifier.weight(1f),
            ) { Text("Install") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Help(
            "Keep the watch screen awake (tap it) until Installed appears. When it is done you can turn Wireless " +
                "debugging off. Already paired? Turn Wireless debugging on and tap Install.",
        )
    }
}
