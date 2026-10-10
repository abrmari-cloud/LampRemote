package com.maria.lampremote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val PERMS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

fun hasPerms(ctx: Context) =
    PERMS.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

fun isLocationOn(ctx: Context) =
    ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true

fun copyToClipboard(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("lamp backup", text))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LampTheme {
                Surface(Modifier.fillMaxSize(), color = Twilight) { App() }
            }
        }
    }
}

@Composable
fun App(vm: LampViewModel = viewModel()) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(hasPerms(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasPerms(ctx) }
    var recording by rememberSaveable { mutableStateOf(false) }

    when {
        !granted -> PermissionScreen { launcher.launch(PERMS) }
        recording -> RecordScreen(vm, onBack = { recording = false })
        else -> RemoteScreen(vm, onRecord = { recording = true })
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(GlyphKind.SUN_OUTLINE, "", Amber, 64.dp)
        Text("Нужен Bluetooth", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Телефон слушает настоящий пульт и передаёт его команды лампе. Для этого нужен доступ к Bluetooth и геолокации — без неё Android не даёт искать Bluetooth-сигналы.",
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onAllow, shape = RoundedCornerShape(26.dp), modifier = Modifier.height(52.dp)) {
            Text("Разрешить")
        }
    }
}
