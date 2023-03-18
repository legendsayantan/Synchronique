package com.legendsayantan.sync.workers

import android.content.Context
import android.media.AudioFormat
import android.os.Build
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.SeekBar
import com.google.android.gms.nearby.connection.Strategy
import com.google.firebase.firestore.FirebaseFirestore
import com.legendsayantan.sync.workers.socket.ServerThread
import com.legendsayantan.sync.models.EndpointInfo
import com.legendsayantan.sync.models.SocketEndpointInfo

/**
 * @author legendsayantan
 */
class Values(context: Context) {
    val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
    var onServerValueUpdate = {}
    var onClientValueUpdate = {}
    val syncParams = mediaSync || audioStream || triggerButtons || notiShare
    fun set(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun set(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun bind(
        switch: CompoundButton,
        key: String,
        default: Boolean = false,
        serverValue: Boolean = true,
        onChange: () -> Unit = {}
    ) {
        switch.isChecked = prefs.getBoolean(key, default)
        switch.setOnCheckedChangeListener { _, isChecked ->
            set(key, isChecked)
            if (serverValue) onServerValueUpdate() else onClientValueUpdate()
            onChange()
        }
        onChange()
    }

    fun bind(
        radioButton1: RadioButton,
        radioButton2: RadioButton,
        key: String,
        default: Boolean,
        serverValue: Boolean = true
    ) {
        radioButton1.isChecked = prefs.getBoolean(key, default)
        radioButton2.isChecked = !prefs.getBoolean(key, default)
        radioButton1.setOnCheckedChangeListener { _, isChecked ->
            set(key, isChecked)
            if (serverValue) onServerValueUpdate() else onClientValueUpdate()
        }
        radioButton2.setOnCheckedChangeListener { _, isChecked ->
            set(key, !isChecked)
            if (serverValue) onServerValueUpdate() else onClientValueUpdate()
        }
    }

    fun bind(
        seekBar: SeekBar,
        key: String,
        def: Int,
        serverValue: Boolean = true,
        multiplier: Int = 1,
        listener: (Int) -> Unit
    ) {
        seekBar.progress = prefs.getInt(key, def) / multiplier
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                set(key, progress * multiplier)
                listener(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (serverValue) onServerValueUpdate() else onClientValueUpdate()
            }
        })
    }

    //server values
    var nearby
        get() = prefs.getBoolean("nearby", true)
        set(value) {
            set("nearby", value)
            onServerValueUpdate()
        }
    var socket
        get() = prefs.getBoolean("socket", false)
        set(value) {
            set("socket", value)
            onServerValueUpdate()
        }
    var multiDevice
        get() = prefs.getBoolean("multidevice", false)
        set(value) {
            set("multidevice", value)
        }
    var socketOnline
        get() = prefs.getBoolean("socketonline", false)
        set(value) {
            set("socketonline", value)
        }
    var emailConnection
        get() = prefs.getBoolean("emailconnection", false)
        set(value) {
            set("emailconnection", value)
        }
    val networkStrategy
        get() = if (multiDevice) Strategy.P2P_STAR else Strategy.P2P_POINT_TO_POINT

    var mediaSync
        get() = prefs.getBoolean("mediasync", false)
        set(value) {
            set("mediasync", value)
        }
    var mediaClientOnly
        get() = prefs.getBoolean("mediaclientonly", false)
        set(value) {
            set("mediaclientonly", value)
        }
    var audioStream
        get() = prefs.getBoolean("audiostream", false)
        set(value) {
            set("audiostream", value)
        }

    var audioStreamMic
        get() = prefs.getBoolean(
            "audiostreammic",
            false
        ) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        set(value) {
            set("audiostreammic", value)
        }

    var audioSample
        get() = prefs.getInt("audiosample", 8000)
        set(value) {
            set("audiosample", value)
        }

    var triggerButtons
        get() = prefs.getBoolean("trigger", false)
        set(value) {
            set("trigger", value)
        }

    var notiShare
        get() = prefs.getBoolean("notishare", false)
        set(value) {
            set("notishare", value)
        }
    var notiReply
        get() = prefs.getBoolean("notireply", false)
        set(value) {
            set("notireply", value)
        }

    //client values
    var allowMediaSync
        get() = prefs.getBoolean("allowmediasync", false)
        set(value) {
            set("allowmediasync", value)
        }
    var audioVolume
        get() = prefs.getInt("audiovolume", 100)
        set(value) {
            set("audiovolume", value)
        }
    var postNotifications
        get() = prefs.getBoolean("postnotifications", false)
        set(value) {
            set("postnotifications", value)
        }

    //connection channel
    val nearby_advertise = "${context.packageName}.connect"

    //firestore
    val firestore = FirebaseFirestore.getInstance().collection("users")
    companion object {
        //constants
        var AUDIO_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        val REQUEST_CODE_CAPTURE_PERM = 156
        var appState = AppState.IDLE
            set(value) {
                field = value
                onAppStateChanged()
                println("App state changed to $value")
            }
        var onAppStateChanged = {}
        var connectedServer: EndpointInfo? = null
            set(value) {
                field = value
                if (value != null) {
                    appState = AppState.ACCESSING
                    onConnectionToServer()
                } else {
                    appState = AppState.IDLE
                    onDisconnectionFromServer()
                }
            }
        var onConnectionToServer = {}
        var onDisconnectionFromServer = {}
        var connectedNearbyClients = object : ArrayList<EndpointInfo>() {
            override fun add(element: EndpointInfo): Boolean {
                val x = super.add(element)
                onNearbyClientAdded()
                if (size > 1) appState = AppState.CONNECTED
                return x
            }

            override fun remove(element: EndpointInfo): Boolean {
                val x = super.remove(element)
                onNearbyClientRemoved()
                return x
            }
        }
        var connectedSocketClients = object : ArrayList<SocketEndpointInfo>() {
            override fun add(element: SocketEndpointInfo): Boolean {
                val x = super.add(element)
                onSocketClientAdded()
                if (size > 0) appState = AppState.CONNECTED
                return x
            }

            override fun remove(element: SocketEndpointInfo): Boolean {
                val x = super.remove(element)
                onSocketClientRemoved()
                return x
            }
        }
        var onNearbyClientAdded = {}
        var onNearbyClientRemoved = {}
        var onSocketClientAdded = {}
        var onSocketClientRemoved = {}

        lateinit var runningServer : ServerThread
        var localIp = "none"
        var localport: Int = 0
        var onlineIp = ""
        var onlinePort = 0
        var onSocketError: (Exception) -> Unit = {}

        val allClients
            get() = connectedNearbyClients + connectedSocketClients
        val connectionText: String
            get() {
                return when (appState) {
                    AppState.CONNECTED -> "Connected to " + if (allClients.size > 1) "${allClients.size} devices" else "${allClients[0].name}"
                    AppState.ACCESSING -> "Accessing ${connectedServer?.name}"
                    else -> ""
                }
            }

        enum class AppState {
            IDLE,
            LOADING,
            PERMS,
            WAITING,
            LOOKING,
            CONNECTED,
            ACCESSING
        }

        var ngrokClient: Any? = null
    }
}