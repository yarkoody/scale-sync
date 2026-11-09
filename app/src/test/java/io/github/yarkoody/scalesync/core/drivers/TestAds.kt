package io.github.yarkoody.scalesync.core.drivers

import io.github.yarkoody.scalesync.core.Advertisement
import io.github.yarkoody.scalesync.core.hexToBytes

/** An advertisement carrying one manufacturer data entry (company id stripped, as Android delivers it). */
fun mfrAd(companyId: Int, hex: String, address: String = "02:00:00:00:00:01", name: String? = null) =
    Advertisement(address = address, name = name, manufacturerData = mapOf(companyId to hex.hexToBytes()))

/** [hex] with byte [index] replaced by [value]. */
fun withByte(hex: String, index: Int, value: Int): String =
    hex.hexToBytes().also { it[index] = value.toByte() }.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
