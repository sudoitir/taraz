<div dir="rtl">

# 0058. نام‌گذاری JSON در REST API: camelCase به‌جای snake_case

**وضعیت:** پذیرفته‌شده (اصلاح بخش نام‌گذاری JSON در [ADR-0043](./0043-rest-contract-details.md)؛ بقیه‌ی آن تصمیم — idempotency، replay، Problem Details، هدر correlation طبق [ADR-0056](./0056-correlation-header-rename.md) — دست‌نخورده است)

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

ADR-0043 برای JSON روی سیم REST، نام‌گذاری snake_case (طبق راهنمای Zalando) را پذیرفت و با
`spring.jackson.property-naming-strategy: SNAKE_CASE` پیاده شد. درخواست صریح توسعه‌دهنده تغییر این
قرارداد به camelCase — سبک native جاوا و پیش‌فرض Jackson — است.

## تصمیم

`spring.jackson.property-naming-strategy` از `SNAKE_CASE` حذف/به `LOWER_CAMEL_CASE` تغییر می‌کند؛ چون
فیلدهای Java در DTOها همین الان camelCase هستند، این یعنی حذف یک لایه‌ی تبدیل، نه افزودنش. کلیدهای JSON
در بدنه‌ی request/response REST از این پس camelCase‌اند: مثلاً `account_id` → `accountId`،
`transaction_id` → `transactionId`، `balance_after` (در صورت وجود) → `balanceAfter`.

این تصمیم **فقط لایه‌ی REST** را در برمی‌گیرد. قرارداد event‌های Kafka (`IntegrationEventEnvelope` در
`adapters/driven/messaging`) از ابتدا با یک `ObjectMapper` جدا و مستقل از bean اسپرینگ سریالایز
می‌شود (ADR-0050) و همیشه camelCase بوده — این تصمیم چیزی آنجا تغییر نمی‌دهد.

## گزینه‌های بررسی‌شده

- **نگه‌داشتن snake_case** — رد شد: تصمیم صریح توسعه‌دهنده.
- **camelCase فقط برای response، snake_case برای request** — رد شد: ناسازگاری بی‌دلیل بین دو جهت یک
  قرارداد واحد؛ هیچ سودی ندارد.

## پیامدها

### مثبت

- یک قرارداد یکدست camelCase در سراسر REST و messaging؛ دیگر نیازی به تبدیل ذهنی بین دو سبک نام‌گذاری
  در لایه‌های مختلف نیست.

### منفی / بدهی فنی

- هر client یا اسکریپت تست (شامل k6، در صورت وجود) که به‌صورت hard-code به کلیدهای snake_case وابسته
  بود باید به‌روزرسانی شود.

</div>
