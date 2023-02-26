package com.legendsayantan.sync.services

import EncryptionManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.IpData
import com.legendsayantan.sync.models.ServerConfig
import com.legendsayantan.sync.models.SocketEndpointInfo
import com.legendsayantan.sync.workers.Notifications
import com.legendsayantan.sync.workers.Values
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

class WaitForConnectionService : Service() {


    lateinit var notificationManager: NotificationManager
    lateinit var builder: Notification.Builder
    var noticount = 2;
    lateinit var serverSocket: ServerSocket
    lateinit var values: Values
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        instance = this
        values = Values(applicationContext)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification =
            Notification.Builder(this, Notifications(applicationContext).wait_channel)
                .setContentTitle("Synchronique is ready")
                .setContentText("Waiting for nearby devices to connect")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        startForeground(1, notification)
        FirebaseAuth.getInstance().currentUser?.let { user ->
            EncryptionManager.fetchDynamicKey({ s ->
                advertise_id =
                    EncryptionManager().encrypt(user.displayName + "_" + user.uid.hashCode(), s)
                println("------------ADVERTISE ID----------------")
                println("$advertise_id ---------------- $s")
                if (values.nearby) startAdvertisingNearby()
                if (values.socket) startAdvertisingSocket()
                Values.onSocketError = {
                    builder = Notification.Builder(
                        applicationContext,
                        Notifications(applicationContext).server_channel
                    )
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Internet Error")
                        .setContentText(it.message)
                    notificationManager.notify(noticount, builder.build())
                    noticount++
                    it.printStackTrace()
                    if (values.nearby) values.socket = false
                    else {
                        Values.appState = Values.Companion.AppState.IDLE
                        stopSelf()
                    }
                }
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
        if (values.nearby) Nearby.getConnectionsClient(applicationContext).stopAdvertising()
        if (values.socket) {
            try {
                serverSocket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    private fun startAdvertisingNearby() {
        val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionResult(p0: String, p1: ConnectionResolution) {
                val endpoint = Values.connectedNearbyClients.find { it.id == p0 }
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
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Connection Error")
                            .setOngoing(false)
                            .setContentText("Could not connect to ${endpoint?.name}, Please retry.")
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                    }
                }
            }

            override fun onDisconnected(p0: String) {
                val endpoint = Values.connectedNearbyClients.find { it.id == p0 }
                builder = Notification.Builder(
                    applicationContext,
                    Notifications(applicationContext).connection_channel
                )
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Device Disconnected")
                    .setOngoing(false)
                    .setContentText("${endpoint?.name} was disconnected.")
                notificationManager.notify(noticount, builder.build())
                noticount++
                Values.connectedNearbyClients.remove(endpoint)
                if (Values.connectedNearbyClients.size == 0) {
                    Values.appState = Values.Companion.AppState.WAITING
                    if (!values.multiDevice && values.nearby) startAdvertisingNearby()
                }
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
                        builder = Notification.Builder(
                            applicationContext,
                            Notifications(applicationContext).connection_channel
                        )
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(name.split("_")[0] + " wants to connect to this device.")
                            .setContentIntent(pendingIntent)
                        notificationManager.notify(noticount, builder.build())
                        noticount++
                    } else {
                        MainActivity.instance!!.startFromNotification(intent)
                    }

                }, {
                    Toast.makeText(
                        applicationContext,
                        "Could not validate authenticaton, check your internet connection",
                        Toast.LENGTH_LONG
                    ).show()
                })

            }
        }
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(
            values.networkStrategy
        ).build()
        Nearby.getConnectionsClient(applicationContext)
            .startAdvertising(
                advertise_id,
                values.nearby_advertise,
                connectionLifecycleCallback,
                advertisingOptions
            )
            .addOnSuccessListener {
                Values.appState = Values.Companion.AppState.WAITING
            }
            .addOnFailureListener {
                Values.appState = Values.Companion.AppState.IDLE
            }
    }

    private fun startAdvertisingSocket() {
        Thread {
            try {
                val url = URL("https://api6.my-ip.io/ip")
                val `in` = BufferedReader(InputStreamReader(url.openStream()))
                val line = `in`.readLine()
                println("Online Ip : $line")
                `in`.close()
                serverSocket = ServerSocket(0)
                println("Listening for incoming connections at port ${serverSocket.localPort}")
                Values.socketPort = serverSocket.localPort
                val user = IpData(
                    EncryptionManager().encrypt(line, serverSocket.localPort.toString())
                )
                values.firestore.document(FirebaseAuth.getInstance().currentUser?.email!!).set(user).addOnSuccessListener {
                    Values.appState = Values.Companion.AppState.WAITING
                    Thread{
                        while (true) {
                            try {
                                val socket = serverSocket.accept()
                                EncryptionManager.fetchDynamicKey({
                                    val name = EncryptionManager().decrypt(socket.getInputStream().bufferedReader().readLine(), it)
                                    ServerService.instance!!.acceptConnection(
                                        SocketEndpointInfo(
                                            name,
                                            socket
                                        )
                                    )
                                }, {})
                            }catch (e:Exception){
                                Values.onSocketError(e)
                            }
                        }
                    }.start()
                }.addOnFailureListener {
                    Values.onSocketError(it)
                }
            } catch (e: Exception) {
                Values.onSocketError(e)
            }

        }.start()
    }

    companion object {
        lateinit var instance: WaitForConnectionService
        lateinit var advertise_id: String
        var serverConfig: ServerConfig? = null
    }
}