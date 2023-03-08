package org.snomed.snowstorm.fhir.pojo;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;

public class ConceptAndSystemResult {

	private final Concept concept;
	private final FHIRCodeSystemVersion codeSystemVersion;
	private String message;

	public ConceptAndSystemResult(Concept concept, FHIRCodeSystemVersion codeSystemVersion) {
		this.concept = concept;
		this.codeSystemVersion = codeSystemVersion;
	}

	public ConceptAndSystemResult(Concept concept, FHIRCodeSystemVersion codeSystemVersion, String message) {
		this.concept = concept;
		this.codeSystemVersion = codeSystemVersion;
		this.message = message;
	}

	public Concept getConcept() {
		return concept;
	}

	public FHIRCodeSystemVersion getCodeSystemVersion() {
		return codeSystemVersion;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
