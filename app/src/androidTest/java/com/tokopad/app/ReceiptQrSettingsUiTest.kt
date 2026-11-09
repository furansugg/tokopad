package com.tokopad.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReceiptQrSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun qrContent_validatesUtf8WithoutTruncation_andCanBeCleared() {
        val settings = mutableStateOf(ShopSettings(language = "en"))
        val section = mutableStateOf("receipt")
        compose.setContent {
            TokopadTheme {
                SettingsScreen(
                    settings = settings.value, printers = emptyList(),
                    onSave = { settings.value = it },
                    onFindPrinters = {}, onTestPrint = {}, onExport = {}, onImport = {},
                    onSelectPrinter = {}, onConnectPrinter = {}, onOpenBluetooth = {},
                    printerStatus = "", section = section.value, onSection = { section.value = it },
                )
            }
        }

        val field = compose.onNodeWithTag("receipt-qr-content")
        val limit = "é".repeat(100)
        val tooLong = limit + "é"
        field.performScrollTo().performTextInput(tooLong)
        field.assertTextContains(tooLong)
        compose.onNodeWithText("Maximum 200 UTF-8 bytes.").assertExists()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithTag("settings-general").performClick()
        compose.onNodeWithText("Check the QR text or URL in Receipt settings.").assertExists()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithTag("settings-receipt").performClick()
        field.performScrollTo().assertTextContains(tooLong).performTextClearance()
        field.performTextInput(limit)
        compose.onNodeWithText("Save settings").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(limit, settings.value.qrContent) }

        field.performScrollTo().performTextClearance()
        compose.onNodeWithText("Save settings").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("", settings.value.qrContent) }
    }

    @Test fun qrInstructions_areLocalized() {
        compose.setContent {
            TokopadTheme {
                SettingsScreen(
                    settings = ShopSettings(language = "id"), printers = emptyList(), onSave = {},
                    onFindPrinters = {}, onTestPrint = {}, onExport = {}, onImport = {},
                    onSelectPrinter = {}, onConnectPrinter = {}, onOpenBluetooth = {},
                    printerStatus = "", section = "receipt", onSection = {},
                )
            }
        }
        compose.onNodeWithText("Teks atau URL QR").performScrollTo().assertExists()
        compose.onNodeWithText("Masukkan teks atau URL untuk mencetak kode QR", substring = true).assertExists()
    }
}
