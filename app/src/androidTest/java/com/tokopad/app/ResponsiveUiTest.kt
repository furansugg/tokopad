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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                products = listOf(product), customers = emptyList(), cart = emptyList(),
                language = "en", busy = false, onCart = {}, onComplete = { _, _, _, _ -> },
            )
        }
        compose.onNodeWithText(product.name).performScrollTo().assertIsDisplayed().performClick()
        listOf("digital-buyer-search", "digital-buyer-name", "digital-destination", "digital-kwh", "digital-code")
            .forEach { tag -> compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Add").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("digital-buyer-search").assertDoesNotExist()
        compose.onNodeWithTag("checkout-cart").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("checkout-products").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText(product.name).performScrollTo().assertIsDisplayed()
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
