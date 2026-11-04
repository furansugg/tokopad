package com.tokopad.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepeatPurchaseTest {
    private lateinit var buyer: Customer
    private lateinit var electricity: Product
    private lateinit var original: Sale
    private lateinit var multiLine: Sale
    private lateinit var nonElectricity: Sale
    private lateinit var unavailable: Sale

    @get:Rule(order = 0)
    val shop = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase("tokopad.db")
            Store(context).use { store ->
                store.saveSettings(ShopSettings(shopName = "Repeat Store", language = "en", theme = "light"))
                buyer = Customer(name = "Rina Repeat", phone = "08123456789", meterNumber = "00123456789")
                    .let { it.copy(id = store.saveCustomer(it)) }
                electricity = Product(name = "PLN repeat", price = 50_000, kind = ProductKind.ELECTRICITY, requiresCode = false)
                    .let { it.copy(id = store.saveProduct(it)) }
                val data = Product(name = "Data package", price = 20_000, kind = ProductKind.DATA)
                    .let { it.copy(id = store.saveProduct(it)) }
                val inactive = Product(name = "PLN inactive", price = 40_000, kind = ProductKind.ELECTRICITY)
                    .let { it.copy(id = store.saveProduct(it)) }
                val line = CartLine(electricity, destination = buyer.meterNumber, code = "OLD TOKEN",
                    note = "old private note", buyerName = buyer.name, kwh = "36.5", customerId = buyer.id)
                original = store.checkout(listOf(line), buyer, 0, "noncash", 50_000)
                multiLine = store.checkout(listOf(line, CartLine(data, destination = buyer.phone)), buyer, 0, "noncash", 70_000)
                nonElectricity = store.checkout(listOf(CartLine(data, destination = buyer.phone)), buyer, 0, "noncash", 20_000)
                unavailable = store.checkout(listOf(line.copy(product = inactive)), buyer, 0, "noncash", 40_000)
                electricity = electricity.copy(price = 55_000)
                store.saveProduct(electricity)
                store.saveProduct(inactive.copy(active = false))
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
    fun repeatFromHistory_prefillsAndCompletesNewSaleWithoutChangingOriginal() {
        openReceipt(original)
        compose.onNodeWithTag("sale-buy-again").performScrollTo().performClick()
        waitForTag("digital-buyer-name")
        compose.onNodeWithText(money(electricity.price, "en")).performScrollTo()
        compose.onNodeWithTag("digital-buyer-name").performScrollTo().assertTextContains(buyer.name)
        compose.onNodeWithTag("digital-destination").performScrollTo().assertTextContains(buyer.meterNumber)
        compose.onNodeWithTag("digital-kwh").performScrollTo().assertTextContains("36.5")
        compose.onNodeWithTag("digital-code").performScrollTo().assert(editableText(""))
        compose.onNodeWithText("Note (optional)").performScrollTo().assert(editableText(""))
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithTag("digital-code").performTextReplacement("NEW TOKEN")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        waitForTag("checkout-cart")
        compose.onNodeWithTag("checkout-cart").performClick()
        screenTag("checkout-customer").assertTextContains(buyer.name)
        screenTag("checkout-payment-noncash").performClick()
        screenTag("checkout-review").assertIsEnabled().performClick()
        compose.onNodeWithTag("checkout-complete").assertIsEnabled().performClick()
        waitForText("Receipt")
        readStore { store ->
            val sales = store.sales()
            assertEquals(5, sales.size)
            assertEquals(original, store.sale(original.id))
            val sale = sales.single { it.id !in setOf(original.id, multiLine.id, nonElectricity.id, unavailable.id) }
            assertEquals(buyer.id, sale.customerId)
            assertEquals("noncash", sale.payment)
            assertEquals(electricity.price, sale.total)
            val line = sale.lines.single()
            assertEquals(electricity, line.product)
            assertEquals(buyer.id, line.customerId)
            assertEquals(buyer.name, line.buyerName)
            assertEquals(buyer.meterNumber, line.destination)
            assertEquals("36.5", line.kwh)
            assertEquals("NEW TOKEN", line.code)
            assertEquals("", line.note)
        }
    }

    @Test
    fun receiptAction_onlyAppearsForSingleElectricityLine() {
        openReceipt(multiLine)
        compose.onNodeWithTag("sale-buy-again").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        openReceipt(nonElectricity, navigate = false)
        compose.onNodeWithTag("sale-buy-again").assertDoesNotExist()
    }

    @Test
    fun inactiveProduct_showsErrorAndKeepsReceipt() {
        openReceipt(unavailable)
        compose.onNodeWithTag("sale-buy-again").performScrollTo().performClick()
        waitForText("Couldn't finish")
        compose.onNodeWithText("OK").performClick()
        waitForText("Receipt")
        compose.onNodeWithTag("sale-buy-again").performScrollTo()
        readStore { store ->
            assertEquals(unavailable, store.sale(unavailable.id))
            assertEquals(4, store.sales().size)
            assertTrue(store.products(true).any { it.id == unavailable.lines.single().product.id && !it.active })
        }
    }

    private fun openReceipt(sale: Sale, navigate: Boolean = true) {
        if (navigate) {
            waitForText("Overview")
            compose.onNodeWithTag("nav-more").performClick()
            waitForText("Workspace")
            compose.onNodeWithText("History").performScrollTo().performClick()
            waitForText("Receipt or customer name")
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Receipt or customer name"))
        compose.onNodeWithText("Receipt or customer name").performScrollTo().performTextReplacement(buyer.name)
        compose.onNodeWithText("Search").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag("busy-dialog").fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(sale.receiptNumber))
        compose.onNodeWithText(sale.receiptNumber).performScrollTo().performClick()
        waitForText("Receipt")
    }

    private fun screenTag(tag: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }

    private fun editableText(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))

    private fun readStore(check: (Store) -> Unit) {
        Store(InstrumentationRegistry.getInstrumentation().targetContext).use(check)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithTag("busy-dialog").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithTag("busy-dialog").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }
}
