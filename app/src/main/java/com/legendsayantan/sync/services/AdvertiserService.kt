package com.legendsayantan.sync.services

import EncryptionManager
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R

class AdvertiserService : Service() {

    lateinit var SERVICE_ID: String
    lateinit var notificationManager: NotificationManager
    lateinit var notificationChannel: NotificationChannel
    lateinit var builder: Notification.Builder
    var noticount = 0;
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SERVICE_ID = "$packageName.advertise"
        val chan = NotificationChannel(
            SERVICE_ID,
            "Advertiser Service",
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)

        val notification = Notification.Builder(this, SERVICE_ID)
            .setContentText("App is running in background")
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
        FirebaseAuth.getInstance().currentUser?.let { user ->
            EncryptionManager.fetchDynamicKey({
                advertise_id =
                    EncryptionManager().encrypt(user.displayName + "_" + user.uid.hashCode(), it)
                println("------------ADVERTISE ID----------------")
                println(advertise_id)
                startAdvertising()
            }, {
                Toast.makeText(applicationContext, "Could not advertise", Toast.LENGTH_SHORT).show()
                it.printStackTrace()
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        ADVERTISING = false
        Nearby.getConnectionsClient(this).stopAdvertising()
        advertise_update()
        super.onDestroy()
    }

    private fun startAdvertising() {
        val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionResult(p0: String, p1: ConnectionResolution) {
                advertise_update()
                when (p1.status.statusCode) {

                    ConnectionsStatusCodes.STATUS_OK -> {

                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {

                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        builder = Notification.Builder(
                            applicationContext,
                            "${applicationContext.packageName}.request"
                        )
                            .setSmallIcon(R.drawable.ic_launcher_background)
                            .setContentTitle("Connection Error")
                            .setOngoing(false)
                            .setContentText("Could not connect to ${SingularConnectionService.ENDPOINT_NAME}, Please retry.")
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                        SingularConnectionService.CONNECTED = false
                        SingularConnectionService.connectionUpdate()
                        MediaService.instance?.transferThread?.interrupt()
                        stopService(Intent(applicationContext, MediaService::class.java))
                        stopService(Intent(applicationContext, SingularConnectionService::class.java))
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
                    .setContentText("${SingularConnectionService.ENDPOINT_NAME} was disconnected.")
                notificationManager.notify(noticount, builder.build())
                noticount++
                SingularConnectionService.CONNECTED = false
                SingularConnectionService.connectionUpdate()
                MediaService.instance?.transferThread?.interrupt()
                stopService(Intent(applicationContext, MediaService::class.java))
                stopService(Intent(applicationContext, SingularConnectionService::class.java))
            }

            override fun onConnectionInitiated(p0: String, p1: ConnectionInfo) {
                //show a notification

                EncryptionManager.fetchDynamicKey({
                    //set pendingintent for mainactivity
                    var name = EncryptionManager().decrypt(p1.endpointName, it);
                    var intent = Intent(
                        applicationContext,
                        MainActivity::class.java
                    ).putExtra("endpointName", name).putExtra("endpointId", p0)


                    if (MainActivity.instance == null) {
                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext, 0, intent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        builder = Notification.Builder(applicationContext, "$packageName.request")
                            .setSmallIcon(R.drawable.ic_launcher_background)
                            .setContentTitle("New link request")
                            .setContentText("Accept to link with " + name.replace("_", " -> "))
                            .setContentIntent(pendingIntent)
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                    } else {
                        MainActivity.instance!!.startFromNotification(intent)
                    }

                }, {
                    Toast.makeText(applicationContext, "Could not validate auth, check your internet connection", Toast.LENGTH_LONG).show()
                })

            }
        }
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(
            Strategy.P2P_POINT_TO_POINT
        ).build()
        Nearby.getConnectionsClient(applicationContext)
            .startAdvertising(
                advertise_id, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener {
                println("Advertising started successfully")
                ADVERTISING = true
                advertise_update()
            }
            .addOnFailureListener {
                println("Advertising failed " + it.message)
                ADVERTISING = false
                advertise_update()
            }
    }

    companion object {
        lateinit var instance: AdvertiserService
        var ADVERTISING = false
        var advertise_update: () -> Unit = {}
        lateinit var advertise_id: String
    }
}