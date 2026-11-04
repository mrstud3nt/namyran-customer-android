> گزارش تاریخیِ پیش از اتصال Native؛ وضعیت جاری در `docs/STAGE_EXECUTION_REPORT_FA.md` است.

# ممیزی خواندنی قراردادهای مشتری Core — Stage

تاریخ: ۱۴۰۵/۰۸/۱۳ (2026-11-04)

## شواهد بررسی‌شده
- محیط: `https://stage.namyranservice.ir`، با اتصال خواندنی WPVibe
- `namyran-service-core/namyran-service-core.php`: نسخه `2.0.0-alpha.5.81`؛ `NSC_DB_VERSION=2.0.0-alpha.5.5`
- `includes/class-nsc-rest.php`: ثبت ۱۱ مسیر و بررسی متدهای درخواست مشتری
- `config/endpoint-policies-mobile-alpha56.json`: احراز هویت با nonce عمومی برای مسیرهای اولیه و نشست‌های تأییدشده برای مسیرهای مشتری
- Easy MCP: پاسخ ۴۰۳ در برخی درخواست‌ها؛ WPVibe GET و بعضی درخواست‌های Easy MCP موفق؛ علت دقیق ۴۰۳ هنوز قطعی نیست.

## مسیرهای تعریف‌شده در سورس
تمام مسیرهای زیر `POST` و در فضای `/wp-json/namyran-service/v1` قرار دارند:
`otp/send`, `otp/verify`, `otp/session`, `otp/logout`, `requests`, `track`, `my-requests`, `customer-addresses`, `refund/request`, `feedback`, `connection/check`.

## حقایق منتخب از سورس؛ نه تأیید عملکرد زنده
- ارسال OTP شماره `mobile` و `purpose` (پیش‌فرض `customer_service`) دارد و در کد محدودیت تلاش و فاصله ارسال وجود دارد.
- تأیید OTP از `mobile`، `purpose` و `code` استفاده می‌کند؛ پاسخ موفق دارای `success`، `purpose`، `verification_token`، `verified_for` است (داده حساس بازخوانی نشده است).
- اعتبارسنجی نشست به `mobile` و `verification_token` متکی است؛ کوکی customer_session در ابزارهای وب استفاده می‌شود.
- ایجاد درخواست نیازمند `mobile`، `verification_token`، `submission_token` معتبر (حداقل ۱۶ نویسه)، اطلاعات خدمت، آدرس و زمان است. `visit_date` باید میلادی معتبر و `visit_time` از بازه‌های شناخته‌شده Core باشد. پذیرش شرایط با `terms_accepted` بررسی می‌شود. نام مشتری و نشانی نیز اعتبارسنجی می‌شوند.
- شناسه `submission_token` برای بازیابی ثبت تکراری در سورس به کار می‌رود؛ دقیق‌بودن رفتار هم‌زمان باید با تست Stage تأیید شود.
- پیگیری به `tracking_code`، `mobile`، `client_id` و در جریان مرتبط `session_token` متکی است؛ پاسخ ممکن است `tracking_session_token` و توکن‌های مجاز اقدام مشتری داشته باشد.
- `my-requests` با شماره موبایل تأییدشده و `verification_token` عمل می‌کند و فهرست درخواست‌ها را محدود می‌کند.

## مسائل باز برای اتصال Native
1. اعتبارسنجی روش دستیابی امن به `X-NSC-Public-Nonce` بدون وابستگی به صفحه وب یا تعبیه nonce ثابت در APK.
2. طراحی امن چرخه توکن Native و انقضا، ابطال، جابه‌جایی شبکه و کنترل دستگاه.
3. بررسی ثبت درخواست با ظرفیت واقعی شهر، بدون ساختن زمان ساختگی.
4. تست ورود/ثبت/پیگیری با کاربران و داده‌های ساختگی آزمایشی در Stage، بدون استفاده از اطلاعات کاربران Production.
5. بررسی قراردادهای دستگاه، پشتیبانی، ضمانت، پرداخت، اعلان و OTP با نقش‌های حداقلی.
6. پیاده‌سازی لایه API صرفاً بعد از عقد قرارداد و آزمایش امنیتی؛ در سورس فعلی اینترنت فعال نیست.

## خطای 403
ثبت بعضی درخواست‌های مدیریتی Easy MCP با `403 Forbidden` و از سوی دیگر موفقیت خواندن Stage با WPVibe نشان می‌دهد خطای 403 معادل قطعی کامل سایت نیست. علت مشخص (پروکسی/WAF، توکن، محدودیت اتصال یا مجوز) **تأیید نشده**؛ نباید برای رفع آن امنیت Core، Policy Registry یا WAF بی‌برنامه غیرفعال شود.
