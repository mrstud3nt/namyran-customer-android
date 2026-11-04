package ir.namyranservice.customer.domain

import ir.namyranservice.customer.data.normalizedDigits
import org.junit.Assert.*
import org.junit.Test

class NativeRulesTest {
    @Test fun onlyExactStageBaseIsAllowed(){
        assertTrue(NativeRules.allowedBase("https://stage.namyranservice.ir/wp-json/"))
        listOf("http://stage.namyranservice.ir/wp-json/","https://namyranservice.ir/wp-json/","https://namyran.ir/wp-json/","https://stage.namyranservice.ir.evil.test/wp-json/","https://user@stage.namyranservice.ir/wp-json/","https://stage.namyranservice.ir:443/wp-json/","https://stage.namyranservice.ir/wp-json/?target=other","https://stage.namyranservice.ir/wp-json/#other","https://stage.namyranservice.ir/other/","bad").forEach{assertFalse(it,NativeRules.allowedBase(it))}
    }
    @Test fun onlyExplicitNonceRejectionsAllowAutomaticRetry(){
        assertTrue(NativeRules.mayRefreshNonce("nsc_frontend_nonce_denied"))
        assertTrue(NativeRules.mayRefreshNonce("rest_cookie_invalid_nonce"))
        listOf("nsc_sms_failed","network_error","http_502","nsc_rate_limit","nsc_request_db","nsc_customer_session_denied").forEach{assertFalse(NativeRules.mayRefreshNonce(it))}
    }
    @Test fun uncertainWritesNeverDiscardPendingSubmission(){
        assertFalse(NativeRules.rejectedBeforeInsert("nsc_request_db"))
        assertFalse(NativeRules.rejectedBeforeInsert("network_error"))
        assertFalse(NativeRules.rejectedBeforeInsert("http_500"))
        assertTrue(NativeRules.rejectedBeforeInsert("nsc_time_unavailable"))
        assertTrue(NativeRules.rejectedBeforeInsert("nsc_catalog_invalid"))
    }
    @Test fun persianAndArabicDigitsAreNormalizedWithoutAlteringOtherText(){
        assertEquals("09123456789",normalizedDigits("۰۹۱۲۳۴۵۶۷۸۹"))
        assertEquals("1234",normalizedDigits("١٢٣٤"))
        assertEquals("text-123",normalizedDigits("text-۱۲۳"))
    }
}
