import android.widget.Toast
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.legendsayantan.sync.BuildConfig
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class EncryptionManager {
    private val keySize = 256
    private val transformation = "AES/CBC/PKCS5Padding"
    private val encoding = StandardCharsets.UTF_8

    fun as16(secretKey: String): String {
        var x = "";
        repeat(16 - secretKey.length) {
            x += "0"
        }
        x += secretKey
        return x;
    }

    fun encrypt(plainText: String, secretKey: String): String {
        val key = SecretKeySpec(as16(secretKey).toByteArray(encoding), "AES")
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(encoding))
        return Base64.getEncoder().encodeToString(iv + encryptedBytes)
    }

    fun decrypt(cipherText: String, secretKey: String): String {
        val decodedBytes = Base64.getDecoder().decode(cipherText)
        val iv = decodedBytes.copyOfRange(0, 16)
        val encryptedBytes = decodedBytes.copyOfRange(16, decodedBytes.size)
        val key = SecretKeySpec(as16(secretKey).toByteArray(encoding), "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, encoding)
    }
    companion object{
        var cachedKey :String? = ""
        fun fetchDynamicKey(onSuccess: (key: String) -> Unit = {}, onFailure: (exception:java.lang.Exception) -> Unit = {}){
            if (cachedKey.equals("")) {
                val remoteConfig = Firebase.remoteConfig

                val defaultConfigMap = HashMap<String, Any>()
                defaultConfigMap["cryptokey"] = ""
                remoteConfig.setDefaultsAsync(defaultConfigMap)
                var x = 3600*24L
                val cacheExpiration = if (BuildConfig.DEBUG) {
                    x
                } else {
                    x*7 - ((System.currentTimeMillis()/1000) % (x*7))
                }
                remoteConfig.fetch(cacheExpiration).addOnSuccessListener { task ->
                    remoteConfig.activate()
                    val formatter = DateTimeFormatter.ofPattern("DDD")
                    val currentDate = LocalDateTime.now().format(formatter)
                    cachedKey = currentDate+remoteConfig.getString("cryptokey")
                    fetchDynamicKey ({ onSuccess(it) }, { onFailure(it) })
                }.addOnFailureListener {
                    onFailure(it);
                }
            } else {
                onSuccess(cachedKey!!)
                return
            }
        }
    }
}
