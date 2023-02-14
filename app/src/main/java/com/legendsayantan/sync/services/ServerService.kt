package com.legendsayantan.sync.services

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.PlaybackState.ACTION_PLAY_PAUSE
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.legendsayantan.sync.interfaces.PayloadPacket
import com.legendsayantan.sync.interfaces.ServerConfig
import com.legendsayantan.sync.workers.Notifications
import com.legendsayantan.sync.workers.Values

class ServerService : Service() {

    lateinit var values: Values
    lateinit var serverConfig: ServerConfig
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        values = Values(applicationContext)
        serverConfig = WaitForConnectionService.serverConfig
        val notification = Notification.Builder(this, Notifications.server_channel)
            .setContentTitle("Synchronique server is ready")
            .setContentText("Connected to ${Values.connectedClients.size} devices")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        Values.onClientAdded = {
            acceptConnection(Values.connectedClients[Values.connectedClients.size - 1])
        }
    }

    private fun acceptConnection(endpointInfo: EndpointInfo) {
        Nearby.getConnectionsClient(this)
            .acceptConnection(endpointInfo.id, object : PayloadCallback() {
                override fun onPayloadReceived(p0: String, p1: Payload) {
                    if (p1.type == Payload.Type.BYTES && p1.asBytes() != null) {
                        println("--------------- PAYLOAD -----------------")
                        println(String(p1.asBytes()!!))
                        Toast.makeText(applicationContext, "payload", Toast.LENGTH_SHORT).show()
                        if (PayloadPacket.fromEncBytes(p1.asBytes()!!).payloadType == PayloadPacket.Companion.PayloadType.DISCONNECT) {
                            CONNECTED = false
                            connectionUpdate()
                            Nearby.getConnectionsClient(applicationContext)
                                .disconnectFromEndpoint(endpointInfo.id)
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
                    "Connected to ${endpointInfo.name}",
                    Toast.LENGTH_SHORT
                ).show()
                CONNECTED = true
                connectionUpdate()
                if (!serverConfig.multiDevice) stopService(
                    Intent(
                        applicationContext,
                        WaitForConnectionService::class.java
                    )
                )
                //here comes the actual connection
                /*Nearby.getConnectionsClient(applicationContext).sendPayload(
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
                    startServingData()
                }*/
            }
            .addOnFailureListener {
                Toast.makeText(
                    applicationContext,
                    "Failed to connect to ${endpointInfo.name}",
                    Toast.LENGTH_SHORT
                ).show()
                CONNECTED = false
                connectionUpdate()
                it.printStackTrace()
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
       /* when(intent?.action){
            "play" -> {
                Toast.makeText(applicationContext, "play", Toast.LENGTH_SHORT).show()
            }
            "prev" -> {
                Toast.makeText(applicationContext, "prev", Toast.LENGTH_SHORT).show()
            }
            "next" -> {
                Toast.makeText(applicationContext, "next", Toast.LENGTH_SHORT).show()
            }
            "rewind" -> {
                Toast.makeText(applicationContext, "rewind", Toast.LENGTH_SHORT).show()
            }
            "ff" -> {
                Toast.makeText(applicationContext, "forward", Toast.LENGTH_SHORT).show()
            }
        }*/
        return START_STICKY
    }

    override fun onDestroy() {
        for (endpoint in Values.connectedClients) {
            Nearby.getConnectionsClient(applicationContext).sendPayload(
                endpoint.id,
                Payload.fromBytes(
                    PayloadPacket.toEncBytes(
                        PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT, ByteArray(0))
                    )
                )
            ).addOnCompleteListener {
                Nearby.getConnectionsClient(applicationContext).disconnectFromEndpoint(endpoint.id)
                stopService(Intent(applicationContext, MediaService::class.java))
            }
        }
        super.onDestroy()
    }

/*    fun mediaControls() {
        val playPauseIntent = Intent(this, ServerService::class.java)
        playPauseIntent.action = "play"
        val playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(this, ServerService::class.java)
        prevIntent.action = "prev"
        val prevPendingIntent = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, ServerService::class.java)
        nextIntent.action = "next"
        val nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val rewindIntent = Intent(this, ServerService::class.java)
        rewindIntent.action = "rewind"
        val rewindPendingIntent = PendingIntent.getService(this, 0, rewindIntent, PendingIntent.FLAG_IMMUTABLE)

        val ffIntent = Intent(this, ServerService::class.java)
        ffIntent.action = "ff"
        val ffPendingIntent = PendingIntent.getService(this, 0, ffIntent, PendingIntent.FLAG_IMMUTABLE)
// Create the notification with media playback controls
        val notification = Notification.Builder(this, Notifications.controls_channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Media Controls")
            .addAction(R.drawable.baseline_fast_rewind_24, "Rewind", rewindPendingIntent)
            .addAction(R.drawable.baseline_skip_previous_24, "Previous", prevPendingIntent)
            .addAction(R.drawable.baseline_play_arrow_24, "Play/Pause", playPausePendingIntent)
            .addAction(R.drawable.baseline_skip_next_24, "Next", nextPendingIntent)
            .addAction(R.drawable.baseline_fast_forward_24, "Fast Forward", ffPendingIntent)
            .build()

// Show the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(5, notification)
        }
    }
    fun startServingData(){
        if(serverConfig.clientConfig.media)mediaControls()
    }
    */


    companion object {
        lateinit var instance: ServerService
        var CONNECTED = false
        var connectionUpdate: () -> Unit = {}
    }
}