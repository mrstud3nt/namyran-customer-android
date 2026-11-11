package ir.namyranservice.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerRegionTest {
    private val cities = listOf(
        CustomerCity("shiraz", "شیراز", "fars", "فارس"),
        CustomerCity("sadra", "صدرا", "fars", "فارس"),
        CustomerCity("duplicate-one", "نورآباد", "lorestan", "لرستان"),
        CustomerCity("duplicate-two", "نورآباد", "fars", "فارس")
    )

    @Test fun acceptsAlternateGeocoderFieldsAndEnglishCoreKey() {
        assertEquals("shiraz", matchCustomerCity(listOf("", "Shiraz"), "Fars Province", cities)?.id)
        assertEquals("shiraz", matchCustomerCity(listOf("شيراز"), "استان فارس", cities)?.id)
    }

    @Test fun ambiguousCityNeedsMatchingProvinceAndUnknownCityIsNotGuessed() {
        assertNull(matchCustomerCity(listOf("نورآباد"), "", cities))
        assertEquals("duplicate-two", matchCustomerCity(listOf("نورآباد"), "استان فارس", cities)?.id)
        assertNull(matchCustomerCity(listOf("Hamburg"), "", cities))
        assertEquals("sadra", matchCustomerCity(listOf("صدرا", "شیراز"), "فارس", cities)?.id)
    }
}
