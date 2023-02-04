package com.legendsayantan.sync.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.MediaPacket
import com.legendsayantan.sync.interfaces.PayloadPacket
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*


class MediaService : Service() {

    lateinit var SERVICE_ID: String

    var noticount = 0;
    var count = 0
    var timer = Timer()
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    private lateinit var audioCaptureThread: Thread
    private var audioRecord: AudioRecord? = null
    lateinit var mediaPacket: MediaPacket
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        SERVICE_ID = "$packageName.media"
        instance = this
        val chan = NotificationChannel(
            SERVICE_ID,
            "Media",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        chan.setSound(null, null)

        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
        val notification = Notification.Builder(this, SERVICE_ID)
            .setContentTitle(Notification_Title)
            .setContentText(Notification_Text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        if (SingularConnectionService.CONNECTED) {
            if (SingularConnectionService.CONNECTION_MODE == SingularConnectionService.Companion.ConnectionMode.INITIATE) {

            } else if(streamMode){

            }else {
                syncTo(SingularConnectionService.ENDPOINT_ID)
            }
        }
    }

    private fun syncTo(endpointId: String) {
        val mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = OnActiveSessionsChangedListener { mediaControllers ->
            println("listener invoke")
            if (mediaControllers != null) {
                for (controller in mediaControllers) {
                    val mediaControllerCallback = object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            super.onMetadataChanged(metadata)
                            sendMediaSync(controller, endpointId)
                        }
                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            super.onPlaybackStateChanged(state)
                            sendMediaSync(controller, endpointId)
                        }
                    }
                    controller.registerCallback(mediaControllerCallback)
                }
            }
        }
        val sessions = mediaSessionManager.getActiveSessions(
            ComponentName(
                applicationContext,
                NotificationListener::class.java
            )
        )
        for (controller in sessions) {
            val mediaControllerCallback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    super.onMetadataChanged(metadata)
                    sendMediaSync(controller, endpointId)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    sendMediaSync(controller, endpointId)

                }
            }
            controller.registerCallback(mediaControllerCallback)
        }
        mediaSessionManager.addOnActiveSessionsChangedListener(
            listener,
            ComponentName(this, NotificationListener::class.java)
        )
        println("Streaming to.")
    }

    private fun StreamMediaTo(endpointId: String){




    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioStream() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(8000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()


            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                // For optimal performance, the buffer size
                // can be optionally specified to store audio samples.
                // If the value is not specified,
                // uses a single frame and lets the
                // native code figure out the minimum buffer size.
                .setBufferSizeInBytes(BUFFER_SIZE_IN_BYTES)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            audioRecord!!.startRecording()

            val buffer = ByteArray(BUFFER_SIZE_IN_BYTES)

            val recordingThread = Thread {
                while (true) {
                    val result: Int = audioRecord!!.read(buffer, 0, buffer.size)
                    if (result < 0) {
                        // Handle error
                        break
                    }
                    val audioStream: InputStream = ByteArrayInputStream(buffer, 0, result)
                    val payload: Payload = Payload.fromStream(audioStream)
                    Nearby.getConnectionsClient(this).sendPayload(
                        SingularConnectionService.ENDPOINT_ID,
                        payload
                    )
                }
            }
            recordingThread.start()
        }

    }

    fun sendMediaSync(controller: MediaController, endpointId: String) {
        println("sendMedia invoke")
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
            val m = MediaPacket(title, artist, position, duration, isPlaying)
            val p = Payload.fromBytes(
                PayloadPacket.toEncBytes(
                    PayloadPacket(
                        PayloadPacket.Companion.PayloadType.MEDIA_PACKET, m
                    )
                )
            )
            Nearby.getConnectionsClient(applicationContext).sendPayload(endpointId, p);
        }
    }

    fun recvMediaSync(m: MediaPacket) {
        mediaPacket = m;
        count = 0;
        timer.cancel()
        timer = Timer()
        val noti = (if (mediaPacket.isPlaying == true) "Playing : " else "Paused : ") + mediaPacket.title
        val noti2 = "${mediaPacket.artist}"
        if (mediaPacket.isPlaying == true) {
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (count * 1000 >= mediaPacket.duration?.toInt()!! || mediaPacket.isPlaying == false) {
                        this.cancel()
                        timer.cancel()
                        count = 0
                        return
                    }
                    val notification = Notification.Builder(applicationContext, SERVICE_ID)
                        .setContentTitle(noti)
                        .setContentText(noti2)
                        .setSubText(Notification_Title)
                        .setProgress(
                            mediaPacket.duration?.toInt()!!,
                            (mediaPacket.timeStamp?.toInt()!!) + (count * 1000),
                            false
                        )
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .build()
                    val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    manager.notify(1, notification)
                    count++
                }
            }, 0, 1000)
        }
        else{
            val notification = Notification.Builder(applicationContext, SERVICE_ID)
                .setContentTitle(noti)
                .setContentText(noti2)
                .setSubText(Notification_Title)
                .setProgress(
                    mediaPacket.duration?.toInt()!!,
                    (mediaPacket.timeStamp?.toInt()!!),
                    false
                )
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
            val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            manager.notify(1, notification)
        }
    }

    companion object {
        lateinit var instance: MediaService
        var Notification_Text: String =
            "Streaming " + if (SingularConnectionService.CONNECTION_MODE == SingularConnectionService.Companion.ConnectionMode.ACCEPT) "to ${SingularConnectionService.ENDPOINT_NAME}" else "from ${SingularConnectionService.ENDPOINT_NAME}"
        var Notification_Title: String = "Media Service"
        var streamMode : Boolean = false

        private const val LOG_TAG = "AudioCaptureService"
        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "AudioCapture channel"

        private const val NUM_SAMPLES_PER_READ = 1024
        private const val BYTES_PER_SAMPLE = 2 // 2 bytes since we hardcoded the PCM 16-bit format
        private const val BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE

        const val ACTION_START = "AudioCaptureService:Start"
        const val ACTION_STOP = "AudioCaptureService:Stop"
        const val EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData"
    }

}