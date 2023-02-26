package com.legendsayantan.sync.workers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.EndpointInfo
import com.legendsayantan.sync.models.MediaActionPacket
import com.legendsayantan.sync.models.MediaSyncPacket
import com.legendsayantan.sync.models.PayloadPacket
import com.legendsayantan.sync.services.NotificationListener
import com.legendsayantan.sync.services.ServerService
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author legendsayantan
 */
/**
 * This worker handles MEDIA_SYNC_PACKET and MEDIA_ACTION_PACKET
 */
class MediaWorker(var context: Context) {
    private var network : Network = Network(context)
    var count = 0
    var timer = Timer()
    fun startMediaControls() {
        val playPauseIntent = Intent(context, ServerService::class.java)
        playPauseIntent.action = "play"
        val playPausePendingIntent =
            PendingIntent.getService(context, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(context, ServerService::class.java)
        prevIntent.action = "prev"
        val prevPendingIntent =
            PendingIntent.getService(context, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(context, ServerService::class.java)
        nextIntent.action = "next"
        val nextPendingIntent =
            PendingIntent.getService(context, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE)

// Create the notification with media playback controls
        val notification =
            NotificationCompat.Builder(context, Notifications(context).controls_channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Media Controls")
                .setOngoing(true)
                .addAction(R.drawable.baseline_skip_previous_24, "Previous", prevPendingIntent)
                .addAction(R.drawable.baseline_play_arrow_24, "Play/Pause", playPausePendingIntent)
                .addAction(R.drawable.baseline_skip_next_24, "Next", nextPendingIntent)
                .build()

// Show the notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(5, notification)
        }
    }

    fun onMediaAction(action: String) {
        val actionType = when (action) {
            "play" -> MediaActionPacket.Companion.Action.MEDIA_PLAY_PAUSE
            "prev" -> MediaActionPacket.Companion.Action.MEDIA_PREV
            "next" -> MediaActionPacket.Companion.Action.MEDIA_NEXT
            else -> null
        } ?: return

        val mediaActionPacket = MediaActionPacket(
            actionType, (System.currentTimeMillis() + 1000)
        )
        network.push(mediaActionPacket)
        if(!Values(context).mediaClientOnly){
            recvMediaAction(mediaActionPacket)
        }
    }

    fun sendMediaSync(controller: MediaController) {
        val mediaMetadata = controller.metadata
        if (mediaMetadata != null) {
            val title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val duration = mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val position = controller.playbackState!!.position
            val isPlaying = controller.playbackState!!.state == PlaybackState.STATE_PLAYING

            println("Title: $title")
            println("Artist: $artist")
            println("Duration: $duration")
            println("Current Timestamp: $position")
            println("Is Playing: $isPlaying")
            val m = MediaSyncPacket(title, artist, position, duration, isPlaying)
            network.push(m)
        }
    }

    fun syncTo() {
        val mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { mediaControllers ->
            if (mediaControllers != null) {
                for (controller in mediaControllers) {
                    val mediaControllerCallback = object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            super.onMetadataChanged(metadata)
                            sendMediaSync(controller)
                        }

                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            super.onPlaybackStateChanged(state)
                            sendMediaSync(controller)
                        }
                    }
                    controller.registerCallback(mediaControllerCallback)
                }
            }
        }
        val sessions = mediaSessionManager.getActiveSessions(
            ComponentName(
                context,
                NotificationListener::class.java
            )
        )
        for (controller in sessions) {
            val mediaControllerCallback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    super.onMetadataChanged(metadata)
                    sendMediaSync(controller)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    sendMediaSync(controller)

                }
            }
            controller.registerCallback(mediaControllerCallback)
        }
        mediaSessionManager.addOnActiveSessionsChangedListener(
            listener,
            ComponentName(context, NotificationListener::class.java)
        )
        println("Synced to.")
    }

    fun recvMediaAction(mediaActionPacket: MediaActionPacket) {
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = mediaSessionManager.getActiveSessions(
            ComponentName(
                context,
                NotificationListener::class.java
            )
        )
        if (controllers.size == 0) {
            val notification = Notification.Builder(context, Notifications(context).client_channel)
                .setContentTitle("Media Sync")
                .setContentText("Play media on this device to sync.")
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
            val manager =
                (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
            manager.notify(1, notification)
            return
        }
        val recentController = controllers[0]
        Timer().schedule(object : TimerTask() {
            override fun run() {
                when(mediaActionPacket.action){
                    MediaActionPacket.Companion.Action.MEDIA_PLAY_PAUSE -> {
                        if(recentController.playbackState!!.state == PlaybackState.STATE_PLAYING) recentController.transportControls.pause()
                        else recentController.transportControls.play()
                    }
                    MediaActionPacket.Companion.Action.MEDIA_PREV -> recentController.transportControls.skipToPrevious()
                    MediaActionPacket.Companion.Action.MEDIA_NEXT -> recentController.transportControls.skipToNext()
                }
            }
        }, Date(mediaActionPacket.executeAt))
    }

    fun recvMediaSync(mediaSyncPacket: MediaSyncPacket) {
        count = 0;
        timer.cancel()
        timer = Timer()
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = mediaSessionManager.getActiveSessions(
            ComponentName(
                context,
                NotificationListener::class.java
            )
        )
        if (controllers.size == 0) {
            val notification = Notification.Builder(context, Notifications(context).client_channel)
                .setContentTitle("Media Sync")
                .setContentText("Play media on this device to sync.")
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
            val manager =
                (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
            manager.notify(3, notification)
            return
        }
        val recentController = controllers[0]
        if(clientSyncToServer)mediaSyncPacket.timeStamp?.let { recentController.transportControls.seekTo(it) }
        val noti =
            (if (mediaSyncPacket.isPlaying == true) "Playing : " else "Paused : ") + mediaSyncPacket.title
        val noti2 = "${mediaSyncPacket.artist}"
        if (mediaSyncPacket.isPlaying == true) {
            if(clientSyncToServer)recentController.transportControls.play()
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (count * 1000 >= mediaSyncPacket.duration?.toInt()!! || mediaSyncPacket.isPlaying == false) {
                        this.cancel()
                        timer.cancel()
                        count = 0
                        return
                    }
                    val notification =
                        Notification.Builder(context, Notifications(context).client_channel)
                            .setContentTitle(noti)
                            .setContentText(noti2)
                            .setSubText(Notification_Title)
                            .setProgress(
                                mediaSyncPacket.duration?.toInt()!!,
                                (mediaSyncPacket.timeStamp?.toInt()!!) + (count * 1000),
                                false
                            )
                            .setOngoing(true)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .build()
                    val manager =
                        (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
                    manager.notify(1, notification)
                    count++
                }
            }, 3, 1000)
        } else {
            if(clientSyncToServer){
                recentController.transportControls.pause()
                val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                intent.putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                )
                context.sendBroadcast(intent)
            }
            val notification = Notification.Builder(context, Notifications(context).client_channel)
                .setContentTitle(noti)
                .setContentText(noti2)
                .setSubText(Notification_Title)
                .setProgress(
                    mediaSyncPacket.duration?.toInt()!!,
                    (mediaSyncPacket.timeStamp?.toInt()!!),
                    false
                )
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
            val manager =
                (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
            manager.notify(1, notification)
        }
    }
    companion object{
        var Notification_Title: String = "Media Service"
        var clientSyncToServer:Boolean = true
    }

}