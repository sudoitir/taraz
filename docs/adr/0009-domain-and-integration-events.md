<div dir="rtl">

# 0009. رویدادمحوری: Domain Event و Integration Event

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

وقوع عملیات مالی باید به‌صورت رویداد قابل‌انتشار باشد، بدون آلوده‌کردن دامنه به جزئیات فنی.

## تصمیم

- **DomainEvent**: خالص، بدون جزئیات فنی، تاریخ‌ها با فرمت استاندارد **ISO 8601**.
- **IntegrationEvent**: برای انتشار به بیرون از مرز سرویس، جدا از DomainEvent.

- مرجع: https://herbertograca.com/2017/10/05/event-driven-architecture/

## پیامدها

- انتشار Integration Event از طریق Outbox (ADR-0010) انجام می‌شود.

</div>
