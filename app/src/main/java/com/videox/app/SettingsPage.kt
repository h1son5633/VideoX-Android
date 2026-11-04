package com.videox.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast

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

import coil.imageLoader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File

// 设置页面（所有选项都会持久化保存，并被实际功能读取）
@Composable
fun SettingsPage() {

    val context = LocalContext.current

    var videoDest by remember {
        mutableStateOf(SettingsStore.destCode(context, "video"))
    }

    var imageDest by remember {
        mutableStateOf(SettingsStore.destCode(context, "image"))
    }

    var musicDest by remember {
        mutableStateOf(SettingsStore.destCode(context, "music"))
    }

    var autoClipboard by remember {
        mutableStateOf(SettingsStore.autoClipboard(context))
    }

    var wifiOnly by remember {
        mutableStateOf(SettingsStore.wifiOnly(context))
    }

    val scope = rememberCoroutineScope()

    var cacheSizeText by remember {
        mutableStateOf("计算中…")
    }

    var downloadsSizeText by remember {
        mutableStateOf("计算中…")
    }

    // 统计图片缓存和已下载文件的占用（IO 线程执行）
    suspend fun computeSizes(): Pair<Long, Long> {

        return withContext(Dispatchers.IO) {

            val loader = context.imageLoader

            val cache = (loader.memoryCache?.size?.toLong() ?: 0L) +
                    (loader.diskCache?.size ?: 0L)

            var downloads = 0L

            listOf(
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MUSIC
            ).forEach { type ->

                downloads += folderSize(
                    File(
                        Environment.getExternalStoragePublicDirectory(type),
                        "VideoX"
                    )
                )
            }

            val downloadsRoot = Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )

            downloadsRoot.listFiles()?.forEach { file ->

                if (file.isFile && file.name.startsWith("VideoX_")) {
                    downloads += file.length()
                }
            }

            cache to downloads
        }
    }

    LaunchedEffect(Unit) {

        val (cache, downloads) = computeSizes()

        cacheSizeText = formatFileSize(cache)
        downloadsSizeText = formatFileSize(downloads)
    }

    val versionName = remember {

        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        } catch (e: Exception) {
            null
        }
    } ?: "未知"

    fun copyToClipboard(text: String) {

        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("VideoX 路径", text)
        )

        Toast.makeText(
            context,
            "路径已复制",
            Toast.LENGTH_SHORT
        ).show()
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
                "底栏外观",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                TabPill(
                    text = "有底板",
                    selected = NavStyleState.value ==
                            SettingsStore.NAV_STYLE_BOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    NavStyleState.value =
                        SettingsStore.NAV_STYLE_BOARD
                    SettingsStore.setNavStyle(
                        context,
                        SettingsStore.NAV_STYLE_BOARD
                    )
                }

                TabPill(
                    text = "无底板悬浮",
                    selected = NavStyleState.value ==
                            SettingsStore.NAV_STYLE_FLOATING,
                    modifier = Modifier.weight(1f)
                ) {
                    NavStyleState.value =
                        SettingsStore.NAV_STYLE_FLOATING
                    SettingsStore.setNavStyle(
                        context,
                        SettingsStore.NAV_STYLE_FLOATING
                    )
                }

                TabPill(
                    text = "跟随系统",
                    selected = NavStyleState.value ==
                            SettingsStore.NAV_STYLE_SYSTEM,
                    modifier = Modifier.weight(1f)
                ) {
                    NavStyleState.value =
                        SettingsStore.NAV_STYLE_SYSTEM
                    SettingsStore.setNavStyle(
                        context,
                        SettingsStore.NAV_STYLE_SYSTEM
                    )
                }
            }

            if (NavStyleState.value ==
                SettingsStore.NAV_STYLE_BOARD
            ) {

                Spacer(Modifier.height(14.dp))

                Text(
                    "底板颜色",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    listOf(
                        Color(0xFF202820),
                        Color(0xFF000000),
                        Color(0xFF2B2B2B),
                        Color(0xFF14532D),
                        Color(0xFF1E3A5F),
                        Color(0xFFF5F5F5)
                    ).forEach { color ->

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color,
                                    RoundedCornerShape(50.dp)
                                )
                                .border(
                                    if (NavBoardColorState.value == color) {
                                        3.dp
                                    } else {
                                        1.dp
                                    },
                                    if (NavBoardColorState.value == color) {
                                        AccentColorState.value
                                    } else {
                                        Color.White.copy(alpha = 0.25f)
                                    },
                                    RoundedCornerShape(50.dp)
                                )
                                .clickable {
                                    NavBoardColorState.value = color
                                    SettingsStore.setNavBoardColor(
                                        context,
                                        color
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            if (NavBoardColorState.value == color) {

                                Text(
                                    "✓",
                                    color = AccentColorState.value,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "“跟随系统”时底板随系统深/浅模式变色；界面整体外观也会跟随系统。",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        RoundedPanel {

            Text(
                "文件夹",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "下载位置（点右侧按钮更改，立即生效）",
                color = TextMain,
                fontSize = 14.sp
            )

            DestChooserRow(
                label = "视频",
                code = videoDest,
                options = listOf(
                    SettingsStore.DEST_MEDIA,
                    SettingsStore.DEST_DOWNLOADS,
                    SettingsStore.DEST_DOWNLOADS_VIDEOX,
                    SettingsStore.DEST_DCIM
                )
            ) { selected ->
                videoDest = selected
                SettingsStore.setDestCode(
                    context,
                    "video",
                    selected
                )
            }

            DestChooserRow(
                label = "图片",
                code = imageDest,
                options = listOf(
                    SettingsStore.DEST_MEDIA,
                    SettingsStore.DEST_DOWNLOADS,
                    SettingsStore.DEST_DOWNLOADS_VIDEOX,
                    SettingsStore.DEST_DCIM
                )
            ) { selected ->
                imageDest = selected
                SettingsStore.setDestCode(
                    context,
                    "image",
                    selected
                )
            }

            DestChooserRow(
                label = "音乐",
                code = musicDest,
                options = listOf(
                    SettingsStore.DEST_MEDIA,
                    SettingsStore.DEST_DOWNLOADS,
                    SettingsStore.DEST_DOWNLOADS_VIDEOX
                )
            ) { selected ->
                musicDest = selected
                SettingsStore.setDestCode(
                    context,
                    "music",
                    selected
                )
            }

            Divider(color = Color(0xFF39443C))

            val dataDirPath = SettingsStore.dataDirPath(context)

            val videoDirPath =
                SettingsStore.destDisplayPath(context, "video")

            val imageDirPath =
                SettingsStore.destDisplayPath(context, "image")

            val musicDirPath =
                SettingsStore.destDisplayPath(context, "music")

            FolderRow(
                label = "App 数据文件夹",
                path = dataDirPath,
                onCopy = {
                    copyToClipboard(dataDirPath)
                }
            )

            Divider(color = Color(0xFF39443C))

            FolderRow(
                label = "视频保存文件夹",
                path = videoDirPath,
                onCopy = {
                    copyToClipboard(videoDirPath)
                }
            )

            Divider(color = Color(0xFF39443C))

            FolderRow(
                label = "图片保存文件夹",
                path = imageDirPath,
                onCopy = {
                    copyToClipboard(imageDirPath)
                }
            )

            Divider(color = Color(0xFF39443C))

            FolderRow(
                label = "音乐保存文件夹",
                path = musicDirPath,
                onCopy = {
                    copyToClipboard(musicDirPath)
                }
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "上面选的位置就是实际下载位置；原来的“保存到相册”开关已升级为这里的逐类选择，旧设置会自动沿用。",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        RoundedPanel {

            Text(
                "存储空间",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(Modifier.weight(1f)) {

                    Text(
                        "图片缓存",
                        color = TextMain,
                        fontSize = 14.sp
                    )

                    Text(
                        cacheSizeText,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {

                        scope.launch {

                            withContext(Dispatchers.IO) {

                                context.imageLoader
                                    .memoryCache?.clear()

                                context.imageLoader
                                    .diskCache?.clear()
                            }

                            val (cache, downloads) = computeSizes()

                            cacheSizeText = formatFileSize(cache)
                            downloadsSizeText = formatFileSize(downloads)

                            Toast.makeText(
                                context,
                                "图片缓存已清除",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("清除")
                }
            }

            Divider(color = Color(0xFF39443C))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(Modifier.weight(1f)) {

                    Text(
                        "下载文件占用",
                        color = TextMain,
                        fontSize = 14.sp
                    )

                    Text(
                        downloadsSizeText,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
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
                "版本 $versionName",
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

// 下载位置编码的显示名
fun destCodeLabel(code: Int): String {

    return when (code) {

        SettingsStore.DEST_DOWNLOADS ->
            "下载目录"

        SettingsStore.DEST_DOWNLOADS_VIDEOX ->
            "下载目录 / VideoX"

        SettingsStore.DEST_DCIM ->
            "DCIM / VideoX"

        else ->
            "媒体库 / VideoX"
    }
}

// 下载位置选择行（名称 + 当前选项按钮 + 下拉菜单）
@Composable
fun DestChooserRow(
    label: String,
    code: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            label,
            color = TextMain,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        Box {

            OutlinedButton(
                onClick = {
                    expanded = true
                }
            ) {
                Text(destCodeLabel(code) + " ▾")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {

                options.forEach { option ->

                    DropdownMenuItem(
                        text = {
                            Text(destCodeLabel(option))
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

// 文件夹路径行（名称 + 路径 + 复制按钮）
@Composable
fun FolderRow(
    label: String,
    path: String,
    onCopy: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(Modifier.weight(1f)) {

            Text(
                label,
                color = TextMain,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(2.dp))

            Text(
                path,
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        TextButton(onClick = onCopy) {
            Text("复制")
        }
    }
}

// 递归统计一个文件夹的总大小（不存在时返回 0）
fun folderSize(file: File): Long {

    if (!file.exists()) {
        return 0L
    }

    if (file.isFile) {
        return file.length()
    }

    return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
}
