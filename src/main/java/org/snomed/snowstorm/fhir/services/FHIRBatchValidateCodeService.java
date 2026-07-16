package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;

@Service
public class FHIRBatchValidateCodeService implements FHIRConstants {

	private static final Pattern VALIDATE_CODE_URL_PATTERN =
			Pattern.compile("^CodeSystem(?:/([^/]+))?/\\$validate-code(?:\\?(.*))?$");

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	@Autowired
	private FHIRHelper fhirHelper;

	public Bundle processBatch(Bundle input, String acceptLanguageHeader) {
		if (input.getType() == Bundle.BundleType.TRANSACTION) {
			throw exception("Only batch Bundles are supported. Use type 'batch' for CodeSystem/$validate-code requests.",
					IssueType.NOTSUPPORTED, 400);
		}
		if (input.getType() != Bundle.BundleType.BATCH) {
			throw exception(format("Unsupported Bundle type '%s'. Only 'batch' is supported.", input.getType()),
					IssueType.NOTSUPPORTED, 400);
		}

		Bundle response = new Bundle();
		response.setType(Bundle.BundleType.BATCHRESPONSE);

		for (BundleEntryComponent entry : input.getEntry()) {
			BundleEntryComponent responseEntry = new BundleEntryComponent();
			if (entry.hasFullUrl()) {
				responseEntry.setFullUrl(entry.getFullUrl());
			}
			try {
				Parameters result = processEntry(entry, acceptLanguageHeader);
				responseEntry.getResponse().setStatus("200 OK");
				responseEntry.setResource(result);
			} catch (SnowstormFHIRServerResponseException e) {
				responseEntry.getResponse().setStatus(e.getStatusCode() + " " + statusLabel(e.getStatusCode()));
				if (e.getOperationOutcome() != null) {
					responseEntry.setResource(e.getOperationOutcome());
				} else {
					OperationOutcome outcome = new OperationOutcome();
					outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
							.setCode(IssueType.EXCEPTION)
							.setDiagnostics(e.getMessage());
					responseEntry.setResource(outcome);
				}
			}
			response.addEntry(responseEntry);
		}
		return response;
	}

	private Parameters processEntry(BundleEntryComponent entry, String acceptLanguageHeader) {
		if (entry.getRequest() == null || entry.getRequest().getMethod() == null) {
			throw exception("Batch entry must include request.method.", IssueType.INVALID, 400);
		}

		HTTPVerb method = entry.getRequest().getMethod();
		String requestUrl = entry.getRequest().getUrl();
		if (requestUrl == null) {
			throw exception("Batch entry must include request.url.", IssueType.INVALID, 400);
		}

		Matcher matcher = VALIDATE_CODE_URL_PATTERN.matcher(requestUrl);
		if (!matcher.matches()) {
			throw exception(format("Unsupported batch entry URL '%s'. Only CodeSystem/$validate-code is supported.", requestUrl),
					IssueType.NOTSUPPORTED, 501);
		}

		String codeSystemId = matcher.group(1);
		String query = matcher.group(2);

		return switch (method) {
			case GET -> validateFromQueryParams(codeSystemId, query, acceptLanguageHeader);
			case POST -> validateFromPostEntry(codeSystemId, entry.getResource(), acceptLanguageHeader);
			default -> throw exception(format("Unsupported batch request method '%s' for CodeSystem/$validate-code.", method),
					IssueType.NOTSUPPORTED, 501);
		};
	}

	private Parameters validateFromQueryParams(String codeSystemId, String query, String acceptLanguageHeader) {
		Map<String, String> params = parseQueryParams(query);
		ValidateCodeRequest request = parseValidateCodeRequest(codeSystemId, params, null);
		return executeValidateCodeRequest(request, acceptLanguageHeader);
	}

	private Parameters validateFromPostEntry(String codeSystemId, Resource resource, String acceptLanguageHeader) {
		if (!(resource instanceof Parameters parameters)) {
			throw exception("POST batch entries for CodeSystem/$validate-code must include a Parameters resource.",
					IssueType.INVALID, 400);
		}
		ValidateCodeRequest request = parseValidateCodeRequest(codeSystemId, null, parameters);
		return executeValidateCodeRequest(request, acceptLanguageHeader);
	}

	private ValidateCodeRequest parseValidateCodeRequest(String codeSystemId, Map<String, String> queryParams, Parameters parameters) {
		String url = getParamString(queryParams, parameters, "url");
		String version = getParamString(queryParams, parameters, "version");
		String code = getParamString(queryParams, parameters, "code");
		String display = getParamString(queryParams, parameters, "display");
		String codeSystem = getParamString(queryParams, parameters, "codeSystem");
		String date = getParamString(queryParams, parameters, "date");
		String displayLanguage = getParamString(queryParams, parameters, "displayLanguage");
		Coding coding = getParamCoding(queryParams, parameters, "coding");

		if (codeSystemId == null) {
			notSupported("codeSystem", codeSystem);
			notSupported("date", date);
			notSupported("displayLanguage", displayLanguage);
		}
		mutuallyExclusive("code", code, "coding", coding);
		mutuallyRequired("display", display, "code", code, "coding", coding);

		CodeType codeType = code != null ? new CodeType(code) : null;
		UriType urlType = url != null ? new UriType(url) : null;
		StringType versionType = version != null ? new StringType(version) : null;

		FHIRCodeSystemVersionParams codeSystemParams = codeSystemId != null
				? getCodeSystemVersionParams(new IdType(codeSystemId), urlType, versionType, coding)
				: getCodeSystemVersionParams(null, urlType, versionType, coding);

		return new ValidateCodeRequest(codeSystemParams, fhirHelper.recoverCode(codeType, coding), display);
	}

	private Parameters executeValidateCodeRequest(ValidateCodeRequest request, String acceptLanguageHeader) {
		return fhirCodeSystemService.validateCode(
				request.codeSystemParams(),
				request.code(),
				request.display(),
				acceptLanguageHeader);
	}

	private String getParamString(Map<String, String> queryParams, Parameters parameters, String name) {
		if (queryParams != null && queryParams.containsKey(name)) {
			return queryParams.get(name);
		}
		if (parameters == null) {
			return null;
		}
		return parameters.getParameter().stream()
				.filter(p -> name.equals(p.getName()) && p.hasValue())
				.findFirst()
				.map(p -> p.getValue().primitiveValue())
				.orElse(null);
	}

	private Coding getParamCoding(Map<String, String> queryParams, Parameters parameters, String name) {
		if (queryParams != null && queryParams.containsKey(name)) {
			return parseCodingParam(queryParams.get(name));
		}
		if (parameters == null) {
			return null;
		}
		return parameters.getParameter().stream()
				.filter(p -> name.equals(p.getName()) && p.hasValue())
				.findFirst()
				.map(p -> {
					if (p.getValue() instanceof Coding coding) {
						return coding;
					}
					return parseCodingParam(p.getValue().primitiveValue());
				})
				.orElse(null);
	}

	private Coding parseCodingParam(String value) {
		int pipe = value.indexOf('|');
		if (pipe == -1) {
			return new Coding(null, value, null);
		}
		String system = value.substring(0, pipe);
		String remainder = value.substring(pipe + 1);
		int secondPipe = remainder.indexOf('|');
		if (secondPipe == -1) {
			return new Coding(system, remainder, null);
		}
		return new Coding(system, remainder.substring(0, secondPipe), remainder.substring(secondPipe + 1));
	}

	private Map<String, String> parseQueryParams(String query) {
		Map<String, String> params = new LinkedHashMap<>();
		if (query == null || query.isEmpty()) {
			return params;
		}
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq >= 0) {
				params.put(
						URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
						URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
			} else {
				params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
			}
		}
		return params;
	}

	private static String statusLabel(int statusCode) {
		return switch (statusCode) {
			case 400 -> "Bad Request";
			case 404 -> "Not Found";
			case 501 -> "Not Implemented";
			default -> "Error";
		};
	}

	private record ValidateCodeRequest(FHIRCodeSystemVersionParams codeSystemParams, String code, String display) {}
}
