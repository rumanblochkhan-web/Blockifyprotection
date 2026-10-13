package com.example.recoveryshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.PermissionType
import com.example.recoveryshield.ui.components.AccessibilityDisclosureDialog
import com.example.ui.theme.BlueDark
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.White

@Composable
fun SetupPermissionsScreen(
    permissionState: PermissionState,
    onBackClick: () -> Unit,
    onOpenPermission: (PermissionType) -> Unit,
    onCompleteSetup: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    val allPermissionsGranted = permissionState.accessibilityGranted &&
            permissionState.overlayGranted &&
            permissionState.batteryOptimizationIgnored &&
            permissionState.deviceAdminGranted

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
            .background(White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("permissions_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Back Arrow Icon Button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag("permissions_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Permissions",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Enable all permissions to activate full protection and blocking on your device.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Card 1: Accessibility Service (Essential)
            PermissionCard(
                icon = Icons.Default.AccessibilityNew,
                title = "Accessibility Service",
                description = "Primary protection engine: detects restricted content and triggers immediate intervention.",
                isGranted = permissionState.accessibilityGranted,
                testTag = "permission_card_accessibility",
                onToggle = {
                    if (permissionState.accessibilityGranted) {
                        onOpenPermission(PermissionType.ACCESSIBILITY_SERVICE)
                    } else {
                        showAccessibilityDisclosure = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Display Over Other Apps (Overlay)
            PermissionCard(
                icon = Icons.Default.Layers,
                title = "Display Over Other Apps",
                description = "Required to show the protection screen immediately when triggered.",
                isGranted = permissionState.overlayGranted,
                testTag = "permission_card_overlay",
                onToggle = { onOpenPermission(PermissionType.SYSTEM_ALERT_WINDOW) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Battery Optimization / Background Run (Essential)
            PermissionCard(
                icon = Icons.Default.BatterySaver,
                title = "Run in background (Always Active)",
                description = "Exempts app from battery optimizations so it runs 100% reliably in the background.",
                isGranted = permissionState.batteryOptimizationIgnored,
                testTag = "permission_card_battery",
                onToggle = { onOpenPermission(PermissionType.IGNORE_BATTERY_OPTIMIZATION) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 4: Device Admin Protection (Essential)
            PermissionCard(
                icon = Icons.Default.Shield,
                title = "Device Admin Protection",
                description = "Optional self-discipline & parental protection against impulsive uninstallation. Can be disabled anytime via Emergency Stop.",
                isGranted = permissionState.deviceAdminGranted,
                testTag = "permission_card_device_admin",
                onToggle = { onOpenPermission(PermissionType.DEVICE_ADMIN) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Continue Button
        Button(
            onClick = {
                if (allPermissionsGranted) {
                    onCompleteSetup()
                } else if (!permissionState.accessibilityGranted) {
                    showAccessibilityDisclosure = true
                } else {
                    onShowMessage("Allow permission to continue")
                }
            },
            enabled = true, // Keep enabled so it can show the tooltip on click
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp)
                .testTag("permissions_continue_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = White,
                contentColor = TextPrimary
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.25f)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(16.dp))
            .background(White)
            .clickable(onClick = onToggle)
            .padding(16.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon in blue circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BluePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // iOS-style Blue Switch
            Switch(
                checked = isGranted,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White,
                    checkedTrackColor = BluePrimary,
                    uncheckedThumbColor = White,
                    uncheckedTrackColor = BorderLight,
                    uncheckedBorderColor = BorderLight
                )
            )
        }
    }
}
