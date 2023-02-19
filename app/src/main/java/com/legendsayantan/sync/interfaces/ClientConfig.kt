package com.legendsayantan.sync.interfaces

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
    constructor(values: Values) : this(values.mediaSync, values.audioStream, values.audioSample, values.cameraShutter, values.notiShare) {

    }
}