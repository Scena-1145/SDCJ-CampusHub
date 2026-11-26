package com.scena.campushub.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后重排闹钟（重启会丢失已注册的 alarm）。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            ReminderScheduler.reschedule(context.applicationContext)
        }
    }
}
