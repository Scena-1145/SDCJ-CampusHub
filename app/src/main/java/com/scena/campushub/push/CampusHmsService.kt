package com.scena.campushub.push

import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

/** HMS Core 通道（华为 / 荣耀设备）。 */
class CampusHmsService : HmsMessageService() {

    override fun onNewToken(token: String?, bundle: Bundle?) {
        if (!token.isNullOrBlank()) {
            PushStore.saveToken(this, "HMS", token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage?) {
        message ?: return
        val notification = message.notification
        val data = message.dataOfMap ?: emptyMap()
        val title = notification?.title ?: data["title"] ?: "校园枢纽"
        val body = notification?.body ?: data["body"] ?: ""
        Notifications.show(this, title, body)
    }
}
