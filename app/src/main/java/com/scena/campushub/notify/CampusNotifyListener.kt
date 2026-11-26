package com.scena.campushub.notify

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.scena.campushub.Config

/**
 * 通知聚合。
 *
 * 只读取本机已经显示出来的通知（三个校园 App 的），
 * 不逆向对方 App、不需要账号密码、不联网上报。
 */
class CampusNotifyListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (!isTarget(n.packageName)) return
        // 常驻通知（播放器、下载进度等）不记录
        if (n.isOngoing) return

        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence("android.text")
            ?: extras.getCharSequence("android.bigText"))?.toString()?.trim().orEmpty()

        if (title.isBlank() && text.isBlank()) return

        NotifyStore.add(
            applicationContext,
            NotifyStore.Captured(
                pkg = n.packageName,
                appLabel = NotifyHelper.labelOf(applicationContext, n.packageName),
                title = title.ifBlank { text },
                text = if (title.isBlank()) "" else text,
                time = if (n.postTime > 0) n.postTime else System.currentTimeMillis()
            )
        )
    }

    private fun isTarget(pkg: String): Boolean = pkg in Config.NOTIFY_PACKAGES
}
