package com.example.recoveryshield.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recoveryshield.ui.components.PrimaryButton
import com.example.recoveryshield.ui.components.SettingRow
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.White

/**
 * ShieldFocus Support Screen matching Screen 21 in the reference design.
 */
@Composable
fun SupportScreen(
    onBackClick: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val sendEmail = {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@blockify.app")
                putExtra(Intent.EXTRA_SUBJECT, "Blockify Support Request")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Contact: support@blockify.app", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("support_screen")
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
                text = "Need Help?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Help Topics List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
        ) {
            Column {
                SettingRow(
                    title = "Contact Support",
                    subtitle = "Get direct help from our team",
                    icon = Icons.Default.Email,
                    onClick = sendEmail,
                    testTag = "support_contact_row"
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Troubleshooting",
                    subtitle = "Quick solutions for common questions",
                    icon = Icons.Default.Build,
                    onClick = {
                        Toast.makeText(context, "Ensure battery optimization is disabled for Blockify.", Toast.LENGTH_LONG).show()
                    }
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Permission Help",
                    subtitle = "Steps to enable Accessibility and Overlay",
                    icon = Icons.Default.Security,
                    onClick = onNavigateToPermissions
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Protection Not Working",
                    subtitle = "Verify service and background execution",
                    icon = Icons.Default.HelpOutline,
                    onClick = {
                        Toast.makeText(context, "Check if Accessibility service is running in system settings.", Toast.LENGTH_LONG).show()
                    }
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f), thickness = 0.6.dp)

                SettingRow(
                    title = "Account Help",
                    subtitle = "Questions about your data or preferences",
                    icon = Icons.Default.Person,
                    onClick = sendEmail
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Still Need Help Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Still need help?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Send a support request and our team will respond promptly.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "Contact Support",
                    onClick = sendEmail,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "bottom_contact_support_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
