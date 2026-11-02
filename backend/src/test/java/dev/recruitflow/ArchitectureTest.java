package dev.recruitflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Module and layering rules from CLAUDE.md. Do not weaken these to make code compile.
 * Plain JUnit tests (not the ArchUnit JUnit engine) so Surefire counts and fails them reliably.
 */
class ArchitectureTest {

    private static JavaClasses main;

    @BeforeAll
    static void importClasses() {
        main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.recruitflow");
    }

    @Test
    void modulesAreFreeOfCycles() {
        slices().matching("dev.recruitflow.(*)..").should().beFreeOfCycles()
                .allowEmptyShould(true).check(main);
    }

    @Test
    void sharedKernelDependsOnNoModule() {
        classes().that().resideInAPackage("dev.recruitflow.shared..")
                .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                        "dev.recruitflow.identity..", "dev.recruitflow.skill..", "dev.recruitflow.candidate..",
                        "dev.recruitflow.job..", "dev.recruitflow.pipeline..", "dev.recruitflow.interview..",
                        "dev.recruitflow.document..", "dev.recruitflow.notification..", "dev.recruitflow.audit..",
                        "dev.recruitflow.analytics..", "dev.recruitflow.matching..")
                .allowEmptyShould(true).check(main);
    }

    @Test
    void controllersDoNotUseRepositories() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should().onlyDependOnClassesThat().haveSimpleNameNotEndingWith("Repository")
                .allowEmptyShould(true).check(main);
    }

    @Test
    void noFieldInjection() {
        fields().should().notBeAnnotatedWith(Autowired.class)
                .because("constructor injection only")
                .check(main);
    }
}
