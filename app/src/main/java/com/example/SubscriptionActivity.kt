package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.ProSubscriptionPackage
import com.example.data.preferences.ThemeModeOption
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.browser.SubscriptionScreen
import com.example.ui.theme.ApexBrowserTheme
import kotlinx.coroutines.launch

class SubscriptionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        RenderNodeLogGuard.install()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialPromptReason = intent?.getStringExtra(EXTRA_PROMPT_REASON)
        val preferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            val preferences by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = BrowserPreferences()
            )
            val coroutineScope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val lifecycleOwner = LocalLifecycleOwner.current

            var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var selectedPackage by remember { mutableStateOf(ProSubscriptionPackage.MONTH) }
            var activationError by remember { mutableStateOf<String?>(null) }
            var promptReason by remember { mutableStateOf(initialPromptReason) }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        nowMillis = System.currentTimeMillis()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val darkTheme = when (preferences.themeMode) {
                ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
                ThemeModeOption.LIGHT -> false
                ThemeModeOption.DARK -> true
            }

            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
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

            val isProCurrentlyActive = preferences.isProCurrentlyActive(nowMillis)
            val isProExpired = preferences.isProExpired(nowMillis)

            ApexBrowserTheme(
                darkTheme = darkTheme,
                dynamicColor = preferences.dynamicColorEnabled,
                seedPreset = preferences.seedPreset
            ) {
                SubscriptionScreen(
                    isProActivated = isProCurrentlyActive,
                    isProExpired = isProExpired,
                    activeKey = preferences.proActivationKey,
                    activePackage = preferences.proPackage,
                    expiresAtMillis = preferences.proExpiresAtMillis,
                    remainingProMillis = preferences.remainingProMillis(nowMillis),
                    selectedPackage = selectedPackage,
                    onSelectPackage = { pkg ->
                        selectedPackage = pkg
                    },
                    remainingFreePlaysToday = preferences.remainingFreeAutomationPlaysToday(
                        nowMillis = nowMillis
                    ),
                    dailyPlayLimit = BrowserPreferences.FREE_DAILY_AUTOMATION_PLAY_LIMIT,
                    promptReason = promptReason,
                    activationError = activationError,
                    onActivateKey = { rawKey, pkg ->
                        val trimmed = rawKey.trim()
                        if (trimmed.isEmpty()) {
                            activationError = "Please paste or enter an activation key from the author."
                        } else {
                            coroutineScope.launch {
                                val currentNow = System.currentTimeMillis()
                                val effectivePkg = BrowserPreferences.resolvePackageForKey(trimmed, pkg)
                                val activated = preferencesRepository.activateProSubscription(
                                    rawKey = trimmed,
                                    selectedPackage = pkg,
                                    nowMillis = currentNow
                                )
                                nowMillis = currentNow
                                if (activated) {
                                    val resolved = effectivePkg ?: pkg
                                    selectedPackage = resolved
                                    activationError = null
                                    promptReason = null
                                    snackbarHostState.showSnackbar(
                                        "Apex Pro (${resolved.displayName}) activated for ${resolved.billingUnitLabel.lowercase()}!"
                                    )
                                } else {
                                    activationError = "Invalid activation key. Contact the author to get a valid key."
                                }
                            }
                        }
                    },
                    onClearError = { activationError = null },
                    onDeactivatePro = {
                        coroutineScope.launch {
                            preferencesRepository.deactivateProSubscription()
                            nowMillis = System.currentTimeMillis()
                            activationError = null
                            snackbarHostState.showSnackbar(
                                "Switched to Free plan (5 daily automation plays)."
                            )
                        }
                    },
                    onBack = { finish() },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    companion object {
        const val EXTRA_PROMPT_REASON = "extra_prompt_reason"

        fun createIntent(context: Context, promptReason: String? = null): Intent {
            return Intent(context, SubscriptionActivity::class.java).apply {
                if (!promptReason.isNullOrBlank()) {
                    putExtra(EXTRA_PROMPT_REASON, promptReason)
                }
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }
}
