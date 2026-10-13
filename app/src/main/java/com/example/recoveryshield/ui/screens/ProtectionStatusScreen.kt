package com.example.recoveryshield.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionState
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

/**
 * ShieldFocus Protection Status Screen matching Screen 10 in the reference design.
 */
@Composable
fun ProtectionStatusScreen(
    protectionState: ProtectionState,
    permissionState: PermissionState,
    isServiceRunning: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    protectionPlan: ProtectionPlanInfo = ProtectionPlanInfo()
) {
    val isProtectionActive = (protectionState == ProtectionState.ACTIVE || protectionState == ProtectionState.COOLDOWN_ACTIVE) &&
            isServiceRunning && permissionState.accessibilityGranted && protectionPlan.isProtectionDayActive

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("protection_status_screen")
    ) {
        // Top bar with Back Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("protection_status_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Protection Status",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large status hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (isProtectionActive) GreenLightSurface else RedLightSurface)
                    .border(
                        2.dp,
                        if (isProtectionActive) GreenLightBorder else RedError.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isProtectionActive) GreenSuccess else RedError)
                ) {
                    Icon(
                        imageVector = if (isProtectionActive) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isProtectionActive) "PROTECTED" else "ATTENTION NEEDED",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isProtectionActive) "All systems operational" else "Review missing permissions below",
                fontSize = 13.sp,
                color = if (isProtectionActive) GreenDark else RedError,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Detailed Status Rows Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                .padding(vertical = 6.dp)
        ) {
            Column {
                StatusDetailRow(
                    label = "Protection",
                    value = if (isProtectionActive) "Active" else "Inactive",
                    isGood = isProtectionActive
                )
                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                StatusDetailRow(
                    label = "Accessibility",
                    value = if (permissionState.accessibilityGranted) "Enabled" else "Disabled",
                    isGood = permissionState.accessibilityGranted
                )
                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                StatusDetailRow(
                    label = "Overlay",
                    value = if (permissionState.overlayGranted) "Enabled" else "Disabled",
                    isGood = permissionState.overlayGranted
                )
                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                StatusDetailRow(
                    label = "Service",
                    value = if (isServiceRunning) "Running" else "Stopped",
                    isGood = isServiceRunning
                )
                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                StatusDetailRow(
                    label = "Last Check",
                    value = "Just now",
                    isGood = true,
                    hideIcon = true
                )
                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                StatusDetailRow(
                    label = "Detection",
                    value = if (isProtectionActive) "Active" else "Inactive",
                    isGood = isProtectionActive
                )
                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                StatusDetailRow(
                    label = if (protectionPlan.isPremiumActive) "Subscription" else "Free Trial",
                    value = protectionPlan.remainingTimeFormatted,
                    isGood = protectionPlan.isProtectionDayActive
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Protection is designed to run quietly in the background.",
            fontSize = 13.sp,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusDetailRow(
    label: String,
    value: String,
    isGood: Boolean,
    hideIcon: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!hideIcon) {
                Icon(
                    imageVector = if (isGood) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isGood) GreenSuccess else RedError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (hideIcon) TextSecondary else if (isGood) GreenDark else RedError
            )
        }
    }
}
