<div dir="rtl">

# 0060. Actuator فقط با endpoint سلامت

**وضعیت:** پذیرفته‌شده

**تاریخ:** 2026-08-19

## زمینه

برنامه هیچ سیگنال سلامت از خودش ندارد؛ فقط سرویس‌های داخل compose healthcheck دارند. برای اجرای production-like (و حتی برای دانستن اینکه «برنامه واقعاً بالا آمده») endpoint سلامت لازم است. هم‌زمان قاعده‌ی چالش می‌گوید زیرساخت اضافی فقط با توجیه واقعی — پس دامنه باید حداقلی بماند.

## تصمیم

- `spring-boot-starter-actuator` فقط به ماژول `container` (محل مونتاژ برنامه) اضافه می‌شود.
- از طریق HTTP **فقط** گروه `health` expose می‌شود: `management.endpoints.web.exposure.include: health`.
- probeهای liveness و readiness فعال می‌شوند: `management.endpoint.health.probes.enabled: true` → `/actuator/health/liveness` و `/actuator/health/readiness`.
- indicatorهای db و redis و kafka با همان starterهای موجود به‌صورت خودکار فعال می‌شوند؛ هیچ indicator سفارشی نوشته نمی‌شود.
- metrics و tracing (Micrometer/Prometheus/OTel) همچنان خارج از scope می‌مانند و در بخش «وضعیت پیاده‌سازی» README ثبت‌اند.

## گزینه‌های بررسی‌شده

- **expose کردن info و metrics هم** — رد شد؛ هیچ مصرف‌کننده‌ای برایشان نیست و سطح حمله/نشت اطلاعات را بی‌دلیل بزرگ می‌کند.
- **endpoint سلامت دست‌نویس** — رد شد؛ چرخ اختراع مجدد می‌شد و وضعیت اجزا (db/cache/broker) را نمی‌داد.

## پیامدها

### مثبت

- سیگنال استاندارد سلامت/readiness برای orchestrator و برای اجرای محلی (`/actuator/health` با وضعیت db و redis و kafka).
- readiness می‌تواند در آینده به‌عنوان دروازه‌ی شروع تست‌های k6 استفاده شود.

### منفی / بدهی فنی

- سطح HTTP کمی بزرگ‌تر می‌شود؛ با محدود کردن exposure به `health` کنترل شده و بقیه‌ی endpointها 404 می‌شوند.
- احراز هویت روی actuator تعریف نشده — برای محیط ارزیابی تک‌نود قابل قبول است؛ در استقرار واقعی باید پشت شبکه‌ی داخلی/احراز هویت برود.

</div>
