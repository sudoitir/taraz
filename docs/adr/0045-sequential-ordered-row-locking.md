<div dir="rtl">

# 0045. قفل‌گیری ترتیبی با چند `SELECT ... FOR UPDATE` متوالی، نه یک query چندسطری

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

ADR-0026 ادعا می‌کند deadlock در transfer «ناممکن by design» است، چون همه‌ی قفل‌های حساب به یک
ترتیب گرفته می‌شوند (ADR-0042). این ادعا باید ویژگی کدِ ما باشد، نه ویژگی یک query plan. دو راه
پیاده‌سازی برای `AccountRepository.lockAllInIdOrder` وجود دارد: (۱) N بار متوالی
`SELECT ... FROM account WHERE id = ? FOR UPDATE` به ترتیب `AccountId` صعودی، یا (۲) یک
`SELECT ... WHERE id IN (:ids) ORDER BY id FOR UPDATE`. مستندات خودِ PostgreSQL ترتیب گرفتن قفل
در یک `FOR UPDATE` چندسطری را قرارداد رسمی اعلام نمی‌کنند — یعنی گزینه‌ی دوم ترتیب قفل‌گیری را به
planner واگذار می‌کند، جایی که یک تغییر plan در آینده (index دیگر، مسیر bitmap، بازنویسی
parallel-safe) می‌تواند بی‌صدا ترتیب را عوض کند.

## تصمیم

`AccountRepositoryJpaAdapter.lockAllInIdOrder` شناسه‌ها را با کامپریتور کانونیک `AccountId`
(ADR-0042) در یک `TreeSet` مرتب و یکتا می‌کند، سپس **N بار متوالی**
`em.find(AccountEntity.class, id, LockModeType.PESSIMISTIC_WRITE)` به همان ترتیب صدا می‌زند. هزینه:
یک round trip اضافه روی transfer (حداکثر ۲ حساب)، صفر روی credit/debit — هزینه‌ای ناچیز در برابر
نگه‌داشتن اثبات deadlock-free روی پایه‌ی کد خودمان.

## گزینه‌های بررسی‌شده

- **یک `WHERE id IN (:ids) ORDER BY id FOR UPDATE`** — رد شد: ترتیب قفل‌گیری واقعی را به یک قرارداد
  مستندنشده در planner گره می‌زند؛ اثبات «ناممکن by design» دیگر روی کد ما استوار نیست.

## پیامدها

### مثبت

- استدلال deadlock-free ADR-0026 روی رفتاری استوار است که خودِ کد تضمین می‌کند، نه plan یک query.

### منفی / بدهی فنی

- یک round trip اضافه روی هر transfer؛ در مقیاس این چالش قابل‌چشم‌پوشی.

</div>
