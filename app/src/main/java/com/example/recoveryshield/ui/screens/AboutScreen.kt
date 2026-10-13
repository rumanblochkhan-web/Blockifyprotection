package com.example.recoveryshield.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.BorderLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

/**
 * ShieldFocus About Screen matching Screen 24 in the reference design.
 */
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onNavigateToSupport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .testTag("about_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("about_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "About Blockify",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center App Icon & Blockify Version Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_safemind_shield),
                contentDescription = "Blockify Logo",
                modifier = Modifier
                    .size(80.dp)
                    .testTag("about_logo")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Blockify",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "v1.0.0",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "A personal protection tool designed to help you stay away from content that conflicts with your goals.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        AboutNavigationItem(
            title = "Privacy Policy",
            onClick = {
                Toast.makeText(context, "ShieldFocus operates with local on-device processing.", Toast.LENGTH_SHORT).show()
            },
            testTag = "about_privacy_policy_row"
        )

        HorizontalDivider(color = BorderLight, thickness = 0.8.dp)

        AboutNavigationItem(
            title = "Terms of Service",
            onClick = {
                Toast.makeText(context, "ShieldFocus is a voluntary personal focus tool.", Toast.LENGTH_SHORT).show()
            },
            testTag = "about_terms_row"
        )

        HorizontalDivider(color = BorderLight, thickness = 0.8.dp)

        AboutNavigationItem(
            title = "Open Source Licenses",
            onClick = {
                Toast.makeText(context, "Built with Kotlin, Jetpack Compose, and Material 3.", Toast.LENGTH_SHORT).show()
            },
            testTag = "about_licenses_row"
        )

        HorizontalDivider(color = BorderLight, thickness = 0.8.dp)

        AboutNavigationItem(
            title = "Contact Support",
            onClick = onNavigateToSupport,
            testTag = "about_support_row"
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AboutNavigationItem(
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = TextPrimary
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate to $title",
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
