<div dir="rtl">

# 0006. معماری صریح (Explicit Architecture)

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

ساختار کد باید مرزهای دامنه/کاربرد/زیرساخت را صریح کند تا تصمیم‌های هم‌زمانی و سازگاری قابل‌بازبینی باشند.

## تصمیم

**معماری صریح** به سبک Herberto Graça: تلفیق Hexagonal / Onion / Clean با Ports & Adapters — دامنه در مرکز، فریم‌ورک‌ها در لبه.

- مرجع: https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/

## پیامدها

- وابستگی‌ها فقط به سمت داخل؛ قوانین معماری با ArchUnit (ADR-0023) enforce می‌شوند.

</div>
