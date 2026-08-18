<div dir="rtl">

# 0022. هرم کامل تست

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

چالش تست را بخش اصلی داوری می‌داند: صحت، هم‌زمانی، idempotency و عملکرد.

## تصمیم

هرم تست کامل:

- **Unit test** با JUnit 5 و **Mockito**
- **Integration test** با **Testcontainers** (PostgreSQL و Valkey واقعی)
- **Performance test** با **Grafana k6** (CLI)

## پیامدها

- تست‌های هم‌زمانی و idempotency طبق سناریوهای چالش (`.claude/rules/challenge-testing.md`) اجباری‌اند؛ اجرای همه با `./mvnw test` و از طریق `just` (ADR-0004).

</div>
