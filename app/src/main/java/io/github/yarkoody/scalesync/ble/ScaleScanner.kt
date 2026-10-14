package io.github.yarkoody.scalesync.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Reads the scale from its advertisements only. It never connects, so it works alongside
 * Zepp Life and picks up the weigh-in the moment the scale shows it.
 */
object ScaleScanner {
    private const val TAG = "ScaleSync"
    val SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000181b-0000-1000-8000-00805f9b34fb")

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(context: Context) = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    /** True for any advertisement from a Mi body composition scale. */
    @SuppressLint("MissingPermission")
    fun isMiScale(result: ScanResult): Boolean {
        val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: ""
        val data = result.scanRecord?.getServiceData(SERVICE_UUID)
        return name.startsWith("MIBCS") || name.startsWith("MIBFS") || data?.size == 13
    }

    fun serviceData(result: ScanResult): ByteArray? = result.scanRecord?.getServiceData(SERVICE_UUID)

    private fun backgroundIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScanResultReceiver::class.java).setAction(ScanResultReceiver.ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 1, intent, flags)
    }

    /**
     * Hands a hardware-filtered scan to the system. Android keeps it running while the app is
     * closed and wakes [ScanResultReceiver] whenever the scale advertises.
     */
    @SuppressLint("MissingPermission")
    fun startBackground(context: Context, address: String): Boolean {
        if (!hasPermissions(context)) return false
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return false
        val filters = listOf(ScanFilter.Builder().setDeviceAddress(address).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        return runCatching {
            scanner.stopScan(backgroundIntent(context))
            scanner.startScan(filters, settings, backgroundIntent(context)) == 0
        }.onFailure { Log.w(TAG, "startBackground failed", it) }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stopBackground(context: Context) {
        if (!hasPermissions(context)) return
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(backgroundIntent(context)) }
    }

    /** A fast foreground scan while the app is open. Pass [address] null to discover scales. */
    @SuppressLint("MissingPermission")
    fun startForeground(context: Context, address: String?, onResult: (ScanResult) -> Unit): ScanCallback? {
        if (!hasPermissions(context)) return null
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(onResult)
            override fun onScanFailed(errorCode: Int) { Log.w(TAG, "Foreground scan failed: $errorCode") }
        }
        val filters = address?.let { listOf(ScanFilter.Builder().setDeviceAddress(it).build()) } ?: emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching { scanner.startScan(filters, settings, callback); callback }
            .onFailure { Log.w(TAG, "startForeground failed", it) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun stopForeground(context: Context, callback: ScanCallback?) {
        if (callback == null || !hasPermissions(context)) return
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(callback) }
    }
}
