<div dir="rtl">

# 0047. کلید مصنوعی برای ledger_transaction، کلید طبیعی برای processed_transaction (تنگ‌کردن ADR-0016)

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۹

## زمینه

هویت دامنه‌ی `Transaction` یک رشته‌ی دلخواهِ client-supplied (`TransactionId`) است، اما ADR-0016
کلید اصلی UUIDv7 را برای همه‌ی جدول‌ها به‌خاطر locality درجِ B-tree الزامی کرده. یک PK با کلید
طبیعی (رشته) روی `ledger_transaction` یعنی یک varchar تا ۶۴ بایتی در FK هر `ledger_entry` و در
index آن FK تکرار می‌شود — بدتر از locality همان چیزی که ADR-0016 می‌خواست حل کند. از طرف دیگر،
`processed_transaction` دقیقاً یک مسیر دسترسی دارد (`WHERE transaction_id = ?`)، فرزندی ندارد و
هویت دومی هم ندارد؛ یک PK مصنوعی روی آن یعنی یک index که هیچ query‌ای آن را مصرف نمی‌کند — دقیقاً
همان چیزی که ADR-0019 «index تزئینی» می‌نامد و ممنوع می‌کند.

## تصمیم

- **`ledger_transaction`**: PK مصنوعی `id uuid` (UUIDv7، ADR-0016/0038) به‌علاوه‌ی
  `external_id varchar(64) UNIQUE NOT NULL` که همان `TransactionId` دامنه (و `Idempotency-Key` در
  REST) است. `ledger_entry.transaction_id` به همین PK مصنوعی اشاره می‌کند.
- **`processed_transaction`**: PK روی خودِ کلید طبیعی (`transaction_id varchar(64)`) — بدون کلید
  مصنوعی اضافه.
- این ADR قاعده‌ی ADR-0016 را برای جدول‌هایی که هویت مصنوعی/فرزند دارند تنگ می‌کند؛ برای جدولی با
  یک مسیر دسترسی و بدون فرزند، کلید طبیعی است.

## گزینه‌های بررسی‌شده

- **کلید طبیعی روی هر دو جدول (طبق متن لفظی ADR-0016)** — رد شد برای `ledger_transaction`: FK فربه
  و index آن هم randomly-ordered می‌شود؛ یک index randomly-ordered (خودِ UNIQUE) قطعاً ارزان‌تر از
  یک PK randomly-ordered به‌علاوه‌ی یک FK فربه‌ی randomly-ordered است.
- **کلید مصنوعی روی هر دو جدول** — رد شد برای `processed_transaction`: index بدون مصرف‌کننده،
  مستقیم نقض ADR-0019.

## پیامدها

### مثبت

- هر دو جدول دقیقاً همان شکل locality/index را دارند که الگوی دسترسی واقعی‌شان می‌طلبد.
- تعارض ADR-0016 با کلید دامنه‌ی رشته‌ای صریحاً حل شد، نه دور زده شد.

### منفی / بدهی فنی

- دو مدل هویت متفاوت در دو جدول مجاور، باید در mapper و migration مستند و رعایت شود؛ نام‌گذاری
  `external_id` (نه `transaction_id`) روی `ledger_transaction` عمداً از تداخل با معنای PK
  `processed_transaction` جلوگیری می‌کند.

</div>
