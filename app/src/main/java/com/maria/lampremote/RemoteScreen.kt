package com.maria.lampremote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.min

/** The four ring positions, laid out like the physical remote: brightness top/bottom, colour temperature left/right. */
private enum class Slot(val glyph: GlyphKind, val buttonName: String, val align: Alignment, val startAngle: Float) {
    TOP(GlyphKind.BRIGHT_UP, "Яркость +", Alignment.TopCenter, -135f),
    RIGHT(GlyphKind.K_UP, "K+", Alignment.CenterEnd, -45f),
    BOTTOM(GlyphKind.BRIGHT_DOWN, "Яркость −", Alignment.BottomCenter, 45f),
    LEFT(GlyphKind.K_DOWN, "K−", Alignment.CenterStart, 135f),
}

private const val INNER = 0.52f   // inner radius of the ring, as a fraction of the outer radius
private const val GAP = 7f        // degrees between ring segments
private val DIAL_SIZE = 272.dp

private val WARM_SCENE = Scene("Тёплый свет", listOf("ON", "Солнце светлое"))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(vm: LampViewModel, onRecord: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var editMode by remember { mutableStateOf(false) }
    var showSceneDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val slotButtons = Slot.values().associateWith { s -> vm.buttons.firstOrNull { glyphFor(it.name) == s.glyph } }
    val onButton = vm.buttons.firstOrNull { it.name.equals("ON", ignoreCase = true) }
    val offButton = vm.buttons.firstOrNull { it.name.equals("OFF", ignoreCase = true) }
    val placed = (slotButtons.values.filterNotNull() + listOfNotNull(onButton, offButton)).map { it.name }.toSet()
    val others = vm.buttons.filter { it.name !in placed }

    fun press(b: LampButton) {
        if (editMode) {
            pendingDelete = "кнопку «${b.name}»" to { vm.deleteButton(b.name) }
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.send(b.name, b.payloads)
        }
    }

    fun pressScene(s: Scene) {
        if (editMode) {
            pendingDelete = "сцену «${s.name}»" to { vm.deleteScene(s.name) }
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.runScene(s)
        }
    }

    Scaffold(
        containerColor = Twilight,
        topBar = {
            TopAppBar(
                title = { Text("Свет", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Twilight),
                actions = {
                    TextButton(onClick = onRecord) { Text("Записать") }
                    if (vm.buttons.isNotEmpty() || vm.scenes.isNotEmpty()) {
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                if (editMode) Icons.Filled.Done else Icons.Filled.Edit,
                                contentDescription = if (editMode) "Готово" else "Править",
                                tint = if (editMode) Amber else Muted,
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (editMode) {
                item {
                    Text(
                        "Нажмите на кнопку или сцену, чтобы удалить её.",
                        color = Amber,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Dial(
                        slotButtons = slotButtons,
                        active = vm.lastCommand,
                        sending = vm.sending,
                        dark = vm.lastCommand != null && vm.lastCommand == offButton?.name,
                        centerText = vm.lastCommand ?: if (vm.buttons.isEmpty()) "Пульт пуст" else "Готов",
                        onTap = { slot ->
                            val b = slotButtons[slot]
                            if (b == null) vm.status = "Кнопка «${slot.buttonName}» ещё не записана" else press(b)
                        },
                    )
                }
            }

            if (vm.status.isNotEmpty()) {
                item {
                    Text(
                        vm.status,
                        color = Coral,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PowerPill("Включить", onButton, warm = true, editMode = editMode, modifier = Modifier.weight(1f)) {
                        onButton?.let(::press)
                    }
                    PowerPill("Выключить", offButton, warm = false, editMode = editMode, modifier = Modifier.weight(1f)) {
                        offButton?.let(::press)
                    }
                }
            }

            if (vm.buttons.isEmpty()) {
                item { EmptyRemote(onRecord) }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Сцены", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (!editMode) TextButton(onClick = { showSceneDialog = true }) { Text("Новая сцена") }
                        }
                        val names = vm.buttons.map { it.name }.toSet()
                        val offerWarm = vm.scenes.none { it.name == WARM_SCENE.name } && WARM_SCENE.steps.all { it in names }
                        if (vm.scenes.isEmpty() && !offerWarm) {
                            Text(
                                "Сцена нажимает несколько кнопок подряд — например, «Луна», а потом теплее.",
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        FlowRow(
                            maxItemsInEachRow = 2,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            vm.scenes.forEach { s ->
                                SceneTile(
                                    scene = s,
                                    running = vm.sending && vm.lastCommand == s.name,
                                    editMode = editMode,
                                    modifier = Modifier.weight(1f),
                                ) { pressScene(s) }
                            }
                            if (offerWarm && !editMode) {
                                SuggestedSceneTile(WARM_SCENE, Modifier.weight(1f)) { vm.saveScene(WARM_SCENE) }
                            }
                        }
                    }
                }

                if (others.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Кнопки", style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                others.forEach { b ->
                                    RoundKey(
                                        button = b,
                                        active = vm.sending && vm.lastCommand == b.name,
                                        editMode = editMode,
                                    ) { press(b) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { (what, action) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить $what?") },
            confirmButton = {
                TextButton(onClick = { action(); pendingDelete = null }) { Text("Удалить", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
    if (showSceneDialog) {
        SceneDialog(
            buttonNames = vm.buttons.map { it.name },
            onDismiss = { showSceneDialog = false },
            onSave = { scene -> vm.saveScene(scene); showSceneDialog = false },
        )
    }
}

@Composable
private fun Dial(
    slotButtons: Map<Slot, LampButton?>,
    active: String?,
    sending: Boolean,
    dark: Boolean,
    centerText: String,
    onTap: (Slot) -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val idleAlpha by animateFloatAsState(if (dark) 0.05f else 0.32f, tween(500), label = "idleAlpha")
    val glowAlpha = if (sending) pulseAlpha else idleAlpha
    val tap by rememberUpdatedState(onTap)

    Box(
        Modifier
            .size(DIAL_SIZE)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val d = o - c
                    val r = min(size.width, size.height) / 2f
                    val dist = d.getDistance()
                    if (dist < r * INNER || dist > r) return@detectTapGestures
                    val angle = Math.toDegrees(atan2(d.y, d.x).toDouble())
                    val slot = when {
                        angle >= -135 && angle < -45 -> Slot.TOP
                        angle >= -45 && angle < 45 -> Slot.RIGHT
                        angle >= 45 && angle < 135 -> Slot.BOTTOM
                        else -> Slot.LEFT
                    }
                    tap(slot)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val outer = size.minDimension / 2f
            val inner = outer * INNER
            val ringWidth = outer - inner
            val mid = (outer + inner) / 2f
            // The lamp: a warm glow inside the ring.
            drawCircle(
                Brush.radialGradient(
                    listOf(Amber.copy(alpha = glowAlpha), Amber.copy(alpha = glowAlpha * 0.25f), Color.Transparent),
                    center = center,
                    radius = inner,
                ),
                radius = inner,
                center = center,
            )
            Slot.values().forEach { slot ->
                val b = slotButtons[slot]
                val color = when {
                    b != null && sending && b.name == active -> Amber.copy(alpha = 0.5f)
                    b != null -> PanelHigh
                    else -> Panel
                }
                drawArc(
                    color = color,
                    startAngle = slot.startAngle + GAP / 2,
                    sweepAngle = 90f - GAP,
                    useCenter = false,
                    topLeft = Offset(center.x - mid, center.y - mid),
                    size = Size(mid * 2, mid * 2),
                    style = Stroke(ringWidth),
                )
            }
        }
        Slot.values().forEach { slot ->
            val present = slotButtons[slot] != null
            val pad = if (slot == Slot.TOP || slot == Slot.BOTTOM) {
                Modifier.padding(vertical = 21.dp)
            } else {
                Modifier.padding(horizontal = 15.dp)
            }
            Box(Modifier.align(slot.align).then(pad)) {
                Glyph(slot.glyph, "", if (present) Ink else Muted.copy(alpha = 0.4f), 24.dp)
            }
        }
        Text(
            centerText,
            color = if (dark) Muted else Ink,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(112.dp),
        )
    }
}

@Composable
private fun PowerPill(
    label: String,
    button: LampButton?,
    warm: Boolean,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = button != null,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (editMode) Coral.copy(alpha = 0.2f) else if (warm) Amber else PanelHigh,
            contentColor = if (editMode) Coral else if (warm) OnAmber else Ink,
            disabledContainerColor = Panel,
            disabledContentColor = Muted.copy(alpha = 0.6f),
        ),
    ) {
        Glyph(GlyphKind.POWER, "", LocalContentColor.current, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneTile(scene: Scene, running: Boolean, editMode: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (editMode) Coral.copy(alpha = 0.14f) else if (running) Amber.copy(alpha = 0.28f) else AmberTint,
        modifier = modifier.heightIn(min = 84.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                scene.name,
                color = if (editMode) Coral else Amber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                scene.steps.joinToString(" → "),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedSceneTile(scene: Scene, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Twilight,
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.5f)),
        modifier = modifier.heightIn(min = 84.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("+ ${scene.name}", color = Amber, style = MaterialTheme.typography.titleMedium)
            Text(scene.steps.joinToString(" → "), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundKey(button: LampButton, active: Boolean, editMode: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Box {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (active) AmberTint else PanelHigh,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Glyph(glyphFor(button.name), button.name, if (active) Amber else Ink, 26.dp)
                }
            }
            if (editMode) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).background(Coral, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = Color(0xFF3B0C05), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            button.name,
            color = Muted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyRemote(onRecord: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Пульт пока пустой", style = MaterialTheme.typography.titleMedium)
            Text(
                "Запишите кнопки с настоящего пульта: каждое нажатие можно сохранить как кнопку здесь.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRecord, shape = RoundedCornerShape(24.dp)) { Text("Записать кнопки") }
        }
    }
}
