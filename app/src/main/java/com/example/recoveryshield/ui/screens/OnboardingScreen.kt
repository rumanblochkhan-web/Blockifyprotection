package com.example.recoveryshield.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.PermissionType
import com.example.recoveryshield.ui.components.AccessibilityDisclosureDialog
import com.example.recoveryshield.ui.components.PermissionCard
import com.example.recoveryshield.ui.components.PrimaryButton
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightBorder
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

/**
 * ShieldFocus Onboarding flow covering Screens 1, 2, 3, and 4 from the reference design.
 */
@Composable
fun OnboardingScreen(
    permissionState: PermissionState,
    onOpenPermission: (PermissionType) -> Unit,
    onActivateProtection: () -> Unit,
    onSkipOnboarding: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(1) }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding_step_transition",
        modifier = modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding_screen")
    ) { currentStep ->
        when (currentStep) {
            1 -> OnboardingStep1(
                onGetStarted = { step = 2 },
                onSkip = onSkipOnboarding
            )
            2 -> OnboardingStep2(
                onContinue = { step = 3 }
            )
            3 -> OnboardingStep3(
                permissionState = permissionState,
                onOpenPermission = onOpenPermission,
                onContinue = { step = 4 },
                onShowMessage = onShowMessage
            )
            4 -> OnboardingStep4(
                onActivate = onActivateProtection
            )
        }
    }
}

/**
 * Screen 1: Welcome & Value Proposition
 */
@Composable
private fun OnboardingStep1(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row with skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onSkip,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Skip",
                    tint = TextTertiary
                )
            }
        }

        // Center Branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_safemind_shield),
                contentDescription = "Blockify Logo",
                modifier = Modifier
                    .size(96.dp)
                    .testTag("onboarding_logo")
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Blockify",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Protect Your Focus",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = BluePrimary
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Protect your focus.\nA quieter phone. A stronger routine.",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        // Bottom CTA & Pagination
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryButton(
                text = "Get Started",
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_get_started"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your privacy stays on your device.",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pagination Dots: Step 1 active [● ○ ○]
            PagerDots(activeDot = 0, totalDots = 3)
        }
    }
}

/**
 * Screen 2: How It Works
 */
@Composable
private fun OnboardingStep2(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Protection works\nin the background.",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Step 1
            NumberedStepRow(
                number = "1",
                title = "Enable Protection",
                description = "Turn on the voluntary protection system."
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step 2
            NumberedStepRow(
                number = "2",
                title = "Allow Required Permissions",
                description = "Give the app the necessary on-device access."
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step 3
            NumberedStepRow(
                number = "3",
                title = "Stay Protected",
                description = "The app will step in quietly when needed."
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Supporting Note Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlueLightSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, BlueLightBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "When protected content is detected, the app gives you a moment to step away.",
                    fontSize = 13.sp,
                    color = BluePrimary,
                    lineHeight = 18.sp
                )
            }
        }

        // Bottom CTA & Pagination
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_step2_continue"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Pagination Dots: Step 2 active [○ ● ○]
            PagerDots(activeDot = 1, totalDots = 3)
        }
    }
}

/**
 * Screen 3: Permissions Setup
 */
@Composable
private fun OnboardingStep3(
    permissionState: PermissionState,
    onOpenPermission: (PermissionType) -> Unit,
    onContinue: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scrollState = rememberScrollState()
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(scrollState)) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Set up your protection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Grant all required device capabilities to enable automated focus deflection.",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Accessibility Service
            PermissionCard(
                title = "Accessibility",
                explanation = "Required to keep you protected across supported apps.",
                statusText = if (permissionState.accessibilityGranted) "Enabled" else "Not Enabled",
                isEnabled = permissionState.accessibilityGranted,
                onEnableClick = {
                    if (permissionState.accessibilityGranted) {
                        onOpenPermission(PermissionType.ACCESSIBILITY_SERVICE)
                    } else {
                        showAccessibilityDisclosure = true
                    }
                },
                icon = Icons.Default.Visibility,
                testTag = "onboarding_permission_accessibility"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Display Over Other Apps
            PermissionCard(
                title = "Display Over Other Apps",
                explanation = "Required to show the protection screen when needed.",
                statusText = if (permissionState.overlayGranted) "Enabled" else "Not Enabled",
                isEnabled = permissionState.overlayGranted,
                onEnableClick = { onOpenPermission(PermissionType.SYSTEM_ALERT_WINDOW) },
                icon = Icons.Default.Layers,
                testTag = "onboarding_permission_overlay"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Battery Optimization Exemption
            PermissionCard(
                title = "Battery Optimization",
                explanation = "Allows 100% background run. Prevents system from killing the app.",
                statusText = if (permissionState.batteryOptimizationIgnored) "Exempted" else "Enable",
                isEnabled = permissionState.batteryOptimizationIgnored,
                onEnableClick = { onOpenPermission(PermissionType.IGNORE_BATTERY_OPTIMIZATION) },
                icon = Icons.Default.BatterySaver,
                testTag = "onboarding_permission_battery"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Device Admin Protection
            PermissionCard(
                title = "Device Admin Protection",
                explanation = "Optional self-discipline & parental protection against impulsive uninstallation. Can be disabled anytime via Emergency Stop.",
                statusText = if (permissionState.deviceAdminGranted) "Active" else "Enable",
                isEnabled = permissionState.deviceAdminGranted,
                onEnableClick = { onOpenPermission(PermissionType.DEVICE_ADMIN) },
                icon = Icons.Default.Shield,
                testTag = "onboarding_permission_device_admin"
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Bottom CTA & Pagination
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (allPermissionsGranted) {
                        onContinue()
                    } else if (!permissionState.accessibilityGranted) {
                        showAccessibilityDisclosure = true
                    } else {
                        onShowMessage("Allow permission to continue")
                    }
                },
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_step3_continue"),
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

            Spacer(modifier = Modifier.height(20.dp))

            // Pagination Dots: Step 3 active [○ ○ ●]
            PagerDots(activeDot = 2, totalDots = 3)
        }
    }
}

/**
 * Screen 4: Protection Ready
 */
@Composable
private fun OnboardingStep4(
    onActivate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        // Large status hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_safemind_shield),
                contentDescription = "ShieldFocus Ready",
                modifier = Modifier
                    .size(112.dp)
                    .testTag("onboarding_ready_shield")
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "You're ready.",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status pill "Protection READY" with green check
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(GreenLightSurface, RoundedCornerShape(50.dp))
                    .border(1.dp, GreenLightBorder, RoundedCornerShape(50.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("protection_ready_badge")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = GreenDark,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Protection READY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenDark
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Your protection system can now run in the background.",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Bottom CTA
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryButton(
                text = "Activate Protection",
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth(),
                testTag = "activate_protection_button"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumberedStepRow(
    number: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(BlueLightSurface, CircleShape)
                .border(1.dp, BlueLightBorder, CircleShape)
        ) {
            Text(
                text = number,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun PagerDots(
    activeDot: Int,
    totalDots: Int = 3
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalDots) { index ->
            val isActive = index == activeDot
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) BluePrimary else Color(0xFFCBD5E1))
            )
        }
    }
}
