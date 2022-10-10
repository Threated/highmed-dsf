package org.highmed.dsf.bpe.listener;

import static org.highmed.dsf.bpe.ConstantsBase.BPMN_EXECUTION_VARIABLE_QUESTIONNAIRE_RESPONSE_ID;
import static org.highmed.dsf.bpe.ConstantsBase.CODESYSTEM_HIGHMED_BPMN;
import static org.highmed.dsf.bpe.ConstantsBase.CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_BUSINESS_KEY;
import static org.highmed.dsf.bpe.ConstantsBase.CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_USER_TASK_ID;
import static org.highmed.dsf.bpe.ConstantsBase.CODESYSTEM_HIGHMED_BPMN_VALUE_ERROR;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.highmed.dsf.bpe.ConstantsBase;
import org.highmed.dsf.fhir.authorization.read.ReadAccessHelper;
import org.highmed.dsf.fhir.client.FhirWebserviceClientProvider;
import org.highmed.dsf.fhir.organization.OrganizationProvider;
import org.highmed.dsf.fhir.questionnaire.QuestionnaireResponseHelper;
import org.highmed.dsf.fhir.task.TaskHelper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

public class DefaultUserTaskListener implements TaskListener, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(DefaultUserTaskListener.class);

	private final FhirWebserviceClientProvider clientProvider;
	private final OrganizationProvider organizationProvider;
	private final QuestionnaireResponseHelper questionnaireResponseHelper;
	private final TaskHelper taskHelper;
	private final ReadAccessHelper readAccessHelper;

	/**
	 * @deprecated as of release 0.8.0, use {@link #getExecution()} instead
	 */
	@Deprecated
	protected DelegateExecution execution;

	public DefaultUserTaskListener(FhirWebserviceClientProvider clientProvider,
			OrganizationProvider organizationProvider, QuestionnaireResponseHelper questionnaireResponseHelper,
			TaskHelper taskHelper, ReadAccessHelper readAccessHelper)
	{
		this.clientProvider = clientProvider;
		this.organizationProvider = organizationProvider;
		this.questionnaireResponseHelper = questionnaireResponseHelper;
		this.taskHelper = taskHelper;
		this.readAccessHelper = readAccessHelper;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(clientProvider, "clientProvider");
		Objects.requireNonNull(organizationProvider, "organizationProvider");
		Objects.requireNonNull(questionnaireResponseHelper, "questionnaireResponseHelper");
		Objects.requireNonNull(taskHelper, "taskHelper");
		Objects.requireNonNull(readAccessHelper, "readAccessHelper");
	}

	@Override
	public final void notify(DelegateTask userTask)
	{
		this.execution = userTask.getExecution();

		try
		{
			logger.trace("Execution of user task with id='{}'", execution.getCurrentActivityId());

			String questionnaireUrlWithVersion = userTask.getBpmnModelElementInstance().getCamundaFormKey();
			Questionnaire questionnaire = readQuestionnaire(questionnaireUrlWithVersion);

			String businessKey = execution.getBusinessKey();
			String userTaskId = userTask.getId();

			QuestionnaireResponse questionnaireResponse = createDefaultQuestionnaireResponse(
					questionnaireUrlWithVersion, businessKey, userTaskId);
			addPlaceholderAnswersToQuestionnaireResponse(questionnaireResponse, questionnaire);

			modifyQuestionnaireResponse(userTask, questionnaireResponse);

			checkQuestionnaireResponse(questionnaireResponse);

			IdType created = clientProvider.getLocalWebserviceClient().withRetryForever(60000)
					.create(questionnaireResponse).getIdElement();
			execution.setVariable(BPMN_EXECUTION_VARIABLE_QUESTIONNAIRE_RESPONSE_ID, created.getIdPart());

			logger.info("Created user task with id={}, process waiting for it's completion", created.getValue());
		}
		catch (Exception exception)
		{
			Task task = getTask();

			logger.debug("Error while executing user task listener " + getClass().getName(), exception);
			logger.error("Process {} has fatal error in step {} for task with id {}, reason: {}",
					execution.getProcessDefinitionId(), execution.getActivityInstanceId(), task.getId(),
					exception.getMessage());

			String errorMessage = "Process " + execution.getProcessDefinitionId() + " has fatal error in step "
					+ execution.getActivityInstanceId() + ", reason: " + exception.getMessage();

			task.addOutput(taskHelper.createOutput(CODESYSTEM_HIGHMED_BPMN, CODESYSTEM_HIGHMED_BPMN_VALUE_ERROR,
					errorMessage));
			task.setStatus(Task.TaskStatus.FAILED);

			clientProvider.getLocalWebserviceClient().withMinimalReturn().update(task);

			// TODO evaluate throwing exception as alternative to stopping the process instance
			execution.getProcessEngine().getRuntimeService().deleteProcessInstance(execution.getProcessInstanceId(),
					exception.getMessage());
		}
	}

	private Questionnaire readQuestionnaire(String urlWithVersion)
	{
		Bundle search = clientProvider.getLocalWebserviceClient().search(Questionnaire.class,
				Map.of("url", Collections.singletonList(urlWithVersion)));

		List<Questionnaire> questionnaires = search.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
				.map(Bundle.BundleEntryComponent::getResource).filter(r -> r instanceof Questionnaire)
				.map(r -> (Questionnaire) r).collect(Collectors.toList());

		if (questionnaires.size() < 1)
			throw new RuntimeException("Could not find Questionnaire resource with url|version=" + urlWithVersion);

		if (questionnaires.size() > 1)
			logger.info("Found {} Questionnaire resources with url|version={}, using the first", questionnaires.size(),
					urlWithVersion);

		return questionnaires.get(0);
	}

	private QuestionnaireResponse createDefaultQuestionnaireResponse(String questionnaireUrlWithVersion,
			String businessKey, String userTaskId)
	{
		QuestionnaireResponse questionnaireResponse = new QuestionnaireResponse();
		questionnaireResponse.setQuestionnaire(questionnaireUrlWithVersion);
		questionnaireResponse.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);

		questionnaireResponse.setAuthor(new Reference().setType(ResourceType.Organization.name())
				.setIdentifier(organizationProvider.getLocalIdentifier()));

		questionnaireResponseHelper.addItemLeave(questionnaireResponse,
				CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_BUSINESS_KEY, "The business-key of the process execution",
				new StringType(businessKey));
		questionnaireResponseHelper.addItemLeave(questionnaireResponse,
				CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_USER_TASK_ID, "The user-task-id of the process execution",
				new StringType(userTaskId));

		readAccessHelper.addLocal(questionnaireResponse);

		return questionnaireResponse;
	}

	private void addPlaceholderAnswersToQuestionnaireResponse(QuestionnaireResponse questionnaireResponse,
			Questionnaire questionnaire)
	{
		questionnaire.getItem().stream()
				.filter(i -> !CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_BUSINESS_KEY.equals(i.getLinkId()))
				.filter(i -> !CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_USER_TASK_ID.equals(i.getLinkId()))
				.forEach(i -> createAndAddAnswerPlaceholder(questionnaireResponse, i));
	}

	private void createAndAddAnswerPlaceholder(QuestionnaireResponse questionnaireResponse,
			Questionnaire.QuestionnaireItemComponent question)
	{
		Type answer = questionnaireResponseHelper.transformQuestionTypeToAnswerType(question);
		questionnaireResponseHelper.addItemLeave(questionnaireResponse, question.getLinkId(), question.getText(),
				answer);
	}

	private void checkQuestionnaireResponse(QuestionnaireResponse questionnaireResponse)
	{
		questionnaireResponse.getItem().stream()
				.filter(i -> CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_BUSINESS_KEY.equals(i.getLinkId())).findFirst()
				.orElseThrow(() -> new RuntimeException("QuestionnaireResponse does not contain an item with linkId='"
						+ CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_BUSINESS_KEY + "'"));

		questionnaireResponse.getItem().stream()
				.filter(i -> CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_USER_TASK_ID.equals(i.getLinkId())).findFirst()
				.orElseThrow(() -> new RuntimeException("QuestionnaireResponse does not contain an item with linkId='"
						+ CODESYSTEM_HIGHMED_BPMN_USER_TASK_VALUE_USER_TASK_ID + "'"));

		if (!QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS.equals(questionnaireResponse.getStatus()))
			throw new RuntimeException("QuestionnaireResponse must be in status 'in-progress'");
	}

	/**
	 * Use this method to modify the {@link QuestionnaireResponse} before it will be created in state
	 * {@link QuestionnaireResponse.QuestionnaireResponseStatus#INPROGRESS}
	 *
	 * @param userTask
	 *            not <code>null</code>, user task on which this {@link QuestionnaireResponse} is based
	 * @param questionnaireResponse
	 *            not <code>null</code>, containing an answer placeholder for every item in the corresponding
	 *            {@link Questionnaire}
	 */
	protected void modifyQuestionnaireResponse(DelegateTask userTask, QuestionnaireResponse questionnaireResponse)
	{
		// Nothing to do in default behaviour
	}

	protected final DelegateExecution getExecution()
	{
		return execution;
	}

	protected final TaskHelper getTaskHelper()
	{
		return taskHelper;
	}

	protected final FhirWebserviceClientProvider getFhirWebserviceClientProvider()
	{
		return clientProvider;
	}

	protected final ReadAccessHelper getReadAccessHelper()
	{
		return readAccessHelper;
	}

	/**
	 * @return the active task from execution variables, i.e. the leading task if the main process is running or the
	 *         current task if a subprocess is running.
	 * @throws IllegalStateException
	 *             if execution of this service delegate has not been started
	 * @see ConstantsBase#BPMN_EXECUTION_VARIABLE_TASK
	 */
	protected final Task getTask()
	{
		return taskHelper.getTask(execution);
	}

	/**
	 * @return the current task from execution variables, the task resource that started the current process or
	 *         subprocess
	 * @throws IllegalStateException
	 *             if execution of this service delegate has not been started
	 * @see ConstantsBase#BPMN_EXECUTION_VARIABLE_TASK
	 */
	protected final Task getCurrentTaskFromExecutionVariables()
	{
		return taskHelper.getCurrentTaskFromExecutionVariables(execution);
	}

	/**
	 * @return the leading task from execution variables, same as current task if not in a subprocess
	 * @throws IllegalStateException
	 *             if execution of this service delegate has not been started
	 * @see ConstantsBase#BPMN_EXECUTION_VARIABLE_LEADING_TASK
	 */
	protected final Task getLeadingTaskFromExecutionVariables()
	{
		return taskHelper.getLeadingTaskFromExecutionVariables(execution);
	}

	/**
	 * <i>Use this method to update the process engine variable {@link ConstantsBase#BPMN_EXECUTION_VARIABLE_TASK},
	 * after modifying the {@link Task}.</i>
	 *
	 * @param task
	 *            not <code>null</code>
	 * @throws IllegalStateException
	 *             if execution of this service delegate has not been started
	 * @see ConstantsBase#BPMN_EXECUTION_VARIABLE_TASK
	 */
	protected final void updateCurrentTaskInExecutionVariables(Task task)
	{
		taskHelper.updateCurrentTaskInExecutionVariables(execution, task);
	}

	/**
	 * <i>Use this method to update the process engine variable
	 * {@link ConstantsBase#BPMN_EXECUTION_VARIABLE_LEADING_TASK}, after modifying the {@link Task}.</i>
	 * <p>
	 * Updates the current task if no leading task is set.
	 *
	 * @param task
	 *            not <code>null</code>
	 * @throws IllegalStateException
	 *             if execution of this service delegate has not been started
	 * @see ConstantsBase#BPMN_EXECUTION_VARIABLE_LEADING_TASK
	 */
	protected final void updateLeadingTaskInExecutionVariables(Task task)
	{
		taskHelper.updateLeadingTaskInExecutionVariables(execution, task);
	}
}