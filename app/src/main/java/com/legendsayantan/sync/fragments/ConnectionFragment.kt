package com.legendsayantan.sync.fragments

import EncryptionManager
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.legendsayantan.sync.MainActivity
import com.legendsayantan.sync.R
import com.legendsayantan.sync.adapters.NotificationListAdapter
import com.legendsayantan.sync.models.NotificationData
import com.legendsayantan.sync.models.NotificationReply
import com.legendsayantan.sync.models.SocketEndpointInfo
import com.legendsayantan.sync.services.*
import com.legendsayantan.sync.views.AskDialog
import com.legendsayantan.sync.views.ChatDialog
import com.legendsayantan.sync.views.ConnectionDialog
import com.legendsayantan.sync.workers.*
import java.net.Socket
import java.util.*

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
    lateinit var values: Values
    private lateinit var singleLookupButton: MaterialCardView
    private lateinit var multiLookupButton: MaterialCardView
    private lateinit var onlineLookupButton: MaterialCardView
    private lateinit var accessCard: View
    private lateinit var connectionCard: View
    lateinit var network: Network
    private var replyDialog: ChatDialog? = null

    var noticount = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
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

        var instance: ConnectionFragment? = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        instance = this
        network = Network(requireContext())
        values = Values(requireContext())
        accessCard = requireView().findViewById(R.id.accessCard)
        connectionCard = requireView().findViewById(R.id.connectCard)
        singleLookupButton = requireView().findViewById(R.id.singleCard)
        multiLookupButton = requireView().findViewById(R.id.multiCard)
        onlineLookupButton = requireView().findViewById(R.id.onlineCard)
        onlineLookupButton.findViewById<MaterialCardView>(R.id.startOnlineSearch).setOnClickListener {
            val editText = onlineLookupButton.findViewById<EditText>(R.id.email)
            if(editText.text.toString().isEmpty())return@setOnClickListener
            when (Values.appState) {
                Values.Companion.AppState.IDLE -> {
                    val portText = onlineLookupButton.findViewById<EditText>(R.id.port)
                    val text = editText.text.toString()
                    if (Patterns.EMAIL_ADDRESS.matcher(text).matches()) {
                        //email
                        values.firestore.document(text)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document != null) {
                                    val value = document.data?.get("ip") as String
                                    Thread{
                                        try {
                                            val ip = EncryptionManager().decrypt(value,portText.text.toString())
                                            val socket = Socket(ip, portText.text.toString().toInt())
                                            socket.getOutputStream().write(FirebaseAuth.getInstance().currentUser?.displayName?.toByteArray())
                                            Values.connectedServer = SocketEndpointInfo(text, socket)
                                        }catch (e: Exception) {
                                            portText.error = "Wrong port or user is not online"
                                        }
                                    }.start()
                                } else {
                                    editText.error = "No such user found"
                                }
                            }
                            .addOnFailureListener { exception ->
                                editText.error = "No such user found"
                            }
                    } else {
                        //ip
                        Thread{
                            try {
                                val ip = text
                                val socket = Socket(ip, portText.text.toString().toInt())
                                socket.getOutputStream().write(FirebaseAuth.getInstance().currentUser?.displayName?.toByteArray())
                                Values.connectedServer = SocketEndpointInfo(text, socket)
                            }catch (e: Exception) {
                                portText.error = "Wrong port or user is not online"
                            }
                        }.start()
                    }

                }
                else -> {

                }
            }
        }
        onlineLookupButton.findViewById<MaterialCardView>(R.id.cancelOnlineSearch).setOnClickListener {
            TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
            onlineLookupButton.findViewById<LinearLayout>(R.id.onlineCardLayout).visibility = View.GONE
            onlineLookupButton.findViewById<LinearLayout>(R.id.btn0).visibility = View.VISIBLE
            multiLookupButton.visibility = View.VISIBLE
            singleLookupButton.visibility = View.VISIBLE
        }
        ClientService.connectionConfigured = {
            TransitionManager.beginDelayedTransition(requireView() as ViewGroup)
            initAccessCard()
        }
        onlineLookupButton.setOnClickListener {
            when (Values.appState) {
                Values.Companion.AppState.IDLE -> {
                    multiLookupButton.visibility = View.GONE
                    singleLookupButton.visibility = View.GONE
                    TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
                    onlineLookupButton.findViewById<LinearLayout>(R.id.onlineCardLayout).visibility =
                        View.VISIBLE
                    onlineLookupButton.findViewById<LinearLayout>(R.id.btn0).visibility = View.GONE
                }
                Values.Companion.AppState.CONNECTED -> {
                    AskDialog(
                        requireActivity(),
                        "Stop your own network?",
                        {
                            WaitForConnectionService.instance.stopSelf()
                            ServerService.instance?.stopSelf()
                        })
                }
                Values.Companion.AppState.WAITING -> {
                    AskDialog(
                        requireActivity(),
                        "Stop your own network?",
                        {
                            WaitForConnectionService.instance.stopSelf()
                            ServerService.instance?.stopSelf()
                        })
                }
                else -> {

                }
            }
        }
        singleLookupButton.setOnClickListener {
            if (singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility != View.VISIBLE) {
                //stop
                Nearby.getConnectionsClient(requireContext()).stopDiscovery()
                LookupService.instance.stopSelf()
                TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
                singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility = View.VISIBLE
                singleLookupButton.findViewById<LinearLayout>(R.id.stopLayout).visibility =
                    View.GONE
                Values.appState = Values.Companion.AppState.IDLE
                return@setOnClickListener
            }

            if (!(MainActivity.locationAccess && MainActivity.bluetoothAccess)) {
                setTicker(it.findViewById(R.id.btnText1), "Permissions not enabled", 3000)
                return@setOnClickListener
            }
            if (!MainActivity.isLocationEnabled(requireContext())) {
                setTicker(it.findViewById(R.id.btnText1), "Location not enabled", 3000)
                return@setOnClickListener
            }
            when (Values.appState) {
                Values.Companion.AppState.IDLE -> {
                    multiLookupButton.visibility = View.GONE
                    onlineLookupButton.visibility = View.GONE
                    discovery(false)
                    TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
                    singleLookupButton.findViewById<LinearLayout>(R.id.stopLayout).visibility =
                        View.VISIBLE
                    singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility = View.GONE
                }
                Values.Companion.AppState.CONNECTED -> {
                    AskDialog(
                        requireActivity(),
                        "Stop your own network?",
                        {
                            WaitForConnectionService.instance.stopSelf()
                            ServerService.instance?.stopSelf()
                        })
                }
                Values.Companion.AppState.WAITING -> {
                    AskDialog(
                        requireActivity(),
                        "Stop your own network?",
                        {
                            WaitForConnectionService.instance.stopSelf()
                            ServerService.instance?.stopSelf()
                        })
                }
                else -> {
                    singleLookupButton.findViewById<LinearLayout>(R.id.stopLayout).visibility =
                        View.GONE
                    singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility =
                        View.VISIBLE
                }
            }
        }
        multiLookupButton.setOnClickListener {
            if (multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility != View.VISIBLE) {
                Nearby.getConnectionsClient(requireContext()).stopDiscovery()
                LookupService.instance.stopSelf()
                TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
                multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility = View.VISIBLE
                multiLookupButton.findViewById<LinearLayout>(R.id.stopLayout2).visibility =
                    View.GONE
                Values.appState = Values.Companion.AppState.IDLE
                return@setOnClickListener
            }
            if (!(MainActivity.locationAccess && MainActivity.bluetoothAccess)) {
                setTicker(it.findViewById(R.id.btnText2), "Permissions not enabled", 3000)
                return@setOnClickListener
            }
            if (!MainActivity.isLocationEnabled(requireContext())) {
                setTicker(it.findViewById(R.id.btnText2), "Location not enabled", 3000)
                return@setOnClickListener
            }
            when (Values.appState) {
                Values.Companion.AppState.IDLE -> {
                    singleLookupButton.visibility = View.GONE
                    onlineLookupButton.visibility = View.GONE
                    discovery(true)
                    TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
                    multiLookupButton.findViewById<LinearLayout>(R.id.stopLayout2).visibility =
                        View.VISIBLE
                    multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility = View.GONE
                }
                Values.Companion.AppState.CONNECTED -> {
                    AskDialog(
                        requireActivity(),
                        "Stop your own network?",
                        {
                            WaitForConnectionService.instance.stopSelf()
                            ServerService.instance?.stopSelf()
                        })
                }
                Values.Companion.AppState.WAITING -> {
                    AskDialog(
                        requireActivity(),
                        "Stop your own network?",
                        {
                            WaitForConnectionService.instance.stopSelf()
                            ServerService.instance?.stopSelf()
                        })
                }
                else -> {
                    multiLookupButton.findViewById<LinearLayout>(R.id.stopLayout2).visibility =
                        View.GONE
                    multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility =
                        View.VISIBLE
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ClientService.postNoti = values.postNotifications
    }
    override fun onResume() {
        super.onResume()
        ClientService.postNoti = false
        values.bind(requireView().findViewById(R.id.mediaSwitch), "allowmediasync", true, serverValue = false){
            MediaWorker.clientSyncToServer = values.allowMediaSync
        }
        values.bind(requireView().findViewById(R.id.volumeBar), "audiovolume",100,false){
            AudioWorker.volume = values.audioVolume
            requireView().findViewById<TextView>(R.id.volumeText).text = values.audioVolume.toString()
        }
        requireView().findViewById<TextView>(R.id.volumeText).text = values.audioVolume.toString()
        values.bind(requireView().findViewById(R.id.realtimeNoti), "postnotifications", false,
            serverValue = false
        )
        Values.onAppStateChanged = {
            when (Values.appState) {
                Values.Companion.AppState.ACCESSING -> {
                    TransitionManager.beginDelayedTransition(requireView() as ViewGroup)
                    singleLookupButton.visibility = View.GONE
                    multiLookupButton.visibility = View.GONE
                    onlineLookupButton.visibility = View.GONE
                    accessCard.visibility = View.VISIBLE
                    connectionCard.visibility = View.GONE
                    try {
                        initAccessCard()
                    } catch (_: Exception) {
                    }
                }
                Values.Companion.AppState.LOOKING -> {
                    accessCard.visibility = View.GONE
                    if (LookupService.lookupStrategy == Strategy.P2P_POINT_TO_POINT) {
                        singleLookupButton.visibility = View.VISIBLE
                        multiLookupButton.visibility = View.GONE
                        singleLookupButton.findViewById<LinearLayout>(R.id.stopLayout).visibility =
                            View.VISIBLE
                        singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility =
                            View.GONE
                        setStatusText(
                            requireView().findViewById(R.id.discoverType),
                            "Single-connection",
                            true
                        )
                    } else if(LookupService.lookupStrategy == Strategy.P2P_STAR){
                        multiLookupButton.visibility = View.VISIBLE
                        singleLookupButton.visibility = View.GONE
                        multiLookupButton.findViewById<LinearLayout>(R.id.stopLayout2).visibility =
                            View.VISIBLE
                        multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility =
                            View.GONE
                        setStatusText(
                            requireView().findViewById(R.id.discoverType),
                            "Multi-connection",
                            true
                        )
                    }else{
                        singleLookupButton.visibility = View.GONE
                        multiLookupButton.visibility = View.GONE
                        onlineLookupButton.visibility = View.VISIBLE
                        singleLookupButton.findViewById<LinearLayout>(R.id.stopLayout).visibility =
                            View.GONE
                        singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility =
                            View.VISIBLE
                        multiLookupButton.findViewById<LinearLayout>(R.id.stopLayout2).visibility =
                            View.GONE
                        multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility =
                            View.VISIBLE
                    }
                }
                else -> {
                    singleLookupButton.visibility = View.VISIBLE
                    multiLookupButton.visibility = View.VISIBLE
                    accessCard.visibility = View.GONE
                    singleLookupButton.findViewById<LinearLayout>(R.id.stopLayout).visibility =
                        View.GONE
                    singleLookupButton.findViewById<LinearLayout>(R.id.btn1).visibility =
                        View.VISIBLE
                    multiLookupButton.findViewById<LinearLayout>(R.id.stopLayout2).visibility =
                        View.GONE
                    multiLookupButton.findViewById<LinearLayout>(R.id.btn2).visibility =
                        View.VISIBLE
                    connectionCard.visibility = View.VISIBLE
                    connectionCard.findViewById<LinearLayout>(R.id.onlineCardLayout).visibility =
                        View.GONE
                    connectionCard.findViewById<LinearLayout>(R.id.btn0).visibility = View.VISIBLE
                    setStatusText(requireView().findViewById(R.id.discoverType), "", false)
                }
            }
        }
        Values.onAppStateChanged()
    }

    private fun discovery(multiDevice: Boolean) {
        LookupService.lookupStrategy =
            if (multiDevice) Strategy.P2P_STAR else Strategy.P2P_POINT_TO_POINT
        if (Values.appState == Values.Companion.AppState.IDLE)
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
        if (!isAdded) return
        println("updating list")
        val list = ArrayList<String>()
        LookupService.endpoints.forEach {
            list.add(it.name ?: "no name")
        }
        updateSpecifiedList(requireView().findViewById(R.id.advertiser_list), list)
    }

    private fun updateSpecifiedList(listView: ListView, list: List<String>) {
        listView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list)
        listView.setOnItemClickListener { _, _, position, _ ->
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
                    requireContext().startForegroundService(intent)
                }) {}.show()
        }
    }

    @SuppressLint("SetTextI18n")
    fun initAccessCard() {
        val name = requireView().findViewById<TextView>(R.id.name)
        val hash = requireView().findViewById<TextView>(R.id.hash)
        val disconnectBtn = requireView().findViewById<MaterialCardView>(R.id.disconnectBtn)
        val mediaAccess = requireView().findViewById<View>(R.id.media_access)
        val audioAccess = requireView().findViewById<View>(R.id.audio_access)
        val triggerAccess = requireView().findViewById<View>(R.id.trigger_access)
        val notiAccess = requireView().findViewById<View>(R.id.noti_access)
        name.text = Values.connectedServer?.name
        Values.connectedServer?.uidHash.also {
            hash.text = it?.substring(0, it.length / 3) + " " + it?.substring(
                it.length / 3,
                it.length * 2 / 3
            ) + " " + it?.substring(it.length * 2 / 3, it.length)
        }
        disconnectBtn.setOnClickListener {
            AskDialog(requireActivity(),
                "Are you sure you want to disconnect?",
                {
                    network.disconnect()
                    Values.appState = Values.Companion.AppState.IDLE
                })
        }
        mediaAccess.visibility = if (ClientService.clientConfig.media) View.VISIBLE else View.GONE
        audioAccess.visibility = if (ClientService.clientConfig.audio) View.VISIBLE else View.GONE
        triggerAccess.visibility =
            if (ClientService.clientConfig.trigger) View.VISIBLE else View.GONE
        notiAccess.visibility = if (ClientService.clientConfig.noti) View.VISIBLE else View.GONE
        val cardList = java.util.ArrayList<MaterialCardView>()
        cardList.add(mediaAccess as MaterialCardView)
        cardList.add(audioAccess as MaterialCardView)
        cardList.add(triggerAccess as MaterialCardView)
        cardList.add(notiAccess as MaterialCardView)
        CardAnimator.initExpandableCards(cardList)
        //notification list
        val notiList = requireView().findViewById<ListView>(R.id.noti_list)
        notiList.adapter =
            NotificationListAdapter(requireContext(), ClientService.notificationDataList)
        ClientService.onNotificationUpdated = { notificationData: NotificationData, i: Int ->
            if (isAdded) {
                val firstVisibleItem = notiList.firstVisiblePosition
                val topOffset = notiList.getChildAt(0)?.top ?: 0
                notiList.adapter =
                    NotificationListAdapter(requireContext(), ClientService.notificationDataList)
                notiList.setSelectionFromTop(firstVisibleItem, topOffset)
            }else if(values.postNotifications){
                postNotification(notificationData)
            }
            if (replyDialog != null) {
                replyDialog?.newData(notificationData)
            }
        }
        notiList.setOnItemClickListener { parent, view, position, id ->
            if (!ClientService.notificationDataList[position].canReply) return@setOnItemClickListener
            replyDialog =
                ChatDialog(requireActivity(), ClientService.notificationDataList, position)
            replyDialog?.replyListener = { reply: String, key: String ->
                network.push(NotificationReply(reply, key))
            }
        }
    }
    fun postNotification(notificationData: NotificationData){
        val builder = Notification.Builder(
            requireContext(),
            Notifications(requireContext()).client_channel
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationData.title)
            .setContentText(notificationData.text)
            .setSubText(notificationData.app_id+if(notificationData.subtext.isNullOrEmpty())"" else " · "+notificationData.subtext)
            .setWhen(notificationData.time)
            .setShowWhen(true)
        (requireContext().getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager).notify(noticount, builder.build())
        noticount++
    }
    fun stopDiscover() {
        if (isAdded) {

        }
    }

    private fun setStatusText(textView: TextView, string: String?, fromTop: Boolean = true) {
        if (string == null) return
        requireActivity().runOnUiThread {
            textView.animate().alpha(0f)
                .translationY((if (fromTop) 1f else -1f) * textView.height.toFloat())
                .setDuration(250)
                .start()
        }
        Timer().schedule(object : TimerTask() {
            override fun run() {
                requireActivity().runOnUiThread {
                    textView.translationY = (if (fromTop) -1f else 1f) * textView.height.toFloat()
                    textView.text = string
                    textView.animate().alpha(1f).translationY(0f).setDuration(250).start()
                }
            }
        }, 250)
    }

    private fun setTicker(textView: TextView, string: String, ms: Long) {
        val x = textView.text
        TransitionManager.beginDelayedTransition(requireView() as ViewGroup?)
        setStatusText(textView, string, true)
        val s = Values.appState
        Timer().schedule(object : TimerTask() {
            override fun run() {
                requireActivity().runOnUiThread {
                    setStatusText(textView, x as String?, false)
                }
            }
        }, ms)
    }
}