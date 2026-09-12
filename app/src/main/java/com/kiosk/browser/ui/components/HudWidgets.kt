package com.kiosk.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.ui.theme.*

@Composable
fun HudStatusBadge(
    batteryLevel: Int,
    isCharging: Boolean,
    isKioskActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(GlassBg, RoundedCornerShape(8.dp))
            .border(0.5.dp, CyberBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isKioskActive) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Kiosk Active",
                tint = NeonGreen,
                modifier = Modifier.size(14.dp)
            )
        }

        Icon(
            if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
            contentDescription = "Battery",
            tint = if (batteryLevel < 20) NeonRed else NeonCyan,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = "$batteryLevel%",
            color = TextWhite,
            fontSize = 12.sp
        )
    }
}

/**
 * Невидимая секретная область в верхнем правом углу экрана для вызова PIN диалога
 */
@Composable
fun SecretTapOverlay(
    onSecretTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(80.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSecretTap()
            }
    )
}
