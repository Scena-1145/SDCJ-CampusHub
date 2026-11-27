package com.scena.campushub.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scena.campushub.data.Store
import com.scena.campushub.push.Notifications

/** 接收课表闹钟，发出本地通知。不依赖任何推送服务。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext

        // 测试提醒不受开关限制，用于排查链路
        if (intent.action == ACTION_TEST) {
            ReminderStore.clearErrors(ctx)
            Notifications.show(
                ctx,
                "测试提醒成功 ✅",
                "闹钟链路正常：系统能唤醒本应用并弹出通知。\n" +
                    "如果看到这条，说明提醒功能在这台手机上是好的。"
            )
            return
        }

        if (!ReminderStore.isEnabled(ctx)) return

        val courses = ReminderStore.cachedCourses(ctx)
        when (intent.action) {
            ACTION_SUMMARY -> {
                if (!ReminderStore.isCacheFresh(ctx) && Store(ctx).canAutoRefresh()) {
                    // 缓存还是昨天的课表：接口只给当天数据，只能联网补一次，
                    // 这条闹钟已经空跑，由刷新任务回来后补发汇总。
                    ScheduleRefreshJob.refreshNow(ctx, notifySummary = true)
                } else if (courses.isNotEmpty()) {
                    Notifications.show(ctx, "今天 ${courses.size} 门课", ReminderScheduler.summaryText(courses))
                }
                // 今天没课且缓存是今天的 → 不打扰
                ReminderScheduler.reschedule(ctx)
            }

            ACTION_CLASS -> {
                val idx = intent.getIntExtra(EXTRA_INDEX, -1)
                val c = courses.getOrNull(idx) ?: return
                val where = c.room.ifBlank { c.sections }
                Notifications.show(
                    ctx,
                    "15 分钟后 · ${c.name}",
                    listOfNotNull(
                        c.start.takeIf { it.isNotBlank() }?.let { "$it 开始" },
                        where.takeIf { it.isNotBlank() },
                        c.teacher.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                )
            }

            ACTION_TEST -> {
                // 已在方法开头处理
            }
        }
    }

    companion object {
        const val ACTION_SUMMARY = "com.scena.campushub.REMIND_SUMMARY"
        const val ACTION_CLASS = "com.scena.campushub.REMIND_CLASS"
        const val ACTION_TEST = "com.scena.campushub.REMIND_TEST"
        const val EXTRA_INDEX = "course_index"
    }
}
