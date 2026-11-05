package ir.namyranservice.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCodeConsentTest {
    @Test fun extractsPersianDigitsFromVerificationMessage() {
        assertEquals("1234", extractSmsCode("کد ورود نامیران سرویس: ۱۲۳۴"))
    }

    @Test fun ignoresLongNumbersAndAmbiguousCodes() {
        assertNull(extractSmsCode("شماره پیگیری 09123456789"))
        assertNull(extractSmsCode("1234 یا 4321"))
    }
}
