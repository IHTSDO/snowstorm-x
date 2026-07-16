package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class FHIRCodeSystemProviderSubsumesTest extends AbstractFHIRTest {

	private static final String WK_ONLY_CONCEPT = "2577511006";
	private static final String BR_ONLY_CONCEPT = "25775999006";
	private static final int BR_VERSION = 20190831;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	@Test
	void testSubsumption() {
		String version = "http://snomed.info/sct" + "/" + sampleModuleId + "/version/" + sampleVersion;

		//Test subsumption using defaults
		String url = baseUrl + "/CodeSystem/$subsumes?system=http://snomed.info/sct" + "&version=" + version + "&codeA=" + Concepts.SNOMEDCT_ROOT +"&codeB=" + sampleSCTID;
		Parameters p = getParameters(url);
		String result = toString(getProperty(p, "outcome"));
		assertEquals("subsumes", result);

		//Test reverse subsumption using defaults
		url = baseUrl + "/CodeSystem/$subsumes?system=http://snomed.info/sct" + "&version=" + version + "&codeB=" + Concepts.SNOMEDCT_ROOT +"&codeA=" + sampleSCTID;
		p = getParameters(url);
		result = toString(getProperty(p, "outcome"));
		assertEquals("subsumed-by", result);

		// Alternative URLs using coding, system param is missing - should fail
		url = baseUrl + "/CodeSystem/$subsumes?version=" + version + "&codingA=" + SNOMED_URI + "|" + Concepts.SNOMEDCT_ROOT + "&codingB=" + SNOMED_URI + "|" + sampleSCTID;
		getParameters(url, 400, "One of id or system parameters must be supplied");
	}

	@Test
	void testSubsumptionWithoutVersionInternationalContent() {
		String url = baseUrl + "/CodeSystem/$subsumes?system=http://snomed.info/sct&codeA=" + Concepts.SNOMEDCT_ROOT + "&codeB=" + sampleSCTID;
		Parameters p = getParameters(url);
		assertEquals("subsumes", toString(getProperty(p, "outcome")));
	}

	@Test
	void testSubsumptionWithoutVersionExtensionOnly() {
		removeInternationalPublishedVersion();

		String url = baseUrl + "/CodeSystem/$subsumes?system=http://snomed.info/sct&codeA=" + Concepts.SNOMEDCT_ROOT + "&codeB=" + sampleSCTID;
		Parameters p = getParameters(url);
		assertEquals("subsumes", toString(getProperty(p, "outcome")));
	}

	@Test
	void testSubsumptionWithoutVersionEmpty2000International() {
		removeInternationalPublishedVersion();
		codeSystemService.getOrCreateEmpty2000Version();

		String url = baseUrl + "/CodeSystem/$subsumes?system=http://snomed.info/sct&codeA=" + Concepts.SNOMEDCT_ROOT + "&codeB=" + sampleSCTID;
		Parameters p = getParameters(url);
		assertEquals("subsumes", toString(getProperty(p, "outcome")));
	}

	@Test
	void testSubsumptionWithoutVersionNoCommonEdition() throws Exception {
		removeInternationalPublishedVersion();
		createBrazilExtensionWithUniqueConcept();

		getParameters(baseUrl + "/CodeSystem/$subsumes?system=http://snomed.info/sct&codeA=" + WK_ONLY_CONCEPT + "&codeB=" + BR_ONLY_CONCEPT,
				404, "No single loaded SNOMED CT edition contains both codes");

		FHIRCodeSystemVersionParams params = new FHIRCodeSystemVersionParams(SNOMED_URI);
		SnowstormFHIRServerResponseException exception = assertThrows(SnowstormFHIRServerResponseException.class,
				() -> fhirCodeSystemService.resolveSnomedCodeSystemVersionForSubsumes(WK_ONLY_CONCEPT, BR_ONLY_CONCEPT, params));
		assertEquals(404, exception.getStatusCode());
		assertTrue(exception.getMessage().contains("No single loaded SNOMED CT edition contains both codes"));
	}

	private void removeInternationalPublishedVersion() {
		CodeSystem international = codeSystemService.find("SNOMEDCT");
		CodeSystemVersion internationalVersion = codeSystemService.findVersion("SNOMEDCT", 20190131);
		codeSystemService.deleteVersion(international, internationalVersion);
	}

	private void createBrazilExtensionWithUniqueConcept() throws Exception {
		CodeSystem wkCodeSystem = codeSystemService.find("SNOMEDCT-WK");
		CodeSystemVersion wkVersion = codeSystemService.findVersion("SNOMEDCT-WK", sampleVersion);
		String releaseBranch = wkVersion.getParentBranchPath();
		String branchBR = releaseBranch + "/SNOMEDCT-BR";
		CodeSystem codeSystemBR = new CodeSystem("SNOMEDCT-BR", branchBR).setUriModuleId("999000000000001");
		codeSystemService.createCodeSystem(codeSystemBR);

		Concept brConcept = new Concept(BR_ONLY_CONCEPT)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE))
				.addDescription(new Description("Brazil only concept (Substance)")
						.setTypeId(Concepts.FSN)
						.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED));
		conceptService.batchCreate(List.of(brConcept), branchBR);
		codeSystemService.createVersion(codeSystemBR, BR_VERSION, "Brazil test version");
	}

}
