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
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.Tasks
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.interfaces.BasePacket
import com.legendsayantan.sync.interfaces.PayloadPacket
import com.legendsayantan.sync.services.AdvertiserService
import com.legendsayantan.sync.services.DiscoverService
import com.legendsayantan.sync.services.SingularConnectionService
import com.legendsayantan.sync.views.AppDialog

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
    lateinit var connectionList : ListView
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
        // TODO: Rename and change types and number of parameters
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
        connectionList = view.findViewById(R.id.device_list)
        println("Discover fragment Started")
        if (MainActivity.isLocationEnabled(requireContext())) discovery()
        else println("location not enabled")
    }

    private fun discovery() {
        if (AdvertiserService.ADVERTISING) {
            requireActivity().stopService(Intent(requireContext(), AdvertiserService::class.java))
        }
        if (MainActivity.locationAccess && MainActivity.bluetoothAccess) {
            requireActivity().startForegroundService(
                Intent(
                    requireContext(),
                    DiscoverService::class.java
                )
            )
            DiscoverService.endpoint_updated = ::updateList
        }
        updateConnection()
    }

    @SuppressLint("SetTextI18n")
    private fun updateList() {
        if(!isAdded)return
        val list = ArrayList<String>()
        DiscoverService.endpoints.forEach {
            list.add(it.name ?: "no name")
        }
        requireView().findViewById<ListView>(R.id.advertiser_list).adapter =
            ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, list)
        requireView().findViewById<ListView>(R.id.advertiser_list)
            .setOnItemClickListener { parent, view, position, id ->
                val endpoint = DiscoverService.endpoints[position]
                AppDialog(requireActivity(),
                    "Advertiser Details",
                    list[position],
                    endpoint.uidHash,
                    "Sync",
                    ArrayList(),
                    { ints: ArrayList<Int>, appDialog: AppDialog ->
                        SingularConnectionService.connectionInitiated = { appDialog.hide() }
                        SingularConnectionService.connectionUpdate = ::updateConnection
                        SingularConnectionService.ENDPOINT_ID = endpoint.endpointId
                        SingularConnectionService.ENDPOINT_NAME = endpoint.name.toString()
                        SingularConnectionService.CONNECTION_MODE =
                            SingularConnectionService.Companion.ConnectionMode.INITIATE
                        SingularConnectionService.ENDPOINT_HASH = endpoint.uidHash
                        SingularConnectionService.ACCESS = ints
                        val intent = Intent(requireContext(), SingularConnectionService::class.java)
                        requireActivity().startForegroundService(intent)
                    }) {}.show()
            }
    }
    private fun updateConnection() {
        val x = ArrayList<String>()
        if(SingularConnectionService.CONNECTED)x.add(SingularConnectionService.ENDPOINT_NAME)
        connectionList.adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_1,
            x
        )
        connectionList.setOnItemClickListener { parent, view, position, id ->
            AppDialog(requireActivity(),
                "Connection Details",
                SingularConnectionService.ENDPOINT_NAME,
                SingularConnectionService.ENDPOINT_HASH,
                "Disconnect",
                SingularConnectionService.ACCESS,
                { ints: ArrayList<Int>, appDialog: AppDialog ->
                    Nearby.getConnectionsClient(requireContext()).sendPayload(SingularConnectionService.ENDPOINT_ID, Payload.fromBytes(PayloadPacket.toEncBytes(PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT, BasePacket())))).onSuccessTask {
                        SingularConnectionService.connectionUpdate = {}
                        SingularConnectionService.connectionInitiated = {}
                        SingularConnectionService.ENDPOINT_ID = ""
                        SingularConnectionService.ENDPOINT_NAME = ""
                        SingularConnectionService.ACCESS = ArrayList()
                        Nearby.getConnectionsClient(requireContext()).disconnectFromEndpoint(SingularConnectionService.ENDPOINT_ID)
                        val intent = Intent(requireContext(), SingularConnectionService::class.java)
                        requireActivity().stopService(intent)
                        updateConnection()
                        appDialog.hide()
                        return@onSuccessTask Tasks.forResult(null)
                    }
                },{}).show()
        }
    }
}