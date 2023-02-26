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
    val values = Values(context)
    fun push(data: Any) {
        val bytes = PayloadPacket.toEncBytes(
            PayloadPacket(
                when (data) {
                    is ClientConfig -> PayloadPacket.Companion.PayloadType.CONFIG_PACKET
                    is MediaActionPacket -> PayloadPacket.Companion.PayloadType.MEDIA_ACTION_PACKET
                    is MediaSyncPacket -> PayloadPacket.Companion.PayloadType.MEDIA_SYNC_PACKET
                    is TriggerPacket -> PayloadPacket.Companion.PayloadType.TRIGGER_PACKET
                    is NotificationData -> PayloadPacket.Companion.PayloadType.NOTIFICATION_PACKET
                    is NotificationReply -> PayloadPacket.Companion.PayloadType.NOTIFICATION_REPLY
                    else -> PayloadPacket.Companion.PayloadType.UNKNOWN
                }, data
            )
        )
        when (Values.appState) {
            Values.Companion.AppState.CONNECTED -> {
                if (values.nearby) {
                    val pl = Payload.fromBytes(bytes)
                    for (client in Values.connectedNearbyClients) {
                        nearby.sendPayload(client.id, pl)
                    }
                }
                if (values.socket)
                    for (endpoint in Values.connectedSocketClients) {
                        endpoint.socket.getOutputStream().write(bytes)
                    }
            }
            Values.Companion.AppState.ACCESSING -> {
                if (Values.connectedServer is SocketEndpointInfo) {
                    (Values.connectedServer as SocketEndpointInfo).socket.getOutputStream()
                        .write(bytes)
                } else {
                    val pl = Payload.fromBytes(bytes)
                    nearby.sendPayload(Values.connectedServer!!.id, pl)
                }
            }
            else -> {}
        }

    }

    fun disconnect() {
        when (Values.appState) {
            Values.Companion.AppState.CONNECTED -> {
                if (values.nearby) for (endpoint in Values.connectedNearbyClients) {
                    nearby.sendPayload(
                        endpoint.id,
                        Payload.fromBytes(
                            PayloadPacket.toEncBytes(
                                PayloadPacket(
                                    PayloadPacket.Companion.PayloadType.DISCONNECT,
                                    ByteArray(0)
                                )
                            )
                        )
                    )
                    nearby.disconnectFromEndpoint(endpoint.id)
                }
                if (values.socket) for (endpoint in Values.connectedSocketClients) {
                    endpoint.socket.getOutputStream().write(
                        PayloadPacket.toEncBytes(
                            PayloadPacket(
                                PayloadPacket.Companion.PayloadType.DISCONNECT,
                                ByteArray(0)
                            )
                        )
                    )
                    endpoint.socket.close()
                }
            }
            Values.Companion.AppState.ACCESSING -> {
                if (Values.connectedServer is SocketEndpointInfo) {
                    val socket = (Values.connectedServer as SocketEndpointInfo).socket
                    socket.getOutputStream().write(
                        PayloadPacket.toEncBytes(
                            PayloadPacket(
                                PayloadPacket.Companion.PayloadType.DISCONNECT,
                                ByteArray(0)
                            )
                        )
                    )
                    socket.close()
                } else {
                    nearby.sendPayload(
                        Values.connectedServer!!.id,
                        Payload.fromBytes(
                            PayloadPacket.toEncBytes(
                                PayloadPacket(
                                    PayloadPacket.Companion.PayloadType.DISCONNECT,
                                    ByteArray(0)
                                )
                            )
                        )
                    )
                    nearby.disconnectFromEndpoint(Values.connectedServer!!.id)
                }
            }
            else -> {}
        }
        Nearby.getConnectionsClient(context).stopAllEndpoints()
    }
}