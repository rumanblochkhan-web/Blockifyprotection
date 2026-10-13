package com.example.recoveryshield.protection.intervention

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.recoveryshield.protection.engine.ProtectionEngine
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.White

class InterventionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val engine = ProtectionEngine.getInstance(applicationContext)
                engine.interventionController.performNavigationAction()
                finish()
            }
        })

        setContent {
            MyApplicationTheme {
                val engine = ProtectionEngine.getInstance(applicationContext)
                val interventionState by engine.interventionController.interventionState.collectAsState()

                // When countdown ends or navigation away happens, close InterventionActivity immediately
                androidx.compose.runtime.LaunchedEffect(interventionState.isActive) {
                    if (!interventionState.isActive) {
                        finish()
                    }
                }

                InterventionScreenContent(
                    countdown = interventionState.countdownRemainingSeconds,
                    isAntiTamper = interventionState.isAntiTamper,
                    statusMessage = interventionState.statusMessage,
                    onLeaveClicked = {
                        engine.interventionController.performNavigationAction()
                        finish()
                    },
                    onCloseClicked = {
                        engine.interventionController.performNavigationAction()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun InterventionScreenContent(
    countdown: Int,
    isAntiTamper: Boolean = false,
    statusMessage: String = "",
    onLeaveClicked: () -> Unit,
    onCloseClicked: () -> Unit
) {
    // Total countdown is 3 seconds
    val totalSeconds = 3f
    val progress = ((totalSeconds - countdown.coerceIn(0, 3)) / totalSeconds).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "intervention_progress"
    )

    // Fully transparent background with no dim/scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("intervention_screen_box"),
        contentAlignment = Alignment.Center
    ) {
        // Floating Elegant Card with Cross (X) Button on top right
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)),
            color = White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar inside card with Close / Cross button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCloseClicked,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .testTag("intervention_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Blockify Shield Icon
                Image(
                    painter = painterResource(id = R.drawable.ic_safemind_shield),
                    contentDescription = "Blockify Shield",
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("intervention_shield_icon")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Heading: "Stop", "Protection Active", or "Access Blocked"
                Text(
                    text = when {
                        isAntiTamper -> "Protection Active"
                        statusMessage.startsWith("Blocked") -> "Access Blocked"
                        else -> "Stop"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = when {
                        isAntiTamper -> "Blockify is protecting you. Disabling or modifying settings is restricted."
                        statusMessage.isNotBlank() -> statusMessage
                        else -> "This content can harm you.\nYou're stronger than this."
                    },
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 5-second circular countdown timer with animated indicator
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .testTag("intervention_countdown_circle"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = BluePrimary,
                        trackColor = Color(0xFFE2E8F0),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$countdown",
                            fontSize = 36.sp,
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

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Redirecting automatically...",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                androidx.compose.material3.Button(
                    onClick = onLeaveClicked,
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
