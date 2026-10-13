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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recoveryshield.domain.model.UserStats
import com.example.recoveryshield.ui.components.EmptyStateView
import com.example.recoveryshield.ui.components.StatCard
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightBorder
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

enum class ProgressFilter {
    TODAY,
    SEVEN_DAYS,
    THIRTY_DAYS
}

/**
 * ShieldFocus Progress Screen matching Screen 13 & Screen 25 in the reference design.
 */
@Composable
fun ProgressScreen(
    onNavigateToRewards: () -> Unit,
    modifier: Modifier = Modifier,
    userStats: UserStats = UserStats(),
    onActivateProtectionClick: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(ProgressFilter.TODAY) }
    var userToggledData by remember { mutableStateOf(false) }
    val hasData = userToggledData || userStats.protectedDays > 0 || userStats.interventionsCount > 0 || userStats.isProtectionActive

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("progress_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Progress",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            IconButton(
                onClick = onNavigateToRewards,
                modifier = Modifier.testTag("progress_rewards_icon")
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Rewards",
                    tint = BluePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasData) {
            // Screen 25: Empty progress state variant
            EmptyStateView(
                title = "No progress yet",
                subtitle = "Start your protection to build your streak and view statistics.",
                icon = Icons.Default.Star,
                actionButtonText = "Activate Protection",
                onActionClick = {
                    userToggledData = true
                    onActivateProtectionClick()
                }
            )
        } else {
            // Main Hero Card: 7 Days Protected with Checkmark
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(GreenLightSurface)
                            .border(1.dp, GreenLightBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = GreenSuccess,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        val protectedDaysCount = userStats.protectedDays
                        Text(
                            text = if (protectedDaysCount == 1) "1 Day Protected" else "$protectedDaysCount Days Protected",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (protectedDaysCount == 0) "Protection active today. Day 1 starts now!" else "Keep going. Every day builds your routine.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Filter Chips: Today, 7 days, 30 days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    text = "Today",
                    isSelected = selectedFilter == ProgressFilter.TODAY,
                    onClick = { selectedFilter = ProgressFilter.TODAY },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    text = "7 days",
                    isSelected = selectedFilter == ProgressFilter.SEVEN_DAYS,
                    onClick = { selectedFilter = ProgressFilter.SEVEN_DAYS },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    text = "30 days",
                    isSelected = selectedFilter == ProgressFilter.THIRTY_DAYS,
                    onClick = { selectedFilter = ProgressFilter.THIRTY_DAYS },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Protection History Timeline Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        text = "PROTECTION HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mon Tue Wed Thu Fri Sat Sun
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        days.forEachIndexed { index, day ->
                            val isCompleted = if (userStats.protectedDays >= 7) true else (index >= 7 - userStats.protectedDays)
                            DayCheckItem(
                                day = day,
                                isCompleted = isCompleted,
                                isToday = index == 6
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Statistics Grid: 4 tiles
            Text(
                text = "STATISTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Protected Days",
                    value = "${userStats.protectedDays}",
                    modifier = Modifier.weight(1f),
                    testTag = "stat_protected_days"
                )

                StatCard(
                    title = "Interventions",
                    value = "${userStats.interventionsCount}",
                    modifier = Modifier.weight(1f),
                    testTag = "stat_progress_interventions"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val timeDisplay = if (userStats.protectionTimeHours > 0f) "${userStats.protectionTimeHours}h" else "0.0h"
                StatCard(
                    title = "Protection Time",
                    value = timeDisplay,
                    modifier = Modifier.weight(1f),
                    testTag = "stat_progress_time"
                )

                val streakDisplay = if (userStats.longestStreakDays > 0) "${userStats.longestStreakDays} days" else "${userStats.currentStreakDays} days"
                StatCard(
                    title = "Longest Run",
                    value = streakDisplay,
                    modifier = Modifier.weight(1f),
                    testTag = "stat_longest_run"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) BluePrimary else White)
            .border(1.dp, if (isSelected) BluePrimary else BorderLight, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) White else TextSecondary
        )
    }
}

@Composable
private fun DayCheckItem(
    day: String,
    isCompleted: Boolean,
    isToday: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day,
            fontSize = 11.sp,
            color = if (isToday) BluePrimary else TextTertiary,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isCompleted) GreenLightSurface else Color(0xFFF1F5F9))
                .border(
                    1.dp,
                    if (isCompleted) GreenLightBorder else BorderLight,
                    CircleShape
                )
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = GreenDark,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (isToday) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Today",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary
            )
        }
    }
}
