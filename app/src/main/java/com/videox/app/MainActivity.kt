package com.videox.app

import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

internal val Bg = Color(0xFF101411)
internal val SurfaceGlass = Color(0xFF1C241F)
internal val SurfaceInner = Color(0xFF252E28)

internal val AccentColorState =
    mutableStateOf(SettingsStore.DefaultAccent)

internal val TextMain = Color(0xFFF1F5F2)
internal val TextMuted = Color(0xFF9CAAA1)

class MainActivity : ComponentActivity() {

    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 恢复用户上次选择的主题颜色
        AccentColorState.value = SettingsStore.accentColor(this)

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
