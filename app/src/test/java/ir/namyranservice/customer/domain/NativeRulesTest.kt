package ir.namyranservice.customer.domain

import ir.namyranservice.customer.data.normalizedDigits
import org.junit.Assert.*
import org.junit.Test

class NativeRulesTest {
    @Test fun deviceChangeCannotCarryVipOrOldBrandIntoAnotherDevice(){
        val stale=mapOf("service_type" to "install","category" to "group","brand" to "old","custom_brand" to "custom","installation_package" to "vip","tv_size" to "85","tv_bracket" to "wall","tv_wall" to "brick","tv_services" to "old","catalog_service_ids" to "123","catalog_service_versions" to "old","catalog_subtype_id" to "old")
        val kept=stale.filterKeys{it !in NativeRules.invalidatedFields("device")}
        assertEquals(mapOf("service_type" to "install","category" to "group"),kept)
    }
    @Test fun categoryAndServiceChangesInvalidateTheCorrectAncestors(){
        assertTrue("device" in NativeRules.invalidatedFields("category"))
        assertFalse("service_type" in NativeRules.invalidatedFields("category"))
        assertTrue("category" in NativeRules.invalidatedFields("service_type"))
        assertEquals(setOf("catalog_service_ids","catalog_service_versions"),NativeRules.invalidatedFields("catalog_subtype_id"))
    }
    @Test fun customBrandMarkersFollowTheCoreFormContract(){
        assertTrue(NativeRules.isCustomBrand("برند دیگر"))
        assertTrue(NativeRules.isCustomBrand("سایر مدل‌ها"))
        assertFalse(NativeRules.isCustomBrand("LG"))
    }
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
