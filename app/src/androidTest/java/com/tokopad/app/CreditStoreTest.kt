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
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CreditStoreTest {
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

    private fun request() = UUID.randomUUID().toString()

    private fun product(trackStock: Boolean = true): Product = Product(name = "Rice", price = 100, trackStock = trackStock, stock = 20)
        .let { it.copy(id = store.saveProduct(it)) }

    private fun customer(name: String = "Alice", phone: String = ""): Customer = Customer(name = name, phone = phone)
        .let { it.copy(id = store.saveCustomer(it)) }

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Expected input rejection")
        } catch (_: IllegalArgumentException) {
            // Assertion failures and database errors must still fail the test.
        }
    }

    private fun editedBackup(edit: (JSONObject) -> Unit): String {
        val envelope = JSONObject(store.exportBackup())
        val data = JSONObject(envelope.getString("payload"))
        edit(data)
        val payload = data.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return envelope.put("payload", payload).put("sha256", digest).toString()
    }

    @Test fun zeroAndPartialCreditPreserveInitialPaymentAndUseDiscountedBalance() {
        val product = product()
        val buyer = customer()
        val zero = store.checkout(listOf(CartLine(product)), buyer, 10, "cash", 0, isCredit = true)
        val partial = store.checkout(listOf(CartLine(product, 2)), buyer, 20, "noncash", 50, isCredit = true)
        assertTrue(zero.isCredit)
        assertEquals(90L, zero.total)
        assertEquals(0L, zero.paid)
        assertEquals(90L, zero.balance)
        assertEquals(0L, zero.received)
        assertNull(zero.settlement)
        assertEquals(180L, partial.total)
        assertEquals(50L, partial.paid)
        assertEquals(130L, partial.balance)
        assertEquals(50L, partial.received)
        assertEquals(17L, store.products().single().stock)
        assertEquals(Dashboard(270, 2, 50, 220), store.dashboard(0, Long.MAX_VALUE))
        assertEquals(listOf(partial, zero).sortedWith(compareByDescending<Sale> { it.createdAt }.thenByDescending { it.id }),
            store.outstandingForCustomer(buyer.id))
        rejected { store.deleteCustomer(buyer.id) }
        assertEquals(buyer, store.customers().single())
    }

    @Test fun invalidCreditRollsBackStockAndNeverCreatesCustomersOrSales() {
        val product = product()
        val buyer = customer()
        val deleted = customer("Deleted")
        store.deleteCustomer(deleted.id)
        val original = store.exportBackup()
        val invalid = listOf<() -> Unit>(
            { store.checkout(listOf(CartLine(product)), null, 0, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), Customer(name = "Unsaved"), 0, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer.copy(id = 999), 0, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), deleted, 0, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 100, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 101, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 0, "cash", -1, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 100, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 0, "noncash", 101, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 0, "credit", 10, isCredit = true) },
            { store.checkout(listOf(CartLine(product, 2), CartLine(product, 19)), buyer, 0, "cash", 0, isCredit = true) },
            { store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 0) },
        )
        for (attempt in invalid) {
            rejected(attempt)
            assertEquals(original, store.exportBackup())
            assertEquals(20L, store.products().single().stock)
            assertTrue(store.sales().isEmpty())
            assertEquals(listOf(buyer), store.customers())
        }
        val digital = Product(name = "Data", price = 100, kind = ProductKind.DATA, trackStock = true, stock = 5)
            .let { it.copy(id = store.saveProduct(it)) }
        val beforeDigital = store.exportBackup()
        val line = CartLine(digital, destination = "08123456", buyerName = "New recipient")
        rejected { store.checkout(listOf(CartLine(product), line), null, 0, "cash", 0, isCredit = true) }
        assertEquals(beforeDigital, store.exportBackup())
    }

    @Test fun settlementRejectsPartialOverpaymentInvalidMethodAndConflictingRequestsAtomically() {
        val product = product()
        val buyer = customer()
        val open = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 30, isCredit = true)
        val other = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 30, isCredit = true)
        val paid = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 100)
        val original = store.exportBackup()
        for (amount in listOf(-1L, 0L, 1L, 69L, 71L, Long.MAX_VALUE)) {
            rejected { store.settleSale(open.id, amount, "cash", request()) }
            assertEquals(original, store.exportBackup())
        }
        for (method in listOf("", "credit", "Cash")) {
            rejected { store.settleSale(open.id, 70, method, request()) }
        }
        for (id in listOf("", "not a UUID", "1-1-1-1-1")) {
            rejected { store.settleSale(open.id, 70, "cash", id) }
        }
        rejected { store.settleSale(999, 70, "cash", request()) }
        rejected { store.settleSale(paid.id, 100, "cash", request()) }
        assertEquals(original, store.exportBackup())
        val request = request()
        val settled = store.settleSale(open.id, 70, "noncash", request)
        assertEquals(30L, settled.paid)
        assertEquals(0L, settled.balance)
        assertEquals(100L, settled.received)
        assertEquals(70L, settled.settlement!!.amount)
        assertEquals("noncash", settled.settlement!!.payment)
        assertEquals(request, settled.settlement!!.requestId)
        assertTrue(settled.settlement!!.createdAt >= settled.createdAt)
        assertEquals(settled, store.settleSale(open.id, 70, "noncash", request))
        val afterSettlement = store.exportBackup()
        rejected { store.settleSale(open.id, 69, "noncash", request) }
        rejected { store.settleSale(open.id, 70, "cash", request) }
        rejected { store.settleSale(other.id, 70, "noncash", request) }
        rejected { store.settleSale(open.id, 70, "noncash", request()) }
        assertEquals(afterSettlement, store.exportBackup())
        store.restoreBackup(afterSettlement)
        store.close()
        store = Store(context)
        rejected { store.settleSale(other.id, 70, "noncash", request) }
        assertEquals(settled, store.settleSale(open.id, 70, "noncash", request))
        assertEquals(afterSettlement, store.exportBackup())
        assertEquals(listOf(other), store.outstandingForCustomer(buyer.id))
        assertEquals(17L, store.products().single().stock)
    }

    @Test fun checkoutAndSettlementRetriesSurviveBackupAndDatabaseReopen() {
        val product = product()
        val buyer = customer()
        val lines = listOf(CartLine(product, 2))
        val checkoutRequest = request()
        val settlementRequest = request()
        val open = store.checkout(lines, buyer, 10, "cash", 40, checkoutRequest, true)
        assertEquals(open, store.checkout(lines, buyer, 10, "cash", 40, checkoutRequest, true))
        rejected { store.checkout(lines, buyer, 10, "cash", 40, checkoutRequest, false) }
        val openBackup = store.exportBackup()
        assertEquals(3, JSONObject(openBackup).getInt("version"))
        store.restoreBackup(openBackup)
        store.close()
        store = Store(context)
        assertEquals(open, store.sale(open.id))
        assertEquals(open, store.checkout(lines, buyer, 10, "cash", 40, checkoutRequest, true))
        val settled = store.settleSale(open.id, 150, "cash", settlementRequest)
        repeat(2) {
            assertEquals(settled, store.settleSale(open.id, 150, "cash", settlementRequest))
            assertEquals(settled, store.checkout(lines, buyer, 10, "cash", 40, checkoutRequest, true))
            assertEquals(40L, store.sale(open.id).paid)
            val backup = store.exportBackup()
            Store.validateBackup(backup)
            store.restoreBackup(backup)
            assertEquals(backup, store.exportBackup())
            store.close()
            store = Store(context)
        }
        assertEquals(settled, store.settleSale(open.id, 150, "cash", settlementRequest))
        assertEquals(settled, store.checkout(lines, buyer, 10, "cash", 40, checkoutRequest, true))
        rejected { store.settleSale(open.id, 150, "noncash", settlementRequest) }
        assertEquals(1, store.sales().size)
        assertEquals(18L, store.products().single().stock)
        assertTrue(store.outstandingForCustomer(buyer.id).isEmpty())
        assertEquals(Dashboard(190, 1, 190, 0), store.dashboard(0, Long.MAX_VALUE))
        store.deleteCustomer(buyer.id)
        assertNull(store.sale(open.id).customerId)
        store.restoreBackup(store.exportBackup())
        assertEquals(settled.copy(customerId = null), store.sale(open.id))
    }

    @Test fun legacyVersionTwoCreditRestorePreservesBalancesSettlementsAndRetries() {
        val product = product()
        val buyer = customer()
        val lines = listOf(CartLine(product))
        val openRequest = request()
        val settledRequest = request()
        val settlementRequest = request()
        val open = store.checkout(lines, buyer, 0, "cash", 30, openRequest, true)
        val settledSale = store.checkout(lines, buyer, 0, "cash", 40, settledRequest, true)
        val settled = store.settleSale(settledSale.id, 60, "noncash", settlementRequest)
        val legacy = editedBackup { data ->
            data.getJSONObject("settings").remove("qrContent")
            val sales = data.getJSONArray("sales")
            for (i in 0 until sales.length()) sales.getJSONObject(i).getJSONObject("shop").remove("qrContent")
        }
        store.restoreBackup(JSONObject(legacy).put("version", 2).toString())
        assertEquals(ShopSettings(), store.settings())
        assertEquals(open, store.sale(open.id))
        assertEquals(settled, store.sale(settled.id))
        assertEquals(open, store.checkout(lines, buyer, 0, "cash", 30, openRequest, true))
        assertEquals(settled, store.checkout(lines, buyer, 0, "cash", 40, settledRequest, true))
        assertEquals(settled, store.settleSale(settled.id, 60, "noncash", settlementRequest))
        assertEquals(listOf(open), store.outstandingForCustomer(buyer.id))
        assertEquals(Dashboard(200, 2, 130, 70), store.dashboard(0, Long.MAX_VALUE))
        assertEquals(18L, store.products().single().stock)
        val upgraded = store.exportBackup()
        assertEquals(3, JSONObject(upgraded).getInt("version"))
        store.restoreBackup(upgraded)
        assertEquals(open, store.sale(open.id))
        assertEquals(settled, store.sale(settled.id))
        assertEquals(upgraded, store.exportBackup())
    }

    @Test fun cancellationOfOpenAndSettledCreditRestoresStockAndPermitsDeletion() {
        val product = product()
        for (settle in listOf(false, true)) {
            val buyer = customer(if (settle) "Settled" else "Open")
            val open = store.checkout(listOf(CartLine(product, 2)), buyer, 0, "noncash", 50, isCredit = true)
            rejected { store.deleteCustomer(buyer.id) }
            val beforeCancel = if (settle) store.settleSale(open.id, 150, "cash", request()) else open
            assertEquals(18L, store.products().single().stock)
            assertEquals(Dashboard(200, 1, if (settle) 200 else 50, if (settle) 0 else 150), store.dashboard(0, Long.MAX_VALUE))
            store.cancelSale(open.id, "Correction")
            val cancelled = store.sale(open.id)
            assertNotNull(cancelled.cancelledAt)
            assertEquals(0L, cancelled.balance)
            assertEquals(50L, cancelled.paid)
            assertEquals(beforeCancel.settlement, cancelled.settlement)
            assertEquals(beforeCancel.received, cancelled.received)
            assertEquals(20L, store.products().single().stock)
            assertTrue(store.outstandingForCustomer(buyer.id).isEmpty())
            assertEquals(Dashboard(), store.dashboard(0, Long.MAX_VALUE))
            val afterCancel = store.exportBackup()
            rejected { store.cancelSale(open.id, "Again") }
            rejected { store.settleSale(open.id, 150, "cash", request()) }
            assertEquals(afterCancel, store.exportBackup())
            store.deleteCustomer(buyer.id)
            assertNull(store.sale(open.id).customerId)
            val backup = store.exportBackup()
            Store.validateBackup(backup)
            store.restoreBackup(backup)
            assertEquals(cancelled.copy(customerId = null), store.sale(open.id))
        }
    }

    @Test fun outstandingUsesResponsibleCustomerNotDigitalRecipient() {
        val product = Product(name = "Data", price = 100, kind = ProductKind.DATA)
            .let { it.copy(id = store.saveProduct(it)) }
        val payer = customer("Payer")
        val recipient = customer("Recipient", "08123456")
        val line = CartLine(product, destination = recipient.phone, buyerName = recipient.name, customerId = recipient.id)
        val original = store.exportBackup()
        rejected { store.checkout(listOf(line), null, 0, "cash", 0, isCredit = true) }
        assertEquals(original, store.exportBackup())
        val debt = store.checkout(listOf(line), payer, 0, "cash", 0, isCredit = true)
        assertEquals(payer.id, debt.customerId)
        assertEquals(recipient.id, debt.lines.single().customerId)
        assertEquals(listOf(debt), store.salesForCustomer(recipient.id))
        assertEquals(listOf(debt), store.outstandingForCustomer(payer.id))
        assertTrue(store.outstandingForCustomer(recipient.id).isEmpty())
        store.deleteCustomer(recipient.id)
        rejected { store.deleteCustomer(payer.id) }
        store.restoreBackup(store.exportBackup())
        assertEquals(listOf(debt), store.outstandingForCustomer(payer.id))
        assertTrue(store.outstandingForCustomer(recipient.id).isEmpty())
    }

    @Test fun cancellationAfterClockRollbackCannotPrecedeRestoredSettlement() {
        val product = product()
        val buyer = customer()
        val open = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 30, isCredit = true)
        store.settleSale(open.id, 70, "noncash", request())
        val future = System.currentTimeMillis() + 86_400_000
        val backup = editedBackup { data ->
            data.getJSONArray("sales").getJSONObject(0).getJSONObject("settlement").put("createdAt", future)
        }
        store.restoreBackup(backup)
        assertEquals(future, store.sale(open.id).settlement!!.createdAt)
        store.cancelSale(open.id, "Correction after clock rollback")
        val cancelled = store.sale(open.id)
        assertTrue(cancelled.cancelledAt!! >= future)
        assertEquals(future, cancelled.settlement!!.createdAt)
        assertEquals(30L, cancelled.paid)
        assertEquals(0L, cancelled.balance)
        assertEquals(20L, store.products().single().stock)
        assertEquals(Dashboard(), store.dashboard(0, Long.MAX_VALUE))
        assertEquals(Dashboard(), store.dashboard(future, future + 1))
        val cancelledBackup = store.exportBackup()
        Store.validateBackup(cancelledBackup)
        store.restoreBackup(cancelledBackup)
        store.close()
        store = Store(context)
        assertEquals(cancelled, store.sale(open.id))
        store.deleteCustomer(buyer.id)
        Store.validateBackup(store.exportBackup())
    }

    @Test fun outstandingIncludesAllDebtsBeyondLatestFiveHundredHistoryRows() {
        val product = product(trackStock = false)
        val buyer = customer()
        val debts = List(503) { store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 0, isCredit = true) }
        store.settleSale(debts.last().id, 100, "cash", request())
        store.cancelSale(debts[501].id, "Correction")
        store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 100)
        val expected = debts.take(501).sortedWith(compareByDescending<Sale> { it.createdAt }.thenByDescending { it.id })
        assertEquals(500, store.salesForCustomer(buyer.id).size)
        assertFalse(store.salesForCustomer(buyer.id).any { it.id == debts.first().id })
        assertEquals(501, store.outstandingForCustomer(buyer.id).size)
        assertEquals(expected, store.outstandingForCustomer(buyer.id))
        assertEquals(expected, store.outstandingSales())
        assertEquals(Dashboard(50_300, 503, 200, 50_100), store.dashboard(0, Long.MAX_VALUE))
        assertEquals(Dashboard(0, 0, 0, 50_100), store.dashboard(0, 0))
        store.restoreBackup(store.exportBackup())
        store.close()
        store = Store(context)
        assertEquals(expected, store.outstandingForCustomer(buyer.id))
        assertEquals(expected, store.outstandingSales())
        rejected { store.deleteCustomer(buyer.id) }
    }

    @Test fun outstandingSalesIncludesAllDatesAndCustomersButNotSettledOrCancelled() {
        val product = product(trackStock = false)
        val first = customer("First")
        val second = customer("Second")
        assertTrue(store.outstandingSales().isEmpty())
        val old = store.checkout(listOf(CartLine(product)), first, 0, "cash", 0, isCredit = true)
        val newer = store.checkout(listOf(CartLine(product)), second, 0, "cash", 30, isCredit = true)
        val sameTime = store.checkout(listOf(CartLine(product)), first, 0, "cash", 20, isCredit = true)
        val settled = store.checkout(listOf(CartLine(product)), second, 0, "cash", 0, isCredit = true)
        val cancelled = store.checkout(listOf(CartLine(product)), first, 0, "cash", 0, isCredit = true)
        store.checkout(listOf(CartLine(product)), first, 0, "cash", 100)
        store.settleSale(settled.id, 100, "cash", request())
        store.cancelSale(cancelled.id, "Correction")
        store.restoreBackup(editedBackup { data ->
            val sales = data.getJSONArray("sales")
            for (index in 0 until sales.length()) {
                val sale = sales.getJSONObject(index)
                if (sale.getLong("id") == old.id) sale.put("createdAt", 1000)
                if (sale.getLong("id") == newer.id || sale.getLong("id") == sameTime.id) sale.put("createdAt", 2000)
            }
        })
        val expected = listOf(store.sale(sameTime.id), store.sale(newer.id), store.sale(old.id))
        assertEquals(expected, store.outstandingSales())
        store.close()
        store = Store(context)
        assertEquals(expected, store.outstandingSales())
    }

    @Test fun dashboardSeparatesSaleDateSettlementDateAndAllTimeOutstanding() {
        val product = product()
        val buyer = customer()
        val debt = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 30, isCredit = true)
        store.settleSale(debt.id, 70, "noncash", request())
        val open = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 0, isCredit = true)
        store.checkout(listOf(CartLine(product)), null, 0, "cash", 150)
        val cancelled = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 25, isCredit = true)
        store.settleSale(cancelled.id, 75, "cash", request())
        store.cancelSale(cancelled.id, "Correction")
        val dated = editedBackup { data ->
            val sales = data.getJSONArray("sales")
            for (index in 0 until sales.length()) {
                val sale = sales.getJSONObject(index)
                sale.put("createdAt", if (sale.getLong("id") == open.id) 2000 else 1000)
                if (!sale.isNull("settlement")) sale.getJSONObject("settlement").put("createdAt", 3000)
                if (!sale.isNull("cancelledAt")) sale.put("cancelledAt", 4000)
            }
        }
        store.restoreBackup(dated)
        assertEquals(Dashboard(200, 2, 130, 100), store.dashboard(1000, 2000))
        assertEquals(Dashboard(100, 1, 0, 100), store.dashboard(2000, 3000))
        assertEquals(Dashboard(0, 0, 70, 100), store.dashboard(3000, 3001))
        assertEquals(Dashboard(0, 0, 0, 100), store.dashboard(4000, 5000))
        assertEquals(Dashboard(0, 0, 0, 100), store.dashboard(3000, 3000))
        assertEquals(Dashboard(300, 3, 200, 100), store.dashboard(0, Long.MAX_VALUE))
        store.close()
        store = Store(context)
        assertEquals(Dashboard(0, 0, 70, 100), store.dashboard(3000, 3001))
    }

    @Test fun settlementStorageFailureRollsBackPaymentAndRetryIdentity() {
        val product = product()
        val buyer = customer()
        val open = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 30, isCredit = true)
        val original = store.exportBackup()
        val request = request()
        context.openOrCreateDatabase("tokopad.db", Context.MODE_PRIVATE, null).use { db ->
            val actual = db.rawQuery("SELECT bytes FROM storage_budget WHERE id = 1", null).use { c -> c.moveToFirst(); c.getLong(0) }
            val nearLimit = 28L * 1024 * 1024 - 1
            db.execSQL("UPDATE storage_budget SET bytes = ? WHERE id = 1", arrayOf(nearLimit))
            try {
                rejected { store.settleSale(open.id, 70, "noncash", request) }
                assertEquals(original, store.exportBackup())
                assertEquals(open, store.sale(open.id))
                assertEquals(Dashboard(100, 1, 30, 70), store.dashboard(0, Long.MAX_VALUE))
                db.rawQuery("SELECT bytes FROM storage_budget WHERE id = 1", null).use { c -> c.moveToFirst(); assertEquals(nearLimit, c.getLong(0)) }
            } finally {
                db.execSQL("UPDATE storage_budget SET bytes = ? WHERE id = 1", arrayOf(actual))
            }
        }
        val settled = store.settleSale(open.id, 70, "noncash", request)
        assertEquals(100L, settled.received)
        assertEquals(19L, store.products().single().stock)
        assertEquals(settled, store.settleSale(open.id, 70, "noncash", request))
    }

    @Test fun corruptCreditBackupsRejectBeforeReplacingAnyData() {
        val product = product()
        val buyer = customer()
        val open = store.checkout(listOf(CartLine(product)), buyer, 0, "cash", 30, isCredit = true)
        val original = store.exportBackup()
        val corruptions = listOf<(JSONObject) -> Unit>(
            { it.put("customerId", JSONObject.NULL) },
            { it.put("customerId", 999) },
            { it.put("paid", -1) },
            { it.put("paid", 100) },
            { it.put("payment", "credit") },
            { it.put("isCredit", "true") },
            { it.put("settlement", JSONObject().put("amount", 69).put("payment", "cash").put("createdAt", open.createdAt).put("requestId", request())) },
        )
        for (corrupt in corruptions) {
            rejected { store.restoreBackup(editedBackup { corrupt(it.getJSONArray("sales").getJSONObject(0)) }) }
            assertEquals(original, store.exportBackup())
            assertEquals(open, store.sale(open.id))
            assertEquals(19L, store.products().single().stock)
        }
        rejected { store.restoreBackup(JSONObject(original).put("version", 1).toString()) }
        assertEquals(original, store.exportBackup())
        val settled = store.settleSale(open.id, 70, "cash", request())
        val settledBackup = store.exportBackup()
        for ((key, bad) in listOf("amount" to 71, "payment" to "credit", "createdAt" to open.createdAt - 1, "requestId" to "invalid")) {
            rejected { store.restoreBackup(editedBackup { it.getJSONArray("sales").getJSONObject(0).getJSONObject("settlement").put(key, bad) }) }
            assertEquals(settledBackup, store.exportBackup())
            assertEquals(settled, store.sale(open.id))
        }
    }
}
