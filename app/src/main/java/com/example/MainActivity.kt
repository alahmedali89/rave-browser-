package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.preferences.ThemeModeOption
import com.example.engine.DownloadNotificationHelper
import com.example.ui.browser.BrowserScreen
import com.example.ui.theme.ApexBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private var openDownloadsTrigger by mutableIntStateOf(0)
    private var pendingViewUrl by androidx.compose.runtime.mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        RenderNodeLogGuard.install()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingViewUrl = extractBrowsableUrl(intent)
        if (intent?.action == DownloadNotificationHelper.ACTION_OPEN_DOWNLOADS) {
            openDownloadsTrigger += 1
        }

        setContent {
            val browserViewModel: BrowserViewModel = viewModel(
                factory = BrowserViewModel.provideFactory(applicationContext)
            )
            val preferences by browserViewModel.preferencesState.collectAsStateWithLifecycle()
            val uiState by browserViewModel.uiState.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(activity = this)

            LaunchedEffect(pendingViewUrl) {
                val url = pendingViewUrl
                if (!url.isNullOrBlank()) {
                    browserViewModel.submitNavigation(url)
                    pendingViewUrl = null
                }
            }

            LaunchedEffect(openDownloadsTrigger) {
                if (openDownloadsTrigger > 0) {
                    browserViewModel.setDownloadsScreenOpen(true)
                }
            }

            val darkTheme = when (preferences.themeMode) {
                ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
                ThemeModeOption.LIGHT -> false
                ThemeModeOption.DARK -> true
            }
            val forceLightStatusBarIcons = uiState.networkBannerState != com.example.ui.viewmodel.NetworkStatusBannerState.HIDDEN

            LaunchedEffect(darkTheme, forceLightStatusBarIcons) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme || forceLightStatusBarIcons) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            ApexBrowserTheme(
                darkTheme = darkTheme,
                dynamicColor = preferences.dynamicColorEnabled,
                seedPreset = preferences.seedPreset
            ) {
                BrowserScreen(
                    viewModel = browserViewModel,
                    windowSizeClass = windowSizeClass
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractBrowsableUrl(intent)?.takeIf { it.isNotBlank() }?.let { url ->
            pendingViewUrl = url
        }
        if (intent.action == DownloadNotificationHelper.ACTION_OPEN_DOWNLOADS) {
            openDownloadsTrigger += 1
        }
    }

    private fun extractBrowsableUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_VIEW) {
            return intent.dataString
        }
        return null
    }
}
