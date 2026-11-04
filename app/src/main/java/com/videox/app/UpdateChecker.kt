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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// 一个可用的新版本：版本号、发行说明（即弹窗里展示的更新内容）、APK 直链
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String
)

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

// 查 GitHub 最新正式发行：有比当前更新的版本就返回它，没有/失败返回 null
suspend fun checkForUpdate(context: Context): UpdateInfo? {

    return withContext(Dispatchers.IO) {

        try {

            val connection = URL(
                "https://api.github.com/repos/h2587145633-lgtm/VideoX-Android/releases/latest"
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

                if (connection.responseCode !in 200..299) {
                    return@withContext null
                }

                val body = connection.inputStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return@withContext null

                val json = JSONObject(body)

                val name = json.optString("name", "")

                val version = Regex("""v(\d+\.\d+\.\d+)""")
                    .find(name)
                    ?.groupValues
                    ?.get(1)
                    ?: return@withContext null

                if (!isNewerVersion(
                        version,
                        currentVersionName(context)
                    )
                ) {
                    return@withContext null
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
                    return@withContext null
                }

                UpdateInfo(
                    version = version,
                    notes = json.optString("body", "").trim(),
                    apkUrl = apkUrl
                )

            } finally {

                connection.disconnect()
            }

        } catch (e: Exception) {

            null
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
                            info.apkUrl
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

// 下载新 APK 到缓存目录（带进度回调）；失败返回 null
private suspend fun downloadUpdateApk(
    context: Context,
    apkUrl: String,
    onProgress: (Int) -> Unit
): File? {

    return withContext(Dispatchers.IO) {

        try {

            val connection = URL(apkUrl)
                .openConnection() as HttpURLConnection

            try {

                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000

                connection.setRequestProperty(
                    "User-Agent",
                    "VideoX-Android"
                )

                if (connection.responseCode !in 200..299) {
                    return@withContext null
                }

                val total = connection.contentLengthLong
                val dir = context.externalCacheDir
                    ?: context.cacheDir
                val file = File(dir, "videox-update.apk")

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
                    }

                } ?: return@withContext null

                file

            } finally {

                connection.disconnect()
            }

        } catch (e: Exception) {

            null
        }
    }
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
