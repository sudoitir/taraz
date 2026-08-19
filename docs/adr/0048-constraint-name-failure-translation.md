<div dir="rtl">

# 0048. ترجمه‌ی خطای persistence بر اساس نام constraint + دو ErrorCode جدید

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

ADR-0041 می‌گوید تکرار یک `transactionId` با پارامترهای متفاوت باید «یک خطای صریح client» بدهد،
نه پذیرش بی‌صدا و نه یک خطای دسته‌بندی‌نشده. اما کاتالوگ فعلی `ErrorCode` هیچ کدی برای این حالت
ندارد؛ بدون آن، `DataIntegrityViolationException` ناشی از constraint یکتای `processed_transaction`
تا لایه‌ی REST نشت می‌کند و به‌عنوان `500` مبهم سرو می‌شود — دقیقاً همان چیزی که ADR-0041 ممنوع
کرده. به همین ترتیب، اتمام timeout قفل یا استخر connection نیاز به یک کد صریح دارد تا زیر بار
واقعی به‌جای صف نامرئی، یک پاسخ typed بدهد.

## تصمیم

- دو `ErrorCode` جدید: `TRANSACTION_ID_CONFLICT` (همان `transactionId`، پارامترهای متفاوت — ADR-0041)
  و `CONCURRENCY_CONFLICT` (اتمام timeout قفل یا استخر connection).
- `PersistenceFailureTranslator` این کدها را با تطبیق **نام constraint** پستگرس تولید می‌کند —
  `pk_processed_transaction` / `uq_ledger_transaction_external_id` برای اولی، نوع exception قفل/
  timeout برای دومی — هرگز با متن پیام خطا، که قراردادی پایدار بین نسخه‌های PostgreSQL نیست.
- `TRANSACTION_ID_CONFLICT` روی HTTP **۴۰۹** و `CONCURRENCY_CONFLICT` روی HTTP **۵۰۳** (با
  `Retry-After`) نگاشت می‌شوند.
- یک تست schema نام این constraintها را صریحاً assert می‌کند تا تغییر نام آن‌ها بی‌صدا مترجم خطا
  را بی‌اثر نکند.

## گزینه‌های بررسی‌شده

- **تطبیق روی متن پیام خطا** — رد شد: متن پیام قرارداد پایدار نیست و بین نسخه‌ها/locale تغییر
  می‌کند؛ تطبیق شکننده‌ای می‌سازد که در آینده بی‌صدا می‌شکند.
- **بازگرداندن `500` عمومی برای هر دو حالت** — رد شد: مستقیماً نقض متن صریح ADR-0041.

## پیامدها

### مثبت

- سپر آخر ADR-0041 حالا واقعاً یک خطای typed و قابل‌اتکا به client می‌دهد، نه یک stack trace نشت‌کرده.
- اتمام ظرفیت (قفل یا connection pool) یک سیگنال typed و قابل‌اقدام (`Retry-After`) می‌دهد.

### منفی / بدهی فنی

- تطبیق روی نام constraint یعنی تغییر نام یک constraint در migration بعدی باید مترجم خطا را هم
  به‌روزرسانی کند؛ تست schema این را در build زودهنگام آشکار می‌کند.

</div>
