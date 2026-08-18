<div dir="rtl">

# 0034. مرز commandمحور application service — validation روی command و idempotency در command handler

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

باید مشخص شود application service دقیقاً چه چیزی را به‌عنوان ورودی می‌پذیرد، validation کجا اتفاق می‌افتد و idempotency در کدام لایه تضمین می‌شود. اگر DTOهای REST مستقیم به service برسند، قرارداد لایه‌ی application به framework وب آلوده می‌شود؛ اگر validation در controller انجام شود، قواعد یکسان برای سایر driving adapterها تکرار یا از دست می‌رود؛ و اگر idempotency در adapter باشد، معنای «transactionId دقیقاً یک بار اثر می‌گذارد» (invariant چالش) به لایه‌ی ناپایدار منتقل می‌شود.

## تصمیم

- **Application service فقط command می‌پذیرد.** ورودی هر use case یک command immutable (record) در پکیج `core.application.ports.inbound` است — نه DTO وب، نه entity.
- **نگاشت DTO → command در adapter انجام می‌شود:** ماژول `rest` با MapStruct درخواست HTTP را به command تبدیل می‌کند. command از مرز adapter عبور می‌کند، DTO هرگز.
- **Annotationهای jakarta validation روی خود command قرار می‌گیرند** (`@NotNull`، `@Positive`، …) و **command handler قبل از اجرای منطق، command را validate می‌کند** (`jakarta.validation.Validator`). بنابراین validation بخشی از قرارداد use case است و مستقل از ورودی HTTP عمل می‌کند.
- **Idempotency در لایه‌ی command handler تضمین می‌شود:** handler در ابتدای اجرا `transactionId` را از طریق outbound port مربوطه (Valkey، طبق ADR-0021) بررسی/ثبت می‌کند؛ تکرارِ یک transactionId — حتی همزمان — به no-op امن ختم می‌شود و هرگز اثر دوم روی موجودی ندارد. adapter هیچ نقشی در idempotency ندارد.

## گزینه‌های بررسی‌شده

- **عبور DTO وب تا service** — رد شد: core به annotations/framework وب وابسته می‌شد (نقض ADR-0005).
- **Validation فقط در controller با `@Valid`** — رد شد: قانون validation از use case جدا می‌ماند و برای adapterهای آینده (consumerها) تکرار یا فراموش می‌شد.
- **Idempotency در persistence/adapter با unique constraint صرف** — رد شد: معنای retry امن باید در منطق use case صریح باشد؛ ADR-0021 هم بررسی Valkey را قبل از DB می‌خواهد.

## پیامدها

### مثبت

- قرارداد application کاملاً framework-agnostic می‌ماند؛ validation یک بار و در یک جا تعریف می‌شود و برای همه‌ی driving adapterها اعمال می‌شود؛ idempotency جزئی از semantics هر use case است، نه تصادفِ پیاده‌سازی adapter.

### منفی / بدهی فنی

- یک لایه‌ی نگاشت اضافه (DTO → command) در rest؛ handlerها باید خودشان `Validator` را صدا بزنند (کد تکراری کوتاه در هر handler که با یک helper کوچک قابل جمع شدن است).

</div>
