package com.tokopad.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResponsiveUiTest {
    @get:Rule val compose = createComposeRule()

    private fun showNarrowScreen(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = 640
                fontScale = 1.5f
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                TokopadTheme(theme = "dark") {
                    Box(Modifier.width(320.dp).height(640.dp)) { content() }
                }
            }
        }
    }

    @Test fun settings_largeFonts_sectionTabsAndSaveRemainReachable() {
        val settings = mutableStateOf(ShopSettings())
        val section = mutableStateOf("general")
        var saves = 0
        showNarrowScreen {
            SettingsScreen(
                settings = settings.value, printers = emptyList(),
                onSave = { settings.value = it; saves++ },
                onFindPrinters = {}, onTestPrint = {}, onExport = {}, onImport = {},
                onSelectPrinter = {}, onConnectPrinter = {}, onOpenBluetooth = {},
                printerStatus = "", section = section.value, onSection = { section.value = it },
            )
        }
        listOf("receipt", "printer", "data", "general").forEach { id ->
            compose.onNodeWithTag("settings-$id")
                .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            if (id == "general" || id == "receipt") {
                compose.onNodeWithText("Save settings").assertIsDisplayed().assertHasClickAction()
            } else compose.onNodeWithText("Save settings").assertDoesNotExist()
        }
        compose.onNodeWithText("Dark").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals("system", settings.value.theme)
            assertEquals(0, saves)
        }
        compose.onNodeWithText("Save settings").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("dark", settings.value.theme)
            assertEquals(1, saves)
        }
    }

    @Test fun checkout_largeFonts_digitalFieldsAndDismissRemainReachable() {
        val product = Product(id = 1, name = "PLN 50,000", price = 50_000, kind = ProductKind.ELECTRICITY)
        showNarrowScreen {
            CheckoutScreen(
                products = listOf(product), customers = (1L..6L).map { Customer(id = it, name = "Long buyer name $it", meterNumber = "12345678901") }, cart = emptyList(),
                language = "en", busy = false, onCart = {}, onComplete = { _, _, _, _, _ -> },
            )
        }
        compose.onNodeWithTag("checkout-kind-ELECTRICITY").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(product.name).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("digital-buyer-3").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("digital-buyer-results").assertDoesNotExist()
        listOf("digital-buyer-search", "digital-buyer-name", "digital-destination", "digital-kwh", "digital-code")
            .forEach { tag -> compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Add").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("digital-buyer-search").assertDoesNotExist()
        compose.onNodeWithTag("checkout-cart").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("checkout-products").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText(product.name).performScrollTo().assertIsDisplayed()
    }

    @Test fun loading_largeFonts_indicatorAndLabelCenteredInDialog() {
        showNarrowScreen { BusyDialog("id") }
        val dialog = compose.onNodeWithTag("busy-dialog").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithTag("busy-progress").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag("busy-label").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(dialog.center.x, progress.center.x, 1f)
        assertEquals(dialog.center.x, label.center.x, 1f)
        assertEquals(progress.top - dialog.top, dialog.bottom - label.bottom, 1f)
    }

    @Test fun debtCheckout_largeFontsIndonesian_customerInitialBalanceAndConfirmReachable() {
        val product = Product(id = 1, name = "Buku catatan", price = 50_000)
        val customer = Customer(id = 1, name = "Pelanggan dengan nama yang sangat panjang", phone = "081234567890")
        var completions = 0
        showNarrowScreen {
            CheckoutScreen(listOf(product), listOf(customer), listOf(CartLine(product)), "id", false, {},
                onComplete = { selected, _, method, paid, credit ->
                    assertEquals(customer, selected)
                    assertEquals("noncash", method)
                    assertEquals(10_000L, paid)
                    assertTrue(credit)
                    completions++
                })
        }
        fun scrollToTag(tag: String) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("checkout-cart").performClick()
        scrollToTag("checkout-credit")
        compose.onNodeWithTag("checkout-credit").performClick()
        scrollToTag("checkout-customer-1")
        compose.onNodeWithTag("checkout-customer-1").performClick()
        scrollToTag("checkout-payment-noncash")
        compose.onNodeWithTag("checkout-payment-noncash").performClick()
        scrollToTag("checkout-paid")
        compose.onNodeWithTag("checkout-paid").performTextReplacement("10000")
        scrollToTag("checkout-balance")
        compose.onNodeWithTag("checkout-balance").assertTextContains(money(40_000, "id"))
        scrollToTag("checkout-review")
        compose.onNodeWithTag("checkout-review").assertIsEnabled().performClick()
        listOf("checkout-confirm-customer", "checkout-confirm-initial", "checkout-confirm-balance").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("checkout-complete").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun debtSettlement_largeFontsIndonesian_fixedBalanceMethodAndConfirmReachable() {
        val product = Product(id = 1, name = "Buku", price = 50_000)
        val sale = Sale(id = 1, number = "0001", createdAt = 1_730_000_000_000, zoneId = "Asia/Jakarta",
            lines = listOf(CartLine(product)), customerId = 1, customerName = "Pelanggan dengan nama yang sangat panjang",
            customerPhone = "081234567890", subtotal = 50_000, discount = 0, total = 50_000,
            payment = "cash", paid = 10_000, shop = ShopSettings(language = "id"), isCredit = true)
        var settled = false
        showNarrowScreen {
            SaleDialog(sale, sale.shop, false, {}, {}, {}, onSettle = { method, _ -> assertEquals("noncash", method); settled = true })
        }
        compose.onNodeWithTag("sale-settle").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settlement-amount").performScrollTo().assertIsDisplayed().assertTextContains(money(40_000, "id"))
        compose.onNodeWithTag("settlement-noncash").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("settlement-confirm").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(settled) }
    }

    @Test fun productEditor_largeFonts_keyboardKeepsSaveAndFieldsReachable() {
        showNarrowScreen { ProductsScreen(emptyList(), "id") { _, _ -> } }
        compose.onNodeWithText("Tambah produk").performClick()
        compose.onNodeWithTag("product-kind").performScrollTo().performClick()
        compose.onNodeWithTag("product-kind-ELECTRICITY").performScrollTo().performClick()
        listOf("product-kind", "product-name", "product-price", "product-provider", "product-nominal", "product-cost", "product-category")
            .forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("product-price").performScrollTo().performClick().performTextReplacement("52000")
        val save = compose.onNodeWithTag("product-save").assertIsDisplayed().assertHasClickAction().fetchSemanticsNode().boundsInRoot
        val dialog = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        assertTrue(save.left >= dialog.left && save.right <= dialog.right && save.bottom <= dialog.bottom)
        compose.onNodeWithText("Batal").assertIsDisplayed().performClick()
        compose.onNodeWithTag("product-editor").assertDoesNotExist()
    }

    @Test fun indonesianSettingsTabsFillWidthEvenlyAndLanguageUsesSelection() {
        val settings = mutableStateOf(ShopSettings(language = "id"))
        compose.setContent {
            TokopadTheme {
                Box(Modifier.width(360.dp).height(640.dp).testTag("settings-container")) {
                    SettingsScreen(
                        settings = settings.value, printers = emptyList(), onSave = { settings.value = it },
                        onFindPrinters = {}, onTestPrint = {}, onExport = {}, onImport = {},
                        onSelectPrinter = {}, onConnectPrinter = {}, onOpenBluetooth = {},
                        printerStatus = "", section = "general", onSection = {},
                    )
                }
            }
        }
        val tabs = listOf("general", "receipt", "printer", "data").map {
            compose.onNodeWithTag("settings-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        tabs.forEach { assertEquals(tabs.first().width, it.width, 1f) }
        val density = compose.density.density
        assertEquals(20f * density, tabs.first().left, 1f)
        val container = compose.onNodeWithTag("settings-container").fetchSemanticsNode().boundsInRoot
        assertEquals(container.right - 20f * density, tabs.last().right, 1f)
        compose.onNodeWithText("Bahasa Indonesia").performScrollTo().assertIsSelected()
        compose.onNodeWithText("English").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("id", settings.value.language) }
        assertTrue(tabs.zipWithNext().all { (left, right) -> right.left >= left.right })
    }
}
