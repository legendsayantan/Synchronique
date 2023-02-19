package com.legendsayantan.sync.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * @author legendsayantan
 */
class Notifications(context: Context) {
    var wait_channel : String
    var lookup_channel : String
    var connection_channel : String
    var server_channel : String
    var client_channel : String
    var controls_channel: String

    var wait : NotificationChannel
    var lookup : NotificationChannel
    var connection : NotificationChannel
    var server : NotificationChannel
    var client : NotificationChannel
    var controls : NotificationChannel
    init {
        wait_channel = "${context.packageName}.wait"
        lookup_channel = "${context.packageName}.lookup"
        connection_channel = "${context.packageName}.connection"
        server_channel = "${context.packageName}.server"
        client_channel = "${context.packageName}.client"
        controls_channel = "${context.packageName}.controls"

        wait = NotificationChannel(wait_channel, "Ready for connection", NotificationManager.IMPORTANCE_LOW)
        lookup = NotificationChannel(lookup_channel, "Discover", NotificationManager.IMPORTANCE_NONE)
        connection = NotificationChannel(connection_channel, "Connection", NotificationManager.IMPORTANCE_HIGH)
        server = NotificationChannel(server_channel, "Synchronique server", NotificationManager.IMPORTANCE_LOW)
        client = NotificationChannel(client_channel, "Synchronique client", NotificationManager.IMPORTANCE_LOW)
        controls = NotificationChannel(controls_channel, "Controls", NotificationManager.IMPORTANCE_HIGH)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(wait)
        notificationManager.createNotificationChannel(lookup)
        notificationManager.createNotificationChannel(connection)
        notificationManager.createNotificationChannel(server)
        notificationManager.createNotificationChannel(client)
        notificationManager.createNotificationChannel(controls)
    }
}