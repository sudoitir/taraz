<div dir="rtl">

# 0022. هرم کامل تست

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

چالش تست را بخش اصلی داوری می‌داند: صحت (موجودی منفی ممنوع)، هم‌زمانی (۱۰۰۰ عملیات روی ۱۰۰٬۰۰۰)، idempotency (سه‌بار credit با یک transactionId)، و اتمیسیته transfer. تستِ mockمحورِ دیتابیس یا cache، raceهای واقعی (قفل سطری، SETNX، constraint) را هرگز exercise نمی‌کند — پس integration با infra واقعی اجباری است، نه لوکس.

## تصمیم

هرم تست:

- **Unit test** با JUnit 5 و **Mockito** — دامنه‌ی خالص (ADR-0005) و specificationها (ADR-0011) بدون هیچ infra.
- **Integration test** با **Testcontainers** — PostgreSQL و Valkey **واقعی**؛ raceهای قفل سطری (ADR-0026)، unique constraint و SETNX (ADR-0021) در اینجا اثبات می‌شوند.
- **Performance/load test** با **Grafana k6** (CLI) — سناریوی ۱۰۰۰ عملیات هم‌زمان چالش از مسیر HTTP واقعی (ADR-0008) و با assertion روی موجودی نهایی دقیق.

## گزینه‌های بررسی‌شده

- **فقط unit + mock** — رد شد: هیچ race واقعی (قفل، constraint، SETNX) را پوشش نمی‌دهد؛ دقیقاً همان چیزهایی که چالش داوری می‌کند.
- **حذف k6 و اکتفا به JUnit** — رد شد: JUnit درستی منطق را ثابت می‌کند؛ k6 رفتار زیر load واقعیِ HTTP (connection، virtual thread، contention) را نشان می‌دهد که در هرم پایین‌تر دیده نمی‌شود.

## پیامدها

### مثبت

- تست‌های هم‌زمانی و idempotency طبق سناریوهای چالش (`.claude/rules/challenge-testing.md`) اجباری‌اند؛ اجرای همه با `./mvnw test` و از طریق `just` (ADR-0004). تست‌های race با barrier-synchronized start احتمال interleaving را بالا می‌برند.

### منفی / بدهی فنی

- Testcontainers زمان build را بالا می‌برد و به Docker نیاز دارد؛ k6 ابزار جدا برای نصب است (از طریق just کپسوله می‌شود).

</div>
