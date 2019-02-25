package org.highmed.fhir.webservice.jaxrs;

import javax.ws.rs.Path;

import org.highmed.fhir.webservice.specification.PatientService;
import org.hl7.fhir.r4.model.Patient;

@Path(PatientServiceJaxrs.PATH)
public class PatientServiceJaxrs extends AbstractServiceJaxrs<Patient, PatientService> implements PatientService
{
	public static final String PATH = "Patient";

	public PatientServiceJaxrs(PatientService delegate)
	{
		super(delegate);
	}

	@Override
	public String getPath()
	{
		return PATH;
	}
}
