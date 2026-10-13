package com.blockify.ui.block

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/*
 * BLOCKIFY — FULL APP BLOCKING SECTION
 *
 * Provides complete application blocking with full-time (24/7) or scheduled custom times.
 * In-app sub-feature blocking has been removed per user requirement.
 */

private val BlockifyBlue = Color(0xFF1565F3)
private val BlockifyGreen = Color(0xFF16A66A)
private val BlockifyRed = Color(0xFFD92D20)
private val BlockifyText = Color(0xFF142033)
private val BlockifyMuted = Color(0xFF6C788B)
private val BlockifySurface = Color(0xFFF7F9FC)
private val BlockifyBorder = Color(0xFFE1E7EF)

/**
 * Loads and renders the real, official application launcher icon directly from PackageManager.
 */
@Composable
fun AppIconView(
    packageName: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val context = LocalContext.current
    val appIconBitmap = remember(packageName) {
        try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                val b = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = Color(0xFFF1F5FA)
    ) {
        if (appIconBitmap != null) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackText.take(1).uppercase(),
                    color = BlockifyBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

data class BlockFeature(
    val id: String = "full_app",
    val title: String = "Full App",
    val description: String = "Entire application blocked",
    val detectionSignals: List<String> = emptyList(),
    val enabledByDefault: Boolean = true
)

data class AppCapabilityProfile(
    val key: String,
    val displayName: String,
    val packageNames: List<String>,
    val category: String,
    val features: List<BlockFeature> = emptyList()
)

data class SelectedFeature(
    val featureId: String = "full_app",
    val enabled: Boolean = true
)

data class ScheduleRule(
    val everyDay: Boolean = true,
    val selectedDays: Set<WeekDay> = WeekDay.entries.toSet(),
    val allDay: Boolean = true,
    val startMinutes: Int = 0,
    val endMinutes: Int = 24 * 60 - 1
)

data class ProtectionRule(
    val appPackage: String,
    val appName: String,
    val appCategory: String,
    val blockEntireApp: Boolean = true,
    val features: List<SelectedFeature> = emptyList(),
    val schedule: ScheduleRule,
    val active: Boolean = true,
    val paused: Boolean = false
)

enum class WeekDay(val short: String) {
    MON("Mon"),
    TUE("Tue"),
    WED("Wed"),
    THU("Thu"),
    FRI("Fri"),
    SAT("Sat"),
    SUN("Sun")
}

object BlockifyFeatureCatalog {
    private val profiles = listOf(
        AppCapabilityProfile(
            key = "instagram",
            displayName = "Instagram",
            packageNames = listOf("com.instagram.android"),
            category = "Social Media",
            features = listOf(
                BlockFeature("reels", "Reels", "Block Instagram Reels", listOf("reels")),
                BlockFeature("stories", "Stories", "Block Instagram Stories", listOf("stories")),
                BlockFeature("explore", "Explore", "Block Explore Feed", listOf("explore"))
            )
        ),
        AppCapabilityProfile(
            key = "youtube",
            displayName = "YouTube",
            packageNames = listOf("com.google.android.youtube"),
            category = "Entertainment",
            features = listOf(
                BlockFeature("shorts", "Shorts", "Block YouTube Shorts", listOf("shorts")),
                BlockFeature("comments", "Comments", "Block Comments", listOf("comments"))
            )
        ),
        AppCapabilityProfile(
            key = "whatsapp",
            displayName = "WhatsApp",
            packageNames = listOf("com.whatsapp", "com.whatsapp.w4b"),
            category = "Messaging",
            features = listOf(
                BlockFeature("status", "Status", "Block WhatsApp Status", listOf("status"))
            )
        )
    )

    fun resolve(packageName: String, appName: String): AppCapabilityProfile? {
        val lowerPkg = packageName.lowercase()
        val lowerName = appName.lowercase()
        return profiles.firstOrNull { profile ->
            profile.packageNames.any { lowerPkg == it.lowercase() } ||
            profile.displayName.equals(appName, ignoreCase = true) ||
            profile.key.equals(lowerName, ignoreCase = true)
        }
    }

    fun candidateFeatures(packageName: String, appName: String): List<BlockFeature> {
        return resolve(packageName, appName)?.features ?: emptyList()
    }
}

/* ---------- UI ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockScreen(
    installedApps: List<InstalledAppItem>,
    initialRules: List<ProtectionRule> = emptyList(),
    onSaveRule: (ProtectionRule) -> Unit = {},
    onDeleteRule: (ProtectionRule) -> Unit = {},
    onPauseRule: (ProtectionRule) -> Unit = {},
    onProtectedActionAttempt: (ProtectedAction) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var screen by remember { mutableStateOf(BlockSubScreen.OVERVIEW) }
    var selectedApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var draftRule by remember { mutableStateOf<ProtectionRule?>(null) }

    val rules = remember(initialRules) {
        mutableStateListOf<ProtectionRule>().apply { addAll(initialRules) }
    }

    fun saveRule(rule: ProtectionRule) {
        val finalRule = rule.copy(blockEntireApp = true, features = emptyList())
        val existingIndex = rules.indexOfFirst { it.appPackage == finalRule.appPackage }
        if (existingIndex >= 0) {
            rules[existingIndex] = finalRule
        } else {
            rules.add(finalRule)
        }
        onSaveRule(finalRule)
    }

    when (screen) {
        BlockSubScreen.OVERVIEW -> {
            BlockOverviewScreen(
                rules = rules,
                onAddProtection = {
                    screen = BlockSubScreen.APP_PICKER
                },
                onRuleClick = {
                    // Disabled: once an app is blocked, tapping it does nothing and does not open detail/edit screen
                }
            )
        }

        BlockSubScreen.APP_PICKER -> {
            AppPickerScreen(
                apps = installedApps,
                onBack = { screen = BlockSubScreen.OVERVIEW },
                onSelect = { app ->
                    selectedApp = app
                    screen = BlockSubScreen.PROTECTION_MODE_SELECTOR
                }
            )
        }

        BlockSubScreen.PROTECTION_MODE_SELECTOR -> {
            val app = selectedApp ?: return
            ProtectionModeSelectorScreen(
                app = app,
                onBack = { screen = BlockSubScreen.APP_PICKER },
                onFullAppBlock = {
                    draftRule = ProtectionRule(
                        appPackage = app.packageName,
                        appName = app.appName,
                        appCategory = app.category,
                        blockEntireApp = true,
                        features = emptyList(),
                        schedule = ScheduleRule()
                    )
                    screen = BlockSubScreen.SCHEDULE
                },
                onFeatureBlock = {
                    draftRule = ProtectionRule(
                        appPackage = app.packageName,
                        appName = app.appName,
                        appCategory = app.category,
                        blockEntireApp = false,
                        features = emptyList(),
                        schedule = ScheduleRule()
                    )
                    screen = BlockSubScreen.FEATURE_SELECTOR
                }
            )
        }

        BlockSubScreen.FEATURE_SELECTOR -> {
            val app = selectedApp ?: return
            val current = draftRule ?: return
            FeatureSelectorScreen(
                app = app,
                rule = current,
                onBack = { screen = BlockSubScreen.PROTECTION_MODE_SELECTOR },
                onSave = { rule ->
                    saveRule(rule)
                    screen = BlockSubScreen.OVERVIEW
                }
            )
        }

        BlockSubScreen.SCHEDULE -> {
            val app = selectedApp ?: return
            val current = draftRule ?: ProtectionRule(
                appPackage = app.packageName,
                appName = app.appName,
                appCategory = app.category,
                blockEntireApp = true,
                features = emptyList(),
                schedule = ScheduleRule()
            )

            ScheduleScreen(
                app = app,
                rule = current,
                onBack = {
                    screen = if (rules.any { it.appPackage == app.packageName }) BlockSubScreen.RULE_DETAIL else BlockSubScreen.APP_PICKER
                },
                onSave = { scheduled ->
                    val finalRule = scheduled.copy(blockEntireApp = true, features = emptyList(), active = true, paused = false)
                    saveRule(finalRule)
                    draftRule = finalRule
                    screen = BlockSubScreen.OVERVIEW
                }
            )
        }

        BlockSubScreen.RULE_DETAIL -> {
            val rule = draftRule ?: return
            val app = selectedApp ?: InstalledAppItem(rule.appPackage, rule.appName, rule.appCategory, rule.appName.take(1).uppercase())

            RuleDetailScreen(
                rule = rule,
                app = app,
                onBack = { screen = BlockSubScreen.OVERVIEW },
                onEdit = {
                    screen = BlockSubScreen.SCHEDULE
                },
                onPauseAttempt = {
                    onProtectedActionAttempt(
                        ProtectedAction.PAUSE_RULE(rule.appPackage, rule.appName)
                    )
                },
                onDeleteAttempt = {
                    onProtectedActionAttempt(
                        ProtectedAction.DELETE_RULE(rule.appPackage, rule.appName)
                    )
                }
            )
        }
    }
}

enum class BlockSubScreen {
    OVERVIEW,
    APP_PICKER,
    PROTECTION_MODE_SELECTOR,
    FEATURE_SELECTOR,
    SCHEDULE,
    RULE_DETAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionModeSelectorScreen(
    app: InstalledAppItem,
    onBack: () -> Unit,
    onFullAppBlock: () -> Unit,
    onFeatureBlock: () -> Unit
) {
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Choose Protection Mode", color = BlockifyText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionTile(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Block,
                title = "Block Entire App",
                subtitle = "Completely block access to this app.",
                onClick = onFullAppBlock
            )
            ActionTile(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Tune,
                title = "Block Specific Features",
                subtitle = "Block only selected parts of the app.",
                onClick = onFeatureBlock
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureSelectorScreen(
    app: InstalledAppItem,
    rule: ProtectionRule,
    onBack: () -> Unit,
    onSave: (ProtectionRule) -> Unit
) {
    val features = remember(app) { BlockifyFeatureCatalog.candidateFeatures(app.packageName, app.appName) }
    val selectedFeatures = remember { mutableStateListOf<BlockFeature>() }
    
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Select Features", color = BlockifyText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    onSave(rule.copy(features = selectedFeatures.map { SelectedFeature(it.id, true) }))
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Confirm Features")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(features) { feature ->
                val isSelected = feature in selectedFeatures
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        if (isSelected) selectedFeatures.remove(feature) else selectedFeatures.add(feature)
                    },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE0E7FF) else Color.White),
                    border = BorderStroke(1.dp, if (isSelected) BlockifyBlue else BlockifyBorder)
                ) {
                    Text(feature.title, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }
            item {
                Text("Custom discovery not fully implemented yet", color = BlockifyMuted, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

sealed interface ProtectedAction {
    data class PAUSE_RULE(val packageName: String, val appName: String) : ProtectedAction
    data class DELETE_RULE(val packageName: String, val appName: String) : ProtectedAction
    data class FULL_APP_ACCESS(
        val packageName: String,
        val appName: String
    ) : ProtectedAction
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val category: String,
    val iconText: String = "▣"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockOverviewScreen(
    rules: List<ProtectionRule>,
    onAddProtection: () -> Unit,
    onRuleClick: (ProtectionRule) -> Unit
) {
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Block Apps",
                            color = BlockifyText,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Choose apps to completely block permanently or on schedule.",
                            color = BlockifyMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Protection settings",
                        tint = BlockifyBlue
                    )
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProtectionModeHeader()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Apps,
                        title = "Block App",
                        subtitle = "Select app to block",
                        onClick = onAddProtection
                    )
                    ActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarMonth,
                        title = "Schedule",
                        subtitle = "24/7 or custom hours",
                        onClick = onAddProtection
                    )
                }
            }

            item {
                SectionTitle("Your Blocked Apps")
            }

            if (rules.isEmpty()) {
                item {
                    EmptyProtectionCard(onAddProtection = onAddProtection)
                }
            } else {
                items(rules, key = { "${it.appPackage}:${it.appName}" }) { rule ->
                    ProtectionSummaryCard(
                        rule = rule,
                        onClick = { onRuleClick(rule) }
                    )
                }
            }

            item {
                Button(
                    onClick = onAddProtection,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlockifyBlue)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add App to Block", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProtectionModeHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F6FF)),
        border = BorderStroke(1.dp, Color(0xFFDDEAFF))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = BlockifyBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Strict Full App Blocking",
                    color = BlockifyText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Block entire applications all day or on customized schedules.",
                    color = BlockifyMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BlockifyBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = BlockifyBlue, modifier = Modifier.size(25.dp))
            Text(title, color = BlockifyText, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BlockifyMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = BlockifyText,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun EmptyProtectionCard(onAddProtection: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BlockifySurface),
        border = BorderStroke(1.dp, BlockifyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFFB3BDCA),
                modifier = Modifier.size(42.dp)
            )
            Text(
                "No apps blocked yet",
                color = BlockifyText,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Select an app and choose when you want to block it completely.",
                color = BlockifyMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onAddProtection,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Block your first app")
            }
        }
    }
}

@Composable
private fun ProtectionSummaryCard(
    rule: ProtectionRule,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BlockifyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconView(
                packageName = rule.appPackage,
                fallbackText = rule.appName,
                size = 46.dp,
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(rule.appName, color = BlockifyText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Entire app blocked",
                    color = BlockifyMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    scheduleLabel(rule.schedule),
                    color = BlockifyMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (rule.active && !rule.paused) "Active" else "Paused",
                        color = if (rule.active && !rule.paused) BlockifyGreen else BlockifyMuted
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerScreen(
    apps: List<InstalledAppItem>,
    onBack: () -> Unit,
    onSelect: (InstalledAppItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Select an app to block", color = BlockifyText, fontWeight = FontWeight.Bold)
                        Text(
                            "${apps.size} installed on your device",
                            color = BlockifyMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search installed apps...") },
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.height(12.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = Color(0xFFB1BBC8),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "No installed apps found",
                            color = BlockifyText,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Only applications installed on your device will appear here.",
                            color = BlockifyMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps matching \"$query\"",
                        color = BlockifyMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(app) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, BlockifyBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconView(
                                    packageName = app.packageName,
                                    fallbackText = app.appName,
                                    size = 42.dp,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, color = BlockifyText, fontWeight = FontWeight.Bold)
                                    Text(app.category, color = BlockifyMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = Color(0xFFB1BBC8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleScreen(
    app: InstalledAppItem,
    rule: ProtectionRule,
    onBack: () -> Unit,
    onSave: (ProtectionRule) -> Unit
) {
    var everyDay by remember { mutableStateOf(rule.schedule.everyDay) }
    var selectedDays by remember { mutableStateOf(rule.schedule.selectedDays) }
    var allDay by remember { mutableStateOf(rule.schedule.allDay) }
    var startMinutes by remember { mutableStateOf(if (rule.schedule.startMinutes == 0 && rule.schedule.endMinutes >= 24 * 60 - 1) 9 * 60 else rule.schedule.startMinutes) }
    var endMinutes by remember { mutableStateOf(if (rule.schedule.startMinutes == 0 && rule.schedule.endMinutes >= 24 * 60 - 1) 20 * 60 else rule.schedule.endMinutes) }

    fun currentSchedule() = ScheduleRule(
        everyDay = everyDay,
        selectedDays = selectedDays,
        allDay = allDay,
        startMinutes = if (allDay) 0 else startMinutes,
        endMinutes = if (allDay) 24 * 60 - 1 else endMinutes
    )

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Block Schedule", color = BlockifyText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        onSave(rule.copy(schedule = currentSchedule(), blockEntireApp = true, active = true, paused = false))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlockifyBlue)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Block Rule", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BlockifySurface),
                    border = BorderStroke(1.dp, BlockifyBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconView(
                            packageName = app.packageName,
                            fallbackText = app.appName,
                            size = 44.dp,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Block Entire App",
                                color = BlockifyText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                app.appName,
                                color = BlockifyMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Block Frequency",
                    color = BlockifyText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                ChoiceCard(
                    selected = everyDay,
                    title = "Every day",
                    subtitle = "Monday – Sunday",
                    onClick = {
                        everyDay = true
                        selectedDays = WeekDay.entries.toSet()
                    }
                )
            }

            item {
                ChoiceCard(
                    selected = !everyDay,
                    title = "Custom days",
                    subtitle = "Choose specific days of the week",
                    onClick = { everyDay = false }
                )
            }

            item {
                Text(
                    "Block Time",
                    color = BlockifyText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                ChoiceCard(
                    selected = allDay,
                    title = "Full Time / 24 Hours",
                    subtitle = "App is blocked at all times",
                    onClick = { allDay = true }
                )
            }

            item {
                ChoiceCard(
                    selected = !allDay,
                    title = "Scheduled Hours",
                    subtitle = "Set specific hours during the day",
                    onClick = { allDay = false }
                )
            }

            if (!allDay) {
                item {
                    Text(
                        "Time Range",
                        color = BlockifyText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    TimeRangeRow(
                        startMinutes = startMinutes,
                        endMinutes = endMinutes,
                        onStartChanged = { startMinutes = it },
                        onEndChanged = { endMinutes = it }
                    )
                }
            }

            if (!everyDay) {
                item {
                    Text(
                        "Select Active Days",
                        color = BlockifyText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeekDay.entries.forEach { day ->
                            val selected = day in selectedDays
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedDays =
                                            if (selected) selectedDays - day
                                            else selectedDays + day
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) BlockifyBlue else Color(0xFFF5F7FA)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        day.short,
                                        color = if (selected) Color.White else BlockifyMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                InfoCard(
                    title = "Continuous Enforcement",
                    message = "RecoveryShield immediately blocks ${app.appName} when opened according to this schedule."
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (selected) BlockifyBlue else BlockifyBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = RoundedCornerShape(50),
                color = if (selected) BlockifyBlue else Color.Transparent,
                border = if (!selected) BorderStroke(1.dp, Color(0xFF9AA6B4)) else null
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = BlockifyText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = BlockifyMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimeRangeRow(
    startMinutes: Int,
    endMinutes: Int,
    onStartChanged: (Int) -> Unit,
    onEndChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimeSelector(
            modifier = Modifier.weight(1f),
            label = "Start",
            minutes = startMinutes,
            onMinutesChanged = onStartChanged
        )
        TimeSelector(
            modifier = Modifier.weight(1f),
            label = "End",
            minutes = endMinutes,
            onMinutesChanged = onEndChanged
        )
    }
}

@Composable
private fun TimeSelector(
    modifier: Modifier,
    label: String,
    minutes: Int,
    onMinutesChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val options = remember {
        (0..47).map { it * 30 }
    }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BlockifyText)
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = BlockifyMuted)
                Text(formatMinutes(minutes), fontWeight = FontWeight.SemiBold)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(formatMinutes(value)) },
                    onClick = {
                        onMinutesChanged(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDetailScreen(
    rule: ProtectionRule,
    app: InstalledAppItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPauseAttempt: () -> Unit,
    onDeleteAttempt: () -> Unit
) {
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(rule.appName, color = BlockifyText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BlockifySurface),
                    border = BorderStroke(1.dp, BlockifyBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconView(
                            packageName = app.packageName,
                            fallbackText = app.appName,
                            size = 48.dp,
                            shape = RoundedCornerShape(13.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.appName, color = BlockifyText, fontWeight = FontWeight.Bold)
                            Text(app.category, color = BlockifyMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F6FF)),
                    border = BorderStroke(1.dp, Color(0xFFDDEAFF))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Entire App Blocked",
                            color = BlockifyText,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "RecoveryShield restricts access to ${rule.appName} during active hours.",
                            color = BlockifyMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            scheduleLabel(rule.schedule),
                            color = BlockifyBlue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Schedule")
                    }

                    OutlinedButton(
                        onClick = onPauseAttempt,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pause")
                    }

                    OutlinedButton(
                        onClick = onDeleteAttempt,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlockifyRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }

            item {
                Text(
                    "Protected Actions",
                    color = BlockifyText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                InfoCard(
                    title = "Pause & Delete Protection",
                    message = "To prevent impulsive relapses, pause or delete requests are protected by the safety cooldown."
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFD)),
        border = BorderStroke(1.dp, BlockifyBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = BlockifyText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(message, color = BlockifyMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun scheduleLabel(schedule: ScheduleRule): String {
    val days = if (schedule.everyDay) {
        "Every day"
    } else {
        schedule.selectedDays.joinToString(", ") { it.short }
    }
    return if (schedule.allDay) {
        "$days • Full Time (24 Hours)"
    } else {
        "$days • ${formatMinutes(schedule.startMinutes)} – ${formatMinutes(schedule.endMinutes)}"
    }
}

private fun formatMinutes(total: Int): String {
    val safe = total.coerceIn(0, 1439)
    val h24 = safe / 60
    val minute = safe % 60
    val am = h24 < 12
    val hour12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    val suffix = if (am) "AM" else "PM"
    return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, suffix)
}
