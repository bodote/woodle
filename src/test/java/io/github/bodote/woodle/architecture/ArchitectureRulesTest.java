package io.github.bodote.woodle.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "io.github.bodote.woodle",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_application_adapter_or_config =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..adapter..", "..config..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring_or_jakarta =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapter_or_config =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..adapter..", "..config..");

    @ArchTest
    static final ArchRule application_and_domain_do_not_depend_on_web_adapter_types =
            noClasses()
                    .that().resideInAnyPackage("..application..", "..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule dto_suffix_is_reserved_for_web_adapter_transfer_types =
            classes()
                    .that().haveSimpleNameEndingWith("DTO")
                    .should().resideInAPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule dao_suffix_is_reserved_for_persistence_adapter_types =
            classes()
                    .that().haveSimpleNameEndingWith("DAO")
                    .should().resideInAPackage("..adapter.out.persistence..");
}
