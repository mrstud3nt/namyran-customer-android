package ir.namyranservice.customer.data
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
class Clone2HostAccessTest {
    @After fun cleanup() { Clone2HostAccess.clear() }
    @Test fun credentialsStayOnExactClone2Origin() {
        Clone2HostAccess.configure("fixture", "fixture-only")
        assertNotNull(Clone2HostAccess.header("https://clone2.namyranservice.ir/wp-json/"))
        for (url in listOf("https://stage.namyranservice.ir/", "https://namyranservice.ir/", "http://clone2.namyranservice.ir/", "https://clone2.namyranservice.ir.evil.test/", "https://user@clone2.namyranservice.ir/", "https://clone2.namyranservice.ir:8443/"))
            assertNull(Clone2HostAccess.header(url))
    }
    @Test fun onlyHostChallengesPromptForHostCredentials() {
        assertTrue(Clone2HostAccess.challenge(401, "Basic realm=fixture"))
        assertFalse(Clone2HostAccess.challenge(401, null))
        assertFalse(Clone2HostAccess.challenge(403, "Basic realm=fixture"))
        assertFalse(Clone2HostAccess.challenge(401, "Bearer realm=fixture"))
    }
    @Test fun clearingDropsCredentials() {
        Clone2HostAccess.configure("fixture", "fixture-only"); Clone2HostAccess.clear()
        assertNull(Clone2HostAccess.header("https://clone2.namyranservice.ir/"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidUsernames() { Clone2HostAccess.configure("invalid:user", "fixture-only") }
}
