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
import androidx.compose.material.icons.filled.Schedule
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
import com.example.recoveryshield.domain.model.CooldownState
import com.example.recoveryshield.ui.components.PrimaryButton
import com.example.recoveryshield.ui.components.SecondaryButton
import com.example.ui.theme.AmberLightSurface
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.RedError
import com.example.ui.theme.RedLightSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

/**
 * ShieldFocus Cooling-off / Disable Protection Screen matching Screen 20 in the reference design.
 */
@Composable
fun DisableScreen(
    cooldownState: CooldownState,
    onBackClick: () -> Unit,
    onCancelRequest: () -> Unit,
    onConfirmDisable: () -> Unit,
    onRequestCooldown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRunning = cooldownState.hasActiveRequest
    val isReadyToConfirm = cooldownState.isExpiredAwaitingConfirmation
    val formattedRemainingTime = String.format("%02d:%02d:%02d", cooldownState.remainingHours, cooldownState.remainingMinutes, cooldownState.remainingSeconds)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("disable_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Top App Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("disable_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = if (isRunning) "Cooling-off Period" else "Pause Protection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Header
            Text(
                text = if (isRunning) "Protection remains active" else "Do you want to pause protection?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Protection will remain active during the cooling-off period to safeguard against impulsive disablement.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Cooling-off Period Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) AmberLightSurface else BlueLightSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isRunning) AmberWarning else BluePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Cooling-off Period",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isRunning) formattedRemainingTime else "02:00:00",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isReadyToConfirm)
                            "Waiting period finished. You can now confirm your decision."
                        else
                            "After the waiting period, you'll be asked to confirm your decision.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Action Buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isReadyToConfirm) {
                PrimaryButton(
                    text = "Confirm & Disable Protection",
                    onClick = onConfirmDisable,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "confirm_disable_button"
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(
                    text = "Keep Protection On",
                    onClick = onCancelRequest,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isRunning) {
                PrimaryButton(
                    text = "Keep Protection",
                    onClick = onCancelRequest,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "keep_protection_button"
                )
            } else {
                PrimaryButton(
                    text = "Request Disable",
                    onClick = onRequestCooldown,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "request_disable_button"
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(
                    text = "Keep Protection On",
                    onClick = onBackClick,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "keep_protection_on_button"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
