package com.videox.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// 一条解析历史：保存解析结果本身，点一下即可恢复，不必重新解析
data class ParseHistoryEntry(
    val sourceUrl: String,
    val type: String,
    val title: String,
    val desc: String,
    val author: String,
    val cover: String,
    val videoUrl: String,
    val images: List<String>,
    val musicTitle: String,
    val musicAuthor: String,
    val musicUrl: String,
    val time: Long
)

// 解析历史的持久化存储（SharedPreferences 里存一段 JSON 数组）
// 解析成功后写入，解析页底部读取展示，最多保留 30 条
object HistoryStore {

    private const val PREFS_NAME = "videox_history"

    private const val KEY_ENTRIES = "entries"

    private const val MAX_ENTRIES = 30

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun load(context: Context): List<ParseHistoryEntry> {

        val raw = prefs(context).getString(KEY_ENTRIES, "")
            .orEmpty()

        if (raw.isBlank()) {
            return emptyList()
        }

        return try {

            val array = JSONArray(raw)
            val result = mutableListOf<ParseHistoryEntry>()

            for (i in 0 until array.length()) {

                val obj = array.optJSONObject(i) ?: continue

                val imagesArray = obj.optJSONArray("images")
                val images = mutableListOf<String>()

                if (imagesArray != null) {

                    for (j in 0 until imagesArray.length()) {

                        val img = imagesArray.optString(j, "")

                        if (img.isNotBlank()) {
                            images.add(img)
                        }
                    }
                }

                result.add(
                    ParseHistoryEntry(
                        sourceUrl = obj.optString("sourceUrl", ""),
                        type = obj.optString("type", "video"),
                        title = obj.optString("title", ""),
                        desc = obj.optString("desc", ""),
                        author = obj.optString("author", ""),
                        cover = obj.optString("cover", ""),
                        videoUrl = obj.optString("videoUrl", ""),
                        images = images,
                        musicTitle = obj.optString("musicTitle", ""),
                        musicAuthor = obj.optString("musicAuthor", ""),
                        musicUrl = obj.optString("musicUrl", ""),
                        time = obj.optLong("time", 0L)
                    )
                )
            }

            result

        } catch (e: Exception) {

            emptyList()
        }
    }

    fun add(context: Context, entry: ParseHistoryEntry) {

        val current = load(context)
            .filterNot { it.sourceUrl == entry.sourceUrl }
            .toMutableList()

        current.add(0, entry)

        save(context, current.take(MAX_ENTRIES))
    }

    fun clear(context: Context) {

        prefs(context).edit()
            .remove(KEY_ENTRIES)
            .apply()
    }

    private fun save(
        context: Context,
        entries: List<ParseHistoryEntry>
    ) {

        val array = JSONArray()

        entries.forEach { entry ->

            val imagesArray = JSONArray()

            entry.images.forEach { imagesArray.put(it) }

            val obj = JSONObject()

            obj.put("sourceUrl", entry.sourceUrl)
            obj.put("type", entry.type)
            obj.put("title", entry.title)
            obj.put("desc", entry.desc)
            obj.put("author", entry.author)
            obj.put("cover", entry.cover)
            obj.put("videoUrl", entry.videoUrl)
            obj.put("images", imagesArray)
            obj.put("musicTitle", entry.musicTitle)
            obj.put("musicAuthor", entry.musicAuthor)
            obj.put("musicUrl", entry.musicUrl)
            obj.put("time", entry.time)

            array.put(obj)
        }

        prefs(context).edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }
}
