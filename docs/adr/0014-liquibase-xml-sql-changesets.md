<div dir="rtl">

# 0014. Migration با Liquibase: changelog در XML، changeset در SQL

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

تغییرات schema باید versioned، قابل‌بازبینی و تکرارپذیر باشد؛ auto-DDL از روی Entity (مثل `ddl-auto`) schema واقعی را از review خارج می‌کند و در یک سرویس مالی غیرقابل‌قبول است. انضباط‌های ADR-0019 (ترتیب ستون، index آگاهانه) فقط با DDL صریح قابل‌اجراست.

## تصمیم

**Liquibase** با changelogهای **XML** که changesetهای آن‌ها به‌صورت **SQL خالص** نوشته می‌شوند؛ XML فقط اسکلت نسخه‌بندی و checksum است.

## گزینه‌های بررسی‌شده

- **Hibernate `ddl-auto`** — رد شد: schema از review خارج می‌شود؛ خروجی تولیدشده با انضباط‌های ADR-0019 کنترل نمی‌شود.
- **Flyway با SQL ساده** — رد شد: ساده‌تر است، ولی انضباط checksum، rollback و contextهای Liquibase برای تغییر schema در سرویس مالی ارزشمندتر از سادگی‌اش است.
- **changeset در XML (بدون SQL خالص)** — رد شد: abstraction روی DDL کنترل دقیق ستون‌ها و indexها را می‌گیرد.

## پیامدها

### مثبت

- SQL خالص کنترل دقیق روی DDL می‌دهد (ستون‌ها، indexها — ADR-0019)؛ هر تغییر schema versioned و در review قابل‌اشاره است.

### منفی / بدهی فنی

- دو فرمت برای نگهداشت (XML + SQL)؛ rollback باید صریح نوشته شود، خودکار نیست.

</div>
