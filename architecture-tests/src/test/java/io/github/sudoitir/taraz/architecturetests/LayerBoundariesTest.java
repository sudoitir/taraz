package io.github.sudoitir.taraz.architecturetests;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.sudoitir.taraz.core.domain.common.AbstractAggregateRoot;
import io.github.sudoitir.taraz.core.domain.common.AbstractEntity;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Layer-boundary rules (ADR-0006 map, enforced per ADR-0023). Plain JUnit — no Spring context, no Docker — so
 * {@code ./mvnw test} stays docker-free.
 */
@AnalyzeClasses(packages = "io.github.sudoitir.taraz", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerBoundariesTest {

    private static final String DOMAIN = "..core.domain..";
    private static final String APPLICATION = "..core.application..";
    private static final String OUTBOUND_PORTS_PACKAGE = "io.github.sudoitir.taraz.core.application.ports.outbound";
    private static final String PORTS_PACKAGE_PREFIX = "io.github.sudoitir.taraz.core.application.ports";

    // ADR-0033/0034: the read side's own outbound port; every other class in this package is a write-side
    // port the read side must never reach (ADR-0007's "queries never go through the application service").
    private static final String READ_SIDE_OUTBOUND_PORT = "AccountBalanceReadRepository";

    /** ADR-0039: application may depend on Spring stereotype/DI only, never any other Spring package. */
    private static final DescribedPredicate<JavaClass> SPRING_BEYOND_STEREOTYPE_AND_DI =
            new DescribedPredicate<>("a Spring package other than stereotype/beans.factory (ADR-0039)") {
                @Override
                public boolean test(JavaClass javaClass) {
                    String pkg = javaClass.getPackageName();
                    return pkg.startsWith("org.springframework.")
                            && !pkg.startsWith("org.springframework.stereotype")
                            && !pkg.startsWith("org.springframework.beans.factory");
                }
            };

    /** Every class in the outbound-ports package except the read side's own port (ADR-0007/0033). */
    private static final DescribedPredicate<JavaClass> WRITE_SIDE_OUTBOUND_PORT =
            new DescribedPredicate<>("a write-side-only outbound port") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getPackageName().equals(OUTBOUND_PORTS_PACKAGE)
                            && !javaClass.getSimpleName().equals(READ_SIDE_OUTBOUND_PORT);
                }
            };

    /** Any real class in a ports package, excluding the compiler-generated {@code package-info}. */
    private static final DescribedPredicate<JavaClass> PORT_CONTRACT_CANDIDATE =
            new DescribedPredicate<>("in a ports package, excluding package-info") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getPackageName().startsWith(PORTS_PACKAGE_PREFIX)
                            && !javaClass.getSimpleName().equals("package-info");
                }
            };

    /** Domain stays fully Spring-free (ADR-0005/0006); application may use stereotype + DI only (ADR-0039). */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule application_depends_on_no_spring_package_other_than_stereotype_and_di = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat(SPRING_BEYOND_STEREOTYPE_AND_DI);

    @ArchTest
    static final ArchRule query_does_not_depend_on_write_side_outbound_ports_or_service = noClasses()
            .that()
            .resideInAPackage("..core.application.query..")
            .should()
            .dependOnClassesThat(WRITE_SIDE_OUTBOUND_PORT)
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage("..core.application.service..");

    /** ADR-0006: ports are contracts and value types only — no concrete logic classes. */
    @ArchTest
    static final ArchRule ports_contain_only_contracts_and_value_types = classes()
            .that(PORT_CONTRACT_CANDIDATE)
            .should()
            .beInterfaces()
            .orShould()
            .beRecords()
            .orShould()
            .beEnums()
            .orShould()
            .beAssignableTo(Throwable.class);

    @ArchTest
    static final ArchRule core_never_reads_ambient_time =
            noClasses().that().resideInAPackage("..core..").should().callMethod(Instant.class, "now");

    @ArchTest
    static final ArchRule core_does_not_depend_on_adapters = noClasses()
            .that()
            .resideInAPackage("..core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..adapters..");

    @ArchTest
    static final ArchRule driving_adapters_do_not_use_outbound_ports = noClasses()
            .that()
            .resideInAPackage("..adapters.driving..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..core.application.ports.outbound..");

    @ArchTest
    static final ArchRule driving_adapters_do_not_use_service = noClasses()
            .that()
            .resideInAPackage("..adapters.driving..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..core.application.service..");

    /**
     * ADR-0006 keeps adapters off the domain's behavior — but the inbound ports the driving adapter serves
     * deliberately expose a small set of domain value types as the contract surface (ADR-0043): the adapter
     * may handle exactly those and nothing else in {@code core.domain}.
     */
    private static final DescribedPredicate<JavaClass> DOMAIN_TYPE_NOT_ON_PORT_CONTRACT_SURFACE =
            new DescribedPredicate<>("a domain type outside the port contract surface (Result, DomainError, "
                    + "ErrorCode, Money, AccountId, TransactionId)") {
                private static final String SURFACE_PREFIX = "io.github.sudoitir.taraz.core.domain.";
                private static final java.util.Set<String> ALLOWED = java.util.Set.of(
                        SURFACE_PREFIX + "common.DomainError",
                        SURFACE_PREFIX + "common.ErrorCode",
                        SURFACE_PREFIX + "money.Money",
                        SURFACE_PREFIX + "account.AccountId",
                        SURFACE_PREFIX + "transaction.TransactionId");
                private static final String RESULT = SURFACE_PREFIX + "common.Result";

                @Override
                public boolean test(JavaClass javaClass) {
                    String name = javaClass.getName();
                    return javaClass
                                    .getPackageName()
                                    .startsWith(SURFACE_PREFIX.substring(0, SURFACE_PREFIX.length() - 1))
                            && !ALLOWED.contains(name)
                            && !name.equals(RESULT)
                            && !name.startsWith(RESULT + "$");
                }
            };

    @ArchTest
    static final ArchRule driving_adapters_use_only_port_surface_domain_types = noClasses()
            .that()
            .resideInAPackage("..adapters.driving..")
            .should()
            .dependOnClassesThat(DOMAIN_TYPE_NOT_ON_PORT_CONTRACT_SURFACE);

    @ArchTest
    static final ArchRule driven_adapters_do_not_use_driving = noClasses()
            .that()
            .resideInAPackage("..adapters.driven..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..adapters.driving..");

    // --- Domain purity (ADR-0005; the domain's only dependencies are the JDK, JSpecify and JUG) ---

    @ArchTest
    static final ArchRule domain_depends_only_on_jdk_jspecify_jug = classes()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "org.jspecify..", "com.fasterxml.uuid..", DOMAIN);

    @ArchTest
    static final ArchRule domain_has_no_framework_orm_or_codegen = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta..", "lombok..", "org.mapstruct..", "org.springframework..");

    @ArchTest
    static final ArchRule domain_never_reads_ambient_time_or_random = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .callMethod(System.class, "currentTimeMillis")
            .orShould()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(UUID.class, "randomUUID");

    @ArchTest
    static final ArchRule domain_never_uses_legacy_date_types = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Date.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(Calendar.class);

    @ArchTest
    static final ArchRule aggregate_roots_live_only_in_the_domain = classes()
            .that()
            .areAssignableTo(AbstractAggregateRoot.class)
            .should()
            .resideInAPackage(DOMAIN);

    @ArchTest
    static final ArchRule entities_have_no_public_setters = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .areAssignableTo(AbstractEntity.class)
            .and()
            .arePublic()
            .should()
            .haveNameMatching("set[A-Z].*");

    @ArchTest
    static final ArchRule entities_have_no_public_no_arg_constructor = noConstructors()
            .that()
            .areDeclaredInClassesThat()
            .areAssignableTo(AbstractEntity.class)
            .and()
            .arePublic()
            .should()
            .haveRawParameterTypes(new Class<?>[0])
            .allowEmptyShould(true); // empty means: no entity has a public constructor at all — the goal
}
