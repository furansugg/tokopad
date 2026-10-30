package com.tokopad.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private var saved: Product? = null

    private fun showEditor(product: Product? = null) {
        compose.setContent {
            TokopadTheme {
                ProductsScreen(listOfNotNull(product), "en") { value, done -> saved = value; done() }
            }
        }
        compose.onNodeWithText(if (product == null) "Add product" else "Edit product").performClick()
    }

    private fun selectKind(kind: ProductKind) {
        compose.onNodeWithTag("product-kind").performScrollTo().performClick()
        compose.onNodeWithTag("product-kind-${kind.name}").performScrollTo().performClick()
    }

    private fun replace(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun save() = compose.onNodeWithTag("product-save").assertIsDisplayed().performClick()

    @Test fun productTypes_showOnlyRelevantFields_stockTogglePreservesDraft() {
        showEditor()
        compose.onNodeWithTag("product-unit").assertExists()
        compose.onNodeWithTag("product-stock").assertDoesNotExist()
        compose.onNodeWithTag("product-provider").assertDoesNotExist()
        compose.onNodeWithText("Track stock").performScrollTo().performClick()
        replace("product-stock", "7")
        compose.onNodeWithText("Track stock").performScrollTo().performClick()
        compose.onNodeWithTag("product-stock").assertDoesNotExist()
        compose.onNodeWithText("Track stock").performScrollTo().performClick()
        compose.onNodeWithTag("product-stock").performScrollTo().assertTextContains("7")

        for (kind in ProductKind.values().filter { it != ProductKind.PHYSICAL }) {
            selectKind(kind)
            compose.onNodeWithTag("product-unit").assertDoesNotExist()
            compose.onNodeWithTag("product-stock").assertDoesNotExist()
            compose.onNodeWithTag("product-provider").assertExists()
            if (kind in listOf(ProductKind.PULSA, ProductKind.ELECTRICITY)) {
                compose.onNodeWithTag("product-nominal").assertExists()
                compose.onNodeWithTag("product-package").assertDoesNotExist()
            } else {
                compose.onNodeWithTag("product-package").assertExists()
                compose.onNodeWithTag("product-nominal").assertDoesNotExist()
            }
        }
        selectKind(ProductKind.PHYSICAL)
        compose.onNodeWithTag("product-stock").performScrollTo().assertTextContains("7")
        compose.runOnIdle { assertNull(saved) }
    }

    @Test fun newElectricity_defaultsProvider_separatesFaceValueAndPrice_savesWithoutPackage() {
        showEditor()
        selectKind(ProductKind.ELECTRICITY)
        compose.onNodeWithTag("product-provider").performScrollTo().assertTextContains("PLN")
        replace("product-name", "Token PLN 50.000")
        replace("product-price", "52000")
        replace("product-nominal", "50000")
        compose.onNodeWithText("Require token at checkout").performScrollTo().performClick()
        save()
        compose.runOnIdle {
            val product = checkNotNull(saved)
            assertEquals(ProductKind.ELECTRICITY, product.kind)
            assertEquals("PLN", product.provider)
            assertEquals(52_000L, product.price)
            assertEquals(50_000L, product.nominal)
            assertEquals("", product.packageName)
            assertNull(product.cost)
            assertTrue(product.requiresCode)
            assertFalse(product.trackStock)
        }
    }

    @Test fun hiddenLegacyFields_surviveEditingAndTypeSwitches() {
        val product = Product(id = 1, name = "PLN", price = 52000, kind = ProductKind.ELECTRICITY,
            provider = "Custom provider", packageName = "Legacy package", nominal = 50000, unit = "token", requiresCode = true)
        showEditor(product)
        compose.onNodeWithTag("product-package").assertDoesNotExist()
        replace("product-name", "Updated PLN")
        save()
        compose.runOnIdle { assertEquals(product.copy(name = "Updated PLN"), saved) }
        compose.onNodeWithText("Edit product").performClick()
        selectKind(ProductKind.DATA)
        compose.onNodeWithTag("product-package").performScrollTo().assertTextContains(product.packageName)
        compose.onNodeWithText("Require code at checkout").performScrollTo().assertIsDisplayed()
        replace("product-name", "Updated PLN")
        save()
        compose.runOnIdle {
            assertEquals(product.copy(kind = ProductKind.DATA, name = "Updated PLN"), saved)
        }
    }

    @Test fun invalidNamesAmountsAndStock_rejectedBeforeSave_acceptsStorageBoundaries() {
        showEditor()
        save()
        compose.runOnIdle { assertNull(saved) }
        replace("product-name", "a".repeat(201))
        compose.onNodeWithText("Use at most 200 characters.").performScrollTo().assertIsDisplayed()
        replace("product-price", "100")
        save()
        compose.runOnIdle { assertNull(saved) }
        replace("product-name", "商品")
        replace("product-price", (Store.MAX_AMOUNT + 1).toString())
        save()
        compose.runOnIdle { assertNull(saved) }
        replace("product-price", Store.MAX_AMOUNT.toString())
        replace("product-cost", (Store.MAX_AMOUNT + 1).toString())
        save()
        compose.runOnIdle { assertNull(saved) }
        replace("product-cost", "0")
        compose.onNodeWithText("Track stock").performScrollTo().performClick()
        replace("product-stock", (Store.MAX_QUANTITY + 1).toString())
        save()
        compose.runOnIdle { assertNull(saved) }
        replace("product-stock", Store.MAX_QUANTITY.toString())
        replace("product-category", "x".repeat(101))
        save()
        compose.runOnIdle { assertNull(saved) }
        replace("product-category", "x".repeat(100))
        save()
        compose.runOnIdle {
            val product = checkNotNull(saved)
            assertEquals("商品", product.name)
            assertEquals(Store.MAX_AMOUNT, product.price)
            assertEquals(0L, product.cost)
            assertEquals(Store.MAX_QUANTITY, product.stock)
        }
    }

    @Test fun disablingStockTracking_hidesFieldWithoutClearingExistingStock() {
        val product = Product(id = 1, name = "Rice", price = 100, trackStock = true, stock = 12)
        showEditor(product)
        replace("product-stock", "0")
        compose.onNodeWithText("Track stock").performScrollTo().performClick()
        compose.onNodeWithTag("product-stock").assertDoesNotExist()
        save()
        compose.runOnIdle { assertEquals(product.copy(trackStock = false), saved) }
    }

    @Test fun convertingStockedProduct_requiresConfirmation_cancelPreservesStock() {
        val product = Product(id = 1, name = "Rice", price = 100, trackStock = true, stock = 12,
            provider = "Legacy provider", packageName = "Legacy package", nominal = 50000)
        showEditor(product)
        selectKind(ProductKind.ELECTRICITY)
        save()
        compose.onNodeWithText("Remove physical stock?").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Keep editing").performClick()
        selectKind(ProductKind.PHYSICAL)
        compose.onNodeWithTag("product-stock").performScrollTo().assertTextContains("12")
        selectKind(ProductKind.ELECTRICITY)
        save()
        compose.onNodeWithText("Remove stock and save").performClick()
        compose.runOnIdle {
            assertEquals(product.copy(kind = ProductKind.ELECTRICITY, trackStock = false, stock = 0), saved)
        }
    }
}
