package com.example.recoveryshield.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlueDark
import com.example.ui.theme.BlueLightBorder
import com.example.ui.theme.BlueLightSurface
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLightBorder
import com.example.ui.theme.GreenLightSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.White

import androidx.compose.material.icons.filled.Lock
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionPlanType

/**
 * ShieldFocus Protection Plans Screen matching Screen 16.
 */
@Composable
fun PlansScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    protectionPlan: ProtectionPlanInfo = ProtectionPlanInfo(),
    onUpgradeToPremium: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("plans_screen")
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
                text = "Protection Plans",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // FREE / Standard Plan Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Standard Protection",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (protectionPlan.isPremiumActive) "Basic tier" else "3-day free trial + rewarded ad days",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    if (!protectionPlan.isPremiumActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(GreenLightSurface)
                                .border(1.dp, GreenLightBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Current Plan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PlanFeatureRow("Core on-device detection")
                PlanFeatureRow("Instant 3-second countdown intervention")
                PlanFeatureRow("3-Day Free Trial upon setup")
                PlanFeatureRow("Watch 1 ad every 24h to extend +1 day")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // PREMIUM Plan Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .border(2.dp, if (protectionPlan.isPremiumActive) GreenDark else BluePrimary, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Premium Protection",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueDark
                            )
                        }
                        Text(
                            text = "$2.99 / month • Cancel anytime",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BluePrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (protectionPlan.isPremiumActive) GreenLightSurface else BlueLightSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (protectionPlan.isPremiumActive) GreenDark else BluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PlanFeatureRow("Unlimited protection (never expires)")
                PlanFeatureRow("Zero ads required")
                PlanFeatureRow("Priority content detection & updates")
                PlanFeatureRow("Customizable intervention delay")
                PlanFeatureRow("Cloud backup & multi-device sync")

                Spacer(modifier = Modifier.height(20.dp))

                if (protectionPlan.isPremiumActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GreenLightSurface)
                            .border(1.dp, GreenLightBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (protectionPlan.isLifetimePremium) "✓ Premium Active (Lifetime)" else "✓ Premium Active • ${protectionPlan.countdownFullFormatted}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenDark
                        )
                    }
                } else {
                    Button(
                        onClick = onUpgradeToPremium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            contentColor = White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("subscribe_premium_button")
                    ) {
                        Text(
                            text = "Subscribe for $2.99 / Month",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PlanFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = BluePrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = TextPrimary
        )
    }
}
