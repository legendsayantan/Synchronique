package com.legendsayantan.sync.services

import EncryptionManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioFormat
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.MediaPacket
import com.legendsayantan.sync.interfaces.PayloadPacket
import kotlin.collections.ArrayList
import kotlin.math.E
import kotlin.system.exitProcess

class SingularConnectionService : Service() {


    lateinit var SERVICE_ID: String
    lateinit var notificationManager: NotificationManager
    lateinit var notificationChannel: NotificationChannel
    lateinit var builder: Notification.Builder
    var endpointName = ENDPOINT_NAME
    var noticount = 0;

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SERVICE_ID = "$packageName.connection"
        val chan = NotificationChannel(
            SERVICE_ID,
            "Connection Service",
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)

        val notification = Notification.Builder(this, SERVICE_ID)
            .setContentTitle("Connection Service")
            .setContentText("Connected to $ENDPOINT_NAME")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        notificationChannel = NotificationChannel(
            "$packageName.request",
            "Requests",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.BLUE
        notificationChannel.enableVibration(false)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
        println("Endpoint ID: $ENDPOINT_ID")
        println("Endpoint Name: $ENDPOINT_NAME")
        if (CONNECTION_MODE == ConnectionMode.INITIATE) {
            initiateConnection()
        } else {
            acceptConnection()
        }
    }

    private fun acceptConnection() {
        Nearby.getConnectionsClient(this)
            .acceptConnection(ENDPOINT_ID, object : PayloadCallback() {
                override fun onPayloadReceived(p0: String, p1: Payload) {
                    if (p1.type == Payload.Type.BYTES && p1.asBytes() != null) {
                        println("--------------- PAYLOAD -----------------")
                        println(String(p1.asBytes()!!))
                        Toast.makeText(applicationContext, "payload", Toast.LENGTH_SHORT).show()
                        if (PayloadPacket.fromEncBytes(p1.asBytes()!!).payloadType == PayloadPacket.Companion.PayloadType.DISCONNECT) {
                            CONNECTED = false
                            connectionUpdate()
                            Nearby.getConnectionsClient(applicationContext)
                                .disconnectFromEndpoint(ENDPOINT_ID)
                            stopSelf()
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
                    "Connected to $ENDPOINT_NAME",
                    Toast.LENGTH_SHORT
                ).show()
                CONNECTED = true
                connectionUpdate()
                if (ACCESS.contains(0)) {
                    startForegroundService(Intent(applicationContext, MediaService::class.java))
                    MediaService.streamMode = getSharedPreferences("default", MODE_PRIVATE).getBoolean("streamMedia", Build.VERSION.SDK_INT> Build.VERSION_CODES.P)
                    MediaService.SAMPLE_RATE = getSharedPreferences("default", Context.MODE_PRIVATE).getInt("quality", 8000)
                }
            }
            .addOnFailureListener {
                CONNECTED = false
                connectionUpdate()
                builder = Notification.Builder(
                    applicationContext,
                    "${applicationContext.packageName}.request"
                )
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentTitle("Failed to Connect")
                    .setContentText("Could not connect to $ENDPOINT_NAME")
                notificationManager.notify(noticount, builder.build())
                noticount++
                it.printStackTrace()
                stopSelf()
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        CONNECTED = false
        connectionUpdate()
        Nearby.getConnectionsClient(this).disconnectFromEndpoint(ENDPOINT_ID)
        Nearby.getConnectionsClient(applicationContext).sendPayload(SingularConnectionService.ENDPOINT_ID,
            Payload.fromBytes(PayloadPacket.toEncBytes(
                PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT,ByteArray(0))
            ))).addOnCompleteListener {
            Nearby.getConnectionsClient(applicationContext).disconnectFromEndpoint(ENDPOINT_ID)
            stopService(Intent(applicationContext,MediaService::class.java))
        }
        super.onDestroy()
    }

    private fun initiateConnection() {
        EncryptionManager.fetchDynamicKey({
            Nearby.getConnectionsClient(applicationContext).requestConnection(
                EncryptionManager().encrypt(
                    FirebaseAuth.getInstance().currentUser?.displayName.toString() + "_" + FirebaseAuth.getInstance().currentUser?.uid.toString()
                        .hashCode() + "_" + ACCESS.joinToString(separator = ","), it
                ),
                ENDPOINT_ID,
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
                                            PayloadPacket.Companion.PayloadType.MEDIA_PACKET -> {
                                                MediaService.instance?.recvMediaSync(payloadPacket.data as MediaPacket)
                                            }
                                            PayloadPacket.Companion.PayloadType.AUDIO_PACKET ->{
                                                MediaService.SAMPLE_RATE = payloadPacket.data as Int
                                            }
                                            PayloadPacket.Companion.PayloadType.DISCONNECT -> {
                                                CONNECTED = false
                                                connectionUpdate()
                                                Nearby.getConnectionsClient(applicationContext)
                                                    .disconnectFromEndpoint(ENDPOINT_ID)
                                                stopSelf()
                                            }
                                        }
                                    }else if(p1.type==Payload.Type.STREAM){
                                        println("Stream receiving")
                                        MediaService.instance?.playAudioFromPayload(p1)
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
                                    "${applicationContext.packageName}.request"
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Request Accepted")
                                    .setContentText("Your request to $ENDPOINT_NAME was accepted.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                if (ACCESS.contains(0)) {
                                    startForegroundService(
                                        Intent(
                                            applicationContext,
                                            MediaService::class.java
                                        )
                                    )
                                }
                                CONNECTED = true
                                connectionUpdate()

                            }
                            ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    "${applicationContext.packageName}.request"
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Request Rejected")
                                    .setContentText("Your request to $ENDPOINT_NAME was rejected.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                CONNECTED = false
                                connectionUpdate()
                                stopSelf()
                            }
                            ConnectionsStatusCodes.STATUS_ERROR -> {
                                builder = Notification.Builder(
                                    applicationContext,
                                    "${applicationContext.packageName}.request"
                                )
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle("Error Happened")
                                    .setContentText("Your request to $ENDPOINT_NAME failed because of wireless error.")
                                notificationManager.notify(noticount, builder.build())
                                noticount++
                                CONNECTED = false
                                connectionUpdate()
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
                            .setContentText("$endpointName was disconnected.")
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                        CONNECTED = false
                        connectionUpdate()
                        MediaService.instance?.transferThread?.interrupt()
                        stopService(Intent(applicationContext, MediaService::class.java))
                        stopSelf()
                    }
                })
        }, {})
    }

    companion object {
        lateinit var ENDPOINT_HASH: String
        lateinit var instance: SingularConnectionService
        var CONNECTED = false
        var connectionUpdate: () -> Unit = {}
        var connectionInitiated: () -> Unit = {}
        var ENDPOINT_ID: String = ""
        var ENDPOINT_NAME: String = ""

        enum class ConnectionMode {
            INITIATE, ACCEPT
        }

        var CONNECTION_MODE = ConnectionMode.INITIATE
        var ACCESS = ArrayList<Int>()
    }
}