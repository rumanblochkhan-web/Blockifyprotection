package com.example.recoveryshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.PermissionType
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.ui.components.AccessibilityDisclosureDialog
import com.example.recoveryshield.ui.components.SectionHeader
import com.example.recoveryshield.ui.components.SettingRow
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

/**
 * ShieldFocus Settings Screen matching Screen 18 in the reference design.
 */
@Composable
fun SettingsScreen(
    protectionState: ProtectionState,
    permissionState: PermissionState,
    onBackClick: () -> Unit,
    onToggleProtection: (Boolean) -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToCooldown: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToRewards: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToFreeTrial: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onOpenPermission: (PermissionType) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val isProtectionActive = protectionState == ProtectionState.ACTIVE || protectionState == ProtectionState.COOLDOWN_ACTIVE

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                onOpenPermission(PermissionType.ACCESSIBILITY_SERVICE)
            },
            onDeny = {
                showAccessibilityDisclosure = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("settings_screen")
    ) {
        // Header
        Text(
            text = "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION: PROTECTION
        SectionHeader(title = "Protection")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
        ) {
            Column {
                SettingRow(
                    title = "Protection Status",
                    subtitle = if (isProtectionActive) "Active in background" else "Paused",
                    icon = Icons.Default.Shield,
                    badge = if (isProtectionActive) "ACTIVE" else "PAUSED",
                    badgeColor = if (isProtectionActive) GreenSuccess else RedError,
                    onClick = onNavigateToStatus,
                    testTag = "setting_protection_status"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                // Enable / Disable Row with Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFEFF6FF), CircleShape)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Protection",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isProtectionActive) "Monitoring active apps" else "Protection is disabled",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isProtectionActive,
                        onCheckedChange = { checked ->
                            onToggleProtection(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = White,
                            checkedTrackColor = BluePrimary,
                            uncheckedThumbColor = White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.testTag("protection_enable_switch")
                    )
                }

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "System Settings & Apps Lock",
                    subtitle = if (isProtectionActive) "Settings/Apps guarded against tampering" else "Guarded when active",
                    icon = Icons.Default.Lock,
                    badge = if (isProtectionActive) "LOCKED" else "OFF",
                    badgeColor = if (isProtectionActive) GreenSuccess else TextTertiary,
                    onClick = onNavigateToStatus,
                    testTag = "setting_settings_lock"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: PERMISSIONS
        SectionHeader(title = "Permissions")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
        ) {
            Column {
                SettingRow(
                    title = "Accessibility Service",
                    subtitle = if (permissionState.accessibilityGranted) "Active and monitoring" else "Not enabled",
                    icon = Icons.Default.Visibility,
                    badge = if (permissionState.accessibilityGranted) "Enabled" else "Required",
                    badgeColor = if (permissionState.accessibilityGranted) GreenSuccess else RedError,
                    onClick = {
                        if (permissionState.accessibilityGranted) {
                            onOpenPermission(PermissionType.ACCESSIBILITY_SERVICE)
                        } else {
                            showAccessibilityDisclosure = true
                        }
                    },
                    testTag = "setting_permission_accessibility"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Display Over Other Apps",
                    subtitle = if (permissionState.overlayGranted) "Active" else "Not enabled",
                    icon = Icons.Default.Layers,
                    badge = if (permissionState.overlayGranted) "Enabled" else "Required",
                    badgeColor = if (permissionState.overlayGranted) GreenSuccess else RedError,
                    onClick = { onOpenPermission(PermissionType.SYSTEM_ALERT_WINDOW) },
                    testTag = "setting_permission_overlay"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Notifications",
                    subtitle = if (permissionState.notificationsGranted) "Active" else "Optional",
                    icon = Icons.Default.Notifications,
                    badge = if (permissionState.notificationsGranted) "Enabled" else "Optional",
                    badgeColor = if (permissionState.notificationsGranted) GreenSuccess else TextTertiary,
                    onClick = { onOpenPermission(PermissionType.POST_NOTIFICATIONS) },
                    testTag = "setting_permission_notifications"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Exempt Battery Optimizations",
                    subtitle = if (permissionState.batteryOptimizationIgnored) "Unrestricted background" else "Optimized (recommended to exempt)",
                    icon = Icons.Default.Shield,
                    badge = if (permissionState.batteryOptimizationIgnored) "Exempted" else "Not Exempted",
                    badgeColor = if (permissionState.batteryOptimizationIgnored) GreenSuccess else TextTertiary,
                    onClick = { onOpenPermission(PermissionType.IGNORE_BATTERY_OPTIMIZATION) },
                    testTag = "setting_permission_battery_opt"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: ACCOUNT
        SectionHeader(title = "Account")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
        ) {
            Column {
                SettingRow(
                    title = "Continue with Google",
                    subtitle = "Securely back up preferences",
                    icon = Icons.Default.AccountCircle,
                    onClick = onNavigateToAccount,
                    testTag = "setting_account"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Account Status & Plans",
                    subtitle = "Manage protection plan & status",
                    icon = Icons.Default.CardMembership,
                    onClick = onNavigateToPlans
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: SUPPORT & DIAGNOSTICS
        SectionHeader(title = "Support")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
        ) {
            Column {
                SettingRow(
                    title = "Help Center",
                    subtitle = "Answers to common questions",
                    icon = Icons.Default.HelpOutline,
                    onClick = onNavigateToSupport
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Contact Support",
                    subtitle = "Get assistance from our team",
                    icon = Icons.Default.Mail,
                    onClick = onNavigateToSupport,
                    testTag = "setting_contact_support"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Report a Problem",
                    subtitle = "Report an issue or false detection",
                    icon = Icons.Default.ReportProblem,
                    onClick = onNavigateToSupport
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Diagnostics",
                    subtitle = "View background engine state & logs",
                    icon = Icons.Default.Analytics,
                    onClick = onNavigateToDiagnostics,
                    testTag = "setting_diagnostics"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: PRIVACY
        SectionHeader(title = "Privacy")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
        ) {
            Column {
                SettingRow(
                    title = "Privacy Policy",
                    subtitle = "On-device processing commitment",
                    icon = Icons.Default.Lock,
                    onClick = onNavigateToPrivacy,
                    testTag = "setting_privacy"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Data & Privacy",
                    subtitle = "Local-first guarantee",
                    onClick = onNavigateToPrivacy
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Local Processing",
                    subtitle = "Zero server transmission of screen data",
                    badge = "LOCAL",
                    badgeColor = GreenSuccess,
                    onClick = onNavigateToPrivacy
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: APP
        SectionHeader(title = "App")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
        ) {
            Column {
                SettingRow(
                    title = "About Blockify",
                    subtitle = "Mission and voluntary focus protection",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToAbout,
                    testTag = "setting_about"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Version",
                    subtitle = "v1.0.0",
                    onClick = {}
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Open Source Licenses",
                    subtitle = "Third-party notices",
                    onClick = onNavigateToAbout
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
