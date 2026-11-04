package com.tokopad.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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

class CartCustomerUiTest {
    @get:Rule val compose = createComposeRule()

    private val electricity = Product(id = 1, name = "PLN 50,000", price = 50_000, kind = ProductKind.ELECTRICITY)
    private val physical = Product(id = 2, name = "Notebook", price = 2_500)
    private val buyer = Customer(id = 9, name = "Rina PLN", phone = "08123456789", meterNumber = "12345678901")
    private val other = Customer(id = 10, name = "Dina PLN", phone = "08987654321", meterNumber = "10987654321")
    private val responsible = Customer(id = 11, name = "Nadia Responsible", phone = "08111111111")
    private val customers = mutableStateOf(listOf(buyer, other, responsible))
    private val cart = mutableStateOf(emptyList<CartLine>())
    private var completions = 0
    private var completedCustomer: Customer? = null
    private var completedPaid: Long? = null
    private var completedCredit: Boolean? = null

    private fun line(customer: Customer = buyer) = CartLine(electricity, buyerName = customer.name,
        destination = customer.meterNumber, kwh = "20", customerId = customer.id)

    private fun showCheckout(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            TokopadTheme {
                CheckoutScreen(listOf(electricity, physical), customers.value, cart.value, "en", false,
                    onCart = { cart.value = it },
                    onComplete = { customer, _, _, paid, credit ->
                        completions++
                        completedCustomer = customer
                        completedPaid = paid
                        completedCredit = credit
                    })
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithTag("checkout-cart").performClick()
    }

    private fun checkoutTag(tag: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }

    private fun addSavedBuyer(customer: Customer) {
        compose.onNodeWithTag("checkout-products").performClick()
        checkoutTag("checkout-kind-ELECTRICITY").performClick()
        compose.onNodeWithText(electricity.name).performClick()
        compose.onNodeWithTag("digital-buyer-${customer.id}").performScrollTo().performClick()
        compose.onNodeWithTag("digital-kwh").performScrollTo().performTextReplacement("20")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        compose.onNodeWithTag("checkout-cart").performClick()
    }

    private fun editBuyer(customer: Customer) {
        checkoutTag("edit-digital-0").performClick()
        compose.onNodeWithTag("digital-buyer-${customer.id}").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(customer.id, cart.value.first().customerId) }
    }

    private fun chooseCustomer(customer: Customer) {
        checkoutTag("checkout-customer").performClick()
        checkoutTag("checkout-customer-${customer.id}").performClick()
    }

    private fun complete(customer: Customer?, paid: Long, credit: Boolean) {
        checkoutTag("checkout-review").assertIsEnabled().performClick()
        if (credit) compose.onNodeWithTag("checkout-confirm-customer").performScrollTo()
            .assertTextContains("Customer: ${customer?.name}")
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithTag("checkout-complete").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(customer, completedCustomer)
            assertEquals(paid, completedPaid)
            assertEquals(credit, completedCredit)
        }
    }

    @Test fun addingSavedPlnBuyer_selectsCartCustomer_andPassesItToFullyPaidCheckout() {
        showCheckout()
        addSavedBuyer(buyer)
        compose.runOnIdle { assertEquals(listOf(line()), cart.value) }
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        checkoutTag("checkout-paid").performTextReplacement("50000")
        complete(buyer, 50_000, false)
    }

    @Test fun editingSavedPlnBuyer_updatesAutomaticCustomer_andPartialDebtCallback() {
        cart.value = listOf(line())
        showCheckout()
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        editBuyer(other)
        checkoutTag("checkout-customer").assertTextContains(other.name)
        checkoutTag("checkout-credit").performClick().assertIsOn()
        // The chooser stays closed when an automatic customer is already available.
        checkoutTag("checkout-customer").assertTextContains("+")
        checkoutTag("checkout-paid").performTextReplacement("10000")
        checkoutTag("checkout-balance").assertTextContains("Remaining: ${money(40_000, "en")}")
        complete(other, 10_000, true)
    }

    @Test fun repeatedBuyer_ignoresPhysicalAndUnnamedDigitalLines_andAllowsZeroPaymentDebt() {
        val unnamed = CartLine(electricity.copy(kind = ProductKind.PULSA), destination = "08000000000")
        cart.value = listOf(line(), line().copy(buyerName = ""),
            CartLine(physical, buyerName = other.name, customerId = other.id), unnamed)
        showCheckout()
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        checkoutTag("checkout-credit").performClick().assertIsOn()
        checkoutTag("checkout-customer").assertTextContains(buyer.name).assertTextContains("+")
        checkoutTag("checkout-balance").assertTextContains("Remaining: ${money(152_500, "en")}")
        complete(buyer, 0, true)
    }

    @Test fun conflictingBuyers_clearAutomaticChoice_removalResolvesConflict_andDebtRequiresExplicitChoice() {
        cart.value = listOf(line())
        showCheckout()
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        compose.runOnIdle { cart.value = cart.value + line(other) }
        checkoutTag("checkout-customer").assertTextContains("Add customer (optional)")
        checkoutTag("edit-digital-1")
        compose.onAllNodesWithText("Remove").onLast().performClick()
        compose.runOnIdle { assertEquals(listOf(line()), cart.value) }
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        compose.runOnIdle { cart.value = cart.value + line(other) }
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-customer-required").assertIsDisplayed()
        checkoutTag("checkout-review").assertIsNotEnabled()
        // A missing customer opens the chooser directly; do not toggle it first.
        checkoutTag("checkout-customer-${responsible.id}").performClick()
        checkoutTag("checkout-customer").assertTextContains(responsible.name)
        complete(responsible, 0, true)
    }

    @Test fun manualChoice_survivesRestoration_andAddingOrEditingRecipients() {
        cart.value = listOf(line())
        val restoration = StateRestorationTester(compose)
        showCheckout(restoration)
        chooseCustomer(responsible)
        restoration.emulateSavedInstanceStateRestore()
        checkoutTag("checkout-customer").assertTextContains(responsible.name)
        editBuyer(other)
        checkoutTag("checkout-customer").assertTextContains(responsible.name)
        addSavedBuyer(other)
        checkoutTag("checkout-customer").assertTextContains(responsible.name)
        restoration.emulateSavedInstanceStateRestore()
        checkoutTag("checkout-customer").assertTextContains(responsible.name)
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-customer").assertTextContains("+")
        complete(responsible, 0, true)
    }

    @Test fun manualClear_survivesRestorationAndRecipientChanges_andFullyPaidCallbackStaysAnonymous() {
        cart.value = listOf(line())
        val restoration = StateRestorationTester(compose)
        showCheckout(restoration)
        checkoutTag("checkout-customer").assertTextContains(buyer.name).performClick()
        checkoutTag("checkout-clear-customer").performClick()
        restoration.emulateSavedInstanceStateRestore()
        checkoutTag("checkout-customer").assertTextContains("Add customer (optional)")
        editBuyer(other)
        addSavedBuyer(other)
        restoration.emulateSavedInstanceStateRestore()
        checkoutTag("checkout-customer").assertTextContains("Add customer (optional)")
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-customer-search").assertIsDisplayed()
        checkoutTag("checkout-review").assertIsNotEnabled()
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-payment-noncash").performClick()
        complete(null, 100_000, false)
    }

    @Test fun missingOrUnsavedRecipients_neverAutoAssign_evenWhenNameMatchesSavedCustomer() {
        val unsaved = line().copy(customerId = null)
        val missing = line().copy(customerId = 999, buyerName = "")
        val cases = listOf(listOf(unsaved), listOf(missing), listOf(line(), unsaved),
            listOf(line(), missing), listOf(line().copy(customerId = null, buyerName = "   ")),
            listOf(CartLine(physical, buyerName = buyer.name, customerId = buyer.id)))
        cart.value = cases.first()
        showCheckout()
        cases.forEach { lines ->
            compose.runOnIdle { cart.value = lines }
            checkoutTag("checkout-customer").assertTextContains("Add customer (optional)")
        }
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-customer-required").assertIsDisplayed()
        checkoutTag("checkout-customer-search").assertIsDisplayed()
        checkoutTag("checkout-review").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, completions); assertNull(completedCustomer) }
    }

    @Test fun removingSavedCustomers_invalidatesAutomaticAndManualSelection_withoutFallback() {
        cart.value = listOf(line())
        showCheckout()
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        compose.runOnIdle { customers.value = customers.value.filterNot { it.id == buyer.id } }
        checkoutTag("checkout-customer").assertTextContains("Add customer (optional)")
        compose.runOnIdle { customers.value = listOf(buyer, other, responsible) }
        chooseCustomer(other)
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-review").assertIsEnabled()
        compose.runOnIdle { customers.value = customers.value.filterNot { it.id == other.id } }
        checkoutTag("checkout-customer").assertTextContains("Select customer (required)")
        checkoutTag("checkout-customer-required").assertIsDisplayed()
        checkoutTag("checkout-review").assertIsNotEnabled()
        compose.runOnIdle { cart.value = cart.value + line() }
        checkoutTag("checkout-customer").assertTextContains("Select customer (required)")
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-payment-noncash").performClick()
        complete(null, 100_000, false)
    }
}
