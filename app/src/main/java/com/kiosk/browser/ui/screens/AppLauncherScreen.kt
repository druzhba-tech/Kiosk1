package com.kiosk.browser.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.ui.theme.*

data class LauncherItem(
    val title: String,
    val packageName: String,
    val isWeb: Boolean = false,
    val url: String = ""
)

@Composable
fun AppLauncherScreen(
    allowedPackages: List<String>,
    onOpenWeb: () -> Unit,
    onOpenSettingsWithPin: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val appList = remember(allowedPackages) {
        val list = mutableListOf<LauncherItem>()
        // Добавляем встроенный браузер
        list.add(LauncherItem("Kiosk Browser", context.packageName, isWeb = true))

        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && (allowedPackages.isEmpty() || allowedPackages.contains(pkg))) {
                list.add(
                    LauncherItem(
                        title = info.loadLabel(pm).toString(),
                        packageName = pkg
                    )
                )
            }
        }
        list
    }

    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APP LAUNCHER",
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                IconButton(onClick = onOpenSettingsWithPin) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextMuted)
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(appList) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            if (item.isWeb) {
                                onOpenWeb()
                            } else {
                                val launchIntent = pm.getLaunchIntentForPackage(item.packageName)
                                launchIntent?.let { context.startActivity(it) }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = CyberCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyberSurface)
                                .border(0.5.dp, if (item.isWeb) NeonCyan else NeonGreen, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (item.isWeb) Icons.Default.Language else Icons.Default.Apps,
                                contentDescription = null,
                                tint = if (item.isWeb) NeonCyan else NeonGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = item.title,
                            color = TextWhite,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
