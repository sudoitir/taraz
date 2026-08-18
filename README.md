# تراز (Taraz) — سرویس مدیریت موجودی هم‌روند

<div dir="rtl">

سرویس مدیریت موجودی حساب‌ها با تضمین **صحت، سازگاری و قابلیت اطمینان زیر concurrency بالا** — چالش کدنویسی Java سطح senior (`docs/Coding_Challenge_V2_English.md`).

> **وضعیت فعلی: اسکلت پروژه.** ساختار ماژول‌ها، بیلد، قوانین معماری (ArchUnit)، formatting، Error Prone/NullAway و زیرساخت docker-compose آماده است. منطق دامنه (credit/debit/transfer)، idempotency و API هنوز پیاده‌سازی نشده‌اند — بخش «وضعیت پیاده‌سازی» در انتهای همین فایل جزئیات را می‌گوید.

## معماری

ساختار چندماژوله‌ی Maven بر پایه‌ی DDD + Explicit Architecture + CQRS + event-driven (outbox) — طبق ADR-0005 و ADR-0006:

<div dir="ltr">

```
taraz (parent pom)
├── core                        → صفر وابستگی به framework (ADR-0005)
│   ├── domain                  → مدل دامنه؛ builder + factory خالص
│   └── application (pom)
│       ├── port                → قراردادها: پکیج‌های inbound/ (command) و outbound/
│       ├── service             → سمت نوشتن CQRS: command handlerها؛ validation + idempotency اینجا (ADR-0034)
│       └── query               → سمت خواندن CQRS؛ adapterها مستقیم صدایش می‌زنند، نه از طریق service
├── adapters
│   ├── driving (pom)
│   │   └── rest                → DTO → command با MapStruct؛ فقط inbound ports + query (هرگز outbound، هرگز service)
│   └── driven (pom)
│       ├── persistence         → PostgreSQL (قفل pessimistic مرتب — ADR-0026) + Valkey (ADR-0020/0021)
│       └── messaging           → outbox publisher به Kafka (ADR-0010/0027)
├── container                   → composition root: Spring Boot app، پیکربندی، Liquibase، compose
└── architecture-tests          → ArchUnit؛ نقض مرزهای لایه = شکست بیلد (ADR-0023)
```

</div>

ساختار فیزیکی ماژول‌ها آینه‌ی نقشه‌ی معماری است (ADR-0033) و وابستگی‌های Maven مرزها را در compile time enforce می‌کنند. هر سرویس یک `compensate` متناظر دارد (ADR-0035).

تصمیم‌های معماری در `docs/adr/` ثبت و **لازم‌الاجرا** هستند.

## Concurrency

*(در اسکلت فعلی هنوز پیاده‌سازی نشده — تصمیم طراحی طبق ADRها:)*

اصل طراحی: صحت موجودی با **قفل در سطح حساب** تضمین می‌شود، نه قفل سراسری — عملیات روی حساب‌های مستقل همدیگر را block نمی‌کنند. virtual threads (`spring.threads.virtual.enabled=true`) هم‌روندی بالای I/O را ارزان می‌کنند. دو `debit` هم‌زمان روی یک حساب دقیقاً یک بار موفق می‌شوند؛ ترکیب قفل per-account با constraint دیتابیس (موجودی منفی ممنوع) آخرین خط دفاع است. مکانیزم دقیق (striped lock / قفل دیتابیسی) در ADR مربوط به پیاده‌سازی دامنه ثبت و این بخش به‌روز می‌شود.

## Idempotency

*(هنوز پیاده‌سازی نشده — تصمیم طراحی:)*

هر عملیات مالی یک `transactionId` یکتا دارد. تکرار درخواست (حتی هم‌زمان) فقط **یک بار** روی موجودی اثر می‌گذارد. idempotency در **لایه‌ی command handler** تضمین می‌شود (ADR-0034): handler پیش از اجرای منطق، `transactionId` را از طریق outbound port در Valkey بررسی/ثبت می‌کند (ADR-0021) و اجرای دوم به no-op امن ختم می‌شود. adapterها هیچ نقشی در idempotency ندارند؛ منبع حقیقت نهایی PostgreSQL است.

## Transfer

*(هنوز پیاده‌سازی نشده — تصمیم طراحی:)*

`transfer` در **یک transaction دیتابیس** روی هر دو حساب اثر می‌گذارد؛ بنابراین حالت میانی (کسر بدون واریز) وجود ندارد. Deadlock با **ترتیب یکسان قفل‌گذاری بر اساس accountId** (مثلاً همیشه ابتدا حساب با شناسه‌ی کوچک‌تر) از طراحی حذف می‌شود — نه با timeout و retry.

## انتقال به همان حساب

`transfer(A, A, ...)` **رد می‌شود** (خطای validation): هیچ semantic معتبری برای «انتقال پول از حساب به خودش» وجود ندارد و پذیرفتن آن فقط مصرف `transactionId` را برای یک عملیات بی‌معنی هدر می‌دهد. موجودی بدون تغییر می‌ماند. تصمیم نهایی در پیاده‌سازی دامنه ثبت و این بخش به‌روز می‌شود.

## انتخاب‌های فناوری

| فناوری | چرا | trade-off |
|---|---|---|
| PostgreSQL | system of record؛ transaction و constraint واقعی برای صحت مالی (ADR-0013) | یک سرویس برای بالاآوردن |
| Valkey | cache و کمک به idempotency با latency پایین (ADR-0020)؛ با پروتکل Redis و Lettuce | حالت توزیع‌شده‌ی اضافه؛ منبع حقیقت نیست |
| Kafka + outbox | انتشار event بدون dual-write (ADR-0010، ADR-0027) | پیچیدگی عملیاتی؛ eventual consistency |
| Liquibase | مالکیت schema با migration نسخه‌بندی‌شده؛ `ddl-auto=none` (ADR-0014) | نوشتن changeset دستی |
| Testcontainers | تست‌های واقعی روی PostgreSQL واقعی (ADR-0022) | نیاز به Docker در تست‌ها |
| ArchUnit | مرزهای لایه را در بیلد enforce می‌کند (ADR-0023) | نگهداشت قوانین |
| Spotless + palantir-java-format | formatting یکدست به سبک Spring Framework، apply در compile (ADR-0029) | سبک ثابت، غیرقابل مذاکره |
| Error Prone + NullAway (JSpecify) | کشف bug و خطای null در compile time | سخت‌گیری بیشتر هنگام کامپایل |
| Lombok + MapStruct | حذف boilerplate در adapters؛ mapping با codegen بدون reflection | annotation processing؛ در `core` ممنوع (ADR-0005) |

## بیلد، اجرا و تست

پیش‌نیاز: **Java 21** و **Docker**. Maven با wrapper پین شده است.

<div dir="ltr">

```bash
./mvnw test          # دستور استاندارد چالش: بیلد + همه‌ی تست‌ها (از جمله ArchUnit)
./mvnw -B verify     # بیلد کامل با verify

just test            # معادل ./mvnw test (نیازمند just)
just up              # بالاآوردن PostgreSQL + Valkey + Kafka با docker compose
just run             # اجرای اپلیکیشن (boot با spring-boot-docker-compose)
just down            # توقف زیرساخت
just format          # اعمال formatting
```

</div>

تنظیمات اتصال (دیتابیس، Valkey، پورت) از متغیرهای محیطی خوانده می‌شود؛ `.env.example` را به `.env` کپی کنید (`.env` در gitignore است و هرگز commit نمی‌شود). در حالت dev، `spring-boot-docker-compose` سرویس‌های `compose.yaml` را خودکار بالا می‌آورد و connection details را wire می‌کند.

## وضعیت پیاده‌سازی (صداقت در scope)

**پیاده‌سازی‌شده:**

- اسکلت چندماژوله‌ی سلسله‌مراتبی Maven (core → application → port/service/query؛ adapters → driving/driven → rest/persistence/messaging؛ container؛ architecture-tests) با Spring Boot 4.1.0 و Java 21 (ADR-0033)
- قوانین ArchUnit برای مرزهای لایه (در `./mvnw test` اجرا و نقض = شکست بیلد)
- formatting اجباری (Spotless + palantir)، تحلیل استاتیک (Error Prone + NullAway)، CI-friendly versioning با flatten plugin
- زیرساخت docker-compose (PostgreSQL 18، Valkey 9، Kafka 4.3) با healthcheck و `.env`
- Liquibase master changelog خالی؛ `application.yaml` با virtual threads و `ddl-auto=none`
- CI با GitHub Actions

**باقی‌مانده (گام‌های بعدی، هر کدام با change proposal جدا در OpenSpec):**

1. مدل دامنه‌ی `Account` + پورت‌های application در `core`
2. پیاده‌سازی `BalanceService` (credit / debit / transfer) با قفل per-account و idempotency
3. persistence با JPA + Liquibase changesetها؛ outbox برای eventها
4. REST API در driving adapters با validation
5. تست‌های concurrency و idempotency (سناریوهای اجباری چالش: ۱۰۰۰ عملیات هم‌زمان روی موجودی ۱۰۰٬۰۰۰ و…)
6. تست‌های integration با Testcontainers

</div>
