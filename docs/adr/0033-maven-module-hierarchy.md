<div dir="rtl">

# 0033. سلسله‌مراتب فیزیکی ماژول‌های Maven مطابق نقشه‌ی ADR-0006

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

ADR-0006 نقشه‌ی منطقی لایه‌ها را تعریف می‌کند: `core` (domain + application) و `adapters` (driving + driven). در اسکافولد اولیه این نقشه به صورت تخت پیاده شد (`core` با ماژول‌های مستقیم، `adapters` با ماژول‌های مستقیم) و مرز `application` درون core و مرز driving/driven درون adapters فقط در نام پکیج دیده می‌شد. نتیجه: مرزهای معماری در ساختار فیزیکی مخفی بودند و وابستگی‌های Maven نمی‌توانستند آن‌ها را enforce کنند.

## تصمیم

ساختار فیزیکی ماژول‌ها دقیقاً آینه‌ی نقشه‌ی ADR-0006 است:

<div dir="ltr">

```
taraz
├── core
│   ├── domain
│   └── application (pom)
│       ├── port      ← contracts: packages inbound/ + outbound/
│       ├── service   ← CQRS write side (command handlers)
│       └── query     ← CQRS read side
├── adapters
│   ├── driving (pom)
│   │   └── rest
│   └── driven (pom)
│       ├── persistence
│       └── messaging
├── container
└── architecture-tests
```

</div>

- `inbound` و `outbound` **پکیج** درون ماژول `port` هستند، نه ماژول جدا.
- `messaging` فقط سمت driven وجود دارد (outbox publisher طبق ADR-0010/0027)؛ driving messaging تا وقتی consumer نداریم ساخته نمی‌شود.
- artifactIdها ساده و بدون prefix هستند: `domain`, `port`, `service`, `query`, `rest`, `persistence`, `messaging`, `container`.
- قواعد وابستگی که Maven enforce می‌کند (و ArchUnit در سطح پکیج بازبینی می‌کند):
  - `port` → `domain`؛ `service` → `domain` + `port`؛ `query` → `domain` + `port`
  - `rest` → `port` (فقط inbound) + `query` — هرگز outbound ports، هرگز `service`، هرگز `domain`. Queryها از application service عبور نمی‌کنند.
  - `persistence` و `messaging` → `port` + `domain`
  - `container` → همه‌ی ماژول‌های برگ (composition root)

## گزینه‌های بررسی‌شده

- **ساختار تخت (ماژول‌های مستقیم زیر core و adapters)** — رد شد: مرز application و مرز driving/driven در بیلد دیده نمی‌شد.
- **inbound/outbound به‌عنوان ماژول جدا** — رد شد: گرانولیشن اضافی؛ یک ماژول port با دو پکیج همان enforce کردن را با ArchUnit می‌دهد.
- **prefix ‏`taraz-` در نام ماژول‌ها** — رد شد: artifactId باید با نام دایرکتوری یکی باشد؛ `<name>` در pom نمایش کامل را دارد.

## پیامدها

### مثبت

- ساختار دایرکتوری همان نقشه‌ی معماری است؛ وابستگی‌های غلط در compile-time می‌شکنند؛ هر لایه به‌تنهایی قابل build و test است.

### منفی / بدهی فنی

- سه aggregator pom اضافه (`application`, `driving`, `driven`)؛ عمق بیشتر مسیرها. هزینه‌ی ناچیز در برابر enforce شدن مرزها.

</div>
