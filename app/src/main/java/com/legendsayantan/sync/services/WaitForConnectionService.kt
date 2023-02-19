package com.legendsayantan.sync.services

import EncryptionManager
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.ServerConfig
import com.legendsayantan.sync.workers.Notifications
import com.legendsayantan.sync.workers.Values

class WaitForConnectionService : Service() {


    lateinit var notificationManager: NotificationManager
    lateinit var builder: Notification.Builder
    var noticount = 0;
    lateinit var values: Values
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        instance = this
        values = Values(applicationContext)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = Notification.Builder(this, Notifications(applicationContext).wait_channel)
            .setContentTitle("Synchronique is ready")
            .setContentText("Waiting for nearby devices to connect")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        FirebaseAuth.getInstance().currentUser?.let { user ->
            EncryptionManager.fetchDynamicKey({
                advertise_id =
                    EncryptionManager().encrypt(user.displayName + "_" + user.uid.hashCode(), it)
                println("------------ADVERTISE ID----------------")
                println("$advertise_id ---------------- $it")
                startAdvertising()
            }, {
                it.printStackTrace()
            })
        }
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        Values.appState = Values.Companion.AppState.IDLE
        Nearby.getConnectionsClient(this).stopAdvertising()
        super.onDestroy()
    }

    private fun startAdvertising() {
        val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionResult(p0: String, p1: ConnectionResolution) {
                val endpoint = Values.connectedClients.find { it.id == p0 }
                when (p1.status.statusCode) {

                    ConnectionsStatusCodes.STATUS_OK -> {

                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {

                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        builder = Notification.Builder(
                            applicationContext,
                            Notifications(applicationContext).connection_channel
                        )
                            .setSmallIcon(R.drawable.ic_launcher_background)
                            .setContentTitle("Connection Error")
                            .setOngoing(false)
                            .setContentText("Could not connect to ${endpoint?.name}, Please retry.")
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                    }
                }
            }

            override fun onDisconnected(p0: String) {
                val endpoint = Values.connectedClients.find { it.id == p0 }
                builder = Notification.Builder(
                    applicationContext,
                    Notifications(applicationContext).connection_channel
                )
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentTitle("Device Disconnected")
                    .setOngoing(false)
                    .setContentText("${endpoint?.name} was disconnected.")
                notificationManager.notify(noticount, builder.build())
                noticount++
                Values.connectedClients.remove(endpoint)
            }

            override fun onConnectionInitiated(p0: String, p1: ConnectionInfo) {
                //show a notification

                EncryptionManager.fetchDynamicKey({
                    val name = EncryptionManager().decrypt(p1.endpointName, it);
                    val intent = Intent(
                        applicationContext,
                        MainActivity::class.java
                    ).putExtra("endpointName", name).putExtra("endpointId", p0)


                    if (MainActivity.instance == null) {
                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext, 0, intent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        builder = Notification.Builder(applicationContext, Notifications(applicationContext).connection_channel)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(name.split("_")[0]+" wants to connect to this device.")
                            .setContentIntent(pendingIntent)
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                    } else {
                        MainActivity.instance!!.startFromNotification(intent)
                    }

                }, {
                    Toast.makeText(applicationContext, "Could not validate authenticaton, check your internet connection", Toast.LENGTH_LONG).show()
                })

            }
        }
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(
            values.networkStrategy
        ).build()
        Nearby.getConnectionsClient(applicationContext)
            .startAdvertising(
                advertise_id, values.nearby_advertise, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener {
                Values.appState = Values.Companion.AppState.WAITING
            }
            .addOnFailureListener {
                Values.appState = Values.Companion.AppState.IDLE
            }
    }

    companion object {
        lateinit var instance: WaitForConnectionService
        lateinit var advertise_id: String
        var serverConfig: ServerConfig? = null
    }
}