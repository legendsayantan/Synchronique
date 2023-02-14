package com.legendsayantan.sync.services

import EncryptionManager
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.R
import com.legendsayantan.sync.workers.Notifications
import com.legendsayantan.sync.workers.Values
import java.util.*
import kotlin.collections.ArrayList

class LookupService : Service() {
    lateinit var values : Values
    lateinit var lookupThread: Timer

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        endpoints = ArrayList()
        values = Values(applicationContext)
        val notification = Notification.Builder(this, Notifications.lookup_channel)
            .setContentTitle("Looking for nearby devices")
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
        lookupThread.cancel()
        Nearby.getConnectionsClient(this).stopDiscovery()
        Values.appState = Values.Companion.AppState.IDLE
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
                endpoints.removeIf { it.id == p0 }
            }
        }
        Values.appState = Values.Companion.AppState.LOOKING
        lookupThread = lookupNow(applicationContext,endpointDiscoveryCallback)
    }

    fun lookupNow(context: Context,endpointDiscoveryCallback: EndpointDiscoveryCallback): Timer{
        var singleLookUp = true
        val timer = Timer()
        timer.scheduleAtFixedRate(object: TimerTask(){
            override fun run() {
                val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(if(singleLookUp)Strategy.P2P_POINT_TO_POINT else Strategy.P2P_STAR).build()
                Nearby.getConnectionsClient(context).stopDiscovery()
                Nearby.getConnectionsClient(context)
                    .startDiscovery(values.nearby_advertise, endpointDiscoveryCallback, discoveryOptions)
                    .addOnCompleteListener {
                        singleLookUp = !singleLookUp
                    }
                    .addOnSuccessListener {

                    }
                    .addOnFailureListener {

                    }
            }
        },0,10000)
        return timer
    }



    companion object {
        lateinit var instance : LookupService
        var endpoint_updated: () -> Unit = {}
        lateinit var discover_id : String
        var endpoints = ArrayList<EndpointInfo>()
    }
}