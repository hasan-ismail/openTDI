package com.opentdi.mobile.ui

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.opentdi.mobile.link.SamsungKitTransfer
import com.opentdi.mobile.link.WatchInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KIT_AFTER_INSTALL_MS = 5_000L

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
    var pairedHost by remember { mutableStateOf(installer.pairedHost) }
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
                found.pairing != null -> "Found the watch's code screen."
                connect != null -> "Found your watch."
                // Only the paired address is looked for, and a router can give the watch a new one.
                pairedHost != null -> "Looking for your watch… If it is not found within a minute, and the watch is " +
                    "on the same Wi-Fi with Wireless debugging on, its Wi-Fi address may have changed: tap Pair " +
                    "again below."
                else -> "Looking for the watch… If nothing shows within a minute, check the watch is on the same " +
                    "Wi-Fi as this phone with Wireless debugging on, and tap the watch screen to keep it awake."
            },
        )
        // One button at a time: Pair until this phone has paired, then Install.
        if (pairedHost == null) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("The 6 numbers on the watch") },
                supportingText = { Text("Type them slowly. A wrong code can restart the watch screen for a moment.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val pairing = found.pairing ?: return@Button
                    busy = true
                    message = "Pairing…"
                    scope.launch {
                        val problem = installer.pair(pairing, code) // saves the host itself
                        if (problem == null) pairedHost = pairing.host
                        message = problem ?: "Paired. Close the code on the watch, then tap Install."
                        busy = false
                    }
                },
                enabled = !busy && code.length == 6 && found.pairing != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pair") }
        } else {
            Button(
                onClick = {
                    val target = connect ?: return@Button
                    busy = true
                    message = "Installing… this takes about a minute."
                    scope.launch {
                        val problem = installer.install(target)
                        message = problem ?: "Installed. OpenTDI is open on the watch, waiting for the phone."
                        if (problem == null && SamsungKitTransfer.name(context) != null) {
                            delay(KIT_AFTER_INSTALL_MS) // the new watch app has to start before it can receive
                            message = SamsungKitTransfer.send(context)?.let { "Installed. Samsung's kit: $it" }
                                ?: "Installed, with your Samsung kit. OpenTDI is open on the watch."
                        }
                        busy = false
                    }
                },
                enabled = !busy && connect != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Install") }
            TextButton(
                onClick = {
                    pairedHost = null
                    message = null
                },
                enabled = !busy,
            ) { Text("Pair again") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Help(
            "Keep the watch screen awake (tap it) until Installed appears. When it is done you can turn Wireless " +
                "debugging off; turn it on again to install an update.",
        )
    }
}

/**
 * Samsung's sensor kit (bring your own kit): the user downloads it from Samsung, picks the download here, and the phone
 * converts it and sends it to the watch. Without it the watch uses its basic sensors (heart rate and movement).
 */
@Composable
fun SamsungKitCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(SamsungKitTransfer.name(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        message = "Converting Samsung's kit…"
        scope.launch {
            message = try {
                name = SamsungKitTransfer.import(context, uri)
                SamsungKitTransfer.send(context)?.let { "Kit ready on this phone. $it" }
                    ?: "Kit ready and sent: the watch uses it from the next night."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.message ?: "Could not read that file."
            }
            busy = false
        }
    }
    Section("Samsung's sensor kit") {
        Help(
            "Adds skin response, skin temperature, blood oxygen and the raw pulse signal on Galaxy Watches. Samsung " +
                "only lets you download it yourself: sign in at developer.samsung.com > Health > Sensor SDK, " +
                "accept Samsung's license, download it, then pick the downloaded zip here. Without it the watch " +
                "uses its basic sensors (heart rate and movement), which is all OpenTDI needs.",
        )
        Text(name?.let { "On this phone: $it" } ?: "No kit added yet.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(if (name == null) "Add the kit" else "Replace")
            }
            OutlinedButton(
                onClick = {
                    busy = true
                    message = "Sending to the watch…"
                    scope.launch {
                        message = SamsungKitTransfer.send(context)
                            ?: "Sent: the watch uses it from the next night."
                        busy = false
                    }
                },
                enabled = !busy && name != null,
                modifier = Modifier.weight(1f),
            ) { Text("Send to watch") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
