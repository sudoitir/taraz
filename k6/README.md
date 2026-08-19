# تست‌های k6 — اثبات Black-box نیازمندی‌های چالش

<div dir="rtl">

این پکیج مجموعه‌ای از سناریوهای k6 است که سرویس را از طریق HTTP واقعی (نه mock) درایو می‌کند و هر بند از چالش را با assertion قطعی اثبات می‌کند — نه فقط «exception نداشت». هر سناریو threshold با `checks: rate==1` دارد؛ یعنی حتی یک check شکست‌خورده کل اجرا را fail می‌کند.

## پیش‌نیاز

</div>

<div dir="ltr">

```bash
brew install k6
```

</div>

<div dir="rtl">

هیچ dependency دیگری (npm و …) لازم نیست. سرویس باید روی `:8080` بالا باشد:

</div>

<div dir="ltr">

```bash
just up && just run
```

</div>

<div dir="rtl">

آدرس دیگر؟ `BASE_URL` را override کنید: `BASE_URL=http://host:port just k6 smoke`

## اجرا

</div>

<div dir="ltr">

```bash
just k6    # همه‌ی سناریوها به ترتیب
```

</div>

<div dir="rtl">

## نگاشت نیازمندی چالش → سناریو

| نیازمندی چالش | سناریو | اثبات |
|---|---|---|
| اعتبارسنجی (amount ≤ 0، حساب ناشناخته، transfer به خود) | `validation.js` | status + `code` دقیقِ problem، و خواندن مجدد balance بعد از هر رد: بدون تغییر |
| idempotency ترتیبی | `idempotency.js` / `sequential` | credit/debit/transfer با یک کلید ×۳ → فقط یک بار اثر؛ پاسخ‌های بعدی `REPLAYED` + header `Idempotency-Replayed: true` |
| idempotency همزمان | `idempotency.js` / `*_storm` | ۵۰ VU همزمان با **یک** `Idempotency-Key` → metric `*_applied: count==1` و balance نهایی دقیقاً یک‌بار جابه‌جا شده |
| همزمانی تک‌حسابه (مثال خود چالش: دو debit 700 روی 1000) | `concurrency-single-account.js` / `race_pair` | balance نهایی دقیقاً **300** — نه منفی، نه هر دو |
| همزمانی تک‌حسابه (شکل مرجع: 100٬000 با 1٬000 عملیات) | `concurrency-single-account.js` / `thousand_debits` | دقیقاً 1000 debit موفق (`count==1000`) و balance نهایی دقیقاً **0**؛ بدون sleep برای بیشترین احتمال race |
| عدم بلاک‌شدن حساب‌های مستقل | `concurrency-multi-account.js` | هر VU یک جفت حساب خصوصی؛ مجموع هر جفت دقیقاً ثابت + threshold روی `p(95)` — lock سراسری latency را منفجر می‌کرد |
| atomicity ترانسفر | `transfer-atomicity.js` / `ping_pong` | 1٬000 ترانسفر رفت‌وبرگشت روی دو حساب داغ؛ teardown: هر دو balance دقیقاً برابر مقدار اولیه |
| عدم مشاهده‌ی ترانسفر نیمه‌کاره | `transfer-atomicity.js` / `conservation_monitor` | یک VU موازی پیوسته هر دو balance را می‌خواند؛ اگر مجموع حتی یک لحظه ≠ کل اولیه شود، `money_conserved` fail می‌شود |

اجرای تکی یک سناریو: `k6 run k6/scenarios/<name>.js`

## قراردادهایی که assert می‌شوند

- خطاها `application/problem+json` با member پایدارِ `code` (assert روی `code`، نه `title`/`detail`).
- فیلدهای snake_case (`account_id`, `transaction_id`)، `Cache-Control: no-store` روی خواندن balance.
- `X-Flow-ID` روی پاسخ موفق **و** خطا echo می‌شود.
- replay ها `201` با `status: REPLAYED` هستند — نه 409.

## ساختار

</div>

<div dir="ltr">

```
k6/
├── config.js          # BASE_URL (env override)
├── lib/
│   ├── client.js      # createAccount / credit / debit / transfer / getBalance + txKey
│   └── assert.js      # expectApplied / expectReplayed / expectProblem / expectFlowIdEcho
└── scenarios/         # یک اسکریپت per نگرانی
```

</div>
