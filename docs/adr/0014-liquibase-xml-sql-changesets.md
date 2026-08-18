<div dir="rtl">

# 0014. Migration با Liquibase: changelog در XML، changeset در SQL

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

تغییرات schema باید versioned، قابل‌بازبینی و تکرارپذیر باشد.

## تصمیم

**Liquibase** با changelogهای **XML** که changesetهای آن‌ها به‌صورت **SQL خالص** نوشته می‌شوند.

## پیامدها

- SQL خالص کنترل دقیق روی DDL می‌دهد (ستون‌ها، indexها — ADR-0019)؛ XML فقط اسکلت نسخه‌بندی است.

</div>
