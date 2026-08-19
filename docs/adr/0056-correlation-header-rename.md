<div dir="rtl">

# 0056. تغییر نام هدر correlation به X-Correlation-ID؛ هدر Kafka مطابق قرارداد Spring Kafka

**وضعیت:** پذیرفته‌شده (تکمیل/اصلاح [ADR-0043](./0043-rest-contract-details.md))

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

ADR-0043 نام `X-Flow-ID` را برای correlation انتخاب کرد (اصطلاح Zalando). در کار همین تغییر، هدر
معادل روی رویدادهای Kafka هم باید تعریف می‌شد. `X-Flow-ID` یک اصطلاح خاصِ rulebook Zalando است، نه
یک قرارداد به‌طور گسترده شناخته‌شده در اکوسیستم HTTP/Kafka؛ **`X-Correlation-ID`** نامی است که در
عمل بیشتر شناخته و پیاده‌سازی می‌شود، و Spring Kafka خودش برای correlation یک ثابت رسمی دارد
(`KafkaHeaders.CORRELATION_ID` = `"kafka_correlationId"`) که ابزارهای request/reply آن پروژه
(`ReplyingKafkaTemplate`, `@KafkaListener`) بی‌نیاز از پیکربندی اضافه می‌شناسند.

## تصمیم

- هدر HTTP از `X-Flow-ID` به **`X-Correlation-ID`** تغییر نام می‌دهد؛ رفتار دقیقاً همان می‌ماند
  (echo وقتی client می‌فرستد، تولید وقتی غایب است، روی هر پاسخ از جمله خطاها، bind به MDC).
- کلاس `FlowIdFilter` به **`CorrelationIdFilter`** تغییر نام می‌دهد؛ کلید MDC از `flow_id` به
  **`correlation_id`** تغییر می‌کند (`RestHeaders.CORRELATION_ID_MDC_KEY`).
- ستون outbox و ثابت‌های adapter پیام‌رسانی (ADR-0052) به همین ترتیب: `flow_id` → **`correlation_id`**.
- هدر روی رکورد Kafka منتشرشده **`kafka_correlationId`** است — دقیقاً همان ثابتی که
  `org.springframework.kafka.support.KafkaHeaders.CORRELATION_ID` تعریف می‌کند، نه یک نام سفارشی.

## گزینه‌های بررسی‌شده

- **نگه‌داشتن `X-Flow-ID`** — رد شد: فقط در rulebook Zalando رایج است؛ خارج از آن اکوسیستم بیشتر
  توسعه‌دهنده‌ها با `X-Correlation-ID` آشنا هستند.
- **همان نام سفارشی (`X-Flow-ID` یا `X-Correlation-ID`) روی هدر Kafka هم** — رد شد: هزینه‌ی هم‌راستا
  کردن با ثابت رسمی Spring Kafka عملاً صفر است و همین امروز (یا هر زمان که consumer واقعی اضافه شود)
  با ابزارهای خودِ آن اکوسیستم بدون تنظیم اضافه کار می‌کند.

## پیامدها

### مثبت

- نام هدر HTTP با قراردادی که خارج از Zalando هم شناخته‌شده است هم‌راستا شد.
- هدر Kafka از روز اول با قرارداد رسمی Spring Kafka سازگار است، بدون نیاز به mapping دستی در آینده.

### منفی / بدهی فنی

- تغییر نام کد قبلاً پذیرفته‌شده (`FlowIdFilter` → `CorrelationIdFilter`) — هزینه‌اش یک rename محدود
  است، چون این تغییر پیش از هر deploy واقعی رخ داد.

</div>
