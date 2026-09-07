package com.sailracing.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.SailRacingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PermissionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun offersTheRequestOrTheSettingsShortcut() {
        val log = mutableListOf<String>()
        compose.setContent {
            SailRacingTheme {
                androidx.compose.foundation.layout.Column {
                    PermissionScreen(onRequest = { log += "request" }, permanentlyDenied = false, onOpenSettings = { log += "settings" })
                }
            }
        }
        compose.onNodeWithText("Allow location").assertIsDisplayed().performClick()
        assertEquals(listOf("request"), log)
    }

    @Test
    fun permanentlyDeniedOpensSettings() {
        val log = mutableListOf<String>()
        compose.setContent {
            SailRacingTheme { PermissionScreen(onRequest = { log += "request" }, permanentlyDenied = true, onOpenSettings = { log += "settings" }) }
        }
        compose.onNodeWithText("Open app settings").assertIsDisplayed().performClick()
        assertEquals(listOf("settings"), log)
    }
}
