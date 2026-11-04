package com.tokopad.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class DebtUiTest {
    @get:Rule val compose = createComposeRule()

    private val product = Product(id = 1, name = "Notebook", price = 2_500)
    private val buyer = Customer(id = 9, name = "Alex Buyer", phone = "08123456789", meterNumber = "12345678901")
    private val customers = mutableStateOf(listOf(buyer))
    private val cart = mutableStateOf(listOf(CartLine(product)))
    private val busy = mutableStateOf(false)
    private var completions = 0
    private var completedCustomer: Customer? = null
    private var completedPaid: Long? = null
    private var completedPayment: String? = null
    private var completedCredit: Boolean? = null

    private fun showCheckout(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            TokopadTheme {
                CheckoutScreen(listOf(product), customers.value, cart.value, "en", busy.value,
                    onCart = { cart.value = it },
                    onComplete = { customer, _, payment, paid, credit ->
                        completions++
                        completedCustomer = customer
                        completedPayment = payment
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

    private fun chooseBuyer() {
        checkoutTag("checkout-customer-${buyer.id}").performClick()
    }

    @Test fun zeroInitialPayment_requiresExplicitSavedCustomer_andConfirmsFullDebt() {
        showCheckout()
        checkoutTag("checkout-credit").performClick().assertIsOn()
        checkoutTag("checkout-customer-required").assertIsDisplayed()
        checkoutTag("checkout-review").assertIsNotEnabled()
        chooseBuyer()
        checkoutTag("checkout-paid").assertIsDisplayed()
        checkoutTag("checkout-balance").assertTextContains("Remaining: ${money(product.price, "en")}")
        checkoutTag("checkout-customer").performClick()
        checkoutTag("checkout-clear-customer").performClick()
        checkoutTag("checkout-review").assertIsNotEnabled()
        checkoutTag("checkout-customer").performClick()
        chooseBuyer()
        checkoutTag("checkout-review").assertIsEnabled().performClick()
        compose.onNodeWithTag("checkout-confirm-customer").performScrollTo().assertTextContains("Customer: ${buyer.name}")
        compose.onNodeWithTag("checkout-confirm-initial").performScrollTo().assertTextContains("Initial payment: ${money(0, "en")} · Cash")
        compose.onNodeWithTag("checkout-confirm-balance").performScrollTo().assertTextContains("Remaining: ${money(product.price, "en")}")
        compose.runOnIdle { assertEquals(0, completions) }
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithTag("checkout-complete").assertIsNotEnabled()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithTag("checkout-complete").performClick()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(buyer, completedCustomer)
            assertEquals(0L, completedPaid)
            assertEquals("cash", completedPayment)
            assertEquals(true, completedCredit)
        }
    }

    @Test fun debtValidation_rejectsFullOrExcessInitial_zeroTotal_andRemovedCustomer() {
        showCheckout()
        checkoutTag("checkout-credit").performClick()
        chooseBuyer()
        listOf("2500", "2501").forEach { amount ->
            checkoutTag("checkout-paid").performTextReplacement(amount)
            checkoutTag("checkout-review").assertIsNotEnabled()
        }
        checkoutTag("checkout-paid").performTextReplacement("0")
        checkoutTag("checkout-review").assertIsEnabled()
        compose.onNodeWithText("Discount (Rp)").performScrollTo().performTextReplacement("2500")
        checkoutTag("checkout-review").assertIsNotEnabled()
        compose.onNodeWithText("Discount (Rp)").performScrollTo().performTextReplacement("2501")
        checkoutTag("checkout-review").assertIsNotEnabled()
        compose.onNodeWithText("Discount (Rp)").performScrollTo().performTextReplacement("")
        checkoutTag("checkout-review").assertIsEnabled()
        compose.runOnIdle { customers.value = emptyList() }
        checkoutTag("checkout-review").assertIsNotEnabled()
        checkoutTag("checkout-customer-required").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
    }

    @Test fun partialInitialPayment_supportsCashAndNoncash_showsRemainingNotChange() {
        showCheckout()
        checkoutTag("checkout-credit").performClick()
        chooseBuyer()
        checkoutTag("checkout-paid").performTextReplacement("1000")
        checkoutTag("checkout-balance").assertTextContains("Remaining: ${money(1_500, "en")}")
        checkoutTag("checkout-review").assertIsEnabled()
        checkoutTag("checkout-payment-noncash").performClick().assertIsSelected()
        checkoutTag("checkout-paid").assertTextContains("1000")
        checkoutTag("checkout-balance").assertTextContains("Remaining: ${money(1_500, "en")}")
        compose.onAllNodes(hasText("Change:", substring = true)).assertCountEquals(0)
        checkoutTag("checkout-review").performClick()
        compose.onNodeWithTag("checkout-confirm-initial").performScrollTo().assertTextContains("Initial payment: ${money(1_000, "en")} · Noncash")
        compose.onNodeWithTag("checkout-confirm-balance").performScrollTo().assertTextContains("Remaining: ${money(1_500, "en")}")
        compose.onNodeWithTag("checkout-complete").performClick()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(buyer, completedCustomer)
            assertEquals(1_000L, completedPaid)
            assertEquals("noncash", completedPayment)
            assertEquals(true, completedCredit)
        }
    }

    @Test fun savedDigitalBuyer_automaticallySelectsDebtCustomer() {
        val digital = product.copy(kind = ProductKind.ELECTRICITY)
        cart.value = listOf(CartLine(digital, destination = buyer.meterNumber, kwh = "10", buyerName = buyer.name, customerId = buyer.id))
        showCheckout()
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-customer").assertTextContains(buyer.name)
        compose.onNodeWithTag("checkout-customer-required").assertDoesNotExist()
        compose.onNodeWithTag("checkout-customer-search").assertDoesNotExist()
        checkoutTag("checkout-paid").performTextReplacement("1000")
        checkoutTag("checkout-review").assertIsEnabled().performClick()
        compose.onNodeWithTag("checkout-confirm-customer").performScrollTo().assertTextContains("Customer: ${buyer.name}")
        compose.onNodeWithTag("checkout-complete").performClick()
        compose.runOnIdle {
            assertEquals(buyer, completedCustomer)
            assertEquals(1_000L, completedPaid)
            assertEquals(true, completedCredit)
        }
    }

    @Test fun customerSearch_showsUsefulPreview_andFindsNamePhoneOrMeter() {
        customers.value = (1L..5L).map { buyer.copy(id = it, name = "Buyer $it", phone = "0800000000$it", meterNumber = "1200000000$it") }
        showCheckout()
        checkoutTag("checkout-credit").performClick()
        checkoutTag("checkout-customer-3").assertIsDisplayed()
        compose.onNodeWithTag("checkout-customer-5").assertDoesNotExist()
        val last = customers.value.last()
        listOf(last.name, last.phone, last.meterNumber).forEach { query ->
            checkoutTag("checkout-customer-search").performTextReplacement(query)
            checkoutTag("checkout-customer-5").assertIsDisplayed()
        }
        checkoutTag("checkout-customer-5").performClick()
        checkoutTag("checkout-customer").assertTextContains(last.name)
        checkoutTag("checkout-review").assertIsEnabled()
    }

    private fun restoreDebtReview(initial: String, method: String) {
        val restoration = StateRestorationTester(compose)
        showCheckout(restoration)
        checkoutTag("checkout-credit").performClick()
        chooseBuyer()
        checkoutTag("checkout-paid").performTextReplacement(initial)
        checkoutTag("checkout-payment-$method").performClick()
        checkoutTag("checkout-review").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Finish this sale?").assertIsDisplayed()
        compose.onNodeWithTag("checkout-confirm-customer").performScrollTo().assertTextContains("Customer: ${buyer.name}")
        val amount = initial.toLongOrNull() ?: 0L
        compose.onNodeWithTag("checkout-confirm-initial").performScrollTo().assertTextContains("Initial payment: ${money(amount, "en")} · ${if (method == "cash") "Cash" else "Noncash"}")
        compose.onNodeWithTag("checkout-confirm-balance").performScrollTo().assertTextContains("Remaining: ${money(product.price - amount, "en")}")
        compose.onNodeWithTag("checkout-complete").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(buyer, completedCustomer)
            assertEquals(amount, completedPaid)
            assertEquals(method, completedPayment)
            assertEquals(true, completedCredit)
        }
    }

    @Test fun zeroDebtReview_survivesSavedStateRestoration() = restoreDebtReview("", "cash")

    @Test fun partialCashDebtReview_survivesSavedStateRestoration() = restoreDebtReview("1000", "cash")

    @Test fun partialNoncashDebtReview_survivesSavedStateRestoration() = restoreDebtReview("1000", "noncash")

    private fun debtSale() = Sale(
        id = 1, number = "0001", createdAt = 1_730_000_000_000, zoneId = "Asia/Jakarta",
        lines = listOf(CartLine(product)), customerId = buyer.id, customerName = buyer.name,
        customerPhone = buyer.phone, subtotal = product.price, discount = 0, total = product.price,
        payment = "cash", paid = 1_000, shop = ShopSettings(), isCredit = true,
    )

    @Test fun settlement_fixedFullBalance_requiresConfirmation_keepsRetryId_andDisablesBusy() {
        val sale = mutableStateOf(debtSale())
        val requests = mutableListOf<Pair<String, String>>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TokopadTheme {
                SaleDialog(sale.value, ShopSettings(), busy.value, onDismiss = {}, onPrint = {}, onCancel = {},
                    onSettle = { method, requestId -> requests += method to requestId; busy.value = true })
            }
        }
        compose.onNodeWithTag("sale-payment-status").assertTextContains("Payment status: Partially paid")
        compose.onNodeWithTag("sale-settle").performScrollTo().performClick()
        compose.onNodeWithTag("settlement-amount").performScrollTo().assertTextContains(money(1_500, "en"))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("settlement-noncash").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        compose.onNodeWithTag("settlement-confirm").performClick()
        compose.onNodeWithTag("settlement-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("settlement-cash").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals("noncash", requests.single().first)
            UUID.fromString(requests.single().second)
            busy.value = false // A failed request may be retried with the same idempotency key.
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settlement-noncash").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("settlement-amount").performScrollTo().assertTextContains(money(1_500, "en"))
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("sale-settle").performScrollTo().performClick()
        compose.onNodeWithTag("settlement-confirm").performClick()
        compose.runOnIdle {
            assertEquals(2, requests.size)
            assertEquals(requests[0], requests[1])
            sale.value = sale.value.copy(settlement = Settlement(1_500, "noncash", 1_730_086_400_000, requests.last().second))
            busy.value = false
        }
        compose.onNodeWithTag("sale-payment-status").assertTextContains("Payment status: Paid")
        compose.onNodeWithTag("sale-balance").assertTextContains("Remaining: ${money(0, "en")}")
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
        compose.onNodeWithTag("sale-settlement").performScrollTo().assertTextContains("Noncash", substring = true)
        compose.onNodeWithTag("sale-settlement").assertTextContains("28 Oct 2024", substring = true)
    }

    @Test fun settlementUnavailable_forCancelledSettledOrOrdinarySale() {
        val sale = mutableStateOf(debtSale().copy(cancelledAt = 1_730_086_400_000, cancellationReason = "Returned"))
        compose.setContent {
            TokopadTheme { SaleDialog(sale.value, ShopSettings(), false, {}, {}, {}, onSettle = { _, _ -> error("Unexpected settlement") }) }
        }
        compose.onNodeWithTag("sale-payment-status").assertTextContains("Payment status: Cancelled")
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
        compose.runOnIdle { sale.value = debtSale().copy(settlement = Settlement(1_500, "cash", 1_730_086_400_000, "settled")) }
        compose.onNodeWithTag("sale-payment-status").assertTextContains("Payment status: Paid")
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
        compose.runOnIdle { sale.value = debtSale().copy(isCredit = false, paid = product.price) }
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
    }

    @Test fun cancellation_warnsDebtVoidedAndReceivedPaymentRefundedSeparately() {
        var reason: String? = null
        compose.setContent {
            TokopadTheme { SaleDialog(debtSale(), ShopSettings(), false, {}, {}, onCancel = { reason = it }, onSettle = { _, _ -> }) }
        }
        compose.onNodeWithText("Cancel transaction").performScrollTo().performClick()
        compose.onNodeWithText("Outstanding debt will be voided: ${money(1_500, "en")}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Refund received payment separately: ${money(1_000, "en")}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
        compose.onNodeWithText("Cancellation reason").performScrollTo().performTextReplacement("Returned")
        compose.onNodeWithText("Confirm cancellation").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Returned", reason) }
    }

    @Test fun dashboard_receivedUsesSelectedDates_outstandingExplicitlyAllDates_andDebtRowsNotPaid() {
        compose.setContent {
            TokopadTheme {
                DashboardScreen(Dashboard(revenue = 2_500, count = 1, received = 1_000, outstanding = 7_500),
                    listOf(debtSale().copy(paid = 0), debtSale().copy(id = 2)), "en", 20_000, 20_000, { _, _ -> }, {}, {})
            }
        }
        checkoutTag("dashboard-received").assertTextContains(money(1_000, "en"))
        compose.onNodeWithText("Received · selected dates").performScrollTo().assertIsDisplayed()
        checkoutTag("dashboard-outstanding").assertTextContains(money(7_500, "en"))
        compose.onNodeWithText("Outstanding debt · all dates").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Owing"))
        compose.onNodeWithText("Owing").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Partially paid"))
        compose.onNodeWithText("Partially paid").assertIsDisplayed()
        compose.onNodeWithText("Paid").assertDoesNotExist()
    }
}
