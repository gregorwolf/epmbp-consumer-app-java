package de.linuxdozent.epmbp.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sap.cds.ql.cqn.CqnLimit;
import com.sap.cds.ql.cqn.CqnValue;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.cloudplatform.connectivity.Destination;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.exception.DestinationAccessException;
import com.sap.cloud.sdk.odatav2.connectivity.ODataException;
import com.sap.cloud.sdk.service.prov.api.response.ErrorResponse;
import com.sap.cloud.sdk.service.prov.api.response.QueryResponse;

import cds.gen.catalogservice.*;
import de.linuxdozent.vdm.namespaces.delinuxdozentvdmzepmbpsrv.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;

@Component
@ServiceName(CatalogService_.CDS_NAME)
public class CatalogService implements EventHandler {
	
	Logger logger = LoggerFactory.getLogger(CatalogService.class);
	
	private Map<Object, Map<String, Object>> epmBPs = new HashMap<>();

	@On(event = CdsService.EVENT_READ, entity = EPMBusinessPartners_.CDS_NAME)
	public void onRead(CdsReadEventContext context) {		
		System.out.println("destinations: " + System.getenv("destinations"));

		try {
			Destination destination = DestinationAccessor.getDestination("NPL");
			if(context.getCqn().limit().isPresent()) {
				CqnLimit limit = context.getCqn().limit().get();
				CqnValue rows = limit.rows();
				System.out.println("rows:" + rows.toString());
			}
			System.out.println("Read EPMBusinessPartners:" + context.toString());
			
			try {
				// Create Map containing request header information
				Map<String, String> requestHeaders = new HashMap<>();
				requestHeaders.put("Content-Type", "application/json");
	
				final List<EPMBusinessPartner> EPMBusinessPartners = 
					new DefaultZEPMBPSRVEdmxService().getAllEPMBusinessPartner()
						.withHeaders(requestHeaders)
						.onRequestAndImplicitRequests()
						.select(EPMBusinessPartner.BUSINESS_PARTNER_ID, EPMBusinessPartner.COMPANY)
						.execute(destination.asHttp());
				// How to convert List EPMBusinessPartners to Map epmBPs
			} catch (final ODataException e) {
				logger.error("Error occurred with the Query operation: " + e.getMessage(), e);
				ErrorResponse er = ErrorResponse.getBuilder()
						.setMessage("Error occurred with the Query operation: " + e.getMessage()).setStatusCode(500)
						.setCause(e).response();
			}
	
			context.setResult(epmBPs.values());	
		} catch (DestinationAccessException e) {
			System.out.println("Message: " + e.getMessage());
		}	
		
	}

}
