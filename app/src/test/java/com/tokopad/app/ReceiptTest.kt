package com.tokopad.app

import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTest {
    @Test fun plnReceiptMatchesReferenceSectionsAndAlignedLabels() {
        val transaction = sale(listOf(CartLine(
            Product(name = "Token PLN 20000", price = 24_000, nominal = 20_000, kind = ProductKind.ELECTRICITY),
            buyerName = "JURIAH", destination = "14484689824", kwh = "30", code = "51514314049379930860",
        ))).copy(discount = 0, total = 24_000, paid = 24_000,
            shop = ShopSettings(shopName = "RAZZSTORE", address = "JL. BUNGA RAYA GG. SAUDARA", footer = "Terima kasih."))
        for ((paper, columns) in listOf(58 to 32, 80 to 48)) {
            val blocks = Receipt.blocks(transaction, ShopSettings(language = "id", paperWidth = paper))
            val text = blocks.joinToString("\n") { it.text }
            val sections = listOf("RAZZSTORE", "JL. BUNGA RAYA GG. SAUDARA", "STRUK TOKEN LISTRIK PLN",
                "No    : TP-00000001", "Tgl   : 01/01/1970 07:00", "Plg   : JURIAH", "Meter : 14484689824",
                "kWh   : 30 kWh", "Produk: Token PLN 20000", "NOMOR TOKEN / STROOM", "Nominal", "TOTAL", "Bayar (TUNAI)", "Terima kasih.")
            assertTrue(sections.zipWithNext().all { (a, b) -> text.indexOf(a) < text.indexOf(b) })
            assertTrue(blocks.all { it.text.codePointCount(0, it.text.length) <= if (it.large) columns / 2 else columns })
            assertEquals("51514314049379930860", blocks.filter { it.large }.joinToString("") { it.text.replace(" ", "") })
            assertEquals(listOf("5151 4314 0493", "7993 0860"), blocks.filter { it.large }.map { it.text })
            assertTrue(blocks.filter { it.large }.all { it.emphasis && it.centered })
            assertEquals("Nominal" + " ".repeat(columns - 7 - 6) + "24.000", blocks.first { it.text.startsWith("Nominal") }.text)
            assertTrue(blocks.last().centered)
            assertTrue(blocks.any { it.text == "=".repeat(columns) })
            assertFalse(text.contains("Subtotal"))
            assertFalse(text.contains("20.000"))
            assertFalse(text.contains("Kembali"))
        }
    }

    private val shop = ShopSettings(
        shopName = "Original shop", address = "Original address", contact = "0210001",
        header = "Original header", footer = "Original footer", cashier = "Original cashier",
    )
    private fun sale(lines: List<CartLine> = listOf(CartLine(Product(name = "Rice", price = 12_000), quantity = 2))) = Sale(
        id = 1, number = "SALE-0001", createdAt = 0, zoneId = "Asia/Jakarta", lines = lines,
        customerId = 3, customerName = "Original customer", customerPhone = "08123456789",
        subtotal = 24_000, discount = 1_000, total = 23_000, payment = "cash", paid = 25_000, shop = shop,
    )

    @Test fun snapshotBusinessDataWithCurrentPresentation() {
        val result = Receipt.text(sale(), ShopSettings(
            language = "id", paperWidth = 80, shopName = "Changed shop", address = "Changed address",
            header = "Changed header", footer = "Changed footer", cashier = "Changed cashier",
        ))
        for (value in listOf("Original shop", "Original address", "Original header", "Original footer", "Original cashier", "Original customer")) {
            assertTrue(value, result.contains(value))
        }
        assertFalse(result.contains("Changed"))
        assertTrue(result.contains("Tanggal: 01 Jan 1970 07:00"))
        assertTrue(result.contains("Pembayaran: Tunai"))
        assertTrue(result.contains("Diskon"))
        assertTrue(result.contains("-Rp 1.000"))
        assertTrue(result.contains("Rp 23.000"))
        assertTrue(result.contains("Kembalian"))
        assertTrue(result.contains("Rp 2.000"))
        assertTrue(result.lines().contains("-".repeat(48)))
    }

    @Test fun englishPhysicalAmountsAndOptionalFields() {
        val result = Receipt.text(sale(), ShopSettings(showCustomer = false, showDiscount = false))
        assertTrue(result.contains("2 pcs x Rp 12,000"))
        assertTrue(result.contains("Rp 24,000"))
        assertTrue(result.contains("Payment: Cash"))
        assertTrue(result.contains("Paid"))
        assertTrue(result.contains("Change"))
        assertTrue(result.contains("Rp 2,000"))
        assertFalse(result.contains("Discount"))
        assertFalse(result.contains("Customer:"))
        assertFalse(result.contains("08123456789"))
        assertFalse(result.contains("Destination:"))
        assertFalse(result.contains("Code:"))
    }

    @Test fun mixedItemsUseServiceSpecificDetails() {
        val lines = listOf(
            CartLine(Product(name = "Rice", price = 10_000)),
            CartLine(Product(name = "Credit", price = 21_000, kind = ProductKind.PULSA, provider = "Operator A", nominal = 20_000), destination = "001234"),
            CartLine(Product(name = "Electricity", price = 22_000, kind = ProductKind.ELECTRICITY, nominal = 20_000), destination = "000987", code = "0011-2233-4455-6677-8899"),
            CartLine(Product(name = "Data", price = 30_000, kind = ProductKind.DATA, provider = "Operator B", packageName = "10 GB"), destination = "008888"),
            CartLine(Product(name = "WiFi", price = 5_000, kind = ProductKind.VOUCHER, provider = "Provider C", packageName = "1 hour"), code = "000CODE"),
            CartLine(Product(name = "Other service", price = 1_000, kind = ProductKind.OTHER), destination = "000DEST", code = "000OTHER", note = "Manual service"),
        )
        val result = Receipt.text(sale(lines), ShopSettings(paperWidth = 80))
        for (value in listOf(
            "Phone: 001234", "Operator: Operator A", "Nominal: Rp 20,000",
            "Meter / customer ID: 000987", "TOKEN / STROOM NUMBER\n0011 2233 4455\n6677 8899",
            "Phone: 008888", "Package: 10 GB", "Provider: Provider C", "Package: 1 hour",
            "Voucher code: 000CODE", "Destination: 000DEST", "Code: 000OTHER", "Note: Manual service",
        )) assertTrue(value, result.contains(value))
        assertFalse(result.lines().any { it.trimEnd().endsWith(":") })
    }

    @Test fun longLinesWrapWithoutLosingTokensOrAmounts() {
        val token = "001122334455667788990011223344556677889900112233445566778899"
        val transaction = sale(listOf(CartLine(
            Product(name = "Long product name ".repeat(8), price = Long.MAX_VALUE, kind = ProductKind.ELECTRICITY),
            destination = "00001", code = token,
        ))).copy(subtotal = Long.MAX_VALUE, discount = 0, total = Long.MAX_VALUE, paid = Long.MAX_VALUE)
        for ((paper, columns) in listOf(58 to 32, 80 to 48)) {
            val result = Receipt.text(transaction, ShopSettings(paperWidth = paper))
            assertTrue(result.lines().all { it.codePointCount(0, it.length) <= columns })
            assertTrue(result.contains("Rp 9,223,372,036,854,775,807"))
            assertTrue(result.substringAfter("TOKEN / STROOM NUMBER").replace(Regex("\\s"), "").startsWith(token))
            val enlarged = Receipt.blocks(transaction, ShopSettings(paperWidth = paper)).filter { it.large }
            assertTrue(enlarged.all { it.text.length <= columns / 2 && it.emphasis && it.centered })
            assertEquals(token, enlarged.joinToString("") { it.text.replace(" ", "") })
        }
    }

    @Test fun cancellationIsProminentAndLocalized() {
        val transaction = sale().copy(cancelledAt = 60_000, cancellationReason = "Wrong item")
        val english = Receipt.text(transaction, ShopSettings())
        assertTrue(english.contains("*** CANCELLED ***"))
        assertTrue(english.contains("Cancelled: 01 Jan 1970 07:01"))
        assertTrue(english.contains("Reason: Wrong item"))
        val indonesian = Receipt.text(transaction, ShopSettings(language = "id"))
        assertTrue(indonesian.contains("*** DIBATALKAN ***"))
        assertTrue(indonesian.contains("Alasan: Wrong item"))
    }

    private fun creditSale(kind: ProductKind, paid: Long) = sale(listOf(CartLine(
        Product(name = if (kind == ProductKind.ELECTRICITY) "PLN 20" else "Rice", price = 24_000, kind = kind),
        buyerName = "Siti", destination = "000987", kwh = "13.2", code = "12345678901234567890",
    ))).copy(isCredit = true, paid = paid)

    private fun creditText(transaction: Sale, language: String, paper: Int): String {
        val settings = ShopSettings(language = language, paperWidth = paper, showCustomer = false, showDiscount = false)
        val blocks = Receipt.blocks(transaction, settings)
        val columns = if (paper == 80) 48 else 32
        assertTrue(blocks.all { it.text.codePointCount(0, it.text.length) <= if (it.large) columns / 2 else columns })
        val text = Receipt.text(transaction, settings).replace(Regex("\\s+"), " ")
        val id = language == "id"
        assertTrue(text.contains(if (id) "Penanggung jawab: Original customer" else "Responsible customer: Original customer"))
        assertTrue(text.contains(if (id) "Telepon: 08123456789" else "Phone: 08123456789"))
        assertTrue(text.contains(if (id) "Metode awal: Tunai" else "Initial method: Cash"))
        assertTrue(text.contains(if (id) "TOTAL Rp 23.000" else "TOTAL Rp 23,000"))
        assertFalse(text.contains("Change"))
        assertFalse(text.contains("Kembali"))
        assertFalse(text.contains("Rp -"))
        assertFalse(text.contains("-Rp"))
        assertTrue(blocks.any { it.emphasis && it.text.startsWith(if (id) "Status utang:" else "Credit status:") })
        if (transaction.lines.single().product.kind == ProductKind.ELECTRICITY) {
            assertTrue(text.contains(if (id) "Plg : Siti" else "Buyer : Siti"))
            assertEquals("12345678901234567890", blocks.filter { it.large }.joinToString("") { it.text.replace(" ", "") })
        }
        return text
    }

    @Test fun zeroPaymentCreditShowsResponsibleCustomerAndEntireDebt() {
        for (kind in listOf(ProductKind.PHYSICAL, ProductKind.ELECTRICITY)) {
            for (language in listOf("en", "id")) for (paper in listOf(58, 80)) {
                val text = creditText(creditSale(kind, 0), language, paper)
                val sections = if (language == "id") listOf(
                    "Bayar awal Rp 0", "Total dibayar Rp 0", "Sisa utang Rp 23.000", "Status utang: BELUM LUNAS",
                ) else listOf("Initial payment Rp 0", "Paid total Rp 0", "Outstanding Rp 23,000", "Credit status: OWING")
                for (section in sections) assertTrue(section, text.contains(section))
                assertFalse(text.contains("Settled:"))
                assertFalse(text.contains("Dilunasi:"))
            }
        }
    }

    @Test fun partialCreditShowsInitialTenderAndRemainingDebt() {
        for (kind in listOf(ProductKind.PHYSICAL, ProductKind.ELECTRICITY)) {
            for (language in listOf("en", "id")) for (paper in listOf(58, 80)) {
                val text = creditText(creditSale(kind, 5_000), language, paper)
                val sections = if (language == "id") listOf(
                    "Bayar awal Rp 5.000", "Total dibayar Rp 5.000", "Sisa utang Rp 18.000", "Status utang: BELUM LUNAS",
                ) else listOf("Initial payment Rp 5,000", "Paid total Rp 5,000", "Outstanding Rp 18,000", "Credit status: OWING")
                for (section in sections) assertTrue(section, text.contains(section))
                assertFalse(text.contains("Settled:"))
                assertFalse(text.contains("Dilunasi:"))
            }
        }
    }

    @Test fun settledCreditKeepsInitialTenderAndAddsDatedSettlement() {
        for (kind in listOf(ProductKind.PHYSICAL, ProductKind.ELECTRICITY)) {
            for (language in listOf("en", "id")) for (paper in listOf(58, 80)) {
                val transaction = creditSale(kind, 5_000).copy(settlement = Settlement(18_000, "non_cash", 86_400_000, "private-request"))
                val text = creditText(transaction, language, paper)
                val sections = if (language == "id") listOf(
                    "Bayar awal Rp 5.000", "Dilunasi: 02 Jan 1970 07:00", "Metode pelunasan: Nontunai",
                    "Pelunasan Rp 18.000", "Total dibayar Rp 23.000", "Sisa utang Rp 0", "Status utang: LUNAS",
                ) else listOf(
                    "Initial payment Rp 5,000", "Settled: 02 Jan 1970 07:00", "Settlement method: Non-cash",
                    "Settlement Rp 18,000", "Paid total Rp 23,000", "Outstanding Rp 0", "Credit status: PAID",
                )
                for (section in sections) assertTrue(section, text.contains(section))
                assertFalse(text.contains("private-request"))
                assertFalse(text.contains("BELUM LUNAS"))
                assertFalse(text.contains("OWING"))
                assertEquals(5_000L, transaction.paid)
            }
        }
    }

    @Test fun cancelledCreditVoidsDebtButKeepsPaymentHistoryForManualRefunds() {
        for (kind in listOf(ProductKind.PHYSICAL, ProductKind.ELECTRICITY)) {
            val original = creditSale(kind, 5_000)
            for (transaction in listOf(original.copy(paid = 0), original,
                original.copy(settlement = Settlement(18_000, "noncash", 86_400_000, "private-request")))) {
                for (language in listOf("en", "id")) for (paper in listOf(58, 80)) {
                    val text = creditText(transaction.copy(cancelledAt = 172_800_000, cancellationReason = "Wrong item"), language, paper)
                    val id = language == "id"
                    val sections = if (id) listOf(
                        "*** DIBATALKAN ***", "Dibatalkan: 03 Jan 1970 07:00", "Alasan: Wrong item",
                        "Sisa utang (batal) Rp 0", "Status utang: DIBATALKAN", "Pengembalian dana manual",
                    ) else listOf(
                        "*** CANCELLED ***", "Cancelled: 03 Jan 1970 07:00", "Reason: Wrong item",
                        "Outstanding (void) Rp 0", "Credit status: CANCELLED", "Refunds handled manually",
                    )
                    for (section in sections) assertTrue(section, text.contains(section))
                    val initial = if (transaction.paid == 0L) "0" else if (id) "5.000" else "5,000"
                    val received = if (transaction.settlement != null) { if (id) "23.000" else "23,000" } else initial
                    assertTrue(text.contains("${if (id) "Bayar awal" else "Initial payment"} Rp $initial"))
                    assertTrue(text.contains("${if (id) "Total dibayar" else "Paid total"} Rp $received"))
                    if (transaction.settlement != null) {
                        assertTrue(text.contains(if (id) "Dilunasi: 02 Jan 1970 07:00" else "Settled: 02 Jan 1970 07:00"))
                        assertTrue(text.contains(if (id) "Metode pelunasan: Nontunai" else "Settlement method: Non-cash"))
                        assertTrue(text.contains(if (id) "Pelunasan Rp 18.000" else "Settlement Rp 18,000"))
                    }
                    assertFalse(text.contains("Credit status: PAID"))
                    assertFalse(text.contains("Status utang: LUNAS"))
                    assertFalse(text.contains("BELUM LUNAS"))
                    assertFalse(text.contains("OWING"))
                }
            }
        }
    }

    @Test fun paidInFullNonCreditKeepsExistingPaymentSections() {
        for (kind in listOf(ProductKind.PHYSICAL, ProductKind.ELECTRICITY)) {
            for (language in listOf("en", "id")) for (paper in listOf(58, 80)) {
                val transaction = creditSale(kind, 23_000).copy(isCredit = false)
                val text = Receipt.text(transaction, ShopSettings(language = language, paperWidth = paper, showCustomer = false))
                    .replace(Regex("\\s+"), " ")
                val id = language == "id"
                if (kind == ProductKind.ELECTRICITY) {
                    assertTrue(text.contains(if (id) "Bayar (TUNAI) 23.000" else "Paid (CASH) 23,000"))
                    assertFalse(text.contains("Change"))
                    assertFalse(text.contains("Kembali"))
                } else {
                    assertTrue(text.contains(if (id) "Pembayaran: Tunai" else "Payment: Cash"))
                    assertTrue(text.contains(if (id) "Dibayar Rp 23.000" else "Paid Rp 23,000"))
                    assertTrue(text.contains(if (id) "Kembalian Rp 0" else "Change Rp 0"))
                }
                for (label in listOf("Responsible customer", "Penanggung jawab", "Initial payment", "Bayar awal",
                    "Outstanding", "Sisa utang", "Credit status", "Status utang", "Original customer")) {
                    assertFalse(label, text.contains(label))
                }
            }
        }
    }

    @Test fun blankOptionalValuesProduceNoLabels() {
        val result = Receipt.text(sale().copy(
            customerName = "", customerPhone = "", shop = ShopSettings(shopName = "", footer = ""),
            payment = "non_cash", paid = 23_000,
        ), ShopSettings())
        assertFalse(result.contains("Customer:"))
        assertFalse(result.contains("Phone:"))
        assertFalse(result.contains("Cashier:"))
        assertTrue(result.contains("Payment: Non-cash"))
        assertFalse(result.lines().any { it.trimEnd().endsWith(":") })
    }

    @Test fun testPrintUsesCurrentShopAndLanguage() {
        val result = Receipt.testText(shop.copy(language = "id", paperWidth = 80))
        assertTrue(result.contains("Original shop"))
        assertTrue(result.contains("Cetak percobaan"))
        assertTrue(result.contains("Nota: TP-00000000"))
        assertTrue(result.contains("Rp 10.000"))
        assertTrue(result.lines().all { it.length <= 48 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedPaperWidthIsRejected() {
        Receipt.text(sale(), ShopSettings(paperWidth = 42))
    }

    @Test fun unicodePreviewPreservesCodePointsAndNeutralizesControlCharacters() {
        val name = "Café \uD83D\uDE00 ".repeat(12) + "\u001b@"
        val result = Receipt.text(sale(listOf(CartLine(Product(name = name, price = 12_000)))), ShopSettings())
        assertTrue(result.contains("Café \uD83D\uDE00"))
        assertFalse(result.contains('\u001b'))
        assertTrue(result.lines().all { it.codePointCount(0, it.length) <= 32 })
        assertEquals(12, Regex("\uD83D\uDE00").findAll(result).count())
    }

    @Test fun receiptNumberNeverExposesInternalIdentifier() {
        val transaction = sale().copy(id = 42, number = "3aa971ab-70f4-459c-9a56-00333b790d21")
        val result = Receipt.text(transaction, ShopSettings())
        assertTrue(result.contains("Receipt: TP-00000042"))
        assertFalse(result.contains(transaction.number))
    }

    @Test fun headerModesUseSnapshotRatherThanCurrentSettings() {
        for (mode in listOf("name", "logo", "both")) {
            val transaction = sale().copy(shop = shop.copy(headerMode = mode, logo = "snapshot-logo"))
            val blocks = Receipt.blocks(transaction, ShopSettings(headerMode = "name", logo = "changed-logo"))
            assertEquals(mode != "logo", blocks.any { it.text == shop.shopName && it.centered && it.emphasis })
            assertEquals(mode != "name", blocks.any { it.logo == "snapshot-logo" && it.centered })
            assertFalse(blocks.any { it.logo == "changed-logo" })
            assertTrue(blocks.first { it.text == shop.header }.centered)
            assertEquals(mode != "logo", Receipt.text(transaction, ShopSettings()).contains(shop.shopName))
        }
    }

    @Test fun englishPlnFieldColonsAlign() {
        val transaction = sale(listOf(CartLine(Product(name = "PLN", price = 1, kind = ProductKind.ELECTRICITY),
            buyerName = "Siti", destination = "123", kwh = "1")))
        val fields = Receipt.blocks(transaction, ShopSettings()).map { it.text }
            .filter { it.startsWith("No ") || it.startsWith("Date ") || it.startsWith("Cashier:") ||
                it.startsWith("Buyer ") || it.startsWith("Meter ") || it.startsWith("kWh ") || it.startsWith("Product:") }
        assertEquals(7, fields.size)
        assertTrue(fields.all { it.indexOf(':') == 7 })
    }

    @Test fun digitalBuyerMeterKwhAndTokenPrecedePricesAndTotals() {
        val transaction = sale(listOf(CartLine(
            Product(name = "PLN 20", price = 22_000, kind = ProductKind.ELECTRICITY, nominal = 20_000),
            destination = "000987", code = "12345678901234567890", buyerName = "Siti", kwh = "13.2",
        )))
        val blocks = Receipt.blocks(transaction, ShopSettings())
        val result = Receipt.text(transaction, ShopSettings())
        for (value in listOf("Product: PLN 20", "Buyer  : Siti", "Meter  : 000987", "kWh    : 13.2 kWh")) {
            assertTrue(value, result.contains(value))
        }
        assertEquals(listOf("1234 5678 9012", "3456 7890"), blocks.filter { it.large }.map { it.text })
        val tokenEnd = result.indexOf("3456 7890")
        for (label in listOf("Amount", "Subtotal", "TOTAL", "Paid", "Change")) {
            assertTrue(label, result.indexOf(label) > tokenEnd)
        }
        assertTrue(blocks.first { it.text.startsWith("TOTAL") }.emphasis)
        assertTrue(Receipt.text(transaction, ShopSettings(language = "id")).contains("Plg   : Siti"))
    }

    @Test fun digitalTypesKeepBuyerAndSpecializedDetails() {
        for (kind in ProductKind.entries.filter { it != ProductKind.PHYSICAL }) {
            val result = Receipt.text(sale(listOf(CartLine(
                Product(name = kind.name, price = 1, kind = kind, provider = "Provider", packageName = "Package", nominal = 1),
                buyerName = "Buyer name", destination = "000DEST", code = "ABC123", note = "Service note",
            ))), ShopSettings())
            assertTrue(kind.name, result.contains(if (kind == ProductKind.ELECTRICITY) "Buyer  : Buyer name" else "Buyer: Buyer name"))
            assertTrue(kind.name, result.contains("ABC123"))
            assertTrue(kind.name, result.contains("Note: Service note"))
        }
    }

    @Test fun bytesEmitExactEscPosStylesAndResetAfterToken() {
        val transaction = sale(listOf(CartLine(
            Product(name = "PLN", price = 22_000, kind = ProductKind.ELECTRICITY, nominal = 20_000),
            code = "12345678901234567890",
        )))
        val bytes = Receipt.bytes(transaction, ShopSettings())
        assertArrayEquals(byteArrayOf(0x1b, 0x40, 0x1b, 0x74, 0x00), bytes.copyOfRange(0, 5))
        val wire = bytes.toString(Charsets.US_ASCII)
        assertTrue(wire.contains("\u001ba\u0001\u001bE\u0001\u001d!\u0000Original shop\n"))
        assertTrue(wire.contains("\u001ba\u0001\u001bE\u0001\u001d!\u00111234 5678 9012\n"))
        assertTrue(wire.contains("\u001ba\u0001\u001bE\u0001\u001d!\u00113456 7890\n\u001ba\u0000\u001bE\u0000\u001d!\u0000" + "-".repeat(32)))
        assertTrue(wire.indexOf("3456 7890") < wire.indexOf("TOTAL"))
        val reset = byteArrayOf(0x1b, 0x61, 0x00, 0x1b, 0x45, 0x00, 0x1d, 0x21, 0x00, 0x0a, 0x0a, 0x0a)
        assertArrayEquals(reset, bytes.takeLast(reset.size).toByteArray())
    }

    @Test fun qrRasterRoundTripsSnapshotAfterFooterOnBothReceiptBranches() {
        val payloads = listOf(" leading\n\ttrailing ", "Café 日本語 😀", "x".repeat(200), "😀".repeat(50))
        val reset = byteArrayOf(0x1b, 0x61, 0x00, 0x1b, 0x45, 0x00, 0x1d, 0x21, 0x00, 0x0a, 0x0a, 0x0a)
        val start = byteArrayOf(0x1b, 0x61, 0x01, 0x1b, 0x45, 0x00, 0x1d, 0x21, 0x00, 0x1d, 0x76, 0x30, 0x00)
        for (kind in listOf(ProductKind.PHYSICAL, ProductKind.ELECTRICITY)) for (paper in listOf(58, 80)) {
            val original = sale(listOf(CartLine(Product(name = "Item", price = 1, kind = kind))))
            val disabled = Receipt.bytes(original, ShopSettings(paperWidth = paper, qrContent = "current setting"))
            assertFalse(disabled.toString(Charsets.ISO_8859_1).contains("\u001dv0"))
            for (payload in payloads) {
                val transaction = original.copy(shop = original.shop.copy(qrContent = payload))
                val settings = ShopSettings(paperWidth = paper, qrContent = "different current setting")
                val bytes = Receipt.bytes(transaction, settings)
                val prefix = disabled.copyOfRange(0, disabled.size - reset.size)
                assertArrayEquals(prefix, bytes.copyOfRange(0, prefix.size))
                assertTrue(prefix.toString(Charsets.US_ASCII).endsWith("Original footer\n"))
                assertArrayEquals(start, bytes.copyOfRange(prefix.size, prefix.size + start.size))
                assertArrayEquals(reset, bytes.takeLast(reset.size).toByteArray())
                assertEquals(Receipt.text(original, settings), Receipt.text(transaction, settings))
                val header = prefix.size + start.size - 4
                val stride = (bytes[header + 4].toInt() and 255) or ((bytes[header + 5].toInt() and 255) shl 8)
                val height = (bytes[header + 6].toInt() and 255) or ((bytes[header + 7].toInt() and 255) shl 8)
                assertTrue(stride * 8 <= 256)
                assertTrue(height <= 256)
                val matrix = Encoder.encode(payload, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix
                val scale = height / (matrix.height + 8)
                assertTrue(scale >= 3)
                assertEquals((matrix.height + 8) * scale, height)
                val pixels = IntArray(stride * 8 * height) { index ->
                    val x = index % (stride * 8)
                    val y = index / (stride * 8)
                    val black = bytes[header + 8 + y * stride + x / 8].toInt() and (0x80 ushr (x % 8)) != 0
                    val mx = x / scale - 4
                    val my = y / scale - 4
                    val expected = x < height && mx in 0 until matrix.width && my in 0 until matrix.height && matrix.get(mx, my).toInt() == 1
                    assertEquals(expected, black)
                    if (black) 0xff000000.toInt() else 0xffffffff.toInt()
                }
                val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(stride * 8, height, pixels))))
                assertEquals(payload, result.text)
                @Suppress("UNCHECKED_CAST")
                val segments = result.resultMetadata[ResultMetadataType.BYTE_SEGMENTS] as List<ByteArray>
                assertArrayEquals(payload.toByteArray(Charsets.UTF_8), segments.reduce { a, b -> a + b })
            }
        }
    }

    @Test fun bytesCannotInjectCommandsThroughAnyTextField() {
        val injection = "bad\u001b@\u001dv0\u0000\u000c\u0007"
        val transaction = sale(listOf(CartLine(
            Product(name = injection, price = 1, kind = ProductKind.OTHER, provider = injection, packageName = injection),
            destination = injection, code = injection, note = injection, buyerName = injection,
        ))).copy(shop = shop.copy(shopName = injection, footer = injection), payment = injection)
        val blocks = Receipt.blocks(transaction, ShopSettings())
        assertTrue(blocks.all { block -> block.text.none { it.isISOControl() } })
        val wire = Receipt.bytes(transaction, ShopSettings()).toString(Charsets.US_ASCII)
        assertEquals(1, Regex("\u001b@").findAll(wire).count())
        assertFalse(wire.contains("\u001dv0"))
        assertFalse(wire.contains('\u000c'))
        assertFalse(wire.contains('\u0007'))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedLogoRejectedBeforeAndroidDecode() {
        Receipt.bytes(sale().copy(shop = shop.copy(headerMode = "logo", logo = "A".repeat(32_769))), ShopSettings())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPngLogoRejectedBeforeAndroidDecode() {
        Receipt.bytes(sale().copy(shop = shop.copy(headerMode = "both", logo = "bm90IGEgcG5n")), ShopSettings())
    }
}
