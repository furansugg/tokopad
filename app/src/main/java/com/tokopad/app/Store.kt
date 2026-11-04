package com.tokopad.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/** Synchronous; call on an IO thread. Amounts are whole rupiah. */
class Store(context: Context) : java.io.Closeable {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "tokopad.db", null, 3) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, active INTEGER NOT NULL, stock INTEGER NOT NULL CHECK(stock >= 0), data TEXT NOT NULL)")
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT NOT NULL)")
            db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK(id = 1), data TEXT NOT NULL)")
            db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, total INTEGER NOT NULL CHECK(total >= 0), customer_id INTEGER REFERENCES customers(id) ON DELETE SET NULL, cancelled_at INTEGER, cancellation_reason TEXT NOT NULL DEFAULT '', data TEXT NOT NULL)")
            db.execSQL("CREATE INDEX sales_created_at ON sales(created_at)")
            db.execSQL("CREATE INDEX sales_customer_id ON sales(customer_id)")
            createSaleCustomers(db)
            createCreditColumns(db)
            // ponytail: sum(UTF-8 data bytes + 256/row) <= 28 MiB keeps escaped backups below 64 MiB; raise only with the backup ceiling.
            db.execSQL("CREATE TABLE storage_budget (id INTEGER PRIMARY KEY CHECK(id = 1), bytes INTEGER NOT NULL CONSTRAINT storage_budget_limit CHECK(bytes BETWEEN 0 AND $MAX_STORAGE_BYTES))")
            db.execSQL("INSERT INTO storage_budget (id, bytes) VALUES (1, 0)")
            for (table in listOf("products", "customers", "sales", "settings")) {
                db.execSQL("CREATE TRIGGER ${table}_budget_insert AFTER INSERT ON $table BEGIN UPDATE storage_budget SET bytes = bytes + length(CAST(NEW.data AS BLOB)) + 256 WHERE id = 1; END")
                db.execSQL("CREATE TRIGGER ${table}_budget_update AFTER UPDATE OF data ON $table BEGIN UPDATE storage_budget SET bytes = bytes + length(CAST(NEW.data AS BLOB)) - length(CAST(OLD.data AS BLOB)) WHERE id = 1; END")
                db.execSQL("CREATE TRIGGER ${table}_budget_delete AFTER DELETE ON $table BEGIN UPDATE storage_budget SET bytes = bytes - length(CAST(OLD.data AS BLOB)) - 256 WHERE id = 1; END")
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion in 1..2 && newVersion == 3) { "Unsupported database version: $oldVersion to $newVersion" }
            if (oldVersion < 2) {
                createSaleCustomers(db)
                db.query("sales", null, null, null, null, null, "id").use { c ->
                    while (c.moveToNext()) linkSaleCustomers(db, sale(c))
                }
            }
            createCreditColumns(db)
            db.execSQL("UPDATE sales SET initial_received = total")
        }
    }

    override fun close() = helper.close()

    private fun createCreditColumns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN initial_received INTEGER NOT NULL DEFAULT 0 CHECK(initial_received >= 0)")
        db.execSQL("ALTER TABLE sales ADD COLUMN outstanding INTEGER NOT NULL DEFAULT 0 CHECK(outstanding >= 0)")
        db.execSQL("ALTER TABLE sales ADD COLUMN settled_at INTEGER")
        db.execSQL("ALTER TABLE sales ADD COLUMN settlement_amount INTEGER NOT NULL DEFAULT 0 CHECK(settlement_amount >= 0)")
        db.execSQL("ALTER TABLE sales ADD COLUMN settlement_request TEXT")
        db.execSQL("CREATE INDEX sales_outstanding_customer ON sales(customer_id, outstanding)")
        db.execSQL("CREATE INDEX sales_settled_at ON sales(settled_at)")
        db.execSQL("CREATE UNIQUE INDEX sales_settlement_request ON sales(settlement_request)")
    }

    private fun createSaleCustomers(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sale_customers (sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, PRIMARY KEY(sale_id, customer_id))")
        db.execSQL("CREATE INDEX sale_customers_customer_id ON sale_customers(customer_id, sale_id)")
    }

    private fun linkSaleCustomers(db: SQLiteDatabase, sale: Sale) {
        val ids = sale.lines.mapNotNull { it.customerId }.toMutableSet()
        sale.customerId?.let { ids.add(it) }
        // Deleted buyers remain receipt snapshots, never live profile links.
        db.compileStatement("INSERT OR IGNORE INTO sale_customers (sale_id, customer_id) SELECT ?, id FROM customers WHERE id = ?").use { statement ->
            statement.bindLong(1, sale.id)
            for (id in ids) {
                statement.bindLong(2, id)
                statement.executeInsert()
            }
        }
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } catch (e: SQLiteConstraintException) {
            if (e.message.orEmpty().contains("budget", ignoreCase = true)) {
                throw IllegalArgumentException("Local data limit reached; export backup before starting a new dataset", e)
            }
            throw e
        } finally {
            db.endTransaction()
        }
    }

    fun products(includeInactive: Boolean = false): List<Product> =
        helper.readableDatabase.query("products", null, if (includeInactive) null else "active = 1", null, null, null, "id DESC").use { c ->
            buildList { while (c.moveToNext()) add(product(c)) }
        }

    fun saveProduct(product: Product): Long {
        validateProduct(product, allowNew = true)
        return transaction { db ->
            val values = productValues(product)
            if (product.id == 0L) {
                requireCapacity(db, "products", MAX_PRODUCTS)
                db.insertOrThrow("products", null, values).also { validId(it) }
            } else {
                require(db.update("products", values, "id = ?", arrayOf(product.id.toString())) == 1) { "Product no longer exists" }
                product.id
            }
        }
    }

    fun customers(): List<Customer> = helper.readableDatabase.query("customers", null, null, null, null, null, "id DESC").use { c ->
        buildList { while (c.moveToNext()) add(customer(c)) }
    }

    fun saveCustomer(customer: Customer): Long {
        validateCustomer(customer, allowNew = true)
        return transaction { db ->
            val values = ContentValues().apply { put("data", customerJson(customer).toString()) }
            if (customer.id == 0L) {
                requireCapacity(db, "customers", MAX_CUSTOMERS)
                db.insertOrThrow("customers", null, values).also { validId(it) }
            } else {
                require(db.update("customers", values, "id = ?", arrayOf(customer.id.toString())) == 1) { "Customer no longer exists" }
                customer.id
            }
        }
    }

    fun deleteCustomer(id: Long) {
        validId(id)
        transaction { db ->
            db.query("sales", arrayOf("id"), "customer_id = ? AND outstanding > 0", arrayOf(id.toString()), null, null, null, "1").use {
                require(!it.moveToFirst()) { "Customer has outstanding debt; settle or cancel those sales before deleting" }
            }
            db.delete("customers", "id = ?", arrayOf(id.toString()))
        }
    }

    fun settings(): ShopSettings = readSettings(helper.readableDatabase)

    fun saveSettings(settings: ShopSettings) {
        validateSettings(settings)
        transaction { db -> writeSettings(db, settings) }
    }

    fun checkout(lines: List<CartLine>, customer: Customer?, discount: Long, payment: String, paid: Long, requestId: String = UUID.randomUUID().toString(), isCredit: Boolean = false): Sale {
        require(UUID.fromString(requestId).toString() == requestId) { "Invalid checkout request" }
        require(lines.isNotEmpty() && lines.size <= MAX_LINES) { "Cart must contain 1–$MAX_LINES lines" }
        amount(discount, "Discount")
        amount(paid, "Paid amount")
        require(payment in PAYMENTS) { "Payment must be cash or noncash" }
        customer?.let { validateCustomer(it, allowNew = true) }
        require(!isCredit || (customer != null && customer.id > 0)) { "Debt requires a saved customer" }
        lines.forEach { validateLine(it) }
        // Assigned buyer IDs are snapshots, not part of the caller's retry identity.
        // Keep the request digest through cancellation/backup: resolved sale customers can differ from the request.
        val signature = digest(JSONArray().apply {
            lines.forEach { put(JSONArray(listOf(it.product.id, it.product.price, it.quantity, it.destination, it.code, it.note, it.buyerName, it.kwh))) }
            put(customer?.let { customerJson(it) } ?: JSONObject.NULL)
            put(discount); put(payment); put(paid)
            // Preserve the digest of paid-in-full checkouts made by previous versions.
            if (isCredit) put(true)
        }.toString())
        return transaction { db ->
            db.query("sales", null, "number = ?", arrayOf(requestId), null, null, null).use { c ->
                if (c.moveToFirst()) {
                    val existing = sale(c)
                    val storedSignature = JSONObject(c.string("data")).optionalString("checkoutSignature")
                    fun legacySignature(items: List<CartLine>) = items.map { listOf(it.product.id.toString(), it.product.price.toString(), it.quantity.toString(), it.destination, it.code, it.note, it.buyerName, it.kwh) }
                    require(if (storedSignature.isNotEmpty()) storedSignature == signature else
                        legacySignature(existing.lines) == legacySignature(lines) && existing.discount == discount &&
                        existing.payment == payment && existing.paid == paid && existing.isCredit == isCredit &&
                        existing.customerName == customer?.name.orEmpty() && existing.customerPhone == customer?.phone.orEmpty()) {
                        "Checkout request already used for a different sale; discard the cart and start again"
                    }
                    return@transaction existing
                }
            }
            requireCapacity(db, "sales", MAX_SALES)
            var subtotal = 0L
            val snapshots = lines.map { line ->
                validId(line.product.id)
                val fresh = findProduct(db, line.product.id) ?: throw IllegalArgumentException("Product no longer exists")
                require(fresh.active) { "Product is inactive: ${fresh.name}" }
                require(fresh.price == line.product.price) { "Price changed: ${fresh.name}; refresh the cart" }
                val snapshot = line.copy(product = fresh)
                validateLine(snapshot)
                if (fresh.kind == ProductKind.ELECTRICITY) {
                    require(snapshot.buyerName.isNotBlank()) { "Electricity buyer name is required" }
                    require(snapshot.kwh.isNotEmpty()) { "Electricity kWh is required" }
                }
                subtotal = addAmount(subtotal, lineTotal(snapshot))
                if (fresh.trackStock) {
                    db.execSQL("UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?", arrayOf(line.quantity, fresh.id, line.quantity))
                    db.rawQuery("SELECT changes()", null).use { c ->
                        c.moveToFirst()
                        require(c.getLong(0) == 1L) { "Insufficient stock: ${fresh.name}" }
                    }
                }
                snapshot
            }
            require(discount <= subtotal) { "Discount exceeds subtotal" }
            val total = subtotal - discount
            if (isCredit) require(total > 0 && paid < total) { "Debt initial payment must be less than a positive total" }
            else require(if (payment == "cash") paid >= total else paid == total) { "Cash must cover total; noncash must equal total" }
            val currentCustomer = customer?.takeIf { it.id > 0 }?.let {
                findCustomer(db, it.id) ?: throw IllegalArgumentException("Customer no longer exists")
            }
            val selected = snapshots.map { line -> line.customerId?.let {
                findCustomer(db, it) ?: throw IllegalArgumentException("Customer no longer exists")
            } }
            val buyers by lazy {
                db.query("customers", null, null, null, null, null, "id").use { c ->
                    buildList { while (c.moveToNext()) add(customer(c)) }.toMutableList()
                }
            }
            fun saveBuyer(buyer: Customer): Customer {
                validateCustomer(buyer, allowNew = true)
                requireCapacity(db, "customers", MAX_CUSTOMERS)
                val id = db.insertOrThrow("customers", null, ContentValues().apply { put("data", customerJson(buyer).toString()) })
                validId(id)
                return buyer.copy(id = id).also { buyers.add(it) }
            }
            val manualBuyer = currentCustomer ?: customer?.let { supplied ->
                buyers.firstOrNull { it.name == supplied.name && it.phone == supplied.phone && it.meterNumber == supplied.meterNumber }
                    ?: saveBuyer(supplied)
            }
            val savedLines = snapshots.mapIndexed { index, line ->
                if (line.product.kind == ProductKind.PHYSICAL || line.buyerName.isBlank() || line.destination.isEmpty()) line
                else {
                    val electricity = line.product.kind == ProductKind.ELECTRICITY
                    fun matches(buyer: Customer) = buyer.name == line.buyerName &&
                        (if (electricity) buyer.meterNumber else buyer.phone) == line.destination
                    val buyer = selected[index]?.takeIf { matches(it) } ?: buyers.firstOrNull { matches(it) }
                        ?: saveBuyer(Customer(name = line.buyerName, phone = if (electricity) "" else line.destination,
                            meterNumber = if (electricity) line.destination else ""))
                    line.copy(customerId = buyer.id)
                }
            }
            val customerSnapshot = manualBuyer
            val sale = Sale(
                id = 0, number = requestId, createdAt = System.currentTimeMillis(),
                zoneId = ZoneId.systemDefault().id, lines = savedLines,
                customerId = customerSnapshot?.id, customerName = customerSnapshot?.name.orEmpty(),
                customerPhone = customerSnapshot?.phone.orEmpty(), subtotal = subtotal, discount = discount,
                total = total, payment = payment, paid = paid, shop = readSettings(db), isCredit = isCredit,
            )
            val id = db.insertOrThrow("sales", null, saleValues(sale).apply {
                put("data", saleJson(sale).put("checkoutSignature", signature).toString())
            }).also { validId(it) }
            sale.copy(id = id).also { linkSaleCustomers(db, it) }
        }
    }

    fun sales(from: Long = 0, to: Long = Long.MAX_VALUE, query: String = ""): List<Sale> {
        validRange(from, to)
        text(query, 100, "Search")
        return helper.readableDatabase.query(
            "sales", null, "created_at >= ? AND created_at < ?",
            arrayOf(from.toString(), to.toString()), null, null, "created_at DESC, id DESC", if (query.isEmpty()) "500" else null,
        ).use { c -> buildList {
            while (size < 500 && c.moveToNext()) {
                if (query.isEmpty()) {
                    add(sale(c))
                    continue
                }
                val receiptNumber = "TP-${c.long("id").toString().padStart(8, '0')}"
                if (receiptNumber.contains(query) || c.string("number").contains(query)) {
                    add(sale(c))
                    continue
                }
                val data = JSONObject(c.string("data"))
                if (data.string("customerName").contains(query, ignoreCase = true) ||
                    data.objects("lines", MAX_LINES).any {
                        ProductKind.valueOf(it.getJSONObject("product").string("kind")) != ProductKind.PHYSICAL &&
                            it.optionalString("buyerName").contains(query, ignoreCase = true)
                    }) add(sale(c, data))
            }
        } }
    }

    /** Iterates all sales in the range without applying the history search or result limit. Call on an IO thread. */
    fun forEachSaleInRange(from: Long, to: Long, consume: (Sale) -> Unit) {
        validRange(from, to)
        helper.readableDatabase.query(
            "sales", null, "created_at >= ? AND created_at < ?",
            arrayOf(from.toString(), to.toString()), null, null, "created_at DESC, id DESC",
        ).use { c -> while (c.moveToNext()) consume(sale(c)) }
    }

    fun sale(id: Long): Sale {
        validId(id)
        return findSale(helper.readableDatabase, id) ?: throw IllegalArgumentException("Sale not found")
    }

    fun salesForCustomer(customerId: Long): List<Sale> {
        validId(customerId)
        return helper.readableDatabase.query(
            "sales", null, "id IN (SELECT sale_id FROM sale_customers WHERE customer_id = ?)", arrayOf(customerId.toString()),
            null, null, "created_at DESC, id DESC", "500",
        ).use { c -> buildList { while (c.moveToNext()) add(sale(c)) } }
    }

    fun outstandingSales(): List<Sale> = helper.readableDatabase.query(
        "sales", null, "outstanding > 0 AND cancelled_at IS NULL", null,
        null, null, "created_at DESC, id DESC",
    ).use { c -> buildList { while (c.moveToNext()) add(sale(c)) } }

    fun outstandingForCustomer(customerId: Long): List<Sale> {
        validId(customerId)
        return helper.readableDatabase.query(
            "sales", null, "customer_id = ? AND outstanding > 0", arrayOf(customerId.toString()),
            null, null, "created_at DESC, id DESC",
        ).use { c -> buildList { while (c.moveToNext()) add(sale(c)) } }
    }

    fun settleSale(id: Long, amount: Long, payment: String, requestId: String): Sale {
        validId(id)
        Companion.amount(amount, "Settlement amount")
        require(payment in PAYMENTS) { "Settlement payment must be cash or noncash" }
        require(UUID.fromString(requestId).toString() == requestId) { "Invalid settlement request" }
        return transaction { db ->
            val sale = findSale(db, id) ?: throw IllegalArgumentException("Sale not found")
            require(sale.cancelledAt == null) { "Cannot settle a cancelled sale" }
            sale.settlement?.let { existing ->
                require(existing.requestId == requestId && existing.amount == amount && existing.payment == payment) { "Debt is already settled" }
                return@transaction sale
            }
            require(sale.isCredit && sale.balance > 0) { "Sale has no outstanding debt" }
            require(amount == sale.balance) { "Settlement must pay the entire remaining debt" }
            db.query("sales", arrayOf("id"), "settlement_request = ?", arrayOf(requestId), null, null, null).use {
                require(!it.moveToFirst()) { "Settlement request already used for another sale" }
            }
            val settlement = Settlement(amount, payment, maxOf(System.currentTimeMillis(), sale.createdAt), requestId)
            val updated = sale.copy(settlement = settlement)
            val data = db.query("sales", arrayOf("data"), "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
                check(c.moveToFirst()); JSONObject(c.string("data"))
            }
            val values = ContentValues().apply {
                put("outstanding", 0)
                put("settled_at", settlement.createdAt)
                put("settlement_amount", settlement.amount)
                put("settlement_request", requestId)
                put("data", data.put("settlement", settlementJson(settlement)).toString())
            }
            check(db.update("sales", values, "id = ? AND outstanding > 0 AND cancelled_at IS NULL", arrayOf(id.toString())) == 1)
            updated
        }
    }

    fun cancelSale(id: Long, reason: String) {
        validId(id)
        text(reason, 1000, "Cancellation reason", required = true, multiline = true)
        transaction { db ->
            val sale = findSale(db, id) ?: throw IllegalArgumentException("Sale not found")
            require(sale.cancelledAt == null) { "Sale is already cancelled" }
            for (line in sale.lines.filter { it.product.trackStock }) {
                val product = findProduct(db, line.product.id) ?: error("Sale product is missing")
                require(line.quantity <= MAX_QUANTITY - product.stock) { "Restored stock exceeds limit" }
                db.execSQL("UPDATE products SET stock = stock + ? WHERE id = ?", arrayOf(line.quantity, product.id))
            }
            val timestamp = maxOf(System.currentTimeMillis(), sale.createdAt, sale.settlement?.createdAt ?: 0)
            val values = ContentValues().apply {
                put("cancelled_at", timestamp)
                put("cancellation_reason", reason)
                put("outstanding", 0)
                val data = db.query("sales", arrayOf("data"), "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
                    check(c.moveToFirst()); JSONObject(c.string("data"))
                }
                put("data", data.put("cancelledAt", timestamp).put("cancellationReason", reason).toString())
            }
            check(db.update("sales", values, "id = ? AND cancelled_at IS NULL", arrayOf(id.toString())) == 1)
        }
    }

    fun dashboard(from: Long, to: Long): Dashboard {
        validRange(from, to)
        return transaction { db ->
            val totals = db.rawQuery(
                "SELECT COALESCE(SUM(total), 0), COUNT(*), COALESCE(SUM(initial_received), 0) FROM sales WHERE cancelled_at IS NULL AND created_at >= ? AND created_at < ?",
                arrayOf(from.toString(), to.toString()),
            ).use { c -> c.moveToFirst(); Dashboard(c.getLong(0), c.getLong(1), c.getLong(2)) }
            val settlements = db.rawQuery(
                "SELECT COALESCE(SUM(settlement_amount), 0) FROM sales WHERE cancelled_at IS NULL AND settled_at >= ? AND settled_at < ?",
                arrayOf(from.toString(), to.toString()),
            ).use { c -> c.moveToFirst(); c.getLong(0) }
            val outstanding = db.rawQuery("SELECT COALESCE(SUM(outstanding), 0) FROM sales WHERE cancelled_at IS NULL", null)
                .use { c -> c.moveToFirst(); c.getLong(0) }
            totals.copy(received = totals.received + settlements, outstanding = outstanding)
        }
    }

    fun exportBackup(): String = transaction { db ->
        val products = JSONArray()
        db.query("products", null, null, null, null, null, "id").use { c -> while (c.moveToNext()) products.put(productJson(product(c))) }
        val customers = JSONArray()
        db.query("customers", null, null, null, null, null, "id").use { c -> while (c.moveToNext()) customers.put(customerJson(customer(c))) }
        val sales = JSONArray()
        db.query("sales", null, null, null, null, null, "id").use { c -> while (c.moveToNext()) {
            val json = saleJson(sale(c))
            val signature = JSONObject(c.string("data")).optionalString("checkoutSignature")
            if (signature.isNotEmpty()) json.put("checkoutSignature", signature)
            sales.put(json)
        } }
        val payload = JSONObject().put("products", products).put("customers", customers).put("sales", sales)
            .put("settings", settingsJson(readSettings(db).copy(printerAddress = ""))).toString()
        val result = JSONObject().put("version", 2).put("sha256", digest(payload)).put("payload", payload).toString()
        requireBackupSize(result)
        result
    }

    fun restoreBackup(text: String) {
        val data = decodeBackup(text) // Fully validate before opening the replacement transaction.
        transaction { db ->
            db.delete("sales", null, null)
            db.delete("customers", null, null)
            db.delete("products", null, null)
            db.delete("settings", null, null)
            db.delete("sqlite_sequence", "name IN ('products', 'customers', 'sales')", null)
            for (p in data.products) db.insertOrThrow("products", null, productValues(p).apply { put("id", p.id) })
            for (c in data.customers) db.insertOrThrow("customers", null, ContentValues().apply {
                put("id", c.id); put("data", customerJson(c).toString())
            })
            for (s in data.sales) {
                db.insertOrThrow("sales", null, saleValues(s).apply {
                    put("id", s.id)
                    data.signatures[s.id]?.let { put("data", saleJson(s).put("checkoutSignature", it).toString()) }
                })
                linkSaleCustomers(db, s)
            }
            writeSettings(db, data.settings.copy(printerAddress = ""))
        }
    }

    private fun product(c: Cursor): Product = parseProduct(JSONObject(c.string("data"))).copy(id = c.long("id"), stock = c.long("stock"))
    private fun customer(c: Cursor): Customer = parseCustomer(JSONObject(c.string("data"))).copy(id = c.long("id"))
    private fun sale(c: Cursor, data: JSONObject = JSONObject(c.string("data"))): Sale = parseSale(data).copy(
        id = c.long("id"), customerId = c.nullableLong("customer_id"),
        cancelledAt = c.nullableLong("cancelled_at"), cancellationReason = c.string("cancellation_reason"),
    )

    private fun findProduct(db: SQLiteDatabase, id: Long): Product? =
        db.query("products", null, "id = ?", arrayOf(id.toString()), null, null, null).use { if (it.moveToFirst()) product(it) else null }
    private fun findCustomer(db: SQLiteDatabase, id: Long): Customer? =
        db.query("customers", null, "id = ?", arrayOf(id.toString()), null, null, null).use { if (it.moveToFirst()) customer(it) else null }
    private fun findSale(db: SQLiteDatabase, id: Long): Sale? =
        db.query("sales", null, "id = ?", arrayOf(id.toString()), null, null, null).use { if (it.moveToFirst()) sale(it) else null }
    private fun readSettings(db: SQLiteDatabase): ShopSettings =
        db.query("settings", null, "id = 1", null, null, null, null).use { if (it.moveToFirst()) parseSettings(JSONObject(it.string("data"))) else ShopSettings() }
    private fun writeSettings(db: SQLiteDatabase, settings: ShopSettings) {
        val values = ContentValues().apply {
            put("id", 1); put("data", settingsJson(settings).toString())
        }
        if (db.update("settings", values, "id = 1", null) == 0) db.insertOrThrow("settings", null, values)
    }

    private fun productValues(p: Product) = ContentValues().apply {
        put("active", if (p.active) 1 else 0); put("stock", p.stock); put("data", productJson(p).toString())
    }
    private fun saleValues(s: Sale) = ContentValues().apply {
        put("number", s.number); put("created_at", s.createdAt); put("total", s.total)
        put("initial_received", minOf(s.paid, s.total)); put("outstanding", s.balance)
        if (s.settlement == null) putNull("settled_at") else put("settled_at", s.settlement.createdAt)
        put("settlement_amount", s.settlement?.amount ?: 0)
        if (s.settlement == null) putNull("settlement_request") else put("settlement_request", s.settlement.requestId)
        if (s.customerId == null) putNull("customer_id") else put("customer_id", s.customerId)
        if (s.cancelledAt == null) putNull("cancelled_at") else put("cancelled_at", s.cancelledAt)
        put("cancellation_reason", s.cancellationReason); put("data", saleJson(s).toString())
    }

    private fun requireCapacity(db: SQLiteDatabase, table: String, max: Int) {
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
            c.moveToFirst(); require(c.getLong(0) < max) { "$table limit reached ($max)" }
        }
    }

    companion object {
        internal const val MAX_AMOUNT = 1_000_000_000_000L
        internal const val MAX_QUANTITY = 1_000_000_000L
        private const val MAX_ID = 1_000_000_000_000_000L
        private const val MAX_TIME = 253_402_300_799_999L
        private const val MAX_PRODUCTS = 10_000
        private const val MAX_CUSTOMERS = 20_000
        private const val MAX_SALES = 100_000
        private const val MAX_LINES = 200
        private const val MAX_STORAGE_BYTES = 28 * 1024 * 1024
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
        private val PAYMENTS = setOf("cash", "noncash")

        /** Throws IllegalArgumentException for malformed, incompatible, or inconsistent backups. */
        fun validateBackup(text: String) { decodeBackup(text) }

        private data class Backup(val products: List<Product>, val customers: List<Customer>, val sales: List<Sale>, val settings: ShopSettings, val signatures: Map<Long, String>)

        private fun decodeBackup(text: String): Backup {
            requireBackupSize(text)
            try {
                boundedNesting(text)
                val envelope = JSONObject(text)
                val version = envelope.integer("version", 1, 2)
                val payload = envelope.string("payload")
                val checksum = envelope.string("sha256")
                require(checksum.matches(Regex("[0-9a-f]{64}")) && checksum == digest(payload)) { "Backup integrity check failed" }
                boundedNesting(payload)
                val data = JSONObject(payload)
                val products = data.objects("products", MAX_PRODUCTS).map { parseProduct(it).also { p -> validateProduct(p) } }
                val customers = data.objects("customers", MAX_CUSTOMERS).map { parseCustomer(it).also { c -> validateCustomer(c) } }
                val signatures = mutableMapOf<Long, String>()
                val sales = data.objects("sales", MAX_SALES).map { json -> parseSale(json).also { s ->
                    require(version >= 2 || (!s.isCredit && s.settlement == null)) { "Debt requires backup version 2" }
                    validateSale(s)
                    if (json.has("checkoutSignature")) {
                        val signature = json.string("checkoutSignature")
                        require(signature.matches(Regex("[0-9a-f]{64}"))) { "Invalid checkout signature" }
                        signatures[s.id] = signature
                    }
                } }
                val settings = parseSettings(data.getJSONObject("settings")).also { validateSettings(it) }
                require(products.map { it.id }.toSet().size == products.size) { "Duplicate product ID" }
                require(customers.map { it.id }.toSet().size == customers.size) { "Duplicate customer ID" }
                require(sales.map { it.id }.toSet().size == sales.size) { "Duplicate sale ID" }
                require(sales.map { it.number }.toSet().size == sales.size) { "Duplicate sale number" }
                val settlementRequests = sales.mapNotNull { it.settlement?.requestId }
                require(settlementRequests.toSet().size == settlementRequests.size) { "Duplicate settlement request" }
                val productIds = products.map { it.id }.toSet()
                val customerIds = customers.map { it.id }.toSet()
                for (sale in sales) {
                    require(sale.customerId == null || sale.customerId in customerIds) { "Missing sale customer" }
                    require(sale.lines.all { it.product.id in productIds }) { "Missing sale product" }
                }
                return Backup(products, customers, sales, settings, signatures)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid backup structure", e)
            }
        }

        private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

        private fun requireBackupSize(text: String) {
            require(text.length <= MAX_BACKUP_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
                "Backup exceeds supported size"
            }
        }

        // Bound recursion before org.json parses an untrusted file, including unknown fields.
        private fun boundedNesting(text: String) {
            var depth = 0
            var quoted = false
            var escaped = false
            for (c in text) {
                if (quoted) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') quoted = false
                } else when (c) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 24) { "Backup nesting exceeds limit" } }
                    '}', ']' -> { depth--; require(depth >= 0) { "Unbalanced backup JSON" } }
                }
            }
            require(depth == 0 && !quoted) { "Incomplete backup JSON" }
        }

        private fun validId(id: Long, allowNew: Boolean = false) {
            require(id in (if (allowNew) 0L else 1L)..MAX_ID) { "Invalid ID" }
        }
        private fun amount(value: Long, label: String) { require(value in 0..MAX_AMOUNT) { "$label must be between 0 and $MAX_AMOUNT" } }
        private fun text(value: String, max: Int, label: String, required: Boolean = false, multiline: Boolean = false) {
            require(value.length <= max && (!required || value.isNotBlank())) { "$label is required or too long (max $max)" }
            require(value.none { it.isISOControl() && !(multiline && it in "\n\r\t") }) { "$label contains control characters" }
        }
        private fun validRange(from: Long, to: Long) { require(from >= 0 && to >= from) { "Invalid date range" } }
        private fun addAmount(a: Long, b: Long): Long {
            require(b in 0..MAX_AMOUNT && a in 0..(MAX_AMOUNT - b)) { "Total exceeds supported amount" }
            return a + b
        }
        private fun lineTotal(line: CartLine): Long {
            require(line.product.price == 0L || line.quantity <= MAX_AMOUNT / line.product.price) { "Line total exceeds supported amount" }
            return line.quantity * line.product.price
        }
        private fun validateProduct(p: Product, allowNew: Boolean = false) {
            validId(p.id, allowNew)
            text(p.name, 200, "Product name", required = true)
            text(p.category, 100, "Category")
            text(p.unit, 30, "Unit", required = true)
            text(p.provider, 100, "Provider")
            text(p.packageName, 200, "Package")
            amount(p.price, "Price"); p.cost?.let { amount(it, "Cost") }; amount(p.nominal, "Nominal")
            require(p.stock in 0..MAX_QUANTITY) { "Stock exceeds supported range" }
        }
        private fun validateCustomer(c: Customer, allowNew: Boolean = false) {
            validId(c.id, allowNew)
            text(c.name, 200, "Customer name", required = true)
            text(c.phone, 64, "Phone")
            text(c.note, 2000, "Customer note", multiline = true)
            if (c.meterNumber.isNotEmpty()) validateDestination(c.meterNumber)
        }
        private fun validateSettings(s: ShopSettings) {
            require(s.language in setOf("en", "id")) { "Unsupported language" }
            text(s.shopName, 200, "Shop name", required = true)
            text(s.address, 1000, "Address", multiline = true)
            text(s.contact, 200, "Contact")
            text(s.header, 2000, "Header", multiline = true)
            text(s.footer, 2000, "Footer", multiline = true)
            text(s.cashier, 200, "Cashier")
            require(s.paperWidth == 58 || s.paperWidth == 80) { "Paper width must be 58 or 80" }
            require(s.printerAddress.isEmpty() || s.printerAddress.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}"))) { "Invalid printer address" }
            require(s.theme in setOf("system", "light", "dark")) { "Unsupported theme" }
            require(s.headerMode in setOf("name", "logo", "both")) { "Unsupported receipt header mode" }
            require(s.headerMode == "name" || s.logo.isNotEmpty()) { "Receipt header requires a logo" }
            if (s.logo.isNotEmpty()) {
                require(s.logo.length <= 32 * 1024) { "Logo exceeds 24 KiB" }
                val bytes = Base64.getDecoder().decode(s.logo)
                require(bytes.size in 33..(24 * 1024)) { "Logo must be a PNG of at most 24 KiB" }
                require(bytes.take(16) == listOf(137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82).map { it.toByte() }) { "Logo must have a PNG IHDR header" }
                fun dimension(offset: Int): Long = (offset until offset + 4).fold(0L) { value, i -> (value shl 8) or (bytes[i].toLong() and 255) }
                require(dimension(16) in 1L..384L && dimension(20) in 1L..192L) { "Logo exceeds 384x192 pixels" }
                requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Logo must be a decodable PNG" }.recycle()
            }
        }
        private fun validateDestination(value: String) {
            require(value.length in 3..64 && value.any { it in '0'..'9' } &&
                value.all { it in '0'..'9' || it in "+() .-" }) { "A valid phone number or meter/customer ID is required" }
        }
        private fun validateLine(line: CartLine) {
            validateProduct(line.product)
            require(line.quantity in 1..MAX_QUANTITY) { "Quantity must be positive and within supported range" }
            text(line.destination, 64, "Destination")
            text(line.code, 256, "Code")
            text(line.note, 1000, "Line note", multiline = true)
            text(line.buyerName, 200, "Buyer name")
            line.customerId?.let { validId(it) }
            if (line.kwh.isNotEmpty()) {
                require(line.kwh.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,3})?")) &&
                    line.kwh.toBigDecimal() > java.math.BigDecimal.ZERO && line.kwh.toBigDecimal() <= java.math.BigDecimal("1000000")) {
                    "kWh must be positive, at most 1000000, with at most 3 decimal places"
                }
            }
            if (line.product.kind in setOf(ProductKind.PULSA, ProductKind.DATA, ProductKind.ELECTRICITY)) {
                validateDestination(line.destination)
            }
            require(!line.product.requiresCode || line.code.isNotBlank()) { "Product requires a code" }
            lineTotal(line)
        }
        private fun validateSale(s: Sale) {
            validId(s.id)
            require(UUID.fromString(s.number).toString() == s.number) { "Invalid sale number" }
            require(s.createdAt in 0..MAX_TIME) { "Invalid sale time" }
            text(s.zoneId, 100, "Time zone", required = true)
            ZoneId.of(s.zoneId)
            require(s.lines.isNotEmpty() && s.lines.size <= MAX_LINES) { "Invalid sale line count" }
            var subtotal = 0L
            for (line in s.lines) { validateLine(line); subtotal = addAmount(subtotal, lineTotal(line)) }
            amount(s.subtotal, "Subtotal"); amount(s.discount, "Discount"); amount(s.total, "Total"); amount(s.paid, "Paid amount")
            require(s.subtotal == subtotal && s.discount <= subtotal && s.total == subtotal - s.discount) { "Inconsistent sale totals" }
            require(s.payment in PAYMENTS) { "Invalid payment" }
            if (s.isCredit) {
                require(s.total > 0 && s.paid < s.total) { "Invalid debt initial payment" }
                require(s.customerName.isNotBlank() && (s.balance == 0L || s.customerId != null)) { "Missing debt customer" }
            } else {
                require(if (s.payment == "cash") s.paid >= s.total else s.paid == s.total) { "Invalid payment" }
                require(s.settlement == null) { "Unexpected settlement" }
            }
            s.settlement?.let {
                require(it.amount == s.total - s.paid && it.payment in PAYMENTS) { "Settlement must pay the entire remaining debt" }
                require(it.createdAt in s.createdAt..(s.cancelledAt ?: MAX_TIME)) { "Invalid settlement time" }
                require(UUID.fromString(it.requestId).toString() == it.requestId) { "Invalid settlement request" }
            }
            s.customerId?.let { validId(it) }
            text(s.customerName, 200, "Customer name")
            text(s.customerPhone, 64, "Customer phone")
            require(s.customerId == null || s.customerName.isNotBlank()) { "Missing customer snapshot" }
            validateSettings(s.shop)
            text(s.cancellationReason, 1000, "Cancellation reason", multiline = true)
            if (s.cancelledAt == null) require(s.cancellationReason.isEmpty()) { "Unexpected cancellation reason" }
            else require(s.cancelledAt in s.createdAt..MAX_TIME && s.cancellationReason.isNotBlank()) { "Invalid cancellation" }
        }

        private fun productJson(p: Product) = JSONObject().put("id", p.id).put("name", p.name).put("category", p.category)
            .put("price", p.price).put("cost", p.cost ?: JSONObject.NULL).put("kind", p.kind.name).put("unit", p.unit)
            .put("trackStock", p.trackStock).put("stock", p.stock).put("provider", p.provider).put("packageName", p.packageName)
            .put("nominal", p.nominal).put("requiresCode", p.requiresCode).put("active", p.active)
        private fun customerJson(c: Customer) = JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone).put("note", c.note)
            .put("meterNumber", c.meterNumber)
        private fun settingsJson(s: ShopSettings) = JSONObject().put("language", s.language).put("shopName", s.shopName)
            .put("address", s.address).put("contact", s.contact).put("header", s.header).put("footer", s.footer)
            .put("showCustomer", s.showCustomer).put("cashier", s.cashier).put("showDiscount", s.showDiscount)
            .put("paperWidth", s.paperWidth).put("printerAddress", s.printerAddress)
            .put("theme", s.theme).put("headerMode", s.headerMode).put("logo", s.logo)
        private fun saleJson(s: Sale): JSONObject {
            val lines = JSONArray()
            for (line in s.lines) lines.put(JSONObject().put("product", productJson(line.product)).put("quantity", line.quantity)
                .put("destination", line.destination).put("code", line.code).put("note", line.note)
                .put("buyerName", line.buyerName).put("kwh", line.kwh).put("customerId", line.customerId ?: JSONObject.NULL))
            return JSONObject().put("id", s.id).put("number", s.number).put("createdAt", s.createdAt).put("zoneId", s.zoneId)
                .put("lines", lines).put("customerId", s.customerId ?: JSONObject.NULL).put("customerName", s.customerName)
                .put("customerPhone", s.customerPhone).put("subtotal", s.subtotal).put("discount", s.discount).put("total", s.total)
                .put("payment", s.payment).put("paid", s.paid).put("shop", settingsJson(s.shop))
                .put("cancelledAt", s.cancelledAt ?: JSONObject.NULL).put("cancellationReason", s.cancellationReason)
                .put("isCredit", s.isCredit).put("settlement", s.settlement?.let { settlementJson(it) } ?: JSONObject.NULL)
        }
        private fun settlementJson(s: Settlement) = JSONObject().put("amount", s.amount).put("payment", s.payment)
            .put("createdAt", s.createdAt).put("requestId", s.requestId)
        private fun parseProduct(o: JSONObject) = Product(
            id = o.integer("id", 0, MAX_ID), name = o.string("name"), category = o.string("category"), price = o.integer("price", 0, MAX_AMOUNT),
            cost = o.nullableInteger("cost", 0, MAX_AMOUNT), kind = ProductKind.valueOf(o.string("kind")), unit = o.string("unit"),
            trackStock = o.boolean("trackStock"), stock = o.integer("stock", 0, MAX_QUANTITY), provider = o.string("provider"),
            packageName = o.string("packageName"), nominal = o.integer("nominal", 0, MAX_AMOUNT),
            requiresCode = o.boolean("requiresCode"), active = o.boolean("active"),
        )
        private fun parseCustomer(o: JSONObject) = Customer(o.integer("id", 0, MAX_ID), o.string("name"), o.string("phone"), o.string("note"), o.optionalString("meterNumber"))
        private fun parseSettings(o: JSONObject) = ShopSettings(
            language = o.string("language"), shopName = o.string("shopName"), address = o.string("address"), contact = o.string("contact"),
            header = o.string("header"), footer = o.string("footer"), showCustomer = o.boolean("showCustomer"),
            cashier = o.string("cashier"), showDiscount = o.boolean("showDiscount"), paperWidth = o.integer("paperWidth", 58, 80).toInt(),
            printerAddress = o.string("printerAddress"),
            theme = o.optionalString("theme", "system"), headerMode = o.optionalString("headerMode", "name"), logo = o.optionalString("logo"),
        )
        private fun parseSale(o: JSONObject) = Sale(
            id = o.integer("id", 0, MAX_ID), number = o.string("number"), createdAt = o.integer("createdAt", 0, MAX_TIME), zoneId = o.string("zoneId"),
            lines = o.objects("lines", MAX_LINES).map { CartLine(parseProduct(it.getJSONObject("product")), it.integer("quantity", 1, MAX_QUANTITY),
                it.string("destination"), it.string("code"), it.string("note"), it.optionalString("buyerName"), it.optionalString("kwh"),
                if (it.has("customerId")) it.nullableInteger("customerId", 1, MAX_ID) else null) },
            customerId = o.nullableInteger("customerId", 1, MAX_ID), customerName = o.string("customerName"), customerPhone = o.string("customerPhone"),
            subtotal = o.integer("subtotal", 0, MAX_AMOUNT), discount = o.integer("discount", 0, MAX_AMOUNT), total = o.integer("total", 0, MAX_AMOUNT),
            payment = o.string("payment"), paid = o.integer("paid", 0, MAX_AMOUNT), shop = parseSettings(o.getJSONObject("shop")),
            cancelledAt = o.nullableInteger("cancelledAt", 0, MAX_TIME), cancellationReason = o.string("cancellationReason"),
            isCredit = if (o.has("isCredit")) o.boolean("isCredit") else false,
            settlement = if (!o.has("settlement") || o.get("settlement") === JSONObject.NULL) null else o.getJSONObject("settlement").let {
                Settlement(it.integer("amount", 1, MAX_AMOUNT), it.string("payment"), it.integer("createdAt", 0, MAX_TIME), it.string("requestId"))
            },
        )

        // JSONObject getters coerce strings/floats; backup inputs must retain their actual JSON types.
        private fun JSONObject.integer(key: String, min: Long, max: Long): Long {
            val value = get(key)
            require(value is Int || value is Long) { "$key must be an integer" }
            val number = (value as Number).toLong()
            require(number in min..max) { "$key exceeds supported range" }
            return number
        }
        private fun JSONObject.nullableInteger(key: String, min: Long, max: Long): Long? =
            if (get(key) === JSONObject.NULL) null else integer(key, min, max)
        private fun JSONObject.string(key: String): String = get(key).also { require(it is String) { "$key must be text" } } as String
        private fun JSONObject.optionalString(key: String, default: String = ""): String = if (has(key)) string(key) else default
        private fun JSONObject.boolean(key: String): Boolean = get(key).also { require(it is Boolean) { "$key must be boolean" } } as Boolean
        private fun JSONObject.objects(key: String, max: Int): List<JSONObject> {
            val array = getJSONArray(key)
            require(array.length() <= max) { "$key exceeds record limit ($max)" }
            return List(array.length()) { array.getJSONObject(it) }
        }
        private fun Cursor.string(key: String): String = getString(getColumnIndexOrThrow(key))
        private fun Cursor.long(key: String): Long = getLong(getColumnIndexOrThrow(key))
        private fun Cursor.nullableLong(key: String): Long? = getColumnIndexOrThrow(key).let { if (isNull(it)) null else getLong(it) }
    }
}
