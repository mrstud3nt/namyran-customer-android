package ir.namyranservice.customer

import ir.namyranservice.customer.data.array
import ir.namyranservice.customer.data.objects
import ir.namyranservice.customer.data.strings
import org.json.JSONObject

internal data class DeviceServiceResult(val serviceId: String, val category: String, val deviceId: String, val title: String)

internal fun supportsService(device: JSONObject, mode: String): Boolean =
    mode in device.array("service_modes").strings() || mode == "periodic" &&
        listOf("periodic_enabled", "settings_enabled", "maintenance_enabled").any { device.optBoolean(it) }

private fun searchWords(text: String): Set<String> = text.replace('ي','ی').replace('ك','ک')
    .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()

internal fun findDeviceServices(catalog: JSONObject, services: List<JSONObject>, term: String): List<DeviceServiceResult> {
    val categories = catalog.optJSONObject("categories") ?: return emptyList()
    val query = searchWords(term)
    if (query.isEmpty()) return emptyList()
    return buildList {
        categories.keys().forEach { category ->
            categories.array(category).objects().forEach deviceLoop@{ device ->
                val deviceTitle = device.optString("title")
                val deviceWords = searchWords(deviceTitle)
                if (query.none { word -> deviceWords.any { it.startsWith(word) } }) return@deviceLoop
                services.forEach { service ->
                    val mode = service.optString("id")
                    val serviceTitle = service.optString("title")
                    val availableWords = deviceWords + searchWords(serviceTitle)
                    if (supportsService(device, mode) && query.all { word -> availableWords.any { it.startsWith(word) } }) {
                        add(DeviceServiceResult(mode, category, device.optString("id"), "$serviceTitle · $deviceTitle"))
                    }
                }
            }
        }
    }.distinctBy { Triple(it.serviceId,it.category,it.deviceId) }
}
