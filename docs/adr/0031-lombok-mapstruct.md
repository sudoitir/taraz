<div dir="rtl">

# 0031. Lombok + MapStruct در لایه‌های غیردامنه (هرگز در core)

**وضعیت:** پذیرفته‌شده

**تاریخ:** ۲۰۲۶-۰۸-۱۸

## زمینه

entityهای JPA، DTOها و mapperها در لایه‌ی adapters پر از boilerplate‌اند (getter/setter، equals/hashCode، mapping فیلد‌به‌فیلد). نوشتن دستی آن‌ها خطاپذیر و در review پر سر و صداست. اما `core` طبق ADR-0005 باید خالص و بدون وابستگی به framework/annotation processing بماند.

## تصمیم

- **Lombok** (۱.۱۸.۴۶، scope ‏`provided`) و **MapStruct** (۱.۶.۳) فقط در `adapters` و `container` فعال‌اند؛ در `core` ممنوع (دامنه با builder و factory خالص ساخته می‌شود — ADR-0005).
- هر دو از طریق `annotationProcessorPaths` با **lombok-mapstruct-binding** (۰.۲.۰) به هم وصل می‌شوند (Lombok اول اجرا شود تا MapStruct getterها را ببیند).
- نسخه‌ها در `dependencyManagement` ریشه پین می‌شوند.

## گزینه‌های بررسی‌شده

- **Java record برای همه‌ی DTOها** — رد شد برای entityهای JPA: record immutable است و با مدل persistence (ADR-0015) سازگار نیست؛ برای DTOها همچنان گزینه‌ی اول است.
- **MapStruct در core برای mapping دامنه** — رد شد: نقض مستقیم ADR-0005؛ core هیچ annotation processorای ندارد.
- **بدون Lombok** — رد شد: حجم boilerplate در adapters ارزش چندانی به خوانایی اضافه نمی‌کند.

## پیامدها

### مثبت

- boilerplate حذف می‌شود؛ mapping در compile time با codegen (بدون reflection در runtime) انجام می‌شود؛ خطای mapping به‌جای runtime در compile دیده می‌شود.

### منفی / بدهی فنی

- وابستگی به annotation processing و ترتیب processorها؛ مرز «ممنوع در core» باید در review (و در آینده با ArchUnit) حفظ شود.

</div>
