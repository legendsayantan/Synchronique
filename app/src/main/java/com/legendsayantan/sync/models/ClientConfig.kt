package com.legendsayantan.sync.models

import com.legendsayantan.sync.workers.Values

/**
 * @author legendsayantan
 */
class ClientConfig(
    var media: Boolean,
    var audio: Boolean,
    var audioSample: Int,
    var camera: Boolean,
    var noti: Boolean,
) {
    constructor(values: Values) : this(values.mediaSync, values.audioStream, values.audioSample, values.triggerButtons, values.notiShare) {

    }
}