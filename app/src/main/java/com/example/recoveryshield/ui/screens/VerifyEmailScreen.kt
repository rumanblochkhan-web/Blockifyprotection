package com.example.recoveryshield.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.AmberLightSurface
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueLightBorder
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Unbypassable Verification Wall.
 * Prevents access to the entire application until the user's email has been verified.
 * Survives app restarts, tab killing, and re-launches.
 */
@Composable
fun VerifyEmailScreen(
    userEmail: String,
    onEmailVerified: () -> Unit,
    onSignOutAndChangeEmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { try { FirebaseAuth.getInstance() } catch (_: Throwable) { null } }

    var isChecking by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    // Intercept back button - prevent navigating into the app
    BackHandler {
        Toast.makeText(
            context,
            "Please verify your email address to continue to Blockify",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Cooldown countdown for resend button
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000L)
            resendCooldown -= 1
        }
    }

    // Function to check if user has verified email
    val checkVerification = { showToast: Boolean ->
        if (!isChecking) {
            isChecking = true
            scope.launch {
                try {
                    val user = auth?.currentUser
                    if (user != null) {
                        user.reload().await()
                        if (user.isEmailVerified) {
                            isErrorMessage = false
                            statusMessage = "Email verified successfully! Opening Blockify..."
                            Toast.makeText(context, "Email verified!", Toast.LENGTH_SHORT).show()
                            delay(600L)
                            onEmailVerified()
                        } else {
                            if (showToast) {
                                isErrorMessage = true
                                statusMessage = "Email not verified yet. Please check your inbox and click the verification link."
                            }
                        }
                    } else {
                        // User session lost
                        if (showToast) {
                            isErrorMessage = true
                            statusMessage = "Session expired. Please sign in again."
                        }
                    }
                } catch (e: Exception) {
                    if (showToast) {
                        isErrorMessage = true
                        statusMessage = "Unable to check status: ${e.localizedMessage ?: "Network error"}"
                    }
                } finally {
                    isChecking = false
                }
            }
        }
    }

    // Auto-check whenever the app comes back to the foreground (e.g. user returns from Gmail app)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkVerification(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFCFF))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("verify_email_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App Icon & Lock/Shield Visual
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(BlueLightSurface)
                    .border(2.dp, BlueLightBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MarkEmailUnread,
                    contentDescription = "Unverified Email",
                    tint = BluePrimary,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Warning / Wall Status Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AmberLightSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("verification_status_chip")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIVATION REQUIRED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Verify Your Account",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "We have sent an activation link to your email. You must activate your account before you can open and use Blockify.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Email Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_email_display_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BlueLightSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sent to",
                            fontSize = 11.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (userEmail.isNotBlank()) userEmail else (auth?.currentUser?.email ?: "Your email"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Instructions Box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Steps to Unlock:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Open your mail app (Gmail, Outlook, etc.).\n2. Open the verification email from Blockify.\n3. Click the activation link.\n4. Return here and tap 'I've Verified My Email'.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            // Status or Error Feedback Banner
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isErrorMessage) RedLightSurface else GreenLightSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isErrorMessage) RedError.copy(alpha = 0.3f) else GreenLightBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isErrorMessage) Icons.Default.Lock else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isErrorMessage) RedError else GreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = if (isErrorMessage) RedError else GreenDark,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Action: "I've Verified My Email"
            Button(
                onClick = { checkVerification(true) },
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("verify_email_confirm_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BluePrimary,
                    contentColor = White
                )
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Checking verification...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("I've Verified My Email", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Action: "Open Mail App"
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Please open your email application manually", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_mail_app_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BlueLightBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Email App", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Resend Verification Email Button
            OutlinedButton(
                onClick = {
                    if (resendCooldown == 0 && !isResending) {
                        isResending = true
                        scope.launch {
                            try {
                                val user = auth?.currentUser
                                if (user != null) {
                                    user.sendEmailVerification().await()
                                    resendCooldown = 60
                                    isErrorMessage = false
                                    statusMessage = "Verification email resent to ${user.email}!"
                                    Toast.makeText(context, "Verification email resent!", Toast.LENGTH_SHORT).show()
                                } else {
                                    isErrorMessage = true
                                    statusMessage = "Please sign in again to resend verification."
                                }
                            } catch (e: Exception) {
                                isErrorMessage = true
                                statusMessage = "Failed to resend: ${e.localizedMessage ?: "Try again in a few minutes"}"
                            } finally {
                                isResending = false
                            }
                        }
                    }
                },
                enabled = resendCooldown == 0 && !isResending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("resend_verification_email_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (resendCooldown == 0) TextPrimary else TextTertiary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (resendCooldown == 0) BorderLight else Color(0xFFE2E8F0)
                )
            ) {
                if (isResending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BluePrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resending...", fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (resendCooldown > 0) "Resend link in ${resendCooldown}s" else "Resend Verification Email",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom option to change email or sign out
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "Used the wrong email address?",
                fontSize = 12.sp,
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sign Out & Change Email",
                fontSize = 13.sp,
                color = BluePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onSignOutAndChangeEmail()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("sign_out_change_email_button")
            )
        }
    }
}
