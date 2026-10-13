package com.example.recoveryshield.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class AccessibilityPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AccessibilityPopup {
                finish()
            }
        }
    }
}

@Composable
fun AccessibilityPopup(onDismiss: () -> Unit) {
    val showDialog = remember { mutableStateOf(true) }
    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showDialog.value = false
                onDismiss()
            },
            title = { Text(text = "Blockify Protection") },
            text = { Text(text = "You are currently in the Blockify accessibility settings. Ensure the service is enabled for full protection.") },
            confirmButton = {
                Button(onClick = {
                    showDialog.value = false
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        )
    }
}
