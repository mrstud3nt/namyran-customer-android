package ir.namyranservice.customer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.namyranservice.customer.ui.ExpressivePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

internal data class CustomerCity(val id: String, val title: String, val provinceId: String, val province: String)

@Composable
internal fun CustomerRegionPicker(vm: CustomerViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val cities = vm.customerCities
    val provinces = cities.distinctBy { it.provinceId }.map { it.provinceId to it.province }
    var province by rememberSaveable { mutableStateOf(vm.customerCity?.provinceId.orEmpty()) }
    var city by rememberSaveable { mutableStateOf(vm.customerCity?.id.orEmpty()) }
    var finding by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun locate() {
        if (finding) return
        finding = true
        status = ""
        scope.launch {
            try {
                val match = findCustomerCity(context, cities)
                if (match == null) status = "شهر از روی موقعیت پیدا نشد. استان و شهر را دستی انتخاب کنید."
                else {
                    province = match.provinceId
                    city = match.id
                    status = "موقعیت، «${match.title}» را پیشنهاد می‌کند. شهر را بررسی و تأیید کنید."
                }
            } finally { finding = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) locate() else status = "می‌توانید استان و شهر را دستی انتخاب کنید."
    }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Outlined.LocationOn, null) },
        title = { Text("استان و شهر شما") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("شهر پیش‌فرض برای پیشنهاد محل خدمت و نمایش شماره پشتیبانی شعبه استفاده می‌شود.")
                OutlinedButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }, enabled = !finding, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text("تشخیص شهر با موقعیت")
                }
                if (finding) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status.isNotBlank()) Text(status)
                ExpressivePicker("استان", provinces, province, !finding) {
                    if (province != it) { province = it; city = "" }
                }
                if (province.isNotBlank()) {
                    ExpressivePicker("شهر", cities.filter { it.provinceId == province }.map { it.id to it.title },
                        city, !finding) { city = it }
                }
            }
        },
        confirmButton = {
            val selection = cities.firstOrNull { it.id == city && it.provinceId == province }
            Button(enabled = selection != null && !finding, onClick = {
                selection?.let { if (vm.saveCustomerCity(it)) onClose() }
            }) { Text("تأیید شهر") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("بعداً") } }
    )
}

private fun normalizePlace(value: String): String = value.lowercase(Locale.ROOT)
    .replace('ي', 'ی').replace('ك', 'ک').filter { it.isLetterOrDigit() }

private suspend fun findCustomerCity(context: Context, cities: List<CustomerCity>): CustomerCity? {
    if (!Geocoder.isPresent()) return null
    val point = withTimeoutOrNull(12_000) { currentPoint(context) } ?: return null
    val result = try {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.forLanguageTag("fa-IR")).getFromLocation(point.latitude, point.longitude, 4).orEmpty()
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { return null }
    for (address in result) {
        if (!address.countryCode.equals("IR", ignoreCase = true)) continue
        val locality = normalizePlace(address.locality.orEmpty())
        if (locality.isEmpty()) continue
        val province = normalizePlace(address.adminArea.orEmpty().replace("استان", "").replace("Province", "", true))
        val matching = cities.filter { locality == normalizePlace(it.title) || locality == normalizePlace(it.id) }
        if (matching.size == 1) return matching.single()
        matching.singleOrNull { province == normalizePlace(it.province) || province == normalizePlace(it.provinceId) }
            ?.let { return it }
    }
    return null
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun currentPoint(context: Context): Location? = suspendCancellableCoroutine { continuation ->
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val manager = context.getSystemService(LocationManager::class.java) ?: run {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            manager.removeUpdates(this)
            if (continuation.isActive) continuation.resume(location)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Legacy callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }
    val started = runCatching {
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .first { manager.isProviderEnabled(it) }
        manager.requestLocationUpdates(provider, 0, 0f, listener, Looper.getMainLooper())
    }.isSuccess
    if (started) continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    else continuation.resume(null)
}
