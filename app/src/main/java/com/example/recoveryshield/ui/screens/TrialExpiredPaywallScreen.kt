package com.example.recoveryshield.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlueDark
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedError
import com.example.ui.theme.RedLightSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

/**
 * Mandatory locked Paywall Screen triggered when the 10-day free trial has expired.
 * Prevents access to app features until the user selects and activates a subscription plan.
 */
@Composable
fun TrialExpiredPaywallScreen(
    onSubscribe: (durationDays: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlanIndex by remember { mutableIntStateOf(1) } // 0: Weekly, 1: Monthly (Default), 2: Annual

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFCFF))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("trial_expired_paywall_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Lock Shield Hero Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(RedLightSurface)
                    .border(2.dp, RedError.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "10-Day Free Trial Expired",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Aapka 10 din ka free trial khatam ho chuka hai. Blockify protection ko jari rakhne ke liye subscription plan select karein.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Plan Options
            PaywallPlanCard(
                title = "Weekly Protection",
                price = "$2.99 / week",
                badge = null,
                isSelected = selectedPlanIndex == 0,
                onClick = { selectedPlanIndex = 0 }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PaywallPlanCard(
                title = "Monthly Pro (30 Days)",
                price = "$7.99 / month",
                badge = "MOST POPULAR",
                isSelected = selectedPlanIndex == 1,
                onClick = { selectedPlanIndex = 1 }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PaywallPlanCard(
                title = "Annual Shield (365 Days)",
                price = "$39.99 / year ($3.33/mo)",
                badge = "BEST VALUE • SAVE 60%",
                isSelected = selectedPlanIndex == 2,
                onClick = { selectedPlanIndex = 2 }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Value props
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    FeatureRow(text = "100% On-Device Adult Content Impulse Shield")
                    Spacer(modifier = Modifier.height(6.dp))
                    FeatureRow(text = "Anti-Uninstall & Voluntary Tamper Protection")
                    Spacer(modifier = Modifier.height(6.dp))
                    FeatureRow(text = "Streak, Milestone & Focus Diagnostics")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Subscribe Action Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val (days, planLabel) = when (selectedPlanIndex) {
                0 -> Pair(7, "Weekly ($2.99)")
                1 -> Pair(30, "Monthly ($7.99)")
                else -> Pair(365, "Annual ($39.99)")
            }

            Button(
                onClick = { onSubscribe(days) },
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("subscribe_and_unlock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Subscribe & Unlock ($planLabel)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Voluntary focus protection. Secure checkout via Google Play.",
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PaywallPlanCard(
    title: String,
    price: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) BluePrimary else BorderLight,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag("paywall_plan_${title.take(6)}"),
        color = if (isSelected) BlueLightSurface else CardBackground,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (badge.contains("BEST")) Color(0xFFFEF3C7) else GreenLightSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (badge.contains("BEST")) Color(0xFFD97706) else GreenDark
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = price,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) BluePrimary else TextSecondary
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        if (isSelected) BluePrimary else Color(0xFFCBD5E1),
                        CircleShape
                    )
                    .background(if (isSelected) BluePrimary else Color.Transparent)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = GreenSuccess,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
