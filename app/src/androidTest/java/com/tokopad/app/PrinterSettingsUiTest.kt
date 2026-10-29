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

class PrinterSettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun printerSelection_savesImmediately_testsOnlyOnRequest_andSurvivesSectionChanges() {
        val printer = "Thermal 58" to "AA:BB:CC:DD:EE:FF"
        val settings = mutableStateOf(ShopSettings(language = "en"))
        val section = mutableStateOf("printer")
        var selectionSaves = 0
        var settingsSaves = 0
        val connections = mutableListOf<String>()
        val prints = mutableListOf<String>()

        compose.setContent {
            TokopadTheme {
                SettingsScreen(
                    settings = settings.value,
                    printers = listOf(printer),
                    onSave = {
                        settings.value = it
                        settingsSaves++
                    },
                    onFindPrinters = {},
                    onTestPrint = { prints.add(settings.value.printerAddress) },
                    onExport = {},
                    onImport = {},
                    onSelectPrinter = {
                        settings.value = settings.value.copy(printerAddress = it)
                        selectionSaves++
                    },
                    onConnectPrinter = { connections.add(settings.value.printerAddress) },
                    onOpenBluetooth = {},
                    printerStatus = "",
                    section = section.value,
                    onSection = { section.value = it },
                )
            }
        }

        compose.onNodeWithTag("settings-printer").assertIsSelected()
        compose.onNodeWithText("Test connection").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Test print").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Save settings").assertDoesNotExist()
        compose.onNodeWithText("${printer.first}\n${printer.second}", substring = false)
            .performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(printer.second, settings.value.printerAddress)
            assertEquals(1, selectionSaves)
            assertEquals(0, settingsSaves)
            assertEquals(emptyList<String>(), connections)
            assertEquals(emptyList<String>(), prints)
        }

        compose.onNodeWithText("Test connection").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(printer.second), connections)
            assertEquals(emptyList<String>(), prints)
        }
        compose.onNodeWithText("Test print").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(printer.second), prints) }

        listOf("receipt", "general").forEach { destination ->
            compose.onNodeWithTag("settings-$destination").performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(printer.second, settings.value.printerAddress) }
            compose.onNodeWithTag("settings-printer").performClick().assertIsSelected()
            compose.onNodeWithText("${printer.first}\n${printer.second}", substring = false)
                .performScrollTo().assertIsSelected()
            compose.onNodeWithText("Test connection").performScrollTo().assertIsEnabled()
        }
        compose.runOnIdle {
            assertEquals(1, selectionSaves)
            assertEquals(0, settingsSaves)
            assertEquals(listOf(printer.second), connections)
            assertEquals(listOf(printer.second), prints)
        }
    }
}
