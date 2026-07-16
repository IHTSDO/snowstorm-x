package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class FHIRCodeSystemProviderValidateBatchTest extends AbstractFHIRTest {

	private static final String VERSION = "http://snomed.info/sct/1234000008";

	@Test
	void testBatchGetTwoValidCodes() {
		Bundle request = batchBundle(
				batchGetEntry("CodeSystem/$validate-code?url=" + SNOMED_URI + "&version=" + VERSION + "&code=" + sampleSCTID),
				batchGetEntry("CodeSystem/$validate-code?version=" + VERSION + "&coding=http://snomed.info/sct|" + sampleSCTID));

		Bundle response = postBatch(request);

		assertEquals(BundleType.BATCHRESPONSE, response.getType());
		assertEquals(2, response.getEntry().size());
		assertEntryResult(response.getEntry().get(0), "true");
		assertEntryResult(response.getEntry().get(1), "true");
	}

	@Test
	void testBatchGetValidAndUnknownCode() {
		Bundle request = batchBundle(
				batchGetEntry("CodeSystem/$validate-code?url=" + SNOMED_URI + "&version=" + VERSION + "&code=" + sampleSCTID),
				batchGetEntry("CodeSystem/$validate-code?url=" + SNOMED_URI + "&version=" + VERSION + "&code=1234000008501"));

		Bundle response = postBatch(request);

		assertEquals(BundleType.BATCHRESPONSE, response.getType());
		assertEntryResult(response.getEntry().get(0), "true");
		assertEntryResult(response.getEntry().get(1), "false");
	}

	@Test
	void testBatchGetDisplayValidation() {
		Bundle request = batchBundle(
				batchGetEntry("CodeSystem/$validate-code?version=" + VERSION + "&coding=http://snomed.info/sct|" + sampleSCTID + "&display=Baked potato 1"),
				batchGetEntry("CodeSystem/$validate-code?version=" + VERSION + "&coding=http://snomed.info/sct|" + sampleSCTID + "&display=foo"));

		Bundle response = postBatch(request);

		assertEntryResult(response.getEntry().get(0), "true");
		assertEntryResult(response.getEntry().get(1), "false");
	}

	@Test
	void testBatchPostWithParametersBody() {
		Parameters first = new Parameters();
		first.addParameter("url", new UriType(SNOMED_URI));
		first.addParameter("version", new StringType(VERSION));
		first.addParameter("code", new CodeType(sampleSCTID));

		Parameters second = new Parameters();
		second.addParameter("version", new StringType(VERSION));
		second.addParameter("coding", new Coding("http://snomed.info/sct", sampleSCTID, null));

		Bundle request = batchBundle(
				batchPostEntry("CodeSystem/$validate-code", first),
				batchPostEntry("CodeSystem/$validate-code", second));

		Bundle response = postBatch(request);

		assertEquals(2, response.getEntry().size());
		assertEntryResult(response.getEntry().get(0), "true");
		assertEntryResult(response.getEntry().get(1), "true");
	}

	@Test
	void testBatchWithUnsupportedEntry() {
		Bundle request = batchBundle(
				batchGetEntry("CodeSystem/$validate-code?url=" + SNOMED_URI + "&version=" + VERSION + "&code=" + sampleSCTID),
				batchGetEntry("CodeSystem/$lookup?system=" + SNOMED_URI + "&code=" + sampleSCTID));

		Bundle response = postBatch(request);

		assertEquals(2, response.getEntry().size());
		assertEntryResult(response.getEntry().get(0), "true");
		assertTrue(response.getEntry().get(1).getResponse().getStatus().startsWith("501"));
		assertInstanceOf(OperationOutcome.class, response.getEntry().get(1).getResource());
	}

	@Test
	void testTransactionBundleRejected() {
		Bundle request = new Bundle();
		request.setType(BundleType.TRANSACTION);
		request.addEntry(batchGetEntry("CodeSystem/$validate-code?url=" + SNOMED_URI + "&version=" + VERSION + "&code=" + sampleSCTID));

		ResponseEntity<String> response = postBatchRaw(request);
		expectResponse(response, 400, "Only batch Bundles are supported");
	}

	@Test
	void testBatchInstanceLevelValidateCode() {
		String codeSystemId = "sct_" + sampleModuleId + "_" + sampleVersion;
		Bundle request = batchBundle(
				batchGetEntry("CodeSystem/" + codeSystemId + "/$validate-code?code=" + sampleSCTID));

		Bundle response = postBatch(request);

		assertEquals(1, response.getEntry().size());
		assertEntryResult(response.getEntry().get(0), "true");
	}

	private Bundle postBatch(Bundle request) {
		ResponseEntity<String> response = postBatchRaw(request);
		expectResponse(response, 200);
		return fhirJsonParser.parseResource(Bundle.class, response.getBody());
	}

	private ResponseEntity<String> postBatchRaw(Bundle request) {
		String body = fhirJsonParser.encodeResourceToString(request);
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		return restTemplate.exchange(baseUrl + "/", HttpMethod.POST, entity, String.class);
	}

	private Bundle batchBundle(BundleEntryComponent... entries) {
		Bundle bundle = new Bundle();
		bundle.setType(BundleType.BATCH);
		for (BundleEntryComponent entry : entries) {
			bundle.addEntry(entry);
		}
		return bundle;
	}

	private BundleEntryComponent batchGetEntry(String url) {
		BundleEntryComponent entry = new BundleEntryComponent();
		entry.getRequest().setMethod(Bundle.HTTPVerb.GET).setUrl(url);
		return entry;
	}

	private BundleEntryComponent batchPostEntry(String url, Resource resource) {
		BundleEntryComponent entry = new BundleEntryComponent();
		entry.setResource(resource);
		entry.getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl(url);
		return entry;
	}

	private void assertEntryResult(BundleEntryComponent entry, String expectedResult) {
		assertTrue(entry.getResponse().getStatus().startsWith("200"), () -> entry.getResponse().getStatus());
		assertInstanceOf(Parameters.class, entry.getResource());
		Parameters parameters = (Parameters) entry.getResource();
		assertEquals(expectedResult, getPropertyString(parameters, "result"));
	}
}
