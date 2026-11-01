package com.videox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var url by remember { mutableStateOf(sharedText) }
    var platform by remember { mutableStateOf("等待识别") }
    var message by remember { mutableStateOf("粘贴视频链接，开始识别") }

    val background = Color(0xFF101114)
    val cardColor = Color(0xFF1D2026)
    val green = Color(0xFF42D392)

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "VideoX",
                color = green,
                fontSize = 32.sp
            )

            Text(
                text = "全平台视频下载助手",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text(
                        text = "视频链接",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            platform = "等待识别"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("请粘贴视频分享链接")
                        },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = green,
                            unfocusedBorderColor = Color.Gray,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            platform = detectPlatform(url)
                            message = if (platform == "未知平台") {
                                "暂不支持识别该链接"
                            } else {
                                "已识别：$platform"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = green
                        )
                    ) {
                        Text("识别链接", color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "平台：$platform",
                        color = Color.White
                    )

                    Text(
                        text = message,
                        color = green,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "下载任务",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "当前暂无下载任务",
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = green
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "VideoX · Android",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
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
