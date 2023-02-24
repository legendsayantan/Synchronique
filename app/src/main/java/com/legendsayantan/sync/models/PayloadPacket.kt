package com.legendsayantan.sync.models

import EncryptionManager
import com.google.gson.Gson

/**
 * @author legendsayantan
 */
class PayloadPacket(var payloadType: PayloadType, var data: Any) {
    companion object {
        private val gson = Gson()
        private fun toJSON(packet: PayloadPacket): String {
            return gson.toJson(packet)
        }

        private fun fromJson(json: String): PayloadPacket {
            val packet = gson.fromJson(json, PayloadPacket::class.java)
            packet.data = gson.fromJson(
                gson.toJson(packet.data), when (packet.payloadType) {
                    PayloadType.CONFIG_PACKET -> ClientConfig::class.java
                    PayloadType.MEDIA_SYNC_PACKET -> MediaSyncPacket::class.java
                    PayloadType.MEDIA_ACTION_PACKET -> MediaActionPacket::class.java
                    PayloadType.TRIGGER_PACKET -> TriggerPacket::class.java
                    PayloadType.NOTIFICATION_PACKET -> NotificationData::class.java
                    PayloadType.NOTIFICATION_REPLY -> NotificationReply::class.java
                    PayloadType.DISCONNECT -> ByteArray::class.java
                    PayloadType.UNKNOWN -> ByteArray::class.java
                }
            )
            return packet
        }

        enum class PayloadType {
            UNKNOWN,
            CONFIG_PACKET,
            MEDIA_SYNC_PACKET,
            MEDIA_ACTION_PACKET,
            TRIGGER_PACKET,
            NOTIFICATION_PACKET,
            NOTIFICATION_REPLY,
            DISCONNECT,
        }

        fun toEncBytes(data: PayloadPacket): ByteArray {
            return EncryptionManager().encrypt(toJSON(data), EncryptionManager.cachedKey!!)
                .toByteArray()
        }

        fun fromEncBytes(data: ByteArray): PayloadPacket {
            return fromJson(
                EncryptionManager().decrypt(
                    String(data),
                    EncryptionManager.cachedKey!!
                )
            )
        }
    }
}

