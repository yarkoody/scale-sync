package io.github.yarkoody.scalesync.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.yarkoody.scalesync.Processor
import io.github.yarkoody.scalesync.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Woken by the system-held background scan each time the scale advertises. */
class ScanResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results: List<ScanResult> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        } ?: return
        if (results.isEmpty()) return

        val pending = goAsync()
        receiverScope.launch {
            try {
                results.forEach { Processor.onServiceData(context.applicationContext, ScaleScanner.serviceData(it)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "io.github.yarkoody.scalesync.SCAN_RESULT"
    }
}

/** Re-arms the background scan after a reboot, an app update, or Bluetooth being turned back on. */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON
        ) return
        val address = Store(context).scaleAddress ?: return
        ScaleScanner.startBackground(context, address)
    }
}
