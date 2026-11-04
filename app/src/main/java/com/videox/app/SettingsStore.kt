package com.videox.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// 应用设置的持久化存储（SharedPreferences）
// 设置页写入、其他功能读取，保证开关和主题色重启后不丢失且真正生效
object SettingsStore {

    private const val PREFS_NAME = "videox_settings"

    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
    private const val KEY_AUTO_CLIPBOARD = "auto_clipboard"
    private const val KEY_WIFI_ONLY = "wifi_only"

    val DefaultAccent = Color(0xFF62E6A2)

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
}
