package com.maria.lampremote

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun packets(n: Int): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11 -> "пакет"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "пакета"
        else -> "пакетов"
    }
    return "$n $word"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vm: LampViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var saveBurstId by remember { mutableStateOf<Int?>(null) }
    var showImport by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    DisposableEffect(Unit) {
        onDispose { if (vm.scanning) vm.toggleScan() } // stop listening when leaving this screen
    }

    Scaffold(
        containerColor = Twilight,
        topBar = {
            TopAppBar(
                title = { Text("Запись кнопок", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Twilight),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Ink)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ListenPanel(
                    scanning = vm.scanning,
                    locationOff = !isLocationOn(ctx),
                    onToggle = { vm.toggleScan() },
                )
            }
            if (vm.status.isNotEmpty()) {
                item { Text(vm.status, color = Amber, style = MaterialTheme.typography.bodyMedium) }
            }
            if (vm.bursts.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Нажатия", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearBursts() }) { Text("Очистить") }
                    }
                }
            }
            items(vm.bursts, key = { it.id }) { b ->
                BurstCard(b, vm, onSave = { saveBurstId = b.id })
            }
            item { HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Резервная копия", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Кнопки и сцены в виде текста. Сохраните его в заметки — пригодится после переустановки или на новом телефоне.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            copyToClipboard(ctx, vm.exportJson())
                            vm.status = "Копия скопирована в буфер обмена"
                        }) { Text("Скопировать") }
                        TextButton(onClick = { showImport = true }) { Text("Восстановить") }
                    }
                }
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
}

@Composable
private fun ListenPanel(scanning: Boolean, locationOff: Boolean, onToggle: () -> Unit) {
    val t = rememberInfiniteTransition(label = "listen")
    val dotAlpha by t.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).background(
                        if (scanning) Amber.copy(alpha = dotAlpha) else Muted.copy(alpha = 0.5f),
                        CircleShape,
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (scanning) "Слушаю пульт" else "Запись выключена",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (scanning) {
                    "Поднесите пульт к телефону и нажимайте кнопки по одной. Каждое нажатие появится ниже."
                } else {
                    "Включите запись и нажимайте кнопки настоящего пульта рядом с телефоном."
                },
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (locationOff) {
                Text(
                    "Включите геолокацию: без неё Android не показывает найденные Bluetooth-сигналы.",
                    color = Coral,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = if (scanning) {
                    ButtonDefaults.buttonColors(containerColor = PanelHigh, contentColor = Ink)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (scanning) "Остановить запись" else "Включить запись", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BurstCard(b: Burst, vm: LampViewModel, onSave: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Нажатие ${b.id}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(timeFmt.format(Date(b.time)), color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            Text("${packets(b.count)}, сигнал ${b.rssi} dBm", color = Muted, style = MaterialTheme.typography.labelMedium)
            b.payloads.forEach { hex ->
                SelectionContainer {
                    Text(
                        hex,
                        color = Muted.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                vm.describe(hex, b.id)?.let {
                    Text(it, color = Amber, style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.send("Нажатие ${b.id}", b.payloads) },
                    enabled = !vm.sending,
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Проверить") }
                Button(onClick = onSave, shape = RoundedCornerShape(20.dp)) { Text("Сохранить кнопку") }
            }
        }
    }
}
