package com.hexboundrealms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void domainHasNoFrameworkDependency() {
    var classes = new ClassFileImporter().importPackages("com.hexboundrealms.domain");
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "com.hexboundrealms.infrastructure..")
        .check(classes);
  }
}
