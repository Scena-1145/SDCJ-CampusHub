package com.scena.campushub.notify

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** 通知使用权检查与授权跳转。 */
object NotifyHelper {

    /** 用户是否已授予「通知使用权」。 */
    fun isEnabled(ctx: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    } catch (e: Throwable) {
        false
    }

    fun openSettings(ctx: Context): Boolean = try {
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    fun labelOf(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        when (pkg) {
            "com.wisedu.cpdaily" -> "今日校园"
            "com.chaoxing.mobile" -> "学习通"
            else -> "校园平台"
        }
    }
}
