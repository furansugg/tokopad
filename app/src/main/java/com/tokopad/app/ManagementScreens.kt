package com.tokopad.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ManagementSearch(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { PadIcon("search") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun SettingsGroup(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadIcon(icon, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
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
    var editing by rememberSaveable { mutableStateOf<Product?>(null) }
    val query = search.trim()
    val visible = remember(products, query) {
        products.filter { product ->
            listOf(product.name, product.category, product.provider, product.packageName)
                .any { it.contains(query, ignoreCase = true) }
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SectionHeading(
                    l(language, "Products", "Produk"),
                    l(language, "${products.size} products in your catalog", "${products.size} produk di katalog"),
                )
                Button(
                    onClick = { editing = Product(name = "", price = 0) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    PadIcon("plus", tint = MaterialTheme.colorScheme.onPrimary)
                    Text(l(language, "Add product", "Tambah produk"), Modifier.padding(start = 8.dp))
                }
                ManagementSearch(search, { search = it }, l(language, "Search products", "Cari produk"))
            }
        }
        if (visible.isEmpty()) item {
            EmptyState("products", l(language, "No products found", "Produk tidak ditemukan"),
                l(language, "Add a product or try another search.", "Tambah produk atau coba pencarian lain."))
        }
        items(visible, key = { it.id }) { product ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PadIcon(if (product.kind == ProductKind.PHYSICAL) "products" else "receipt",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(listOf(kindLabel(product.kind, language), product.category).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val status = buildList {
                            if (product.kind == ProductKind.PHYSICAL && product.trackStock) {
                                add("${l(language, "Stock", "Stok")}: ${product.stock} ${product.unit}")
                            }
                            if (!product.active) add(l(language, "Inactive", "Nonaktif"))
                        }.joinToString(" · ")
                        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(product.price, language), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { editing = product }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(l(language, "Edit product", "Ubah produk"), style = MaterialTheme.typography.labelMedium)
                        }
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
    var draft by rememberSaveable(product) { mutableStateOf(product) }
    var price by rememberSaveable(product) { mutableStateOf(if (product.id == 0L) "" else product.price.toString()) }
    var cost by rememberSaveable(product) { mutableStateOf(product.cost?.toString().orEmpty()) }
    var stock by rememberSaveable(product) { mutableStateOf(product.stock.toString()) }
    var nominal by rememberSaveable(product) { mutableStateOf(product.nominal.toString()) }
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
    var editing by rememberSaveable { mutableStateOf<Customer?>(null) }
    var deleting by remember { mutableStateOf<Customer?>(null) }
    val query = search.trim()
    val visible = remember(customers, query) {
        customers.filter { customer ->
            listOf(customer.name, customer.phone, customer.note).any { it.contains(query, ignoreCase = true) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SectionHeading(
                    l(language, "Customers", "Pelanggan"),
                    l(language, "${customers.size} saved customers", "${customers.size} pelanggan tersimpan"),
                )
                Button(
                    onClick = { editing = Customer(name = "") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    PadIcon("plus", tint = MaterialTheme.colorScheme.onPrimary)
                    Text(l(language, "Add customer", "Tambah pelanggan"), Modifier.padding(start = 8.dp))
                }
                ManagementSearch(search, { search = it }, l(language, "Search customers", "Cari pelanggan"))
            }
        }
        if (visible.isEmpty()) item {
            EmptyState("customers", l(language, "No customers found", "Pelanggan tidak ditemukan"),
                l(language, "Add a customer or try another search.", "Tambah pelanggan atau coba pencarian lain."))
        }
        items(visible, key = { it.id }) { customer ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(customer.name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }
                                .joinToString("").uppercase(), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (customer.phone.isNotBlank()) Text(customer.phone,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editing = customer }) { Text(l(language, "Edit", "Ubah")) }
                        TextButton(onClick = { onHistory(customer) }) {
                            PadIcon("history", Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                            Text(l(language, "Transactions", "Transaksi"), Modifier.padding(start = 5.dp))
                        }
                        TextButton(onClick = { deleting = customer }) {
                            Text(l(language, "Delete", "Hapus"), color = MaterialTheme.colorScheme.error)
                        }
                    }
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
    var draft by rememberSaveable(customer) { mutableStateOf(customer) }
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
    var draft by rememberSaveable(settings) { mutableStateOf(settings) }
    var attempted by remember(settings) { mutableStateOf(false) }
    val language = draft.language
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeading(l(language, "Settings", "Pengaturan"),
            l(language, "Make Tokopad feel like your shop.", "Atur Tokopad sesuai kebutuhan tokomu."))
        SettingsGroup(l(language, "Language", "Bahasa"), "settings") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { draft = draft.copy(language = "en") }, enabled = language != "en") {
                    Text("English")
                }
                TextButton(onClick = { draft = draft.copy(language = "id") }, enabled = language != "id") {
                    Text("Indonesian")
                }
            }
        }
        SettingsGroup(l(language, "Store details", "Detail toko"), "home") {
            ManagementField(draft.shopName, { draft = draft.copy(shopName = it) }, l(language, "Store name", "Nama toko"),
                if (attempted && draft.shopName.isBlank()) l(language, "Required.", "Wajib diisi.") else null)
            ManagementField(draft.address, { draft = draft.copy(address = it) }, l(language, "Address", "Alamat"), multiline = true)
            ManagementField(draft.contact, { draft = draft.copy(contact = it) }, l(language, "Contact", "Kontak"))
            ManagementField(draft.cashier, { draft = draft.copy(cashier = it) }, l(language, "Cashier label", "Label kasir"))
        }
        SettingsGroup(l(language, "Receipt", "Nota"), "receipt") {
            ManagementField(draft.header, { draft = draft.copy(header = it) },
                l(language, "Receipt header", "Teks pembuka nota"), multiline = true)
            ManagementField(draft.footer, { draft = draft.copy(footer = it) },
                l(language, "Receipt footer", "Teks penutup nota"), multiline = true)
            Column {
                ManagementToggle(l(language, "Show customer on receipts", "Tampilkan pelanggan di nota"), draft.showCustomer) {
                    draft = draft.copy(showCustomer = it)
                }
                ManagementToggle(l(language, "Show discount details", "Tampilkan rincian diskon"), draft.showDiscount) {
                    draft = draft.copy(showDiscount = it)
                }
            }
            Text(l(language, "Paper width", "Lebar kertas"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.selectableGroup()) {
                listOf(58, 80).forEach { width ->
                    Box(Modifier.weight(1f)) {
                        ManagementChoice("$width mm", draft.paperWidth == width) { draft = draft.copy(paperWidth = width) }
                    }
                }
            }
        }
        SettingsGroup(l(language, "Printer", "Printer"), "receipt") {
            Text(l(language, "Bluetooth printer", "Printer Bluetooth"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onFindPrinters, shape = RoundedCornerShape(12.dp)) {
                PadIcon("search", Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                Text(l(language, "Find paired printers", "Cari printer yang dipasangkan"), Modifier.padding(start = 8.dp))
            }
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
                "Pair a printer in Android Bluetooth settings, then find printers.",
                "Pasangkan printer di pengaturan Bluetooth Android, lalu cari printer."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draft.printerAddress.isNotEmpty() && printers.none { it.second == draft.printerAddress }) {
                Text("${l(language, "Selected printer (not listed)", "Printer terpilih (tidak terdaftar)")}: ${draft.printerAddress}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onTestPrint) {
                Text(l(language, "Test print · saved settings", "Cetak percobaan · pengaturan tersimpan"))
            }
            Text(l(language, "Test printing uses saved settings, not unsaved changes.",
                "Cetak percobaan memakai pengaturan tersimpan, bukan perubahan yang belum disimpan."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsGroup(l(language, "Backup", "Cadangan"), "history") {
            TextButton(onClick = onExport) { Text(l(language, "Export backup", "Ekspor cadangan")) }
            Text(l(language, "Import replaces current data. Back up first; confirmation follows.",
                "Impor mengganti data saat ini. Buat cadangan terlebih dahulu; konfirmasi akan ditampilkan."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onImport) { Text(l(language, "Import backup", "Impor cadangan")) }
        }
        Button(onClick = {
            attempted = true
            if (draft.shopName.isNotBlank()) onSave(draft.copy(
                shopName = draft.shopName.trim(), address = draft.address.trim(), contact = draft.contact.trim(),
                header = draft.header.trim(), footer = draft.footer.trim(), cashier = draft.cashier.trim(),
            ))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
            PadIcon("check", tint = MaterialTheme.colorScheme.onPrimary)
            Text(l(language, "Save settings", "Simpan pengaturan"), Modifier.padding(start = 8.dp))
        }
    }
}
