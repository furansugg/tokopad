package com.tokopad.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
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
        compose.onNodeWithText("Your shop, in order.").assertIsDisplayed()
        screenshot("home")

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
        compose.onNodeWithText(label).performScrollTo().performClick()
        waitForText("• $label")
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
