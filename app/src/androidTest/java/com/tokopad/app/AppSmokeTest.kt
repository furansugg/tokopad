package com.tokopad.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
    private lateinit var electricityReceipt: Sale
    @get:Rule(order = 0)
    val sampleShop = object : ExternalResource() {
        override fun before() {
            Store(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
                store.saveSettings(ShopSettings(shopName = "The Daily Store"))
                val notebook = Product(name = "Pocket notebook", category = "Stationery", price = 18000, stock = 24, trackStock = true).let { it.copy(id = store.saveProduct(it)) }
                store.saveProduct(Product(name = "Internet 10 GB", price = 35000, kind = ProductKind.DATA, provider = "Telkomsel", packageName = "10 GB / 30 days"))
                val electricity = Product(name = "Electricity 50K", price = 52000, kind = ProductKind.ELECTRICITY, nominal = 50000, requiresCode = true).let { it.copy(id = store.saveProduct(it)) }
                val customer = Customer(name = "Nadia Putri", phone = "081234567890", meterNumber = "12345678901").let { it.copy(id = store.saveCustomer(it)) }
                (1..5).forEach { store.saveCustomer(Customer(name = "PLN buyer $it", meterNumber = "1234567800$it")) }
                store.checkout(listOf(CartLine(notebook, 2)), customer, 0, "cash", 50000)
                store.checkout(listOf(CartLine(notebook)), null, 0, "noncash", 18000)
                electricityReceipt = store.checkout(listOf(CartLine(electricity, destination = customer.meterNumber,
                    buyerName = customer.name, kwh = "33.5", code = "12345678901234567890", customerId = customer.id)), customer, 0, "cash", 60000)
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
        waitForText("Overview")
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("New sale").assertIsDisplayed()
        compose.onNodeWithText("The Daily Store").assertIsDisplayed()
        listOf("Home", "Checkout", "Products", "More").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("Apply dates").assertDoesNotExist()
        screenshot("home")
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText(electricityReceipt.receiptNumber).performScrollTo().performClick()
        compose.onNodeWithText("Receipt", substring = false).assertIsDisplayed()
        screenshot("invoice-pln")
        compose.onNodeWithText("Close").performClick()

        compose.onNodeWithTag("nav-checkout").performClick()
        compose.onNodeWithText("Find a product").assertIsDisplayed()
        screenshot("checkout")
        compose.onNodeWithTag("checkout-kind-ELECTRICITY").performClick()
        compose.onNodeWithText("Electricity 50K").performClick()
        screenshot("checkout-pln")
        compose.onNodeWithTag("digital-buyer-search").performTextReplacement("Nadia")
        compose.onNodeWithText("Nadia Putri").performScrollTo().performClick()
        screenshot("checkout-pln-selected")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("checkout-all-kinds").performClick()
        compose.onNodeWithTag("checkout-kind-PHYSICAL").performClick()
        compose.onNodeWithText("Pocket notebook").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("View cart").performClick()
        screenshot("cart")
        compose.onNodeWithTag("nav-products").performClick()
        compose.onNodeWithText("Discard").performClick()

        navigate("Products")
        compose.onNodeWithText("Add product").assertIsDisplayed()
        compose.onNodeWithText("Add product").assertIsDisplayed()
        compose.onNodeWithText("Search products").assertIsDisplayed()
        screenshot("products")
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithTag("product-kind").performClick()
        compose.onNodeWithTag("product-kind-ELECTRICITY").performScrollTo().performClick()
        screenshot("add-product-pln")
        compose.onNodeWithTag("product-kind").performScrollTo().performClick()
        compose.onNodeWithTag("product-kind-DATA").performScrollTo().performClick()
        screenshot("add-product-data")
        compose.onNodeWithText("Cancel").performClick()

        navigate("Customers")
        compose.onNodeWithText("Customers").assertIsDisplayed()
        compose.onNodeWithText("Add customer").assertIsDisplayed()
        compose.onNodeWithText("Search customers").assertIsDisplayed()

        navigate("Settings")
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsSelected()
        screenshot("settings")

        compose.onNodeWithTag("settings-printer").performClick()
        compose.onNodeWithText("Find paired printers").performScrollTo().assertIsDisplayed()
        screenshot("printer")
        compose.onNodeWithTag("settings-general").performClick()

        compose.onNodeWithText("Dark", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Save settings").performClick()
        waitForText("Settings")
        screenshot("settings-dark")
        navigate("Products")
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithTag("product-kind").performClick()
        compose.onNodeWithTag("product-kind-ELECTRICITY").performScrollTo().performClick()
        screenshot("add-product-pln-dark")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("nav-dashboard").performClick()
        waitForText("Overview")
        screenshot("home-dark")
        compose.onNodeWithTag("nav-checkout").performClick()
        screenshot("checkout-dark")
        compose.onNodeWithTag("checkout-kind-ELECTRICITY").performClick()
        compose.onNodeWithText("Electricity 50K").performClick()
        screenshot("checkout-pln-dark")
        compose.onNodeWithText("Cancel").performClick()
        navigate("Settings")
        compose.onNodeWithText("Light", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Save settings").performClick()
        waitForText("Settings")

        compose.onNodeWithText("Bahasa Indonesia").performScrollTo().performClick()
        compose.onNodeWithText("Simpan pengaturan").performClick()
        waitForText("Pengaturan")

        navigate("Beranda")
        waitForText("Ringkasan")
        compose.onNodeWithText("Ringkasan").assertIsDisplayed()
    }

    private fun navigate(label: String) {
        if (label == "Products") {
            compose.onNodeWithTag("nav-products").performClick()
            waitForText("Add product")
            return
        } else if (label in listOf("Customers", "Settings")) {
            compose.onNodeWithTag("nav-more").performClick()
            waitForText("Workspace")
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
