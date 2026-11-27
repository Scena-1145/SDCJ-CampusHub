package com.scena.campushub.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.scena.campushub.data.Course
import java.util.Calendar

/**
 * 课表提醒调度。
 *
 * 两类闹钟：
 *   ① 每天 07:00  汇总今天所有课
 *   ② 每节课前 15 分钟  单独提醒
 *
 * 用 setExactAndAllowWhileIdle（API 23+）保证大致准时；
 * Android 12+ 若无精确闹钟权限，降级为 setAndAllowWhileIdle（可能误差几分钟）。
 */
object ReminderScheduler {

    private const val REQ_SUMMARY = 1000
    private const val REQ_TEST = 1500
    private const val REQ_COURSE_BASE = 2000
    private const val MAX_COURSES = 24
    private const val LEAD_MINUTES = 15
    private const val SUMMARY_HOUR = 7

    /** 重排全部闹钟。数据刷新后、开关切换后、开机后都要调用。 */
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        cancelAll(app)
        if (!ReminderStore.isEnabled(app)) {
            ReminderStore.setPlanned(app, emptyList())
            return
        }

        val planned = ArrayList<ReminderStore.Planned>()

        // ① 每日汇总
        val summaryAt = nextSummaryMillis()
        if (setAlarm(app, REQ_SUMMARY, summaryAt, ReminderReceiver.ACTION_SUMMARY, 0)) {
            planned.add(ReminderStore.Planned(fmt(summaryAt), "汇总"))
        }

        // ② 每节课前 15 分钟
        val now = System.currentTimeMillis()
        val cached = ReminderStore.cachedCourses(app)
        cached.forEachIndexed { i, c ->
            // 上限必须生效：cancelAll 只清 REQ_COURSE_BASE 起的 MAX_COURSES 个 code，
            // 超出的闹钟注册了就再也取消不掉。
            if (i >= MAX_COURSES) return@forEachIndexed
            val trigger = classStartMillis(c)?.minus(LEAD_MINUTES * 60_000L) ?: return@forEachIndexed
            if (trigger > now) {
                if (setAlarm(app, REQ_COURSE_BASE + i, trigger, ReminderReceiver.ACTION_CLASS, i)) {
                    planned.add(ReminderStore.Planned(fmt(trigger), c.name))
                }
            }
        }
        if (cached.size > MAX_COURSES) {
            ReminderStore.appendError(app, "课程 ${cached.size} 门超出闹钟上限 $MAX_COURSES，后面的课未注册提醒")
        }

        // ③ 课表接口只返回「当天」，App 不打开就拿不到明天的数据 —— 排一个清晨的联网刷新任务兜底
        ScheduleRefreshJob.scheduleDaily(app)

        ReminderStore.setPlanned(app, planned)
    }

    /** 汇总通知的正文，闹钟链路与后台刷新任务共用。 */
    fun summaryText(courses: List<Course>): String =
        courses.joinToString("\n") { c ->
            val time = c.start.ifBlank { "第${c.sections}节" }
            val place = c.room.ifBlank { "" }
            if (place.isBlank()) "· $time ${c.name}" else "· $time ${c.name} @ $place"
        }

    /** 立即安排一个 N 秒后的测试提醒，用于在真机上验证整条链路。 */
    fun scheduleTest(ctx: Context, seconds: Int): Boolean {
        val app = ctx.applicationContext
        val at = System.currentTimeMillis() + seconds * 1000L
        return setAlarm(app, REQ_TEST, at, ReminderReceiver.ACTION_TEST, 0)
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 注意：PendingIntent 靠 Intent.filterEquals 匹配（比较 action，不比较 extras），
        // 所以取消时必须用与注册时完全相同的 action，否则取消不掉。
        am.cancel(pending(ctx, REQ_SUMMARY, ReminderReceiver.ACTION_SUMMARY, 0))
        am.cancel(pending(ctx, REQ_TEST, ReminderReceiver.ACTION_TEST, 0))
        repeat(MAX_COURSES) { i ->
            am.cancel(pending(ctx, REQ_COURSE_BASE + i, ReminderReceiver.ACTION_CLASS, 0))
        }
        ReminderStore.setPlanned(ctx, emptyList())
    }

    private fun fmt(millis: Long): String =
        java.text.SimpleDateFormat("M-d HH:mm", java.util.Locale.CHINA).format(java.util.Date(millis))

    private fun nextSummaryMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, SUMMARY_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun scheduleDailySummaryRemoved() {
        // 已合并进 reschedule()，保留占位避免误引用
    }

    private fun setAlarm(
        ctx: Context,
        code: Int,
        triggerAt: Long,
        action: String,
        courseIndex: Int
    ): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, code, action, courseIndex)
        return try {
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            true
        } catch (e: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                true
            } catch (e2: Throwable) {
                ReminderStore.appendError(ctx, "setAlarm 失败: ${e2.javaClass.simpleName} ${e2.message}")
                false
            }
        } catch (e: Throwable) {
            ReminderStore.appendError(ctx, "setAlarm 异常: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    private fun pending(ctx: Context, code: Int, action: String, courseIndex: Int): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderReceiver.EXTRA_INDEX, courseIndex)
        }
        return PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Android 12+ 需要用户授予「精确闹钟」权限。 */
    fun canExact(am: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

    fun canExact(ctx: Context): Boolean =
        canExact(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    fun needsExactPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact(ctx)

    /** 跳到系统「闹钟和提醒」授权页（Android 12+）。 */
    fun openExactAlarmSettings(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 把 "08:10" 换算成今天的毫秒时间戳。 */
    private fun classStartMillis(c: Course): Long? {
        val (h, m) = parseHm(c.start) ?: return null
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseHm(s: String): Pair<Int, Int>? {
        val mm = Regex("(\\d{1,2}):(\\d{2})").find(s) ?: return null
        return mm.groupValues[1].toIntOrNull()?.let { h ->
            mm.groupValues[2].toIntOrNull()?.let { m -> h to m }
        }
    }
}
