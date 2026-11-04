package com.tokopad.app

import java.io.StringWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCsvTest {
    private fun sale() = Sale(
        id = 42, number = "private-id", createdAt = 0, zoneId = "Asia/Jakarta",
        lines = listOf(CartLine(Product(name = "Electricity", price = 100), code = "secret-token")),
        customerId = 1, customerName = "Café", customerPhone = "private-phone",
        subtotal = 100, discount = 0, total = 100, payment = "cash", paid = 150,
        shop = ShopSettings(),
    )

    @Test fun bytesIncludeBomAndExportReceiptAndLocalTimeWithoutSecrets() {
        val bytes = SalesCsv.bytes(listOf(sale()))
        assertArrayEquals(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), bytes.copyOfRange(0, 3))
        val csv = bytes.toString(Charsets.UTF_8)
        assertEquals("\uFEFFReceipt,Date/time,Customer,Status,Total,Paid,Outstanding\r\n" +
            "\"TP-00000042\",\"1970-01-01T07:00:00+07:00\",\"Café\",\"PAID\",100,100,0\r\n", csv)
        for (secret in listOf("private-id", "private-phone", "secret-token")) assertFalse(csv.contains(secret))
    }

    @Test fun exportsCreditSettlementAndCancellationWithPaymentHistory() {
        val credit = sale().copy(isCredit = true, paid = 30)
        val settled = credit.copy(settlement = Settlement(70, "cash", 1, "private-request"))
        val cancelled = settled.copy(cancelledAt = 2)
        val csv = SalesCsv.text(listOf(credit, settled, cancelled))
        assertTrue(csv.contains("\"OWING\",100,30,70\r\n"))
        assertTrue(csv.contains("\"PAID\",100,100,0\r\n"))
        assertTrue(csv.contains("\"CANCELLED\",100,100,0\r\n"))
        assertFalse(csv.contains("private-request"))
    }

    @Test fun quotesMultilineAndNeutralizesSpreadsheetFormulas() {
        val names = listOf("=1+1", "+cmd", "-1+2", "@cmd", "  =1+1", "\tordinary", " \tordinary", "\r\n=1+1", "A, \"B\"\r\nC")
        val csv = SalesCsv.text(names.map { sale().copy(customerName = it) })
        for (name in names.dropLast(1)) {
            assertTrue(name, csv.contains("\"'${name.replace("\"", "\"\"")}\""))
        }
        assertTrue(csv.contains("\"A, \"\"B\"\"\r\nC\""))
    }

    @Test fun emptyExportContainsHeaderAndBom() {
        assertEquals("\uFEFFReceipt,Date/time,Customer,Status,Total,Paid,Outstanding\r\n", SalesCsv.text(emptyList()))
    }

    @Test fun streamingWritesHeaderOnceAndMatchesListExport() {
        val sales = listOf(sale(), sale().copy(customerName = "=1+1", isCredit = true, paid = 30),
            sale().copy(cancelledAt = 1))
        val writer = StringWriter()
        SalesCsv.writeHeader(writer)
        sales.forEach { SalesCsv.writeSale(writer, it) }
        assertEquals(SalesCsv.text(sales), writer.toString())
        assertEquals(1, writer.toString().count { it == '\uFEFF' })
    }

    @Test fun streamingEmptyExportAndIndividualSaleDoNotCloseWriter() {
        var closed = false
        var flushed = false
        val writer = object : StringWriter() {
            override fun close() { closed = true }
            override fun flush() { flushed = true }
        }
        SalesCsv.writeHeader(writer)
        assertEquals(SalesCsv.text(emptyList()), writer.toString())
        SalesCsv.writeSale(writer, sale())
        writer.write("still open")
        assertEquals(SalesCsv.text(listOf(sale())) + "still open", writer.toString())
        assertFalse(closed)
        assertFalse(flushed)
    }
}
