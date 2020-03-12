package de.linuxdozent.epmbp.handlers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sap.cds.Result;
import com.sap.cds.feature.auth.AuthenticatedUserClaimProvider;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnLimit;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnValue;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.cloudplatform.connectivity.Destination;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.exception.DestinationAccessException;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceDecorator;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration.TimeLimiterConfiguration;
import com.sap.cloud.sdk.s4hana.connectivity.DefaultErpHttpDestination;
import com.sap.cloud.sdk.s4hana.connectivity.ErpHttpDestination;
import com.sap.cloud.sdk.s4hana.connectivity.ErpHttpDestinationUtils;
import com.sap.cloud.sdk.s4hana.datamodel.odata.exception.NoSuchEntityFieldException;
import com.sap.cloud.sdk.service.prov.api.response.ErrorResponse;

import cds.gen.catalogservice.*;
import de.linuxdozent.vdm.namespaces.zepmbpsrvedmx.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;
import de.linuxdozent.vdm.services.ZEPMBPSRVEdmxService;

@Component
@ServiceName(CatalogService_.CDS_NAME)
public class CatalogService implements EventHandler {
	
	Logger logger = LoggerFactory.getLogger(CatalogService.class);
	
	// private final Destination destination = DestinationAccessor.getDestination("NPL_SDK");
	private final ErpHttpDestination httpDest = DestinationAccessor
		.getDestination("NPL_SDK").asHttp().decorate(DefaultErpHttpDestination::new);
	
	private final TimeLimiterConfiguration timeLimit = TimeLimiterConfiguration.of()
            .timeoutDuration(Duration.ofSeconds(10));

    private final ResilienceConfiguration resilienceConfiguration =
            ResilienceConfiguration.of(CatalogService.class)
			.timeLimiterConfiguration(timeLimit);
	private final ZEPMBPSRVEdmxService epmBPservice = new DefaultZEPMBPSRVEdmxService();
	
	private final Map<Object, Map<String, Object>> epmBPs = new HashMap<>();

	@On(event = CdsService.EVENT_CREATE, entity = EPMBusinessPartners_.CDS_NAME)
	public void onCreate(final CdsCreateEventContext context) {
		context.getCqn().entries().forEach(e -> epmBPs.put(e.get("BpId"), e));
		context.setResult(context.getCqn().entries());
	}

	@On(event = CdsService.EVENT_READ, entity = EPMBusinessPartners_.CDS_NAME)
	public void onRead(final CdsReadEventContext context) {
		System.out.println("destinations: " + System.getenv("destinations"));
		/*
		// Maybe we have to try it with the CAP service consumption?
		CdsService service = context.getService();
		CqnSelect query = Select.from("my.bookshop.EPMBusinessPartners")
			.columns("BpId","CompanyName","City","Street");
		Result result = service.run(query);
		*/

		try {
			// Maybe needed to get the JWT: AuthenticatedUserClaimProvider.INSTANCE.getUserClaim()
			String jwt = AuthenticatedUserClaimProvider.INSTANCE.getUserClaim();
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
				requestHeaders.put("Authorization", "Bearer " + jwt);

				final List<EPMBusinessPartner> EPMBusinessPartners =  ResilienceDecorator.executeCallable(
                    () -> epmBPservice
						.getAllEPMBusinessPartner()
						.withHeaders(requestHeaders)
						.onRequestAndImplicitRequests()
						.select(EPMBusinessPartner.BUSINESS_PARTNER_ID, EPMBusinessPartner.COMPANY)
						// .execute(destination.asHttp())
						.execute(httpDest),
						resilienceConfiguration);
				final int size = EPMBusinessPartners.size();
				logger.info("Number of EPMBusinessPartners: " + size);

				// How to convert List EPMBusinessPartners to Map epmBPs
				final Iterator epmBPiterator = EPMBusinessPartners.iterator();
				while (epmBPiterator.hasNext()) {
					final EPMBusinessPartner epmBP = (EPMBusinessPartner) epmBPiterator.next();
					try {
						Map<String, Object> epmBPfields = new HashMap<>();
						epmBPfields.put(EPMBusinessPartner.BUSINESS_PARTNER_ID.getFieldName(), epmBP.getBusinessPartnerID());
						epmBPfields.put(EPMBusinessPartner.COMPANY.getFieldName(), epmBP.getCompany());
						epmBPs.put(epmBP.getBusinessPartnerID(), epmBPfields);
					} catch (final NoSuchEntityFieldException e) {
						logger.error("Error occurred with the Query operation: " + e.getMessage());
						throw new ServiceException("An internal server error occurred", e);
					}
				}
			} catch (final Exception e) {
				logger.error("Error occurred with the Query operation: " + e.getMessage(), e);
				throw new ServiceException("An internal server error occurred", e);
			}
			context.setResult(epmBPs.values());
		} catch (final DestinationAccessException e) {
			System.out.println("Message: " + e.getMessage());
			throw new ServiceException("An internal server error occurred", e);
		}	
		
	}

}
