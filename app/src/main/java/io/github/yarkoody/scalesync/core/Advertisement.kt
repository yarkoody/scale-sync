package io.github.yarkoody.scalesync.core

import java.util.Locale
import java.util.UUID

/**
 * One BLE advertisement, as plain data so drivers can be unit-tested without Android.
 * Built from an Android ScanResult in `ble/ScaleScanner.kt`.
 */
class Advertisement(
    val address: String,
    val name: String? = null,
    /** Service data by service UUID (16-bit UUIDs expanded with the Bluetooth base UUID). */
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    /** Manufacturer specific data by 16-bit company id (the first two bytes, little-endian). */
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<UUID> = emptyList(),
    val rssi: Int? = null,
    /** The whole advertisement payload, for diagnostic captures. */
    val raw: ByteArray? = null,
) {
    fun serviceData16(uuid16: Int): ByteArray? = serviceData[uuid16(uuid16)]

    fun hasService16(uuid16: Int): Boolean = uuid16(uuid16).let { it in serviceUuids || it in serviceData }

    /** Name in upper case, or "" if the scale doesn't advertise one. */
    val upperName: String get() = name?.uppercase(Locale.ROOT).orEmpty()

    companion object {
        fun uuid16(v: Int): UUID = UUID.fromString(String.format(Locale.ROOT, "%08x-0000-1000-8000-00805f9b34fb", v))

        /** The 16-bit form of [u] if it is on the Bluetooth base UUID, otherwise null. */
        fun short(u: UUID): Int? =
            if (u.leastSignificantBits == BASE.leastSignificantBits && (u.mostSignificantBits and 0xFFFFFFFFL) == 0x1000L) {
                (u.mostSignificantBits ushr 32).toInt()
            } else null

        private val BASE: UUID = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb")
    }
}

fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }

fun String.hexToBytes(): ByteArray {
    val s = filterNot { it.isWhitespace() || it == ':' || it == '-' }
    require(s.length % 2 == 0) { "odd hex length" }
    return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** Little-endian unsigned 16-bit at [i]. */
internal fun ByteArray.u16le(i: Int) = ((this[i + 1].toInt() and 0xFF) shl 8) or (this[i].toInt() and 0xFF)

/** Big-endian unsigned 16-bit at [i]. */
internal fun ByteArray.u16be(i: Int) = ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF)

internal fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
