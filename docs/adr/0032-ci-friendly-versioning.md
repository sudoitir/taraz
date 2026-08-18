<div dir="rtl">

# 0032. نسخه‌بندی CI-friendly با ${revision} + flatten plugin و Maven wrapper پین‌شده

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

در پروژه‌ی چندماژوله، تکرار نسخه‌ی hardcodeشده در همه‌ی pomها هنگام release دردناک و خطاپذیر است. همچنین نسخه‌ی Maven بین توسعه‌دهنده‌ها و CI باید یکسان باشد تا رفتار بیلد drift نگیرد.

## تصمیم

- نسخه‌ی پروژه یک بار در property ‏`revision` ریشه (`0.1.0-SNAPSHOT`) تعریف می‌شود و همه‌ی ماژول‌ها با `<version>${revision}</version>` به parent وصل می‌شوند.
- **flatten-maven-plugin** (۱.۷.۳، mode ‏`resolveCiFriendliesOnly`) هنگام install/deploy نسخه‌ی واقعی را در pom تخت شده حل می‌کند؛ `.flattened-pom.xml` در gitignore است.
- نسخه‌ی Maven با **wrapper** (`.mvn/wrapper/maven-wrapper.properties`، نسخه‌ی ۳.۹.۱۶) پین می‌شود؛ این فایل در git commit می‌شود و jAR wrapper خودکار دانلود می‌شود.
- maven-enforcer-plugin حداقل Java 21 و Maven 3.9 را enforce می‌کند.

## گزینه‌های بررسی‌شده

- **نسخه‌ی hardcode در هر pom** — رد شد: باید در N فایل دستی عوض شود.
- **maven-release-plugin با تگ و bump خودکار** — رد شد: برای این چالش سنگین است؛ bump دستی `revision` کافی است.

## پیامدها

### مثبت

- تغییر نسخه = تغییر یک property؛ بیلد محلی و CI دقیقاً با همان نسخه‌ی Maven اجرا می‌شوند؛ pomهای منتشرشده نسخه‌ی حل‌شده دارند.

### منفی / بدهی فنی

- یک plugin اضافه در چرخه‌ی بیلد؛ IDEهای قدیمی‌تر ممکن است با `${revision}` در parent ناسازگار باشند (flatten آن را پوشش می‌دهد).

</div>
