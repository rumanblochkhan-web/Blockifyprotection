package com.example.recoveryshield.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.recoveryshield.domain.model.PermissionType
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.ui.screens.AboutScreen
import com.example.recoveryshield.ui.screens.AccountScreen
import com.example.recoveryshield.ui.screens.AppBottomBar
import com.example.recoveryshield.ui.screens.DiagnosticsScreen
import com.example.recoveryshield.ui.screens.DisableScreen
import com.example.recoveryshield.ui.screens.FreeTrialScreen
import com.example.recoveryshield.ui.screens.HomeScreen
import com.example.recoveryshield.ui.screens.InsightsScreen
import com.example.recoveryshield.ui.screens.OnboardingScreen
import com.example.recoveryshield.ui.screens.PlansScreen
import com.example.recoveryshield.ui.screens.PrivacyScreen
import com.example.recoveryshield.ui.screens.ProgressScreen
import com.example.recoveryshield.ui.screens.ProtectionStatusScreen
import com.example.recoveryshield.ui.screens.RewardsScreen
import com.example.recoveryshield.ui.screens.ScreenDestination
import com.example.recoveryshield.ui.screens.SettingsScreen
import com.example.recoveryshield.ui.screens.SetupPermissionsScreen
import com.example.recoveryshield.ui.screens.SignUpScreen
import com.example.recoveryshield.ui.screens.SplashScreen
import com.example.recoveryshield.ui.screens.SupportScreen
import com.example.recoveryshield.ui.screens.TransparentInterventionOverlay
import com.example.recoveryshield.ui.screens.TrialExpiredPaywallScreen
import com.example.recoveryshield.ui.screens.VerifyEmailScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenPermissionSettings: (PermissionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val protectionState by viewModel.protectionState.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val cooldownState by viewModel.cooldownState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val diagnosticsSummary by viewModel.diagnosticsSummary.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val userStats by viewModel.userStats.collectAsState()
    val protectionPlan by viewModel.protectionPlan.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentDestination by remember { mutableStateOf(ScreenDestination.SPLASH) }

    // Persistent flag to know if user has signed up, completed onboarding & setup
    val sharedPrefs = remember {
        context.getSharedPreferences("safemind_prefs", Context.MODE_PRIVATE)
    }
    var hasSignedUp by remember {
        mutableStateOf(sharedPrefs.getBoolean("has_signed_up", false))
    }
    var hasCompletedOnboarding by remember {
        mutableStateOf(sharedPrefs.getBoolean("has_completed_onboarding", false))
    }
    var hasCompletedSetup by remember {
        mutableStateOf(sharedPrefs.getBoolean("has_completed_setup", false))
    }
    var pendingVerificationEmail by remember {
        mutableStateOf(sharedPrefs.getString("pending_verification_email", "") ?: "")
    }

    // In-App 3-Second Transparent Intervention Overlay state
    var showInterventionOverlay by remember { mutableStateOf(false) }
    var interventionCountdownSeconds by remember { mutableIntStateOf(3) }

    // 3-second countdown timer effect with auto-dismiss
    LaunchedEffect(showInterventionOverlay) {
        if (showInterventionOverlay) {
            interventionCountdownSeconds = 3
            while (interventionCountdownSeconds > 0) {
                delay(1000L)
                interventionCountdownSeconds -= 1
            }
            delay(300L)
            showInterventionOverlay = false
            snackbarHostState.showSnackbar("Blockify Protection Maintained!")
        }
    }

    // Splash timer: Step 1 Splash -> Step 2 Google Sign Up -> Step 3 Onboarding -> Step 4 Permissions -> Step 5 Home
    LaunchedEffect(Unit) {
        delay(1400L)
        if (currentDestination == ScreenDestination.SPLASH) {
            val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Throwable) { null }
            if (currentUser != null) {
                val currentUid = currentUser.uid
                val email = currentUser.email ?: sharedPrefs.getString("user_email", null)
                if (currentUid.isNotBlank()) {
                    viewModel.restoreSubscriptionOnLogin(currentUid, email)
                }
            }

            val isSetupNeeded = (!hasCompletedSetup && !permissionState.accessibilityGranted)
            currentDestination = when {
                !hasSignedUp -> ScreenDestination.SIGN_UP
                !hasCompletedOnboarding -> ScreenDestination.ONBOARDING
                isSetupNeeded -> ScreenDestination.ENABLE_ACCESSIBILITY
                else -> ScreenDestination.HOME
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Intercept hardware back button
    BackHandler(enabled = currentDestination == ScreenDestination.VERIFY_EMAIL || (currentDestination != ScreenDestination.HOME && currentDestination != ScreenDestination.SPLASH)) {
        if (currentDestination == ScreenDestination.VERIFY_EMAIL) {
            return@BackHandler
        }
        currentDestination = when (currentDestination) {
            ScreenDestination.ABOUT,
            ScreenDestination.CONTACT_SUPPORT,
            ScreenDestination.DIAGNOSTICS,
            ScreenDestination.PRIVACY,
            ScreenDestination.PLANS,
            ScreenDestination.ACCOUNT,
            ScreenDestination.REWARDS,
            ScreenDestination.FREE_TRIAL -> ScreenDestination.SETTINGS
            ScreenDestination.EMERGENCY_STOP,
            ScreenDestination.PROTECTION_STATUS -> ScreenDestination.HOME
            ScreenDestination.REQUEST_TO_DISABLE -> ScreenDestination.SETTINGS
            ScreenDestination.PROGRESS,
            ScreenDestination.INSIGHTS,
            ScreenDestination.SETTINGS -> ScreenDestination.HOME
            ScreenDestination.ENABLE_ACCESSIBILITY -> if (!hasCompletedOnboarding) ScreenDestination.ONBOARDING else ScreenDestination.HOME
            ScreenDestination.ONBOARDING -> if (!hasSignedUp) ScreenDestination.SIGN_UP else ScreenDestination.SPLASH
            ScreenDestination.SIGN_UP -> ScreenDestination.SPLASH
            else -> ScreenDestination.HOME
        }
    }

    val isServiceRunning = permissionState.accessibilityGranted

    val showBottomBar = when (currentDestination) {
        ScreenDestination.HOME,
        ScreenDestination.PROGRESS,
        ScreenDestination.INSIGHTS,
        ScreenDestination.SETTINGS,
        ScreenDestination.PROTECTION_STATUS -> true
        else -> false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { dest -> currentDestination = dest }
                )
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = Modifier.testTag("app_root_scaffold")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { destination ->
                when (destination) {
                    ScreenDestination.SPLASH -> {
                        SplashScreen()
                    }

                    ScreenDestination.SIGN_UP -> {
                        SignUpScreen(
                            onGoogleSignInSuccess = { name, email ->
                                sharedPrefs.edit()
                                    .putBoolean("has_signed_up", true)
                                    .putString("user_name", name)
                                    .putString("user_email", email)
                                    .apply()
                                hasSignedUp = true
                                val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
                                if (!currentUid.isNullOrBlank()) {
                                    viewModel.restoreSubscriptionOnLogin(currentUid, email)
                                }
                                // Ensure user begins strictly with 0/0 personalized stats
                                viewModel.initializeNewUserStats()
                                currentDestination = if (!hasCompletedOnboarding) {
                                    ScreenDestination.ONBOARDING
                                } else {
                                    ScreenDestination.ENABLE_ACCESSIBILITY
                                }
                            },
                            onEmailAuthSuccess = { name, email ->
                                sharedPrefs.edit()
                                    .putBoolean("has_signed_up", true)
                                    .remove("pending_verification_email")
                                    .putString("user_name", name)
                                    .putString("user_email", email)
                                    .apply()
                                hasSignedUp = true
                                val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Throwable) { null }
                                if (!currentUid.isNullOrBlank()) {
                                    viewModel.restoreSubscriptionOnLogin(currentUid, email)
                                }
                                viewModel.initializeNewUserStats()
                                currentDestination = if (!hasCompletedOnboarding) {
                                    ScreenDestination.ONBOARDING
                                } else {
                                    ScreenDestination.ENABLE_ACCESSIBILITY
                                }
                            },
                            onEmailVerificationRequired = { email ->
                                pendingVerificationEmail = email
                                sharedPrefs.edit()
                                    .putString("pending_verification_email", email)
                                    .putBoolean("has_signed_up", false)
                                    .apply()
                                currentDestination = ScreenDestination.VERIFY_EMAIL
                            }
                        )
                    }

                    ScreenDestination.ONBOARDING -> {
                        OnboardingScreen(
                            permissionState = permissionState,
                            onOpenPermission = { type -> onOpenPermissionSettings(type) },
                            onActivateProtection = {
                                sharedPrefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                hasCompletedOnboarding = true
                                if (permissionState.accessibilityGranted) {
                                    viewModel.activateProtection()
                                    currentDestination = ScreenDestination.HOME
                                } else {
                                    currentDestination = ScreenDestination.ENABLE_ACCESSIBILITY
                                }
                            },
                            onSkipOnboarding = {
                                sharedPrefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                hasCompletedOnboarding = true
                                currentDestination = ScreenDestination.HOME
                            },
                            onShowMessage = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }

                    ScreenDestination.ENABLE_ACCESSIBILITY -> {
                        SetupPermissionsScreen(
                            permissionState = permissionState,
                            onBackClick = {
                                currentDestination = if (!hasCompletedOnboarding) ScreenDestination.ONBOARDING else ScreenDestination.HOME
                            },
                            onOpenPermission = { type ->
                                onOpenPermissionSettings(type)
                            },
                            onCompleteSetup = {
                                sharedPrefs.edit().putBoolean("has_completed_setup", true).apply()
                                hasCompletedSetup = true
                                if (permissionState.accessibilityGranted) {
                                    viewModel.activateProtection()
                                    currentDestination = ScreenDestination.HOME
                                } else {
                                    onOpenPermissionSettings(PermissionType.ACCESSIBILITY_SERVICE)
                                }
                            },
                            onShowMessage = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }

                    ScreenDestination.HOME -> {
                        val activity = context as? android.app.Activity
                        val isAdLoading by viewModel.adMobManager.isLoading.collectAsState()
                        val isAdLoaded by viewModel.adMobManager.isAdLoaded.collectAsState()
                        val adStatusMessage by viewModel.adMobManager.adStatusMessage.collectAsState()

                        HomeScreen(
                            protectionState = protectionState,
                            permissionState = permissionState,
                            isServiceRunning = isServiceRunning,
                            cooldownState = cooldownState,
                            userStats = userStats,
                            protectionPlan = protectionPlan,
                            isAdLoading = isAdLoading,
                            isAdLoaded = isAdLoaded,
                            adStatusMessage = adStatusMessage,
                            onWatchAdForCredit = {
                                if (activity != null) {
                                    viewModel.watchRewardedAd(activity)
                                } else {
                                    viewModel.watchRewardedAd()
                                }
                            },
                            onNavigateToPlans = { currentDestination = ScreenDestination.PLANS },
                            onPowerClick = {
                                if (protectionState == ProtectionState.ACTIVE || protectionState == ProtectionState.COOLDOWN_ACTIVE) {
                                    // Trigger 3-second transparent intervention overlay
                                    showInterventionOverlay = true
                                    interventionCountdownSeconds = 3
                                } else {
                                    if (!permissionState.accessibilityGranted) {
                                        currentDestination = ScreenDestination.ENABLE_ACCESSIBILITY
                                    } else {
                                        sharedPrefs.edit().putBoolean("has_completed_setup", true).apply()
                                        hasCompletedSetup = true
                                        viewModel.activateProtection()
                                    }
                                }
                            },
                            onNavigateToSettings = { currentDestination = ScreenDestination.SETTINGS },
                            onNavigateToStatus = { currentDestination = ScreenDestination.PROTECTION_STATUS },
                            onNavigateToSupport = { currentDestination = ScreenDestination.CONTACT_SUPPORT },
                            onNavigateToCooldown = { currentDestination = ScreenDestination.REQUEST_TO_DISABLE },
                            onNavigateToEmergencyStop = { currentDestination = ScreenDestination.EMERGENCY_STOP },
                            onTestEmergencyOpen = {
                                try {
                                    val intent = android.content.Intent(context, com.example.recoveryshield.protection.intervention.EmergencyOpenActivity::class.java).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to open Emergency Open preview: ${e.message}")
                                    }
                                }
                            },
                            antiUninstallEnabled = permissionState.deviceAdminGranted && userSettings.antiUninstallProtectionEnabled,
                            onToggleAntiUninstall = { enabled ->
                                if (enabled) {
                                    if (!permissionState.deviceAdminGranted) {
                                        onOpenPermissionSettings(PermissionType.DEVICE_ADMIN)
                                    } else {
                                        viewModel.setAntiUninstallProtectionEnabled(true)
                                    }
                                } else {
                                    viewModel.setAntiUninstallProtectionEnabled(false)
                                }
                            },
                            onTriggerTestIntervention = {
                                showInterventionOverlay = true
                                interventionCountdownSeconds = 3
                            }
                        )
                    }

                    ScreenDestination.PROGRESS -> {
                        ProgressScreen(
                            userStats = userStats,
                            onNavigateToRewards = { currentDestination = ScreenDestination.REWARDS }
                        )
                    }

                    ScreenDestination.INSIGHTS -> {
                        InsightsScreen(
                            onProtectedActionAttempt = { action ->
                                showInterventionOverlay = true
                                interventionCountdownSeconds = 3
                                viewModel.engine.triggerInstantIntervention(
                                    "Protected action attempt: $action",
                                    isAntiTamper = true
                                )
                            },
                            onNavigateBack = { currentDestination = ScreenDestination.HOME }
                        )
                    }

                    ScreenDestination.SETTINGS -> {
                        SettingsScreen(
                            protectionState = protectionState,
                            permissionState = permissionState,
                            onBackClick = { currentDestination = ScreenDestination.HOME },
                            onToggleProtection = { shouldEnable: Boolean ->
                                if (!shouldEnable) {
                                    showInterventionOverlay = true
                                    interventionCountdownSeconds = 3
                                } else {
                                    if (!permissionState.accessibilityGranted) {
                                        currentDestination = ScreenDestination.ENABLE_ACCESSIBILITY
                                    } else {
                                        sharedPrefs.edit().putBoolean("has_completed_setup", true).apply()
                                        hasCompletedSetup = true
                                        viewModel.activateProtection()
                                    }
                                }
                            },
                            onNavigateToStatus = { currentDestination = ScreenDestination.PROTECTION_STATUS },
                            onNavigateToCooldown = { currentDestination = ScreenDestination.REQUEST_TO_DISABLE },
                            onNavigateToAccount = { currentDestination = ScreenDestination.ACCOUNT },
                            onNavigateToRewards = { currentDestination = ScreenDestination.REWARDS },
                            onNavigateToPlans = { currentDestination = ScreenDestination.PLANS },
                            onNavigateToFreeTrial = { currentDestination = ScreenDestination.FREE_TRIAL },
                            onNavigateToSupport = { currentDestination = ScreenDestination.CONTACT_SUPPORT },
                            onNavigateToDiagnostics = { currentDestination = ScreenDestination.DIAGNOSTICS },
                            onNavigateToPrivacy = { currentDestination = ScreenDestination.PRIVACY },
                            onNavigateToAbout = { currentDestination = ScreenDestination.ABOUT },
                            onOpenPermission = { type -> onOpenPermissionSettings(type) }
                        )
                    }

                    ScreenDestination.PROTECTION_STATUS -> {
                        ProtectionStatusScreen(
                            protectionState = protectionState,
                            permissionState = permissionState,
                            isServiceRunning = isServiceRunning,
                            protectionPlan = protectionPlan,
                            onBackClick = { currentDestination = ScreenDestination.HOME }
                        )
                    }

                    ScreenDestination.REWARDS -> {
                        RewardsScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            onNavigateToFreeTrial = { currentDestination = ScreenDestination.FREE_TRIAL }
                        )
                    }

                    ScreenDestination.FREE_TRIAL -> {
                        FreeTrialScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            onStartProtection = {
                                viewModel.activateProtection()
                                currentDestination = ScreenDestination.HOME
                            },
                            onNavigateToRewards = { currentDestination = ScreenDestination.REWARDS }
                        )
                    }

                    ScreenDestination.PLANS -> {
                        PlansScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            protectionPlan = protectionPlan,
                            onUpgradeToPremium = { viewModel.upgradeToPremium() }
                        )
                    }

                    ScreenDestination.ACCOUNT -> {
                        AccountScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS }
                        )
                    }

                    ScreenDestination.REQUEST_TO_DISABLE -> {
                        DisableScreen(
                            cooldownState = cooldownState,
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            onCancelRequest = {
                                viewModel.cancelDisableRequest()
                                currentDestination = ScreenDestination.SETTINGS
                            },
                            onConfirmDisable = {
                                viewModel.confirmDisable()
                                currentDestination = ScreenDestination.HOME
                            },
                            onRequestCooldown = {
                                viewModel.requestDisable()
                            }
                        )
                    }

                    ScreenDestination.CONTACT_SUPPORT -> {
                        SupportScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            onNavigateToPermissions = { currentDestination = ScreenDestination.ENABLE_ACCESSIBILITY }
                        )
                    }

                    ScreenDestination.DIAGNOSTICS -> {
                        DiagnosticsScreen(
                            auditLogs = auditLogs,
                            protectionState = protectionState,
                            permissionState = permissionState,
                            cooldownState = cooldownState,
                            diagnosticsSummary = diagnosticsSummary,
                            isServiceRunning = isServiceRunning,
                            onTriggerTestIntervention = {
                                showInterventionOverlay = true
                                interventionCountdownSeconds = 3
                            },
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS }
                        )
                    }

                    ScreenDestination.PRIVACY -> {
                        PrivacyScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            onNavigateToPermissions = { currentDestination = ScreenDestination.ENABLE_ACCESSIBILITY }
                        )
                    }

                    ScreenDestination.ABOUT -> {
                        AboutScreen(
                            onBackClick = { currentDestination = ScreenDestination.SETTINGS },
                            onNavigateToSupport = { currentDestination = ScreenDestination.CONTACT_SUPPORT }
                        )
                    }

                    ScreenDestination.EMERGENCY_STOP -> {
                        com.blockify.emergencystop.EmergencyStopFlow(
                            onNavigateBack = { currentDestination = ScreenDestination.HOME },
                            onEmergencyStopConfirmed = {
                                viewModel.deactivateProtection()
                            }
                        )
                    }

                    ScreenDestination.VERIFY_EMAIL -> {
                        VerifyEmailScreen(
                            userEmail = pendingVerificationEmail,
                            onEmailVerified = {
                                val user = FirebaseAuth.getInstance().currentUser
                                val email = user?.email ?: pendingVerificationEmail
                                val name = user?.displayName ?: email.substringBefore("@")
                                sharedPrefs.edit()
                                    .putBoolean("has_signed_up", true)
                                    .remove("pending_verification_email")
                                    .putString("user_name", name)
                                    .putString("user_email", email)
                                    .apply()
                                hasSignedUp = true
                                val currentUid = user?.uid
                                if (!currentUid.isNullOrBlank()) {
                                    viewModel.restoreSubscriptionOnLogin(currentUid, email)
                                }
                                viewModel.initializeNewUserStats()
                                currentDestination = if (!hasCompletedOnboarding) {
                                    ScreenDestination.ONBOARDING
                                } else if (!hasCompletedSetup && !permissionState.accessibilityGranted) {
                                    ScreenDestination.ENABLE_ACCESSIBILITY
                                } else {
                                    ScreenDestination.HOME
                                }
                            },
                            onSignOutAndChangeEmail = {
                                try { FirebaseAuth.getInstance().signOut() } catch (_: Throwable) {}
                                viewModel.onUserLoggedOut()
                                sharedPrefs.edit()
                                    .remove("pending_verification_email")
                                    .putBoolean("has_signed_up", false)
                                    .apply()
                                hasSignedUp = false
                                pendingVerificationEmail = ""
                                currentDestination = ScreenDestination.SIGN_UP
                            }
                        )
                    }
                }
            }

            // In-App Floating 5-Second Transparent Intervention Overlay
            AnimatedVisibility(
                visible = showInterventionOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TransparentInterventionOverlay(
                    countdownSeconds = interventionCountdownSeconds,
                    onClose = {
                        showInterventionOverlay = false
                    },
                    onStayProtected = {
                        showInterventionOverlay = false
                    }
                )
            }
        }
    }
}
