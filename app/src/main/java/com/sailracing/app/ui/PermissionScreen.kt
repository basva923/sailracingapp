package com.sailracing.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sailracing.app.ui.components.ActionButton
import com.sailracing.app.ui.theme.RaceColors

/** Shown until the location permission is granted; the app is useless without GPS. */
@Composable
fun PermissionScreen(onRequest: () -> Unit, permanentlyDenied: Boolean, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sail Racing needs your position", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "The GPS gives the distance to the start line, your speed, heading and the wind estimate. " +
                "Nothing leaves the phone.",
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = RaceColors.Muted,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            ActionButton("Open app settings", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), containerColor = RaceColors.Info, contentColor = RaceColors.Black)
        } else {
            ActionButton("Allow location", onClick = onRequest, modifier = Modifier.fillMaxWidth(), containerColor = RaceColors.Info, contentColor = RaceColors.Black)
        }
    }
}
