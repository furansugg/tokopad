package com.tokopad.app

import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun explicitThemes_ignoreSystemNightMode() {
        checkThemes(listOf("light" to false, "light" to true, "dark" to false, "dark" to true))
    }

    @Test
    fun systemTheme_followsConfigurationChanges() {
        checkThemes(listOf("system" to false, "system" to true, "system" to false))
    }

    private fun checkThemes(cases: List<Pair<String, Boolean>>) {
        val selected = mutableStateOf(cases.first())
        lateinit var colors: ColorScheme
        compose.setContent {
            val (theme, systemDark) = selected.value
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                TokopadTheme(theme = theme) {
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { colors = scheme }
                }
            }
        }
        cases.forEach { (theme, systemDark) ->
            compose.runOnIdle { selected.value = theme to systemDark }
            compose.runOnIdle {
                val dark = theme == "dark" || (theme == "system" && systemDark)
                val context = "theme=$theme, systemDark=$systemDark"
                assertTrue("Background: $context", if (dark) colors.background.luminance() < 0.1f else colors.background.luminance() > 0.9f)
                assertTrue("Foreground: $context", if (dark) colors.onSurface.luminance() > 0.9f else colors.onSurface.luminance() < 0.1f)
            }
        }
    }
}
