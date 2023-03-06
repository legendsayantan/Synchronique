package com.legendsayantan.sync.workers.socket

import java.io.PrintWriter
import java.net.Socket

/**
 * @author legendsayantan
 */
class SenderThread(var socket: Socket,val lock:Any,var onClosed :()->Unit = {}): Thread() {
    var data = mutableListOf<String>()
    override fun run() {
        val printWriter = PrintWriter(socket.getOutputStream(), true)
        while (!socket.isClosed) {
            if (data.isNotEmpty()) {
                printWriter.println(data[0])
                data.removeAt(0)
            }
            if (socket.isClosed) {
                break
            }
        }
        onClosed()
    }
    fun push(data: String){
        this.data.add(data)
    }
}