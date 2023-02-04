package com.legendsayantan.sync.services

import EncryptionManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.R

class DiscoverService : Service() {

    lateinit var SERVICE_ID: String
    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        SERVICE_ID = "$packageName.advertise"
        instance = this
        endpoints = ArrayList()
        val chan = NotificationChannel(
            "$packageName.discover",
            "Discover Service",
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)

        val notification = Notification.Builder(this, SERVICE_ID)
            .setContentTitle("Discover Service")
            .setContentText("Discover Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
        FirebaseAuth.getInstance().currentUser?.let {
            discover_id = it.uid
        }
        println("-----------Starting Discovery---------------")
        startDiscovery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    override fun onDestroy() {
        DISCOVERING = false
        discover_stop()
        Nearby.getConnectionsClient(this).stopDiscovery()
        super.onDestroy()
    }
    private fun startDiscovery() {
        println("StartDiscovery")
        val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(p0: String, p1: DiscoveredEndpointInfo) {
                    EncryptionManager.fetchDynamicKey ({
                        println("${p1.endpointName} -------------------- ")
                        val splits = EncryptionManager().decrypt(p1.endpointName, it).split("_")
                        println("found $splits - $p0")
                        endpoints.add(EndpointInfo(p0,splits[0],splits[1],p1))
                        endpoint_updated()
                    },{
                        Toast.makeText(applicationContext,"Unable to verify token",Toast.LENGTH_SHORT).show()
                        it.printStackTrace()
                    })

            }

            override fun onEndpointLost(p0: String) {
                endpoints.removeIf { it.endpointId == p0 }
                endpoint_updated()
            }
        }
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        Nearby.getConnectionsClient(this)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnCompleteListener {
                println("Discover service trying to start")
            }
            .addOnSuccessListener {
                DISCOVERING = true
                discover_start()
                println("Discover started successfully")
            }
            .addOnFailureListener {
                DISCOVERING = false
                discover_stop()
                println("Discover failed "+it.message)
            }
    }





    companion object {
        lateinit var instance : DiscoverService
        var DISCOVERING = false
        var discover_start: () -> Unit = {}
        var discover_stop: () -> Unit = {}
        var endpoint_updated: () -> Unit = {}
        lateinit var discover_id : String
        var endpoints = ArrayList<EndpointInfo>()
    }
}