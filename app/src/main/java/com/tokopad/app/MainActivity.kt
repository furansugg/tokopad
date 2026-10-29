package com.tokopad.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

fun l(language: String, en: String, id: String): String = if (language == "id") id else en
fun money(value: Long, language: String): String = "Rp " + NumberFormat.getIntegerInstance(
    if (language == "id") Locale.forLanguageTag("id-ID") else Locale.US,
).format(value)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF226957), secondary = Color(0xFF576B62),
                background = Color(0xFFF8FAF8), surface = Color(0xFFF8FAF8),
            )) { Tokopad() }
        }
    }
}

@Composable
private fun Tokopad() {
    val context = LocalContext.current
    val store = remember { Store(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var settings by remember { mutableStateOf(ShopSettings()) }
    var products by remember { mutableStateOf(emptyList<Product>()) }
    var customers by remember { mutableStateOf(emptyList<Customer>()) }
    var sales by remember { mutableStateOf(emptyList<Sale>()) }
    var dashboard by remember { mutableStateOf(Dashboard()) }
    var from by remember { mutableLongStateOf(java.time.LocalDate.now().toEpochDay()) }
    var to by remember { mutableLongStateOf(from) }
    var query by remember { mutableStateOf("") }
    var screen by rememberSaveable { mutableStateOf("dashboard") }
    var cart by rememberSaveable { mutableStateOf(emptyList<CartLine>()) }
    var checkoutId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var printers by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var discardTarget by remember { mutableStateOf<String?>(null) }
    var backupWarning by remember { mutableStateOf<String?>(null) }
    var safetyBackup by remember { mutableStateOf<String?>(null) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var customerHistory by remember { mutableStateOf<Pair<Customer, List<Sale>>?>(null) }
    val lang = settings.language

    suspend fun refresh() {
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = java.time.LocalDate.ofEpochDay(to + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val data = withContext(Dispatchers.IO) {
            AppData(store.settings(), store.products(true), store.customers(), store.sales(start, end, query), store.dashboard(start, end))
        }
        settings = data.settings
        products = data.products
        customers = data.customers
        sales = data.sales
        dashboard = data.dashboard
        loaded = true
    }

    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { task() } catch (_: TimeoutCancellationException) { error = l(lang, "Printer timed out. Check the paper before retrying.", "Printer tidak merespons. Periksa kertas sebelum mengulang.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = operationError(e, lang) }
            finally { busy = false }
        }
    }

    fun findPrinters() = runTask {
        printers = withContext(Dispatchers.IO) { Receipt.pairedPrinters(context) }
        if (printers.isEmpty()) snackbar.showSnackbar(l(lang, "Pair your printer in Android Bluetooth settings first.", "Pasangkan printer di pengaturan Bluetooth Android dulu."))
    }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) findPrinters() else error = l(lang, "Bluetooth permission denied. Enable it in app settings.", "Izin Bluetooth ditolak. Aktifkan di pengaturan aplikasi.")
    }
    fun requestPrinters() {
        if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) else findPrinters()
    }

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask {
            restoreText = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open backup" }
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(bytes.size() + count <= Store.MAX_BACKUP_BYTES) { "Backup exceeds 64 MB" }
                        bytes.write(buffer, 0, count)
                    }
                    bytes.toString("UTF-8")
                }
            }
        }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runTask {
            val safety = safetyBackup
            val text = safety ?: withContext(Dispatchers.IO) { store.exportBackup() }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output) { "Cannot write backup" }
                    output.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            safetyBackup = null
            if (safety != null) openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            else snackbar.showSnackbar(l(lang, "Backup saved.", "Cadangan tersimpan."))
        } else safetyBackup = null
    }

    LaunchedEffect(Unit) { runTask { refresh() } }

    fun navigate(target: String) {
        if (screen == "checkout" && cart.isNotEmpty() && target != screen) discardTarget = target
        else screen = target
    }

    BackHandler(enabled = screen == "checkout" && cart.isNotEmpty()) { discardTarget = "dashboard" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("dashboard" to l(lang, "Home", "Beranda"), "checkout" to l(lang, "Checkout", "Kasir"),
                        "history" to l(lang, "History", "Riwayat"), "products" to l(lang, "Products", "Produk"),
                        "customers" to l(lang, "Customers", "Pelanggan"), "settings" to l(lang, "Settings", "Pengaturan")).forEach { (key, label) ->
                        TextButton(onClick = { navigate(key) }, enabled = !busy) { Text(if (screen == key) "• $label" else label) }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tokopad", style = MaterialTheme.typography.headlineSmall)
                Text(l(lang, "Your shop, in order.", "Toko rapi, jualan nyaman."), style = MaterialTheme.typography.bodySmall)
            }
            if (!loaded) {
                Button(onClick = { runTask { refresh() } }, enabled = !busy, modifier = Modifier.padding(16.dp)) { Text(l(lang, "Load shop", "Muat toko")) }
            } else Box(Modifier.weight(1f)) {
                when (screen) {
                    "dashboard" -> DashboardScreen(dashboard, sales.take(5), lang, from, to, onRange = { a, b -> from = a; to = b; runTask { refresh() } }, onCheckout = { navigate("checkout") }, onSale = { selectedSale = it })
                    "checkout" -> CheckoutScreen(products.filter { it.active }, customers, cart, lang, busy,
                        onCart = { cart = it }, onComplete = { customer, discount, payment, paid -> runTask {
                            val sale = withContext(Dispatchers.IO) { store.checkout(cart, customer, discount, payment, paid, checkoutId) }
                            cart = emptyList()
                            checkoutId = java.util.UUID.randomUUID().toString()
                            screen = "history"
                            selectedSale = sale
                            refresh()
                        } })
                    "history" -> HistoryScreen(sales, lang, from, to, query,
                        onSearch = { query = it; runTask { refresh() } }, onRange = { a, b -> from = a; to = b; runTask { refresh() } }, onSale = { selectedSale = it })
                    "products" -> ProductsScreen(products, lang) { product, saved -> runTask { withContext(Dispatchers.IO) { store.saveProduct(product) }; saved(); refresh() } }
                    "customers" -> CustomersScreen(customers, lang,
                        onSave = { customer, saved -> runTask { withContext(Dispatchers.IO) { store.saveCustomer(customer) }; saved(); refresh() } },
                        onDelete = { customer -> runTask { withContext(Dispatchers.IO) { store.deleteCustomer(customer.id) }; refresh() } },
                        onHistory = { customer -> runTask { customerHistory = customer to withContext(Dispatchers.IO) { store.salesForCustomer(customer.id) } } })
                    "settings" -> SettingsScreen(settings, printers,
                        onSave = { value -> runTask { withContext(Dispatchers.IO) { store.saveSettings(value) }; refresh(); snackbar.showSnackbar(l(value.language, "Settings saved.", "Pengaturan tersimpan.")) } },
                        onFindPrinters = { requestPrinters() },
                        onTestPrint = { runTask { Receipt.print(context, settings.printerAddress, Receipt.testText(settings)); snackbar.showSnackbar(l(lang, "Sent. Check the printer before retrying.", "Terkirim. Periksa printer sebelum mengulang.")) } },
                        onExport = { backupWarning = "export" }, onImport = { backupWarning = "import" })
                }
            }
        }
    }

    selectedSale?.let { sale -> SaleDialog(sale, settings, busy, onDismiss = { selectedSale = null },
        onPrint = { runTask { Receipt.print(context, settings.printerAddress, Receipt.text(sale, settings)); snackbar.showSnackbar(l(lang, "Sent. Check the printer before retrying.", "Terkirim. Periksa printer sebelum mengulang.")) } },
        onCancel = { reason -> runTask {
            selectedSale = withContext(Dispatchers.IO) { store.cancelSale(sale.id, reason); store.sale(sale.id) }
            refresh()
        } }) }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text(l(lang, "Couldn't finish", "Belum berhasil")) },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        dismissButton = { if (screen == "settings") TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); error = null }) { Text("Bluetooth") } }) }
    discardTarget?.let { target -> AlertDialog(onDismissRequest = { discardTarget = null }, title = { Text(l(lang, "Discard this cart?", "Buang keranjang ini?")) },
        text = { Text(l(lang, "Unsaved items will be lost.", "Item yang belum disimpan akan hilang.")) },
        confirmButton = { TextButton(onClick = { cart = emptyList(); screen = target; discardTarget = null }) { Text(l(lang, "Discard", "Buang")) } },
        dismissButton = { TextButton(onClick = { discardTarget = null }) { Text(l(lang, "Keep", "Kembali")) } }) }
    backupWarning?.let { action -> AlertDialog(onDismissRequest = { backupWarning = null },
        title = { Text(l(lang, "Sensitive backup", "Cadangan berisi data sensitif")) },
        text = { Text(l(lang, "Backup files are not encrypted. Keep them private. Restore replaces all shop data; save a safety backup first, then select the file to restore.", "File cadangan tidak dienkripsi. Simpan di tempat aman. Pemulihan mengganti semua data toko; simpan cadangan pengaman dulu, lalu pilih file yang akan dipulihkan.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            backupWarning = null
            if (action == "import") runTask { safetyBackup = withContext(Dispatchers.IO) { store.exportBackup() }; createBackup.launch("tokopad-before-restore-${System.currentTimeMillis()}.json") }
            else createBackup.launch("tokopad-${System.currentTimeMillis()}.json")
        }) { Text(l(lang, "Save backup", "Simpan cadangan")) } }, dismissButton = { TextButton(onClick = { backupWarning = null }) { Text(l(lang, "Cancel", "Batal")) } }) }
    restoreText?.let { text -> AlertDialog(onDismissRequest = { restoreText = null }, title = { Text(l(lang, "Replace all data?", "Ganti semua data?")) },
        text = { Text(l(lang, "Current products, customers, transactions and settings will be replaced. Invalid backups are rejected before changing any data.", "Produk, pelanggan, transaksi, dan pengaturan saat ini akan diganti. Cadangan tidak valid ditolak sebelum data diubah.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { restoreText = null; runTask { withContext(Dispatchers.IO) { store.restoreBackup(text) }; cart = emptyList(); selectedSale = null; query = ""; refresh(); snackbar.showSnackbar(l(settings.language, "Backup restored.", "Cadangan dipulihkan.")) } }) { Text(l(lang, "Replace data", "Ganti data")) } },
        dismissButton = { TextButton(onClick = { restoreText = null }) { Text(l(lang, "Cancel", "Batal")) } }) }
    customerHistory?.let { (customer, history) -> AlertDialog(onDismissRequest = { customerHistory = null }, title = { Text(customer.name) },
        text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Text(l(lang, "Latest 500 transactions", "500 transaksi terbaru"))
            if (history.isEmpty()) Text(l(lang, "No transactions yet.", "Belum ada transaksi."))
            history.forEach { sale -> TextButton(onClick = { customerHistory = null; selectedSale = sale }) { Text("${sale.number}\n${money(sale.total, lang)}" + if (sale.cancelledAt != null) " · " + l(lang, "Cancelled", "Dibatalkan") else "") } }
        } }, confirmButton = { TextButton(onClick = { customerHistory = null }) { Text(l(lang, "Close", "Tutup")) } }) }
    if (busy) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(l(lang, "Working…", "Sedang diproses…"))
        } }
    }
}

private data class AppData(val settings: ShopSettings, val products: List<Product>, val customers: List<Customer>, val sales: List<Sale>, val dashboard: Dashboard)

internal fun operationError(error: Exception, language: String): String {
    val message = error.message.orEmpty()
    if (language != "id") return message.ifBlank { "Operation failed. Check your inputs and try again." }
    return when {
        error is SecurityException -> "Izin ditolak. Periksa izin aplikasi di pengaturan Android."
        message.contains("Insufficient stock") -> "Stok tidak cukup. Kurangi jumlah atau perbarui stok produk."
        message.contains("Price changed") -> "Harga produk berubah. Hapus item terkait, lalu tambahkan kembali."
        message.contains("inactive", true) -> "Produk sudah nonaktif. Hapus dari keranjang."
        message.contains("no longer exists") -> "Produk atau pelanggan sudah tidak tersedia. Muat ulang data."
        message.contains("already cancelled") -> "Transaksi ini sudah dibatalkan. Stok tidak dikembalikan lagi."
        message.contains("Bluetooth permission") -> "Izin Bluetooth diperlukan. Cari printer di Pengaturan untuk memberi izin."
        message.contains("Bluetooth is turned off") -> "Bluetooth mati. Aktifkan di pengaturan Android."
        message.contains("Bluetooth is unavailable") -> "Perangkat ini tidak menyediakan Bluetooth."
        message.contains("Bluetooth address") -> "Pilih printer Bluetooth lalu simpan pengaturan terlebih dahulu."
        message.contains("backup", true) || message.contains("JSON", true) -> "Cadangan tidak valid, rusak, terlalu besar, atau versinya tidak didukung. Data lama tidak diubah."
        message.contains("Cash must cover") -> "Pembayaran tunai harus mencukupi total; nontunai harus sama dengan total."
        message.contains("Discount") -> "Diskon harus berupa rupiah bulat dan tidak melebihi subtotal."
        message.contains("code", true) -> "Produk ini memerlukan token atau kode. Lengkapi sebelum menyimpan."
        message.contains("phone number") -> "Nomor tujuan atau ID pelanggan tidak valid. Periksa kembali."
        message.contains("limit", true) || message.contains("range", true) || message.contains("amount", true) -> "Nilai atau jumlah data melewati batas aplikasi. Kurangi nilai dan coba lagi."
        error is java.io.IOException -> "Koneksi printer atau akses file gagal. Periksa perangkat, izin, dan ruang penyimpanan sebelum mengulang."
        else -> "Proses belum berhasil. Periksa kelengkapan dan panjang isian, lalu coba lagi."
    }
}
