package com.videox.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import android.widget.VideoView

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

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

// 带请求头的图片加载：抖音图片 CDN 对缺 Referer 的请求更容易失败，统一带上
// 与下载时用的请求头保持一致；加载中显示骨架屏微光占位；
// 加载失败会回调 onError 供界面提示重试
@Composable
fun AuthedAsyncImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {

    val ctx = LocalContext.current

    val request = remember(url) {

        ImageRequest.Builder(ctx)
            .data(url)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            .addHeader("Referer", "https://www.douyin.com/")
            .crossfade(true)
            .build()
    }

    val skeletonTransition = rememberInfiniteTransition(
        label = "image_skeleton"
    )

    val skeletonAlpha by skeletonTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "image_skeleton_alpha"
    )

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        SurfaceInner.copy(alpha = skeletonAlpha)
                    )
            )
        },
        error = {

            SideEffect {
                onError()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A332C))
            )
        }
    )
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

    var fullText by remember {
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

    // 与 imageUrls 一一对应：该图是动图时为它的视频直链，否则为空串
    var liveVideoUrls by remember {
        mutableStateOf<List<String>>(emptyList())
    }

    var musicTitle by remember {
        mutableStateOf("")
    }

    var musicAuthor by remember {
        mutableStateOf("")
    }

    var musicUrl by remember {
        mutableStateOf("")
    }

    var selectedImages by remember {
        mutableStateOf<Set<Int>>(emptySet())
    }

    var failedImages by remember {
        mutableStateOf<Set<Int>>(emptySet())
    }

    var retryMap by remember {
        mutableStateOf<Map<Int, Int>>(emptyMap())
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

    var showResult by remember {
        mutableStateOf(false)
    }

    var resultTab by remember {
        mutableStateOf(0)
    }

    var localFileName by remember {
        mutableStateOf("")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var historyEntries by remember {
        mutableStateOf<List<ParseHistoryEntry>>(emptyList())
    }

    // 从历史记录恢复一条解析结果（不重新请求接口，直接进结果页）
    fun restoreFromHistory(entry: ParseHistoryEntry) {

        url = entry.sourceUrl
        videoTitle = entry.title
        fullText = entry.desc.ifBlank { entry.title }
        author = entry.author
        coverUrl = entry.cover
        contentType = entry.type
        videoUrl = entry.videoUrl
        imageUrls = entry.images
        liveVideoUrls =
            (entry.liveVideos +
                    List(entry.images.size) { "" })
                .take(entry.images.size)
        musicTitle = entry.musicTitle
        musicAuthor = entry.musicAuthor
        musicUrl = entry.musicUrl
        selectedImages = entry.images.indices.toSet()
        failedImages = emptySet()
        retryMap = emptyMap()
        showPlayer = false
        resultTab = 0
        recognized =
            entry.videoUrl.startsWith("http://") ||
            entry.videoUrl.startsWith("https://") ||
            entry.images.isNotEmpty()
        message = "已从历史记录恢复"
        showResult = true
    }

    // 发起一次解析（解析按钮和结果页的“重新解析”共用）
    fun startParse() {

        if (loading) {
            return
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
                liveVideoUrls = emptyList()
                fullText = ""
                musicTitle = ""
                musicAuthor = ""
                musicUrl = ""
                selectedImages = emptySet()
                failedImages = emptySet()
                retryMap = emptyMap()
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

                            fullText = data.optString(
                                "desc",
                                ""
                            ).ifBlank {
                                videoTitle
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

                            // 动图（实况照片）：live_photo 每项是
                            // 静态图 + 一小段视频，按静态图地址配对
                            val liveArray =
                                data.optJSONArray("live_photo")

                            val liveMap =
                                mutableMapOf<String, String>()

                            if (liveArray != null) {

                                for (i in 0 until liveArray.length()) {

                                    val item = liveArray
                                        .optJSONObject(i)
                                        ?: continue

                                    val img = item.optString(
                                        "image",
                                        ""
                                    )

                                    val vid = item.optString(
                                        "video",
                                        ""
                                    )

                                    if (img.isNotBlank() &&
                                        vid.startsWith("http")
                                    ) {
                                        liveMap[img] = vid
                                    }
                                }
                            }

                            if (liveMap.isNotEmpty()) {

                                // 动图里有、图片列表里没有的补进来
                                liveMap.keys.forEach { img ->

                                    if (img !in parsedImages) {
                                        parsedImages.add(img)
                                    }
                                }

                                imageUrls = parsedImages

                                liveVideoUrls = parsedImages.map {
                                    liveMap[it] ?: ""
                                }
                            }

                            val musicObject =
                                data.optJSONObject("music")

                            musicTitle = musicObject?.optString(
                                "title",
                                ""
                            ) ?: ""

                            musicAuthor = musicObject?.optString(
                                "author",
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

                            val liveCount = liveVideoUrls.count {
                                it.isNotBlank()
                            }

                            message = when {

                                hasVideo ->
                                    "解析成功，可以下载视频"

                                liveCount > 0 ->
                                    "解析成功，动图作品：共 ${parsedImages.size} 张，其中 $liveCount 张是动图"

                                contentType == "image" && hasImages ->
                                    "解析成功，这是图文图集，共 ${parsedImages.size} 张图片"

                                hasImages ->
                                    "解析成功，共 ${parsedImages.size} 张图片可下载"

                                else ->
                                    "解析成功，但没有获取到可下载的内容"
                            }

                            if (recognized) {

                                haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )

                                HistoryStore.add(
                                    context,
                                    ParseHistoryEntry(
                                        sourceUrl = douyinUrl,
                                        type = contentType,
                                        title = videoTitle,
                                        desc = fullText,
                                        author = author,
                                        cover = coverUrl,
                                        videoUrl = videoUrl,
                                        images = parsedImages,
                                        liveVideos = liveVideoUrls,
                                        musicTitle = musicTitle,
                                        musicAuthor = musicAuthor,
                                        musicUrl = musicUrl,
                                        time = System.currentTimeMillis()
                                    )
                                )

                                historyEntries =
                                    HistoryStore.load(context)

                                // 解析成功：图片默认全选，自动进入结果页
                                selectedImages =
                                    parsedImages.indices.toSet()
                                resultTab = 0
                                showResult = true
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

// TXT 文件选择器
val txtPicker = rememberLauncherForActivityResult(
ActivityResultContracts.OpenDocument()
) { uri ->

if (uri!= null) {

scope.launch {

try {

val content = withContext(Dispatchers.IO) {

context.contentResolver
.openInputStream(uri)
?.bufferedReader()
?.use { it.readText()}
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

if (uri!= null) {

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
clipText!= url &&
extractDouyinUrl(clipText)!= null
) {

url = clipText
recognized = false
videoUrl = ""
message = "已自动识别剪贴板中的抖音链接，点击解析即可"
}
}
}

// 输入页与结果页之间横向平移切换
AnimatedContent(
targetState = showResult,
transitionSpec = {

if (targetState) {

slideIntoContainer(
AnimatedContentTransitionScope.SlideDirection.Left,
tween(durationMillis = 300)
) togetherWith slideOutOfContainer(
AnimatedContentTransitionScope.SlideDirection.Left,
tween(durationMillis = 300)
)

} else {

slideIntoContainer(
AnimatedContentTransitionScope.SlideDirection.Right,
tween(durationMillis = 300)
) togetherWith slideOutOfContainer(
AnimatedContentTransitionScope.SlideDirection.Right,
tween(durationMillis = 300)
)
}
},
label = "result_slide"
) { resultShown ->

if (resultShown) {

// ================= 解析结果页 =================
Column(
modifier = Modifier.fillMaxSize()
) {

Row(
verticalAlignment = Alignment.CenterVertically
) {

TextButton(
onClick = {
videoViewRef?.stopPlayback()
showPlayer = false
showResult = false
}
) {
Text("‹ 返回")
}

Text(
"解析结果",
color = TextMain,
fontSize = 20.sp,
modifier = Modifier.weight(1f)
)

TextButton(
onClick = {
startParse()
},
enabled =!loading
) {
Text(
if (loading) {
"解析中…"
} else {
"重新解析"
}
)
}
}

Text(
videoTitle,
color = TextMain,
fontSize = 16.sp,
maxLines = 2,
overflow = TextOverflow.Ellipsis
)

Spacer(Modifier.height(4.dp))

Text(
"作者：$author",
color = TextMuted,
fontSize = 13.sp
)

Text(
message,
color = if (recognized) {
AccentColorState.value
} else {
TextMuted
},
fontSize = 12.sp
)

Spacer(Modifier.height(10.dp))

val tabTitles = mutableListOf<String>()

if (videoUrl.isNotBlank()) {
tabTitles.add("视频下载")
}

if (imageUrls.isNotEmpty()) {
tabTitles.add("图片下载")
}

if (musicUrl.startsWith("http://") ||
musicUrl.startsWith("https://")
) {
tabTitles.add("音乐下载")
}

tabTitles.add("复制文案")

val currentTab = resultTab.coerceIn(
0,
tabTitles.size - 1
)

val currentTitle = tabTitles[currentTab]

TabRow(
selectedTabIndex = currentTab,
containerColor = Color.Transparent,
contentColor = AccentColorState.value
) {

tabTitles.forEachIndexed { index, title ->

Tab(
selected = index == currentTab,
onClick = {
resultTab = index
},
text = {
Text(
title,
fontSize = 13.sp
)
}
)
}
}

Column(
modifier = Modifier
.weight(1f)
.verticalScroll(rememberScrollState())
) {

when (currentTitle) {

"视频下载" -> {

Spacer(Modifier.height(14.dp))

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

AuthedAsyncImage(
url = coverUrl,
contentDescription = "视频封面",
contentScale = ContentScale.Crop,
modifier = Modifier
.fillMaxWidth()
.aspectRatio(16f / 9f)
.clip(RoundedCornerShape(12.dp))
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
}

"图片下载" -> {

Spacer(Modifier.height(14.dp))

Text(
if (liveVideoUrls.any { it.isNotBlank() }) {
"共 ${imageUrls.size} 张（${liveVideoUrls.count { it.isNotBlank() }} 张动图）· 点图看大图，点右上角圆圈勾选"
} else {
"共 ${imageUrls.size} 张 · 点图看大图，点右上角圆圈勾选"
},
color = TextMuted,
fontSize = 12.sp
)

// 正方形预览、每行两张的网格
imageUrls.indices.chunked(2).forEach { rowIndices ->

Spacer(Modifier.height(8.dp))

Row(
horizontalArrangement =
Arrangement.spacedBy(8.dp)
) {

rowIndices.forEach { index ->

Box(
modifier = Modifier
.weight(1f)
.aspectRatio(1f)
.clip(RoundedCornerShape(12.dp))
.background(Color(0xFF2A332C))
.clickable {

if (index in failedImages) {

failedImages =
failedImages - index

retryMap = retryMap +
(index to ((retryMap[index]?: 0) + 1))

} else {

viewerIndex = index
}
}
) {

key(index, retryMap[index]?: 0) {

AuthedAsyncImage(
url = imageUrls[index],
contentDescription = "第 ${index + 1} 张图片",
contentScale = ContentScale.Crop,
modifier = Modifier.fillMaxSize(),
onError = {
failedImages =
failedImages + index
}
)
}

if (index in failedImages) {

Text(
"加载失败\n点图重试",
color = Color.White,
fontSize = 12.sp,
modifier = Modifier
.align(Alignment.Center)
)
}

// 动图标记（左上角小角标）
if (liveVideoUrls.getOrNull(index)
?.isNotBlank() == true
) {

Box(
modifier = Modifier
.align(Alignment.TopStart)
.padding(6.dp)
.background(
Color.Black.copy(alpha = 0.55f),
RoundedCornerShape(6.dp)
)
.padding(
horizontal = 6.dp,
vertical = 2.dp
)
) {

Text(
"动图",
color = Color.White,
fontSize = 10.sp
)
}
}

// 右上角勾选圈（常显）
Box(
modifier = Modifier
.align(Alignment.TopEnd)
.padding(6.dp)
.size(24.dp)
.background(
if (index in selectedImages) {
AccentColorState.value
} else {
Color.White.copy(alpha = 0.85f)
},
CircleShape
)
.clickable {

selectedImages =
if (index in selectedImages) {
selectedImages - index
} else {
selectedImages + index
}
},
contentAlignment = Alignment.Center
) {

if (index in selectedImages) {

Text(
"✓",
color = Color.Black,
fontSize = 14.sp
)
}
}

// 底部保存条：普通图是“保存本图”；动图左右各半，
// 左存静态图、右存动图（视频部分）
if (liveVideoUrls.getOrNull(index)
?.isNotBlank() == true
) {

Row(
modifier = Modifier
.align(Alignment.BottomCenter)
.fillMaxWidth()
) {

Box(
modifier = Modifier
.weight(1f)
.background(
Color.Black.copy(alpha = 0.45f)
)
.clickable {

downloadImages(
context = context,
imageUrls = imageUrls,
title = videoTitle,
onlyIndices = setOf(index)
)
}
.padding(vertical = 6.dp),
contentAlignment = Alignment.Center
) {

Text(
"静态图",
color = Color.White,
fontSize = 12.sp
)
}

Box(
modifier = Modifier
.weight(1f)
.background(
AccentColorState.value.copy(
alpha = 0.75f
)
)
.clickable {

downloadLiveVideos(
context = context,
items = listOf(
index to
liveVideoUrls[index]
),
title = videoTitle,
total = imageUrls.size
)
}
.padding(vertical = 6.dp),
contentAlignment = Alignment.Center
) {

Text(
"保存动图",
color = Color.White,
fontSize = 12.sp
)
}
}

} else {

Box(
modifier = Modifier
.align(Alignment.BottomCenter)
.fillMaxWidth()
.background(
Color.Black.copy(alpha = 0.45f)
)
.clickable {

downloadImages(
context = context,
imageUrls = imageUrls,
title = videoTitle,
onlyIndices = setOf(index)
)
}
.padding(vertical = 6.dp),
contentAlignment = Alignment.Center
) {

Text(
"保存本图",
color = Color.White,
fontSize = 12.sp
)
}
}
}
}

if (rowIndices.size == 1) {

Spacer(Modifier.weight(1f))
}
}
}
}

"音乐下载" -> {

Spacer(Modifier.height(14.dp))

Text(
"歌曲：${musicTitle.ifBlank { "未知音乐"}}",
color = TextMain,
fontSize = 15.sp
)

Spacer(Modifier.height(6.dp))

Text(
"歌手：${musicAuthor.ifBlank { "未知"}}",
color = TextMuted,
fontSize = 13.sp
)

Spacer(Modifier.height(14.dp))

GreenButton(
text = "下载音乐",
onClick = {

downloadMusic(
context = context,
musicUrl = musicUrl,
musicTitle = musicTitle,
workTitle = videoTitle
)
}
)
}

else -> {

// 复制文案：全文只在这里看和复制
Spacer(Modifier.height(14.dp))

SelectionContainer {

Text(
fullText.ifBlank { videoTitle},
color = TextMain,
fontSize = 14.sp
)
}

Spacer(Modifier.height(14.dp))

GreenButton(
text = "复制全文",
onClick = {

val clipboard =
context.getSystemService(
Context.CLIPBOARD_SERVICE
) as ClipboardManager

clipboard.setPrimaryClip(
ClipData.newPlainText(
"VideoX 文案",
fullText.ifBlank { videoTitle}
)
)

Toast.makeText(
context,
"文案已复制",
Toast.LENGTH_SHORT
).show()
}
)
}
}

Spacer(Modifier.height(16.dp))
}

// 图片标签页的底部固定栏
if (currentTitle == "图片下载") {

Row(
modifier = Modifier
.fillMaxWidth()
.padding(vertical = 8.dp),
verticalAlignment = Alignment.CenterVertically
) {

OutlinedButton(
onClick = {

selectedImages =
if (selectedImages.size == imageUrls.size) {
emptySet()
} else {
imageUrls.indices.toSet()
}
}
) {
Text(
if (selectedImages.size == imageUrls.size) {
"取消全选"
} else {
"全选"
}
)
}

Spacer(Modifier.width(8.dp))

Button(
onClick = {

if (selectedImages.isEmpty()) {

// 复用 downloadImages 的未勾选提示
downloadImages(
context = context,
imageUrls = imageUrls,
title = videoTitle,
onlyIndices = emptySet()
)

} else {

// 勾选里分开处理：普通图批量下图片，
// 动图下它们的视频部分
val stillSelected = selectedImages
.filter {
liveVideoUrls.getOrNull(it)
.isNullOrBlank()
}
.toSet()

val liveSelected = selectedImages
.filter {
!liveVideoUrls.getOrNull(it)
.isNullOrBlank()
}

if (stillSelected.isNotEmpty()) {

downloadImages(
context = context,
imageUrls = imageUrls,
title = videoTitle,
onlyIndices = stillSelected
)
}

if (liveSelected.isNotEmpty()) {

downloadLiveVideos(
context = context,
items = liveSelected.map {
it to liveVideoUrls[it]
},
title = videoTitle,
total = imageUrls.size
)
}
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
"保存全部图片（${selectedImages.size}/${imageUrls.size}）",
color = Color(0xFF102017),
fontSize = 16.sp
)
}
}
}
}

} else {

// ================= 输入页 =================
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
fullText = ""
author = ""
coverUrl = ""
videoUrl = ""
contentType = "video"
imageUrls = emptyList()
liveVideoUrls = emptyList()
musicTitle = ""
musicAuthor = ""
musicUrl = ""
selectedImages = emptySet()
failedImages = emptySet()
retryMap = emptyMap()
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
"开始解析"
},
onClick = {
startParse()
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

// 解析历史（没有记录时显示空状态）
RoundedPanel {

if (historyEntries.isEmpty()) {

Text(
"解析历史",
color = TextMain,
fontSize = 18.sp
)

Spacer(Modifier.height(8.dp))

Text(
"暂无解析历史",
color = TextMuted,
fontSize = 13.sp
)

Text(
"解析成功的内容会自动存到这里，点一条就能恢复，不用重新解析",
color = TextMuted,
fontSize = 12.sp
)

} else {

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
""
} else {
""
}) + entry.title.ifBlank { "无标题"},
color = TextMain,
fontSize = 14.sp,
maxLines = 2
)

Text(
SimpleDateFormat(
"MM-dd HH:mm",
Locale.getDefault()
).format(Date(entry.time)) +
" · " + entry.author.ifBlank { "未知作者"} +
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
}
}

// 图集大图查看（点任意一张图打开，点画面关闭；动图会循环播放它的视频部分）
if (viewerIndex in imageUrls.indices) {

val viewerLiveUrl = liveVideoUrls
.getOrNull(viewerIndex)
.orEmpty()

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

if (viewerLiveUrl.isNotBlank()) {

AndroidView(
factory = { ctx ->

VideoView(ctx).apply {

if (Build.VERSION.SDK_INT >= 26) {

setVideoURI(
Uri.parse(viewerLiveUrl),
mapOf(
"User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
"Referer" to "https://www.douyin.com/"
)
)

} else {

setVideoURI(Uri.parse(viewerLiveUrl))
}

setOnPreparedListener { player ->
player.isLooping = true
player.start()
}
}
},
onRelease = { view ->
view.stopPlayback()
},
modifier = Modifier.fillMaxSize()
)

Text(
"动图 ${viewerIndex + 1} / 共 ${imageUrls.size} 张",
color = Color.White,
fontSize = 13.sp,
modifier = Modifier
.align(Alignment.TopCenter)
.padding(16.dp)
)

Row(
modifier = Modifier
.align(Alignment.BottomCenter)
.padding(16.dp),
horizontalArrangement =
Arrangement.spacedBy(8.dp)
) {

Button(
onClick = {

downloadImages(
context = context,
imageUrls = imageUrls,
title = videoTitle,
onlyIndices = setOf(viewerIndex)
)
}
) {
Text("保存静态图")
}

Button(
onClick = {

val stillUrl = imageUrls[viewerIndex]

scope.launch {

Toast.makeText(
context,
"正在合成动态照片...",
Toast.LENGTH_SHORT
).show()

val resultMessage = saveMotionPhoto(
context = context,
imageUrl = stillUrl,
videoUrl = viewerLiveUrl,
title = videoTitle
)

Toast.makeText(
context,
resultMessage,
Toast.LENGTH_LONG
).show()
}
}
) {
Text("存为动态照片")
}
}

} else {

AuthedAsyncImage(
url = imageUrls[viewerIndex],
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
