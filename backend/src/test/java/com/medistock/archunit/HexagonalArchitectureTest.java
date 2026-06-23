package com.medistock.archunit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;

@DisplayName("Hexagonal architecture rules")
class HexagonalArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter().importPackages("com.medistock");
    }

    @Test
    @DisplayName("Domain layer must not depend on framework classes")
    void domainLayerMustNotDependOnFramework() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "javax.persistence..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Application layer must only depend on domain")
    void applicationLayerMustOnlyDependOnDomain() {
        noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "org.springframework.web..", "jakarta.servlet..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Infrastructure must not depend on other context infrastructure")
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
    @DisplayName("FefoAllocationStrategy must be in domain layer")
    void fefoAllocationStrategyMustBeInDomain() {
        classes()
                .that().haveSimpleName("FefoAllocationStrategy")
                .should().resideInAPackage("..domain..")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    @Test
    @DisplayName("BatchAllocator must be in application layer")
    void batchAllocatorMustBeInApplication() {
        classes()
                .that().haveSimpleName("BatchAllocator")
                .should().resideInAPackage("..application..")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    @Test
    @DisplayName("Controllers must be in infrastructure.rest package")
    void controllersMustBeInInfrastructureRest() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..infrastructure.rest..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Repository ports are in domain, implementations in infrastructure")
    void repositoryPortsAreInDomainImplementationsInInfrastructure() {
        classes()
                .that().areInterfaces()
                .and().haveSimpleNameEndingWith("Repository")
                .and().haveSimpleNameNotStartingWith("SpringDataJpa")
                .should().resideInAPackage("..domain.port..")
                .check(importedClasses);

        classes()
                .that().haveSimpleNameStartingWith("Jpa")
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..infrastructure.persistence..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Sales application may depend on inventory application")
    void salesApplicationMayDependOnInventoryApplication() {
        noClasses()
                .that().resideInAnyPackage("com.medistock.inventory..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.medistock.sales..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Sales domain must not depend on inventory domain")
    void salesDomainMustNotDependOnInventoryDomain() {
        noClasses()
                .that().resideInAnyPackage("com.medistock.sales.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.medistock.inventory.domain..")
                .allowEmptyShould(true)
                .check(importedClasses);
    }
}
