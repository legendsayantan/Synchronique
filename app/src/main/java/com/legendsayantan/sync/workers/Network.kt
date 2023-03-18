package com.legendsayantan.sync.workers

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
                    is AudioBufferPacket -> PayloadPacket.Companion.PayloadType.AUDIO_BUFFER
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
                if (values.socket) Values.runningServer.send(String(bytes))
            }
            Values.Companion.AppState.ACCESSING -> {
                if (Values.connectedServer is SocketEndpointInfo) {
                    (Values.connectedServer as SocketEndpointInfo).senderThread.push(String(bytes))
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
                if (values.socket) {
                    Values.runningServer.send(
                        PayloadPacket.toEncString(
                            PayloadPacket(
                                PayloadPacket.Companion.PayloadType.DISCONNECT,
                                ByteArray(0)
                            )
                        )
                    )
                    Values.runningServer.interrupt()
                }
            }
            Values.Companion.AppState.ACCESSING -> {
                if (Values.connectedServer is SocketEndpointInfo) {
                    (Values.connectedServer as SocketEndpointInfo).senderThread.push(
                        PayloadPacket.toEncString(
                            PayloadPacket(
                                PayloadPacket.Companion.PayloadType.DISCONNECT,
                                ByteArray(0)
                            )
                        )
                    )
                    (Values.connectedServer as SocketEndpointInfo).senderThread.socket.close()
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
    fun createNgrokTunnel() {
        val launchIntent = Intent()
        launchIntent.action = "com.legendsayantan.ngrokclient.START_TUNNEL"
        launchIntent.putExtra("protocol", "tcp")
        launchIntent.putExtra("port", Values.localport)
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(launchIntent)
        }catch (e: Exception) {
            Toast.makeText(context, "Ngrok client not installed, failed to tunnel through internet.", Toast.LENGTH_SHORT).show()
        }
    }
}