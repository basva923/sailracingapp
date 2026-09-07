package com.sailracing.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sailracing.app.di.appGraph
import com.sailracing.app.race.RaceService
import com.sailracing.app.ui.PermissionScreen
import com.sailracing.app.ui.RaceViewModel
import com.sailracing.app.ui.SailRacingApp
import com.sailracing.app.ui.theme.SailRacingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }.getOrDefault("")
        setContent {
            SailRacingTheme {
                val viewModel: RaceViewModel = viewModel(factory = RaceViewModel.Factory(appGraph))
                AppRoot(viewModel = viewModel, versionName = versionName)
            }
        }
    }

    /** Gates the app behind the location permission and keeps the session and screen alive while granted. */
    @Composable
    private fun AppRoot(viewModel: RaceViewModel, versionName: String) {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasLocationPermission()) }
        var askedOnce by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askedOnce = true
            granted = hasLocationPermission()
        }
        val request = {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
            launcher.launch(permissions.toTypedArray())
        }

        if (!granted) {
            LaunchedEffect(Unit) { if (!askedOnce) request() }
            val permanentlyDenied = askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            PermissionScreen(
                onRequest = request,
                permanentlyDenied = permanentlyDenied,
                onOpenSettings = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                },
            )
            return
        }

        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val running by viewModel.isRunning.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.ensureRunning()
            RaceService.start(context)
        }
        DisposableEffect(settings.keepScreenOn, running) {
            if (settings.keepScreenOn && running) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        SailRacingApp(viewModel = viewModel, versionName = versionName)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
