<div dir="rtl">

# 0002. پشته‌ی پلتفرم: Java 21 و Spring Boot 4.1.0

**وضعیت:** پذیرفته‌شده
**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

چالش Java 21 و Spring Boot را الزام کرده و Maven یا Gradle را آزاد گذاشته است. بار اصلی چالش I/O-bound است (تراکنش دیتابیس، Valkey، Kafka) و تست هم‌زمانی با ۱۰۰۰ عملیات هم‌راستا می‌خواهد؛ پس مدل thread باید ساده و در عین حال ظرفیت بالا داشته باشد.

## تصمیم

- **Java 21**، **Spring Boot 4.1.0**، **Maven**
- **Virtual Threads** فعال (`spring.threads.virtual.enabled=true`)
- groupId: `io.github.sudoitir`

## گزینه‌های بررسی‌شده

- **Gradle** — رد شد: Maven برای داوری چالش رایج‌تر و خواناتر است؛ سرعت build تفاوت معناداری در این مقیاس ندارد.
- **Platform threads کلاسیک** — رد شد: برای I/O-bound با هزاران درخواست هم‌زمان، virtual thread مدل برنامه‌نویسی blocking را با هزینه‌ی thread ارزان نگه می‌دارد؛ reactive (WebFlux) پیچیدگی خوانایی را بی‌دلیل بالا می‌برد.

## پیامدها

### مثبت

- Virtual Threads مدل هم‌زمانی را ساده می‌کند: کد blocking خوانا، بدون callback/reactive.

### منفی / بدهی فنی

- صحت منطق قفل‌گذاری همچنان بر عهده‌ی کد است (ADR-0026)؛ virtual thread هیچ race را حل نمی‌کند و `synchronized` پین‌شونده باید اجتناب شود.

</div>
