package com.tokopad.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.IOException
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object Receipt {
    fun text(sale: Sale, settings: ShopSettings): String {
        val id = settings.language.substringBefore('-').equals("id", ignoreCase = true)
        fun label(en: String, indonesian: String) = if (id) indonesian else en
        val locale = if (id) Locale.forLanguageTag("id-ID") else Locale.US
        val numbers = NumberFormat.getIntegerInstance(locale)
        fun money(value: Long) = "Rp ${numbers.format(value)}"
        val width = when (settings.paperWidth) {
            58 -> 32
            80 -> 48
            else -> throw IllegalArgumentException("Unsupported paper width: ${settings.paperWidth}")
        }
        val result = StringBuilder()
        fun length(value: String) = value.codePointCount(0, value.length)
        fun line(value: String) {
            for (raw in value.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                var remaining = raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
                while (length(remaining) > width) {
                    val end = remaining.offsetByCodePoints(0, width)
                    val space = remaining.lastIndexOf(' ', end)
                    val cut = if (space in 1..end) space else end
                    result.appendLine(remaining.substring(0, cut))
                    remaining = remaining.substring(cut).trimStart()
                }
                result.appendLine(remaining)
            }
        }
        fun optional(value: String) { if (value.isNotBlank()) line(value) }
        fun detail(name: String, value: String) { if (value.isNotBlank()) line("$name: $value") }
        fun row(name: String, value: String) {
            val padding = width - length(name) - length(value)
            if (padding >= 1 && !name.contains('\n') && !value.contains('\n')) {
                line(name + " ".repeat(padding) + value)
            } else {
                line(name)
                line(value)
            }
        }
        fun rule() = line("-".repeat(width))
        val date = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", locale).withZone(ZoneId.of(sale.zoneId))
        val shop = sale.shop
        optional(shop.shopName)
        optional(shop.address)
        optional(shop.contact)
        optional(shop.header)
        rule()
        if (sale.cancelledAt != null) {
            line("*** ${label("CANCELLED", "DIBATALKAN")} ***")
            detail(label("Cancelled", "Dibatalkan"), date.format(Instant.ofEpochMilli(sale.cancelledAt)))
            detail(label("Reason", "Alasan"), sale.cancellationReason)
            rule()
        }
        detail(label("Receipt", "Nota"), sale.number)
        detail(label("Date", "Tanggal"), date.format(Instant.ofEpochMilli(sale.createdAt)))
        if (settings.showCustomer) {
            detail(label("Customer", "Pelanggan"), sale.customerName)
            detail(label("Phone", "Telepon"), sale.customerPhone)
        }
        detail(label("Cashier", "Kasir"), shop.cashier)
        rule()
        for (item in sale.lines) {
            val product = item.product
            line(product.name)
            val quantity = numbers.format(item.quantity)
            val unit = if (product.kind == ProductKind.PHYSICAL && product.unit.isNotBlank()) " ${product.unit}" else ""
            row("$quantity$unit x ${money(product.price)}", money(Math.multiplyExact(item.quantity, product.price)))
            when (product.kind) {
                ProductKind.PHYSICAL -> Unit
                ProductKind.PULSA -> {
                    detail(label("Phone", "Nomor tujuan"), item.destination)
                    detail(label("Operator", "Operator"), product.provider)
                    if (product.nominal > 0) detail(label("Nominal", "Nominal"), money(product.nominal))
                }
                ProductKind.ELECTRICITY -> {
                    detail(label("Meter / customer ID", "Meter / ID pelanggan"), item.destination)
                    if (product.nominal > 0) detail(label("Nominal", "Nominal"), money(product.nominal))
                    detail(label("Token", "Token"), item.code)
                }
                ProductKind.DATA -> {
                    detail(label("Phone", "Nomor tujuan"), item.destination)
                    detail(label("Operator", "Operator"), product.provider)
                    detail(label("Package", "Paket"), product.packageName)
                }
                ProductKind.VOUCHER -> {
                    detail(label("Provider", "Penyedia"), product.provider)
                    detail(label("Package", "Paket"), product.packageName)
                    detail(label("Voucher code", "Kode voucher"), item.code)
                }
                ProductKind.OTHER -> {
                    detail(label("Destination", "Tujuan"), item.destination)
                    detail(label("Provider", "Penyedia"), product.provider)
                    detail(label("Package", "Paket"), product.packageName)
                    detail(label("Code", "Kode"), item.code)
                }
            }
            if (product.kind == ProductKind.PULSA || product.kind == ProductKind.DATA) {
                detail(label("Code", "Kode"), item.code)
            }
            detail(label("Note", "Catatan"), item.note)
        }
        rule()
        row(label("Subtotal", "Subtotal"), money(sale.subtotal))
        if (settings.showDiscount && sale.discount > 0) row(label("Discount", "Diskon"), "-${money(sale.discount)}")
        row(label("TOTAL", "TOTAL"), money(sale.total))
        val payment = when (sale.payment.lowercase(Locale.ROOT)) {
            "cash", "tunai" -> label("Cash", "Tunai")
            "noncash", "non_cash", "non-cash", "cashless", "nontunai", "non tunai" -> label("Non-cash", "Nontunai")
            else -> sale.payment
        }
        detail(label("Payment", "Pembayaran"), payment)
        row(label("Paid", "Dibayar"), money(sale.paid))
        row(label("Change", "Kembalian"), money(Math.subtractExact(sale.paid, sale.total)))
        rule()
        optional(shop.footer)
        return result.toString()
    }

    fun testText(settings: ShopSettings): String {
        val id = settings.language.substringBefore('-').equals("id", ignoreCase = true)
        val name = if (id) "Cetak percobaan" else "Test print"
        return text(
            Sale(
                id = 0, number = "TEST", createdAt = System.currentTimeMillis(),
                zoneId = ZoneId.systemDefault().id,
                lines = listOf(CartLine(Product(name = name, price = 10_000))),
                customerId = null, customerName = "", customerPhone = "",
                subtotal = 10_000, discount = 0, total = 10_000, payment = "cash", paid = 10_000,
                shop = settings,
            ),
            settings,
        )
    }

    @SuppressLint("MissingPermission")
    private fun adapter(context: Context): BluetoothAdapter {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Bluetooth permission required: $permission")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("Bluetooth is unavailable")
        if (!adapter.isEnabled) throw IOException("Bluetooth is turned off")
        return adapter
    }

    @SuppressLint("MissingPermission")
    fun pairedPrinters(context: Context): List<Pair<String, String>> =
        adapter(context).bondedDevices.map { (it.name?.takeIf(String::isNotBlank) ?: it.address) to it.address }
            .sortedBy { it.first }

    @SuppressLint("MissingPermission")
    suspend fun print(context: Context, address: String, text: String) = withContext(Dispatchers.IO) {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "Invalid Bluetooth address" }
        val socket = adapter(context).getRemoteDevice(address)
            .createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        try {
            withTimeout(15_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { runCatching { socket.close() } }
                    launch(Dispatchers.IO) {
                        try {
                            socket.connect()
                            // ponytail: ASCII subset of CP437; add tested code pages for richer printer glyphs.
                            val safe = StringBuilder()
                            text.codePoints().forEach { code ->
                                safe.append(when (code) {
                                    10 -> '\n'
                                    in 32..126 -> code.toChar()
                                    else -> '?'
                                })
                            }
                            val output = socket.outputStream
                            output.write(byteArrayOf(0x1b, 0x40, 0x1b, 0x74, 0x00))
                            output.write(safe.toString().toByteArray(Charsets.US_ASCII))
                            output.write(byteArrayOf(0x0a, 0x0a, 0x0a))
                            output.flush()
                            continuation.resume(Unit)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    }
}
