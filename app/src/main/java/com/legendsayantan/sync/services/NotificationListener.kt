package com.legendsayantan.sync.services

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.legendsayantan.sync.R
import com.legendsayantan.sync.notihelper.NotificationUtils
import com.legendsayantan.sync.models.ActionStore
import com.legendsayantan.sync.models.NotificationData
import com.legendsayantan.sync.models.NotificationReply
import com.legendsayantan.sync.workers.Network
import com.legendsayantan.sync.workers.Notifications
import com.legendsayantan.sync.workers.Values


class NotificationListener : NotificationListenerService() {
    lateinit var network : Network
    lateinit var sentNotifications : ArrayList<Long>

    override fun onCreate() {
        super.onCreate()
        val notification = Notification.Builder(this, Notifications(applicationContext).server_channel)
            .setContentTitle("Synchronique")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        network = Network(applicationContext)
        sentNotifications = ArrayList()
        println("NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        println("NotificationListener disconnected")
        stopSelf()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if(sbn==null||sentNotifications.contains(sbn.postTime))return@onNotificationPosted
        if (Values.appState == Values.Companion.AppState.CONNECTED && shareNoti) {
            NotificationData(sbn, allowReply).let { notificationData ->
                network.push(notificationData)
                sentNotifications.add(sbn.postTime)
                if (allowReply && (sbn.notification.actions != null) && (sbn.notification.actions.find {
                        it.title.toString().lowercase().trim() == "reply"
                    } != null)) actions.add(
                    ActionStore(
                        NotificationUtils.getQuickReplyAction(
                            sbn.notification,
                            packageName
                        ), sbn.key
                    )
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        println("NotificationListener notification removed")
    }

    companion object {
        var shareNoti: Boolean = false
        var allowReply: Boolean = false
        private val actions = ArrayList<ActionStore>()
        fun sendReplyTo(notificationReply: NotificationReply,applicationContext: Context) {
            println(actions.toArray())
            actions.find { it.key == notificationReply.key }?.action?.sendReply(applicationContext, notificationReply.reply)
        }
    }
}