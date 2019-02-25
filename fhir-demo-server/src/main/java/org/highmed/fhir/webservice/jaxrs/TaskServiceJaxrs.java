package org.highmed.fhir.webservice.jaxrs;

import javax.ws.rs.Path;

import org.highmed.fhir.webservice.specification.TaskService;
import org.hl7.fhir.r4.model.Task;

@Path(TaskServiceJaxrs.PATH)
public class TaskServiceJaxrs extends AbstractServiceJaxrs<Task, TaskService> implements TaskService
{
	public static final String PATH = "Task";

	public TaskServiceJaxrs(TaskService delegate)
	{
		super(delegate);
	}

	@Override
	public String getPath()
	{
		return PATH;
	}
}
