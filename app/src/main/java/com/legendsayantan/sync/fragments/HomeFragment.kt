package com.legendsayantan.sync.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.services.AdvertiserService
import com.legendsayantan.sync.services.DiscoverService
import com.legendsayantan.sync.services.SingularConnectionService
import kotlin.system.exitProcess


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeFragment() : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    lateinit var firebaseAuth: FirebaseAuth
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
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    @SuppressLint("CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        println("Home fragment Started")
        var textname = requireView().findViewById<TextView>(R.id.textname)
        textname.text = firebaseAuth.currentUser?.displayName ?: "";
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
            (requireActivity() as MainActivity).signOut()
        }
        view.findViewById<TextView>(R.id.killSwitch).setOnClickListener{
            exitProcess(0)
        }
        var uid = firebaseAuth.currentUser?.uid.hashCode().toString()
        requireView().findViewById<TextView>(R.id.hash).text =
            uid.substring(0, uid.length / 3) + " " + uid.substring(
                uid.length / 3,
                uid.length * 2 / 3
            ) + " " + uid.substring(uid.length * 2 / 3, uid.length)
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q){
            view.findViewById<RadioButton>(R.id.radioStream).isEnabled = false
            requireActivity().getSharedPreferences("default",Context.MODE_PRIVATE).edit().putBoolean("streamMedia",false).apply()
        }
        requireActivity().getSharedPreferences("default", Context.MODE_PRIVATE).getBoolean("streamMedia",true).let {
            view.findViewById<RadioButton>(R.id.radioStream).isChecked = it
            view.findViewById<RadioButton>(R.id.radioSync).isChecked = !it
        }
        view.findViewById<RadioButton>(R.id.radioSync).setOnClickListener {
            //store in shared prefs
            requireActivity().getSharedPreferences("default",Context.MODE_PRIVATE).edit().putBoolean("streamMedia",false).apply()
        }
        view.findViewById<RadioButton>(R.id.radioSync).setOnClickListener {
            //store in shared prefs
            requireActivity().getSharedPreferences("default",Context.MODE_PRIVATE).edit().putBoolean("streamMedia",true).apply()
        }
        permissions()

        if (MainActivity.isLocationEnabled(requireContext())) startAdvertise()
        AdvertiserService.advertise_start = ::stateUpdate
        AdvertiserService.advertise_stop = ::stateUpdate
        SingularConnectionService.connectionUpdate = ::stateUpdate
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    fun permissions() {
        requireView().findViewById<MaterialCardView>(R.id.networkCard).visibility = View.GONE
        val locationSwitch = requireView().findViewById<Switch>(R.id.locPermission);
        val bluetoothSwitch = requireView().findViewById<Switch>(R.id.bluetoothPermission);
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
        } else locationSwitch.setOnClickListener {
            (requireActivity() as MainActivity).askLocationPermission()
        }
        if (MainActivity.bluetoothAccess) {
            bluetoothSwitch.isChecked = true
            bluetoothSwitch.setOnClickListener {}
            bluetoothSwitch.isEnabled = false
        } else bluetoothSwitch.setOnClickListener {
            (requireActivity() as MainActivity).askBluetoothPermission()
        }
    }

    fun startAdvertise() {
        if (DiscoverService.DISCOVERING) requireActivity().stopService(
            Intent(
                requireContext(),
                DiscoverService::class.java
            )
        )
        if (!AdvertiserService.ADVERTISING) {
            if (MainActivity.locationAccess && MainActivity.bluetoothAccess && !AdvertiserService.ADVERTISING) {
                requireActivity().startForegroundService(
                    Intent(
                        requireContext(),
                        AdvertiserService::class.java
                    )
                )
            }
        }
        networkState()
    }

    private fun networkState() {
        var network =
            if (AdvertiserService.ADVERTISING) "Advertising" else if (DiscoverService.DISCOVERING) "Discovering" else "Invisible"
        if (SingularConnectionService.CONNECTED) network += " , Connected"
        requireView().findViewById<TextView>(R.id.networkText).text = "Network - $network"
    }

    private fun stateUpdate() {
        if (isAdded) {
            networkState()
        }
    }
}