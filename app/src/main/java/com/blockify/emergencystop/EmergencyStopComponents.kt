package com.blockify.emergencystop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top Home Header Chip button for Emergency Stop.
 * Replaces the small clock icon next to the "Protection" header.
 */
@Composable
fun EmergencyStopHomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFEE2E2), // Subtle light red/pink background
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
        modifier = modifier.testTag("home_emergency_stop_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFFDC2626), // Primary Red
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Emergency Stop",
                color = Color(0xFFDC2626),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 8-Box Emergency Code Input Component with hidden underlying BasicTextField.
 */
@Composable
fun EmergencyCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_pulse")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("emergency_code_input_container"),
        contentAlignment = Alignment.Center
    ) {
        // Invisible input field intercepting key events
        BasicTextField(
            value = code,
            onValueChange = { newText ->
                val filtered = newText
                    .uppercase()
                    .filter { it.isLetterOrDigit() }
                    .take(8)
                onCodeChange(filtered)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
                .matchParentSize()
                .testTag("emergency_code_hidden_textfield"),
            decorationBox = { /* Hidden decoration box */ }
        )

        // Visual 8-box presentation
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until 8) {
                val char = code.getOrNull(index)
                val isFocused = code.length == index

                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isFocused) Color(0xFFF1F5F9) else Color(0xFFF8FAFC))
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = when {
                                isFocused -> Color(0xFF2563EB)
                                char != null -> Color(0xFF94A3B8)
                                else -> Color(0xFFE2E8F0)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (char != null) {
                        Text(
                            text = char.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    } else if (isFocused) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(22.dp)
                                .background(Color(0xFF2563EB).copy(alpha = cursorAlpha))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Processing checklist showing server steps during verification.
 */
@Composable
fun EmergencyStepChecklist(
    activeStepIndex: Int,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        EmergencyStopStep.VALIDATING_CODE,
        EmergencyStopStep.STOPPING_SERVICES,
        EmergencyStopStep.DISABLING_MONITORING,
        EmergencyStopStep.REMOVING_OVERLAYS
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        steps.forEachIndexed { index, step ->
            val isCompleted = index < activeStepIndex
            val isCurrent = index == activeStepIndex

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> Color(0xFF10B981)
                                isCurrent -> Color(0xFF2563EB).copy(alpha = 0.15f)
                                else -> Color(0xFFF1F5F9)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (isCurrent) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = step.label,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isCompleted -> Color(0xFF0F172A)
                        isCurrent -> Color(0xFF2563EB)
                        else -> Color(0xFF94A3B8)
                    }
                )
            }
        }
    }
}
