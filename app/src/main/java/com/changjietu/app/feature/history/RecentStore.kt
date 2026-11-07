package com.changjietu.app.feature.history

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentItem(
    val uri: String,
    val width: Int,
    val height: Int,
    val sliceCount: Int,
    val timestamp: Long,
)

/**
 * MVP：SharedPreferences 保存最近处理，不做 Room。
 */
class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent_longshots", Context.MODE_PRIVATE)

    fun list(): List<RecentItem> = runCatching {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uri = o.optString("uri")
                if (uri.isBlank()) continue
                add(
                    RecentItem(
                        uri = uri,
                        width = o.optInt("width"),
                        height = o.optInt("height"),
                        sliceCount = o.optInt("sliceCount"),
                        timestamp = o.optLong("timestamp"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList()).sortedByDescending { it.timestamp }

    fun add(uri: Uri, width: Int, height: Int, sliceCount: Int) {
        val items = list().filterNot { it.uri == uri.toString() }.toMutableList()
        items.add(
            0,
            RecentItem(
                uri = uri.toString(),
                width = width,
                height = height,
                sliceCount = sliceCount,
                timestamp = System.currentTimeMillis(),
            ),
        )
        val trimmed = items.take(20)
        val arr = JSONArray()
        for (item in trimmed) {
            arr.put(
                JSONObject()
                    .put("uri", item.uri)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("sliceCount", item.sliceCount)
                    .put("timestamp", item.timestamp),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "items"
    }
}
