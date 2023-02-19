package com.legendsayantan.sync.workers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.legendsayantan.sync.services.ClientService
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * @author legendsayantan
 * This worker handles Audio Stream Payloads
 */
class AudioWorker(var context: Context,var mediaProjection: MediaProjection?,var sampleRate : Int) {
    lateinit var audioRecord: AudioRecord
    private lateinit var transferThread: Thread
    lateinit var audioStream: PipedInputStream
    lateinit var audioOutputStream: PipedOutputStream
    var clientele = object : ArrayList<EndpointInfo>(){
        override fun add(element: EndpointInfo): Boolean {
            val x = super.add(element)
            Nearby.getConnectionsClient(context).sendPayload(element.id,Payload.fromStream(audioStream))
            return x
        }
    }

    init {
        try {
            audioStream = PipedInputStream()
            audioOutputStream = PipedOutputStream(audioStream)
        } catch (_: IOException) { }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startAudioStream() {
        println("start audio stream")
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val audioFormat = AudioFormat.Builder()
            .setEncoding(Values.AUDIO_FORMAT)
            .setSampleRate(sampleRate)
            .setChannelMask(Values.AUDIO_CONFIG)
            .build()


        val BUFFER_SIZE = AudioRecord.getMinBufferSize(sampleRate, Values.AUDIO_CONFIG, Values.AUDIO_FORMAT)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        audioRecord.startRecording()
        var errorCount = 0
        val buffer = ByteArray(BUFFER_SIZE)
        transferThread = Thread {
            while (true) {
                val result = audioRecord.read(buffer, 0, buffer.size)
                try {
                    audioOutputStream.write(buffer, 0, result)
                    errorCount--
                } catch (e: Exception) {
                    errorCount++
                }
                if(errorCount > 10) {
                    Nearby.getConnectionsClient(context).stopAllEndpoints()
                    val notification = Notification.Builder(context, Notifications(context).connection_channel)
                        .setContentTitle("Audio Stream (System)")
                        .setContentText("Connection timed out!")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .build()
                    val manager = (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
                    manager.notify(Values.REQUEST_CODE_CAPTURE_PERM, notification)
                    break
                }
            }
            println("Transfer thread streaming")
        }
        transferThread.start()
    }
    fun startAudioRecord(){
        println("start audio record")
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            Values.AUDIO_CONFIG,
            Values.AUDIO_FORMAT
        )
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            Values.AUDIO_CONFIG,
            Values.AUDIO_FORMAT,
            bufferSize
        )
        audioRecord.startRecording()
        var errorCount = 0
        transferThread = Thread {
            try {
                val buffer = ByteArray(bufferSize)
                while (true) {
                    val result = audioRecord.read(buffer, 0, buffer.size)
                    try {
                        audioOutputStream.write(buffer, 0, result)
                        errorCount--
                    } catch (e: Exception) {
                        errorCount++
                    }
                    if(errorCount > 10) {
                        Nearby.getConnectionsClient(context).stopAllEndpoints()
                        val notification = Notification.Builder(context, Notifications(context).connection_channel)
                            .setContentTitle("Audio Stream (Mic)")
                            .setContentText("Connection timed out!")
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .build()
                        val manager = (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
                        manager.notify(Values.REQUEST_CODE_CAPTURE_PERM, notification)
                        break
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                transferThread.interrupt()
            }
        }
        transferThread.start()
    }
    fun playAudioFromPayload(payload: Payload) {
        val bufferSize = AudioTrack.getMinBufferSize(
            ClientService.clientConfig.audioSample,
            Values.AUDIO_CONFIG,
            Values.AUDIO_FORMAT
        )
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            ClientService.clientConfig.audioSample,
            Values.AUDIO_CONFIG,
            Values.AUDIO_FORMAT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack.play()
        transferThread = Thread {
            try {
                payload.asStream()!!.asInputStream().use { inputStream ->
                    val buffer = ByteArray(bufferSize)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        audioTrack.write(buffer, 0, length)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                transferThread.interrupt()
            }
        }
        transferThread.start()
    }
}