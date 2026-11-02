
package com.videox.app

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.net.URL
import java.net.URLEncoder
import java.net.HttpURLConnection

private val Bg = Color(0xFF101411)
private val SurfaceGlass = Color(0xFF1C241F)
private val SurfaceInner = Color(0xFF252E28)

private val AccentColorState =
    mutableStateOf(Color(0xFF62E6A2))

private val TextMain = Color(0xFFF1F5F2)
private val TextMuted = Color(0xFF9CAAA1)

// 下载记录数据
data class DownloadRecord(
    val id: Long,
    val title: String,
    val status: String,
    val progress: Int,
    val sizeText: String,
    val reason: String
)

class MainActivity : ComponentActivity() {

    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedText = getSharedText(intent)

        setContent {
            VideoXApp(sharedText)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = getSharedText(intent)
    }

    private fun getSharedText(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) {
            return ""
        }

        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            .orEmpty()
    }
}

@Composable
fun VideoXApp(sharedText: String) {

    var currentPage by remember {
        mutableStateOf(0)
    }

    MaterialTheme {

        Scaffold(
            containerColor = Bg,
            bottomBar = {
                BottomNavigationBar(
                    currentPage = currentPage,
                    onPageChange = {
                        currentPage = it
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                )
            }
        ) { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .padding(innerPadding)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {

                    Spacer(Modifier.height(16.dp))

                    when (currentPage) {
                        0 -> ParsePage(sharedText)
                        1 -> DownloadPage()
                        2 -> SettingsPage()
                    }
                }
            }
        }
    }
}

@Composable
fun ParsePage(sharedText: String) {

    var url by remember {
        mutableStateOf(sharedText)
    }

    var message by remember {
        mutableStateOf("粘贴分享链接后开始识别")
    }

    var recognized by remember {
        mutableStateOf(false)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var videoTitle by remember {
        mutableStateOf("")
    }

    var author by remember {
        mutableStateOf("")
    }

    var coverUrl by remember {
        mutableStateOf("")
    }

    var videoUrl by remember {
        mutableStateOf("")
    }

    var localFileName by remember {
        mutableStateOf("")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // TXT 文件选择器
    val txtPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->

        if (uri != null) {

            scope.launch {

                try {

                    val content = withContext(Dispatchers.IO) {

                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: throw Exception("无法读取文件")
                    }

                    url = content
                    recognized = false
                    videoUrl = ""
                    message = "TXT 文件读取成功，点击解析即可"

                } catch (e: Exception) {

                    message = "TXT 读取失败：${e.message}"
                }
            }
        }
    }

    // 本地视频选择器
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->

        if (uri != null) {

            try {

                localFileName = getDisplayName(context, uri)

                message = "已选择本地视频：$localFileName"

                Toast.makeText(
                    context,
                    "本地视频已选择",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                message = "读取视频信息失败：${e.message}"
            }
        }
    }

    // 接收其他应用分享的内容
    LaunchedEffect(sharedText) {

        if (sharedText.isNotBlank()) {

            url = sharedText
            recognized = false
            videoUrl = ""
            message = "已接收分享内容，请点击解析"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            "VideoX",
            color = AccentColorState.value,
            fontSize = 32.sp
        )

        Text(
            "全平台媒体下载助手",
            color = TextMuted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(24.dp))

        RoundedPanel {

            Text(
                "链接",
                color = TextMain,
                fontSize = 20.sp
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    message = "等待解析"
                    recognized = false
                    videoTitle = ""
                    author = ""
                    coverUrl = ""
                    videoUrl = ""
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("粘贴抖音分享文案或视频链接")
                },
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    focusedBorderColor = AccentColorState.value,
                    unfocusedBorderColor = Color(0xFF46534A),
                    focusedPlaceholderColor = TextMuted,
                    unfocusedPlaceholderColor = TextMuted,
                    cursorColor = AccentColorState.value
                )
            )

            Spacer(Modifier.height(12.dp))

            // 粘贴和 TXT 按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {

                        val clipboard =
                            context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager

                        url = clipboard.primaryClip
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()

                        recognized = false
                        videoUrl = ""

                        message = if (url.isBlank()) {
                            "剪贴板没有文字"
                        } else {
                            "已读取剪贴板"
                        }
                    }
                ) {
                    Text("粘贴")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        txtPicker.launch(
                            arrayOf("text/plain", "text/*")
                        )
                    }
                ) {
                    Text("读取 TXT")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 本地视频选择
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    videoPicker.launch(
                        arrayOf("video/*")
                    )
                }
            ) {
                Text("选择本地视频")
            }

            if (localFileName.isNotBlank()) {

                Spacer(Modifier.height(6.dp))

                Text(
                    "本地文件：$localFileName",
                    color = AccentColorState.value,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // 解析按钮
            GreenButton(
                text = if (loading) {
                    "正在解析..."
                } else {
                    "解析并获取视频"
                },
                onClick = {

                    if (loading) {
                        return@GreenButton
                    }

                    val douyinUrl = extractDouyinUrl(url)

                    when {

                        url.isBlank() -> {
                            message = "请先粘贴链接或读取 TXT 文件"
                        }

                        douyinUrl == null -> {
                            recognized = false
                            message = "没有识别到有效的抖音分享链接"
                        }

                        else -> {

                            loading = true
                            recognized = false
                            videoUrl = ""
                            message = "正在请求解析接口..."

                            scope.launch {

                                try {

                                    val result = parseDouyin(douyinUrl)

                                    val code = result.optInt("code")
                                    val data = result.optJSONObject("data")

                                    if (code == 200 && data != null) {

                                        videoTitle = data.optString(
                                            "title",
                                            "抖音视频"
                                        ).ifBlank {
                                            "抖音视频"
                                        }

                                        author = data.optString(
                                            "author",
                                            "未知作者"
                                        )

                                        coverUrl = data.optString(
                                            "cover",
                                            ""
                                        )

                                        videoUrl = data.optString(
                                            "url",
                                            ""
                                        )

                                        recognized =
                                            videoUrl.startsWith("http://") ||
                                            videoUrl.startsWith("https://")

                                        message = if (recognized) {
                                            "解析成功，可以下载视频"
                                        } else {
                                            "解析成功，但没有获取到有效的视频地址"
                                        }

                                    } else {

                                        message =
                                            "接口返回错误：${result.optString("msg", "未知错误")}（code=$code）"
                                    }

                                } catch (e: Exception) {

                                    message =
                                        "解析失败：${e.message ?: "未知错误"}"

                                } finally {

                                    loading = false
                                }
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            Text(
                message,
                color = if (recognized) {
                    AccentColorState.value
                } else {
                    TextMuted
                },
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        // 识别结果
        RoundedPanel {

            Text(
                "识别结果",
                color = TextMain,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            if (recognized) {

                Text(
                    videoTitle,
                    color = TextMain,
                    fontSize = 17.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "作者：$author",
                    color = TextMuted,
                    fontSize = 13.sp
                )

                if (coverUrl.isNotBlank()) {

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "已获取封面地址",
                        color = AccentColorState.value,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                GreenButton(
                    text = "下载视频",
                    onClick = {

                        if (videoUrl.isBlank()) {

                            Toast.makeText(
                                context,
                                "视频地址为空，请重新解析",
                                Toast.LENGTH_LONG
                            ).show()

                        } else {

                            downloadVideo(
                                context = context,
                                videoUrl = videoUrl,
                                title = videoTitle
                            )
                        }
                    }
                )

            } else {

                Text(
                    "解析后将在这里显示视频标题和下载入口",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "VideoX · Android",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            )
        )

        Spacer(Modifier.height(16.dp))
    }
}

// 提取抖音分享链接
fun extractDouyinUrl(input: String): String? {

    val regex = Regex(
        """https?://(?:v\.douyin\.com|www\.douyin\.com|douyin\.com)/[^\s，。]+""",
        RegexOption.IGNORE_CASE
    )

    return regex.find(input)
        ?.value
        ?.trimEnd('.', ',', '，', '。', ')', '）')
}

// 请求抖音解析接口
suspend fun parseDouyin(inputUrl: String): JSONObject {

    return withContext(Dispatchers.IO) {

        val encodedUrl = URLEncoder.encode(
            inputUrl.trim(),
            "UTF-8"
        )

        val apiUrl =
            "http://api.xhus.cn/api/douyin?url=$encodedUrl"

        val connection = URL(apiUrl)
            .openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "GET"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000

            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val status = connection.responseCode

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw Exception(
                    "服务器没有返回内容，HTTP $status"
                )

            if (status !in 200..299) {

                throw Exception(
                    "接口 HTTP $status：${response.take(300)}"
                )
            }

            val cleanResponse = response
                .removePrefix("\uFEFF")
                .trim()

            if (!cleanResponse.startsWith("{")) {

                throw Exception(
                    "接口返回的不是 JSON：${cleanResponse.take(200)}"
                )
            }

            JSONObject(cleanResponse)

        } finally {

            connection.disconnect()
        }
    }
}

// 获取本地文件名称
fun getDisplayName(
    context: Context,
    uri: Uri
): String {

    var name = "未命名文件"

    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->

        if (cursor.moveToFirst()) {

            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0) {
                name = cursor.getString(index) ?: name
            }
        }
    }

    return name
}

// 下载视频
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

        val request = DownloadManager.Request(uri)
            .setTitle("VideoX - ${title.ifBlank { "抖音视频" }}")
            .setDescription("VideoX 正在下载视频")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "VideoX_${System.currentTimeMillis()}.mp4"
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

        GreenButton(
            text = if (loading) {
                "正在刷新..."
            } else {
                "刷新下载记录"
            },
            onClick = {
                if (!loading) {
                    refreshDownloads()
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            pageMessage,
            color = TextMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {

            RoundedPanel {

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

        } else {

            records.forEach { record ->

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

// 设置页面
@Composable
fun SettingsPage() {

    var saveToGallery by remember {
        mutableStateOf(true)
    }

    var autoClipboard by remember {
        mutableStateOf(false)
    }

    var wifiOnly by remember {
        mutableStateOf(false)
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
                }
            )

            Divider(color = Color(0xFF39443C))

            SettingSwitch(
                title = "自动识别剪贴板",
                subtitle = "检测到分享链接时提示识别",
                checked = autoClipboard,
                onCheckedChange = {
                    autoClipboard = it
                }
            )

            Divider(color = Color(0xFF39443C))

            SettingSwitch(
                title = "仅 Wi-Fi 下载",
                subtitle = "避免使用移动网络消耗流量",
                checked = wifiOnly,
                onCheckedChange = {
                    wifiOnly = it
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

// 圆角面板
@Composable
fun RoundedPanel(
    content: @Composable ColumnScope.() -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceGlass.copy(alpha = 0.72f)
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

// 绿色按钮
@Composable
fun GreenButton(
    text: String,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentColorState.value
        )
    ) {

        Text(
            text,
            color = Color(0xFF102017),
            fontSize = 16.sp
        )
    }
}

// 下载页面切换按钮
@Composable
fun TabPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Box(
        modifier = modifier
            .background(
                if (selected) {
                    AccentColorState.value
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(50.dp)
            )
            .clickable {
                onClick()
            }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text,
            color = if (selected) {
                Color.Black
            } else {
                TextMuted
            }
        )
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

// 底部导航
@Composable
fun BottomNavigationBar(
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF202820).copy(alpha = 0.72f)
        )
    ) {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(52.dp)
        ) {

            val itemWidth = maxWidth / 3

            val animatedOffset by animateDpAsState(
                targetValue = itemWidth * currentPage,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = FastOutSlowInEasing
                ),
                label = "nav_slide"
            )

            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .background(
                        AccentColorState.value.copy(alpha = 0.30f),
                        RoundedCornerShape(22.dp)
                    )
                    .border(
                        1.dp,
                        AccentColorState.value.copy(alpha = 0.45f),
                        RoundedCornerShape(22.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                NavigationItem(
                    text = "解析",
                    selected = currentPage == 0,
                    modifier = Modifier.weight(1f)
                ) {
                    onPageChange(0)
                }

                NavigationItem(
                    text = "下载",
                    selected = currentPage == 1,
                    modifier = Modifier.weight(1f)
                ) {
                    onPageChange(1)
                }

                NavigationItem(
                    text = "设置",
                    selected = currentPage == 2,
                    modifier = Modifier.weight(1f)
                ) {
                    onPageChange(2)
                }
            }
        }
    }
}

// 导航项目
@Composable
fun NavigationItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {

        Text(
            text,
            color = if (selected) {
                AccentColorState.value
            } else {
                TextMuted
            },
            fontSize = 15.sp
        )
    }
}

// 平台识别
fun detectPlatform(input: String): String {

    val text = input.lowercase()

    return when {

        "douyin.com" in text ||
                "iesdouyin.com" in text -> "抖音"

        "kuaishou.com" in text ||
                "gifshow.com" in text -> "快手"

        "xiaohongshu.com" in text ||
                "xhslink.com" in text -> "小红书"

        "bilibili.com" in text ||
                "b23.tv" in text -> "哔哩哔哩"

        "weibo.com" in text ||
                "weibo.cn" in text -> "微博"

        "ixigua.com" in text -> "西瓜视频"

        "pipix.com" in text -> "皮皮虾"

        "toutiao.com" in text -> "今日头条"

        else -> "未知平台"
    }
}