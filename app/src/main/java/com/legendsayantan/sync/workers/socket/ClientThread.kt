package com.legendsayantan.sync.workers.socket

import android.content.Context
import androidx.core.content.ContextCompat
import java.net.Socket

/**
 * @author legendsayantan
 */
class ClientThread(val context: Context,val SERVER_IP_ADDRESS: String,val PORT_NUMBER: Int) : Thread() {
    private lateinit var socket : Socket
    lateinit var recvr : ReceiverThread
    lateinit var sender : SenderThread
    var lock = Object()
    override fun run() {
        try {
            socket = Socket(SERVER_IP_ADDRESS, PORT_NUMBER)
            recvr = ReceiverThread(socket,lock).apply {
                onDataFound = {
                    ContextCompat.getMainExecutor(context).execute{
                        onReceive(it)
                    }
                }
                start()
            }
            sender = SenderThread(socket,lock).apply {
                onClosed = {
                    ContextCompat.getMainExecutor(context).execute {
                        onDisconnect()
                    }
                    interrupt()
                }
                start()
            }
            ContextCompat.getMainExecutor(context).execute {
                onConnect(sender)
            }
        }catch (ex: Exception) {
            ContextCompat.getMainExecutor(context).execute {
                onError(ex)
            }
        }
    }
    var onReceive : (String) -> Unit = {}
    var onConnect : (SenderThread) -> Unit = {}
    var onDisconnect : () -> Unit = {}
    var onError : (Exception) -> Unit = {}
    fun send(data: String){
        sender.push(data)
    }

    override fun interrupt() {
        recvr.interrupt()
        sender.interrupt()
        socket.close()
        super.interrupt()
    }
}