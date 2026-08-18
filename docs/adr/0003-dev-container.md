<div dir="rtl">

# 0003. محیط توسعه با Dev Container

**وضعیت:** منسوخ (جایگزین‌شده با [0025](./0025-docker-compose-for-dev-infra.md))
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

محیط توسعه باید بازتولیدپذیر و مستقل از ماشین میزبان باشد (Java، Postgres، Valkey، ابزارها).

## تصمیم

محیط توسعه با **Dev Container** (`.devcontainer/`) تعریف می‌شود.

## پیامدها

- سرویس‌های زیرساختی (PostgreSQL، Valkey) در همان تعریف dev container بالا می‌آیند.

</div>
