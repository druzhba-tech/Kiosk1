package com.kiosk.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiosk.browser.core.update.UpdateState
import com.kiosk.browser.ui.theme.*

@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstallClick: (downloadUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateState.Idle) return

    Dialog(
        onDismissRequest = {
            if (state !is UpdateState.Downloading && state !is UpdateState.Installing) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is UpdateState.Downloading && state !is UpdateState.Installing,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Иконка
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(27.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state is UpdateState.Downloading || state is UpdateState.Installing)
                            Icons.Default.CloudDownload else Icons.Default.SystemUpdate,
                        contentDescription = "Update",
                        tint = NeonCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Заголовок и контент в зависимости от фазы
                when (state) {
                    is UpdateState.Checking -> {
                        Text(
                            text = "Проверка обновлений...",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(32.dp))
                    }

                    is UpdateState.Available -> {
                        Text(
                            text = "Доступно обновление v${state.versionName}",
                            color = NeonCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.releaseNotes,
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Позже", color = TextMuted)
                            }
                            Button(
                                onClick = { onInstallClick(state.downloadUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text("Установить", color = CyberBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = "Скачивание обновления...",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = state.progressPercent / 100f,
                            color = NeonCyan,
                            trackColor = CyberSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.downloadedBytes / (1024 * 1024)} MB / ${state.totalBytes / (1024 * 1024)} MB",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${state.progressPercent}%",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    is UpdateState.Installing -> {
                        Text(
                            text = "Установка обновления...",
                            color = NeonGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Приложение автоматически перезапустится",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        CircularProgressIndicator(color = NeonGreen, modifier = Modifier.size(36.dp))
                    }

                    is UpdateState.Error -> {
                        Text(
                            text = "Ошибка обновления",
                            color = NeonRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.message,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface)
                        ) {
                            Text("Закрыть", color = TextWhite)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}