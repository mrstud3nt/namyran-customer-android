package ir.namyranservice.customer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ir.namyranservice.customer.BuildConfig
import ir.namyranservice.customer.domain.NativeRules
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CoreFailure(val code: String, message: String, val details: JSONObject = JSONObject()) : Exception(message)

/** No token, OTP or customer payload is written to logs or plaintext preferences. */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("customer_private", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("namyran_customer_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("namyran_customer_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(name: String): JSONObject? {
        val value = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        } catch (_: Exception) { prefs.edit().remove(name).commit(); null }
    }
    @Synchronized fun write(name: String, value: JSONObject?) {
        if (value == null) { check(prefs.edit().remove(name).commit()); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
}

class CoreApi {
    private val base = URI(BuildConfig.API_BASE_URL)
    private val origin = "${base.scheme}://${base.host}"
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private var nonce = ""
    private var restNonce = ""
    init { require(NativeRules.allowedBase(BuildConfig.API_BASE_URL)) }

    private fun request(path: String, method: String, content: String? = null, form: Boolean = false, images: List<JSONObject> = emptyList(), imageStore: AttachmentStore? = null): JSONObject {
        val url = if (path.startsWith("/wp-admin/")) URL(origin + path) else base.resolve(path).toURL()
        require(url.protocol == "https" && url.host == base.host && url.port == -1)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.useCaches = false; connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Origin", origin)
            connection.setRequestProperty("Referer", "$origin/service-request/")
            connection.setRequestProperty("User-Agent", "NamyranCustomerStage/${BuildConfig.VERSION_NAME}")
            cookies.get(url.toURI(), emptyMap()).forEach { (k,v) -> if (v.isNotEmpty()) connection.setRequestProperty(k, v.joinToString("; ")) }
            if (nonce.isNotBlank()) connection.setRequestProperty("X-NSC-Public-Nonce", nonce)
            if (restNonce.isNotBlank()) connection.setRequestProperty("X-WP-Nonce", restNonce)
            if (images.isNotEmpty()) {
                require(method=="POST"&&path=="namyran-service/v1/requests"&&imageStore!=null)
                val fields=JSONObject(content!!)
                val parts=images.mapIndexed { index,meta ->
                    val kind=ir.namyranservice.customer.domain.ImageRules.kind(imageStore.read(meta))
                        ?:throw CoreFailure("image_invalid","تصویر ذخیره‌شده معتبر نیست.")
                    MultipartBody.Part("image-${index+1}.${kind.second}",kind.first,meta.getLong("size")){imageStore.read(meta)}
                }
                val body=MultipartBody("Namyran"+java.util.UUID.randomUUID().toString().replace("-",""),fields.keys().asSequence().map{it to fields.get(it).toString()}.toList(),parts)
                connection.doOutput=true
                connection.setFixedLengthStreamingMode(body.contentLength)
                connection.setRequestProperty("Content-Type","multipart/form-data; boundary=${body.boundary}")
                connection.outputStream.buffered().use(body::writeTo)
            } else if (content != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded; charset=UTF-8" else "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            cookies.put(url.toURI(), connection.headerFields.filterKeys { it != null })
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<=2_000_000){val count=input.read(buffer,0,minOf(buffer.size,2_000_001-out.size()));if(count<0)break;out.write(buffer,0,count)}
                out.toByteArray()
            } ?: byteArrayOf()
            if (bytes.size > 2_000_000) throw CoreFailure("response_too_large", "پاسخ سرور بیش از حد مجاز است.")
            val json = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (_: Exception) {
                throw CoreFailure("non_json_$status", if (status == 403) "دسترسی Stage توسط محافظ سرور مسدود شده است." else "پاسخ معتبر از Stage دریافت نشد.")
            }
            if (status !in 200..299) throw CoreFailure(json.optString("code", "http_$status"), json.optString("message", "خطای ارتباط با سرور"), json.optJSONObject("data") ?: JSONObject())
            return json
        } finally { connection.disconnect() }
    }
    @Synchronized private fun bootstrap() {
        val r = request("/wp-admin/admin-ajax.php", "POST", "action=nsc_frontend_nonce", true)
        val data = r.optJSONObject("data")
        if (!r.optBoolean("success") || data?.optString("nonce").isNullOrBlank()) throw CoreFailure("bootstrap_failed", "دریافت نشست امن انجام نشد.")
        nonce = data!!.getString("nonce"); restNonce = data.optString("rest_nonce")
    }
    @Synchronized fun post(route: String, payload: JSONObject, images: List<JSONObject> = emptyList(), imageStore: AttachmentStore? = null): JSONObject {
        require(route in setOf("otp/send", "otp/verify", "otp/session", "otp/logout", "requests", "my-requests", "track", "customer-addresses"))
        if(images.isNotEmpty()) { require(route=="requests"&&imageStore!=null); images.forEach { imageStore.read(it) } }
        if (nonce.isBlank()) bootstrap()
        try { return request("namyran-service/v1/$route", "POST", payload.toString(), images=images, imageStore=imageStore) }
        catch (e: CoreFailure) {
            if (!NativeRules.mayRefreshNonce(e.code)) throw e
            bootstrap() // Retry only an explicitly rejected nonce, never SMS or an uncertain submission.
            return request("namyran-service/v1/$route", "POST", payload.toString(), images=images, imageStore=imageStore)
        }
    }
    @Synchronized fun customerEmail(payload: JSONObject, receipt: Boolean = false): JSONObject =
        request("namyran-customer/v1/" + if(receipt) "receipt-email" else "profile-email", "POST", payload.toString())

    @Synchronized fun config(): JSONObject = request("namyran-customer/v1/config", "GET").also {
        if (it.optJSONObject("customerMobile")?.optInt("schema") != 1 || it.optString("revision").isBlank()) throw CoreFailure("config_schema", "نسخه اتصال Core پشتیبانی نمی‌شود.")
    }
    @Synchronized fun schedule(date: String): JSONObject = request("namyran-customer/v1/schedule?date=${URLEncoder.encode(date, "UTF-8")}", "GET")
    fun clearCookies() { cookies.cookieStore.removeAll(); nonce = ""; restNonce = "" }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
fun normalizedDigits(value: String): String = value.map { c ->
    when(c) { in '۰'..'۹' -> '0' + (c - '۰'); in '٠'..'٩' -> '0' + (c - '٠'); else -> c }
}.joinToString("")
