# Namyran Customer — Stage Debug via GitHub Actions (v0.2.1)

**وضعیت:** آماده ارسال به GitHub Actions برای آزمایش Build؛ هنوز خروجی APK در این محیط کامپایل و تأیید نشده است.

## ساخت در GitHub، بدون Android Studio

1. یک مخزن خصوصی GitHub بسازید، مثلاً `namyran-customer-android`.
2. **محتویات داخل این ZIP** را در ریشه مخزن آپلود کنید (نه خود فایل ZIP را).
3. در مخزن به **Actions** بروید و Workflow با نام `Namyran Customer Stage APK | Phase 10` را باز کنید.
4. دکمه **Run workflow** را بزنید و شاخه پیش‌فرض را انتخاب کنید.
5. پس از موفقیت Job، از پایین صفحه اجرای Workflow بخش **Artifacts**، فایل `Namyran-Customer-Stage-v0.2.1-debug` را دانلود کنید.
6. ZIP مربوط به Artifact را باز کنید؛ `app-debug.apk` را روی گوشی Android نصب و آزمایش کنید.

## محدودیت‌های ایمنی

- `applicationId`: `ir.namyranservice.customer.stage`
- فقط نشانی Stage در `BuildConfig` وجود دارد.
- بدون INTERNET permission، بدون OTP و بدون ارسال درخواست واقعی؛ همه بخش‌های متکی به Core غیرفعال‌اند.
- Workflow با اجرای دستی ساخته می‌شود، Production و Core تغییر نمی‌کنند.
- از امضای Debug مخصوص runner استفاده می‌شود؛ برای آپدیت‌های متوالی روی همان دستگاه ممکن است به دلیل متفاوت‌بودن کلید امضا نیاز به حذف نسخه قبلی باشد.
- این فایل APK در صورت Build موفق برای آزمایش UI است، نه انتشار Production.

## نکته Build

فایل‌های Gradle Wrapper داخل بسته نیستند؛ Workflow با `gradle/actions/setup-gradle@v4` نسخه دقیق Gradle 9.6.0 را روی runner نصب می‌کند و از دستور `gradle :app:assembleDebug` استفاده می‌کند. بنابراین برای **GitHub Actions** نیازی به `gradlew` نیست. اجرای مستقیم `./gradlew` در این بسته ممکن نیست.

نسخه‌ها: AGP 9.4.0، Gradle 9.6.0، JDK 17، Kotlin Compose Compiler Plugin 2.4.10، compileSdk 37.

## پیش‌نیاز بررسی خطا

اگر Action شکست خورد، اولین خطای `e:` کامپایلر یا متن Failed Task را از Build Job ارسال کنید. موفقیت تست‌های Python جایگزین کامپایل Android نیست.

**مرحله ۱۰ ادامه دارد؛ مرحله ۱۱ شروع نشده است.**
