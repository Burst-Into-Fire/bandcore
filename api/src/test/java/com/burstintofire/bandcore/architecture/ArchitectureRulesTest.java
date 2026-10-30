package com.burstintofire.bandcore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureRulesTest {

	private static final String[] MONEY_PACKAGES = { "..finance..", "..merch.." };

	private static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("com.burstintofire.bandcore");

	@Test
	void noFloatingPointFieldsInMoneyPackages() {
		ArchRule rule = fields().that()
			.areDeclaredInClassesThat()
			.resideInAnyPackage(MONEY_PACKAGES)
			.should()
			.notHaveRawType(float.class)
			.andShould()
			.notHaveRawType(double.class)
			.andShould()
			.notHaveRawType(Float.class)
			.andShould()
			.notHaveRawType(Double.class)
			.because("money is stored as integer cents (ADR-0006)")
			.allowEmptyShould(true);
		rule.check(CLASSES);
	}

	@Test
	void noFloatingPointReturnTypesInMoneyPackages() {
		ArchRule rule = methods().that()
			.areDeclaredInClassesThat()
			.resideInAnyPackage(MONEY_PACKAGES)
			.should()
			.notHaveRawReturnType(float.class)
			.andShould()
			.notHaveRawReturnType(double.class)
			.andShould()
			.notHaveRawReturnType(Float.class)
			.andShould()
			.notHaveRawReturnType(Double.class)
			.because("money is stored as integer cents (ADR-0006)")
			.allowEmptyShould(true);
		rule.check(CLASSES);
	}

	@Test
	void noFieldInjection() {
		NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
	}

	@Test
	void bandsAreOnlyAccessedThroughTheTenancyModule() {
		ArchRule rule = noClasses().that()
			.resideOutsideOfPackage("..tenancy..")
			.should()
			.dependOnClassesThat()
			.resideInAPackage("..tenancy.band..")
			.because("bands is a global table owned by the tenancy module (ADR-0002)");
		rule.check(CLASSES);
	}

	@Test
	void controllersDoNotUseRepositoriesDirectly() {
		ArchRule rule = noClasses().that()
			.areAnnotatedWith(RestController.class)
			.should()
			.dependOnClassesThat()
			.areAssignableTo(Repository.class)
			.because("controllers call services, which own transactions and tenant filtering")
			.allowEmptyShould(true);
		rule.check(CLASSES);
	}

}
