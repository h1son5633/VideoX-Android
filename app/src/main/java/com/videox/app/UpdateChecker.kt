package com.videox.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.FileProvider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

// 一个可用的新版本：版本号、发行说明（弹窗里展示的更新内容）、
// APK 官方直链、发布标签（拼国内 CDN 备用线路用）
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val tag: String
)

// 检查更新的三种结果，必须分开：以前统一返回 null，
// “检查失败”会被误报成“已是最新版本”
sealed interface UpdateCheckResult {

    // 有新版本可用
    data class HasUpdate(
        val info: UpdateInfo
    ) : UpdateCheckResult

    // 已确认是最新版本
    object UpToDate : UpdateCheckResult

    // 检查失败（网络/服务器/数据异常），reason 只作内部区分
    data class Failed(
        val reason: String
    ) : UpdateCheckResult
}

// 当前已安装的版本号（读不到时按 0.0.0 处理，总会提示更新）
fun currentVersionName(context: Context): String {

    return try {

        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "0.0.0"

    } catch (e: Exception) {

        "0.0.0"
    }
}

// 语义版本比较：latest 比 current 新才返回 true（按段逐个比数字）
fun isNewerVersion(latest: String, current: String): Boolean {

    fun parts(v: String): List<Int> {

        return v.split(".")
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    }

    val a = parts(latest)
    val b = parts(current)
    val n = maxOf(a.size, b.size)

    for (i in 0 until n) {

        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }

        if (x != y) {
            return x > y
        }
    }

    return false
}

// 查 GitHub 最新正式发行，结果分三态返回（有新版/已是最新/检查失败）
suspend fun checkForUpdate(
    context: Context
): UpdateCheckResult {

    return withContext(Dispatchers.IO) {

        try {

            val connection = URL(
                "https://api.github.com/repos/h1son5633/VideoX-Android/releases/latest"
            ).openConnection() as HttpURLConnection

            try {

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )

                connection.setRequestProperty(
                    "User-Agent",
                    "VideoX-Android"
                )

                val status = connection.responseCode

                if (status !in 200..299) {
                    return@withContext UpdateCheckResult.Failed(
                        "服务器返回 HTTP $status"
                    )
                }

                val body = connection.inputStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return@withContext UpdateCheckResult.Failed(
                        "服务器没有返回内容"
                    )

                val json = JSONObject(body)

                val name = json.optString("name", "")

                val version = Regex("""v(\d+\.\d+\.\d+)""")
                    .find(name)
                    ?.groupValues
                    ?.get(1)
                    ?: return@withContext UpdateCheckResult.Failed(
                        "无法识别最新版本信息"
                    )

                if (!isNewerVersion(
                        version,
                        currentVersionName(context)
                    )
                ) {
                    return@withContext UpdateCheckResult.UpToDate
                }

                val assets = json.optJSONArray("assets")
                var apkUrl = ""

                if (assets != null) {

                    for (i in 0 until assets.length()) {

                        val asset = assets.optJSONObject(i)
                            ?: continue

                        if (asset.optString("name", "")
                                .endsWith(".apk")
                        ) {

                            apkUrl = asset.optString(
                                "browser_download_url",
                                ""
                            )

                            break
                        }
                    }
                }

                if (apkUrl.isBlank()) {
                    return@withContext UpdateCheckResult.Failed(
                        "最新版没有安装包"
                    )
                }

                UpdateCheckResult.HasUpdate(
                    UpdateInfo(
                        version = version,
                        notes = json.optString("body", "").trim(),
                        apkUrl = apkUrl,
                        tag = json.optString("tag_name", "")
                    )
                )

            } finally {

                connection.disconnect()
            }

        } catch (e: Exception) {

            UpdateCheckResult.Failed(
                e.message ?: "网络异常"
            )
        }
    }
}

// 更新弹窗：显示版本号与更新说明，点“下载并安装”走下载→拉起系统安装器
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember {
        mutableStateOf(false)
    }

    var progress by remember {
        mutableStateOf(0)
    }

    var errorText by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = {
            if (!downloading) {
                onDismiss()
            }
        },
        title = {
            Text("发现新版本 v${info.version}")
        },
        text = {

            Column {

                Text(
                    "更新内容",
                    color = TextMain,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(
                        info.notes.ifBlank {
                            "（本版未附更新说明）"
                        },
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                if (downloading) {

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentColorState.value,
                        trackColor = SurfaceInner
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "正在下载… $progress%",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                if (errorText.isNotBlank()) {

                    Spacer(Modifier.height(8.dp))

                    Text(
                        errorText,
                        color = Color(0xFFFF7777),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {

            Button(
                enabled = !downloading,
                onClick = {

                    downloading = true
                    errorText = ""
                    progress = 0

                    scope.launch {

                        val file = downloadUpdateApk(
                            context,
                            info
                        ) { percent ->
                            progress = percent
                        }

                        downloading = false

                        if (file != null) {

                            installUpdateApk(context, file)
                            onDismiss()

                        } else {

                            errorText =
                                "下载失败，请稍后再试，或去 GitHub Releases 手动下载"
                        }
                    }
                }
            ) {
                Text(if (downloading) "下载中…" else "下载并安装")
            }
        },
        dismissButton = {

            TextButton(
                enabled = !downloading,
                onClick = onDismiss
            ) {
                Text("稍后再说")
            }
        }
    )
}

// 下载新 APK 到缓存目录（带进度回调）；全部线路都失败才返回 null。
// 背景：GitHub 官方文件服务器在国内网络经常连不上（v1.2.3 首次真机更新
// 就卡在这一步），故按顺序尝试多条线路，每条线路都用多线程分段下载：
// 1）gh-proxy 国内加速 2）ghfast 国内加速 3）GitHub 官方直链。
// （jsdelivr 线路实测对本仓库始终 404，2026-11-09 已移除。）
// 安装时系统会校验签名，线路只影响能不能下下来。
private suspend fun downloadUpdateApk(
    context: Context,
    info: UpdateInfo,
    onProgress: (Int) -> Unit
): File? {

    return withContext(Dispatchers.IO) {

        val dir = context.externalCacheDir
            ?: context.cacheDir
        val file = File(dir, "videox-update.apk")

        val sources = mutableListOf<String>()

        sources.add("https://gh-proxy.com/" + info.apkUrl)
        sources.add("https://ghfast.top/" + info.apkUrl)
        sources.add(info.apkUrl)

        for (source in sources) {

            if (file.exists()) {
                file.delete()
            }

            val ok = try {
                downloadFromSource(source, file, onProgress)
            } catch (e: Exception) {
                false
            }

            if (ok && file.exists() && file.length() > 0) {
                return@withContext file
            }
        }

        null
    }
}

// 打开一个带请求头的连接（各下载步骤共用）
private fun openDownloadConnection(
    url: String
): HttpURLConnection {

    val connection = URL(url)
        .openConnection() as HttpURLConnection

    connection.requestMethod = "GET"
    connection.connectTimeout = 15000
    connection.readTimeout = 30000

    connection.setRequestProperty(
        "User-Agent",
        "VideoX-Android"
    )

    return connection
}

// 从一条线路下载：支持分段就 4 线程分段下（每段失败自动重试），
// 不支持分段就单线程整下；下完校验总长度，不对算失败
private suspend fun downloadFromSource(
    url: String,
    file: File,
    onProgress: (Int) -> Unit
): Boolean {

    // 先用 1 字节的 Range 请求探一下总长和分段支持
    var total = -1L

    try {

        val probe = openDownloadConnection(url)

        try {

            probe.setRequestProperty(
                "Range",
                "bytes=0-0"
            )

            if (probe.responseCode == 206) {

                val range = probe.getHeaderField(
                    "Content-Range"
                ) ?: ""

                total = range.substringAfterLast("/")
                    .toLongOrNull()
                    ?: -1L
            }

        } finally {

            probe.disconnect()
        }

    } catch (e: Exception) {

        total = -1L
    }

    if (total > 0) {
        return downloadRanged(url, file, total, onProgress)
    }

    return downloadWhole(url, file, onProgress)
}

// 多线程分段下载：4 段并行，每段独立连接、失败重试 3 次
private suspend fun downloadRanged(
    url: String,
    file: File,
    total: Long,
    onProgress: (Int) -> Unit
): Boolean {

    val threads = 4
    val counter = AtomicLong(0)

    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(total)
    }

    val partSize = (total + threads - 1) / threads

    val results = coroutineScope {

        (0 until threads).map { index ->

            async(Dispatchers.IO) {

                val start = index * partSize
                val end = minOf(
                    start + partSize - 1,
                    total - 1
                )

                if (start > end) {
                    true
                } else {
                    downloadPart(
                        url,
                        file,
                        start,
                        end,
                        total,
                        counter,
                        onProgress
                    )
                }
            }
        }.awaitAll()
    }

    return results.all { it } && file.length() == total
}

// 下载一个分段并写到文件的对应偏移处；整段重试最多 3 次
private fun downloadPart(
    url: String,
    file: File,
    start: Long,
    end: Long,
    total: Long,
    counter: AtomicLong,
    onProgress: (Int) -> Unit
): Boolean {

    val length = end - start + 1

    // 本段已计入总进度的字节数（重试重下时不重复计数）
    var counted = 0L

    for (attempt in 1..3) {

        try {

            val connection = openDownloadConnection(url)

            try {

                connection.setRequestProperty(
                    "Range",
                    "bytes=$start-$end"
                )

                // 分段必须回 206；回 200 说明线路无视 Range，会写坏文件
                if (connection.responseCode != 206) {
                    return false
                }

                RandomAccessFile(file, "rw").use { raf ->

                    raf.seek(start)

                    connection.inputStream?.use { input ->

                        val buffer = ByteArray(64 * 1024)
                        var written = 0L

                        while (true) {

                            val read = input.read(buffer)

                            if (read < 0) {
                                break
                            }

                            raf.write(buffer, 0, read)
                            written += read

                            val delta = written - counted

                            val done = if (delta > 0) {
                                counted = written
                                counter.addAndGet(delta)
                            } else {
                                counter.get()
                            }

                            onProgress(
                                ((done * 100) / total)
                                    .toInt()
                                    .coerceIn(0, 100)
                            )
                        }

                        if (written != length) {
                            throw Exception("分段长度不符")
                        }
                    } ?: throw Exception("无数据")
                }

                return true

            } finally {

                connection.disconnect()
            }

        } catch (e: Exception) {

            if (attempt == 3) {
                return false
            }

            Thread.sleep(600)
        }
    }

    return false
}

// 单线程整体下载（线路不支持分段时）；失败重试 2 次，下完校验长度
private fun downloadWhole(
    url: String,
    file: File,
    onProgress: (Int) -> Unit
): Boolean {

    for (attempt in 1..2) {

        try {

            val connection = openDownloadConnection(url)

            try {

                if (connection.responseCode !in 200..299) {
                    return false
                }

                val total = connection.contentLengthLong

                connection.inputStream?.use { input ->

                    file.outputStream().use { output ->

                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L

                        while (true) {

                            val read = input.read(buffer)

                            if (read < 0) {
                                break
                            }

                            output.write(buffer, 0, read)
                            downloaded += read

                            if (total > 0) {

                                onProgress(
                                    ((downloaded * 100) / total)
                                        .toInt()
                                        .coerceIn(0, 100)
                                )
                            }
                        }

                        if (total > 0 && downloaded != total) {
                            throw Exception("长度不符")
                        }
                    }

                } ?: return false

                return file.length() > 0

            } finally {

                connection.disconnect()
            }

        } catch (e: Exception) {

            if (attempt == 2) {
                return false
            }

            Thread.sleep(600)
        }
    }

    return false
}

// 拉起系统安装器覆盖安装；未授予“安装未知应用”时先引导去授权
private fun installUpdateApk(context: Context, file: File) {

    if (Build.VERSION.SDK_INT >= 26 &&
        !context.packageManager.canRequestPackageInstalls()
    ) {

        Toast.makeText(
            context,
            "请先允许 VideoX 安装应用，允许后回来再点一次更新",
            Toast.LENGTH_LONG
        ).show()

        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(settingsIntent)

        return
    }

    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(
            uri,
            "application/vnd.android.package-archive"
        )
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

    context.startActivity(intent)
}
