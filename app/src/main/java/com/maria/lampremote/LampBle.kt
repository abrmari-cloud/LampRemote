package com.maria.lampremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The remote broadcasts legacy BLE advertisements with manufacturer data
 * under company ID 0xFFFF. We capture those payloads and re-broadcast them as-is.
 */
@SuppressLint("MissingPermission") // permissions are checked in the UI before anything here runs
class LampBle(context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanCallback: ScanCallback? = null

    fun startScan(onPacket: (ByteArray, Int) -> Unit, onError: (String) -> Unit): String? {
        val a = adapter ?: return "Bluetooth недоступен"
        if (!a.isEnabled) return "Включите Bluetooth"
        val scanner = a.bluetoothLeScanner ?: return "Сканер BLE недоступен"
        stopScan()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val data = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
                onPacket(data, result.rssi)
            }

            override fun onScanFailed(errorCode: Int) {
                onError("Сканирование не запустилось, код $errorCode")
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, cb)
        scanCallback = cb
        return null
    }

    fun stopScan() {
        val cb = scanCallback ?: return
        scanCallback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: Exception) {
            // Bluetooth may already be off; nothing to stop.
        }
    }

    /** Broadcasts one payload for [durationMs]. Returns an error text, or null on success. */
    suspend fun advertise(payload: ByteArray, durationMs: Long): String? {
        val a = adapter ?: return "Bluetooth недоступен"
        if (!a.isEnabled) return "Включите Bluetooth"
        val advertiser = a.bluetoothLeAdvertiser ?: return "Телефон не поддерживает BLE-рекламу"

        // Non-connectable legacy advertising: same packet type the remote uses,
        // and Android adds no Flags field, so the 27-byte payload still fits in 31 bytes.
        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(false)
            .setScannable(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(COMPANY_ID, payload)
            .build()

        val started = CompletableDeferred<Int>()
        val cb = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                started.complete(status)
            }
        }

        try {
            advertiser.startAdvertisingSet(params, data, null, null, null, cb)
        } catch (e: IllegalArgumentException) {
            return "Данные не помещаются в пакет: ${e.message}"
        }

        val status = withTimeoutOrNull(2000) { started.await() }
        if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
            advertiser.stopAdvertisingSet(cb)
            return if (status == null) "Передача не запустилась (таймаут)" else "Ошибка передачи: ${statusText(status)}"
        }
        delay(durationMs)
        advertiser.stopAdvertisingSet(cb)
        return null
    }

    private fun statusText(status: Int) = when (status) {
        AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "пакет слишком большой"
        AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "занято слишком много передатчиков"
        AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "уже идёт передача"
        AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "внутренняя ошибка Bluetooth"
        AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "не поддерживается телефоном"
        else -> "код $status"
    }

    companion object {
        const val COMPANY_ID = 0xFFFF
    }
}
