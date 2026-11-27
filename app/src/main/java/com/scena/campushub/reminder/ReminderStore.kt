package com.scena.campushub.reminder

import android.content.Context
import com.scena.campushub.data.Course
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 课表提醒的本地状态：开关 + 今日课表缓存 + 已注册闹钟清单 + 错误记录。
 *
 * 缓存是必需的 —— AlarmReceiver 在 App 未运行时也要能拿到课表内容。
 * 「已注册闹钟清单」用于自检：AlarmManager 没有查询自身闹钟的 API，
 * 所以只能自己记账。
 */
object ReminderStore {

    private const val SP = "campushub_reminder"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    data class Planned(val time: String, val label: String)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("enabled", on).apply()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    // ---------------------------------------------------------------- 已注册清单

    fun setPlanned(ctx: Context, list: List<Planned>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("l", it.label)) }
        prefs(ctx).edit()
            .putString("planned", arr.toString())
            .putLong("planned_at", System.currentTimeMillis())
            .apply()
    }

    fun planned(ctx: Context): List<Planned> {
        val raw = prefs(ctx).getString("planned", "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Planned(o.optString("t"), o.optString("l"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun plannedAt(ctx: Context): Long = prefs(ctx).getLong("planned_at", 0L)

    // ---------------------------------------------------------------- 错误记录

    fun appendError(ctx: Context, msg: String) {
        val p = prefs(ctx)
        val old = p.getString("errors", "") ?: ""
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        val line = "[$stamp] $msg"
        val merged = (old.lines().filter { it.isNotBlank() } + line).takeLast(8).joinToString("\n")
        p.edit().putString("errors", merged).apply()
    }

    fun errors(ctx: Context): String = prefs(ctx).getString("errors", "") ?: ""

    fun clearErrors(ctx: Context) {
        prefs(ctx).edit().remove("errors").apply()
    }

    // ---------------------------------------------------------------- 课表缓存

    /** 写入今日课表缓存（只在同一天内有效）。 */
    fun cacheCourses(ctx: Context, courses: List<Course>) {
        val arr = JSONArray()
        courses.forEach { c ->
            arr.put(
                JSONObject()
                    .put("name", c.name)
                    .put("start", c.start)
                    .put("end", c.end)
                    .put("room", c.room)
                    .put("teacher", c.teacher)
                    .put("sections", c.sections)
            )
        }
        prefs(ctx).edit()
            .putString("courses", arr.toString())
            .putString("courses_date", todayKey())
            .apply()
    }

    /** 读今日课表缓存；缓存的不是今天的则返回空。 */
    fun cachedCourses(ctx: Context): List<Course> {
        val p = prefs(ctx)
        if (p.getString("courses_date", "") != todayKey()) return emptyList()
        val raw = p.getString("courses", "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Course(
                    name = o.optString("name"),
                    start = o.optString("start"),
                    end = o.optString("end"),
                    room = o.optString("room"),
                    teacher = o.optString("teacher"),
                    sections = o.optString("sections")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cachedDate(ctx: Context): String =
        prefs(ctx).getString("courses_date", "") ?: ""

    /** 缓存是不是今天的。空缓存和「昨天的缓存」都算过期。 */
    fun isCacheFresh(ctx: Context): Boolean = cachedDate(ctx) == todayKey()

    /** 退出登录时调用：本地课表来自账号会话，不该留着继续驱动提醒。 */
    fun clearCourses(ctx: Context) {
        prefs(ctx).edit().remove("courses").remove("courses_date").apply()
    }

    fun todayKeyPublic(): String = todayKey()
}

