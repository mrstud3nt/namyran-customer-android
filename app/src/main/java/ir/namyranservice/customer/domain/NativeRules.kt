package ir.namyranservice.customer.domain

import java.net.URI

object NativeRules {
    private val itemDetails=setOf("installation_package","tv_size","tv_bracket","tv_wall","tv_services","catalog_service_ids","catalog_service_versions")
    fun invalidatedFields(changed:String):Set<String> = when(changed){
        "service_type" -> itemDetails + setOf("category","device","catalog_device_id","catalog_device_version","catalog_subtype_id","brand","custom_brand")
        "category" -> itemDetails + setOf("device","catalog_device_id","catalog_device_version","catalog_subtype_id","brand","custom_brand")
        "device" -> itemDetails + setOf("catalog_subtype_id","brand","custom_brand")
        "brand" -> itemDetails + "custom_brand"
        "catalog_subtype_id" -> setOf("catalog_service_ids","catalog_service_versions")
        else -> emptySet()
    }
    fun isCustomBrand(value:String)=value in setOf("برند دیگر","سایر مدل‌ها")
    fun allowedBase(value: String): Boolean = try {
        val uri=URI(value)
        uri.scheme=="https" && uri.host=="stage.namyranservice.ir" && uri.port==-1 && uri.userInfo==null && uri.rawPath=="/wp-json/" && uri.query==null && uri.fragment==null
    } catch(_:Exception){false}
    fun mayRefreshNonce(code:String)=code in setOf("nsc_frontend_nonce_denied","rest_cookie_invalid_nonce")
    fun rejectedBeforeInsert(code:String)=code.startsWith("nsc_required_")||code in setOf("nsc_date_invalid","nsc_date_past","nsc_date_year_range","nsc_time_invalid","nsc_time_unavailable","nsc_name_short","nsc_address_short","nsc_terms","nsc_catalog_invalid","nsc_item_invalid","nsc_items_limit","nsc_location_required","nsc_location_invalid")
}
