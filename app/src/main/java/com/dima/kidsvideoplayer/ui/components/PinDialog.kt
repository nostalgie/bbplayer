package com.dima.kidsvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * PIN code input dialog.
 *
 * @param onDismiss Called when dialog is cancelled
 * @param onPinCorrect Called when correct PIN is entered
 * @param correctPin The expected PIN code (default "1234")
 */
@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    onPinCorrect: () -> Unit,
    correctPin: String = "1234"
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val pinLength = correctPin.length

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "🔒 Введите ПИН",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pinLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < pin.length) Color(0xFF4CAF50)
                                    else Color(0xFF555555)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isError) Color.Red else Color(0xFF888888),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Неверный ПИН-код",
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric keypad (3x4 grid)
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                )

                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (key.isNotEmpty()) {
                                    Surface(
                                        onClick = {
                                            isError = false
                                            when (key) {
                                                "⌫" -> {
                                                    if (pin.isNotEmpty()) {
                                                        pin = pin.dropLast(1)
                                                    }
                                                }
                                                else -> {
                                                    if (pin.length < pinLength) {
                                                        pin += key
                                                    }
                                                    // Check PIN when all digits entered
                                                    if (pin.length == pinLength) {
                                                        if (pin == correctPin) {
                                                            onPinCorrect()
                                                        } else {
                                                            isError = true
                                                            pin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF3C3C3C)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel button
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Отмена",
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
