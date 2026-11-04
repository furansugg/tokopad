package com.tokopad.app

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HistoryDateRangeTest {
    private lateinit var today: LocalDate
    private lateinit var todaySale: Sale
    private lateinit var boundarySale: Sale
    private lateinit var olderSale: Sale

    @get:Rule(order = 0)
    val shop = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase("tokopad.db")
            today = LocalDate.now()
            Store(context).use { store ->
                store.saveSettings(ShopSettings(shopName = "History Range Store", language = "en", theme = "light"))
                val product = Product(name = "Range item", price = 10_000)
                    .let { it.copy(id = store.saveProduct(it)) }
                todaySale = store.checkout(listOf(CartLine(product)), null, 0, "cash", 10_000)
                boundarySale = store.checkout(listOf(CartLine(product)), null, 0, "cash", 10_000)
                olderSale = store.checkout(listOf(CartLine(product)), null, 0, "cash", 10_000)

                val zone = ZoneId.systemDefault()
                val times = mapOf(
                    todaySale.id to today.atStartOfDay(zone).toInstant().toEpochMilli(),
                    boundarySale.id to today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli(),
                    olderSale.id to (today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli() - 1),
                )
                val envelope = JSONObject(store.exportBackup())
                val payload = JSONObject(envelope.getString("payload"))
                val sales = payload.getJSONArray("sales")
                for (index in 0 until sales.length()) {
                    val sale = sales.getJSONObject(index)
                    sale.put("createdAt", times.getValue(sale.getLong("id")))
                }
                val text = payload.toString()
                val checksum = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                store.restoreBackup(envelope.put("payload", text).put("sha256", checksum).toString())
                Store.validateBackup(store.exportBackup())
            }
        }

        override fun after() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            instrumentation.targetContext.deleteDatabase("tokopad.db")
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun historyDefaultsToLast30Days_latestFirst_withoutChangingDashboard() {
        waitForText("Overview")
        assertTodayDashboard()
        openHistory()

        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        val range = "${today.minusDays(29).format(formatter)} – ${today.format(formatter)}"
        waitForText(range)
        // Header, search and explanation occupy the first three lazy-list items.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText(todaySale.receiptNumber).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText(boundarySale.receiptNumber).assertIsDisplayed()

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Receipt or customer name").performTextReplacement(olderSale.receiptNumber)
        compose.onNodeWithText("Search").performClick()
        dismissKeyboard()
        waitForText("No matching transactions")
        compose.onNodeWithText("Receipt or customer name").performTextReplacement("")
        compose.onNodeWithText("Search").performClick()
        dismissKeyboard()
        waitUntilNotBusy()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        waitForText(todaySale.receiptNumber)

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText(range).performClick()
        waitForText("Choose dates")
        val todayDay = hasText(today.dayOfMonth.toString()) and hasClickAction() and hasAnyAncestor(isDialog())
        compose.onNode(todayDay).performClick()
        compose.onNode(todayDay).performClick()
        compose.onNodeWithText("Apply dates").performClick()
        waitForText("Today")
        waitUntilNotBusy()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        waitForText(todaySale.receiptNumber)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Receipt or customer name").performTextReplacement(boundarySale.receiptNumber)
        compose.onNodeWithText("Search").performClick()
        dismissKeyboard()
        waitForText("No matching transactions")
        compose.onNodeWithText("Receipt or customer name").performTextReplacement("")
        compose.onNodeWithText("Search").performClick()
        dismissKeyboard()
        waitUntilNotBusy()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        waitForText(todaySale.receiptNumber)

        compose.onNodeWithTag("nav-dashboard").performClick()
        waitForText("Overview")
        assertTodayDashboard()
        openHistory()
        waitForText(range)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText(boundarySale.receiptNumber).assertIsDisplayed()
    }

    private fun assertTodayDashboard() {
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithTag("dashboard-received").performScrollTo().assertTextContains(money(10_000, "en"))
    }

    private fun openHistory() {
        compose.onNodeWithTag("nav-more").performClick()
        waitForText("Workspace")
        compose.onNodeWithText("History").performScrollTo().performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithText("Working…").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("Sedang diproses…").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private fun dismissKeyboard() {
        compose.runOnUiThread {
            val activity = compose.activity
            val input = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }

    private fun waitUntilNotBusy() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Working…").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("Sedang diproses…").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }
}
