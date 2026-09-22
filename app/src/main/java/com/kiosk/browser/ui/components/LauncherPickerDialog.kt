package com.kiosk.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.kiosk.browser.MainActivity
import com.kiosk.browser.admin.LauncherAppInfo
import com.kiosk.browser.ui.theme.*

/**
 * Диалог выбора лаунчера (домашнего экрана) по умолчанию.
 * Показывает все установленные домашние экраны (Kiosk, системный рабочий стол, сторонние лаунчеры).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherPickerDialog(
    mainActivity: MainActivity,
    onLauncherSelected: (LauncherAppInfo) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var launcherList by remember {
        mutableStateOf(mainActivity.deviceOwnerManager.getInstalledLaunchers())
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery, launcherList) {
        if (searchQuery.isBlank()) launcherList
        else launcherList.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .background(CyberSurface, RoundedCornerShape(16.dp))
                .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
        ) {
            // ── Заголовок ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF0D1525),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ВЫБОР ЛАУНЧЕРА ПО УМОЛЧАНИЮ",
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Выберите главный рабочий стол для кнопки Home",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // ── Поисковая строка ──────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Поиск лаунчера по названию или пакету...", color = TextMuted, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CyberBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = NeonCyan
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // ── Список установленных лаунчеров ────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Лаунчеры не найдены", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }

                items(filteredList, key = { it.packageName }) { app ->
                    val isCurrentDefault = app.isCurrentDefault
                    val isKiosk = app.isKiosk

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCurrentDefault) Color(0xFF0F2636) else CyberCard
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    isCurrentDefault -> NeonGreen.copy(alpha = 0.7f)
                                    isKiosk -> NeonCyan.copy(alpha = 0.4f)
                                    else -> CyberBorder
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onLauncherSelected(app)
                                // Перезагружаем список статусов
                                launcherList = mainActivity.deviceOwnerManager.getInstalledLaunchers()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Иконка приложения
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF07111D)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app.icon != null) {
                                val bitmap = remember(app.packageName) {
                                    runCatching { app.icon.toBitmap(44, 44).asImageBitmap() }.getOrNull()
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(38.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                                }
                            } else {
                                Icon(Icons.Default.Home, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                            }
                        }

                        // Название, пакет и бейджи
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.label,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isKiosk) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "KIOSK",
                                        color = NeonCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = app.packageName,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isCurrentDefault) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "АКТИВЕН ПО УМОЛЧАНИЮ",
                                        color = NeonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Радио-индикатор выбора
                        RadioButton(
                            selected = isCurrentDefault,
                            onClick = {
                                onLauncherSelected(app)
                                launcherList = mainActivity.deviceOwnerManager.getInstalledLaunchers()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = NeonGreen,
                                unselectedColor = TextMuted
                            )
                        )
                    }
                }
            }

            // ── Нижняя панель действий ────────────────────────────────────────
            HorizontalDivider(color = CyberBorder, modifier = Modifier.padding(top = 8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Открыть системные настройки лаунчера
                    OutlinedButton(
                        onClick = {
                            mainActivity.deviceOwnerManager.openHomeSettings(mainActivity)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Настройки Android", fontSize = 11.sp)
                    }

                    // Сбросить выбор (спрашивать при нажатии Home)
                    OutlinedButton(
                        onClick = {
                            onResetDefault()
                            launcherList = mainActivity.deviceOwnerManager.getInstalledLaunchers()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                        border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сбросить выбор", fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCard),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ЗАКРЫТЬ", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
