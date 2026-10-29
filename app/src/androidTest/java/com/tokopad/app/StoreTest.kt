package com.tokopad.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StoreTest {
    private lateinit var context: Context
    private lateinit var store: Store

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("tokopad.db")
        store = Store(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase("tokopad.db")
    }

    private fun product(): Product {
        val product = Product(name = "Rice", price = 100, trackStock = true, stock = 5)
        return product.copy(id = store.saveProduct(product))
    }

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Expected input rejection")
        } catch (_: IllegalArgumentException) {
            // Expected; assertion failures and database errors must still fail the test.
        }
    }

    private fun digital(kind: ProductKind = ProductKind.ELECTRICITY): Product = Product(name = kind.name, price = 100, kind = kind)
        .let { it.copy(id = store.saveProduct(it)) }

    private fun editedBackup(edit: (JSONObject) -> Unit): String {
        val envelope = JSONObject(store.exportBackup())
        val data = JSONObject(envelope.getString("payload"))
        edit(data)
        val payload = data.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return envelope.put("payload", payload).put("sha256", digest).toString()
    }

    private fun logo(): String {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.BLACK)
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            Base64.getEncoder().encodeToString(output.toByteArray())
        } finally { bitmap.recycle() }
    }

    @Test fun digitalBuyersSaveOnlyWithSuccessfulPaymentAndDeduplicateRetries() {
        val product = digital()
        val line = CartLine(product, destination = "00123456", buyerName = "Alice", kwh = "12.345")
        rejected { store.checkout(listOf(line), null, 0, "cash", 99) }
        rejected { store.checkout(listOf(line, line.copy(destination = "bad")), null, 0, "cash", 200) }
        assertTrue(store.customers().isEmpty())
        assertTrue(store.sales().isEmpty())
        val request = UUID.randomUUID().toString()
        val sale = store.checkout(listOf(line, line), null, 0, "cash", 200, request)
        val buyer = store.customers().single()
        assertEquals("Alice", buyer.name)
        assertEquals("00123456", buyer.meterNumber)
        assertEquals("", buyer.phone)
        assertEquals(buyer.id, sale.customerId)
        assertTrue(sale.lines.all { it.customerId == buyer.id })
        assertEquals(sale, store.checkout(listOf(line, line), null, 0, "cash", 200, request))
        rejected { store.checkout(listOf(line.copy(buyerName = "Bob"), line), null, 0, "cash", 200, request) }
        rejected { store.checkout(listOf(line.copy(kwh = "13"), line), null, 0, "cash", 200, request) }
        assertEquals(1, store.customers().size)
        store.cancelSale(sale.id, "Correction")
        val backup = store.exportBackup()
        store.restoreBackup(backup)
        assertEquals(store.sale(sale.id), store.checkout(listOf(line, line), null, 0, "cash", 200, request))
        assertEquals(1, store.customers().size)
        assertEquals(listOf(store.sale(sale.id)), store.sales(query = sale.receiptNumber))
    }

    @Test fun selectedBuyerEditsNeverOverwriteProfilesAndAutofillSurvivesReopen() {
        val product = digital(ProductKind.DATA)
        val buyer = Customer(name = "Alice", phone = "08123456", meterNumber = "00987654", note = "Saved")
            .let { it.copy(id = store.saveCustomer(it)) }
        val line = CartLine(product, destination = buyer.phone, buyerName = buyer.name, customerId = buyer.id)
        val first = store.checkout(listOf(line), null, 0, "cash", 100)
        assertEquals(buyer.id, first.lines.single().customerId)
        val edited = store.checkout(listOf(line.copy(buyerName = "Bob")), null, 0, "cash", 100)
        assertNotEquals(buyer.id, edited.lines.single().customerId)
        val destinationEdit = store.checkout(listOf(line.copy(destination = "08129999")), null, 0, "cash", 100)
        assertNotEquals(buyer.id, destinationEdit.lines.single().customerId)
        assertEquals(buyer, store.customers().first { it.id == buyer.id })
        store.close()
        store = Store(context)
        assertEquals(buyer, store.customers().first { it.id == buyer.id })
        assertEquals(first, store.sale(first.id))
        store.deleteCustomer(buyer.id)
        val backup = store.exportBackup()
        Store.validateBackup(backup) // Line IDs are historical snapshots, not foreign keys.
        store.restoreBackup(backup)
        assertEquals(buyer.id, store.sale(first.id).lines.single().customerId)
        rejected { store.checkout(listOf(line), null, 0, "cash", 100) }
    }

    @Test fun saleInsertFailureRollsBackNewBuyerAndStockTogether() {
        val product = digital().copy(trackStock = true, stock = 5)
        store.saveProduct(product)
        context.openOrCreateDatabase("tokopad.db", Context.MODE_PRIVATE, null).use { db ->
            val actual = db.rawQuery("SELECT bytes FROM storage_budget WHERE id = 1", null).use { c -> c.moveToFirst(); c.getLong(0) }
            val nearLimit = 28L * 1024 * 1024 - 512
            db.execSQL("UPDATE storage_budget SET bytes = ? WHERE id = 1", arrayOf(nearLimit))
            try {
                rejected { store.checkout(listOf(CartLine(product, destination = "12345678", buyerName = "Alice", kwh = "1")), null, 0, "cash", 100) }
                assertTrue(store.customers().isEmpty())
                assertTrue(store.sales().isEmpty())
                assertEquals(5L, store.products().single().stock)
                db.rawQuery("SELECT bytes FROM storage_budget WHERE id = 1", null).use { c -> c.moveToFirst(); assertEquals(nearLimit, c.getLong(0)) }
            } finally {
                db.execSQL("UPDATE storage_budget SET bytes = ? WHERE id = 1", arrayOf(actual))
            }
        }
    }

    @Test fun manualCustomerSavesAtomicallyAndExplicitCustomerWins() {
        val product = digital(ProductKind.PULSA)
        val manual = Customer(name = "Payer", phone = "08111111")
        val line = CartLine(product, destination = "08222222", buyerName = "Recipient")
        rejected { store.checkout(listOf(line), manual, 0, "cash", 0) }
        assertTrue(store.customers().isEmpty())
        val request = UUID.randomUUID().toString()
        val sale = store.checkout(listOf(line), manual, 0, "cash", 100, request)
        assertEquals("Payer", sale.customerName)
        assertEquals(2, store.customers().size)
        assertNotEquals(sale.customerId, sale.lines.single().customerId)
        assertEquals(sale, store.checkout(listOf(line), manual, 0, "cash", 100, request))
        assertEquals(2, store.customers().size)
    }

    @Test fun customerHistoryIncludesEveryRecipientAndPayerAcrossDeletionAndRestore() {
        val product = digital(ProductKind.DATA)
        val lines = listOf(
            CartLine(product, destination = "08111111", buyerName = "Alice"),
            CartLine(product, destination = "08222222", buyerName = "Bob"),
        )
        val sale = store.checkout(lines + lines.first(), Customer(name = "Payer"), 0, "cash", 300)
        val alice = sale.lines[0].customerId!!
        val bob = sale.lines[1].customerId!!
        val payer = sale.customerId!!
        val unrelated = store.saveCustomer(Customer(name = "Unrelated"))
        assertEquals(3, setOf(alice, bob, payer).size)
        for (id in listOf(alice, bob, payer)) assertEquals(listOf(sale), store.salesForCustomer(id))
        assertTrue(store.salesForCustomer(unrelated).isEmpty())
        store.restoreBackup(store.exportBackup())
        for (id in listOf(alice, bob, payer)) assertEquals(listOf(sale), store.salesForCustomer(id))

        store.deleteCustomer(alice)
        store.deleteCustomer(payer)
        val receipt = store.sale(sale.id)
        assertEquals(sale.copy(customerId = null), receipt)
        assertEquals(alice, receipt.lines[0].customerId)
        assertTrue(store.salesForCustomer(alice).isEmpty())
        assertTrue(store.salesForCustomer(payer).isEmpty())
        assertEquals(listOf(receipt), store.salesForCustomer(bob))
        val backup = store.exportBackup()
        repeat(2) { // Replacement must cascade old links before rebuilding them.
            store.restoreBackup(backup)
            assertEquals(receipt, store.sale(sale.id))
            assertTrue(store.salesForCustomer(alice).isEmpty())
            assertTrue(store.salesForCustomer(payer).isEmpty())
            assertEquals(listOf(receipt), store.salesForCustomer(bob))
        }
        context.openOrCreateDatabase("tokopad.db", Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM sale_customers", null).use { c -> c.moveToFirst(); assertEquals(1L, c.getLong(0)) }
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun versionOneMigrationBackfillsAllBuyersButNotDeletedSnapshots() {
        val product = digital(ProductKind.PULSA)
        val lines = listOf(
            CartLine(product, destination = "08111111", buyerName = "Alice"),
            CartLine(product, destination = "08222222", buyerName = "Bob"),
        )
        val implicit = store.checkout(lines, null, 0, "cash", 200)
        val explicit = store.checkout(lines, Customer(name = "Payer"), 0, "cash", 200)
        val deleted = implicit.lines[0].customerId!!
        val remaining = implicit.lines[1].customerId!!
        val payer = explicit.customerId!!
        store.deleteCustomer(deleted)
        val before = store.exportBackup()
        store.close()
        context.openOrCreateDatabase("tokopad.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("DROP TABLE sale_customers")
            db.version = 1
        }
        store = Store(context)
        val expected = listOf(explicit, implicit.copy(customerId = null))
            .sortedWith(compareByDescending<Sale> { it.createdAt }.thenByDescending { it.id })
        assertEquals(expected, store.salesForCustomer(remaining))
        assertEquals(listOf(explicit), store.salesForCustomer(payer))
        assertTrue(store.salesForCustomer(deleted).isEmpty())
        assertEquals(before, store.exportBackup())
        store.close()
        store = Store(context)
        assertEquals(expected, store.salesForCustomer(remaining))
        context.openOrCreateDatabase("tokopad.db", Context.MODE_PRIVATE, null).use { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT COUNT(*) FROM sale_customers", null).use { c -> c.moveToFirst(); assertEquals(3L, c.getLong(0)) }
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun themeLogoAndBuyerFieldsRoundTripWithHistoricalShopSnapshot() {
        val settings = ShopSettings(theme = "dark", headerMode = "both", logo = logo())
        store.saveSettings(settings)
        val product = digital()
        val sale = store.checkout(listOf(CartLine(product, destination = "12345678", buyerName = "Alice", kwh = "0.125")), null, 0, "cash", 100)
        store.saveSettings(settings.copy(theme = "light", headerMode = "name", logo = ""))
        val backup = store.exportBackup()
        store.restoreBackup(backup)
        assertEquals(settings.copy(theme = "light", headerMode = "name", logo = ""), store.settings())
        assertEquals(settings, store.sale(sale.id).shop)
        assertEquals(sale, store.sale(sale.id))
        assertEquals(backup, store.exportBackup())
    }

    @Test fun oldBackupsDefaultMissingOptionalFields() {
        val product = digital()
        val buyer = Customer(name = "Alice", meterNumber = "12345678").let { it.copy(id = store.saveCustomer(it)) }
        val sale = store.checkout(listOf(CartLine(product, destination = buyer.meterNumber, buyerName = buyer.name, kwh = "1")), buyer, 0, "cash", 100)
        val legacy = editedBackup { data ->
            data.getJSONArray("customers").getJSONObject(0).remove("meterNumber")
            fun oldSettings(settings: JSONObject) { listOf("theme", "headerMode", "logo").forEach { settings.remove(it) } }
            oldSettings(data.getJSONObject("settings"))
            val oldSale = data.getJSONArray("sales").getJSONObject(0)
            oldSale.remove("checkoutSignature")
            oldSettings(oldSale.getJSONObject("shop"))
            val line = oldSale.getJSONArray("lines").getJSONObject(0)
            listOf("buyerName", "kwh", "customerId").forEach { line.remove(it) }
        }
        store.restoreBackup(legacy)
        assertEquals("", store.customers().single().meterNumber)
        assertEquals(ShopSettings(), store.settings())
        val line = store.sale(sale.id).lines.single()
        assertEquals("", line.buyerName)
        assertEquals("", line.kwh)
        assertNull(line.customerId)
        val restored = store.sale(sale.id)
        assertEquals(restored, store.checkout(listOf(line), store.customers().single(), 0, "cash", 100, sale.number))
        assertEquals(1, store.sales().size)
    }

    @Test fun invalidNewFieldsRejectWithoutChangingData() {
        val product = digital()
        val valid = CartLine(product, destination = "12345678", buyerName = "Alice", kwh = "1")
        val original = store.exportBackup()
        for (kwh in listOf("", "0", "-1", "1e2", "NaN", "1000000.001", "1.1234", " 1")) {
            rejected { store.checkout(listOf(valid.copy(kwh = kwh)), null, 0, "cash", 100) }
        }
        for (name in listOf("", "   ")) {
            rejected { store.checkout(listOf(valid.copy(buyerName = name)), null, 0, "cash", 100) }
        }
        rejected { store.checkout(listOf(valid.copy(buyerName = "x".repeat(201))), null, 0, "cash", 100) }
        rejected { store.checkout(listOf(valid.copy(customerId = 999)), null, 0, "cash", 100) }
        rejected { store.saveCustomer(Customer(name = "Alice", meterNumber = "invalid")) }
        for (settings in listOf(ShopSettings(theme = "invalid"), ShopSettings(headerMode = "invalid"),
            ShopSettings(headerMode = "logo"), ShopSettings(headerMode = "both"), ShopSettings(logo = "not base64"),
            ShopSettings(logo = Base64.getEncoder().encodeToString(ByteArray(24 * 1024 + 1))))) {
            rejected { store.saveSettings(settings) }
        }
        for ((offset, dimension) in listOf(16 to 385, 20 to 193, 16 to 0)) {
            val bytes = Base64.getDecoder().decode(logo())
            for (i in 0..3) bytes[offset + i] = (dimension ushr (24 - i * 8)).toByte()
            rejected { store.saveSettings(ShopSettings(logo = Base64.getEncoder().encodeToString(bytes))) }
        }
        assertEquals(original, store.exportBackup())
    }

    @Test fun backupOptionalFieldsRemainStrictlyTyped() {
        val product = digital()
        val sale = store.checkout(listOf(CartLine(product, destination = "12345678", buyerName = "Alice", kwh = "1")), null, 0, "cash", 100)
        val original = store.exportBackup()
        for (key in listOf("buyerName", "kwh", "customerId")) {
            for (bad in listOf<Any>(true, "invalid", -1, 1.5, JSONObject.NULL)) {
                if (key == "customerId" && bad === JSONObject.NULL) continue
                if (key != "customerId" && bad == "invalid") continue
                val malformed = editedBackup { it.getJSONArray("sales").getJSONObject(0).getJSONArray("lines").getJSONObject(0).put(key, bad) }
                rejected { store.restoreBackup(malformed) }
            }
        }
        for (key in listOf("theme", "headerMode", "logo")) {
            for (bad in listOf<Any>(123, true, JSONObject.NULL)) {
                rejected { store.restoreBackup(editedBackup { it.getJSONObject("settings").put(key, bad) }) }
                rejected { store.restoreBackup(editedBackup { it.getJSONArray("sales").getJSONObject(0).getJSONObject("shop").put(key, bad) }) }
            }
        }
        rejected { store.restoreBackup(editedBackup { it.getJSONArray("customers").getJSONObject(0).put("meterNumber", 12345678) }) }
        assertEquals(original, store.exportBackup())
        assertEquals(sale, store.sale(sale.id))
    }

    @Test fun truncatedPngBackupRejectsBeforeReplacingData() {
        val product = product()
        val sale = store.checkout(listOf(CartLine(product)), null, 0, "cash", 100)
        val original = store.exportBackup()
        val truncated = Base64.getEncoder().encodeToString(Base64.getDecoder().decode(logo()).copyOf(33))
        rejected { store.saveSettings(ShopSettings(logo = truncated)) }
        rejected { store.restoreBackup(editedBackup { it.getJSONObject("settings").put("logo", truncated) }) }
        rejected { store.restoreBackup(editedBackup { it.getJSONArray("sales").getJSONObject(0).getJSONObject("shop").put("logo", truncated) }) }
        assertEquals(original, store.exportBackup())
        assertEquals(sale, store.sale(sale.id))
    }

    @Test fun insufficientStockRollsBackEarlierLines() {
        val product = product()
        rejected {
            store.checkout(listOf(CartLine(product, 2), CartLine(product, 4)), null, 0, "cash", 600)
        }
        assertEquals(5L, store.products().single().stock)
        assertTrue(store.sales().isEmpty())

        val sale = store.checkout(listOf(CartLine(product, 2)), null, 0, "noncash", 200)
        assertEquals(3L, store.products().single().stock)
        assertEquals(200L, sale.total)
        assertEquals(Dashboard(200, 1), store.dashboard(0, Long.MAX_VALUE))
    }

    @Test fun retryingCheckoutRequestDoesNotDuplicateSaleOrStock() {
        val product = product()
        val request = java.util.UUID.randomUUID().toString()
        val first = store.checkout(listOf(CartLine(product, 2)), null, 0, "cash", 200, request)
        val retry = store.checkout(listOf(CartLine(product, 2)), null, 0, "cash", 200, request)
        rejected { store.checkout(listOf(CartLine(product)), null, 0, "cash", 100, request) }
        assertEquals(first, retry)
        assertEquals(1, store.sales().size)
        assertEquals(3L, store.products().single().stock)
    }

    @Test fun storageQuotaRejectsInsertWithoutChangingData() {
        val original = store.exportBackup()
        context.openOrCreateDatabase("tokopad.db", Context.MODE_PRIVATE, null).use { db ->
            val actual = db.rawQuery("SELECT bytes FROM storage_budget WHERE id = 1", null).use { c ->
                c.moveToFirst(); c.getLong(0)
            }
            val nearLimit = 28L * 1024 * 1024 - 1
            db.execSQL("UPDATE storage_budget SET bytes = ? WHERE id = 1", arrayOf(nearLimit))
            try {
                try {
                    store.saveProduct(Product(name = "Over quota", price = 100))
                    fail("Expected storage quota rejection")
                } catch (e: IllegalArgumentException) {
                    assertEquals("Local data limit reached; export backup before starting a new dataset", e.message)
                }
                assertTrue(store.products(true).isEmpty())
                assertEquals(original, store.exportBackup())
                db.rawQuery("SELECT bytes FROM storage_budget WHERE id = 1", null).use { c ->
                    c.moveToFirst(); assertEquals(nearLimit, c.getLong(0))
                }
            } finally {
                db.execSQL("UPDATE storage_budget SET bytes = ? WHERE id = 1", arrayOf(actual))
            }
        }
    }

    @Test fun cancellationRestoresDeactivatedProductExactlyOnce() {
        val product = product()
        val sale = store.checkout(listOf(CartLine(product, 2)), null, 0, "cash", 200)
        store.saveProduct(store.products().single().copy(active = false))

        store.cancelSale(sale.id, "Wrong order")
        assertEquals(5L, store.products(true).single().stock)
        assertTrue(store.products().isEmpty())
        val cancelled = store.sale(sale.id)
        assertNotNull(cancelled.cancelledAt)
        assertEquals("Wrong order", cancelled.cancellationReason)
        assertEquals(Dashboard(), store.dashboard(0, Long.MAX_VALUE))

        rejected { store.cancelSale(sale.id, "Again") }
        assertEquals(5L, store.products(true).single().stock)
        assertEquals(cancelled, store.sale(sale.id))
    }

    @Test fun dateRangesIncludeStartButExcludeEnd() {
        val product = product()
        val sale = store.checkout(listOf(CartLine(product)), null, 0, "cash", 100)
        assertTrue(store.sales(0, sale.createdAt).isEmpty())
        assertEquals(Dashboard(), store.dashboard(0, sale.createdAt))
        assertEquals(listOf(sale), store.sales(sale.createdAt, sale.createdAt + 1))
        assertEquals(Dashboard(100, 1), store.dashboard(sale.createdAt, sale.createdAt + 1))
        assertTrue(store.sales(sale.createdAt, sale.createdAt).isEmpty())
        assertEquals(Dashboard(), store.dashboard(sale.createdAt, sale.createdAt))
    }

    @Test fun priceChangesRejectStaleCartAndDoNotAlterReceipts() {
        val product = product()
        store.saveSettings(ShopSettings(shopName = "Original shop"))
        store.saveProduct(product.copy(price = 150, name = "Updated rice"))
        rejected { store.checkout(listOf(CartLine(product)), null, 0, "cash", 100) }
        assertEquals(5L, store.products().single().stock)
        assertTrue(store.sales().isEmpty())

        val fresh = store.products().single()
        val sale = store.checkout(listOf(CartLine(fresh)), null, 10, "cash", 150)
        store.saveProduct(store.products().single().copy(name = "Later name", price = 300))
        store.saveSettings(ShopSettings(shopName = "Later shop"))
        val receipt = store.sale(sale.id)
        assertEquals(sale, receipt)
        assertEquals("Updated rice", receipt.lines.single().product.name)
        assertEquals(150L, receipt.lines.single().product.price)
        assertEquals(140L, receipt.total)
        assertEquals("Original shop", receipt.shop.shopName)
    }

    @Test fun restoreReplacesDatasetButInvalidChecksumChangesNothing() {
        val product = product()
        val sale = store.checkout(listOf(CartLine(product)), null, 0, "noncash", 100)
        val original = store.exportBackup()
        Store.validateBackup(original)

        store.saveProduct(store.products().single().copy(name = "Changed", price = 300))
        store.saveCustomer(Customer(name = "Extra customer"))
        store.saveSettings(ShopSettings(shopName = "Changed shop"))
        val beforeInvalidRestore = store.exportBackup()
        val corrupt = JSONObject(original).put("sha256", "0".repeat(64)).toString()
        rejected { store.restoreBackup(corrupt) }
        assertEquals(beforeInvalidRestore, store.exportBackup())

        store.restoreBackup(original)
        assertEquals("Rice", store.products().single().name)
        assertEquals(4L, store.products().single().stock)
        assertTrue(store.customers().isEmpty())
        assertEquals(ShopSettings(), store.settings())
        assertEquals(sale, store.sale(sale.id))
        assertEquals(original, store.exportBackup())
    }

    @Test fun deletingCustomerUnlinksProfileButPreservesSnapshots() {
        val product = product()
        val customer = Customer(name = "Alice", phone = "0012345")
            .let { it.copy(id = store.saveCustomer(it)) }
        val sale = store.checkout(listOf(CartLine(product)), customer, 0, "cash", 100)
        assertEquals(listOf(sale), store.salesForCustomer(customer.id))

        store.deleteCustomer(customer.id)
        assertTrue(store.customers().isEmpty())
        assertTrue(store.salesForCustomer(customer.id).isEmpty())
        val receipt = store.sale(sale.id)
        assertNull(receipt.customerId)
        assertEquals("Alice", receipt.customerName)
        assertEquals("0012345", receipt.customerPhone)
        assertEquals(sale.copy(customerId = null), receipt)

        rejected { store.checkout(listOf(CartLine(product)), customer, 0, "cash", 100) }
        val detached = store.checkout(listOf(CartLine(product)), null, 0, "cash", 100)
        assertNull(detached.customerId)
        assertEquals("", detached.customerName)
        val backup = store.exportBackup()
        Store.validateBackup(backup)
        store.restoreBackup(backup)
        assertEquals(receipt, store.sale(sale.id))
        assertEquals(detached, store.sale(detached.id))
    }
}
