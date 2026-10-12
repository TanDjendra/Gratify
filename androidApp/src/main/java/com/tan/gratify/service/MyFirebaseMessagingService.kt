package com.tan.gratify.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data["kind"] in setOf("message", "message_request", "story")) {
            SocialNotificationWorker.enqueue(this, remoteMessage.data["event_id"].orEmpty(), remoteMessage.data["owner_id"].orEmpty())
            return
        }
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        if (title == null && body == null) return
        DeveloperNotificationWorker.enqueue(this, title ?: "Gratify", body.orEmpty(),
            remoteMessage.data["url"] ?: remoteMessage.data["browseId"], show = true)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushDeviceRegistration.tokenChanged()
    }
}
