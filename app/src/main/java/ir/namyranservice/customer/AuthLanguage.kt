package ir.namyranservice.customer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.LocalTextStyle

internal val LocalAuthEnglish = compositionLocalOf { false }
private val englishAuthCopy = mapOf(
    "نامیران سرویس" to "Namyran Service",
    "ورود به حساب کاربری" to "Sign in to your account",
    "ورود با پیامک" to "Sign in with SMS",
    "ورود با نام کاربری و رمز عبور" to "Username and password",
    "ورود با نام کاربری" to "Sign in with username",
    "نام کاربری" to "Username",
    "رمز عبور" to "Password",
    "شماره موبایل" to "Mobile number",
    "شماره‌ام را به خاطر بسپار" to "Remember my number",
    "ورود با حساب Google" to "Sign in with Google",
    "ورود امن و محافظت از اطلاعات شما" to "Secure sign-in. Your information is protected.",
    "فراموشی رمز عبور؟" to "Forgot password?",
    "ثبت‌نام" to "Register",
    "راهنما" to "Help",
    "پشتیبانی" to "Support",
    "یا" to "or",
    "ادامه با پیامک" to "Continue with SMS",
    "کد تأیید پیامکی" to "SMS verification code",
    "کد آزمایشی را وارد کنید." to "Enter the test code.",
    "کد آزمایشی صحیح نیست." to "The test code is incorrect.",
    "تأیید و ورود" to "Verify and sign in",
    "ارسال مجدد کد" to "Resend code",
    "آمادهٔ ارسال" to "Ready to resend",
    "بازگشت و ویرایش شماره موبایل" to "Back and edit mobile number",
    "ایجاد حساب مشتری" to "Create a customer account",
    "بازیابی دسترسی به حساب" to "Recover account access",
    "ورود با رمز عبور در حال حاضر در دسترس نیست. برای ورود به حساب از پیامک استفاده کنید." to "Password sign-in is currently unavailable. Please use SMS to access your account.",
    "با تأیید شماره همراه، دسترسی مشتری به درخواست‌ها و خدمات فعال می‌شود." to "Verify your mobile number to access your requests and services.",
    "شماره همراه حساب را تأیید کنید تا دوباره وارد شوید. تغییر رمز عبور پس از فعال‌شدن این قابلیت در دسترس خواهد بود." to "Verify your account's mobile number to sign in again. Password changes are not available yet.",
    "شماره موبایل نامعتبر است. شماره را به صورت ۰۹xxxxxxxxx وارد کنید." to "Enter a valid Iranian mobile number: 09xxxxxxxxx.",
    "اعتبار کد تأیید پایان یافته است. کد جدید دریافت کنید." to "This verification code has expired. Request a new code.",
    "بازکردن شماره‌گیر ممکن نبود." to "Unable to open the phone dialer.",
    "راهنمای ورود" to "Sign-in help",
    "متوجه شدم" to "Got it",
    "شماره همراه خود را وارد کنید و کد پیامک را تأیید کنید. کد این نسخه چهاررقمی است. اگر اجازه دهید، کد پیامک در فرم قرار می‌گیرد. پس از ورود، نشست حساب روی گوشی ذخیره می‌شود." to "Enter your mobile number and verify the four-digit SMS code. With your permission, the code can be filled automatically. Your session is stored securely on this device.",
    "امنیت اطلاعات" to "Data security",
    "پشتیبانی سریع" to "Quick support",
    "خدمات با ضمانت" to "Guaranteed services",
    "تکنسین تأییدشده" to "Verified technicians",
    "خدمات تخصصی تعمیر و سرویس\nلوازم خانگی و تلویزیون" to "Expert repairs and servicing\nHome appliances and televisions",
    "در حال آماده‌سازی حساب شما" to "Preparing your account",
    "تلاش دوباره" to "Try again"
)

@Composable
internal fun AuthText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, style: TextStyle = LocalTextStyle.current) {
    val translated = if (!LocalAuthEnglish.current) text else englishAuthCopy[text] ?: when {
        text.startsWith("کد ارسال‌شده به ") -> "Code sent to " + text.removePrefix("کد ارسال‌شده به ")
        text.startsWith("ارسال دوباره تا ") -> "Resend in " + text.removePrefix("ارسال دوباره تا ").removeSuffix(" ثانیه دیگر") + " seconds"
        else -> text
    }
    androidx.compose.material3.Text(translated, modifier, color = color, fontSize = fontSize,
        fontWeight = fontWeight, textAlign = textAlign, style = style)
}
