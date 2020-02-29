package de.linuxdozent.epmbp.handlers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import cds.gen.catalogservice.*;
import de.linuxdozent.vdm.namespaces.delinuxdozentvdmzepmbpsrv.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;

@Component
@ServiceName(CatalogService_.CDS_NAME)
public class CatalogService implements EventHandler {

	private Map<Object, Map<String, Object>> epmBPs = new HashMap<>();

	@On(event = CdsService.EVENT_READ, entity = EPMBusinessPartners_.CDS_NAME)
	public void onRead(CdsReadEventContext context) {
		System.out.println("Read EPMBusinessPartners:" + context.toString());
		context.setResult(epmBPs.values());
	}
	
/*
	@Query(serviceName = "CatalogService", entity = "EPMBusinessPartners")
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
					.execute(destination.asHttp());
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
*/
}
