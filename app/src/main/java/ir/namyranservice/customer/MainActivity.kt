package ir.namyranservice.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.namyranservice.customer.design.NamyranColors
import ir.namyranservice.customer.design.NamyranTheme
import ir.namyranservice.customer.domain.RequestDraft
import ir.namyranservice.customer.domain.RequestRules
import ir.namyranservice.customer.ui.GradientAction
import ir.namyranservice.customer.ui.InfoPanel

private enum class RootTab(val label: String) {
    HOME("خانه"), REQUESTS("درخواست‌ها"), CREATE("ثبت درخواست"), SUPPORT("پشتیبانی"), PROFILE("حساب کاربری")
}

/** Navigation remains offline: all customer data is deliberately absent until Stage auth passes. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CustomerApplication() }
    }
}

@Composable
private fun CustomerApplication() {
    var dark by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(RootTab.HOME) }
    var detail by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableIntStateOf(-1) }
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(RequestDraft()) }
    var validationRequested by rememberSaveable { mutableStateOf(false) }
    BackHandler(step >= 0 || detail.isNotBlank()) {
        if (step > 0) { step--; validationRequested = false }
        else if (step == 0) step = -1
        else detail = ""
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        NamyranTheme(dark) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (step < 0 && detail.isBlank()) NavigationBar {
                        RootTab.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = tab == dest,
                                onClick = {
                                    if (dest == RootTab.CREATE) {
                                        step = 0
                                        validationRequested = false
                                    } else { tab = dest; detail = "" }
                                },
                                icon = { Icon(when (dest) {
                                    RootTab.HOME -> Icons.Outlined.Home
                                    RootTab.REQUESTS -> Icons.Outlined.List
                                    RootTab.CREATE -> Icons.Outlined.AddCircleOutline
                                    RootTab.SUPPORT -> Icons.Outlined.SupportAgent
                                    RootTab.PROFILE -> Icons.Outlined.PersonOutline
                                }, contentDescription = null) },
                                label = { Text(dest.label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    TopHeader(dark, onThemeToggle = { dark = !dark })
                    if (step >= 0) {
                        RequestSteps(step, draft, validationRequested, onDraftChange = { draft = it },
                            onBack = { if (step > 0) step-- else step = -1; validationRequested = false },
                            onContinue = {
                                validationRequested = true
                                if (RequestRules.check(step, draft).isEmpty() && step < 3) {
                                    step++; validationRequested = false
                                }
                            }, onClose = { step = -1 })
                    } else if (detail.isNotBlank()) {
                        TextButton(onClick = { detail = "" }) { Text("بازگشت به حساب کاربری") }
                        DetailPage(detail)
                    } else when (tab) {
                        RootTab.HOME -> HomePage(onStart = { step = 0 }, onNavigate = { tab = it })
                        RootTab.REQUESTS -> RequestsPage()
                        RootTab.CREATE -> Unit
                        RootTab.SUPPORT -> SupportPage()
                        RootTab.PROFILE -> ProfilePage(onOpen = { detail = it })
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private val draftSaver = androidx.compose.runtime.saveable.listSaver<RequestDraft, Any>(
    save = { listOf(it.service, it.appliance, it.brand, it.model, it.modelUnknown, it.description, it.province, it.city, it.address, it.termsAccepted) },
    restore = { RequestDraft(it[0] as String, it[1] as String, it[2] as String, it[3] as String, it[4] as Boolean, it[5] as String, it[6] as String, it[7] as String, it[8] as String, it[9] as Boolean) }
)

@Composable
private fun TopHeader(dark: Boolean, onThemeToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("نامیران سرویس", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("نسخه آزمایشی آفلاین · Stage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onThemeToggle) { Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, "تغییر حالت نمایش") }
    }
}

@Composable
private fun HomePage(onStart: () -> Unit, onNavigate: (RootTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(NamyranColors.blue, NamyranColors.cyan)), RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("نصب اصولی، سرمایش واقعی", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Text("خدمات نصب، سرویس و تعمیر لوازم خانگی", color = Color.White)
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = NamyranColors.blue)) { Text("ثبت درخواست جدید") }
    }
    Text("دسته‌های خدمات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    listOf("کولر گازی", "یخچال و فریزر", "ماشین لباسشویی", "ماشین ظرفشویی", "تلویزیون", "سایر خدمات").chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { name ->
                Card(onClick = onStart, modifier = Modifier.weight(1f).height(116.dp), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(if (name == "کولر گازی") Icons.Outlined.AcUnit else if (name == "تلویزیون") Icons.Outlined.Tv else Icons.Outlined.Build, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(name, textAlign = TextAlign.Center, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    InfoPanel("درخواست‌های فعال", "تا زمان ورود امن و تأیید APIهای Stage، اطلاعات شخصی یا مأموریت واقعی نمایش داده نمی‌شود.", "مشاهده درخواست‌ها") { onNavigate(RootTab.REQUESTS) }
}

@Composable private fun RequestsPage() {
    Text("درخواست‌های من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    InfoPanel("هنوز به حساب وصل نشده‌اید", "درخواست‌ها، وضعیت تکنسین، تایم‌لاین و تاریخچه تنها پس از احراز هویت مشتری و دریافت داده از Core نمایش داده می‌شوند.")
    InfoPanel("پیگیری و جزئیات مأموریت", "نمایش تأخیر، لغو، خطا، فاکتور و تکمیل خدمت در این نسخه به داده آزمایشی ساختگی متکی نیست. عملیات واقعی غیرفعال است.")
}

@Composable private fun SupportPage() {
    Text("پشتیبانی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    InfoPanel("مرکز پشتیبانی", "چت، تیکت و تماس در نسخه نهایی با APIهای معتبر Stage فعال می‌شوند؛ در این نسخه ارسال پیام انجام نمی‌شود.")
    InfoPanel("راهنمای درخواست", "پس از ثبت قطعی درخواست در Core، شماره پیگیری و وضعیت واقعی در همین بخش قابل مشاهده خواهد بود.")
}

@Composable private fun ProfilePage(onOpen: (String) -> Unit) {
    Text("حساب کاربری", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    InfoPanel("ورود امن", "ورود با OTP فقط بعد از تأیید قرارداد نشست Native در Stage فعال خواهد شد؛ در این نسخه هیچ شماره موبایلی ارسال نمی‌شود.")
    listOf("دستگاه‌های من", "آدرس‌ها", "اعلان‌ها", "فاکتورها", "ضمانت خدمات", "تاریخچه درخواست‌ها", "تنظیمات").forEach { name ->
        Card(onClick = { onOpen(name) }, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
            }
        }
    }
}

@Composable private fun DetailPage(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val descriptions = mapOf(
        "دستگاه‌های من" to "فهرست دستگاه‌ها و مدل‌های ثبت‌شده پس از احراز هویت و دریافت اطلاعات واقعی نمایش داده می‌شود.",
        "آدرس‌ها" to "نشانی‌های ذخیره‌شده به صورت امن از API مشتری در Stage دریافت خواهند شد.",
        "اعلان‌ها" to "برای اعلان‌های مأموریت و پرداخت به قرارداد اطلاع‌رسانی معتبر نیاز است.",
        "فاکتورها" to "فقط مبالغ و اسناد تأییدشده Core مجاز به نمایش هستند.",
        "ضمانت خدمات" to "ضمانت معتبر و تاریخ آن باید از Warranty Core خوانده شود.",
        "تاریخچه درخواست‌ها" to "فقط پرونده‌های متعلق به نشست تأییدشده مشتری نمایش داده می‌شوند.",
        "تنظیمات" to "حالت روشن/تاریک از دکمه بالای برنامه قابل تغییر است؛ سایر تنظیمات پس از تکمیل زیرساخت اضافه خواهند شد."
    )
    InfoPanel("در انتظار اتصال امن Stage", descriptions[title] ?: "داده‌ای از سرور دریافت نشده است.")
}

@Composable
private fun RequestSteps(step: Int, draft: RequestDraft, showErrors: Boolean, onDraftChange: (RequestDraft) -> Unit, onBack: () -> Unit, onContinue: () -> Unit, onClose: () -> Unit) {
    val titles = listOf("انتخاب خدمت و دستگاه", "شرح مشکل", "نشانی خدمت", "زمان مراجعه", "بازبینی و تأیید")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("درخواست جدید", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClose) { Text("بستن") }
    }
    Text("مرحله ${step + 1} از ۵ · ${titles[step]}", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(5) { i -> Box(Modifier.weight(1f).height(6.dp).background(if (i <= step) NamyranColors.blue else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) }
    }
    if (step == 0) {
        Text("نوع خدمت")
        listOf("تعمیر", "نصب و راه‌اندازی", "سرویس دوره‌ای", "عیب‌یابی").forEach { Choice(it, draft.service == it) { onDraftChange(draft.copy(service = it)) } }
        Text("نوع دستگاه")
        listOf("کولر گازی", "یخچال و فریزر", "ماشین لباسشویی", "ماشین ظرفشویی", "تلویزیون").forEach { Choice(it, draft.appliance == it) { onDraftChange(draft.copy(appliance = it)) } }
        OutlinedTextField(draft.brand, { onDraftChange(draft.copy(brand = it)) }, label = { Text("برند دستگاه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (!draft.modelUnknown) OutlinedTextField(draft.model, { onDraftChange(draft.copy(model = it)) }, label = { Text("مدل دستگاه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(draft.modelUnknown, { onDraftChange(draft.copy(modelUnknown = it)) })
            Text("مدل را نمی‌دانم")
        }
    }
    if (step == 1) {
        OutlinedTextField(draft.description, { onDraftChange(draft.copy(description = it)) }, label = { Text("شرح مشکل یا نیاز به خدمات") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Text("تصاویر و ویدیو بعد از تأیید دسترسی فایل خصوصی Core اضافه خواهند شد.", style = MaterialTheme.typography.bodySmall)
    }
    if (step == 2) {
        OutlinedTextField(draft.province, { onDraftChange(draft.copy(province = it, city = "")) }, label = { Text("استان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(draft.city, { onDraftChange(draft.copy(city = it)) }, label = { Text("شهر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(draft.address, { onDraftChange(draft.copy(address = it)) }, label = { Text("نشانی دقیق") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Text("ورودی فعلی صرفاً پیش‌نویس است. انتخاب فهرستی استان/شهر و کنترل محدوده پوشش باید با داده معتبر Stage تکمیل شود.", style = MaterialTheme.typography.bodySmall)
    }
    if (step == 3) {
        InfoPanel("ظرفیت معتبر دریافت نشده است", "هیچ بازه زمانی تأییدنشده‌ای برای رزرو نمایش داده نمی‌شود. ابتدا باید API ظرفیت واقعی شهر و خدمت از Core در محیط Stage تأیید شود.")
    }
    if (step == 4) {
        InfoPanel("ثبت نهایی غیرفعال است", "بدون احراز هویت، پذیرش شرایط، زمان معتبر و پاسخ موفق Core شماره پیگیری ایجاد نمی‌شود.")
    }
    if (showErrors) RequestRules.check(step, draft).forEach { error -> Text("• ${error.explanation}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("بازگشت") }
        Box(Modifier.weight(1f)) {
            GradientAction(if (step >= 3) "در انتظار اتصال Stage" else "ادامه", enabled = step < 3, onClick = onContinue)
        }
    }
    Text("پیش‌نمایش آفلاین؛ هیچ اطلاعاتی به Stage یا Production ارسال نمی‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
