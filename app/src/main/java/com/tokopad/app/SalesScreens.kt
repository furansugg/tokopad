package com.tokopad.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun DateRange(language: String, from: Long, to: Long, onRange: (Long, Long) -> Unit) {
    var start by remember(from) { mutableStateOf(LocalDate.ofEpochDay(from).toString()) }
    var end by remember(to) { mutableStateOf(LocalDate.ofEpochDay(to).toString()) }
    var invalid by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text(l(language, "From", "Dari")) }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, isError = invalid)
        OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text(l(language, "To", "Sampai")) }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, isError = invalid)
    }
    TextButton(onClick = {
        val a = runCatching { LocalDate.parse(start).toEpochDay() }.getOrNull()
        val b = runCatching { LocalDate.parse(end).toEpochDay() }.getOrNull()
        invalid = a == null || b == null || a > b
        if (!invalid && a != null && b != null) onRange(a, b)
    }) { Text(l(language, "Apply dates", "Terapkan tanggal")) }
    if (invalid) Text(l(language, "Use YYYY-MM-DD. End must not precede start.", "Gunakan YYYY-MM-DD. Tanggal akhir tidak boleh lebih awal."), color = MaterialTheme.colorScheme.error)
}

@Composable
fun DashboardScreen(data: Dashboard, sales: List<Sale>, language: String, from: Long, to: Long, onRange: (Long, Long) -> Unit, onCheckout: () -> Unit, onSale: (Sale) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(l(language, "A little overview", "Sekilas toko kamu"), style = MaterialTheme.typography.titleLarge) }
        item { DateRange(language, from, to, onRange) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(l(language, "Sales", "Omzet")); Text(money(data.revenue, language), style = MaterialTheme.typography.headlineMedium)
            Text("${data.count} " + l(language, "transactions", "transaksi"))
            Text(l(language, "Average ", "Rata-rata ") + money(data.average, language))
        } } }
        item { Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) { Text(l(language, "New sale", "Mulai jualan")) } }
        item { Text(l(language, "Recent transactions", "Transaksi terbaru"), style = MaterialTheme.typography.titleMedium) }
        if (sales.isEmpty()) item { Text(l(language, "No sales yet. Your next sale starts here.", "Belum ada penjualan. Mulai dari kasir, yuk.")) }
        items(sales, key = { it.id }) { SaleRow(it, language, onSale) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun HistoryScreen(sales: List<Sale>, language: String, from: Long, to: Long, query: String, onSearch: (String) -> Unit, onRange: (Long, Long) -> Unit, onSale: (Sale) -> Unit) {
    var search by remember(query) { mutableStateOf(query) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(l(language, "History", "Riwayat"), style = MaterialTheme.typography.titleLarge) }
        item { DateRange(language, from, to, onRange) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text(l(language, "Transaction number", "Nomor transaksi")) }, singleLine = true) }
        item { TextButton(onClick = { onSearch(search) }) { Text(l(language, "Search", "Cari")) } }
        item { Text(l(language, "Showing up to 500 matches. Narrow the dates to find older sales.", "Maksimal 500 hasil. Persempit tanggal untuk mencari transaksi lama."), style = MaterialTheme.typography.bodySmall) }
        if (sales.isEmpty()) item { Text(l(language, "No matching transactions.", "Tidak ada transaksi yang cocok.")) }
        items(sales, key = { it.id }) { SaleRow(it, language, onSale) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SaleRow(sale: Sale, language: String, onSale: (Sale) -> Unit) {
    OutlinedCard(onClick = { onSale(sale) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sale.number, style = MaterialTheme.typography.labelMedium)
            Text(money(sale.total, language), style = MaterialTheme.typography.titleMedium)
            Text(DateTimeFormatter.ofPattern(if (language == "id") "dd MMM yyyy HH:mm" else "MMM dd, yyyy HH:mm").withZone(ZoneId.of(sale.zoneId)).format(Instant.ofEpochMilli(sale.createdAt)))
            if (sale.customerName.isNotBlank()) Text(sale.customerName)
            if (sale.cancelledAt != null) Text(l(language, "Cancelled", "Dibatalkan"), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun SaleDialog(sale: Sale, settings: ShopSettings, busy: Boolean, onDismiss: () -> Unit, onPrint: () -> Unit, onCancel: (String) -> Unit) {
    var cancelling by remember(sale.id) { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val language = settings.language
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(l(language, "Receipt", "Nota")) },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Receipt.text(sale, settings), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            if (sale.cancelledAt == null) {
                if (!cancelling) TextButton(onClick = { cancelling = true }, enabled = !busy) { Text(l(language, "Cancel transaction", "Batalkan transaksi")) }
                else {
                    Text(l(language, "Stock will be returned. Refund payment separately.", "Stok dikembalikan. Pengembalian pembayaran dilakukan terpisah."))
                    OutlinedTextField(reason, { reason = it.take(500) }, label = { Text(l(language, "Cancellation reason", "Alasan pembatalan")) })
                    TextButton(onClick = { onCancel(reason.trim()) }, enabled = !busy && reason.isNotBlank()) { Text(l(language, "Confirm cancellation", "Konfirmasi pembatalan")) }
                }
            } else Text(sale.cancellationReason)
        } },
        confirmButton = { TextButton(onClick = onPrint, enabled = !busy) { Text(l(language, "Print", "Cetak")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(l(language, "Close", "Tutup")) } })
}

@Composable
fun CheckoutScreen(products: List<Product>, customers: List<Customer>, cart: List<CartLine>, language: String, busy: Boolean, onCart: (List<CartLine>) -> Unit, onComplete: (Customer?, Long, String, Long) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf<Product?>(null) }
    var customer by rememberSaveable { mutableStateOf<Customer?>(null) }
    var customerSearch by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("") }
    var payment by rememberSaveable { mutableStateOf("cash") }
    var paid by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val subtotal = runCatching { cart.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.product.price, line.quantity)) } }.getOrNull()
    val discountValue = if (discount.isBlank()) 0L else discount.toLongOrNull()
    val total = if (subtotal != null && discountValue != null && discountValue in 0..subtotal) subtotal - discountValue else null
    val paidValue = if (payment == "cash") paid.toLongOrNull() else total
    val canComplete = cart.isNotEmpty() && total != null && paidValue != null && paidValue >= total && !busy
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(l(language, "Checkout", "Kasir"), style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text(l(language, "Find a product", "Cari produk")) }, singleLine = true) }
        if (products.isEmpty()) item { Text(l(language, "Add products in Products first.", "Tambahkan produk di menu Produk dulu.")) }
        items(products.filter { it.name.contains(search, true) || it.category.contains(search, true) }.take(20), key = { "product-${it.id}" }) { product ->
            OutlinedCard(onClick = { adding = product }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(product.name); if (product.trackStock) Text(l(language, "Stock: ", "Stok: ") + product.stock, style = MaterialTheme.typography.bodySmall) }
                    Text(money(product.price, language))
                }
            }
        }
        item { HorizontalDivider(); Text(l(language, "Your cart", "Keranjang"), style = MaterialTheme.typography.titleMedium) }
        if (cart.isEmpty()) item { Text(l(language, "Choose a product to get started.", "Pilih produk untuk mulai.")) }
        items(cart.indices.toList(), key = { "cart-$it" }) { index ->
            val line = cart[index]
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(line.product.name, style = MaterialTheme.typography.titleSmall)
                Text("${line.quantity} × ${money(line.product.price, language)}")
                if (line.destination.isNotBlank()) Text(line.destination)
                if (line.code.isNotBlank()) Text(line.code)
                Row {
                    TextButton(enabled = !busy && line.quantity > 1, onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity - 1) else value }) }) { Text("−") }
                    TextButton(enabled = !busy && line.quantity < 1_000_000, onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity + 1) else value }) }) { Text("+") }
                    TextButton(enabled = !busy, onClick = { onCart(cart.filterIndexed { i, _ -> i != index }) }) { Text(l(language, "Remove", "Hapus")) }
                }
            } }
        }
        item { OutlinedTextField(customerSearch, { customerSearch = it }, Modifier.fillMaxWidth(), label = { Text(l(language, "Find customer (optional)", "Cari pelanggan (opsional)")) }, singleLine = true) }
        if (customerSearch.isNotBlank()) items(customers.filter { it.name.contains(customerSearch, true) || it.phone.contains(customerSearch) }.take(5), key = { "customer-${it.id}" }) { value ->
            TextButton(onClick = { customer = value; customerSearch = "" }) { Text("${value.name} · ${value.phone}") }
        }
        item {
            TextButton(onClick = { customer = null }) { Text(customer?.let { "${it.name} ×" } ?: l(language, "Walk-in customer", "Pelanggan umum")) }
            OutlinedTextField(discount, { discount = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), label = { Text(l(language, "Discount (Rp)", "Diskon (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = total == null)
            Row { FilterChip(selected = payment == "cash", onClick = { payment = "cash" }, label = { Text(l(language, "Cash", "Tunai")) }); Spacer(Modifier.width(8.dp)); FilterChip(selected = payment == "noncash", onClick = { payment = "noncash" }, label = { Text(l(language, "Noncash", "Nontunai")) }) }
            Text(l(language, "Total: ", "Total: ") + (total?.let { money(it, language) } ?: "—"), style = MaterialTheme.typography.titleLarge)
            if (payment == "cash") OutlinedTextField(paid, { paid = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), label = { Text(l(language, "Cash received (Rp)", "Uang diterima (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (total != null && paidValue != null && paidValue >= total) Text(l(language, "Change: ", "Kembalian: ") + money(paidValue - total, language))
            Button(onClick = { confirm = true }, enabled = canComplete, modifier = Modifier.fillMaxWidth()) { Text(l(language, "Review sale", "Periksa penjualan")) }
            Spacer(Modifier.height(20.dp))
        }
    }
    adding?.let { product -> AddLineDialog(product, language, onDismiss = { adding = null }, onAdd = { line ->
        val match = if (product.kind == ProductKind.PHYSICAL) cart.indexOfFirst { it.product.id == product.id } else -1
        if (match >= 0) onCart(cart.mapIndexed { i, value -> if (i == match) value.copy(quantity = value.quantity + line.quantity) else value }) else onCart(cart + line)
        adding = null
    }) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(l(language, "Finish this sale?", "Selesaikan penjualan?")) },
        text = { Text("${cart.size} " + l(language, "items", "item") + "\n" + money(total ?: 0, language) + "\n" + l(language, "The sale is saved before printing.", "Penjualan disimpan sebelum mencetak.")) },
        confirmButton = { TextButton(enabled = canComplete, onClick = { confirm = false; onComplete(customer, discountValue ?: 0, payment, paidValue ?: 0) }) { Text(l(language, "Complete sale", "Selesaikan")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(l(language, "Back", "Kembali")) } })
}

@Composable
private fun AddLineDialog(product: Product, language: String, onDismiss: () -> Unit, onAdd: (CartLine) -> Unit) {
    var quantity by remember { mutableStateOf("1") }
    var destination by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val needsDestination = product.kind in listOf(ProductKind.PULSA, ProductKind.DATA, ProductKind.ELECTRICITY)
    val q = quantity.toLongOrNull()
    val valid = q != null && q in 1..1_000_000 && (!needsDestination || destination.trim().matches(Regex("[+0-9 -]{5,30}"))) && (!product.requiresCode || code.isNotBlank())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(product.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(7) }, label = { Text(l(language, "Quantity", "Jumlah")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (product.kind != ProductKind.PHYSICAL) {
                OutlinedTextField(destination, { destination = it.take(100) }, label = { Text(if (product.kind == ProductKind.ELECTRICITY) l(language, "Meter / customer ID", "Nomor meter / ID pelanggan") else l(language, "Destination", "Tujuan")) }, supportingText = { if (needsDestination) Text(l(language, "Required: 5–30 digits, +, spaces or hyphens.", "Wajib: 5–30 angka, +, spasi, atau tanda hubung.")) })
                if (product.kind in listOf(ProductKind.ELECTRICITY, ProductKind.VOUCHER, ProductKind.OTHER) || product.requiresCode) OutlinedTextField(code, { code = it.take(200) }, label = { Text(l(language, "Token / voucher code", "Token / kode voucher")) }, supportingText = { if (product.requiresCode) Text(l(language, "Required", "Wajib diisi")) })
                OutlinedTextField(note, { note = it.take(500) }, label = { Text(l(language, "Note (optional)", "Catatan (opsional)")) })
            }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onAdd(CartLine(product, q ?: 1, destination.trim(), code.trim(), note.trim())) }) { Text(l(language, "Add", "Tambah")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(l(language, "Cancel", "Batal")) } })
}
