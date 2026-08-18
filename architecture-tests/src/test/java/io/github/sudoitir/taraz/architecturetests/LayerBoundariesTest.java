package io.github.sudoitir.taraz.architecturetests;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Layer-boundary rules (ADR-0006 map, enforced per ADR-0023). Plain JUnit — no Spring context, no Docker — so
 * {@code ./mvnw test} stays docker-free.
 */
@AnalyzeClasses(packages = "io.github.sudoitir.taraz", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerBoundariesTest {

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
}
