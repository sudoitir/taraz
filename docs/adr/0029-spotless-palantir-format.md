<div dir="rtl">

# 0029. Formatting اجباری با Spotless + palantir-java-format

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

بدون formatter اجباری، سبک کد بین توسعه‌دهنده‌ها و IDEها drift می‌گیرد و diffها با تغییرات سبکی پر می‌شوند. formatter باید: (۱) در compile اجرا شود تا کد commitنشده همیشه format شده باشد، (۲) در CI به‌صورت check اجبار شود، (۳) به قراردادهای Spring Framework (از جمله ترتیب importها) نزدیک باشد.

## تصمیم

**Spotless Maven plugin** (۳.۱۰.۰) با **palantir-java-format** (۲.۹۷.۰) در parent pom پیکربندی می‌شود:

- `palantirJavaFormat` برای layout کد؛
- `importOrder` به سبک Spring Framework: `java` ← `javax|jakarta` ← `org` ← `com` ← بقیه، static importها آخر (`\#`)؛
- `removeUnusedImports`؛
- goal `apply` به فاز `compile` متصل است (format خودکار هنگام بیلد محلی)؛
- profile `ci` در فاز `verify` goal `check` را اجرا می‌کند (کد format‌نشده = شکست CI).

## گزینه‌های بررسی‌شده

- **google-java-format (AOSP)** — رد شد: palantir خواناتر است و به سبک Spring نزدیک‌تر.
- **formatter سطح IDE (مثلاً IntelliJ)** — رد شد: به تنظیمات محلی وابسته است و در CI قابل enforce نیست.
- **اجرای check در همه‌ی بیلدهای محلی** — رد شد: apply-on-compile تجربه‌ی توسعه را روان‌تر می‌کند؛ اجبار فقط در CI کافی است.

## پیامدها

### مثبت

- سبک کد یکدست و غیرقابل مذاکره؛ diffهای تمیز؛ هیچ بحث سبکی در code review.

### منفی / بدهی فنی

- سبک palantir با سبک پیش‌فرض IntelliJ فرق دارد؛ توسعه‌دهنده باید به format خودکار عادت کند و در IDE روی آن حساس نباشد.

</div>
