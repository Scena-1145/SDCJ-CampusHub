package com.scena.campushub.reminder

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import com.scena.campushub.data.CampusApi
import com.scena.campushub.data.LysoftClient
import com.scena.campushub.data.Store
import com.scena.campushub.push.Notifications
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * 后台刷新课表并重排当天提醒。
 *
 * 存在的理由：`getSchedule` 忽略参数、只返回**当天**课表，所以 App 不打开就永远拿不到
 * 明天的数据，课前 15 分钟提醒会在跨天后静默消失。每天清晨联网补一次，
 * 让提醒链路不再依赖「用户今天记得打开 App」。
 *
 * 只用框架自带的 JobScheduler（不引 WorkManager），要求有网、可跨重启。
 * 走的是已保存的会话，与用户手动打开 App 时刷新的是同一件事。
 */
class ScheduleRefreshJob : JobService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters?): Boolean {
        // API 23 没有 setExtras，而那条路径只可能是 refreshNow，回来一律补发通知
        // 清晨那条不许说话（05:30 人还在睡），只有 07:00 补救那条会带 notify_summary
        val notifySummary =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                params?.extras?.getBoolean(KEY_NOTIFY, false) ?: false
            else true
        exec.execute {
            try {
                doWork(applicationContext, notifySummary)
            } finally {
                main.post { jobFinished(params, false) }
            }
        }
        return true
    }

    /** 被系统提前掐掉时返回 true：还有课要提醒，稍后重试。 */
    override fun onStopJob(params: JobParameters?): Boolean = true

    private fun doWork(ctx: Context, notifySummary: Boolean) {
        val store = Store(ctx)
        if (!store.canAutoRefresh()) {
            ReminderStore.appendError(ctx, "跳过自动刷新：没有可用的账号密码")
            return
        }
        val api = CampusApi(ctx)
        val courses = try {
            var s = store.loadSession()
                ?: api.login(store.userId, store.password).also { store.saveSession(it) }
            try {
                api.todayCourses(s)
            } catch (e: LysoftClient.ApiException) {
                if (!LysoftClient.isSessionDead(e.code)) throw e
                s = api.login(store.userId, store.password)
                store.saveSession(s)
                api.todayCourses(s)
            }
        } catch (t: Throwable) {
            ReminderStore.appendError(
                ctx, "自动刷新课表失败: ${t.javaClass.simpleName} ${t.message}"
            )
            return
        }
        ReminderStore.cacheCourses(ctx, courses)
        ReminderScheduler.reschedule(ctx)
        if (notifySummary && ReminderStore.isEnabled(ctx) && courses.isNotEmpty()) {
            Notifications.show(ctx, "今天 ${courses.size} 门课", ReminderScheduler.summaryText(courses))
        }
    }

    companion object {
        private const val JOB_DAILY = 41
        private const val JOB_HEAL = 42
        private const val KEY_NOTIFY = "notify_summary"

        /** 清晨跑，赶在早课（一般 08:00 起）的课前提醒之前。 */
        private const val REFRESH_HOUR = 5
        private const val REFRESH_MINUTE = 30

        /** 弹性窗口：系统可以在 05:30–06:30 之间挑省电的时机。 */
        private const val DAILY_WINDOW_MS = 60 * 60_000L
        private const val HEAL_DEADLINE_MS = 15 * 60_000L

        private val exec = Executors.newSingleThreadExecutor()

        /** 排下一次清晨刷新。同 jobId 重复提交即覆盖，所以每次 reschedule 都可以放心调。 */
        fun scheduleDaily(ctx: Context) {
            // API 23 的 JobScheduler 既不能定时也不能跨重启保留，退化为「打开 App 才刷」
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val wait = millisUntilNext(REFRESH_HOUR, REFRESH_MINUTE)
            submit(ctx, builder(ctx, JOB_DAILY)
                .setMinimumLatency(wait)
                .setOverrideDeadline(wait + DAILY_WINDOW_MS)
                .setPersisted(true)
                .build())
        }

        /**
         * 立刻补一次刷新（缓存还是昨天的课表时调用）。
         * @param notifySummary 刷新成功后补发汇总通知 —— 07:00 那条闹钟已经空跑了。
         */
        fun refreshNow(ctx: Context, notifySummary: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                submit(ctx, builder(ctx, JOB_HEAL).build())
                return
            }
            submit(ctx, builder(ctx, JOB_HEAL)
                .setMinimumLatency(1_000L)
                .setOverrideDeadline(HEAL_DEADLINE_MS)
                .setPersisted(true)
                .setExtras(PersistableBundle().apply { putBoolean(KEY_NOTIFY, notifySummary) })
                .build())
        }

        fun cancel(ctx: Context) {
            (ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler)
                ?.apply { cancel(JOB_DAILY); cancel(JOB_HEAL) }
        }

        /**
         * 同一个 jobId 再 schedule 一次即覆盖，天然幂等（`setReplaceCurrent` 不是公开 API）。
         * NETWORK_TYPE_ANY = 要求有网；23 上也没有 CONNECTED 这个取值，故不做版本分支。
         */
        private fun builder(ctx: Context, id: Int) =
            JobInfo.Builder(id, ComponentName(ctx, ScheduleRefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

        private fun submit(ctx: Context, info: JobInfo) {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (js.schedule(info) != JobScheduler.RESULT_SUCCESS) {
                ReminderStore.appendError(ctx, "课表刷新任务入队失败")
            }
        }

        private fun millisUntilNext(hour: Int, minute: Int): Long {
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            return (target - System.currentTimeMillis()).coerceAtLeast(60_000L)
        }
    }
}
