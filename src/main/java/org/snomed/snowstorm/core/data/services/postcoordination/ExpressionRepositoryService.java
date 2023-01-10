package org.snomed.snowstorm.core.data.services.postcoordination;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.common.util.set.Sets;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierHelper;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.data.services.postcoordination.model.PostCoordinatedExpression;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpressionRepositoryService {

	@Autowired
	private ExpressionParser expressionParser;

	@Autowired
	private ExpressionTransformationAndValidationService transformationService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchService branchService;

	@Autowired
	private IdentifierSource identifierSource;

	@Autowired
	private IncrementalClassificationService incrementalClassificationService;

	// 1119435002 | Canonical close to user form expression reference set (foundation metadata concept) |
	// referencedComponentId - a generated SCTID for expression
	// expression - the close to user form expression
	// substrate - the URI of the SNOMED CT Edition and release the expression was authored against

	// 1119468009 | Classifiable form expression reference set (foundation metadata concept) |
	// referencedComponentId - the SCTID matching the close-to-user form expression
	// expression - the classifiable form expression, created by transforming the close-to-user form expression.
	// substrate - the URI of the SNOMED CT Edition and release that was used to transform close-to-user form expression to the classifiable form expression.

	private static final String CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET = "1119435002";
	private static final String CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET = "1119468009";
	private static final String SUBSTRATE_FIELD = "substrate";

	public Page<PostCoordinatedExpression> findAll(String branch, PageRequest pageRequest) {
		Page<ReferenceSetMember> membersPage = memberService.findMembers(branch,
				new MemberSearchRequest()
						.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET),
				pageRequest);
		return getPostCoordinatedExpressions(pageRequest, membersPage);
	}

	public Page<PostCoordinatedExpression> findByCanonicalCloseToUserForm(String branch, String expression, PageRequest pageRequest) {
		return getPostCoordinatedExpressions(pageRequest, memberService.findMembers(branch,
				new MemberSearchRequest()
						.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET)
						.additionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION, expression),
				pageRequest));
	}

	private PageImpl<PostCoordinatedExpression> getPostCoordinatedExpressions(PageRequest pageRequest, Page<ReferenceSetMember> membersPage) {
		List<PostCoordinatedExpression> expressions = membersPage.getContent().stream()
				.map((ReferenceSetMember closeToUserFormMember) -> toExpression(closeToUserFormMember, new ReferenceSetMember())).collect(Collectors.toList());
		return new PageImpl<>(expressions, pageRequest, membersPage.getTotalElements());
	}

	public PostCoordinatedExpression createExpression(String closeToUserFormExpression, String branch, String moduleId) throws ServiceException {
		List<PostCoordinatedExpression> expressions = createExpressionsAllOrNothing(Collections.singletonList(closeToUserFormExpression), branch, moduleId);
		return expressions.get(0);
	}

	public List<PostCoordinatedExpression> createExpressionsAllOrNothing(List<String> closeToUserFormExpressions, String branch, String moduleId) throws ServiceException {
		int namespace = IdentifierHelper.getNamespaceFromSCTID(moduleId);
		final List<PostCoordinatedExpression> postCoordinatedExpressions = parseValidateTransformAndClassifyExpressions(closeToUserFormExpressions, branch, namespace);

		if (postCoordinatedExpressions.stream().noneMatch(PostCoordinatedExpression::hasException)) {
			for (PostCoordinatedExpression postCoordinatedExpression : postCoordinatedExpressions) {
				final String expressionId = postCoordinatedExpression.getId();
				final ReferenceSetMember closeToUserFormMember = new ReferenceSetMember(moduleId, CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET, expressionId)
						.setAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION, postCoordinatedExpression.getCloseToUserForm().replace(" ", ""));
				final ReferenceSetMember classifiableFormMember = new ReferenceSetMember(moduleId, CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET, expressionId)
						.setAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION, postCoordinatedExpression.getClassifiableForm().replace(" ", ""));

				memberService.createMembers(branch, Sets.newHashSet(closeToUserFormMember, classifiableFormMember));
				// Internal id namespace 1000104
			}
		}

		return postCoordinatedExpressions;
	}

	public PostCoordinatedExpression parseValidateTransformAndClassifyExpression(String branch, String originalCloseToUserForm, int namespace) throws ServiceException {
		return parseValidateTransformAndClassifyExpressions(Collections.singletonList(originalCloseToUserForm), branch, namespace).get(0);
	}

	public List<PostCoordinatedExpression> parseValidateTransformAndClassifyExpressions(List<String> originalCloseToUserForms, String branch, int namespace) {
		List<PostCoordinatedExpression> expressionOutcomes = new ArrayList<>();
		for (String originalCloseToUserForm : originalCloseToUserForms) {
			TimerUtil timer = new TimerUtil("exp");
			ExpressionContext context = new ExpressionContext(branch, branchService, versionControlHelper, mrcmService, timer);
			try {
				// Sort contents of expression
				ComparableExpression closeToUserFormExpression = expressionParser.parseExpression(originalCloseToUserForm);
				timer.checkpoint("Parse expression");

				// Validate and transform expression to classifiable form if needed.
				// This groups any 'loose' attributes
				final ComparableExpression classifiableFormExpression;
				classifiableFormExpression = transformationService.validateAndTransform(closeToUserFormExpression, context);
				timer.checkpoint("Transformation");

				// Assign identifier
				if (classifiableFormExpression.getExpressionId() == null) {
					List<Long> expressionIds = identifierSource.reserveIds(namespace, "16", 1);
					classifiableFormExpression.setExpressionId(expressionIds.get(0));
				}

				// Classify
				ComparableExpression necessaryNormalForm;
				boolean skipClassification = false;
				if (skipClassification) {
					necessaryNormalForm = classifiableFormExpression;
				} else {
					necessaryNormalForm = incrementalClassificationService.classify(classifiableFormExpression, branch);
					timer.checkpoint("Classify");
				}

				// TODO: Add attribute sorting
				final PostCoordinatedExpression pce = new PostCoordinatedExpression(
						necessaryNormalForm.getExpressionId().toString(), closeToUserFormExpression.toString(),
						classifiableFormExpression.toString(), necessaryNormalForm.toString());

				populateHumanReadableForms(pce, context);
				timer.checkpoint("Add human readable");
				expressionOutcomes.add(pce);
				timer.finish();
			} catch (ServiceException e) {
				String humanReadableCloseToUserForm;
				try {
					humanReadableCloseToUserForm = createHumanReadableExpression(originalCloseToUserForm, context);
				} catch (ServiceException e2) {
					humanReadableCloseToUserForm = originalCloseToUserForm;
				}
				expressionOutcomes.add(new PostCoordinatedExpression(humanReadableCloseToUserForm, e));
			}
		}
		return expressionOutcomes;
	}

	private void populateHumanReadableForms(PostCoordinatedExpression expressionForms, ExpressionContext context) throws ServiceException {
		final List<String> humanReadableExpressions = createHumanReadableExpressions(
				Lists.newArrayList(expressionForms.getClassifiableForm(), expressionForms.getNecessaryNormalForm()), context);
		expressionForms.setHumanReadableClassifiableForm(humanReadableExpressions.get(0));
		expressionForms.setHumanReadableNecessaryNormalForm(humanReadableExpressions.get(1));
	}

	private PostCoordinatedExpression toExpression(ReferenceSetMember closeToUserFormMember, ReferenceSetMember classifiableFormMember) {
		return new PostCoordinatedExpression(closeToUserFormMember.getReferencedComponentId(),
				closeToUserFormMember.getAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION), classifiableFormMember.getAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION), null);
	}

	private String createHumanReadableExpression(String expression, ExpressionContext context) throws ServiceException {
		if (expression != null) {
			return createHumanReadableExpressions(Lists.newArrayList(expression), context).get(0);
		}
		return null;
	}

	private List<String> createHumanReadableExpressions(List<String> expressions, ExpressionContext context) throws ServiceException {
		final Set<String> allConceptIds = new HashSet<>();
		for (String expression : expressions) {
			final ComparableExpression comparableExpression = expressionParser.parseExpression(expression);
			allConceptIds.addAll(comparableExpression.getAllConceptIds());
		}
		return transformationService.addHumanPTsToExpressionStrings(expressions, allConceptIds, context);
	}

}
