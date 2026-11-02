
package com.videox.app

import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.content.ClipboardManager
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
    var currentPage by remember { mutableStateOf(0) }

    MaterialTheme {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                BottomNavigationBar(
                    currentPage = currentPage,
                    onPageChange = { currentPage = it },
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

    var url by remember { mutableStateOf(sharedText) }

    var platform by remember {
        mutableStateOf("等待识别")
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

    var videoTitle by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }

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
                    platform = "等待识别"
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

                            platform = detectPlatform(url)
                            recognized = false
                            message = "没有识别到有效的抖音分享链接"
                        }

                        else -> {

                            loading = true
                            recognized = false
                            platform = "抖音"
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
                                        )

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
                        downloadVideo(
                            context = context,
                            videoUrl = videoUrl,
                            title = videoTitle
                        )
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

        if (
            !videoUrl.startsWith("http://") &&
            !videoUrl.startsWith("https://")
        ) {

            Toast.makeText(
                context,
                "视频地址无效",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val request = DownloadManager.Request(
            Uri.parse(videoUrl)
        )
            .setTitle(
                title.ifBlank {
                    "VideoX 抖音视频"
                }
            )
            .setDescription("VideoX 正在下载")
            .setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "VideoX_${System.currentTimeMillis()}.mp4"
            )

        val manager = context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

        manager.enqueue(request)

        Toast.makeText(
            context,
            "已加入下载任务，请查看系统下载通知",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {

        Toast.makeText(
            context,
            "下载失败：${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// 下载页面
@Composable
fun DownloadPage() {

    var selectedTab by remember {
        mutableStateOf(0)
    }

    Column(Modifier.fillMaxSize()) {

        Text(
            "下载",
            color = TextMain,
            fontSize = 28.sp
        )

        Text(
            "管理你的下载内容",
            color = TextMuted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        RoundedPanel {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SurfaceInner,
                        RoundedCornerShape(50.dp)
                    )
                    .padding(4.dp)
            ) {

                TabPill(
                    text = "视频",
                    selected = selectedTab == 0,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedTab = 0
                }

                TabPill(
                    text = "图集",
                    selected = selectedTab == 1,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedTab = 1
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        SurfaceInner,
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        if (selectedTab == 0) {
                            "暂无视频任务"
                        } else {
                            "暂无图集任务"
                        },
                        color = TextMain,
                        fontSize = 18.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "识别并添加下载后，内容会显示在这里",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
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

    Column(Modifier.fillMaxSize()) {

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

        Spacer(Modifier.weight(1f))
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