package com.kiosk.browser.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * Значок в статус-баре:
 *  - Мигает зелёным → доступна новая версия (нажми чтобы скачать)
 *  - Мигает голубым + "XX%" → идёт фоновая загрузка APK
 *  - Мигает жёлтым + "..." → идёт установка
 *
 * Приложение продолжает работать во всех состояниях!
 */
@Composable
fun UpdateAvailableBadge(
    versionName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = versionName.endsWith("%")
    val isInstalling  = versionName == "..."

    val badgeColor = when {
        isInstalling  -> NeonOrange
        isDownloading -> NeonCyan
        else          -> NeonGreen
    }
    val bgColor = when {
        isInstalling  -> Color(0xFF1A1000)
        isDownloading -> Color(0xFF001A1A)
        else          -> Color(0xFF0D1A00)
    }

    val pulse by rememberInfiniteTransition(label = "update_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isDownloading || isInstalling) 0.6f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isDownloading) 600 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = 0.95f))
            .border(1.dp, badgeColor.copy(alpha = pulse), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            if (isInstalling) Icons.Default.SystemUpdate else Icons.Default.SystemUpdate,
            contentDescription = "Обновление",
            tint = badgeColor.copy(alpha = if (isDownloading) pulse else 1f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = if (isDownloading) "↓ $versionName"
                   else if (isInstalling) "⚙ УСТВ"
                   else "↑ v$versionName",
            color = badgeColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            fontFamily = FontFamily.Monospace
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
