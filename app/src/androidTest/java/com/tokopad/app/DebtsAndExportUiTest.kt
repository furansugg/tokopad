package com.tokopad.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebtsAndExportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyDebtScreenShowsAllDateTotal() {
        compose.setContent { TokopadTheme { OutstandingDebtsScreen(emptyList(), "en") {} } }
        compose.onNodeWithTag("outstanding-debts-total").assertIsDisplayed()
        compose.onNodeWithText("No outstanding debts").assertIsDisplayed()
        compose.onNodeWithText("All customers · all dates").assertIsDisplayed()
    }

    @Test fun historyExportExplainsScopeAndInvokesAction() {
        var exported = false
        compose.setContent {
            TokopadTheme {
                HistoryScreen(emptyList(), "en", 0, 0, "search", {}, { _, _ -> }, {}, { exported = true })
            }
        }
        compose.onNodeWithText("Export includes all sales by sale date, including cancellations; search and the 500-result limit do not apply.").assertIsDisplayed()
        compose.onNodeWithTag("history-export-csv").performClick()
        assertTrue(exported)
    }
}
