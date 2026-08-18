<div dir="rtl">

# 0039. Spring در application layer مجاز است، در domain هرگز

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

قانون فعلی ArchUnit (`core_does_not_depend_on_spring`) کل `..core..` را از وابستگی به Spring منع می‌کند. اما ADR-0018 صریحاً می‌گوید مرز تراکنش باید در **command handler** — یعنی داخل `core/application/service` — باز شود. اگر Spring در این ماژول کلاً ممنوع بماند، نه `@Service`/constructor injection معمول Spring قابل استفاده است و نه راهی برای بیان صریح مرز تراکنش با ابزار خودِ framework باقی می‌ماند؛ توسعه‌دهنده مجبور می‌شود یا قانون را نقض کند یا انتزاعی موازی با چیزی که Spring از قبل می‌دهد بسازد. باید مشخص شود این قانون دقیقاً چه محدوده‌ای را باید enforce کند.

## تصمیم

قانون به `..core.domain..` محدود می‌شود، نه `..core..`. در `core/application` (هر دو ماژول `service` و `query`):

- **مجاز:** `@Service`/`@Component` و constructor injection (`org.springframework.stereotype..`, `org.springframework.beans.factory..`).
- **همچنان ممنوع:** Spring Data (repository/entity types)، `ApplicationEventPublisher`، `@Value`/`Environment` lookup، و هر annotation processing (Lombok، MapStruct — طبق ADR-0031 که همچنان در کل `core` معتبر است).
- تراکنش دیتابیسی همچنان **از طریق `UnitOfWork` outbound port** باز می‌شود (ADR-0040)، نه `@Transactional` مستقیم روی handler — بنابراین اجازه‌ی استفاده از stereotype به معنای اجازه‌ی نشت persistence framework به handler نیست.

`core/domain` بدون هیچ استثنا همچنان صفر وابستگی به Spring دارد — این تصمیم چیزی در آن لایه تغییر نمی‌دهد.

## گزینه‌های بررسی‌شده

- **`core/application` کاملاً بدون Spring، wiring با `@Bean` در `container`** — رد شد: هر handler را از یک configuration class جداگانه در `container` عبور می‌دهد بدون فایده‌ی واقعی، وقتی مرز تراکنش و persistence از قبل با ADR-0040 از handler بیرون نگه داشته می‌شود.
- **آزادی کامل Spring در `core/application`** — رد شد: اجازه می‌دهد Spring Data types یا `ApplicationEventPublisher` مستقیم در امضای use-case ظاهر شوند — دقیقاً همان coupling که ADR-0006 منع می‌کند.

## پیامدها

### مثبت

- Handlerها با `@Service` و constructor تزریق می‌شوند، دقیقاً به سبک معمول Spring، بدون نیاز به configuration class جداگانه؛ در عین حال هیچ persistence/transport type به امضای use-case نشت نمی‌کند.
- قانون ArchUnit به‌جای یک ممنوعیت سراسری نادرست، دقیقاً همان مرزی را enforce می‌کند که ADR-0006 برای domain در نظر گرفته.

### منفی / بدهی فنی

- قانون ArchUnit باید دقیق‌تر نوشته شود (لیست صریح پکیج‌های مجاز Spring)، نه یک `noClasses().dependOnClassesThat().resideInAnyPackage("org.springframework..")` ساده؛ نگهداری آن کمی دقت بیشتری می‌خواهد.
- توضیح ماژول `service` در `pom.xml` که ادعای «framework-free» دارد باید اصلاح شود تا با این تصمیم هم‌خوان بماند.

</div>
