package com.kiosk.browser.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiosk.browser.ui.theme.*

/**
 * Мастер первоначальной настройки Kiosk при первом запуске:
 * Позволяет пользователю сразу выбрать:
 * 1. Основной режим: Веб-сайт (URL) или Android-приложение (по умолчанию).
 * 2. Ввести ссылку или выбрать установленное приложение из списка.
 * 3. Задать PIN-код администратора для защиты настроек.
 */
@Composable
fun FirstRunSetupDialog(
    initialUrl: String,
    initialPin: String,
    onComplete: (mode: String, url: String, appPackage: String, pin: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val pm = context.packageManager

    var selectedMode by remember { mutableStateOf("WEB") } // "WEB" или "APP"
    var urlText by remember { mutableStateOf(initialUrl) }
    var selectedPackage by remember { mutableStateOf("") }
    var selectedAppName by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf(initialPin) }

    var showAppPicker by remember { mutableStateOf(false) }

    // Загрузка названия выбранного приложения при изменении пакета
    LaunchedEffect(selectedPackage) {
        if (selectedPackage.isNotEmpty()) {
            try {
                val appInfo = pm.getApplicationInfo(selectedPackage, 0)
                selectedAppName = pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                selectedAppName = selectedPackage
            }
        }
    }

    Dialog(
        onDismissRequest = {}, // Нельзя закрыть свайпом мимо — обязательна начальная настройка
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CyberCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Заголовок ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(27.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "ПЕРВОНАЧАЛЬНАЯ НАСТРОЙКА",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Выберите, что должен отображать киоск по умолчанию: веб-сайт или установленное приложение.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // ── Выбор режима: Сайт или Приложение ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Карточка: Веб-сайт
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedMode = "WEB" }
                            .border(
                                width = if (selectedMode == "WEB") 1.5.dp else 0.5.dp,
                                color = if (selectedMode == "WEB") NeonCyan else CyberBorder,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMode == "WEB") Color(0xFF0D2030) else CyberSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = if (selectedMode == "WEB") NeonCyan else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "Веб-сайт",
                                color = if (selectedMode == "WEB") TextWhite else TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Карточка: Приложение
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedMode = "APP" }
                            .border(
                                width = if (selectedMode == "APP") 1.5.dp else 0.5.dp,
                                color = if (selectedMode == "APP") NeonCyan else CyberBorder,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMode == "APP") Color(0xFF0D2030) else CyberSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = null,
                                tint = if (selectedMode == "APP") NeonCyan else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "Приложение",
                                color = if (selectedMode == "APP") TextWhite else TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Содержимое в зависимости от выбранного режима ───────────
                if (selectedMode == "WEB") {
                    // Ввод URL
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Стартовый адрес сайта (URL):",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            placeholder = { Text("https://your-site.com", color = TextMuted) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrEmpty()) urlText = clip
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Вставить", tint = NeonCyan)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CyberBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = NeonCyan
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                } else {
                    // Выбор приложения
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Основное приложение киоска:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (selectedPackage.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0D2030))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedAppName,
                                        color = TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = selectedPackage,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                TextButton(onClick = { showAppPicker = true }) {
                                    Text("Изменить", color = NeonCyan, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = { showAppPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = NeonCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Выбрать приложение из списка", color = TextWhite)
                            }
                        }
                    }
                }

                // ── PIN-код администратора ────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PIN-код для входа в настройки:",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { if (it.length <= 8) pinText = it },
                        placeholder = { Text("1234", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonGreen) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = NeonGreen
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // ── Кнопка «Запустить Киоск» ─────────────────────────────────
                Button(
                    onClick = {
                        val finalUrl = if (urlText.isNotBlank()) urlText.trim() else "https://demo.home-assistant.io"
                        val finalPin = if (pinText.isNotBlank()) pinText.trim() else "1234"
                        onComplete(selectedMode, finalUrl, selectedPackage, finalPin)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyberBlack)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ЗАПУСТИТЬ КИОСК",
                        color = CyberBlack,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    // Диалог выбора установленного приложения
    if (showAppPicker) {
        AppPickerDialog(
            selectedPackages = if (selectedPackage.isNotEmpty()) listOf(selectedPackage) else emptyList(),
            onConfirm = { pkgs ->
                if (pkgs.isNotEmpty()) {
                    selectedPackage = pkgs.first()
                }
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}
