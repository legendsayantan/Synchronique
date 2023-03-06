package com.legendsayantan.sync.models

import com.legendsayantan.sync.workers.socket.SenderThread

/**
 * @author legendsayantan
 */
class SocketEndpointInfo(name:String,var senderThread: SenderThread) : EndpointInfo(senderThread.socket.inetAddress.hostAddress as String, name,senderThread.socket.port.toString()) {
}