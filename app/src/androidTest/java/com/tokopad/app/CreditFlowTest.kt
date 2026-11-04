package com.tokopad.app

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CreditFlowTest {
    private lateinit var responsible: Customer
    private lateinit var recipient: Customer
    private lateinit var zero: Sale
    private lateinit var partial: Sale
    private lateinit var seededProducts: List<Product>

    // The outer rule seeds before launch and deletes only after MainActivity has closed its Store.
    @get:Rule(order = 0)
    val creditShop = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase("tokopad.db")
            Store(context).use { store ->
                store.saveSettings(ShopSettings(shopName = "Credit Flow Store", language = "en", theme = "light"))
                val notebook = Product(name = "Pocket notebook", price = 18_000, stock = 24, trackStock = true)
                    .let { it.copy(id = store.saveProduct(it)) }
                val data = Product(name = "Internet 10 GB", price = 35_000, kind = ProductKind.DATA,
                    provider = "Telkomsel", packageName = "10 GB / 30 days")
                    .let { it.copy(id = store.saveProduct(it)) }
                responsible = Customer(name = "Nadia Responsible", phone = "081234567890")
                    .let { it.copy(id = store.saveCustomer(it)) }
                recipient = Customer(name = "Rani Recipient", phone = "089876543210")
                    .let { it.copy(id = store.saveCustomer(it)) }
                zero = store.checkout(listOf(CartLine(notebook, 2)), responsible, 0, "cash", 0, isCredit = true)
                partial = store.checkout(listOf(CartLine(data, destination = recipient.phone,
                    buyerName = recipient.name, customerId = recipient.id)), responsible, 0, "noncash", 10_000, isCredit = true)

                // Use the public restore path to put both sales outside today's dashboard range.
                val envelope = JSONObject(store.exportBackup())
                val payload = JSONObject(envelope.getString("payload"))
                val yesterday = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val sales = payload.getJSONArray("sales")
                for (index in 0 until sales.length()) sales.getJSONObject(index).put("createdAt", yesterday)
                val text = payload.toString()
                val checksum = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                store.restoreBackup(envelope.put("payload", text).put("sha256", checksum).toString())
                zero = store.sale(zero.id)
                partial = store.sale(partial.id)
                seededProducts = store.products(true)
                Store.validateBackup(store.exportBackup())
            }
        }

        override fun after() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            instrumentation.targetContext.deleteDatabase("tokopad.db")
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun customerDebt_settlesZeroAndPartialCredit_updatesReceiptDashboardAndBackup() {
        waitForText("Overview")
        dashboardTag("dashboard-received").assertTextContains(money(0, "en"))
        dashboardTag("dashboard-outstanding").assertTextContains(money(61_000, "en"))
        readStore { store ->
            assertEquals(Dashboard(71_000, 2, 10_000, 61_000), store.dashboard(0, Long.MAX_VALUE))
            assertEquals(0L, zero.paid)
            assertEquals(10_000L, partial.paid)
            assertEquals(responsible.id, partial.customerId)
            assertEquals(recipient.id, partial.lines.single().customerId)
            assertNull(zero.settlement)
            assertNull(partial.settlement)
        }

        compose.onNodeWithTag("nav-more").performClick()
        waitForText("Workspace")
        compose.onNodeWithText("Customers").performScrollTo().performClick()
        waitForText("Search customers")
        openCustomerHistory(recipient)
        compose.onNodeWithText(money(0, "en")).assertIsDisplayed()
        compose.onNodeWithTag("customer-debt-${partial.id}").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()

        openCustomerHistory(responsible)
        compose.onNodeWithText(money(61_000, "en")).assertIsDisplayed()
        compose.onNodeWithTag("customer-debt-${zero.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("customer-debt-${partial.id}").performScrollTo().assertIsDisplayed()
        screenshot("credit-customer-history")
        settle(zero, "Owing", "noncash", "Noncash")

        openCustomerHistory(responsible)
        compose.onNodeWithText(money(25_000, "en")).assertIsDisplayed()
        compose.onNodeWithTag("customer-debt-${zero.id}").assertDoesNotExist()
        compose.onNodeWithTag("customer-debt-${partial.id}").performScrollTo().assertIsDisplayed()
        settle(partial, "Partially paid", "cash", "Cash")

        openCustomerHistory(responsible)
        compose.onNodeWithText(money(0, "en")).assertIsDisplayed()
        compose.onNodeWithTag("customer-debt-${zero.id}").assertDoesNotExist()
        compose.onNodeWithTag("customer-debt-${partial.id}").assertDoesNotExist()
        screenshot("credit-customer-history-paid")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("nav-dashboard").performClick()
        waitForText("Overview")
        dashboardTag("dashboard-received").assertTextContains(money(61_000, "en"))
        dashboardTag("dashboard-outstanding").assertTextContains(money(0, "en"))
        screenshot("credit-dashboard-paid")
        readStore { store ->
            assertTrue(store.outstandingForCustomer(responsible.id).isEmpty())
            assertTrue(store.outstandingForCustomer(recipient.id).isEmpty())
            assertEquals(Dashboard(71_000, 2, 71_000, 0), store.dashboard(0, Long.MAX_VALUE))
            assertEquals(2, store.sales().size)
            assertEquals(seededProducts, store.products(true))
            assertEquals(22L, store.products().single { it.trackStock }.stock)
            val requestIds = listOf(zero, partial).map { requireNotNull(store.sale(it.id).settlement).requestId }
            assertEquals(2, requestIds.toSet().size)
            Store.validateBackup(store.exportBackup())
        }
    }

    private fun openCustomerHistory(customer: Customer) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Search customers"))
        compose.onNodeWithText("Search customers").performScrollTo().performTextReplacement(customer.name)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Transactions"))
        compose.onNodeWithText("Transactions").performScrollTo().performClick()
        waitForText("Outstanding debt (all dates)")
    }

    private fun settle(original: Sale, status: String, method: String, label: String) {
        compose.onNodeWithTag("customer-debt-${original.id}").performScrollTo().performClick()
        waitForTagText("sale-payment-status", "Payment status: $status")
        compose.onNodeWithTag("sale-balance").performScrollTo().assertTextContains(money(original.balance, "en"))
        compose.onNodeWithTag("sale-settle").performScrollTo().performClick()
        compose.onNodeWithTag("settlement-amount").performScrollTo().assertTextContains(money(original.balance, "en"))
        compose.onNodeWithTag("settlement-$method").performScrollTo().performClick().assertIsSelected()
        readStore { store -> assertEquals(original, store.sale(original.id)) }
        screenshot("credit-settlement-confirmation-$method")
        compose.onNodeWithTag("settlement-confirm").performClick()
        waitForTagText("sale-payment-status", "Payment status: Paid")
        compose.onNodeWithTag("sale-balance").performScrollTo().assertTextContains(money(0, "en"))
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
        compose.onNodeWithTag("sale-settlement").performScrollTo()
            .assertTextContains(money(original.balance, "en")).assertTextContains(label)
        screenshot("credit-receipt-settled-$method")
        readStore { store ->
            val settled = store.sale(original.id)
            val settlement = requireNotNull(settled.settlement)
            assertEquals(original, settled.copy(settlement = null))
            assertEquals(original.balance, settlement.amount)
            assertEquals(method, settlement.payment)
            assertTrue(settlement.createdAt >= original.createdAt)
            assertNotNull(UUID.fromString(settlement.requestId))
            assertEquals(0L, settled.balance)
            assertEquals(original.total, settled.received)
            assertEquals(setOf(zero.id, partial.id), store.sales().map { it.id }.toSet())
            assertEquals(seededProducts, store.products(true))
            Store.validateBackup(store.exportBackup())
        }
        compose.onNodeWithText("Close").performClick()
    }

    private fun dashboardTag(tag: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }

    private fun readStore(check: (Store) -> Unit) {
        Store(InstrumentationRegistry.getInstrumentation().targetContext).use(check)
    }

    private fun notBusy(): Boolean =
        compose.onAllNodesWithTag("busy-dialog").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithText("Working…").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithText("Sedang diproses…").fetchSemanticsNodes().isEmpty()

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 && notBusy()
        }
        compose.waitForIdle()
    }

    private fun waitForTagText(tag: String, text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(text)).fetchSemanticsNodes().size == 1 && notBusy()
        }
        compose.waitForIdle()
    }

    private fun screenshot(screen: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val context = instrumentation.targetContext
        val file = File(requireNotNull(context.getExternalFilesDir(null)), "screenshots/tokopad-$screen.png")
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Cannot create screenshot directory" }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Cannot capture $screen" }
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot save $screen screenshot" }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
