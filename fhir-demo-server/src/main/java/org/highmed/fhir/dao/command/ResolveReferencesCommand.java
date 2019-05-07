package org.highmed.fhir.dao.command;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.WebApplicationException;

import org.highmed.fhir.dao.DomainResourceDao;
import org.highmed.fhir.dao.exception.ResourceDeletedException;
import org.highmed.fhir.dao.exception.ResourceNotFoundException;
import org.highmed.fhir.help.ExceptionHandler;
import org.highmed.fhir.help.ParameterConverter;
import org.highmed.fhir.help.ResponseGenerator;
import org.highmed.fhir.service.ReferenceExtractor;
import org.highmed.fhir.service.ReferenceResolver;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;

public class ResolveReferencesCommand<R extends DomainResource, D extends DomainResourceDao<R>>
		extends AbstractCommandWithResource<R, D> implements Command
{
	private final ReferenceExtractor referenceExtractor;
	private final ResponseGenerator responseGenerator;
	private final ReferenceResolver referenceResolver;

	public ResolveReferencesCommand(int index, Bundle bundle, BundleEntryComponent entry, String serverBase, R resource,
			D dao, ExceptionHandler exceptionHandler, ParameterConverter parameterConverter,
			ReferenceExtractor referenceExtractor, ResponseGenerator responseGenerator,
			ReferenceResolver referenceResolver)
	{
		super(4, index, bundle, entry, serverBase, resource, dao, exceptionHandler, parameterConverter);

		this.referenceExtractor = referenceExtractor;
		this.responseGenerator = responseGenerator;
		this.referenceResolver = referenceResolver;
	}

	@Override
	public void preExecute(Map<String, IdType> idTranslationTable)
	{
	}

	@Override
	public void execute(Map<String, IdType> idTranslationTable, Connection connection)
			throws SQLException, WebApplicationException
	{
		R latest = latestOrErrorIfDeletedOrNotFound(idTranslationTable, connection);

		boolean resourceNeedsUpdated = referenceExtractor.getReferences(latest)
				.map(resolveReference(idTranslationTable, connection)).anyMatch(b -> b);
		if (resourceNeedsUpdated)
		{
			try
			{
				dao.updateSameRowWithTransaction(connection, latest);
			}
			catch (ResourceNotFoundException e)
			{
				throw exceptionHandler.internalServerError(e);
			}
		}
	}

	private R latestOrErrorIfDeletedOrNotFound(Map<String, IdType> idTranslationTable, Connection connection)
			throws SQLException
	{
		try
		{
			String id = idTranslationTable.getOrDefault(entry.getFullUrl(), resource.getIdElement()).getIdPart();
			return dao.readWithTransaction(connection, parameterConverter.toUuid(resource.getResourceType().name(), id))
					.orElseThrow(() -> exceptionHandler.internalServerError(new ResourceNotFoundException(id)));
		}
		catch (ResourceDeletedException e)
		{
			throw exceptionHandler.internalServerError(e);
		}
	}

	private Function<ResourceReference, Boolean> resolveReference(Map<String, IdType> idTranslationTable,
			Connection connection) throws WebApplicationException
	{
		return resourceReference ->
		{
			switch (resourceReference.getType(serverBase))
			{
				case TEMPORARY:
					return resolveTemporaryReference(resourceReference, idTranslationTable);
				case LITERAL_INTERNAL:
					return referenceResolver.resolveLiteralInternalReference(resource, index, resourceReference,
							connection);
				case LITERAL_EXTERNAL:
					return referenceResolver.resolveLiteralExternalReference(resource, index, resourceReference);
				case CONDITIONAL:
					return referenceResolver.resolveConditionalReference(resource, index, resourceReference,
							connection);
				case LOGICAL:
					return referenceResolver.resolveLogicalReference(resource, index, resourceReference, connection);
				case UNKNOWN:
				default:
					throw new WebApplicationException(
							responseGenerator.unknownReference(index, resource, resourceReference));
			}
		};
	}

	private boolean resolveTemporaryReference(ResourceReference resourceReference,
			Map<String, IdType> idTranslationTable)
	{
		IdType newId = idTranslationTable.get(resourceReference.getReference().getReference());
		if (newId == null)
			throw new WebApplicationException(responseGenerator.unknownReference(index, resource, resourceReference));
		else
			resourceReference.getReference().setReferenceElement(newId);

		return true; // throws exception if reference could not be resolved
	}

	@Override
	public BundleEntryComponent postExecute()
	{
		return null;
	}
}
