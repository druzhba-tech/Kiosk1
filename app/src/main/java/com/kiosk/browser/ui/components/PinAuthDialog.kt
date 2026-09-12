package com.kiosk.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kiosk.browser.ui.theme.*

@Composable
fun PinAuthDialog(
    expectedPin: String,
    onPinCorrect: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun appendDigit(digit: String) {
        if (enteredPin.length < 8) {
            isError = false
            val newPin = enteredPin + digit
            enteredPin = newPin
            if (newPin == expectedPin) {
                onPinCorrect()
            } else if (newPin.length == expectedPin.length) {
                isError = true
                enteredPin = ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberCard),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(1.dp, if (isError) NeonRed else NeonCyan, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SECURITY CHECK",
                    color = NeonCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isError) "НЕВЕРНЫЙ PIN-КОД" else "Введите PIN администратора",
                    color = if (isError) NeonRed else TextMuted,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // PIN индикаторы
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = expectedPin.length.coerceAtLeast(4)
                    for (i in 0 until count) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) NeonCyan else Color.Transparent)
                                .border(1.5.dp, if (isError) NeonRed else NeonCyan, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Цифровая клавиатура 3x4
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CANCEL", "0", "DEL")
                )

                for (row in keys) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(CyberSurface)
                                    .border(1.dp, CyberBorder, CircleShape)
                                    .clickable {
                                        when (key) {
                                            "CANCEL" -> onDismiss()
                                            "DEL" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            else -> appendDigit(key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "CANCEL" -> Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextMuted)
                                    "DEL" -> Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = NeonOrange)
                                    else -> Text(
                                        text = key,
                                        color = TextWhite,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
