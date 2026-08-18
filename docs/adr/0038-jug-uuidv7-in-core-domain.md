<div dir="rtl">

# 0038. کتابخانه‌ی JUG برای UUIDv7 داخل core/domain

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

طبق ADR-0016 شناسه‌ها UUIDv7 هستند (مرتب‌شدنی بر اساس زمان، مناسب index دیتابیس). JDK هیچ generator داخلی برای v7 ندارد. دامنه طبق ADR-0005 نباید dependency بیرونی داشته باشد مگر پشت interface محلی خودش. همچنین چون روی virtual thread هستیم (ADR-0002)، هر کتابخانه‌ای که وارد core می‌شود باید از نظر pinning بررسی شود.

## تصمیم

- از `com.fasterxml.uuid:java-uuid-generator` (JUG) با `Generators.timeBasedEpochGenerator()` استفاده می‌شود — پیاده‌سازی RFC 9562 v7.
- JUG فقط در یک کلاس (`UuidV7IdGenerator`) import می‌شود و بقیه‌ی دامنه interface محلی `IdGenerator` را می‌بیند — «external need = local interface» در ADR-0005.
- version در root `pom.xml` به‌صورت property `jug.version` و dependencyManagement پین می‌شود (نسخه‌ی فعلی: 5.2.0).
- تحلیل pinning: JUG برای تولید v7 داخلش `synchronized` کوتاهی روی خواندن ساعت دارد؛ critical section چند نانوثانیه کار کاملاً in-memory است و هیچ blocking call داخلش نیست، پس carrier thread عملاً pin نمی‌شود — همان خطرناکی که ADR-0002 از آن هشدار می‌دهد (blocking I/O داخل synchronized) اینجا وجود ندارد. generator مستنداً thread-safe است و یک instance بین threadها share می‌شود.

## گزینه‌های بررسی‌شده

- **پیاده‌سازی دستی v7** — رد شد: جزئیات RFC (بیت‌های version/variant، monotonicity داخل یک میلی‌ثانیه) پر از edge case است؛ بازتولید کتابخانه‌ی بالغ هیچ ارزشی ندارد.
- **`uuid-creator`** — رد شد: قابلیت مشابه JUG؛ JUG maintainer فعال‌تر (FasterXML) و API ساده‌تری دارد. دلیلی برای دو نامزد هم‌ارز نمانده بود.
- **صبر برای پشتیبانی JDK** — رد شد: در Java 21 وجود ندارد و roadmap مشخصی ندارد.

## پیامدها

### مثبت

- UUIDv7 استاندارد و مرتب‌شدنی بدون کد دستی؛ دامنه از کتابخانه جدا است و تعویض پیاده‌سازی فقط یک کلاس را لمس می‌کند؛ با virtual threadها سازگار.

### منفی / بدهی فنی

- اولین compile dependency در `core/domain`؛ توصیف ماژول از «zero dependencies» به «zero framework dependencies» اصلاح می‌شود که با متن واقعی ADR-0005 (ممنوعیت framework/ORM/transport) هم‌خوان است.

</div>
