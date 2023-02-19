package com.legendsayantan.sync.interfaces

/**
 * @author legendsayantan
 */
class MediaActionPacket(var action: Action,var executeAt: Long) {
    companion object{
        enum class Action{
            MEDIA_PLAY_PAUSE,
            MEDIA_PREV,
            MEDIA_NEXT
        }
    }
}