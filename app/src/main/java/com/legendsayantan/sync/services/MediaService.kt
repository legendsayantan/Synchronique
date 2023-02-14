package com.legendsayantan.sync.services

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.MediaPacket
import com.legendsayantan.sync.interfaces.PayloadPacket
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.*


class MediaService : Service() {
    lateinit var SERVICE_ID: String
    var CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
    val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    var count = 0
    var errorCount = 0
    var timer = Timer()
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    lateinit var audioStream: PipedInputStream
    lateinit var audioOutputStream: PipedOutputStream
    lateinit var transferThread: Thread
    private var audioRecord: AudioRecord? = null
    lateinit var mediaPacket: MediaPacket
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        SERVICE_ID = "$packageName.media"
        instance = this



    }
    private fun syncTo(endpointId: String) {
        val mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = OnActiveSessionsChangedListener { mediaControllers ->
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
        println("Synced to.")
    }

    private fun streamMediaTo(endpointId: String) {
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        MainActivity.instance?.startActivityForResult(captureIntent, REQUEST_CODE_CAPTURE_PERM)
        ENDPOINT_ID = endpointId
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun startAudioStream(resultCode: Int, data: Intent?) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            println("Sampling Rate: $SAMPLE_RATE -------------------------------------")
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            try {
                audioStream = PipedInputStream()
                audioOutputStream = PipedOutputStream(audioStream)
            } catch (e: IOException) {
                // Handle exception
                return
            }
            val BUFFER_SIZE = 2 * AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(BUFFER_SIZE)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            audioRecord!!.startRecording()
            val buffer = ByteArray(BUFFER_SIZE)
            transferThread = Thread {
                while (true) {
                    val result = audioRecord!!.read(buffer, 0, buffer.size)
                    try {
                        audioOutputStream.write(buffer, 0, result)
                        errorCount--
                    } catch (e: Exception) {
                        errorCount++
                    }
                    if(errorCount > 10) {
                        Nearby.getConnectionsClient(applicationContext).disconnectFromEndpoint(ENDPOINT_ID)
                        val notification = Notification.Builder(applicationContext, SERVICE_ID)
                            .setContentTitle("Media Service")
                            .setContentText("Connection timed out!")
                            .setOngoing(true)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .build()
                        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        manager.notify(1, notification)
                        ClientService.instance.stopSelf()
                        instance?.stopSelf()
                        break
                    }
                }
            }
            transferThread.start()
            if (true) {

            }
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        }catch (ignored:java.lang.Exception){}
        try{
            audioOutputStream.close()
        }catch (ignored:java.lang.Exception){}
        try{
            audioStream.close()
        }catch (ignored:java.lang.Exception){}
        try{
            transferThread.interrupt()
        }catch (ignored:java.lang.Exception){}
        instance = null
    }
    fun recvMediaSync(m: MediaPacket) {
        mediaPacket = m;
        count = 0;
        timer.cancel()
        timer = Timer()
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = mediaSessionManager.getActiveSessions(ComponentName(applicationContext, NotificationListener::class.java))
        if (controllers.size == 0) {
            val notification = Notification.Builder(applicationContext, SERVICE_ID)
                .setContentTitle("Media Service")
                .setContentText("Play media on this device to sync.")
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
            val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            manager.notify(1, notification)
            return
        }
        val recentController = controllers[0]
        m.timeStamp?.let { recentController.transportControls.seekTo(it) }
        val noti =
            (if (mediaPacket.isPlaying == true) "Playing : " else "Paused : ") + mediaPacket.title
        val noti2 = "${mediaPacket.artist}"
        if (mediaPacket.isPlaying == true) {
            recentController.transportControls.play()
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
        } else {
            recentController.transportControls.pause()
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            )
            sendBroadcast(intent)
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
        val REQUEST_CODE_CAPTURE_PERM = 156
        var instance: MediaService? = null
        var SAMPLE_RATE = 16000
        var Notification_Title: String = "Media Service"
        var streamMode: Boolean = false
        lateinit var ENDPOINT_ID: String
    }
}