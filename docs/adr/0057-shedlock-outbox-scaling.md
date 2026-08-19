<div dir="rtl">

# 0057. ShedLock برای هماهنگی scheduler outbox زیر scale-out افقی

**وضعیت:** پذیرفته‌شده (تکمیل [ADR-0055](./0055-outbox-delivery-policy.md))

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

ADR-0055 صراحتاً پذیرفت که هماهنگی publisher/cleanup فقط برای یک poller تک‌نمونه‌ای صحیح است: با
بیش از یک pod، `FOR UPDATE SKIP LOCKED` از publish دوگانه‌ی یک ردیف جلوگیری می‌کند، اما هر pod
همچنان هر ۲۰۰ms به‌طور مستقل poll می‌کند — یعنی با N نمونه، N برابر query claim غیرلازم روی جدول
داغ، و ترتیب انتشار بین‌pod تضمین نمی‌شود. سرویس باید بتواند با تعداد pod مقیاس بگیرد (Kubernetes
horizontal scale-out یک سناریوی واقعی برای این سرویس مالی است)، پس این محدودیت باید حل شود، نه فقط
مستند بماند.

## تصمیم

**ShedLock** (`net.javacrumbs.shedlock`, نسخه‌ی ۷.۸.۰) با provider مبتنی‌بر JDBC روی همان
PostgreSQL موجود (بدون زیرساخت سوم) هماهنگی scheduler را تضمین می‌کند: در هر لحظه دقیقاً یک pod
قفل هر task را می‌گیرد.

- جدول `shedlock` (migration مستقل، schema دقیقاً مطابق قرارداد JDBC provider خودِ ShedLock) —
  مالکیت با adapter پیام‌رسانی (ADR-0049).
- `@EnableSchedulerLock(defaultLockAtMostFor = "5m")` روی `OutboxPublisherConfiguration`؛
  `LockProvider` با `JdbcTemplateLockProvider` و `.usingDbTime()` — زمان دیتابیس، نه ساعت هر pod،
  تا drift ساعت بین podها باعث انقضای زودهنگام/دیرهنگام قفل نشود.
- `OutboxPollingPublisher.publishBatch()`: `@SchedulerLock(name = "outbox-poll", lockAtMostFor =
  "30s")` — سقفی به‌مراتب بالاتر از یک دسته‌ی واقعی، ولی به‌اندازه‌ی کافی کوتاه که یک pod مرده قفل
  را برای مدت طولانی نگه ندارد؛ بدون `lockAtLeastFor` چون بیشتر tickها batch خالی می‌بینند و نباید
  قفل را مصنوعی نگه دارند.
- `OutboxCleanupJob.cleanupPublishedRows()`: `@SchedulerLock(name = "outbox-cleanup", lockAtMostFor
  = "10m")` — job روزانه‌ای که می‌تواند واقعاً طولانی اجرا شود.

## گزینه‌های بررسی‌شده

- **بدون هماهنگی، تکیه بر `SKIP LOCKED` تنها (تصمیم قبلی، ADR-0055)** — رد شد: ایمن برای هر ردیف
  است اما مقیاس‌پذیر نیست — هر pod اضافه یعنی query claim تکراری روی جدول داغ، و ترتیب انتشار
  تضمین نمی‌شود.
- **قفل توزیع‌شده روی Valkey** — رد شد: یک زیرساخت سوم مستقیماً برای این هماهنگی توجیه نمی‌شود،
  وقتی PostgreSQL که همین حالا system of record است همان تضمین را با یک جدول کوچک می‌دهد؛ ADR-0020
  همین استدلال را برای رد قفل توزیع‌شده در idempotency هم به کار برد.
- **poller partition-aware دستی (هر pod فقط زیرمجموعه‌ای از ردیف‌ها را claim کند)** — رد شد: طراحی
  و نگهداری پیچیده‌تر برای همان نتیجه‌ای که یک کتابخانه‌ی بالغ و تک‌منظوره می‌دهد.

## پیامدها

### مثبت

- outbox واقعاً با تعداد pod مقیاس می‌گیرد: در هر لحظه دقیقاً یک pod poll/cleanup می‌کند، بدون query
  تکراری روی جدول داغ و بدون ابهام ترتیب انتشار.
- بدون زیرساخت سوم — همان PostgreSQL موجود.

### منفی / بدهی فنی

- یک وابستگی و یک جدول اضافه (`shedlock`)؛ `lockAtMostFor` باید با رفتار واقعی batch هم‌راستا نگه
  داشته شود — عدد خیلی کوچک یعنی preemption زودهنگام یک batch مشروع، عدد خیلی بزرگ یعنی pod مرده
  قفل را برای مدت طولانی نگه می‌دارد.
- throughput واقعی همچنان به یک pod «فعال» در هر لحظه محدود است — این تصمیم هماهنگی را حل می‌کند،
  نه موازی‌سازی واقعی انتشار؛ اگر روزی throughput یک pod کافی نبود، طراحی جدیدی (partition‌بندی
  واقعی) لازم است.

</div>
