package com.tokopad.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.LocaleList
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SalesPrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
private val SalesInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val SalesBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val SalesSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
private val SalesOnPrimary: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
private val SalesMuted: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val SalesSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val SalesBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant

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
    OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SalesBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = SalesSurface, contentColor = SalesInk), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        PadIcon("calendar", Modifier.size(16.dp), SalesSecondary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(locale)) }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            val state = rememberDateRangePickerState(initialSelectedStartDateMillis = from * 86_400_000L, initialSelectedEndDateMillis = to * 86_400_000L)
            val colors = DatePickerDefaults.colors(containerColor = SalesSurface, selectedDayContainerColor = SalesPrimary, selectedDayContentColor = SalesOnPrimary, todayContentColor = SalesInk, todayDateBorderColor = SalesPrimary, dayInSelectionRangeContainerColor = SalesMuted, dayInSelectionRangeContentColor = SalesInk)
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
        item { Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(l(language, "Overview", "Ringkasan"), color = SalesInk, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            DateRange(language, from, to, onRange)
        } }
        item { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = SalesSurface, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(l(language, "Total sales", "Total penjualan"), color = SalesSecondary, style = MaterialTheme.typography.labelLarge)
                    PadIcon("receipt", Modifier.size(18.dp), SalesSecondary)
                }
                Text(money(data.revenue, language), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        } } }
        item { Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(l(language, "Transactions", "Transaksi") to "${data.count}", l(language, "Average sale", "Rata-rata penjualan") to money(data.average, language)).forEach { (label, value) ->
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SalesSurface, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = SalesSecondary)
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = SalesInk)
                    }
                }
            }
        } }
        item { Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesPrimary, contentColor = SalesOnPrimary)) {
            PadIcon("plus", tint = SalesOnPrimary)
            Spacer(Modifier.width(10.dp))
            Text(l(language, "New sale", "Mulai jualan"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            PadIcon("arrow", tint = SalesOnPrimary)
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
        item { Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(l(language, "History", "Riwayat"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = SalesInk)
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
    Column(Modifier.fillMaxWidth().background(SalesSurface).clickable { onSale(sale) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(sale.receiptNumber, style = MaterialTheme.typography.labelLarge, color = SalesInk)
                Text(DateTimeFormatter.ofPattern(if (language == "id") "d MMM · HH:mm" else "MMM d · HH:mm", Locale.forLanguageTag(language)).withZone(ZoneId.of(sale.zoneId)).format(Instant.ofEpochMilli(sale.createdAt)), style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                Text(sale.customerName.ifBlank { l(language, "Walk-in customer", "Pelanggan umum") }, style = MaterialTheme.typography.bodySmall, color = SalesSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(sale.total, language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SalesInk)
                Surface(shape = RoundedCornerShape(4.dp), color = SalesSurface, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
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
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.imePadding(), containerColor = SalesSurface, shape = RoundedCornerShape(10.dp), title = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(l(language, "Receipt", "Nota"), color = SalesInk)
        Text(sale.receiptNumber, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Physical receipt paper deliberately stays white in either theme.
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, contentColor = Color.Black, border = BorderStroke(1.dp, SalesBorder)) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                    val columns = if (settings.paperWidth == 80) 48 else 32
                    val textSize = minOf(12f, maxWidth.value / LocalDensity.current.fontScale / (columns * 0.62f))
                    Column(Modifier.fillMaxWidth()) {
                    Receipt.blocks(sale, settings).forEach { block ->
                        val bitmap = remember(block.logo) {
                            if (block.logo.isBlank()) null else runCatching {
                                val bytes = Base64.decode(block.logo.orEmpty().substringAfter("base64,"), Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bitmap != null) Image(bitmap, l(language, "Shop logo", "Logo toko"), Modifier.align(Alignment.CenterHorizontally).sizeIn(maxWidth = 160.dp, maxHeight = 100.dp))
                        if (block.text.isNotEmpty()) Text(block.text, Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace,
                            fontSize = (if (block.large) textSize * 2 else textSize).sp,
                            lineHeight = (if (block.large) textSize * 2.5f else textSize * 1.5f).sp,
                            fontWeight = if (block.emphasis || block.large) FontWeight.Bold else FontWeight.Normal,
                            textAlign = if (block.centered) TextAlign.Center else TextAlign.Start, color = Color.Black)
                    }
                    }
                }
            }
            if (sale.cancelledAt == null) {
                if (!cancelling) TextButton(onClick = { cancelling = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(l(language, "Cancel transaction", "Batalkan transaksi")) }
                else {
                    Text(l(language, "Stock will be returned. Refund payment separately.", "Stok dikembalikan. Pengembalian pembayaran dilakukan terpisah."))
                    OutlinedTextField(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Cancellation reason", "Alasan pembatalan")) })
                    TextButton(onClick = { onCancel(reason.trim()) }, enabled = !busy && reason.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text(l(language, "Confirm cancellation", "Konfirmasi pembatalan")) }
                }
            } else Text(sale.cancellationReason)
        } },
        confirmButton = { TextButton(onClick = onPrint, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(l(language, "Print", "Cetak")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(l(language, "Close", "Tutup")) } })
}

@Composable
fun CheckoutScreen(products: List<Product>, customers: List<Customer>, cart: List<CartLine>, language: String, busy: Boolean, onCart: (List<CartLine>) -> Unit, onComplete: (Customer?, Long, String, Long) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf<Product?>(null) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
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
    Column(Modifier.fillMaxSize().background(SalesBackground).imePadding()) {
        Text(l(language, "Checkout", "Kasir"), Modifier.padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = SalesInk)
        Surface(Modifier.padding(horizontal = 16.dp), color = SalesMuted, contentColor = SalesInk, shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                listOf(false, true).forEach { isCart ->
                    val selected = cartTab == isCart
                    Tab(selected = selected, onClick = { cartTab = isCart }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(if (isCart) "checkout-cart" else "checkout-products").background(if (selected) SalesSurface else Color.Transparent, RoundedCornerShape(6.dp)), selectedContentColor = SalesInk, unselectedContentColor = SalesSecondary, text = {
                        Text(if (isCart) l(language, "Cart", "Keranjang") + " (${cart.size})" else l(language, "Products", "Produk"), style = MaterialTheme.typography.labelLarge)
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
                    Column(Modifier.fillMaxWidth().background(SalesSurface).clickable(enabled = !busy && cart.size < 200) { editing = null; adding = product }) {
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
                                Text(money(product.price, language), style = MaterialTheme.typography.labelLarge, color = SalesInk)
                            }
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
                    Column(Modifier.fillMaxWidth().background(SalesSurface).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(line.product.name, style = MaterialTheme.typography.titleSmall, color = SalesInk)
                            Text("${line.quantity} × ${money(line.product.price, language)}", style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                            Text(runCatching { money(Math.multiplyExact(line.quantity, line.product.price), language) }.getOrDefault("—"), style = MaterialTheme.typography.labelLarge, color = SalesInk)
                        }
                        if (line.buyerName.isNotBlank()) Text(line.buyerName, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.destination.isNotBlank()) Text(line.destination, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.kwh.isNotBlank()) Text("${line.kwh} kWh", style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.code.isNotBlank()) Text(line.code, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.note.isNotBlank()) Text(line.note, style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        if (line.product.kind == ProductKind.PHYSICAL) {
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = SalesSurface, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(enabled = !busy && line.quantity > 1, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = l(language, "Decrease quantity for ", "Kurangi jumlah ") + line.product.name }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = SalesInk), onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity - 1) else value }) }) { Text("−", fontSize = 20.sp) }
                                    Text("${line.quantity}", Modifier.weight(1f).padding(horizontal = 6.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = SalesInk)
                                    TextButton(enabled = !busy && line.quantity < 1_000_000, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = l(language, "Increase quantity for ", "Tambah jumlah ") + line.product.name }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = SalesInk), onClick = { onCart(cart.mapIndexed { i, value -> if (i == index) value.copy(quantity = value.quantity + 1) else value }) }) { Text("+", fontSize = 20.sp) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (line.product.kind != ProductKind.PHYSICAL) TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("edit-digital-$index").semantics { contentDescription = l(language, "Edit ", "Ubah ") + line.product.name }, onClick = { editing = index; adding = line.product }, colors = ButtonDefaults.textButtonColors(contentColor = SalesInk)) { Text(l(language, "Edit", "Ubah")) }
                            TextButton(enabled = !busy, modifier = Modifier.heightIn(min = 48.dp), onClick = { onCart(cart.filterIndexed { i, _ -> i != index }) }, colors = ButtonDefaults.textButtonColors(contentColor = SalesSecondary)) { Text(l(language, "Remove", "Hapus")) }
                        }
                        HorizontalDivider(color = SalesBorder)
                    }
                }
                item { OutlinedButton(onClick = { showCustomer = !showCustomer }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp).testTag("checkout-customer"), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SalesBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = SalesSurface, contentColor = SalesInk)) {
                    PadIcon("customers", Modifier.size(18.dp), SalesSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(customer?.name ?: l(language, "Add customer (optional)", "Tambah pelanggan (opsional)"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (showCustomer) "−" else "+")
                } }
                if (showCustomer) {
                    item { OutlinedTextField(customerSearch, { customerSearch = it }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(8.dp), leadingIcon = { PadIcon("search", tint = SalesSecondary) }, label = { Text(l(language, "Find customer (optional)", "Cari pelanggan (opsional)")) }, singleLine = true) }
                    if (customerSearch.isNotBlank()) items(customers.filter { it.name.contains(customerSearch, true) || it.phone.contains(customerSearch) }.take(5), key = { "customer-${it.id}" }) { value ->
                        TextButton(onClick = { customer = value; customerSearch = ""; showCustomer = false }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = SalesInk)) { Text("${value.name} · ${value.phone}") }
                    }
                    item { TextButton(onClick = { customer = null; showCustomer = false }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = SalesSecondary)) { Text(l(language, "Walk-in customer", "Pelanggan umum")) } }
                }
                item {
                    Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(10.dp), color = SalesSurface, contentColor = SalesInk, border = BorderStroke(1.dp, SalesBorder)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(l(language, "Payment", "Pembayaran"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(l(language, "Subtotal", "Subtotal"), color = SalesSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text(subtotal?.let { money(it, language) } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            OutlinedTextField(discount, { discount = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Discount (Rp)", "Diskon (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = total == null)
                            HorizontalDivider(color = SalesBorder)
                            Text(l(language, "Total: ", "Total: ") + (total?.let { money(it, language) } ?: "—"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Surface(color = SalesMuted, contentColor = SalesInk, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                                    listOf("cash", "noncash").forEach { method ->
                                        Tab(selected = payment == method, onClick = { payment = method }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).background(if (payment == method) SalesSurface else Color.Transparent, RoundedCornerShape(6.dp)), selectedContentColor = SalesInk, unselectedContentColor = SalesSecondary, text = { Text(if (method == "cash") l(language, "Cash", "Tunai") else l(language, "Noncash", "Nontunai"), style = MaterialTheme.typography.labelLarge) })
                                    }
                                }
                            }
                            if (payment == "cash") OutlinedTextField(paid, { paid = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Cash received (Rp)", "Uang diterima (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            if (total != null && paidValue != null && paidValue >= total) Text(l(language, "Change: ", "Kembalian: ") + money(paidValue - total, language), color = SalesSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { Button(onClick = { confirm = true }, enabled = canComplete, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesPrimary, contentColor = SalesOnPrimary)) {
                    Text(l(language, "Review sale", "Periksa penjualan"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    PadIcon("arrow", tint = if (canComplete) SalesOnPrimary else SalesSecondary)
                } }
            }
        }
        if (!cartTab && cart.isNotEmpty()) Surface(color = SalesSurface, contentColor = SalesInk, shadowElevation = 0.dp) {
            Column {
                HorizontalDivider(color = SalesBorder)
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${cart.size} " + l(language, "items", "item"), style = MaterialTheme.typography.bodySmall, color = SalesSecondary)
                        Text(total?.let { money(it, language) } ?: "—", style = MaterialTheme.typography.titleMedium, color = SalesInk, fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { cartTab = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SalesPrimary, contentColor = SalesOnPrimary)) { Text(l(language, "View cart", "Lihat keranjang")) }
                }
            }
        }
    }
    adding?.let { product -> AddLineDialog(product, customers, language, initialLine = editing?.let { cart.getOrNull(it) },
        maxQuantity = if (editing == null && product.kind == ProductKind.PHYSICAL) 1_000_000L - (cart.firstOrNull { it.product.id == product.id }?.quantity ?: 0L) else 1_000_000L,
        onDismiss = { adding = null; editing = null }, onAdd = { line ->
        val match = if (product.kind == ProductKind.PHYSICAL) cart.indexOfFirst { it.product.id == product.id } else -1
        if (editing != null) onCart(cart.mapIndexed { i, value -> if (i == editing) line else value })
        else if (match >= 0) onCart(cart.mapIndexed { i, value -> if (i == match) value.copy(quantity = value.quantity + line.quantity) else value }) else onCart(cart + line)
        adding = null
        editing = null
    }) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = SalesSurface, shape = RoundedCornerShape(10.dp), title = { Text(l(language, "Finish this sale?", "Selesaikan penjualan?")) },
        text = { Text("${cart.size} " + l(language, "items", "item") + "\n" + money(total ?: 0, language) + "\n" + l(language, "The sale is saved before printing.", "Penjualan disimpan sebelum mencetak.")) },
        confirmButton = { TextButton(enabled = canComplete, onClick = { confirm = false; onComplete(customer, discountValue ?: 0, payment, paidValue ?: 0) }) { Text(l(language, "Complete sale", "Selesaikan")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(l(language, "Back", "Kembali")) } })
}

@Composable
private fun AddLineDialog(product: Product, customers: List<Customer>, language: String, initialLine: CartLine? = null, maxQuantity: Long = 1_000_000, onDismiss: () -> Unit, onAdd: (CartLine) -> Unit) {
    val context = LocalContext.current
    var quantity by rememberSaveable(product.id, initialLine) { mutableStateOf((initialLine?.quantity ?: 1).toString()) }
    var destination by rememberSaveable(product.id, initialLine) { mutableStateOf(initialLine?.destination.orEmpty()) }
    var code by rememberSaveable(product.id, initialLine) { mutableStateOf(initialLine?.code.orEmpty()) }
    var note by rememberSaveable(product.id, initialLine) { mutableStateOf(initialLine?.note.orEmpty()) }
    var buyerName by rememberSaveable(product.id, initialLine) { mutableStateOf(initialLine?.buyerName.orEmpty()) }
    var kwh by rememberSaveable(product.id, initialLine) { mutableStateOf(initialLine?.kwh.orEmpty()) }
    var customerId by rememberSaveable(product.id, initialLine) { mutableStateOf(initialLine?.customerId) }
    var buyerSearch by rememberSaveable(product.id, initialLine) { mutableStateOf("") }
    var showBuyers by rememberSaveable(product.id, initialLine) { mutableStateOf(true) }
    val electricity = product.kind == ProductKind.ELECTRICITY
    val digital = product.kind != ProductKind.PHYSICAL
    val needsDestination = product.kind in listOf(ProductKind.PULSA, ProductKind.DATA, ProductKind.ELECTRICITY)
    val q = quantity.toLongOrNull()
    val normalizedKwh = kwh.trim().replace(',', '.')
    val kwhValue = normalizedKwh.toBigDecimalOrNull()
    val validKwh = normalizedKwh.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,3})?")) && kwhValue != null && kwhValue.signum() == 1 && kwhValue <= java.math.BigDecimal("1000000")
    val validDestination = destination.trim().let { value -> value.length in 3..64 && value.any { it in '0'..'9' } && value.all { it in '0'..'9' || it in "+() .-" } }
    val valid = q != null && q in 1..maxQuantity && (!needsDestination || validDestination) && (!product.requiresCode || code.isNotBlank()) && (!electricity || (buyerName.isNotBlank() && validKwh))
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.imePadding(), containerColor = SalesSurface, shape = RoundedCornerShape(10.dp), title = { Text(product.name, color = SalesInk) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(money(product.price, language), style = MaterialTheme.typography.titleLarge, color = SalesInk, fontWeight = FontWeight.SemiBold)
            if (!digital) OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Quantity", "Jumlah")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = q == null || q !in 1..maxQuantity)
            if (digital) {
                OutlinedTextField(buyerSearch, { buyerSearch = it.take(100); showBuyers = true }, Modifier.fillMaxWidth().testTag("digital-buyer-search"), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Find saved buyer", "Cari pembeli tersimpan")) }, leadingIcon = { PadIcon("search", tint = SalesSecondary) }, singleLine = true)
                if (showBuyers) {
                    val matches = remember(customers, buyerSearch) {
                        val query = buyerSearch.trim()
                        customers.asSequence().filter { it.name.contains(query, true) || it.phone.contains(query, true) || it.meterNumber.contains(query, true) }.take(4).toList()
                    }
                    if (matches.isNotEmpty()) Surface(Modifier.fillMaxWidth().testTag("digital-buyer-results"), shape = RoundedCornerShape(8.dp), color = SalesSurface, border = BorderStroke(1.dp, SalesBorder)) {
                        Column {
                            matches.take(3).forEachIndexed { index, buyer ->
                                if (index > 0) HorizontalDivider(color = SalesBorder)
                                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                    customerId = buyer.id
                                    buyerName = buyer.name
                                    destination = if (electricity) buyer.meterNumber else buyer.phone
                                    showBuyers = false
                                }.heightIn(min = 48.dp).testTag("digital-buyer-${buyer.id}").padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(buyer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = SalesInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val details = if (electricity) buyer.meterNumber else buyer.phone
                                        if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = SalesSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    PadIcon("chevron", Modifier.size(16.dp), SalesSecondary)
                                }
                            }
                        }
                    }
                    if (matches.size > 3) Text(l(language, "Showing 3 buyers. Search to narrow results.", "Menampilkan 3 pembeli. Cari untuk mempersempit hasil."), color = SalesSecondary, style = MaterialTheme.typography.bodySmall)
                    if (matches.isEmpty()) Text(if (customers.isEmpty()) l(language, "No saved buyers. Enter details below.", "Belum ada pembeli. Isi data di bawah.") else l(language, "No matching buyers. Try another search or enter details below.", "Pembeli tidak ditemukan. Coba pencarian lain atau isi data di bawah."), color = SalesSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(onClick = { showBuyers = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("digital-change-buyer")) {
                        Text(l(language, "Choose another buyer", "Pilih pembeli lain"))
                    }
                }
                OutlinedTextField(buyerName, { buyerName = it.take(100); customerId = null }, Modifier.fillMaxWidth().testTag("digital-buyer-name"), shape = RoundedCornerShape(8.dp), label = { Text(if (electricity) l(language, "Customer name", "Nama pelanggan") else l(language, "Customer name (optional)", "Nama pelanggan (opsional)")) }, supportingText = { if (electricity) Text(l(language, "Required", "Wajib diisi")) }, singleLine = true)
                OutlinedTextField(destination, { destination = it.take(64); customerId = null }, Modifier.fillMaxWidth().testTag("digital-destination"), shape = RoundedCornerShape(8.dp), label = { Text(if (electricity) l(language, "Meter / customer ID", "Nomor meter / ID pelanggan") else l(language, "Destination", "Tujuan")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                    trailingIcon = {
                        if (electricity) TextButton(enabled = destination.isNotBlank(), modifier = Modifier.testTag("digital-copy-meter").semantics { contentDescription = l(language, "Copy meter number", "Salin nomor meter") }, onClick = {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                ClipData.newPlainText(l(language, "Meter number", "Nomor meter"), destination.trim()),
                            )
                            if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, l(language, "Meter number copied.", "Nomor meter disalin."), Toast.LENGTH_SHORT).show()
                        }) { Text(l(language, "Copy", "Salin")) }
                    },
                    supportingText = { if (needsDestination) Text(l(language, "Required: 3–64 characters; digits and phone punctuation only.", "Wajib: 3–64 karakter; hanya angka dan tanda baca telepon.")) })
                if (electricity) OutlinedTextField(kwh, { kwh = it.take(20) }, Modifier.fillMaxWidth().testTag("digital-kwh"), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Energy (kWh)", "Energi (kWh)")) }, supportingText = { Text(l(language, "Required: 0–1,000,000, greater than zero; up to 3 decimals.", "Wajib: 0–1.000.000, lebih dari nol; maksimal 3 desimal.")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = kwh.isNotBlank() && !validKwh)
                OutlinedTextField(code, { code = it.take(200) }, Modifier.fillMaxWidth().testTag("digital-code"), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Token / voucher code", "Token / kode voucher")) }, supportingText = { Text(if (product.requiresCode) l(language, "Required", "Wajib diisi") else l(language, "Optional", "Opsional")) })
                OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), label = { Text(l(language, "Note (optional)", "Catatan (opsional)")) })
            }
        }
    }, confirmButton = { TextButton(enabled = valid, modifier = Modifier.heightIn(min = 48.dp), onClick = { onAdd(CartLine(product, q ?: 1, destination.trim(), code.trim(), note.trim(), buyerName.trim(), if (electricity) normalizedKwh else "", customerId)) }) { Text(if (initialLine == null) l(language, "Add", "Tambah") else l(language, "Save", "Simpan")) } }, dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(l(language, "Cancel", "Batal")) } })
}
