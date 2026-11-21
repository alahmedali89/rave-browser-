package com.example.engine

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.R

data class DefaultBrowserStatus(
    val isApexDefault: Boolean = false,
    val currentDefaultName: String = "None selected",
    val currentDefaultPackage: String? = null
)

object DefaultBrowserHelper {

    private val systemPickerPackages = setOf(
        "android",
        "com.android.internal.app",
        "com.android.intentresolver"
    )

    fun getStatus(context: Context): DefaultBrowserStatus {
        val ownPackage = context.packageName
        val appLabel = runCatching { context.getString(R.string.app_name) }
            .getOrDefault("Apex Browser")
            .ifBlank { "Apex Browser" }

        val isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager != null &&
                    roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
            }.getOrDefault(false)
        } else {
            false
        }

        if (isRoleHeld) {
            return DefaultBrowserStatus(
                isApexDefault = true,
                currentDefaultName = appLabel,
                currentDefaultPackage = ownPackage
            )
        }

        val resolved = resolveDefaultHttpHandler(context)
        val resolvedPkg = resolved?.activityInfo?.packageName
        if (resolvedPkg == ownPackage) {
            return DefaultBrowserStatus(
                isApexDefault = true,
                currentDefaultName = appLabel,
                currentDefaultPackage = ownPackage
            )
        }

        if (resolvedPkg.isNullOrBlank() || resolvedPkg in systemPickerPackages) {
            return DefaultBrowserStatus(
                isApexDefault = false,
                currentDefaultName = "None selected",
                currentDefaultPackage = null
            )
        }

        val label = runCatching {
            resolved.loadLabel(context.packageManager).toString().trim()
        }.getOrDefault("")
            .ifBlank { resolvedPkg }

        return DefaultBrowserStatus(
            isApexDefault = false,
            currentDefaultName = label,
            currentDefaultPackage = resolvedPkg
        )
    }

    fun createRequestOrSettingsIntent(
        context: Context,
        preferSystemSettingsWhenDefault: Boolean = true
    ): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleIntent = runCatching {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    val alreadyHeld = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                    if (!alreadyHeld || !preferSystemSettingsWhenDefault) {
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.getOrNull()
            if (roleIntent != null) return roleIntent
        }

        val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        if (defaultAppsIntent.resolveActivity(context.packageManager) != null) {
            return defaultAppsIntent
        }

        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }

    private fun resolveDefaultHttpHandler(context: Context): ResolveInfo? {
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()
    }
}
