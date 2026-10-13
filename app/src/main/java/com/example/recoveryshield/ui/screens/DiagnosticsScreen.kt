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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.recoveryshield.domain.model.CooldownState
import com.example.recoveryshield.domain.model.DiagnosticsSummary
import com.example.recoveryshield.domain.model.EventLog
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.model.SafeEventCode
import com.example.recoveryshield.ui.components.EmptyStateView
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightBorder
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedError
import com.example.ui.theme.RedLightSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShieldFocus Diagnostics Screen matching Screen 22 in the reference design.
 */
@Composable
fun DiagnosticsScreen(
    auditLogs: List<EventLog>,
    protectionState: ProtectionState,
    permissionState: PermissionState,
    cooldownState: CooldownState,
    diagnosticsSummary: DiagnosticsSummary,
    isServiceRunning: Boolean,
    onTriggerTestIntervention: () -> Unit,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("LOGS", "STATE", "STATS")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("diagnostics_screen")
    ) {
        // Top Bar with Back Arrow and Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Diagnostics",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            IconButton(onClick = { /* refresh */ }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System Health Quick Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusMiniItem(
                    label = "Accessibility",
                    value = if (isServiceRunning) "Running" else "Stopped",
                    isGood = isServiceRunning
                )
                StatusMiniItem(
                    label = "Overlay",
                    value = if (permissionState.overlayGranted) "Enabled" else "Disabled",
                    isGood = permissionState.overlayGranted
                )
                StatusMiniItem(
                    label = "Engine",
                    value = if (protectionState == ProtectionState.ACTIVE) "Ready" else "Inactive",
                    isGood = protectionState == ProtectionState.ACTIVE
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clean Segmented Tab Row: LOGS | STATE | STATS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            tabs.forEachIndexed { index, tabName ->
                val isSelected = selectedTab == index
                Column(
                    modifier = Modifier
                        .clickable { selectedTab = index }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("diag_tab_$tabName"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tabName,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) BluePrimary else TextTertiary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BluePrimary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.5.dp))
                    }
                }
            }
        }

        HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> LogsTabContent(auditLogs)
            1 -> StateTabContent(protectionState, permissionState, cooldownState, isServiceRunning)
            2 -> StatsTabContent(diagnosticsSummary, onTriggerTestIntervention)
        }
    }
}

@Composable
private fun StatusMiniItem(label: String, value: String, isGood: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = TextTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isGood) GreenDark else RedError
        )
    }
}

@Composable
private fun LogsTabContent(auditLogs: List<EventLog>) {
    if (auditLogs.isEmpty()) {
        EmptyStateView(
            title = "No events logged yet",
            subtitle = "Safe on-device diagnostics will be listed here as protection runs.",
            icon = Icons.Default.Refresh
        )
    } else {
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            items(auditLogs) { log ->
                val codeName = log.eventCode.name
                val formattedTime = timeFormat.format(Date(log.timestampMillis))

                val dotColor = when (log.eventCode) {
                    SafeEventCode.SERVICE_STARTED,
                    SafeEventCode.PERMISSION_GRANTED -> GreenSuccess
                    SafeEventCode.HIGH_CONFIDENCE_INTERVENTION,
                    SafeEventCode.COOLDOWN_STARTED,
                    SafeEventCode.DISABLE_REQUESTED -> AmberWarning
                    SafeEventCode.SERVICE_STOPPED,
                    SafeEventCode.DETECTOR_DEGRADED -> RedError
                    else -> BluePrimary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        color = TextTertiary,
                        modifier = Modifier.width(55.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatEventCodeTitle(codeName),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        if (log.safeMetadata.isNotBlank()) {
                            Text(
                                text = log.safeMetadata,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StateTabContent(
    protectionState: ProtectionState,
    permissionState: PermissionState,
    cooldownState: CooldownState,
    isServiceRunning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                StateItemRow(label = "Protection State", value = protectionState.name, isGood = protectionState == ProtectionState.ACTIVE)
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                StateItemRow(label = "Accessibility Service", value = if (isServiceRunning) "Running" else "Stopped", isGood = isServiceRunning)
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                StateItemRow(label = "Overlay Permission", value = if (permissionState.overlayGranted) "Granted" else "Denied", isGood = permissionState.overlayGranted)
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                StateItemRow(label = "Battery Optimization", value = if (permissionState.batteryOptimizationIgnored) "Ignored" else "Optimized", isGood = permissionState.batteryOptimizationIgnored)
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                StateItemRow(label = "Cooldown Active", value = if (cooldownState.hasActiveRequest) "Yes (${cooldownState.remainingMinutes}m left)" else "No", isGood = !cooldownState.hasActiveRequest)
            }
        }
    }
}

@Composable
private fun StatsTabContent(
    diagnosticsSummary: DiagnosticsSummary,
    onTriggerTestIntervention: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                StateItemRow(label = "Interventions Launched", value = "${diagnosticsSummary.interventionsLaunchedCount}", isGood = true)
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                StateItemRow(label = "Events Processed", value = "${diagnosticsSummary.eventsProcessedCount}", isGood = true)
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                StateItemRow(label = "Detector Invocations", value = "${diagnosticsSummary.detectorInvocationsCount}", isGood = true)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onTriggerTestIntervention,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("diag_test_intervention_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = BluePrimary,
                contentColor = White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Test 3-Second Intervention Overlay", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StateItemRow(label: String, value: String, isGood: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isGood) GreenDark else RedError
        )
    }
}

private fun formatEventCodeTitle(codeName: String): String {
    return codeName.replace('_', ' ')
        .lowercase(Locale.ROOT)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
