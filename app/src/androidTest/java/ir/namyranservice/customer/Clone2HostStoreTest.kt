package ir.namyranservice.customer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.namyranservice.customer.data.Clone2HostAccess
import ir.namyranservice.customer.data.Clone2HostStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Clone2HostStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "clone2_host_access_instrumentation"
    private val user = "fixture-user"
    private val password = "fixture-password-only"
    private val origin = "https://clone2.namyranservice.ir/"
    @Test fun encryptedCredentialsRestoreWithoutPlaintextPreferences() {
        val store = Clone2HostStore(context, name)
        try {
            store.save(user, password)
            val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.values.joinToString()
            assertFalse(stored.contains(user)); assertFalse(stored.contains(password))
            Clone2HostAccess.clear(); assertNull(Clone2HostAccess.header(origin))
            assertTrue(Clone2HostStore(context, name).restore())
            assertNotNull(Clone2HostAccess.header(origin))
            assertNull(Clone2HostAccess.header("https://stage.namyranservice.ir/"))
        } finally { store.forget() }
    }
    @Test fun tamperedCiphertextFailsClosed() {
        val store = Clone2HostStore(context, name)
        try {
            store.save(user, password)
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val bytes = java.util.Base64.getDecoder().decode(prefs.getString("encrypted_credentials",null)!!)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            check(prefs.edit().putString("encrypted_credentials",java.util.Base64.getEncoder().encodeToString(bytes)).commit())
            assertFalse(Clone2HostStore(context, name).restore());assertFalse(store.isSaved())
            assertNull(Clone2HostAccess.header(origin))
        } finally { store.forget() }
    }
    @Test fun forgetRemovesSavedAndActiveAccess() {
        val store = Clone2HostStore(context, name)
        try {
            store.save(user, password);store.forget()
            assertFalse(store.isSaved());assertFalse(Clone2HostStore(context,name).restore())
            assertNull(Clone2HostAccess.header(origin))
        } finally { store.forget() }
    }
    @Test fun optOutKeepsOnlyCurrentProcessAccess() {
        val store = Clone2HostStore(context, name)
        try {
            store.save(user, password);store.removeSaved()
            assertNotNull(Clone2HostAccess.header(origin))
            Clone2HostAccess.clear();assertFalse(Clone2HostStore(context,name).restore())
            assertNull(Clone2HostAccess.header(origin))
        } finally { store.forget() }
    }
}
