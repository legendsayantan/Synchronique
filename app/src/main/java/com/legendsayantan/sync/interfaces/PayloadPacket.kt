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
            if(packet.payloadType == PayloadType.CONFIG_PACKET){
                packet.data = Gson().fromJson(Gson().toJson(packet.data), ClientConfig::class.java)
            }
            if(packet.payloadType == PayloadType.MEDIA_SYNC_PACKET){
                packet.data = Gson().fromJson(Gson().toJson(packet.data), MediaSyncPacket::class.java)
            }
            if(packet.payloadType == PayloadType.MEDIA_ACTION_PACKET){
                packet.data = Gson().fromJson(Gson().toJson(packet.data), MediaActionPacket::class.java)
            }
            return packet
        }
        enum class PayloadType{
            CONFIG_PACKET,
            MEDIA_SYNC_PACKET,
            MEDIA_ACTION_PACKET,
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

