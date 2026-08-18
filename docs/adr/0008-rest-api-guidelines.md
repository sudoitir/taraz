<div dir="rtl">

# 0008. رهنمودهای REST API بر مبنای Zalando

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

HTTP API در چالش اجباری نیست (حداقل، interface داخلی `BalanceService` است)، اما بدون آن داور نمی‌تواند سرویس را از بیرون drive کند و تست e2e و سناریوی k6 معنا پیدا نمی‌کند. اگر API می‌آید، باید از یک rulebook استاندارد پیروی کند تا قراردادش بدون توضیح شفاهی قابل‌داوری باشد.

## تصمیم

REST API ارائه می‌شود و از **Zalando RESTful API Guidelines** پیروی می‌کند؛ فرمان‌های موفق با **`201 Created`** و هدر **`Location`** پاسخ می‌دهند و هدرهای استاندارد رعایت می‌شوند. Controllerها driving adapter خالص‌اند (ADR-0006): فقط ترجمه‌ی HTTP به command/query.

- مرجع: http://opensource.zalando.com/restful-api-guidelines/

## گزینه‌های بررسی‌شده

- **بدون HTTP (حداقل چالش)** — رد شد: e2e و داوریِ black-box را غیرممکن می‌کند؛ سرویسِ «موجودی» بدون API قابل‌لمس نیست.
- **طراحی ad-hoc** — رد شد: هر تصمیم قراردادی (status code، خطا، pagination) بحث ذهنی تازه می‌خواست؛ rulebook آماده این بحث‌ها را حذف می‌کند.

## پیامدها

### مثبت

- قرارداد API قابل‌پیش‌بینی و مستند است؛ تست k6 و e2e روی HTTP واقعی اجرا می‌شود (ADR-0022).

### منفی / بدهی فنی

- هزینه‌ی نگاشت wire ↔ دامنه (DTO و validation در driving adapter)؛ نگه‌داشت guideline compliance در review.

</div>
