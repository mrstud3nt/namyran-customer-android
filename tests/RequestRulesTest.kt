import ir.namyranservice.customer.domain.RequestDraft
import ir.namyranservice.customer.domain.RequestRules

fun main() {
    val blank = RequestDraft()
    check(RequestRules.check(0, blank).size == 4)
    val device = blank.copy(service = "تعمیر", appliance = "تلویزیون", brand = "LG", modelUnknown = true)
    check(RequestRules.check(0, device).isEmpty())
    check(RequestRules.check(1, device.copy(description = "خراب")).isNotEmpty())
    val explained = device.copy(description = "پس از روشن‌شدن تصویر قطع می‌شود")
    check(RequestRules.check(1, explained).isEmpty())
    check(RequestRules.check(2, explained.copy(province = "فارس", city = "شیراز", address = "خیابان نمونه پلاک ۱۲ واحد ۲")).isEmpty())
    check(RequestRules.check(3, explained).single().field == "slots")
    check(RequestRules.check(4, explained).single().field == "submission")
    println("PASS 7 offline request-rule assertions")
}
