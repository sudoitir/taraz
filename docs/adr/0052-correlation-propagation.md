<div dir="rtl">

# 0052. انتشار correlation با ثابت MDC تکراری و تست معماری

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

`CorrelationIdFilter` (ADR-0043/0056) از قبل `X-Correlation-ID` را در MDC با کلید `correlation_id`
تولید می‌کند — سؤال این تغییر منشأ نیست، سه گام باقی‌مانده است: MDC → ردیف outbox → هدر Kafka → MDC
روی thread پابلیشر. محدودیت: `messaging` نباید به `adapters.driving.rest` وابسته شود (قاعده‌ی
ArchUnit)، و پکیج outbound ports فقط اجازه‌ی interface/record/enum دارد — پس نه یک outbound port
جدید و نه یک ثابت در `port` راه‌حل تمیزی می‌دهند.

## تصمیم

نام کلید MDC به‌عنوان یک ثابت public روی `RestHeaders` (`CORRELATION_ID_MDC_KEY`) ارتقا می‌یابد؛ یک
ثابت معادل در `messaging` (`MessagingCorrelation.CORRELATION_ID_MDC_KEY`) اعلام می‌شود؛ و یک تست در
`architecture-tests` برابری این دو رشته را assert می‌کند — تکرار به یک invariant تضمین‌شده در build
تبدیل می‌شود، نه یک رشته‌ی جادویی بی‌محافظ.

مسیر: MDC (`CorrelationIdFilter`) → `JdbcOutboxAppender` مقدار MDC را می‌خواند و در
`outbox.correlation_id` ذخیره می‌کند → publisher هدر Kafka **`kafka_correlationId`** را می‌گذارد —
ثابت رسمی `KafkaHeaders.CORRELATION_ID` در Spring Kafka، نه نام هدر HTTP (ADR-0056 دلیل این انتخاب
را مستند می‌کند) → publisher با try-with-resources دور هر send، MDC را روی thread خودش بازیابی
می‌کند. وقتی correlation id غایب است، مقدار `NULL` ذخیره می‌شود و هدر حذف می‌شود — هرگز مقداری
ساختگی تولید نمی‌شود (یک correlation id ساختگی در جست‌وجوی لاگ از یک correlation id واقعی
قابل‌تشخیص نیست).

## گزینه‌های بررسی‌شده

- **outbound port جدید `CorrelationContext`** — رد شد: یک دغدغه‌ی transport را از پورت
  DomainEvent که ADR-0009 عمداً تمیز نگه داشته عبور می‌دهد؛ هیچ‌چیز در `core` واقعاً به correlation
  id نیاز ندارد.
- **ثابت در ماژول `port`** — رد شد: قاعده‌ی ArchUnit `ports_contain_only_contracts_and_value_types`
  را می‌شکند.
- **ماژول مشترک جدید فقط برای یک رشته** — رد شد: نامتناسب.

## پیامدها

### مثبت

- correlation از HTTP تا Kafka بدون افزودن یک وابستگی ماژولی جدید جریان می‌یابد.

### منفی / بدهی فنی

- دو مقدار literal برابر در دو ماژول؛ محافظت‌شده با تست، نه با قرارداد کامپایلری.

</div>
