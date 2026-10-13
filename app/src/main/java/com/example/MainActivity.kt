package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.recoveryshield.domain.model.PermissionType
import com.example.recoveryshield.ui.MainScreen
import com.example.recoveryshield.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_DEVICE_ADMIN = 1001
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenPermissionSettings = { type ->
                        openPermissionSettings(type)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_DEVICE_ADMIN) {
            viewModel.onResume()
        }
    }

    private fun openPermissionSettings(type: PermissionType) {
        val intent = viewModel.engine.permissionOrchestrator.createIntentForPermission(type)
        try {
            if (type == PermissionType.DEVICE_ADMIN) {
                // ACTION_ADD_DEVICE_ADMIN must NOT have FLAG_ACTIVITY_NEW_TASK
                // so Android's DeviceAdminAdd activity opens directly and displays the Activate screen
                intent.flags = 0
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to launch intent for $type: ${e.message}", e)
            if (type == PermissionType.DEVICE_ADMIN) {
                // Secondary targeted fallbacks for Device Admin settings
                try {
                    val fallbackIntent = android.content.Intent("android.settings.DEVICE_ADMIN_SETTINGS").apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(fallbackIntent)
                } catch (_: Exception) {
                    try {
                        val fallbackSecurity = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(fallbackSecurity)
                    } catch (_: Exception) {
                        try {
                            startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                        } catch (_: Exception) {}
                    }
                }
            } else {
                // General settings fallback for other permissions
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }
}

