<div dir="rtl">

# 0012. توسعه‌ی spec-driven با ابزار AI (SDD)

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

توسعه با دستیار AI بدون spec مشترک، به کد غیرقابل‌دفاع منجر می‌شود.

## تصمیم

رویکرد توسعه **Spec-Driven Development** است: هر ویژگی/پیاده‌سازی از مسیر OpenSpec می‌گذرد — `/opsx:propose` ← `/opsx:apply` ← `/opsx:archive`. نوشتن کد بدون proposal تأییدشده ممنوع.

- مرجع: `.claude/rules/openspec.md`

## پیامدها

- specها (سناریوهای WHEN/THEN) منبع حقیقت زنده‌ی پروژه‌اند و در `openspec/specs/` نگه‌داری می‌شوند.

</div>
