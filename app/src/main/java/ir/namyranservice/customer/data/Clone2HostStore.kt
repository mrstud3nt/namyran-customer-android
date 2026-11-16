package ir.namyranservice.customer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Stores one authenticated ciphertext; key material remains in Android Keystore. */
class Clone2HostStore(context: Context, storageName: String = "clone2_host_access") {
    private val prefs = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    private val alias = "nsc_host_" + storageName
    private val aad = ("clone2.namyranservice.ir|" + context.packageName).toByteArray(Charsets.UTF_8)
    private val entry = "encrypted_credentials"
    fun isSaved() = prefs.contains(entry)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(username: String, password: String) {
        Clone2HostAccess.validate(username, password)
        val plain = JSONObject().put("username", username).put("password", password).toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
            val blob = Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain))
            check(prefs.edit().putString(entry, blob).commit()) { "host_credential_storage_failed" }
            Clone2HostAccess.configure(username, password)
        } finally { plain.fill(0) }
    }
    fun restore(): Boolean {
        return try {
            val blob = prefs.getString(entry, null) ?: return false
            val bytes = Base64.getDecoder().decode(blob)
            require(bytes.size >= 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))); updateAAD(aad)
            }
            val plain = cipher.doFinal(bytes.copyOfRange(12,bytes.size))
            try {
                val value = JSONObject(String(plain, Charsets.UTF_8))
                Clone2HostAccess.configure(value.getString("username"), value.getString("password"))
            } finally { plain.fill(0) }
            true
        } catch (_: Exception) {
            Clone2HostAccess.clear()
            runCatching { prefs.edit().remove(entry).commit() }
            false
        }
    }
    fun removeSaved() { check(prefs.edit().remove(entry).commit()) { "host_credential_removal_failed" } }
    fun forget() { try { removeSaved() } finally { Clone2HostAccess.clear() } }
}
