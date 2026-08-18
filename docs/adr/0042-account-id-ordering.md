<div dir="rtl">

# 0042. ترتیب یکتای AccountId هم‌راستا با PostgreSQL

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

عدم‌امکان deadlock در ADR-0026 به این فرض متکی است که **همه‌ی فراخوانی‌ها قفل ردیف‌های حساب را به یک ترتیب یکسان می‌گیرند**. `AccountId` روی `UUID` ساخته شده؛ `UUID.compareTo` جاوا دو نیمه‌ی ۶۴بیتی UUID را به‌صورت **signed long** مقایسه می‌کند. اما نوع `uuid` در PostgreSQL بایت‌های ۱۶گانه را به‌صورت **unsigned lexicographic** مقایسه می‌کند. برای هر جفت UUID که بیت بالای نیمه‌ی اول‌شان متفاوت باشد، این دو ترتیب **مخالف هم** هستند. اگر application layer با `UUID.compareTo` مرتب کند و یک query یا adapter دیگر روزی با `ORDER BY id` در SQL مرتب کند، دو transfer بین همان دو حساب می‌توانند قفل‌ها را به ترتیب معکوس بگیرند — استدلال «بدون deadlock» بی‌صدا می‌شکند.

## تصمیم

`AccountId` یک ترتیب یکتا و صریح تعریف می‌کند که با ترتیب native نوع `uuid` در PostgreSQL هم‌راستاست: مقایسه‌ی unsigned نیمه‌ی most-significant، و در صورت تساوی، نیمه‌ی least-significant:

```java
public record AccountId(UUID value) implements Comparable<AccountId> {
    @Override
    public int compareTo(AccountId other) {
        int msb = Long.compareUnsigned(value.getMostSignificantBits(), other.value.getMostSignificantBits());
        return msb != 0 ? msb
                : Long.compareUnsigned(value.getLeastSignificantBits(), other.value.getLeastSignificantBits());
    }
}
```

- این ترتیب — نه `UUID.compareTo` — تنها مبنای «ترتیب صعودی `accountId`» است که ADR-0026 برای قفل‌گیری transfer می‌خواهد.
- `AccountRepository.lockAllInIdOrder` (outbound port) این ترتیب را خودش روی مجموعه‌ی ورودی اعمال می‌کند — caller هرگز لازم نیست از قبل مرتب کند، پس امکان اشتباه در محل فراخوانی از بین می‌رود.
- هر query یا migration آینده که روی `account.id` مرتب می‌کند باید به همین معنا (native `ORDER BY id` روی نوع `uuid`) متکی باشد؛ چون خودِ PostgreSQL همین ترتیب unsigned را طبیعتاً می‌دهد، هیچ تبدیل خاصی در SQL لازم نیست — فقط جاوا بود که باید اصلاح می‌شد.

## گزینه‌های بررسی‌شده

- **استفاده از `UUID.compareTo` پیش‌فرض جاوا** — رد شد: برای جفت‌هایی که بیت بالای نیمه‌ی اول متفاوت است، مستقیماً با ترتیب PostgreSQL مخالف است — همان چیزی که این ADR می‌خواهد از آن جلوگیری کند.
- **مرتب‌سازی بر اساس `toString()` (نمایش hex-با-خط‌تیره)** — رد شد: رشته‌ای و کندتر، و هنوز باید اثبات شود با ترتیب باینری PostgreSQL یکی است؛ مقایسه‌ی مستقیم unsigned روی بیت‌ها هم سریع‌تر است هم بی‌ابهام.

## پیامدها

### مثبت

- استدلال deadlock-free بودن ADR-0026 روی یک تعریف واحد و آزمایش‌شده استوار است، نه فرض ضمنی که در Java و SQL یکسان است.
- مسئولیت ترتیب‌دهی در outbound port است، نه در هر call site — امکان خطا در آینده حذف می‌شود.

### منفی / بدهی فنی

- توسعه‌دهنده‌ای که به `UUID.compareTo` عادت دارد باید این تفاوت را بداند؛ Javadoc روی `AccountId` این را صریح مستند می‌کند.

</div>
