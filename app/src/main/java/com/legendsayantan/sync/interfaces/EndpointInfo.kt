package com.legendsayantan.sync.interfaces

import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo

/**
 * @author legendsayantan
 */
class EndpointInfo(var id: String, var name: String?, var uidHash:String, var info: DiscoveredEndpointInfo?) {
}