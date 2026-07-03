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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dima.kidsvideoplayer.ui.theme.DialogBackground
import com.dima.kidsvideoplayer.ui.theme.DotBorder
import com.dima.kidsvideoplayer.ui.theme.DotEmpty
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.KeypadBackground
import com.dima.kidsvideoplayer.ui.theme.TextGray
import kotlinx.coroutines.delay

/** Default PIN code — extract to DataStore for configurable PIN in future. */
const val DEFAULT_PIN = "1234"

/** Maximum failed attempts before lockout. */
private const val MAX_PIN_ATTEMPTS = 3

/** Lockout duration in seconds. */
private const val LOCKOUT_DURATION_SECONDS = 30

/**
 * PIN code input dialog with rate limiting.
 *
 * Security features:
 * - Configurable PIN via [correctPin] parameter
 * - Rate limiting: after [MAX_PIN_ATTEMPTS] failed attempts, locks for [LOCKOUT_DURATION_SECONDS]
 * - PIN is compared as plain text (for a kids' app; use hashed storage for production)
 *
 * @param onDismiss Called when dialog is cancelled
 * @param onPinCorrect Called when correct PIN is entered
 * @param title Dialog title shown above the PIN dots
 * @param correctPin The expected PIN code (default from [DEFAULT_PIN] constant)
 */
@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    onPinCorrect: () -> Unit,
    title: String = "Введите ПИН",
    correctPin: String = DEFAULT_PIN
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLockedOut by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutRemaining by remember { mutableIntStateOf(0) }

    // Lockout countdown timer
    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            lockoutRemaining = LOCKOUT_DURATION_SECONDS
            while (lockoutRemaining > 0) {
                delay(1000)
                lockoutRemaining--
            }
            isLockedOut = false
            failedAttempts = 0
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DialogBackground,
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
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(correctPin.length) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < pin.length) GreenPrimary
                                    else DotEmpty
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isError) Color.Red else DotBorder,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isLockedOut) "Подождите $lockoutRemaining сек."
                               else "Неверный ПИН-код",
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
                                            if (isLockedOut) return@Surface
                                            isError = false
                                            when (key) {
                                                "⌫" -> {
                                                    if (pin.isNotEmpty()) {
                                                        pin = pin.dropLast(1)
                                                    }
                                                }
                                                else -> {
                                                    if (pin.length < correctPin.length) {
                                                        pin += key
                                                    }
                                                    // Check PIN when all digits entered
                                                    if (pin.length == correctPin.length) {
                                                        if (pin == correctPin) {
                                                            onPinCorrect()
                                                        } else {
                                                            failedAttempts++
                                                            if (failedAttempts >= MAX_PIN_ATTEMPTS) {
                                                                isLockedOut = true
                                                            }
                                                            isError = true
                                                            pin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = KeypadBackground
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
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
