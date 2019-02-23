package org.highmed.fhir.webservice.impl;

import org.highmed.fhir.dao.ResearchStudyDao;
import org.highmed.fhir.event.EventGenerator;
import org.highmed.fhir.event.EventManager;
import org.highmed.fhir.help.ExceptionHandler;
import org.highmed.fhir.help.ParameterConverter;
import org.highmed.fhir.help.ResponseGenerator;
import org.highmed.fhir.service.ResourceValidator;
import org.highmed.fhir.webservice.specification.ResearchStudyService;
import org.hl7.fhir.r4.model.ResearchStudy;

public class ResearchStudyServiceImpl extends AbstractServiceImpl<ResearchStudyDao, ResearchStudy>
		implements ResearchStudyService
{
	public ResearchStudyServiceImpl(String resourceTypeName, String serverBase, int defaultPageCount,
			ResearchStudyDao dao, ResourceValidator validator, EventManager eventManager,
			ExceptionHandler exceptionHandler, EventGenerator<ResearchStudy> eventGenerator,
			ResponseGenerator responseGenerator, ParameterConverter parameterConverter)
	{
		super(resourceTypeName, serverBase, defaultPageCount, dao, validator, eventManager, exceptionHandler,
				eventGenerator, responseGenerator, parameterConverter);
	}
}
