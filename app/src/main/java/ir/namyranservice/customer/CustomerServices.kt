package ir.namyranservice.customer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.namyranservice.customer.data.CoreFailure
import ir.namyranservice.customer.data.objects
import ir.namyranservice.customer.ui.persianDigits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private class FeatureState {
    var data by mutableStateOf<JSONObject?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf("")

    suspend fun run(request: suspend () -> JSONObject): JSONObject? {
        if (loading) return null
        loading = true
        error = ""
        return try {
            request().also { data = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoreFailure) {
            error = if (e.code == "rest_no_route" || e.code == "feature_unavailable")
                "این خدمت در حال حاضر در دسترس نیست." else "دریافت پاسخ ممکن نشد. دوباره تلاش کنید."
            null
        } catch (_: Exception) {
            error = "ارتباط برقرار نشد. اتصال اینترنت را بررسی کنید."
            null
        } finally {
            loading = false
        }
    }
}

@Composable
private fun FeatureStatus(state: FeatureState) {
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun Partners(vm: CustomerViewModel, preview: Boolean = false) {
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var cursor by remember { mutableStateOf("") }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val state = remember(vm.mobile) { FeatureState() }
    val scope = rememberCoroutineScope()
    suspend fun load(append: Boolean = false) {
        val result = state.run {
            vm.customerFeature("partner-stores", JSONObject()
                .put("query", appliedQuery).put("cursor", if (append) cursor else "")
                .put("limit", if (preview) 3 else 20))
                .also { it.getJSONArray("stores") }
        } ?: return
        val page = result.getJSONArray("stores").objects()
        stores = ((if (append) stores else emptyList()) + page).distinctBy { it.optString("code") }
        cursor = result.optString("next_cursor").takeUnless { result.isNull("next_cursor") }.orEmpty()
    }
    LaunchedEffect(vm.mobile, appliedQuery) {
        stores = emptyList()
        cursor = ""
        load()
    }
    FormCard("خدمات و فروشگاه‌های همکار", Icons.Outlined.Storefront) {
        if (preview) {
            Text("فروشگاه‌های همکار را ببینید و کد فروشگاه را پیدا کنید.")
            TextButton(onClick = { vm.tab = "partners" }) { Text("مشاهده همه") }
        } else {
            OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth(),
                label = { Text("نام، شهر یا کد فروشگاه") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) })
            Button(enabled = !state.loading, onClick = {
                if (appliedQuery == query.trim()) scope.launch { load() }
                else appliedQuery = query.trim()
            }) { Text("جست‌وجوی فروشگاه") }
        }
        FeatureStatus(state)
        if (!state.loading && state.error.isNotBlank()) {
            TextButton(onClick = { scope.launch { load() } }) { Text("تلاش دوباره") }
        }
        if (stores.isEmpty() && state.data != null && !state.loading && state.error.isBlank()) {
            Text("فروشگاهی پیدا نشد.")
        }
        stores.forEach { PartnerCard(it) }
        if (!preview && cursor.isNotBlank()) {
            OutlinedButton(onClick = { scope.launch { load(append = true) } }, enabled = !state.loading) {
                Text("فروشگاه‌های بیشتر")
            }
        }
    }
}

@Composable
private fun PartnerCard(store: JSONObject) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val code = store.optString("code")
    val website = store.optString("website")
    val uri = Uri.parse(website)
    var copied by remember(code) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(
            listOf(colors.tertiaryContainer, colors.surface))).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartnerLogo(store.optString("logo_url"))
                Text(store.optString("name"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val rating = store.optDouble("rating")
                if (rating in 0.0..5.0 && !store.isNull("rating")) {
                    Icon(Icons.Outlined.Star, "امتیاز", modifier = Modifier.size(18.dp))
                    Text(persianDigits(rating.toString()))
                }
            }
            store.optString("city").takeIf { it.isNotBlank() }?.let { Text(it) }
            if (code.isNotBlank()) {
                Text("کد فروشگاه · $code")
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }) {
                    Text(if (copied) "کد کپی شد" else "کپی کد فروشگاه")
                }
            }
            if (uri.isHierarchical && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }) {
                    Text("وب‌سایت فروشگاه")
                }
            }
        }
    }
}

@Composable
private fun PartnerLogo(url: String) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        if (uri?.scheme != "https" || uri.host != java.net.URI(BuildConfig.API_BASE_URL).host || uri.userInfo != null ||
            uri.port != -1 || !uri.path.orEmpty().startsWith("/wp-content/uploads/") || uri.query != null) return@LaunchedEffect
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val connection = uri.toURL().openConnection() as java.net.HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    if (connection.responseCode != 200 || connection.contentLength !in 1..262144) return@runCatching null
                    val bytes = connection.inputStream.use { java.io.DataInputStream(it).let { input -> ByteArray(connection.contentLength).also(input::readFully) } }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return@runCatching null
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    val logo = bitmap
    if (logo == null) Icon(Icons.Outlined.Storefront, null)
    else androidx.compose.foundation.Image(logo.toComposeBitmap(), null, Modifier.size(40.dp))
}

private fun android.graphics.Bitmap.toComposeBitmap() = asImageBitmap()

@Composable
internal fun Support(vm: CustomerViewModel, onCall: (String) -> Unit, onChangeCity: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("home") }
    var ticket by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var writeId by rememberSaveable { mutableStateOf("") }
    val state = remember(vm.mobile, page, ticket) { FeatureState() }
    val scope = rememberCoroutineScope()
    fun open(next: String) { page = next; message = ""; writeId = "" }
    suspend fun request(action: String, writing: Boolean = false): JSONObject? = state.run {
        val payload = JSONObject().put("action", action).put("ticket_id", ticket)
        if (writing) {
            if (writeId.isBlank()) writeId = UUID.randomUUID().toString()
            payload.put("subject", subject.trim()).put("message", message.trim()).put("idempotency_key", writeId)
        }
        vm.customerFeature("support", payload).also {
            when (action) {
                "tickets" -> it.getJSONArray("tickets")
                "messages", "chat", "send_reply", "send_chat" -> it.getJSONArray("messages")
                "create_ticket" -> check(it.getString("ticket_id").isNotBlank())
            }
        }
    }
    BackHandler(page != "home") { if (!state.loading) open("home") }
    LaunchedEffect(page, ticket) {
        when (page) {
            "home" -> request("tickets")
            "tickets", "messages" -> request(page)
            "chat" -> {
                request("chat")
                while (state.error.isBlank() && state.data?.optBoolean("online") == true) {
                    delay(8000)
                    request("chat")
                }
            }
        }
    }
    if (page == "home") {
        Text("پشتیبانی", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SupportTile("ثبت تیکت", "ثبت و پیگیری درخواست پشتیبانی", Icons.Outlined.ConfirmationNumber,
                MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f)) { open("new") }
            SupportTile("چت آنلاین", "گفت‌وگو با کارشناس پشتیبانی", Icons.Outlined.Forum,
                MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f)) { open("chat") }
        }
        FormCard("تیکت‌های من", Icons.Outlined.ConfirmationNumber) {
            FeatureStatus(state)
            val tickets = state.data?.optJSONArray("tickets")?.objects()
            if (tickets?.isEmpty() == true) Text("هنوز تیکتی ثبت نشده است.")
            tickets?.take(2)?.forEach { row ->
                TicketRow(row, !state.loading) { ticket = it; open("messages") }
            }
            TextButton(onClick = { open("tickets") }) { Text("مشاهده همه تیکت‌ها") }
        }
        FormCard("تماس با پشتیبانی", Icons.Outlined.Call) {
            val national = vm.nationalSupport()
            Text("پشتیبانی سراسری", fontWeight = FontWeight.Bold)
            Text("ارجاع و پیگیری درخواست‌های سراسر ایران")
            OutlinedButton(onClick = { onCall(national) }, modifier = Modifier.fillMaxWidth()) {
                Text("تماس سراسری · " + persianDigits(national))
            }
            HorizontalDivider()
            val city = vm.customerCity
            val local = vm.localSupport()
            Text(if (local == null) "پشتیبانی محلی" else "پشتیبانی ${local.first}", fontWeight = FontWeight.Bold)
            Text(city?.let { "${it.province} · ${it.title}" } ?: "استان و شهر خود را انتخاب کنید.")
            if (local != null) {
                OutlinedButton(onClick = { onCall(local.second) }, modifier = Modifier.fillMaxWidth()) {
                    Text("تماس محلی · " + persianDigits(local.second))
                }
            } else if (city != null) Text("شمارهٔ محلی برای این محدوده در دسترس نیست.")
            TextButton(onClick = onChangeCity) { Text("انتخاب یا تغییر شهر") }
        }
        return
    }
    val title = when (page) {
        "new" -> "ثبت تیکت جدید"
        "tickets" -> "تیکت‌های من"
        "messages" -> "گفت‌وگوی تیکت"
        else -> "چت آنلاین"
    }
    FormCard(title, if (page == "chat") Icons.Outlined.Forum else Icons.Outlined.ConfirmationNumber) {
        TextButton(onClick = { open("home") }, enabled = !state.loading) { Text("بازگشت به پشتیبانی") }
        FeatureStatus(state)
        if (page == "new") {
            OutlinedTextField(subject, { subject = it.take(120); writeId = "" }, Modifier.fillMaxWidth(),
                enabled = !state.loading, label = { Text("موضوع تیکت") }, singleLine = true)
        } else {
            TextButton(onClick = { scope.launch { request(page) } }, enabled = !state.loading) { Text("به‌روزرسانی") }
            val rows = state.data?.optJSONArray(if (page == "tickets") "tickets" else "messages")?.objects()
            if (rows?.isEmpty() == true) Text(if (page == "tickets") "هنوز تیکتی ثبت نشده است." else "هنوز پیامی وجود ندارد.")
            rows?.forEach { row ->
                if (page == "tickets") {
                    TicketRow(row, !state.loading) { ticket = it; open("messages") }
                } else {
                    val mine = row.optString("sender") == "customer"
                    Surface(shape = RoundedCornerShape(16.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(if (mine) 0.88f else 0.95f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (mine) "شما" else "پشتیبانی", fontWeight = FontWeight.Bold)
                            Text(row.optString("message"))
                        }
                    }
                }
            }
        }
        if (page == "chat" && state.data?.optBoolean("online") == false) {
            Text("کارشناس آنلاین نیست. می‌توانید تیکت ثبت کنید.")
            TextButton(onClick = { open("new") }) { Text("ثبت تیکت") }
        }
        val canWrite = page == "new" || page == "chat" && state.data?.optBoolean("online") == true ||
            page == "messages" && state.data?.optBoolean("can_reply") == true
        if (canWrite) {
            OutlinedTextField(message, { message = it.take(4000); writeId = "" }, Modifier.fillMaxWidth(),
                enabled = !state.loading, label = { Text("متن پیام") }, minLines = 3)
            Button(enabled = !state.loading && message.isNotBlank() && (page != "new" || subject.isNotBlank()),
                modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        val action = when (page) {
                            "new" -> "create_ticket"
                            "chat" -> "send_chat"
                            else -> "send_reply"
                        }
                        val result = request(action, writing = true) ?: return@launch
                        if (page == "new") {
                            ticket = result.getString("ticket_id")
                            subject = ""
                            open("messages")
                        } else {
                            message = ""
                            writeId = ""
                        }
                    }
                }) { Text(if (page == "new") "ثبت تیکت" else "ارسال پیام") }
        }
    }
}

@Composable
private fun SupportTile(title: String, description: String, icon: ImageVector,
                        color: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 148.dp)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, color))).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = color, shape = RoundedCornerShape(16.dp)) {
                Icon(icon, null, Modifier.padding(10.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TicketRow(row: JSONObject, enabled: Boolean, onOpen: (String) -> Unit) {
    val id = row.optString("id")
    OutlinedCard(onClick = { onOpen(id) }, enabled = enabled && id.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ConfirmationNumber, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(row.optString("subject"), fontWeight = FontWeight.Bold)
                if (row.optString("code").isNotBlank()) Text("شماره تیکت · " + persianDigits(row.optString("code")))
            }
            AssistChip(onClick = { onOpen(id) }, enabled = enabled && id.isNotBlank(),
                label = { Text(row.optString("status_label").ifBlank { "جزئیات" }) })
        }
    }
}
