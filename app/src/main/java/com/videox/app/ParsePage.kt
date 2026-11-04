package com.videox.app

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import android.widget.VideoView

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

import coil.compose.AsyncImage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.net.URL
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var contentType by remember {
        mutableStateOf("video")
    }

    var imageUrls by remember {
        mutableStateOf<List<String>>(emptyList())
    }

    var musicTitle by remember {
        mutableStateOf("")
    }

    var musicUrl by remember {
        mutableStateOf("")
    }

    var multiSelectMode by remember {
        mutableStateOf(false)
    }

    var selectedImages by remember {
        mutableStateOf<Set<Int>>(emptySet())
    }

    var viewerIndex by remember {
        mutableStateOf(-1)
    }

    var showPlayer by remember {
        mutableStateOf(false)
    }

    var videoViewRef by remember {
        mutableStateOf<VideoView?>(null)
    }

    var localFileName by remember {
        mutableStateOf("")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var historyEntries by remember {
        mutableStateOf<List<ParseHistoryEntry>>(emptyList())
    }

    // 从历史记录恢复一条解析结果（不重新请求接口）
    fun restoreFromHistory(entry: ParseHistoryEntry) {

        url = entry.sourceUrl
        videoTitle = entry.title
        author = entry.author
        coverUrl = entry.cover
        contentType = entry.type
        videoUrl = entry.videoUrl
        imageUrls = entry.images
        musicTitle = entry.musicTitle
        musicUrl = entry.musicUrl
        multiSelectMode = false
        selectedImages = emptySet()
        showPlayer = false
        recognized =
            entry.videoUrl.startsWith("http://") ||
            entry.videoUrl.startsWith("https://") ||
            entry.images.isNotEmpty()
        message = "已从历史记录恢复，点击即可下载"
    }

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

    // 进入页面时加载解析历史
    LaunchedEffect(Unit) {

        historyEntries = HistoryStore.load(context)
    }

    // 离开解析页时停掉预览播放，避免后台继续出声
    DisposableEffect(Unit) {

        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    // 自动识别剪贴板（设置中开启后，进入解析页时检测一次）
    LaunchedEffect(Unit) {

        if (sharedText.isBlank() &&
            SettingsStore.autoClipboard(context)
        ) {

            val clipboard =
                context.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as? ClipboardManager

            val clipText = clipboard?.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()

            if (clipText.isNotBlank() &&
                clipText != url &&
                extractDouyinUrl(clipText) != null
            ) {

                url = clipText
                recognized = false
                videoUrl = ""
                message = "已自动识别剪贴板中的抖音链接，点击解析即可"
            }
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
                    contentType = "video"
                    imageUrls = emptyList()
                    musicTitle = ""
                    musicUrl = ""
                    multiSelectMode = false
                    selectedImages = emptySet()
                    videoViewRef?.stopPlayback()
                    showPlayer = false
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
                            contentType = "video"
                            imageUrls = emptyList()
                            musicTitle = ""
                            musicUrl = ""
                            multiSelectMode = false
                            selectedImages = emptySet()
                            videoViewRef?.stopPlayback()
                            showPlayer = false
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

                                        val authorObject =
                                            data.optJSONObject("author")

                                        author = authorObject?.optString(
                                            "name",
                                            "未知作者"
                                        )?.ifBlank {
                                            "未知作者"
                                        } ?: data.optString(
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

                                        contentType = data.optString(
                                            "type",
                                            "video"
                                        )

                                        val imagesArray =
                                            data.optJSONArray("images")

                                        val parsedImages =
                                            mutableListOf<String>()

                                        if (imagesArray != null) {

                                            for (i in 0 until imagesArray.length()) {

                                                val img = imagesArray
                                                    .optString(i, "")

                                                if (img.startsWith("http://") ||
                                                    img.startsWith("https://")
                                                ) {
                                                    parsedImages.add(img)
                                                }
                                            }
                                        }

                                        imageUrls = parsedImages

                                        val musicObject =
                                            data.optJSONObject("music")

                                        musicTitle = musicObject?.optString(
                                            "title",
                                            ""
                                        ) ?: ""

                                        musicUrl = musicObject?.optString(
                                            "url",
                                            ""
                                        ) ?: ""

                                        val hasVideo =
                                            videoUrl.startsWith("http://") ||
                                            videoUrl.startsWith("https://")

                                        val hasImages =
                                            parsedImages.isNotEmpty()

                                        recognized = hasVideo || hasImages

                                        message = when {

                                            hasVideo ->
                                                "解析成功，可以下载视频"

                                            contentType == "image" && hasImages ->
                                                "解析成功，这是图文图集，共 ${parsedImages.size} 张图片"

                                            hasImages ->
                                                "解析成功，共 ${parsedImages.size} 张图片可下载"

                                            else ->
                                                "解析成功，但没有获取到可下载的内容"
                                        }

                                        if (recognized) {

                                            HistoryStore.add(
                                                context,
                                                ParseHistoryEntry(
                                                    sourceUrl = douyinUrl,
                                                    type = contentType,
                                                    title = videoTitle,
                                                    author = author,
                                                    cover = coverUrl,
                                                    videoUrl = videoUrl,
                                                    images = parsedImages,
                                                    musicTitle = musicTitle,
                                                    musicUrl = musicUrl,
                                                    time = System.currentTimeMillis()
                                                )
                                            )

                                            historyEntries =
                                                HistoryStore.load(context)
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

                if (musicUrl.startsWith("http://") ||
                    musicUrl.startsWith("https://")
                ) {

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            "背景音乐：${musicTitle.ifBlank { "未知音乐" }}",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {

                                downloadMusic(
                                    context = context,
                                    musicUrl = musicUrl,
                                    musicTitle = musicTitle,
                                    workTitle = videoTitle
                                )
                            }
                        ) {
                            Text("下载音乐")
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (videoUrl.isNotBlank()) {

                    // 视频预览：默认显示封面，点封面开始播放
                    if (showPlayer) {

                        AndroidView(
                            factory = { ctx ->

                                VideoView(ctx).apply {

                                    val uri = Uri.parse(videoUrl)

                                    if (Build.VERSION.SDK_INT >= 26) {

                                        setVideoURI(
                                            uri,
                                            mapOf(
                                                "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
                                                "Referer" to "https://www.douyin.com/"
                                            )
                                        )

                                    } else {

                                        setVideoURI(uri)
                                    }

                                    setOnPreparedListener { player ->
                                        player.start()
                                    }

                                    setOnClickListener { view ->

                                        val vv = view as VideoView

                                        if (vv.isPlaying) {
                                            vv.pause()
                                        } else {
                                            vv.start()
                                        }
                                    }

                                    videoViewRef = this
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                videoViewRef?.stopPlayback()
                                showPlayer = false
                            }
                        ) {
                            Text("关闭预览（点画面可暂停/继续）")
                        }

                    } else if (coverUrl.isNotBlank()) {

                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "视频封面",
                            contentScale = ContentScale.Crop,
                            placeholder = ColorPainter(Color(0xFF2A332C)),
                            error = ColorPainter(Color(0xFF2A332C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF2A332C))
                                .clickable {
                                    showPlayer = true
                                }
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "点封面播放预览",
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                    } else {

                        Text(
                            "未获取到封面",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

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

                    if (coverUrl.isNotBlank()) {

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                                downloadCover(
                                    context = context,
                                    coverUrl = coverUrl,
                                    title = videoTitle
                                )
                            }
                        ) {
                            Text("保存封面")
                        }
                    }

                } else if (imageUrls.isNotEmpty()) {

                    Text(
                        "图文图集 · 共 ${imageUrls.size} 张图片",
                        color = TextMain,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    GreenButton(
                        text = "下载全部图片（${imageUrls.size} 张）",
                        onClick = {

                            downloadImages(
                                context = context,
                                imageUrls = imageUrls,
                                title = videoTitle
                            )
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        OutlinedButton(
                            onClick = {
                                multiSelectMode = !multiSelectMode
                                selectedImages = emptySet()
                            }
                        ) {
                            Text(
                                if (multiSelectMode) {
                                    "退出多选"
                                } else {
                                    "多选"
                                }
                            )
                        }

                        if (multiSelectMode) {

                            Text(
                                "已选 ${selectedImages.size} 张",
                                color = TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = {
                                    selectedImages =
                                        imageUrls.indices.toSet()
                                }
                            ) {
                                Text("全选")
                            }

                            TextButton(
                                onClick = {

                                    downloadImages(
                                        context = context,
                                        imageUrls = imageUrls,
                                        title = videoTitle,
                                        onlyIndices = selectedImages
                                    )
                                }
                            ) {
                                Text("下载所选")
                            }
                        }
                    }

                    // 图片逐张展示：每张可单独下载，点开看大图
                    imageUrls.forEachIndexed { index, imageUrl ->

                        Spacer(Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            if (multiSelectMode) {

                                Checkbox(
                                    checked = index in selectedImages,
                                    onCheckedChange = { checked ->

                                        selectedImages =
                                            if (checked) {
                                                selectedImages + index
                                            } else {
                                                selectedImages - index
                                            }
                                    }
                                )
                            }

                            Text(
                                "第 ${index + 1} 张",
                                color = TextMain,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = {

                                    downloadImages(
                                        context = context,
                                        imageUrls = imageUrls,
                                        title = videoTitle,
                                        onlyIndices = setOf(index)
                                    )
                                }
                            ) {
                                Text("下载这张")
                            }
                        }

                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "第 ${index + 1} 张图片",
                            contentScale = ContentScale.Crop,
                            placeholder = ColorPainter(Color(0xFF2A332C)),
                            error = ColorPainter(Color(0xFF2A332C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(Color(0xFF2A332C))
                                .clickable {
                                    viewerIndex = index
                                }
                        )

                        Text(
                            "点图片看大图",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

            } else {

                Text(
                    "解析后将在这里显示标题和下载入口",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        if (historyEntries.isNotEmpty()) {

            Spacer(Modifier.height(18.dp))

            // 解析历史
            RoundedPanel {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        "解析历史",
                        color = TextMain,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {

                            HistoryStore.clear(context)
                            historyEntries = emptyList()
                        }
                    ) {
                        Text("清空")
                    }
                }

                Spacer(Modifier.height(8.dp))

                historyEntries.forEach { entry ->

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                restoreFromHistory(entry)
                            }
                    ) {

                        Text(
                            (if (entry.type == "image") {
                                "【图集】"
                            } else {
                                "【视频】"
                            }) + entry.title.ifBlank { "无标题" },
                            color = TextMain,
                            fontSize = 14.sp,
                            maxLines = 2
                        )

                        Text(
                            SimpleDateFormat(
                                "MM-dd HH:mm",
                                Locale.getDefault()
                            ).format(Date(entry.time)) +
                                    " · " + entry.author.ifBlank { "未知作者" } +
                                    if (entry.type == "image") {
                                        " · ${entry.images.size} 张图"
                                    } else {
                                        ""
                                    },
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    "点任意一条即可恢复当时的解析结果，不用重新解析",
                    color = TextMuted,
                    fontSize = 12.sp
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

    // 图集大图查看（点任意一张图打开，点画面关闭）
    if (viewerIndex in imageUrls.indices) {

        Dialog(
            onDismissRequest = {
                viewerIndex = -1
            }
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        viewerIndex = -1
                    }
            ) {

                AsyncImage(
                    model = imageUrls[viewerIndex],
                    contentDescription = "第 ${viewerIndex + 1} 张图片大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                Text(
                    "第 ${viewerIndex + 1} 张 / 共 ${imageUrls.size} 张 · 点任意处关闭",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
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
            "https://api.odysphere.tech/jiexi/douyin?url=$encodedUrl"

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
