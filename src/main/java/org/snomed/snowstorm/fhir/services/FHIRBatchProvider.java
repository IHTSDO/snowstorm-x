package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.ACCEPT_LANGUAGE_HEADER;

@Component
public class FHIRBatchProvider {

	@Autowired
	private FHIRBatchValidateCodeService batchValidateCodeService;

	@Transaction
	public Bundle batch(HttpServletRequest request, @TransactionParam Bundle input) {
		return batchValidateCodeService.processBatch(input, request.getHeader(ACCEPT_LANGUAGE_HEADER));
	}
}
