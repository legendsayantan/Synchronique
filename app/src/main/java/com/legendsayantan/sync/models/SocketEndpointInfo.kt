package com.legendsayantan.sync.models

import java.net.Socket

/**
 * @author legendsayantan
 */
class SocketEndpointInfo(name:String,var socket: Socket) : EndpointInfo(socket.inetAddress.hostAddress as String, name,socket.port.toString()) {
}