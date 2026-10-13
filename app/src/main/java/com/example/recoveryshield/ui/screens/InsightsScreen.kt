package com.example.recoveryshield.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.blockify.ui.block.BlockScreen
import com.blockify.ui.block.ProtectedAction
import com.example.recoveryshield.protection.block.BlockRulesManager

/**
 * Blockify Block Section (Replaced Insights Section).
 * Connects the BlockScreen UI flow to the BlockRulesManager and the ProtectionEngine.
 */
@Composable
fun InsightsScreen(
    onProtectedActionAttempt: (ProtectedAction) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val rulesManager = remember { BlockRulesManager.getInstance(context) }
    val rules by rulesManager.rulesFlow.collectAsState()
    val installedApps = remember { rulesManager.getInstalledApps() }

    BlockScreen(
        installedApps = installedApps,
        initialRules = rules,
        onSaveRule = { rule ->
            rulesManager.saveRule(rule)
        },
        onDeleteRule = { rule ->
            // Pause/Delete attempts trigger existing intervention protection flow
            onProtectedActionAttempt(ProtectedAction.DELETE_RULE(rule.appPackage, rule.appName))
        },
        onPauseRule = { rule ->
            // Pause/Delete attempts trigger existing intervention protection flow
            onProtectedActionAttempt(ProtectedAction.PAUSE_RULE(rule.appPackage, rule.appName))
        },
        onProtectedActionAttempt = { action ->
            onProtectedActionAttempt(action)
        },
        onNavigateBack = onNavigateBack
    )
}
