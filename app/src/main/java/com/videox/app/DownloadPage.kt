package com.videox.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 保存成功时轻轻震动一下（失败不震）
fun buzz(context: Context) {

    try {

        val vibrator = context.getSystemService(
            Context.VIBRATOR_SERVICE
        ) as? Vibrator ?: return

        if (Build.VERSION.SDK_INT >= 26) {

            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    30,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )

        } else {

            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }

    } catch (e: Exception) {
        // 震动失败不影响下载本身，忽略
    }
}

// 下载记录数据
data class DownloadRecord(
    val id: Long,
    val title: String,
    val status: String,
    val progress: Int,
    val sizeText: String,
    val reason: String
)

// 下载视频（会读取设置：仅 Wi-Fi 下载、默认保存到相册）
fun downloadVideo(
    context: Context,
    videoUrl: String,
    title: String
) {

    try {

        val uri = Uri.parse(videoUrl)

        if (
            (uri.scheme != "http" && uri.scheme != "https") ||
            uri.host.isNullOrBlank()
        ) {

            Toast.makeText(
                context,
                "视频地址无效，请重新解析",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val manager = context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

        val fileName = "VideoX_${System.currentTimeMillis()}.mp4"

        val request = DownloadManager.Request(uri)
            .setTitle("VideoX - ${title.ifBlank { "抖音视频" }}")
            .setDescription("VideoX 正在下载视频")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // 仅 Wi-Fi 下载（设置中开启时限制网络类型）
        if (SettingsStore.wifiOnly(context)) {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI
            )

        } else {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                        DownloadManager.Request.NETWORK_MOBILE
            )
        }

        // 保存位置：读取设置里为视频选的位置
        val (videoDirType, videoSubPrefix) =
            SettingsStore.downloadDest(context, "video")

        request.setDestinationInExternalPublicDir(
            videoDirType,
            videoSubPrefix + fileName
        )

        request.addRequestHeader(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        )

        request.addRequestHeader(
            "Referer",
            "https://www.douyin.com/"
        )

        val downloadId = manager.enqueue(request)

        buzz(context)

        Toast.makeText(
            context,
            "下载任务已提交，任务编号：$downloadId",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {

        Toast.makeText(
            context,
            "下载提交失败：${e.message ?: "未知错误"}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// 提交一个图片下载任务（标题统一带 VideoX - 前缀，下载记录页靠它识别）
// 会读取设置：仅 Wi-Fi 下载、默认保存到相册；成功返回 true
fun enqueueImageDownload(
    context: Context,
    manager: DownloadManager,
    imageUrl: String,
    displayTitle: String,
    fileName: String
): Boolean {

    return try {

        val uri = Uri.parse(imageUrl)

        if (
            (uri.scheme != "http" && uri.scheme != "https") ||
            uri.host.isNullOrBlank()
        ) {
            return false
        }

        val request = DownloadManager.Request(uri)
            .setTitle(displayTitle)
            .setDescription("VideoX 正在下载图片")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // 仅 Wi-Fi 下载（设置中开启时限制网络类型）
        if (SettingsStore.wifiOnly(context)) {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI
            )

        } else {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                        DownloadManager.Request.NETWORK_MOBILE
            )
        }

        // 保存位置：读取设置里为图片选的位置
        val (imageDirType, imageSubPrefix) =
            SettingsStore.downloadDest(context, "image")

        request.setDestinationInExternalPublicDir(
            imageDirType,
            imageSubPrefix + fileName
        )

        request.addRequestHeader(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        )

        request.addRequestHeader(
            "Referer",
            "https://www.douyin.com/"
        )

        manager.enqueue(request)

        true

    } catch (e: Exception) {

        false
    }
}

// 下载图集图片（onlyIndices 为空时下载全部，否则只下载勾选的图片）
fun downloadImages(
    context: Context,
    imageUrls: List<String>,
    title: String,
    onlyIndices: Set<Int>? = null
) {

    if (imageUrls.isEmpty()) {

        Toast.makeText(
            context,
            "没有可下载的图片",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    if (onlyIndices != null && onlyIndices.isEmpty()) {

        Toast.makeText(
            context,
            "先勾选要下载的图片",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    val manager = context.getSystemService(
        Context.DOWNLOAD_SERVICE
    ) as DownloadManager

    var submitted = 0

    imageUrls.forEachIndexed { index, imageUrl ->

        if (onlyIndices != null && index !in onlyIndices) {
            return@forEachIndexed
        }

        val ext = imageFileExtension(imageUrl)

        val fileName =
            "VideoX_${System.currentTimeMillis()}_${index + 1}.$ext"

        val ok = enqueueImageDownload(
            context = context,
            manager = manager,
            imageUrl = imageUrl,
            displayTitle = "VideoX - ${title.ifBlank { "抖音图集" }}（图 ${index + 1}/${imageUrls.size}）",
            fileName = fileName
        )

        if (ok) {
            submitted++
        }
    }

    if (submitted > 0) {
        buzz(context)
    }

    Toast.makeText(
        context,
        if (submitted > 0) {
            "已提交 $submitted 张图片下载"
        } else {
            "图片下载提交失败"
        },
        Toast.LENGTH_LONG
    ).show()
}

// 下载视频封面为图片
fun downloadCover(
    context: Context,
    coverUrl: String,
    title: String
) {

    if (coverUrl.isBlank()) {

        Toast.makeText(
            context,
            "没有可保存的封面",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    val manager = context.getSystemService(
        Context.DOWNLOAD_SERVICE
    ) as DownloadManager

    val ext = imageFileExtension(coverUrl)

    val fileName =
        "VideoX_${System.currentTimeMillis()}_cover.$ext"

    val ok = enqueueImageDownload(
        context = context,
        manager = manager,
        imageUrl = coverUrl,
        displayTitle = "VideoX - ${title.ifBlank { "抖音视频" }}（封面）",
        fileName = fileName
    )

    if (ok) {
        buzz(context)
    }

    Toast.makeText(
        context,
        if (ok) {
            "封面下载已提交"
        } else {
            "封面下载提交失败"
        },
        Toast.LENGTH_LONG
    ).show()
}

// 下载背景音乐（会读取设置：仅 Wi-Fi 下载、默认保存到相册）
fun downloadMusic(
    context: Context,
    musicUrl: String,
    musicTitle: String,
    workTitle: String
) {

    try {

        val uri = Uri.parse(musicUrl)

        if (
            (uri.scheme != "http" && uri.scheme != "https") ||
            uri.host.isNullOrBlank()
        ) {

            Toast.makeText(
                context,
                "音乐地址无效",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val manager = context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

        val fileName = "VideoX_${System.currentTimeMillis()}.mp3"

        val request = DownloadManager.Request(uri)
            .setTitle("VideoX - ${workTitle.ifBlank { "抖音作品" }}（音乐：${musicTitle.ifBlank { "背景音乐" }}）")
            .setDescription("VideoX 正在下载背景音乐")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // 仅 Wi-Fi 下载（设置中开启时限制网络类型）
        if (SettingsStore.wifiOnly(context)) {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI
            )

        } else {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                        DownloadManager.Request.NETWORK_MOBILE
            )
        }

        // 保存位置：读取设置里为音乐选的位置
        val (musicDirType, musicSubPrefix) =
            SettingsStore.downloadDest(context, "music")

        request.setDestinationInExternalPublicDir(
            musicDirType,
            musicSubPrefix + fileName
        )

        request.addRequestHeader(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        )

        request.addRequestHeader(
            "Referer",
            "https://www.douyin.com/"
        )

        manager.enqueue(request)

        buzz(context)

        Toast.makeText(
            context,
            "音乐下载已提交",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {

        Toast.makeText(
            context,
            "音乐下载提交失败：${e.message ?: "未知错误"}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// 批量下载动图（实况照片）的视频部分：每张动图是一小段 MP4，
// 存到与视频相同的位置，下载记录标题带“动图”字样；items 是 (序号, 视频直链)
fun downloadLiveVideos(
    context: Context,
    items: List<Pair<Int, String>>,
    title: String,
    total: Int
) {

    if (items.isEmpty()) {
        return
    }

    val manager = context.getSystemService(
        Context.DOWNLOAD_SERVICE
    ) as DownloadManager

    val (dirType, subPrefix) =
        SettingsStore.downloadDest(context, "video")

    var submitted = 0

    items.forEach { (index, videoUrl) ->

        try {

            val uri = Uri.parse(videoUrl)

            if (
                (uri.scheme != "http" && uri.scheme != "https") ||
                uri.host.isNullOrBlank()
            ) {
                return@forEach
            }

            val fileName =
                "VideoX_${System.currentTimeMillis()}_live_${index + 1}.mp4"

            val request = DownloadManager.Request(uri)
                .setTitle("VideoX - ${title.ifBlank { "抖音动图" }}（动图 ${index + 1}/$total）")
                .setDescription("VideoX 正在下载动图")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            if (SettingsStore.wifiOnly(context)) {

                request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI
                )

            } else {

                request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                            DownloadManager.Request.NETWORK_MOBILE
                )
            }

            request.setDestinationInExternalPublicDir(
                dirType,
                subPrefix + fileName
            )

            request.addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            )

            request.addRequestHeader(
                "Referer",
                "https://www.douyin.com/"
            )

            manager.enqueue(request)

            submitted++

        } catch (e: Exception) {
            // 单张失败不影响其余动图提交
        }
    }

    if (submitted > 0) {
        buzz(context)
    }

    Toast.makeText(
        context,
        if (submitted > 0) {
            "已提交 $submitted 张动图下载"
        } else {
            "动图下载提交失败"
        },
        Toast.LENGTH_LONG
    ).show()
}

// 根据图片直链判断文件扩展名（抖音图集直链多为 webp）
fun imageFileExtension(imageUrl: String): String {

    val lower = imageUrl.lowercase()

    return when {

        ".png" in lower -> "png"

        ".jpg" in lower || ".jpeg" in lower -> "jpg"

        else -> "webp"
    }
}

// 按下载记录标题判类型（标题由上面各下载函数生成，规则固定；
// DownloadRecord 里的标题已去掉 "VideoX - " 前缀）：
// 含“（音乐：”→音乐（downloadMusic）；含“（动图 ”→动图（downloadLiveVideos）；
// 含“（图 ”→图片（downloadImages）、含“（封面）”→图片（downloadCover）；
// 其余无后缀标记的普通标题→视频（downloadVideo，也是判不准时的默认类）
fun downloadCategory(title: String): String {

    return when {

        title.contains("（音乐：") -> "音乐"

        title.contains("（动图 ") -> "动图"

        title.contains("（图 ") ||
                title.contains("（封面）") -> "图片"

        else -> "视频"
    }
}

// 下载记录页面
@Composable
fun DownloadPage() {

    val context = LocalContext.current

    var records by remember {
        mutableStateOf(emptyList<DownloadRecord>())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var pageMessage by remember {
        mutableStateOf("点击刷新查看下载任务")
    }

    // 下载记录类型筛选（全部/视频/图片/动图/音乐），仅内存状态
    var typeFilter by remember {
        mutableStateOf("全部")
    }

    val shownRecords = if (typeFilter == "全部") {
        records
    } else {
        records.filter {
            downloadCategory(it.title) == typeFilter
        }
    }

    fun refreshDownloads() {

        loading = true

        try {

            val manager = context.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

            val cursor = manager.query(
                DownloadManager.Query()
            )

            val result = mutableListOf<DownloadRecord>()

            cursor?.use {

                val titleIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_TITLE
                )

                val idIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_ID
                )

                val statusIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_STATUS
                )

                val reasonIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_REASON
                )

                val downloadedIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                )

                val totalIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                )

                while (it.moveToNext()) {

                    val title = it.getString(titleIndex).orEmpty()

                    if (!title.startsWith("VideoX - ")) {
                        continue
                    }

                    val id = it.getLong(idIndex)
                    val statusCode = it.getInt(statusIndex)
                    val reasonCode = it.getInt(reasonIndex)

                    val downloaded = it.getLong(downloadedIndex)
                    val total = it.getLong(totalIndex)

                    val progress = if (total > 0) {
                        ((downloaded * 100) / total)
                            .toInt()
                            .coerceIn(0, 100)
                    } else {
                        0
                    }

                    val statusText = when (statusCode) {

                        DownloadManager.STATUS_PENDING ->
                            "等待下载"

                        DownloadManager.STATUS_RUNNING ->
                            "正在下载"

                        DownloadManager.STATUS_PAUSED ->
                            "下载暂停"

                        DownloadManager.STATUS_SUCCESSFUL ->
                            "下载完成"

                        DownloadManager.STATUS_FAILED ->
                            "下载失败"

                        else ->
                            "未知状态"
                    }

                    val reasonText = when (reasonCode) {

                        DownloadManager.ERROR_CANNOT_RESUME ->
                            "无法继续下载"

                        DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                            "存储设备不可用"

                        DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
                            "文件已存在"

                        DownloadManager.ERROR_FILE_ERROR ->
                            "文件写入错误"

                        DownloadManager.ERROR_HTTP_DATA_ERROR ->
                            "HTTP 数据错误"

                        DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                            "存储空间不足"

                        DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                            "重定向次数过多"

                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                            "服务器返回异常 HTTP 状态码"

                        DownloadManager.ERROR_UNKNOWN ->
                            "未知下载错误"

                        DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
                            "等待 Wi-Fi"

                        DownloadManager.PAUSED_WAITING_FOR_NETWORK ->
                            "等待网络"

                        DownloadManager.PAUSED_WAITING_TO_RETRY ->
                            "等待重试"

                        else ->
                            if (statusCode == DownloadManager.STATUS_FAILED) {
                                "错误代码：$reasonCode"
                            } else {
                                ""
                            }
                    }

                    val sizeText = if (total > 0) {
                        "${formatFileSize(downloaded)} / ${formatFileSize(total)}"
                    } else {
                        "${formatFileSize(downloaded)} / 未知大小"
                    }

                    result.add(
                        DownloadRecord(
                            id = id,
                            title = title.removePrefix("VideoX - "),
                            status = statusText,
                            progress = progress,
                            sizeText = sizeText,
                            reason = reasonText
                        )
                    )
                }
            }

            records = result

            pageMessage = if (result.isEmpty()) {
                "暂时没有 VideoX 下载记录"
            } else {
                "共找到 ${result.size} 个下载任务"
            }

        } catch (e: Exception) {

            pageMessage = "读取下载记录失败：${e.message}"

        } finally {

            loading = false
        }
    }

    // 清空已结束（成功/失败）的 VideoX 下载记录，进行中的保留
    fun clearFinishedRecords() {

        try {

            val manager = context.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

            val cursor = manager.query(
                DownloadManager.Query()
            )

            val ids = mutableListOf<Long>()

            cursor?.use {

                val titleIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_TITLE
                )

                val idIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_ID
                )

                val statusIndex = it.getColumnIndex(
                    DownloadManager.COLUMN_STATUS
                )

                while (it.moveToNext()) {

                    val title = it.getString(titleIndex).orEmpty()

                    if (!title.startsWith("VideoX - ")) {
                        continue
                    }

                    val statusCode = it.getInt(statusIndex)

                    if (statusCode == DownloadManager.STATUS_SUCCESSFUL ||
                        statusCode == DownloadManager.STATUS_FAILED
                    ) {
                        ids.add(it.getLong(idIndex))
                    }
                }
            }

            if (ids.isEmpty()) {

                Toast.makeText(
                    context,
                    "没有可清空的已结束记录",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                manager.remove(*ids.toLongArray())

                Toast.makeText(
                    context,
                    "已清空 ${ids.size} 条下载记录",
                    Toast.LENGTH_SHORT
                ).show()

                refreshDownloads()
            }

        } catch (e: Exception) {

            Toast.makeText(
                context,
                "清空失败：${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        refreshDownloads()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            "下载管理",
            color = AccentColorState.value,
            fontSize = 28.sp
        )

        Text(
            "查看 VideoX 下载任务和进度",
            color = TextMuted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {
                    if (!loading) {
                        refreshDownloads()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColorState.value
                )
            ) {

                Text(
                    if (loading) {
                        "正在刷新..."
                    } else {
                        "刷新下载记录"
                    },
                    color = Color(0xFF102017),
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    clearFinishedRecords()
                },
                modifier = Modifier.height(52.dp)
            ) {
                Text("清空记录")
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            pageMessage,
            color = TextMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        // 类型筛选 chip（只过滤显示，不影响记录本身）
        Row(
            modifier = Modifier.horizontalScroll(
                rememberScrollState()
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            listOf("全部", "视频", "图片", "动图", "音乐")
                .forEach { category ->

                    FilterChip(
                        selected = typeFilter == category,
                        onClick = {
                            typeFilter = category
                        },
                        label = {
                            Text(category, fontSize = 13.sp)
                        }
                    )
                }
        }

        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {

            RoundedPanel {

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "↓",
                        color = TextMuted,
                        fontSize = 36.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "暂无下载任务",
                        color = TextMain,
                        fontSize = 17.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "解析视频后点击下载，任务会显示在这里。",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }

        } else if (shownRecords.isEmpty()) {

            RoundedPanel {

                Text(
                    "该类型暂无下载记录",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }

        } else {

            shownRecords.forEach { record ->

                RoundedPanel {

                    Text(
                        record.title,
                        color = TextMain,
                        fontSize = 16.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "状态：${record.status}",
                        color = when (record.status) {
                            "下载完成" -> AccentColorState.value
                            "下载失败" -> Color(0xFFFF7777)
                            else -> TextMuted
                        },
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    if (
                        record.status == "正在下载" ||
                        record.status == "下载暂停"
                    ) {

                        LinearProgressIndicator(
                            progress = {
                                record.progress / 100f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentColorState.value,
                            trackColor = SurfaceInner
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "${record.progress}%",
                            color = AccentColorState.value,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        record.sizeText,
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    if (record.reason.isNotBlank()) {

                        Spacer(Modifier.height(6.dp))

                        Text(
                            record.reason,
                            color = if (record.status == "下载失败") {
                                Color(0xFFFF7777)
                            } else {
                                TextMuted
                            },
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "任务编号：${record.id}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// 文件大小格式化
fun formatFileSize(bytes: Long): String {

    if (bytes < 0) {
        return "未知"
    }

    if (bytes < 1024) {
        return "$bytes B"
    }

    val kb = bytes / 1024.0

    if (kb < 1024) {
        return String.format("%.1f KB", kb)
    }

    val mb = kb / 1024.0

    if (mb < 1024) {
        return String.format("%.1f MB", mb)
    }

    val gb = mb / 1024.0

    return String.format("%.2f GB", gb)
}
