package com.legendsayantan.sync.workers

import android.content.Context
import android.os.Build
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.SeekBar
import com.google.android.gms.nearby.connection.Strategy
import com.legendsayantan.sync.interfaces.EndpointInfo

/**
 * @author legendsayantan
 */
class Values(context: Context) {
    val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
    var onUpdate = {}
    val syncParams = mediaSync || audioStream || cameraShutter || notiShare
    fun set(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun set(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun bind(switch: CompoundButton, key: String) {
        switch.isChecked = prefs.getBoolean(key, false)
        switch.setOnCheckedChangeListener { _, isChecked ->
            set(key, isChecked)
            onUpdate()
        }
    }

    fun bind(radioButton1: RadioButton, radioButton2: RadioButton, key: String) {
        radioButton1.isChecked = prefs.getBoolean(key, false)
        radioButton2.isChecked = !prefs.getBoolean(key, false)
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
        seekBar.progress = prefs.getInt(key, def)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                set(key, progress*1000)
                listener(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onUpdate()
            }
        })
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
        ) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        set(value) {
            set("audiostreammic", value)
        }

    var audioSample
        get() = prefs.getInt("audiosample", 8000)
        set(value) {
            set("audiosample", value)
        }

    var cameraShutter
        get() = prefs.getBoolean("camerashutter", false)
        set(value) {
            set("camerashutter", value)
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

    //constants
    //notification channels


    //connection channel
    val nearby_advertise = "${context.packageName}.connect"

    companion object {
        var appState = AppState.IDLE
            set(value) {
                field = value
                onAppStateChanged()
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