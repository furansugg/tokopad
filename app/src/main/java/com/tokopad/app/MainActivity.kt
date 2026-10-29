package com.tokopad.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
            Tokopad()
        }
    }
}

@Composable
private fun Tokopad() {
    val context = LocalContext.current
    val store = remember { Store(context.applicationContext) }
    DisposableEffect(store) { onDispose { store.close() } }
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
    var settingsSection by rememberSaveable { mutableStateOf("general") }
    var printerStatus by remember { mutableStateOf("") }
    var pendingPrinterAction by remember { mutableStateOf<String?>(null) }
    var pendingPrintSale by remember { mutableStateOf<Sale?>(null) }
    val lang = settings.language
    val dark = settings.theme == "dark" || (settings.theme == "system" && isSystemInDarkTheme())
    LaunchedEffect(dark) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        )
    }

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

    fun performPrinterAction(action: String, sale: Sale? = null) = runTask {
        printerStatus = ""
        when (action) {
            "find" -> {
                printers = withContext(Dispatchers.IO) { Receipt.pairedPrinters(context) }
                if (printers.isEmpty()) printerStatus = l(lang, "No paired printers. Pair one in Android settings.", "Belum ada printer dipasangkan. Pasangkan di pengaturan Android.")
            }
            "connect" -> {
                Receipt.testConnection(context, settings.printerAddress)
                printerStatus = "verified"
            }
            "print" -> {
                if (sale == null) Receipt.printTest(context, settings)
                else Receipt.print(context, settings.printerAddress, sale, settings)
                printerStatus = "sent"
                snackbar.showSnackbar(l(lang, "Data sent. Check the paper before printing again.", "Data terkirim. Periksa kertas sebelum mencetak ulang."))
            }
        }
    }

    fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission")
    fun bluetoothEnabled() = hasBluetoothPermission() && context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = pendingPrinterAction
        pendingPrinterAction = null
        if (bluetoothEnabled() && action != null) performPrinterAction(action, pendingPrintSale)
        else error = l(lang, "Turn on Bluetooth to connect your printer.", "Aktifkan Bluetooth untuk menghubungkan printer.")
    }
    fun requestBluetoothEnable() {
        try { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        catch (_: android.content.ActivityNotFoundException) {
            pendingPrinterAction = null
            error = l(lang, "Bluetooth settings are unavailable. Enable Bluetooth from the Android quick settings.", "Pengaturan Bluetooth tidak tersedia. Aktifkan dari pengaturan cepat Android.")
        }
    }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val action = pendingPrinterAction
        if (!hasBluetoothPermission()) {
            pendingPrinterAction = null
            error = l(lang, "Nearby devices permission denied. Allow it in Android app settings.", "Izin perangkat di sekitar ditolak. Izinkan di pengaturan aplikasi Android.")
        } else if (!bluetoothEnabled()) requestBluetoothEnable()
        else {
            pendingPrinterAction = null
            if (action != null) performPrinterAction(action, pendingPrintSale)
        }
    }
    fun requestPrinterAction(action: String, sale: Sale? = null) {
        if (busy) return
        if (context.getSystemService(BluetoothManager::class.java)?.adapter == null) {
            error = l(lang, "Bluetooth is unavailable on this device.", "Bluetooth tidak tersedia di perangkat ini.")
            return
        }
        if (action != "find" && settings.printerAddress.isBlank()) {
            selectedSale = null
            settingsSection = "printer"
            screen = "settings"
            error = l(lang, "Select a paired printer first. The selection is saved automatically.", "Pilih printer yang sudah dipasangkan dulu. Pilihan langsung tersimpan.")
            return
        }
        pendingPrinterAction = action
        pendingPrintSale = sale
        if (!hasBluetoothPermission()) bluetoothPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        else if (!bluetoothEnabled()) requestBluetoothEnable()
        else { pendingPrinterAction = null; performPrinterAction(action, sale) }
    }

    val bluetoothSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasBluetoothPermission() && bluetoothEnabled()) performPrinterAction("find")
    }
    fun openBluetoothSettings() {
        try { bluetoothSettings.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            error = l(lang, "Open Bluetooth from Android settings to pair your printer.", "Buka Bluetooth dari pengaturan Android untuk memasangkan printer.")
        }
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
    LaunchedEffect(screen, settingsSection) {
        if (screen == "settings" && settingsSection == "printer" && hasBluetoothPermission() && bluetoothEnabled()) performPrinterAction("find")
    }

    fun navigate(target: String) {
        if (screen == "checkout" && cart.isNotEmpty() && target != screen) discardTarget = target
        else screen = target
    }

    BackHandler(enabled = screen == "checkout" && cart.isNotEmpty()) { discardTarget = "dashboard" }
    BackHandler(enabled = screen in listOf("customers", "settings")) { screen = "more" }
    BackHandler(enabled = screen == "products") { screen = "dashboard" }
    BackHandler(enabled = screen == "more" || screen == "history" || (screen == "checkout" && cart.isEmpty())) { screen = "dashboard" }

    TokopadTheme(theme = settings.theme) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    listOf("dashboard" to l(lang, "Home", "Beranda"), "checkout" to l(lang, "Checkout", "Kasir"),
                        "products" to l(lang, "Products", "Produk"), "more" to l(lang, "More", "Lainnya")).forEach { (key, label) ->
                        val selected = screen == key || (key == "more" && screen in listOf("history", "customers", "settings"))
                        val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(Modifier.weight(1f).background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
                            .selectable(selected, enabled = !busy, role = Role.Tab, onClick = { navigate(key) }).testTag("nav-$key").padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PadIcon(if (key == "dashboard") "home" else key, Modifier.size(20.dp), tint)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    PadIcon("receipt", Modifier.size(17.dp), MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(settings.shopName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Tokopad", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { settingsSection = "printer"; navigate("settings") }, modifier = Modifier.testTag("open-printer").semantics { contentDescription = l(lang, "Printer settings", "Pengaturan printer") }) {
                    PadIcon("printer", Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (!loaded) {
                Button(onClick = { runTask { refresh() } }, enabled = !busy, modifier = Modifier.padding(16.dp)) { Text(l(lang, "Load shop", "Muat toko")) }
            } else Box(Modifier.weight(1f)) {
                when (screen) {
                    "more" -> ShopMenu(lang, settings.shopName, onNavigate = { if (it == "settings") settingsSection = "general"; navigate(it) })
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
                        onFindPrinters = { requestPrinterAction("find") },
                        onTestPrint = { requestPrinterAction("print") },
                        onExport = { backupWarning = "export" }, onImport = { backupWarning = "import" },
                        onSelectPrinter = { address -> runTask {
                            withContext(Dispatchers.IO) { store.saveSettings(settings.copy(printerAddress = address)) }
                            settings = settings.copy(printerAddress = address)
                            printerStatus = "selected"
                        } },
                        onConnectPrinter = { requestPrinterAction("connect") },
                        onOpenBluetooth = { openBluetoothSettings() },
                        printerStatus = when (printerStatus) {
                            "verified" -> l(lang, "Connection verified. Ready to print.", "Koneksi berhasil diuji. Siap mencetak.")
                            "sent" -> l(lang, "Data sent. Check the paper before printing again.", "Data terkirim. Periksa kertas sebelum mencetak ulang.")
                            "selected" -> l(lang, "Printer saved. Test the connection next.", "Printer tersimpan. Uji koneksi selanjutnya.")
                            else -> printerStatus
                        }, section = settingsSection, onSection = { settingsSection = it })
                }
            }
        }
    }

    selectedSale?.let { sale -> SaleDialog(sale, settings, busy, onDismiss = { selectedSale = null },
        onPrint = { requestPrinterAction("print", sale) },
        onCancel = { reason -> runTask {
            selectedSale = withContext(Dispatchers.IO) { store.cancelSale(sale.id, reason); store.sale(sale.id) }
            refresh()
        } }) }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text(l(lang, "Couldn't finish", "Belum berhasil")) },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        dismissButton = { if (screen == "settings") TextButton(onClick = { error = null; openBluetoothSettings() }) { Text("Bluetooth") } }) }
    discardTarget?.let { target -> AlertDialog(onDismissRequest = { discardTarget = null }, title = { Text(l(lang, "Discard this cart?", "Buang keranjang ini?")) },
        text = { Text(l(lang, "Unsaved items will be lost.", "Item yang belum disimpan akan hilang.")) },
        confirmButton = { TextButton(onClick = { cart = emptyList(); checkoutId = java.util.UUID.randomUUID().toString(); screen = target; discardTarget = null }) { Text(l(lang, "Discard", "Buang")) } },
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
        confirmButton = { TextButton(enabled = !busy, onClick = { restoreText = null; runTask { withContext(Dispatchers.IO) { store.restoreBackup(text) }; cart = emptyList(); checkoutId = java.util.UUID.randomUUID().toString(); selectedSale = null; query = ""; refresh(); snackbar.showSnackbar(l(settings.language, "Backup restored.", "Cadangan dipulihkan.")) } }) { Text(l(lang, "Replace data", "Ganti data")) } },
        dismissButton = { TextButton(onClick = { restoreText = null }) { Text(l(lang, "Cancel", "Batal")) } }) }
    customerHistory?.let { (customer, history) -> AlertDialog(onDismissRequest = { customerHistory = null }, title = { Text(customer.name) },
        text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Text(l(lang, "Latest 500 transactions", "500 transaksi terbaru"))
            if (history.isEmpty()) Text(l(lang, "No transactions yet.", "Belum ada transaksi."))
            history.forEach { sale -> TextButton(onClick = { customerHistory = null; selectedSale = sale }) { Text("${sale.receiptNumber}\n${money(sale.total, lang)}" + if (sale.cancelledAt != null) " · " + l(lang, "Cancelled", "Dibatalkan") else "") } }
        } }, confirmButton = { TextButton(onClick = { customerHistory = null }) { Text(l(lang, "Close", "Tutup")) } }) }
    if (busy) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(l(lang, "Working…", "Sedang diproses…"))
        } }
    }
    }
}

@Composable
private fun ShopMenu(language: String, shopName: String, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(l(language, "Workspace", "Ruang toko"), shopName)
        listOf(
            Triple("history", l(language, "History", "Riwayat"), l(language, "Transactions & receipts", "Transaksi & nota")),
            Triple("customers", l(language, "Customers", "Pelanggan"), l(language, "Profiles & purchase history", "Profil & riwayat belanja")),
            Triple("settings", l(language, "Settings", "Pengaturan"), l(language, "Receipts, printer & backups", "Nota, printer & cadangan")),
        ).forEach { (key, title, subtitle) ->
            OutlinedCard(onClick = { onNavigate(key) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PadIcon(key, Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PadIcon("chevron", Modifier.size(18.dp))
                }
            }
        }
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
        message.contains("Checkout request already") -> "Permintaan kasir ini sudah dipakai. Keluar dari kasir, buang keranjang, lalu mulai penjualan baru."
        message.contains("Local data limit") -> "Batas data lokal tercapai. Ekspor cadangan sebelum memulai dataset toko yang baru. Data lama tetap aman."
        message.contains("Bluetooth permission") -> "Izin Bluetooth diperlukan. Cari printer di Pengaturan untuk memberi izin."
        message.contains("Bluetooth is turned off") -> "Bluetooth mati. Aktifkan di pengaturan Android."
        message.contains("Bluetooth is unavailable") -> "Perangkat ini tidak menyediakan Bluetooth."
        message.contains("Bluetooth address") -> "Pilih printer Bluetooth yang sudah dipasangkan. Pilihan langsung tersimpan."
        message.contains("LE-only", true) || message.contains("BLE printers are not supported", true) -> "Printer ini hanya mendukung BLE. Tokopad saat ini memerlukan Bluetooth Classic ESC/POS. Periksa mode printer atau gunakan printer Classic."
        message.contains("not paired", true) || message.contains("not bonded", true) -> "Printer belum dipasangkan. Buka pengaturan Bluetooth Android, pasangkan printer, lalu cari kembali."
        message.contains("Printer write", true) -> "Pengiriman nota gagal atau melewati batas waktu. Nota mungkin tercetak sebagian. Periksa kertas sebelum mencoba lagi."
        message.contains("connect", true) && error is java.io.IOException -> "Koneksi printer gagal. Pastikan printer menyala, dekat, tidak dipakai aplikasi/perangkat lain, lalu uji koneksi lagi. Jika tetap gagal, kirim model printer dan detail error: $message"
        message.contains("backup", true) || message.contains("JSON", true) -> "Cadangan tidak valid, rusak, terlalu besar, atau versinya tidak didukung. Data lama tidak diubah."
        message.contains("Cash must cover") -> "Pembayaran tunai harus mencukupi total; nontunai harus sama dengan total."
        message.contains("Discount") -> "Diskon harus berupa rupiah bulat dan tidak melebihi subtotal."
        message.contains("code", true) -> "Produk ini memerlukan token atau kode. Lengkapi sebelum menyimpan."
        message.contains("phone number") -> "Nomor tujuan atau ID pelanggan tidak valid. Periksa kembali."
        message.contains("kWh") -> "Isi jumlah kWh lebih dari nol, maksimal 1.000.000, hingga 3 angka desimal."
        message.contains("Electricity buyer") -> "Lengkapi nama pelanggan dan jumlah kWh untuk transaksi PLN."
        message.contains("logo", true) -> "Logo tidak valid atau terlalu besar. Pilih gambar sederhana maksimal 4 MB, lalu simpan ulang."
        message.contains("limit", true) || message.contains("range", true) || message.contains("amount", true) -> "Nilai atau jumlah data melewati batas aplikasi. Kurangi nilai dan coba lagi."
        error is java.io.IOException -> "Koneksi printer atau akses file gagal. Periksa perangkat, izin, dan ruang penyimpanan sebelum mengulang."
        else -> "Proses belum berhasil. Periksa kelengkapan dan panjang isian, lalu coba lagi."
    }
}
