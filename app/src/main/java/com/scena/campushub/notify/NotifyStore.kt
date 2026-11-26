package com.scena.campushub.notify

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 被聚合通知的本地存储（最多 60 条）。 */
object NotifyStore {

    private const val SP = "campushub_notify"
    private const val MAX = 60

    data class Captured(
        val pkg: String,
        val appLabel: String,
        val title: String,
        val text: String,
        val time: Long
    ) {
        fun timeText(): String =
            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    @Synchronized
    fun add(ctx: Context, item: Captured) {
        val list = list(ctx).toMutableList()

        // 去重：系统可能对同一条通知重复回调
        val dup = list.any {
            it.pkg == item.pkg && it.title == item.title && it.text == item.text &&
                kotlin.math.abs(it.time - item.time) < 60_000
        }
        if (dup) return

        list.add(0, item)
        val trimmed = if (list.size > MAX) list.subList(0, MAX) else list

        val arr = JSONArray()
        trimmed.forEach {
            arr.put(
                JSONObject()
                    .put("p", it.pkg)
                    .put("a", it.appLabel)
                    .put("t", it.title)
                    .put("x", it.text)
                    .put("m", it.time)
            )
        }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }

    fun list(ctx: Context): List<Captured> {
        val raw = prefs(ctx).getString("items", "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Captured(
                    pkg = o.optString("p"),
                    appLabel = o.optString("a"),
                    title = o.optString("t"),
                    text = o.optString("x"),
                    time = o.optLong("m")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove("items").apply()
    }
}
