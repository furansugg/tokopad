package com.tokopad.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReceiptBlock(
    val text: String,
    val emphasis: Boolean = false,
    val large: Boolean = false,
    val centered: Boolean = false,
    val logo: String = "",
)

object Receipt {
    fun blocks(sale: Sale, settings: ShopSettings): List<ReceiptBlock> {
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
        val result = mutableListOf<ReceiptBlock>()
        fun length(value: String) = value.codePointCount(0, value.length)
        fun line(value: String, emphasis: Boolean = false, large: Boolean = false, centered: Boolean = false) {
            val columns = if (large) width / 2 else width
            for (raw in value.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                var remaining = raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
                while (length(remaining) > columns) {
                    val end = remaining.offsetByCodePoints(0, columns)
                    val space = remaining.lastIndexOf(' ', end)
                    val cut = if (space in 1..end) space else end
                    result += ReceiptBlock(remaining.substring(0, cut), emphasis, large, centered)
                    remaining = remaining.substring(cut).trimStart()
                }
                result += ReceiptBlock(remaining, emphasis, large, centered)
            }
        }
        fun optional(value: String, centered: Boolean = false) { if (value.isNotBlank()) line(value, centered = centered) }
        fun detail(name: String, value: String) { if (value.isNotBlank()) line("$name: $value") }
        fun row(name: String, value: String, emphasis: Boolean = false) {
            val padding = width - length(name) - length(value)
            if (padding >= 1 && !name.contains('\n') && !value.contains('\n')) {
                line(name + " ".repeat(padding) + value, emphasis = emphasis)
            } else {
                line(name, emphasis = emphasis)
                line(value, emphasis = emphasis)
            }
        }
        fun rule(character: Char = '-') = line(character.toString().repeat(width))
        fun token(value: String) {
            if (value.isBlank()) return
            line(label("TOKEN / STROOM NUMBER", "NOMOR TOKEN / STROOM"), emphasis = true, centered = true)
            val digits = value.filter { it in '0'..'9' }
            if (digits.isNotEmpty() && value.all { it in '0'..'9' || it == ' ' || it == '-' }) {
                val groups = digits.chunked(4)
                val perLine = (width / 2 + 1) / 5
                groups.chunked(perLine).forEach { line(it.joinToString(" "), emphasis = true, large = true, centered = true) }
            } else line(value, emphasis = true, large = true, centered = true)
        }
        val date = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", locale).withZone(ZoneId.of(sale.zoneId))
        val shop = sale.shop
        require(shop.headerMode in listOf("name", "logo", "both")) { "Unsupported receipt header mode" }
        if (shop.headerMode != "name" && shop.logo.isNotBlank()) {
            result += ReceiptBlock("", centered = true, logo = shop.logo)
        }
        if (shop.headerMode != "logo" && shop.shopName.isNotBlank()) {
            line(shop.shopName, emphasis = true, centered = true)
        }
        optional(shop.address, centered = true)
        optional(shop.contact, centered = true)
        optional(shop.header, centered = true)
        val electricityOnly = sale.lines.isNotEmpty() && sale.lines.all { it.product.kind == ProductKind.ELECTRICITY }
        if (electricityOnly) {
            rule('=')
            line(label("PLN ELECTRICITY TOKEN RECEIPT", "STRUK TOKEN LISTRIK PLN"), centered = true)
            rule('=')
            if (sale.cancelledAt != null) {
                line("*** ${label("CANCELLED", "DIBATALKAN")} ***", emphasis = true)
                detail(label("Cancelled", "Dibatalkan"), date.format(Instant.ofEpochMilli(sale.cancelledAt)))
                detail(label("Reason", "Alasan"), sale.cancellationReason)
                rule()
            }
            fun field(name: String, value: String) { if (value.isNotBlank()) line("${name.padEnd(6)}: $value") }
            field("No", sale.receiptNumber)
            field(label("Date", "Tgl"), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)
                .withZone(ZoneId.of(sale.zoneId)).format(Instant.ofEpochMilli(sale.createdAt)))
            if (shop.cashier.isNotBlank()) field(label("Cashier", "Kasir"), shop.cashier)
            for (item in sale.lines) {
                field(label("Buyer", "Plg"), item.buyerName.ifBlank { sale.customerName })
                field("Meter", item.destination)
                field("kWh", item.kwh.takeIf { it.isNotBlank() }?.let { "$it kWh" }.orEmpty())
                field(label("Product", "Produk"), item.product.name)
                detail(label("Note", "Catatan"), item.note)
                rule()
                token(item.code)
                rule()
                // Nominal here is the charged line amount, not the prepaid face value.
                row(label("Amount", "Nominal"), numbers.format(Math.multiplyExact(item.quantity, item.product.price)))
                if (item.quantity != 1L) detail(label("Quantity", "Jumlah"), numbers.format(item.quantity))
                if (sale.lines.size > 1) rule()
            }
            if (settings.showDiscount && sale.discount > 0) {
                row(label("Subtotal", "Subtotal"), money(sale.subtotal))
                row(label("Discount", "Diskon"), "-${money(sale.discount)}")
            }
            rule('=')
            row("TOTAL", money(sale.total), emphasis = true)
            val method = if (sale.payment == "cash") label("CASH", "TUNAI") else label("NONCASH", "NONTUNAI")
            row(label("Paid", "Bayar") + " ($method)", numbers.format(sale.paid))
            if (sale.paid != sale.total) row(label("Change", "Kembali"), money(Math.subtractExact(sale.paid, sale.total)))
            rule('=')
            optional(shop.footer, centered = true)
            return result
        }
        rule()
        if (sale.cancelledAt != null) {
            line("*** ${label("CANCELLED", "DIBATALKAN")} ***", emphasis = true)
            detail(label("Cancelled", "Dibatalkan"), date.format(Instant.ofEpochMilli(sale.cancelledAt)))
            detail(label("Reason", "Alasan"), sale.cancellationReason)
            rule()
        }
        detail(label("Receipt", "Nota"), sale.receiptNumber)
        detail(label("Date", "Tanggal"), date.format(Instant.ofEpochMilli(sale.createdAt)))
        if (settings.showCustomer) {
            detail(label("Customer", "Pelanggan"), sale.customerName)
            detail(label("Phone", "Telepon"), sale.customerPhone)
        }
        detail(label("Cashier", "Kasir"), shop.cashier)
        rule()
        for (item in sale.lines) {
            val product = item.product
            line(product.name, emphasis = true)
            val quantity = numbers.format(item.quantity)
            val unit = if (product.kind == ProductKind.PHYSICAL && product.unit.isNotBlank()) " ${product.unit}" else ""
            if (product.kind != ProductKind.PHYSICAL) detail(label("Buyer", "Nama pembeli"), item.buyerName)
            when (product.kind) {
                ProductKind.PHYSICAL -> Unit
                ProductKind.PULSA -> {
                    detail(label("Phone", "Nomor tujuan"), item.destination)
                    detail(label("Operator", "Operator"), product.provider)
                    if (product.nominal > 0) detail(label("Nominal", "Nominal"), money(product.nominal))
                }
                ProductKind.ELECTRICITY -> {
                    detail(label("Meter / customer ID", "Meter / ID pelanggan"), item.destination)
                    detail("kWh", item.kwh)
                    token(item.code)
                    if (product.nominal > 0) detail(label("Nominal", "Nominal"), money(product.nominal))
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
            row("$quantity$unit x ${money(product.price)}", money(Math.multiplyExact(item.quantity, product.price)))
        }
        rule()
        row(label("Subtotal", "Subtotal"), money(sale.subtotal))
        if (settings.showDiscount && sale.discount > 0) row(label("Discount", "Diskon"), "-${money(sale.discount)}")
        row(label("TOTAL", "TOTAL"), money(sale.total), emphasis = true)
        val payment = when (sale.payment.lowercase(Locale.ROOT)) {
            "cash", "tunai" -> label("Cash", "Tunai")
            "noncash", "non_cash", "non-cash", "cashless", "nontunai", "non tunai" -> label("Non-cash", "Nontunai")
            else -> sale.payment
        }
        detail(label("Payment", "Pembayaran"), payment)
        row(label("Paid", "Dibayar"), money(sale.paid))
        row(label("Change", "Kembalian"), money(Math.subtractExact(sale.paid, sale.total)))
        rule()
        optional(shop.footer, centered = true)
        return result
    }

    fun text(sale: Sale, settings: ShopSettings): String =
        blocks(sale, settings).filter { it.logo.isEmpty() }.joinToString("\n", postfix = "\n") { it.text }

    fun bytes(sale: Sale, settings: ShopSettings): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0x1b, 0x40, 0x1b, 0x74, 0x00))
        for (block in blocks(sale, settings)) {
            write(byteArrayOf(0x1b, 0x61, if (block.centered) 1 else 0, 0x1b, 0x45, if (block.emphasis) 1 else 0, 0x1d, 0x21, if (block.large) 0x11 else 0))
            if (block.logo.isNotEmpty()) write(raster(block.logo)) else write(ascii(block.text))
            write(0x0a)
        }
        write(byteArrayOf(0x1b, 0x61, 0x00, 0x1b, 0x45, 0x00, 0x1d, 0x21, 0x00, 0x0a, 0x0a, 0x0a))
    }.toByteArray()

    private fun ascii(text: String): ByteArray {
        // ponytail: ASCII subset of CP437; add tested code pages for richer printer glyphs.
        val safe = StringBuilder()
        text.codePoints().forEach { code ->
            safe.append(when (code) {
                10 -> '\n'
                in 32..126 -> code.toChar()
                else -> '?'
            })
        }
        return safe.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun raster(logo: String): ByteArray {
        require(logo.length <= 32_768) { "Receipt logo must be at most 24 KiB" }
        val png = Base64.getDecoder().decode(logo)
        require(png.size <= 24_576 && png.size >= 8 && png.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        )) { "Receipt logo must be a PNG of at most 24 KiB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        require(bounds.outWidth in 1..384 && bounds.outHeight in 1..192) { "Receipt logo must fit 384 x 192 pixels" }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(png, 0, png.size)) { "Invalid receipt logo PNG" }
        try {
            val stride = (bitmap.width + 7) / 8
            val data = ByteArray(stride * bitmap.height)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24
                val luminance = (299 * (pixel shr 16 and 255) + 587 * (pixel shr 8 and 255) + 114 * (pixel and 255)) / 1000
                if (alpha * (255 - luminance) > 127 * 255) {
                    val offset = y * stride + x / 8
                    data[offset] = (data[offset].toInt() or (0x80 ushr (x % 8))).toByte()
                }
            }
            return byteArrayOf(0x1d, 0x76, 0x30, 0x00, stride.toByte(), (stride shr 8).toByte(), bitmap.height.toByte(), (bitmap.height shr 8).toByte()) + data
        } finally {
            bitmap.recycle()
        }
    }

    fun testText(settings: ShopSettings): String = text(testSale(settings), settings)

    private fun testSale(settings: ShopSettings): Sale {
        val id = settings.language.substringBefore('-').equals("id", ignoreCase = true)
        val name = if (id) "Cetak percobaan" else "Test print"
        return Sale(
            id = 0, number = "TEST", createdAt = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault().id,
            lines = listOf(CartLine(Product(name = name, price = 10_000))),
            customerId = null, customerName = "", customerPhone = "",
            subtotal = 10_000, discount = 0, total = 10_000, payment = "cash", paid = 10_000,
            shop = settings,
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
        adapter(context).bondedDevices.map {
            val address = it.address.uppercase(Locale.ROOT)
            (it.name?.takeIf(String::isNotBlank) ?: address) to address
        }
            .sortedBy { it.first }

    suspend fun print(context: Context, address: String, text: String) = send(
        context, address, byteArrayOf(0x1b, 0x40, 0x1b, 0x74, 0x00) + ascii(text) + byteArrayOf(0x0a, 0x0a, 0x0a),
    )

    suspend fun print(context: Context, address: String, sale: Sale, settings: ShopSettings) =
        withContext(Dispatchers.IO) { send(context, address, bytes(sale, settings)) }

    suspend fun printTest(context: Context, settings: ShopSettings) =
        print(context, settings.printerAddress, testSale(settings), settings)

    private suspend fun send(context: Context, address: String, bytes: ByteArray) = withPrinter(context, address) { socket ->
        val output = socket.outputStream
        output.write(bytes)
        output.flush()
    }

    suspend fun testConnection(context: Context, address: String) = withPrinter(context, address) { }

    @SuppressLint("MissingPermission")
    private suspend fun withPrinter(context: Context, address: String, write: (BluetoothSocket) -> Unit) =
        withContext(Dispatchers.IO) {
            val normalized = address.trim().uppercase(Locale.ROOT)
            require(BluetoothAdapter.checkBluetoothAddress(normalized)) { "Invalid Bluetooth address" }
            val adapter = adapter(context)
            val device = adapter.getRemoteDevice(normalized)
            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                throw IOException("Printer is Bluetooth LE-only; Classic Bluetooth SPP is required. BLE printers are not supported.")
            }
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                throw IOException("Printer is not paired. Pair it in Android Bluetooth settings first.")
            }
            val discoveryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.BLUETOOTH_ADMIN
            }
            val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            PrinterConnection.use(
                secure = { device.createRfcommSocketToServiceRecord(spp) },
                insecure = { device.createInsecureRfcommSocketToServiceRecord(spp) },
                connect = { socket ->
                    if (context.checkSelfPermission(discoveryPermission) == PackageManager.PERMISSION_GRANTED) {
                        adapter.cancelDiscovery()
                    }
                    socket.connect()
                },
                write = write,
            )
        }
}
