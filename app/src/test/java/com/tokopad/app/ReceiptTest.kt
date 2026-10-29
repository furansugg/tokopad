package com.tokopad.app

import org.junit.Assert.assertEquals
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
            "Meter / customer ID: 000987", "Token: 0011-2233-4455-6677-8899",
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
            assertTrue(result.substringAfter("Token:").replace("\n", "").trimStart().startsWith(token))
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
        assertTrue(result.contains("Nota: TEST"))
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
}
