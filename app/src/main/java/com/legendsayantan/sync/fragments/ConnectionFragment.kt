package com.legendsayantan.sync.fragments

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.android.gms.nearby.connection.*
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.services.LookupService
import com.legendsayantan.sync.services.ClientService
import com.legendsayantan.sync.views.ConnectionDialog
import com.legendsayantan.sync.workers.Values

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ConnectionFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConnectionFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    lateinit var notificationManager: NotificationManager
    lateinit var notificationChannel: NotificationChannel
    lateinit var builder: Notification.Builder

    var noticount = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
        notificationChannel = NotificationChannel(
            "${requireContext().packageName}.request",
            "Requests",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.BLUE
        notificationChannel.enableVibration(false)
        notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_connection, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ConnectionFragment.
         */
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ConnectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Values.onAppStateChanged = {

        }
        if (MainActivity.isLocationEnabled(requireContext()) && Values.appState==Values.Companion.AppState.IDLE) discovery()
        else println("location not enabled")
    }

    private fun discovery() {
        if (Values.appState==Values.Companion.AppState.IDLE)
        if (MainActivity.locationAccess && MainActivity.bluetoothAccess) {
            requireActivity().startForegroundService(
                Intent(
                    requireContext(),
                    LookupService::class.java
                )
            )
            LookupService.endpoint_updated = ::updateList
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateList() {
        if(!isAdded)return
        println("updating list")
        val list = ArrayList<String>()
        LookupService.endpoints.forEach {
            list.add(it.name ?: "no name")
        }
        requireView().findViewById<ListView>(R.id.advertiser_list).adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list)
        requireView().findViewById<ListView>(R.id.advertiser_list)
            .setOnItemClickListener { parent, view, position, id ->
                val endpoint = LookupService.endpoints[position]
                ConnectionDialog(requireActivity(),
                    "Advertiser Details",
                    list[position],
                    endpoint.uidHash,
                    "Connect",
                    { connectionDialog: ConnectionDialog ->
                        connectionDialog.d.findViewById<TextView>(R.id.btnText).text =
                            "Requesting..."
                        ClientService.connectionCanceled = { connectionDialog.hide() }
                        Values.onConnectionToServer = {
                            connectionDialog.hide()
                            Values.onConnectionToServer = {}
                        }
                        ClientService.serverEndpoint = endpoint
                        val intent = Intent(requireContext(), ClientService::class.java)
                        requireActivity().startForegroundService(intent)
                    }) {}.show()
            }
    }

}