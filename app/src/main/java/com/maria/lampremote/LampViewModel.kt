package com.maria.lampremote

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** One button press on the remote = one burst of packets a few ms apart. */
data class Burst(
    val id: Int,
    val time: Long,
    val payloads: List<String>, // distinct payloads seen in this burst, in order
    val count: Int,
    val rssi: Int,
)

class LampViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = LampBle(app)
    private val store = ButtonStore(app)

    var buttons by mutableStateOf(store.load()); private set
    var bursts by mutableStateOf(emptyList<Burst>()); private set
    var scanning by mutableStateOf(false); private set
    var sending by mutableStateOf(false); private set
    var status by mutableStateOf("")

    private var nextId = 1
    private var lastPacketAt = 0L

    fun toggleScan() {
        if (scanning) {
            ble.stopScan()
            scanning = false
            return
        }
        val err = ble.startScan(
            onPacket = ::onPacket,
            onError = { status = it; scanning = false },
        )
        if (err != null) status = err else { scanning = true; status = "" }
    }

    private fun onPacket(data: ByteArray, rssi: Int) {
        if (rssi < MIN_RSSI) return // ignore far-away devices; the remote is held next to the phone
        val hex = data.toHex()
        val now = SystemClock.elapsedRealtime()
        val cur = bursts.firstOrNull()
        bursts = if (cur != null && now - lastPacketAt < BURST_GAP_MS) {
            val updated = cur.copy(
                payloads = if (hex in cur.payloads) cur.payloads else cur.payloads + hex,
                count = cur.count + 1,
                rssi = maxOf(cur.rssi, rssi),
            )
            listOf(updated) + bursts.drop(1)
        } else {
            (listOf(Burst(nextId++, System.currentTimeMillis(), listOf(hex), 1, rssi)) + bursts).take(MAX_BURSTS)
        }
        lastPacketAt = now
    }

    /** Explains whether this payload was seen before — the key test for "same button = same packet?". */
    fun describe(hex: String, burstId: Int): String? {
        buttons.firstOrNull { hex in it.payloads }?.let { return "= сохранённая кнопка «${it.name}»" }
        val same = bursts.filter { it.id != burstId && hex in it.payloads }.map { "#${it.id}" }
        return if (same.isEmpty()) null else "совпадает с пачкой ${same.joinToString()}"
    }

    fun send(label: String, payloads: List<String>) {
        if (sending || payloads.isEmpty()) return
        viewModelScope.launch {
            sending = true
            status = "Отправляю «$label»…"
            var err: String? = null
            for (p in payloads) {
                err = ble.advertise(p.hexToBytes(), SEND_MS)
                if (err != null) break
            }
            status = err ?: "Отправлено: «$label»"
            sending = false
        }
    }

    fun saveButton(name: String, payloads: List<String>) {
        buttons = buttons.filter { it.name != name } + LampButton(name, payloads)
        store.save(buttons)
        status = "Сохранено: «$name»"
    }

    fun deleteButton(name: String) {
        buttons = buttons.filter { it.name != name }
        store.save(buttons)
    }

    fun clearBursts() {
        bursts = emptyList()
    }

    fun exportJson(): String = store.toJson(buttons)

    /** Returns an error text, or null on success. */
    fun importJson(text: String): String? {
        return try {
            val list = store.fromJson(text)
            buttons = list
            store.save(list)
            status = "Импортировано кнопок: ${list.size}"
            null
        } catch (e: Exception) {
            "Не удалось прочитать: ${e.message}"
        }
    }

    override fun onCleared() {
        ble.stopScan()
    }

    companion object {
        const val MIN_RSSI = -70
        const val BURST_GAP_MS = 400L
        const val MAX_BURSTS = 30
        const val SEND_MS = 700L
    }
}
