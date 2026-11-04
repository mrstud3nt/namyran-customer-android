package ir.namyranservice.customer.domain

/** Offline-only validation. The server remains authoritative for coverage, slots and submissions. */
data class RequestDraft(
    val service: String = "",
    val appliance: String = "",
    val brand: String = "",
    val model: String = "",
    val modelUnknown: Boolean = false,
    val description: String = "",
    val province: String = "",
    val city: String = "",
    val address: String = "",
    val termsAccepted: Boolean = false
)

data class FieldIssue(val field: String, val explanation: String)

object RequestRules {
    fun check(step: Int, draft: RequestDraft): List<FieldIssue> = when (step) {
        0 -> buildList {
            if (draft.service.isBlank()) add(FieldIssue("service", "نوع خدمت را انتخاب کنید."))
            if (draft.appliance.isBlank()) add(FieldIssue("appliance", "دستگاه را انتخاب کنید."))
            if (draft.brand.isBlank()) add(FieldIssue("brand", "برند را وارد کنید."))
            if (!draft.modelUnknown && draft.model.isBlank()) add(FieldIssue("model", "مدل دستگاه را وارد کنید یا گزینه «مدل را نمی‌دانم» را انتخاب کنید."))
        }
        1 -> if (draft.description.trim().length < 10) listOf(FieldIssue("description", "شرح مشکل باید حداقل ۱۰ نویسه داشته باشد.")) else emptyList()
        2 -> buildList {
            if (draft.province.isBlank()) add(FieldIssue("province", "استان را انتخاب کنید."))
            if (draft.city.isBlank()) add(FieldIssue("city", "شهر را وارد کنید."))
            if (draft.address.trim().length < 12) add(FieldIssue("address", "نشانی دقیق را با حداقل ۱۲ نویسه وارد کنید."))
        }
        3 -> listOf(FieldIssue("slots", "تا اتصال به ظرفیت واقعی Stage امکان انتخاب وقت وجود ندارد."))
        4 -> listOf(FieldIssue("submission", "ثبت نهایی فقط پس از تأیید نشست، ظرفیت و پذیرش شرایط توسط Core مجاز است."))
        else -> listOf(FieldIssue("step", "مرحله معتبر نیست."))
    }
}
