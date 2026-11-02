
package com.videox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF101411)
private val SurfaceGlass = Color(0xFF1C241F)
private val SurfaceInner = Color(0xFF252E28)
private val Green = Color(0xFF62E6A2)
private val TextMain = Color(0xFFF1F5F2)
private val TextMuted = Color(0xFF9CAAA1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else {
            ""
        }

        setContent {
            VideoXApp(sharedText)
        }
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
    var platform by remember { mutableStateOf("等待识别") }
    var message by remember { mutableStateOf("粘贴分享链接后开始识别") }
    var recognized by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "VideoX",
            color = Green,
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
                    message = "粘贴分享链接后开始识别"
                    recognized = false
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("粘贴视频或图集分享链接")
                },
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    focusedBorderColor = Green,
                    unfocusedBorderColor = Color(0xFF46534A),
                    focusedPlaceholderColor = TextMuted,
                    unfocusedPlaceholderColor = TextMuted,
                    cursorColor = Green
                )
            )

            Spacer(Modifier.height(16.dp))

            GreenButton(
                text = "识别链接",
                onClick = {
                    platform = detectPlatform(url)
                    recognized = platform != "未知平台"

                    message = when {
                        url.isBlank() -> "请先粘贴链接"
                        recognized -> "已识别平台：$platform"
                        else -> "暂不支持识别该链接"
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            Text(
                message,
                color = if (recognized) Green else TextMuted,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        RoundedPanel {
            Text(
                "识别结果",
                color = TextMain,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            if (recognized) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(platform, color = Green, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "链接已识别，等待解析功能接入",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Button(
                        onClick = {
                            message = "当前仅完成平台识别，真实下载功能尚未接入"
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green
                        )
                    ) {
                        Text("下载", color = Color.Black)
                    }
                }
            } else {
                Text(
                    "识别后将在这里显示媒体预览和下载入口",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "VideoX · Android",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun DownloadPage() {
    var selectedTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Text("下载", color = TextMain, fontSize = 28.sp)
        Text("管理你的下载内容", color = TextMuted, fontSize = 14.sp)

        Spacer(Modifier.height(20.dp))

        RoundedPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceInner, RoundedCornerShape(50.dp))
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
                    .background(SurfaceInner, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (selectedTab == 0) "暂无视频任务" else "暂无图集任务",
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

@Composable
fun SettingsPage() {
    var saveToGallery by remember { mutableStateOf(true) }
    var autoClipboard by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("设置", color = TextMain, fontSize = 28.sp)
        Text("自定义 VideoX 使用体验", color = TextMuted, fontSize = 14.sp)

        Spacer(Modifier.height(20.dp))

        RoundedPanel {
            Text("保存设置", color = Green, fontSize = 16.sp)

            Spacer(Modifier.height(8.dp))

            SettingSwitch(
                title = "默认保存到相册",
                subtitle = "下载完成后保存到系统媒体库",
                checked = saveToGallery,
                onCheckedChange = { saveToGallery = it }
            )

            Divider(color = Color(0xFF39443C))

            SettingSwitch(
                title = "自动识别剪贴板",
                subtitle = "检测到分享链接时提示识别",
                checked = autoClipboard,
                onCheckedChange = { autoClipboard = it }
            )

            Divider(color = Color(0xFF39443C))

            SettingSwitch(
                title = "仅 Wi-Fi 下载",
                subtitle = "避免使用移动网络消耗流量",
                checked = wifiOnly,
                onCheckedChange = { wifiOnly = it }
            )
        }

        Spacer(Modifier.height(16.dp))

        RoundedPanel {
            Text("关于", color = Green, fontSize = 16.sp)

            Spacer(Modifier.height(12.dp))

            Text("VideoX Android", color = TextMain)
            Text("版本 0.1.0 · UI 开发阶段", color = TextMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun RoundedPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceGlass
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

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
            containerColor = Green
        )
    ) {
        Text(text, color = Color(0xFF102017), fontSize = 16.sp)
    }
}

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
                if (selected) Green else Color.Transparent,
                RoundedCornerShape(50.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.Black else TextMuted
        )
    }
}

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
            Text(title, color = TextMain, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Green,
                checkedTrackColor = Color(0xFF385B46)
            )
        )
    }
}

@Composable
fun BottomNavigationBar(
    currentPage: Int,
    onPageChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF202820)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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

@Composable
fun NavigationItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) Color(0xFF355D43) else Color.Transparent,
                RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Green else TextMuted,
            fontSize = 15.sp
        )
    }
}

fun detectPlatform(input: String): String {
    val text = input.lowercase()

    return when {
        "douyin.com" in text || "iesdouyin.com" in text -> "抖音"
        "kuaishou.com" in text || "gifshow.com" in text -> "快手"
        "xiaohongshu.com" in text || "xhslink.com" in text -> "小红书"
        "bilibili.com" in text || "b23.tv" in text -> "哔哩哔哩"
        "weibo.com" in text || "weibo.cn" in text -> "微博"
        "ixigua.com" in text -> "西瓜视频"
        "pipix.com" in text -> "皮皮虾"
        "toutiao.com" in text -> "今日头条"
        else -> "未知平台"
    }
}