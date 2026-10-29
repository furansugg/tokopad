package com.tokopad.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

        val detached = store.checkout(listOf(CartLine(product)), customer, 0, "cash", 100)
        assertNull(detached.customerId)
        assertEquals("Alice", detached.customerName)
        val backup = store.exportBackup()
        Store.validateBackup(backup)
        store.restoreBackup(backup)
        assertEquals(receipt, store.sale(sale.id))
        assertEquals(detached, store.sale(detached.id))
    }
}
