package ir.namyranservice.customer.domain

import org.junit.Assert.*
import org.junit.Test

/** Tests the existing offline draft; does not certify the Core contract. */
class RequestRulesTest {
    @Test fun blankDeviceReportsAllMissingFields() {
        assertEquals(setOf("service", "appliance", "brand", "model"),
            RequestRules.check(0, RequestDraft()).map { it.field }.toSet())
    }
    @Test fun unknownModelOnlyWaivesModelRequirement() {
        assertEquals(setOf("service", "appliance", "brand"),
            RequestRules.check(0, RequestDraft(modelUnknown = true)).map { it.field }.toSet())
    }
    @Test fun blankValuesCannotSatisfyDeviceFields() {
        assertEquals(4, RequestRules.check(0, RequestDraft(service=" ", appliance=" ", brand=" ", model=" ")).size)
    }
    @Test fun descriptionBoundaryUsesTrimmedInput() {
        assertFalse(RequestRules.check(1, RequestDraft(description=" 123456789 ")).isEmpty())
        assertTrue(RequestRules.check(1, RequestDraft(description=" 1234567890 ")).isEmpty())
    }
    @Test fun missingLocationReportsEveryField() {
        assertEquals(setOf("province", "city", "address"),
            RequestRules.check(2, RequestDraft()).map { it.field }.toSet())
    }
    @Test fun unverifiedCapacityAndSubmissionAlwaysFailClosed() {
        val draft=RequestDraft(service="repair", appliance="tv", brand="LG", model="test", description="1234567890", province="test", city="test", address="123456789012", termsAccepted=true)
        assertEquals("slots", RequestRules.check(3, draft).single().field)
        assertEquals("submission", RequestRules.check(4, draft).single().field)
    }
    @Test fun invalidStepsCannotBypassValidation() {
        for(step in listOf(-1,5,Int.MAX_VALUE)) assertEquals("step", RequestRules.check(step, RequestDraft()).single().field)
    }
}
