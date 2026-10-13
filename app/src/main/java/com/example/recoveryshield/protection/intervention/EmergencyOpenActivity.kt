package com.example.recoveryshield.protection.intervention

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.blockify.emergencystop.EmergencyCodeBackendValidator
import com.blockify.emergencystop.EmergencyCodeInput
import com.blockify.emergencystop.EmergencyCodeValidationStatus
import com.example.recoveryshield.protection.service.RecoveryAccessibilityService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Emergency Open Overlay Activity.
 * Intercepts unauthorized navigation into Blockify's App Info / Settings screen
 * and requires the user to input a valid 8-digit emergency code to unlock the page.
 */
class EmergencyOpenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EmergencyOpenSession.isPromptVisible = true

        setContent {
            MyApplicationTheme {
                EmergencyOpenScreen(
                    onAuthorized = {
                        EmergencyOpenSession.authorizeAppInfoAccess()
                        RecoveryAccessibilityService.dismissSystemIntervention()
                        finish()
                    },
                    onCancelAndLeave = {
                        cancelAndNavigateAway()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EmergencyOpenSession.isPromptVisible = false
    }

    private fun cancelAndNavigateAway() {
        EmergencyOpenSession.revoke()
        // Escort user away from settings so App Info cannot be accessed
        RecoveryAccessibilityService.performCompliantNavigationAway()
        finish()
    }
}

@Composable
fun EmergencyOpenScreen(
    onAuthorized: () -> Unit,
    onCancelAndLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val validator = remember { EmergencyCodeBackendValidator(context) }
    val focusRequester = remember { FocusRequester() }

    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isAuthorizedSuccess by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    // Intercept hardware/gesture Back button to preserve privacy
    BackHandler {
        onCancelAndLeave()
    }

    LaunchedEffect(Unit) {
        delay(300)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Fullscreen opaque backdrop for complete privacy protection
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // 100% solid dark slate privacy blackout
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("emergency_open_screen"),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isAuthorizedSuccess,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "emergency_open_transition"
        ) { success ->
            if (success) {
                // Success State
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Access Authorized",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Emergency code verified. Opening Blockify App Info...",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // Input State Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top row with Cancel / Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEE2E2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Privacy Lock",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFDC2626)
                                )
                            }

                            IconButton(
                                onClick = onCancelAndLeave,
                                modifier = Modifier.testTag("emergency_open_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel & Leave",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Shield Icon Badge
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2))
                                .border(1.dp, Color(0xFFFECACA), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Title: Emergency Open
                        Text(
                            text = "Emergency Open",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Blockify App Info is locked to protect your privacy and prevent unauthorized data deletion or uninstallation.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Code Input Box Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Enter 8-Digit Authorization Code",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Enter the code provided by Blockify Support.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 8-box Code Input
                                EmergencyCodeInput(
                                    code = codeInput,
                                    onCodeChange = {
                                        codeInput = it
                                        if (errorMessage != null) errorMessage = null
                                    },
                                    onDone = {
                                        if (codeInput.length == 8 && !isVerifying) {
                                            submitVerification(
                                                code = codeInput,
                                                validator = validator,
                                                coroutineScope = coroutineScope,
                                                onVerifyingChange = { isVerifying = it },
                                                onError = { errorMessage = it },
                                                onSuccess = {
                                                    isAuthorizedSuccess = true
                                                    coroutineScope.launch {
                                                        delay(600)
                                                        onAuthorized()
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    focusRequester = focusRequester
                                )

                                // Error Banner
                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFEF2F2))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = errorMessage ?: "",
                                            color = Color(0xFFDC2626),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                submitVerification(
                                    code = codeInput,
                                    validator = validator,
                                    coroutineScope = coroutineScope,
                                    onVerifyingChange = { isVerifying = it },
                                    onError = { errorMessage = it },
                                    onSuccess = {
                                        isAuthorizedSuccess = true
                                        coroutineScope.launch {
                                            delay(600)
                                            onAuthorized()
                                        }
                                    }
                                )
                            },
                            enabled = codeInput.length == 8 && !isVerifying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("emergency_open_submit_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Verifying Code...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text(text = "Verify & Open Page", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Cancel / Exit Button
                        OutlinedButton(
                            onClick = onCancelAndLeave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("emergency_open_cancel_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Cancel & Leave",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Support Link
                        Row(
                            modifier = Modifier
                                .clickable { showSupportDialog = true }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Need an Emergency Code? Contact Support",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }
            }
        }
    }

    // Support contact dialog
    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = {
                Text(
                    text = "Blockify Emergency Support",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "To obtain an 8-character Emergency Open code, please contact Blockify Support directly. Authorized users receive instant verification.",
                        fontSize = 14.sp,
                        color = Color(0xFF334155),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "📧 Email: rumanblochkhan@gmail.com\n🛡️ App: Blockify Shield Protection",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:rumanblochkhan@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Blockify Emergency Open Code Request")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Hello Blockify Support,\n\nI need an Emergency Open authorization code for my device to access App Info."
                                )
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                        showSupportDialog = false
                    }
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Email Support")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun submitVerification(
    code: String,
    validator: EmergencyCodeBackendValidator,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onVerifyingChange: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    onVerifyingChange(true)
    coroutineScope.launch {
        delay(400) // Brief smooth feedback
        val result = validator.validateCodeForEmergencyOpen(code)
        onVerifyingChange(false)
        if (result.status == EmergencyCodeValidationStatus.SUCCESS) {
            onSuccess()
        } else {
            onError(result.message ?: "Invalid emergency code. Please contact support.")
        }
    }
}
