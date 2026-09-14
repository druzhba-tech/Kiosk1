package com.kiosk.browser.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiosk.browser.core.update.UpdateManager
import com.kiosk.browser.ui.theme.*

/**
 * Диалог обновления — показывает прогресс загрузки и установки APK.
 *
 * Состояния:
 *  - UpdateAvailable  → предлагает установить
 *  - Downloading      → прогресс-бар скачивания
 *  - Installing       → спиннер установки
 *  - InstallSuccess   → успех
 *  - Error            → ошибка
 */
@Composable
fun UpdateDialog(
    updateState: UpdateManager.UpdateState,
    onInstall: (apkUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            // Не закрываем во время загрузки/установки
            if (updateState !is UpdateManager.UpdateState.Downloading &&
                updateState !is UpdateManager.UpdateState.Installing
            ) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1A0B), Color(0xFF0D1520))
                    )
                )
                .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            when (updateState) {

                // ── Доступно обновление ────────────────────────────────────────
                is UpdateManager.UpdateState.UpdateAvailable -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "ДОСТУПНО ОБНОВЛЕНИЕ",
                            color = NeonGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Версия ${updateState.versionName}",
                            color = TextWhite,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Установка произойдёт автоматически без прерывания работы киоска.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                            ) {
                                Text("ПОЗЖЕ")
                            }
                            Button(
                                onClick = { onInstall(updateState.apkUrl) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CyberBlack, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("УСТАНОВИТЬ", color = CyberBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ── Загрузка APK ───────────────────────────────────────────────
                is UpdateManager.UpdateState.Downloading -> {
                    val progress = updateState.progress / 100f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "ЗАГРУЗКА ОБНОВЛЕНИЯ",
                            color = NeonCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        // Cyber прогресс-бар
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberSurface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(NeonCyan, NeonGreen)
                                        )
                                    )
                            )
                        }

                        Text(
                            text = "${updateState.progress}%",
                            color = NeonCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Не закрывайте приложение во время загрузки...",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Установка APK ──────────────────────────────────────────────
                is UpdateManager.UpdateState.Installing -> {
                    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing)
                        ),
                        label = "rotation"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NeonGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "УСТАНОВКА...",
                            color = NeonGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Приложение обновляется. Устройство перезапустится автоматически.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Успешная установка ─────────────────────────────────────────
                is UpdateManager.UpdateState.InstallSuccess -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✅", fontSize = 48.sp, textAlign = TextAlign.Center)
                        Text(
                            text = "ОБНОВЛЕНИЕ УСТАНОВЛЕНО",
                            color = NeonGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Киоск продолжает работу с новой версией.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("OK", color = CyberBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Ошибка ─────────────────────────────────────────────────────
                is UpdateManager.UpdateState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("⚠️", fontSize = 40.sp, textAlign = TextAlign.Center)
                        Text(
                            text = "ОШИБКА ОБНОВЛЕНИЯ",
                            color = NeonRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = updateState.message,
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ЗАКРЫТЬ", color = CyberBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> { /* Idle / Checking — диалог не показывается */ }
            }
        }
    }
}
