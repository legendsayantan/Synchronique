package com.legendsayantan.sync.interfaces

/**
 * @author legendsayantan
 */
class ServerConfig(
    var multiDevice : Boolean,
    var clientConfig: ClientConfig,
    var mediaClientOnly : Boolean,
    var audioMic: Boolean,
    var notiReply: Boolean
) {
}