package com.legendsayantan.sync.interfaces

import android.util.Base64
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import java.io.UnsupportedEncodingException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.sql.Timestamp
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

/**
 * @author legendsayantan
 */
class EndpointInfo(var endpointId: String,var name: String?, var uidHash:String, var info: DiscoveredEndpointInfo?) {
}