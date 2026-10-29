package com.tokopad.app

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private val SalesPrimary = Color(0xFF18181B)
private val SalesInk = Color(0xFF09090B)
private val SalesBackground = Color(0xFFFAFAFA)
private val SalesMuted = Color(0xFFF4F4F5)
private val SalesSecondary = Color(0xFF71717A)
private val SalesBorder = Color(0xFFE4E4E7)

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
    OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SalesBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SalesInk), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        PadIcon("calendar", Modifier.size(16.dp), SalesSecondary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(locale)) }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            val state = rememberDateRangePickerState(initialSelectedStartDateMillis = from * 86_400_000L, initialSelectedEndDateMillis = to * 86_400_000L)
            val colors = DatePickerDefaults.colors(containerColor = Color.White, selectedDayContainerColor = SalesPrimary, selectedDayContentColor = Color.White, todayContentColor = SalesInk, todayDateBorderColor = SalesPrimary, dayInSelectionRangeContainerColor = SalesMuted, dayInSelectionRangeContentColor = SalesInk)
            DatePickerDialog(onDismissRequest = { open = false }, shape = RoundedCornerShape(10.dp), colors = colors, confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null, onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null && start <= end) {
                        onRange(start / 86_400_000L, end / 86_400_000L)
                        open = false
                    }
                }) { Text(l(language, "Apply dates", "Terapkan tanggal")) }
            }, dismissButton = { TextButton(onClick = { open = false }) { Text(l(language, "Cancel", "Batal")) } }) {
                DateRangePicker(state = state, colors = colors, modifier = Modifier.heightIn(max = 480.dp), title = { Text(l(language, "Choose dates", "Pilih tanggal"), Modifier.padding(start = 24.dp, top = 16.dp)) }, showModeToggle = false)
            }
        }
    }
}

@Composable
fun DashboardScreen(data: Dashboard, sales: List<Sale>, language: String, from: Long, to: Long, onRange: (Long, Long) -> Unit, onCheckout: () -> Unit, onSale: (Sale) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(SalesBackground), contentPadding = PaddingValues(16.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l(language, "Overview", "Ringkasan"), Modifier.weight(1f), color = SalesInk, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            DateRange(language, from, to, onRange)
        } }
        item { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(l(language, "Total sales", "Total penjualan"), color = SalesSecondary, style = MaterialTheme.typography.labelLarge)
                    PadIcon("receipt", Modifier.size(18.dp), SalesSecondary)
                }
                Text(money(data.revenue, language), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        } } }
        item { Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(l(language, "Transactions", "Transaksi") to "${data.count}", l(language, "Average sale", "Rata-rata penjualan") to money(data.average, language)).forEach { (label, value) ->
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = SalesSecondary)
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = SalesInk)
                    }
                }
            }
        } }
        item { Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesPrimary, contentColor = Color.White)) {
            PadIcon("plus", tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(l(language, "New sale", "Mulai jualan"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            PadIcon("arrow", tint = Color.White)
        } }
        item { Column(Modifier.padding(top = 28.dp, bottom = 12.dp)) { SectionHeading(l(language, "Recent transactions", "Transaksi terbaru")) } }
        if (sales.isEmpty()) item { EmptyState("receipt", l(language, "No sales yet", "Belum ada penjualan"), l(language, "Completed sales appear here.", "Penjualan selesai akan tampil di sini.")) }
        items(sales, key = { it.id }) { SaleRow(it, language, onSale) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun HistoryScreen(sales: List<Sale>, language: String, from: Long, to: Long, query: String, onSearch: (String) -> Unit, onRange: (Long, Long) -> Unit, onSale: (Sale) -> Unit) {
    var search by remember(query) { mutableStateOf(query) }
    LazyColumn(Modifier.fillMaxSize().background(SalesBackground), contentPadding = PaddingValues(16.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(l(language, "History", "Riwayat"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = SalesInk)
            DateRange(language, from, to, onRange)
        } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), leadingIcon = { PadIcon("search") }, label = { Text(l(language, "Transaction number", "Nomor transaksi")) }, trailingIcon = { TextButton(onClick = { onSearch(search) }) { Text(l(language, "Search", "Cari")) } }, singleLine = true) }
        item { Text(l(language, "Showing up to 500 matches. Narrow the dates to find older sales.", "Maksimal 500 hasil. Persempit tanggal untuk mencari transaksi lama."), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = SalesSecondary) }
        if (sales.isEmpty()) item { EmptyState("history", l(language, "No matching transactions", "Tidak ada transaksi yang cocok"), l(language, "Try another date range or receipt number.", "Coba rentang tanggal atau nomor nota lain.")) }
        items(sales, key = { it.id }) { SaleRow(it, language, onSale) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SaleRow(sale: Sale, language: String, onSale: (Sale) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).clickable { onSale(sale) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("#${sale.number.takeLast(8)}", style = MaterialTheme.typography.labelLarge, color = SalesInk)
                Text(DateTimeFormatter.ofPattern(if (language == "id") "d MMM · HH:mm" else "MMM d · HH:mm", Locale.forLanguageTag(language)).withZone(ZoneId.of(sale.zoneId)).format(Instant.ofEpochMilli(sale.createdAt)), style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                Text(sale.customerName.ifBlank { l(language, "Walk-in customer", "Pelanggan umum") }, style = MaterialTheme.typography.bodySmall, color = SalesSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(sale.total, language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SalesInk)
                Surface(shape = RoundedCornerShape(4.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
                    Text(if (sale.cancelledAt != null) l(language, "Cancelled", "Dibatalkan") else l(language, "Paid", "Lunas"), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = SalesSecondary)
                }
            }
        }
        HorizontalDivider(color = SalesBorder)
    }
}

@Composable
fun SaleDialog(sale: Sale, settings: ShopSettings, busy: Boolean, onDismiss: () -> Unit, onPrint: () -> Unit, onCancel: (String) -> Unit) {
    var cancelling by remember(sale.id) { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val language = settings.language
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Color.White, shape = RoundedCornerShape(10.dp), title = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(l(language, "Receipt", "Nota"), color = SalesInk)
        Text(sale.number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(Receipt.text(sale, settings), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = SalesInk, softWrap = false)
                }
            }
            if (sale.cancelledAt == null) {
                if (!cancelling) TextButton(onClick = { cancelling = true }, enabled = !busy) { Text(l(language, "Cancel transaction", "Batalkan transaksi")) }
                else {
                    Text(l(language, "Stock will be returned. Refund payment separately.", "Stok dikembalikan. Pengembalian pembayaran dilakukan terpisah."))
                    OutlinedTextField(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Cancellation reason", "Alasan pembatalan")) })
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
    var cartTab by rememberSaveable { mutableStateOf(false) }
    var showCustomer by rememberSaveable { mutableStateOf(false) }
    val productScroll = rememberLazyListState()
    val cartScroll = rememberLazyListState()
    val subtotal = runCatching { cart.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.product.price, line.quantity)) } }.getOrNull()
    val discountValue = if (discount.isBlank()) 0L else discount.toLongOrNull()
    val total = if (subtotal != null && discountValue != null && discountValue in 0..subtotal) subtotal - discountValue else null
    val paidValue = if (payment == "cash") paid.toLongOrNull() else total
    val canComplete = cart.isNotEmpty() && total != null && paidValue != null && paidValue >= total && !busy
    val matchingProducts = products.filter { it.name.contains(search, true) || it.category.contains(search, true) }
    Column(Modifier.fillMaxSize().background(SalesBackground)) {
        Text(l(language, "Checkout", "Kasir"), Modifier.padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = SalesInk)
        Surface(Modifier.padding(horizontal = 16.dp), color = SalesMuted, shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                listOf(false, true).forEach { isCart ->
                    val selected = cartTab == isCart
                    Tab(selected = selected, onClick = { cartTab = isCart }, modifier = Modifier.weight(1f).testTag(if (isCart) "checkout-cart" else "checkout-products").background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(6.dp)), selectedContentColor = SalesInk, unselectedContentColor = SalesSecondary, text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isCart) l(language, "Cart", "Keranjang") else l(language, "Products", "Produk"), style = MaterialTheme.typography.labelLarge)
                            if (isCart) Text("(${cart.size})", style = MaterialTheme.typography.labelMedium)
                        }
                    })
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = if (cartTab) cartScroll else productScroll, contentPadding = PaddingValues(16.dp)) {
            if (!cartTab) {
                item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(8.dp), leadingIcon = { PadIcon("search", tint = SalesSecondary) }, label = { Text(l(language, "Find a product", "Cari produk")) }, singleLine = true) }
                if (products.isEmpty()) item { EmptyState("products", l(language, "Your shelves are waiting", "Rak tokomu masih kosong"), l(language, "Add products in Products first.", "Tambahkan produk di menu Produk dulu.")) }
                else if (matchingProducts.isEmpty()) item { EmptyState("search", l(language, "No products found", "Produk tidak ditemukan"), l(language, "Try a different name or category.", "Coba nama atau kategori lain.")) }
                items(matchingProducts, key = { "product-${it.id}" }) { product ->
                    Column(Modifier.fillMaxWidth().background(Color.White).clickable(enabled = !busy && cart.size < 200) { adding = product }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(product.name, style = MaterialTheme.typography.titleSmall, color = SalesInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(when (product.kind) {
                                    ProductKind.PHYSICAL -> l(language, "Physical", "Barang")
                                    ProductKind.PULSA -> l(language, "Airtime", "Pulsa")
                                    ProductKind.ELECTRICITY -> l(language, "Electricity", "Listrik")
                                    ProductKind.DATA -> l(language, "Data", "Data")
                                    ProductKind.VOUCHER -> l(language, "Voucher", "Voucher")
                                    ProductKind.OTHER -> l(language, "Other", "Lainnya")
                                } + if (product.trackStock) " · " + l(language, "Stock: ", "Stok: ") + product.stock else "", style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                            }
                            Text(money(product.price, language), style = MaterialTheme.typography.labelLarge, color = SalesInk)
                            PadIcon("plus", Modifier.size(18.dp), if (!busy && cart.size < 200) SalesInk else SalesSecondary)
                        }
                        HorizontalDivider(color = SalesBorder)
                    }
                }
            } else {
                if (cart.size >= 200) item { Text(l(language, "Cart limit: 200 lines. Complete this sale before adding more.", "Batas keranjang: 200 baris. Selesaikan penjualan sebelum menambah item."), Modifier.padding(bottom = 12.dp), color = SalesSecondary) }
                if (cart.isEmpty()) item { EmptyState("checkout", l(language, "Cart is empty", "Keranjang kosong"), l(language, "Choose a product to get started.", "Pilih produk untuk mulai.")) }
                items(cart.indices.toList(), key = { "cart-$it" }) { index ->
                    val line = cart[index]
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(line.product.name, style = MaterialTheme.typography.titleSmall, color = SalesInk)
                                Text("${line.quantity} × ${money(line.product.price, language)}", style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                            }
                            Text(runCatching { money(Math.multiplyExact(line.quantity, line.product.price), language) }.getOrDefault("—"), style = MaterialTheme.typography.labelLarge, color = SalesInk)
                        }
                        if (line.destination.isNotBlank()) Text(line.destination, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.code.isNotBlank()) Text(line.code, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.note.isNotBlank()) Text(line.note, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, SalesBorder)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(enabled = !busy && line.quantity > 1, modifier = Modifier.width(48.dp).semantics { contentDescription = l(language, "Decrease quantity for ", "Kurangi jumlah ") + line.product.name }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = SalesInk), onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity - 1) else value }) }) { Text("−", fontSize = 20.sp) }
                                    Text("${line.quantity}", Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelLarge, color = SalesInk)
                                    TextButton(enabled = !busy && line.quantity < 1_000_000, modifier = Modifier.width(48.dp).semantics { contentDescription = l(language, "Increase quantity for ", "Tambah jumlah ") + line.product.name }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = SalesInk), onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity + 1) else value }) }) { Text("+", fontSize = 20.sp) }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(enabled = !busy, onClick = { onCart(cart.filterIndexed { i, _ -> i != index }) }, colors = ButtonDefaults.textButtonColors(contentColor = SalesSecondary)) { Text(l(language, "Remove", "Hapus")) }
                        }
                        HorizontalDivider(color = SalesBorder)
                    }
                }
                item { OutlinedButton(onClick = { showCustomer = !showCustomer }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("checkout-customer"), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SalesBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SalesInk)) {
                    PadIcon("customers", Modifier.size(18.dp), SalesSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(customer?.name ?: l(language, "Add customer (optional)", "Tambah pelanggan (opsional)"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (showCustomer) "−" else "+")
                } }
                if (showCustomer) {
                    item { OutlinedTextField(customerSearch, { customerSearch = it }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(8.dp), leadingIcon = { PadIcon("search", tint = SalesSecondary) }, label = { Text(l(language, "Find customer (optional)", "Cari pelanggan (opsional)")) }, singleLine = true) }
                    if (customerSearch.isNotBlank()) items(customers.filter { it.name.contains(customerSearch, true) || it.phone.contains(customerSearch) }.take(5), key = { "customer-${it.id}" }) { value ->
                        TextButton(onClick = { customer = value; customerSearch = ""; showCustomer = false }, colors = ButtonDefaults.textButtonColors(contentColor = SalesInk)) { Text("${value.name} · ${value.phone}") }
                    }
                    item { TextButton(onClick = { customer = null; showCustomer = false }, colors = ButtonDefaults.textButtonColors(contentColor = SalesSecondary)) { Text(l(language, "Walk-in customer", "Pelanggan umum")) } }
                }
                item {
                    Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(10.dp), color = Color.White, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(l(language, "Payment", "Pembayaran"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(l(language, "Subtotal", "Subtotal"), color = SalesSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text(subtotal?.let { money(it, language) } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            OutlinedTextField(discount, { discount = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Discount (Rp)", "Diskon (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = total == null)
                            HorizontalDivider(color = SalesBorder)
                            Text(l(language, "Total: ", "Total: ") + (total?.let { money(it, language) } ?: "—"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Surface(color = SalesMuted, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                                    listOf("cash", "noncash").forEach { method ->
                                        Tab(selected = payment == method, onClick = { payment = method }, modifier = Modifier.weight(1f).background(if (payment == method) Color.White else Color.Transparent, RoundedCornerShape(6.dp)), selectedContentColor = SalesInk, unselectedContentColor = SalesSecondary, text = { Text(if (method == "cash") l(language, "Cash", "Tunai") else l(language, "Noncash", "Nontunai"), style = MaterialTheme.typography.labelLarge) })
                                    }
                                }
                            }
                            if (payment == "cash") OutlinedTextField(paid, { paid = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Cash received (Rp)", "Uang diterima (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            if (total != null && paidValue != null && paidValue >= total) Text(l(language, "Change: ", "Kembalian: ") + money(paidValue - total, language), color = SalesSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { Button(onClick = { confirm = true }, enabled = canComplete, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesPrimary, contentColor = Color.White)) {
                    Text(l(language, "Review sale", "Periksa penjualan"), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    PadIcon("arrow", tint = if (canComplete) Color.White else SalesSecondary)
                } }
            }
        }
        if (!cartTab && cart.isNotEmpty()) Surface(color = Color.White, shadowElevation = 0.dp) {
            Column {
                HorizontalDivider(color = SalesBorder)
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${cart.size} " + l(language, "items", "item"), style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        Text(total?.let { money(it, language) } ?: "—", style = MaterialTheme.typography.titleMedium, color = SalesInk, fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { cartTab = true }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesPrimary, contentColor = Color.White)) { Text(l(language, "View cart", "Lihat keranjang")) }
                }
            }
        }
    }
    adding?.let { product -> AddLineDialog(product, language, onDismiss = { adding = null }, onAdd = { line ->
        val match = if (product.kind == ProductKind.PHYSICAL) cart.indexOfFirst { it.product.id == product.id } else -1
        if (match >= 0) onCart(cart.mapIndexed { i, value -> if (i == match) value.copy(quantity = value.quantity + line.quantity) else value }) else onCart(cart + line)
        adding = null
    }) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color.White, shape = RoundedCornerShape(10.dp), title = { Text(l(language, "Finish this sale?", "Selesaikan penjualan?")) },
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
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(10.dp), title = { Text(product.name, color = SalesInk) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(money(product.price, language), style = MaterialTheme.typography.titleLarge, color = SalesInk, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Quantity", "Jumlah")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (product.kind != ProductKind.PHYSICAL) {
                OutlinedTextField(destination, { destination = it.take(100) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(if (product.kind == ProductKind.ELECTRICITY) l(language, "Meter / customer ID", "Nomor meter / ID pelanggan") else l(language, "Destination", "Tujuan")) }, supportingText = { if (needsDestination) Text(l(language, "Required: 5–30 digits, +, spaces or hyphens.", "Wajib: 5–30 angka, +, spasi, atau tanda hubung.")) })
                if (product.kind in listOf(ProductKind.ELECTRICITY, ProductKind.VOUCHER, ProductKind.OTHER) || product.requiresCode) OutlinedTextField(code, { code = it.take(200) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Token / voucher code", "Token / kode voucher")) }, supportingText = { if (product.requiresCode) Text(l(language, "Required", "Wajib diisi")) })
                OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Note (optional)", "Catatan (opsional)")) })
            }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onAdd(CartLine(product, q ?: 1, destination.trim(), code.trim(), note.trim())) }) { Text(l(language, "Add", "Tambah")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(l(language, "Cancel", "Batal")) } })
}
