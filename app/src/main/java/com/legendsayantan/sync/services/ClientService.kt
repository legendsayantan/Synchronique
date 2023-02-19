package com.legendsayantan.sync.services

import EncryptionManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.*
import com.legendsayantan.sync.workers.*

class ClientService : Service() {


    lateinit var notificationManager: NotificationManager
    lateinit var builder: Notification.Builder
    var noticount = 0;
    lateinit var transferThread: Thread
    lateinit var values: Values

    lateinit var mediaWorker: MediaWorker
    lateinit var audioWorker: AudioWorker

    lateinit var startStreamReceivers: () -> Unit
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        values = Values(applicationContext)
        val notification =
            Notification.Builder(this, Notifications(applicationContext).client_channel)
                .setContentTitle("Client Service")
                .setContentText("Server : ${serverEndpoint.name}")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        startForeground(1, notification)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        initiateConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        connectionCanceled()
        try {
            transferThread.interrupt()
        } catch (_: java.lang.Exception) {
        }
        Nearby.getConnectionsClient(applicationContext).sendPayload(
            serverEndpoint.id,
            Payload.fromBytes(
                PayloadPacket.toEncBytes(
                    PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT, ByteArray(0))
                )
            )
        ).addOnCompleteListener {
            Nearby.getConnectionsClient(applicationContext)
                .disconnectFromEndpoint(serverEndpoint.id)
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
                                        processIncomingPayload(p1, serverEndpoint)
                                    } else if (p1.type == Payload.Type.STREAM) {
                                        startStreamReceivers = {
                                            if (clientConfig.audio) {
                                                audioWorker.playAudioFromPayload(p1)
                                            }
                                            startStreamReceivers = {}
                                        }
                                        //try to start now
                                        try {
                                            startStreamReceivers()
                                        }catch (_: java.lang.Exception){}
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
                                    Notifications(applicationContext).connection_channel
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
                                    Notifications(applicationContext).connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Request Rejected")
                                    .setContentText("Your connection to ${serverEndpoint.name} was rejected.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                connectionCanceled()
                                stopSelf()
                            }
                            ConnectionsStatusCodes.STATUS_ERROR -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    Notifications(applicationContext).connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Error Happened")
                                    .setContentText("Your request to ${serverEndpoint.name} failed.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
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
                        Values.connectedServer = null
                        Values.appState = Values.Companion.AppState.IDLE
                        connectionCanceled()
                        stopSelf()
                    }
                })
        }, {})
    }

    private fun prepareWorkers(clientConfig: ClientConfig) {
        MainActivity.instance?.runOnUiThread {
            Values.appState = Values.Companion.AppState.PERMS
            PermissionManager(MainActivity.instance!!).ask(clientConfig) {
                if (clientConfig.media) {
                    mediaWorker = MediaWorker(this)
                }
                if (clientConfig.audio) {
                    audioWorker = AudioWorker(this, null, clientConfig.audioSample)
                }
                Values.appState = Values.Companion.AppState.ACCESSING
                startStreamReceivers()
            }
        }
    }

    fun processIncomingPayload(payload: Payload, endpointInfo: EndpointInfo) {
        val payloadPacket = PayloadPacket.fromEncBytes(payload.asBytes()!!)
        when (payloadPacket.payloadType) {
            PayloadPacket.Companion.PayloadType.DISCONNECT -> {
                Nearby.getConnectionsClient(applicationContext)
                    .disconnectFromEndpoint(endpointInfo.id)
                connectionCanceled()
                Values.connectedServer = null
                Values.appState = Values.Companion.AppState.IDLE
                stopSelf()
            }
            PayloadPacket.Companion.PayloadType.CONFIG_PACKET -> {
                clientConfig = payloadPacket.data as ClientConfig
                prepareWorkers(clientConfig)
                connectionConfigured()
            }
            PayloadPacket.Companion.PayloadType.MEDIA_SYNC_PACKET -> {
                mediaWorker.recvMediaSync(payloadPacket.data as MediaSyncPacket)
            }
            PayloadPacket.Companion.PayloadType.MEDIA_ACTION_PACKET -> {
                mediaWorker.recvMediaAction(payloadPacket.data as MediaActionPacket)
            }
        }
    }

    companion object {
        lateinit var instance: ClientService
        var connectionCanceled: () -> Unit = {}
        var connectionInitiated: () -> Unit = {}
        var connectionConfigured: () -> Unit = {}
        lateinit var clientConfig: ClientConfig
        lateinit var serverEndpoint: EndpointInfo
    }
}