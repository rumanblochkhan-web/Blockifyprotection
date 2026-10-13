package com.blockify.emergencystop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recoveryshield.protection.engine.ProtectionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Complete Emergency Stop Screen Flow with input, validation, verification animation,
 * and persistent protection deactivation.
 */
@Composable
fun EmergencyStopFlow(
    onNavigateBack: () -> Unit,
    onEmergencyStopConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val validator = remember { EmergencyCodeBackendValidator(context) }
    val focusRequester = remember { FocusRequester() }

    var uiState by remember { mutableStateOf(EmergencyStopUiState.INPUT) }
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeStepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState) {
        if (uiState == EmergencyStopUiState.INPUT) {
            delay(200)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "emergency_stop_flow_transition"
    ) { state ->
        when (state) {
            EmergencyStopUiState.INPUT, EmergencyStopUiState.INVALID -> {
                EmergencyStopInputScreen(
                    code = codeInput,
                    errorMessage = errorMessage,
                    onCodeChange = {
                        codeInput = it
                        if (errorMessage != null) errorMessage = null
                    },
                    onSubmit = {
                        if (codeInput.length == 8 || codeInput.length == 6) {
                            errorMessage = null
                            uiState = EmergencyStopUiState.VERIFYING
                            activeStepIndex = 0

                            coroutineScope.launch {
                                // Step 1: Validating Code with backend
                                activeStepIndex = 0
                                delay(600)
                                val result = validator.validateAndConsumeCode(codeInput)

                                if (result.status == EmergencyCodeValidationStatus.SUCCESS) {
                                    // Step 2: Stopping protection services
                                    activeStepIndex = 1
                                    delay(500)

                                    // Step 3: Disabling protection monitoring
                                    activeStepIndex = 2
                                    delay(400)

                                    // Step 4: Removing overlays
                                    activeStepIndex = 3
                                    delay(300)

                                    // Execute real shutdown on ProtectionEngine
                                    val engine = ProtectionEngine.getInstance(context)
                                    engine.deactivateVoluntaryProtection()
                                    com.example.recoveryshield.protection.service.RecoveryAccessibilityService.dismissSystemIntervention()

                                    // Complete all steps
                                    activeStepIndex = 4
                                    delay(400)
                                    uiState = EmergencyStopUiState.SUCCESS_STOPPED
                                } else {
                                    errorMessage = result.message ?: "Invalid code. Please try again."
                                    uiState = EmergencyStopUiState.INVALID
                                }
                            }
                        }
                    },
                    onBack = onNavigateBack,
                    onOpenSupport = { uiState = EmergencyStopUiState.SUPPORT },
                    focusRequester = focusRequester
                )
            }

            EmergencyStopUiState.VERIFYING -> {
                EmergencyStopVerifyingScreen(
                    activeStepIndex = activeStepIndex,
                    onBack = onNavigateBack
                )
            }

            EmergencyStopUiState.SUCCESS_STOPPED -> {
                EmergencyStopSuccessScreen(
                    onContinue = { uiState = EmergencyStopUiState.FINAL_STOPPED }
                )
            }

            EmergencyStopUiState.FINAL_STOPPED -> {
                EmergencyStopFinalScreen(
                    onReturnToBlockify = {
                        onEmergencyStopConfirmed()
                        onNavigateBack()
                    }
                )
            }

            EmergencyStopUiState.SUPPORT -> {
                EmergencyStopSupportScreen(
                    onBack = { uiState = EmergencyStopUiState.INPUT }
                )
            }
        }
    }
}

/**
 * Screen 1: Emergency Code Input
 */
@Composable
private fun EmergencyStopInputScreen(
    code: String,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onOpenSupport: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val canSubmit = code.length == 8

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("emergency_stop_input_screen")
    ) {
        // Top Back Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("emergency_stop_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Shield / Warning Header Badge
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEE2E2))
                .border(1.dp, Color(0xFFFECACA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Emergency Stop",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Need to temporarily stop protection?",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF334155)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Emergency Stop temporarily disables Blockify protection so you can safely change your device settings or resolve an urgent situation.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card: Enter Emergency Code
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Enter Emergency Code",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Enter the code provided by Blockify Support.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 6-Character Input
                EmergencyCodeInput(
                    code = code,
                    onCodeChange = onCodeChange,
                    onDone = { if (canSubmit) onSubmit() },
                    focusRequester = focusRequester
                )

                // Error State Message if invalid
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF2F2))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stop Protection Button
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("emergency_stop_submit_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626), // Emergency Red
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE2E8F0),
                disabledContentColor = Color(0xFF94A3B8)
            )
        ) {
            Text(
                text = "Stop Protection",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bottom Support Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSupport)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Contact Blockify Support",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2563EB)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Screen 2: Verifying Code State
 */
@Composable
private fun EmergencyStopVerifyingScreen(
    activeStepIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("emergency_stop_verifying_screen")
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Pulsing / Rotating Indicator
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 4.dp,
                color = Color(0xFF2563EB),
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Verifying Code...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Please wait while we validate your emergency code and safely pause protection.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step checklist
        EmergencyStepChecklist(activeStepIndex = activeStepIndex)
    }
}

/**
 * Screen 3: Protection Stopped Confirmation
 */
@Composable
private fun EmergencyStopSuccessScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("emergency_stop_success_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Green Check Badge
        Surface(
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            color = Color(0xFFECFDF5),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Protection Stopped",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Blockify protection has been temporarily disabled.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Confirmation Checklist Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfirmationItem(text = "Accessibility protection paused")
                ConfirmationItem(text = "Overlay monitoring disabled")
                ConfirmationItem(text = "Content interception inactive")
                ConfirmationItem(text = "Account data and streaks preserved")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("emergency_stop_continue_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConfirmationItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF334155),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Screen 4: Final Protection Stopped State
 */
@Composable
private fun EmergencyStopFinalScreen(
    onReturnToBlockify: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("emergency_stop_final_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Shield Inactive Badge
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = Color(0xFFF1F5F9),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Protection is stopped",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your Blockify protection is currently inactive.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Informational Callout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEFF6FF))
                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Protection can be enabled again at any time directly from the main Blockify screen.",
                    fontSize = 13.sp,
                    color = Color(0xFF1E40AF),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onReturnToBlockify,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("emergency_stop_return_home_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Return to Blockify",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
