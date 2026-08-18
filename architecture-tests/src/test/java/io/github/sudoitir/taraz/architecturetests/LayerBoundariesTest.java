package io.github.sudoitir.taraz.architecturetests;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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

    @ArchTest
    static final ArchRule core_does_not_depend_on_spring = noClasses()
            .that()
            .resideInAPackage("..core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

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

    @ArchTest
    static final ArchRule driving_adapters_do_not_use_domain = noClasses()
            .that()
            .resideInAPackage("..adapters.driving..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..core.domain..");

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
