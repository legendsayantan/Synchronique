package com.legendsayantan.sync.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.*
import com.legendsayantan.sync.workers.*
import java.io.OutputStream
import java.io.PrintWriter

class ServerService : Service() {

    lateinit var values: Values
    lateinit var serverConfig: ServerConfig
    lateinit var mediaProjection: MediaProjection

    lateinit var mediaWorker: MediaWorker
    lateinit var audioWorker: AudioWorker
    lateinit var network: Network
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        values = Values(applicationContext)
        network = Network(applicationContext)
        serverConfig = if (WaitForConnectionService.serverConfig == null) {
            println("---------------------------------------------------------")
            println("SERVERCONFIG NULL")
            println("---------------------------------------------------------")
            ServerConfig(Values(applicationContext))
        } else WaitForConnectionService.serverConfig!!
        val notification =
            Notification.Builder(this, Notifications(applicationContext).server_channel)
                .setContentTitle("Synchronique server is ready")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        if (serverConfig.clientConfig.audio && !serverConfig.audioMic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
        } else {
            startForeground(1, notification)
        }
        NotificationListener.allowReply = serverConfig.clientConfig.noti&&serverConfig.notiReply
        NotificationListener.shareNoti = serverConfig.clientConfig.noti
    }

    fun acceptConnection(endpointInfo: EndpointInfo) {
        if (endpointInfo is SocketEndpointInfo) {
            endpointInfo.senderThread.push(
                PayloadPacket.toEncString(
                    PayloadPacket(
                        PayloadPacket.Companion.PayloadType.CONFIG_PACKET,
                        WaitForConnectionService.serverConfig!!.clientConfig
                    )
                )
            )
            Values.connectedSocketClients.add(endpointInfo)
            if (Values.allClients.size == 1) initialiseServe()
            NotificationListener.allNotifications.forEach {
                endpointInfo.senderThread.push(
                    PayloadPacket.toEncString(
                        PayloadPacket(PayloadPacket.Companion.PayloadType.NOTIFICATION_PACKET, it)
                    )
                )
            }
            serveDataTo(endpointInfo)
        } else {
            Nearby.getConnectionsClient(applicationContext)
                .acceptConnection(endpointInfo.id, object : PayloadCallback() {
                    override fun onPayloadReceived(p0: String, p1: Payload) {
                        if (p1.type == Payload.Type.BYTES && p1.asBytes() != null) {
                            println("--------------- PAYLOAD -----------------")
                            println(String(p1.asBytes()!!))
                            val payloadPacket = PayloadPacket.fromEncBytes(p1.asBytes()!!)
                            Toast.makeText(
                                applicationContext,
                                "payload - ${payloadPacket.payloadType}",
                                Toast.LENGTH_SHORT
                            ).show()
                            when (payloadPacket.payloadType) {
                                PayloadPacket.Companion.PayloadType.DISCONNECT -> {
                                    Nearby.getConnectionsClient(applicationContext)
                                        .disconnectFromEndpoint(endpointInfo.id)
                                }
                                PayloadPacket.Companion.PayloadType.TRIGGER_PACKET -> {}
                                PayloadPacket.Companion.PayloadType.NOTIFICATION_REPLY -> {
                                    NotificationListener.sendReplyTo(
                                        payloadPacket.data as NotificationReply,
                                        applicationContext
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    override fun onPayloadTransferUpdate(
                        p0: String,
                        p1: PayloadTransferUpdate
                    ) {
                        when (p1.status) {
                            PayloadTransferUpdate.Status.SUCCESS -> {

                            }
                            PayloadTransferUpdate.Status.FAILURE -> {

                            }
                        }
                    }
                })
                .addOnSuccessListener {
                    Toast.makeText(
                        applicationContext,
                        "Connected to ${endpointInfo.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    if ((values.nearby && !serverConfig.multiDevice) && !values.socket) stopService(
                        Intent(
                            applicationContext,
                            WaitForConnectionService::class.java
                        )
                    )
                    if (Values.allClients.size == 1) initialiseServe()
                    //here comes the actual connection
                    Nearby.getConnectionsClient(applicationContext).sendPayload(
                        endpointInfo.id,
                        Payload.fromBytes(
                            PayloadPacket.toEncBytes(
                                PayloadPacket(
                                    PayloadPacket.Companion.PayloadType.CONFIG_PACKET,
                                    serverConfig.clientConfig
                                )
                            )
                        )
                    ).addOnCompleteListener {
                        println("----------- appstate ------------")
                        println(Values.appState)
                        Values.connectedNearbyClients.add(endpointInfo)
                        serveDataTo(endpointInfo)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        applicationContext,
                        "Failed to connect to ${endpointInfo.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    it.printStackTrace()
                }
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { mediaWorker.onMediaAction(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        network.disconnect()
        stopService(Intent(applicationContext, NotificationListener::class.java))
        try {
            audioWorker.kill()
        } catch (_: UninitializedPropertyAccessException) {
        }
        Values.appState = Values.Companion.AppState.IDLE
        super.onDestroy()
    }


    private fun initialiseServe() {
        println("initialise Serve")
        if (serverConfig.clientConfig.media) {
            mediaWorker = MediaWorker(applicationContext)
            mediaWorker.startMediaControls()
            if (!values.mediaClientOnly) mediaWorker.syncTo()
        }
        if (serverConfig.clientConfig.audio) {
            if (serverConfig.audioMic) {
                audioWorker =
                    AudioWorker(applicationContext, null, serverConfig.clientConfig.audioSample)
                audioWorker.startAudioRecord()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    audioWorker = AudioWorker(
                        applicationContext,
                        mediaProjectionManager.getMediaProjection(resultCode, data),
                        serverConfig.clientConfig.audioSample
                    )
                    audioWorker.startAudioStream()
                }
            }
        }
        if (serverConfig.clientConfig.trigger) {

        }
        if (serverConfig.clientConfig.noti) {
            startForegroundService(Intent(applicationContext, NotificationListener::class.java))
        }
    }

    private fun serveDataTo(endpointInfo: EndpointInfo) {
        if (serverConfig.clientConfig.audio) {
            audioWorker.clientele.add(endpointInfo)
        }
    }


    companion object {
        var instance: ServerService? = null
        lateinit var mediaProjectionManager: MediaProjectionManager
        var resultCode: Int = 0
        lateinit var data: Intent
    }
}