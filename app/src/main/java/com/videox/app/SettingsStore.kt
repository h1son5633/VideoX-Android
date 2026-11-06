package com.videox.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

import java.io.File

// 应用设置的持久化存储（SharedPreferences）
// 设置页写入、其他功能读取，保证开关和主题色重启后不丢失且真正生效
object SettingsStore {

    private const val PREFS_NAME = "videox_settings"

    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
    private const val KEY_AUTO_CLIPBOARD = "auto_clipboard"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_NAV_STYLE = "nav_style"
    private const val KEY_NAV_BOARD_COLOR = "nav_board_color"
    private const val KEY_PARSE_API = "parse_api"
    private const val KEY_VIDEO_DEST = "video_dest"
    private const val KEY_IMAGE_DEST = "image_dest"
    private const val KEY_MUSIC_DEST = "music_dest"

    val DefaultAccent = Color(0xFF62E6A2)

    // 底栏样式：0 = 有底板（自定义颜色），1 = 无底板悬浮，2 = 跟随系统深浅
    const val NAV_STYLE_BOARD = 0
    const val NAV_STYLE_FLOATING = 1
    const val NAV_STYLE_SYSTEM = 2

    val DefaultNavBoardColor = Color(0xFF202820)

    // 下载位置编码：0 = 媒体库/VideoX，1 = 下载目录，2 = 下载目录/VideoX，3 = DCIM/VideoX
    const val DEST_MEDIA = 0
    const val DEST_DOWNLOADS = 1
    const val DEST_DOWNLOADS_VIDEOX = 2
    const val DEST_DCIM = 3

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun accentColor(context: Context): Color {
        val argb = prefs(context).getInt(
            KEY_ACCENT_COLOR,
            DefaultAccent.toArgb()
        )
        return Color(argb)
    }

    fun setAccentColor(context: Context, color: Color) {
        prefs(context).edit()
            .putInt(KEY_ACCENT_COLOR, color.toArgb())
            .apply()
    }

    fun saveToGallery(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SAVE_TO_GALLERY, true)
    }

    fun setSaveToGallery(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SAVE_TO_GALLERY, value)
            .apply()
    }

    fun autoClipboard(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_CLIPBOARD, false)
    }

    fun setAutoClipboard(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_CLIPBOARD, value)
            .apply()
    }

    fun wifiOnly(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, false)
    }

    fun setWifiOnly(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_WIFI_ONLY, value)
            .apply()
    }

    // ===== 底栏外观 =====

    fun navStyle(context: Context): Int {
        // 默认“跟随系统”：底板颜色随系统深/浅模式（用户 2026-11-06 指定）
        return prefs(context).getInt(KEY_NAV_STYLE, NAV_STYLE_SYSTEM)
    }

    fun setNavStyle(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_NAV_STYLE, value)
            .apply()
    }

    fun navBoardColor(context: Context): Color {
        val argb = prefs(context).getInt(
            KEY_NAV_BOARD_COLOR,
            DefaultNavBoardColor.toArgb()
        )
        return Color(argb)
    }

    fun setNavBoardColor(context: Context, color: Color) {
        prefs(context).edit()
            .putInt(KEY_NAV_BOARD_COLOR, color.toArgb())
            .apply()
    }

    // ===== 解析接口 =====

    // 解析接口编号：1 = 接口一（推荐），2 = 接口二，3 = 接口三
    const val PARSE_API_1 = 1
    const val PARSE_API_2 = 2
    const val PARSE_API_3 = 3

    fun parseApi(context: Context): Int {
        return prefs(context).getInt(KEY_PARSE_API, PARSE_API_1)
    }

    fun setParseApi(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_PARSE_API, value)
            .apply()
    }

    // ===== 下载位置 =====

    private fun destKey(kind: String): String {
        return when (kind) {
            "video" -> KEY_VIDEO_DEST
            "image" -> KEY_IMAGE_DEST
            else -> KEY_MUSIC_DEST
        }
    }

    private fun mediaDirType(kind: String): String {
        return when (kind) {
            "video" -> Environment.DIRECTORY_MOVIES
            "image" -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_MUSIC
        }
    }

    // 读取某一类文件的下载位置编码；从未设置过时按旧“保存到相册”开关迁移
    fun destCode(context: Context, kind: String): Int {

        val stored = prefs(context).getInt(
            destKey(kind),
            Int.MIN_VALUE
        )

        if (stored != Int.MIN_VALUE) {
            return stored
        }

        return if (saveToGallery(context)) {
            DEST_MEDIA
        } else {
            DEST_DOWNLOADS
        }
    }

    fun setDestCode(context: Context, kind: String, code: Int) {
        prefs(context).edit()
            .putInt(destKey(kind), code)
            .apply()
    }

    // 返回 (公共目录类型, 子路径前缀)，供 DownloadManager 设置目标位置
    fun downloadDest(
        context: Context,
        kind: String
    ): Pair<String, String> {

        return when (destCode(context, kind)) {

            DEST_DOWNLOADS ->
                Environment.DIRECTORY_DOWNLOADS to ""

            DEST_DOWNLOADS_VIDEOX ->
                Environment.DIRECTORY_DOWNLOADS to "VideoX/"

            DEST_DCIM ->
                Environment.DIRECTORY_DCIM to "VideoX/"

            else ->
                mediaDirType(kind) to "VideoX/"
        }
    }

    // 当前下载位置的完整路径（设置页展示用）
    fun destDisplayPath(context: Context, kind: String): String {

        val (dirType, subPrefix) = downloadDest(context, kind)

        val base = Environment
            .getExternalStoragePublicDirectory(dirType)
            .absolutePath

        return if (subPrefix.isBlank()) {
            base
        } else {
            base + File.separator + "VideoX"
        }
    }

    // App 数据文件夹路径（设置页展示用）
    fun dataDirPath(context: Context): String {
        return context.filesDir.parentFile?.absolutePath
            ?: context.filesDir.absolutePath
    }
}
