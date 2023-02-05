package com.legendsayantan.sync.interfaces

import EncryptionManager
import com.google.gson.Gson

/**
 * @author legendsayantan
 */
class PayloadPacket(var payloadType: PayloadType, var data: Any) {
    companion object{
        private fun toJSON(packet: PayloadPacket): String {
            return Gson().toJson(packet)
        }
        private fun fromJson(json:String): PayloadPacket {
            val packet = Gson().fromJson(json, PayloadPacket::class.java)
            if(packet.payloadType == PayloadType.MEDIA_PACKET){
                packet.data = Gson().fromJson(Gson().toJson(packet.data), MediaPacket::class.java)
            }
            if(packet.payloadType == PayloadType.AUDIO_PACKET){
                packet.data = Gson().fromJson(Gson().toJson(packet.data), Int::class.java)
            }
            return packet
        }
        enum class PayloadType{
            MEDIA_PACKET,
            AUDIO_PACKET,
            DISCONNECT,
        }
        fun toEncBytes(data: PayloadPacket): ByteArray {
            return EncryptionManager().encrypt(toJSON(data), EncryptionManager.cachedKey!!).toByteArray()
        }
        fun fromEncBytes(data: ByteArray): PayloadPacket {
            return fromJson(EncryptionManager().decrypt(String(data), EncryptionManager.cachedKey!!))
        }
    }
}

