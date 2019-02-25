package org.highmed.fhir.webservice.jaxrs;

import javax.ws.rs.Path;

import org.highmed.fhir.webservice.specification.CodeSystemService;
import org.hl7.fhir.r4.model.CodeSystem;

@Path(CodeSystemServiceJaxrs.PATH)
public class CodeSystemServiceJaxrs extends AbstractServiceJaxrs<CodeSystem, CodeSystemService>
		implements CodeSystemService
{
	public static final String PATH = "CodeSystem";

	public CodeSystemServiceJaxrs(CodeSystemService delegate)
	{
		super(delegate);
	}

	@Override
	public String getPath()
	{
		return PATH;
	}
}
