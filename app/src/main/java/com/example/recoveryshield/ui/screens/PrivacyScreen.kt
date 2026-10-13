package com.example.recoveryshield.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

/**
 * ShieldFocus Privacy Screen matching Screen 23 in the reference design.
 */
@Composable
fun PrivacyScreen(
    onBackClick: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showTermsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("privacy_screen")
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Privacy",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Your privacy matters.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Blockify operates under strict on-device data isolation principles.",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3 Core Privacy Cards
        PrivacyPillarCard(
            title = "LOCAL FIRST",
            description = "Protection processing is designed to happen exclusively on your device. Text, UI nodes, and screen triggers never leave your hardware.",
            icon = Icons.Default.Lock
        )

        Spacer(modifier = Modifier.height(14.dp))

        PrivacyPillarCard(
            title = "MINIMAL DATA",
            description = "Only information strictly needed for the app's operation is temporarily retained in local memory.",
            icon = Icons.Default.Security
        )

        Spacer(modifier = Modifier.height(14.dp))

        PrivacyPillarCard(
            title = "YOUR CONTROL",
            description = "You control every permission the app uses. You can revoke or review permissions anytime in system settings.",
            icon = Icons.Default.Tune
        )

        Spacer(modifier = Modifier.height(14.dp))

        PrivacyPillarCard(
            title = "VOLUNTARY COMMITMENT & UNINSTALL GUARD",
            description = "If you voluntarily turn on Anti-Uninstall Protection, Blockify guards your focus commitment. If you attempt to access App Info or remove the app, a 3-second reminder appears. The designated path to safely disable protection or uninstall is through Emergency Stop via support verification code.",
            icon = Icons.Default.Security
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy Links Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
        ) {
            Column {
                PrivacyLinkRow(
                    label = "Terms of Service & Voluntary Agreement",
                    onClick = { showTermsDialog = true }
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                PrivacyLinkRow(
                    label = "Privacy Policy",
                    onClick = { showTermsDialog = true }
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                PrivacyLinkRow(
                    label = "Data & Privacy Controls",
                    onClick = {
                        Toast.makeText(context, "All statistics are stored locally using encrypted preferences.", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                PrivacyLinkRow(
                    label = "Manage Permissions",
                    onClick = onNavigateToPermissions
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showTermsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Terms & Protection Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "1. Voluntary Opt-In Protection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Blockify provides recovery and focus monitoring strictly upon user consent. You choose which features to activate, including keywords, adult content blockers, and voluntary Anti-Uninstall Protection.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "2. Anti-Uninstall Guard & Reminders",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When you explicitly enable Anti-Uninstall Protection, a 3-second safeguard reminder alerts you if Blockify's App Info is accessed. This gives you time to pause impulsive deletion and maintain your commitment.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "3. Emergency Stop Exit Route",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To cleanly stop protection, unlock settings, or uninstall Blockify when Anti-Uninstall Guard is active, use the Emergency Stop feature inside the app. By contacting support, you receive an authorized unlock code that completely deactivates all guards.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "4. Zero Cloud Tracking",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All text inspection, accessibility events, and timer logic operate 100% locally on your device without transmitting personal data to external servers.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { showTermsDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("I Understand & Agree", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun PrivacyPillarCard(
    title: String,
    description: String,
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(16.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BlueLightSurface)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
private fun PrivacyLinkRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
