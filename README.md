# تراز (Taraz) — سرویس مدیریت موجودی هم‌روند

<div dir="rtl">

سرویس مدیریت موجودی حساب‌ها با تضمین **صحت، سازگاری و قابلیت اطمینان زیر concurrency بالا** — چالش کدنویسی Java سطح senior (`docs/Coding_Challenge_V2_English.md`).

> **وضعیت فعلی: هسته‌ی دامنه + لایه‌ی application کامل و تست‌شده.** مدل دامنه (Account/Transaction، Money، PostingService)، commandها و command handlerها (credit/debit/transfer)، سمت خواندن (CQRS query)، تمام outbound portها، و idempotency/atomicity/concurrency در سطح منطق (با fakeهای in-memory) آماده و تست‌شده‌اند. adapterهای واقعی (REST، PostgreSQL، Valkey، Kafka) هنوز جایگزین fakeها نشده‌اند — بخش «وضعیت پیاده‌سازی» در انتهای همین فایل جزئیات را می‌گوید.

## معماری

ساختار چندماژوله‌ی Maven بر پایه‌ی DDD + Explicit Architecture + CQRS + event-driven (outbox) — طبق ADR-0005 و ADR-0006:

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

ساختار فیزیکی ماژول‌ها آینه‌ی نقشه‌ی معماری است (ADR-0033) و وابستگی‌های Maven مرزها را در compile time enforce می‌کنند. هر سرویس یک `compensate` متناظر دارد (ADR-0035).

تصمیم‌های معماری در `docs/adr/` ثبت و **لازم‌الاجرا** هستند.

## Concurrency

صحت موجودی با **قفل در سطح حساب** (row-level pessimistic locking، `SELECT ... FOR UPDATE`) تضمین می‌شود، نه قفل سراسری (ADR-0026). هر command handler دقیقاً یک تراکنش دیتابیس است (ADR-0018)، باز شده از طریق پورت `UnitOfWork` (ADR-0040): برای credit/debit یک ردیف حساب قفل می‌شود، برای transfer هر دو ردیف — به **یک ترتیب یکتا و ثابت** بر اساس `AccountId` (ADR-0042، پایین را ببینید). بررسی کفایت موجودی و به‌روزرسانی **بعد از** گرفتن قفل، در همان تراکنش انجام می‌شود؛ بازنده‌ی race هیچ‌گاه وضعیت قدیمی نمی‌بیند، فقط صف می‌کشد و با موجودی به‌روز تصمیم می‌گیرد. عملیات روی حساب‌های مستقل هرگز پشت یک قفل مشترک صف نمی‌کشند — هیچ حساب clearing سراسری وجود ندارد (ADR-0037). virtual threads (`spring.threads.virtual.enabled=true`) هم‌روندی بالای I/O را ارزان می‌کنند؛ کد لایه‌ی application از `synchronized` دور بلاک‌شونده استفاده نمی‌کند تا thread مجازی pin نشود (ADR-0002).

این مکانیزم با تست‌های concurrency در `core/application/service` اثبات شده: ۱۰۰۰ عملیات هم‌زمان (barrier-synchronized) روی یک حساب با موجودی دقیق نهایی؛ سناریوی «دو debit همزمان ۷۰۰تایی روی موجودی ۱۰۰۰» با دقیقاً یک موفقیت؛ اثبات ساختاری که حساب‌های مستقل هرگز منتظر هم نمی‌مانند؛ و ۲۰۰ transfer هم‌زمان دوطرفه بین همان دو حساب بدون deadlock. **صداقت در scope:** این تست‌ها منطق handler را روی قفل شبیه‌سازی‌شده (in-memory `ReentrantLock`) اثبات می‌کنند؛ اثبات واقعی روی `SELECT ... FOR UPDATE` در PostgreSQL نیازمند Testcontainers است و با تغییر persistence می‌آید.

## Idempotency

هر عملیات مالی یک `transactionId` یکتا دارد. تکرار درخواست — حتی هم‌زمان — فقط **یک بار** روی موجودی اثر می‌گذارد. idempotency در **لایه‌ی command handler** تضمین می‌شود (ADR-0034)، با دو خط دفاع مکمل (ADR-0021/0041):

1. **گیت سریع و مشاوره‌ای در Valkey** (`IdempotencyGate`) پیش از باز شدن تراکنش بررسی می‌شود: اگر پاسخ «قبلاً اعمال شده» بود، نتیجه‌ی ذخیره‌شده بازگردانده می‌شود بدون لمس دیتابیس.
2. **مرجع نهایی و همیشه معتبر PostgreSQL است.** داخل همان تراکنشی که قفل‌های ردیف حساب گرفته شده، `processed_transaction` بررسی می‌شود — **بعد از** قفل، نه قبل. دو duplicate هم‌زمان روی همان قفل صف می‌کشند؛ بازنده رکورد برنده را می‌بیند و replay می‌کند، بدون تکیه بر خطای unique constraint در مسیر عادی.

نکته‌ی طراحی مهم (ADR-0041، مکمل ADR-0021): گیت Valkey **fail-open** است — نبودِ پاسخ (یا در دسترس نبودن Valkey) همیشه به مسیر مرجع دیتابیس می‌افتد. بنابراین قطعی Valkey فقط latency را بالا می‌برد، هرگز صحت را نمی‌شکند؛ و هیچ حالت میانی («IN_PROGRESS یتیم») وجود ندارد که یک reader مجبور به تفسیرش باشد. adapterها هیچ نقشی در idempotency ندارند.

## Transfer

`transfer` واحد اتمیک **دو حساب + یک Transaction دوطرفه (double-entry) + outbox** است، همه در **یک transaction دیتابیس** (ADR-0037/0010)؛ بنابراین حالت میانی (کسر بدون واریز) وجود ندارد — مبلغ کسرشده از مبدأ و مبلغ اضافه‌شده به مقصد ساختاری برابرند (دو leg با مجموع صفر). **Deadlock ممکن نیست، نه با timeout/retry بلکه با طراحی**: هر دو حساب به یک ترتیب یکتا قفل می‌شوند — ترتیب صعودی بر اساس `AccountId`، با مقایسه‌ی unsigned بایت‌به‌بایت که دقیقاً با ترتیب native نوع `uuid` در PostgreSQL هم‌راستاست (ADR-0026/0042؛ **نه** `UUID.compareTo` جاوا، که برای برخی جفت‌ها با ترتیب PostgreSQL مخالف است). چون همه‌ی transferها — صرف‌نظر از این‌که کدام حساب source و کدام destination است — قفل‌ها را به همین یک ترتیب می‌گیرند، چرخه‌ای در گراف انتظار قفل هرگز شکل نمی‌گیرد.

## انتقال به همان حساب

`transfer(A, A, ...)` **رد می‌شود** (خطای validation، کد `SAME_ACCOUNT_TRANSFER`) — **پیش از هر قفل یا تراکنشی**: هیچ semantic معتبری برای «انتقال پول از حساب به خودش» وجود ندارد و پذیرفتن آن فقط مصرف `transactionId` را برای یک عملیات بی‌معنی هدر می‌دهد. موجودی بدون تغییر می‌ماند و `transactionId` مصرف نمی‌شود — یک درخواست معتبر بعدی با همان شناسه از نو ارزیابی می‌شود، نه به‌عنوان تکرار.

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
- مدل دامنه‌ی کامل: `Account`/`Transaction`/`LedgerEntry`، `Money`، `PostingService`، Specificationهای قوانین کسب‌وکار، domain eventها (ADR-0005/0009/0011/0036/0037)
- لایه‌ی application کامل: commandها و command handlerهای credit/debit/transfer (یک package به‌ازای هر use case، ADR-0007)، سمت خواندن CQRS (`GetBalanceHandler`)، فاساد `BalanceService` مطابق قرارداد دقیق چالش، و تمام inbound/outbound portها
- atomicity، idempotency (fail-open، Postgres-authoritative)، ordered locking بدون deadlock، و validation — همه در سطح handler پیاده و با تست واحد اثبات‌شده (fake repositoryها/gate، نه دیتابیس واقعی)
- قوانین ArchUnit برای مرزهای لایه، شامل قوانین جدید Spring-in-application و ports purity (در `./mvnw test` اجرا و نقض = شکست بیلد)
- formatting اجباری (Spotless + palantir)، تحلیل استاتیک (Error Prone + NullAway)، CI-friendly versioning با flatten plugin
- زیرساخت docker-compose (PostgreSQL 18، Valkey 9، Kafka 4.3) با healthcheck و `.env`
- Liquibase master changelog خالی؛ `application.yaml` با virtual threads و `ddl-auto=none`
- CI با GitHub Actions

**باقی‌مانده (گام‌های بعدی، هر کدام با change proposal جدا در OpenSpec):**

1. پیاده‌سازی واقعی outbound portها: `AccountRepository`/`TransactionRepository`/`ProcessedTransactionStore` روی JPA + Liquibase changesetها با `SELECT ... FOR UPDATE`
2. `IdempotencyGate` روی Valkey (Lettuce)
3. `OutboxAppender` + polling publisher به Kafka
4. REST API در `adapters/driving/rest` با نگاشت DTO ↔ command (MapStruct)
5. compensate handlerها (ADR-0035) — قرارداد inbound port تعریف‌شده، پیاده‌سازی و expose نشده
6. تست‌های integration با Testcontainers روی PostgreSQL/Valkey واقعی — اثبات واقعی همان سناریوهای concurrency/idempotency که اکنون روی fake اثبات شده‌اند
7. تست‌های load با k6 (سناریوی ۱۰۰۰ عملیات هم‌زمان روی HTTP واقعی، ADR-0022)

</div>
