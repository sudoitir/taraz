<div dir="rtl">

# 0043. جزئیات قرارداد REST: هدرهای idempotency، correlation و Problem Details

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

ADR-0008 اصل ارائه‌ی REST API بر مبنای Zalando را پذیرفت، اما چند تصمیم قراردادی را باز گذاشت: چگونه `transactionId` دامنه روی HTTP بیاید، پاسخِ replay چه شکلی باشد، خطاها چه قالبی داشته باشند و correlation چگونه انجام شود. بدون ثبت این تصمیم‌ها، controllerها هر کدام به سلیقه‌ی خودشان پیاده می‌شدند.

## تصمیم

- **هدر `Idempotency-Key`** روی همه‌ی POSTهای مالی (credit/debit/transfer) اجباری است و دقیقاً همان `transactionId` دامنه است — مفهوم جداگانه‌ای وجود ندارد. نبودن یا خالی‌بودن آن `400` با کد `INVALID_TRANSACTION_ID` می‌دهد، پیش از هر lookup.
- **Replay همان پاسخ اصلی را برمی‌گرداند**: همان `201 Created`، همان `Location`، همان بدنه؛ تنها تفاوت هدر **`Idempotency-Replayed: true`** است. (درخواست یکسان → پاسخ یکسان؛ معنای واقعی idempotency.)
- **`X-Flow-ID`** برای correlation: اگر client بفرستد echo می‌شود، وگرنه سرور تولید می‌کند؛ روی همه‌ی پاسخ‌ها (از جمله خطاها) هست و در لاگ‌ها (MDC) می‌آید.
- **خطاها RFC 7807 Problem Details** هستند، با `ProblemDetail` داخلی Spring (نه کتابخانه‌ی `problems` زالاندو — Spring 6+ این را native پوشش می‌دهد). هر problem یک extension member به نام `code` دارد با نامِ `ErrorCode` تا client روی کد assert کند، نه روی متن. نگاشت: `INVALID_AMOUNT`/`INVALID_ACCOUNT_ID`/`INVALID_TRANSACTION_ID` → 400، `ACCOUNT_NOT_FOUND` → 404، `INSUFFICIENT_FUNDS`/`SAME_ACCOUNT_TRANSFER` → 422، invariantهای داخلی → 500 بدون لو دادن جزئیات.
- **JSON با نام‌گذاری snake_case** (راهنمای Zalando) و مبالغ به‌صورت عدد صحیحِ minor units (ADR-0036 — تک‌ارزی، بدون money object روی wire).
- **`GET /accounts/{id}/balance`** با `Cache-Control: no-store` پاسخ می‌دهد — خواندن مالی هرگز cacheable نیست.
- replay مسئولیتِ یکسانی روی همه‌ی endpointها دارد: یک `Idempotency-Key` تکراری روی endpoint یا بدنه‌ی متفاوت هم «همان تراکنش» محسوب می‌شود (منسجم با ADR-0041)؛ یکتایی کلید در سراسر سرویس مسئولیت client است.

## گزینه‌های بررسی‌شده

- **`201` در اولین اجرا و `200` در replay** — رد شد: شفاف‌تر روی سیم است اما معنای «درخواست یکسان، پاسخ یکسان» را می‌شکند.
- **کتابخانه‌ی `org.zalando:problem` و `problem-spring-web`** — رد شد: پشتیبانی native اسپرینگ از ProblemDetail همان را پوشش می‌دهد؛ وابستگی اضافی چیزی نمی‌خرد.
- **`transactionId` در بدنه به‌جای هدر** — رد شد: هدرِ استانداردِ درحال‌گسترشِ `Idempotency-Key` دقیقاً برای همین ساخته شده و بدنه را برای داده‌ی عملیات نگه می‌دارد.

## پیامدها

### مثبت

- قرارداد کامل و قابل‌داوری بدون توضیح شفاهی؛ تست‌های slice روی هدرها و problem‌ها دقیق assert می‌کنند.

### منفی / بدهی فنی

- client باید یکتایی `Idempotency-Key` را در سراسر سرویس تضمین کند (نه per-endpoint)؛ در README مستند می‌شود.

</div>
