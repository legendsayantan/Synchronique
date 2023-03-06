package com.legendsayantan.sync.workers.socket

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException

/**
 * @author legendsayantan
 */
class ReceiverThread(val socket: Socket,val lock : Any, var onDataFound: (String) -> Unit = {}) : Thread() {
    override fun run() {
        val bufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))
        while (!socket.isClosed) {
            try {
                val data = bufferedReader.readLine()
                if(data!=null && data!="null")onDataFound(data)
            }catch (ex: SocketException){
                ex.printStackTrace()
                socket.close()
            }
        }
        interrupt()
    }
}