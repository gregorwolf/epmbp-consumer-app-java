package de.linuxdozent.epmbp.handlers;

import java.util.HashMap;
import java.util.Iterator;
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
import com.sap.cloud.sdk.s4hana.datamodel.odata.exception.NoSuchEntityFieldException;
import com.sap.cloud.sdk.service.prov.api.response.ErrorResponse;
import com.sap.cloud.sdk.service.prov.api.response.QueryResponse;

import cds.gen.catalogservice.*;
import de.linuxdozent.vdm.namespaces.delinuxdozentvdmzepmbpsrv.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;

@Component
@ServiceName(CatalogService_.CDS_NAME)
public class CatalogService implements EventHandler {
	
	Logger logger = LoggerFactory.getLogger(CatalogService.class);
	
	private final Map<Object, Map<String, Object>> epmBPs = new HashMap<>();

	@On(event = CdsService.EVENT_CREATE, entity = EPMBusinessPartners_.CDS_NAME)
	public void onCreate(final CdsCreateEventContext context) {
		context.getCqn().entries().forEach(e -> epmBPs.put(e.get("BpId"), e));
		context.setResult(context.getCqn().entries());
	}

	@On(event = CdsService.EVENT_READ, entity = EPMBusinessPartners_.CDS_NAME)
	public void onRead(final CdsReadEventContext context) {
		System.out.println("destinations: " + System.getenv("destinations"));

		try {
			final Destination destination = DestinationAccessor.getDestination("NPL");
			if (context.getCqn().limit().isPresent()) {
				final CqnLimit limit = context.getCqn().limit().get();
				final CqnValue rows = limit.rows();
				System.out.println("rows:" + rows.toString());
			}
			System.out.println("Read EPMBusinessPartners:" + context.toString());

			try {
				// Create Map containing request header information
				final Map<String, String> requestHeaders = new HashMap<>();
				requestHeaders.put("Content-Type", "application/json");

				final List<EPMBusinessPartner> EPMBusinessPartners = new DefaultZEPMBPSRVEdmxService()
						.getAllEPMBusinessPartner()
						.withHeaders(requestHeaders)
						.onRequestAndImplicitRequests()
						.select(EPMBusinessPartner.BUSINESS_PARTNER_ID, EPMBusinessPartner.COMPANY)
						.execute(destination.asHttp());
				final int size = EPMBusinessPartners.size();
				logger.info("Number of EPMBusinessPartners: " + size);

				// How to convert List EPMBusinessPartners to Map epmBPs
				final Iterator epmBPiterator = EPMBusinessPartners.iterator();
				while (epmBPiterator.hasNext()) {
					final EPMBusinessPartner epmBP = (EPMBusinessPartner) epmBPiterator.next();
					try {
						Map<String, Object> epmBPfields = epmBP.getCustomFields();
						epmBPs.put(epmBP.getCustomField(EPMBusinessPartner.BUSINESS_PARTNER_ID), epmBPfields);
					} catch (final NoSuchEntityFieldException e) {
						logger.error("Error occurred with the Query operation: " + e.getMessage());
					}
				}
			} catch (final ODataException e) {
				logger.error("Error occurred with the Query operation: " + e.getMessage(), e);
				final ErrorResponse er = ErrorResponse.getBuilder()
						.setMessage("Error occurred with the Query operation: " + e.getMessage()).setStatusCode(500)
						.setCause(e).response();
			}

			context.setResult(epmBPs.values());
		} catch (final DestinationAccessException e) {
			System.out.println("Message: " + e.getMessage());
		}	
		
	}

}
