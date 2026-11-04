package com.videox.app

import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import kotlinx.coroutines.launch

import kotlin.math.roundToInt

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

    // 三个页面装进水平分页容器：点底栏动画平移过去，也能左右滑动切页
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 }
    )

    val scope = rememberCoroutineScope()

    MaterialTheme {

        Scaffold(
            containerColor = Bg,
            bottomBar = {
                BottomNavigationBar(
                    pagePosition = pagerState.currentPage +
                            pagerState.currentPageOffsetFraction,
                    onPageChange = { target ->
                        scope.launch {
                            pagerState.animateScrollToPage(target)
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 10.dp
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

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp
                            )
                    ) {

                        when (page) {
                            0 -> ParsePage(sharedText)
                            1 -> DownloadPage()
                            2 -> SettingsPage()
                        }
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

// 底部导航（悬浮胶囊式：两端正半圆，跟随页面平移位置滑动高亮）
@Composable
fun BottomNavigationBar(
    pagePosition: Float,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {

    val selectedPage = pagePosition
        .roundToInt()
        .coerceIn(0, 2)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(percent = 50)
            ),
        shape = RoundedCornerShape(percent = 50),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF202820).copy(alpha = 0.72f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 10.dp
        )
    ) {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .height(52.dp)
        ) {

            val itemWidth = maxWidth / 3

            Box(
                modifier = Modifier
                    .offset(x = itemWidth * pagePosition)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .background(
                        AccentColorState.value.copy(alpha = 0.30f),
                        RoundedCornerShape(percent = 50)
                    )
                    .border(
                        1.dp,
                        AccentColorState.value.copy(alpha = 0.45f),
                        RoundedCornerShape(percent = 50)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                NavigationItem(
                    text = "解析",
                    selected = selectedPage == 0,
                    modifier = Modifier.weight(1f)
                ) {
                    onPageChange(0)
                }

                NavigationItem(
                    text = "下载",
                    selected = selectedPage == 1,
                    modifier = Modifier.weight(1f)
                ) {
                    onPageChange(1)
                }

                NavigationItem(
                    text = "设置",
                    selected = selectedPage == 2,
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
