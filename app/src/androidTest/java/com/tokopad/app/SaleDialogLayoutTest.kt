package com.tokopad.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SaleDialogLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val settings = ShopSettings(language = "en")
    private val electricity = Product(id = 1, name = "PLN token", price = 50_000, kind = ProductKind.ELECTRICITY)
    private val physical = Product(id = 2, name = "Rice bag", price = 20_000)
    private val plnLine = CartLine(electricity, buyerName = "Rina Buyer", destination = "00123456789", kwh = "36.5", code = "12345678")

    private fun sale(lines: List<CartLine>, total: Long) = Sale(
        id = 42, number = "sale-42", createdAt = 1_730_000_000_000, zoneId = "Asia/Jakarta",
        lines = lines, customerId = 9, customerName = "Rina Buyer", customerPhone = "08123456789",
        subtotal = total, discount = 0, total = total, payment = "cash", paid = total, shop = settings,
    )

    private fun visible(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        if (!node.isDisplayed()) node.performScrollTo()
        return node.assertIsDisplayed()
    }

    private fun text(value: String) = visible(compose.onNodeWithText(value, substring = true))

    @Test fun singlePln_showsTransactionDetailsAndBuyAgain() {
        val transaction = sale(listOf(plnLine), 50_000)
        var repeats = 0
        compose.setContent {
            TokopadTheme { SaleDialog(transaction, settings, false, {}, {}, {}, onBuyAgain = { repeats++ }) }
        }
        text("00123456789")
        text("36.5")
        text("1234 5678")
        visible(compose.onNodeWithTag("sale-buy-again")).performClick()
        compose.runOnIdle { assertEquals(1, repeats) }
    }

    @Test fun plnOnlyNonCredit_doesNotShowCustomerPhone_evenWhenCustomerVisibilityIsEnabled() {
        val transaction = sale(listOf(plnLine), 50_000)
        compose.setContent {
            TokopadTheme { SaleDialog(transaction, settings.copy(showCustomer = true), false, {}, {}, {}) }
        }
        text("00123456789")
        compose.onNodeWithText(transaction.customerPhone, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Phone").assertDoesNotExist()
    }

    @Test fun plnHiddenDiscount_hidesSubtotalAndDiscount_butKeepsTotal() {
        val transaction = sale(listOf(plnLine), 50_000).copy(discount = 5_000, total = 45_000, paid = 45_000)
        val showDiscount = mutableStateOf(false)
        compose.setContent {
            TokopadTheme { SaleDialog(transaction, settings.copy(showDiscount = showDiscount.value), false, {}, {}, {}) }
        }
        text("Total")
        compose.onNodeWithText("Subtotal").assertDoesNotExist()
        compose.onNodeWithText("Discount").assertDoesNotExist()
        compose.runOnIdle { showDiscount.value = true }
        text("Subtotal")
        text("Discount")
    }

    @Test fun mixedSaleWithBlankPlnBuyer_doesNotRevealTransactionCustomerWhenHidden() {
        val transaction = sale(listOf(CartLine(physical), plnLine.copy(buyerName = "")), 70_000)
            .copy(customerName = "Private transaction customer")
        compose.setContent {
            TokopadTheme { SaleDialog(transaction, settings.copy(showCustomer = false), false, {}, {}, {}) }
        }
        text("Rice bag")
        text("00123456789")
        compose.onNodeWithText(transaction.customerName, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Buyer").assertDoesNotExist()
    }

    @Test fun credit_showsInitialMethodAndCancelledRefundWarning() {
        val debt = sale(listOf(CartLine(physical)), 20_000).copy(isCredit = true, paid = 5_000, payment = "noncash")
        val transaction = mutableStateOf(debt)
        compose.setContent {
            TokopadTheme { SaleDialog(transaction.value, settings, false, {}, {}, {}) }
        }
        text("Initial method: Noncash")
        compose.onNodeWithText("Refunds handled manually").assertDoesNotExist()
        compose.runOnIdle { transaction.value = debt.copy(cancelledAt = 1_730_086_400_000, cancellationReason = "Returned") }
        text("Initial method: Noncash")
        text("Refunds handled manually")
    }

    @Test fun physicalAndMixedSales_showItemsWithoutBuyAgain() {
        val transaction = mutableStateOf(sale(listOf(CartLine(physical, quantity = 2)), 40_000))
        compose.setContent {
            TokopadTheme { SaleDialog(transaction.value, settings, false, {}, {}, {}) }
        }
        text("Rice bag")
        compose.onNodeWithTag("sale-buy-again").assertDoesNotExist()
        compose.runOnIdle { transaction.value = sale(listOf(CartLine(physical), plnLine), 70_000) }
        text("Rice bag")
        text("PLN token")
        text("00123456789")
        compose.onNodeWithTag("sale-buy-again").assertDoesNotExist()
    }

    @Test fun debtAndCancelledSales_showStatusAndOnlyOfferValidActions() {
        val debt = sale(listOf(CartLine(physical)), 20_000).copy(isCredit = true, paid = 5_000)
        val transaction = mutableStateOf(debt)
        compose.setContent {
            TokopadTheme { SaleDialog(transaction.value, settings, false, {}, {}, {}, onSettle = { _, _ -> }) }
        }
        visible(compose.onNodeWithTag("sale-payment-status")).assertTextContains("Partially paid")
        val balance = hasTestTag("sale-balance")
        visible(compose.onNode((balance or hasAnyAncestor(balance)) and hasText(money(15_000, "en"), substring = true)))
        visible(compose.onNodeWithTag("sale-settle"))
        compose.runOnIdle { transaction.value = debt.copy(cancelledAt = 1_730_086_400_000, cancellationReason = "Customer returned item") }
        visible(compose.onNodeWithTag("sale-payment-status")).assertTextContains("Cancelled")
        compose.onNodeWithTag("sale-settle").assertDoesNotExist()
        compose.onNodeWithText("Cancel transaction").assertDoesNotExist()
    }

    @Test fun printAndClose_remainAccessibleAfterScrolling_andRespectBusy() {
        val transaction = sale(List(8) { index -> CartLine(physical.copy(id = index.toLong() + 10, name = "Item $index")) }, 160_000)
        val busy = mutableStateOf(false)
        var prints = 0
        var closes = 0
        compose.setContent {
            TokopadTheme { SaleDialog(transaction, settings, busy.value, onDismiss = { closes++ }, onPrint = { prints++ }, onCancel = {}) }
        }
        text("Item 7")
        compose.onNodeWithText("Print").assertIsDisplayed().performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, prints); assertEquals(1, closes); busy.value = true }
        compose.onNodeWithText("Print").assertIsNotEnabled()
        compose.onNodeWithText("Close").assertIsNotEnabled()
    }
}
