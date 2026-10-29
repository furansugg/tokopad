package com.tokopad.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DigitalCheckoutUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val electricity = Product(id = 1, name = "PLN 50,000", price = 50_000, kind = ProductKind.ELECTRICITY)
    private val buyer = Customer(id = 9, name = "Rina PLN", phone = "08123456789", meterNumber = "12345678901")
    private val cart = mutableStateOf(emptyList<CartLine>())
    private var completions = 0

    private fun showCheckout(product: Product = electricity, customers: List<Customer> = listOf(buyer)) {
        compose.setContent {
            TokopadTheme {
                CheckoutScreen(listOf(product), customers, cart.value, "en", false,
                    onCart = { cart.value = it },
                    onComplete = { _, _, _, _ -> completions++ })
            }
        }
        compose.onNodeWithText(product.name).performClick()
    }

    private fun replace(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    @Test
    fun savedBuyer_shownImmediately_searchesNamePhoneMeter_autofillsMeterNotPhone() {
        showCheckout()
        compose.onNodeWithTag("digital-buyer-search").assertIsDisplayed()
        compose.onNodeWithTag("digital-buyer-${buyer.id}").assertIsDisplayed()
        listOf("Rina", buyer.phone, buyer.meterNumber).forEach { query ->
            replace("digital-buyer-search", query)
            compose.onNodeWithTag("digital-buyer-${buyer.id}").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("digital-buyer-${buyer.id}").performClick()
        compose.onNodeWithTag("digital-buyer-name").performScrollTo().assertTextContains(buyer.name)
        compose.onNodeWithTag("digital-destination").performScrollTo().assertTextContains(buyer.meterNumber)
        compose.onNodeWithText("Add").assertIsNotEnabled()
        listOf("0", "-1", "1.2345", "1000001").forEach { invalid ->
            replace("digital-kwh", invalid)
            compose.onNodeWithText("Add").assertIsNotEnabled()
        }
        replace("digital-kwh", "36,5")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.runOnIdle {
            val line = cart.value.single()
            assertEquals(buyer.name, line.buyerName)
            assertEquals(buyer.meterNumber, line.destination)
            assertEquals(buyer.id, line.customerId)
            assertEquals("36.5", line.kwh)
            assertEquals("", line.code)
            assertEquals(1L, line.quantity)
            assertEquals(0, completions)
        }
    }

    @Test
    fun buyerResults_compact_searchFindsBeyondPreview_selectionCollapsesAndCanReopen() {
        val customers = (1L..8L).map { buyer.copy(id = it, name = "Buyer $it", meterNumber = "1234567800$it") }
        showCheckout(customers = customers)
        compose.onNodeWithTag("digital-buyer-3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("digital-buyer-4").assertDoesNotExist()
        compose.onNodeWithText("Showing 3 buyers. Search to narrow results.").performScrollTo().assertIsDisplayed()
        replace("digital-buyer-search", "Buyer 8")
        compose.onNodeWithTag("digital-buyer-8").performScrollTo().performClick()
        compose.onNodeWithTag("digital-buyer-results").assertDoesNotExist()
        compose.onNodeWithTag("digital-buyer-name").performScrollTo().assertTextContains("Buyer 8")
        compose.onNodeWithTag("digital-destination").performScrollTo().assertTextContains("12345678008")
        compose.onNodeWithTag("digital-change-buyer").performScrollTo().performClick()
        compose.onNodeWithTag("digital-buyer-8").performScrollTo().assertIsDisplayed()
        replace("digital-buyer-search", "not found")
        compose.onNodeWithTag("digital-buyer-results").assertDoesNotExist()
        compose.onNodeWithText("No matching buyers. Try another search or enter details below.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("digital-buyer-name").performScrollTo().assertTextContains("Buyer 8")
    }

    @Test
    fun manualBuyer_requiresNameMeterPositiveKwh_keepsCustomerUnsavedUntilCheckout() {
        val customers = listOf(buyer)
        showCheckout(customers = customers)
        replace("digital-destination", "99887766554")
        replace("digital-kwh", "25.75")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        replace("digital-buyer-name", "New PLN buyer")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(buyer), customers)
            assertEquals(0, completions)
            assertEquals("New PLN buyer", cart.value.single().buyerName)
            assertEquals("99887766554", cart.value.single().destination)
            assertEquals("25.75", cart.value.single().kwh)
            assertNull(cart.value.single().customerId)
        }
    }

    @Test
    fun editingSavedBuyerDetails_clearsSelectedId_preservesQuantity() {
        cart.value = listOf(CartLine(electricity, quantity = 2, buyerName = buyer.name,
            destination = buyer.meterNumber, kwh = "20", customerId = buyer.id))
        compose.setContent {
            TokopadTheme {
                CheckoutScreen(listOf(electricity), listOf(buyer), cart.value, "en", false,
                    onCart = { cart.value = it }, onComplete = { _, _, _, _ -> completions++ })
            }
        }
        compose.onNodeWithTag("checkout-cart").performClick()
        compose.onNodeWithTag("edit-digital-0").performScrollTo().performClick()
        replace("digital-buyer-name", "Corrected buyer")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertNull(cart.value.single().customerId)
            assertEquals("Corrected buyer", cart.value.single().buyerName)
            assertEquals(2L, cart.value.single().quantity)
        }
        compose.onNodeWithTag("edit-digital-0").performScrollTo().performClick()
        compose.onNodeWithTag("digital-buyer-${buyer.id}").performScrollTo().performClick()
        replace("digital-destination", "11223344556")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertNull(cart.value.single().customerId)
            assertEquals(buyer.name, cart.value.single().buyerName)
            assertEquals("11223344556", cart.value.single().destination)
            assertEquals(2L, cart.value.single().quantity)
        }
    }

    @Test
    fun savedBuyerDestinations_acceptStoredPhonePunctuationAndShortMeter() {
        val formatted = buyer.copy(meterNumber = "123")
        showCheckout(customers = listOf(formatted))
        compose.onNodeWithTag("digital-buyer-${buyer.id}").performScrollTo().performClick()
        replace("digital-kwh", "1")
        compose.onNodeWithText("Add").assertIsEnabled()
        replace("digital-destination", "0812(345).678")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("0812(345).678", cart.value.single().destination) }
    }

    @Test
    fun configuredTokenRequired_beforeAdd() {
        showCheckout(product = electricity.copy(requiresCode = true))
        compose.onNodeWithTag("digital-buyer-${buyer.id}").performScrollTo().performClick()
        replace("digital-kwh", "12.5")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        replace("digital-code", "12345 67890 12345 67890")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("12345 67890 12345 67890", cart.value.single().code) }
    }

    @Test
    fun airtime_usesSavedPhone_buyerOptional_checkoutDoesNotSelectGlobalCustomer() {
        val airtime = Product(id = 2, name = "Airtime 10,000", price = 10_000, kind = ProductKind.PULSA)
        showCheckout(product = airtime)
        compose.onNodeWithTag("digital-buyer-${buyer.id}").performScrollTo().performClick()
        compose.onNodeWithTag("digital-destination").performScrollTo().assertTextContains(buyer.phone)
        replace("digital-buyer-name", "")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("", cart.value.single().buyerName)
            assertEquals(buyer.phone, cart.value.single().destination)
            assertNull(cart.value.single().customerId)
        }
        compose.onNodeWithTag("checkout-cart").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Add customer (optional)"))
        compose.onNodeWithTag("checkout-customer").assertTextContains("Add customer (optional)")
    }
}
