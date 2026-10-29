package com.tokopad.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrandingSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themeStaysInDraft_logoRequiredBeforeSave() {
        val settings = mutableStateOf(ShopSettings())
        val section = mutableStateOf("general")
        var saves = 0
        compose.setContent {
            TokopadTheme {
                SettingsScreen(
                    settings = settings.value,
                    printers = emptyList(),
                    onSave = { settings.value = it; saves++ },
                    onFindPrinters = {}, onTestPrint = {}, onExport = {}, onImport = {},
                    onSelectPrinter = {}, onConnectPrinter = {}, onOpenBluetooth = {},
                    printerStatus = "", section = section.value, onSection = { section.value = it },
                )
            }
        }
        compose.onNodeWithText("Dark").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals("system", settings.value.theme)
            assertEquals(0, saves)
        }
        compose.onNodeWithTag("settings-receipt").performScrollTo().performClick()
        compose.onNodeWithText("Logo", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithText("Choose a logo or select Shop name before saving.").assertExists()
        compose.onNodeWithText("Name + logo").performScrollTo().performClick()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithText("Shop name", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Save settings").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("dark", settings.value.theme)
            assertEquals("name", settings.value.headerMode)
            assertEquals(1, saves)
        }
    }
}
