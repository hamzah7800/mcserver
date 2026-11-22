package com.example.mcserver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

private const val TERMUX_PERM = "com.termux.permission.RUN_COMMAND"
private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
private const val TERMUX_HOME = "/data/data/com.termux/files/home"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashFile = File(filesDir, "crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText(Log.getStackTraceString(e)) } catch (_: Exception) {}
            prev?.uncaughtException(t, e)
        }
        val saved: String? = try { if (crashFile.exists()) crashFile.readText() else null } catch (_: Exception) { null }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.systemBarsPadding()) {
                    var report by remember { mutableStateOf(saved) }
                    val r = report
                    if (r != null) CrashScreen(r) { crashFile.delete(); report = null }
                    else ServerScreen()
                }
            }
        }
    }
}

@Composable
fun CrashScreen(text: String, onClear: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("The app crashed last time", style = MaterialTheme.typography.headlineSmall)
        Text("Tap Copy and send this text to Claude.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
            OutlinedButton(onClear) { Text("Clear and continue") }
        }
        SelectionContainer { Text(text.take(4000), style = MaterialTheme.typography.bodySmall) }
    }
}

fun termuxInstalled(ctx: Context): Boolean =
    try { ctx.packageManager.getPackageInfo("com.termux", 0); true } catch (e: Exception) { false }

fun runInTermux(ctx: Context, args: List<String>, foreground: Boolean) {
    try {
        val script = ctx.assets.open("mc.sh").bufferedReader().use { it.readText() }
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script, "mc") + args)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", !foreground)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        ContextCompat.startForegroundService(ctx, intent)
        if (!foreground) Toast.makeText(ctx, "Command sent to Termux", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Termux error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ServerScreen() {
    val ctx = LocalContext.current
    val hasTermux = remember { termuxInstalled(ctx) }
    var version by remember { mutableStateOf("1.21.1") }
    var ram by remember { mutableStateOf("3G") }
    var slug by remember { mutableStateOf("") }
    var eula by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, TERMUX_PERM) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    fun go(foreground: Boolean, vararg a: String) {
        if (!hasTermux) { Toast.makeText(ctx, "Install Termux from F-Droid first", Toast.LENGTH_LONG).show(); return }
        if (!granted) { permLauncher.launch(TERMUX_PERM); return }
        runInTermux(ctx, a.toList(), foreground)
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("S20 FE Minecraft Server", style = MaterialTheme.typography.headlineSmall)
        if (!hasTermux) Text("Termux is not installed. Install it from F-Droid first, then reopen this app.", color = MaterialTheme.colorScheme.error)
        else if (!granted) Text("Tap any button to grant the Termux permission.", color = MaterialTheme.colorScheme.error)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(version, { version = it }, label = { Text("MC version") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(ram, { ram = it }, label = { Text("RAM") }, modifier = Modifier.weight(0.6f), singleLine = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(eula, { eula = it })
            Text("I accept the Minecraft EULA (aka.ms/MinecraftEULA)")
        }
        Button({ go(true, "install", version.trim(), ram.trim(), if (eula) "yes" else "no") }, Modifier.fillMaxWidth()) { Text("1. Install server + mods + playit") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ go(false, "start") }, Modifier.weight(1f)) { Text("Start") }
            Button({ go(false, "stop") }, Modifier.weight(1f)) { Text("Stop") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ go(true, "link") }, Modifier.weight(1f)) { Text("playit link / address") }
            OutlinedButton({ go(true, "console") }, Modifier.weight(1f)) { Text("Console") }
        }
        OutlinedButton({ go(false, "backup") }, Modifier.fillMaxWidth()) { Text("Backup world") }

        OutlinedTextField(slug, { slug = it }, label = { Text("Modrinth mod slug (e.g. create-fabric)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton({ if (slug.isNotBlank()) go(true, "addmod", slug.trim()) }, Modifier.fillMaxWidth()) { Text("Add mod") }
        Text("Your own .jar mods go in Termux: ~/mcserver/mods", style = MaterialTheme.typography.bodySmall)
    }
}
