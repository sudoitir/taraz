<div dir="rtl">

# 0040. مرز تراکنش با outbound port به نام UnitOfWork

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

ADR-0018 می‌گوید تراکنش دقیقاً در command handler، در مرز واحد اتمیک واقعی باز می‌شود. ADR-0039 اجازه‌ی `@Service`/constructor injection را در `core/application/service` می‌دهد، اما استفاده‌ی مستقیم از `@Transactional` روی متد handler دو مشکل دارد: (۱) طبق ADR-0021/0026، گیت idempotency در Valkey باید **قبل از** باز شدن تراکنش اجرا شود و تراکنش نباید I/O بیرونی داشته باشد — یک `@Transactional` روی کل متد handler این ترتیب را می‌شکند؛ (۲) `@Transactional` روی self-invocation از طریق پروکسی Spring کار نمی‌کند و مرز واقعی را در خودِ کد handler نامرئی می‌کند — دقیقاً نقطه‌ای که ADR-0018 می‌خواهد صریح باشد.

## تصمیم

مرز تراکنش با یک outbound port به نام `UnitOfWork` بیان می‌شود:

```java
public interface UnitOfWork {
    <T> Result<T> inTransaction(Supplier<Result<T>> work);
}
```

- Handler دقیقاً واحد اتمیک را — بعد از گیت Valkey، بعد از گرفتن قفل‌های ردیف — داخل یک فراخوانی `unitOfWork.inTransaction(...)` قرار می‌دهد؛ این یک خط از کد خودِ handler است، نه annotation نامرئی.
- بازگشت `Failure` از تابع درونی، rollback را معنا می‌کند — این قرارداد صریح است، نه وابسته به این‌که exception پرتاب شود یا نشود.
- پیاده‌سازی واقعی (با `TransactionTemplate` روی `PlatformTransactionManager`) در adapter پایداری می‌آید؛ `core/application/service` هرگز API تراکنش Spring را import نمی‌کند.

## گزینه‌های بررسی‌شده

- **`@Transactional` مستقیم روی متد handler** — رد شد: گیت Valkey را هم داخل تراکنش می‌کشد (نقض ADR-0021/0026 که تراکنش باید کوتاه و بدون I/O بیرونی بماند).
- **`@Transactional` روی یک bean دوم که handler به آن delegate می‌کند** — رد شد: یک use-case را بین دو کلاس می‌شکند و مرز واقعی را پشت پروکسی Spring پنهان می‌کند — دقیقاً برخلاف قصد ADR-0018.
- **`UnitOfWork`** (انتخاب‌شده): مرز در یک خط از کد خودِ handler صریح است، بدون پروکسی، بدون self-invocation trap.

## پیامدها

### مثبت

- مرز تراکنش در بازبینی کد قابل‌اشاره و دقیقاً به‌اندازه‌ی واحد اتمیک واقعی است — همان چیزی که ADR-0018 می‌خواهد.
- `core/application/service` هیچ‌گاه به API تراکنش Spring وابسته نیست؛ تست واحد handler با یک `UnitOfWork` جعلی (که فقط تابع را اجرا می‌کند) کاملاً ممکن است، بدون context اسپرینگ.

### منفی / بدهی فنی

- یک انتزاع اضافه روی چیزی که Spring از قبل می‌دهد (`TransactionTemplate`)؛ هزینه‌اش یک interface کوچک است در برابر مرز صریح و تست‌پذیری بدون context.

</div>
