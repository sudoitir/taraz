<div dir="rtl">

# 0006. معماری صریح (Explicit Architecture)

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

ساختار کد باید مرزهای دامنه/کاربرد/زیرساخت را صریح کند تا تصمیم‌های هم‌زمانی، اتمیسیته و سازگاری قابل‌بازبینی باشند. چالش روی «درستی زیر فشار هم‌زمانی» داوری می‌شود؛ پس محل زندگی قوانین concurrency و transaction باید از روز اول مشخص باشد، نه این‌که در یک سرویس چندلایه‌ی نامشخص پخش شود.

## تصمیم

**معماری صریح** به سبک Herberto Graça: تلفیق Hexagonal (Ports & Adapters) / Onion / Clean — دامنه در مرکز، فریم‌ورک‌ها در لبه.

- مرجع: https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/

### قاعده‌ی سخت

**driving adapterها outbound portها را هرگز نمی‌بینند.** کنترلر REST یا consumer فقط از طریق inbound port (command) یا queryِ سمت خواندن به core دسترسی دارد — هرگز مستقیم به repository یا client بیرونی. این قاعده با تست معماری (ADR-0023) در build enforce می‌شود، نه فقط به‌عنوان قرارداد شفاهی.

</div>

<div dir="ltr">

### High-level shape

```
                       ┌───────────────────────────────────────┐
                       │            driving adapters            │
                       │  (REST, messaging consumers, CLI/RPC…) │
                       └───────────────────┬───────────────────┘
                                           │  commands (inbound ports)
                                           │  queries  (read side)
                       ┌───────────────────▼───────────────────┐
                       │                 core                   │
                       │  application/service (write side)      │
                       │  application/query   (read side)       │
                       │  application/ports/{inbound,outbound}  │
                       │  domain (pure aggregates, VOs, rules)  │
                       └───────────────────┬───────────────────┘
                                           │  outbound ports
                       ┌───────────────────▼───────────────────┐
                       │             driven adapters            │
                       │  (persistence, external clients, cache)│
                       └────────────────────────────────────────┘

                       container = composition root / wiring
                       architecture-tests = boundary enforcement
```

### Directory map (top-level packages under `io.github.sudoitir.taraz`)

```
core/
├── domain/                  pure domain (aggregates, VOs, specifications)
└── application/
    ├── ports/inbound/       command + query contracts
    ├── ports/outbound/      repository + external-client contracts
    ├── service/             CQRS write side (use-case handlers)
    └── query/               CQRS read side (handlers, DTOs, pagination)
adapters/
├── driving/                 inbound entry points (REST, messaging, RPC)
└── driven/                  outbound implementations (persistence, cache, clients)
container/                   composition root, config, migrations, entry point
architecture-tests/          automated boundary enforcement (ADR-0023)
documents/                   → docs/adr, diagrams, API specs
```

### Dependency rules (cheat sheet)

```
driving adapters    → inbound ports (commands) | read-side ports (queries)
driving adapters    ✗ outbound ports                                (forbidden)
application/service → outbound ports → driven adapters (implementations)
driven adapters     → outbound ports (implement them)
domain              has zero adapter/framework dependencies, either direction
```

</div>

<div dir="rtl">

### لایه‌ها به‌اختصار

- **domain** — لایه‌ی خالص دامنه؛ وابستگی صفر به framework و persistence (الگوها در ADR-0005).
- **application/ports** — فقط قرارداد (interface)؛ هیچ منطقی این‌جا نیست.
- **application/service** — سمت نوشتن CQRS؛ ارکستراسیون use-case و compensation، بدون قانون دامنه و بدون جزئیات transport (ADR-0007).
- **application/query** — سمت خواندن CQRS؛ handler بدون حالت، DTO خواندنی، outbound port مخصوص query-repository (ADR-0007).
- **adapters/driving** — ترجمه‌ی تریگر خارجی (HTTP، پیام، …) به command/query و delegation؛ بدون منطق دامنه. محل طبیعی anti-corruption layer: فرمت wire این‌جا به زبان دامنه ترجمه می‌شود، نه عمیق‌تر.
- **adapters/driven** — پیاده‌سازی outbound portها؛ ممکن است به domain و outbound port وابسته باشند، هرگز به driving adapter یا container.
- **container** — composition root: entry point، wiring، پیکربندی، migrationها (ADR-0014). تنها لایه‌ای که همه‌ی ماژول‌ها را هم‌زمان می‌شناسد.

### نگرانی‌های عرضی (cross-cutting)

- **پیکربندی:** متمرکز و externalized؛ secretها جدا از تنظیمات غیرحساس resolve می‌شوند.
- **Migration:** تغییرات schema صریح و نسخه‌دار، نه auto-generated (ADR-0014).
- **Observability:** metric/tracing/health دغدغه‌ی adapter است؛ به دامنه نشت نمی‌کند.
- **امنیت:** احراز هویت/مجوز در مرز adapter اعمال می‌شود.
- **مستندسازی به‌همراه کد:** ADR و قرارداد API کنار همان تغییری می‌آیند که به آن‌ها انگیزه داده است، نه به‌عنوان کارِ بعداً.

## گزینه‌های بررسی‌شده

- **Layered ساده (controller/service/repository)** — رد شد: مرز دامنه با زیرساخت محو می‌شود و قوانین concurrency در سرویس‌های ناشفاف پخش می‌شوند.
- **جداسازی فقط با قرارداد شفاهی** — رد شد: بدون enforce خودکار (ADR-0023) مرزها در review فرسوده می‌شوند.

## پیامدها

### مثبت

- وابستگی‌ها فقط به سمت داخل؛ دامنه بدون framework قابل‌تست است.
- محل هر تصمیم مشخص است: قانون دامنه در domain، اتمیسیته و idempotency در application/service، نگاشت wire در driving adapter.
- شکستن مرز لایه‌ها = قرمزشدن build (با ADR-0023).

### منفی / بدهی فنی

- ساختار بسته‌بندی و boilerplate بیشتر از یک سرویس ساده (port + adapter برای هر مرز).

</div>
