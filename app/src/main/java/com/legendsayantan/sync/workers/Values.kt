package com.legendsayantan.sync.workers

import android.content.Context
import android.media.AudioFormat
import android.os.Build
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.SeekBar
import com.google.android.gms.nearby.connection.Strategy
import com.legendsayantan.sync.models.EndpointInfo

/**
 * @author legendsayantan
 */
class Values(context: Context) {
    val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
    var onUpdate = {}
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

    fun bind(switch: CompoundButton, key: String,default: Boolean = false, onChange: () -> Unit = {}){
        switch.isChecked = prefs.getBoolean(key, default)
        switch.setOnCheckedChangeListener { _, isChecked ->
            set(key, isChecked)
            onUpdate()
            onChange()
        }
        onChange()
    }

    fun bind(radioButton1: RadioButton, radioButton2: RadioButton, key: String,default: Boolean) {
        radioButton1.isChecked = prefs.getBoolean(key, default)
        radioButton2.isChecked = !prefs.getBoolean(key, default)
        radioButton1.setOnCheckedChangeListener { _, isChecked ->
            set(key, isChecked)
            onUpdate()
        }
        radioButton2.setOnCheckedChangeListener { _, isChecked ->
            set(key, !isChecked)
            onUpdate()
        }
    }

    fun bind(seekBar: SeekBar, key: String, def: Int, listener: (Int) -> Unit) {
        seekBar.progress = prefs.getInt(key, def)/1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                set(key, progress * 1000)
                listener(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onUpdate()
            }
        })
    }
    var nearby
        get() = prefs.getBoolean("nearby", true)
        set(value) {
            set("nearby", value)
        }
    var socket
        get() = prefs.getBoolean("socket", false)
        set(value) {
            set("socket", value)
        }

    var multiDevice
        get() = prefs.getBoolean("multidevice", false)
        set(value) {
            set("multidevice", value)
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



    //connection channel
    val nearby_advertise = "${context.packageName}.connect"

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
                if (value != null) onConnectionToServer()
                else onDisconnectionFromServer()
            }
        var onConnectionToServer = {}
        var onDisconnectionFromServer = {}
        var connectedClients = object : ArrayList<EndpointInfo>() {
            override fun add(element: EndpointInfo): Boolean {
                val x = super.add(element)
                onClientAdded()
                return x
            }

            override fun remove(element: EndpointInfo): Boolean {
                val x = super.remove(element)
                onClientRemoved()
                return x
            }
        }
        var onClientAdded = {}
        var onClientRemoved = {}

        val connectionText: String
            get() {
                return when (appState) {
                    AppState.CONNECTED -> "Connected to " + if (connectedClients.size > 1) "${connectedClients.size} devices" else "${connectedClients[0].name}"
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
    }
}