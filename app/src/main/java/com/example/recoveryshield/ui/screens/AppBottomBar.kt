package com.example.recoveryshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

/**
 * ShieldFocus 4-tab bottom navigation bar.
 * Destinations: HOME, PROGRESS, INSIGHTS, SETTINGS (Matching Screen 30 & Reference Design)
 */
@Composable
fun AppBottomBar(
    currentDestination: ScreenDestination,
    onNavigate: (ScreenDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHome = currentDestination == ScreenDestination.HOME ||
            currentDestination == ScreenDestination.PROTECTION_STATUS
    val isProgress = currentDestination == ScreenDestination.PROGRESS ||
            currentDestination == ScreenDestination.REWARDS ||
            currentDestination == ScreenDestination.FREE_TRIAL
    val isInsights = currentDestination == ScreenDestination.INSIGHTS
    val isSettings = currentDestination == ScreenDestination.SETTINGS ||
            currentDestination == ScreenDestination.PLANS ||
            currentDestination == ScreenDestination.ACCOUNT ||
            currentDestination == ScreenDestination.PRIVACY ||
            currentDestination == ScreenDestination.ABOUT ||
            currentDestination == ScreenDestination.CONTACT_SUPPORT ||
            currentDestination == ScreenDestination.DIAGNOSTICS

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = BorderLight, thickness = 0.8.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = isHome,
                testTag = "bottom_nav_home",
                onClick = { onNavigate(ScreenDestination.HOME) }
            )

            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "Progress",
                isSelected = isProgress,
                testTag = "bottom_nav_progress",
                onClick = { onNavigate(ScreenDestination.PROGRESS) }
            )

            BottomNavItem(
                icon = Icons.Default.Block,
                label = "Block",
                isSelected = isInsights,
                testTag = "bottom_nav_block",
                onClick = { onNavigate(ScreenDestination.INSIGHTS) }
            )

            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = isSettings,
                testTag = "bottom_nav_settings",
                onClick = { onNavigate(ScreenDestination.SETTINGS) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val tint = if (isSelected) BluePrimary else TextTertiary
    val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .testTag(testTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = fontWeight,
            color = tint
        )
    }
}
