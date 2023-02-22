package com.legendsayantan.sync.workers

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.legendsayantan.sync.models.*

/**
 * @author legendsayantan
 */
class Network(var context: Context) {
    val nearby = Nearby.getConnectionsClient(context)
    fun push(data:Any){
        val pl = Payload.fromBytes(PayloadPacket.toEncBytes(PayloadPacket(
            when (data) {
                is ClientConfig -> PayloadPacket.Companion.PayloadType.CONFIG_PACKET
                is MediaActionPacket -> PayloadPacket.Companion.PayloadType.MEDIA_ACTION_PACKET
                is MediaSyncPacket -> PayloadPacket.Companion.PayloadType.MEDIA_SYNC_PACKET
                is TriggerPacket -> PayloadPacket.Companion.PayloadType.TRIGGER_PACKET
                is NotificationData -> PayloadPacket.Companion.PayloadType.NOTIFICATION_PACKET
                is NotificationReply -> PayloadPacket.Companion.PayloadType.NOTIFICATION_REPLY
                else -> PayloadPacket.Companion.PayloadType.UNKNOWN
            }, data
        )))
        when(Values.appState){
            Values.Companion.AppState.CONNECTED -> {
                for(client in Values.connectedClients){
                    nearby.sendPayload(client.id, pl)
                }
            }
            Values.Companion.AppState.ACCESSING -> {
                nearby.sendPayload(Values.connectedServer!!.id, pl)
            }
            else -> {}
        }

    }
    fun disconnect(){
        when(Values.appState){
            Values.Companion.AppState.CONNECTED -> {
                for (endpoint in Values.connectedClients) {
                    nearby.sendPayload(
                        endpoint.id,
                        Payload.fromBytes(
                            PayloadPacket.toEncBytes(
                                PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT, ByteArray(0))
                            )
                        )
                    )
                    nearby.disconnectFromEndpoint(endpoint.id)
                }
            }
            Values.Companion.AppState.ACCESSING -> {
                nearby.sendPayload(
                    Values.connectedServer!!.id,
                    Payload.fromBytes(
                        PayloadPacket.toEncBytes(
                            PayloadPacket(PayloadPacket.Companion.PayloadType.DISCONNECT, ByteArray(0))
                        )
                    )
                )
                nearby.disconnectFromEndpoint(Values.connectedServer!!.id)
            }
            else -> {}
        }
        Nearby.getConnectionsClient(context).stopAllEndpoints()
    }
}