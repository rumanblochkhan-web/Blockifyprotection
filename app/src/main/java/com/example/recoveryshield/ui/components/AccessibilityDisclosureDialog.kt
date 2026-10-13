package com.example.recoveryshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.White

/**
 * Prominent In-App Disclosure Dialog for Android AccessibilityService API.
 * Meets Google Play Developer Policy requirements for Accessibility API:
 * 1. Appears within the app before directing user to system Accessibility settings.
 * 2. Clearly discloses what data is monitored and why.
 * 3. Clearly confirms that no data is stored or transmitted externally (100% on-device).
 * 4. Provides explicit "Agree & Turn On" and "Deny" buttons.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDeny,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("accessibility_disclosure_dialog"),
            color = White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BluePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Accessibility Disclosure",
                        tint = BluePrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Accessibility Service Disclosure",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Google Play Prominent In-App Disclosure",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BluePrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Body Section 1: Purpose
                Text(
                    text = "Why Blockify requires this service:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Blockify uses the Android AccessibilityService API solely for real-time adult content filtering, impulse prevention, and parental control to keep you focused and protected.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Body Section 2: Privacy Guarantees
                Text(
                    text = "What data is accessed & privacy guarantees:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                DisclosureBullet(
                    icon = Icons.Default.Shield,
                    text = "Real-Time Detection: Observes active window titles and browser URL bars locally to detect trigger terms and blocked apps."
                )
                Spacer(modifier = Modifier.height(8.dp))
                DisclosureBullet(
                    icon = Icons.Default.Lock,
                    text = "100% On-Device & Private: Screen text is processed entirely on your device. No keystrokes, personal messages, or photos are ever collected or stored."
                )
                Spacer(modifier = Modifier.height(8.dp))
                DisclosureBullet(
                    icon = Icons.Default.CheckCircle,
                    text = "No External Transmission: Zero accessibility data is sent to external servers, cloud databases, or third-party advertisers."
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons (Agree & Deny)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Deny Button
                    OutlinedButton(
                        onClick = onDeny,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("accessibility_disclosure_deny_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text(
                            text = "Deny",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Agree & Turn On Button
                    Button(
                        onClick = onAgree,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("accessibility_disclosure_agree_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            contentColor = White
                        )
                    ) {
                        Text(
                            text = "Agree & Turn On",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureBullet(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BluePrimary,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )
    }
}
