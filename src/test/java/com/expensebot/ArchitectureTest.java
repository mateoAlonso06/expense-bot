package com.expensebot;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.expensebot", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] MODULES = {"accounts", "budgets", "channels", "conversation", "expenses"};

    @ArchTest
    static final ArchRule domain_is_free_of_frameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static void modules_only_use_the_public_api_of_other_modules(com.tngtech.archunit.core.domain.JavaClasses classes) {
        for (String module : MODULES) {
            for (String other : MODULES) {
                if (module.equals(other)) {
                    continue;
                }
                noClasses()
                        .that().resideInAPackage("com.expensebot." + module + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                "com.expensebot." + other + ".domain..",
                                "com.expensebot." + other + ".application..",
                                "com.expensebot." + other + ".infrastructure..")
                        .allowEmptyShould(true)
                        .because("module '" + module + "' may only use the public API of '" + other + "'")
                        .check(classes);
            }
        }
    }
}
