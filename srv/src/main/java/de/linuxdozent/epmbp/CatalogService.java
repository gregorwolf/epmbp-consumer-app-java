package de.linuxdozent.epmbp;

import java.util.*;

import com.sap.cloud.sdk.service.prov.api.annotations.*;
import com.sap.cloud.sdk.service.prov.api.*;
import com.sap.cloud.sdk.service.prov.api.operations.Query;
import com.sap.cloud.sdk.service.prov.api.request.*;
import com.sap.cloud.sdk.service.prov.api.response.*;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpDestination;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestinationProperties;
import com.sap.cloud.sdk.odatav2.connectivity.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.sdk.s4hana.connectivity.*;
import de.linuxdozent.vdm.namespaces.delinuxdozentvdmzepmbpsrv.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;

public class CatalogService {

	Logger logger = LoggerFactory.getLogger(CatalogService.class);
	
	private static final String DESTINATION_NAME = "NPL"; // Refers to the destination created in Step 6

	private final DefaultHttpDestination destination = DestinationAccessor.getDestination(DESTINATION_NAME).;

	@Query(serviceName = "S4BookshopService", entity = "EPMBusinessPartners")
	public QueryResponse queryEPMBusinessPartner(QueryRequest qryRequest) {

		QueryResponse queryResponse;
		int top = qryRequest.getTopOptionValue();
		int skip = qryRequest.getSkipOptionValue();

		try {
			// Create Map containing request header information
			Map<String, String> requestHeaders = new HashMap<>();
			requestHeaders.put("Content-Type", "application/json");

			final List<EPMBusinessPartner> EPMBusinessPartners = new DefaultZEPMBPSRVEdmxService().getAllEPMBusinessPartner()
					.withHeaders(requestHeaders)
					.onRequestAndImplicitRequests()
					.select(EPMBusinessPartner.BUSINESS_PARTNER_ID, EPMBusinessPartner.COMPANY)
					.top(top >= 0 ? top : 50)
					.skip(skip >= 0 ? skip : -1)
					.execute(destination);
			queryResponse = QueryResponse.setSuccess().setData(EPMBusinessPartners).response();

		} catch (final ODataException e) {
			logger.error("Error occurred with the Query operation: " + e.getMessage(), e);
			ErrorResponse er = ErrorResponse.getBuilder()
					.setMessage("Error occurred with the Query operation: " + e.getMessage()).setStatusCode(500)
					.setCause(e).response();
			queryResponse = QueryResponse.setError(er);
		}

		return queryResponse;
	}

}
