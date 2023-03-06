package com.legendsayantan.sync.workers.socket

import android.content.Context
import androidx.core.content.ContextCompat
import com.legendsayantan.sync.workers.Network
import com.legendsayantan.sync.workers.Values
import java.net.*
import java.util.*

/**
 * @author legendsayantan
 */
class ServerThread(val context: Context) : Thread() {
    lateinit var serverSocket : ServerSocket
    var senders = ArrayList<SenderThread>()
    var port = 0
    var lock = Object()
    override fun run() {
        try {
            serverSocket = ServerSocket(0)
            port = serverSocket.localPort
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress && inetAddress.isSiteLocalAddress) {
                        if(inetAddress.hostAddress?.startsWith("192.168.") == true){
                            Values.localIp = inetAddress.hostAddress as String
                        }
                    }
                }
            }
            ContextCompat.getMainExecutor(context).execute {
                onReady(port)
            }
        } catch (ex: SocketException) {
            ContextCompat.getMainExecutor(context).execute {
                onError(ex)
            }
        }
        while (!serverSocket.isClosed) {
            try {
                val clientSocket = serverSocket.accept()
                ReceiverThread(clientSocket,lock).apply {
                    onDataFound = {
                        ContextCompat.getMainExecutor(context).execute {
                            onReceive(it,clientSocket)
                        }
                    }
                    start()
                }
                val socketSenderThread = SenderThread(clientSocket,lock).apply {
                    onClosed = {
                        ContextCompat.getMainExecutor(context).execute {
                            onDisconnect(this)
                        }
                        senders.remove(this)
                    }
                    start()
                }
                senders.add(socketSenderThread)
                ContextCompat.getMainExecutor(context).execute {
                    onConnect(socketSenderThread)
                }
            }catch (ex: Exception) {
                ContextCompat.getMainExecutor(context).execute {
                    onError(ex)
                }
            }
        }
    }
    var onReady : (Int) -> Unit = {}
    var onReceive : (String,Socket) -> Unit = { data: String, socket: Socket -> }
    var onConnect : (SenderThread) -> Unit = {  }
    var onDisconnect : (SenderThread) -> Unit = {  }
    var onError : (java.lang.Exception) -> Unit = {}
    fun send(data: String) {
        senders.forEach {
            it.push(data)
        }
    }
    override fun interrupt() {
        serverSocket.close()
        senders.forEach {
            it.socket.close()
        }
        super.interrupt()
    }
}