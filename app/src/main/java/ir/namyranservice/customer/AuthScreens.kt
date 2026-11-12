package ir.namyranservice.customer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ir.namyranservice.customer.data.normalizedDigits
import ir.namyranservice.customer.ui.persianDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gold = Color(0xFFC69212)
private val Electric = Color(0xFF008FD5)
private val Ink = Color(0xFFF1F5FB)
private val AuthColors = lightColorScheme(primary = Color(0xFF075CB4), onPrimary = Color.White,
    secondary = Electric, background = Ink, surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF142337), onSurfaceVariant = Color(0xFF4E6076),
    outline = Color(0xFF9AAEC7), error = Color(0xFFAE263A))

@Composable
internal fun CustomerLogin(vm: CustomerViewModel) {
    var page by rememberSaveable { mutableStateOf("sms") }
    var phone by rememberSaveable { mutableStateOf(vm.mobile.takeIf { vm.otpSent } ?: vm.rememberedMobile) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var revealPassword by remember { mutableStateOf(false) }
    var rememberPhone by rememberSaveable { mutableStateOf(vm.rememberedMobile.isNotBlank()) }
    var code by remember { mutableStateOf("") }
    var pendingSms by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var sendingGoogle by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val languagePrefs = remember { context.getSharedPreferences("customer_language", 0) }
    var english by rememberSaveable { mutableStateOf(languagePrefs.getBoolean("english", false)) }
    val scope = rememberCoroutineScope()
    val formScroll = rememberScrollState()
    val busy = vm.busy || listening || sendingGoogle
    val codeStep = vm.otpSent
    LaunchedEffect(page, codeStep) { formScroll.scrollTo(0) }
    val listen = rememberSmsCodeConsent { received ->
        pendingSms = received
        if (vm.otpSent) code = received
    }
    fun changePage(next: String) {
        if (busy) return
        password = ""; revealPassword = false; localError = ""; vm.dismissError()
        page = next
    }
    fun send() {
        if (busy) return
        localError = ""; code = ""; pendingSms = ""
        if (!phone.matches(Regex("09[0-9]{9}"))) {
            localError = "شماره موبایل نامعتبر است. شماره را به صورت ۰۹xxxxxxxxx وارد کنید."
            return
        }
        listening = true
        listen { vm.sendOtp(phone, rememberPhone); listening = false }
    }
    BackHandler(!vm.hasSavedSession && (codeStep || page != "sms")) {
        if (!busy) {
            if (codeStep) { code = ""; vm.editOtpMobile() }
            else changePage("sms")
        }
    }
    LaunchedEffect(vm.resendAt, vm.otpExpiresAt) {
        while (now < maxOf(vm.resendAt, vm.otpExpiresAt)) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(vm.otpGeneration) {
        code = pendingSms
        pendingSms = ""
    }
    val remaining = ((vm.resendAt - now + 999) / 1000).coerceAtLeast(0)
    val expired = vm.otpSent && vm.otpExpiresAt > 0 && now >= vm.otpExpiresAt

    CompositionLocalProvider(LocalAuthEnglish provides english,
        LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl) {
    MaterialTheme(colorScheme = AuthColors) {
        if (vm.hasSavedSession) {
            SessionOpening(vm)
            return@MaterialTheme
        }
        Scaffold(containerColor = Ink, contentWindowInsets = WindowInsets.systemBars) { padding ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xFFDBE5F2), Color(0xFFF4F7FC), Color(0xFFDFE9F4)))).padding(padding)
                .consumeWindowInsets(padding).imePadding()) {
                Image(painterResource(R.drawable.auth_silver_environment), null,
                    Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter)
                    .verticalScroll(formScroll).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Absolute.Right) {
                        TextButton(onClick = {
                            english = !english
                            languagePrefs.edit().putBoolean("english", english).apply()
                        }, modifier = Modifier.semantics { contentDescription = "Change login language" },
                            colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFF1B354E), contentColor = Color.White)) {
                            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            androidx.compose.material3.Text(if (english) "English" else "فارسی")
                            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(16.dp))
                        }
                    }
                    AuthBrand()
                    AuthPanel {
                        if (codeStep) {
                            AuthHeading("کد تأیید پیامکی", Icons.Outlined.Sms)
                            AuthText("کد ارسال‌شده به " + persianDigits(vm.mobile), textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OtpInput(code, { code = normalizedDigits(it).filter { c -> c in '0'..'9' }.take(4); localError = ""; vm.dismissError() },
                                enabled = !busy && !expired)
                            if (expired) AuthError("اعتبار کد تأیید پایان یافته است. کد جدید دریافت کنید.", "nsc_otp_expired")
                            AuthError(localError.ifBlank { vm.error }, vm.lastFailureCode)
                            if (vm.message.isNotBlank()) AuthText(vm.message, color = Electric)
                            GoldButton("تأیید و ورود", !busy && code.length == 4 && !expired) {
                                vm.verifyOtp(code)
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = ::send, enabled = !busy && (remaining == 0L)) {
                                    Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(4.dp)); AuthText("ارسال مجدد کد")
                                }
                                AuthText(if (remaining > 0) persianDigits("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}") else "آمادهٔ ارسال",
                                    color = Gold, style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(enabled = !busy, onClick = {
                                code = ""; localError = ""; vm.editOtpMobile()
                            }, modifier = Modifier.fillMaxWidth()) { AuthText("بازگشت و ویرایش شماره موبایل") }
                        } else {
                            AuthHeading(when (page) {
                                "password" -> "ورود با نام کاربری"
                                "register" -> "ایجاد حساب مشتری"
                                "reset" -> "بازیابی دسترسی به حساب"
                                else -> "ورود به حساب کاربری"
                            }, if (page == "password") Icons.Outlined.PersonOutline else Icons.Outlined.PersonOutline)
                            if (vm.message.isNotBlank()) AuthText(vm.message, color = Electric)
                            if (page == "password") {
                                AuthField("نام کاربری", username, { username = it.take(100); localError = "" }, Icons.Outlined.PersonOutline, !busy)
                                AuthField("رمز عبور", password, { password = it.take(256); localError = "" }, Icons.Outlined.Lock, !busy,
                                    type = KeyboardType.Password,
                                    transformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailing = {
                                        IconButton(onClick = { revealPassword = !revealPassword }) {
                                            Icon(if (revealPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                if (revealPassword) "پنهان‌کردن رمز" else "نمایش رمز")
                                        }
                                    })
                                TextButton(onClick = { changePage("reset") }, enabled = !busy) { AuthText("فراموشی رمز عبور؟", color = Electric) }
                                AuthText("ورود با رمز عبور در حال حاضر در دسترس نیست. برای ورود به حساب از پیامک استفاده کنید.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                GoldButton("ادامه با پیامک", !busy) { changePage("sms") }
                            } else {
                                if (page == "register") AuthText("با تأیید شماره همراه، دسترسی مشتری به درخواست‌ها و خدمات فعال می‌شود.")
                                if (page == "reset") AuthText("شماره همراه حساب را تأیید کنید تا دوباره وارد شوید. تغییر رمز عبور پس از فعال‌شدن این قابلیت در دسترس خواهد بود.")
                                AuthField("شماره موبایل", phone, {
                                    phone = normalizedDigits(it).filter { c -> c in '0'..'9' }.take(11)
                                    localError = ""; vm.dismissError()
                                }, Icons.Outlined.PhoneAndroid, !busy, KeyboardType.Phone)
                                Row(Modifier.fillMaxWidth().toggleableAuth(!busy, rememberPhone) {
                                    rememberPhone = it; if (!it) vm.rememberMobile(false)
                                }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(rememberPhone, null, enabled = !busy)
                                    AuthText("شماره‌ام را به خاطر بسپار", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                                }
                                AuthError(localError.ifBlank { vm.error }, vm.lastFailureCode)
                                GoldButton("ورود با پیامک", !busy && remaining == 0L, ::send)
                                if (remaining > 0) AuthText("ارسال دوباره تا " + persianDigits(remaining.toString()) + " ثانیه دیگر", color = Gold)
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(Modifier.weight(1f)); AuthText("یا"); HorizontalDivider(Modifier.weight(1f))
                            }
                            if (page != "password") OutlinedButton(onClick = { changePage("password") },
                                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.8f))) {
                                Icon(Icons.Outlined.PersonOutline, null)
                                Spacer(Modifier.width(8.dp))
                                AuthText("ورود با نام کاربری و رمز عبور", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(enabled = !busy, onClick = {
                                password = ""; sendingGoogle = true; localError = ""
                                scope.launch {
                                    try { vm.signInWithGoogle(context) }
                                    finally { sendingGoogle = false }
                                }
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, Color(0xFF88919D)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF172332))) {
                                AuthText("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                Spacer(Modifier.width(8.dp))
                                AuthText("ورود با حساب Google", color = Color.White)
                            }
                            if (page == "password") AuthError(localError.ifBlank { vm.error }, vm.lastFailureCode)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { changePage("register") }, enabled = !busy) { AuthText("ثبت‌نام") }
                                TextButton(onClick = { help = true }) { AuthText("راهنما") }
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", vm.nationalSupport(), null))) }
                                        .onFailure { localError = "بازکردن شماره‌گیر ممکن نبود." }
                                }) { AuthText("پشتیبانی") }
                            }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Outlined.Lock, null, tint = Gold, modifier = Modifier.size(16.dp))
                        AuthText("ورود امن و محافظت از اطلاعات شما", color = Color(0xFF233B59), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { AuthText("راهنمای ورود") },
            text = { AuthText("شماره همراه خود را وارد کنید و کد پیامک را تأیید کنید. کد این نسخه چهاررقمی است. اگر اجازه دهید، کد پیامک در فرم قرار می‌گیرد. پس از ورود، نشست حساب روی گوشی ذخیره می‌شود.") },
            confirmButton = { TextButton(onClick = { help = false }) { AuthText("متوجه شدم") } })
    }
}

}

private fun Modifier.toggleableAuth(enabled: Boolean, value: Boolean, onChange: (Boolean) -> Unit): Modifier =
    this.semantics { role = Role.Checkbox; toggleableState = if (value) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
        .clickable(enabled = enabled) { onChange(!value) }

@Composable
private fun AuthPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().shadow(14.dp, shape).background(Brush.linearGradient(listOf(Electric, Color(0xFF53CCF8), Electric)), shape).padding(1.5.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFFDFEFF), Color(0xFFE8F2FF))), shape)
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun AuthHeading(title: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Electric, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(8.dp))
        AuthText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AuthField(label: String, value: String, onChange: (String) -> Unit, icon: ImageVector, enabled: Boolean,
                      type: KeyboardType = KeyboardType.Text, transformation: VisualTransformation = VisualTransformation.None,
                      trailing: (@Composable () -> Unit)? = null) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { AuthText(label) },
        enabled = enabled, singleLine = true, shape = RoundedCornerShape(16.dp),
        leadingIcon = { Icon(icon, null) }, trailingIcon = trailing ?: if (type == KeyboardType.Phone) ({
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text("🇮🇷 +98", color = Color(0xFF17395B), fontWeight = FontWeight.SemiBold)
                }
            }
        }) else null,
        visualTransformation = transformation, keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Electric, unfocusedBorderColor = Color(0xFF87929E),
            focusedContainerColor = Color(0xFFF8FBFF), unfocusedContainerColor = Color(0xFFEEF3F9)))
}

@Composable
private fun GoldButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(
        Brush.verticalGradient(if (enabled) listOf(Color(0xFF129AFF), Color(0xFF0751BB), Color(0xFF003E8C)) else listOf(Color(0xFFD3DEEB), Color(0xFFBCCADD))))) {
        Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = shape, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)) {
            AuthText(title, color = if (enabled) Color.White else Color(0xFF52657D), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Login, null, tint = if (enabled) Color.White else Color(0xFF52657D))
        }
    }
}

@Composable
private fun MethodButton(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, modifier = modifier.heightIn(min = 55.dp), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Electric else Color(0xFF697582)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color(0xFF063255) else Color(0xFF28323B))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) Electric else Color.White)
            AuthText(title, textAlign = TextAlign.Center, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OtpInput(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(value, onChange, enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "کد چهاررقمی تأیید" },
            decorationBox = { inner ->
                Box {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                        repeat(4) { index ->
                            Box(Modifier.weight(1f).height(56.dp).border(1.5.dp,
                                if (index == value.length && enabled) Electric else Color(0xFF8391A0), RoundedCornerShape(13.dp))
                                .background(Color(0xFFF3F8FF), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                AuthText(persianDigits(value.getOrNull(index)?.toString().orEmpty()), color = Color(0xFF17395B),
                                    style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    Box(Modifier.matchParentSize().alpha(0.01f)) { inner() }
                }
            })
    }
}

@Composable
private fun AuthError(message: String, code: String) {
    if (message.isBlank()) return
    val icon = when {
        code.contains("expired") -> Icons.Outlined.TimerOff
        code.contains("network") || code.contains("non_json") -> Icons.Outlined.WifiOff
        code.contains("otp") -> Icons.Outlined.SmsFailed
        else -> Icons.Outlined.ErrorOutline
    }
    Surface(color = Color(0xFFFFEFF0), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFFAB4855))) {
        Row(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFB12B3A))
            AuthText(message, color = Color(0xFF912434), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AuthBrand() {
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 700
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (!keyboardOpen) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.namyran_symbol), "نشان نامیران سرویس", Modifier.size(if (compact) 100.dp else 146.dp), contentScale = ContentScale.Fit)
            AuthText("نامیران سرویس", color = Color(0xFF183B60), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            AuthText("خدمات تخصصی تعمیر و سرویس\nلوازم خانگی و تلویزیون",
                modifier = Modifier.widthIn(max = 240.dp).padding(top = 4.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = Color(0xFF203B57))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Assurance(Icons.Outlined.Lock, "امنیت اطلاعات")
            Assurance(Icons.Outlined.HeadsetMic, "پشتیبانی سریع")
            Assurance(Icons.Outlined.WorkspacePremium, "خدمات با ضمانت")
            Assurance(Icons.Outlined.VerifiedUser, "تکنسین تأییدشده")
        }
    }
}

@Composable
private fun RowScope.Assurance(icon: ImageVector, title: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(33.dp).shadow(4.dp, RoundedCornerShape(11.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1878BF), Color(0xFF073D70))), RoundedCornerShape(11.dp))
            .border(1.dp, Color(0xFF9CD4F4), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(23.dp), tint = Color(0xFFFFD34A))
        }
        AuthText(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF203852), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SessionOpening(vm: CustomerViewModel) {
    val animation = rememberInfiniteTransition(label = "Brand loading")
    val opacity by animation.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "Logo pulse")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF0A204A), Color(0xFF0757B5), Color(0xFF007E8B)))).safeDrawingPadding(),
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(30.dp).widthIn(max = 400.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(R.drawable.namyran_symbol), "نشان نامیران سرویس", Modifier.size(130.dp).alpha(opacity))
            AuthText("نامیران سرویس", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            AuthText("در حال آماده‌سازی حساب شما", color = Color.White, textAlign = TextAlign.Center)
            if (vm.busy) CircularProgressIndicator(color = Color.White)
            else {
                AuthError(vm.error, vm.lastFailureCode)
                Button(onClick = vm::refresh) { AuthText("تلاش دوباره") }
            }
        }
    }
}
