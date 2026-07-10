package org.snomed.snowstorm.fhir.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.snomed.snowstorm.fhir.domain.FHIRMapElement;
import org.snomed.snowstorm.fhir.domain.FHIRMapTarget;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FHIRConceptMapProviderTranslateTest {

	private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
	private static final String ICD10_SYSTEM = "http://hl7.org/fhir/sid/icd-10";
	private static final String SOURCE_CODE = "257751006";
	private static final String TARGET_CODE = "A1.100";

	@Mock
	private FHIRConceptMapService service;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@InjectMocks
	private FHIRConceptMapProvider provider;

	@Test
	void translateReturnsMatchWhenMappingFound() {
		when(request.getHeader("Accept-Language")).thenReturn(null);
		FHIRConceptMap map = implicitSnomedMap();
		FHIRMapElement element = mapElement(TARGET_CODE, "equivalent", "Test display");

		when(service.findMaps(isNull(), any(Coding.class), eq(ICD10_SYSTEM), isNull(), isNull()))
				.thenReturn(List.of(map));
		when(service.findMapElements(eq(map), any(Coding.class), eq(ICD10_SYSTEM), anyList()))
				.thenReturn(List.of(element));

		Parameters result = provider.translate(
				request, response,
				null, null, null,
				SOURCE_CODE, SNOMED_SYSTEM, null,
				null, null, null,
				null, ICD10_SYSTEM, null);

		assertTrue(result.getParameterBool("result"));
		Coding matchConcept = getMatchConcept(result);
		assertNotNull(matchConcept);
		assertEquals(ICD10_SYSTEM, matchConcept.getSystem());
		assertEquals(TARGET_CODE, matchConcept.getCode());
		assertEquals("Test display", matchConcept.getDisplay());
	}

	@Test
	void translateReturnsFalseWhenNoMappingFound() {
		when(request.getHeader("Accept-Language")).thenReturn(null);
		FHIRConceptMap map = implicitSnomedMap();

		when(service.findMaps(isNull(), any(Coding.class), isNull(), isNull(), isNull()))
				.thenReturn(List.of(map));
		when(service.findMapElements(eq(map), any(Coding.class), isNull(), anyList()))
				.thenReturn(Collections.emptyList());

		Parameters result = provider.translate(
				request, response,
				null, null, null,
				SOURCE_CODE, SNOMED_SYSTEM, null,
				null, null, null,
				null, null, null);

		assertFalse(result.getParameterBool("result"));
		assertEquals(
				"No mapping found for code '257751006', system 'http://snomed.info/sct'.",
				getStringParameter(result, "message"));
	}

	@Test
	void translateThrowsNotFoundWhenNoSuitableMap() {
		when(request.getHeader("Accept-Language")).thenReturn(null);
		when(service.findMaps(isNull(), any(Coding.class), isNull(), isNull(), isNull()))
				.thenReturn(Collections.emptyList());

		SnowstormFHIRServerResponseException exception = assertThrows(
				SnowstormFHIRServerResponseException.class,
				() -> provider.translate(
						request, response,
						null, null, null,
						SOURCE_CODE, SNOMED_SYSTEM, null,
						null, null, null,
						null, null, null));

		assertEquals(404, exception.getStatusCode());
		assertEquals("No suitable map found.", exception.getMessage());
	}

	@Test
	void translateRejectsReverseParameter() {
		SnowstormFHIRServerResponseException exception = assertThrows(
				SnowstormFHIRServerResponseException.class,
				() -> provider.translate(
						request, response,
						null, null, null,
						SOURCE_CODE, SNOMED_SYSTEM, null,
						null, null, null,
						null, null, new org.hl7.fhir.r4.model.BooleanType(true)));

		assertEquals(400, exception.getStatusCode());
		assertTrue(exception.getMessage().contains("reverse"));
	}

	private FHIRConceptMap implicitSnomedMap() {
		FHIRConceptMap map = new FHIRConceptMap();
		map.setUrl("http://snomed.info/sct?fhir_cm=447562003");
		map.setTargetUri(ICD10_SYSTEM + FHIRConceptMapService.WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX);
		map.setImplicitSnomedMap(true);
		return map;
	}

	private FHIRMapElement mapElement(String targetCode, String equivalence, String display) {
		FHIRMapTarget mapTarget = new FHIRMapTarget(targetCode, equivalence, null);
		mapTarget.setDisplay(display);
		return new FHIRMapElement()
				.setCode(SOURCE_CODE)
				.setTarget(List.of(mapTarget));
	}

	private String getStringParameter(Parameters parameters, String name) {
		for (Parameters.ParametersParameterComponent param : parameters.getParameter()) {
			if (name.equals(param.getName()) && param.getValue() != null) {
				return param.getValue().primitiveValue();
			}
		}
		return null;
	}

	private Coding getMatchConcept(Parameters parameters) {
		for (Parameters.ParametersParameterComponent param : parameters.getParameter()) {
			if ("match".equals(param.getName())) {
				for (Parameters.ParametersParameterComponent part : param.getPart()) {
					if ("concept".equals(part.getName())) {
						return (Coding) part.getValue();
					}
				}
			}
		}
		return null;
	}
}
