package org.highmed.dsf.fhir.organization;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Endpoint;

public interface EndpointProvider
{
	Endpoint getLocalEndpoint();

	default String getLocalEndpointAddress()
	{
		return getLocalEndpoint().getAddress();
	}

	Map<String, Endpoint> getDefaultEndpointsByOrganizationIdentifier();

	default Map<String, String> getDefaultEndpointAdressesByOrganizationIdentifier()
	{
		return getDefaultEndpointsByOrganizationIdentifier().entrySet().stream()
				.collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getAddress()));
	}

	Optional<Endpoint> getFirstDefaultEndpoint(String organizationIdentifierValue);

	default Optional<String> getFirstDefaultEndpointAddress(String organizationIdentifierValue)
	{
		return getFirstDefaultEndpoint(organizationIdentifierValue).map(Endpoint::getAddress);
	}

	Map<String, Endpoint> getConsortiumEndpointsByOrganizationIdentifier(String consortiumIdentifierValue);

	default Map<String, String> getConsortiumEndpointAdressesByOrganizationIdentifier(String consortiumIdentifierValue)
	{
		return getConsortiumEndpointsByOrganizationIdentifier(consortiumIdentifierValue).entrySet().stream()
				.collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getAddress()));
	}

	Map<String, Endpoint> getConsortiumEndpointsByOrganizationIdentifier(String consortiumIdentifierValue,
			String roleSystem, String roleCode);

	default Map<String, String> getConsortiumEndpointAdressesByOrganizationIdentifier(String consortiumIdentifierValue,
			String roleSystem, String roleCode)
	{
		return getConsortiumEndpointsByOrganizationIdentifier(consortiumIdentifierValue, roleSystem, roleCode)
				.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getAddress()));
	}

	Optional<Endpoint> getFirstConsortiumEndpoint(String consortiumIdentifierValue, String roleSystem, String roleCode,
			String organizationIdentifierValue);

	default Optional<String> getFirstConsortiumEndpointAdress(String consortiumIdentifierValue, String roleSystem,
			String roleCode, String organizationIdentifierValue)
	{
		return getFirstConsortiumEndpoint(consortiumIdentifierValue, roleSystem, roleCode, organizationIdentifierValue)
				.map(Endpoint::getAddress);
	}
}