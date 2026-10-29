package com.tokopad.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule(order = 0)
    val sampleShop = object : ExternalResource() {
        override fun before() {
            Store(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
                store.saveSettings(ShopSettings(shopName = "The Daily Store"))
                val notebook = Product(name = "Pocket notebook", category = "Stationery", price = 18000, stock = 24, trackStock = true).let { it.copy(id = store.saveProduct(it)) }
                store.saveProduct(Product(name = "Internet 10 GB", price = 35000, kind = ProductKind.DATA, provider = "Telkomsel", packageName = "10 GB / 30 days"))
                store.saveProduct(Product(name = "Electricity 50K", price = 52000, kind = ProductKind.ELECTRICITY, nominal = 50000, requiresCode = true))
                val customer = Customer(name = "Nadia Putri", phone = "081234567890").let { it.copy(id = store.saveCustomer(it)) }
                store.checkout(listOf(CartLine(notebook, 2)), customer, 0, "cash", 50000)
                store.checkout(listOf(CartLine(notebook)), null, 0, "noncash", 18000)
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun restoreEnglish() {
        Store(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
            store.saveSettings(store.settings().copy(language = "en"))
        }
    }

    @Test
    fun realLaunch_navigation_languageSwitch_andScreenshots() {
        waitForText("A little overview")
        compose.onNodeWithText("A little overview").assertIsDisplayed()
        compose.onNodeWithText("On device").assertIsDisplayed()
        listOf("Home", "Checkout", "History", "More").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("Apply dates").assertDoesNotExist()
        screenshot("home")
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("nav-checkout").performClick()
        compose.onNodeWithText("Find a product").assertIsDisplayed()
        screenshot("checkout")

        navigate("Products")
        compose.onNodeWithText("Products").assertIsDisplayed()
        compose.onNodeWithText("Add product").assertIsDisplayed()
        compose.onNodeWithText("Search products").assertIsDisplayed()
        screenshot("products")

        navigate("Customers")
        compose.onNodeWithText("Customers").assertIsDisplayed()
        compose.onNodeWithText("Add customer").assertIsDisplayed()
        compose.onNodeWithText("Search customers").assertIsDisplayed()

        navigate("Settings")
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsNotEnabled()
        screenshot("settings")

        compose.onNodeWithText("Indonesian").performScrollTo().performClick()
        compose.onNodeWithText("Simpan pengaturan").performScrollTo().performClick()
        waitForText("Pengaturan")

        navigate("Beranda")
        waitForText("Sekilas toko kamu")
        compose.onNodeWithText("Sekilas toko kamu").assertIsDisplayed()
    }

    private fun navigate(label: String) {
        if (label in listOf("Products", "Customers", "Settings")) {
            compose.onNodeWithTag("nav-more").performClick()
            waitForText("Your shop")
            compose.onNodeWithText(label).performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("nav-dashboard").performClick()
        }
        waitForText(label)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithText("Working…").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("Sedang diproses…").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun screenshot(screen: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val context = instrumentation.targetContext
        val file = File(requireNotNull(context.getExternalFilesDir(null)), "screenshots/tokopad-$screen.png")
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Cannot create screenshot directory" }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Cannot capture $screen" }
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot save $screen screenshot" }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
