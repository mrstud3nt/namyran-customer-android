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
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.LocationCity
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
        status = "در حال شناسایی موقعیت…"
        scope.launch {
            try {
                val match = findCustomerCity(context, cities)
                if (match == null) status = "شهر از روی موقعیت پیدا نشد. استان و شهر را دستی انتخاب کنید."
                else {
                    province = match.provinceId
                    city = match.id
                    status = "موقعیت، «${match.title}» را پیشنهاد می‌کند. شهر را بررسی و تأیید کنید."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "دریافت موقعیت ممکن نشد. دوباره تلاش کنید یا شهر را دستی انتخاب کنید." }
            finally { finding = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_COARSE_LOCATION] == true || it[Manifest.permission.ACCESS_FINE_LOCATION] == true) locate() else status = "می‌توانید استان و شهر را دستی انتخاب کنید."
    }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoStarted && vm.customerCity == null) {
            autoStarted = true
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(32.dp)
        val surface = MaterialTheme.colorScheme.surface
        Surface(Modifier.fillMaxWidth().padding(20.dp).widthIn(max = 480.dp), shape = shape,
            color = surface.copy(alpha = 0.96f), shadowElevation = 18.dp,
            border = BorderStroke(1.dp, Color(0xFF9FDDF4))) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF46C5ED).copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.05f), Color(0xFF3297DE).copy(alpha = 0.09f))))
                .padding(22.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF087BA7), shadowElevation = 8.dp) {
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(38.dp), tint = Color.White)
                    }
                }
                Text("استان و شهر شما", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("با اجازه شما موقعیت برای پیشنهاد شهر و پشتیبانی محلی بررسی می‌شود. مختصات دقیق برای این انتخاب به سرور نامیران ارسال نمی‌شود.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, enabled = !finding, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087DA7))) {
                    Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(8.dp))
                    Text(if (finding) "در حال شناسایی موقعیت…" else "تشخیص خودکار موقعیت")
                }
                if (finding) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status.isNotBlank()) Surface(shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                ExpressivePicker("استان", provinces, province, !finding) {
                    if (province != it) { province = it; city = ""; status = "" }
                }
                if (province.isNotBlank()) ExpressivePicker("شهر",
                    cities.filter { it.provinceId == province }.map { it.id to it.title }, city, !finding) { city = it }
                val selection = cities.firstOrNull { it.id == city && it.provinceId == province }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = selection != null && !finding, onClick = {
                        selection?.let { if (vm.saveCustomerCity(it)) onClose() }
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("تأیید و ادامه") }
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("بعداً") }
                }
            }
        }
    }
}

private fun normalizePlace(value: String): String = value.lowercase(Locale.ROOT)
    .replace('ي', 'ی').replace('ك', 'ک').filter { it.isLetterOrDigit() }

internal fun matchCustomerCity(names: List<String>, province: String, cities: List<CustomerCity>): CustomerCity? {
    val normalizedProvince = normalizePlace(province.replace("استان", "").replace("Province", "", true))
    for (name in names) {
        val normalized = normalizePlace(name)
        if (normalized.isEmpty()) continue
        val matches = cities.filter { normalized == normalizePlace(it.title) || normalized == normalizePlace(it.id) }
        val inProvince = matches.filter {
            normalizedProvince == normalizePlace(it.province) || normalizedProvince == normalizePlace(it.provinceId)
        }
        if (inProvince.size == 1) return inProvince.single()
        if (matches.size == 1) return matches.single()
    }
    return null
}

private suspend fun findCustomerCity(context: Context, cities: List<CustomerCity>): CustomerCity? {
    if (!Geocoder.isPresent()) return null
    val point = withTimeoutOrNull(15_000) { currentPoint(context) } ?: return null
    for (locale in listOf(Locale.forLanguageTag("fa-IR"), Locale.ENGLISH)) {
        val result = try {
            val geocoder = Geocoder(context, locale)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<List<android.location.Address>> { continuation ->
                        geocoder.getFromLocation(point.latitude, point.longitude, 5,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                    if (continuation.isActive) continuation.resume(addresses)
                                }
                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(emptyList())
                                }
                            })
                    }
                }.orEmpty()
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(point.latitude, point.longitude, 5).orEmpty()
                }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        for (address in result) {
            if (!address.countryCode.equals("IR", ignoreCase = true)) continue
            matchCustomerCity(listOfNotNull(address.locality, address.subAdminArea, address.subLocality),
                address.adminArea.orEmpty(), cities)?.let { return it }
        }
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
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val recent = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter {
            val age = android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos
            age in 0..120_000_000_000L && it.hasAccuracy() && it.accuracy <= 3000
        }.maxByOrNull { it.elapsedRealtimeNanos }
    if (recent != null) {
        continuation.resume(recent)
        return@suspendCancellableCoroutine
    }
    var started = false
    providers.forEach { provider ->
        if (runCatching { manager.requestLocationUpdates(provider, 0, 0f, listener, Looper.getMainLooper()) }.isSuccess) started = true
    }
    if (started) continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    else continuation.resume(null)
}
