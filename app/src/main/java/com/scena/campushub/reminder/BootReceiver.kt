package com.scena.campushub.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scena.campushub.data.Store

/** 开机后重排闹钟（重启会丢失已注册的 alarm）。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext
            // 夜间关过机的话，清晨刷新和 07:00 汇总都不会补发，开机正好是唯一补救时机
            if (ReminderStore.isEnabled(app) && !ReminderStore.isCacheFresh(app) &&
                Store(app).canAutoRefresh()
            ) {
                ScheduleRefreshJob.refreshNow(app, notifySummary = true)
            }
            ReminderScheduler.reschedule(app)
        }
    }
}
