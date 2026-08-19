<div dir="rtl">

# 0050. شکل قرارداد IntegrationEvent؛ outbox بایت‌های نهایی wire را ذخیره می‌کند

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

ADR-0009 جداسازی `DomainEvent`/`IntegrationEvent` را تصمیم گرفته اما شکل دقیق قرارداد بیرونی و
این‌که چه چیزی در ردیف outbox ذخیره شود را باز گذاشته. اگر ردیف outbox رویداد داخلی خام را نگه
دارد، publisher باید در لحظه‌ی publish دوباره map و serialize کند — یعنی رفتار publish‌شده می‌تواند
از رفتار commit‌شده جدا بیفتد اگر mapper بین آن دو لحظه تغییر کند.

## تصمیم

- قرارداد `IntegrationEventEnvelope` (فیلدهای پایدار: `eventId`، `eventType`، `eventVersion`،
  `aggregateType`، `aggregateId`، `transactionId؟`، `flowId؟`، `occurredAt` ISO-8601، `data`) با
  رکوردهای payload نسخه‌بندی‌شده (`AccountOpenedV1`, `AccountCreditedV1`, …) — نسخه در نام نوع، نه
  یک فیلد جدا، تا افزودن v2 additive بماند.
- مبالغ پولی همیشه **رشته‌ی decimal** هستند، هرگز عدد JSON — `Money` دقیق و نامحدود است (ADR-0036)
  و یک عدد JSON می‌تواند از مسیر یک double با از‌دست‌رفتن دقت رد شود.
- **ردیف outbox بایت‌های نهاییِ سریالایز‌شده‌ی wire را در لحظه‌ی append ذخیره می‌کند.** Publisher
  آن‌ها را عیناً به Kafka کپی می‌کند (`ByteArraySerializer`) — یک لوله‌ی ساده، بدون منطق mapping،
  ایمن در برابر drift بین لحظه‌ی append و لحظه‌ی publish.

## گزینه‌های بررسی‌شده

- **ذخیره‌ی DomainEvent خام، map در لحظه‌ی publish** — رد شد: امکان drift بین commit و publish را
  باز می‌گذارد؛ publisher باید به مدل داخلی دامنه وابسته شود.
- **مبالغ به‌صورت عدد JSON** — رد شد: خطر از‌دست‌رفتن دقت روی مقادیر دقیق `BigDecimal`.

## پیامدها

### مثبت

- رویداد منتشرشده دقیقاً همان چیزی است که commit شده؛ publisher نیازی به دانستن قرارداد ندارد.

### منفی / بدهی فنی

- سریالایز کردن در لحظه‌ی append انجام می‌شود، نه publish؛ تغییر قرارداد یعنی migration نسخه در
  خودِ append، نه فقط در publisher.

</div>
