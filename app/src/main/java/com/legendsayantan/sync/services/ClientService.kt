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
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.*
import com.legendsayantan.sync.workers.*
import java.util.*
import kotlin.collections.ArrayList

class ClientService : Service() {


    lateinit var notificationManager: NotificationManager
    lateinit var builder: Notification.Builder
    var noticount = 2;
    lateinit var transferThread: Thread
    lateinit var values: Values

    private var mediaWorker: MediaWorker? = null
    var audioWorker: AudioWorker? =null

    lateinit var startStreamReceivers: () -> Unit

    lateinit var network: Network

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        values = Values(applicationContext)
        network = Network(applicationContext)
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
        network.disconnect()
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
                                                audioWorker!!.playAudioFromPayload(p1)
                                            }
                                            startStreamReceivers = {}
                                        }
                                        //try to start now
                                        try {
                                            startStreamReceivers()
                                        } catch (_: java.lang.Exception) {
                                        }
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
                                stopService(Intent(applicationContext, LookupService::class.java))
                                builder = Notification.Builder(
                                    applicationContext,
                                    Notifications(applicationContext).connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                                    .setContentTitle("Request Accepted")
                                    .setContentText("Your connection to ${serverEndpoint.name} was accepted.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                Values.connectedServer = serverEndpoint
                                Values.appState = Values.Companion.AppState.ACCESSING
                                // actual connection starts

                            }
                            ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    Notifications(applicationContext).connection_channel
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                            Notifications(applicationContext).connection_channel
                        )
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Device Disconnected")
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
        PermissionManager().ask(applicationContext,clientConfig) {
            if (clientConfig.media) {
                mediaWorker = MediaWorker(this)
            }
            if (clientConfig.audio) {
                audioWorker = AudioWorker(this, null, clientConfig.audioSample)
            }
            if (clientConfig.trigger) {

            }
            if (clientConfig.noti) {
                notificationDataList = ArrayList()
            }
            Values.appState = Values.Companion.AppState.ACCESSING
            try {
                startStreamReceivers()
            } catch (_: Exception) {
            }
        }
        connectionConfigured()
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
            }
            PayloadPacket.Companion.PayloadType.MEDIA_SYNC_PACKET -> {
                if (mediaWorker==null) return
                mediaWorker!!.recvMediaSync(payloadPacket.data as MediaSyncPacket)
            }
            PayloadPacket.Companion.PayloadType.MEDIA_ACTION_PACKET -> {
                if (mediaWorker==null) return
                mediaWorker!!.recvMediaAction(payloadPacket.data as MediaActionPacket)
            }
            PayloadPacket.Companion.PayloadType.NOTIFICATION_PACKET -> {
                val nData = payloadPacket.data as NotificationData
                val search = notificationDataList.find { it.key == nData.key }
                var index = notificationDataList.indexOf(search)
                if (search == null || index < 0 || notificationDataList[index].canReply) {
                    notificationDataList.add(nData)
                    index = notificationDataList.size - 1
                } else {
                    notificationDataList[index] = nData
                }
                onNotificationUpdated(notificationDataList[index], index)
            }
            else -> {}
        }
    }

    companion object {
        lateinit var instance: ClientService
        var connectionCanceled: () -> Unit = {}
        var connectionInitiated: () -> Unit = {}
        var connectionConfigured: () -> Unit = {}
        lateinit var clientConfig: ClientConfig
        var serverEndpoint = EndpointInfo("", "", "")

        var notificationDataList = ArrayList<NotificationData>()
        var onNotificationUpdated: (NotificationData, Int) -> Unit = { _, _ -> }

        var postNoti = false
    }
}