package com.videox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 设置页面（所有选项都会持久化保存，并被实际功能读取）
@Composable
fun SettingsPage() {

    val context = LocalContext.current

    var saveToGallery by remember {
        mutableStateOf(SettingsStore.saveToGallery(context))
    }

    var autoClipboard by remember {
        mutableStateOf(SettingsStore.autoClipboard(context))
    }

    var wifiOnly by remember {
        mutableStateOf(SettingsStore.wifiOnly(context))
    }

    val themeColors = listOf(
        Color(0xFF62E6A2),
        Color(0xFF64B5FF),
        Color(0xFFB388FF),
        Color(0xFFFF80AB),
        Color(0xFFFFB74D)
    )

    val themeNames = listOf(
        "绿色",
        "蓝色",
        "紫色",
        "粉色",
        "橙色"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            "设置",
            color = TextMain,
            fontSize = 28.sp
        )

        Text(
            "自定义 VideoX 使用体验",
            color = TextMuted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        RoundedPanel {

            Text(
                "主题颜色",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                themeColors.forEachIndexed { index, color ->

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color,
                                    RoundedCornerShape(50.dp)
                                )
                                .border(
                                    if (AccentColorState.value == color) {
                                        3.dp
                                    } else {
                                        1.dp
                                    },
                                    if (AccentColorState.value == color) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.25f)
                                    },
                                    RoundedCornerShape(50.dp)
                                )
                                .clickable {
                                    AccentColorState.value = color
                                    SettingsStore.setAccentColor(
                                        context,
                                        color
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            if (AccentColorState.value == color) {

                                Text(
                                    "✓",
                                    color = Color.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(5.dp))

                        Text(
                            themeNames[index],
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Divider(
                color = Color.White.copy(alpha = 0.10f)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "保存设置",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitch(
                title = "默认保存到相册",
                subtitle = "下载完成后保存到系统媒体库",
                checked = saveToGallery,
                onCheckedChange = {
                    saveToGallery = it
                    SettingsStore.setSaveToGallery(context, it)
                }
            )

            Divider(color = Color(0xFF39443C))

            SettingSwitch(
                title = "自动识别剪贴板",
                subtitle = "检测到分享链接时提示识别",
                checked = autoClipboard,
                onCheckedChange = {
                    autoClipboard = it
                    SettingsStore.setAutoClipboard(context, it)
                }
            )

            Divider(color = Color(0xFF39443C))

            SettingSwitch(
                title = "仅 Wi-Fi 下载",
                subtitle = "避免使用移动网络消耗流量",
                checked = wifiOnly,
                onCheckedChange = {
                    wifiOnly = it
                    SettingsStore.setWifiOnly(context, it)
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        RoundedPanel {

            Text(
                "关于",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "VideoX Android",
                color = TextMain
            )

            Text(
                "版本 0.1.0 · 抖音解析测试版",
                color = TextMuted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// 设置开关
@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(Modifier.weight(1f)) {

            Text(
                title,
                color = TextMain,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                subtitle,
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentColorState.value,
                checkedTrackColor = Color(0xFF385B46)
            )
        )
    }
}
