# تراز (Taraz) — سرویس مدیریت موجودی هم‌روند

<div dir="ltr">

[![CI](https://github.com/sudoitir/taraz/actions/workflows/ci.yml/badge.svg)](https://github.com/sudoitir/taraz/actions/workflows/ci.yml)
[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=sudoitir_taraz&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sudoitir_taraz)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=sudoitir_taraz&metric=bugs)](https://sonarcloud.io/summary/new_code?id=sudoitir_taraz)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=sudoitir_taraz&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=sudoitir_taraz)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=sudoitir_taraz&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=sudoitir_taraz)

</div>

<div dir="rtl">

سرویس مدیریت موجودی حساب‌ها با تضمین **صحت، سازگاری و قابلیت اطمینان زیر concurrency بالا** — چالش کدنویسی Java سطح senior (`docs/Coding_Challenge_V2_English.md`).

> **وضعیت فعلی:** سرویس کامل و end-to-end اجرا می‌شود. مدل دامنه، لایه‌ی application (credit/debit/transfer + query)، REST API، و هر سه adapter واقعی — PostgreSQL (JPA + Liquibase)، Valkey (idempotency gate)، Kafka (outbox publisher) — پیاده‌سازی شده و روی زیرساخت واقعی با Testcontainers تست شده‌اند. جزئیات و باقی‌مانده‌ها در بخش [وضعیت پیاده‌سازی](#وضعیت-پیاده‌سازی-صداقت-در-scope).

## معماری

ساختار چندماژوله‌ی Maven بر پایه‌ی DDD + Explicit Architecture + CQRS + event-driven (outbox)، طبق ADR-0005 و ADR-0006:

<div dir="ltr">

```
taraz (parent pom)
├── core                        → صفر وابستگی به framework (ADR-0005)
│   ├── domain                  → مدل دامنه؛ builder + factory خالص
│   └── application (pom)
│       ├── port                → قراردادها: پکیج‌های inbound/ (command) و outbound/
│       ├── service             → سمت نوشتن CQRS: command handlerها؛ validation + idempotency اینجا (ADR-0034)؛ Spring stereotype مجاز، تراکنش با UnitOfWork (ADR-0039/0040)
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

ساختار فیزیکی ماژول‌ها آینه‌ی همین نقشه است (ADR-0033) و وابستگی‌های Maven مرزها را در compile time اجرا می‌کنند. هر service یک `compensate` متناظر دارد (ADR-0035). تصمیم‌های معماری در `docs/adr/` ثبت و **لازم‌الاجرا** هستند.

## Concurrency

صحت موجودی با **قفل در سطح حساب** تضمین می‌شود (row-level pessimistic locking، `SELECT ... FOR UPDATE`)، نه قفل سراسری (ADR-0026).

- هر command handler دقیقاً یک تراکنش دیتابیس است (ADR-0018)، باز شده از طریق پورت `UnitOfWork` (ADR-0040).
- credit/debit یک ردیف حساب را قفل می‌کند؛ transfer هر دو ردیف را، به **یک ترتیب یکتا و ثابت** بر اساس `AccountId` (ADR-0042، جزئیات در بخش [Transfer](#transfer)).
- کفایت موجودی **بعد از** گرفتن قفل، در همان تراکنش بررسی و اعمال می‌شود؛ بازنده‌ی race هیچ‌گاه وضعیت قدیمی نمی‌بیند — فقط صف می‌کشد و با موجودی به‌روز تصمیم می‌گیرد.
- عملیات روی حساب‌های مستقل هرگز پشت یک قفل مشترک صف نمی‌کشند؛ هیچ clearing سراسری وجود ندارد (ADR-0037).
- virtual threads (`spring.threads.virtual.enabled=true`) هم‌روندی بالای I/O را ارزان می‌کنند؛ لایه‌ی application از `synchronized` دور بلاک‌شونده دوری می‌کند تا thread مجازی pin نشود (ADR-0002).

این مکانیزم دو بار اثبات شده: یک‌بار با fakeهای in-memory در سطح منطق handler (`core/application/service`)، و یک‌بار همان سناریوها روی PostgreSQL واقعی با Testcontainers (`container/src/test/.../it`):

- ۱۰۰۰ عملیات هم‌زمان (barrier-synchronized) روی یک حساب، با موجودی نهایی دقیق.
- سناریوی مرجع چالش: دو debit همزمان ۷۰۰تایی روی موجودی ۱۰۰۰ → دقیقاً یک موفقیت.
- اثبات ساختاری (با یک held side-connection lock، نه timing) که حساب‌های مستقل هرگز منتظر هم نمی‌مانند.
- ۲۰۰ transfer هم‌زمان دوطرفه بین همان دو حساب، بدون deadlock.
- اثبات مستقیم این‌که ترتیب قفل همیشه بر اساس حساب کوچک‌تر است، صرف‌نظر از جهت transfer.

connection pool دیتابیس عمداً کوچک و کوتاه‌timeout است (ADR-0054): فشار بیش از ظرفیت به‌جای صف نامرئی، خطای معنادار `CONCURRENCY_CONFLICT` (۵۰۳ با `Retry-After`) برمی‌گرداند. تست‌های burst سنگین همین رفتار را با retry روی همان `transactionId` می‌پوشانند — دقیقاً مثل یک client واقعی.

## Idempotency

هر عملیات مالی یک `transactionId` یکتا دارد. تکرار درخواست — حتی هم‌زمان — فقط **یک بار** روی موجودی اثر می‌گذارد. idempotency در **لایه‌ی command handler** تضمین می‌شود (ADR-0034)، با دو خط دفاع مکمل (ADR-0021/0041):

1. **گیت سریع و مشاوره‌ای در Valkey** (`IdempotencyGate`) پیش از باز شدن تراکنش بررسی می‌شود: اگر پاسخ «قبلاً اعمال شده» بود، نتیجه‌ی ذخیره‌شده بدون لمس دیتابیس بازگردانده می‌شود.
2. **مرجع نهایی PostgreSQL است.** داخل همان تراکنشی که قفل‌های ردیف حساب گرفته شده، `processed_transaction` بررسی می‌شود — **بعد از** قفل، نه قبل. دو duplicate هم‌زمان روی همان قفل صف می‌کشند؛ بازنده رکورد برنده را می‌بیند و replay می‌کند، بدون تکیه بر خطای unique constraint در مسیر عادی.

نکته‌ی طراحی (ADR-0041، مکمل ADR-0021): گیت Valkey **fail-open** است — نبودِ پاسخ یا در دسترس نبودن Valkey همیشه به مسیر مرجع دیتابیس می‌افتد. قطعی Valkey فقط latency را بالا می‌برد، هرگز صحت را نمی‌شکند، و هیچ حالت میانی («IN_PROGRESS یتیم») وجود ندارد که یک reader مجبور به تفسیرش باشد. `ValkeyIdempotencyGate` یک **read-through cache** خالص است، نه یک reservation gate — هرگز placeholder نمی‌نویسد. client Lettuce با `DisconnectedBehavior.REJECT_COMMANDS` و timeout ۲۰۰ میلی‌ثانیه پیکربندی شده تا از کار افتادن Valkey به یک تأخیر کوتاه و قابل پیش‌بینی تبدیل شود، نه یک stall نامحدود.

این طراحی با Testcontainers روی PostgreSQL و Valkey واقعی اثبات شده:

- سه‌بار ارسال متوالی همان `transactionId` برای credit/debit/transfer.
- ده‌ها duplicate هم‌زمان (barrier-synchronized) روی همان `transactionId`، با دقیقاً یک `APPLIED`.
- Valkey متوقف (paused) در حین عملیات — exactly-once هم‌چنان از طریق `processed_transaction` برقرار می‌ماند و پاسخ‌ها سریع می‌مانند.
- سناریوی محافظ نهایی: همان `transactionId` روی **دو حساب مستقل** هم‌زمان. چون قفل مشترکی ندارند، بررسی سطح application آن‌ها را نمی‌گیرد و constraint یکتای `pk_processed_transaction` در PostgreSQL تصمیم‌گیرنده‌ی نهایی است؛ بازنده به‌جای exception خام، خطای typed `409 TRANSACTION_ID_CONFLICT` می‌گیرد.

## Transfer

`transfer` واحد اتمیک **دو حساب + یک Transaction دوطرفه (double-entry) + outbox** است، همه در **یک transaction دیتابیس** (ADR-0037/0010). بنابراین حالت میانی (کسر بدون واریز) وجود ندارد — مبلغ کسرشده از مبدأ و مبلغ اضافه‌شده به مقصد ساختاری برابرند (دو leg با مجموع صفر).

**Deadlock ممکن نیست، نه با timeout/retry بلکه با طراحی:** هر دو حساب به یک ترتیب یکتا قفل می‌شوند — ترتیب صعودی بر اساس `AccountId`، با مقایسه‌ی unsigned بایت‌به‌بایت که دقیقاً با ترتیب native نوع `uuid` در PostgreSQL هم‌راستاست (ADR-0026/0042؛ **نه** `UUID.compareTo` جاوا، که برای برخی جفت‌ها با ترتیب PostgreSQL مخالف است). چون همه‌ی transferها — صرف‌نظر از این‌که کدام حساب source و کدام destination است — قفل‌ها را به همین یک ترتیب می‌گیرند، چرخه‌ای در گراف انتظار قفل هرگز شکل نمی‌گیرد.

## انتقال به همان حساب

`transfer(A, A, ...)` **رد می‌شود** (خطای validation، کد `SAME_ACCOUNT_TRANSFER`) — **پیش از هر قفل یا تراکنشی**: هیچ semantic معتبری برای «انتقال پول از حساب به خودش» وجود ندارد و پذیرفتن آن فقط `transactionId` را برای یک عملیات بی‌معنی هدر می‌دهد. موجودی بدون تغییر می‌ماند و `transactionId` مصرف نمی‌شود؛ یک درخواست معتبر بعدی با همان شناسه از نو ارزیابی می‌شود، نه به‌عنوان تکرار.

## انتخاب‌های فناوری

| فناوری | چرا | trade-off |
|---|---|---|
| PostgreSQL | system of record؛ transaction و constraint واقعی برای صحت مالی (ADR-0013) | یک سرویس برای بالاآوردن |
| Valkey | cache و کمک به idempotency با latency پایین (ADR-0020)؛ با پروتکل Redis و Lettuce | حالت توزیع‌شده‌ی اضافه؛ منبع حقیقت نیست |
| Kafka + outbox | انتشار event بدون dual-write، یک topic به‌ازای هر نوع aggregate برای حفظ ترتیب per-account (ADR-0010/0027/0051) | پیچیدگی عملیاتی؛ eventual consistency |
| ShedLock | فقط یک پاد در هر لحظه outbox را poll/cleanup می‌کند؛ با scale-up افقی پادها به‌نوبت کار می‌کنند، نه هم‌زمان و تکراری (ADR-0057) | یک جدول lock اضافه در دیتابیس |
| Liquibase | مالکیت schema با migration نسخه‌بندی‌شده؛ `ddl-auto=none` (ADR-0014) | نوشتن changeset دستی |
| Testcontainers | تست‌های واقعی روی PostgreSQL واقعی (ADR-0022) | نیاز به Docker در تست‌ها |
| ArchUnit | مرزهای لایه را در بیلد اجرا می‌کند (ADR-0023) | نگهداشت قوانین |
| Spotless + palantir-java-format | formatting یکدست به سبک Spring Framework، apply در compile (ADR-0029) | سبک ثابت، غیرقابل مذاکره |
| Error Prone + NullAway (JSpecify) | کشف bug و خطای null در compile time | سخت‌گیری بیشتر هنگام کامپایل |
| Lombok + MapStruct | حذف boilerplate در adapters؛ mapping با codegen بدون reflection | annotation processing؛ در `core` ممنوع (ADR-0005) |
| springdoc-openapi | قرارداد API همیشه از روی کد، زنده و قابل‌اجرا در مرورگر (Swagger UI) — بدون فایل دست‌نگهداردی (ADR-0059) | یک وابستگی و حاشیه‌نویسی در driving adapter |
| Spring Boot Actuator | سیگنال استاندارد سلامت/readiness روی db و Valkey و Kafka؛ فقط `health` expose شده (ADR-0060) | سطح HTTP کمی بزرگ‌تر؛ metrics/tracing خارج از scope |

## بیلد، اجرا و تست

پیش‌نیاز: **Java 21** و **Docker**. Maven با wrapper پین شده است.

<div dir="ltr">

```bash
./mvnw test          # دستور استاندارد چالش: بیلد + همه‌ی تست‌ها (از جمله ArchUnit)
./mvnw -B verify     # بیلد کامل با verify

just test            # معادل ./mvnw test (نیازمند just)
just up              # بالاآوردن PostgreSQL + Valkey + Kafka با docker compose
just run             # اجرای اپلیکیشن (boot با spring-boot-docker-compose)
just k6               # سناریوهای load test با k6 روی HTTP واقعی (نیازمند just up && just run؛ جزئیات: k6/README.md)
just down            # توقف زیرساخت
just format          # اعمال formatting
just docs            # بازکردن Swagger UI در مرورگر (macOS/Linux/Windows)
```

</div>

با اپ در حال اجرا: مستندات تعاملی API در `/swagger-ui` (سند OpenAPI 3 در `/v3/api-docs`) و وضعیت سلامت اجزا (db، Valkey، Kafka) در `/actuator/health` در دسترس است.

تنظیمات اتصال (دیتابیس، Valkey، پورت) از متغیرهای محیطی خوانده می‌شود؛ `.env.example` را به `.env` کپی کنید (`.env` در gitignore است و هرگز commit نمی‌شود). در حالت dev، `spring-boot-docker-compose` سرویس‌های `compose.yaml` را خودکار بالا می‌آورد و connection details را wire می‌کند.

## وضعیت پیاده‌سازی (صداقت در scope)

**پیاده‌سازی‌شده:**

- اسکلت چندماژوله‌ی سلسله‌مراتبی Maven (core → application → port/service/query؛ adapters → driving/driven → rest/persistence/messaging؛ container؛ architecture-tests) با Spring Boot 4.1.0 و Java 21 (ADR-0033)
- مدل دامنه‌ی کامل: `Account`/`Transaction`/`LedgerEntry`، `Money`، `PostingService`، Specificationهای قوانین کسب‌وکار، domain eventها (ADR-0005/0009/0011/0036/0037)
- لایه‌ی application کامل: commandها و command handlerهای credit/debit/transfer (یک package به‌ازای هر use case، ADR-0007)، سمت خواندن CQRS (`GetBalanceHandler`)، فاساد `BalanceService` مطابق قرارداد دقیق چالش، و تمام inbound/outbound portها
- REST API در `adapters/driving/rest`: نگاشت DTO ↔ command با MapStruct، `Idempotency-Key`، خطاهای RFC 7807، فیلتر `X-Correlation-ID`
- **adapter واقعی PostgreSQL** (JPA + Liquibase): قفل pessimistic مرتب (ADR-0026/0042/0045)، `TransactionTemplateUnitOfWork` با READ COMMITTED (ADR-0046)، ترجمه‌ی خطای persistence با تطبیق نام constraint — هرگز متن پیام (ADR-0048) — به دو خطای typed تازه: `TRANSACTION_ID_CONFLICT` (۴۰۹) و `CONCURRENCY_CONFLICT` (۵۰۳ با `Retry-After`)
- **adapter واقعی Valkey**: `ValkeyIdempotencyGate` (read-through، fail-open) + پیکربندی resilience Lettuce
- **adapter واقعی Kafka**: outbox JDBC که در همان تراکنش JPA فراخواننده enlist می‌شود (ADR-0049)، بایت‌های نهایی wire را ذخیره می‌کند (ADR-0050)، publisher با `FOR UPDATE SKIP LOCKED` + backoff نمایی، و ShedLock برای هماهنگی بین چند پاد (ADR-0057)
- انتشار correlation id از MDC تا ردیف outbox تا header کافکای `kafka_correlationId` (استاندارد Spring Kafka — ADR-0052/0056)
- Liquibase: پنج جدول (`account`/`ledger_transaction`/`ledger_entry`/`processed_transaction`/`outbox`) + جدول ShedLock، با ستون‌های `numeric` برای مبلغ (ADR-0044) و session GUCها برای timeout قفل
- Hikari با pool ثابت و `connection-timeout` کوتاه به‌عنوان مکانیزم backpressure (ADR-0054)؛ `spring.jpa.open-in-view=false`
- تست‌های integration با Testcontainers روی PostgreSQL/Valkey/Kafka واقعی — همان سناریوهای concurrency/idempotency/transfer/correlation که در بالا توضیح داده شد، به‌علاوه‌ی boot کامل context اپلیکیشن
- قوانین ArchUnit برای مرزهای لایه، شامل استقلال `persistence`/`messaging` از هم و از `application.service` (در `./mvnw test` اجرا و نقض = شکست بیلد)
- JaCoCo، formatting اجباری (Spotless + palantir)، تحلیل استاتیک (Error Prone + NullAway)، CI-friendly versioning با flatten plugin
- زیرساخت docker-compose (PostgreSQL 18، Valkey 9، Kafka 4.3) با healthcheck و `.env`
- CI با GitHub Actions (`-Pci verify`، اجرای اجباری تست‌های Docker-محور)
- مستندسازی زنده‌ی API با springdoc-openapi (Swagger UI در `/swagger-ui`، سند OpenAPI 3 در `/v3/api-docs` — ADR-0059) و Actuator فقط با endpoint سلامت (`/actuator/health` + probeهای liveness/readiness — ADR-0060)
- تست‌های load با k6 (`just k6`، جزئیات در `k6/README.md`) روی HTTP واقعی: validation، idempotency ترتیبی و همزمان، همزمانی تک‌حسابه (شامل شکل مرجع چالش)، عدم بلاک‌شدن حساب‌های مستقل، atomicity ترانسفر — با assertion دقیق روی balance نهایی، نه فقط «exception نداشت»

**باقی‌مانده (گام‌های بعدی، هر کدام با change proposal جدا در OpenSpec):**

1. compensate handlerها (ADR-0035) — قرارداد inbound port تعریف‌شده، پیاده‌سازی و expose نشده
2. Micrometer Tracing / `traceparent` توزیع‌شده — عمداً خارج از scope؛ correlation id فعلی کافی برای ردیابی log-به-log است

## گزارش تست کارایی (k6)

همه‌ی اعداد زیر **اندازه‌گیری واقعی** از یک اجرای زنده هستند (۲۰۲۶-۰۸-۱۹)، نه تخمین. محیط اجرا:

| مؤلفه | مقدار |
|---|---|
| ماشین | Apple M1 Pro، ۱۶GB RAM |
| JDK | OpenJDK 25.0.4 |
| k6 | v2.2.0 (darwin/arm64) |
| Docker | 29.7.2 |
| ایمیجها | postgres:18، valkey/valkey:9، apache/kafka:4.3.1 — همه single-node روی همان ماشین |

### سناریوهای صحت (`just k6`)

شش سناریو، هر کدام با threshold قطعی `checks: rate==1`؛ یعنی حتی یک check شکست‌خورده کل اجرا را fail می‌کند. نتیجه‌ی اجرا:

| سناریو | checkها | نرخ | p95 |
|---|---|---|---|
| smoke | ۲۶/۲۶ | ۱۶٫۵ req/s | ۲۰۹٫۴ms |
| validation | ۷۵/۷۵ | ۱۸۵٫۳ req/s | ۱۵٫۲ms |
| idempotency | ۳۴۳/۳۴۳ | ۷٫۶ req/s | ۱۵۹٫۳ms |
| concurrency-single-account | ۱۰۰۴/۱۰۰۴ | ۱۱۸٫۹ req/s | ۴۸۱٫۸ms |
| concurrency-multi-account | ۱۲۷۵/۱۲۷۵ | ۷۹۹٫۲ req/s | ۳۵٫۷ms |
| transfer-atomicity | ۱۰۰۳/۱۰۰۳ | ۱۸۴۰٫۵ req/s | ۶۹۹µs |

دو نکته‌ی تفسیری: در سناریوی تک‌حسابه، ۱۰۰۰ عملیات هم‌زمان عمداً روی **یک ردیف داغ** صف می‌کشند — p95 بالا همان هزینه‌ی سریالیزیشن درستِ قفل ردیف است، نه کندی. در transfer-atomicity بیشتر حجم، خواندن‌های monitor است (۲۲هزار+ نمونه‌ی بررسی conservation در حین توفان ۱۰۰۰ ترانسفر دوطرفه) و p95 در حد میکروثانیه است چون ترانسفرها روی دو حساب مرتب‌قفل‌شده سریع‌اند.

### بنچمارک بار مداوم (`just benchmark`)

سناریوی `k6/scenarios/benchmark.js`: نرخ ورودی ثابت **۵۰۰ درخواست بر ثانیه به مدت ۶۰ ثانیه** روی ۲۰۰ حساب، با ترکیب ۴۰٪ credit، ۳۰٪ debit، ۲۰٪ transfer و ۱۰٪ خواندن موجودی؛ کلید idempotency یکتا برای هر عملیات و check روی تک‌تک پاسخ‌ها.

| متریک | مقدار اندازه‌گیری‌شده |
|---|---|
| عملیات | ۳۰٬۰۰۱ (checkها: ۱۰۰٪ — هیچ شکستی) |
| خطای HTTP | ۰٫۰۰٪ (۰ از ۳۰٬۴۰۱ درخواست) |
| median | ۴٫۷۳ms |
| avg | ۴٫۸۴ms |
| p90 / p95 / p99 | ۶٫۷ / ۸٫۱ / ۱۲٫۱۷ms |
| max | ۲۷٫۲۷ms |

### شواهد سطح دیتابیس (pg_stat_statements + auto_explain)

پایگاه‌داده‌ی dev با `pg_stat_statements` و `auto_explain` (آستانه‌ی ۵۰ms) بالا می‌آید؛ `just db-stats` پرکارکردترین statementها را نشان می‌دهد. وضعیت پس از اجرای مجموعه‌ی بالا:

- پرهزینه‌ترین statement همان `SELECT ... FOR UPDATE` ردیف حساب است: میانگین ۵٫۷ms و بیشینه‌ی ۳۰۴۱ms — بیشینه مربوط به صف قفل در توفان تک‌حسابه است (رفتار طراحی‌شده، نه plan بد).
- همه‌ی insert/updateها (ledger، outbox، processed_transaction، به‌روزرسانی موجودی) میانگین ۰٫۰۲ تا ۰٫۰۷ میلی‌ثانیه دارند؛ خواندن موجودی ۰٫۰۰۶ms.
- auto_explain در کل اجرا ۱۵۰۵ plan ثبت کرد: ۱۴۲۹ مورد همان select قفل‌دار در طوفان‌های ردیف داغ و ۷۶ مورد poll مربوط به `FOR UPDATE SKIP LOCKED` اوت‌باکس — هیچ plan کند غیرمنتظره (مثل seq scan) دیده نشد.

### صداقت درباره‌ی این اعداد

این‌ها خروجی یک ماشین توسعه با زیرساخت تک‌نود است و روی سخت‌افزار دیگر متفاوت خواهد بود؛ هدف مجموعه اثبات صحت زیر بار است و بنچمارک یک baseline قابل‌تکرار (`just benchmark` با `RATE`/`DURATION` قابل‌تنظیم) می‌دهد، نه ادعای تنظیم‌شده‌ی production. در همین جلسه همین مجموعه یک ناهمتوازنیِ واقعی (۵۲۰/۴۸۰ به‌جای ۵۰۰/۵۰۰ در تقسیم جهت ترانسفرها) را گرفت؛ تحلیل forensics روی ledger نشان داد سرور دقیقاً همان را ثبت کرده که درخواست شده — با ۱۰۰۰ کلید یکتا، ۱۰۰۰ تراکنش، و تطابق دقیق موجودی با جمع ورودی‌های ledger — و علت، یک bug در طراحی خود سناریو بود که رفع شد.

</div>
