package com.tokopad.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

class CheckoutUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val product = Product(id = 1, name = "Test notebook", price = 2_500, trackStock = true, stock = 10)
    private val customer = Customer(id = 1, name = "Alex Buyer", phone = "08123456789")
    private val cart = mutableStateOf(emptyList<CartLine>())
    private var completions = 0
    private var completedCustomer: Customer? = null
    private var completedDiscount: Long? = null
    private var completedPayment: String? = null
    private var completedPaid: Long? = null

    private fun showCheckout() {
        compose.setContent {
            TokopadTheme {
                CheckoutScreen(
                    products = listOf(product),
                    customers = listOf(customer),
                    cart = cart.value,
                    language = "en",
                    busy = false,
                    onCart = { cart.value = it },
                    onComplete = { selectedCustomer, discount, payment, paid ->
                        completions++
                        completedCustomer = selectedCustomer
                        completedDiscount = discount
                        completedPayment = payment
                        completedPaid = paid
                    },
                )
            }
        }
    }

    private fun checkoutNode(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        return compose.onNodeWithText(label).performScrollTo()
    }

    private fun addProduct(quantity: String) {
        checkoutNode(product.name).performClick()
        compose.onNodeWithText("Quantity").performTextReplacement(quantity)
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.onNodeWithTag("checkout-products").assertIsSelected()
    }

    @Test
    fun cashSale_updatesQuantity_selectsCustomer_validatesPayment_andCompletesAfterReview() {
        showCheckout()
        compose.onNodeWithTag("checkout-products").assertIsSelected()
        compose.onNodeWithTag("checkout-cart").performClick()
        checkoutNode("Review sale").assertIsNotEnabled()
        compose.onNodeWithTag("checkout-products").performClick()
        addProduct("2")
        compose.onNodeWithTag("checkout-cart").performClick()
        checkoutNode(product.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase quantity for ${product.name}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3L, cart.value.single().quantity) }
        compose.onNodeWithContentDescription("Decrease quantity for ${product.name}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2L, cart.value.single().quantity) }

        compose.onNodeWithText("Find customer (optional)").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("checkout-customer"))
        compose.onNodeWithTag("checkout-customer").performClick()
        checkoutNode("Find customer (optional)").performTextReplacement("Alex")
        checkoutNode("${customer.name} · ${customer.phone}").performClick()
        compose.onNodeWithTag("checkout-customer").assertTextContains(customer.name)
        checkoutNode("Discount (Rp)").performTextReplacement("500")
        checkoutNode("Total: ${money(4_500, "en")}").assertIsDisplayed()

        checkoutNode("Cash received (Rp)").performTextReplacement("4499")
        checkoutNode("Review sale").assertIsNotEnabled()
        checkoutNode("Cash received (Rp)").performTextReplacement("5000")
        checkoutNode("Change: ${money(500, "en")}").assertIsDisplayed()
        checkoutNode("Review sale").assertIsEnabled().performClick()
        compose.onNodeWithText("Finish this sale?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }

        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(0, completions) }
        checkoutNode("Review sale").performClick()
        compose.onNodeWithText("Complete sale").assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(customer, completedCustomer)
            assertEquals(500L, completedDiscount)
            assertEquals("cash", completedPayment)
            assertEquals(5_000L, completedPaid)
            assertEquals(listOf(CartLine(product, quantity = 2)), cart.value)
        }
        compose.onNodeWithText("Finish this sale?").assertDoesNotExist()
    }

    @Test
    fun noncashSale_completesWithoutCashReceived_orCustomer() {
        showCheckout()
        addProduct("1")
        compose.onNodeWithTag("checkout-cart").performClick()
        checkoutNode("Review sale").assertIsNotEnabled()
        checkoutNode("Noncash").performClick().assertIsSelected()
        compose.onNodeWithText("Cash received (Rp)").assertDoesNotExist()
        checkoutNode("Review sale").assertIsEnabled().performClick()
        compose.onNodeWithText("Finish this sale?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithText("Complete sale").performClick()

        compose.runOnIdle {
            assertEquals(1, completions)
            assertNull(completedCustomer)
            assertEquals(0L, completedDiscount)
            assertEquals("noncash", completedPayment)
            assertEquals(product.price, completedPaid)
            assertEquals(listOf(CartLine(product)), cart.value)
        }
    }

    @Test
    fun productsTab_hidesPaymentFields_andPreservesCartEntriesWhenSwitching() {
        showCheckout()
        compose.onNodeWithTag("checkout-products").assertIsSelected()
        compose.onNodeWithText("Discount (Rp)").assertDoesNotExist()
        compose.onNodeWithText("Cash received (Rp)").assertDoesNotExist()
        compose.onNodeWithText("Review sale").assertDoesNotExist()
        addProduct("2")
        compose.onNodeWithTag("checkout-cart").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("checkout-customer"))
        compose.onNodeWithTag("checkout-customer").performClick()
        checkoutNode("Find customer (optional)").performTextReplacement("Alex")
        checkoutNode("${customer.name} · ${customer.phone}").performClick()
        checkoutNode("Discount (Rp)").performTextReplacement("500")
        checkoutNode("Cash received (Rp)").performTextReplacement("5000")

        compose.onNodeWithTag("checkout-products").performClick().assertIsSelected()
        compose.onNodeWithText("Discount (Rp)").assertDoesNotExist()
        compose.onNodeWithText("Cash received (Rp)").assertDoesNotExist()
        compose.onNodeWithText("Review sale").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(CartLine(product, 2)), cart.value) }

        compose.onNodeWithTag("checkout-cart").performClick().assertIsSelected()
        checkoutNode(product.name).assertIsDisplayed()
        checkoutNode("2 × ${money(product.price, "en")}").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("checkout-customer"))
        compose.onNodeWithTag("checkout-customer").assertTextContains(customer.name)
        checkoutNode("Discount (Rp)").assertTextContains("500")
        checkoutNode("Cash received (Rp)").assertTextContains("5000")
        checkoutNode("Cash").assertIsSelected()
        checkoutNode("Total: ${money(4_500, "en")}").assertIsDisplayed()
        checkoutNode("Review sale").assertIsEnabled()
    }

    @Test
    fun checkoutDraftSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val savedCart = rememberSaveable { mutableStateOf(listOf(CartLine(product, 2))) }
            TokopadTheme {
                CheckoutScreen(listOf(product), emptyList(), savedCart.value, "en", false,
                    onCart = { savedCart.value = it },
                    onComplete = { _, discount, payment, paid ->
                        completedDiscount = discount; completedPayment = payment; completedPaid = paid
                    })
            }
        }
        compose.onNodeWithTag("checkout-cart").performClick()
        checkoutNode("Discount (Rp)").performTextReplacement("500")
        checkoutNode("Cash received (Rp)").performTextReplacement("5000")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("checkout-cart").assertIsSelected()
        checkoutNode("Total: ${money(4_500, "en")}").assertIsDisplayed()
        checkoutNode("Review sale").assertIsEnabled().performClick()
        compose.onNodeWithText("Complete sale").performClick()
        compose.runOnIdle {
            assertEquals(500L, completedDiscount)
            assertEquals("cash", completedPayment)
            assertEquals(5_000L, completedPaid)
        }
    }
}
