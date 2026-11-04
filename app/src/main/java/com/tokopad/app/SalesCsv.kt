package com.tokopad.app

import java.io.StringWriter
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SalesCsv {
    /** UTF-8 CSV with a BOM for spreadsheet applications. All sales, including cancellations, are exported. */
    fun bytes(sales: List<Sale>): ByteArray = text(sales).toByteArray(Charsets.UTF_8)

    /** Amounts are unformatted currency units; paid includes settlements and excludes change. */
    fun text(sales: List<Sale>): String {
        val writer = StringWriter()
        writeHeader(writer)
        for (sale in sales) writeSale(writer, sale)
        return writer.toString()
    }

    /** Call once per export before writing any sales. Use a UTF-8 writer for spreadsheet compatibility. */
    fun writeHeader(writer: Writer) {
        writer.write("\uFEFFReceipt,Date/time,Customer,Status,Total,Paid,Outstanding\r\n")
    }

    /** Writes one sale without retaining it, flushing, or closing the writer. */
    fun writeSale(writer: Writer, sale: Sale) {
        val status = when {
            sale.cancelledAt != null -> "CANCELLED"
            sale.balance > 0 -> "OWING"
            else -> "PAID"
        }
        val date = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(sale.createdAt).atZone(ZoneId.of(sale.zoneId)),
        )
        writer.write(listOf(
            cell(sale.receiptNumber), cell(date), cell(sale.customerName), cell(status),
            sale.total.toString(), sale.received.toString(), sale.balance.toString(),
        ).joinToString(","))
        writer.write("\r\n")
    }

    private fun cell(value: String): String {
        // Spreadsheet programs may ignore leading whitespace before interpreting a formula.
        val prefix = value.takeWhile { it.isWhitespace() || it.isISOControl() }
        val dangerous = prefix.any { it.isISOControl() } ||
            value.drop(prefix.length).firstOrNull() in listOf('=', '+', '-', '@')
        val safe = if (dangerous) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
