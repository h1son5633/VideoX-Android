package com.videox.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.view.WindowCompat

import kotlinx.coroutines.launch

import kotlin.math.roundToInt

// 当前是否为深色外观（跟随系统，在 VideoXApp 里同步）
internal val AppDarkState = mutableStateOf(true)

internal val Bg: Color
    get() = if (AppDarkState.value) {
        Color(0xFF101411)
    } else {
        Color(0xFFF3F6F3)
    }

internal val SurfaceGlass: Color
    get() = if (AppDarkState.value) {
        Color(0xFF1C241F)
    } else {
        Color(0xFFFFFFFF)
    }

internal val SurfaceInner: Color
    get() = if (AppDarkState.value) {
        Color(0xFF252E28)
    } else {
        Color(0xFFE6EDE7)
    }

internal val AccentColorState =
    mutableStateOf(SettingsStore.DefaultAccent)

// 底栏外观（启动时从 SettingsStore 恢复，设置页修改时同步更新）
internal val NavStyleState =
    mutableStateOf(SettingsStore.NAV_STYLE_BOARD)

internal val NavBoardColorState =
    mutableStateOf(SettingsStore.DefaultNavBoardColor)

internal val TextMain: Color
    get() = if (AppDarkState.value) {
        Color(0xFFF1F5F2)
    } else {
        Color(0xFF15211A)
    }

internal val TextMuted: Color
    get() = if (AppDarkState.value) {
        Color(0xFF9CAAA1)
    } else {
        Color(0xFF5D6F64)
    }

class MainActivity : ComponentActivity() {

    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 恢复用户上次选择的主题颜色
        AccentColorState.value = SettingsStore.accentColor(this)

        // 恢复底栏外观设置
        NavStyleState.value = SettingsStore.navStyle(this)
        NavBoardColorState.value = SettingsStore.navBoardColor(this)

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

    // 界面外观跟随系统深/浅模式
    AppDarkState.value = isSystemInDarkTheme()

    // 系统状态栏、导航栏与 App 背景同色（沉浸式）
    val activity = LocalContext.current as? Activity

    SideEffect {

        activity?.window?.let { window ->

            window.statusBarColor = Bg.toArgb()
            window.navigationBarColor = Bg.toArgb()

            val controller = WindowCompat.getInsetsController(
                window,
                window.decorView
            )

            controller.isAppearanceLightStatusBars =
                !AppDarkState.value

            controller.isAppearanceLightNavigationBars =
                !AppDarkState.value
        }
    }

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

// 底部导航（两种形态：共享底板的胶囊栏，或三个按钮各自悬浮；点按带震动反馈）
@Composable
fun BottomNavigationBar(
    pagePosition: Float,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {

    val selectedPage = pagePosition
        .roundToInt()
        .coerceIn(0, 2)

    val haptic = LocalHapticFeedback.current

    fun select(page: Int) {

        haptic.performHapticFeedback(
            HapticFeedbackType.LongPress
        )

        onPageChange(page)
    }

    val navItems = listOf(
        Triple("解析", Icons.Filled.Link, 0),
        Triple("下载", Icons.Filled.Download, 1),
        Triple("设置", Icons.Filled.Settings, 2)
    )

    if (NavStyleState.value == SettingsStore.NAV_STYLE_FLOATING) {

        // 无底板：三个按钮各自带一个小胶囊底，直接悬浮在页面上
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            navItems.forEach { (text, icon, page) ->

                val selected = selectedPage == page

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(
                            if (selected) {
                                AccentColorState.value.copy(alpha = 0.30f)
                            } else if (AppDarkState.value) {
                                Color.White.copy(alpha = 0.07f)
                            } else {
                                Color.Black.copy(alpha = 0.05f)
                            },
                            RoundedCornerShape(percent = 50)
                        )
                        .border(
                            1.dp,
                            if (selected) {
                                AccentColorState.value.copy(alpha = 0.45f)
                            } else {
                                Color.Transparent
                            },
                            RoundedCornerShape(percent = 50)
                        )
                        .clickable {
                            select(page)
                        },
                    contentAlignment = Alignment.Center
                ) {

                    NavItemContent(
                        text = text,
                        icon = icon,
                        selected = selected
                    )
                }
            }
        }

        return
    }

    // 有底板：颜色可自定义，或跟随系统深浅
    val boardColor = if (
        NavStyleState.value == SettingsStore.NAV_STYLE_SYSTEM
    ) {
        if (AppDarkState.value) {
            Color(0xFF202820)
        } else {
            Color(0xFFFFFFFF)
        }
    } else {
        NavBoardColorState.value
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (AppDarkState.value) {
                    Color.White.copy(alpha = 0.16f)
                } else {
                    Color.Black.copy(alpha = 0.10f)
                },
                RoundedCornerShape(percent = 50)
            ),
        shape = RoundedCornerShape(percent = 50),
        colors = CardDefaults.cardColors(
            containerColor = boardColor.copy(alpha = 0.95f)
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

                navItems.forEach { (text, icon, page) ->

                    NavigationItem(
                        text = text,
                        icon = icon,
                        selected = selectedPage == page,
                        modifier = Modifier.weight(1f)
                    ) {
                        select(page)
                    }
                }
            }
        }
    }
}

// 导航项目内容（图标 + 文字）
@Composable
fun NavItemContent(
    text: String,
    icon: ImageVector,
    selected: Boolean
) {

    val contentColor = if (selected) {
        AccentColorState.value
    } else {
        TextMuted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
        )

        Spacer(Modifier.width(5.dp))

        Text(
            text,
            color = contentColor,
            fontSize = 15.sp
        )
    }
}

// 导航项目
@Composable
fun NavigationItem(
    text: String,
    icon: ImageVector,
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

        NavItemContent(
            text = text,
            icon = icon,
            selected = selected
        )
    }
}
