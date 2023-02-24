package com.legendsayantan.sync.fragments

import android.annotation.SuppressLint
import android.content.Intent
import com.google.android.gms.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.nearby.Nearby
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.models.ServerConfig
import com.legendsayantan.sync.services.ClientService
import com.legendsayantan.sync.services.LookupService
import com.legendsayantan.sync.services.ServerService
import com.legendsayantan.sync.services.WaitForConnectionService
import com.legendsayantan.sync.views.AskDialog
import com.legendsayantan.sync.workers.Values
import com.legendsayantan.sync.workers.CardAnimator
import com.legendsayantan.sync.workers.Network
import com.legendsayantan.sync.workers.PermissionManager
import java.util.*
import kotlin.collections.ArrayList


// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeFragment() : Fragment() {
    private var param1: String? = null
    private var param2: String? = null
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var values: Values
    lateinit var startBtn: MaterialCardView
    var firstTime = true
    lateinit var network: Network
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment HomeFragment.
         */
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }

        var latestInstance: HomeFragment? = null
    }

    @SuppressLint("CutPasteId", "SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        latestInstance = this
        network = Network(requireContext())
        println("Home fragment Started")
        //initialisations
        values = Values(requireContext())
        permissions()
        startBtn = requireView().findViewById(R.id.startBtn)
        //appStateUpdate()
        Values.onAppStateChanged = {
            values.onUpdate()
            appStateUpdate()
        }

        val textname = requireView().findViewById<TextView>(R.id.textname)
        textname.text = firebaseAuth.currentUser?.displayName ?: ""
        requireView().findViewById<MaterialCardView>(R.id.welcomeCard).setOnClickListener {
            if (textname.text.equals(firebaseAuth.currentUser?.displayName)) {
                textname.animate().translationX(-1 * textname.width.toFloat()).alpha(0F)
                    .setDuration(500).withEndAction {
                        textname.text = firebaseAuth.currentUser?.email
                        textname.translationX = textname.width.toFloat()
                        textname.animate().translationX(0F).alpha(1F).setDuration(500).start()
                    }
            } else {
                textname.animate().translationX(textname.width.toFloat()).alpha(0F).setDuration(500)
                    .withEndAction {
                        textname.text = firebaseAuth.currentUser?.displayName
                        textname.translationX = -1 * textname.width.toFloat()
                        textname.animate().translationX(0F).alpha(1F).setDuration(500).start()
                    }
            }
        }
        requireView().findViewById<ImageView>(R.id.signOut).setOnClickListener {
            AskDialog(requireActivity(), "Sign out of your account ?", {
                (requireActivity() as MainActivity).signOut()
            })
        }
        val uid = firebaseAuth.currentUser?.uid.hashCode().toString()
        requireView().findViewById<TextView>(R.id.hash).text =
            uid.substring(0, uid.length / 3) + " " + uid.substring(
                uid.length / 3,
                uid.length * 2 / 3
            ) + " " + uid.substring(uid.length * 2 / 3, uid.length)
        if (firstTime) {
            firstTime = false
            CardAnimator.staggerList(requireView().findViewById(R.id.homeList))
        }
    }

    @SuppressLint("CutPasteId")
    override fun onResume() {
        super.onResume()
        //bindings
        values.onUpdate = onUpdate@{
            if (Values.appState == Values.Companion.AppState.IDLE) {
                WaitForConnectionService.serverConfig = ServerConfig(values)
                if(isAdded)requireView().findViewById<CheckBox>(R.id.multidevice).isEnabled = true
                println("serverConfig setup")
            } else if (Values.appState == Values.Companion.AppState.WAITING) {

            }
            if(!isAdded)return@onUpdate
            requireView().findViewById<ImageView>(R.id.imageSync).animate().alpha(
                if (WaitForConnectionService.serverConfig!!.clientConfig.media) 1F else 0.3F
            ).setDuration(250).start()
            requireView().findViewById<ImageView>(R.id.imageAudio).animate().alpha(
                if (WaitForConnectionService.serverConfig!!.clientConfig.audio) 1F else 0.3F
            ).setDuration(250).start()
            requireView().findViewById<ImageView>(R.id.imageTrigger).animate().alpha(
                if (WaitForConnectionService.serverConfig!!.clientConfig.trigger) 1F else 0.3F
            ).setDuration(250).start()
            requireView().findViewById<ImageView>(R.id.imageNoti).animate().alpha(
                if (WaitForConnectionService.serverConfig!!.clientConfig.noti) 1F else 0.3F
            ).setDuration(250).start()
            requireView().findViewById<CheckBox>(R.id.multidevice).isEnabled =
                (Values.appState == Values.Companion.AppState.IDLE)
        }
        values.bind(requireView().findViewById(R.id.nearby), "nearby", true) {
            if (values.nearby && (!values.socket)) {
                requireView().findViewById<CompoundButton>(R.id.nearby).isEnabled = false
            } else if (values.socket && (!values.nearby)) {
                requireView().findViewById<CompoundButton>(R.id.socket).isEnabled = false
            } else {
                requireView().findViewById<CompoundButton>(R.id.nearby).isEnabled = true
                requireView().findViewById<CompoundButton>(R.id.socket).isEnabled = true
            }
            TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
            requireView().findViewById<View>(R.id.multidevice).visibility =
                if (values.nearby) View.VISIBLE else View.GONE
        }
        values.bind(requireView().findViewById(R.id.socket), "socket") {
            if (values.nearby && (!values.socket)) {
                requireView().findViewById<CompoundButton>(R.id.nearby).isEnabled = false
            } else if (values.socket && (!values.nearby)) {
                requireView().findViewById<CompoundButton>(R.id.socket).isEnabled = false
            } else {
                requireView().findViewById<CompoundButton>(R.id.nearby).isEnabled = true
                requireView().findViewById<CompoundButton>(R.id.socket).isEnabled = true
            }
        }
        values.bind(requireView().findViewById(R.id.multidevice), "multidevice")
        values.bind(requireView().findViewById(R.id.media), "mediasync")
        values.bind(requireView().findViewById(R.id.media_client_only), "mediaclientonly")
        values.bind(requireView().findViewById(R.id.audio), "audiostream")
        values.bind(requireView().findViewById(R.id.trigger), "trigger")
        values.bind(requireView().findViewById(R.id.noti), "notishare")
        values.bind(requireView().findViewById(R.id.noti_reply), "notireply")
        values.bind(
            requireView().findViewById(R.id.audio_mic),
            requireView().findViewById(R.id.audio_internal),
            "audiostreammic",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        )
        values.bind(requireView().findViewById(R.id.quality), "audiosample", 8000) {
            requireView().findViewById<TextView>(R.id.seekValue).text = it.toString()
        }
        requireView().findViewById<TextView>(R.id.seekValue).text =
            (values.audioSample / 1000).toString()
        requireView().findViewById<RadioButton>(R.id.audio_internal).isEnabled =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        values.onUpdate()
        requireView().findViewById<ImageView>(R.id.info).setOnClickListener {
            AskDialog(requireActivity(),
                (if (values.nearby) resources.getString(R.string.nearby_info) + "\n" else "") +
                        (if (values.socket) resources.getString(R.string.socket_info) else ""),
                {},
                {},
                false
            )
        }

        //Card Animations
        val cardList = ArrayList<MaterialCardView>()
        cardList.add(requireView().findViewById(R.id.mediaCard))
        cardList.add(requireView().findViewById(R.id.audioCard))
        cardList.add(requireView().findViewById(R.id.triggerCard))
        cardList.add(requireView().findViewById(R.id.notiCard))
        CardAnimator.initToggleableCards(cardList)

        //run
        startBtn.setOnClickListener {
            when (Values.appState) {
                Values.Companion.AppState.IDLE -> {
                    if (MainActivity.isLocationEnabled(requireContext())) {
                        startToAdvertise()
                    } else {
                        //ask to enable location
                        enableLocationSettings()
                    }
                }
                Values.Companion.AppState.PERMS -> if (MainActivity.isLocationEnabled(requireContext())) {
                    startToAdvertise()
                } else {
                    //ask to enable location
                    enableLocationSettings()
                }
                Values.Companion.AppState.LOADING -> return@setOnClickListener
                Values.Companion.AppState.WAITING -> {
                    requireContext().stopService(
                        Intent(
                            requireContext(),
                            WaitForConnectionService::class.java
                        )
                    )
                    requireContext().stopService(
                        Intent(
                            requireContext(),
                            ServerService::class.java
                        )
                    )
                }
                Values.Companion.AppState.LOOKING -> {
                    AskDialog(
                        requireActivity(),
                        "Stop looking for nearby devices and start your own server?",
                        {
                            Nearby.getConnectionsClient(requireContext()).stopDiscovery()
                            requireContext().stopService(
                                Intent(
                                    requireContext(),
                                    LookupService::class.java
                                )
                            )
                            Values.appState = Values.Companion.AppState.IDLE
                            if (MainActivity.isLocationEnabled(requireContext())) {
                                startToAdvertise()
                            } else {
                                //ask to enable location
                                enableLocationSettings()
                            }
                        })
                }
                Values.Companion.AppState.CONNECTED -> {
                    AskDialog(
                        requireActivity(),
                        "Network is connected. Do you want to disconnect and stop? ",
                        {
                            network.disconnect()
                            requireContext().stopService(
                                Intent(
                                    requireContext(),
                                    WaitForConnectionService::class.java
                                )
                            )
                            requireContext().stopService(
                                Intent(
                                    requireContext(),
                                    ServerService::class.java
                                )
                            )
                            Values.appState = Values.Companion.AppState.IDLE
                        })
                }
                Values.Companion.AppState.ACCESSING -> {
                    AskDialog(
                        requireActivity(),
                        "Do you want to stop accessing ${Values.connectedServer?.name} and start your own connection instead?",
                        {
                            requireContext().stopService(
                                Intent(
                                    requireContext(),
                                    ClientService::class.java
                                )
                            )
                        })
                }
            }
        }
        appStateUpdate(Values.appState == Values.Companion.AppState.IDLE)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun permissions() {
        val locationSwitch = requireView().findViewById<Switch>(R.id.locPermission)
        val bluetoothSwitch = requireView().findViewById<Switch>(R.id.bluetoothPermission)
        if (MainActivity.locationAccess) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationSwitch.isChecked = true
                locationSwitch.isEnabled = false
                if (MainActivity.bluetoothAccess) {
                    requireView().findViewById<MaterialCardView>(R.id.permissionCard).visibility =
                        View.GONE
                    requireView().findViewById<MaterialCardView>(R.id.networkCard).visibility =
                        View.VISIBLE
                }
            } else {
                requireView().findViewById<MaterialCardView>(R.id.permissionCard).visibility =
                    View.GONE
                requireView().findViewById<MaterialCardView>(R.id.networkCard).visibility =
                    View.VISIBLE
            }
        } else {
            requireView().findViewById<MaterialCardView>(R.id.networkCard).visibility = View.GONE
            locationSwitch.setOnClickListener {
                (requireActivity() as MainActivity).askLocationPermission()
            }
        }
        if (MainActivity.bluetoothAccess) {
            bluetoothSwitch.isChecked = true
            bluetoothSwitch.setOnClickListener {}
            bluetoothSwitch.isEnabled = false
        } else {
            requireView().findViewById<MaterialCardView>(R.id.networkCard).visibility = View.GONE
            bluetoothSwitch.setOnClickListener {
                (requireActivity() as MainActivity).askBluetoothPermission()
            }
        }
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun appStateUpdate(disableAnim: Boolean = false) {
        val text = when (Values.appState) {
            Values.Companion.AppState.IDLE -> "Idle"
            Values.Companion.AppState.PERMS -> "Checking permissions"
            Values.Companion.AppState.WAITING -> "Ready for connection"
            Values.Companion.AppState.CONNECTED -> Values.connectionText
            Values.Companion.AppState.LOOKING -> "Looking for nearby devices"
            Values.Companion.AppState.ACCESSING -> Values.connectionText
            else -> null
        }
        if (disableAnim) {
            requireView().findViewById<TextView>(R.id.networkText).text = text
        } else {
            setStatusText(text, Values.appState != Values.Companion.AppState.IDLE)
        }
        (startBtn.getChildAt(0) as ImageView).setImageDrawable(
            requireContext().getDrawable(
                when (Values.appState) {
                    Values.Companion.AppState.IDLE -> R.drawable.baseline_play_arrow_24
                    Values.Companion.AppState.LOADING -> R.drawable.baseline_hourglass_bottom_24
                    Values.Companion.AppState.PERMS -> R.drawable.baseline_hourglass_bottom_24
                    Values.Companion.AppState.WAITING -> R.drawable.baseline_close_24
                    Values.Companion.AppState.LOOKING -> R.drawable.baseline_play_arrow_24
                    Values.Companion.AppState.CONNECTED -> R.drawable.baseline_close_24
                    Values.Companion.AppState.ACCESSING -> R.drawable.baseline_play_arrow_24
                }
            )
        )
    }

    protected fun enableLocationSettings() {
        val locationRequest: LocationRequest = LocationRequest.Builder(0)
            .setPriority(Priority.PRIORITY_PASSIVE).build()
        val builder: LocationSettingsRequest.Builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        LocationServices
            .getSettingsClient(requireActivity())
            .checkLocationSettings(builder.build())
            .addOnSuccessListener(requireActivity()) {
                if (MainActivity.isLocationEnabled(requireContext())) {
                    startToAdvertise()
                } else {
                    locationNotAvailable()
                }
            }
            .addOnFailureListener(requireActivity()) {
                locationNotAvailable()
            }
    }

    private fun startToAdvertise() = if (Values(requireContext()).syncParams) {
        Values.appState = Values.Companion.AppState.PERMS
        WaitForConnectionService.serverConfig = ServerConfig(values)
        PermissionManager().ask(requireActivity(),WaitForConnectionService.serverConfig!!) {
            Timer().scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (isAdded) {
                        requireContext().startForegroundService(
                            Intent(
                                requireContext(),
                                WaitForConnectionService::class.java
                            )
                        )
                        requireContext().startForegroundService(
                            Intent(
                                requireContext(),
                                ServerService::class.java
                            )
                        )
                        cancel()
                    }
                }
            }, 1000, 500)
        }
    } else {
        nothingSelected()
    }

    private fun setStatusText(string: String?, fromTop: Boolean = true) {
        if (string.isNullOrEmpty()) return
        val textView = requireView().findViewById<TextView>(R.id.networkText)
        textView.animate().alpha(0f)
            .translationY((if (fromTop) 1f else -1f) * textView.height.toFloat()).setDuration(250)
            .start()
        Timer().schedule(object : TimerTask() {
            override fun run() {
                if (isAdded) requireActivity().runOnUiThread {
                    textView.translationY = (if (fromTop) -1f else 1f) * textView.height.toFloat()
                    textView.text = string
                    textView.animate().alpha(1f).translationY(0f).setDuration(250).start()
                }
            }
        }, 250)
    }

    private fun setTicker(string: String, ms: Long) {
        setStatusText(string, true)
        val s = Values.appState
        Values.appState = Values.Companion.AppState.LOADING
        Timer().schedule(object : TimerTask() {
            override fun run() {
                requireActivity().runOnUiThread {
                    Values.appState = s
                }
            }
        }, ms)
    }

    private fun nothingSelected() {
        requireView().findViewById<ImageView>(R.id.imageSync).animate().alpha(1F).scaleX(1.25f)
            .scaleY(1.25f).setDuration(250).setStartDelay(150).start()
        requireView().findViewById<MaterialCardView>(R.id.mediaCard).animate().scaleX(1.05f)
            .scaleY(1.05f).setDuration(250).setStartDelay(150).start()
        requireView().findViewById<ImageView>(R.id.imageAudio).animate().alpha(1F).scaleX(1.25f)
            .scaleY(1.25f).setDuration(250).setStartDelay(300).start()
        requireView().findViewById<MaterialCardView>(R.id.audioCard).animate().scaleX(1.05f)
            .scaleY(1.05f).setDuration(250).setStartDelay(300).start()
        requireView().findViewById<ImageView>(R.id.imageTrigger).animate().alpha(1F).scaleX(1.25f)
            .scaleY(1.25f).setDuration(250).setStartDelay(450).start()
        requireView().findViewById<MaterialCardView>(R.id.triggerCard).animate().scaleX(1.05f)
            .scaleY(1.05f).setDuration(250).setStartDelay(450).start()
        requireView().findViewById<ImageView>(R.id.imageNoti).animate().alpha(1F).scaleX(1.25f)
            .scaleY(1.25f).setDuration(250).setStartDelay(600).start()
        requireView().findViewById<MaterialCardView>(R.id.notiCard).animate().scaleX(1.05f)
            .scaleY(1.05f).setDuration(250).setStartDelay(600).start()
        setTicker("Nothing selected", 1500)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                requireActivity().runOnUiThread {
                    requireView().findViewById<ImageView>(R.id.imageSync).animate().alpha(0.3F)
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(150).start()
                    requireView().findViewById<MaterialCardView>(R.id.mediaCard).animate()
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(150).start()
                    requireView().findViewById<ImageView>(R.id.imageAudio).animate().alpha(0.3F)
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(300).start()
                    requireView().findViewById<MaterialCardView>(R.id.audioCard).animate()
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(300).start()
                    requireView().findViewById<ImageView>(R.id.imageTrigger).animate().alpha(0.3F)
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(450).start()
                    requireView().findViewById<MaterialCardView>(R.id.triggerCard).animate()
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(450).start()
                    requireView().findViewById<ImageView>(R.id.imageNoti).animate().alpha(0.3F)
                        .scaleX(1f).scaleY(1f).setDuration(250).setStartDelay(600).start()
                    requireView().findViewById<MaterialCardView>(R.id.notiCard).animate().scaleX(1f)
                        .scaleY(1f).setDuration(250).setStartDelay(600).start()

                }
            }
        }, 750)
    }

    private fun locationNotAvailable() {
        val btnImg = startBtn.getChildAt(0) as ImageView
        setTicker("Location not enabled", 2000)
        var count = 0
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                requireActivity().runOnUiThread {
                    if (count == 1) {
                        btnImg.setImageDrawable(requireContext().getDrawable(R.drawable.baseline_location_off_24))
                    }
                    startBtn.animate().alpha((count % 2).toFloat()).setDuration(500).start()
                    count++
                    if (count == 4) {
                        btnImg.setImageDrawable(requireContext().getDrawable(R.drawable.baseline_play_arrow_24))
                        cancel()
                    }
                }

            }
        }, 500, 500)
    }

    fun refreshFragment() {
        if (isAdded) {
            permissions()
        }
    }
}