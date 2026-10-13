package com.example.recoveryshield.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.recoveryshield.ui.components.PrimaryButton
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightBorder
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

private val supportiveMessages = listOf(
    "Your next decision matters.",
    "Stay with the goal you chose.",
    "You don't need to act on every impulse.",
    "One moment can change the direction of the next hour.",
    "Keep going."
)

@Composable
fun InterventionCountdownScreen(
    countdownSeconds: Int,
    onLeaveNow: () -> Unit,
    onReportMistake: () -> Unit = onLeaveNow,
    modifier: Modifier = Modifier
) {
    TransparentInterventionOverlay(
        countdownSeconds = countdownSeconds,
        onClose = onLeaveNow,
        onStayProtected = onLeaveNow,
        modifier = modifier
    )
}

/**
 * Transparent floating overlay matching Screens 11 & 12 in the reference design.
 * Semi-transparent scrim overlay with a floating white rounded card.
 */
@Composable
fun TransparentInterventionOverlay(
    countdownSeconds: Int,
    onClose: () -> Unit,
    onStayProtected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = 3f
    val isFinished = countdownSeconds <= 0
    val progress = ((totalSeconds - countdownSeconds.coerceIn(0, 3)) / totalSeconds).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "intervention_countdown_progress"
    )

    val messageIndex = (3 - countdownSeconds.coerceIn(1, 3)) % supportiveMessages.size
    val currentMessage = supportiveMessages[messageIndex]

    // Semi-transparent scrim overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x990F172A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("intervention_countdown_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Floating White Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Do not dismiss when tapping inside card */ }
                ),
            color = White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Shield and Close (X) button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_safemind_shield),
                            contentDescription = "Blockify",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Blockify",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .testTag("intervention_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = isFinished,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "intervention_state_transition"
                ) { finished ->
                    if (finished) {
                        // Screen 12: Intervention - End
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Let's move on.",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Returning you to safety.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            // Large green checkmark inside circular badge
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(GreenLightSurface)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(GreenSuccess)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            PrimaryButton(
                                text = "Continue to Safety",
                                onClick = onStayProtected,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "intervention_end_continue_button"
                            )
                        }
                    } else {
                        // Screen 11: Intervention Popup
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Stop",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "This content can harm you.\nYou're stronger than this.",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Circular countdown with smooth progress ring
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(130.dp)
                            ) {
                                // Background circular track
                                CircularProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier.size(130.dp),
                                    color = Color(0xFFF1F5F9),
                                    strokeWidth = 8.dp
                                )

                                // Animated foreground progress ring
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(130.dp),
                                    color = BluePrimary,
                                    strokeWidth = 8.dp
                                )

                                // Center Countdown Number
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$countdownSeconds",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "seconds",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Redirecting automatically...",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            androidx.compose.material3.Button(
                                onClick = onStayProtected,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = BluePrimary,
                                    contentColor = White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("intervention_leave_now_button")
                            ) {
                                Text(
                                    text = "Leave Now",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
