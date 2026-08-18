<div dir="rtl">

# 0015. Entity تخت با JPA Embeddable

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

مدل persistence نباید گراف عمیق شی‌گرا شود؛ خوانایی schema و پیش‌بینی‌پذیری query مهم‌تر است.

## تصمیم

Entityهای persistence **تخت** هستند و ترکیب از طریق `@Embeddable` / `@Embedded` انجام می‌شود؛ مدل دامنه جدا از مدل persistence باقی می‌ماند.

## پیامدها

- بدون relationهای عمیق JPA؛ mapping صریح بین دامنه و persistence لازم است.

</div>
