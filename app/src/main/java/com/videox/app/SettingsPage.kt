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

    var saveToGallery by remember {
        mutableStateOf(SettingsStore.saveToGallery(context))
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
                "文件夹",
                color = AccentColorState.value,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            val dataDirPath = context.filesDir.parentFile?.absolutePath
                ?: context.filesDir.absolutePath

            val downloadsRootPath = Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                .absolutePath

            val videoDirPath = if (saveToGallery) {
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                ).absolutePath + "/VideoX"
            } else {
                downloadsRootPath
            }

            val imageDirPath = if (saveToGallery) {
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ).absolutePath + "/VideoX"
            } else {
                downloadsRootPath
            }

            val musicDirPath = if (saveToGallery) {
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC
                ).absolutePath + "/VideoX"
            } else {
                downloadsRootPath
            }

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
                "后三个保存位置跟着“默认保存到相册”开关走：开 → 相册里的 VideoX 文件夹；关 → 下载目录",
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
