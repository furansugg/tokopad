package com.tokopad.app

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SalesForest = Color(0xFF165C46)
private val SalesLime = Color(0xFFD4EE94)
private val SalesInk = Color(0xFF192B25)
private val SalesCream = Color(0xFFF7F8F2)
private val SalesBorder = Color(0xFFE4E8E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRange(language: String, from: Long, to: Long, onRange: (Long, Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val locale = Locale.forLanguageTag(language)
    val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val today = LocalDate.now().toEpochDay()
    val label = if (from == today && to == today) l(language, "Today", "Hari ini")
    else if (from == to) LocalDate.ofEpochDay(from).format(formatter)
    else "${LocalDate.ofEpochDay(from).format(formatter)} – ${LocalDate.ofEpochDay(to).format(formatter)}"
    OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, SalesBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SalesInk), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        PadIcon("calendar", Modifier.size(18.dp), SalesForest)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(10.dp))
        PadIcon("chevron", Modifier.size(16.dp), SalesForest)
    }
    if (open) {
        val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(locale)) }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            val state = rememberDateRangePickerState(initialSelectedStartDateMillis = from * 86_400_000L, initialSelectedEndDateMillis = to * 86_400_000L)
            DatePickerDialog(onDismissRequest = { open = false }, colors = DatePickerDefaults.colors(containerColor = SalesCream), confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null, onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null && start <= end) {
                        onRange(start / 86_400_000L, end / 86_400_000L)
                        open = false
                    }
                }) { Text(l(language, "Apply dates", "Terapkan tanggal")) }
            }, dismissButton = { TextButton(onClick = { open = false }) { Text(l(language, "Cancel", "Batal")) } }) {
                DateRangePicker(state = state, modifier = Modifier.heightIn(max = 480.dp), title = { Text(l(language, "Choose dates", "Pilih tanggal"), Modifier.padding(start = 24.dp, top = 16.dp)) }, showModeToggle = false)
            }
        }
    }
}

@Composable
fun DashboardScreen(data: Dashboard, sales: List<Sale>, language: String, from: Long, to: Long, onRange: (Long, Long) -> Unit, onCheckout: () -> Unit, onSale: (Sale) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(SalesCream), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(l(language, "OVERVIEW", "RINGKASAN"), color = SalesForest, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Text(l(language, "A little overview", "Sekilas toko kamu"), color = SalesInk, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        } }
        item { DateRange(language, from, to, onRange) }
        item { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = SalesForest, contentColor = Color.White) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(l(language, "Total sales", "Total penjualan"), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(12.dp), color = SalesLime, contentColor = SalesForest) { PadIcon("receipt", Modifier.padding(10.dp).size(20.dp), SalesForest) }
                }
                Text(money(data.revenue, language), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(l(language, "Transactions", "Transaksi"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .7f))
                        Text("${data.count}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(l(language, "Average sale", "Rata-rata penjualan"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .7f))
                        Text(money(data.average, language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    }
                }
        } } }
        item { Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesForest, contentColor = Color.White)) {
            PadIcon("plus", tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(l(language, "New sale", "Mulai jualan"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            PadIcon("arrow", tint = Color.White)
        } }
        item { SectionHeading(l(language, "Recent transactions", "Transaksi terbaru")) }
        if (sales.isEmpty()) item { Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
            EmptyState("receipt", l(language, "Your first sale starts here", "Penjualan pertamamu dimulai di sini"), l(language, "Make a new sale. We’ll keep everything in order.", "Mulai jualan. Semua transaksi akan tercatat rapi."), Modifier.fillMaxWidth().padding(12.dp))
        } }
        items(sales, key = { it.id }) { SaleRow(it, language, onSale) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun HistoryScreen(sales: List<Sale>, language: String, from: Long, to: Long, query: String, onSearch: (String) -> Unit, onRange: (Long, Long) -> Unit, onSale: (Sale) -> Unit) {
    var search by remember(query) { mutableStateOf(query) }
    LazyColumn(Modifier.fillMaxSize().background(SalesCream), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading(l(language, "History", "Riwayat"), l(language, "Every sale, neatly recorded.", "Setiap penjualan, tercatat rapi.")) }
        item { DateRange(language, from, to, onRange) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), leadingIcon = { PadIcon("search") }, label = { Text(l(language, "Transaction number", "Nomor transaksi")) }, trailingIcon = { TextButton(onClick = { onSearch(search) }) { Text(l(language, "Search", "Cari")) } }, singleLine = true) }
        item { Text(l(language, "Showing up to 500 matches. Narrow the dates to find older sales.", "Maksimal 500 hasil. Persempit tanggal untuk mencari transaksi lama."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (sales.isEmpty()) item { EmptyState("history", l(language, "No matching transactions", "Tidak ada transaksi yang cocok"), l(language, "Try another date range or receipt number.", "Coba rentang tanggal atau nomor nota lain.")) }
        items(sales, key = { it.id }) { SaleRow(it, language, onSale) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SaleRow(sale: Sale, language: String, onSale: (Sale) -> Unit) {
    OutlinedCard(onClick = { onSale(sale) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SalesBorder), colors = CardDefaults.outlinedCardColors(containerColor = Color.White, contentColor = SalesInk)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = SalesCream) { PadIcon("receipt", Modifier.padding(12.dp).size(20.dp), SalesForest) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("#${sale.number.takeLast(8)}", style = MaterialTheme.typography.titleSmall)
                Text(DateTimeFormatter.ofPattern(if (language == "id") "d MMM · HH:mm" else "MMM d · HH:mm", Locale.forLanguageTag(language)).withZone(ZoneId.of(sale.zoneId)).format(Instant.ofEpochMilli(sale.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sale.customerName.isNotBlank()) Text(sale.customerName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(sale.total, language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(shape = RoundedCornerShape(8.dp), color = if (sale.cancelledAt != null) MaterialTheme.colorScheme.errorContainer else SalesLime.copy(alpha = .55f)) {
                    Text(if (sale.cancelledAt != null) l(language, "Cancelled", "Dibatalkan") else l(language, "Paid", "Lunas"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (sale.cancelledAt != null) MaterialTheme.colorScheme.error else SalesForest)
                }
            }
        }
    }
}

@Composable
fun SaleDialog(sale: Sale, settings: ShopSettings, busy: Boolean, onDismiss: () -> Unit, onPrint: () -> Unit, onCancel: (String) -> Unit) {
    var cancelling by remember(sale.id) { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val language = settings.language
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = SalesCream, shape = RoundedCornerShape(24.dp), title = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(l(language, "Receipt", "Nota"), color = SalesInk)
        Text(sale.number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(Receipt.text(sale, settings), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = SalesInk, softWrap = false)
                }
            }
            if (sale.cancelledAt == null) {
                if (!cancelling) TextButton(onClick = { cancelling = true }, enabled = !busy) { Text(l(language, "Cancel transaction", "Batalkan transaksi")) }
                else {
                    Text(l(language, "Stock will be returned. Refund payment separately.", "Stok dikembalikan. Pengembalian pembayaran dilakukan terpisah."))
                    OutlinedTextField(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), label = { Text(l(language, "Cancellation reason", "Alasan pembatalan")) })
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
    var adding by rememberSaveable { mutableStateOf<Product?>(null) }
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
    val matchingProducts = products.filter { it.name.contains(search, true) || it.category.contains(search, true) }
    LazyColumn(Modifier.fillMaxSize().background(SalesCream), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading(l(language, "Checkout", "Kasir"), l(language, "Good things, ready to go.", "Siap melayani pelangganmu.")) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), leadingIcon = { PadIcon("search") }, label = { Text(l(language, "Find a product", "Cari produk")) }, singleLine = true) }
        if (products.isEmpty()) item { EmptyState("products", l(language, "Your shelves are waiting", "Rak tokomu masih kosong"), l(language, "Add products in Products first.", "Tambahkan produk di menu Produk dulu.")) }
        else if (matchingProducts.isEmpty()) item { EmptyState("search", l(language, "No products found", "Produk tidak ditemukan"), l(language, "Try a different name or category.", "Coba nama atau kategori lain.")) }
        items(if (search.isBlank()) matchingProducts.take(8) else matchingProducts, key = { "product-${it.id}" }) { product ->
            OutlinedCard(onClick = { adding = product }, enabled = !busy && cart.size < 200, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SalesBorder), colors = CardDefaults.outlinedCardColors(containerColor = Color.White, contentColor = SalesInk)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = if (product.kind == ProductKind.PHYSICAL) SalesCream else SalesLime.copy(alpha = .4f)) {
                        PadIcon("products", Modifier.padding(12.dp).size(22.dp), SalesForest)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when (product.kind) {
                                ProductKind.PHYSICAL -> l(language, "Physical", "Barang")
                                ProductKind.PULSA -> l(language, "Airtime", "Pulsa")
                                ProductKind.ELECTRICITY -> l(language, "Electricity", "Listrik")
                                ProductKind.DATA -> l(language, "Data", "Data")
                                ProductKind.VOUCHER -> l(language, "Voucher", "Voucher")
                                ProductKind.OTHER -> l(language, "Other", "Lainnya")
                            }, style = MaterialTheme.typography.labelSmall, color = SalesForest)
                            if (product.trackStock) Text("· " + l(language, "Stock: ", "Stok: ") + product.stock, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(money(product.price, language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Surface(shape = RoundedCornerShape(8.dp), color = SalesLime) { PadIcon("plus", Modifier.padding(5.dp).size(16.dp), SalesForest) }
                    }
                }
            }
        }
        if (search.isBlank() && products.size > 8) item { Text(l(language, "Search to find more products.", "Cari untuk melihat produk lainnya."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Spacer(Modifier.height(8.dp)); SectionHeading(l(language, "Your cart", "Keranjang"), "${cart.size} " + l(language, "items in this sale", "item dalam penjualan ini")) }
        if (cart.size >= 200) item { Text(l(language, "Cart limit: 200 lines. Complete this sale before adding more.", "Batas keranjang: 200 baris. Selesaikan penjualan sebelum menambah item.")) }
        if (cart.isEmpty()) item { Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
            EmptyState("checkout", l(language, "A fresh start", "Siap mulai jualan"), l(language, "Choose a product to get started.", "Pilih produk untuk mulai."), Modifier.fillMaxWidth())
        } }
        items(cart.indices.toList(), key = { "cart-$it" }) { index ->
            val line = cart[index]
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(line.product.name, style = MaterialTheme.typography.titleSmall)
                        Text("${line.quantity} × ${money(line.product.price, language)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(runCatching { money(Math.multiplyExact(line.quantity, line.product.price), language) }.getOrDefault("—"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (line.destination.isNotBlank()) Text(line.destination, style = MaterialTheme.typography.bodySmall)
                if (line.code.isNotBlank()) Text(line.code, style = MaterialTheme.typography.bodySmall)
                if (line.note.isNotBlank()) Text(line.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SalesCream, border = BorderStroke(1.dp, SalesBorder)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !busy && line.quantity > 1, modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp), onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity - 1) else value }) }) { Text("−", color = if (!busy && line.quantity > 1) SalesForest else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f), fontSize = 20.sp) }
                            Text("${line.quantity}", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelLarge)
                            TextButton(enabled = !busy && line.quantity < 1_000_000, modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp), onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity + 1) else value }) }) { Text("+", color = if (!busy && line.quantity < 1_000_000) SalesForest else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f), fontSize = 20.sp) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = { onCart(cart.filterIndexed { i, _ -> i != index }) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(l(language, "Remove", "Hapus")) }
                }
            } }
        }
        item { Spacer(Modifier.height(8.dp)); SectionHeading(l(language, "Customer", "Pelanggan"), l(language, "A personal touch. Always optional.", "Lebih personal. Tetap opsional.")) }
        item { OutlinedTextField(customerSearch, { customerSearch = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), leadingIcon = { PadIcon("customers") }, label = { Text(l(language, "Find customer (optional)", "Cari pelanggan (opsional)")) }, singleLine = true) }
        if (customerSearch.isNotBlank()) items(customers.filter { it.name.contains(customerSearch, true) || it.phone.contains(customerSearch) }.take(5), key = { "customer-${it.id}" }) { value ->
            TextButton(onClick = { customer = value; customerSearch = "" }) { Text("${value.name} · ${value.phone}") }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { customer = null }, colors = ButtonDefaults.textButtonColors(contentColor = SalesForest)) { Text(customer?.let { "${it.name} ×" } ?: l(language, "Walk-in customer", "Pelanggan umum")) }
                SectionHeading(l(language, "Payment", "Pembayaran"), l(language, "One last detail, then you’re done.", "Selangkah lagi, transaksi selesai."))
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(l(language, "Subtotal", "Subtotal"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(subtotal?.let { money(it, language) } ?: "—", fontWeight = FontWeight.Medium)
                        }
                        OutlinedTextField(discount, { discount = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text(l(language, "Discount (Rp)", "Diskon (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = total == null)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(selected = payment == "cash", onClick = { payment = "cash" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SalesLime, selectedLabelColor = SalesForest), label = { Text(l(language, "Cash", "Tunai")) }, leadingIcon = { if (payment == "cash") PadIcon("check", Modifier.size(18.dp), SalesForest) })
                            FilterChip(selected = payment == "noncash", onClick = { payment = "noncash" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SalesLime, selectedLabelColor = SalesForest), label = { Text(l(language, "Noncash", "Nontunai")) }, leadingIcon = { if (payment == "noncash") PadIcon("check", Modifier.size(18.dp), SalesForest) })
                        }
                        HorizontalDivider(color = SalesBorder)
                        Text(l(language, "Total: ", "Total: ") + (total?.let { money(it, language) } ?: "—"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = SalesForest)
                        if (payment == "cash") OutlinedTextField(paid, { paid = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text(l(language, "Cash received (Rp)", "Uang diterima (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (total != null && paidValue != null && paidValue >= total) Surface(shape = RoundedCornerShape(12.dp), color = SalesCream) {
                            Text(l(language, "Change: ", "Kembalian: ") + money(paidValue - total, language), Modifier.fillMaxWidth().padding(12.dp), color = SalesForest, style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = { confirm = true }, enabled = canComplete, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesForest, contentColor = Color.White)) {
                            Text(l(language, "Review sale", "Periksa penjualan"), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(12.dp))
                            PadIcon("arrow", tint = if (canComplete) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    adding?.let { product -> AddLineDialog(product, language, onDismiss = { adding = null }, onAdd = { line ->
        val match = if (product.kind == ProductKind.PHYSICAL) cart.indexOfFirst { it.product.id == product.id } else -1
        if (match >= 0) onCart(cart.mapIndexed { i, value -> if (i == match) value.copy(quantity = value.quantity + line.quantity) else value }) else onCart(cart + line)
        adding = null
    }) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = SalesCream, shape = RoundedCornerShape(24.dp), title = { Text(l(language, "Finish this sale?", "Selesaikan penjualan?")) },
        text = { Text("${cart.size} " + l(language, "items", "item") + "\n" + money(total ?: 0, language) + "\n" + l(language, "The sale is saved before printing.", "Penjualan disimpan sebelum mencetak.")) },
        confirmButton = { TextButton(enabled = canComplete, onClick = { confirm = false; onComplete(customer, discountValue ?: 0, payment, paidValue ?: 0) }) { Text(l(language, "Complete sale", "Selesaikan")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(l(language, "Back", "Kembali")) } })
}

@Composable
private fun AddLineDialog(product: Product, language: String, onDismiss: () -> Unit, onAdd: (CartLine) -> Unit) {
    var quantity by rememberSaveable(product.id) { mutableStateOf("1") }
    var destination by rememberSaveable(product.id) { mutableStateOf("") }
    var code by rememberSaveable(product.id) { mutableStateOf("") }
    var note by rememberSaveable(product.id) { mutableStateOf("") }
    val needsDestination = product.kind in listOf(ProductKind.PULSA, ProductKind.DATA, ProductKind.ELECTRICITY)
    val q = quantity.toLongOrNull()
    val valid = q != null && q in 1..1_000_000 && (!needsDestination || destination.trim().matches(Regex("[+0-9 -]{5,30}"))) && (!product.requiresCode || code.isNotBlank())
    AlertDialog(onDismissRequest = onDismiss, containerColor = SalesCream, shape = RoundedCornerShape(24.dp), title = { Text(product.name, color = SalesInk) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(money(product.price, language), style = MaterialTheme.typography.titleLarge, color = SalesForest, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text(l(language, "Quantity", "Jumlah")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (product.kind != ProductKind.PHYSICAL) {
                OutlinedTextField(destination, { destination = it.take(100) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), label = { Text(if (product.kind == ProductKind.ELECTRICITY) l(language, "Meter / customer ID", "Nomor meter / ID pelanggan") else l(language, "Destination", "Tujuan")) }, supportingText = { if (needsDestination) Text(l(language, "Required: 5–30 digits, +, spaces or hyphens.", "Wajib: 5–30 angka, +, spasi, atau tanda hubung.")) })
                if (product.kind in listOf(ProductKind.ELECTRICITY, ProductKind.VOUCHER, ProductKind.OTHER) || product.requiresCode) OutlinedTextField(code, { code = it.take(200) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), label = { Text(l(language, "Token / voucher code", "Token / kode voucher")) }, supportingText = { if (product.requiresCode) Text(l(language, "Required", "Wajib diisi")) })
                OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), label = { Text(l(language, "Note (optional)", "Catatan (opsional)")) })
            }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onAdd(CartLine(product, q ?: 1, destination.trim(), code.trim(), note.trim())) }) { Text(l(language, "Add", "Tambah")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(l(language, "Cancel", "Batal")) } })
}
