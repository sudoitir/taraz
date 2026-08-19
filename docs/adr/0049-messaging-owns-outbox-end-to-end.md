<div dir="rtl">

# 0049. مالکیت کامل outbox توسط adapter پیام‌رسانی

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

الگوی Outbox (ADR-0010) نیاز به تصمیم‌گیری دارد: کدام ماژول جدول `outbox`، پیاده‌سازی
`OutboxAppender`، و publisher را مالک است؟ اگر persistence مالک باشد، messaging باید یا به schema
persistence وابسته شود یا duplicate آن را نگه دارد؛ اگر مالکیت پخش شود، مرز اتمی «تغییر حالت +
رویداد» در دو ماژول پخش می‌شود و نگه‌داشتنش سخت‌تر است.

## تصمیم

**`adapters/driven/messaging` مالک outbox از سر تا ته است**: schema جدول `outbox` (migration
مستقل خودش)، `OutboxAppender`، قرارداد `IntegrationEvent`، و polling publisher به Kafka. این adapter
با `JdbcClient` روی همان `DataSource` مشترک به تراکنش JPA فعالِ caller می‌پیوندد (Spring
`JpaTransactionManager` اتصال تراکنش JPA را به‌عنوان `ConnectionHolder` روی `DataSource` bind
می‌کند؛ `JdbcClient` روی همان `DataSource` همان connection فیزیکی را می‌گیرد) — بدون یک persistence
stack دوم و بدون وابستگی صریح ماژول messaging به persistence.

## گزینه‌های بررسی‌شده

- **persistence مالک جدول outbox، messaging فقط publish می‌کند** — رد شد: مرز اتمی و مدل رویداد را
  بین دو ماژول پخش می‌کند؛ persistence باید یا از قرارداد IntegrationEvent باخبر باشد یا یک شکل
  خام رویداد را persist کند که با ADR-0009 (mapping در adapter، در لحظه‌ی append) در تضاد است.

## پیامدها

### مثبت

- تمام مسئولیت outbox — schema، append، publish — در یک ماژول است؛ مرز بین persistence و messaging
  دقیقاً همان مرز ماژولی است که ADR-0033 می‌خواهد.
- اتصال به تراکنش caller بدون یک persistence framework دوم انجام می‌شود.

### منفی / بدهی فنی

- messaging باید همان `DataSource` را از container دریافت کند؛ این وابستگی پیکربندی (نه وابستگی
  ماژول Maven) باید در تست schema صریح بررسی شود.

</div>
