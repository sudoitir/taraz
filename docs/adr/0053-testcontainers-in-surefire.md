<div dir="rtl">

# 0053. Testcontainers داخل surefire می‌ماند؛ auto-skip بدون Docker + پرچم اجبار در CI

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

سه منبع الزام هم‌زمان وجود دارد: `.claude/rules/challenge-testing.md` می‌گوید «تست‌ها باید با
دستور استاندارد `./mvnw test` اجرا شوند»؛ مشخصه‌ی زنده‌ی `project-scaffolding` می‌گوید همین دستور
بدون Docker هم باید کار کند؛ و ADR-0022 وجود تست‌های Testcontainers را الزامی می‌کند. یک راه رایج
حل این تنش جدا کردن IT ها به فاز `verify` با plugin `failsafe` است — اما این کار دقیقاً همان
مدرکی را که چالش داوری می‌کند (اثبات concurrency/idempotency روی زیرساخت واقعی) از دستور نام‌برده‌ی
چالش بیرون می‌برد.

## تصمیم

تست‌های Testcontainers **داخل surefire می‌مانند** و با `./mvnw test` اجرا می‌شوند:
`@Testcontainers(disabledWithoutDocker = true)` به‌علاوه‌ی یک `ExecutionCondition` سفارشی که وقتی
`-Dtaraz.require.docker=true` تنظیم شده و Docker در دسترس نیست، **exception پرتاب می‌کند** (نه
صرفاً disable) — پروفایل Maven `ci` این پرچم را تنظیم می‌کند. نتیجه: `./mvnw test` روی یک ماشین
بدون Docker همچنان سبز می‌ماند (skip تمیز)، ولی یک اجرای CI بدون Docker با شکست بلند اعلام می‌شود،
نه با یک build سبز که اثبات concurrency را بی‌صدا رد کرده.

## گزینه‌های بررسی‌شده

- **جداسازی failsafe/`verify`** — رد شد: مدرک درجه‌بندی‌شده‌ی چالش را از دستور استانداردِ نام‌برده
  بیرون می‌برد؛ دقیقاً نقض `.claude/rules/challenge-testing.md`.

## پیامدها

### مثبت

- یک دستور واحد (`./mvnw test`) هم برای reviewer بدون Docker و هم برای CI با Docker کار می‌کند.
- CI نمی‌تواند بی‌صدا از کنار غیبت Docker رد شود.

### منفی / بدهی فنی

- منطق auto-skip/enforce یک ExecutionCondition سفارشی است که باید نگه‌داشته و تست شود.

</div>
