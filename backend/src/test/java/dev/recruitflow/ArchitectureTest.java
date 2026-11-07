package dev.recruitflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Module and layering rules from CONTRIBUTING.md. Do not weaken these to make code compile.
 * Plain JUnit tests (not the ArchUnit JUnit engine) so Surefire counts and fails them reliably.
 */
class ArchitectureTest {

    private static final List<String> MODULES = List.of("identity", "skill", "candidate", "job", "pipeline",
            "interview", "document", "notification", "audit", "analytics", "matching");
    private static final String[] INTERNAL_LAYERS = {"api", "service", "domain", "repository", "config", "mail",
            "infra", "storage", "email", "outbox"};

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

    /** Other modules may only use a module's root-package facade, never its internal layers. */
    @Test
    void modulesExposeOnlyTheirRootPackage() {
        for (String module : MODULES) {
            noClasses().that().resideOutsideOfPackage("dev.recruitflow." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            Arrays.stream(INTERNAL_LAYERS).map(layer -> "dev.recruitflow." + module + "." + layer + "..")
                                    .toArray(String[]::new))
                    .because(module + " must be used through its public facade")
                    .allowEmptyShould(true).check(main);
        }
    }

    @Test
    void noFieldInjection() {
        fields().should().notBeAnnotatedWith(Autowired.class)
                .because("constructor injection only")
                .check(main);
    }
}
