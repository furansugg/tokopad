package com.tokopad.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private fun managementNumber(text: String): Long? =
    text.takeIf { it.isNotEmpty() && it.all { digit -> digit in '0'..'9' } }?.toLongOrNull()

// Runnable from a unit test without exercising the Compose UI.
internal fun managementInputSelfCheck() {
    check(managementNumber("0") == 0L)
    check(managementNumber("00012") == 12L)
    check(managementNumber(Long.MAX_VALUE.toString()) == Long.MAX_VALUE)
    listOf("", "-1", "+1", "1.5", " 1", "1e3", "１２", "9223372036854775808").forEach {
        check(managementNumber(it) == null)
    }
}

private fun kindLabel(kind: ProductKind, language: String): String = when (kind) {
    ProductKind.PHYSICAL -> l(language, "Physical", "Fisik")
    ProductKind.PULSA -> l(language, "Digital · Mobile credit", "Digital · Pulsa")
    ProductKind.ELECTRICITY -> l(language, "Digital · Electricity token", "Digital · Token listrik")
    ProductKind.DATA -> l(language, "Digital · Data package", "Digital · Kuota internet")
    ProductKind.VOUCHER -> l(language, "Digital · Internet voucher", "Digital · Voucher internet")
    ProductKind.OTHER -> l(language, "Digital · Other", "Digital · Lainnya")
}

@Composable
private fun ManagementField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        isError = error != null,
        supportingText = if (error == null) null else ({ Text(error) }),
    )
}

@Composable
private fun ManagementToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ManagementChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun ProductsScreen(products: List<Product>, language: String, onSave: (Product, () -> Unit) -> Unit) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    val query = search.trim()
    val visible = remember(products, query) {
        products.filter { product ->
            listOf(product.name, product.category, product.provider, product.packageName)
                .any { it.contains(query, ignoreCase = true) }
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(l(language, "Products", "Produk"), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { editing = Product(name = "", price = 0) }) {
                Text(l(language, "Add product", "Tambah produk"))
            }
            ManagementField(search, { search = it }, l(language, "Search products", "Cari produk"))
        }
        if (visible.isEmpty()) item {
            Text(l(language, "No products found. Add a product or change your search.",
                "Produk tidak ditemukan. Tambah produk atau ubah pencarian."))
        }
        items(visible, key = { it.id }) { product ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(kindLabel(product.kind, language))
                    if (product.category.isNotBlank()) Text(product.category)
                    Text(money(product.price, language))
                    if (product.kind == ProductKind.PHYSICAL && product.trackStock) {
                        Text("${l(language, "Stock", "Stok")}: ${product.stock} ${product.unit}")
                    }
                    if (!product.active) Text(l(language, "Inactive", "Nonaktif"))
                    TextButton(onClick = { editing = product }) {
                        Text(l(language, "Edit product", "Ubah produk"))
                    }
                }
            }
        }
    }
    editing?.let { product ->
        ProductEditor(product, language, { editing = null }) {
            onSave(it) { editing = null }
        }
    }
}

@Composable
private fun ProductEditor(product: Product, language: String, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var draft by remember(product) { mutableStateOf(product) }
    var price by remember(product) { mutableStateOf(if (product.id == 0L) "" else product.price.toString()) }
    var cost by remember(product) { mutableStateOf(product.cost?.toString().orEmpty()) }
    var stock by remember(product) { mutableStateOf(product.stock.toString()) }
    var nominal by remember(product) { mutableStateOf(product.nominal.toString()) }
    var attempted by remember(product) { mutableStateOf(false) }
    var kindsOpen by remember { mutableStateOf(false) }
    val physical = draft.kind == ProductKind.PHYSICAL
    val required = l(language, "Required.", "Wajib diisi.")
    val numeric = l(language, "Use whole digits from 0 to ${Long.MAX_VALUE}.",
        "Gunakan angka bulat dari 0 hingga ${Long.MAX_VALUE}.")
    val valid = draft.name.isNotBlank() && managementNumber(price) != null &&
        (cost.isEmpty() || managementNumber(cost) != null) &&
        (if (physical) draft.unit.isNotBlank() && managementNumber(stock) != null
        else managementNumber(nominal) != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(l(language, if (product.id == 0L) "Add product" else "Edit product",
            if (product.id == 0L) "Tambah produk" else "Ubah produk")) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ManagementField(draft.name, { draft = draft.copy(name = it) }, l(language, "Name", "Nama"),
                    if (attempted && draft.name.isBlank()) required else null)
                ManagementField(draft.category, { draft = draft.copy(category = it) }, l(language, "Category", "Kategori"))
                ManagementField(price, { price = it }, l(language, "Selling price (IDR)", "Harga jual (IDR)"),
                    if (attempted && managementNumber(price) == null) numeric else null, KeyboardType.Number)
                ManagementField(cost, { cost = it }, l(language, "Cost (IDR, optional)", "Harga modal (IDR, opsional)"),
                    if (attempted && cost.isNotEmpty() && managementNumber(cost) == null) numeric else null,
                    KeyboardType.Number)
                Text(l(language, "Product type", "Jenis produk"), style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(onClick = { kindsOpen = true }) { Text(kindLabel(draft.kind, language)) }
                    DropdownMenu(expanded = kindsOpen, onDismissRequest = { kindsOpen = false }) {
                        ProductKind.values().forEach { kind ->
                            DropdownMenuItem(text = { Text(kindLabel(kind, language)) }, onClick = {
                                draft = draft.copy(kind = kind)
                                kindsOpen = false
                            })
                        }
                    }
                }
                if (physical) {
                    ManagementField(draft.unit, { draft = draft.copy(unit = it) }, l(language, "Unit", "Satuan"),
                        if (attempted && draft.unit.isBlank()) required else null)
                    ManagementToggle(l(language, "Track stock", "Lacak stok"), draft.trackStock) {
                        draft = draft.copy(trackStock = it)
                    }
                    ManagementField(stock, { stock = it }, l(language, "Stock (whole units)", "Stok (jumlah bulat)"),
                        if (attempted && managementNumber(stock) == null) numeric else null, KeyboardType.Number)
                } else {
                    ManagementField(draft.provider, { draft = draft.copy(provider = it) },
                        l(language, "Operator / provider", "Operator / penyedia"))
                    ManagementField(draft.packageName, { draft = draft.copy(packageName = it) },
                        l(language, "Package name", "Nama paket"))
                    ManagementField(nominal, { nominal = it }, l(language, "Service nominal (IDR)", "Nominal layanan (IDR)"),
                        if (attempted && managementNumber(nominal) == null) numeric else null, KeyboardType.Number)
                    ManagementToggle(l(language, "Require token / code at checkout", "Wajib token / kode saat transaksi"),
                        draft.requiresCode) { draft = draft.copy(requiresCode = it) }
                }
                ManagementToggle(l(language, "Active", "Aktif"), draft.active) { draft = draft.copy(active = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                val parsedPrice = managementNumber(price)
                if (valid && parsedPrice != null) onSave(draft.copy(
                    name = draft.name.trim(), category = draft.category.trim(), price = parsedPrice,
                    cost = managementNumber(cost), unit = draft.unit.trim(),
                    trackStock = physical && draft.trackStock,
                    stock = if (physical) managementNumber(stock) ?: 0 else 0,
                    provider = draft.provider.trim(), packageName = draft.packageName.trim(),
                    nominal = if (physical) 0 else managementNumber(nominal) ?: 0,
                    requiresCode = !physical && draft.requiresCode,
                ))
            }) { Text(l(language, "Save", "Simpan")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(l(language, "Cancel", "Batal")) } },
    )
}

@Composable
fun CustomersScreen(
    customers: List<Customer>,
    language: String,
    onSave: (Customer, () -> Unit) -> Unit,
    onDelete: (Customer) -> Unit,
    onHistory: (Customer) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var deleting by remember { mutableStateOf<Customer?>(null) }
    val query = search.trim()
    val visible = remember(customers, query) {
        customers.filter { customer ->
            listOf(customer.name, customer.phone, customer.note).any { it.contains(query, ignoreCase = true) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(l(language, "Customers", "Pelanggan"), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { editing = Customer(name = "") }) {
                Text(l(language, "Add customer", "Tambah pelanggan"))
            }
            ManagementField(search, { search = it }, l(language, "Search customers", "Cari pelanggan"))
        }
        if (visible.isEmpty()) item {
            Text(l(language, "No customers found. Add a customer or change your search.",
                "Pelanggan tidak ditemukan. Tambah pelanggan atau ubah pencarian."))
        }
        items(visible, key = { it.id }) { customer ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(customer.name, style = MaterialTheme.typography.titleMedium)
                    if (customer.phone.isNotBlank()) Text(customer.phone)
                    if (customer.note.isNotBlank()) Text(customer.note)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { editing = customer }) { Text(l(language, "Edit", "Ubah")) }
                        TextButton(onClick = { deleting = customer }) { Text(l(language, "Delete", "Hapus")) }
                    }
                    TextButton(onClick = { onHistory(customer) }) { Text(l(language, "Transactions", "Transaksi")) }
                }
            }
        }
    }
    editing?.let { customer ->
        CustomerEditor(customer, language, { editing = null }) {
            onSave(it) { editing = null }
        }
    }
    deleting?.let { customer ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(l(language, "Delete customer?", "Hapus pelanggan?")) },
            text = { Text(l(language,
                "Delete ${customer.name}'s profile? Past transactions and their saved customer details remain. This does not erase historical personal data.",
                "Hapus profil ${customer.name}? Transaksi lama beserta salinan data pelanggan tetap tersimpan. Ini tidak menghapus data pribadi dalam riwayat.")) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDelete(customer) }) { Text(l(language, "Delete", "Hapus")) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(l(language, "Cancel", "Batal")) }
            },
        )
    }
}

@Composable
private fun CustomerEditor(customer: Customer, language: String, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var draft by remember(customer) { mutableStateOf(customer) }
    var attempted by remember(customer) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(l(language, if (customer.id == 0L) "Add customer" else "Edit customer",
            if (customer.id == 0L) "Tambah pelanggan" else "Ubah pelanggan")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ManagementField(draft.name, { draft = draft.copy(name = it) }, l(language, "Name", "Nama"),
                    if (attempted && draft.name.isBlank()) l(language, "Required.", "Wajib diisi.") else null)
                ManagementField(draft.phone, { draft = draft.copy(phone = it) },
                    l(language, "Phone (optional)", "Telepon (opsional)"), keyboard = KeyboardType.Phone)
                ManagementField(draft.note, { draft = draft.copy(note = it) },
                    l(language, "Note (optional)", "Catatan (opsional)"), multiline = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                if (draft.name.isNotBlank()) onSave(draft.copy(
                    name = draft.name.trim(), phone = draft.phone.trim(), note = draft.note.trim(),
                ))
            }) { Text(l(language, "Save", "Simpan")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(l(language, "Cancel", "Batal")) } },
    )
}

@Composable
fun SettingsScreen(
    settings: ShopSettings,
    printers: List<Pair<String, String>>,
    onSave: (ShopSettings) -> Unit,
    onFindPrinters: () -> Unit,
    onTestPrint: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var attempted by remember(settings) { mutableStateOf(false) }
    val language = draft.language
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(l(language, "Settings", "Pengaturan"), style = MaterialTheme.typography.headlineSmall)
        Text(l(language, "Language", "Bahasa"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { draft = draft.copy(language = "en") }, enabled = language != "en") {
                Text(l(language, "English", "Inggris"))
            }
            TextButton(onClick = { draft = draft.copy(language = "id") }, enabled = language != "id") {
                Text(l(language, "Indonesian", "Indonesia"))
            }
        }
        ManagementField(draft.shopName, { draft = draft.copy(shopName = it) }, l(language, "Store name", "Nama toko"),
            if (attempted && draft.shopName.isBlank()) l(language, "Required.", "Wajib diisi.") else null)
        ManagementField(draft.address, { draft = draft.copy(address = it) }, l(language, "Address", "Alamat"), multiline = true)
        ManagementField(draft.contact, { draft = draft.copy(contact = it) }, l(language, "Contact", "Kontak"))
        ManagementField(draft.header, { draft = draft.copy(header = it) },
            l(language, "Receipt header", "Teks pembuka nota"), multiline = true)
        ManagementField(draft.footer, { draft = draft.copy(footer = it) },
            l(language, "Receipt footer", "Teks penutup nota"), multiline = true)
        ManagementField(draft.cashier, { draft = draft.copy(cashier = it) }, l(language, "Cashier label", "Label kasir"))
        ManagementToggle(l(language, "Show customer on receipts", "Tampilkan pelanggan di nota"), draft.showCustomer) {
            draft = draft.copy(showCustomer = it)
        }
        ManagementToggle(l(language, "Show discount details", "Tampilkan rincian diskon"), draft.showDiscount) {
            draft = draft.copy(showDiscount = it)
        }
        Text(l(language, "Paper width", "Lebar kertas"), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            listOf(58, 80).forEach { width ->
                ManagementChoice("$width mm", draft.paperWidth == width) { draft = draft.copy(paperWidth = width) }
            }
        }
        Text(l(language, "Bluetooth printer", "Printer Bluetooth"), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onFindPrinters) { Text(l(language, "Find paired printers", "Cari printer yang dipasangkan")) }
        Column(Modifier.selectableGroup()) {
            ManagementChoice(l(language, "No printer", "Tanpa printer"), draft.printerAddress.isEmpty()) {
                draft = draft.copy(printerAddress = "")
            }
            printers.distinctBy { it.second }.forEach { (name, address) ->
                ManagementChoice("${name.ifBlank { l(language, "Unnamed printer", "Printer tanpa nama") }}\n$address",
                    draft.printerAddress == address) { draft = draft.copy(printerAddress = address) }
            }
        }
        if (printers.isEmpty()) Text(l(language,
            "No paired printers listed. Pair your printer in Android Bluetooth settings, then find printers.",
            "Belum ada printer terdaftar. Pasangkan printer di pengaturan Bluetooth Android, lalu cari printer."))
        if (draft.printerAddress.isNotEmpty() && printers.none { it.second == draft.printerAddress }) {
            Text("${l(language, "Selected printer (not listed)", "Printer terpilih (tidak terdaftar)")}: ${draft.printerAddress}")
        }
        Button(onClick = {
            attempted = true
            if (draft.shopName.isNotBlank()) onSave(draft.copy(
                shopName = draft.shopName.trim(), address = draft.address.trim(), contact = draft.contact.trim(),
                header = draft.header.trim(), footer = draft.footer.trim(), cashier = draft.cashier.trim(),
            ))
        }) { Text(l(language, "Save settings", "Simpan pengaturan")) }
        Text(l(language, "Test printing uses saved settings, not unsaved changes.",
            "Cetak percobaan memakai pengaturan tersimpan, bukan perubahan yang belum disimpan."))
        TextButton(onClick = onTestPrint) {
            Text(l(language, "Test print · saved settings", "Cetak percobaan · pengaturan tersimpan"))
        }
        Text(l(language, "Backup", "Cadangan"), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onExport) { Text(l(language, "Export backup", "Ekspor cadangan")) }
        Text(l(language, "Import replaces current data. Back up first; confirmation follows.",
            "Impor mengganti data saat ini. Buat cadangan terlebih dahulu; konfirmasi akan ditampilkan."))
        TextButton(onClick = onImport) { Text(l(language, "Import backup", "Impor cadangan")) }
    }
}
