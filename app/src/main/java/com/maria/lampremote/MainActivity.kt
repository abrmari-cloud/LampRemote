package com.maria.lampremote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PERMS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private val SUGGESTIONS = listOf(
    "ON", "OFF", "Яркость +", "Яркость −", "K+", "K−", "K",
    "◐", "Лампочка", "Луна", "Солнце тёмное", "Солнце светлое",
)

private val WARM_SCENE = Scene("Тёплый свет", listOf("ON", "Солнце светлое"))

private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

private fun hasPerms(ctx: Context) =
    PERMS.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private fun isLocationOn(ctx: Context) =
    ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true

private fun copyToClipboard(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("lamp buttons", text))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = scheme) { App() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App(vm: LampViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasPerms(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasPerms(ctx) }
    var saveBurstId by remember { mutableStateOf<Int?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showSceneDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Пульт лампы") }) }) { pad ->
        if (!granted) {
            Column(
                Modifier.padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Нужен доступ к Bluetooth (поиск и передача) и к геолокации — Android требует её для сканирования BLE.")
                Button(onClick = { launcher.launch(PERMS) }) { Text("Разрешить") }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Scenes ----
            item { Text("Сцены", style = MaterialTheme.typography.titleLarge) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.scenes.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            vm.scenes.forEach { s ->
                                if (editMode) {
                                    OutlinedButton(onClick = { vm.deleteScene(s.name) }) { Text("✕ ${s.name}") }
                                } else {
                                    FilledTonalButton(onClick = { vm.runScene(s) }, enabled = !vm.sending) { Text(s.name) }
                                }
                            }
                        }
                    }
                    val names = vm.buttons.map { it.name }
                    if (vm.scenes.none { it.name == WARM_SCENE.name } && WARM_SCENE.steps.all { it in names }) {
                        OutlinedButton(onClick = { vm.saveScene(WARM_SCENE) }) {
                            Text("+ «${WARM_SCENE.name}»: ${WARM_SCENE.steps.joinToString(" → ")}")
                        }
                    }
                    if (vm.buttons.isNotEmpty()) {
                        TextButton(onClick = { showSceneDialog = true }) { Text("+ Новая сцена") }
                    } else if (vm.scenes.isEmpty()) {
                        Text("Сцена собирается из сохранённых кнопок — сначала запишите их.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---- Saved buttons ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Кнопки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editMode = !editMode }) { Text(if (editMode) "Готово" else "Править") }
                }
            }
            item {
                if (vm.buttons.isEmpty()) {
                    Text("Пока пусто. Запишите кнопки пульта ниже.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        vm.buttons.forEach { b ->
                            if (editMode) {
                                OutlinedButton(onClick = { vm.deleteButton(b.name) }) { Text("✕ ${b.name}") }
                            } else {
                                Button(onClick = { vm.send(b.name, b.payloads) }, enabled = !vm.sending) { Text(b.name) }
                            }
                        }
                    }
                }
            }
            if (editMode) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            copyToClipboard(ctx, vm.exportJson())
                            vm.status = "Кнопки скопированы в буфер обмена"
                        }) { Text("Экспорт") }
                        OutlinedButton(onClick = { showImport = true }) { Text("Импорт") }
                    }
                }
            }
            if (vm.status.isNotEmpty()) {
                item { Text(vm.status, color = MaterialTheme.colorScheme.primary) }
            }

            item { HorizontalDivider() }

            // ---- Capture from the remote ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Запись с пульта", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (vm.bursts.isNotEmpty()) TextButton(onClick = { vm.clearBursts() }) { Text("Очистить") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.toggleScan() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (vm.scanning) "Остановить запись" else "Начать запись")
                    }
                    if (vm.scanning) {
                        Text(
                            "Держите пульт рядом с телефоном и нажимайте по одной кнопке. Каждое нажатие — отдельная пачка.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!isLocationOn(ctx)) {
                        Text(
                            "Геолокация выключена — без неё Android не отдаёт результаты сканирования.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            items(vm.bursts, key = { it.id }) { b ->
                BurstCard(b, vm, onSave = { saveBurstId = b.id })
            }
        }
    }

    saveBurstId?.let { id ->
        SaveDialog(
            onDismiss = { saveBurstId = null },
            onSave = { name ->
                vm.bursts.firstOrNull { it.id == id }?.let { vm.saveButton(name, it.payloads) }
                saveBurstId = null
            },
        )
    }
    if (showImport) {
        ImportDialog(onDismiss = { showImport = false }, onImport = { text -> vm.importJson(text) })
    }
    if (showSceneDialog) {
        SceneDialog(
            buttonNames = vm.buttons.map { it.name },
            onDismiss = { showSceneDialog = false },
            onSave = { scene -> vm.saveScene(scene); showSceneDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneDialog(buttonNames: List<String>, onDismiss: () -> Unit, onSave: (Scene) -> Unit) {
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая сцена") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Нажимайте кнопки в нужном порядке:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    buttonNames.forEach { n -> SuggestionChip(onClick = { steps = steps + n }, label = { Text(n) }) }
                }
                Text(if (steps.isEmpty()) "Шаги: —" else "Шаги: " + steps.joinToString(" → "))
                if (steps.isNotEmpty()) {
                    TextButton(onClick = { steps = steps.dropLast(1) }) { Text("Убрать последний шаг") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Scene(name.trim(), steps)) },
                enabled = name.isNotBlank() && steps.isNotEmpty(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun BurstCard(b: Burst, vm: LampViewModel, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "#${b.id} · ${timeFmt.format(Date(b.time))} · пакетов: ${b.count} · ${b.rssi} dBm",
                style = MaterialTheme.typography.labelLarge,
            )
            b.payloads.forEach { hex ->
                SelectionContainer { Text(hex, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                vm.describe(hex, b.id)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (b.payloads.size > 1) {
                Text("Разных пакетов в пачке: ${b.payloads.size}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.send("пачка #${b.id}", b.payloads) }, enabled = !vm.sending) {
                    Text("Отправить")
                }
                Button(onClick = onSave) { Text("Сохранить как…") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название кнопки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SUGGESTIONS.forEach { s -> SuggestionChip(onClick = { name = s }, label = { Text(s) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт кнопок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Вставьте JSON, скопированный кнопкой «Экспорт». Текущие кнопки и сцены будут заменены.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = onImport(text)
                if (err == null) onDismiss() else error = err
            }, enabled = text.isNotBlank()) { Text("Импортировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
