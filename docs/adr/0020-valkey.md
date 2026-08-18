<div dir="rtl">

# 0020. Valkey به‌عنوان ذخیره‌ی توزیع‌شده

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

برای cache و هماهنگی توزیع‌شده (مثل idempotency keys) به یک ذخیره‌ی سریع in-memory نیاز داریم.

## تصمیم

**Valkey** (fork متن‌باز Redis) به‌عنوان cache/coordination store.

## پیامدها

- سازگار با پروتکل Redis؛ بدون وابستگی به لایسنس Redis Ltd.

</div>
