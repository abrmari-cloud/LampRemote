package com.maria.lampremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Names that match the physical remote; buttons saved under them get the remote's own icon. */
private val BUTTON_NAMES = listOf(
    "ON", "OFF", "Яркость +", "Яркость −", "K+", "K−", "K",
    "◐", "Лампочка", "Луна", "Солнце тёмное", "Солнце светлое",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Какая это кнопка?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Выберите кнопку с пульта — у неё будет такой же значок. Или введите своё название.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BUTTON_NAMES.forEach { s -> SuggestionChip(onClick = { name = s }, label = { Text(s) }) }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название, например «Луна тёплая»") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Нажимайте кнопки в том порядке, в каком нажимали бы на пульте. Одну кнопку можно добавить несколько раз.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    buttonNames.forEach { n -> SuggestionChip(onClick = { steps = steps + n }, label = { Text(n) }) }
                }
                Text(
                    if (steps.isEmpty()) "Шагов пока нет" else steps.joinToString(" → "),
                    color = if (steps.isEmpty()) Muted else Amber,
                    style = MaterialTheme.typography.titleSmall,
                )
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
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Восстановить из копии") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Вставьте текст резервной копии. Текущие кнопки и сцены будут заменены.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                error?.let { Text(it, color = Coral) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val err = onImport(text)
                    if (err == null) onDismiss() else error = err
                },
                enabled = text.isNotBlank(),
            ) { Text("Восстановить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
