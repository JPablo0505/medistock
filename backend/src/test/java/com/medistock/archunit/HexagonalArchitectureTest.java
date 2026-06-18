package com.medistock.archunit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Enforces hexagonal architecture boundaries at package level.
 *
 * <p>Rules mirror the dependency constraints documented in
 * {@code docs/design/technical-design.md §10}.</p>
 */
class HexagonalArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter().importPackages("com.medistock");
    }

    @Test
    void domainLayerMustNotDependOnFramework() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "javax.persistence..")
                .check(importedClasses);
    }

    @Test
    void applicationLayerMustOnlyDependOnDomain() {
        noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "org.springframework.web..", "jakarta.servlet..")
                .check(importedClasses);
    }

    @Test
    void infrastructureMustNotDependOnOtherContextInfrastructure() {
        noClasses()
                .that().resideInAnyPackage("..infrastructure..")
                .and().resideInAPackage("com.medistock.productcatalog..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.medistock.inventory.infrastructure..",
                        "com.medistock.sales.infrastructure..",
                        "com.medistock.alerting.infrastructure..")
                .check(importedClasses);

        noClasses()
                .that().resideInAnyPackage("..infrastructure..")
                .and().resideInAPackage("com.medistock.inventory..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.medistock.productcatalog.infrastructure..",
                        "com.medistock.sales.infrastructure..",
                        "com.medistock.alerting.infrastructure..")
                .check(importedClasses);
    }

    @Test
    void fefoAllocationStrategyMustBeInDomain() {
        classes()
                .that().haveSimpleName("FefoAllocationStrategy")
                .should().resideInAPackage("..domain..")
                .check(importedClasses);
    }

    @Test
    void batchAllocatorMustBeInApplication() {
        classes()
                .that().haveSimpleName("BatchAllocator")
                .should().resideInAPackage("..application..")
                .check(importedClasses);
    }

    @Test
    void controllersMustBeInInfrastructureRest() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..infrastructure.rest..")
                .check(importedClasses);
    }

    @Test
    void repositoryPortsAreInDomainImplementationsInInfrastructure() {
        classes()
                .that().areInterfaces()
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..domain.port..")
                .check(importedClasses);

        classes()
                .that().haveSimpleNameStartingWith("Jpa")
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..infrastructure.persistence..")
                .check(importedClasses);
    }

    @Test
    void salesApplicationMayDependOnInventoryApplication() {
        noClasses()
                .that().resideInAnyPackage("com.medistock.inventory..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.medistock.sales..")
                .check(importedClasses);
    }

    @Test
    void salesDomainMustNotDependOnInventoryDomain() {
        noClasses()
                .that().resideInAnyPackage("com.medistock.sales.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.medistock.inventory.domain..")
                .check(importedClasses);
    }
}
