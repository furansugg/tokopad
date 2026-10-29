package com.tokopad.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTest {
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
            "Meter / customer ID: 000987", "Token:\n0011 2233 4455 6677 8899",
            "Phone: 008888", "Package: 10 GB", "Provider: Provider C", "Package: 1 hour",
            "Voucher code: 000CODE", "Destination: 000DEST", "Code: 000OTHER", "Note: Manual service",
        )) assertTrue(value, result.contains(value))
        assertFalse(result.lines().any { it.trimEnd().endsWith(":") && it != "Token:" })
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
            assertTrue(result.substringAfter("Token:").replace(Regex("\\s"), "").startsWith(token))
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

    @Test fun digitalBuyerMeterKwhAndTokenPrecedePricesAndTotals() {
        val transaction = sale(listOf(CartLine(
            Product(name = "PLN 20", price = 22_000, kind = ProductKind.ELECTRICITY, nominal = 20_000),
            destination = "000987", code = "12345678901234567890", buyerName = "Siti", kwh = "13.2",
        )))
        val blocks = Receipt.blocks(transaction, ShopSettings())
        val result = Receipt.text(transaction, ShopSettings())
        for (value in listOf("PLN 20", "Buyer: Siti", "Meter / customer ID: 000987", "kWh: 13.2")) {
            assertTrue(value, result.contains(value))
        }
        assertEquals(listOf("1234 5678", "9012 3456 7890"), blocks.filter { it.large }.map { it.text })
        val tokenEnd = result.indexOf("9012 3456 7890")
        for (label in listOf("Nominal:", "1 x Rp", "Subtotal", "TOTAL", "Paid", "Change")) {
            assertTrue(label, result.indexOf(label) > tokenEnd)
        }
        assertTrue(blocks.first { it.text.startsWith("TOTAL") }.emphasis)
        assertTrue(Receipt.text(transaction, ShopSettings(language = "id")).contains("Nama pembeli: Siti"))
    }

    @Test fun digitalTypesKeepBuyerAndSpecializedDetails() {
        for (kind in ProductKind.entries.filter { it != ProductKind.PHYSICAL }) {
            val result = Receipt.text(sale(listOf(CartLine(
                Product(name = kind.name, price = 1, kind = kind, provider = "Provider", packageName = "Package", nominal = 1),
                buyerName = "Buyer name", destination = "000DEST", code = "ABC123", note = "Service note",
            ))), ShopSettings())
            assertTrue(kind.name, result.contains("Buyer: Buyer name"))
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
        assertTrue(wire.contains("\u001ba\u0001\u001bE\u0001\u001d!\u00111234 5678\n"))
        assertTrue(wire.contains("\u001ba\u0001\u001bE\u0001\u001d!\u00119012 3456 7890\n\u001ba\u0000\u001bE\u0000\u001d!\u0000Nominal:"))
        assertTrue(wire.indexOf("9012 3456 7890") < wire.indexOf("TOTAL"))
        val reset = byteArrayOf(0x1b, 0x61, 0x00, 0x1b, 0x45, 0x00, 0x1d, 0x21, 0x00, 0x0a, 0x0a, 0x0a)
        assertArrayEquals(reset, bytes.takeLast(reset.size).toByteArray())
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
