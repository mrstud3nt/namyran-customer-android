package ir.namyranservice.customer.data

import java.net.URI
import java.util.Base64
import java.io.IOException

class Clone2HostAccessRequired : IOException("محافظ Clone2 نیاز به ورود دارد؛ «دسترسی محیط آزمایشی» را باز کنید.")

/** Process memory only; never persists, logs, or forwards a host credential. */
object Clone2HostAccess {
    @Volatile private var authorization: String? = null
    fun configure(username: String, password: String) {
        require(username.isNotBlank() && ':' !in username && password.isNotEmpty())
        require((username + password).none { it.isISOControl() })
        authorization = "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
    }
    fun clear() { authorization = null }
    fun header(url: String): String? = try {
        val uri = URI(url)
        if (uri.scheme == "https" && uri.host == "clone2.namyranservice.ir" &&
            uri.port == -1 && uri.rawUserInfo == null) authorization else null
    } catch (_: Exception) { null }
    fun challenge(status: Int, value: String?, contentType: String? = null): Boolean {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mime == "application/json" || mime?.endsWith("+json") == true) return false
        return status == 401 &&
        value?.trim()?.let { it.equals("Basic", true) || it.startsWith("Basic ", true) } == true
    }
}
