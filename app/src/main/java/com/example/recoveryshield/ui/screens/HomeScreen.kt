package com.example.recoveryshield.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionPlanType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockify.emergencystop.EmergencyStopHomeButton
import com.example.R
import com.example.recoveryshield.domain.model.CooldownState
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.model.UserStats
import com.example.recoveryshield.ui.components.PrimaryButton
import com.example.recoveryshield.ui.components.SecondaryButton
import com.example.recoveryshield.ui.components.StatCard
import com.example.recoveryshield.ui.components.SystemStatusPill
import com.example.ui.theme.AmberLightSurface
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueDark
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightBorder
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedError
import com.example.ui.theme.RedLightSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

enum class HomeUiState {
    ACTIVE,
    INCOMPLETE,
    PAUSED,
    SERVICE_ISSUE,
    COOLDOWN,
    EXPIRED
}

/**
 * ShieldFocus Home Screen matching Screens 5, 6, 7, 8 in the reference design.
 */
@Composable
fun HomeScreen(
    protectionState: ProtectionState,
    permissionState: PermissionState,
    isServiceRunning: Boolean,
    onPowerClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onTriggerTestIntervention: () -> Unit = {},
    modifier: Modifier = Modifier,
    cooldownState: CooldownState = CooldownState(),
    onNavigateToCooldown: () -> Unit = onNavigateToSettings,
    userStats: UserStats = UserStats(),
    protectionPlan: ProtectionPlanInfo = ProtectionPlanInfo(),
    onWatchAdForCredit: () -> Unit = {},
    isAdLoading: Boolean = false,
    isAdLoaded: Boolean = true,
    adStatusMessage: String? = null,
    onNavigateToPlans: () -> Unit = {},
    onNavigateToEmergencyStop: () -> Unit = {},
    onTestEmergencyOpen: () -> Unit = {},
    antiUninstallEnabled: Boolean = false,
    onToggleAntiUninstall: (Boolean) -> Unit = {}
) {
    var showConsentDialog by remember { mutableStateOf(false) }
    var showEmergencyStopInfoDialog by remember { mutableStateOf(false) }
    var showExpiredPlanDialog by remember { mutableStateOf(false) }
    var liveTicker by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            liveTicker = System.currentTimeMillis()
        }
    }

    // Determine state
    val homeState = when {
        cooldownState.hasActiveRequest -> HomeUiState.COOLDOWN
        !permissionState.accessibilityGranted || !permissionState.overlayGranted -> HomeUiState.INCOMPLETE
        protectionPlan.isExpired -> HomeUiState.EXPIRED
        protectionState == ProtectionState.ERROR || (protectionState == ProtectionState.ACTIVE && !isServiceRunning) -> HomeUiState.SERVICE_ISSUE
        protectionState == ProtectionState.DISABLED || protectionState == ProtectionState.READY_BUT_INACTIVE || protectionState == ProtectionState.FIRST_RUN -> HomeUiState.PAUSED
        else -> HomeUiState.ACTIVE
    }

    if (showExpiredPlanDialog) {
        AlertDialog(
            onDismissRequest = { showExpiredPlanDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Subscription Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Your 20-day free trial has expired. To activate protection and block adult content & apps, please upgrade to Premium.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExpiredPlanDialog = false
                        onNavigateToPlans()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("View Plans", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("home_screen")
    ) {
        // Top Bar: "Protection" header + Emergency Stop Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Protection",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            EmergencyStopHomeButton(
                onClick = onNavigateToEmergencyStop
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Center Hero Component based on Home State
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (homeState) {
                HomeUiState.ACTIVE -> {
                    // Concentric Green Circles with Checkmark
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPowerClick
                            )
                            .testTag("home_hero_active"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer pulse ring
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(GreenLightSurface)
                        )
                        // Middle ring
                        Box(
                            modifier = Modifier
                                .size(126.dp)
                                .clip(CircleShape)
                                .background(GreenLightBorder)
                        )
                        // Inner circle
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = White,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Protection Active",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Your protection is active.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                HomeUiState.INCOMPLETE -> {
                    // Concentric Amber Circles with PriorityHigh / Exclamation
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .testTag("home_hero_incomplete"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(AmberLightSurface)
                        )
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(AmberWarning.copy(alpha = 0.25f))
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AmberWarning),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Incomplete",
                                tint = White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Finish Setup",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "One or more permissions are missing.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Open Settings",
                        onClick = onNavigateToSettings,
                        modifier = Modifier.height(42.dp),
                        testTag = "home_open_settings_button"
                    )
                }

                HomeUiState.PAUSED -> {
                    // Gray / Slate Circles with Pause / Play Icon
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPowerClick
                            )
                            .testTag("home_hero_paused"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                        )
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF64748B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Paused",
                                tint = White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Protection Inactive",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Protection is currently stopped. Tap below to turn on.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Turn Protection On",
                        onClick = onPowerClick,
                        modifier = Modifier.height(42.dp),
                        testTag = "home_resume_button"
                    )
                }

                HomeUiState.SERVICE_ISSUE -> {
                    // Red Circles with Warning
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .testTag("home_hero_issue"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(RedLightSurface)
                        )
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(RedError.copy(alpha = 0.25f))
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(RedError),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Issue",
                                tint = White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Protection needs attention",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Your protection service is not currently running.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Fix Protection",
                        onClick = onNavigateToSettings,
                        modifier = Modifier.height(42.dp),
                        testTag = "home_fix_protection_button"
                    )
                }

                HomeUiState.COOLDOWN -> {
                    // Cooling-off Period Active
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .testTag("home_hero_cooldown"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(AmberLightSurface)
                        )
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(AmberWarning.copy(alpha = 0.25f))
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AmberWarning),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Cooldown",
                                tint = White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Cooling-off Period Active",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val timeLeft = String.format("%02d:%02d:%02d", cooldownState.remainingHours, cooldownState.remainingMinutes, cooldownState.remainingSeconds)
                    Text(
                        text = "Protection remains active. Time left: $timeLeft",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SecondaryButton(
                        text = "Keep Protection",
                        onClick = onNavigateToCooldown,
                        modifier = Modifier.height(42.dp)
                    )
                }

                HomeUiState.EXPIRED -> {
                    // Red / Amber Circles with Lock Icon & Expired Status
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showExpiredPlanDialog = true }
                            )
                            .testTag("home_hero_expired"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(RedLightSurface)
                        )
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(RedError.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(RedError),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Expired",
                                tint = White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Protection Inactive",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "20-day free trial has expired. Upgrade to Premium to resume.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Upgrade to Premium",
                        onClick = onNavigateToPlans,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        testTag = "home_upgrade_plan_button"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Compact System Status Row: 3 pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SystemStatusPill(
                label = "Accessibility",
                status = if (permissionState.accessibilityGranted) "Connected" else "Missing",
                isGood = permissionState.accessibilityGranted,
                modifier = Modifier.weight(1f)
            )

            SystemStatusPill(
                label = "Overlay",
                status = if (permissionState.overlayGranted) "Connected" else "Missing",
                isGood = permissionState.overlayGranted,
                modifier = Modifier.weight(1f)
            )

            SystemStatusPill(
                label = "Service",
                status = if (isServiceRunning) "Running" else "Stopped",
                isGood = isServiceRunning,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Main Card: "You're protected."
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                .padding(18.dp)
                .testTag("home_main_protected_card")
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(BlueLightSurface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "You're protected.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Protection is active in the background.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                PrimaryButton(
                    text = "View Status",
                    onClick = onNavigateToStatus,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "home_view_status_button"
                )
            }
        }

        if (!protectionPlan.isPremiumActive) {
            Spacer(modifier = Modifier.height(18.dp))

            // PREMIUM UPGRADE CTA BANNER (Placed above Free Trial & Watch Ad)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, BlueLightBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onNavigateToPlans)
                    .padding(16.dp)
                    .testTag("home_premium_upgrade_cta")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upgrade to Premium",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$2.99/month for unlimited protection",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onNavigateToPlans,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            contentColor = White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = "Upgrade",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // PROTECTION DAYS CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, if (protectionPlan.isPremiumActive) BlueLightBorder else BorderLight, RoundedCornerShape(14.dp))
                .padding(18.dp)
                .testTag("protection_days_card")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (protectionPlan.isPremiumActive) BlueLightSurface else AmberLightSurface)
                        ) {
                            Icon(
                                imageVector = if (protectionPlan.isPremiumActive) Icons.Default.Star else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (protectionPlan.isPremiumActive) BluePrimary else AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            val titleText = when {
                                protectionPlan.isPremiumActive -> "Premium Protection"
                                protectionPlan.planType == ProtectionPlanType.FREE_TRIAL -> "20-Day Free Trial"
                                else -> "Protection Days"
                            }
                            Text(
                                text = titleText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val subtitleText = when {
                                protectionPlan.isPremiumActive && protectionPlan.isLifetimePremium -> "Lifetime unlimited protection active"
                                protectionPlan.isPremiumActive -> "Active premium subscription"
                                protectionPlan.isProtectionDayActive -> "Active 20-day free trial period"
                                else -> "20-day trial expired • Subscription required"
                            }
                            Text(
                                text = subtitleText,
                                fontSize = 12.sp,
                                color = if (protectionPlan.isProtectionDayActive) GreenDark else RedError,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (protectionPlan.isProtectionDayActive) GreenLightSurface else RedLightSurface)
                            .border(1.dp, if (protectionPlan.isProtectionDayActive) GreenLightBorder else RedError.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (protectionPlan.isPremiumActive) "PREMIUM" else if (protectionPlan.isProtectionDayActive) "ACTIVE" else "EXPIRED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (protectionPlan.isProtectionDayActive) GreenDark else RedError,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (!protectionPlan.isLifetimePremium) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            protectionPlan.isPremiumActive -> BlueLightSurface
                            protectionPlan.isProtectionDayActive -> AmberLightSurface
                            else -> RedLightSurface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                protectionPlan.isPremiumActive -> BlueLightBorder
                                protectionPlan.isProtectionDayActive -> AmberWarning.copy(alpha = 0.3f)
                                else -> RedError.copy(alpha = 0.3f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = when {
                                        protectionPlan.isPremiumActive -> BluePrimary
                                        protectionPlan.isProtectionDayActive -> AmberWarning
                                        else -> RedError
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        protectionPlan.isPremiumActive -> "Subscription Countdown:"
                                        protectionPlan.isProtectionDayActive -> "Trial Countdown:"
                                        else -> "Plan Status:"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = if (protectionPlan.isProtectionDayActive || protectionPlan.isPremiumActive) {
                                    protectionPlan.liveCountdownFormatted
                                } else {
                                    "Expired • Upgrade Required"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    protectionPlan.isPremiumActive -> BluePrimary
                                    protectionPlan.isProtectionDayActive -> AmberWarning
                                    else -> RedError
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ANTI-UNINSTALL PROTECTION CARD (Explicit user toggle with consent)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(
                    1.dp,
                    if (antiUninstallEnabled) GreenLightBorder else BorderLight,
                    RoundedCornerShape(14.dp)
                )
                .padding(18.dp)
                .testTag("anti_uninstall_protection_card")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (antiUninstallEnabled) GreenLightSurface else BlueLightSurface)
                        ) {
                            Icon(
                                imageVector = if (antiUninstallEnabled) Icons.Default.Security else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (antiUninstallEnabled) GreenSuccess else BluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Anti-Uninstall Guard",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (antiUninstallEnabled) GreenLightSurface else Color(0xFFF1F5F9))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (antiUninstallEnabled) "ACTIVE" else "OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (antiUninstallEnabled) GreenDark else TextTertiary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (antiUninstallEnabled)
                                    "Guards Blockify from impulsive deletion. Disable via Emergency Stop."
                                else
                                    "Optional voluntary lock to prevent impulsive deletion or stopping.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = antiUninstallEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showConsentDialog = true
                            } else {
                                showEmergencyStopInfoDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = White,
                            checkedTrackColor = GreenSuccess,
                            uncheckedThumbColor = White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.testTag("anti_uninstall_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // TODAY Section
        Text(
            text = "TODAY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val protectedTodayStr = if (homeState == HomeUiState.ACTIVE) {
                if (userStats.protectedDays > 0) "${userStats.protectedDays}" else "1"
            } else "0"

            StatCard(
                title = "Protected Today",
                value = protectedTodayStr,
                modifier = Modifier.weight(1f),
                testTag = "stat_protected_today"
            )

            StatCard(
                title = "Interventions",
                value = "${userStats.interventionsCount}",
                modifier = Modifier.weight(1f),
                testTag = "stat_interventions"
            )

            val timeStr = if (homeState == HomeUiState.ACTIVE) {
                if (userStats.protectionTimeHours > 0f) "${userStats.protectionTimeHours}h" else "0.1h"
            } else "0h"

            StatCard(
                title = "Protection Time",
                value = timeStr,
                modifier = Modifier.weight(1f),
                testTag = "stat_protection_time"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Support row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToSupport)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Need help? ",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = "Contact Support",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BluePrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
