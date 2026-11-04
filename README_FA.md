# اپ مشتری Namyran Service — Native Stage

Kotlin و Jetpack Compose؛ شناسه نصب `ir.namyranservice.customer.stage`.

## وضعیت

این شاخه در حال اتصال و آزمون پذیرش است؛ موفقیت Build به معنی تأیید پیامک، ثبت دیتابیس یا آماده بودن انتشار نیست. گزارش نهایی و وضعیت تست‌های زنده در `docs/STAGE_EXECUTION_REPORT_FA.md` ثبت می‌شوند. ادغام و Production مجاز نیستند.

## اتصال

اپ به `https://stage.namyranservice.ir` محدود است و Redirect را دنبال نمی‌کند. ورود و عملیات مشتری از مسیرهای موجود `namyran-service/v1` استفاده می‌کنند. هیچ رمز مدیریتی، OTP ثابت یا داده نمونه‌ای در نسخه اپ وجود ندارد.

افزونه مستقل `stage-adapter/namyran-customer-stage-bridge` باید فقط در Stage نصب و فعال شود. این افزونه دو مسیر GET در `namyran-customer/v1` برای config و schedule فراهم می‌کند. کاتالوگ از پیکربندی فعلی فرم Core، شهرها از `NSC_Geo_Data::active_service_catalog` و زمان‌بندی از `NSC_Utils::is_visit_slot_available` خوانده می‌شوند؛ ثبت و احراز هویت تغییر نمی‌کنند. فعال شدن افزونه در دامنه اصلی رد می‌شود.

مدل دستگاه و شرح مشکل در Core فعلی متن آزاد هستند. زمان‌ها پیشنهاد مراجعه‌اند، نه رزرو ظرفیت. شهر و محله همراه نشانی به Core ارسال می‌شوند؛ API فعلی ثبت، فیلد مجزای city را ذخیره نمی‌کند.

نشست و پیش‌نویس با Android Keystore رمزگذاری می‌شوند. ثبت مبهم با همان submission_token بازیابی می‌شود و به شماره حساب اولیه محدود است.

## بررسی

با Java 17، Android SDK 36 و Gradle 9.6.0:

```
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 audit/check_stage_only.py
python3 scripts/check_ci_configuration.py
php stage-adapter/tests/bridge-test.php
```

گزارش‌های Android و APK در GitHub Actions ذخیره می‌شوند. آزمون‌های PHP دارای fixture واحد هستند؛ این fixtureها در افزونه نصب یا APK استفاده نمی‌شوند.
