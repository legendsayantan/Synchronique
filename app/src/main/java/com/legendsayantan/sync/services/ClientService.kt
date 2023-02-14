package com.legendsayantan.sync.services

import EncryptionManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.IBinder
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.ClientConfig
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.legendsayantan.sync.interfaces.PayloadPacket
import com.legendsayantan.sync.workers.Notifications
import com.legendsayantan.sync.workers.PermissionManager
import com.legendsayantan.sync.workers.Values
import java.io.IOException

class ClientService : Service() {


    lateinit var notificationManager: NotificationManager
    var CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
    val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    lateinit var builder: Notification.Builder
    var noticount = 0;
    lateinit var clientConfig: ClientConfig
    lateinit var transferThread: Thread
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val notification = Notification.Builder(this, Notifications.client_channel)
            .setContentTitle("Client Service")
            .setContentText("Server : ${serverEndpoint.name}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        initiateConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        CONNECTED = false
        connectionCanceled()
        try {
            transferThread.interrupt()
        }catch ( _ : java.lang.Exception){}
        Nearby.getConnectionsClient(applicationContext).sendPayload(serverEndpoint.id,
            Payload.fromBytes(PayloadPacket.toEncBytes(
                PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT,ByteArray(0))
            ))).addOnCompleteListener {
            Nearby.getConnectionsClient(applicationContext).disconnectFromEndpoint(serverEndpoint.id)
            stopService(Intent(applicationContext,MediaService::class.java))
        }
        super.onDestroy()
    }

    private fun initiateConnection() {
        EncryptionManager.fetchDynamicKey({
            Nearby.getConnectionsClient(applicationContext).requestConnection(
                EncryptionManager().encrypt(
                    FirebaseAuth.getInstance().currentUser?.displayName.toString() + "_" + FirebaseAuth.getInstance().currentUser?.uid.toString()
                        .hashCode(), it
                ),
                serverEndpoint.id,
                object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(p0: String, p1: ConnectionInfo) {
                        Nearby.getConnectionsClient(applicationContext)
                            .acceptConnection(p0, object : PayloadCallback() {
                                override fun onPayloadReceived(
                                    p0: String,
                                    p1: Payload
                                ) {
                                    if (p1.type == Payload.Type.BYTES && p1.asBytes() != null) {
                                        val payloadPacket = PayloadPacket.fromEncBytes(p1.asBytes()!!)
                                        when (payloadPacket.payloadType) {
                                            PayloadPacket.Companion.PayloadType.DISCONNECT -> {
                                                CONNECTED = false
                                                connectionCanceled()
                                                Nearby.getConnectionsClient(applicationContext)
                                                    .disconnectFromEndpoint(serverEndpoint.id)
                                                Values.connectedServer = null
                                                stopSelf()
                                            }
                                            PayloadPacket.Companion.PayloadType.CONFIG_PACKET -> {
                                                clientConfig = payloadPacket.data as ClientConfig
                                                prepareReceivers(clientConfig)
                                            }
                                            PayloadPacket.Companion.PayloadType.MEDIA_PACKET -> {
                                                //MediaService.instance?.recvMediaSync(payloadPacket.data as MediaPacket)
                                            }
                                        }
                                    }else if(p1.type==Payload.Type.STREAM){
                                        println("Stream started")
                                        playAudioFromPayload(p1)
                                    }
                                }
                                override fun onPayloadTransferUpdate(
                                    p0: String,
                                    p1: PayloadTransferUpdate
                                ) {
                                }
                            })
                        connectionInitiated()
                    }

                    override fun onConnectionResult(p0: String, p1: ConnectionResolution) {
                        when (p1.status.statusCode) {
                            ConnectionsStatusCodes.STATUS_OK -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    Notifications.connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Request Accepted")
                                    .setContentText("Your connection to ${serverEndpoint.name} was accepted.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                Values.connectedServer = serverEndpoint
                                // actual connection starts

                            }
                            ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    Notifications.connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Request Rejected")
                                    .setContentText("Your connection to ${serverEndpoint.name} was rejected.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                CONNECTED = false
                                REJECTED = true
                                connectionCanceled()
                                stopSelf()
                            }
                            ConnectionsStatusCodes.STATUS_ERROR -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    Notifications.connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Error Happened")
                                    .setContentText("Your request to ${serverEndpoint.name} failed because of some error.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                CONNECTED = false
                                REJECTED = true
                                connectionCanceled()
                                stopSelf()
                            }
                        }
                    }

                    override fun onDisconnected(p0: String) {
                        builder = Notification.Builder(
                            applicationContext,
                            "${applicationContext.packageName}.request"
                        )
                            .setSmallIcon(R.drawable.ic_launcher_background)
                            .setContentTitle("Device Disconnected")
                            .setOngoing(false)
                            .setContentText("${serverEndpoint.name} was disconnected.")
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                        CONNECTED = false
                        Values.connectedServer = null
                        connectionCanceled()
                        stopSelf()
                    }
                })
        }, {})
    }

    fun prepareReceivers(clientConfig: ClientConfig){
        MainActivity.instance?.runOnUiThread {
            PermissionManager(MainActivity.instance!!).ask(clientConfig){

            }
        }
    }

    fun playAudioFromPayload(payload: Payload) {
        val bufferSize = AudioTrack.getMinBufferSize(
            MediaService.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AUDIO_FORMAT
        )
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            clientConfig.audioSample,
            AudioFormat.CHANNEL_IN_STEREO,
            AUDIO_FORMAT,
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
    companion object {
        lateinit var instance: ClientService
        var CONNECTED = false
        var REJECTED = false
        var connectionCanceled: () -> Unit = {}
        var connectionInitiated: () -> Unit = {}
        lateinit var clientConfig: ClientConfig
        lateinit var serverEndpoint: EndpointInfo
    }
}