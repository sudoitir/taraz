<div dir="rtl">

# 0030. تحلیل استاتیک compile-time با Error Prone + NullAway

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

برای سرویسی که صحت مالی آن زیر concurrency گرید می‌شود، گرفتن bug در compile time ارزان‌ترین خط دفاع است. Java به‌تنهایی null-safety و الگوهای رایج خطا (مثل `equals` روی نوع ناسازگار) را نمی‌گیرد.

## تصمیم

**Error Prone** (۲.۵۰.۰) و **NullAway** (۰.۱۳.۸) به‌عنوان pluginهای javac در `annotationProcessorPaths` پیکربندی می‌شوند:

- NullAway در **JSpecify mode** با severity ‏`ERROR` روی پکیج‌های `io.github.sudoitir.taraz`؛ قرارداد nullness با `@NullMarked` در `package-info.java` اعلام می‌شود؛
- warningهای کد generated (Lombok/MapStruct) با `-XepDisableWarningsInGeneratedCode` خاموش می‌شود؛
- `.mvn/jvm.config` فلگ‌های `--add-exports`/`--add-opens` لازم برای javac را می‌دهد؛
- نقض = شکست کامپایل، هم محلی هم CI.

## گزینه‌های بررسی‌شده

- **فقط Nullability annotationها بدون enforcement** — رد شد: بدون ابزار، annotation دکوراسیون است.
- **SpotBugs/Checker Framework** — رد شد: Error Prone سریع‌تر و در چرخه‌ی compile تعبیه می‌شود؛ NullAway برای nullness کافی است.

## پیامدها

### مثبت

- دسته‌ای از bugها (null dereference، الگوهای غلط) قبل از اجرا شکست می‌خورند؛ قرارداد nullness صریح و ماشین‌خوانا.

### منفی / بدهی فنی

- سخت‌گیری بیشتر هنگام کامپایل و وابستگی به فلگ‌های داخلی javac (`.mvn/jvm.config`) که با ارتقای JDK باید بازبینی شود.

</div>
