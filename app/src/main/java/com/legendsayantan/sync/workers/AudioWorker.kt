package com.legendsayantan.sync.workers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.Virtualizer
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.AudioBufferPacket
import com.legendsayantan.sync.models.EndpointInfo
import com.legendsayantan.sync.models.PayloadPacket
import com.legendsayantan.sync.models.SocketEndpointInfo
import com.legendsayantan.sync.services.ClientService
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * @author legendsayantan
 */
/**
 * This worker handles Audio Stream Payloads
 */
class AudioWorker(
    var context: Context,
    var mediaProjection: MediaProjection?,
    var sampleRate: Int
) {
    lateinit var audioRecord: AudioRecord
    private var transferThread: Thread? = null
    lateinit var audioStream: PipedInputStream
    lateinit var audioOutputStream: PipedOutputStream
    var clientele = object : ArrayList<EndpointInfo>() {
        override fun add(element: EndpointInfo): Boolean {
            val x = super.add(element)
            if (element is SocketEndpointInfo) {

            } else {
                Nearby.getConnectionsClient(context)
                    .sendPayload(element.id, Payload.fromStream(audioStream))
            }
            return x
        }
    }
    init {
        try {
            audioStream = PipedInputStream()
            audioOutputStream = PipedOutputStream(audioStream)
        } catch (_: IOException) {
        }
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

        val bufferSizeInBytes =
            2 * AudioRecord.getMinBufferSize(sampleRate, Values.AUDIO_CONFIG, Values.AUDIO_FORMAT)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        audioRecord.startRecording()
        var errorCount = 0
        val buffer = ByteArray(bufferSizeInBytes)
        transferThread = Thread {
            while (true) {
                val result = audioRecord.read(buffer, 0, buffer.size)
                try {
                    audioOutputStream.write(buffer, 0, result)
                    sendAudioSocketBuffer(buffer)
                    errorCount--
                } catch (e: Exception) {
                    errorCount++
                    if (errorCount > 10) {
                        try {
                            audioStream = PipedInputStream()
                            audioOutputStream = PipedOutputStream(audioStream)
                        } catch (_: IOException) {
                        }
                    }
                }
            }
        }
        transferThread?.start()
    }

    fun startAudioRecord() {
        println("start audio record")
        val bufferSize = 2 * AudioRecord.getMinBufferSize(
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
                        sendAudioSocketBuffer(buffer)
                        errorCount--
                    } catch (e: Exception) {
                        errorCount++
                        if (errorCount > 10) {
                            try {
                                audioStream = PipedInputStream()
                                audioOutputStream = PipedOutputStream(audioStream)
                            } catch (_: IOException) {
                            }
                        }
                    }

                }
            } catch (e: IOException) {
                e.printStackTrace()
                this.transferThread?.interrupt()
            }
        }
        transferThread?.start()
    }
    private fun sendAudioSocketBuffer(data: ByteArray) = if (Values(context).socket) {
        Values.connectedSocketClients.forEach {
            it.senderThread.push(
                PayloadPacket.toJsonString(
                    PayloadPacket(
                        PayloadPacket.Companion.PayloadType.AUDIO_BUFFER,
                        AudioBufferPacket(data)
                    )
                )
            )
        }
    } else { }
    fun playAudioFromPayload(payload: Payload) {
        val inputStream = payload.asStream()!!.asInputStream()
        playAudioFromInputStream(inputStream)
    }

    fun initialiseSocketAudioPlayer(){
        playAudioFromInputStream(audioStream)
    }
    fun recvAudioBufferPacket(audioBufferPacket: AudioBufferPacket){
        try {
            audioOutputStream.write(audioBufferPacket.data)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if(transferThread!=null){
            initialiseSocketAudioPlayer()
        }
    }
    private fun playAudioFromInputStream(inputStream: InputStream){
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
        val buffer = ByteArray(bufferSize)
        var length: Int
        val virtualizer = Virtualizer(0, audioTrack.audioSessionId)
        audioTrack.play()
        //using transferThread as audio player thread
        transferThread = Thread {
            try {
                while (inputStream.read(buffer).also { length = it } > 0) {
                    audioTrack.write(buffer, 0, length)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        onVolumeChanged = {
            audioTrack.setVolume(volume / 100f)
            if (it > 100) {
                virtualizer.enabled = true
                virtualizer.setStrength(((it - 100) * 1000).toShort())
                audioTrack.attachAuxEffect(virtualizer.id)
                audioTrack.setAuxEffectSendLevel(1f)
                audioTrack.setVolume(1f)
            } else {
                if (virtualizer.enabled) virtualizer.enabled = false
            }
        }
        transferThread?.start()
    }



    fun kill() {
        transferThread?.interrupt()
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            audioOutputStream.close()
            audioStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        var volume: Int = 100
            set(value) {
                field = value
                onVolumeChanged(value)
            }
        var onVolumeChanged: (Int) -> Unit = {}
    }
}