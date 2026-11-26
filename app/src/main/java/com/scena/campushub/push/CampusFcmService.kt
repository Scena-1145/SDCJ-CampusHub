package com.scena.campushub.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** GMS / FCM 通道。 */
class CampusFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushStore.saveToken(this, "FCM", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "校园枢纽"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""
        Notifications.show(this, title, body)
    }
}
