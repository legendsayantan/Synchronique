package com.legendsayantan.sync.models

import com.legendsayantan.sync.workers.Values

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
    constructor(values: Values) : this(values.multiDevice,
        ClientConfig(values), values.mediaClientOnly, values.audioStreamMic, values.notiReply) {
    }
}